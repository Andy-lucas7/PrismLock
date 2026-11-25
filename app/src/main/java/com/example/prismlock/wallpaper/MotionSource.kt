package com.example.prismlock.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Settings
import com.example.prismlock.model.MotionScene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sole authorized SensorManager consumer (Etapa 8):
 * Uses TYPE_GAME_ROTATION_VECTOR (fallback TYPE_ROTATION_VECTOR) on a dedicated HandlerThread,
 * strictly active only while visible, parallax enabled, and neither battery-saver nor reduced-motion active.
 */
class MotionSource(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val sensorManager = appCtx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isSupported: Boolean = sensor != null

    private val _offset = MutableStateFlow(0f to 0f)
    val offset: StateFlow<Pair<Float, Float>> = _offset.asStateFlow()

    private val lock = Any()
    private val filter = MotionFilter()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var registered = false
    @Volatile private var sceneConfig = MotionScene()

    private val rotMatrix = FloatArray(9)
    private val prevMatrix = FloatArray(9)
    private val angleChange = FloatArray(3)
    private var hasPrevMatrix = false
    private var accumX = 0f
    private var accumY = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            if (!hasPrevMatrix) {
                System.arraycopy(rotMatrix, 0, prevMatrix, 0, 9)
                hasPrevMatrix = true
                accumX = 0f
                accumY = 0f
                filter.reset()
                _offset.value = 0f to 0f
                return
            }
            SensorManager.getAngleChange(angleChange, rotMatrix, prevMatrix)
            System.arraycopy(rotMatrix, 0, prevMatrix, 0, 9)
            // angleChange: [0]=azimuth(Z), [1]=pitch(X), [2]=roll(Y)
            accumX += angleChange[2]
            accumY += angleChange[1]
            val cfg = sceneConfig
            val next = filter.update(
                angleXRad = accumX,
                angleYRad = accumY,
                timestampNs = event.timestamp,
                axes = cfg.axes,
                invert = cfg.invert
            )
            _offset.value = next
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun isSystemMotionBlocked(): Boolean {
        val reduced = runCatching {
            Settings.Global.getFloat(appCtx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (reduced) return true
        return runCatching {
            appCtx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }

    fun updateConfig(config: MotionScene) {
        sceneConfig = config
        if (!config.enabled || isSystemMotionBlocked()) {
            stop()
        }
    }

    fun start(config: MotionScene): Boolean = synchronized(lock) {
        sceneConfig = config
        if (!isSupported || !config.enabled || isSystemMotionBlocked()) {
            stopLocked()
            return false
        }
        if (registered) return true
        val sm = sensorManager ?: return false
        val s = sensor ?: return false
        hasPrevMatrix = false
        accumX = 0f
        accumY = 0f
        filter.reset()
        val t = HandlerThread("PrismMotion").apply { start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        val ok = sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME, 0, h)
        if (!ok) {
            runCatching { t.quitSafely() }
            thread = null
            handler = null
            return false
        }
        registered = true
        true
    }

    fun recenter() = synchronized(lock) {
        hasPrevMatrix = false
        accumX = 0f
        accumY = 0f
        filter.reset()
        _offset.value = 0f to 0f
    }

    fun stop() = synchronized(lock) {
        stopLocked()
    }

    private fun stopLocked() {
        if (registered) {
            runCatching { sensorManager?.unregisterListener(listener) }
            registered = false
        }
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
        hasPrevMatrix = false
        accumX = 0f
        accumY = 0f
        filter.reset()
        _offset.value = 0f to 0f
    }
}
