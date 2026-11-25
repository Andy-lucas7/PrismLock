package com.example.prismlock.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Single source of truth for motion tokens mirrored from docs/motion.tokens.json.
 */
object PrismMotion {
    object Duration {
        const val INSTANT = 90
        const val FAST = 160
        const val BASE = 280
        const val SLOW = 420
        const val HERO = 650
        const val INTRO = 5200
    }

    object Easing {
        val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        val Accelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    }

    object SpringParams {
        const val TACTILE_STIFFNESS = 420f
        const val TACTILE_DAMPING = 34f
        const val TACTILE_MASS = 1f

        const val CAROUSEL_STIFFNESS = 260f
        const val CAROUSEL_DAMPING = 30f
        const val CAROUSEL_MASS = 1f

        const val EXPAND_STIFFNESS = 190f
        const val EXPAND_DAMPING = 26f
        const val EXPAND_MASS = 1f
    }

    object Blur {
        const val ENTER = 12f
        const val EXIT = 8f
    }

    object Scale {
        const val ENTER = 0.96f
        const val CAROUSEL_SIDE = 0.84f
    }

    object Gesture {
        const val COMMIT_DISTANCE = 0.22f
        const val COMMIT_VELOCITY = 900f
        const val RUBBER_BAND = 0.35f
    }

    fun dampingRatio(stiffness: Float, damping: Float, mass: Float = 1f): Float {
        val denom = 2f * sqrt((stiffness * mass).coerceAtLeast(1e-6f))
        return (damping / denom).coerceIn(0.1f, 2f)
    }

    val TactileFloatSpring: SpringSpec<Float> = spring(
        dampingRatio = dampingRatio(SpringParams.TACTILE_STIFFNESS, SpringParams.TACTILE_DAMPING, SpringParams.TACTILE_MASS),
        stiffness = SpringParams.TACTILE_STIFFNESS
    )

    val TactileIntOffsetSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = dampingRatio(SpringParams.TACTILE_STIFFNESS, SpringParams.TACTILE_DAMPING, SpringParams.TACTILE_MASS),
        stiffness = SpringParams.TACTILE_STIFFNESS
    )

    val CarouselFloatSpring: SpringSpec<Float> = spring(
        dampingRatio = dampingRatio(SpringParams.CAROUSEL_STIFFNESS, SpringParams.CAROUSEL_DAMPING, SpringParams.CAROUSEL_MASS),
        stiffness = SpringParams.CAROUSEL_STIFFNESS
    )

    val ExpandFloatSpring: SpringSpec<Float> = spring(
        dampingRatio = dampingRatio(SpringParams.EXPAND_STIFFNESS, SpringParams.EXPAND_DAMPING, SpringParams.EXPAND_MASS),
        stiffness = SpringParams.EXPAND_STIFFNESS
    )

    // Preserved aliases for existing callers
    val FloatSpring: SpringSpec<Float> = TactileFloatSpring
    val IntOffsetSpring: SpringSpec<IntOffset> = TactileIntOffsetSpring

    fun <T> fastTween(): TweenSpec<T> = tween(durationMillis = Duration.FAST, easing = Easing.Emphasized)
    fun <T> baseTween(): TweenSpec<T> = tween(durationMillis = Duration.BASE, easing = Easing.Emphasized)
    fun <T> slowTween(): TweenSpec<T> = tween(durationMillis = Duration.SLOW, easing = Easing.Decelerate)

    fun rubberBand(distance: Float, maxExtent: Float, factor: Float = Gesture.RUBBER_BAND): Float {
        if (distance == 0f || maxExtent <= 0f || factor <= 0f) return 0f
        val limit = maxExtent * factor
        val absD = abs(distance)
        val damped = (limit * absD) / (limit + absD)
        return if (distance < 0f) -damped else damped
    }

    fun isReducedMotionEnabled(ctx: Context): Boolean = runCatching {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    fun isPowerSaveOrReducedMotion(ctx: Context): Boolean {
        if (isReducedMotionEnabled(ctx)) return true
        return runCatching {
            ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
}
