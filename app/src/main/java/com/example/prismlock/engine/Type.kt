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
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.prismlock.model.Align
import com.example.prismlock.model.LayerNode
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FontAxis(val tag: String, val min: Float, val def: Float, val max: Float)

object FontManager {
    val systemFamilies = listOf("sans-serif", "sans-serif-light", "sans-serif-condensed", "sans-serif-medium", "serif", "monospace", "cursive", "casual", "serif-monospace")
    private val tfCache = HashMap<String, Typeface>()
    private val axesCache = HashMap<String, List<FontAxis>>()
    private lateinit var dir: File

    fun init(ctx: Context) { if (!::dir.isInitialized) dir = File(ctx.applicationContext.filesDir, "fonts").apply { mkdirs() } }

    fun imported(ctx: Context): List<String> { init(ctx); return dir.listFiles()?.map { "file:" + it.name }?.sorted() ?: emptyList() }

    fun import(ctx: Context, uri: Uri): String? = runCatching {
        init(ctx)
        var name = "font_${System.currentTimeMillis()}.ttf"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0).replace(Regex("[^A-Za-z0-9._-]"), "_") }
        val f = File(dir, name)
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        Typeface.Builder(f).build() ?: error("invalid")
        "file:$name"
    }.getOrNull()

    fun file(font: String): File? = if (font.startsWith("file:") && ::dir.isInitialized) File(dir, font.removePrefix("file:")).takeIf { it.exists() } else null

    fun label(font: String) = font.removePrefix("file:")

    fun typeface(font: String, weight: Int): Typeface = synchronized(tfCache) {
        tfCache.getOrPut("$font|$weight") {
            val f = file(font)
            if (f != null) {
                val base = runCatching { Typeface.Builder(f).build() }.getOrNull() ?: Typeface.DEFAULT
                if (axes(font).any { it.tag == "wght" }) base else Typeface.create(base, weight.coerceIn(1, 1000), false)
            } else Typeface.create(Typeface.create(font, Typeface.NORMAL), weight.coerceIn(1, 1000), false)
        }
    }

    fun axes(font: String): List<FontAxis> = synchronized(axesCache) {
        axesCache.getOrPut(font) { file(font)?.let { runCatching { readFvar(it) }.getOrNull() } ?: emptyList() }
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

class TextShape(val path: Path, val width: Float, val height: Float)

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
                val st = stretchContinuousWithNormalComp(raw, b.top, stretchY, baseStrokeEm, comp)
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
        if (out.isEmpty) return TextShape(out, 0f, 0f)
        out.computeBounds(b, true)
        out.offset(0f, -b.top)
        return TextShape(out, maxW.coerceAtLeast(b.right), b.height())
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
                val stretched = stretchContinuousWithNormalComp(tmp, b.top, stretchY, baseStrokeEm, comp)
                stretched.computeBounds(b, true)
                out.addPath(stretched)
                baseline += lineAdvance + (b.height() - origH).coerceAtLeast(0f)
            }
        }

        val scale = targetPx / EM
        val res = if (out.isEmpty) TextShape(out, 0f, 0f) else {
            scaleM.setScale(scale, scale)
            out.transform(scaleM)
            out.computeBounds(b, true)
            out.offset(0f, -b.top)
            TextShape(out, (maxW * scale).coerceAtLeast(b.right), b.height())
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

    /**
     * Continuous vertical stretch with perpendicular normal-based stroke-thickness compensation:
     * Clamps offset to 0.36 * baseStrokeWidth to prevent stroke strangulation on curved digits (3, 6).
     */
    private fun stretchContinuousWithNormalComp(
        rawPath: Path,
        top: Float,
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
        val halfStroke = baseStrokeWidth * 0.5f
        val maxSafeOffset = baseStrokeWidth * 0.36f

        do {
            val len = pm.length
            if (len <= 0.5f) continue
            val pts = ArrayList<FloatArray>((len / step).toInt() + 4)
            var dist = 0f
            while (dist < len) {
                if (pm.getPosTan(dist, pos, tan)) {
                    pts.add(floatArrayOf(pos[0], pos[1], tan[0], tan[1]))
                }
                dist += step
            }
            if (pts.size < 3) continue

            var outVotes = 0
            val probeStep = (pts.size / 16).coerceAtLeast(1)
            for (i in pts.indices step probeStep) {
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
                for (i in pts.indices) {
                    val p0 = pts[i]
                    val p1 = pts[(i + 1) % pts.size]
                    area2 += p0[0].toDouble() * p1[1] - p1[0].toDouble() * p0[1]
                }
                if (area2 >= 0.0) 1f else -1f
            }

            for (i in pts.indices) {
                val p = pts[i]
                val x = p[0]
                val y = p[1]
                val tx = p[2]
                val ty = p[3]
                val nx = ty * outSign
                val ny = -tx * outSign

                val yStretched = top + (y - top) * stretchY
                val denom = sqrt(nx * nx + (ny * stretchY) * (ny * stretchY)).coerceAtLeast(0.0001f)
                val offset = halfStroke * strokeCompensation * (1.0f - (1.0f / denom))
                val clampedOffset = offset.coerceIn(0f, maxSafeOffset)

                val t2x = tx
                val t2y = ty * stretchY
                val t2Len = sqrt(t2x * t2x + t2y * t2y).coerceAtLeast(0.0001f)
                val nx2 = (t2y / t2Len) * outSign
                val ny2 = -(t2x / t2Len) * outSign

                val xFinal = x - nx2 * clampedOffset
                val yFinal = yStretched - ny2 * clampedOffset
                if (i == 0) outPath.moveTo(xFinal, yFinal) else outPath.lineTo(xFinal, yFinal)
            }
            outPath.close()
        } while (pm.nextContour())

        return outPath
    }
}
