package com.example.prismlock.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import com.example.prismlock.data.Env
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Project
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import com.example.prismlock.widget.PrismWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin

private data class HubCachedCard(
    val signature: String,
    val preview: Bitmap,
    val previewImage: ImageBitmap,
    val ambientImage: ImageBitmap
)

private object HubPreviewCache {
    private val cache = object : android.util.LruCache<String, HubCachedCard>(6 * 1024) {
        override fun sizeOf(key: String, value: HubCachedCard): Int {
            val kb = value.preview.allocationByteCount / 1024
            return kb.coerceAtLeast(1)
        }
    }

    fun get(id: String): HubCachedCard? = cache.get(id)
    fun put(id: String, card: HubCachedCard) { cache.put(id, card) }
    fun retainOnly(activeIds: Set<String>) {
        cache.snapshot().keys.filter { it !in activeIds }.forEach { cache.remove(it) }
    }
    fun clear() { cache.evictAll() }
}

fun clearHubPreviewCache() {
    HubPreviewCache.clear()
}

private fun bakeTinyAmbientBitmap(src: Bitmap, outW: Int = 48, outH: Int = 104): Bitmap {
    val scaled = Bitmap.createScaledBitmap(src, outW, outH, true)
    val tiny = if (scaled.config == Bitmap.Config.ARGB_8888 && scaled.isMutable) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, true).also {
            if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        }
    }
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
    val compact = tiny.copy(Bitmap.Config.RGB_565, false)
    if (compact != null) {
        tiny.recycle()
        return compact
    }
    return tiny
}

private fun hubMockTime(): LocalDateTime =
    LocalDateTime.of(java.time.LocalDate.now(), java.time.LocalTime.of(18, 45, 0))
private val HUB_MOCK_ENV: Env = Env(
    battery = 85,
    charging = false,
    batteryTempC = 27.0f,
    temp = 24,
    weatherCode = 1,
    isDay = true,
    city = "São Paulo",
    alarm = "07:00",
    steps = 0,
    locked = true
)

private enum class HubMode { SCREENS, WIDGETS }

@Composable
fun ThemeHubScreen(
    themes: List<Project>,
    activeThemeId: String,
    widgets: List<Project> = emptyList(),
    activeWidgetId: String = "",
    focusThemeId: String? = null,
    onFocusConsumed: () -> Unit = {},
    expandController: ViewExpandController? = null,
    deferPreviewRender: Boolean = false,
    @Suppress("UNUSED_PARAMETER") env: Env,
    onCreateTheme: (String) -> Unit,
    onCreateWidget: (String) -> Unit = {},
    onImportTheme: () -> Unit,
    onImportFolderTheme: () -> Unit = {},
    onEditTheme: (Project) -> Unit,
    onEditWidget: (Project) -> Unit = {},
    onApplyTheme: (Project) -> Unit,
    onApplyWidget: (Project) -> Unit = {},
    onDeleteTheme: (Project) -> Unit,
    onDeleteWidget: (Project) -> Unit = {},
    onReplayHero: () -> Unit = {},
    showBatteryPrompt: Boolean = false,
    onDismissBatteryPrompt: () -> Unit = {},
    onRequestIgnoreBatteryOptimizations: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reducedMotion = remember(ctx) { PrismMotion.isReducedMotionEnabled(ctx) }

    var mode by remember { mutableStateOf(HubMode.SCREENS) }
    val isGridMode by ProjectStore.hubGridModeFlow(ctx).collectAsState(initial = false)
    val gridState = rememberLazyGridState()
    val cardBounds = remember { HashMap<String, Rect>() }
    val modeTransition = remember { Animatable(0f) }
    LaunchedEffect(mode, reducedMotion) {
        val target = if (mode == HubMode.WIDGETS) 1f else 0f
        if (kotlin.math.abs(modeTransition.value - target) > 0.001f) {
            modeTransition.animateTo(
                targetValue = target,
                animationSpec = if (reducedMotion) {
                    PrismMotion.fastTween()
                } else {
                    tween(durationMillis = PrismMotion.Duration.SLOW, easing = PrismMotion.Easing.Decelerate)
                }
            )
        }
    }
    val mp = modeTransition.value
    val morphPulse = sin(mp * Math.PI).toFloat().coerceIn(0f, 1f)

    val activeItems = if (mode == HubMode.SCREENS) themes else widgets
    val activeItemId = if (mode == HubMode.SCREENS) activeThemeId else activeWidgetId

    val screensPagerState = rememberPagerState(pageCount = { themes.size.coerceAtLeast(1) })
    val widgetsPagerState = rememberPagerState(pageCount = { widgets.size.coerceAtLeast(1) })
    val pagerState = if (mode == HubMode.SCREENS) screensPagerState else widgetsPagerState

    val cardRenderer = remember { LiquidGlassRenderer().apply { attachContext(ctx) } }
    val renderMutex = remember { Mutex() }
    val previewImages = remember { mutableStateMapOf<String, ImageBitmap>() }
    val ambientImages = remember { mutableStateMapOf<String, ImageBitmap>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }

    DisposableEffect(cardRenderer) {
        onDispose {
            cardRenderer.release()
            previewImages.clear()
            ambientImages.clear()
        }
    }

    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    val settledIndex by remember(activeItems.size, mode) {
        derivedStateOf { pagerState.settledPage.coerceIn(0, (activeItems.size - 1).coerceAtLeast(0)) }
    }
    val currentTheme = activeItems.getOrNull(settledIndex)
    val dragThresholdPx = with(density) { 86.dp.toPx() }
    val (baseW, baseH) = remember { WallpaperBaker.resolveDisplaySize(ctx) }
    val cardRenderW = 320
    val cardRenderH = (cardRenderW * (baseH.toFloat() / baseW.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)

    LaunchedEffect(focusThemeId, themes, widgets) {
        if (focusThemeId != null) {
            val themeIdx = themes.indexOfFirst { it.id == focusThemeId }
            if (themeIdx >= 0) {
                mode = HubMode.SCREENS
                screensPagerState.animateScrollToPage(themeIdx)
                onFocusConsumed()
            } else {
                val widgetIdx = widgets.indexOfFirst { it.id == focusThemeId }
                if (widgetIdx >= 0) {
                    mode = HubMode.WIDGETS
                    widgetsPagerState.animateScrollToPage(widgetIdx)
                    onFocusConsumed()
                }
            }
        }
    }

    val visibleGridRange by remember(isGridMode, activeItems.size) {
        derivedStateOf {
            if (!isGridMode) null
            else {
                val info = gridState.layoutInfo.visibleItemsInfo
                if (info.isEmpty()) null else info.first().index..info.last().index
            }
        }
    }

    fun triggerEdit(item: Project) {
        val cornerDp = if (isGridMode) 22f else if (item.isWidget) 28f else 32f
        val open: () -> Unit = { if (item.isWidget) onEditWidget(item) else onEditTheme(item) }
        if (expandController != null) {
            expandController.launchEdit(
                project = item,
                snap = previewImages[item.id],
                rect = cardBounds[item.id],
                cornerDp = cornerDp,
                reducedMotion = reducedMotion,
                onPrewarm = {},
                onComplete = open
            )
        } else {
            open()
        }
    }

    fun triggerApply(item: Project) {
        val cornerDp = if (isGridMode) 22f else if (item.isWidget) 28f else 32f
        val apply: () -> Unit = { if (item.isWidget) onApplyWidget(item) else onApplyTheme(item) }
        if (expandController != null) {
            expandController.launchApply(
                project = item,
                snap = previewImages[item.id],
                rect = cardBounds[item.id],
                cornerDp = cornerDp,
                reducedMotion = reducedMotion,
                onDispatchApply = apply
            )
        } else {
            apply()
        }
    }

    // Static mock preview cards (18:45 + mock telemetry) bounded to visible window, deferred while Hero animates
    LaunchedEffect(themes, widgets, mode, settledIndex, isGridMode, visibleGridRange, deferPreviewRender) {
        val allItems = themes + widgets
        val activeIds = allItems.mapTo(HashSet()) { it.id }
        previewImages.keys.toList().filter { it !in activeIds }.forEach { previewImages.remove(it) }
        ambientImages.keys.toList().filter { it !in activeIds }.forEach { ambientImages.remove(it) }
        HubPreviewCache.retainOnly(activeIds)

        val windowList = ArrayList<Project>(12)
        val anchor = settledIndex.coerceIn(0, (activeItems.size - 1).coerceAtLeast(0))
        if (isGridMode) {
            val gridRange = visibleGridRange?.let { r ->
                (r.first - 2).coerceAtLeast(0)..(r.last + 2).coerceAtMost(activeItems.lastIndex.coerceAtLeast(0))
            } ?: (0..(activeItems.lastIndex.coerceAtMost(7).coerceAtLeast(0)))
            for (i in gridRange) {
                activeItems.getOrNull(i)?.let { windowList.add(it) }
            }
        } else {
            val windowRange = ((anchor - 2).coerceAtLeast(0))..((anchor + 2).coerceAtMost(activeItems.lastIndex.coerceAtLeast(0)))
            for (i in windowRange) {
                activeItems.getOrNull(i)?.let { windowList.add(it) }
            }
        }
        val otherList = if (mode == HubMode.SCREENS) widgets else themes
        val otherState = if (mode == HubMode.SCREENS) widgetsPagerState else screensPagerState
        otherList.getOrNull(otherState.settledPage.coerceIn(0, (otherList.size - 1).coerceAtLeast(0)))?.let {
            if (windowList.none { existing -> existing.id == it.id }) windowList.add(it)
        }

        val windowIds = windowList.mapTo(HashSet()) { it.id }
        currentTheme?.id?.let { windowIds.add(it) }
        previewImages.keys.toList().filter { it !in windowIds }.forEach { previewImages.remove(it) }
        ambientImages.keys.toList().filter { it !in windowIds }.forEach { ambientImages.remove(it) }

        val pending = ArrayList<Pair<Int, Project>>()
        windowList.forEachIndexed { idx, theme ->
            val wW = if (theme.isWidget) 420 else cardRenderW
            val wH = if (theme.isWidget) (wW / theme.widgetAspect.coerceIn(0.6f, 4.5f)).roundToInt().coerceAtLeast(120) else cardRenderH
            val sig = "${theme.id}|${theme.hashCode()}|${wW}x${wH}"
            val cached = HubPreviewCache.get(theme.id)
            if (cached != null && cached.signature == sig && !cached.preview.isRecycled) {
                previewImages[theme.id] = cached.previewImage
                ambientImages[theme.id] = cached.ambientImage
            } else {
                pending.add(idx to theme)
            }
        }

        if (deferPreviewRender || pending.isEmpty()) return@LaunchedEffect

        pending.sortBy { (idx, item) ->
            val modeBonus = if ((item.isWidget && mode == HubMode.WIDGETS) || (!item.isWidget && mode == HubMode.SCREENS)) 0 else 100
            val itemIdx = activeItems.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: idx
            modeBonus + (itemIdx - anchor).absoluteValue
        }

        try {
            for ((_, theme) in pending) {
                val wW = if (theme.isWidget) 420 else cardRenderW
                val wH = if (theme.isWidget) (wW / theme.widgetAspect.coerceIn(0.6f, 4.5f)).roundToInt().coerceAtLeast(120) else cardRenderH
                val sig = "${theme.id}|${theme.hashCode()}|${wW}x${wH}"
                val entry = withContext(Dispatchers.Default) {
                    renderMutex.withLock {
                        runCatching {
                            cardRenderer.clearShaderCachesForHome()
                            if (theme.isWidget) {
                                val refBg = PrismWidgetProvider.resolveReferenceBg(ctx, theme, wW, wH, cacheInMemory = false)
                                val bmp = cardRenderer.renderPreviewBitmap(
                                    w = wW,
                                    h = wH,
                                    project = theme,
                                    bg = refBg,
                                    depthMask = null,
                                    env = HUB_MOCK_ENV.copy(locked = false),
                                    now = hubMockTime(),
                                    useRgb565 = false
                                )
                                val tinyAmb = bakeTinyAmbientBitmap(refBg ?: bmp)
                                if (refBg != null && !refBg.isRecycled) runCatching { refBg.recycle() }
                                HubCachedCard(
                                    signature = sig,
                                    preview = bmp,
                                    previewImage = bmp.asImageBitmap(),
                                    ambientImage = tinyAmb.asImageBitmap()
                                )
                            } else {
                                val uri = WallpaperCarousel.currentUri(ctx, theme, advance = false, persist = false)
                                val rawBg = WallpaperCarousel.loadBitmap(ctx, uri, cardRenderW, cardRenderH, preferRgb565 = true, cacheInMemory = false)
                                val rawMask = SubjectDepthEngine.getCachedMaskIfEnabled(ctx, uri, theme, maxEdge = cardRenderH, cacheInMemory = false)
                                val bgBmp = if (rawBg != null && rawMask != null && theme.hasDepthPostProcessing()) {
                                    val base = WallpaperBaker.centerCropOpaque(rawBg, cardRenderW, cardRenderH)
                                    SubjectDepthEngine.composeProcessedWallpaper(base, rawMask, theme) { s, r ->
                                        cardRenderer.bakeBlurredBitmap(s, cardRenderW, cardRenderH, r, lowRes = true)
                                    }.also { if (base !== rawBg && !base.isRecycled) base.recycle() }
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
                                    now = hubMockTime(),
                                    useRgb565 = true
                                )
                                val tinyAmb = bakeTinyAmbientBitmap(bgBmp ?: bmp)
                                if (bgBmp != null && bgBmp !== rawBg && !bgBmp.isRecycled) bgBmp.recycle()
                                if (depthMask != null && depthMask !== rawMask && !depthMask.isRecycled) depthMask.recycle()
                                if (rawBg != null && !rawBg.isRecycled) rawBg.recycle()
                                if (rawMask != null && !rawMask.isRecycled) rawMask.recycle()
                                HubCachedCard(
                                    signature = sig,
                                    preview = bmp,
                                    previewImage = bmp.asImageBitmap(),
                                    ambientImage = tinyAmb.asImageBitmap()
                                )
                            }
                        }.getOrNull()
                    }
                }
                if (entry != null) {
                    withContext(NonCancellable) {
                        HubPreviewCache.put(theme.id, entry)
                        previewImages[theme.id] = entry.previewImage
                        ambientImages[theme.id] = entry.ambientImage
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                renderMutex.withLock {
                    cardRenderer.clearShaderCachesForHome()
                    cardRenderer.releaseOffscreenResources()
                }
            }
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
                        text = if (mode == HubMode.SCREENS) {
                            "${themes.size} ${if (themes.size == 1) "View salva" else "Views salvas"}"
                        } else {
                            "${widgets.size} ${if (widgets.size == 1) "Widget salvo" else "Widgets salvos"}"
                        },
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val gridBtnInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pressScale(gridBtnInteraction)
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(Color(0x52222228))
                            .clickable(interactionSource = gridBtnInteraction, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { ProjectStore.setHubGridMode(ctx, !isGridMode) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isGridMode) MIcons.Rounded.ViewCarousel else MIcons.Rounded.GridView,
                            contentDescription = if (isGridMode) "Modo Carrossel" else "Modo Grade",
                            tint = PrismColors.PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(Color(0x52222228))
                            .clickable { onReplayHero() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.AutoAwesome,
                            contentDescription = "Rever Apresentação",
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
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
                                modifier = Modifier.size(19.dp)
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
                    val addBtnInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pressScale(addBtnInteraction)
                            .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(PrismColors.PureWhite)
                            .clickable(interactionSource = addBtnInteraction, indication = null) {
                                newThemeName = ""
                                showCreateDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.Add,
                            contentDescription = if (mode == HubMode.SCREENS) "Nova View" else "Novo Widget",
                            tint = PrismColors.PureBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Liquid Elastic Mode Selector Pill (Telas <-> Widgets)
            BoxWithConstraints(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .width(276.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x521B1B22))
                    .border(1.dp, PrismColors.GlassBorder, CircleShape)
                    .padding(3.dp)
            ) {
                val segW = maxWidth * 0.5f
                val segWPx = with(density) { segW.toPx() }
                Box(
                    modifier = Modifier
                        .width(segW)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = mp * segWPx
                            scaleX = 1f + 0.08f * morphPulse
                        }
                        .clip(CircleShape)
                        .background(PrismColors.PureWhite)
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple(HubMode.SCREENS, "Views (Lock)", MIcons.Rounded.Wallpaper),
                        Triple(HubMode.WIDGETS, "Widgets (KWGT)", MIcons.Rounded.Widgets)
                    ).forEach { (m, label, ic) ->
                        val frac = if (m == HubMode.SCREENS) (1f - mp).coerceIn(0f, 1f) else mp.coerceIn(0f, 1f)
                        val contentColor = lerpColor(Color(0xCCFFFFFF), PrismColors.PureBlack, frac)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable {
                                    if (mode != m) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        mode = m
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = ic,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = if (frac > 0.5f) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (currentTheme != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .graphicsLayer {
                            val s = 1f - 0.07f * morphPulse
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.35f * morphPulse
                        }
                        .clip(CircleShape)
                        .background(Color(0x5C1E1E24))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (currentTheme.id == activeItemId) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PrismColors.PureWhite)
                        )
                    }
                    BlurRevealText(
                        text = currentTheme.title,
                        color = PrismColors.PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.weight(0.18f))

            // Suave transição Fade + Blur óptico entre Views e Widgets (ou Modo Grade virtualizado)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (!isGridMode && keyEvent.type == KeyEventType.KeyDown && activeItems.size > 1) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                    true
                                }
                                Key.DirectionRight -> {
                                    val target = (pagerState.currentPage + 1).coerceAtMost(activeItems.lastIndex)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isGridMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(PrismMotion.fastTween()) togetherWith fadeOut(PrismMotion.fastTween())
                        } else {
                            (fadeIn(PrismMotion.baseTween()) + scaleIn(PrismMotion.baseTween(), initialScale = PrismMotion.Scale.ENTER)) togetherWith
                                (fadeOut(PrismMotion.fastTween()) + scaleOut(PrismMotion.fastTween(), targetScale = PrismMotion.Scale.ENTER))
                        }
                    },
                    label = "HubGridCarouselMorph"
                ) { gridActive ->
                if (gridActive) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        state = gridState,
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(activeItems.size, key = { idx -> activeItems[idx].id }) { idx ->
                            val item = activeItems[idx]
                            val img = previewImages[item.id]
                            val aspect = if (mode == HubMode.WIDGETS) {
                                item.widgetAspect.coerceIn(1.1f, 2.6f)
                            } else {
                                9f / 17.5f
                            }
                            val cardInteraction = remember(item.id) { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .staggeredEnter(idx)
                                    .pressScale(cardInteraction)
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .onGloballyPositioned { coords -> cardBounds[item.id] = coords.boundsInRoot() }
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(PrismColors.CarbonBlack)
                                    .prismSpotlight()
                                    .border(
                                        1.dp,
                                        if (item.id == activeItemId) PrismColors.PureWhite else PrismColors.GlassBorder,
                                        RoundedCornerShape(22.dp)
                                    )
                                    .clickable(interactionSource = cardInteraction, indication = null) {
                                        triggerEdit(item)
                                    }
                            ) {
                                if (img != null) {
                                    Image(
                                        bitmap = img,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        if (mode == HubMode.WIDGETS) MIcons.Rounded.Widgets else MIcons.Rounded.Wallpaper,
                                        contentDescription = null,
                                        tint = PrismColors.TrackZinc,
                                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                                    )
                                }
                                Text(
                                    text = item.title,
                                    color = PrismColors.PureWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (mp < 0.999f) {
                    val blurMod = if (!reducedMotion && mp > 0.015f) {
                        Modifier.blur((mp * PrismMotion.Blur.ENTER).dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = if (reducedMotion) 1f else lerp(1f, PrismMotion.Scale.ENTER, mp)
                                scaleX = s
                                scaleY = s
                                alpha = (1f - mp).coerceIn(0f, 1f)
                            }
                            .then(blurMod)
                    ) {
                        HubCarouselPager(
                            items = themes,
                            pagerState = screensPagerState,
                            isWidgetMode = false,
                            isInteractive = mode == HubMode.SCREENS && mp < 0.05f,
                            reducedMotion = reducedMotion,
                            previewImages = previewImages,
                            cardBounds = cardBounds,
                            dragThresholdPx = dragThresholdPx,
                            onSelectPage = { target -> scope.launch { screensPagerState.animateScrollToPage(target) } },
                            onEditItem = { triggerEdit(it) },
                            onApplyItem = { triggerApply(it) }
                        )
                    }
                }

                if (mp > 0.001f) {
                    val inv = 1f - mp
                    val blurMod = if (!reducedMotion && inv > 0.015f) {
                        Modifier.blur((inv * PrismMotion.Blur.ENTER).dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = if (reducedMotion) 1f else lerp(PrismMotion.Scale.ENTER, 1f, mp)
                                scaleX = s
                                scaleY = s
                                alpha = mp.coerceIn(0f, 1f)
                            }
                            .then(blurMod)
                    ) {
                        HubCarouselPager(
                            items = widgets,
                            pagerState = widgetsPagerState,
                            isWidgetMode = true,
                            isInteractive = mode == HubMode.WIDGETS && mp > 0.95f,
                            reducedMotion = reducedMotion,
                            previewImages = previewImages,
                            cardBounds = cardBounds,
                            dragThresholdPx = dragThresholdPx,
                            onSelectPage = { target -> scope.launch { widgetsPagerState.animateScrollToPage(target) } },
                            onEditItem = { triggerEdit(it) },
                            onApplyItem = { triggerApply(it) }
                        )
                    }
                }

                // Subtle prev/next chevrons for single-hand / accessibility navigation
                if (activeItems.size > 1) {
                    if (settledIndex > 0) {
                        val prevInter = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(34.dp)
                                .pressScale(prevInter)
                                .clip(CircleShape)
                                .background(Color(0x4418181E))
                                .clickable(interactionSource = prevInter, indication = null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { pagerState.animateScrollToPage((settledIndex - 1).coerceAtLeast(0)) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(MIcons.Rounded.ChevronLeft, contentDescription = "Anterior", tint = Color(0xCCFFFFFF), modifier = Modifier.size(20.dp))
                        }
                    }
                    if (settledIndex < activeItems.lastIndex) {
                        val nextInter = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(34.dp)
                                .pressScale(nextInter)
                                .clip(CircleShape)
                                .background(Color(0x4418181E))
                                .clickable(interactionSource = nextInter, indication = null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { pagerState.animateScrollToPage((settledIndex + 1).coerceAtMost(activeItems.lastIndex)) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(MIcons.Rounded.ChevronRight, contentDescription = "Próxima", tint = Color(0xCCFFFFFF), modifier = Modifier.size(20.dp))
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
                    val editInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(editInteraction)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable(interactionSource = editInteraction, indication = null) {
                                triggerEdit(currentTheme)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.Edit,
                            contentDescription = "Editar",
                            tint = PrismColors.PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Aplicar / Fixar Widget
                    val applyInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(applyInteraction)
                            .graphicsLayer {
                                val s = 1f - 0.06f * morphPulse
                                scaleX = s
                                scaleY = s
                            }
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(PrismColors.PureWhite)
                            .clickable(interactionSource = applyInteraction, indication = null) {
                                triggerApply(currentTheme)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (mp >= 0.5f) MIcons.Rounded.PushPin else MIcons.Rounded.Check,
                            contentDescription = if (mp >= 0.5f) "Aplicar e Fixar Widget" else "Aplicar na Tela de Bloqueio",
                            tint = PrismColors.PureBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Excluir (circular frosted glass)
                    val deleteInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(deleteInteraction)
                            .clip(CircleShape)
                            .background(Color(0x18FFFFFF))
                            .clickable(interactionSource = deleteInteraction, indication = null) { pendingDelete = currentTheme },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MIcons.Rounded.DeleteOutline,
                            contentDescription = "Excluir",
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
                text = {
                    Text(
                        if (target.isWidget) {
                            "Este widget salvo será removido da biblioteca de widgets."
                        } else {
                            "Esta View salva será removida da sua biblioteca."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (target.isWidget) onDeleteWidget(target) else onDeleteTheme(target)
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
            val isWidgetCreate = mode == HubMode.WIDGETS
            FocusedInputDialog(
                title = if (isWidgetCreate) "Novo Widget (KWGT)" else "Nova View",
                initialValue = newThemeName,
                placeholder = if (isWidgetCreate) "Prism Widget" else "Default View",
                exampleHint = if (isWidgetCreate) {
                    "Ex: Relógio Vidro Home (deixe vazio para \"Prism Widget\")"
                } else {
                    "Ex: Minimal OLED (deixe vazio e confirme para \"Default View\")"
                },
                confirmLabel = if (isWidgetCreate) "Criar Widget" else "Criar View",
                dismissLabel = "Cancelar",
                onDismiss = { showCreateDialog = false },
                onConfirm = { raw ->
                    val finalTitle = raw.trim().ifBlank {
                        if (isWidgetCreate) "Prism Widget" else "Default View"
                    }
                    showCreateDialog = false
                    if (isWidgetCreate) onCreateWidget(finalTitle) else onCreateTheme(finalTitle)
                }
            )
        }

        // First-run Frosted Glass Battery Optimization Protection Modal
        if (showBatteryPrompt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA6000000))
            )
            Dialog(onDismissRequest = onDismissBatteryPrompt) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedChrome(shape = RoundedCornerShape(26.dp), dense = true, elevation = 24.dp)
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Proteção de Relógio em Tempo Real",
                        color = PrismColors.PureWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O Android / One UI costuma suspender apps em segundo plano, o que pode atrasar os minutos do relógio ou fechar o motor óptico. Desativar a restrição de bateria mantém a sincronia exata sem consumir bateria com a tela apagada (0% de loop em repouso).",
                        color = PrismColors.MutedZinc,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismissBatteryPrompt,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Agora não", color = PrismColors.MutedZinc, fontSize = 13.sp)
                        }
                        Button(
                            onClick = onRequestIgnoreBatteryOptimizations,
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrismColors.PureWhite,
                                contentColor = PrismColors.PureBlack
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Permitir Agora", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCarouselPager(
    items: List<Project>,
    pagerState: PagerState,
    isWidgetMode: Boolean,
    isInteractive: Boolean,
    reducedMotion: Boolean,
    previewImages: Map<String, ImageBitmap>,
    cardBounds: MutableMap<String, Rect>,
    dragThresholdPx: Float,
    onSelectPage: (Int) -> Unit,
    onEditItem: (Project) -> Unit,
    onApplyItem: (Project) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeDeadZonePx = with(density) { 24.dp.toPx() }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = if (reducedMotion) PrismMotion.fastTween() else PrismMotion.CarouselFloatSpring
    )
    val releaseSpec = if (reducedMotion) PrismMotion.fastTween<Float>() else PrismMotion.ExpandFloatSpring
    val rubberMaxExtent = dragThresholdPx * 3.5f

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = isInteractive,
        flingBehavior = flingBehavior,
        key = { index -> items.getOrNull(index)?.id ?: index },
        contentPadding = PaddingValues(horizontal = if (isWidgetMode) 28.dp else 52.dp),
        pageSpacing = 14.dp,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val item = items.getOrNull(page) ?: return@HorizontalPager
        val isCenter by remember(page) {
            derivedStateOf { pagerState.settledPage == page }
        }

        val dragY = remember(item.id) { Animatable(0f) }
        var triggeredHaptic by remember(item.id) { mutableStateOf(false) }
        val cardShape = RoundedCornerShape(if (isWidgetMode) 28.dp else 32.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                    val scale = lerp(1.0f, PrismMotion.Scale.CAROUSEL_SIDE, pageOffset)
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(1.0f, 0.55f, pageOffset)
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.runtime.key(item.id) {
                val cardModifier = if (isWidgetMode) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(item.widgetAspect.coerceIn(com.example.prismlock.model.WIDGET_ASPECT_MIN, com.example.prismlock.model.WIDGET_ASPECT_MAX))
                } else {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 19.5f)
                }
                Box(
                    modifier = cardModifier
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            if (r.width > 10f && r.height > 10f) cardBounds[item.id] = r
                        }
                        .graphicsLayer {
                            translationY = PrismMotion.rubberBand(dragY.value, rubberMaxExtent)
                            shadowElevation = 16.dp.toPx()
                            shape = cardShape
                            clip = true
                        }
                        .background(PrismColors.PureBlack)
                        .pointerInput(item.id, isCenter, isInteractive) {
                            if (!isCenter || !isInteractive) return@pointerInput
                            val velocityTracker = VelocityTracker()
                            var dragBlockedByEdge = false
                            detectVerticalDragGestures(
                                onDragStart = { startOffset ->
                                    triggeredHaptic = false
                                    velocityTracker.resetTracking()
                                    dragBlockedByEdge = startOffset.y < edgeDeadZonePx || startOffset.y > size.height - edgeDeadZonePx
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragBlockedByEdge) return@detectVerticalDragGestures
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    scope.launch {
                                        val next = (dragY.value + dragAmount).coerceIn(-dragThresholdPx * 2.5f, dragThresholdPx * 2.5f)
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
                                    if (dragBlockedByEdge) return@detectVerticalDragGestures
                                    val cur = dragY.value
                                    val velY = velocityTracker.calculateVelocity().y
                                    val commitUp = cur <= -dragThresholdPx || (cur < -dragThresholdPx * 0.35f && velY <= -PrismMotion.Gesture.COMMIT_VELOCITY)
                                    val commitDown = cur >= dragThresholdPx || (cur > dragThresholdPx * 0.35f && velY >= PrismMotion.Gesture.COMMIT_VELOCITY)
                                    scope.launch {
                                        when {
                                            commitUp -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                dragY.animateTo(0f, releaseSpec)
                                                onEditItem(item)
                                            }
                                            commitDown -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                dragY.animateTo(0f, releaseSpec)
                                                onApplyItem(item)
                                            }
                                            else -> dragY.animateTo(0f, releaseSpec)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { dragY.animateTo(0f, releaseSpec) }
                                }
                            )
                        }
                        .clickable(enabled = isInteractive) {
                            if (isCenter) onEditItem(item) else onSelectPage(page)
                        }
                ) {
                    val img = previewImages[item.id]
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = item.title,
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
                                if (isWidgetMode) MIcons.Rounded.Widgets else MIcons.Rounded.Wallpaper,
                                contentDescription = null,
                                tint = PrismColors.TrackZinc,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    val curDrag = dragY.value
                    val applyWord = if (isWidgetMode) "Fixar" else "Aplicar"
                    val dragLabel = when {
                        curDrag <= -dragThresholdPx * 0.6f -> "Solte ↑ para Editar"
                        curDrag >= dragThresholdPx * 0.6f -> "Solte ↓ para $applyWord"
                        isCenter -> "↑ Editar  ·  ↓ $applyWord"
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
