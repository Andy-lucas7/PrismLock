package com.example.prismlock.wallpaper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.data.launchSafe
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.HitBox
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.TouchActionExecutor
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Cond
import com.example.prismlock.model.Fill
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.flatten
import com.example.prismlock.widget.PrismWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thermal & Event-Driven Engine Audit — Active Triggers ONLY:
 * 1. Intent.ACTION_TIME_TICK (1x/min while visible on Lockscreen; draws pre-baked minute buffer).
 * 2. Intent.ACTION_BATTERY_CHANGED (only on actual pct/charging delta while visible on Lockscreen).
 * 3. Intent.ACTION_USER_PRESENT (260ms alpha unlock fade, then releases Lockscreen shader buffers on Home).
 * 4. Intent.ACTION_SCREEN_OFF (stops animators/jobs, advances carousel after 150ms if enabled, pre-bakes 1 static AOD frame).
 * 5. WallpaperBaker.ACTION_BAKED_READY + ProjectStore.bakeVersionFlow (flushes RAM caches before loading bake_v{version}_*).
 * 6. Surface lifecycle: ProjectStore.flow (config saved), onSurfaceChanged (size change), onVisibilityChanged(visible), double-tap.
 * Single-engine serialization via active engine token + sceneMutex; explicit SRC/CLEAR clears prevent ghosts.
 */
class PrismWallpaperService : WallpaperService() {
    companion object {
        private val engineCounter = AtomicLong(0L)
        @Volatile private var activeLiveEngineId = 0L
        @Volatile private var activePreviewEngineId = 0L
    }

    override fun onCreateEngine(): Engine = PrismEngine()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            WallpaperBaker.clearMemoryCache()
            SubjectDepthEngine.releaseMemory()
            WallpaperCarousel.clearMemoryCache()
        }
    }

    inner class PrismEngine : Engine() {
        private val engineId = engineCounter.incrementAndGet()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = LiquidGlassRenderer()
        private val sceneMutex = Mutex()
        private val wakeInterpolator = OvershootInterpolator(1.08f)
        private val linearInterpolator = LinearInterpolator()

        private var project = Project()
        private var currentUri: String? = null
        private var bgBitmap: Bitmap? = null
        private var frostedBitmap: Bitmap? = null
        private var frostedBlurPx: Float = -1f
        private var depthMask: Bitmap? = null
        private var cachedScene: LiquidGlassRenderer.BakedMinuteScene? = null
        private var cachedSceneKey: String = ""
        private var loadedBakeVersion: Long = -1L

        // Base proportional FHD+ working space (max 1080px width) scaled to physical surface dimensions
        private var baseW = 1080
        private var baseH = 2400

        private var activeAnimator: ValueAnimator? = null
        private var visibilityJob: Job? = null
        private var screenOffJob: Job? = null
        private var initJob: Job? = null
        @Volatile private var isDestroyed = false
        private var surfaceVisible = false
        private var wakeProgress = 1f
        private var lockAlpha = 1f
        private var lastTapMs = 0L
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var touchDownMs = 0L
        private var touchMoved = false
        private var lastActionTapMs = 0L
        @Volatile private var activeHits: List<HitBox> = emptyList()
        private val videoHitsBuffer = ArrayList<HitBox>()

        private val mainHandler = Handler(Looper.getMainLooper())
        private val frameDrawPending = AtomicBoolean(false)
        private val powerManager by lazy { getSystemService(PowerManager::class.java) }
        private var videoRenderer: VideoWallpaperRenderer? = null
        @Volatile private var isVideoActive = false
        @Volatile private var videoAdaptiveColor = 0xFF9AC5FF.toInt()

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isCurrentEngine()) return
                when (intent.action) {
                    Intent.ACTION_TIME_TICK -> {
                        if (PrismWidgetProvider.hasPinnedWidgets(this@PrismWallpaperService)) {
                            PrismWidgetProvider.updateAll(this@PrismWallpaperService)
                        }
                        if (!isDeviceLocked() && hasOnlyLockLayers()) return
                        EnvProvider.refreshAlarm(this@PrismWallpaperService)
                        scope.launchSafe {
                            ensureBakedMinuteScene(force = false)
                            if (!isCurrentEngine()) return@launchSafe
                            if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                        }
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val prev = EnvProvider.env.value
                        if (EnvProvider.applyBattery(intent)) {
                            val cur = EnvProvider.env.value
                            if (prev.battery != cur.battery || prev.charging != cur.charging) {
                                if (PrismWidgetProvider.hasPinnedWidgets(this@PrismWallpaperService)) {
                                    PrismWidgetProvider.updateAll(this@PrismWallpaperService)
                                }
                                if (isDeviceLocked() || (surfaceVisible && isVisible && !hasOnlyLockLayers())) {
                                    scope.launchSafe {
                                        ensureBakedMinuteScene(force = true)
                                        if (!isCurrentEngine()) return@launchSafe
                                        if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                                    }
                                }
                            }
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        EnvProvider.update { it.copy(locked = false) }
                        if (!hasOnlyLockLayers()) {
                            scope.launchSafe {
                                ensureBakedMinuteScene(force = true)
                                if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                            }
                        }
                        if (surfaceVisible && isVisible) {
                            startUnlockFade()
                        } else {
                            lockAlpha = 0f
                            releaseLockscreenBuffersIfHomeOnly()
                        }
                        syncVideoPlaybackState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        syncVideoPlaybackState()
                        if (isDeviceLocked() || !hasOnlyLockLayers()) {
                            EnvProvider.refreshAlarm(this@PrismWallpaperService)
                            scope.launchSafe {
                                ensureBakedMinuteScene(force = false)
                                if (!isCurrentEngine()) return@launchSafe
                                if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        EnvProvider.update { it.copy(locked = true) }
                        lockAlpha = 1f
                        wakeProgress = 1f
                        surfaceVisible = false
                        videoRenderer?.pause()
                        visibilityJob?.cancel()
                        stopAnimations()
                        screenOffJob?.cancel()
                        screenOffJob = scope.launchSafe {
                            delay(150L)
                            if (!isCurrentEngine()) return@launchSafe
                            val shouldAdvance = project.changeOnWake || project.intervalMin > 0
                            if (shouldAdvance) {
                                loadWallpaperAndMask(
                                    advance = project.changeOnWake,
                                    checkInterval = project.intervalMin > 0
                                )
                            }
                            if (shouldAdvance || cachedScene == null) {
                                ensureBakedMinuteScene(force = true)
                            }
                            drawStaticFrameForAod()
                            cachedScene = cachedScene?.trimToCompleteRgb565()
                            releaseIntermediateBakeBuffers()
                        }
                    }
                    WallpaperBaker.ACTION_BAKED_READY, ProjectStore.ACTION_RELOAD_PROJECT -> {
                        scope.launchSafe {
                            project = ProjectStore.readActiveProject(this@PrismWallpaperService)
                            val ver = ProjectStore.bakeVersion(this@PrismWallpaperService)
                            reloadAfterBakeVersionChange(ver)
                        }
                    }
                }
            }
        }

        private fun claimEngineSlot() {
            if (isPreview) activePreviewEngineId = engineId else activeLiveEngineId = engineId
        }

        private fun isCurrentEngine(): Boolean =
            !isDestroyed && (if (isPreview) engineId == activePreviewEngineId else engineId == activeLiveEngineId)

        private fun prewarmIfCrystal(p: Project) {
            if (p.layers.flatten().any { (n, _) -> !n.hidden && n.fill == Fill.CRYSTAL && n.type == LayerType.TEXT }) {
                WallpaperBaker.prewarmGlyphAtlases(p, baseW)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            claimEngineSlot()
            FontManager.init(this@PrismWallpaperService)
            renderer.attachContext(this@PrismWallpaperService)
            setTouchEventsEnabled(true)

            val (bw, bh) = WallpaperBaker.resolveDisplaySize(this@PrismWallpaperService)
            baseW = bw
            baseH = bh

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(WallpaperBaker.ACTION_BAKED_READY)
                addAction(ProjectStore.ACTION_RELOAD_PROJECT)
            }
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

            EnvProvider.refreshBattery(this@PrismWallpaperService)
            EnvProvider.refreshAlarm(this@PrismWallpaperService)
            EnvProvider.refreshAudio(this@PrismWallpaperService)
            EnvProvider.update { it.copy(locked = isDeviceLocked()) }

            initJob = scope.launchSafe {
                val loaded = ProjectStore.readActiveProject(this@PrismWallpaperService)
                if (!isCurrentEngine()) return@launchSafe
                project = loaded
                loadedBakeVersion = ProjectStore.bakeVersion(this@PrismWallpaperService)
                withContext(Dispatchers.Default) {
                    prewarmIfCrystal(loaded)
                }
                loadWallpaperAndMask(advance = false, checkInterval = false)
                ensureBakedMinuteScene(force = true)
                if (!isCurrentEngine()) return@launchSafe
                if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                if (refreshWeatherIfNeeded() && isCurrentEngine()) {
                    ensureBakedMinuteScene(force = true)
                    if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (!isCurrentEngine()) return
            val (bw, bh) = WallpaperBaker.baseSizeForPhysical(width, height)
            if (bw == baseW && bh == baseH && cachedScene != null) {
                if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
                return
            }
            baseW = bw
            baseH = bh
            scope.launchSafe {
                if (!isCurrentEngine()) return@launchSafe
                withContext(Dispatchers.Default) {
                    prewarmIfCrystal(project)
                }
                currentUri = null
                loadWallpaperAndMask(advance = false, checkInterval = false)
                ensureBakedMinuteScene(force = true)
                if (!isCurrentEngine()) return@launchSafe
                if (surfaceVisible && isVisible) drawOnce() else drawStaticFrameForAod()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                claimEngineSlot()
                surfaceVisible = true
                EnvProvider.refreshAudio(this@PrismWallpaperService)
                val locked = isDeviceLocked()
                EnvProvider.update { it.copy(locked = locked) }
                if (locked) {
                    lockAlpha = 1f
                    EnvProvider.refreshAlarm(this@PrismWallpaperService)
                } else {
                    lockAlpha = 0f
                    if (hasOnlyLockLayers() && !isPreview) {
                        releaseLockscreenBuffersIfHomeOnly()
                        syncVideoPlaybackState()
                        drawOnce()
                        return
                    }
                }

                syncVideoPlaybackState()
                if (locked && cachedScene != null && screenOffJob?.isActive != true) {
                    startWakeAnimation()
                    visibilityJob?.cancel()
                    visibilityJob = scope.launchSafe {
                        ensureBakedMinuteScene(force = false)
                        if (surfaceVisible && isVisible && activeAnimator?.isRunning != true && isCurrentEngine()) {
                            drawOnce()
                        }
                        if (refreshWeatherIfNeeded() && surfaceVisible && isVisible && activeAnimator?.isRunning != true && isCurrentEngine()) {
                            ensureBakedMinuteScene(force = true)
                            drawOnce()
                        }
                    }
                    return
                }

                visibilityJob?.cancel()
                visibilityJob = scope.launchSafe {
                    screenOffJob?.join()
                    if (!isCurrentEngine()) return@launchSafe
                    loadWallpaperAndMask(advance = false, checkInterval = false)
                    ensureBakedMinuteScene(force = false)
                    syncVideoPlaybackState()
                    if (surfaceVisible && isVisible && isCurrentEngine()) {
                        if (locked) startWakeAnimation() else drawOnce()
                    }
                    if (refreshWeatherIfNeeded() && surfaceVisible && isVisible && activeAnimator?.isRunning != true && isCurrentEngine()) {
                        ensureBakedMinuteScene(force = true)
                        drawOnce()
                    }
                }
            } else {
                surfaceVisible = false
                videoRenderer?.pause()
                visibilityJob?.cancel()
                stopAnimations()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!isCurrentEngine() || !surfaceVisible || !isVisible) {
                super.onTouchEvent(event)
                return
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownMs = SystemClock.uptimeMillis()
                    touchMoved = false
                    val hitNode = findActionableAt(event.x, event.y)
                    if (hitNode == null && project.changeOnDoubleTap) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapMs in 50L..320L) {
                            lastTapMs = 0L
                            scope.launchSafe {
                                loadWallpaperAndMask(advance = true, checkInterval = false)
                                ensureBakedMinuteScene(force = true)
                                if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                            }
                        } else {
                            lastTapMs = now
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!touchMoved) {
                        val dx = event.x - touchDownX
                        val dy = event.y - touchDownY
                        if (dx * dx + dy * dy > 32f * 32f) touchMoved = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!touchMoved && SystemClock.uptimeMillis() - touchDownMs < 450L) {
                        if (handleLayerTap(event.x, event.y)) {
                            lastActionTapMs = SystemClock.uptimeMillis()
                        }
                    }
                }
            }
            super.onTouchEvent(event)
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (isCurrentEngine() && action == WallpaperManager.COMMAND_TAP && surfaceVisible && isVisible) {
                val now = SystemClock.uptimeMillis()
                if (now - lastActionTapMs > 180L && handleLayerTap(x.toFloat(), y.toFloat())) {
                    lastActionTapMs = now
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun findActionableAt(rawX: Float, rawY: Float): com.example.prismlock.model.LayerNode? {
            val hits = activeHits
            if (hits.isEmpty()) return null
            val frame = surfaceHolder.surfaceFrame
            val viewW = frame.width().coerceAtLeast(1).toFloat()
            val viewH = frame.height().coerceAtLeast(1).toFloat()
            val baseX = rawX * (baseW / viewW)
            val baseY = rawY * (baseH / viewH)
            return TouchActionExecutor.hitTestActionable(hits, project.layers, baseX, baseY)
        }

        private fun handleLayerTap(rawX: Float, rawY: Float): Boolean {
            val hitNode = findActionableAt(rawX, rawY) ?: return false
            val env = EnvProvider.env.value.copy(locked = if (isPreview) true else isDeviceLocked())
            return TouchActionExecutor.execute(
                ctx = this@PrismWallpaperService,
                node = hitNode,
                project = project,
                env = env,
                now = LocalDateTime.now(),
                onMutateProject = { updated ->
                    project = updated
                    scope.launchSafe {
                        ProjectStore.save(this@PrismWallpaperService, updated)
                        ensureBakedMinuteScene(force = true)
                        if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                    }
                },
                onNextWallpaper = {
                    scope.launchSafe {
                        loadWallpaperAndMask(advance = true, checkInterval = false)
                        ensureBakedMinuteScene(force = true)
                        if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                    }
                }
            ).also {
                scope.launchSafe {
                    delay(120L)
                    if (EnvProvider.refreshAudio(this@PrismWallpaperService) && surfaceVisible && isVisible && isCurrentEngine()) {
                        ensureBakedMinuteScene(force = true)
                        drawOnce()
                    }
                }
            }
        }

        override fun onDestroy() {
            isDestroyed = true
            surfaceVisible = false
            isVideoActive = false
            videoRenderer?.release()
            videoRenderer = null
            initJob?.cancel()
            screenOffJob?.cancel()
            visibilityJob?.cancel()
            stopAnimations()
            clearAllRamCaches()
            renderer.release()
            runCatching { unregisterReceiver(receiver) }
            scope.cancel()
            super.onDestroy()
        }

        private fun canPlayVideo(): Boolean {
            if (!isVideoActive || isDestroyed || !isCurrentEngine()) return false
            if (!isVisible || !surfaceVisible) return false
            if (powerManager?.isInteractive != true) return false
            if (!isPreview && !isDeviceLocked()) return false
            return true
        }

        private fun syncVideoPlaybackState() {
            if (!isVideoActive || isDestroyed) {
                videoRenderer?.pause()
                renderer.clearLiveVideoMode()
                return
            }
            val vr = videoRenderer ?: return
            if (canPlayVideo()) {
                vr.play()
            } else if (!isPreview && !isDeviceLocked()) {
                vr.pauseAtFirstFrame()
            } else {
                vr.pause()
            }
        }

        private fun ensureVideoRenderer(): VideoWallpaperRenderer {
            videoRenderer?.let { return it }
            return VideoWallpaperRenderer(this@PrismWallpaperService) {
                if (frameDrawPending.compareAndSet(false, true)) {
                    mainHandler.post {
                        frameDrawPending.set(false)
                        if (isVideoActive && surfaceVisible && isVisible && isCurrentEngine() &&
                            (isPreview || isDeviceLocked() || !hasOnlyLockLayers())
                        ) {
                            drawOnce()
                        }
                    }
                }
            }.also { videoRenderer = it }
        }

        private fun clearAllRamCaches() {
            stopAnimations()
            videoRenderer?.pause()
            isVideoActive = false
            cachedScene?.recycle()
            cachedScene = null
            cachedSceneKey = ""
            currentUri = null
            bgBitmap = null
            frostedBitmap = null
            frostedBlurPx = -1f
            depthMask = null
            WallpaperBaker.clearMemoryCache()
            renderer.clearShaderCachesForHome()
        }

        private suspend fun reloadAfterBakeVersionChange(newVersion: Long) {
            if (!isCurrentEngine()) return
            loadedBakeVersion = newVersion
            clearAllRamCaches()
            withContext(Dispatchers.Default) {
                prewarmIfCrystal(project)
            }
            loadWallpaperAndMask(advance = false, checkInterval = false)
            ensureBakedMinuteScene(force = true)
            if (surfaceVisible && isVisible) {
                drawOnce()
            } else {
                drawStaticFrameForAod()
            }
        }

        private fun isDeviceLocked(): Boolean {
            val km = getSystemService(KeyguardManager::class.java)
            return km?.isKeyguardLocked ?: true
        }

        private fun needsActiveHomeLayerRendering(): Boolean =
            project.layers.flatten().any { (node, _) ->
                !node.hidden && node.type != LayerType.DEPTH && (node.cond == Cond.HOME_ONLY || node.touch.isActive)
            }

        private fun hasOnlyLockLayers(): Boolean = !needsActiveHomeLayerRendering()

        private fun releaseIntermediateBakeBuffers() {
            val f = frostedBitmap
            if (f != null && f !== bgBitmap && !f.isRecycled) {
                runCatching { f.recycle() }
            }
            frostedBitmap = null
            val d = depthMask
            if (d != null && !d.isRecycled) {
                runCatching { d.recycle() }
            }
            depthMask = null
            renderer.release()
        }

        private fun releaseLockscreenBuffersIfHomeOnly() {
            if (hasOnlyLockLayers()) {
                cachedScene?.recycle()
                cachedScene = null
                cachedSceneKey = ""
                releaseIntermediateBakeBuffers()
            }
        }

        private suspend fun refreshWeatherIfNeeded(): Boolean {
            val loc = EnvProvider.lastLocation(this@PrismWallpaperService)
            val lat = loc?.first ?: project.lat
            val lon = loc?.second ?: project.lon
            return EnvProvider.refreshWeather(this@PrismWallpaperService, lat, lon, project.city)
        }

        private suspend fun loadWallpaperAndMask(advance: Boolean, checkInterval: Boolean = false) = sceneMutex.withLock {
            if (!isCurrentEngine()) return@withLock
            val uri = WallpaperCarousel.currentUri(
                ctx = this@PrismWallpaperService,
                project = project,
                advance = advance,
                checkInterval = checkInterval
            )
            if (uri != null && (uri != currentUri || bgBitmap == null)) {
                currentUri = uri
                val video = WallpaperCarousel.isVideoUri(this@PrismWallpaperService, uri)
                isVideoActive = video
                val baked = withContext(Dispatchers.IO) {
                    WallpaperBaker.loadBakedForUri(this@PrismWallpaperService, uri, baseW, baseH)
                }
                if (baked != null) {
                    loadedBakeVersion = baked.version
                    bgBitmap = baked.sharp
                    frostedBitmap = baked.frosted
                    frostedBlurPx = baked.blurPx
                    val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                    depthMask = if (!video && project.depth && project.isDepthEnabledFor(imgKey)) baked.depthMask else null
                } else {
                    val raw = WallpaperCarousel.loadBitmap(this@PrismWallpaperService, uri, baseW, baseH)
                    bgBitmap = raw
                    frostedBitmap = null
                    frostedBlurPx = -1f
                    depthMask = if (!video && raw != null) {
                        SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, raw, project)
                    } else null
                }
                if (video) {
                    videoAdaptiveColor = renderer.extractAdaptiveColor(bgBitmap)
                    val blurPx = WallpaperBaker.resolvePrimaryBlurPx(project, baseW / 1080f)
                    val vr = ensureVideoRenderer()
                    vr.setSource(uri, baseW, baseH, blurPx)
                } else {
                    videoRenderer?.pause()
                    renderer.clearLiveVideoMode()
                }
                cachedSceneKey = ""
            } else if (uri != null) {
                val video = WallpaperCarousel.isVideoUri(this@PrismWallpaperService, uri)
                isVideoActive = video
                if (video) {
                    depthMask = null
                    val blurPx = WallpaperBaker.resolvePrimaryBlurPx(project, baseW / 1080f)
                    ensureVideoRenderer().setSource(uri, baseW, baseH, blurPx)
                } else {
                    videoRenderer?.pause()
                    renderer.clearLiveVideoMode()
                    val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                    if (!project.depth || !project.isDepthEnabledFor(imgKey)) {
                        depthMask = null
                    } else if (depthMask == null) {
                        val baked = withContext(Dispatchers.IO) {
                            WallpaperBaker.loadBakedForUri(this@PrismWallpaperService, uri, baseW, baseH)
                        }
                        depthMask = baked?.depthMask ?: bgBitmap?.let { bmp ->
                            SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, bmp, project)
                        }
                    }
                }
            }
            if (isCurrentEngine()) {
                syncVideoPlaybackState()
            }
        }

        private suspend fun ensureBakedMinuteScene(force: Boolean) = sceneMutex.withLock {
            if (!isCurrentEngine()) return@withLock
            if (!isDeviceLocked() && hasOnlyLockLayers()) {
                releaseLockscreenBuffersIfHomeOnly()
                return@withLock
            }
            val now = LocalDateTime.now()
            val env = EnvProvider.env.value.copy(locked = if (isPreview) true else isDeviceLocked())
            val uri = currentUri
            val wantDepthNow = !isVideoActive && uri != null && project.depth &&
                project.isDepthEnabledFor(WallpaperCarousel.imageKey(this@PrismWallpaperService, uri))
            val key = "${loadedBakeVersion}|${uri}|${baseW}x${baseH}|${now.year}-${now.dayOfYear}-${now.hour}:${now.minute}|${env.locked}|${env.battery}|${env.charging}|${env.temp}|${env.weatherCode}|${env.mediaVolume}|${env.mediaPlaying}|${project.hashCode()}|$wantDepthNow"
            if (!force && key == cachedSceneKey && cachedScene != null) return@withLock

            if (uri != null && loadedBakeVersion > 0L && !isVideoActive) {
                if (frostedBitmap == null) {
                    frostedBitmap = withContext(Dispatchers.IO) {
                        WallpaperBaker.loadBakedFrostedBitmap(this@PrismWallpaperService, uri, loadedBakeVersion)
                    }
                }
                if (wantDepthNow && depthMask == null) {
                    depthMask = withContext(Dispatchers.IO) {
                        WallpaperBaker.loadBakedDepthBitmap(this@PrismWallpaperService, uri, loadedBakeVersion)
                    }
                }
            }

            val sharp = bgBitmap
            val frosted = frostedBitmap
            val preBlur = frostedBlurPx
            val mask = depthMask
            val proj = project
            val bw = baseW
            val bh = baseH
            val isSteady = wakeProgress >= 0.998f && activeAnimator?.isRunning != true
            val freshHits = ArrayList<HitBox>()
            val scene = withContext(Dispatchers.Default) {
                renderer.bakeMinuteScene(
                    w = bw,
                    h = bh,
                    project = proj,
                    sharpBg = sharp,
                    frostedBg = frosted,
                    depthMask = mask,
                    env = env,
                    now = now,
                    prebakedBlurPx = preBlur,
                    hits = freshHits,
                    includeCompleteBitmap = true,
                    useRgb565ForComplete = true,
                    retainOnlyComplete = isSteady
                )
            }
            if (isCurrentEngine()) {
                val old = cachedScene
                cachedScene = scene
                cachedSceneKey = key
                activeHits = freshHits
                if (old != null && old !== scene) old.recycle()
                if (isSteady) {
                    releaseIntermediateBakeBuffers()
                }
            } else {
                scene.recycle()
            }
        }

        private fun startWakeAnimation() {
            if (!isCurrentEngine()) return
            stopAnimations()
            wakeProgress = 0f
            drawOnce()
            activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500L
                interpolator = wakeInterpolator
                addUpdateListener { anim ->
                    if (!surfaceVisible || !isVisible || !isCurrentEngine()) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    wakeProgress = anim.animatedValue as Float
                    drawOnce()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (isCurrentEngine() && wakeProgress >= 0.998f) {
                            cachedScene = cachedScene?.trimToCompleteRgb565()
                            releaseIntermediateBakeBuffers()
                        }
                    }
                })
                start()
            }
        }

        private fun startUnlockFade() {
            if (!isCurrentEngine()) return
            if (lockAlpha <= 0.01f) {
                lockAlpha = 0f
                releaseLockscreenBuffersIfHomeOnly()
                drawOnce()
                return
            }
            stopAnimations()
            val startAlpha = lockAlpha
            activeAnimator = ValueAnimator.ofFloat(startAlpha, 0f).apply {
                duration = 260L
                interpolator = linearInterpolator
                addUpdateListener { anim ->
                    if (!surfaceVisible || !isVisible || !isCurrentEngine()) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    lockAlpha = anim.animatedValue as Float
                    if (lockAlpha <= 0.01f) {
                        releaseLockscreenBuffersIfHomeOnly()
                    }
                    drawOnce()
                }
                start()
            }
        }

        private fun stopAnimations() {
            activeAnimator?.cancel()
            activeAnimator = null
        }

        private fun drawStaticFrameForAod() {
            if (!isCurrentEngine()) return
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val scene = cachedScene ?: return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                renderer.drawWakeAnimationFrame(
                    canvas = canvas,
                    dstW = canvas.width,
                    dstH = canvas.height,
                    project = project,
                    sharpBg = bgBitmap,
                    scene = scene,
                    depthMask = depthMask,
                    wakeProgress = 1f,
                    lockAlpha = 1f
                )
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawOnce() {
            if (!surfaceVisible || !isVisible || !isCurrentEngine()) return
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                if (!isPreview && !isDeviceLocked() && lockAlpha <= 0.01f && hasOnlyLockLayers()) {
                    renderer.drawUnlockedHomeFrame(
                        canvas = canvas,
                        dstW = canvas.width,
                        dstH = canvas.height,
                        project = project,
                        sharpBg = bgBitmap
                    )
                    return
                }
                if (isVideoActive) {
                    val drewVideo = videoRenderer?.withCurrentFrame { frame ->
                        val env = EnvProvider.env.value.copy(locked = if (isPreview) true else isDeviceLocked())
                        renderer.drawLiveVideoFrame(
                            canvas = canvas,
                            viewW = canvas.width,
                            viewH = canvas.height,
                            baseW = baseW,
                            baseH = baseH,
                            project = project,
                            sharpBmp = frame.sharpBitmap,
                            sharpShader = frame.sharpShader,
                            frostedBmp = frame.frostedBitmap,
                            frostedShader = frame.frostedShader,
                            env = env,
                            now = LocalDateTime.now(),
                            adaptiveColor = videoAdaptiveColor,
                            hits = videoHitsBuffer
                        )
                        activeHits = videoHitsBuffer.toList()
                        true
                    } ?: false
                    if (drewVideo) return
                }
                val scene = cachedScene
                if (scene != null) {
                    renderer.drawWakeAnimationFrame(
                        canvas = canvas,
                        dstW = canvas.width,
                        dstH = canvas.height,
                        project = project,
                        sharpBg = bgBitmap,
                        scene = scene,
                        depthMask = depthMask,
                        wakeProgress = wakeProgress,
                        lockAlpha = lockAlpha
                    )
                } else {
                    renderer.renderScaledBaseFrame(
                        canvas = canvas,
                        viewW = canvas.width,
                        viewH = canvas.height,
                        baseW = baseW,
                        baseH = baseH,
                        project = project,
                        bg = bgBitmap,
                        depthMask = depthMask,
                        env = EnvProvider.env.value,
                        now = LocalDateTime.now()
                    )
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
