package com.example.prismlock.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.prismlock.data.Env
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Project
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private data class HubCachedCard(
    val signature: String,
    val preview: Bitmap,
    val previewImage: ImageBitmap,
    val ambientImage: ImageBitmap
)

private object HubPreviewCache {
    val map = ConcurrentHashMap<String, HubCachedCard>()
}

private fun bakeTinyAmbientBitmap(src: Bitmap, outW: Int = 72, outH: Int = 156): Bitmap {
    val tiny = Bitmap.createScaledBitmap(src, outW, outH, true).copy(Bitmap.Config.ARGB_8888, true)
    val pix = IntArray(outW * outH)
    val tmp = IntArray(outW * outH)
    tiny.getPixels(pix, 0, outW, 0, 0, outW, outH)
    val r = 12
    val div = r * 2 + 1
    repeat(2) {
        for (y in 0 until outH) {
            val rowOff = y * outW
            var rSum = 0; var gSum = 0; var bSum = 0
            for (dx in -r..r) {
                val c = pix[rowOff + dx.coerceIn(0, outW - 1)]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (x in 0 until outW) {
                tmp[rowOff + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val addC = pix[rowOff + (x + r + 1).coerceIn(0, outW - 1)]
                val remC = pix[rowOff + (x - r).coerceIn(0, outW - 1)]
                rSum += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                gSum += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                bSum += (addC and 0xFF) - (remC and 0xFF)
            }
        }
        for (x in 0 until outW) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (dy in -r..r) {
                val c = tmp[dy.coerceIn(0, outH - 1) * outW + x]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (y in 0 until outH) {
                pix[y * outW + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val addC = tmp[(y + r + 1).coerceIn(0, outH - 1) * outW + x]
                val remC = tmp[(y - r).coerceIn(0, outH - 1) * outW + x]
                rSum += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                gSum += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                bSum += (addC and 0xFF) - (remC and 0xFF)
            }
        }
    }
    tiny.setPixels(pix, 0, outW, 0, 0, outW, outH)
    tiny.setHasAlpha(false)
    return tiny
}

private val HUB_MOCK_TIME: LocalDateTime = LocalDateTime.of(2026, 11, 17, 18, 45, 0)
private val HUB_MOCK_ENV: Env = Env(
    battery = 85,
    charging = false,
    batteryTempC = 27.0f,
    temp = 24,
    weatherCode = 1,
    isDay = true,
    city = "São Paulo",
    alarm = "07:00",
    steps = 4280,
    locked = true
)

@Composable
fun ThemeHubScreen(
    themes: List<Project>,
    activeThemeId: String,
    focusThemeId: String? = null,
    onFocusConsumed: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") env: Env,
    onCreateTheme: (String) -> Unit,
    onImportTheme: () -> Unit,
    onImportFolderTheme: () -> Unit = {},
    onEditTheme: (Project) -> Unit,
    onApplyTheme: (Project) -> Unit,
    onDeleteTheme: (Project) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val pagerState = rememberPagerState(pageCount = { themes.size.coerceAtLeast(1) })
    val cardRenderer = remember { LiquidGlassRenderer() }
    val renderMutex = remember { Mutex() }
    val previewImages = remember { mutableStateMapOf<String, ImageBitmap>() }
    val ambientImages = remember { mutableStateMapOf<String, ImageBitmap>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }

    DisposableEffect(cardRenderer) {
        onDispose { cardRenderer.release() }
    }

    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    val settledIndex by remember(themes.size) {
        derivedStateOf { pagerState.settledPage.coerceIn(0, (themes.size - 1).coerceAtLeast(0)) }
    }
    val currentTheme = themes.getOrNull(settledIndex)
    val dragThresholdPx = with(density) { 86.dp.toPx() }
    val (baseW, baseH) = remember { WallpaperBaker.resolveDisplaySize(ctx) }
    val cardRenderW = 420
    val cardRenderH = (cardRenderW * (baseH.toFloat() / baseW.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)

    LaunchedEffect(focusThemeId, themes) {
        if (focusThemeId != null) {
            val idx = themes.indexOfFirst { it.id == focusThemeId }
            if (idx >= 0) {
                pagerState.animateScrollToPage(idx)
                onFocusConsumed()
            }
        }
    }

    // Static mock preview cards (18:45 + mock telemetry) cached across navigations
    LaunchedEffect(themes) {
        val activeIds = themes.mapTo(HashSet()) { it.id }
        previewImages.keys.toList().filter { it !in activeIds }.forEach { previewImages.remove(it) }
        ambientImages.keys.toList().filter { it !in activeIds }.forEach { ambientImages.remove(it) }
        HubPreviewCache.map.keys.toList().filter { it !in activeIds }.forEach { HubPreviewCache.map.remove(it) }

        val pending = ArrayList<Pair<Int, Project>>()
        themes.forEachIndexed { idx, theme ->
            val sig = "${theme.id}|${theme.hashCode()}|${cardRenderW}x${cardRenderH}"
            val cached = HubPreviewCache.map[theme.id]
            if (cached != null && cached.signature == sig && !cached.preview.isRecycled) {
                previewImages[theme.id] = cached.previewImage
                ambientImages[theme.id] = cached.ambientImage
            } else {
                pending.add(idx to theme)
            }
        }

        val anchor = pagerState.settledPage.coerceIn(0, (themes.size - 1).coerceAtLeast(0))
        pending.sortBy { (idx, _) -> (idx - anchor).absoluteValue }

        for ((_, theme) in pending) {
            val sig = "${theme.id}|${theme.hashCode()}|${cardRenderW}x${cardRenderH}"
            val entry = withContext(Dispatchers.Default) {
                renderMutex.withLock {
                    cardRenderer.clearShaderCachesForHome()
                    val uri = WallpaperCarousel.currentUri(ctx, theme, advance = false)
                    val rawBg = WallpaperCarousel.loadBitmap(ctx, uri, cardRenderW, cardRenderH)
                    val rawMask = SubjectDepthEngine.getOrCreateMaskIfEnabled(ctx, uri, rawBg, theme)
                    val bgBmp = if (rawBg != null && rawMask != null && theme.hasDepthPostProcessing()) {
                        val base = WallpaperBaker.centerCropOpaque(rawBg, cardRenderW, cardRenderH)
                        SubjectDepthEngine.composeProcessedWallpaper(base, rawMask, theme) { s, r ->
                            cardRenderer.bakeBlurredBitmap(s, cardRenderW, cardRenderH, r)
                        }
                    } else rawBg
                    val depthMask = if (rawMask != null && theme.hasDepthPostProcessing()) {
                        SubjectDepthEngine.applySubjectColorPopMask(rawMask, theme)
                    } else rawMask
                    val bmp = cardRenderer.renderPreviewBitmap(
                        w = cardRenderW,
                        h = cardRenderH,
                        project = theme,
                        bg = bgBmp,
                        depthMask = depthMask,
                        env = HUB_MOCK_ENV,
                        now = HUB_MOCK_TIME
                    )
                    val tinyAmb = bakeTinyAmbientBitmap(bgBmp ?: bmp)
                    HubCachedCard(
                        signature = sig,
                        preview = bmp,
                        previewImage = bmp.asImageBitmap(),
                        ambientImage = tinyAmb.asImageBitmap()
                    )
                }
            }
            HubPreviewCache.map[theme.id] = entry
            previewImages[theme.id] = entry.previewImage
            ambientImages[theme.id] = entry.ambientImage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrismColors.PureBlack)
    ) {
        // Pre-blurred low-res ambient wallpaper glow (zero real-time GPU blur during swipe)
        val ambientImg = currentTheme?.let { ambientImages[it.id] ?: previewImages[it.id] }
        Crossfade(
            targetState = ambientImg,
            animationSpec = tween(260),
            label = "AmbientGlow"
        ) { targetImg ->
            if (targetImg != null) {
                Image(
                    bitmap = targetImg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x9E000000))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Modern Circular Glass TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val brandFamily = remember {
                        FontManager.init(ctx)
                        FontManager.composeFontFamily(FontManager.SAMSUNG_SYSTEM_FONT_ID, 600)
                    }
                    Text(
                        text = "PrismLock",
                        color = PrismColors.PureWhite,
                        fontFamily = brandFamily,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        text = "${themes.size} ${if (themes.size == 1) "tema salvo" else "temas salvos"}",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(CircleShape)
                                .background(Color(0x66222228))
                                .clickable { showImportMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                MIcons.Rounded.FileDownload,
                                contentDescription = "Importar",
                                tint = PrismColors.PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = Color(0xEC141418)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.FileDownload, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Importar Arquivo (.prism / .zip)", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showImportMenu = false
                                    onImportTheme()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.FolderOpen, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Importar Pasta", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showImportMenu = false
                                    onImportFolderTheme()
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(PrismColors.PureWhite)
                            .clickable {
                                newThemeName = ""
                                showCreateDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.Add,
                            contentDescription = "Nova Tela",
                            tint = PrismColors.PureBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (currentTheme != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(CircleShape)
                        .background(Color(0x5C1E1E24))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (currentTheme.id == activeThemeId) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PrismColors.PureWhite)
                        )
                    }
                    Text(
                        text = currentTheme.title,
                        color = PrismColors.PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.weight(0.18f))

            // Central Spotlight HorizontalPager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.73f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { index -> themes.getOrNull(index)?.id ?: index },
                    contentPadding = PaddingValues(horizontal = 52.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val theme = themes.getOrNull(page) ?: return@HorizontalPager
                    val isCenter by remember(page) {
                        derivedStateOf { pagerState.settledPage == page }
                    }

                    val dragY = remember(theme.id) { Animatable(0f) }
                    var triggeredHaptic by remember(theme.id) { mutableStateOf(false) }

                    val cardShape = RoundedCornerShape(32.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                    .absoluteValue
                                    .coerceIn(0f, 1f)
                                val scale = lerp(1.0f, 0.86f, pageOffset)
                                scaleX = scale
                                scaleY = scale
                                alpha = lerp(1.0f, 0.55f, pageOffset)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.runtime.key(theme.id) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 19.5f)
                                    .offset { IntOffset(0, dragY.value.roundToInt()) }
                                    .graphicsLayer {
                                        shadowElevation = 16.dp.toPx()
                                        shape = cardShape
                                        clip = true
                                    }
                                    .background(PrismColors.PureBlack)
                                .pointerInput(theme.id, isCenter) {
                                    if (!isCenter) return@pointerInput
                                    detectVerticalDragGestures(
                                        onDragStart = { triggeredHaptic = false },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            scope.launch {
                                                val next = (dragY.value + dragAmount).coerceIn(-dragThresholdPx * 1.45f, dragThresholdPx * 1.45f)
                                                dragY.snapTo(next)
                                                if (!triggeredHaptic && next.absoluteValue >= dragThresholdPx) {
                                                    triggeredHaptic = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                } else if (triggeredHaptic && next.absoluteValue < dragThresholdPx * 0.7f) {
                                                    triggeredHaptic = false
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val cur = dragY.value
                                            scope.launch {
                                                when {
                                                    cur <= -dragThresholdPx -> {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        dragY.animateTo(0f, PrismMotion.FloatSpring)
                                                        onEditTheme(theme)
                                                    }
                                                    cur >= dragThresholdPx -> {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        dragY.animateTo(0f, PrismMotion.FloatSpring)
                                                        onApplyTheme(theme)
                                                    }
                                                    else -> dragY.animateTo(0f, PrismMotion.FloatSpring)
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch { dragY.animateTo(0f, PrismMotion.FloatSpring) }
                                        }
                                    )
                                }
                                .clickable(enabled = isCenter) { onEditTheme(theme) }
                        ) {
                            val img = previewImages[theme.id]
                            if (img != null) {
                                Image(
                                    bitmap = img,
                                    contentDescription = theme.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(PrismColors.CarbonBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        MIcons.Rounded.Wallpaper,
                                        contentDescription = null,
                                        tint = PrismColors.TrackZinc,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            // Vertical gesture action pill (borderless frosted glass)
                            val curDrag = dragY.value
                            val dragLabel = when {
                                curDrag <= -dragThresholdPx * 0.6f -> "Solte ↑ para Editar"
                                curDrag >= dragThresholdPx * 0.6f -> "Solte ↓ para Aplicar"
                                isCenter -> "↑ Editar  ·  ↓ Aplicar"
                                else -> null
                            }
                            if (dragLabel != null) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 14.dp)
                                        .clip(CircleShape)
                                        .background(if (curDrag.absoluteValue >= dragThresholdPx) Color(0xCC1E1E24) else Color(0x72000000))
                                        .padding(horizontal = 13.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (curDrag <= -dragThresholdPx * 0.6f) {
                                        Icon(MIcons.Rounded.KeyboardArrowUp, null, tint = PrismColors.PureWhite, modifier = Modifier.size(14.dp))
                                    } else if (curDrag >= dragThresholdPx * 0.6f) {
                                        Icon(MIcons.Rounded.KeyboardArrowDown, null, tint = PrismColors.PureWhite, modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = dragLabel,
                                        color = PrismColors.PureWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(0.45f))

            // Modern Borderless Frosted Floating Pill Dock with Circular Icons
            if (currentTheme != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .shadow(20.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color(0x6E1E1E24))
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Editar (circular frosted glass)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable { onEditTheme(currentTheme) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.Edit,
                            contentDescription = "Editar",
                            tint = PrismColors.PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Aplicar (signature bright PureWhite circular CTA inside glass dock)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(PrismColors.PureWhite)
                            .clickable { onApplyTheme(currentTheme) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.Check,
                            contentDescription = "Aplicar na Tela de Bloqueio",
                            tint = PrismColors.PureBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Excluir (circular frosted glass)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x18FFFFFF))
                            .clickable { pendingDelete = currentTheme },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.DeleteOutline,
                            contentDescription = "Excluir tema",
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Frosted monochrome confirmation dialog for Delete
        pendingDelete?.let { target ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            )
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = PrismColors.CarbonBlack,
                titleContentColor = PrismColors.PureWhite,
                textContentColor = PrismColors.MutedZinc,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Excluir \"${target.title}\"?", fontWeight = FontWeight.SemiBold) },
                text = { Text("Esta configuração salva será removida do carrossel de temas.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteTheme(target)
                            pendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Excluir", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancelar", color = PrismColors.MutedZinc)
                    }
                }
            )
        }

        if (showCreateDialog) {
            FocusedInputDialog(
                title = "Nova Tela de Bloqueio",
                initialValue = newThemeName,
                placeholder = "Default Screen",
                exampleHint = "Ex: Minimal OLED (deixe vazio e confirme para \"Default Screen\")",
                confirmLabel = "Criar Tela",
                dismissLabel = "Cancelar",
                onDismiss = { showCreateDialog = false },
                onConfirm = { raw ->
                    val finalTitle = raw.trim().ifBlank { "Default Screen" }
                    showCreateDialog = false
                    onCreateTheme(finalTitle)
                }
            )
        }
    }
}
