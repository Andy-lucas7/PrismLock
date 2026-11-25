package com.example.prismlock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.prismlock.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ViewExpandMode { NONE, EDIT, APPLY }

/**
 * Single progress p in [0, 1] controller driving View -> Studio expansion,
 * Apply expand+fade+blur coverage, and reversible Predictive Back collapse.
 */
@Stable
class ViewExpandController(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    val applyFade = Animatable(0f)
    val handoffAlpha = Animatable(1f)

    var mode by mutableStateOf(ViewExpandMode.NONE)
        private set
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    var originRect by mutableStateOf<Rect?>(null)
        private set
    var originCornerDp by mutableFloatStateOf(32f)
        private set
    var activeProject by mutableStateOf<Project?>(null)
        private set

    private var animJob: Job? = null

    val isVisible: Boolean
        get() = mode != ViewExpandMode.NONE && snapshot != null && originRect != null

    val shouldPrewarmStudio: Boolean
        get() = mode == ViewExpandMode.EDIT && progress.value > 0.08f

    fun launchEdit(
        project: Project,
        snap: ImageBitmap?,
        rect: Rect?,
        cornerDp: Float,
        reducedMotion: Boolean,
        onPrewarm: () -> Unit,
        onComplete: () -> Unit
    ) {
        if (snap == null || rect == null || rect.width < 10f || rect.height < 10f || reducedMotion) {
            onPrewarm()
            onComplete()
            return
        }
        animJob?.cancel()
        activeProject = project
        snapshot = snap
        originRect = rect
        originCornerDp = cornerDp
        mode = ViewExpandMode.EDIT
        animJob = scope.launch {
            applyFade.snapTo(0f)
            handoffAlpha.snapTo(1f)
            progress.snapTo(0f)
            onPrewarm()
            var completed = false
            progress.animateTo(1f, PrismMotion.ExpandFloatSpring) {
                if (!completed && value >= 0.72f) {
                    completed = true
                    onComplete()
                }
            }
            if (!completed) onComplete()
            handoffAlpha.animateTo(0f, tween(PrismMotion.Duration.FAST, easing = PrismMotion.Easing.Decelerate))
            mode = ViewExpandMode.NONE
        }
    }

    fun launchApply(
        project: Project,
        snap: ImageBitmap?,
        rect: Rect?,
        cornerDp: Float,
        reducedMotion: Boolean,
        onDispatchApply: () -> Unit
    ) {
        if (snap == null || rect == null || rect.width < 10f || rect.height < 10f || reducedMotion) {
            onDispatchApply()
            return
        }
        animJob?.cancel()
        activeProject = project
        snapshot = snap
        originRect = rect
        originCornerDp = cornerDp
        mode = ViewExpandMode.APPLY
        animJob = scope.launch {
            applyFade.snapTo(0f)
            handoffAlpha.snapTo(1f)
            progress.snapTo(0f)
            progress.animateTo(1f, PrismMotion.ExpandFloatSpring)
            onDispatchApply()
            applyFade.animateTo(1f, tween(PrismMotion.Duration.SLOW, easing = PrismMotion.Easing.Decelerate))
            mode = ViewExpandMode.NONE
            progress.snapTo(0f)
            applyFade.snapTo(0f)
        }
    }

    fun updatePredictiveBack(backProgress: Float) {
        val snap = snapshot ?: return
        val rect = originRect ?: return
        if (rect.width < 10f || rect.height < 10f) return
        if (mode != ViewExpandMode.EDIT) {
            mode = ViewExpandMode.EDIT
        }
        animJob?.cancel()
        scope.launch {
            handoffAlpha.snapTo((backProgress * 3.5f).coerceIn(0f, 1f))
            progress.snapTo((1f - backProgress * 0.48f).coerceIn(0.15f, 1f))
        }
        // Keep compiler happy about snap usage
        if (snap.width <= 0) return
    }

    fun cancelPredictiveBack(reducedMotion: Boolean) {
        if (mode != ViewExpandMode.EDIT) return
        animJob?.cancel()
        animJob = scope.launch {
            val spec = if (reducedMotion) PrismMotion.fastTween<Float>() else PrismMotion.ExpandFloatSpring
            progress.animateTo(1f, spec)
            handoffAlpha.animateTo(0f, tween(PrismMotion.Duration.INSTANT))
            mode = ViewExpandMode.NONE
        }
    }

    fun collapseToHub(
        reducedMotion: Boolean,
        onReturned: () -> Unit
    ) {
        val snap = snapshot
        val rect = originRect
        if (snap == null || rect == null || rect.width < 10f || rect.height < 10f || reducedMotion) {
            mode = ViewExpandMode.NONE
            onReturned()
            return
        }
        animJob?.cancel()
        mode = ViewExpandMode.EDIT
        animJob = scope.launch {
            if (progress.value < 0.05f) progress.snapTo(1f)
            handoffAlpha.snapTo(1f)
            onReturned()
            progress.animateTo(0f, PrismMotion.ExpandFloatSpring)
            mode = ViewExpandMode.NONE
        }
    }
}

/**
 * Unclipped full-screen overlay interpolating the View snapshot strictly inside graphicsLayer.
 */
@Composable
fun ViewExpandOverlay(
    controller: ViewExpandController,
    reducedMotion: Boolean
) {
    if (!controller.isVisible) return
    val snap = controller.snapshot ?: return
    val rect = controller.originRect ?: return
    val p = controller.progress.value.coerceIn(0f, 1f)
    val fade = controller.applyFade.value.coerceIn(0f, 1f)
    val handoff = controller.handoffAlpha.value.coerceIn(0f, 1f)
    val finalAlpha = ((1f - fade) * handoff).coerceIn(0f, 1f)
    if (finalAlpha <= 0.002f) return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = finalAlpha }
    ) {
        val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val startScaleX = (rect.width / fullW).coerceIn(0.05f, 1f)
        val startScaleY = (rect.height / fullH).coerceIn(0.05f, 1f)
        val curScaleX = lerp(startScaleX, 1f, p)
        val curScaleY = lerp(startScaleY, 1f, p)
        val curTx = lerp(rect.left, 0f, p)
        val curTy = lerp(rect.top, 0f, p)
        val curCorner = lerp(controller.originCornerDp, 0f, p).coerceAtLeast(0f).dp

        // Background scrim tracks single progress p
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (p * (1f - fade)).coerceIn(0f, 1f) }
                .background(PrismColors.PureBlack)
        )

        val blurDp = if (!reducedMotion && controller.mode == ViewExpandMode.APPLY && fade > 0.01f) {
            (fade * PrismMotion.Blur.ENTER).dp
        } else 0.dp
        val blurMod = if (blurDp > 0.25.dp) {
            Modifier.blur(blurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = curTx
                    translationY = curTy
                    scaleX = curScaleX
                    scaleY = curScaleY
                    shape = RoundedCornerShape(curCorner)
                    clip = curCorner > 0.5.dp
                    shadowElevation = lerp(18.dp.toPx(), 0f, p)
                }
                .then(blurMod)
                .background(PrismColors.PureBlack)
        ) {
            Image(
                bitmap = snap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
