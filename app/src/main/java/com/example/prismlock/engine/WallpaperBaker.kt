package com.example.prismlock.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.model.Align
import com.example.prismlock.model.Fill
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.flatten
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
data class BakedWallpaperEntry(
    val id: String,
    val uri: String,
    val imageKey: String,
    val hasDepth: Boolean
)

@Serializable
data class BakedManifest(
    val screenW: Int,
    val screenH: Int,
    val blurPx: Float,
    val entries: List<BakedWallpaperEntry> = emptyList()
)

class BakedWallAssets(
    val id: String,
    val uri: String,
    val sharp: Bitmap,
    val frosted: Bitmap,
    val depthMask: Bitmap?
)

class BakedGlyphLens(
    val ch: Char,
    val bitmap: Bitmap,
    val drawLeft: Float,
    val drawTop: Float,
    val advance: Float
)

class BakedNodeLensAtlas(
    val key: String,
    val glyphs: Map<Char, BakedGlyphLens>,
    val spaceAdvance: Float,
    val lineAdvance: Float,
    val letterSpacingPx: Float,
    val align: Align,
    val baseTopOffset: Float
)

object GlyphAtlasBaker {
    private const val ATLAS_CHARS = "0123456789:"
    private val lensAtlasCache = ConcurrentHashMap<String, BakedNodeLensAtlas>()
    private val nodeLookup = ConcurrentHashMap<String, LayerNode>()

    fun registerNode(node: LayerNode) {
        nodeLookup[node.id] = node
    }

    fun getRegisteredNode(nodeId: String): LayerNode? = nodeLookup[nodeId]

    fun lensAtlasKey(node: LayerNode, u: Float, bevelPx: Float): String =
        "${StemStretch.atlasKey(node, u)}|bevel=${bevelPx.roundToInt()}"

    fun bakeForNode(node: LayerNode, u: Float) {
        registerNode(node)
        if (node.type != LayerType.TEXT) return
        val atlas = StemStretch.getOrBakeAtlas(node, u)
        if (node.fill != Fill.CRYSTAL) return

        val bevelPx = (node.glass.bevelWidth * u).coerceIn(2f, 64f * u)
        val lKey = lensAtlasKey(node, u, bevelPx)
        if (lensAtlasCache.containsKey(lKey)) return

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val r1 = (bevelPx * 0.45f).coerceAtLeast(1f)
        val r2 = (bevelPx * 1.45f).coerceAtLeast(2f)
        val pad = ceil(r2 * 2f + 6f).toInt()

        var minTop = Float.MAX_VALUE
        for (g in atlas.glyphs.values) {
            if (!g.path.isEmpty && g.bounds.top < minTop) {
                minTop = g.bounds.top
            }
        }
        if (minTop == Float.MAX_VALUE) minTop = 0f

        val glyphLensMap = HashMap<Char, BakedGlyphLens>(16)
        for ((ch, g) in atlas.glyphs) {
            if (g.path.isEmpty) continue
            val leftPx = floor(g.bounds.left).toInt() - pad
            val topPx = floor(g.bounds.top).toInt() - pad
            val rightPx = ceil(g.bounds.right).toInt() + pad
            val bottomPx = ceil(g.bounds.bottom).toInt() + pad
            val bw = (rightPx - leftPx).coerceAtLeast(4)
            val bh = (bottomPx - topPx).coerceAtLeast(4)

            val shifted = Path(g.path).apply { offset(-leftPx.toFloat(), -topPx.toFloat()) }
            val sharp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val sc = Canvas(sharp)
            maskPaint.maskFilter = null
            maskPaint.alpha = 255
            sc.drawPath(shifted, maskPaint)

            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val oc = Canvas(out)
            maskPaint.maskFilter = BlurMaskFilter(r1, BlurMaskFilter.Blur.NORMAL)
            maskPaint.alpha = 145
            oc.drawPath(shifted, maskPaint)
            maskPaint.maskFilter = BlurMaskFilter(r2, BlurMaskFilter.Blur.NORMAL)
            maskPaint.alpha = 115
            oc.drawPath(shifted, maskPaint)
            maskPaint.maskFilter = null
            maskPaint.alpha = 255
            oc.drawBitmap(sharp, 0f, 0f, cutPaint)
            sharp.recycle()

            glyphLensMap[ch] = BakedGlyphLens(
                ch = ch,
                bitmap = out,
                drawLeft = leftPx.toFloat(),
                drawTop = topPx.toFloat(),
                advance = g.advance
            )
        }

        lensAtlasCache[lKey] = BakedNodeLensAtlas(
            key = lKey,
            glyphs = glyphLensMap,
            spaceAdvance = atlas.spaceAdvance,
            lineAdvance = atlas.lineAdvance,
            letterSpacingPx = atlas.letterSpacingPx,
            align = atlas.align,
            baseTopOffset = -minTop
        )
    }

    fun tryAssembleScreenLens(
        node: LayerNode,
        text: String,
        localToScreen: Matrix,
        screenW: Int,
        screenH: Int,
        u: Float,
        bevelPx: Float
    ): Bitmap? {
        if (text.isEmpty() || node.type != LayerType.TEXT) return null
        for (i in text.indices) {
            val c = text[i]
            if (c != ' ' && c != '\n' && ATLAS_CHARS.indexOf(c) < 0) return null
        }
        val lKey = lensAtlasKey(node, u, bevelPx)
        val atlas = lensAtlasCache[lKey] ?: return null

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

        val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(out)
        val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.concat(localToScreen)

        var yOff = atlas.baseTopOffset
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
                    val gl = atlas.glyphs[c]
                    if (gl != null) {
                        canvas.drawBitmap(gl.bitmap, xOff + gl.drawLeft, yOff + gl.drawTop, blitPaint)
                        xOff += gl.advance
                    }
                }
                if (j < line.lastIndex) xOff += atlas.letterSpacingPx
            }
            yOff += atlas.lineAdvance
        }
        canvas.restore()
        return out
    }
}

object WallpaperBaker {
    const val ACTION_BAKED_READY = "com.example.prismlock.ACTION_BAKED_READY"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val memAssetsCache = ConcurrentHashMap<String, BakedWallAssets>()

    fun bakedDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "baked_cache").apply { mkdirs() }

    private fun manifestFile(ctx: Context): File =
        File(bakedDir(ctx), "manifest.json")

    fun imageId(uriStr: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uriStr.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    fun sharpFile(ctx: Context, id: String): File = File(bakedDir(ctx), "wall_sharp_$id.webp")
    fun frostedFile(ctx: Context, id: String): File = File(bakedDir(ctx), "wall_frosted_$id.webp")
    fun depthFile(ctx: Context, id: String): File = File(bakedDir(ctx), "wall_depth_$id.png")

    fun readManifest(ctx: Context): BakedManifest? = runCatching {
        val f = manifestFile(ctx)
        if (!f.exists()) return null
        json.decodeFromString(BakedManifest.serializer(), f.readText())
    }.getOrNull()

    fun resolvePrimaryBlurPx(project: Project, u: Float): Float {
        val allNodes = project.layers.flatten().map { it.first }
        val crystalOrGlass = allNodes.firstOrNull { it.fill == Fill.CRYSTAL || it.fill == Fill.GLASS }
        if (crystalOrGlass == null) return (26f * u).coerceIn(1f, 80f * u)
        val g = crystalOrGlass.glass
        return if (crystalOrGlass.fill == Fill.CRYSTAL) {
            if (g.frostAmount > 0.01f) (g.blur.coerceAtLeast(18f * g.frostAmount) * u).coerceIn(1f, 80f * u) else (20f * u)
        } else {
            (g.blur * u).coerceIn(1f, 80f * u)
        }
    }

    fun loadBakedForUri(ctx: Context, uriStr: String?): BakedWallAssets? {
        if (uriStr.isNullOrBlank()) return null
        val id = imageId(uriStr)
        memAssetsCache[id]?.let { cached ->
            if (!cached.sharp.isRecycled && !cached.frosted.isRecycled) return cached
        }
        val sf = sharpFile(ctx, id)
        val ff = frostedFile(ctx, id)
        if (!sf.exists() || !ff.exists()) return null

        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val sharp = BitmapFactory.decodeFile(sf.absolutePath, opts)?.apply { setHasAlpha(false) } ?: return null
        val frosted = BitmapFactory.decodeFile(ff.absolutePath, opts)?.apply { setHasAlpha(false) } ?: return null
        val df = depthFile(ctx, id)
        val depth = if (df.exists()) BitmapFactory.decodeFile(df.absolutePath, opts) else null

        val assets = BakedWallAssets(id, uriStr, sharp, frosted, depth)
        if (memAssetsCache.size >= 3) memAssetsCache.clear()
        memAssetsCache[id] = assets
        return assets
    }

    fun prewarmGlyphAtlases(project: Project, screenW: Int) {
        val u = screenW.coerceAtLeast(1) / 1080f
        val allNodes = project.layers.flatten().map { it.first }
        for (node in allNodes) {
            GlyphAtlasBaker.bakeForNode(node, u)
        }
    }

    suspend fun bakeAll(
        ctx: Context,
        project: Project,
        renderer: LiquidGlassRenderer,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BakedManifest = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val dm = ctx.resources.displayMetrics
        val screenW = dm.widthPixels.coerceAtLeast(1080)
        val screenH = dm.heightPixels.coerceAtLeast(2400)
        val u = screenW / 1080f
        val blurPx = resolvePrimaryBlurPx(project, u)

        onProgress(0.08f, "Compilando Atlas de Glifos e Mapas de Lente...")
        val allNodes = project.layers.flatten().map { it.first }
        val now = LocalDateTime.now()
        val env = EnvProvider.env.value.copy(locked = true)
        for (node in allNodes) {
            GlyphAtlasBaker.bakeForNode(node, u)
            if (node.type == LayerType.TEXT) {
                val evaluated = Dynamic.eval(node.text, env, now)
                StemStretch.shape(evaluated, node, u)
            }
        }

        val uris = if (project.folderUri != null) {
            WallpaperCarousel.listImages(ctx, project.folderUri).map { it.toString() }
        } else {
            listOfNotNull(project.imageUri)
        }

        val entries = ArrayList<BakedWallpaperEntry>(uris.size)
        val validIds = HashSet<String>()
        val total = uris.size.coerceAtLeast(1)

        for ((index, uriStr) in uris.withIndex()) {
            val baseRatio = 0.15f + (index.toFloat() / total) * 0.78f
            val imgKey = WallpaperCarousel.imageKey(ctx, uriStr)
            val id = imageId(uriStr)
            validIds.add(id)

            onProgress(baseRatio, "Processando imagem ${index + 1}/$total ($imgKey)...")
            val rawBmp = WallpaperCarousel.loadBitmap(ctx, uriStr, screenW, screenH)
            if (rawBmp != null) {
                val exactSharp = centerCropOpaque(rawBmp, screenW, screenH)
                val sf = sharpFile(ctx, id)
                sf.outputStream().use { out ->
                    exactSharp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 96, out)
                }

                onProgress(baseRatio + (0.25f / total), "Pré-renderizando vidro jateado ${index + 1}/$total...")
                val frostedBmp = renderer.bakeBlurredBitmap(exactSharp, screenW, screenH, blurPx)
                val ff = frostedFile(ctx, id)
                ff.outputStream().use { out ->
                    frostedBmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 94, out)
                }

                val wantDepth = project.depth && project.isDepthEnabledFor(imgKey)
                val df = depthFile(ctx, id)
                var hasDepthMask = false
                if (wantDepth) {
                    onProgress(baseRatio + (0.50f / total), "Recortando sujeito 3D ${index + 1}/$total...")
                    val rawMask = SubjectDepthEngine.getOrCreateMask(ctx, uriStr, exactSharp)
                    if (rawMask != null) {
                        val croppedMask = centerCropAlpha(rawMask, screenW, screenH)
                        df.outputStream().use { out ->
                            croppedMask.compress(Bitmap.CompressFormat.PNG, 92, out)
                        }
                        hasDepthMask = true
                    } else if (df.exists()) {
                        df.delete()
                    }
                } else if (df.exists()) {
                    df.delete()
                }

                memAssetsCache.remove(id)
                entries.add(BakedWallpaperEntry(id = id, uri = uriStr, imageKey = imgKey, hasDepth = hasDepthMask))
            }
        }

        bakedDir(ctx).listFiles()?.forEach { f ->
            if (f.name != "manifest.json") {
                val keep = validIds.any { id -> f.name.contains(id) }
                if (!keep) runCatching { f.delete() }
            }
        }

        val manifest = BakedManifest(
            screenW = screenW,
            screenH = screenH,
            blurPx = blurPx,
            entries = entries
        )
        runCatching {
            manifestFile(ctx).writeText(json.encodeToString(BakedManifest.serializer(), manifest))
        }

        if (entries.isNotEmpty()) {
            val curUri = ProjectStore.currentWallpaperUri(ctx)
            if (curUri == null || entries.none { it.uri == curUri }) {
                val idx = ProjectStore.index(ctx).coerceIn(0, entries.lastIndex)
                ProjectStore.setIndex(ctx, idx, entries[idx].uri)
            }
        }

        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < 1050L) {
            onProgress(0.96f, "Finalizando pacote nativo...")
            delay(1050L - elapsed)
        }
        onProgress(1.0f, "Pacote nativo pronto!")
        manifest
    }

    private fun centerCropOpaque(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h && !src.hasAlpha()) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dx = (w - src.width * scale) * 0.5f
        val dy = (h - src.height * scale) * 0.5f
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        out.setHasAlpha(false)
        return out
    }

    private fun centerCropAlpha(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dx = (w - src.width * scale) * 0.5f
        val dy = (h - src.height * scale) * 0.5f
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
