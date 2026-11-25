package com.example.prismlock.wallpaper

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaMetadataRetriever
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
    private val segLock = Any()
    private var segmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter? = null
    private val memCache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private fun obtainSegmenter(): com.google.mlkit.vision.segmentation.subject.SubjectSegmenter = synchronized(segLock) {
        segmenter ?: SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        ).also { segmenter = it }
    }

    fun releaseMemory() {
        synchronized(segLock) {
            runCatching { segmenter?.close() }
            segmenter = null
        }
        memCache.evictAll()
    }

    private fun key(uri: String): String =
        MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun maskFile(ctx: Context, uri: String): File =
        File(File(ctx.applicationContext.filesDir, "depth_masks").apply { mkdirs() }, "${key(uri)}_hd.png")

    private fun legacyCacheMaskFile(ctx: Context, uri: String): File =
        File(File(ctx.applicationContext.cacheDir, "depth_masks"), "${key(uri)}.png")

    fun getCachedMaskIfEnabled(
        ctx: Context,
        uri: String?,
        project: Project,
        maxEdge: Int = 0,
        cacheInMemory: Boolean = true
    ): Bitmap? {
        if (!project.depth || uri == null) return null
        if (WallpaperCarousel.isVideoUri(ctx, uri)) return null
        val imageKey = WallpaperCarousel.imageKey(ctx, uri)
        if (!project.isDepthEnabledFor(imageKey)) return null
        if (cacheInMemory) {
            memCache.get(uri)?.let { hit ->
                if (!hit.isRecycled) return hit
                memCache.remove(uri)
            }
        }
        val file = maskFile(ctx, uri).takeIf { it.exists() }
            ?: legacyCacheMaskFile(ctx, uri).takeIf { it.exists() }
            ?: return null
        val decoded = runCatching {
            if (maxEdge > 0) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                while (largest / (sample * 2) >= maxEdge) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        }.getOrNull() ?: return null
        if (cacheInMemory) memCache.put(uri, decoded)
        return decoded
    }

    suspend fun getOrCreateMaskIfEnabled(ctx: Context, uri: String?, source: Bitmap?, project: Project): Bitmap? {
        if (!project.depth || uri == null || source == null) return null
        if (WallpaperCarousel.isVideoUri(ctx, uri)) return null
        val imageKey = WallpaperCarousel.imageKey(ctx, uri)
        if (!project.isDepthEnabledFor(imageKey)) return null
        return getOrCreateMask(ctx, uri, source)
    }

    suspend fun getOrCreateMask(
        ctx: Context,
        uri: String,
        source: Bitmap,
        closeAfter: Boolean = true
    ): Bitmap? = withContext(Dispatchers.IO) {
        val minRes = source.width >= 720
        memCache.get(uri)?.let { hit ->
            if (!hit.isRecycled && (!minRes || hit.width >= 720)) return@withContext hit
            if (hit.isRecycled) memCache.remove(uri)
        }
        val file = maskFile(ctx, uri)
        val existingFile = when {
            file.exists() -> file
            legacyCacheMaskFile(ctx, uri).exists() -> legacyCacheMaskFile(ctx, uri)
            else -> null
        }
        if (existingFile != null) {
            val cached = BitmapFactory.decodeFile(existingFile.absolutePath)
            if (cached != null && (!minRes || cached.width >= 720)) {
                if (existingFile != file && minRes) {
                    runCatching { existingFile.copyTo(file, overwrite = true) }
                }
                memCache.put(uri, cached)
                return@withContext cached
            }
            cached?.recycle()
        }
        if (runCatching { Application.getProcessName().endsWith(":wallpaper") }.getOrDefault(false)) {
            return@withContext null
        }
        val fg = try {
            runSegment(source)
        } finally {
            if (closeAfter) {
                synchronized(segLock) {
                    runCatching { segmenter?.close() }
                    segmenter = null
                }
            }
        }
        if (fg == null) return@withContext null
        if (fg.width >= 720) {
            runCatching {
                file.outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 92, it) }
            }
            memCache.put(uri, fg)
        }
        fg
    }

    private suspend fun runSegment(bmp: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        // Segmentation is an enhancement: an ML Kit failure (missing module, bad input) must degrade to "no mask".
        val task = try {
            obtainSegmenter().process(InputImage.fromBitmap(bmp, 0))
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        task
            .addOnSuccessListener { res ->
                if (cont.isActive) cont.resume(res.foregroundBitmap)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    fun buildSubjectColorFilter(project: Project): ColorMatrixColorFilter? {
        val sat = if (project.depthInvertColorPop) {
            1f - project.depthBgDesaturate.coerceIn(0f, 1f)
        } else {
            1f + project.depthSubjectPop.coerceIn(0f, 1f) * 0.14f
        }
        val pop = project.depthSubjectPop.coerceIn(0f, 1f)
        if (kotlin.math.abs(sat - 1f) < 0.01f && pop < 0.01f) return null

        val satMatrix = ColorMatrix().apply { setSaturation(sat.coerceIn(0f, 2f)) }
        val contrast = 1f + pop * 0.22f
        val translate = (-0.5f * contrast + 0.5f) * 255f + pop * 12f
        val cbMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cbMatrix.preConcat(satMatrix)
        return ColorMatrixColorFilter(cbMatrix)
    }

    fun applySubjectColorPopMask(mask: Bitmap, project: Project): Bitmap {
        val filter = buildSubjectColorFilter(project) ?: return mask
        val out = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = filter
        }
        c.drawBitmap(mask, 0f, 0f, p)
        return out
    }

    fun composeProcessedWallpaper(
        baseSharp: Bitmap,
        depthMask: Bitmap?,
        project: Project,
        blurFn: ((Bitmap, Float) -> Bitmap)? = null
    ): Bitmap {
        if (depthMask == null || !project.hasDepthPostProcessing()) return baseSharp
        val w = baseSharp.width.coerceAtLeast(1)
        val h = baseSharp.height.coerceAtLeast(1)
        val u = w / 1080f

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        // 1. Background layer (selective Bokeh blur + Color Pop desaturation)
        val blurPx = (project.depthBgBlur.coerceIn(0f, 1f) * 42f * u).coerceIn(0f, 64f * u)
        val blurredBg = if (blurPx > 0.75f && blurFn != null) blurFn(baseSharp, blurPx) else baseSharp
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (!project.depthInvertColorPop && project.depthBgDesaturate > 0.01f) {
            val sat = (1f - project.depthBgDesaturate.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            bgPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(sat) })
        }
        canvas.drawBitmap(blurredBg, null, android.graphics.Rect(0, 0, w, h), bgPaint)
        if (blurredBg !== baseSharp && !blurredBg.isRecycled) {
            blurredBg.recycle()
        }

        // 2. Subtle vignette + 3D shadow halo behind foreground subject
        val maskMat = Matrix().apply {
            val s = maxOf(w.toFloat() / depthMask.width.coerceAtLeast(1), h.toFloat() / depthMask.height.coerceAtLeast(1))
            val dx = (w - depthMask.width * s) * 0.5f
            val dy = (h - depthMask.height * s) * 0.5f
            setScale(s, s)
            postTranslate(dx, dy)
        }
        val vig = project.depthVignette.coerceIn(0f, 1f)
        if (vig > 0.01f) {
            val vigAlpha = (vig * 0.62f * 255f).toInt().coerceIn(0, 255)
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.46f,
                    maxOf(w, h) * 0.64f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(vigAlpha, 0, 0, 0)),
                    floatArrayOf(0.32f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vigPaint)

            runCatching {
                val alphaMask = depthMask.extractAlpha()
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    color = Color.argb((vig * 0.52f * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
                    maskFilter = BlurMaskFilter((20f * u).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawBitmap(alphaMask, maskMat, haloPaint)
                alphaMask.recycle()
            }
        }

        // 3. Sharp foreground subject with brightness/contrast/pop filter
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = buildSubjectColorFilter(project)
        }
        canvas.drawBitmap(depthMask, maskMat, fgPaint)
        out.setHasAlpha(false)
        return out
    }
}

object WallpaperCarousel {
    private val bmpCache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val thumbCache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val customAssetCache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val keyCache = ConcurrentHashMap<String, String>()
    private val videoCache = ConcurrentHashMap<String, Boolean>()

    fun clearMemoryCache() {
        bmpCache.evictAll()
        thumbCache.evictAll()
        customAssetCache.evictAll()
    }

    private fun createImageDecoderSource(ctx: Context, uri: Uri): ImageDecoder.Source {
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) return ImageDecoder.createSource(File(path))
        }
        return ImageDecoder.createSource(ctx.contentResolver, uri)
    }

    fun isVideoUri(ctx: Context, uriStr: String?): Boolean {
        if (uriStr.isNullOrBlank()) return false
        videoCache[uriStr]?.let { return it }
        val uri = Uri.parse(uriStr)
        val mime = if (uri.scheme == "file") null else runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
        val isVid = when {
            mime != null -> mime.startsWith("video/")
            else -> {
                val lower = (uri.lastPathSegment ?: uriStr).lowercase()
                lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
                    lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".3gp")
            }
        }
        videoCache[uriStr] = isVid
        return isVid
    }

    fun imageKey(ctx: Context, uriStr: String): String {
        keyCache[uriStr]?.let { return it }
        val uri = Uri.parse(uriStr)
        val displayName = if (uri.scheme == "file") null else runCatching {
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
        if (isVideoUri(ctx, uriStr)) {
            val bmp = loadVideoFrame(ctx, uriStr, maxEdge, maxEdge)
            if (bmp != null) thumbCache.put(uriStr, bmp)
            return@withContext bmp
        }
        runCatching {
            val uri = Uri.parse(uriStr)
            val src = createImageDecoderSource(ctx, uri)
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

    suspend fun loadVideoFrame(ctx: Context, uriStr: String, reqW: Int, reqH: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriStr)
            val mmr = MediaMetadataRetriever()
            try {
                if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                    mmr.setDataSource(uri.path)
                } else {
                    mmr.setDataSource(ctx, uri)
                }
                mmr.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqW.coerceAtLeast(1), reqH.coerceAtLeast(1))
                    ?: mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }

    fun listImages(ctx: Context, treeUriStr: String?): List<Uri> {
        if (treeUriStr.isNullOrBlank()) return emptyList()
        if (treeUriStr.startsWith("file://")) {
            return runCatching {
                val dir = File(Uri.parse(treeUriStr).path ?: return emptyList())
                if (!dir.isDirectory) return emptyList()
                val exts = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "mp4", "webm", "mkv", "mov", "m4v")
                val videoExts = setOf("mp4", "webm", "mkv", "mov", "m4v")
                dir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in exts }
                    ?.sortedBy { it.name.lowercase() }
                    ?.map { f ->
                        val u = Uri.fromFile(f)
                        videoCache[u.toString()] = f.extension.lowercase() in videoExts
                        u
                    }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }
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
                    if (mime.startsWith("image/") || mime.startsWith("video/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idCol))
                        videoCache[docUri.toString()] = mime.startsWith("video/")
                        out.add(docUri)
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
        checkInterval: Boolean = false,
        persist: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        if (project.folderUri.isNullOrBlank() && project.imageUri.isNullOrBlank()) {
            return@withContext null
        }
        if (project.folderUri.isNullOrBlank() && !project.imageUri.isNullOrBlank()) {
            if (persist) ProjectStore.setCurrentWallpaperUri(ctx, project.imageUri)
            return@withContext project.imageUri
        }
        val manifest = WallpaperBaker.readManifest(ctx)
        val bakedEntries = if (manifest != null && manifest.projectId.isNotBlank() && manifest.projectId == project.id) {
            manifest.entries
        } else null
        val uriStrings: List<String> = if (!bakedEntries.isNullOrEmpty()) {
            bakedEntries.map { it.uri }
        } else {
            listImages(ctx, project.folderUri).map { it.toString() }
        }
        if (uriStrings.isEmpty()) {
            return@withContext project.imageUri
        }
        if (advance) {
            val nextIdx = (ProjectStore.index(ctx) + 1) % uriStrings.size
            val uri = uriStrings[nextIdx]
            if (persist) ProjectStore.setIndex(ctx, nextIdx, uri)
            return@withContext uri
        }
        if (checkInterval && project.intervalMin > 0) {
            val last = ProjectStore.lastSwitch(ctx)
            if (System.currentTimeMillis() - last >= project.intervalMin * 60_000L) {
                val nextIdx = (ProjectStore.index(ctx) + 1) % uriStrings.size
                val uri = uriStrings[nextIdx]
                if (persist) ProjectStore.setIndex(ctx, nextIdx, uri)
                return@withContext uri
            }
        }
        val savedUri = ProjectStore.currentWallpaperUri(ctx)
        if (savedUri != null && uriStrings.contains(savedUri)) {
            return@withContext savedUri
        }
        val idx = ProjectStore.index(ctx).coerceIn(0, uriStrings.lastIndex)
        val uri = uriStrings[idx]
        if (persist) ProjectStore.setCurrentWallpaperUri(ctx, uri)
        uri
    }

    suspend fun loadBitmap(
        ctx: Context,
        uriStr: String?,
        reqW: Int,
        reqH: Int,
        preferRgb565: Boolean = true,
        cacheInMemory: Boolean = true
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriStr.isNullOrBlank()) return@withContext null
        val key = "$uriStr@${reqW}x${reqH}|$preferRgb565"
        if (cacheInMemory) {
            bmpCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        }
        if (isVideoUri(ctx, uriStr)) {
            val frame = loadVideoFrame(ctx, uriStr, reqW, reqH) ?: return@withContext null
            if (cacheInMemory) bmpCache.put(key, frame)
            return@withContext frame
        }
        runCatching {
            val uri = Uri.parse(uriStr)
            val src = createImageDecoderSource(ctx, uri)
            val decoded = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val scale = maxOf(reqW.toFloat() / w, reqH.toFloat() / h).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            val bmp = if (preferRgb565 && decoded.config != Bitmap.Config.RGB_565 && !decoded.hasAlpha()) {
                decoded.copy(Bitmap.Config.RGB_565, false)?.also { decoded.recycle() } ?: decoded
            } else decoded
            if (cacheInMemory) bmpCache.put(key, bmp)
            bmp
        }.getOrNull()
    }

    fun loadCustomAssetBitmap(ctx: Context, uriStr: String?, reqW: Int, reqH: Int): Bitmap? {
        if (uriStr.isNullOrBlank() || ("://" !in uriStr && !uriStr.startsWith("/"))) return null
        val tw = reqW.coerceIn(16, 1080)
        val th = reqH.coerceIn(16, 1080)
        val key = "$uriStr@${tw}x$th"
        customAssetCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        return runCatching {
            val uri = if (uriStr.startsWith("/")) Uri.fromFile(File(uriStr)) else Uri.parse(uriStr)
            val src = createImageDecoderSource(ctx, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val scale = maxOf(tw.toFloat() / w, th.toFloat() / h).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            customAssetCache.put(key, bmp)
            bmp
        }.getOrNull()
    }
}
