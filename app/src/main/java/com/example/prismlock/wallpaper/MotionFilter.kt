package com.example.prismlock.wallpaper

import com.example.prismlock.model.MotionAxes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Pure math parallax motion filter (zero Android dependencies):
 * - Out-of-order timestamp rejection & >100ms gap reset
 * - Auto-recentering high-pass reference (tau = 1.5s)
 * - One Euro Filter (minCutoff = 1.0Hz, beta = 0.02)
 * - 0.6 deg dead zone
 * - 0.25 normalized per-frame slew rate limit
 * - Smooth tanh saturation at 12 deg into (-1, 1)
 */
class MotionFilter(
    private val minCutoffHz: Float = 1.0f,
    private val beta: Float = 0.02f,
    private val dCutoffHz: Float = 1.0f,
    private val recenterTauSec: Float = 1.5f,
    private val deadZoneDeg: Float = 0.6f,
    private val maxAngleDeg: Float = 12.0f,
    private val maxStepPerFrame: Float = 0.25f,
    private val maxGapSec: Float = 0.100f
) {
    private val deadZoneRad = (deadZoneDeg * PI / 180.0).toFloat()
    private val maxAngleRad = (maxAngleDeg * PI / 180.0).toFloat()

    private var initialized = false
    private var lastTimestampNs = 0L
    private var refX = 0f
    private var refY = 0f

    private var filtX = 0f
    private var filtY = 0f
    private var dFiltX = 0f
    private var dFiltY = 0f

    private var outX = 0f
    private var outY = 0f

    fun reset() {
        initialized = false
        lastTimestampNs = 0L
        refX = 0f
        refY = 0f
        filtX = 0f
        filtY = 0f
        dFiltX = 0f
        dFiltY = 0f
        outX = 0f
        outY = 0f
    }

    private fun smoothingAlpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz.coerceAtLeast(1e-3f))
        return 1f / (1f + tau / dt.coerceAtLeast(1e-5f))
    }

    fun update(
        angleXRad: Float,
        angleYRad: Float,
        timestampNs: Long,
        axes: MotionAxes = MotionAxes.BOTH,
        invert: Boolean = false
    ): Pair<Float, Float> {
        if (!angleXRad.isFinite() || !angleYRad.isFinite()) return outX to outY

        if (!initialized) {
            initialized = true
            lastTimestampNs = timestampNs
            refX = angleXRad
            refY = angleYRad
            filtX = 0f
            filtY = 0f
            dFiltX = 0f
            dFiltY = 0f
            outX = 0f
            outY = 0f
            return 0f to 0f
        }

        val dtNs = timestampNs - lastTimestampNs
        if (dtNs <= 0L) {
            return outX to outY
        }
        val dt = dtNs / 1_000_000_000f
        lastTimestampNs = timestampNs

        if (dt > maxGapSec) {
            // Re-anchor smoothly after a pause without jumping
            refX = angleXRad
            refY = angleYRad
            filtX = 0f
            filtY = 0f
            dFiltX = 0f
            dFiltY = 0f
            outX = 0f
            outY = 0f
            return 0f to 0f
        }

        // High-pass auto-recenter reference (any comfortable holding angle becomes neutral)
        val recenterAlpha = (1.0 - exp((-dt / recenterTauSec).toDouble())).toFloat().coerceIn(0f, 1f)
        refX += (angleXRad - refX) * recenterAlpha
        refY += (angleYRad - refY) * recenterAlpha

        val rawRelX = angleXRad - refX
        val rawRelY = angleYRad - refY

        // One Euro Filter on X
        val dx = (rawRelX - filtX) / dt
        val aD = smoothingAlpha(dCutoffHz, dt)
        dFiltX += (dx - dFiltX) * aD
        val cutoffX = minCutoffHz + beta * abs(dFiltX)
        val aX = smoothingAlpha(cutoffX, dt)
        filtX += (rawRelX - filtX) * aX

        // One Euro Filter on Y
        val dy = (rawRelY - filtY) / dt
        dFiltY += (dy - dFiltY) * aD
        val cutoffY = minCutoffHz + beta * abs(dFiltY)
        val aY = smoothingAlpha(cutoffY, dt)
        filtY += (rawRelY - filtY) * aY

        // Dead zone
        val dzX = if (abs(filtX) < deadZoneRad) 0f else filtX - kotlin.math.sign(filtX) * deadZoneRad
        val dzY = if (abs(filtY) < deadZoneRad) 0f else filtY - kotlin.math.sign(filtY) * deadZoneRad

        // Soft tanh saturation into (-1, 1)
        val sign = if (invert) -1f else 1f
        var targetX = tanh((dzX / maxAngleRad).toDouble()).toFloat() * sign
        var targetY = tanh((dzY / maxAngleRad).toDouble()).toFloat() * sign

        when (axes) {
            MotionAxes.HORIZONTAL -> targetY = 0f
            MotionAxes.VERTICAL -> targetX = 0f
            MotionAxes.BOTH -> Unit
        }

        // Per-frame velocity clamp to swallow isolated sensor spikes
        outX += (targetX - outX).coerceIn(-maxStepPerFrame, maxStepPerFrame)
        outY += (targetY - outY).coerceIn(-maxStepPerFrame, maxStepPerFrame)
        return outX to outY
    }
}
