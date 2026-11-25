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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PorterDuff
import android.graphics.RenderNode
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
import kotlinx.coroutines.launch
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
        /** Idle time before the presenter is stopped so HWUI can drop its GPU context. */
        private const val PRESENTER_IDLE_MS = 1500L
        /** Time invisible before every RAM/GPU cache is dropped. */
        private const val HIBERNATE_DELAY_MS = 3000L
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
            com.example.prismlock.engine.StemStretch.clearCaches()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        onTrimMemory(TRIM_MEMORY_UI_HIDDEN)
    }

    inner class PrismEngine : Engine() {
        private val engineId = engineCounter.incrementAndGet()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = LiquidGlassRenderer()
        private val sceneMutex = Mutex()
        private val wakeInterpolator = OvershootInterpolator(1.08f)
        private val linearInterpolator = LinearInterpolator()

        private var project = Project()
            set(value) {
                field = value
                homeLayersActive = computeHomeLayersActive(value)
            }
        private var homeLayersActive = computeHomeLayersActive(project)
        private var liveReceiverRegistered = false
        private var currentUri: String? = null
        private var bgBitmap: Bitmap? = null
        private var frostedBitmap: Bitmap? = null
        private var frostedBlurPx: Float = -1f
        private var depthMask: Bitmap? = null
        private var depthMaskOwned = false
        private var bgOwned = false
        private var cachedScene: LiquidGlassRenderer.BakedMinuteScene? = null
        private var cachedSceneKey: String = ""
        private var loadedBakeVersion: Long = -1L

        // Base proportional FHD+ working space (max 1080px width) scaled to physical surface dimensions
        private var baseW = 1080
        private var baseH = 2400

        private var activeAnimator: ValueAnimator? = null
        private var visibilityJob: Job? = null
        private var screenOffJob: Job? = null
        private var hibernateJob: Job? = null
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

        /**
         * Own HardwareRenderer on the wallpaper Surface instead of lockHardwareCanvas: the Surface's
         * implicit renderer can never be stopped, so HWUI keeps the GPU context, Skia cache and render
         * targets forever. A stopped renderer lets HWUI tear the context down once every renderer in the
         * process is idle; the last frame stays on screen (SurfaceFlinger keeps the queued buffer).
         */
        private var presenter: HardwareRenderer? = null
        private val presentNode = RenderNode("prism_wallpaper")
        private var presenterRunning = false
        private val stopPresenter = Runnable {
            if (!isVisible || !isInteractive()) {
                // Nobody can see us: drop the surface connection too, freeing the queue's buffers.
                releasePresenter()
            } else {
                presenter?.stop()
                presenterRunning = false
            }
        }
        private val frameDrawPending = AtomicBoolean(false)
        private val powerManager by lazy { getSystemService(PowerManager::class.java) }
        private var videoRenderer: VideoWallpaperRenderer? = null
        private var nextWallpaperJob: kotlinx.coroutines.Job? = null
        private var motionSource: MotionSource? = null
        private var motionCollectJob: kotlinx.coroutines.Job? = null
        @Volatile private var motionNormX: Float = 0f
        @Volatile private var motionNormY: Float = 0f
        private var lastDrawnMotionX: Float = 0f
        private var lastDrawnMotionY: Float = 0f
        private var motionFramePending: Boolean = false
        private val motionFrameCallback = android.view.Choreographer.FrameCallback {
            motionFramePending = false
            if (surfaceVisible && isVisible && isCurrentEngine() && activeAnimator?.isRunning != true) {
                lastDrawnMotionX = motionNormX
                lastDrawnMotionY = motionNormY
                drawOnce()
            }
        }
        @Volatile private var isVideoActive = false
        @Volatile private var videoAdaptiveColor = 0xFF9AC5FF.toInt()

        private fun syncMotionState() {
            val m = project.motion
            val locked = isPreview || isDeviceLocked()
            val shouldRun = !isDestroyed && surfaceVisible && isVisible && isCurrentEngine() &&
                !isVideoActive && m.enabled && !project.isWidget && (locked || m.homeScreen)
            if (!shouldRun) {
                stopMotionEngine()
                return
            }
            val src = motionSource ?: MotionSource(this@PrismWallpaperService).also { motionSource = it }
            if (!src.start(m)) {
                stopMotionEngine()
                return
            }
            if (motionCollectJob?.isActive != true) {
                motionCollectJob = scope.launchSafe {
                    src.offset.collect { (nx, ny) ->
                        motionNormX = nx
                        motionNormY = ny
                        val maxShift = project.motion.maxShiftPx(baseW)
                        val dxPx = kotlin.math.abs(nx - lastDrawnMotionX) * maxShift
                        val dyPx = kotlin.math.abs(ny - lastDrawnMotionY) * maxShift
                        if ((dxPx > 0.25f || dyPx > 0.25f) && !motionFramePending && surfaceVisible && isVisible && isCurrentEngine()) {
                            motionFramePending = true
                            android.view.Choreographer.getInstance().postFrameCallback(motionFrameCallback)
                        }
                    }
                }
            }
        }

        private fun stopMotionEngine() {
            motionCollectJob?.cancel()
            motionCollectJob = null
            if (motionFramePending) {
                android.view.Choreographer.getInstance().removeFrameCallback(motionFrameCallback)
                motionFramePending = false
            }
            motionSource?.stop()
            motionNormX = 0f
            motionNormY = 0f
            lastDrawnMotionX = 0f
            lastDrawnMotionY = 0f
        }

        /** Sempre registrado: eventos de ciclo de tela e IPC (raros, sem custo em repouso). */
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handleBroadcast(intent)
        }

        /**
         * TIME_TICK/BATTERY_CHANGED só existem enquanto o wallpaper está visível com a tela ligada.
         * Tela apagada ou app em primeiro plano = nenhum wakeup do processo :wallpaper.
         */
        private val liveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handleBroadcast(intent)
        }

        private fun isInteractive(): Boolean = powerManager?.isInteractive == true

        private fun refreshWidgetsIfHomeVisible() {
            if (isPreview || isDeviceLocked() || !isInteractive()) return
            if (PrismWidgetProvider.hasPinnedWidgets(this@PrismWallpaperService)) {
                PrismWidgetProvider.updateAll(this@PrismWallpaperService)
            }
        }

        private fun syncLiveReceiver() {
            val want = !isDestroyed && isCurrentEngine() && isVisible && isInteractive()
            if (want == liveReceiverRegistered) return
            if (want) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                }
                registerReceiver(liveReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                liveReceiverRegistered = true
            } else {
                runCatching { unregisterReceiver(liveReceiver) }
                liveReceiverRegistered = false
            }
        }

        private fun handleBroadcast(intent: Intent) {
            if (!isCurrentEngine()) return
            when (intent.action) {
                Intent.ACTION_TIME_TICK -> {
                    if (!isInteractive()) return
                    refreshWidgetsIfHomeVisible()
                    if (!isDeviceLocked() && hasOnlyLockLayers()) return
                    EnvProvider.refreshAlarm(this@PrismWallpaperService)
                    scope.launchSafe {
                        ensureBakedMinuteScene(force = false)
                        if (isCurrentEngine() && surfaceVisible && isVisible) drawOnce()
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val prev = EnvProvider.env.value
                    if (EnvProvider.applyBattery(intent)) {
                        val cur = EnvProvider.env.value
                        if (isInteractive() && (prev.battery != cur.battery || prev.charging != cur.charging)) {
                            refreshWidgetsIfHomeVisible()
                            if (isDeviceLocked() || (surfaceVisible && isVisible && !hasOnlyLockLayers())) {
                                scope.launchSafe {
                                    ensureBakedMinuteScene(force = true)
                                    if (isCurrentEngine() && surfaceVisible && isVisible) drawOnce()
                                }
                            }
                        }
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    EnvProvider.update { it.copy(locked = false) }
                    refreshWidgetsIfHomeVisible()
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
                    syncMotionState()
                }
                Intent.ACTION_SCREEN_ON -> {
                    EnvProvider.refreshBattery(this@PrismWallpaperService)
                    syncLiveReceiver()
                    syncVideoPlaybackState()
                    syncMotionState()
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
                    syncLiveReceiver()
                    releaseVideoPipeline()
                    stopMotionEngine()
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
                        trimAndReleaseBuffers()
                    }
                    // Some devices keep the engine "visible" while dozing: don't rely on onVisibilityChanged.
                    scheduleHibernate()
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
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(WallpaperBaker.ACTION_BAKED_READY)
                addAction(ProjectStore.ACTION_RELOAD_PROJECT)
            }
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

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
                hibernateJob?.cancel()
                hibernateJob = null
                claimEngineSlot()
                surfaceVisible = true
                EnvProvider.refreshBattery(this@PrismWallpaperService)
                syncLiveReceiver()
                EnvProvider.refreshAudio(this@PrismWallpaperService)
                val locked = isDeviceLocked()
                EnvProvider.update { it.copy(locked = locked) }
                if (locked) {
                    lockAlpha = 1f
                    EnvProvider.refreshAlarm(this@PrismWallpaperService)
                } else {
                    lockAlpha = 0f
                    refreshWidgetsIfHomeVisible()
                    if (hasOnlyLockLayers() && !isPreview) {
                        releaseLockscreenBuffersIfHomeOnly()
                        syncVideoPlaybackState()
                        drawOnce()
                        return
                    }
                }

                syncVideoPlaybackState()
                syncMotionState()
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
                syncLiveReceiver()
                releaseVideoPipeline()
                stopMotionEngine()
                visibilityJob?.cancel()
                stopAnimations()
                scheduleHibernate()
            }
        }

        /**
         * Invisible (unlocked on another wallpaper, or display off) = hold nothing. SurfaceFlinger keeps
         * showing the last queued frame, so the next wake reloads from the on-disk bake while the old
         * frame is still on screen. The delay absorbs quick lock/unlock flicker.
         */
        private fun scheduleHibernate() {
            if (isPreview || isDestroyed) return
            hibernateJob?.cancel()
            hibernateJob = scope.launchSafe {
                delay(HIBERNATE_DELAY_MS)
                screenOffJob?.join()
                if ((isVisible && isInteractive()) || isDestroyed) return@launchSafe
                sceneMutex.withLock {
                    if (isVisible && isInteractive()) return@withLock
                    // Locked (screen off / AOD): keep ONLY the finished minute frame (one ~5 MB RGB_565
                    // HardwareBuffer) so the next wake paints instantly instead of decoding the photo,
                    // the 3D mask and re-baking before the first frame. Everything else is dropped.
                    val wakeFrame = cachedScene?.takeIf { isDeviceLocked() && it.isSingleCompleteFrame() }
                    val wakeKey = cachedSceneKey
                    if (wakeFrame != null) cachedScene = null
                    clearAllRamCachesLocked()
                    if (wakeFrame != null) {
                        cachedScene = wakeFrame
                        cachedSceneKey = wakeKey
                    }
                    renderer.release()
                }
                releasePresenter()
                SubjectDepthEngine.releaseMemory()
                WallpaperCarousel.clearMemoryCache()
                com.example.prismlock.engine.StemStretch.clearCaches()
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
                            if (nextWallpaperJob?.isActive != true) {
                                nextWallpaperJob = scope.launchSafe {
                                    loadWallpaperAndMask(advance = true, checkInterval = false)
                                    ensureBakedMinuteScene(force = true)
                                    if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                                }
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
                    val now = SystemClock.uptimeMillis()
                    if (!touchMoved && now - touchDownMs < 450L && now - lastActionTapMs > 180L) {
                        if (handleLayerTap(event.x, event.y)) {
                            lastActionTapMs = now
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
            val motion = project.motion
            val maxShift = if (motion.enabled && !project.isWidget) motion.maxShiftPx(baseW) else 0f
            val itemDx = motionNormX * maxShift * motion.itemsDepth
            val itemDy = motionNormY * maxShift * motion.itemsDepth
            val baseX = rawX * (baseW / viewW) - itemDx
            val baseY = rawY * (baseH / viewH) - itemDy
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
                    if (nextWallpaperJob?.isActive != true) {
                        nextWallpaperJob = scope.launchSafe {
                            loadWallpaperAndMask(advance = true, checkInterval = false)
                            ensureBakedMinuteScene(force = true)
                            if (surfaceVisible && isVisible && isCurrentEngine()) drawOnce()
                        }
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

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePresenter()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            isDestroyed = true
            hibernateJob?.cancel()
            releasePresenter()
            stopMotionEngine()
            surfaceVisible = false
            isVideoActive = false
            videoRenderer?.release()
            videoRenderer = null
            initJob?.cancel()
            screenOffJob?.cancel()
            visibilityJob?.cancel()
            stopAnimations()
            CoroutineScope(Dispatchers.Main.immediate).launch {
                sceneMutex.withLock {
                    clearAllRamCachesLocked()
                    renderer.release()
                }
            }
            runCatching { unregisterReceiver(receiver) }
            if (liveReceiverRegistered) {
                runCatching { unregisterReceiver(liveReceiver) }
                liveReceiverRegistered = false
            }
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
                releaseVideoPipeline()
                renderer.clearLiveVideoMode()
                return
            }
            if (canPlayVideo()) {
                val uri = currentUri ?: return
                val vr = videoRenderer ?: ensureVideoRenderer().also {
                    it.setSource(uri, baseW, baseH, WallpaperBaker.resolvePrimaryBlurPx(project, baseW / 1080f))
                }
                vr.play()
            } else {
                // Fora da tela de bloqueio visível o vídeo não aparece: libera MediaPlayer, contexto EGL e
                // HardwareBuffers. O frame baked (cachedScene / bgBitmap) cobre a tela até o próximo play.
                releaseVideoPipeline()
            }
        }

        private fun releaseVideoPipeline() {
            val vr = videoRenderer ?: return
            videoRenderer = null
            vr.release()
            renderer.clearLiveVideoMode()
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

        private fun clearAllRamCachesLocked() {
            stopAnimations()
            releaseVideoPipeline()
            isVideoActive = false
            cachedScene?.recycle()
            cachedScene = null
            cachedSceneKey = ""
            currentUri = null
            releaseOwnedBgLocked()
            frostedBitmap = null
            frostedBlurPx = -1f
            depthMask = null
            depthMaskOwned = false
            WallpaperBaker.clearMemoryCache()
            renderer.clearShaderCachesForHome()
        }

        private suspend fun reloadAfterBakeVersionChange(newVersion: Long) {
            if (!isCurrentEngine()) return
            loadedBakeVersion = newVersion
            sceneMutex.withLock { clearAllRamCachesLocked() }
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

        private fun releaseOwnedBgLocked() {
            val bg = bgBitmap
            if (bg != null && bgOwned && !bg.isRecycled) runCatching { bg.recycle() }
            bgBitmap = null
            bgOwned = false
        }

        private fun isDeviceLocked(): Boolean {
            val km = getSystemService(KeyguardManager::class.java)
            return km?.isKeyguardLocked ?: true
        }

        private fun computeHomeLayersActive(p: Project): Boolean =
            p.layers.flatten().any { (node, _) ->
                !node.hidden && node.type != LayerType.DEPTH && (node.cond == Cond.HOME_ONLY || node.touch.isActive)
            }

        private fun hasOnlyLockLayers(): Boolean = !homeLayersActive

        /** Paralaxe precisa das camadas separadas (fundo, relógio, sujeito); sem ela basta o frame completo. */
        private fun needsSeparateLayers(): Boolean = project.motion.enabled && !project.isWidget

        private fun releaseIntermediateBakeBuffersLocked() {
            val f = frostedBitmap
            if (f != null && f !== bgBitmap && !f.isRecycled) {
                runCatching { f.recycle() }
            }
            frostedBitmap = null
            if (!needsSeparateLayers()) {
                val d = depthMask
                if (d != null && depthMaskOwned && d !== bgBitmap && !d.isRecycled) {
                    runCatching { d.recycle() }
                }
                depthMask = null
                depthMaskOwned = false
            }
            renderer.release()
            promoteSteadyBitmapsToGpuLocked()
        }

        /**
         * Em repouso os bitmaps só são desenhados pelo canvas de hardware: movê-los para HARDWARE
         * elimina a cópia dupla (heap nativo + textura da GPU) que o HWUI mantém para bitmaps de software.
         */
        private fun promoteSteadyBitmapsToGpuLocked() {
            if (isVideoActive) return
            val bg = bgBitmap
            if (bg != null && bgOwned && !bg.isRecycled && bg.config != Bitmap.Config.HARDWARE) {
                bg.copy(Bitmap.Config.HARDWARE, false)?.let { hw ->
                    bgBitmap = hw
                    bg.recycle()
                }
            }
            val scene = cachedScene
            if (scene != null && scene.isSingleCompleteFrame()) {
                cachedScene = scene.toHardware()
            }
        }

        /** Bake de software (Canvas de bitmap, getPixel) exige o fundo fora da GPU: readback único por despertar. */
        private fun ensureSoftwareBgLocked() {
            val bg = bgBitmap ?: return
            if (bg.isRecycled || bg.config != Bitmap.Config.HARDWARE) return
            bg.copy(Bitmap.Config.RGB_565, false)?.let { sw ->
                bgBitmap = sw
                bg.recycle()
            }
        }

        private fun releaseLockscreenBuffersIfHomeOnlyLocked() {
            if (hasOnlyLockLayers()) {
                cachedScene?.recycle()
                cachedScene = null
                cachedSceneKey = ""
                releaseIntermediateBakeBuffersLocked()
            }
        }

        private fun releaseLockscreenBuffersIfHomeOnly() {
            scope.launchSafe {
                sceneMutex.withLock { releaseLockscreenBuffersIfHomeOnlyLocked() }
            }
        }

        private suspend fun trimAndReleaseBuffers() = sceneMutex.withLock {
            if (!isCurrentEngine() || wakeProgress < 0.998f) return@withLock
            if (!needsSeparateLayers()) {
                cachedScene = cachedScene?.trimToCompleteRgb565()
            }
            releaseIntermediateBakeBuffersLocked()
        }

        private suspend fun refreshWeatherIfNeeded(): Boolean {
            val loc = EnvProvider.lastLocation(this@PrismWallpaperService)
            val lat = loc?.first ?: project.lat
            val lon = loc?.second ?: project.lon
            return EnvProvider.refreshWeather(this@PrismWallpaperService, lat, lon, project.city)
        }

        private suspend fun loadWallpaperAndMask(advance: Boolean, checkInterval: Boolean = false) = sceneMutex.withLock {
            loadWallpaperAndMaskLocked(advance, checkInterval)
        }

        private suspend fun loadWallpaperAndMaskLocked(advance: Boolean, checkInterval: Boolean = false) {
            if (!isCurrentEngine()) return
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
                releaseOwnedBgLocked()
                if (baked != null) {
                    loadedBakeVersion = baked.version
                    bgBitmap = baked.sharp
                    bgOwned = true
                    frostedBitmap = baked.frosted
                    frostedBlurPx = baked.blurPx
                    val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                    depthMask = if (!video && project.depth && project.isDepthEnabledFor(imgKey)) baked.depthMask else null
                    depthMaskOwned = depthMask != null
                } else {
                    val raw = WallpaperCarousel.loadBitmap(this@PrismWallpaperService, uri, baseW, baseH)
                    bgBitmap = raw
                    bgOwned = false
                    frostedBitmap = null
                    frostedBlurPx = -1f
                    depthMask = if (!video && raw != null) {
                        SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, raw, project)
                    } else null
                    depthMaskOwned = false
                }
                if (video) {
                    videoAdaptiveColor = renderer.extractAdaptiveColor(bgBitmap)
                    videoRenderer?.setSource(uri, baseW, baseH, WallpaperBaker.resolvePrimaryBlurPx(project, baseW / 1080f))
                } else {
                    releaseVideoPipeline()
                }
                cachedSceneKey = ""
            } else if (uri != null) {
                val video = WallpaperCarousel.isVideoUri(this@PrismWallpaperService, uri)
                isVideoActive = video
                if (video) {
                    depthMask = null
                    videoRenderer?.setSource(uri, baseW, baseH, WallpaperBaker.resolvePrimaryBlurPx(project, baseW / 1080f))
                } else {
                    releaseVideoPipeline()
                    val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                    if (!project.depth || !project.isDepthEnabledFor(imgKey)) {
                        depthMask = null
                    } else if (depthMask == null) {
                        val baked = withContext(Dispatchers.IO) {
                            WallpaperBaker.loadBakedForUri(this@PrismWallpaperService, uri, baseW, baseH)
                        }
                        val bakedDepth = baked?.depthMask
                        depthMask = bakedDepth ?: bgBitmap?.let { bmp ->
                            SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, bmp, project)
                        }
                        depthMaskOwned = bakedDepth != null
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
                releaseLockscreenBuffersIfHomeOnlyLocked()
                return@withLock
            }
            // After hibernation the photo/mask are gone; every bake path (wake, SCREEN_ON, TIME_TICK)
            // must reload them first or it would bake a frame without the wallpaper photo.
            if (bgBitmap == null) loadWallpaperAndMaskLocked(advance = false)
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
                    depthMaskOwned = depthMask != null
                }
            }

            ensureSoftwareBgLocked()
            val sharp = bgBitmap
            val frosted = frostedBitmap
            val preBlur = frostedBlurPx
            val mask = depthMask
            val proj = project
            val bw = baseW
            val bh = baseH
            val isSteady = wakeProgress >= 0.998f && activeAnimator?.isRunning != true
            val keepLayers = needsSeparateLayers()
            // Tela de bloqueio acesa: o próximo bake vem no próximo minuto, manter fontes evita decodificar do disco a cada TIME_TICK.
            val holdSources = env.locked && isInteractive() && surfaceVisible
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
                    retainOnlyComplete = isSteady && !keepLayers
                )
            }
            if (isCurrentEngine()) {
                val old = cachedScene
                cachedScene = scene
                cachedSceneKey = key
                activeHits = freshHits
                if (old != null && old !== scene) old.recycle()
                // Bake done: the offscreen HardwareRenderers + ImageReaders (GPU buffers per pass) idle until the next minute.
                if (isSteady) renderer.releaseOffscreenResources()
                if (isSteady && !holdSources) {
                    releaseIntermediateBakeBuffersLocked()
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
                            scope.launchSafe { trimAndReleaseBuffers() }
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

        private fun releasePresenter() {
            mainHandler.removeCallbacks(stopPresenter)
            presenter?.destroy()
            presenter = null
            presenterRunning = false
            presentNode.discardDisplayList()
        }

        /** Records one frame into [presentNode] and pushes it; stops the renderer after [PRESENTER_IDLE_MS] without frames. */
        private fun present(draw: (Canvas, Int, Int) -> Unit) {
            val holder = surfaceHolder
            val surface = holder.surface
            if (surface == null || !surface.isValid) return
            val frame = holder.surfaceFrame
            val w = frame.width()
            val h = frame.height()
            if (w <= 0 || h <= 0) return
            val r = presenter ?: HardwareRenderer().also {
                it.setContentRoot(presentNode)
                it.isOpaque = true
                it.setSurface(surface)
                presenter = it
            }
            if (!presenterRunning) {
                r.start()
                presenterRunning = true
            }
            presentNode.setPosition(0, 0, w, h)
            val canvas = presentNode.beginRecording(w, h)
            try {
                draw(canvas, w, h)
            } finally {
                presentNode.endRecording()
            }
            r.createRenderRequest().syncAndDraw()
            mainHandler.removeCallbacks(stopPresenter)
            mainHandler.postDelayed(stopPresenter, PRESENTER_IDLE_MS)
        }

        private fun drawStaticFrameForAod() {
            if (!isCurrentEngine()) return
            val scene = cachedScene ?: return
            present { canvas, w, h ->
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                renderer.drawWakeAnimationFrame(
                    canvas = canvas,
                    dstW = w,
                    dstH = h,
                    project = project,
                    sharpBg = bgBitmap,
                    scene = scene,
                    depthMask = depthMask,
                    wakeProgress = 1f,
                    lockAlpha = 1f
                )
            }
        }

        private fun drawOnce() {
            if (!surfaceVisible || !isVisible || !isCurrentEngine()) return
            present { canvas, w, h ->
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                if (!isPreview && !isDeviceLocked() && lockAlpha <= 0.01f && hasOnlyLockLayers()) {
                    renderer.drawUnlockedHomeFrame(
                        canvas = canvas,
                        dstW = w,
                        dstH = h,
                        project = project,
                        sharpBg = bgBitmap,
                        motionNormX = motionNormX,
                        motionNormY = motionNormY
                    )
                    return@present
                }
                if (isVideoActive) {
                    val drewVideo = videoRenderer?.withCurrentFrame { frame ->
                        val env = EnvProvider.env.value.copy(locked = if (isPreview) true else isDeviceLocked())
                        renderer.drawLiveVideoFrame(
                            canvas = canvas,
                            viewW = w,
                            viewH = h,
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
                    if (drewVideo) return@present
                }
                val scene = cachedScene
                if (scene != null) {
                    renderer.drawWakeAnimationFrame(
                        canvas = canvas,
                        dstW = w,
                        dstH = h,
                        project = project,
                        sharpBg = bgBitmap,
                        scene = scene,
                        depthMask = depthMask,
                        wakeProgress = wakeProgress,
                        lockAlpha = lockAlpha,
                        motionNormX = motionNormX,
                        motionNormY = motionNormY
                    )
                } else if (bgBitmap?.config == Bitmap.Config.HARDWARE) {
                    renderer.drawUnlockedHomeFrame(
                        canvas = canvas,
                        dstW = w,
                        dstH = h,
                        project = project,
                        sharpBg = bgBitmap,
                        motionNormX = motionNormX,
                        motionNormY = motionNormY
                    )
                } else if (!sceneMutex.isLocked) {
                    renderer.renderScaledBaseFrame(
                        canvas = canvas,
                        viewW = w,
                        viewH = h,
                        baseW = baseW,
                        baseH = baseH,
                        project = project,
                        bg = bgBitmap,
                        depthMask = depthMask,
                        env = EnvProvider.env.value,
                        now = LocalDateTime.now()
                    )
                }
            }
        }
    }
}
