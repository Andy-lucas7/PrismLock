package com.example.prismlock.engine

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.example.prismlock.R
import com.example.prismlock.model.Align
import com.example.prismlock.model.LayerNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class FontAxis(val tag: String, val min: Float, val def: Float, val max: Float)

object FontManager {
    const val SAMSUNG_SYSTEM_FONT_ID = "system:samsung_sharp"

    data class BundledFontSpec(val id: String, val label: String, val resId: Int, val fileName: String)

    val bundledFonts = listOf(
        BundledFontSpec("res:outfit", "Outfit", R.font.outfit, "outfit.ttf"),
        BundledFontSpec("res:inter", "Inter", R.font.inter, "inter.ttf"),
        BundledFontSpec("res:space_grotesk", "Space Grotesk", R.font.space_grotesk, "space_grotesk.ttf"),
        BundledFontSpec("res:bebas_neue", "Bebas Neue", R.font.bebas_neue, "bebas_neue.ttf"),
        BundledFontSpec("res:playfair_display", "Playfair Display", R.font.playfair_display, "playfair_display.ttf"),
        BundledFontSpec("res:jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono, "jetbrains_mono.ttf"),
        BundledFontSpec("res:syne", "Syne", R.font.syne, "syne.ttf"),
        BundledFontSpec("res:plus_jakarta_sans", "Plus Jakarta Sans", R.font.plus_jakarta_sans, "plus_jakarta_sans.ttf")
    )

    val systemFamilies = listOf("sans-serif", "sans-serif-light", "sans-serif-condensed", "sans-serif-medium", "serif", "monospace", "cursive", "casual", "serif-monospace")
    private val tfCache = HashMap<String, Typeface>()
    private val composeFamilyCache = HashMap<String, ComposeFontFamily>()
    private val axesCache = HashMap<String, List<FontAxis>>()
    private val familyNameCache = HashMap<String, String>()
    private val samsungFontFiles = ArrayList<File>()
    @Volatile private var scannedSamsung = false
    private var appCtx: Context? = null
    private lateinit var dir: File
    private lateinit var customDir: File
    private lateinit var bundledDir: File

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (!::dir.isInitialized) dir = File(ctx.applicationContext.filesDir, "fonts").apply { mkdirs() }
        if (!::customDir.isInitialized) customDir = File(ctx.applicationContext.filesDir, "custom_fonts").apply { mkdirs() }
        if (!::bundledDir.isInitialized) bundledDir = File(ctx.applicationContext.filesDir, "bundled_fonts").apply { mkdirs() }
        scanSamsungFontsOnce()
    }

    fun customFontsDir(ctx: Context): File {
        init(ctx)
        return customDir
    }

    suspend fun prewarm(ctx: Context) = withContext(Dispatchers.IO) {
        init(ctx)
        availableFonts(ctx).forEach { f ->
            label(f)
            typeface(f, 400)
            axes(f)
        }
    }

    private fun scanSamsungFontsOnce() {
        if (scannedSamsung) return
        synchronized(samsungFontFiles) {
            if (scannedSamsung) return
            val dirs = listOf(
                File("/system/fonts"),
                File("/product/fonts"),
                File("/system/product/fonts")
            )
            val candidates = ArrayList<Pair<Int, File>>()
            for (d in dirs) {
                val files = d.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile || !f.canRead() || f.length() < 4096L) continue
                    val n = f.name
                    val low = n.lowercase()
                    if (!low.endsWith(".ttf") && !low.endsWith(".otf")) continue
                    if ("emoji" in low || "color" in low) continue
                    val priority = when {
                        low.startsWith("samsungsharpsans") -> 0
                        low.startsWith("oneuisans") -> 1
                        low.startsWith("sec") -> 2
                        else -> -1
                    }
                    if (priority >= 0 && runCatching { Typeface.Builder(f).build() }.getOrNull() != null) {
                        candidates.add(priority to f)
                    }
                }
            }
            samsungFontFiles.clear()
            samsungFontFiles.addAll(
                candidates.sortedWith(compareBy<Pair<Int, File>> { it.first }.thenBy { it.second.name.length })
                    .map { it.second }
            )
            scannedSamsung = true
        }
    }

    fun hasNativeSamsungFont(): Boolean {
        scanSamsungFontsOnce()
        return samsungFontFiles.isNotEmpty()
    }

    private fun pickSamsungFile(weight: Int = 400): File? {
        scanSamsungFontsOnce()
        if (samsungFontFiles.isEmpty()) return null
        val want = when {
            weight >= 650 -> listOf("bold", "vf", "regular")
            weight >= 500 -> listOf("medium", "vf", "regular")
            weight <= 320 -> listOf("light", "vf", "regular")
            else -> listOf("regular", "vf", "medium")
        }
        for (tag in want) {
            samsungFontFiles.firstOrNull { tag in it.name.lowercase() }?.let { return it }
        }
        return samsungFontFiles.firstOrNull()
    }

    private fun bundledFile(font: String): File? {
        val spec = bundledFonts.firstOrNull { it.id == font } ?: return null
        val ctx = appCtx ?: return null
        if (!::bundledDir.isInitialized) bundledDir = File(ctx.filesDir, "bundled_fonts").apply { mkdirs() }
        val dst = File(bundledDir, spec.fileName)
        if (!dst.exists() || dst.length() < 1024L) {
            runCatching {
                ctx.resources.openRawResource(spec.resId).use { input ->
                    dst.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
        return dst.takeIf { it.exists() && it.length() >= 1024L }
    }

    fun availableFonts(ctx: Context): List<String> {
        init(ctx)
        val out = ArrayList<String>(32)
        if (hasNativeSamsungFont()) out += SAMSUNG_SYSTEM_FONT_ID
        bundledFonts.forEach { out += it.id }
        out += customSynced(ctx)
        out += imported(ctx)
        out += systemFamilies
        return out
    }

    fun customSynced(ctx: Context): List<String> {
        init(ctx)
        return customDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true)) }
            ?.sortedBy { label("custom:" + it.name).lowercase() }
            ?.map { "custom:" + it.name }
            ?: emptyList()
    }

    fun imported(ctx: Context): List<String> {
        init(ctx)
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.map { "file:" + it.name }
            ?.sorted()
            ?: emptyList()
    }

    suspend fun syncFontFolder(ctx: Context, treeUriStr: String?): Int = withContext(Dispatchers.IO) {
        init(ctx)
        if (treeUriStr.isNullOrBlank()) return@withContext 0
        val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return@withContext 0
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withContext 0

        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootDocId to 0)
        var syncedCount = 0

        while (queue.isNotEmpty()) {
            val (docId, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            runCatching {
                ctx.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val srcSize = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 2) queue.add(childId to (depth + 1))
                            continue
                        }
                        val lower = displayName.lowercase()
                        val isFontFile = lower.endsWith(".ttf") || lower.endsWith(".otf") ||
                            mime.contains("font", ignoreCase = true)
                        if (!isFontFile || displayName.isBlank()) continue

                        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        val dst = File(customDir, safeName)
                        val needsCopy = !dst.exists() || (srcSize > 0L && dst.length() != srcSize)
                        if (needsCopy) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val copied = runCatching {
                                ctx.contentResolver.openInputStream(fileUri)?.use { input ->
                                    dst.outputStream().use { out -> input.copyTo(out) }
                                }
                                Typeface.Builder(dst).build() ?: error("invalid font")
                            }.isSuccess
                            if (copied) {
                                val key = "custom:$safeName"
                                synchronized(tfCache) {
                                    tfCache.keys.removeAll { it.startsWith("$key|") }
                                    composeFamilyCache.keys.removeAll { it.startsWith("$key|") }
                                }
                                synchronized(axesCache) { axesCache.remove(key) }
                                synchronized(familyNameCache) { familyNameCache.remove(key) }
                                syncedCount++
                            } else {
                                dst.delete()
                            }
                        } else {
                            syncedCount++
                        }
                    }
                }
            }
        }
        syncedCount
    }

    fun import(ctx: Context, uri: Uri): String? = runCatching {
        init(ctx)
        var name = "font_${System.currentTimeMillis()}.ttf"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0).replace(Regex("[^A-Za-z0-9._-]"), "_") }
        val f = File(dir, name)
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        Typeface.Builder(f).build() ?: error("invalid")
        val id = "file:$name"
        synchronized(familyNameCache) { familyNameCache.remove(id) }
        id
    }.getOrNull()

    fun file(font: String): File? = when {
        font == SAMSUNG_SYSTEM_FONT_ID -> pickSamsungFile(400) ?: bundledFile("res:outfit")
        font.startsWith("res:") -> bundledFile(font)
        font.startsWith("custom:") && ::customDir.isInitialized -> File(customDir, font.removePrefix("custom:")).takeIf { it.exists() }
        font.startsWith("file:") && ::dir.isInitialized -> File(dir, font.removePrefix("file:")).takeIf { it.exists() }
        else -> null
    }

    fun label(font: String): String = when {
        font == SAMSUNG_SYSTEM_FONT_ID -> if (hasNativeSamsungFont()) "Sistema (Samsung Sharp Sans)" else "Outfit (Geométrica)"
        font.startsWith("res:") -> bundledFonts.firstOrNull { it.id == font }?.label ?: font.removePrefix("res:")
        font.startsWith("custom:") || font.startsWith("file:") -> {
            synchronized(familyNameCache) {
                familyNameCache.getOrPut(font) {
                    val f = file(font)
                    val parsed = f?.let { runCatching { readFamilyName(it) }.getOrNull() }?.takeIf { it.isNotBlank() }
                    parsed ?: font.removePrefix("custom:").removePrefix("file:")
                        .replace(Regex("\\.(ttf|otf)$", RegexOption.IGNORE_CASE), "")
                        .replace('_', ' ')
                        .replace('-', ' ')
                        .trim()
                }
            }
        }
        else -> font
    }

    fun composeFontFamily(font: String, weight: Int = 400): ComposeFontFamily = synchronized(composeFamilyCache) {
        composeFamilyCache.getOrPut("$font|$weight") {
            ComposeFontFamily(typeface(font, weight))
        }
    }

    fun typeface(font: String, weight: Int): Typeface = synchronized(tfCache) {
        tfCache.getOrPut("$font|$weight") {
            val resolvedFile = if (font == SAMSUNG_SYSTEM_FONT_ID) {
                pickSamsungFile(weight) ?: bundledFile("res:outfit")
            } else {
                file(font)
            }
            if (resolvedFile != null) {
                val base = runCatching { Typeface.Builder(resolvedFile).build() }.getOrNull() ?: Typeface.DEFAULT
                if (axes(font).any { it.tag == "wght" }) base else Typeface.create(base, weight.coerceIn(1, 1000), false)
            } else {
                Typeface.create(Typeface.create(font, Typeface.NORMAL), weight.coerceIn(1, 1000), false)
            }
        }
    }

    fun axes(font: String): List<FontAxis> = synchronized(axesCache) {
        axesCache.getOrPut(font) { file(font)?.let { runCatching { readFvar(it) }.getOrNull() } ?: emptyList() }
    }

    /**
     * Parses OpenType 'name' table to extract real Typographic Family Name (nameID 16)
     * or Font Family Name (nameID 1).
     */
    private fun readFamilyName(f: File): String? = RandomAccessFile(f, "r").use { r ->
        var base = 0L
        r.seek(0)
        val tagBytes = ByteArray(4)
        r.readFully(tagBytes)
        if (String(tagBytes) == "ttcf") {
            r.seek(12)
            base = r.readInt().toLong() and 0xFFFFFFFFL
        }
        r.seek(base + 4)
        val n = r.readUnsignedShort()
        var nameOff = -1L
        for (i in 0 until n) {
            r.seek(base + 12 + i * 16L)
            r.readFully(tagBytes)
            r.readInt()
            val o = r.readInt().toLong() and 0xFFFFFFFFL
            if (String(tagBytes) == "name") {
                nameOff = o
                break
            }
        }
        if (nameOff < 0) return null
        r.seek(nameOff)
        r.readUnsignedShort() // format
        val count = r.readUnsignedShort()
        val stringOffset = r.readUnsignedShort()
        val storageStart = nameOff + stringOffset

        var bestName16: String? = null
        var bestName1: String? = null
        for (i in 0 until count) {
            r.seek(nameOff + 6 + i * 12L)
            val platformId = r.readUnsignedShort()
            r.readUnsignedShort() // encodingId
            val languageId = r.readUnsignedShort()
            val nameId = r.readUnsignedShort()
            val len = r.readUnsignedShort()
            val off = r.readUnsignedShort()
            if (nameId != 16 && nameId != 1) continue
            if (len <= 0 || len > 512) continue

            val buf = ByteArray(len)
            r.seek(storageStart + off)
            r.readFully(buf)
            val decoded = when (platformId) {
                3, 0 -> String(buf, Charsets.UTF_16BE)
                1 -> String(buf, Charsets.ISO_8859_1)
                else -> continue
            }.trim().replace("\u0000", "")
            if (decoded.isBlank()) continue

            val isEnglishWin = platformId == 3 && languageId == 0x0409
            if (nameId == 16) {
                if (bestName16 == null || isEnglishWin) bestName16 = decoded
            } else {
                if (bestName1 == null || isEnglishWin) bestName1 = decoded
            }
        }
        bestName16 ?: bestName1
    }

    /**
     * OpenType binary layout:
     *  Offset table: sfntVersion u32 | numTables u16 | searchRange u16 | entrySelector u16 | rangeShift u16
     *  Table record (16B): tag[4] | checksum u32 | offset u32 | length u32
     *  fvar header: major u16 | minor u16 | axesArrayOffset u16 | reserved u16 | axisCount u16 | axisSize u16 | ...
     *  VariationAxisRecord: tag[4] | min Fixed16.16 | default Fixed16.16 | max Fixed16.16 | flags u16 | nameID u16
     */
    private fun readFvar(f: File): List<FontAxis> = RandomAccessFile(f, "r").use { r ->
        var base = 0L
        r.seek(0)
        val tagBytes = ByteArray(4)
        r.readFully(tagBytes)
        if (String(tagBytes) == "ttcf") { r.seek(12); base = r.readInt().toLong() and 0xFFFFFFFFL }
        r.seek(base + 4)
        val n = r.readUnsignedShort()
        var off = -1L
        for (i in 0 until n) {
            r.seek(base + 12 + i * 16L)
            r.readFully(tagBytes)
            r.readInt()
            val o = r.readInt().toLong() and 0xFFFFFFFFL
            if (String(tagBytes) == "fvar") { off = o; break }
        }
        if (off < 0) return emptyList()
        r.seek(off + 4)
        val axesOff = r.readUnsignedShort()
        r.readUnsignedShort()
        val count = r.readUnsignedShort()
        val axisSize = r.readUnsignedShort()
        (0 until count).map { i ->
            r.seek(off + axesOff + i.toLong() * axisSize)
            r.readFully(tagBytes)
            val mn = r.readInt() / 65536f; val df = r.readInt() / 65536f; val mx = r.readInt() / 65536f
            FontAxis(String(tagBytes), mn, df, mx)
        }
    }

    fun variation(node: LayerNode): String {
        val ax = axes(node.font)
        if (ax.isEmpty()) return ""
        val m = LinkedHashMap<String, Float>()
        ax.forEach { m[it.tag] = node.axes[it.tag] ?: if (it.tag == "wght") node.weight.toFloat().coerceIn(it.min, it.max) else it.def }
        return m.entries.joinToString(", ") { "'${it.key}' ${it.value}" }
    }
}

class TextShape(val path: Path, val width: Float, val height: Float, val baselineShiftY: Float = 0f)

class BakedGlyph(
    val ch: Char,
    val path: Path,
    val advance: Float,
    val bounds: RectF
)

class GlyphAtlas(
    val atlasKey: String,
    val glyphs: Map<Char, BakedGlyph>,
    val spaceAdvance: Float,
    val lineAdvance: Float,
    val letterSpacingPx: Float,
    val align: Align
)

object StemStretch {
    private const val EM = 1000f
    private const val ATLAS_CHARS = "0123456789:"
    private val cache = LruCache<String, TextShape>(192)
    private val atlasCache = LruCache<String, GlyphAtlas>(24)

    fun atlasKey(node: LayerNode, u: Float): String {
        val targetPx = (node.size * u).coerceAtLeast(1f)
        val stretchY = node.stretch.coerceIn(1f, 3f)
        val comp = node.strokeCompensation.coerceIn(0f, 1.2f)
        return "${node.font}|${node.weight}|${node.axes}|$targetPx|${node.letterSpacing}|${node.lineSpacing}|${node.align}|$comp|$stretchY"
    }

    fun getOrBakeAtlas(node: LayerNode, u: Float): GlyphAtlas {
        val aKey = atlasKey(node, u)
        atlasCache.get(aKey)?.let { return it }

        val targetPx = (node.size * u).coerceAtLeast(1f)
        val stretchY = node.stretch.coerceIn(1f, 3f)
        val comp = node.strokeCompensation.coerceIn(0f, 1.2f)
        val scale = targetPx / EM

        val workPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        configure(workPaint, node, EM)
        val fm = workPaint.fontMetrics
        val baseline = -fm.ascent
        val b = RectF()
        val baseStrokeEm = measureBaseStrokeWidth(workPaint, b)
        val scaleM = Matrix().apply { setScale(scale, scale) }

        val map = HashMap<Char, BakedGlyph>(16)
        var maxStretchedExtra = 0f
        for (ch in ATLAS_CHARS) {
            val s = ch.toString()
            val adv = workPaint.measureText(s) * scale
            val raw = Path().apply { fillType = Path.FillType.WINDING }
            workPaint.getTextPath(s, 0, 1, 0f, baseline, raw)
            val finalPath = if (stretchY <= 1.01f || raw.isEmpty) {
                raw
            } else {
                raw.computeBounds(b, true)
                val origH = (b.bottom - b.top).coerceAtLeast(1f)
                val st = stretchContinuousWithNormalComp(raw, b.top, origH, stretchY, baseStrokeEm, comp)
                st.computeBounds(b, true)
                maxStretchedExtra = maxOf(maxStretchedExtra, (b.height() - origH).coerceAtLeast(0f))
                st
            }
            finalPath.transform(scaleM)
            val gb = RectF()
            finalPath.computeBounds(gb, true)
            map[ch] = BakedGlyph(ch, finalPath, adv, gb)
        }

        val spaceAdv = workPaint.measureText(" ") * scale
        val lineAdv = ((fm.descent - fm.ascent) + node.lineSpacing * EM + maxStretchedExtra) * scale
        val letterSpPx = node.letterSpacing * targetPx
        val atlas = GlyphAtlas(aKey, map, spaceAdv, lineAdv, letterSpPx, node.align)
        atlasCache.put(aKey, atlas)
        return atlas
    }

    private fun tryAssembleFromAtlas(text: String, node: LayerNode, u: Float): TextShape? {
        if (text.isEmpty()) return null
        for (i in text.indices) {
            val c = text[i]
            if (c != ' ' && c != '\n' && ATLAS_CHARS.indexOf(c) < 0) return null
        }
        val atlas = atlasCache.get(atlasKey(node, u)) ?: return null
        val lines = text.split('\n')
        val lineWidths = FloatArray(lines.size)
        var maxW = 0f
        for ((i, line) in lines.withIndex()) {
            var w = 0f
            for ((j, c) in line.withIndex()) {
                w += if (c == ' ') atlas.spaceAdvance else (atlas.glyphs[c]?.advance ?: 0f)
                if (j < line.lastIndex) w += atlas.letterSpacingPx
            }
            lineWidths[i] = w
            if (w > maxW) maxW = w
        }

        val out = Path().apply { fillType = Path.FillType.WINDING }
        val tmp = Path()
        var yOff = 0f
        for ((i, line) in lines.withIndex()) {
            var xOff = when (atlas.align) {
                Align.START -> 0f
                Align.CENTER -> (maxW - lineWidths[i]) * 0.5f
                Align.END -> maxW - lineWidths[i]
            }
            for ((j, c) in line.withIndex()) {
                if (c == ' ') {
                    xOff += atlas.spaceAdvance
                } else {
                    val g = atlas.glyphs[c]
                    if (g != null && !g.path.isEmpty) {
                        tmp.set(g.path)
                        tmp.offset(xOff, yOff)
                        out.addPath(tmp)
                        xOff += g.advance
                    }
                }
                if (j < line.lastIndex) xOff += atlas.letterSpacingPx
            }
            yOff += atlas.lineAdvance
        }
        val b = RectF()
        if (out.isEmpty) return TextShape(out, 0f, 0f, 0f)
        out.computeBounds(b, true)
        val shiftY = -b.top
        out.offset(0f, shiftY)
        return TextShape(out, maxW.coerceAtLeast(b.right), b.height(), shiftY)
    }

    fun configure(p: Paint, node: LayerNode, textPx: Float) {
        p.isAntiAlias = true
        p.isSubpixelText = true
        p.typeface = FontManager.typeface(node.font, node.weight)
        p.textSize = textPx
        p.letterSpacing = node.letterSpacing
        val v = FontManager.variation(node)
        if (p.fontVariationSettings != v.ifEmpty { null }) p.fontVariationSettings = v.ifEmpty { null }
    }

    fun shape(text: String, node: LayerNode, u: Float, paint: Paint = Paint()): TextShape {
        val targetPx = (node.size * u).coerceAtLeast(1f)
        val stretchY = node.stretch.coerceIn(1f, 3f)
        val comp = node.strokeCompensation.coerceIn(0f, 1.2f)
        val fontKey = "${node.font}|${node.weight}|${node.axes}|$targetPx|${node.letterSpacing}|${node.lineSpacing}|${node.align}|$comp"
        val key = "$fontKey|$text|$stretchY"
        cache.get(key)?.let { return it }

        tryAssembleFromAtlas(text, node, u)?.let { assembled ->
            cache.put(key, assembled)
            return assembled
        }

        val workPaint = Paint(paint)
        val tmp = Path()
        val b = RectF()
        val scaleM = Matrix()

        configure(workPaint, node, EM)
        val fm = workPaint.fontMetrics
        val lines = text.split('\n')
        val out = Path().apply { fillType = Path.FillType.WINDING }
        val widths = lines.map { workPaint.measureText(it) }
        val maxW = widths.maxOrNull() ?: 0f
        var baseline = -fm.ascent
        val lineAdvance = (fm.descent - fm.ascent) + node.lineSpacing * EM
        val baseStrokeEm = measureBaseStrokeWidth(workPaint, b)

        for ((i, line) in lines.withIndex()) {
            val dx = when (node.align) {
                Align.START -> 0f
                Align.CENTER -> (maxW - widths[i]) / 2f
                Align.END -> maxW - widths[i]
            }
            tmp.reset()
            tmp.fillType = Path.FillType.WINDING
            if (line.isNotEmpty()) workPaint.getTextPath(line, 0, line.length, dx, baseline, tmp)
            if (stretchY <= 1.01f || tmp.isEmpty) {
                out.addPath(tmp)
                baseline += lineAdvance
            } else {
                tmp.computeBounds(b, true)
                val origH = (b.bottom - b.top).coerceAtLeast(1f)
                val stretched = stretchContinuousWithNormalComp(tmp, b.top, origH, stretchY, baseStrokeEm, comp)
                stretched.computeBounds(b, true)
                out.addPath(stretched)
                baseline += lineAdvance + (b.height() - origH).coerceAtLeast(0f)
            }
        }

        val scale = targetPx / EM
        val res = if (out.isEmpty) TextShape(out, 0f, 0f, 0f) else {
            scaleM.setScale(scale, scale)
            out.transform(scaleM)
            out.computeBounds(b, true)
            val shiftY = -b.top
            out.offset(0f, shiftY)
            TextShape(out, (maxW * scale).coerceAtLeast(b.right), b.height(), shiftY)
        }
        cache.put(key, res)
        return res
    }

    private fun measureBaseStrokeWidth(paint: Paint, b: RectF): Float {
        val probe = Path()
        val sample = if (paint.hasGlyph("I")) "I" else "1"
        paint.getTextPath(sample, 0, sample.length, 0f, 0f, probe)
        if (!probe.isEmpty) {
            probe.computeBounds(b, true)
            if (b.width() > 1f) return b.width()
        }
        return paint.textSize * 0.11f
    }

    fun measureStrokeWidthPx(node: LayerNode, u: Float): Float {
        val targetPx = (node.size * u).coerceAtLeast(1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        configure(p, node, EM)
        val b = RectF()
        return measureBaseStrokeWidth(p, b) * (targetPx / EM)
    }

    /**
     * Continuous vertical stretch with weighted C-infinity stem elongation and circular-smoothed
     * normal-based stroke compensation with corner/tip protection.
     */
    private fun stretchContinuousWithNormalComp(
        rawPath: Path,
        top: Float,
        origH: Float,
        stretchY: Float,
        baseStrokeWidth: Float,
        strokeCompensation: Float
    ): Path {
        val outPath = Path().apply { fillType = rawPath.fillType }
        val rawBounds = RectF()
        rawPath.computeBounds(rawBounds, true)
        val clipRect = Rect(
            rawBounds.left.toInt() - 8,
            rawBounds.top.toInt() - 8,
            rawBounds.right.toInt() + 8,
            rawBounds.bottom.toInt() + 8
        )
        val region = Region()
        region.setPath(rawPath, Region(clipRect))

        val pm = PathMeasure(rawPath, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val step = 1.0f
        val safeOrigH = origH.coerceAtLeast(1f)
        val halfStroke = baseStrokeWidth * 0.5f
        val maxSafeOffset = baseStrokeWidth * 0.38f
        val alpha = 0.34f
        val twoPiF = (2.0 * Math.PI).toFloat()

        fun stretchYWeighted(y: Float): Float {
            val u = ((y - top) / safeOrigH).coerceIn(0f, 1f)
            val twoPiU = twoPiF * u
            val wIntegral = u - (alpha / twoPiF) * sin(twoPiU)
            return top + safeOrigH * (u + (stretchY - 1f) * wIntegral)
        }

        do {
            val len = pm.length
            if (len <= 0.5f) continue
            val pts = ArrayList<FloatArray>((len / step).toInt() + 4)
            var dist = 0f
            var cMinX = Float.MAX_VALUE
            var cMaxX = -Float.MAX_VALUE
            var cMinY = Float.MAX_VALUE
            var cMaxY = -Float.MAX_VALUE
            while (dist < len) {
                if (pm.getPosTan(dist, pos, tan)) {
                    val px = pos[0]
                    val py = pos[1]
                    if (px < cMinX) cMinX = px
                    if (px > cMaxX) cMaxX = px
                    if (py < cMinY) cMinY = py
                    if (py > cMaxY) cMaxY = py
                    pts.add(floatArrayOf(px, py, tan[0], tan[1]))
                }
                dist += step
            }
            val n = pts.size
            if (n < 3) continue
            fun wrap(idx: Int): Int = ((idx % n) + n) % n

            // Protect small punctuation contours (e.g., ':' and '.'): stretch gently around center with zero thinning offset
            val cW = cMaxX - cMinX
            val cH = cMaxY - cMinY
            if (cH <= baseStrokeWidth * 1.85f && cW <= baseStrokeWidth * 1.85f) {
                val cMidY = (cMinY + cMaxY) * 0.5f
                val midStretched = stretchYWeighted(cMidY)
                val dotStretch = 1f + (stretchY - 1f) * 0.35f
                for (i in 0 until n) {
                    val p = pts[i]
                    val xFinal = p[0]
                    val yFinal = midStretched + (p[1] - cMidY) * dotStretch
                    if (i == 0) outPath.moveTo(xFinal, yFinal) else outPath.lineTo(xFinal, yFinal)
                }
                outPath.close()
                continue
            }

            var outVotes = 0
            val probeStep = (n / 16).coerceAtLeast(1)
            for (i in 0 until n step probeStep) {
                val p = pts[i]
                val candNx = p[3]
                val candNy = -p[2]
                val outHit = region.contains((p[0] + candNx * 1.5f).roundToInt(), (p[1] + candNy * 1.5f).roundToInt())
                val inHit = region.contains((p[0] - candNx * 1.5f).roundToInt(), (p[1] - candNy * 1.5f).roundToInt())
                if (!outHit && inHit) outVotes++ else if (outHit && !inHit) outVotes--
            }
            val outSign = if (outVotes != 0) {
                if (outVotes > 0) 1f else -1f
            } else {
                var area2 = 0.0
                for (i in 0 until n) {
                    val p0 = pts[i]
                    val p1 = pts[(i + 1) % n]
                    area2 += p0[0].toDouble() * p1[1] - p1[0].toDouble() * p0[1]
                }
                if (area2 >= 0.0) 1f else -1f
            }

            // Classify contiguous horizontal lobes uniformly to preserve crisp stem tips and corners
            val nxArr = FloatArray(n)
            val nyArr = FloatArray(n)
            val isHoriz = BooleanArray(n)
            val lobeFactor = FloatArray(n) { 1f }
            for (i in 0 until n) {
                val p = pts[i]
                val nx = p[3] * outSign
                val ny = -p[2] * outSign
                nxArr[i] = nx
                nyArr[i] = ny
                isHoriz[i] = abs(ny) > 0.20f
            }

            val firstNonHoriz = (0 until n).firstOrNull { !isHoriz[it] } ?: -1
            if (firstNonHoriz < 0) {
                val midP = pts[n / 2]
                val dirY = if (nyArr[n / 2] > 0f) -1f else 1f
                val probeDist = baseStrokeWidth * 1.45f
                if ((cMaxX - cMinX) <= baseStrokeWidth * 1.38f &&
                    region.contains(midP[0].roundToInt(), (midP[1] + dirY * probeDist).roundToInt())
                ) {
                    lobeFactor.fill(0f)
                }
            } else {
                val run = ArrayList<Int>(64)
                var offset = 0
                while (offset < n) {
                    val idx = wrap(firstNonHoriz + offset)
                    if (!isHoriz[idx]) {
                        offset++
                        continue
                    }
                    run.clear()
                    var minX = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    while (offset < n) {
                        val cur = wrap(firstNonHoriz + offset)
                        if (!isHoriz[cur]) break
                        run.add(cur)
                        val px = pts[cur][0]
                        if (px < minX) minX = px
                        if (px > maxX) maxX = px
                        offset++
                    }
                    val lobeWidthX = maxX - minX
                    val midIdx = run[run.size / 2]
                    val midP = pts[midIdx]
                    val dirY = if (nyArr[midIdx] > 0f) -1f else 1f
                    val probeDist = baseStrokeWidth * 1.45f
                    if (lobeWidthX <= baseStrokeWidth * 1.38f &&
                        region.contains(midP[0].roundToInt(), (midP[1] + dirY * probeDist).roundToInt())
                    ) {
                        for (k in run) lobeFactor[k] = 0f
                    }
                }
            }

            for (i in 0 until n) {
                val p = pts[i]
                val x = p[0]
                val y = p[1]
                val nx = nxArr[i]
                val ny = nyArr[i]

                val u = ((y - top) / safeOrigH).coerceIn(0f, 1f)
                val twoPiU = twoPiF * u
                val wIntegral = u - (alpha / twoPiF) * sin(twoPiU)
                val yStretched = top + safeOrigH * (u + (stretchY - 1f) * wIntegral)
                val localStretchY = 1f + (stretchY - 1f) * (1f - alpha * cos(twoPiU))

                val denom = sqrt(nx * nx + (ny * localStretchY) * (ny * localStretchY)).coerceAtLeast(0.0001f)
                val rawOffset = halfStroke * strokeCompensation * (1.0f - (1.0f / denom))
                val clampedOffset = (rawOffset * lobeFactor[i]).coerceIn(0f, maxSafeOffset)

                val xFinal = x
                val yFinal = yStretched - ny * clampedOffset
                if (i == 0) outPath.moveTo(xFinal, yFinal) else outPath.lineTo(xFinal, yFinal)
            }
            outPath.close()
        } while (pm.nextContour())

        return outPath
    }
}
