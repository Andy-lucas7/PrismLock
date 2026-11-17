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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun ThemeHubScreen(
    themes: List<Project>,
    activeThemeId: String,
    focusThemeId: String? = null,
    onFocusConsumed: () -> Unit = {},
    env: Env,
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
    val previewBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    val ambientWallpapers = remember { mutableStateMapOf<String, Bitmap?>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }

    DisposableEffect(cardRenderer) {
        onDispose { cardRenderer.release() }
    }

    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    val safeIndex = pagerState.currentPage.coerceIn(0, (themes.size - 1).coerceAtLeast(0))
    val currentTheme = themes.getOrNull(safeIndex)
    val dragThresholdPx = with(density) { 86.dp.toPx() }
    val (baseW, baseH) = remember { WallpaperBaker.resolveDisplaySize(ctx) }
    val cardRenderW = 540
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

    // Render full preview bitmaps offscreen on Dispatchers.Default for visible themes
    val nowMinute = LocalDateTime.now().withSecond(0).withNano(0)
    LaunchedEffect(themes, env, nowMinute) {
        val activeIds = themes.mapTo(HashSet()) { it.id }
        previewBitmaps.keys.toList().filter { it !in activeIds }.forEach { previewBitmaps.remove(it) }
        ambientWallpapers.keys.toList().filter { '|' !in it && it !in activeIds }.forEach { ambientWallpapers.remove(it) }
        for (theme in themes) {
            val cacheKey = "${theme.id}|${theme.hashCode()}|${env.battery}|${env.temp}|${nowMinute}"
            if (previewBitmaps[theme.id] != null && ambientWallpapers.containsKey(cacheKey)) continue
            val (rendered, bg) = withContext(Dispatchers.Default) {
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
                        env = env,
                        now = nowMinute
                    )
                    bmp to bgBmp
                }
            }
            previewBitmaps[theme.id] = rendered
            ambientWallpapers[theme.id] = bg
            ambientWallpapers[cacheKey] = bg
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrismColors.PureBlack)
    ) {
        // Real ambient blurred wallpaper glow behind the pure black hub chrome
        val ambientBmp = currentTheme?.let { ambientWallpapers[it.id] ?: previewBitmaps[it.id] }
        Crossfade(
            targetState = ambientBmp,
            animationSpec = tween(320),
            label = "AmbientGlow"
        ) { targetBmp ->
            if (targetBmp != null && !targetBmp.isRecycled) {
                Image(
                    bitmap = targetBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(72.dp)
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
                    key = { index -> themes.getOrNull(index)?.id ?: index },
                    contentPadding = PaddingValues(horizontal = 52.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val theme = themes.getOrNull(page) ?: return@HorizontalPager
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                    val scale = lerp(1.0f, 0.84f, pageOffset)
                    val cardAlpha = lerp(1.0f, 0.55f, pageOffset)
                    val isCenter = page == pagerState.currentPage

                    val dragY = remember(theme.id) { Animatable(0f) }
                    var triggeredHaptic by remember(theme.id) { mutableStateOf(false) }

                    val cardShape = RoundedCornerShape(32.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = cardAlpha
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
                                        shadowElevation = lerp(28.dp.toPx(), 6.dp.toPx(), pageOffset)
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
                            val bmp = previewBitmaps[theme.id]
                            if (bmp != null && !bmp.isRecycled) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
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
