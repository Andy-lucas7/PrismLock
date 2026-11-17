package com.example.prismlock.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prismlock.data.Env
import com.example.prismlock.engine.HitBox
import com.example.prismlock.engine.KustomFormulaEngine
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.find
import com.example.prismlock.model.flatten
import com.example.prismlock.model.parentOf
import com.example.prismlock.wallpaper.VideoWallpaperRenderer
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PreparedPreview(
    val project: Project,
    val bg: Bitmap?,
    val depth: Bitmap?,
    val env: Env,
    val version: Int
)

private fun hitTestLayer(
    hits: List<HitBox>,
    layers: List<LayerNode>,
    x: Float,
    y: Float,
    minTouchPx: Float
): HitBox? {
    val pt = floatArrayOf(x, y)
    for (box in hits.asReversed()) {
        val node = layers.find(box.id) ?: continue
        if (node.locked || node.type == LayerType.DEPTH) continue
        pt[0] = x
        pt[1] = y
        box.invMatrix.mapPoints(pt)
        val padX = ((minTouchPx - box.localW) * 0.5f).coerceAtLeast(12f)
        val padY = ((minTouchPx - box.localH) * 0.5f).coerceAtLeast(12f)
        if (pt[0] in -padX..(box.localW + padX) && pt[1] in -padY..(box.localH + padY)) return box
    }
    return null
}

@OptIn(FlowPreview::class)
@Composable
fun InteractiveLockPreview(
    renderer: LiquidGlassRenderer,
    project: Project,
    selectedId: String?,
    bgBitmap: Bitmap?,
    depthBitmap: Bitmap?,
    activePreviewUri: String? = null,
    env: Env,
    onSelectLayer: (String?) -> Unit,
    onUpdateLayer: (String, (LayerNode) -> LayerNode) -> Unit,
    onFlushSave: () -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val minTouchPx = with(density) { 48.dp.toPx() }
    val marginPx = with(density) { 24.dp.toPx() }
    val snapRadiusPx = with(density) { 12.dp.toPx() }

    val currentProject by rememberUpdatedState(project)
    val currentSelectedId by rememberUpdatedState(selectedId)
    val currentBg by rememberUpdatedState(bgBitmap)
    val currentDepth by rememberUpdatedState(depthBitmap)
    val currentEnv by rememberUpdatedState(env)

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var preparedVersion by remember(project.id) { mutableStateOf(0) }
    var videoFrameTick by remember { mutableIntStateOf(0) }
    var secondsTick by remember { mutableIntStateOf(0) }
    val hasSecondsLayer = remember(project.layers) {
        project.layers.flatten().any { (n, _) -> !n.hidden && n.type == LayerType.TEXT && KustomFormulaEngine.hasSecondsToken(n.text) }
    }
    LaunchedEffect(hasSecondsLayer) {
        if (!hasSecondsLayer) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000L)
            secondsTick++
        }
    }
    val hitBoxes = remember(project.id) { mutableListOf<HitBox>() }
    var selectedHitRect by remember(project.id) { mutableStateOf<RectF?>(null) }
    var snapLineX by remember(project.id) { mutableStateOf<Float?>(null) }
    var snapLineY by remember(project.id) { mutableStateOf<Float?>(null) }
    var activeDraggingLayerId by remember(project.id) { mutableStateOf<String?>(null) }
    val currentActiveDragId by rememberUpdatedState(activeDraggingLayerId)

    val isVideoPreview by produceState(initialValue = false, activePreviewUri) {
        value = activePreviewUri != null && withContext(Dispatchers.IO) {
            WallpaperCarousel.isVideoUri(ctx, activePreviewUri)
        }
    }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var previewVideoRenderer by remember { mutableStateOf<VideoWallpaperRenderer?>(null) }

    DisposableEffect(isVideoPreview) {
        if (!isVideoPreview) {
            previewVideoRenderer?.release()
            previewVideoRenderer = null
            renderer.clearLiveVideoMode()
            onDispose { }
        } else {
            val vr = VideoWallpaperRenderer(ctx) {
                mainHandler.post { videoFrameTick++ }
            }
            previewVideoRenderer = vr
            onDispose {
                vr.release()
                if (previewVideoRenderer === vr) previewVideoRenderer = null
                renderer.clearLiveVideoMode()
            }
        }
    }

    DisposableEffect(lifecycleOwner, previewVideoRenderer, isVideoPreview) {
        val observer = LifecycleEventObserver { _, event ->
            val vr = previewVideoRenderer ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (isVideoPreview) vr.play()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> vr.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val primaryBlurPx = WallpaperBaker.resolvePrimaryBlurPx(project, 1f)
    LaunchedEffect(isVideoPreview, activePreviewUri, canvasSize, previewVideoRenderer, primaryBlurPx) {
        val vr = previewVideoRenderer ?: return@LaunchedEffect
        val uri = activePreviewUri ?: return@LaunchedEffect
        if (isVideoPreview && canvasSize.width > 0 && canvasSize.height > 0) {
            val (bw, bh) = WallpaperBaker.baseSizeForPhysical(canvasSize.width, canvasSize.height)
            val blurPx = WallpaperBaker.resolvePrimaryBlurPx(currentProject, bw / 1080f)
            vr.setSource(uri, bw, bh, blurPx)
            vr.play()
        }
    }

    DisposableEffect(project.id) {
        onDispose { onDragStateChange(false) }
    }

    LaunchedEffect(renderer, project.id) {
        renderer.clearShaderCachesForHome()
        renderer.setAllowStaleLensOnMiss(true)
        snapshotFlow {
            PreparedPreview(currentProject, currentBg, currentDepth, currentEnv, 0) to canvasSize
        }.debounce(80).conflate().collect { (frame, sz) ->
            if (sz.width > 0 && sz.height > 0) {
                val (bw, bh) = WallpaperBaker.baseSizeForPhysical(sz.width, sz.height)
                withContext(Dispatchers.Default) {
                    renderer.prepareFrameAssets(
                        w = bw,
                        h = bh,
                        project = frame.project,
                        bg = frame.bg,
                        env = frame.env.copy(locked = true),
                        now = LocalDateTime.now()
                    )
                }
                preparedVersion++
            }
        }
    }

    val selected = project.layers.find(selectedId)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .onSizeChanged { canvasSize = it }
                .pointerInput(minTouchPx, marginPx, snapRadiusPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val (bwInt, bhInt) = WallpaperBaker.baseSizeForPhysical(size.width, size.height)
                        val cw = bwInt.toFloat().coerceAtLeast(1f)
                        val ch = bhInt.toFloat().coerceAtLeast(1f)
                        val sxScale = vw / cw
                        val syScale = vh / ch

                        val startPos = down.position
                        val picked = hitTestLayer(
                            hitBoxes,
                            currentProject.layers,
                            startPos.x / sxScale,
                            startPos.y / syScale,
                            minTouchPx / sxScale
                        )
                        val rootId = picked?.id?.let { pid ->
                            generateSequence(pid) { cur -> currentProject.layers.parentOf(cur)?.id }.last()
                        }
                        val rootNode = rootId?.let { currentProject.layers.find(it) }?.takeIf { !it.locked }
                        val lockedDragId = if (rootNode != null) rootId else null
                        activeDraggingLayerId = lockedDragId

                        val rootBox = lockedDragId?.let { id -> hitBoxes.firstOrNull { it.id == id }?.rect }
                        var rawX = rootNode?.x ?: 0f
                        var rawY = rootNode?.y ?: 0f
                        val boxW = rootBox?.width() ?: 0f
                        val boxH = rootBox?.height() ?: 0f
                        val boxOffsetX = if (rootBox != null && rootNode != null) rootBox.left - rootNode.x * cw else 0f
                        val boxOffsetY = if (rootBox != null && rootNode != null) rootBox.top - rootNode.y * ch else 0f
                        val baseMarginX = marginPx / sxScale
                        val baseMarginY = marginPx / syScale
                        val baseSnapX = snapRadiusPx / sxScale
                        val baseSnapY = snapRadiusPx / syScale
                        var movedPastSlop = false
                        var totalPan = Offset.Zero
                        var lastSnapX: Float? = null
                        var lastSnapY: Float? = null

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                if (!movedPastSlop) {
                                    movedPastSlop = true
                                    onDragStateChange(true)
                                } else {
                                    onDragStateChange(true)
                                }
                                val targetId = currentActiveDragId ?: currentSelectedId?.let { sid ->
                                    generateSequence(sid) { cur -> currentProject.layers.parentOf(cur)?.id }.last()
                                }
                                val targetNode = targetId?.let { currentProject.layers.find(it) }
                                if (targetNode != null && !targetNode.locked) {
                                    val zoom = event.calculateZoom()
                                    val rot = event.calculateRotation()
                                    if (zoom != 1f || rot != 0f) {
                                        onUpdateLayer(targetNode.id) {
                                            val rawRot = (it.rotation + rot + 180f).mod(360f) - 180f
                                            it.copy(
                                                scale = (it.scale * zoom).coerceIn(0.25f, 4f),
                                                rotation = rawRot
                                            )
                                        }
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressedCount == 1) {
                                val pan = event.calculatePan()
                                totalPan += pan
                                if (!movedPastSlop && totalPan.getDistance() > touchSlop) {
                                    movedPastSlop = true
                                    if (picked != null) onSelectLayer(picked.id)
                                    if (currentActiveDragId != null) onDragStateChange(true)
                                }
                                val dragId = currentActiveDragId
                                if (movedPastSlop && dragId != null && pan != Offset.Zero) {
                                    rawX = (rawX + (pan.x / sxScale) / cw).coerceIn(0f, 1f)
                                    rawY = (rawY + (pan.y / syScale) / ch).coerceIn(0f, 1f)

                                    val rawLeft = rawX * cw + boxOffsetX
                                    val rawCenterX = rawLeft + boxW * 0.5f
                                    val rawRight = rawLeft + boxW
                                    var nx = rawX
                                    val sX: Float? = when {
                                        abs(rawCenterX - cw * 0.5f) <= baseSnapX -> {
                                            nx = ((cw * 0.5f - boxW * 0.5f - boxOffsetX) / cw).coerceIn(0f, 1f)
                                            cw * 0.5f
                                        }
                                        abs(rawLeft - baseMarginX) <= baseSnapX -> {
                                            nx = ((baseMarginX - boxOffsetX) / cw).coerceIn(0f, 1f)
                                            baseMarginX
                                        }
                                        abs(rawRight - (cw - baseMarginX)) <= baseSnapX -> {
                                            nx = ((cw - baseMarginX - boxW - boxOffsetX) / cw).coerceIn(0f, 1f)
                                            cw - baseMarginX
                                        }
                                        else -> null
                                    }

                                    val rawTop = rawY * ch + boxOffsetY
                                    val rawCenterY = rawTop + boxH * 0.5f
                                    val rawBottom = rawTop + boxH
                                    var ny = rawY
                                    val sY: Float? = when {
                                        abs(rawCenterY - ch * 0.5f) <= baseSnapY -> {
                                            ny = ((ch * 0.5f - boxH * 0.5f - boxOffsetY) / ch).coerceIn(0f, 1f)
                                            ch * 0.5f
                                        }
                                        abs(rawTop - baseMarginY) <= baseSnapY -> {
                                            ny = ((baseMarginY - boxOffsetY) / ch).coerceIn(0f, 1f)
                                            baseMarginY
                                        }
                                        abs(rawBottom - (ch - baseMarginY)) <= baseSnapY -> {
                                            ny = ((ch - baseMarginY - boxH - boxOffsetY) / ch).coerceIn(0f, 1f)
                                            ch - baseMarginY
                                        }
                                        else -> null
                                    }

                                    if ((sX != null && sX != lastSnapX) || (sY != null && sY != lastSnapY)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    lastSnapX = sX
                                    lastSnapY = sY
                                    snapLineX = sX?.let { it * sxScale }
                                    snapLineY = sY?.let { it * syScale }
                                    onUpdateLayer(dragId) { it.copy(x = nx, y = ny) }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        onDragStateChange(false)
                        if (!movedPastSlop) {
                            onSelectLayer(picked?.id)
                        } else {
                            onFlushSave()
                        }
                        activeDraggingLayerId = null
                        snapLineX = null
                        snapLineY = null
                    }
                }
        ) {
            val viewW = size.width.roundToInt().coerceAtLeast(1)
            val viewH = size.height.roundToInt().coerceAtLeast(1)
            val (baseW, baseH) = WallpaperBaker.baseSizeForPhysical(viewW, viewH)
            val sxScale = size.width / baseW.toFloat()
            val syScale = size.height / baseH.toFloat()

            drawRect(Color.Black, blendMode = BlendMode.Clear)
            drawRect(Color.Black)

            val ver = preparedVersion
            val vTick = videoFrameTick
            val sTick = secondsTick
            if (ver >= 0 && vTick >= 0 && sTick >= 0) {
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawColor(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.CLEAR)
                    c.nativeCanvas.drawColor(android.graphics.Color.BLACK)
                    val drewLiveVideo = if (isVideoPreview) {
                        previewVideoRenderer?.withCurrentFrame { frame ->
                            renderer.drawLiveVideoFrame(
                                canvas = c.nativeCanvas,
                                viewW = viewW,
                                viewH = viewH,
                                baseW = baseW,
                                baseH = baseH,
                                project = project,
                                sharpBmp = frame.sharpBitmap,
                                sharpShader = frame.sharpShader,
                                frostedBmp = frame.frostedBitmap,
                                frostedShader = frame.frostedShader,
                                env = env.copy(locked = true),
                                now = LocalDateTime.now(),
                                hits = hitBoxes
                            )
                            true
                        } ?: false
                    } else false

                    if (!drewLiveVideo) {
                        renderer.renderScaledBaseFrame(
                            canvas = c.nativeCanvas,
                            viewW = viewW,
                            viewH = viewH,
                            baseW = baseW,
                            baseH = baseH,
                            project = project,
                            bg = bgBitmap,
                            depthMask = depthBitmap,
                            env = env.copy(locked = true),
                            now = LocalDateTime.now(),
                            hits = hitBoxes
                        )
                    }
                }
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            snapLineX?.let { sx ->
                drawLine(Color(0xD9FFFFFF), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 2f, pathEffect = dash)
            }
            snapLineY?.let { sy ->
                drawLine(Color(0xD9FFFFFF), Offset(0f, sy), Offset(size.width, sy), strokeWidth = 2f, pathEffect = dash)
            }
            val selBox = hitBoxes.firstOrNull { it.id == currentSelectedId }
            val mappedRect = selBox?.rect?.let { r ->
                RectF(r.left * sxScale, r.top * syScale, r.right * sxScale, r.bottom * syScale)
            }
            if (selectedHitRect != mappedRect) {
                selectedHitRect = mappedRect
            }
            mappedRect?.let { r ->
                drawRect(
                    color = PrismColors.PureWhite,
                    topLeft = Offset(r.left - 6f, r.top - 6f),
                    size = Size(r.width() + 12f, r.height() + 12f),
                    style = Stroke(width = 2.5f)
                )
            }
        }

        if (selected != null && selected.type == LayerType.TEXT && !selected.locked && selectedHitRect != null) {
            val rect = selectedHitRect!!
            val handleX = rect.centerX().roundToInt()
            val handleY = (rect.bottom + 14f).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(handleX - 38, handleY - 12) }
                    .size(width = 76.dp, height = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrismColors.PureWhite)
                    .pointerInput(selected.id) {
                        detectDragGestures(
                            onDragStart = { onDragStateChange(true) },
                            onDragEnd = { onDragStateChange(false); onFlushSave() },
                            onDragCancel = { onDragStateChange(false); onFlushSave() }
                        ) { change, dragAmount ->
                            change.consume()
                            onUpdateLayer(selected.id) { it.copy(stretch = (it.stretch + dragAmount.y / 140f).coerceIn(1f, 3f)) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("${"%.2f".format(selected.stretch)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}
