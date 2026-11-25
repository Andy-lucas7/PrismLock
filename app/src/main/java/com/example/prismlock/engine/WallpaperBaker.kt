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
import android.view.WindowManager
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
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
    val hasDepth: Boolean,
    val isVideo: Boolean = false
)

@Serializable
data class BakedManifest(
    val version: Long = 0L,
    val projectId: String = "",
    val screenW: Int,
    val screenH: Int,
    val blurPx: Float,
    val entries: List<BakedWallpaperEntry> = emptyList()
)

class BakedWallAssets(
    val version: Long,
    val id: String,
    val uri: String,
    val sharp: Bitmap,
    val frosted: Bitmap,
    val depthMask: Bitmap?,
    val blurPx: Float
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
    val align: Align
)

object GlyphAtlasBaker {
    private const val ATLAS_CHARS = "0123456789:"
    private const val MAX_LENS_ATLASES = 6
    private val lensAtlasCache: MutableMap<String, BakedNodeLensAtlas> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, BakedNodeLensAtlas>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BakedNodeLensAtlas>?): Boolean =
                size > MAX_LENS_ATLASES
        }
    )
    private val nodeLookup = ConcurrentHashMap<String, LayerNode>()

    fun registerNode(node: LayerNode) {
        nodeLookup[node.id] = node
    }

    fun getRegisteredNode(nodeId: String): LayerNode? = nodeLookup[nodeId]

    fun clearCache() {
        lensAtlasCache.clear()
    }

    fun lensAtlasKey(node: LayerNode, u: Float, bevelPx: Float, strokePx: Float): String =
        "${StemStretch.atlasKey(node, u)}|bevel=${bevelPx.roundToInt()}|stroke=${strokePx.roundToInt()}"

    fun bakeForNode(node: LayerNode, u: Float) {
        registerNode(node)
        if (node.type != LayerType.TEXT) return
        val atlas = StemStretch.getOrBakeAtlas(node, u)
        if (node.fill != Fill.CRYSTAL) return

        val bevelPx = (node.glass.bevelWidth * u).coerceIn(1f, 48f * u)
        val strokePx = StemStretch.measureStrokeWidthPx(node, u)
        val lKey = lensAtlasKey(node, u, bevelPx, strokePx)
        if (lensAtlasCache.containsKey(lKey)) return

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val rInterior = (strokePx * 0.65f).coerceAtLeast(4f * u)
        val rBevel = bevelPx.coerceAtLeast(1f)
        val pad = ceil(maxOf(rInterior, rBevel) * 2f + 6f).toInt()

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
            sc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            maskPaint.maskFilter = null
            maskPaint.alpha = 255
            sc.drawPath(shifted, maskPaint)

            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val oc = Canvas(out)
            oc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Deep continuous convex interior dome + user bevel edge chamfer
            maskPaint.maskFilter = BlurMaskFilter(rInterior, BlurMaskFilter.Blur.NORMAL)
            maskPaint.alpha = 145
            oc.drawPath(shifted, maskPaint)
            maskPaint.maskFilter = BlurMaskFilter(rBevel, BlurMaskFilter.Blur.NORMAL)
            maskPaint.alpha = 125
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
            align = atlas.align
        )
    }

    fun tryAssembleScreenLens(
        node: LayerNode,
        text: String,
        baselineShiftY: Float,
        localToScreen: Matrix,
        screenW: Int,
        screenH: Int,
        u: Float,
        bevelPx: Float,
        strokePx: Float
    ): Bitmap? {
        if (text.isEmpty() || node.type != LayerType.TEXT) return null
        for (i in text.indices) {
            val c = text[i]
            if (c != ' ' && c != '\n' && ATLAS_CHARS.indexOf(c) < 0) return null
        }
        val lKey = lensAtlasKey(node, u, bevelPx, strokePx)
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
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.concat(localToScreen)

        var yOff = baselineShiftY
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

    fun bakedDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "baked_cache").apply { mkdirs() }

    private fun manifestFile(ctx: Context): File =
        File(bakedDir(ctx), "manifest.json")

    fun imageId(uriStr: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uriStr.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    fun sharpFile(ctx: Context, version: Long, id: String): File =
        File(bakedDir(ctx), "bake_v${version}_wall_sharp_$id.webp")

    fun frostedFile(ctx: Context, version: Long, id: String): File =
        File(bakedDir(ctx), "bake_v${version}_wall_frosted_$id.webp")

    fun depthFile(ctx: Context, version: Long, id: String): File =
        File(bakedDir(ctx), "bake_v${version}_wall_depth_$id.png")

    fun readManifest(ctx: Context): BakedManifest? = runCatching {
        val f = manifestFile(ctx)
        if (!f.exists()) return null
        json.decodeFromString(BakedManifest.serializer(), f.readText())
    }.getOrNull()

    fun baseSizeForPhysical(realWidth: Int, realHeight: Int): Pair<Int, Int> {
        val rw = realWidth.coerceAtLeast(1)
        val rh = realHeight.coerceAtLeast(1)
        val aspect = rh.toFloat() / rw.toFloat()
        val baseWidth = minOf(rw, 1080)
        val baseHeight = (baseWidth * aspect).roundToInt().coerceAtLeast(1)
        return baseWidth to baseHeight
    }

    /**
     * Proportional FHD+ working resolution (max 1080px width, exact physical screen aspect):
     * Reduces QHD+ GPU fillrate by >40% while maintaining exact pixel alignment across Editor & Service.
     */
    fun resolveDisplaySize(ctx: Context): Pair<Int, Int> {
        val bounds = runCatching {
            ctx.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
        }.getOrNull()
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            return baseSizeForPhysical(bounds.width(), bounds.height())
        }
        val dm = ctx.resources.displayMetrics
        return baseSizeForPhysical(dm.widthPixels.coerceAtLeast(1080), dm.heightPixels.coerceAtLeast(2400))
    }

    fun resolvePrimaryBlurPx(project: Project, u: Float): Float {
        val allNodes = project.layers.flatten().map { it.first }
        val crystalOrGlass = allNodes.firstOrNull { !it.hidden && (it.fill == Fill.CRYSTAL || it.fill == Fill.GLASS) }
        if (crystalOrGlass == null) return (26f * u).coerceIn(1f, 80f * u)
        val g = crystalOrGlass.glass
        return if (crystalOrGlass.fill == Fill.CRYSTAL) {
            if (g.frostAmount > 0.01f) (g.blur.coerceAtLeast(18f * g.frostAmount) * u).coerceIn(1f, 80f * u) else (20f * u)
        } else {
            (g.blur * u).coerceIn(1f, 80f * u)
        }
    }

    fun clearMemoryCache() {
        GlyphAtlasBaker.clearCache()
        SubjectDepthEngine.releaseMemory()
        WallpaperCarousel.clearMemoryCache()
    }

    fun loadBakedForUri(
        ctx: Context,
        uriStr: String?,
        expectedW: Int = 0,
        expectedH: Int = 0,
        requiredVersion: Long = 0L
    ): BakedWallAssets? {
        if (uriStr.isNullOrBlank()) return null
        val manifest = readManifest(ctx) ?: return null
        if (requiredVersion > 0L && manifest.version != requiredVersion) return null
        val ver = manifest.version
        val id = imageId(uriStr)
        val sf = sharpFile(ctx, ver, id)
        val ff = frostedFile(ctx, ver, id)
        if (!sf.exists() || !ff.exists()) return null

        val opaqueOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = false
        }
        val alphaOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val sharp = BitmapFactory.decodeFile(sf.absolutePath, opaqueOpts)?.apply { if (hasAlpha()) setHasAlpha(false) } ?: return null
        if (expectedW > 0 && expectedH > 0 && (sharp.width != expectedW || sharp.height != expectedH)) {
            sharp.recycle()
            return null
        }
        val frosted = BitmapFactory.decodeFile(ff.absolutePath, opaqueOpts)?.apply { if (hasAlpha()) setHasAlpha(false) } ?: run {
            sharp.recycle()
            return null
        }
        val df = depthFile(ctx, ver, id)
        val depth = if (df.exists()) BitmapFactory.decodeFile(df.absolutePath, alphaOpts) else null

        return BakedWallAssets(ver, id, uriStr, sharp, frosted, depth, manifest.blurPx)
    }

    fun loadBakedFrostedBitmap(ctx: Context, uriStr: String?, version: Long): Bitmap? {
        if (uriStr.isNullOrBlank() || version <= 0L) return null
        val ff = frostedFile(ctx, version, imageId(uriStr))
        if (!ff.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = false
        }
        return BitmapFactory.decodeFile(ff.absolutePath, opts)?.apply { if (hasAlpha()) setHasAlpha(false) }
    }

    fun loadBakedDepthBitmap(ctx: Context, uriStr: String?, version: Long): Bitmap? {
        if (uriStr.isNullOrBlank() || version <= 0L) return null
        val df = depthFile(ctx, version, imageId(uriStr))
        if (!df.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        return BitmapFactory.decodeFile(df.absolutePath, opts)
    }

    fun prewarmGlyphAtlases(project: Project, baseWidth: Int) {
        val u = baseWidth.coerceAtLeast(1) / 1080f
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
        val (baseW, baseH) = resolveDisplaySize(ctx)
        val u = baseW / 1080f
        val blurPx = resolvePrimaryBlurPx(project, u)
        val bakeVersion = System.currentTimeMillis()
        isBakeRunning = true
        var published = false
        try {

        onProgress(0.05f, "Limpando cache em memória...")
        clearMemoryCache()

        onProgress(0.10f, "Compilando Atlas de Glifos e Lentes...")
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

        val bakeRenderer = LiquidGlassRenderer().apply { attachContext(ctx) }
        val uris = if (project.folderUri != null) {
            WallpaperCarousel.listImages(ctx, project.folderUri).take(50).map { it.toString() }
        } else {
            listOfNotNull(project.imageUri)
        }

        val entries = ArrayList<BakedWallpaperEntry>(uris.size)
        val total = uris.size.coerceAtLeast(1)

        try {
            for ((index, uriStr) in uris.withIndex()) {
                val baseRatio = 0.16f + (index.toFloat() / total) * 0.76f
                val imgKey = WallpaperCarousel.imageKey(ctx, uriStr)
                val id = imageId(uriStr)
                val isVideo = WallpaperCarousel.isVideoUri(ctx, uriStr)

                onProgress(baseRatio, if (isVideo) "Processando vídeo ${index + 1}/$total ($imgKey)..." else "Processando imagem ${index + 1}/$total ($imgKey)...")
                val rawBmp = WallpaperCarousel.loadBitmap(ctx, uriStr, baseW, baseH)
                if (rawBmp != null) {
                    val exactSharp = centerCropOpaque(rawBmp, baseW, baseH)
                    val wantDepth = !isVideo && project.depth && project.isDepthEnabledFor(imgKey)
                    var hasDepthMask = false
                    var finalSharp = exactSharp

                    if (wantDepth) {
                        onProgress(baseRatio + (0.18f / total), "Recortando sujeito 3D ${index + 1}/$total...")
                        val rawMask = SubjectDepthEngine.getOrCreateMask(ctx, uriStr, exactSharp, closeAfter = false)
                        if (rawMask != null) {
                            val croppedMask = centerCropAlpha(rawMask, baseW, baseH)
                            val processedMask = if (project.hasDepthPostProcessing()) {
                                onProgress(baseRatio + (0.34f / total), "Aplicando Bokeh e Color Pop 3D ${index + 1}/$total...")
                                finalSharp = SubjectDepthEngine.composeProcessedWallpaper(
                                    baseSharp = exactSharp,
                                    depthMask = croppedMask,
                                    project = project,
                                    blurFn = { src, r -> bakeRenderer.bakeBlurredBitmap(src, baseW, baseH, r) }
                                )
                                SubjectDepthEngine.applySubjectColorPopMask(croppedMask, project)
                            } else {
                                croppedMask
                            }
                            FileOutputStream(depthFile(ctx, bakeVersion, id)).use { out ->
                                processedMask.compress(Bitmap.CompressFormat.PNG, 92, out)
                                out.flush()
                            }
                            hasDepthMask = true
                        }
                    }

                    FileOutputStream(sharpFile(ctx, bakeVersion, id)).use { out ->
                        finalSharp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 96, out)
                        out.flush()
                    }

                    onProgress(baseRatio + (0.55f / total), "Pré-renderizando vidro jateado ${index + 1}/$total...")
                    val frostedBmp = bakeRenderer.bakeBlurredBitmap(finalSharp, baseW, baseH, blurPx)
                    FileOutputStream(frostedFile(ctx, bakeVersion, id)).use { out ->
                        frostedBmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 94, out)
                        out.flush()
                    }
                    if (frostedBmp !== finalSharp && !frostedBmp.isRecycled) frostedBmp.recycle()
                    if (finalSharp !== exactSharp && !finalSharp.isRecycled) finalSharp.recycle()
                    if (exactSharp !== rawBmp && !exactSharp.isRecycled) exactSharp.recycle()

                    entries.add(BakedWallpaperEntry(id = id, uri = uriStr, imageKey = imgKey, hasDepth = hasDepthMask, isVideo = isVideo))
                }
            }
        } finally {
            bakeRenderer.release()
            SubjectDepthEngine.releaseMemory()
            WallpaperCarousel.clearMemoryCache()
        }

        val manifest = BakedManifest(
            version = bakeVersion,
            projectId = project.id,
            screenW = baseW,
            screenH = baseH,
            blurPx = blurPx,
            entries = entries
        )
        val manifestTmp = File(bakedDir(ctx), "manifest.json.tmp")
        FileOutputStream(manifestTmp).use { out ->
            out.write(json.encodeToString(BakedManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            out.flush()
            runCatching { out.fd.sync() }
        }
        if (!manifestTmp.renameTo(manifestFile(ctx))) {
            manifestFile(ctx).writeBytes(manifestTmp.readBytes())
            manifestTmp.delete()
        }

        if (entries.isNotEmpty()) {
            val curUri = ProjectStore.currentWallpaperUri(ctx)
            if (curUri == null || entries.none { it.uri == curUri }) {
                val idx = ProjectStore.index(ctx).coerceIn(0, entries.lastIndex)
                ProjectStore.setIndex(ctx, idx, entries[idx].uri)
            }
        }

        // Atomic version publish ONLY after all baked files are flushed & closed
        ProjectStore.setBakeVersion(ctx, bakeVersion)
        published = true
        purgeOtherVersions(ctx, bakeVersion)

        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < 900L) {
            onProgress(0.97f, "Finalizando pacote nativo...")
            delay(900L - elapsed)
        }
        onProgress(1.0f, "Pacote nativo pronto!")
        manifest
        } finally {
            if (!published) {
                withContext(NonCancellable) { deleteVersionFiles(ctx, bakeVersion) }
            }
            isBakeRunning = false
        }
    }

    @Volatile
    var isBakeRunning = false
        private set

    private fun deleteVersionFiles(ctx: Context, version: Long) {
        val prefix = "bake_v${version}_"
        bakedDir(ctx).listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix) || f.name == "manifest.json.tmp") runCatching { f.deleteRecursively() }
        }
    }

    private fun purgeOtherVersions(ctx: Context, keepVersion: Long) {
        val keepPrefix = "bake_v${keepVersion}_"
        bakedDir(ctx).listFiles()?.forEach { f ->
            if (f.name != "manifest.json" && !f.name.startsWith(keepPrefix)) runCatching { f.deleteRecursively() }
        }
    }

    fun centerCropOpaque(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h && !src.hasAlpha()) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
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
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
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
