package com.example.prismlock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.model.Project
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Persistent store for rendered hub cards. Key = content hash of the project (stable across
 * processes, unlike data-class hashCode with enums) + render size + day (mock clock shows date).
 * Lets the UI drop every preview from RAM on stop and come back without re-rendering.
 */
internal object HubPreviewDiskCache {
    // 2: widget cards no longer borrow the lock-screen photo as backdrop; drop previews baked with it.
    // 3: key also covers the image the card renders (carousel advance / replaced file invalidates it).
    private const val VERSION = 3
    private const val SEP = "__"
    private const val PREVIEW_EXT = ".webp"
    private const val AMBIENT_EXT = ".amb.webp"

    class Entry(val preview: Bitmap, val ambient: Bitmap)

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "hub_previews").apply { mkdirs() }

    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /**
     * [imageUri] is the exact image the card renders (resolved once by the caller and reused for the
     * render), [imageStamp] its modification stamp from [imageStamp].
     */
    fun key(project: Project, w: Int, h: Int, imageUri: String?, imageStamp: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(ProjectStore.json.encodeToString(Project.serializer(), project).toByteArray())
        md.update("|${imageUri.orEmpty()}|$imageStamp".toByteArray())
        md.update("|$w|$h|${LocalDate.now().toEpochDay()}|$VERSION".toByteArray())
        val d = md.digest()
        val sb = StringBuilder(20)
        for (i in 0 until 10) {
            val b = d[i].toInt() and 0xFF
            sb.append(Character.forDigit(b ushr 4, 16)).append(Character.forDigit(b and 0xF, 16))
        }
        return sb.toString()
    }

    /** "lastModified:size" when cheaply known (file path or a provider exposing the columns), else "". */
    fun imageStamp(ctx: Context, uri: String?): String {
        if (uri.isNullOrBlank()) return ""
        return runCatching {
            val parsed = Uri.parse(uri)
            when (parsed.scheme) {
                null, "", "file" -> File(parsed.path ?: uri).takeIf { it.isFile }?.let { "${it.lastModified()}:${it.length()}" }
                "content" -> ctx.contentResolver.query(
                    parsed,
                    arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, OpenableColumns.SIZE),
                    null, null, null
                )?.use { c ->
                    if (!c.moveToFirst()) null else {
                        val lm = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getLong(it) }
                        val sz = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getLong(it) }
                        "${lm ?: ""}:${sz ?: ""}"
                    }
                }
                else -> null
            }
        }.getOrNull().orEmpty()
    }

    private fun base(id: String, key: String) = "${safeId(id)}$SEP$key"

    /** Decodes straight into GPU memory: no Java-heap copy, no HWUI upload on first draw. */
    fun load(ctx: Context, id: String, key: String): Entry? {
        val d = dir(ctx)
        val b = base(id, key)
        val pf = File(d, b + PREVIEW_EXT)
        val af = File(d, b + AMBIENT_EXT)
        if (!pf.isFile || !af.isFile) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        val preview = BitmapFactory.decodeFile(pf.path, opts) ?: return null
        val ambient = BitmapFactory.decodeFile(af.path, opts)
        if (ambient == null) {
            preview.recycle()
            return null
        }
        return Entry(preview, ambient)
    }

    fun save(ctx: Context, id: String, key: String, preview: Bitmap, ambient: Bitmap) {
        val d = dir(ctx)
        val sid = safeId(id)
        val b = base(id, key)
        d.listFiles { f -> f.name.startsWith(sid + SEP) && !f.name.startsWith(b) }?.forEach { it.delete() }
        write(File(d, b + AMBIENT_EXT), ambient, lossless = false)
        write(File(d, b + PREVIEW_EXT), preview, lossless = preview.hasAlpha())
    }

    private fun write(target: File, bmp: Bitmap, lossless: Boolean) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        val ok = tmp.outputStream().buffered().use { out ->
            bmp.compress(
                if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY,
                if (lossless) 60 else 88,
                out
            )
        }
        if (ok) tmp.renameTo(target) else tmp.delete()
    }

    /** Deletes entries of projects that no longer exist. */
    fun retainOnly(ctx: Context, activeIds: Set<String>) {
        val keep = activeIds.mapTo(HashSet()) { safeId(it) }
        dir(ctx).listFiles()?.forEach { f ->
            val sid = f.name.substringBefore(SEP, missingDelimiterValue = "")
            if (sid.isEmpty() || sid !in keep) f.delete()
        }
    }
}
