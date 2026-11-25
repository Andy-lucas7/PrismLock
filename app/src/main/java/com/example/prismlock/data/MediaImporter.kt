package com.example.prismlock.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.prismlock.model.Project
import com.example.prismlock.model.flatten
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object MediaImporter {
    private const val TAG = "MediaImporter"
    private const val MEDIA_DIR = "media"
    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

    fun resolveExtension(mime: String?, fallbackName: String? = null): String {
        val m = mime?.trim()?.lowercase().orEmpty()
        if (m.startsWith("video/")) {
            return when (m) {
                "video/mp4", "video/x-m4v" -> "mp4"
                "video/quicktime" -> "mov"
                "video/x-matroska" -> "mkv"
                "video/webm" -> "webm"
                "video/3gpp" -> "3gp"
                else -> {
                    val mapped = runCatching {
                        MimeTypeMap.getSingleton()?.getExtensionFromMimeType(m)?.lowercase()
                    }.getOrNull()
                    if (mapped in VIDEO_EXTS) mapped!! else "mp4"
                }
            }
        }
        if (m.startsWith("image/")) {
            return when (m) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                "image/bmp", "image/x-ms-bmp" -> "bmp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
        }
        val fromName = fallbackName
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return when {
            fromName in VIDEO_EXTS -> fromName
            fromName in IMAGE_EXTS -> fromName
            else -> "jpg"
        }
    }

    suspend fun importToAppStorage(ctx: Context, uri: Uri): Uri? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val dir = File(appCtx.filesDir, MEDIA_DIR).apply { mkdirs() }
        val mime = runCatching { appCtx.contentResolver.getType(uri) }.getOrNull()
        val ext = resolveExtension(mime, uri.lastPathSegment)
        val unique = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val tmp = File(dir, "$unique.$ext.tmp")
        val out = File(dir, "$unique.$ext")
        try {
            val input = appCtx.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { i ->
                tmp.outputStream().use { o ->
                    i.copyTo(o, 64 * 1024)
                    o.flush()
                }
            }
            if (!tmp.isFile || tmp.length() <= 0L) {
                tmp.delete()
                return@withContext null
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            Uri.fromFile(out)
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            runCatching { out.delete() }
            Log.w(TAG, "Failed to import media from $uri", e)
            null
        }
    }

    fun isManagedMediaUri(ctx: Context, uriStr: String?): Boolean {
        if (uriStr.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return false
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        return runCatching {
            val mediaRoot = File(ctx.applicationContext.filesDir, MEDIA_DIR).canonicalFile
            val target = File(path).canonicalFile
            target.parentFile == mediaRoot
        }.getOrDefault(false)
    }

    fun collectProjectUris(project: Project?): Set<String> {
        if (project == null) return emptySet()
        val out = HashSet<String>()
        project.imageUri?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        project.layers.flatten().forEach { (node, _) ->
            node.imageUri.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        project.customAssets.values.forEach { v ->
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    suspend fun deleteIfUnused(
        ctx: Context,
        candidateUri: String?,
        extraInUseUris: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        if (!isManagedMediaUri(ctx, candidateUri)) return@withContext
        val uriStr = candidateUri!!
        if (uriStr in extraInUseUris) return@withContext
        val appCtx = ctx.applicationContext
        val referenced = HashSet<String>(extraInUseUris)
        val ok = runCatching {
            ProjectStore.themesFlow(appCtx).first().forEach { referenced.addAll(collectProjectUris(it)) }
            ProjectStore.widgetsFlow(appCtx).first().forEach { referenced.addAll(collectProjectUris(it)) }
            referenced.addAll(collectProjectUris(ProjectStore.readActiveProject(appCtx)))
            referenced.addAll(collectProjectUris(ProjectStore.getActiveWidget(appCtx)))
        }.isSuccess
        if (!ok) return@withContext
        if (uriStr !in referenced) {
            runCatching {
                val path = Uri.parse(uriStr).path ?: return@runCatching
                File(path).delete()
            }
        }
    }
}
