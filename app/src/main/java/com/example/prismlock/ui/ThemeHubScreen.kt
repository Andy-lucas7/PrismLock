package com.example.prismlock.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
        for (theme in themes) {
            val cacheKey = "${theme.id}|${theme.hashCode()}|${env.battery}|${env.temp}|${nowMinute}"
            if (previewBitmaps[theme.id] != null && ambientWallpapers.containsKey(cacheKey)) continue
            val (rendered, bg) = withContext(Dispatchers.Default) {
                renderMutex.withLock {
                    cardRenderer.clearShaderCachesForHome()
                    val uri = WallpaperCarousel.currentUri(ctx, theme, advance = false)
                    val bgBmp = WallpaperCarousel.loadBitmap(ctx, uri, cardRenderW, cardRenderH)
                    val depthMask = SubjectDepthEngine.getOrCreateMaskIfEnabled(ctx, uri, bgBmp, theme)
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
        if (ambientBmp != null && !ambientBmp.isRecycled) {
            Image(
                bitmap = ambientBmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .alpha(0.24f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xD0000000),
                            Color(0x55000000),
                            Color(0xEB000000)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimalist monochrome frosted TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PrismLock",
                        color = PrismColors.PureWhite,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "${themes.size} ${if (themes.size == 1) "tema salvo" else "temas salvos"}",
                        color = PrismColors.MutedZinc,
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .frostedChrome(shape = RoundedCornerShape(20.dp), elevation = 6.dp)
                            .clickable { onImportTheme() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(MIcons.Rounded.FileUpload, contentDescription = "Importar", tint = PrismColors.PureWhite, modifier = Modifier.size(16.dp))
                            Text("Importar", color = PrismColors.PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .frostedChrome(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 8.dp,
                                borderColor = PrismColors.GlassBorderStrong
                            )
                            .clickable {
                                newThemeName = ""
                                showCreateDialog = true
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(MIcons.Rounded.Add, contentDescription = "Nova Tela", tint = PrismColors.PureWhite, modifier = Modifier.size(16.dp))
                            Text("Nova Tela", color = PrismColors.PureWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(0.35f))

            // Central Spotlight HorizontalPager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.66f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    key = { index -> themes.getOrNull(index)?.id ?: index },
                    contentPadding = PaddingValues(horizontal = 64.dp),
                    pageSpacing = 16.dp,
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

                    val cardShape = RoundedCornerShape(28.dp)
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
                                        shadowElevation = lerp(24.dp.toPx(), 4.dp.toPx(), pageOffset)
                                        shape = cardShape
                                        clip = true
                                    }
                                    .background(PrismColors.PureBlack)
                                    .border(
                                        width = if (isCenter) 1.2.dp else 1.dp,
                                        color = if (isCenter) PrismColors.GlassBorderStrong else PrismColors.GlassBorder,
                                        shape = cardShape
                                    )
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

                            // Subtle top gradient for theme badge legibility
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x99000000), Color.Transparent)
                                        )
                                    )
                            )

                            // Theme label + Active indicator
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .frostedChrome(shape = RoundedCornerShape(16.dp), elevation = 4.dp)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (theme.id == activeThemeId) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrismColors.PureWhite)
                                    )
                                }
                                Text(
                                    text = theme.title,
                                    color = PrismColors.PureWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            // Vertical gesture action pill
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
                                        .frostedChrome(
                                            shape = RoundedCornerShape(18.dp),
                                            dense = curDrag.absoluteValue >= dragThresholdPx,
                                            borderColor = if (curDrag.absoluteValue >= dragThresholdPx) PrismColors.PureWhite else PrismColors.GlassBorder
                                        )
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
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

            Spacer(Modifier.weight(0.65f))

            // Refined Frosted Floating Dock (#121215 @ 0.85 alpha + blur feel, 1.dp White 15% border)
            if (currentTheme != null) {
                val dockShape = RoundedCornerShape(24.dp)
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .shadow(20.dp, dockShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(dockShape)
                        .background(Color(0xD9121215))
                        .border(1.dp, Color(0x26FFFFFF), dockShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Editar
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x1CFFFFFF))
                            .clickable { onEditTheme(currentTheme) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(MIcons.Rounded.Edit, contentDescription = "Editar", tint = PrismColors.PureWhite, modifier = Modifier.size(17.dp))
                            Text("Editar", color = PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Aplicar (Pure White Primary CTA)
                    Button(
                        onClick = { onApplyTheme(currentTheme) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Icon(MIcons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Aplicar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Excluir
                    IconButton(
                        onClick = { pendingDelete = currentTheme },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                    ) {
                        Icon(
                            MIcons.Rounded.DeleteOutline,
                            contentDescription = "Excluir tema",
                            tint = PrismColors.MutedZinc,
                            modifier = Modifier.size(18.dp)
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
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA6000000))
            )
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = Color(0xEB111115),
                titleContentColor = PrismColors.PureWhite,
                textContentColor = PrismColors.MutedZinc,
                shape = RoundedCornerShape(26.dp),
                title = {
                    Text(
                        text = "Nova Tela de Bloqueio",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Dê um nome para identificar esta tela ou pule para usar \"Default Screen\".",
                            color = PrismColors.MutedZinc,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = newThemeName,
                            onValueChange = { newThemeName = it },
                            singleLine = true,
                            placeholder = { Text("Default Screen", color = PrismColors.MutedZinc) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrismColors.PureWhite,
                                unfocusedBorderColor = PrismColors.GlassBorderStrong,
                                focusedTextColor = PrismColors.PureWhite,
                                unfocusedTextColor = PrismColors.PureWhite,
                                cursorColor = PrismColors.PureWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalTitle = newThemeName.trim().ifBlank { "Default Screen" }
                            showCreateDialog = false
                            onCreateTheme(finalTitle)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Criar Tela", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateDialog = false
                            onCreateTheme("Default Screen")
                        }
                    ) {
                        Text("Pular", color = PrismColors.MutedZinc)
                    }
                }
            )
        }
    }
}
