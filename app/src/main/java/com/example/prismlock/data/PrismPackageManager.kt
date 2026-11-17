package com.example.prismlock.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.example.prismlock.engine.FontManager
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.Project
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.flatten
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class PackagedMediaItem(
    val entryPath: String,
    val originalKey: String = "",
    val depthEnabled: Boolean = true
)

@Serializable
data class PrismBundleManifest(
    val version: Int = 1,
    val title: String = "Default Screen",
    val hasFolder: Boolean = false,
    val media: List<PackagedMediaItem> = emptyList(),
    val fontMap: Map<String, String> = emptyMap()
)

object PrismPackageManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun sanitizeArchiveFileName(title: String): String =
        title.trim()
            .ifBlank { "Default Screen" }
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "Default_Screen" }

    suspend fun exportPackage(ctx: Context, project: Project, outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        FontManager.init(ctx)
        val cleanTitle = project.title.trim().ifBlank { "Default Screen" }

        runCatching {
            val usedFonts = LinkedHashMap<String, File>()
            val fontMap = LinkedHashMap<String, String>()
            for ((node, _) in project.layers.flatten()) {
                val fId = node.font
                if ((fId.startsWith("file:") || fId.startsWith("custom:")) && !usedFonts.containsKey(fId)) {
                    FontManager.file(fId)?.takeIf { it.isFile && it.canRead() }?.let { f ->
                        usedFonts[fId] = f
                        fontMap[fId] = "fonts/${f.name}"
                    }
                }
            }

            val mediaUris = if (!project.folderUri.isNullOrBlank()) {
                WallpaperCarousel.listImages(ctx, project.folderUri).map { it.toString() }
            } else if (!project.imageUri.isNullOrBlank()) {
                listOf(project.imageUri)
            } else {
                emptyList()
            }

            val packagedMedia = ArrayList<PackagedMediaItem>(mediaUris.size)

            ctx.contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                ZipOutputStream(rawOut).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED)

                    // 1. Fontes personalizadas usadas nas camadas
                    val writtenFontPaths = HashSet<String>()
                    for ((_, fontFile) in usedFonts) {
                        val entryPath = "fonts/${fontFile.name}"
                        if (writtenFontPaths.add(entryPath)) {
                            zip.putNextEntry(ZipEntry(entryPath))
                            fontFile.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }

                    // 2. Papéis de parede / vídeos externos puxados
                    mediaUris.forEachIndexed { idx, uriStr ->
                        val origKey = WallpaperCarousel.imageKey(ctx, uriStr)
                        val fileName = safeMediaName(ctx, uriStr, origKey, idx)
                        val entryPath = "assets/$fileName"
                        val stream = openInputStreamAny(ctx, uriStr)
                        if (stream != null) {
                            stream.use { input ->
                                zip.putNextEntry(ZipEntry(entryPath))
                                input.copyTo(zip)
                                zip.closeEntry()
                            }
                            packagedMedia += PackagedMediaItem(
                                entryPath = entryPath,
                                originalKey = origKey,
                                depthEnabled = project.isDepthEnabledFor(origKey)
                            )
                        }
                    }

                    // 3. Metadados do pacote + theme.json portátil
                    val bundle = PrismBundleManifest(
                        version = 1,
                        title = cleanTitle,
                        hasFolder = !project.folderUri.isNullOrBlank(),
                        media = packagedMedia,
                        fontMap = fontMap
                    )
                    zip.putNextEntry(ZipEntry("bundle.json"))
                    zip.write(json.encodeToString(PrismBundleManifest.serializer(), bundle).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    val portableProject = project.copy(
                        title = cleanTitle,
                        folderUri = null,
                        imageUri = null
                    )
                    zip.putNextEntry(ZipEntry("theme.json"))
                    zip.write(json.encodeToString(Project.serializer(), portableProject).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.finish()
                }
            } ?: error("Cannot open export stream")
            true
        }.getOrDefault(false)
    }

    suspend fun importFromDocument(ctx: Context, uri: Uri): Project? = withContext(Dispatchers.IO) {
        FontManager.init(ctx)
        runCatching {
            val rawStream = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
            BufferedInputStream(rawStream, 16 * 1024).use { bis ->
                bis.mark(8)
                val header = ByteArray(4)
                val read = bis.read(header)
                bis.reset()

                val isZip = read >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())

                if (!isZip) {
                    val rawJson = bis.bufferedReader().readText()
                    val imported = json.decodeFromString(Project.serializer(), rawJson)
                    return@withContext imported.copy(
                        id = UUID.randomUUID().toString(),
                        title = imported.title.trim().ifBlank { "Default Screen" },
                        layers = imported.layers.map { it.copyDeep() }
                    )
                }

                val newThemeId = UUID.randomUUID().toString()
                val themeDir = File(File(ctx.filesDir, "imported_themes").apply { mkdirs() }, newThemeId).apply { mkdirs() }
                val assetsDir = File(themeDir, "assets").apply { mkdirs() }
                val customFontsDir = FontManager.customFontsDir(ctx)

                var themeJsonText: String? = null
                var bundleManifest: PrismBundleManifest? = null
                val extractedFonts = HashMap<String, String>()
                val extractedAssets = LinkedHashMap<String, File>()

                ZipInputStream(bis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val rel = entry.name.replace('\\', '/').trimStart('/')
                            if (".." !in rel) {
                                val fileName = File(rel).name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                                when {
                                    (rel == "theme.json" || rel.endsWith("/theme.json")) || (themeJsonText == null && rel.endsWith(".json") && !rel.endsWith("bundle.json")) -> {
                                        themeJsonText = zis.readBytes().toString(Charsets.UTF_8)
                                    }
                                    rel == "bundle.json" || rel.endsWith("/bundle.json") -> {
                                        val bText = zis.readBytes().toString(Charsets.UTF_8)
                                        bundleManifest = runCatching { json.decodeFromString(PrismBundleManifest.serializer(), bText) }.getOrNull()
                                    }
                                    ("/fonts/" in "/$rel") || rel.endsWith(".ttf", true) || rel.endsWith(".otf", true) -> {
                                        val dst = File(customFontsDir, fileName)
                                        dst.outputStream().use { zis.copyTo(it) }
                                        if (runCatching { Typeface.Builder(dst).build() }.getOrNull() != null) {
                                            val fontId = "custom:$fileName"
                                            extractedFonts[rel] = fontId
                                            extractedFonts[fileName] = fontId
                                        } else {
                                            dst.delete()
                                        }
                                    }
                                    ("/assets/" in "/$rel") || isMediaFileName(fileName) -> {
                                        val dst = File(assetsDir, fileName)
                                        dst.outputStream().use { zis.copyTo(it) }
                                        extractedAssets[rel] = dst
                                        extractedAssets[fileName] = dst
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val parsedProject = themeJsonText?.let {
                    runCatching { json.decodeFromString(Project.serializer(), it) }.getOrNull()
                } ?: return@withContext null

                assembleImportedProject(
                    newThemeId = newThemeId,
                    baseProject = parsedProject,
                    bundleManifest = bundleManifest,
                    extractedFonts = extractedFonts,
                    extractedAssets = extractedAssets,
                    assetsDir = assetsDir
                )
            }
        }.getOrNull()
    }

    suspend fun importFromFolder(ctx: Context, treeUri: Uri): Project? = withContext(Dispatchers.IO) {
        FontManager.init(ctx)
        runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val newThemeId = UUID.randomUUID().toString()
            val themeDir = File(File(ctx.filesDir, "imported_themes").apply { mkdirs() }, newThemeId).apply { mkdirs() }
            val assetsDir = File(themeDir, "assets").apply { mkdirs() }
            val customFontsDir = FontManager.customFontsDir(ctx)

            var themeJsonText: String? = null
            var bundleManifest: PrismBundleManifest? = null
            val extractedFonts = HashMap<String, String>()
            val extractedAssets = LinkedHashMap<String, File>()

            val queue = ArrayDeque<Pair<String, String>>()
            queue.add(rootDocId to "")

            while (queue.isNotEmpty()) {
                val (docId, relPrefix) = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                ctx.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx) ?: continue
                        val mime = cursor.getString(mimeIdx) ?: ""
                        val rel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(childId to rel)
                            continue
                        }
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        when {
                            name == "theme.json" || (themeJsonText == null && name.endsWith(".json", true) && name != "bundle.json") -> {
                                themeJsonText = ctx.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
                            }
                            name == "bundle.json" -> {
                                val bText = ctx.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
                                if (bText != null) {
                                    bundleManifest = runCatching { json.decodeFromString(PrismBundleManifest.serializer(), bText) }.getOrNull()
                                }
                            }
                            ("/fonts/" in "/$rel") || name.endsWith(".ttf", true) || name.endsWith(".otf", true) -> {
                                val dst = File(customFontsDir, safeName)
                                ctx.contentResolver.openInputStream(fileUri)?.use { input ->
                                    dst.outputStream().use { output -> input.copyTo(output) }
                                }
                                if (runCatching { Typeface.Builder(dst).build() }.getOrNull() != null) {
                                    val fontId = "custom:$safeName"
                                    extractedFonts[rel] = fontId
                                    extractedFonts[safeName] = fontId
                                } else {
                                    dst.delete()
                                }
                            }
                            ("/assets/" in "/$rel") || mime.startsWith("image/") || mime.startsWith("video/") || isMediaFileName(name) -> {
                                val dst = File(assetsDir, safeName)
                                ctx.contentResolver.openInputStream(fileUri)?.use { input ->
                                    dst.outputStream().use { output -> input.copyTo(output) }
                                }
                                extractedAssets[rel] = dst
                                extractedAssets[safeName] = dst
                            }
                        }
                    }
                }
            }

            val parsedProject = themeJsonText?.let {
                runCatching { json.decodeFromString(Project.serializer(), it) }.getOrNull()
            } ?: return@withContext null

            assembleImportedProject(
                newThemeId = newThemeId,
                baseProject = parsedProject,
                bundleManifest = bundleManifest,
                extractedFonts = extractedFonts,
                extractedAssets = extractedAssets,
                assetsDir = assetsDir
            )
        }.getOrNull()
    }

    private fun assembleImportedProject(
        newThemeId: String,
        baseProject: Project,
        bundleManifest: PrismBundleManifest?,
        extractedFonts: Map<String, String>,
        extractedAssets: Map<String, File>,
        assetsDir: File
    ): Project {
        val remappedLayers = baseProject.layers.map { node ->
            remapLayerFonts(node.copyDeep(), bundleManifest?.fontMap.orEmpty(), extractedFonts)
        }

        val assetFiles = extractedAssets.values.distinctBy { it.absolutePath }.filter { it.isFile }.sortedBy { it.name.lowercase() }
        val newOverrides = LinkedHashMap<String, Boolean>(baseProject.depthOverrides)
        bundleManifest?.media?.forEach { item ->
            val target = extractedAssets[item.entryPath] ?: extractedAssets[File(item.entryPath).name]
            target?.let { dst ->
                newOverrides[dst.name] = item.depthEnabled
            }
        }

        val (restoredFolderUri, restoredImageUri) = when {
            assetFiles.size > 1 || (bundleManifest?.hasFolder == true && assetFiles.isNotEmpty()) -> {
                Uri.fromFile(assetsDir).toString() to null
            }
            assetFiles.size == 1 -> {
                null to Uri.fromFile(assetFiles.first()).toString()
            }
            else -> null to null
        }

        return baseProject.copy(
            id = newThemeId,
            title = (bundleManifest?.title ?: baseProject.title).trim().ifBlank { "Default Screen" },
            layers = remappedLayers,
            folderUri = restoredFolderUri,
            imageUri = restoredImageUri,
            depthOverrides = newOverrides
        )
    }

    private fun remapLayerFonts(
        node: LayerNode,
        bundleFontMap: Map<String, String>,
        extractedFonts: Map<String, String>
    ): LayerNode {
        val bundlePath = bundleFontMap[node.font]
        val mappedFont = when {
            bundlePath != null -> {
                extractedFonts[bundlePath]
                    ?: extractedFonts[File(bundlePath).name]
                    ?: node.font
            }
            node.font.startsWith("file:") -> {
                val fName = node.font.removePrefix("file:")
                extractedFonts["fonts/$fName"] ?: extractedFonts[fName] ?: node.font
            }
            node.font.startsWith("custom:") -> {
                val fName = node.font.removePrefix("custom:")
                extractedFonts["fonts/$fName"] ?: extractedFonts[fName] ?: node.font
            }
            else -> node.font
        }
        return node.copy(
            font = mappedFont,
            children = node.children.map { remapLayerFonts(it, bundleFontMap, extractedFonts) }
        )
    }

    private fun openInputStreamAny(ctx: Context, uriStr: String): InputStream? = runCatching {
        val uri = Uri.parse(uriStr)
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            File(uri.path!!).takeIf { it.isFile && it.canRead() }?.inputStream()
        } else {
            ctx.contentResolver.openInputStream(uri)
        }
    }.getOrNull()

    private fun safeMediaName(ctx: Context, uriStr: String, origKey: String, idx: Int): String {
        val cleanBase = origKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val extFromKey = File(cleanBase).extension.lowercase()
        val ext = if (extFromKey.isNotBlank()) {
            extFromKey
        } else {
            val mime = runCatching { ctx.contentResolver.getType(Uri.parse(uriStr)) }.getOrNull()
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
                ?: if (WallpaperCarousel.isVideoUri(ctx, uriStr)) "mp4" else "jpg"
        }
        val baseWithoutExt = cleanBase.substringBeforeLast('.', cleanBase).ifBlank { "media" }
        return "media_${idx}_${baseWithoutExt}.$ext"
    }

    private fun isMediaFileName(name: String): Boolean {
        val ext = File(name).extension.lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "mp4", "webm", "mkv", "mov", "m4v")
    }
}
