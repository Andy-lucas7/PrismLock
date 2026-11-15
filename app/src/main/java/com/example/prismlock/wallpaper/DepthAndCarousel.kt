package com.example.prismlock.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Project
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

object SubjectDepthEngine {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )
    }
    private val memCache = LruCache<String, Bitmap>(4)

    private fun key(uri: String): String =
        MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun maskFile(ctx: Context, uri: String): File =
        File(File(ctx.cacheDir, "depth_masks").apply { mkdirs() }, "${key(uri)}.png")

    suspend fun getOrCreateMaskIfEnabled(ctx: Context, uri: String?, source: Bitmap?, project: Project): Bitmap? {
        if (!project.depth || uri == null || source == null) return null
        val imageKey = WallpaperCarousel.imageKey(ctx, uri)
        if (!project.isDepthEnabledFor(imageKey)) return null
        return getOrCreateMask(ctx, uri, source)
    }

    suspend fun getOrCreateMask(ctx: Context, uri: String, source: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        memCache.get(uri)?.let { return@withContext it }
        val file = maskFile(ctx, uri)
        if (file.exists()) {
            val cached = BitmapFactory.decodeFile(file.absolutePath)
            if (cached != null) {
                memCache.put(uri, cached)
                return@withContext cached
            }
        }
        val fg = runSegment(source) ?: return@withContext null
        runCatching {
            file.outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 92, it) }
        }
        memCache.put(uri, fg)
        fg
    }

    private suspend fun runSegment(bmp: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        val input = InputImage.fromBitmap(bmp, 0)
        segmenter.process(input)
            .addOnSuccessListener { res ->
                if (cont.isActive) cont.resume(res.foregroundBitmap)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}

object WallpaperCarousel {
    private val bmpCache = LruCache<String, Bitmap>(4)
    private val thumbCache = LruCache<String, Bitmap>(48)
    private val keyCache = ConcurrentHashMap<String, String>()

    fun imageKey(ctx: Context, uriStr: String): String {
        keyCache[uriStr]?.let { return it }
        val uri = Uri.parse(uriStr)
        val displayName = runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val resolved = displayName ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uriStr
        keyCache[uriStr] = resolved
        return resolved
    }

    suspend fun loadThumbnail(ctx: Context, uriStr: String, maxEdge: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        if (uriStr.isBlank()) return@withContext null
        thumbCache.get(uriStr)?.let { return@withContext it }
        runCatching {
            val uri = Uri.parse(uriStr)
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val scale = (maxEdge.toFloat() / maxOf(w, h)).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            thumbCache.put(uriStr, bmp)
            bmp
        }.getOrNull()
    }

    fun listImages(ctx: Context, treeUriStr: String?): List<Uri> {
        if (treeUriStr.isNullOrBlank()) return emptyList()
        return runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
            val out = mutableListOf<Uri>()
            ctx.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    val mime = c.getString(mimeCol) ?: ""
                    if (mime.startsWith("image/")) {
                        out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idCol)))
                    }
                }
            }
            out.sortedBy { it.toString() }
        }.getOrDefault(emptyList())
    }

    suspend fun currentUri(
        ctx: Context,
        project: Project,
        advance: Boolean = false,
        checkInterval: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val bakedEntries = WallpaperBaker.readManifest(ctx)?.entries
        val uriStrings: List<String> = if (!bakedEntries.isNullOrEmpty() && (project.folderUri != null || bakedEntries.size == 1)) {
            bakedEntries.map { it.uri }
        } else {
            listImages(ctx, project.folderUri).map { it.toString() }
        }
        if (uriStrings.isEmpty()) {
            val single = project.imageUri
            ProjectStore.setCurrentWallpaperUri(ctx, single)
            return@withContext single
        }
        if (advance) {
            val nextIdx = (ProjectStore.index(ctx) + 1) % uriStrings.size
            val uri = uriStrings[nextIdx]
            ProjectStore.setIndex(ctx, nextIdx, uri)
            return@withContext uri
        }
        if (checkInterval && project.intervalMin > 0) {
            val last = ProjectStore.lastSwitch(ctx)
            if (System.currentTimeMillis() - last >= project.intervalMin * 60_000L) {
                val nextIdx = (ProjectStore.index(ctx) + 1) % uriStrings.size
                val uri = uriStrings[nextIdx]
                ProjectStore.setIndex(ctx, nextIdx, uri)
                return@withContext uri
            }
        }
        val savedUri = ProjectStore.currentWallpaperUri(ctx)
        if (savedUri != null && uriStrings.contains(savedUri)) {
            return@withContext savedUri
        }
        val idx = ProjectStore.index(ctx).coerceIn(0, uriStrings.lastIndex)
        val uri = uriStrings[idx]
        ProjectStore.setCurrentWallpaperUri(ctx, uri)
        uri
    }

    suspend fun loadBitmap(ctx: Context, uriStr: String?, reqW: Int, reqH: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (uriStr.isNullOrBlank()) return@withContext null
        val key = "$uriStr@${reqW}x${reqH}"
        bmpCache.get(key)?.let { return@withContext it }
        runCatching {
            val uri = Uri.parse(uriStr)
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val scale = maxOf(reqW.toFloat() / w, reqH.toFloat() / h).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            bmpCache.put(key, bmp)
            bmp
        }.getOrNull()
    }
}
