package com.example.prismlock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tactile press scale micro-interaction driven exclusively inside graphicsLayer (zero layout passes).
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val ctx = LocalContext.current
    val reducedMotion = remember(ctx) { PrismMotion.isReducedMotionEnabled(ctx) }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = if (reducedMotion) PrismMotion.fastTween() else PrismMotion.TactileFloatSpring,
        label = "PrismPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Zero-allocation spotlight sheen drawn via drawWithCache for glass cards and pills.
 */
fun Modifier.prismSpotlight(
    color: Color = Color(0x18FFFFFF),
    centerFraction: Offset = Offset(0.5f, 0f)
): Modifier = this.drawWithCache {
    val radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.85f
    val center = Offset(size.width * centerFraction.x, size.height * centerFraction.y)
    val brush = Brush.radialGradient(
        colors = listOf(color, Color.Transparent),
        center = center,
        radius = radius
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

/**
 * Blur-reveal text transition for View titles (Etapa 6), automatically degrading to fast fade under reduced motion.
 */
@Composable
fun BlurRevealText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PrismColors.PureWhite,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    maxLines: Int = 1
) {
    val ctx = LocalContext.current
    val reducedMotion = remember(ctx) { PrismMotion.isReducedMotionEnabled(ctx) }
    val dur = if (reducedMotion) PrismMotion.Duration.FAST else PrismMotion.Duration.BASE
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(tween(dur, easing = PrismMotion.Easing.Decelerate)) togetherWith
                fadeOut(tween(PrismMotion.Duration.FAST, easing = PrismMotion.Easing.Accelerate))
        },
        modifier = modifier,
        label = "PrismBlurRevealText"
    ) { label ->
        val blurPx by transition.animateFloat(
            transitionSpec = { tween(dur, easing = PrismMotion.Easing.Decelerate) },
            label = "PrismBlurRevealRadius"
        ) { state ->
            if (reducedMotion) 0f else when (state) {
                EnterExitState.PreEnter -> PrismMotion.Blur.ENTER
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> PrismMotion.Blur.EXIT
            }
        }
        val blurMod = if (!reducedMotion && blurPx > 0.25f) {
            Modifier.blur(blurPx.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier
        }
        Text(
            text = label,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = blurMod
        )
    }
}

/**
 * Staggered list/grid item enter animation (35ms per item, capped at 5) strictly inside graphicsLayer.
 */
fun Modifier.staggeredEnter(index: Int, stepMs: Int = 35): Modifier = composed {
    val ctx = LocalContext.current
    val reducedMotion = remember(ctx) { PrismMotion.isPowerSaveOrReducedMotion(ctx) }
    val prog = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            prog.snapTo(1f)
            return@LaunchedEffect
        }
        val d = (index.coerceIn(0, 5) * stepMs).toLong()
        if (d > 0L) delay(d)
        prog.animateTo(1f, tween(PrismMotion.Duration.BASE, easing = PrismMotion.Easing.Decelerate))
    }
    this.graphicsLayer {
        val v = prog.value
        alpha = v
        translationY = (1f - v) * 12.dp.toPx()
        val s = PrismMotion.Scale.ENTER + (1f - PrismMotion.Scale.ENTER) * v
        scaleX = s
        scaleY = s
    }
}
