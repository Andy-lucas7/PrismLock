package com.example.prismlock.wallpaper

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Cond
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.flatten
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class PrismWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PrismEngine()

    inner class PrismEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderer = LiquidGlassRenderer()
        private val wakeInterpolator = OvershootInterpolator(1.08f)
        private val linearInterpolator = LinearInterpolator()

        private var project = Project()
        private var currentUri: String? = null
        private var bgBitmap: Bitmap? = null
        private var frostedBitmap: Bitmap? = null
        private var depthMask: Bitmap? = null
        private var cachedScene: LiquidGlassRenderer.BakedMinuteScene? = null
        private var cachedSceneKey: String = ""
        private var surfaceW = 1080
        private var surfaceH = 2400

        private var activeAnimator: ValueAnimator? = null
        private var visibilityJob: Job? = null
        private var screenOffJob: Job? = null
        private var surfaceVisible = false
        private var wakeProgress = 1f
        private var lockAlpha = 1f
        private var lastTapMs = 0L

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_TIME_TICK -> {
                        if (!surfaceVisible || !isVisible) return
                        if (!isDeviceLocked() && hasOnlyLockLayers()) return
                        EnvProvider.refreshAlarm(this@PrismWallpaperService)
                        scope.launchSafe {
                            ensureBakedMinuteScene(force = false)
                            if (surfaceVisible && isVisible) drawOnce()
                        }
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        if (!surfaceVisible || !isVisible) return
                        if (!isDeviceLocked() && hasOnlyLockLayers()) return
                        if (EnvProvider.applyBattery(intent)) {
                            scope.launchSafe {
                                ensureBakedMinuteScene(force = true)
                                if (surfaceVisible && isVisible) drawOnce()
                            }
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        EnvProvider.update { it.copy(locked = false) }
                        if (surfaceVisible && isVisible) {
                            startUnlockFade()
                        } else {
                            lockAlpha = 0f
                            releaseLockscreenBuffersIfHomeOnly()
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        val locked = true
                        EnvProvider.update { it.copy(locked = locked) }
                        lockAlpha = 1f
                        wakeProgress = 1f
                        surfaceVisible = false
                        visibilityJob?.cancel()
                        stopAnimations()
                        screenOffJob?.cancel()
                        screenOffJob = scope.launchSafe {
                            delay(150L)
                            loadWallpaperAndMask(
                                advance = project.changeOnWake,
                                checkInterval = project.intervalMin > 0
                            )
                            ensureBakedMinuteScene(force = true)
                            drawStaticFrameForAod()
                        }
                    }
                    WallpaperBaker.ACTION_BAKED_READY -> {
                        scope.launchSafe {
                            WallpaperBaker.prewarmGlyphAtlases(project, surfaceW)
                            currentUri = null
                            loadWallpaperAndMask(advance = false, checkInterval = false)
                            ensureBakedMinuteScene(force = true)
                            if (surfaceVisible && isVisible) drawOnce()
                        }
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            FontManager.init(this@PrismWallpaperService)
            setTouchEventsEnabled(true)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(WallpaperBaker.ACTION_BAKED_READY)
            }
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

            EnvProvider.refreshBattery(this@PrismWallpaperService)
            EnvProvider.refreshAlarm(this@PrismWallpaperService)
            EnvProvider.update { it.copy(locked = isDeviceLocked()) }

            scope.launchSafe {
                ProjectStore.flow(this@PrismWallpaperService).collectLatest { p ->
                    project = p
                    withContext(Dispatchers.Default) {
                        WallpaperBaker.prewarmGlyphAtlases(p, surfaceW)
                    }
                    loadWallpaperAndMask(advance = false, checkInterval = false)
                    ensureBakedMinuteScene(force = true)
                    refreshWeatherIfNeeded()
                    if (surfaceVisible && isVisible) drawOnce()
                }
            }
            scope.launchSafe {
                EnvProvider.env.collectLatest {
                    if (surfaceVisible && isVisible && activeAnimator?.isRunning != true) {
                        ensureBakedMinuteScene(force = false)
                        drawOnce()
                    }
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width.coerceAtLeast(1)
            surfaceH = height.coerceAtLeast(1)
            scope.launchSafe {
                withContext(Dispatchers.Default) {
                    WallpaperBaker.prewarmGlyphAtlases(project, surfaceW)
                }
                loadWallpaperAndMask(advance = false, checkInterval = false)
                ensureBakedMinuteScene(force = true)
                if (surfaceVisible && isVisible) drawOnce()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                surfaceVisible = true
                val locked = isDeviceLocked()
                EnvProvider.update { it.copy(locked = locked) }
                if (locked) {
                    lockAlpha = 1f
                    EnvProvider.refreshAlarm(this@PrismWallpaperService)
                } else {
                    lockAlpha = 0f
                    if (hasOnlyLockLayers()) {
                        releaseLockscreenBuffersIfHomeOnly()
                        drawOnce()
                        return
                    }
                }

                visibilityJob?.cancel()
                visibilityJob = scope.launchSafe {
                    screenOffJob?.join()
                    loadWallpaperAndMask(advance = false, checkInterval = false)
                    ensureBakedMinuteScene(force = false)
                    if (surfaceVisible && isVisible) {
                        if (locked) startWakeAnimation() else drawOnce()
                    }
                    refreshWeatherIfNeeded()
                }
            } else {
                surfaceVisible = false
                visibilityJob?.cancel()
                stopAnimations()
                return
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && project.changeOnDoubleTap && surfaceVisible && isVisible) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTapMs in 50L..320L) {
                    lastTapMs = 0L
                    scope.launchSafe {
                        loadWallpaperAndMask(advance = true, checkInterval = false)
                        ensureBakedMinuteScene(force = true)
                        if (surfaceVisible && isVisible) drawOnce()
                    }
                } else {
                    lastTapMs = now
                }
            }
            super.onTouchEvent(event)
        }

        override fun onDestroy() {
            surfaceVisible = false
            screenOffJob?.cancel()
            visibilityJob?.cancel()
            stopAnimations()
            cachedScene = null
            renderer.release()
            runCatching { unregisterReceiver(receiver) }
            scope.cancel()
            super.onDestroy()
        }

        private fun isDeviceLocked(): Boolean {
            val km = getSystemService(KeyguardManager::class.java)
            return km?.isKeyguardLocked ?: true
        }

        private fun hasOnlyLockLayers(): Boolean =
            project.layers.flatten().all { (node, _) ->
                node.hidden || node.type == LayerType.DEPTH || node.cond == Cond.LOCK_ONLY
            }

        private fun releaseLockscreenBuffersIfHomeOnly() {
            if (hasOnlyLockLayers()) {
                cachedScene = null
                cachedSceneKey = ""
                renderer.clearShaderCachesForHome()
            }
        }

        private suspend fun refreshWeatherIfNeeded() {
            val loc = EnvProvider.lastLocation(this@PrismWallpaperService)
            val lat = loc?.first ?: project.lat
            val lon = loc?.second ?: project.lon
            EnvProvider.refreshWeather(this@PrismWallpaperService, lat, lon, project.city)
        }

        private suspend fun loadWallpaperAndMask(advance: Boolean, checkInterval: Boolean = false) {
            val uri = WallpaperCarousel.currentUri(
                ctx = this@PrismWallpaperService,
                project = project,
                advance = advance,
                checkInterval = checkInterval
            )
            if (uri != null && (uri != currentUri || bgBitmap == null)) {
                currentUri = uri
                val baked = withContext(Dispatchers.IO) {
                    WallpaperBaker.loadBakedForUri(this@PrismWallpaperService, uri)
                }
                if (baked != null) {
                    bgBitmap = baked.sharp
                    frostedBitmap = baked.frosted
                    val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                    depthMask = if (project.depth && project.isDepthEnabledFor(imgKey)) baked.depthMask else null
                } else {
                    val raw = WallpaperCarousel.loadBitmap(this@PrismWallpaperService, uri, surfaceW, surfaceH)
                    bgBitmap = raw
                    frostedBitmap = null
                    depthMask = if (raw != null) {
                        SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, raw, project)
                    } else null
                }
                cachedSceneKey = ""
            } else if (uri != null) {
                val imgKey = WallpaperCarousel.imageKey(this@PrismWallpaperService, uri)
                if (!project.depth || !project.isDepthEnabledFor(imgKey)) {
                    depthMask = null
                } else if (depthMask == null) {
                    val baked = withContext(Dispatchers.IO) {
                        WallpaperBaker.loadBakedForUri(this@PrismWallpaperService, uri)
                    }
                    depthMask = baked?.depthMask ?: bgBitmap?.let { bmp ->
                        SubjectDepthEngine.getOrCreateMaskIfEnabled(this@PrismWallpaperService, uri, bmp, project)
                    }
                }
            }
        }

        private suspend fun ensureBakedMinuteScene(force: Boolean) {
            if (!isDeviceLocked() && hasOnlyLockLayers()) {
                releaseLockscreenBuffersIfHomeOnly()
                return
            }
            val now = LocalDateTime.now()
            val env = EnvProvider.env.value.copy(locked = true)
            val key = "${currentUri}|${surfaceW}x${surfaceH}|${now.year}-${now.dayOfYear}-${now.hour}:${now.minute}|${env.battery}|${env.charging}|${env.temp}|${env.weatherCode}|${project.hashCode()}|${depthMask != null}"
            if (!force && key == cachedSceneKey && cachedScene != null) return

            val sharp = bgBitmap
            val frosted = frostedBitmap
            val mask = depthMask
            val proj = project
            val w = surfaceW
            val h = surfaceH
            val scene = withContext(Dispatchers.Default) {
                renderer.bakeMinuteScene(
                    w = w,
                    h = h,
                    project = proj,
                    sharpBg = sharp,
                    frostedBg = frosted,
                    depthMask = mask,
                    env = env,
                    now = now
                )
            }
            cachedScene = scene
            cachedSceneKey = key
        }

        private fun startWakeAnimation() {
            stopAnimations()
            wakeProgress = 0f
            drawOnce()
            activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500L
                interpolator = wakeInterpolator
                addUpdateListener { anim ->
                    if (!surfaceVisible || !isVisible) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    wakeProgress = anim.animatedValue as Float
                    drawOnce()
                }
                start()
            }
        }

        private fun startUnlockFade() {
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
                    if (!surfaceVisible || !isVisible) {
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
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val scene = cachedScene ?: return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                renderer.drawWakeAnimationFrame(
                    canvas = canvas,
                    w = canvas.width,
                    h = canvas.height,
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
            if (!surfaceVisible || !isVisible) return
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                if (!isDeviceLocked() && lockAlpha <= 0.01f && hasOnlyLockLayers()) {
                    renderer.drawUnlockedHomeFrame(
                        canvas = canvas,
                        w = canvas.width,
                        h = canvas.height,
                        project = project,
                        sharpBg = bgBitmap
                    )
                    return
                }
                val scene = cachedScene
                if (scene != null) {
                    renderer.drawWakeAnimationFrame(
                        canvas = canvas,
                        w = canvas.width,
                        h = canvas.height,
                        project = project,
                        sharpBg = bgBitmap,
                        scene = scene,
                        depthMask = depthMask,
                        wakeProgress = wakeProgress,
                        lockAlpha = lockAlpha
                    )
                } else {
                    renderer.render(
                        canvas = canvas,
                        w = canvas.width,
                        h = canvas.height,
                        project = project,
                        bg = bgBitmap,
                        depthMask = depthMask,
                        env = EnvProvider.env.value,
                        now = LocalDateTime.now(),
                        wakeProgress = wakeProgress,
                        lockAlpha = lockAlpha
                    )
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
