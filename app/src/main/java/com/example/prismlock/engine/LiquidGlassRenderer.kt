package com.example.prismlock.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.CornerPathEffect
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.LruCache
import com.example.prismlock.data.Env
import com.example.prismlock.model.Align
import com.example.prismlock.model.Anchor
import com.example.prismlock.model.Blend
import com.example.prismlock.model.Cond
import com.example.prismlock.model.Dir
import com.example.prismlock.model.Fill
import com.example.prismlock.model.Glass
import com.example.prismlock.model.GradientKind
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.ShapeKind
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class HitBox(
    val id: String,
    val rect: RectF,
    val localW: Float,
    val localH: Float,
    val invMatrix: Matrix,
    val type: LayerType
)

class LiquidGlassRenderer {
    companion object {
        // Mode C: Classic Frosted Glass (1:1 blurred background + saturation boost + fine grain + tint)
        private const val BLUR_AGSL = """
            uniform shader content;
            uniform float2 uResolution;
            uniform float uSaturation;
            uniform float4 uTint;
            uniform float uGrain;

            float hash12(float2 p) {
                float3 p3 = fract(float3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            half4 main(float2 fragCoord) {
                float2 maxBounds = max(uResolution - float2(2.0), float2(2.0));
                float2 safeUV = clamp(fragCoord, float2(2.0), maxBounds);
                float3 col = float3(content.eval(safeUV).rgb);
                float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
                col = mix(float3(luma), col, uSaturation);
                float n = (hash12(fragCoord) - 0.5) * uGrain;
                col = clamp(col + float3(n), float3(0.0), float3(1.0));
                col = mix(col, uTint.rgb, uTint.a);
                return half4(half3(col), 1.0);
            }
        """

        // Mode D: Optical Crystal (4-neighbor central-diff normal + Snell refraction + RGB dispersion + Fresnel/Specular + frost mix)
        private const val CRYSTAL_AGSL = """
            uniform shader backgroundTex;
            uniform shader frostedTex;
            uniform shader lensHeightMap;
            uniform float2 uResolution;
            uniform float2 uBoxSize;
            uniform float uRefractionStrength;
            uniform float uBevelStrength;
            uniform float uDispersion;
            uniform float uFrostAmount;
            uniform float uRimIntensity;
            uniform float uLightAngle;
            uniform float uSaturation;
            uniform float4 uTint;

            half4 main(float2 fragCoord) {
                float2 maxBounds = max(uResolution - float2(2.0), float2(2.0));
                float2 safeMask = clamp(fragCoord, float2(2.0), maxBounds);
                float maskAlpha = float(lensHeightMap.eval(safeMask).a);
                float hL = float(lensHeightMap.eval(clamp(fragCoord - float2(2.0, 0.0), float2(0.0), uResolution)).a);
                float hR = float(lensHeightMap.eval(clamp(fragCoord + float2(2.0, 0.0), float2(0.0), uResolution)).a);
                float hU = float(lensHeightMap.eval(clamp(fragCoord - float2(0.0, 2.0), float2(0.0), uResolution)).a);
                float hD = float(lensHeightMap.eval(clamp(fragCoord + float2(0.0, 2.0), float2(0.0), uResolution)).a);

                float edgeAtten = smoothstep(0.05, 0.25, maskAlpha);
                float dx = (hL - hR) * 0.5 * edgeAtten;
                float dy = (hU - hD) * 0.5 * edgeAtten;
                float3 normal = normalize(float3(dx * uBevelStrength, dy * uBevelStrength, 1.0));

                float2 refractedUV = fragCoord + normal.xy * uRefractionStrength * 45.0;
                float2 safeUV = clamp(refractedUV, float2(2.0), maxBounds);
                float2 disp = normal.xy * (uDispersion * max(uBoxSize.x, uBoxSize.y));
                float2 safeR = clamp(refractedUV + disp, float2(2.0), maxBounds);
                float2 safeB = clamp(refractedUV - disp, float2(2.0), maxBounds);

                float3 sharpSample = float3(
                    float(backgroundTex.eval(safeR).r),
                    float(backgroundTex.eval(safeUV).g),
                    float(backgroundTex.eval(safeB).b)
                );
                float3 frostedSample = float3(
                    float(frostedTex.eval(safeR).r),
                    float(frostedTex.eval(safeUV).g),
                    float(frostedTex.eval(safeB).b)
                );

                float3 col = mix(sharpSample, frostedSample, clamp(uFrostAmount, 0.0, 1.0));
                float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
                col = mix(float3(luma), col, uSaturation);
                col = mix(col, uTint.rgb, uTint.a * 0.65);

                float3 lightDir = normalize(float3(cos(uLightAngle), sin(uLightAngle), 0.55));
                float fresnel = pow(max(1.0 - max(normal.z, 0.0), 0.0001), 2.8) * uRimIntensity * edgeAtten;
                float spec = pow(max(dot(normal, lightDir), 0.0001), 22.0) * uRimIntensity * edgeAtten;
                col = clamp(col + float3(fresnel * 0.75 + spec * 1.15), float3(0.0), float3(1.0));
                return half4(half3(col), 1.0);
            }
        """
    }

    private val blurShader = RuntimeShader(BLUR_AGSL)
    private val crystalShader = RuntimeShader(CRYSTAL_AGSL)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(4f)
    }
    private val dimPaint = Paint()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val maskCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private val bgLock = Any()
    private val blurLock = Any()
    private val maskLock = Any()

    private val bgNode = RenderNode("prism_bg")
    private val blurGenNode = RenderNode("prism_frosted_gen")
    private var blurThread: HandlerThread? = null
    private var blurHandler: Handler? = null
    private var blurHwRenderer: HardwareRenderer? = null
    private var blurImageReader: ImageReader? = null
    private var blurReaderW = 0
    private var blurReaderH = 0

    private val frostedShaderCache = LruCache<String, BitmapShader>(4)
    private val lensHeightCache = LruCache<String, Bitmap>(8)
    private val lastLensByNode = ConcurrentHashMap<String, Bitmap>()
    private val lensShaderCache = ConcurrentHashMap<String, Pair<Bitmap, BitmapShader>>()
    @Volatile private var allowStaleLensOnMiss = false

    private val rootMatrix = Matrix()
    private val invRootMatrix = Matrix()
    private val tmpMatrix = Matrix()
    private val localToScreen = Matrix()
    private val screenToLocal = Matrix()
    private val restingMatrix = Matrix()
    private val wakeDeltaMatrix = Matrix()
    private val tmpBounds = RectF()
    private val adaptiveCache = ConcurrentHashMap<Int, Int>()
    private val paletteCache = ConcurrentHashMap<Int, List<Int>>()

    private var lastBgId = 0
    private var lastBgW = 0
    private var lastBgH = 0
    @Volatile private var sharpBgBmp: Bitmap? = null
    @Volatile private var sharpBgShader: BitmapShader? = null
    @Volatile private var prebakedFrostedBmp: Bitmap? = null
    @Volatile private var prebakedFrostedShader: BitmapShader? = null

    data class BakedMinuteScene(
        val clockLayerBitmap: Bitmap,
        val completeLockscreenBitmap: Bitmap,
        val showDepth: Boolean,
        val clockCenterX: Float,
        val clockCenterY: Float
    )

    fun setAllowStaleLensOnMiss(enabled: Boolean) {
        allowStaleLensOnMiss = enabled
    }

    fun release() {
        synchronized(blurLock) {
            runCatching { blurHwRenderer?.destroy() }
            runCatching { blurImageReader?.close() }
            runCatching { blurThread?.quitSafely() }
            blurHwRenderer = null
            blurImageReader = null
            blurThread = null
            blurHandler = null
            blurReaderW = 0
            blurReaderH = 0
        }
        prebakedFrostedBmp = null
        prebakedFrostedShader = null
        frostedShaderCache.evictAll()
        lensHeightCache.evictAll()
        lastLensByNode.clear()
        lensShaderCache.clear()
    }

    fun clearShaderCachesForHome() {
        prebakedFrostedBmp = null
        prebakedFrostedShader = null
        frostedShaderCache.evictAll()
        lensHeightCache.evictAll()
        lastLensByNode.clear()
        lensShaderCache.clear()
    }

    fun extractPalette(bmp: Bitmap?): List<Int> {
        if (bmp == null) return listOf(0xFFFFFFFF.toInt(), 0xFF8AB4F8.toInt(), 0xFFFFC1A6.toInt(), 0xFFA6FFD9.toInt(), 0xFFE3B8FF.toInt(), 0xFF1E2430.toInt())
        val id = System.identityHashCode(bmp)
        paletteCache[id]?.let { return it }
        val buckets = HashMap<Int, Pair<Long, LongArray>>()
        val stepX = (bmp.width / 20).coerceAtLeast(1)
        val stepY = (bmp.height / 28).coerceAtLeast(1)
        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                val c = bmp.getPixel(x, y)
                val qr = Color.red(c) shr 5
                val qg = Color.green(c) shr 5
                val qb = Color.blue(c) shr 5
                val key = (qr shl 6) or (qg shl 3) or qb
                val entry = buckets.getOrPut(key) { 0L to LongArray(3) }
                entry.second[0] += Color.red(c)
                entry.second[1] += Color.green(c)
                entry.second[2] += Color.blue(c)
                buckets[key] = (entry.first + 1L) to entry.second
            }
        }
        val top = buckets.values.sortedByDescending { it.first }.take(6).map { (cnt, sum) ->
            Color.rgb((sum[0] / cnt).toInt(), (sum[1] / cnt).toInt(), (sum[2] / cnt).toInt())
        }
        val res = top.ifEmpty { listOf(0xFFFFFFFF.toInt(), 0xFF8AB4F8.toInt()) }
        paletteCache.clear()
        paletteCache[id] = res
        return res
    }

    fun extractAdaptiveColor(bmp: Bitmap?): Int {
        if (bmp == null) return 0xFF9AC5FF.toInt()
        val id = System.identityHashCode(bmp)
        adaptiveCache[id]?.let { return it }
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val stepX = (bmp.width / 24).coerceAtLeast(1)
        val stepY = (bmp.height / 32).coerceAtLeast(1)
        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                val c = bmp.getPixel(x, y)
                val maxC = maxOf(Color.red(c), Color.green(c), Color.blue(c))
                val minC = minOf(Color.red(c), Color.green(c), Color.blue(c))
                val w = (maxC - minC) + 16
                r += Color.red(c) * w
                g += Color.green(c) * w
                b += Color.blue(c) * w
                n += w
            }
        }
        val out = if (n == 0L) 0xFF9AC5FF.toInt() else {
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.25f).coerceIn(0.25f, 0.85f)
            hsv[2] = hsv[2].coerceIn(0.75f, 0.98f)
            Color.HSVToColor(255, hsv)
        }
        adaptiveCache.clear()
        adaptiveCache[id] = out
        return out
    }

    fun render(
        canvas: Canvas,
        w: Int,
        h: Int,
        project: Project,
        bg: Bitmap?,
        depthMask: Bitmap?,
        env: Env,
        now: LocalDateTime,
        wakeProgress: Float = 1f,
        lockAlpha: Float = 1f,
        hits: MutableList<HitBox>? = null
    ) {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        hits?.clear()

        @Suppress("DEPRECATION")
        canvas.getMatrix(rootMatrix)
        if (!rootMatrix.invert(invRootMatrix)) invRootMatrix.reset()

        if (bg != null) {
            ensureSharpBg(bg, screenW, screenH)
            canvas.drawRenderNode(bgNode)
        } else {
            canvas.drawColor(0xFF0A0D14.toInt())
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
        }

        val u = screenW / 1080f
        val adaptive = extractAdaptiveColor(bg)
        var mainBounds: RectF? = null

        for (node in project.layers) {
            if (!isVisible(node, env)) continue
            val isLockOnly = node.cond == Cond.LOCK_ONLY
            val condAlpha = if (isLockOnly) lockAlpha else 1f
            if (condAlpha <= 0.01f) continue

            if (node.type == LayerType.DEPTH) {
                if (project.depth && depthMask != null) {
                    val ok = !project.depthGuard || mainBounds == null || checkCoverage(depthMask, mainBounds, screenW, screenH)
                    if (ok) {
                        paint.reset()
                        paint.isAntiAlias = true
                        paint.alpha = (node.opacity * condAlpha * 255).roundToInt().coerceIn(0, 255)
                        drawBitmapCenterCrop(canvas, depthMask, screenW, screenH, paint)
                    }
                }
                continue
            }

            val box = measureNode(node, u, env, now)
            if (mainBounds == null && (node.type == LayerType.TEXT || node.type == LayerType.STACK)) {
                val (ox, oy) = anchorOffset(node.anchor, node.align, box.w, box.h)
                val px = node.x * screenW + ox
                val py = node.y * screenH + oy
                mainBounds = RectF(px, py, px + box.w, py + box.h)
            }

            val wakeOffset = (1f - wakeProgress) * 42f * u
            val wakeScale = (0.96f + 0.04f * wakeProgress).coerceAtLeast(0.5f)
            val (rx, ry) = anchorOffset(node.anchor, node.align, box.w, box.h)
            val restCx = node.x * screenW + rx + box.w * 0.5f
            val restCy = node.y * screenH + ry + box.h * 0.5f

            wakeDeltaMatrix.reset()
            if (wakeOffset != 0f || wakeScale != 1f) {
                wakeDeltaMatrix.postTranslate(-restCx, -restCy)
                wakeDeltaMatrix.postScale(wakeScale, wakeScale)
                wakeDeltaMatrix.postTranslate(restCx, restCy + wakeOffset)
            }

            drawNode(
                canvas = canvas,
                screenW = screenW,
                screenH = screenH,
                u = u,
                node = node,
                m = box,
                bg = bg,
                env = env,
                now = now,
                adaptive = adaptive,
                alpha = node.opacity * condAlpha * wakeProgress.coerceIn(0f, 1f),
                wakeDy = wakeOffset,
                wakeScale = wakeScale,
                restPoseKey = "${node.x},${node.y},${node.anchor},${node.align},${node.scale},${node.rotation}",
                hits = hits
            )
        }
    }

    fun prepareFrameAssets(
        w: Int,
        h: Int,
        project: Project,
        bg: Bitmap?,
        env: Env,
        now: LocalDateTime
    ) {
        val prevStale = allowStaleLensOnMiss
        allowStaleLensOnMiss = false
        try {
            val screenW = w.coerceAtLeast(1)
            val screenH = h.coerceAtLeast(1)
            val u = screenW / 1080f
            if (bg != null) ensureSharpBg(bg, screenW, screenH)

            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val workMat = Matrix()

            fun prepareNode(node: LayerNode, m: Measured, parentMat: Matrix, restPoseKey: String) {
                GlyphAtlasBaker.registerNode(node)
                val (ox, oy) = if (parentMat.isIdentity) anchorOffset(node.anchor, node.align, m.w, m.h) else 0f to 0f
                val localMat = Matrix(parentMat)
                val left = if (parentMat.isIdentity) node.x * screenW + ox else 0f
                val top = if (parentMat.isIdentity) node.y * screenH + oy else 0f
                val cx = left + m.w * 0.5f
                val cy = top + m.h * 0.5f
                localMat.preTranslate(cx, cy)
                if (node.rotation != 0f) localMat.preRotate(node.rotation)
                if (node.scale != 1f) localMat.preScale(node.scale, node.scale)
                localMat.preTranslate(-m.w * 0.5f, -m.h * 0.5f)

                if (node.type == LayerType.STACK) {
                    val kids = node.children.filter { isVisible(it, env) && it.type != LayerType.DEPTH }
                    val gap = node.spacing * u
                    var cur = 0f
                    kids.forEachIndexed { idx, child ->
                        val cm = m.children[idx]
                        val kx = if (node.dir == Dir.VERTICAL) when (node.align) {
                            Align.START -> 0f; Align.CENTER -> (m.w - cm.w) / 2f; Align.END -> m.w - cm.w
                        } else cur
                        val ky = if (node.dir == Dir.HORIZONTAL) (m.h - cm.h) / 2f else cur
                        val childBase = Matrix(localMat).apply { preTranslate(kx, ky) }
                        prepareNode(
                            node = child,
                            m = cm,
                            parentMat = childBase,
                            restPoseKey = "$restPoseKey|${node.dir},${node.align},$gap,$idx,$kx,$ky,${child.scale},${child.rotation}"
                        )
                        cur += (if (node.dir == Dir.VERTICAL) cm.h else cm.w) + gap
                    }
                    return
                }

                val path = m.path ?: return
                if (path.isEmpty || bg == null) return
                if (node.fill == Fill.GLASS) {
                    val blurPx = (node.glass.blur * u).coerceIn(0f, 80f * u)
                    getFrostedShader(blurPx, screenW, screenH)
                } else if (node.fill == Fill.CRYSTAL) {
                    val g = node.glass
                    val blurPx = if (g.frostAmount > 0.01f) (g.blur.coerceAtLeast(18f * g.frostAmount) * u).coerceIn(0f, 80f * u) else 0f
                    getFrostedShader(blurPx, screenW, screenH)
                    val bevelPx = (g.bevelWidth * u).coerceIn(2f, 64f * u)
                    val restScreenPath = Path(path).apply { transform(localMat) }
                    getLensHeightMap(node.id, restScreenPath, screenW, screenH, bevelPx, "${m.geomKey}|$restPoseKey", localMat, m.evalText, u)
                }
            }

            for (node in project.layers) {
                if (!isVisible(node, env) || node.type == LayerType.DEPTH) continue
                val box = measureNode(node, u, env, now, workPaint, workMat)
                prepareNode(
                    node = node,
                    m = box,
                    parentMat = Matrix(),
                    restPoseKey = "${node.x},${node.y},${node.anchor},${node.align},${node.scale},${node.rotation}"
                )
            }
        } finally {
            allowStaleLensOnMiss = prevStale
        }
    }

    fun setPrebakedBackground(sharp: Bitmap, frosted: Bitmap, w: Int, h: Int) = synchronized(bgLock) {
        val exactSharp = if (sharp.width == w && sharp.height == h && !sharp.hasAlpha()) {
            sharp
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { dst ->
                val sc = Canvas(dst)
                sc.drawColor(Color.BLACK)
                drawBitmapCenterCrop(sc, sharp, w, h, Paint(Paint.FILTER_BITMAP_FLAG), Matrix())
                dst.setHasAlpha(false)
            }
        }
        val exactFrosted = if (frosted.width == w && frosted.height == h && !frosted.hasAlpha()) {
            frosted
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { dst ->
                val fc = Canvas(dst)
                fc.drawColor(Color.BLACK)
                drawBitmapCenterCrop(fc, frosted, w, h, Paint(Paint.FILTER_BITMAP_FLAG), Matrix())
                dst.setHasAlpha(false)
            }
        }
        lastBgId = System.identityHashCode(sharp)
        lastBgW = w
        lastBgH = h
        sharpBgBmp = exactSharp
        sharpBgShader = BitmapShader(exactSharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        prebakedFrostedBmp = exactFrosted
        prebakedFrostedShader = BitmapShader(exactFrosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        bgNode.setPosition(0, 0, w, h)
        val rec = bgNode.beginRecording(w, h)
        rec.drawBitmap(exactSharp, 0f, 0f, null)
        bgNode.endRecording()
    }

    fun bakeBlurredBitmap(sharp: Bitmap, w: Int, h: Int, blurPx: Float): Bitmap {
        val r = blurPx.coerceIn(1f, 80f * (w / 1080f))
        return renderBlurredBitmap(sharp, w, h, r).apply { setHasAlpha(false) }
    }

    fun bakeMinuteScene(
        w: Int,
        h: Int,
        project: Project,
        sharpBg: Bitmap?,
        frostedBg: Bitmap?,
        depthMask: Bitmap?,
        env: Env,
        now: LocalDateTime
    ): BakedMinuteScene {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        val u = screenW / 1080f

        if (sharpBg != null && frostedBg != null) {
            setPrebakedBackground(sharpBg, frostedBg, screenW, screenH)
        } else if (sharpBg != null) {
            prebakedFrostedBmp = null
            prebakedFrostedShader = null
            ensureSharpBg(sharpBg, screenW, screenH)
        }

        val adaptive = extractAdaptiveColor(sharpBgBmp ?: sharpBg)
        var mainBounds: RectF? = null
        var clockCx = screenW * 0.5f
        var clockCy = screenH * 0.25f

        val layerNode = RenderNode("prism_minute_layers").apply {
            setPosition(0, 0, screenW, screenH)
        }
        val rec = layerNode.beginRecording(screenW, screenH)
        rootMatrix.reset()
        invRootMatrix.reset()
        wakeDeltaMatrix.reset()

        for (node in project.layers) {
            if (!isVisible(node, env) || node.type == LayerType.DEPTH) continue
            GlyphAtlasBaker.registerNode(node)
            val box = measureNode(node, u, env, now)
            if (mainBounds == null && (node.type == LayerType.TEXT || node.type == LayerType.STACK)) {
                val (ox, oy) = anchorOffset(node.anchor, node.align, box.w, box.h)
                val px = node.x * screenW + ox
                val py = node.y * screenH + oy
                mainBounds = RectF(px, py, px + box.w, py + box.h)
                clockCx = mainBounds.centerX()
                clockCy = mainBounds.centerY()
            }
            drawNode(
                canvas = rec,
                screenW = screenW,
                screenH = screenH,
                u = u,
                node = node,
                m = box,
                bg = sharpBgBmp ?: sharpBg,
                env = env,
                now = now,
                adaptive = adaptive,
                alpha = node.opacity,
                wakeDy = 0f,
                wakeScale = 1f,
                restPoseKey = "${node.x},${node.y},${node.anchor},${node.align},${node.scale},${node.rotation}",
                hits = null
            )
        }
        layerNode.endRecording()

        val clockLayerBmp = renderNodeToBitmap(layerNode, screenW, screenH)
        val hasDepthLayer = project.layers.any { it.type == LayerType.DEPTH && !it.hidden }
        val showDepth = hasDepthLayer && project.depth && depthMask != null &&
            (!project.depthGuard || mainBounds == null || checkCoverage(depthMask, mainBounds, screenW, screenH))

        val completeBmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
        val cc = Canvas(completeBmp)
        val baseSharp = sharpBgBmp ?: sharpBg
        if (baseSharp != null) {
            drawBitmapCenterCrop(cc, baseSharp, screenW, screenH, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            cc.drawColor(0xFF0A0D14.toInt())
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            cc.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
        }
        cc.drawBitmap(clockLayerBmp, 0f, 0f, null)
        if (showDepth && depthMask != null) {
            val depthNode = project.layers.firstOrNull { it.type == LayerType.DEPTH }
            paint.reset()
            paint.isAntiAlias = true
            paint.alpha = ((depthNode?.opacity ?: 1f) * 255).roundToInt().coerceIn(0, 255)
            drawBitmapCenterCrop(cc, depthMask, screenW, screenH, paint)
        }
        completeBmp.setHasAlpha(false)

        return BakedMinuteScene(
            clockLayerBitmap = clockLayerBmp,
            completeLockscreenBitmap = completeBmp,
            showDepth = showDepth,
            clockCenterX = clockCx,
            clockCenterY = clockCy
        )
    }

    fun drawWakeAnimationFrame(
        canvas: Canvas,
        w: Int,
        h: Int,
        project: Project,
        sharpBg: Bitmap?,
        scene: BakedMinuteScene,
        depthMask: Bitmap?,
        wakeProgress: Float,
        lockAlpha: Float
    ) {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        val p = wakeProgress.coerceIn(0f, 1f)
        val la = lockAlpha.coerceIn(0f, 1f)

        if (p >= 0.998f && la >= 0.998f && !scene.completeLockscreenBitmap.isRecycled) {
            canvas.drawBitmap(scene.completeLockscreenBitmap, 0f, 0f, null)
            return
        }

        val baseSharp = sharpBgBmp ?: sharpBg
        if (baseSharp != null) {
            if (bgNode.hasDisplayList() && lastBgW == screenW && lastBgH == screenH) {
                canvas.drawRenderNode(bgNode)
            } else {
                drawBitmapCenterCrop(canvas, baseSharp, screenW, screenH, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } else {
            canvas.drawColor(0xFF0A0D14.toInt())
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
        }

        val combinedAlpha = (p * la * 255f).roundToInt().coerceIn(0, 255)
        if (combinedAlpha > 0 && !scene.clockLayerBitmap.isRecycled) {
            val u = screenW / 1080f
            val wakeOffset = (1f - p) * 42f * u
            val wakeScale = (0.96f + 0.04f * p).coerceAtLeast(0.5f)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = combinedAlpha

            canvas.save()
            canvas.translate(scene.clockCenterX, scene.clockCenterY + wakeOffset)
            canvas.scale(wakeScale, wakeScale)
            canvas.translate(-scene.clockCenterX, -scene.clockCenterY)
            canvas.drawBitmap(scene.clockLayerBitmap, 0f, 0f, paint)
            canvas.restore()
        }

        if (scene.showDepth && depthMask != null && la > 0.01f) {
            val depthNode = project.layers.firstOrNull { it.type == LayerType.DEPTH }
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = ((depthNode?.opacity ?: 1f) * la * 255f).roundToInt().coerceIn(0, 255)
            drawBitmapCenterCrop(canvas, depthMask, screenW, screenH, paint)
        }
    }

    fun drawUnlockedHomeFrame(
        canvas: Canvas,
        w: Int,
        h: Int,
        project: Project,
        sharpBg: Bitmap?
    ) {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        val baseSharp = sharpBgBmp ?: sharpBg
        if (baseSharp != null) {
            drawBitmapCenterCrop(canvas, baseSharp, screenW, screenH, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(0xFF0A0D14.toInt())
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
        }
    }

    private fun renderNodeToBitmap(node: RenderNode, w: Int, h: Int): Bitmap = synchronized(blurLock) {
        if (blurThread == null) {
            val t = HandlerThread("PrismBlurGen").apply { start() }
            blurThread = t
            blurHandler = Handler(t.looper)
        }
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val hr = HardwareRenderer().apply {
            setSurface(reader.surface)
            setContentRoot(node)
        }
        val latch = CountDownLatch(1)
        reader.setOnImageAvailableListener({ latch.countDown() }, blurHandler)
        hr.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        var img = reader.acquireLatestImage()
        if (img == null) {
            latch.await(200, TimeUnit.MILLISECONDS)
            img = reader.acquireLatestImage()
        }
        reader.setOnImageAvailableListener(null, null)
        val bmp = img?.use { image ->
            val hwBuf = image.hardwareBuffer ?: return@use null
            hwBuf.use { buf ->
                val hwBmp = Bitmap.wrapHardwareBuffer(buf, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return@use null
                val copy = hwBmp.copy(Bitmap.Config.ARGB_8888, true)
                hwBmp.recycle()
                copy
            }
        }
        runCatching { hr.destroy() }
        runCatching { reader.close() }
        bmp ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun ensureSharpBg(bg: Bitmap, w: Int, h: Int) = synchronized(bgLock) {
        val id = System.identityHashCode(bg)
        val bmp = sharpBgBmp
        if (id == lastBgId && w == lastBgW && h == lastBgH && bmp != null && sharpBgShader != null && bgNode.hasDisplayList()) {
            return
        }
        lastBgId = id
        lastBgW = w
        lastBgH = h
        frostedShaderCache.evictAll()

        val exactSharp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sc = Canvas(exactSharp)
        sc.drawColor(Color.BLACK)
        drawBitmapCenterCrop(sc, bg, w, h, Paint(Paint.FILTER_BITMAP_FLAG), Matrix())
        exactSharp.setHasAlpha(false)

        sharpBgBmp = exactSharp
        sharpBgShader = BitmapShader(exactSharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        bgNode.setPosition(0, 0, w, h)
        val rec = bgNode.beginRecording(w, h)
        rec.drawBitmap(exactSharp, 0f, 0f, null)
        bgNode.endRecording()
    }

    private fun getFrostedShader(blurPx: Float, w: Int, h: Int): BitmapShader {
        val sharpShader = sharpBgShader!!
        if (blurPx <= 0.5f) return sharpShader
        prebakedFrostedShader?.let { return it }
        val radius = blurPx.roundToInt().coerceIn(1, 120)
        val key = "$lastBgId|${w}x$h|$radius"
        frostedShaderCache.get(key)?.let { return it }

        val src = sharpBgBmp!!
        val blurred = renderBlurredBitmap(src, w, h, radius.toFloat())
        blurred.setHasAlpha(false)
        val shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        frostedShaderCache.put(key, shader)
        return shader
    }

    private fun renderBlurredBitmap(src: Bitmap, w: Int, h: Int, radius: Float): Bitmap = synchronized(blurLock) {
        if (blurThread == null) {
            val t = HandlerThread("PrismBlurGen").apply { start() }
            blurThread = t
            blurHandler = Handler(t.looper)
        }
        if (blurImageReader == null || blurHwRenderer == null || blurReaderW != w || blurReaderH != h) {
            runCatching { blurHwRenderer?.destroy() }
            runCatching { blurImageReader?.close() }
            val reader = ImageReader.newInstance(
                w, h, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            )
            val hr = HardwareRenderer().apply {
                setSurface(reader.surface)
                setContentRoot(blurGenNode)
            }
            blurImageReader = reader
            blurHwRenderer = hr
            blurReaderW = w
            blurReaderH = h
        }
        val reader = blurImageReader!!
        val hr = blurHwRenderer!!
        val latch = CountDownLatch(1)
        reader.setOnImageAvailableListener({ latch.countDown() }, blurHandler)

        blurGenNode.setPosition(0, 0, w, h)
        blurGenNode.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        val rec = blurGenNode.beginRecording(w, h)
        rec.drawColor(Color.BLACK)
        rec.drawBitmap(src, 0f, 0f, null)
        blurGenNode.endRecording()

        while (true) {
            val stale = reader.acquireNextImage() ?: break
            stale.close()
        }
        hr.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        var img = reader.acquireLatestImage()
        if (img == null) {
            latch.await(200, TimeUnit.MILLISECONDS)
            img = reader.acquireLatestImage()
        }
        reader.setOnImageAvailableListener(null, null)

        val gpuResult = img?.use { image ->
            val hwBuf = image.hardwareBuffer ?: return@use null
            hwBuf.use { buf ->
                val hwBmp = Bitmap.wrapHardwareBuffer(buf, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return@use null
                val copy = hwBmp.copy(Bitmap.Config.ARGB_8888, true)
                hwBmp.recycle()
                copy
            }
        }
        if (gpuResult != null) return gpuResult
        return fastBoxBlur(src, radius.roundToInt().coerceIn(1, 80))
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        val tmp = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val r = radius.coerceIn(1, 60)
        val div = r * 2 + 1
        repeat(2) {
            for (y in 0 until h) {
                val rowOff = y * w
                var rSum = 0; var gSum = 0; var bSum = 0
                for (dx in -r..r) {
                    val c = pix[rowOff + dx.coerceIn(0, w - 1)]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    tmp[rowOff + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val addC = pix[rowOff + (x + r + 1).coerceIn(0, w - 1)]
                    val remC = pix[rowOff + (x - r).coerceIn(0, w - 1)]
                    rSum += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                    gSum += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                    bSum += (addC and 0xFF) - (remC and 0xFF)
                }
            }
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0
                for (dy in -r..r) {
                    val c = tmp[dy.coerceIn(0, h - 1) * w + x]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    pix[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val addC = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                    val remC = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                    rSum += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                    gSum += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                    bSum += (addC and 0xFF) - (remC and 0xFF)
                }
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, bmp: Bitmap, w: Int, h: Int, p: Paint, m: Matrix = tmpMatrix) {
        val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val dx = (w - bmp.width * scale) / 2f
        val dy = (h - bmp.height * scale) / 2f
        m.setScale(scale, scale)
        m.postTranslate(dx, dy)
        canvas.drawBitmap(bmp, m, p)
    }

    private fun checkCoverage(mask: Bitmap, bounds: RectF, w: Int, h: Int): Boolean {
        val scale = maxOf(w.toFloat() / mask.width, h.toFloat() / mask.height)
        val dx = (w - mask.width * scale) / 2f
        val dy = (h - mask.height * scale) / 2f
        val step = 10
        var total = 0
        var covered = 0
        var y = bounds.top.coerceIn(0f, h - 1f)
        while (y < bounds.bottom.coerceAtMost(h.toFloat())) {
            var x = bounds.left.coerceIn(0f, w - 1f)
            while (x < bounds.right.coerceAtMost(w.toFloat())) {
                val mx = ((x - dx) / scale).toInt().coerceIn(0, mask.width - 1)
                val my = ((y - dy) / scale).toInt().coerceIn(0, mask.height - 1)
                if (Color.alpha(mask.getPixel(mx, my)) > 110) covered++
                total++
                x += step
            }
            y += step
        }
        return total == 0 || (covered.toFloat() / total) <= 0.35f
    }

    private fun isVisible(n: LayerNode, env: Env): Boolean {
        if (n.hidden) return false
        return when (n.cond) {
            Cond.ALWAYS -> true
            Cond.LOCK_ONLY -> env.locked
            Cond.HOME_ONLY -> !env.locked
            Cond.CHARGING -> env.charging
            Cond.NOT_CHARGING -> !env.charging
            Cond.LOW_BATTERY -> env.battery <= 20
            Cond.DAY -> env.isDay
            Cond.NIGHT -> !env.isDay
        }
    }

    private data class Measured(
        val w: Float,
        val h: Float,
        val path: Path?,
        val geomKey: String,
        val children: List<Measured> = emptyList(),
        val evalText: String = ""
    )

    private fun measureNode(
        node: LayerNode,
        u: Float,
        env: Env,
        now: LocalDateTime,
        workPaint: Paint = paint,
        workMatrix: Matrix = tmpMatrix
    ): Measured {
        return when (node.type) {
            LayerType.TEXT -> {
                val s = Dynamic.eval(node.text, env, now)
                val shape = StemStretch.shape(s, node, u, workPaint)
                val gKey = "T|$s|${node.font}|${node.size}|${node.weight}|${node.axes}|${node.stretch}|${node.strokeCompensation}|${node.letterSpacing}|${node.lineSpacing}|${node.align}|$u"
                Measured(shape.width, shape.height, shape.path, gKey, evalText = s)
            }
            LayerType.SHAPE -> {
                val w = node.w * u
                val h = node.h * u
                val frac = (Dynamic.number(node.value, env, now) / 100.0).toFloat().coerceIn(0f, 1f)
                val p = buildShapePath(node, w, h, u, frac)
                val gKey = "S|${node.shape}|$w|$h|${node.corner}|${node.strokeWidth}|$frac|$u"
                Measured(w, h, p, gKey)
            }
            LayerType.ICON, LayerType.SVG -> {
                val w = node.w * u
                val h = node.h * u
                val iconName = if (node.type == LayerType.ICON) {
                    if (node.icon == "weather") Icons.weatherIcon(env.weatherCode, env.isDay) else node.icon
                } else ""
                val raw = if (node.type == LayerType.ICON) Icons.builtinPath(iconName) else Icons.path(node.svgData)
                val srcW = (if (node.type == LayerType.ICON) 24f else node.svgW).coerceAtLeast(1f)
                val srcH = (if (node.type == LayerType.ICON) 24f else node.svgH).coerceAtLeast(1f)
                val out = Path(raw)
                workMatrix.setScale(w / srcW, h / srcH)
                out.transform(workMatrix)
                val gKey = if (node.type == LayerType.ICON) "I|$iconName|$w|$h" else "V|${node.svgData.hashCode()}|$w|$h"
                Measured(w, h, out, gKey)
            }
            LayerType.STACK -> {
                val visibleKids = node.children.filter { isVisible(it, env) && it.type != LayerType.DEPTH }
                val kids = visibleKids.map { measureNode(it, u, env, now, workPaint, workMatrix) }
                val gap = node.spacing * u
                if (node.dir == Dir.VERTICAL) {
                    val mw = kids.maxOfOrNull { it.w * it.scaleSafe(visibleKids, kids) } ?: 0f
                    val mh = kids.sumOf { it.h.toDouble() }.toFloat() + gap * (kids.size - 1).coerceAtLeast(0)
                    Measured(mw, mh.coerceAtLeast(0f), null, "K", kids)
                } else {
                    val mw = kids.sumOf { it.w.toDouble() }.toFloat() + gap * (kids.size - 1).coerceAtLeast(0)
                    val mh = kids.maxOfOrNull { it.h } ?: 0f
                    Measured(mw.coerceAtLeast(0f), mh, null, "K", kids)
                }
            }
            LayerType.DEPTH -> Measured(0f, 0f, null, "D")
        }
    }

    private fun Measured.scaleSafe(nodes: List<LayerNode>, all: List<Measured>): Float {
        val idx = all.indexOf(this)
        return if (idx in nodes.indices) nodes[idx].scale else 1f
    }

    private fun buildShapePath(node: LayerNode, w: Float, h: Float, u: Float, frac: Float): Path {
        val p = Path()
        val c = node.corner * u
        when (node.shape) {
            ShapeKind.RECT -> p.addRoundRect(0f, 0f, w, h, c, c, Path.Direction.CW)
            ShapeKind.CIRCLE -> p.addOval(0f, 0f, w, h, Path.Direction.CW)
            ShapeKind.LINE -> p.addRoundRect(0f, 0f, w, h.coerceAtLeast(2f), h / 2f, h / 2f, Path.Direction.CW)
            ShapeKind.BAR -> p.addRoundRect(0f, 0f, (w * frac).coerceAtLeast(h.coerceAtMost(w)), h, c, c, Path.Direction.CW)
            ShapeKind.RING -> {
                val sw = (node.strokeWidth.takeIf { it > 0f } ?: 12f) * u
                val inset = sw / 2f
                val oval = RectF(inset, inset, (w - inset).coerceAtLeast(inset + 1f), (h - inset).coerceAtLeast(inset + 1f))
                val arc = Path()
                arc.addArc(oval, -90f, 360f * frac)
                val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
                stroke.getFillPath(arc, p)
            }
        }
        return p
    }

    private fun anchorOffset(anchor: Anchor, align: Align, width: Float, height: Float): Pair<Float, Float> {
        val ax = when (anchor) {
            Anchor.TOP_LEFT, Anchor.CENTER_LEFT, Anchor.BOTTOM_LEFT -> if (align == Align.START) 0f else alignOffset(align, width)
            Anchor.TOP_CENTER, Anchor.CENTER, Anchor.BOTTOM_CENTER -> -width / 2f
            Anchor.TOP_RIGHT, Anchor.CENTER_RIGHT, Anchor.BOTTOM_RIGHT -> -width
        }
        val ay = when (anchor) {
            Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT -> 0f
            Anchor.CENTER_LEFT, Anchor.CENTER, Anchor.CENTER_RIGHT -> -height / 2f
            Anchor.BOTTOM_LEFT, Anchor.BOTTOM_CENTER, Anchor.BOTTOM_RIGHT -> -height
        }
        return ax to ay
    }

    private fun alignOffset(align: Align, width: Float): Float = when (align) {
        Align.START -> 0f
        Align.CENTER -> -width / 2f
        Align.END -> -width
    }

    private fun drawNode(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        u: Float,
        node: LayerNode,
        m: Measured,
        bg: Bitmap?,
        env: Env,
        now: LocalDateTime,
        adaptive: Int,
        alpha: Float,
        wakeDy: Float,
        wakeScale: Float,
        restPoseKey: String,
        hits: MutableList<HitBox>?,
        originX: Float? = null,
        originY: Float? = null
    ) {
        val (ox, oy) = if (originX == null) anchorOffset(node.anchor, node.align, m.w, m.h) else 0f to 0f
        val left = originX ?: (node.x * screenW + ox)
        val top = (originY ?: (node.y * screenH + oy)) + wakeDy
        val cx = left + m.w / 2f
        val cy = top + m.h / 2f
        val sc = node.scale * wakeScale

        canvas.save()
        canvas.translate(cx, cy)
        if (node.rotation != 0f) canvas.rotate(node.rotation)
        if (sc != 1f) canvas.scale(sc, sc)
        canvas.translate(-m.w / 2f, -m.h / 2f)

        @Suppress("DEPRECATION")
        canvas.getMatrix(tmpMatrix)
        localToScreen.set(invRootMatrix)
        localToScreen.preConcat(tmpMatrix)
        tmpBounds.set(0f, 0f, m.w, m.h)
        localToScreen.mapRect(tmpBounds)
        val screenRect = RectF(tmpBounds)
        val boxInv = Matrix()
        localToScreen.invert(boxInv)
        hits?.add(HitBox(node.id, screenRect, m.w, m.h, boxInv, node.type))

        if (node.type == LayerType.STACK) {
            val kids = node.children.filter { isVisible(it, env) && it.type != LayerType.DEPTH }
            val gap = node.spacing * u
            var cur = 0f
            kids.forEachIndexed { idx, child ->
                val cm = m.children[idx]
                val kx = if (node.dir == Dir.VERTICAL) when (node.align) {
                    Align.START -> 0f; Align.CENTER -> (m.w - cm.w) / 2f; Align.END -> m.w - cm.w
                } else cur
                val ky = if (node.dir == Dir.HORIZONTAL) (m.h - cm.h) / 2f else cur
                drawNode(
                    canvas = canvas,
                    screenW = screenW,
                    screenH = screenH,
                    u = u,
                    node = child,
                    m = cm,
                    bg = bg,
                    env = env,
                    now = now,
                    adaptive = adaptive,
                    alpha = alpha * child.opacity,
                    wakeDy = 0f,
                    wakeScale = 1f,
                    restPoseKey = "$restPoseKey|${node.dir},${node.align},$gap,$idx,$kx,$ky,${child.scale},${child.rotation}",
                    hits = hits,
                    originX = kx,
                    originY = ky
                )
                cur += (if (node.dir == Dir.VERTICAL) cm.h else cm.w) + gap
            }
            canvas.restore()
            return
        }

        if (node.type == LayerType.SHAPE && (node.shape == ShapeKind.BAR || node.shape == ShapeKind.RING)) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = applyAlpha(node.trackColor, alpha)
            if (node.shape == ShapeKind.BAR) {
                val c = node.corner * u
                canvas.drawRoundRect(0f, 0f, m.w, m.h, c, c, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (node.strokeWidth.takeIf { it > 0f } ?: 12f) * u
                val inset = paint.strokeWidth / 2f
                canvas.drawOval(inset, inset, (m.w - inset).coerceAtLeast(inset + 1f), (m.h - inset).coerceAtLeast(inset + 1f), paint)
            }
        }

        val path = m.path
        if (path != null && !path.isEmpty) {
            val blend = toBlendMode(node.blend)
            if ((node.fill == Fill.GLASS || node.fill == Fill.CRYSTAL) && bg != null && canvas.isHardwareAccelerated) {
                val animScreenPath = Path(path).apply { transform(localToScreen) }
                val hasScreenInv = localToScreen.invert(screenToLocal)
                if (hasScreenInv) {
                    if (node.fill == Fill.CRYSTAL) {
                        val restMat = Matrix(localToScreen)
                        val restPath = if (wakeDeltaMatrix.isIdentity) {
                            animScreenPath
                        } else {
                            wakeDeltaMatrix.invert(restingMatrix)
                            restingMatrix.preConcat(localToScreen)
                            restMat.set(restingMatrix)
                            Path(path).apply { transform(restingMatrix) }
                        }
                        drawOpticalCrystal(
                            canvas = canvas,
                            nodeId = node.id,
                            screenW = screenW,
                            screenH = screenH,
                            u = u,
                            animScreenPath = animScreenPath,
                            restScreenPath = restPath,
                            restLocalToScreen = restMat,
                            evalText = m.evalText,
                            geomKey = "${m.geomKey}|$restPoseKey",
                            g = node.glass,
                            screenRect = screenRect,
                            alpha = alpha,
                            blend = blend
                        )
                    } else {
                        drawClassicBlur(
                            canvas = canvas,
                            screenW = screenW,
                            screenH = screenH,
                            u = u,
                            localPath = path,
                            animScreenPath = animScreenPath,
                            g = node.glass,
                            boxW = m.w,
                            boxH = m.h,
                            alpha = alpha,
                            blend = blend
                        )
                    }
                }
            } else if (node.fill != Fill.HOLLOW) {
                paint.reset()
                paint.isAntiAlias = true
                paint.isSubpixelText = true
                paint.blendMode = blend
                when (node.fill) {
                    Fill.SOLID, Fill.GLASS, Fill.CRYSTAL, Fill.HOLLOW -> paint.color = applyAlpha(node.color, alpha)
                    Fill.ADAPTIVE -> paint.color = applyAlpha(adaptive, alpha)
                    Fill.GRADIENT -> paint.shader = makeGradientShader(node, m.w, m.h, alpha)
                }
                if (node.shadowRadius > 0f) {
                    paint.setShadowLayer(node.shadowRadius * u, node.shadowDx * u, node.shadowDy * u, applyAlpha(node.shadowColor, alpha))
                }
                canvas.drawPath(path, paint)
            }

            val effStroke = if (node.fill == Fill.HOLLOW && node.strokeWidth <= 0f) 4f else node.strokeWidth
            if (effStroke > 0f && !(node.type == LayerType.SHAPE && node.shape == ShapeKind.RING)) {
                strokePaint.strokeWidth = effStroke * u
                strokePaint.blendMode = blend
                if (node.fill == Fill.HOLLOW && node.color2 != node.color) {
                    strokePaint.shader = makeGradientShader(node, m.w, m.h, alpha)
                } else {
                    strokePaint.shader = null
                    strokePaint.color = applyAlpha(if (node.fill == Fill.HOLLOW) node.color else node.strokeColor, alpha)
                }
                canvas.drawPath(path, strokePaint)
                strokePaint.shader = null
            }
        }
        canvas.restore()
    }

    private fun makeGradientShader(node: LayerNode, w: Float, h: Float, alpha: Float): Shader {
        val c1 = applyAlpha(node.color, alpha)
        val c2 = applyAlpha(node.color2, alpha)
        return when (node.gradientKind) {
            GradientKind.RADIAL -> RadialGradient(
                w * 0.5f, h * 0.5f, (maxOf(w, h) * 0.6f).coerceAtLeast(1f),
                c1, c2, Shader.TileMode.CLAMP
            )
            GradientKind.SWEEP -> SweepGradient(w * 0.5f, h * 0.5f, intArrayOf(c1, c2, c1), null)
            GradientKind.LINEAR -> {
                val rad = Math.toRadians(node.gradientAngle.toDouble())
                val dx = cos(rad).toFloat() * w * 0.5f
                val dy = sin(rad).toFloat() * h * 0.5f
                LinearGradient(w * 0.5f - dx, h * 0.5f - dy, w * 0.5f + dx, h * 0.5f + dy, c1, c2, Shader.TileMode.CLAMP)
            }
        }
    }

    /**
     * 1:1 Screen-Aligned Multi-Pass Chamfer Height Map (ALPHA_8, exact w x h):
     * Preserves Path.FillType (inner holes of 0, 6, 8, 9), blends two Gaussian passes
     * (edge bevel + interior dome), and masks strictly inside the glyph silhouette (DST_IN).
     */
    private fun getLensHeightMap(
        nodeId: String,
        restScreenPath: Path,
        screenW: Int,
        screenH: Int,
        bevelPx: Float,
        geomKey: String,
        restLocalToScreen: Matrix? = null,
        evalText: String = "",
        u: Float = screenW / 1080f
    ): Bitmap = synchronized(maskLock) {
        val key = "$nodeId|${screenW}x$screenH|${bevelPx.roundToInt()}|$geomKey"
        lensHeightCache.get(key)?.let { return it }

        if (allowStaleLensOnMiss) {
            lastLensByNode[nodeId]?.takeIf { !it.isRecycled && it.width == screenW && it.height == screenH }?.let {
                return it
            }
        }

        if (restLocalToScreen != null && evalText.isNotEmpty()) {
            val regNode = GlyphAtlasBaker.getRegisteredNode(nodeId)
            if (regNode != null) {
                GlyphAtlasBaker.tryAssembleScreenLens(
                    node = regNode,
                    text = evalText,
                    localToScreen = restLocalToScreen,
                    screenW = screenW,
                    screenH = screenH,
                    u = u,
                    bevelPx = bevelPx
                )?.let { atlasOut ->
                    lensHeightCache.put(key, atlasOut)
                    lastLensByNode[nodeId] = atlasOut
                    return atlasOut
                }
            }
        }

        val sharp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ALPHA_8)
        val sc = Canvas(sharp)
        maskPaint.maskFilter = null
        maskPaint.alpha = 255
        sc.drawPath(restScreenPath, maskPaint)

        val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ALPHA_8)
        val oc = Canvas(out)
        val r1 = (bevelPx * 0.45f).coerceAtLeast(1f)
        val r2 = (bevelPx * 1.45f).coerceAtLeast(2f)
        maskPaint.maskFilter = BlurMaskFilter(r1, BlurMaskFilter.Blur.NORMAL)
        maskPaint.alpha = 145
        oc.drawPath(restScreenPath, maskPaint)
        maskPaint.maskFilter = BlurMaskFilter(r2, BlurMaskFilter.Blur.NORMAL)
        maskPaint.alpha = 115
        oc.drawPath(restScreenPath, maskPaint)
        maskPaint.maskFilter = null
        maskPaint.alpha = 255

        oc.drawBitmap(sharp, 0f, 0f, maskCutPaint)
        sharp.recycle()

        lensHeightCache.put(key, out)
        lastLensByNode[nodeId] = out
        return out
    }

    private fun getLensShader(nodeId: String, lensBmp: Bitmap): BitmapShader {
        val existing = lensShaderCache[nodeId]
        if (existing != null && existing.first === lensBmp) {
            existing.second.setLocalMatrix(wakeDeltaMatrix)
            return existing.second
        }
        val shader = BitmapShader(lensBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(wakeDeltaMatrix)
        lensShaderCache[nodeId] = lensBmp to shader
        return shader
    }

    private fun drawClassicBlur(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        u: Float,
        localPath: Path,
        animScreenPath: Path,
        g: Glass,
        boxW: Float,
        boxH: Float,
        alpha: Float,
        blend: BlendMode
    ) {
        val blurPx = (g.blur * u).coerceIn(0f, 80f * u)
        val frostedShader = getFrostedShader(blurPx, screenW, screenH)
        val r = Color.red(g.tint) / 255f
        val gr = Color.green(g.tint) / 255f
        val b = Color.blue(g.tint) / 255f
        val a = Color.alpha(g.tint) / 255f

        blurShader.setInputShader("content", frostedShader)
        blurShader.setFloatUniform("uResolution", screenW.toFloat(), screenH.toFloat())
        blurShader.setFloatUniform("uSaturation", g.saturation)
        blurShader.setFloatUniform("uTint", r, gr, b, a)
        blurShader.setFloatUniform("uGrain", g.grain)

        glassPaint.reset()
        glassPaint.isAntiAlias = true
        glassPaint.shader = blurShader
        glassPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        glassPaint.blendMode = blend

        canvas.save()
        canvas.concat(screenToLocal)
        canvas.drawPath(animScreenPath, glassPaint)
        canvas.restore()

        if (g.rim > 0.01f) {
            val rad = Math.toRadians(g.lightAngle.toDouble())
            val rx = cos(rad).toFloat() * boxW * 0.6f
            val ry = sin(rad).toFloat() * boxH * 0.6f
            val rimAlpha = (g.rim * alpha * 190).roundToInt().coerceIn(0, 255)
            rimPaint.strokeWidth = (2.6f * u).coerceAtLeast(1.8f)
            rimPaint.shader = LinearGradient(
                boxW * 0.5f + rx, boxH * 0.5f + ry, boxW * 0.5f - rx, boxH * 0.5f - ry,
                intArrayOf(Color.argb(rimAlpha, 255, 255, 255), Color.argb(rimAlpha / 6, 255, 255, 255), Color.argb(rimAlpha / 2, 210, 232, 255)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipPath(localPath)
            canvas.drawPath(localPath, rimPaint)
            canvas.restore()
        }
    }

    private fun drawOpticalCrystal(
        canvas: Canvas,
        nodeId: String,
        screenW: Int,
        screenH: Int,
        u: Float,
        animScreenPath: Path,
        restScreenPath: Path,
        restLocalToScreen: Matrix,
        evalText: String,
        geomKey: String,
        g: Glass,
        screenRect: RectF,
        alpha: Float,
        blend: BlendMode
    ) {
        val blurPx = if (g.frostAmount > 0.01f) (g.blur.coerceAtLeast(18f * g.frostAmount) * u).coerceIn(0f, 80f * u) else 0f
        val bevelPx = (g.bevelWidth * u).coerceIn(2f, 64f * u)
        val sharpShader = sharpBgShader ?: return
        val frostedShader = getFrostedShader(blurPx, screenW, screenH)
        val lensBmp = getLensHeightMap(nodeId, restScreenPath, screenW, screenH, bevelPx, geomKey, restLocalToScreen, evalText, u)
        val lensShader = getLensShader(nodeId, lensBmp)

        val r = Color.red(g.tint) / 255f
        val gr = Color.green(g.tint) / 255f
        val b = Color.blue(g.tint) / 255f
        val a = Color.alpha(g.tint) / 255f

        crystalShader.setInputShader("backgroundTex", sharpShader)
        crystalShader.setInputShader("frostedTex", frostedShader)
        crystalShader.setInputShader("lensHeightMap", lensShader)
        crystalShader.setFloatUniform("uResolution", screenW.toFloat(), screenH.toFloat())
        crystalShader.setFloatUniform("uBoxSize", screenRect.width().coerceAtLeast(1f), screenRect.height().coerceAtLeast(1f))
        crystalShader.setFloatUniform("uRefractionStrength", (g.refraction / 36f) * u)
        crystalShader.setFloatUniform("uBevelStrength", (bevelPx * 0.18f).coerceIn(1.2f, 9f))
        crystalShader.setFloatUniform("uDispersion", g.chromaticAberration)
        crystalShader.setFloatUniform("uFrostAmount", g.frostAmount)
        crystalShader.setFloatUniform("uRimIntensity", g.rim)
        crystalShader.setFloatUniform("uLightAngle", Math.toRadians(g.lightAngle.toDouble()).toFloat())
        crystalShader.setFloatUniform("uSaturation", g.saturation)
        crystalShader.setFloatUniform("uTint", r, gr, b, a)

        glassPaint.reset()
        glassPaint.isAntiAlias = true
        glassPaint.shader = crystalShader
        glassPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        glassPaint.blendMode = blend

        canvas.save()
        canvas.concat(screenToLocal)
        canvas.drawPath(animScreenPath, glassPaint)
        canvas.restore()
    }

    private fun applyAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun toBlendMode(b: Blend): BlendMode = when (b) {
        Blend.NORMAL -> BlendMode.SRC_OVER
        Blend.MULTIPLY -> BlendMode.MULTIPLY
        Blend.SCREEN -> BlendMode.SCREEN
        Blend.OVERLAY -> BlendMode.OVERLAY
        Blend.DARKEN -> BlendMode.DARKEN
        Blend.LIGHTEN -> BlendMode.LIGHTEN
        Blend.COLOR_DODGE -> BlendMode.COLOR_DODGE
        Blend.COLOR_BURN -> BlendMode.COLOR_BURN
        Blend.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
        Blend.HARD_LIGHT -> BlendMode.HARD_LIGHT
        Blend.DIFFERENCE -> BlendMode.DIFFERENCE
        Blend.EXCLUSION -> BlendMode.EXCLUSION
        Blend.PLUS -> BlendMode.PLUS
        Blend.HUE -> BlendMode.HUE
        Blend.SATURATION -> BlendMode.SATURATION
        Blend.COLOR -> BlendMode.COLOR
        Blend.LUMINOSITY -> BlendMode.LUMINOSITY
    }
}
