package com.example.prismlock.engine

import android.content.Context
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
import com.example.prismlock.model.collectNestedChildIds
import com.example.prismlock.model.flatten
import com.example.prismlock.wallpaper.WallpaperCarousel
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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

        // Mode D: Optical Crystal (full-stroke 3D normal + 0..100% linear refraction + RGB dispersion + frost mix + directional light & Fresnel)
        private const val CRYSTAL_AGSL = """
            uniform shader backgroundTex;
            uniform shader frostedTex;
            uniform shader lensHeightMap;
            uniform float2 uResolution;
            uniform float2 uBoxSize;
            uniform float uRefractionOffsetPx;
            uniform float uBevelRadius;
            uniform float uStrokeRadius;
            uniform float uDispersion;
            uniform float uFrostAmount;
            uniform float uFresnelIntensity;
            uniform float uLightAngle;
            uniform float uSaturation;
            uniform float4 uTint;

            half4 main(float2 fragCoord) {
                float2 maxBounds = max(uResolution - float2(2.0), float2(2.0));
                float2 safeMask = clamp(fragCoord, float2(2.0), maxBounds);
                float maskAlpha = float(lensHeightMap.eval(safeMask).a);

                float stepPx = clamp(max(uBevelRadius, uStrokeRadius) * 0.12, 1.5, 5.5);
                float hL = float(lensHeightMap.eval(clamp(fragCoord - float2(stepPx, 0.0), float2(2.0), maxBounds)).a);
                float hR = float(lensHeightMap.eval(clamp(fragCoord + float2(stepPx, 0.0), float2(2.0), maxBounds)).a);
                float hU = float(lensHeightMap.eval(clamp(fragCoord - float2(0.0, stepPx), float2(2.0), maxBounds)).a);
                float hD = float(lensHeightMap.eval(clamp(fragCoord + float2(0.0, stepPx), float2(2.0), maxBounds)).a);

                float edgeAtten = smoothstep(0.02, 0.22, maskAlpha);
                float slopeGain = clamp((max(uBevelRadius, uStrokeRadius) * 0.45) / max(stepPx, 1.0), 1.8, 8.0);
                float dx = (hL - hR) * slopeGain * edgeAtten;
                float dy = (hU - hD) * slopeGain * edgeAtten;
                float3 normal = normalize(float3(dx, dy, 1.0));

                float2 refractedUV = fragCoord + normal.xy * uRefractionOffsetPx;
                float2 safeUV = clamp(refractedUV, float2(2.0), maxBounds);
                float2 disp = normal.xy * (uDispersion * (max(uBoxSize.x, uBoxSize.y) + uRefractionOffsetPx * 1.5));
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

                float2 lightVec = float2(cos(radians(uLightAngle)), sin(radians(uLightAngle)));
                float dirLight = pow(max(dot(normalize(normal.xy + float2(0.0001)), lightVec), 0.0), 3.0) * length(normal.xy);
                float fresnel = pow(clamp(1.0 - normal.z, 0.0, 1.0), 2.0);
                col += float3(1.0) * (dirLight * 0.65 + fresnel * 0.55) * uFresnelIntensity * edgeAtten;
                col = clamp(col, float3(0.0), float3(1.0));
                return half4(half3(col), 1.0);
            }
        """
    }

    private val blurShader = RuntimeShader(BLUR_AGSL)
    private val crystalShader = RuntimeShader(CRYSTAL_AGSL)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
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
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bgLock = Any()
    private val blurLock = Any()
    private val maskLock = Any()

    private val bgNode = RenderNode("prism_bg")
    private val blurGenNode = RenderNode("prism_frosted_gen")
    private val scaledBaseNode = RenderNode("prism_scaled_base")
    private val dstRectF = RectF()
    private var blurThread: HandlerThread? = null
    private var blurHandler: Handler? = null
    private var blurHwRenderer: HardwareRenderer? = null
    private var blurImageReader: ImageReader? = null
    private var blurReaderW = 0
    private var blurReaderH = 0
    private var sceneHwRenderer: HardwareRenderer? = null
    private var sceneImageReader: ImageReader? = null
    private var sceneReaderW = 0
    private var sceneReaderH = 0

    private val frostedShaderCache = LruCache<String, BitmapShader>(2)
    private val lensHeightCache = LruCache<String, Bitmap>(3)
    private val lastLensByNode = ConcurrentHashMap<String, Bitmap>()
    private val lastLensInvMatrixByNode = ConcurrentHashMap<String, Matrix>()
    private val lensShaderCache = ConcurrentHashMap<String, Pair<Bitmap, BitmapShader>>()
    @Volatile private var allowStaleLensOnMiss = false
    @Volatile private var lastScaledSceneKey = ""
    private val lastCachedHits = ArrayList<HitBox>()

    private val rootMatrix = Matrix()
    private val invRootMatrix = Matrix()
    private val tmpMatrix = Matrix()
    private val localToScreen = Matrix()
    private val screenToLocal = Matrix()
    private val restingMatrix = Matrix()
    private val wakeDeltaMatrix = Matrix()
    private val lensDeltaMatrix = Matrix()
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
    @Volatile private var prebakedFrostedBlurPx = -1f
    @Volatile private var liveVideoMode = false
    @Volatile private var liveVideoAdaptiveColor = 0xFF9AC5FF.toInt()
    @Volatile private var currentContext: Context? = null
    @Volatile private var activeCustomAssets: Map<String, String> = emptyMap()

    fun attachContext(ctx: Context) {
        currentContext = ctx.applicationContext
    }

    data class BakedMinuteScene(
        val clockLayerBitmap: Bitmap,
        val completeLockscreenBitmap: Bitmap? = null,
        val showDepth: Boolean,
        val clockCenterX: Float,
        val clockCenterY: Float
    ) {
        fun isSingleCompleteFrame(): Boolean =
            completeLockscreenBitmap != null && clockLayerBitmap === completeLockscreenBitmap

        fun trimToCompleteRgb565(): BakedMinuteScene {
            val complete = completeLockscreenBitmap ?: return this
            if (clockLayerBitmap !== complete && !clockLayerBitmap.isRecycled) {
                runCatching { clockLayerBitmap.recycle() }
            }
            return copy(clockLayerBitmap = complete, completeLockscreenBitmap = complete)
        }

        fun recycle() {
            runCatching { if (!clockLayerBitmap.isRecycled) clockLayerBitmap.recycle() }
            runCatching {
                if (completeLockscreenBitmap != null &&
                    completeLockscreenBitmap !== clockLayerBitmap &&
                    !completeLockscreenBitmap.isRecycled
                ) {
                    completeLockscreenBitmap.recycle()
                }
            }
        }
    }

    fun setAllowStaleLensOnMiss(enabled: Boolean) {
        allowStaleLensOnMiss = enabled
    }

    fun releaseOffscreenResources() {
        synchronized(blurLock) {
            runCatching { blurHwRenderer?.destroy() }
            runCatching { blurImageReader?.close() }
            runCatching { sceneHwRenderer?.destroy() }
            runCatching { sceneImageReader?.close() }
            runCatching { blurThread?.quitSafely() }
            blurHwRenderer = null
            blurImageReader = null
            blurReaderW = 0
            blurReaderH = 0
            sceneHwRenderer = null
            sceneImageReader = null
            sceneReaderW = 0
            sceneReaderH = 0
            blurThread = null
            blurHandler = null
        }
        runCatching { blurGenNode.discardDisplayList() }
        frostedShaderCache.evictAll()
        lensHeightCache.evictAll()
        lastLensByNode.clear()
        lastLensInvMatrixByNode.clear()
        lensShaderCache.clear()
    }

    fun release() {
        releaseOffscreenResources()
        sharpBgBmp = null
        sharpBgShader = null
        prebakedFrostedBmp = null
        prebakedFrostedShader = null
        prebakedFrostedBlurPx = -1f
        lastScaledSceneKey = ""
        lastCachedHits.clear()
        runCatching { bgNode.discardDisplayList() }
        runCatching { scaledBaseNode.discardDisplayList() }
    }

    fun clearShaderCachesForHome() {
        liveVideoMode = false
        lastBgId = 0
        sharpBgBmp = null
        sharpBgShader = null
        lastScaledSceneKey = ""
        lastCachedHits.clear()
        runCatching { bgNode.discardDisplayList() }
        prebakedFrostedBmp = null
        prebakedFrostedShader = null
        prebakedFrostedBlurPx = -1f
        frostedShaderCache.evictAll()
        lensHeightCache.evictAll()
        lastLensByNode.clear()
        lastLensInvMatrixByNode.clear()
        lensShaderCache.clear()
    }

    fun updateLiveVideoShaders(
        sharpBmp: Bitmap,
        sharpShader: BitmapShader,
        frostedBmp: Bitmap?,
        frostedShader: BitmapShader?,
        w: Int,
        h: Int,
        adaptiveColor: Int = liveVideoAdaptiveColor
    ) = synchronized(bgLock) {
        liveVideoMode = true
        liveVideoAdaptiveColor = adaptiveColor
        lastBgId = System.identityHashCode(sharpBmp)
        lastBgW = w
        lastBgH = h
        sharpBgBmp = sharpBmp
        sharpBgShader = sharpShader
        prebakedFrostedBmp = frostedBmp
        prebakedFrostedShader = frostedShader
        prebakedFrostedBlurPx = -1f
    }

    fun clearLiveVideoMode() = synchronized(bgLock) {
        if (liveVideoMode) {
            liveVideoMode = false
            sharpBgBmp = null
            sharpBgShader = null
            prebakedFrostedBmp = null
            prebakedFrostedShader = null
            lastBgId = 0
        }
    }

    fun drawLiveVideoFrame(
        canvas: Canvas,
        viewW: Int,
        viewH: Int,
        baseW: Int,
        baseH: Int,
        project: Project,
        sharpBmp: Bitmap,
        sharpShader: BitmapShader,
        frostedBmp: Bitmap?,
        frostedShader: BitmapShader?,
        env: Env,
        now: LocalDateTime,
        adaptiveColor: Int = liveVideoAdaptiveColor,
        hits: MutableList<HitBox>? = null
    ) {
        updateLiveVideoShaders(sharpBmp, sharpShader, frostedBmp, frostedShader, baseW, baseH, adaptiveColor)
        renderScaledBaseFrame(
            canvas = canvas,
            viewW = viewW,
            viewH = viewH,
            baseW = baseW,
            baseH = baseH,
            project = project,
            bg = sharpBmp,
            depthMask = null,
            env = env,
            now = now,
            hits = hits
        )
    }

    fun extractPalette(bmp: Bitmap?): List<Int> {
        if (bmp == null || bmp.config == Bitmap.Config.HARDWARE) return listOf(0xFFFFFFFF.toInt(), 0xFF8AB4F8.toInt(), 0xFFFFC1A6.toInt(), 0xFFA6FFD9.toInt(), 0xFFE3B8FF.toInt(), 0xFF1E2430.toInt())
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
        if (bmp.config == Bitmap.Config.HARDWARE) return liveVideoAdaptiveColor
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

        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        if (bg != null) {
            if (liveVideoMode && bg === sharpBgBmp) {
                canvas.drawBitmap(bg, 0f, 0f, null)
            } else {
                liveVideoMode = false
                ensureSharpBg(bg, screenW, screenH)
                canvas.drawRenderNode(bgNode)
            }
        } else {
            liveVideoMode = false
            canvas.drawColor(0xFF0A0D14.toInt(), PorterDuff.Mode.SRC)
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
        }

        val u = screenW / 1080f
        val adaptive = extractAdaptiveColor(bg)
        val globals = project.globalsMap()
        activeCustomAssets = project.customAssets
        val nestedChildIds = project.layers.collectNestedChildIds()
        val drawnIds = HashSet<String>(project.layers.size * 2)
        var mainBounds: RectF? = null

        for (node in project.layers) {
            if (node.parentId != null || node.id in nestedChildIds) continue
            if (!drawnIds.add(node.id)) continue
            if (!isVisible(node, env, now, globals)) continue
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

            val box = renderLayer(
                canvas = canvas,
                screenW = screenW,
                screenH = screenH,
                u = u,
                node = node,
                bg = bg,
                env = env,
                now = now,
                globals = globals,
                adaptive = adaptive,
                condAlpha = condAlpha,
                wakeProgress = wakeProgress,
                hits = hits
            )
            if (mainBounds == null && (node.type == LayerType.TEXT || node.type == LayerType.STACK)) {
                val (ox, oy) = anchorOffset(node.anchor, node.align, box.w, box.h)
                val px = node.x * screenW + ox
                val py = node.y * screenH + oy
                mainBounds = RectF(px, py, px + box.w, py + box.h)
            }
        }
    }

    fun renderLayer(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        u: Float,
        node: LayerNode,
        bg: Bitmap?,
        env: Env,
        now: LocalDateTime,
        adaptive: Int,
        condAlpha: Float = 1f,
        wakeProgress: Float = 1f,
        hits: MutableList<HitBox>? = null,
        globals: Map<String, String> = emptyMap()
    ): MeasuredBounds {
        val box = measureNode(node, u, env, now, globals)
        val effNode = box.resolvedNode
        GlyphAtlasBaker.registerNode(effNode)
        val wakeOffset = (1f - wakeProgress) * 42f * u
        val wakeScale = (0.96f + 0.04f * wakeProgress).coerceAtLeast(0.5f)
        val (rx, ry) = anchorOffset(effNode.anchor, effNode.align, box.w, box.h)
        val restCx = effNode.x * screenW + rx + box.dxPx + box.w * 0.5f
        val restCy = effNode.y * screenH + ry + box.dyPx + box.h * 0.5f

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
            node = effNode,
            m = box,
            bg = bg,
            env = env,
            now = now,
            globals = globals,
            adaptive = adaptive,
            alpha = effNode.opacity * condAlpha * wakeProgress.coerceIn(0f, 1f),
            wakeDy = wakeOffset,
            wakeScale = wakeScale,
            restPoseKey = "${effNode.x},${effNode.y},${effNode.anchor},${effNode.align},${effNode.scale},${effNode.rotation},${box.dxPx},${box.dyPx}",
            hits = hits
        )
        return MeasuredBounds(box.w, box.h)
    }

    data class MeasuredBounds(val w: Float, val h: Float)

    fun renderScaledBaseFrame(
        canvas: Canvas,
        viewW: Int,
        viewH: Int,
        baseW: Int,
        baseH: Int,
        project: Project,
        bg: Bitmap?,
        depthMask: Bitmap?,
        env: Env,
        now: LocalDateTime,
        hits: MutableList<HitBox>? = null,
        cacheKey: String? = null
    ) {
        val bw = baseW.coerceAtLeast(1)
        val bh = baseH.coerceAtLeast(1)
        if (cacheKey != null && cacheKey == lastScaledSceneKey && scaledBaseNode.hasDisplayList()) {
            if (hits != null) {
                hits.clear()
                hits.addAll(lastCachedHits)
            }
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
            canvas.save()
            canvas.scale(viewW.coerceAtLeast(1).toFloat() / bw, viewH.coerceAtLeast(1).toFloat() / bh)
            canvas.drawRenderNode(scaledBaseNode)
            canvas.restore()
            return
        }

        scaledBaseNode.setPosition(0, 0, bw, bh)
        val rec = scaledBaseNode.beginRecording(bw, bh)
        rec.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        render(
            canvas = rec,
            w = bw,
            h = bh,
            project = project,
            bg = bg,
            depthMask = depthMask,
            env = env,
            now = now,
            wakeProgress = 1f,
            lockAlpha = 1f,
            hits = hits
        )
        scaledBaseNode.endRecording()
        if (cacheKey != null) {
            lastScaledSceneKey = cacheKey
            lastCachedHits.clear()
            if (hits != null) lastCachedHits.addAll(hits)
        } else {
            lastScaledSceneKey = ""
        }

        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        canvas.save()
        canvas.scale(viewW.coerceAtLeast(1).toFloat() / bw, viewH.coerceAtLeast(1).toFloat() / bh)
        canvas.drawRenderNode(scaledBaseNode)
        canvas.restore()
    }

    fun renderPreviewBitmap(
        w: Int,
        h: Int,
        project: Project,
        bg: Bitmap?,
        depthMask: Bitmap?,
        env: Env,
        now: LocalDateTime
    ): Bitmap {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        val scene = bakeMinuteScene(
            w = screenW,
            h = screenH,
            project = project,
            sharpBg = bg,
            frostedBg = null,
            depthMask = depthMask,
            env = env.copy(locked = true),
            now = now,
            includeCompleteBitmap = true
        )
        scene.clockLayerBitmap.recycle()
        return scene.completeLockscreenBitmap!!
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
            val globals = project.globalsMap()
            activeCustomAssets = project.customAssets
            if (bg != null) ensureSharpBg(bg, screenW, screenH)

            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val workMat = Matrix()

            fun prepareNode(m: Measured, parentMat: Matrix, restPoseKey: String) {
                val node = m.resolvedNode
                GlyphAtlasBaker.registerNode(node)
                val (ox, oy) = if (parentMat.isIdentity) anchorOffset(node.anchor, node.align, m.w, m.h) else 0f to 0f
                val localMat = Matrix(parentMat)
                val left = (if (parentMat.isIdentity) node.x * screenW + ox else 0f) + m.dxPx
                val top = (if (parentMat.isIdentity) node.y * screenH + oy else 0f) + m.dyPx
                val cx = left + m.w * 0.5f
                val cy = top + m.h * 0.5f
                localMat.preTranslate(cx, cy)
                if (node.rotation != 0f) localMat.preRotate(node.rotation)
                if (node.scale != 1f) localMat.preScale(node.scale, node.scale)
                localMat.preTranslate(-m.w * 0.5f, -m.h * 0.5f)

                if (node.type == LayerType.STACK) {
                    val gap = node.spacing * u
                    var cur = 0f
                    m.children.forEachIndexed { idx, cm ->
                        val child = cm.resolvedNode
                        val kx = if (node.dir == Dir.VERTICAL) when (node.align) {
                            Align.START -> 0f; Align.CENTER -> (m.w - cm.w) / 2f; Align.END -> m.w - cm.w
                        } else cur
                        val ky = if (node.dir == Dir.HORIZONTAL) (m.h - cm.h) / 2f else cur
                        val childBase = Matrix(localMat).apply { preTranslate(kx, ky) }
                        prepareNode(
                            m = cm,
                            parentMat = childBase,
                            restPoseKey = "$restPoseKey|${node.dir},${node.align},$gap,$idx,$kx,$ky,${child.scale},${child.rotation},${cm.dxPx},${cm.dyPx}"
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
                    val bevelPx = (g.bevelWidth * u).coerceIn(1f, 48f * u)
                    val restScreenPath = Path(path).apply { transform(localMat) }
                    val imgMask = if (node.type == LayerType.IMAGE) resolveLayerImageBitmap(node, m.w, m.h) else null
                    getLensHeightMap(node.id, restScreenPath, screenW, screenH, bevelPx, m.baseStrokePx, "${m.geomKey}|$restPoseKey", localMat, m.evalText, m.baselineShiftY, u, imgMask, path, m.w, m.h)
                }
            }

            val nestedChildIds = project.layers.collectNestedChildIds()
            for (node in project.layers) {
                if (node.parentId != null || node.id in nestedChildIds) continue
                if (!isVisible(node, env, now, globals) || node.type == LayerType.DEPTH) continue
                val box = measureNode(node, u, env, now, globals, workPaint, workMat)
                val eff = box.resolvedNode
                prepareNode(
                    m = box,
                    parentMat = Matrix(),
                    restPoseKey = "${eff.x},${eff.y},${eff.anchor},${eff.align},${eff.scale},${eff.rotation},${box.dxPx},${box.dyPx}"
                )
            }
        } finally {
            allowStaleLensOnMiss = prevStale
        }
    }

    fun setPrebakedBackground(sharp: Bitmap, frosted: Bitmap, w: Int, h: Int, frostedBlurPx: Float = -1f) = synchronized(bgLock) {
        liveVideoMode = false
        val exactSharp = if (sharp.width == w && sharp.height == h) {
            if (sharp.hasAlpha()) runCatching { sharp.setHasAlpha(false) }
            sharp
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { dst ->
                val sc = Canvas(dst)
                sc.drawColor(Color.BLACK)
                drawBitmapCenterCrop(sc, sharp, w, h, Paint(Paint.FILTER_BITMAP_FLAG), Matrix())
                dst.setHasAlpha(false)
            }
        }
        val exactFrosted = if (frosted.width == w && frosted.height == h) {
            if (frosted.hasAlpha()) runCatching { frosted.setHasAlpha(false) }
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
        prebakedFrostedBlurPx = frostedBlurPx

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
        now: LocalDateTime,
        prebakedBlurPx: Float = -1f,
        hits: MutableList<HitBox>? = null,
        includeCompleteBitmap: Boolean = false,
        useRgb565ForComplete: Boolean = false,
        retainOnlyComplete: Boolean = false
    ): BakedMinuteScene {
        val screenW = w.coerceAtLeast(1)
        val screenH = h.coerceAtLeast(1)
        val u = screenW / 1080f
        val globals = project.globalsMap()
        activeCustomAssets = project.customAssets
        hits?.clear()

        if (sharpBg != null && frostedBg != null) {
            setPrebakedBackground(sharpBg, frostedBg, screenW, screenH, prebakedBlurPx)
        } else if (sharpBg != null) {
            prebakedFrostedBmp = null
            prebakedFrostedShader = null
            prebakedFrostedBlurPx = -1f
            ensureSharpBg(sharpBg, screenW, screenH)
        }

        val adaptive = extractAdaptiveColor(sharpBgBmp ?: sharpBg)
        var mainBounds: RectF? = null
        var clockCx = screenW * 0.5f
        var clockCy = screenH * 0.25f

        val needsHardwareShader = project.layers.flatten().any { (n, _) ->
            !n.hidden && (n.fill == Fill.CRYSTAL || n.fill == Fill.GLASS)
        }

        fun drawVisibleLayersOnto(rec: Canvas) {
            rec.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            rootMatrix.reset()
            invRootMatrix.reset()
            wakeDeltaMatrix.reset()

            val nestedChildIds = project.layers.collectNestedChildIds()
            val drawnIds = HashSet<String>(project.layers.size * 2)
            for (node in project.layers) {
                if (node.parentId != null || node.id in nestedChildIds) continue
                if (!drawnIds.add(node.id)) continue
                if (!isVisible(node, env, now, globals) || node.type == LayerType.DEPTH) continue
                val box = renderLayer(
                    canvas = rec,
                    screenW = screenW,
                    screenH = screenH,
                    u = u,
                    node = node,
                    bg = sharpBgBmp ?: sharpBg,
                    env = env,
                    now = now,
                    adaptive = adaptive,
                    condAlpha = 1f,
                    wakeProgress = 1f,
                    hits = hits,
                    globals = globals
                )
                if (mainBounds == null && (node.type == LayerType.TEXT || node.type == LayerType.STACK)) {
                    val (ox, oy) = anchorOffset(node.anchor, node.align, box.w, box.h)
                    val px = node.x * screenW + ox
                    val py = node.y * screenH + oy
                    mainBounds = RectF(px, py, px + box.w, py + box.h)
                    clockCx = mainBounds!!.centerX()
                    clockCy = mainBounds!!.centerY()
                }
            }
        }

        val clockLayerBmp = if (needsHardwareShader) {
            val layerNode = RenderNode("prism_minute_layers").apply {
                setPosition(0, 0, screenW, screenH)
            }
            val rec = layerNode.beginRecording(screenW, screenH)
            drawVisibleLayersOnto(rec)
            layerNode.endRecording()
            val out = renderNodeToBitmap(layerNode, screenW, screenH)
            layerNode.discardDisplayList()
            releaseOffscreenResources()
            out
        } else {
            Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888).also { bmp ->
                drawVisibleLayersOnto(Canvas(bmp))
            }
        }

        val hasDepthLayer = project.layers.any { it.type == LayerType.DEPTH && !it.hidden }
        val showDepth = hasDepthLayer && project.depth && depthMask != null &&
            (!project.depthGuard || mainBounds == null || checkCoverage(depthMask, mainBounds!!, screenW, screenH))

        val completeBmp = if (includeCompleteBitmap) {
            val cfg = if (useRgb565ForComplete) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            Bitmap.createBitmap(screenW, screenH, cfg).also { bmp ->
                val cc = Canvas(bmp)
                cc.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                val baseSharp = sharpBgBmp ?: sharpBg
                if (baseSharp != null) {
                    drawBitmapCenterCrop(cc, baseSharp, screenW, screenH, Paint(Paint.FILTER_BITMAP_FLAG))
                } else {
                    cc.drawColor(0xFF0A0D14.toInt(), PorterDuff.Mode.SRC)
                }
                if (project.dim > 0.005f) {
                    dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
                    cc.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), dimPaint)
                }
                cc.drawBitmap(clockLayerBmp, 0f, 0f, null)
                if (showDepth) {
                    val depthNode = project.layers.firstOrNull { it.type == LayerType.DEPTH }
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.alpha = ((depthNode?.opacity ?: 1f) * 255).roundToInt().coerceIn(0, 255)
                    drawBitmapCenterCrop(cc, depthMask!!, screenW, screenH, paint)
                }
                if (bmp.config != Bitmap.Config.RGB_565) {
                    bmp.setHasAlpha(false)
                }
            }
        } else null

        if (retainOnlyComplete && completeBmp != null) {
            runCatching { if (!clockLayerBmp.isRecycled) clockLayerBmp.recycle() }
            return BakedMinuteScene(
                clockLayerBitmap = completeBmp,
                completeLockscreenBitmap = completeBmp,
                showDepth = showDepth,
                clockCenterX = clockCx,
                clockCenterY = clockCy
            )
        }

        return BakedMinuteScene(
            clockLayerBitmap = clockLayerBmp,
            completeLockscreenBitmap = completeBmp,
            showDepth = showDepth,
            clockCenterX = clockCx,
            clockCenterY = clockCy
        )
    }

    fun renderWidgetBitmap(
        w: Int,
        h: Int,
        project: Project,
        refBg: Bitmap?,
        env: Env,
        now: LocalDateTime
    ): Bitmap {
        val scene = bakeMinuteScene(
            w = w,
            h = h,
            project = project,
            sharpBg = refBg,
            frostedBg = null,
            depthMask = null,
            env = env.copy(locked = false),
            now = now,
            includeCompleteBitmap = false
        )
        return scene.clockLayerBitmap
    }

    fun drawWakeAnimationFrame(
        canvas: Canvas,
        dstW: Int,
        dstH: Int,
        project: Project,
        sharpBg: Bitmap?,
        scene: BakedMinuteScene,
        depthMask: Bitmap?,
        wakeProgress: Float,
        lockAlpha: Float
    ) {
        val targetW = dstW.coerceAtLeast(1)
        val targetH = dstH.coerceAtLeast(1)
        val p = wakeProgress.coerceIn(0f, 1f)
        val la = lockAlpha.coerceIn(0f, 1f)
        dstRectF.set(0f, 0f, targetW.toFloat(), targetH.toFloat())
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        val completeBmp = scene.completeLockscreenBitmap
        if (p >= 0.998f && la >= 0.998f && completeBmp != null && !completeBmp.isRecycled) {
            filterPaint.reset()
            filterPaint.isAntiAlias = true
            filterPaint.isFilterBitmap = true
            canvas.drawBitmap(completeBmp, null, dstRectF, filterPaint)
            return
        }

        val baseSharp = sharpBgBmp ?: sharpBg
        if (baseSharp != null && !baseSharp.isRecycled) {
            filterPaint.reset()
            filterPaint.isAntiAlias = true
            filterPaint.isFilterBitmap = true
            canvas.drawBitmap(baseSharp, null, dstRectF, filterPaint)
        } else {
            canvas.drawColor(0xFF0A0D14.toInt(), PorterDuff.Mode.SRC)
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(dstRectF, dimPaint)
        }

        if (scene.isSingleCompleteFrame() && completeBmp != null && !completeBmp.isRecycled) {
            val combinedAlpha = (la * 255f).roundToInt().coerceIn(0, 255)
            if (combinedAlpha > 0) {
                filterPaint.reset()
                filterPaint.isAntiAlias = true
                filterPaint.isFilterBitmap = true
                filterPaint.alpha = combinedAlpha
                canvas.drawBitmap(completeBmp, null, dstRectF, filterPaint)
            }
            return
        }

        val combinedAlpha = (p * la * 255f).roundToInt().coerceIn(0, 255)
        if (combinedAlpha > 0 && !scene.clockLayerBitmap.isRecycled) {
            val baseW = scene.clockLayerBitmap.width.coerceAtLeast(1).toFloat()
            val baseH = scene.clockLayerBitmap.height.coerceAtLeast(1).toFloat()
            val sx = targetW / baseW
            val sy = targetH / baseH
            val cx = scene.clockCenterX * sx
            val cy = scene.clockCenterY * sy
            val uDst = targetW / 1080f
            val wakeOffset = (1f - p) * 42f * uDst
            val wakeScale = (0.96f + 0.04f * p).coerceAtLeast(0.5f)
            filterPaint.reset()
            filterPaint.isAntiAlias = true
            filterPaint.isFilterBitmap = true
            filterPaint.alpha = combinedAlpha

            canvas.save()
            canvas.translate(cx, cy + wakeOffset)
            canvas.scale(wakeScale, wakeScale)
            canvas.translate(-cx, -cy)
            canvas.drawBitmap(scene.clockLayerBitmap, null, dstRectF, filterPaint)
            canvas.restore()
        }

        if (scene.showDepth && depthMask != null && la > 0.01f) {
            val depthNode = project.layers.firstOrNull { it.type == LayerType.DEPTH }
            filterPaint.reset()
            filterPaint.isAntiAlias = true
            filterPaint.isFilterBitmap = true
            filterPaint.alpha = ((depthNode?.opacity ?: 1f) * la * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(depthMask, null, dstRectF, filterPaint)
        }
    }

    fun drawUnlockedHomeFrame(
        canvas: Canvas,
        dstW: Int,
        dstH: Int,
        project: Project,
        sharpBg: Bitmap?
    ) {
        val targetW = dstW.coerceAtLeast(1)
        val targetH = dstH.coerceAtLeast(1)
        dstRectF.set(0f, 0f, targetW.toFloat(), targetH.toFloat())
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        val baseSharp = sharpBgBmp ?: sharpBg
        if (baseSharp != null) {
            filterPaint.reset()
            filterPaint.isAntiAlias = true
            filterPaint.isFilterBitmap = true
            canvas.drawBitmap(baseSharp, null, dstRectF, filterPaint)
        } else {
            canvas.drawColor(0xFF0A0D14.toInt(), PorterDuff.Mode.SRC)
        }
        if (project.dim > 0.005f) {
            dimPaint.color = Color.argb((project.dim.coerceIn(0f, 0.8f) * 255).roundToInt(), 0, 0, 0)
            canvas.drawRect(dstRectF, dimPaint)
        }
    }

    private fun renderNodeToBitmap(node: RenderNode, w: Int, h: Int): Bitmap = synchronized(blurLock) {
        if (blurThread == null) {
            val t = HandlerThread("PrismBlurGen").apply { start() }
            blurThread = t
            blurHandler = Handler(t.looper)
        }
        if (sceneImageReader == null || sceneHwRenderer == null || sceneReaderW != w || sceneReaderH != h) {
            runCatching { sceneHwRenderer?.destroy() }
            runCatching { sceneImageReader?.close() }
            val reader = ImageReader.newInstance(
                w, h, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            )
            val hr = HardwareRenderer().apply {
                setSurface(reader.surface)
                isOpaque = false
            }
            sceneImageReader = reader
            sceneHwRenderer = hr
            sceneReaderW = w
            sceneReaderH = h
        }
        val reader = sceneImageReader!!
        val hr = sceneHwRenderer!!
        hr.setContentRoot(node)
        val latch = CountDownLatch(1)
        reader.setOnImageAvailableListener({ latch.countDown() }, blurHandler)
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
        val bmp = img?.use { image ->
            val hwBuf = image.hardwareBuffer ?: return@use null
            hwBuf.use { buf ->
                val hwBmp = Bitmap.wrapHardwareBuffer(buf, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return@use null
                val copy = hwBmp.copy(Bitmap.Config.ARGB_8888, true)
                hwBmp.recycle()
                copy
            }
        }
        bmp ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun ensureSharpBg(bg: Bitmap, w: Int, h: Int) = synchronized(bgLock) {
        liveVideoMode = false
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
        prebakedFrostedShader?.let { pre ->
            if (prebakedFrostedBlurPx < 0f || abs(prebakedFrostedBlurPx - blurPx) <= 2f) {
                return pre
            }
        }
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

    private fun isVisible(
        n: LayerNode,
        env: Env,
        now: LocalDateTime = LocalDateTime.now(),
        globals: Map<String, String> = emptyMap()
    ): Boolean {
        if (n.hidden) return false
        val condOk = when (n.cond) {
            Cond.ALWAYS -> true
            Cond.LOCK_ONLY -> env.locked
            Cond.HOME_ONLY -> !env.locked
            Cond.CHARGING -> env.charging
            Cond.NOT_CHARGING -> !env.charging
            Cond.LOW_BATTERY -> env.battery <= 20
            Cond.DAY -> env.isDay
            Cond.NIGHT -> !env.isDay
        }
        if (!condOk) return false
        if (n.visibilityExpr.isNotBlank()) {
            return KustomFormulaEngine.evalTruthy(n.visibilityExpr, env, now, globals)
        }
        return true
    }

    private fun resolveEffectiveNode(
        node: LayerNode,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String>
    ): LayerNode {
        var out = node
        if (node.type == LayerType.IMAGE && node.fxAsset.isBlank() && node.imageUri.isNotBlank()) {
            val mapped = activeCustomAssets[node.imageUri] ?: activeCustomAssets[node.imageUri.lowercase()]
            if (mapped != null) {
                val svg = Icons.decodeSvgAsset(mapped)
                out = if (svg != null) {
                    out.copy(type = LayerType.SVG, svgData = svg.data, svgW = svg.w, svgH = svg.h)
                } else {
                    out.copy(imageUri = mapped)
                }
            }
        }
        if (node.type == LayerType.ICON && node.fxAsset.isBlank() && node.icon.isNotBlank()) {
            val evalIcon = if ('$' in node.icon || '{' in node.icon) Dynamic.eval(node.icon, env, now, globals).trim() else node.icon.trim()
            val mapped = activeCustomAssets[evalIcon] ?: activeCustomAssets[evalIcon.lowercase()]
            val svg = Icons.decodeSvgAsset(mapped)
            if (svg != null) {
                out = out.copy(type = LayerType.SVG, svgData = svg.data, svgW = svg.w, svgH = svg.h)
            } else if (mapped != null) {
                out = out.copy(type = LayerType.IMAGE, imageUri = mapped)
            }
        }
        if (!node.hasFx()) return out
        if (node.fxColor.isNotBlank()) {
            KustomFormulaEngine.evalColor(node.fxColor, env, now, globals)?.let { c ->
                val cAlpha = (c ushr 24) and 0xFF
                val curTintAlpha = (out.glass.tint ushr 24) and 0xFF
                val effTintAlpha = if (cAlpha in 1..254) cAlpha else maxOf(curTintAlpha, 0xA8)
                val tintedGlass = out.glass.copy(tint = (c and 0x00FFFFFF) or (effTintAlpha shl 24))
                out = out.copy(
                    color = c,
                    color2 = if (out.fill == Fill.GRADIENT && out.color2 == node.color) c else out.color2,
                    strokeColor = if (out.fill == Fill.HOLLOW) c else out.strokeColor,
                    fill = if (out.fill == Fill.ADAPTIVE) Fill.SOLID else out.fill,
                    glass = tintedGlass
                )
            }
        }
        if (node.fxOpacity.isNotBlank()) {
            KustomFormulaEngine.evalNumeric(node.fxOpacity, env, now, globals)?.let { pct ->
                out = out.copy(opacity = (node.opacity * (pct.toFloat() / 100f)).coerceIn(0f, 1f))
            }
        }
        if (node.fxRotation.isNotBlank()) {
            KustomFormulaEngine.evalNumeric(node.fxRotation, env, now, globals)?.let { deg ->
                out = out.copy(rotation = node.rotation + deg.toFloat())
            }
        }
        if (node.fxScale.isNotBlank()) {
            KustomFormulaEngine.evalNumeric(node.fxScale, env, now, globals)?.let { sc ->
                out = out.copy(scale = (node.scale * sc.toFloat()).coerceIn(0.05f, 8f))
            }
        }
        if (node.fxSize.isNotBlank()) {
            KustomFormulaEngine.evalNumeric(node.fxSize, env, now, globals)?.toFloat()?.let { sz ->
                if (sz > 1f) {
                    out = when (out.type) {
                        LayerType.TEXT -> out.copy(size = sz.coerceIn(8f, 600f))
                        LayerType.SHAPE, LayerType.ICON, LayerType.SVG, LayerType.IMAGE -> {
                            val ratio = if (out.w > 1f) out.h / out.w else 1f
                            val nw = sz.coerceIn(8f, 2000f)
                            out.copy(w = nw, h = (nw * ratio).coerceIn(4f, 2000f))
                        }
                        else -> out
                    }
                }
            }
        }
        if (node.fxAsset.isNotBlank()) {
            val rawKey = Dynamic.eval(node.fxAsset, env, now, globals).trim()
            if (rawKey.isNotEmpty()) {
                val mapped = activeCustomAssets[rawKey] ?: activeCustomAssets[rawKey.lowercase()]
                val decodedSvg = Icons.decodeSvgAsset(mapped)
                out = when {
                    decodedSvg != null -> out.copy(type = LayerType.SVG, svgData = decodedSvg.data, svgW = decodedSvg.w, svgH = decodedSvg.h)
                    out.type == LayerType.SVG && (rawKey.lowercase() in Icons.builtIn || rawKey.equals("weather", ignoreCase = true)) ->
                        out.copy(type = LayerType.ICON, icon = rawKey.lowercase())
                    out.type == LayerType.ICON && mapped != null ->
                        out.copy(type = LayerType.IMAGE, imageUri = mapped)
                    out.type == LayerType.ICON ->
                        out.copy(icon = rawKey.lowercase())
                    out.type == LayerType.SVG && mapped != null ->
                        out.copy(type = LayerType.IMAGE, imageUri = mapped)
                    out.type == LayerType.IMAGE ->
                        out.copy(imageUri = mapped ?: rawKey)
                    else -> out
                }
            }
        }
        return out
    }

    private fun resolveLayerImageBitmap(node: LayerNode, boxW: Float, boxH: Float): Bitmap? {
        if (node.type != LayerType.IMAGE || node.imageUri.isBlank()) return null
        val resolvedUri = activeCustomAssets[node.imageUri] ?: activeCustomAssets[node.imageUri.lowercase()] ?: node.imageUri
        return currentContext?.let {
            WallpaperCarousel.loadCustomAssetBitmap(
                it,
                resolvedUri,
                boxW.roundToInt().coerceAtLeast(16),
                boxH.roundToInt().coerceAtLeast(16)
            )
        }
    }

    private data class Measured(
        val w: Float,
        val h: Float,
        val path: Path?,
        val geomKey: String,
        val resolvedNode: LayerNode,
        val dxPx: Float = 0f,
        val dyPx: Float = 0f,
        val children: List<Measured> = emptyList(),
        val evalText: String = "",
        val baselineShiftY: Float = 0f,
        val baseStrokePx: Float = 18f
    )

    private fun measureNode(
        rawNode: LayerNode,
        u: Float,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap(),
        workPaint: Paint = paint,
        workMatrix: Matrix = tmpMatrix
    ): Measured {
        val node = resolveEffectiveNode(rawNode, env, now, globals)
        val dxPx = if (node.fxOffsetX.isNotBlank()) {
            (KustomFormulaEngine.evalNumeric(node.fxOffsetX, env, now, globals)?.toFloat() ?: 0f) * u
        } else 0f
        val dyPx = if (node.fxOffsetY.isNotBlank()) {
            (KustomFormulaEngine.evalNumeric(node.fxOffsetY, env, now, globals)?.toFloat() ?: 0f) * u
        } else 0f
        return when (node.type) {
            LayerType.TEXT -> {
                val s = Dynamic.eval(node.text, env, now, globals)
                val shape = StemStretch.shape(s, node, u, workPaint)
                val strokePx = StemStretch.measureStrokeWidthPx(node, u)
                val gKey = "T|$s|${node.font}|${node.size}|${node.weight}|${node.axes}|${node.stretch}|${node.strokeCompensation}|${node.letterSpacing}|${node.lineSpacing}|${node.align}|$u"
                Measured(shape.width, shape.height, shape.path, gKey, node, dxPx, dyPx, evalText = s, baselineShiftY = shape.baselineShiftY, baseStrokePx = strokePx)
            }
            LayerType.SHAPE -> {
                val w = node.w * u
                val h = node.h * u
                val frac = (Dynamic.number(node.value, env, now, globals) / 100.0).toFloat().coerceIn(0f, 1f)
                val p = buildShapePath(node, w, h, u, frac)
                val strokePx = (minOf(w, h) * 0.35f).coerceAtLeast(8f * u)
                val gKey = "S|${node.shape}|$w|$h|${node.corner}|${node.strokeWidth}|$frac|$u"
                Measured(w, h, p, gKey, node, dxPx, dyPx, baseStrokePx = strokePx)
            }
            LayerType.ICON, LayerType.SVG -> {
                val w = node.w * u
                val h = node.h * u
                val iconName = if (node.type == LayerType.ICON) {
                    val resolved = if ('$' in node.icon || '{' in node.icon) Dynamic.eval(node.icon, env, now, globals).trim() else node.icon
                    if (resolved == "weather") Icons.weatherIcon(env.weatherCode, env.isDay) else resolved
                } else ""
                val raw = if (node.type == LayerType.ICON) Icons.builtinPath(iconName) else Icons.path(node.svgData)
                val srcW = (if (node.type == LayerType.ICON) 24f else node.svgW).coerceAtLeast(1f)
                val srcH = (if (node.type == LayerType.ICON) 24f else node.svgH).coerceAtLeast(1f)
                val out = Path(raw)
                workMatrix.setScale(w / srcW, h / srcH)
                out.transform(workMatrix)
                val strokePx = (minOf(w, h) * 0.25f).coerceAtLeast(6f * u)
                val gKey = if (node.type == LayerType.ICON) "I|$iconName|$w|$h" else "V|${node.svgData.hashCode()}|$w|$h"
                Measured(w, h, out, gKey, node, dxPx, dyPx, baseStrokePx = strokePx)
            }
            LayerType.IMAGE -> {
                val w = node.w * u
                val h = node.h * u
                val p = Path().apply {
                    val c = node.corner * u
                    addRoundRect(0f, 0f, w, h, c, c, Path.Direction.CW)
                }
                val strokePx = (minOf(w, h) * 0.25f).coerceAtLeast(6f * u)
                val gKey = "IMG|${node.imageUri}|$w|$h|${node.corner}"
                Measured(w, h, p, gKey, node, dxPx, dyPx, baseStrokePx = strokePx)
            }
            LayerType.STACK -> {
                val visibleKids = node.children
                    .filter { isVisible(it, env, now, globals) && it.type != LayerType.DEPTH }
                    .map { child ->
                        var c = child
                        if (c.fxColor.isBlank() && node.fxColor.isNotBlank()) c = c.copy(fxColor = node.fxColor)
                        if (c.fxOpacity.isBlank() && node.fxOpacity.isNotBlank()) c = c.copy(fxOpacity = node.fxOpacity)
                        c
                    }
                val kids = visibleKids.map { measureNode(it, u, env, now, globals, workPaint, workMatrix) }
                val gap = node.spacing * u
                if (node.dir == Dir.VERTICAL) {
                    val mw = kids.maxOfOrNull { it.w * it.resolvedNode.scale } ?: 0f
                    val mh = kids.sumOf { it.h.toDouble() }.toFloat() + gap * (kids.size - 1).coerceAtLeast(0)
                    Measured(mw, mh.coerceAtLeast(0f), null, "K", node, dxPx, dyPx, kids)
                } else {
                    val mw = kids.sumOf { it.w.toDouble() }.toFloat() + gap * (kids.size - 1).coerceAtLeast(0)
                    val mh = kids.maxOfOrNull { it.h * it.resolvedNode.scale } ?: 0f
                    Measured(mw.coerceAtLeast(0f), mh, null, "K", node, dxPx, dyPx, kids)
                }
            }
            LayerType.DEPTH -> Measured(0f, 0f, null, "D", node)
        }
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
        originY: Float? = null,
        globals: Map<String, String> = emptyMap()
    ) {
        val (ox, oy) = if (originX == null) anchorOffset(node.anchor, node.align, m.w, m.h) else 0f to 0f
        val left = (originX ?: (node.x * screenW + ox)) + m.dxPx
        val top = (originY ?: (node.y * screenH + oy)) + wakeDy + m.dyPx
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
            val gap = node.spacing * u
            var cur = 0f
            m.children.forEachIndexed { idx, cm ->
                val child = cm.resolvedNode
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
                    globals = globals,
                    adaptive = adaptive,
                    alpha = alpha * child.opacity,
                    wakeDy = 0f,
                    wakeScale = 1f,
                    restPoseKey = "$restPoseKey|${node.dir},${node.align},$gap,$idx,$kx,$ky,${child.scale},${child.rotation},${cm.dxPx},${cm.dyPx}",
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
            if (node.shadowRadius > 0.1f) {
                shadowPaint.reset()
                shadowPaint.isAntiAlias = true
                shadowPaint.style = Paint.Style.FILL
                shadowPaint.color = applyAlpha(node.shadowColor, alpha)
                shadowPaint.maskFilter = BlurMaskFilter((node.shadowRadius * u).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                canvas.save()
                canvas.translate(node.shadowDx * u, node.shadowDy * u)
                canvas.drawPath(path, shadowPaint)
                canvas.restore()
            }
            val imgBmp = if (node.type == LayerType.IMAGE) resolveLayerImageBitmap(node, m.w, m.h) else null
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
                            localPath = path,
                            animScreenPath = animScreenPath,
                            restScreenPath = restPath,
                            restLocalToScreen = restMat,
                            evalText = m.evalText,
                            baselineShiftY = m.baselineShiftY,
                            baseStrokePx = m.baseStrokePx,
                            geomKey = "${m.geomKey}|$restPoseKey",
                            g = node.glass,
                            screenRect = screenRect,
                            boxW = m.w,
                            boxH = m.h,
                            alpha = alpha,
                            blend = blend,
                            imageMaskBmp = imgBmp
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
                            blend = blend,
                            imageMaskBmp = imgBmp
                        )
                    }
                }
            } else if (node.fill != Fill.HOLLOW) {
                if (imgBmp != null && !imgBmp.isRecycled) {
                    filterPaint.reset()
                    filterPaint.isAntiAlias = true
                    filterPaint.isFilterBitmap = true
                    filterPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
                    filterPaint.blendMode = blend
                    canvas.save()
                    canvas.clipPath(path)
                    drawBitmapCenterCrop(canvas, imgBmp, m.w.roundToInt().coerceAtLeast(1), m.h.roundToInt().coerceAtLeast(1), filterPaint)
                    canvas.restore()
                } else {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.isSubpixelText = true
                    paint.blendMode = blend
                    when (node.fill) {
                        Fill.SOLID, Fill.GLASS, Fill.CRYSTAL, Fill.HOLLOW -> paint.color = applyAlpha(node.color, alpha)
                        Fill.ADAPTIVE -> paint.color = applyAlpha(adaptive, alpha)
                        Fill.GRADIENT -> paint.shader = makeGradientShader(node, m.w, m.h, alpha)
                    }
                    canvas.drawPath(path, paint)
                }
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
        baseStrokePx: Float,
        geomKey: String,
        restLocalToScreen: Matrix? = null,
        evalText: String = "",
        baselineShiftY: Float = 0f,
        u: Float = screenW / 1080f,
        imageMaskBmp: Bitmap? = null,
        localPath: Path? = null,
        boxW: Float = 0f,
        boxH: Float = 0f
    ): Bitmap = synchronized(maskLock) {
        val imgTag = if (imageMaskBmp != null && !imageMaskBmp.isRecycled) "|img${System.identityHashCode(imageMaskBmp)}" else ""
        val key = "$nodeId|${screenW}x$screenH|${bevelPx.roundToInt()}|${baseStrokePx.roundToInt()}|$geomKey$imgTag"
        lensHeightCache.get(key)?.let { return it }

        if (allowStaleLensOnMiss) {
            lastLensByNode[nodeId]?.takeIf { !it.isRecycled && it.width == screenW && it.height == screenH }?.let {
                return it
            }
        }

        if (imageMaskBmp != null && !imageMaskBmp.isRecycled && restLocalToScreen != null && localPath != null && boxW > 0f && boxH > 0f) {
            val sharp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ALPHA_8)
            val sc = Canvas(sharp)
            maskPaint.maskFilter = null
            maskPaint.alpha = 255
            val alphaBmp = imageMaskBmp.extractAlpha()
            sc.save()
            sc.concat(restLocalToScreen)
            sc.clipPath(localPath)
            drawBitmapCenterCrop(sc, alphaBmp, boxW.roundToInt().coerceAtLeast(1), boxH.roundToInt().coerceAtLeast(1), maskPaint)
            sc.restore()
            alphaBmp.recycle()

            val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ALPHA_8)
            val oc = Canvas(out)
            val rInterior = (baseStrokePx * 0.65f).coerceAtLeast(4f * u)
            val rBevel = bevelPx.coerceAtLeast(1f)
            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val offset = IntArray(2)

            blurPaint.maskFilter = BlurMaskFilter(rInterior, BlurMaskFilter.Blur.NORMAL)
            val interiorBlur = sharp.extractAlpha(blurPaint, offset)
            maskPaint.maskFilter = null
            maskPaint.alpha = 145
            oc.drawBitmap(interiorBlur, offset[0].toFloat(), offset[1].toFloat(), maskPaint)
            interiorBlur.recycle()

            blurPaint.maskFilter = BlurMaskFilter(rBevel, BlurMaskFilter.Blur.NORMAL)
            val bevelBlur = sharp.extractAlpha(blurPaint, offset)
            maskPaint.alpha = 125
            oc.drawBitmap(bevelBlur, offset[0].toFloat(), offset[1].toFloat(), maskPaint)
            bevelBlur.recycle()

            maskPaint.maskFilter = null
            maskPaint.alpha = 255
            oc.drawBitmap(sharp, 0f, 0f, maskCutPaint)
            sharp.recycle()

            lensHeightCache.put(key, out)
            lastLensByNode[nodeId] = out
            val inv = Matrix()
            if (restLocalToScreen.invert(inv)) {
                lastLensInvMatrixByNode[nodeId] = inv
            } else {
                lastLensInvMatrixByNode.remove(nodeId)
            }
            return out
        }

        if (restLocalToScreen != null && evalText.isNotEmpty()) {
            val regNode = GlyphAtlasBaker.getRegisteredNode(nodeId)
            if (regNode != null) {
                GlyphAtlasBaker.tryAssembleScreenLens(
                    node = regNode,
                    text = evalText,
                    baselineShiftY = baselineShiftY,
                    localToScreen = restLocalToScreen,
                    screenW = screenW,
                    screenH = screenH,
                    u = u,
                    bevelPx = bevelPx,
                    strokePx = baseStrokePx
                )?.let { atlasOut ->
                    lensHeightCache.put(key, atlasOut)
                    lastLensByNode[nodeId] = atlasOut
                    val inv = Matrix()
                    if (restLocalToScreen.invert(inv)) lastLensInvMatrixByNode[nodeId] = inv
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
        val rInterior = (baseStrokePx * 0.65f).coerceAtLeast(4f * u)
        val rBevel = bevelPx.coerceAtLeast(1f)
        maskPaint.maskFilter = BlurMaskFilter(rInterior, BlurMaskFilter.Blur.NORMAL)
        maskPaint.alpha = 145
        oc.drawPath(restScreenPath, maskPaint)
        maskPaint.maskFilter = BlurMaskFilter(rBevel, BlurMaskFilter.Blur.NORMAL)
        maskPaint.alpha = 125
        oc.drawPath(restScreenPath, maskPaint)
        maskPaint.maskFilter = null
        maskPaint.alpha = 255

        oc.drawBitmap(sharp, 0f, 0f, maskCutPaint)
        sharp.recycle()

        lensHeightCache.put(key, out)
        lastLensByNode[nodeId] = out
        val inv = Matrix()
        if (restLocalToScreen != null && restLocalToScreen.invert(inv)) {
            lastLensInvMatrixByNode[nodeId] = inv
        } else {
            lastLensInvMatrixByNode.remove(nodeId)
        }
        return out
    }

    private fun getLensShader(nodeId: String, lensBmp: Bitmap, curRestMatrix: Matrix? = null): BitmapShader {
        val bakedInv = lastLensInvMatrixByNode[nodeId]
        if (curRestMatrix != null && bakedInv != null) {
            lensDeltaMatrix.set(bakedInv)
            lensDeltaMatrix.postConcat(curRestMatrix)
            lensDeltaMatrix.postConcat(wakeDeltaMatrix)
        } else {
            lensDeltaMatrix.set(wakeDeltaMatrix)
        }
        val existing = lensShaderCache[nodeId]
        if (existing != null && existing.first === lensBmp) {
            existing.second.setLocalMatrix(lensDeltaMatrix)
            return existing.second
        }
        val shader = BitmapShader(lensBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(lensDeltaMatrix)
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
        blend: BlendMode,
        imageMaskBmp: Bitmap? = null
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

        val useImgMask = imageMaskBmp != null && !imageMaskBmp.isRecycled
        val saveCount = if (useImgMask) {
            layerPaint.reset()
            layerPaint.isAntiAlias = true
            layerPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            layerPaint.blendMode = blend
            canvas.saveLayer(-2f, -2f, boxW + 2f, boxH + 2f, layerPaint)
        } else -1

        glassPaint.reset()
        glassPaint.isAntiAlias = true
        glassPaint.shader = blurShader
        glassPaint.alpha = if (useImgMask) 255 else (alpha * 255).roundToInt().coerceIn(0, 255)
        glassPaint.blendMode = if (useImgMask) BlendMode.SRC_OVER else blend

        canvas.save()
        canvas.concat(screenToLocal)
        canvas.drawPath(animScreenPath, glassPaint)
        canvas.restore()

        if (g.rim > 0.01f) {
            val rad = Math.toRadians(g.lightAngle.toDouble())
            val rx = cos(rad).toFloat() * boxW * 0.6f
            val ry = sin(rad).toFloat() * boxH * 0.6f
            val rimAlpha = (g.rim * (if (useImgMask) 1f else alpha) * 190).roundToInt().coerceIn(0, 255)
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

        if (useImgMask && saveCount >= 0) {
            canvas.save()
            canvas.clipPath(localPath)
            drawBitmapCenterCrop(canvas, imageMaskBmp!!, boxW.roundToInt().coerceAtLeast(1), boxH.roundToInt().coerceAtLeast(1), maskCutPaint)
            canvas.restore()
            canvas.restoreToCount(saveCount)
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
        baselineShiftY: Float,
        baseStrokePx: Float,
        geomKey: String,
        g: Glass,
        screenRect: RectF,
        alpha: Float,
        blend: BlendMode,
        localPath: Path = Path(),
        boxW: Float = 0f,
        boxH: Float = 0f,
        imageMaskBmp: Bitmap? = null
    ) {
        val blurPx = if (g.frostAmount > 0.01f) (g.blur.coerceAtLeast(18f * g.frostAmount) * u).coerceIn(0f, 80f * u) else 0f
        val bevelRadiusPx = (g.bevelWidth * u).coerceIn(1f, 48f * u)
        val sharpShader = sharpBgShader ?: return
        val frostedShader = getFrostedShader(blurPx, screenW, screenH)
        val useImgMask = imageMaskBmp != null && !imageMaskBmp.isRecycled && boxW > 0f && boxH > 0f
        val lensBmp = getLensHeightMap(
            nodeId = nodeId,
            restScreenPath = restScreenPath,
            screenW = screenW,
            screenH = screenH,
            bevelPx = bevelRadiusPx,
            baseStrokePx = baseStrokePx,
            geomKey = geomKey,
            restLocalToScreen = restLocalToScreen,
            evalText = evalText,
            baselineShiftY = baselineShiftY,
            u = u,
            imageMaskBmp = if (useImgMask) imageMaskBmp else null,
            localPath = if (useImgMask) localPath else null,
            boxW = boxW,
            boxH = boxH
        )
        val lensShader = getLensShader(nodeId, lensBmp, restLocalToScreen)

        val sliderValue = (g.refraction / 100f).coerceIn(0f, 1f)
        val refractionOffsetPx = sliderValue * (screenW.toFloat() * 0.22f)

        val r = Color.red(g.tint) / 255f
        val gr = Color.green(g.tint) / 255f
        val b = Color.blue(g.tint) / 255f
        val a = Color.alpha(g.tint) / 255f

        crystalShader.setInputShader("backgroundTex", sharpShader)
        crystalShader.setInputShader("frostedTex", frostedShader)
        crystalShader.setInputShader("lensHeightMap", lensShader)
        crystalShader.setFloatUniform("uResolution", screenW.toFloat(), screenH.toFloat())
        crystalShader.setFloatUniform("uBoxSize", screenRect.width().coerceAtLeast(1f), screenRect.height().coerceAtLeast(1f))
        crystalShader.setFloatUniform("uRefractionOffsetPx", refractionOffsetPx)
        crystalShader.setFloatUniform("uBevelRadius", bevelRadiusPx)
        crystalShader.setFloatUniform("uStrokeRadius", (baseStrokePx * 0.65f).coerceAtLeast(4f * u))
        crystalShader.setFloatUniform("uDispersion", g.chromaticAberration)
        crystalShader.setFloatUniform("uFrostAmount", g.frostAmount.coerceIn(0f, 1f))
        crystalShader.setFloatUniform("uFresnelIntensity", g.rim.coerceIn(0f, 1f))
        crystalShader.setFloatUniform("uLightAngle", g.lightAngle)
        crystalShader.setFloatUniform("uSaturation", g.saturation)
        crystalShader.setFloatUniform("uTint", r, gr, b, a)

        val saveCount = if (useImgMask) {
            layerPaint.reset()
            layerPaint.isAntiAlias = true
            layerPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            layerPaint.blendMode = blend
            canvas.saveLayer(-2f, -2f, boxW + 2f, boxH + 2f, layerPaint)
        } else -1

        glassPaint.reset()
        glassPaint.isAntiAlias = true
        glassPaint.shader = crystalShader
        glassPaint.alpha = if (useImgMask) 255 else (alpha * 255).roundToInt().coerceIn(0, 255)
        glassPaint.blendMode = if (useImgMask) BlendMode.SRC_OVER else blend

        canvas.save()
        canvas.concat(screenToLocal)
        canvas.drawPath(animScreenPath, glassPaint)
        canvas.restore()

        if (useImgMask && saveCount >= 0) {
            canvas.save()
            canvas.clipPath(localPath)
            drawBitmapCenterCrop(canvas, imageMaskBmp!!, boxW.roundToInt().coerceAtLeast(1), boxH.roundToInt().coerceAtLeast(1), maskCutPaint)
            canvas.restore()
            canvas.restoreToCount(saveCount)
        }
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
