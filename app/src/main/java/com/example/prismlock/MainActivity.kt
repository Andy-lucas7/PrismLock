package com.example.prismlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.PrismPackageManager
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.Icons
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.mapNode
import com.example.prismlock.ui.PrismColors
import com.example.prismlock.ui.PrismLockTheme
import com.example.prismlock.ui.PrismMotion
import com.example.prismlock.ui.StudioScreen
import com.example.prismlock.ui.ThemeHubScreen
import com.example.prismlock.ui.frostedChrome
import com.example.prismlock.wallpaper.PrismWallpaperService
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FontManager.init(this)
        EnvProvider.refreshBattery(this)
        EnvProvider.refreshAlarm(this)
        setContent {
            PrismLockTheme {
                PrismApp()
            }
        }
    }
}

private enum class AppScreen { HUB, STUDIO }

@OptIn(FlowPreview::class)
@Composable
private fun PrismApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val renderer = remember { LiquidGlassRenderer() }
    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    val themes by ProjectStore.themesFlow(ctx).collectAsState(initial = ProjectStore.defaultStarterThemes())
    val activeApplied by ProjectStore.flow(ctx).collectAsState(initial = Project())
    val env by EnvProvider.env.collectAsState()

    var screen by remember { mutableStateOf(AppScreen.HUB) }
    var editingProject by remember { mutableStateOf(Project()) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var focusThemeId by remember { mutableStateOf<String?>(null) }
    val saveFlow = remember { MutableSharedFlow<Project>(extraBufferCapacity = 64) }

    LaunchedEffect(Unit) {
        saveFlow.debounce(400).collectLatest { pending ->
            ProjectStore.upsertTheme(ctx, pending)
        }
    }

    var rawBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawDepthBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var depthBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var fontFolderUri by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember {
        FontManager.init(ctx)
        mutableStateOf(FontManager.availableFonts(ctx))
    }

    LaunchedEffect(Unit) {
        val savedFolder = ProjectStore.fontFolderUri(ctx)
        fontFolderUri = savedFolder
        if (savedFolder != null) {
            FontManager.syncFontFolder(ctx, savedFolder)
        }
        FontManager.prewarm(ctx)
        importedFonts = FontManager.availableFonts(ctx)
    }
    val wallPalette = remember(rawBgBitmap) { renderer.extractPalette(rawBgBitmap) }

    var isBaking by remember { mutableStateOf(false) }
    var bakeProgress by remember { mutableFloatStateOf(0f) }
    var bakeStatus by remember { mutableStateOf("Otimizando e compilando pacote nativo...") }

    val currentProject by rememberUpdatedState(editingProject)
    val currentSelectedId by rememberUpdatedState(selectedLayerId)

    LaunchedEffect(
        screen,
        editingProject.id,
        editingProject.folderUri,
        editingProject.imageUri,
        editingProject.depth,
        editingProject.defaultDepthForNewImages,
        editingProject.depthOverrides,
        previewUri
    ) {
        if (screen != AppScreen.STUDIO) return@LaunchedEffect
        val uri = previewUri ?: WallpaperCarousel.currentUri(ctx, editingProject, advance = false)
        if (uri != null && previewUri != uri) previewUri = uri
        val (baseW, baseH) = WallpaperBaker.resolveDisplaySize(ctx)
        val bmp = WallpaperCarousel.loadBitmap(ctx, uri, baseW, baseH)
        rawBgBitmap = bmp
        rawDepthBitmap = SubjectDepthEngine.getOrCreateMaskIfEnabled(ctx, uri, bmp, editingProject)
    }

    LaunchedEffect(
        rawBgBitmap,
        rawDepthBitmap,
        editingProject.depth,
        editingProject.depthBgBlur,
        editingProject.depthBgDesaturate,
        editingProject.depthInvertColorPop,
        editingProject.depthSubjectPop,
        editingProject.depthVignette
    ) {
        val rBg = rawBgBitmap
        val rDepth = rawDepthBitmap
        val proj = currentProject
        if (rBg != null && rDepth != null && proj.hasDepthPostProcessing()) {
            kotlinx.coroutines.delay(60L)
            val (baseW, baseH) = WallpaperBaker.resolveDisplaySize(ctx)
            val (compBg, compDepth) = withContext(Dispatchers.Default) {
                val base = WallpaperBaker.centerCropOpaque(rBg, baseW, baseH)
                val bgOut = SubjectDepthEngine.composeProcessedWallpaper(base, rDepth, proj) { s, r ->
                    renderer.bakeBlurredBitmap(s, baseW, baseH, r)
                }
                val maskOut = SubjectDepthEngine.applySubjectColorPopMask(rDepth, proj)
                bgOut to maskOut
            }
            bgBitmap = compBg
            depthBitmap = compDepth
        } else {
            bgBitmap = rBg
            depthBitmap = rDepth
        }
    }

    LaunchedEffect(editingProject.lat, editingProject.lon, editingProject.city) {
        EnvProvider.refreshWeather(ctx, editingProject.lat, editingProject.lon, editingProject.city)
    }

    fun stage(newProj: Project) {
        editingProject = newProj
        saveFlow.tryEmit(newProj)
    }

    fun flushSave() {
        val snapshot = currentProject
        scope.launch { ProjectStore.upsertTheme(ctx, snapshot) }
    }

    fun commit(newProj: Project) {
        editingProject = newProj
        scope.launch { ProjectStore.upsertTheme(ctx, newProj) }
    }

    fun openStudio(theme: Project) {
        renderer.clearShaderCachesForHome()
        rawBgBitmap = null
        rawDepthBitmap = null
        bgBitmap = null
        depthBitmap = null
        previewUri = null
        editingProject = theme
        selectedLayerId = theme.layers.firstOrNull()?.id
        screen = AppScreen.STUDIO
        fontFolderUri?.let { saved ->
            scope.launch {
                FontManager.syncFontFolder(ctx, saved)
                importedFonts = FontManager.availableFonts(ctx)
            }
        }
    }

    fun returnToHub() {
        val snapshot = currentProject
        scope.launch { ProjectStore.upsertTheme(ctx, snapshot) }
        screen = AppScreen.HUB
    }

    fun applyAndBake(target: Project, forcePicker: Boolean = false) {
        if (isBaking) return
        isBaking = true
        bakeProgress = 0.04f
        bakeStatus = "Otimizando e compilando pacote nativo..."
        scope.launch {
            ProjectStore.save(ctx, target)
            WallpaperBaker.bakeAll(ctx, target, renderer) { p, msg ->
                bakeProgress = p
                bakeStatus = msg
            }
            ctx.sendBroadcast(Intent(WallpaperBaker.ACTION_BAKED_READY).setPackage(ctx.packageName))
            isBaking = false
            val wm = WallpaperManager.getInstance(ctx)
            val info = wm.wallpaperInfo
            val alreadyActive = info?.packageName == ctx.packageName &&
                info?.serviceName == PrismWallpaperService::class.java.name
            if (forcePicker || !alreadyActive) {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(ctx, PrismWallpaperService::class.java)
                    )
                }
                ctx.startActivity(intent)
            } else {
                snackbar.showSnackbar("Tema \"${target.title}\" aplicado na Tela de Bloqueio")
            }
        }
    }

    fun updateLayer(id: String, f: (LayerNode) -> LayerNode) {
        stage(currentProject.copy(layers = currentProject.layers.mapNode(id, f)))
    }

    fun updateSelected(f: (LayerNode) -> LayerNode) {
        val id = currentSelectedId ?: return
        updateLayer(id, f)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            previewUri = null
            commit(currentProject.copy(folderUri = uri.toString(), imageUri = null))
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val s = uri.toString()
            previewUri = s
            commit(currentProject.copy(imageUri = s, folderUri = null))
        }
    }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = FontManager.import(ctx, uri)
            if (f != null) {
                importedFonts = FontManager.availableFonts(ctx)
                updateSelected { it.copy(font = f) }
            }
        }
    }

    val fontFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val s = uri.toString()
            fontFolderUri = s
            scope.launch {
                ProjectStore.setFontFolderUri(ctx, s)
                val count = FontManager.syncFontFolder(ctx, s)
                importedFonts = FontManager.availableFonts(ctx)
                snackbar.showSnackbar("$count ${if (count == 1) "fonte sincronizada" else "fontes sincronizadas"} da pasta")
            }
        }
    }

    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val svg = Icons.importSvg(ctx, uri)
            if (svg != null) {
                val n = LayerNode(name = "SVG", type = LayerType.SVG, svgData = svg.data, svgW = svg.w, svgH = svg.h, w = 140f, h = 140f)
                commit(currentProject.copy(layers = currentProject.layers + n))
                selectedLayerId = n.id
            }
        }
    }

    val importThemePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = PrismPackageManager.importFromDocument(ctx, uri)
                if (imported != null) {
                    FontManager.prewarm(ctx)
                    importedFonts = FontManager.availableFonts(ctx)
                    ProjectStore.upsertTheme(ctx, imported)
                    focusThemeId = imported.id
                    snackbar.showSnackbar("Pacote \"${imported.title}\" importado")
                } else {
                    snackbar.showSnackbar("Pacote .prism / .zip inválido")
                }
            }
        }
    }

    val importFolderThemePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = PrismPackageManager.importFromFolder(ctx, uri)
                if (imported != null) {
                    FontManager.prewarm(ctx)
                    importedFonts = FontManager.availableFonts(ctx)
                    ProjectStore.upsertTheme(ctx, imported)
                    focusThemeId = imported.id
                    snackbar.showSnackbar("Pasta \"${imported.title}\" importada")
                } else {
                    snackbar.showSnackbar("Pasta sem theme.json válido")
                }
            }
        }
    }

    val exportThemePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = PrismPackageManager.exportPackage(ctx, currentProject, uri)
                snackbar.showSnackbar(if (ok) "Pacote .prism exportado com mídia e fontes" else "Erro ao exportar pacote")
            }
        }
    }

    BackHandler(enabled = screen == AppScreen.STUDIO) {
        returnToHub()
    }

    Box(Modifier.fillMaxSize().background(PrismColors.PureBlack)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == AppScreen.STUDIO) {
                    (slideInVertically(animationSpec = PrismMotion.IntOffsetSpring) { it / 4 } + fadeIn(PrismMotion.FloatSpring))
                        .togetherWith(fadeOut(PrismMotion.FloatSpring))
                } else {
                    fadeIn(PrismMotion.FloatSpring)
                        .togetherWith(slideOutVertically(animationSpec = PrismMotion.IntOffsetSpring) { it / 4 } + fadeOut(PrismMotion.FloatSpring))
                }
            },
            label = "ScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                AppScreen.HUB -> ThemeHubScreen(
                    themes = themes,
                    activeThemeId = activeApplied.id,
                    focusThemeId = focusThemeId,
                    onFocusConsumed = { focusThemeId = null },
                    env = env,
                    onCreateTheme = { title ->
                        val finalTitle = title.trim().ifBlank { "Default Screen" }
                        val fresh = ProjectStore.newTheme(finalTitle)
                        scope.launch { ProjectStore.upsertTheme(ctx, fresh) }
                        focusThemeId = fresh.id
                        openStudio(fresh)
                    },
                    onImportTheme = { importThemePicker.launch(arrayOf("*/*")) },
                    onImportFolderTheme = { importFolderThemePicker.launch(null) },
                    onEditTheme = { openStudio(it) },
                    onApplyTheme = { applyAndBake(it, forcePicker = false) },
                    onDeleteTheme = { t ->
                        scope.launch {
                            ProjectStore.deleteTheme(ctx, t.id)
                            snackbar.showSnackbar("Tema \"${t.title}\" removido")
                        }
                    }
                )

                AppScreen.STUDIO -> StudioScreen(
                    renderer = renderer,
                    project = editingProject,
                    selectedId = selectedLayerId,
                    bgBitmap = bgBitmap,
                    depthBitmap = depthBitmap,
                    activePreviewUri = previewUri,
                    importedFonts = importedFonts,
                    wallPalette = wallPalette,
                    env = env,
                    onSelectLayer = { selectedLayerId = it },
                    onUpdateLayer = ::updateLayer,
                    onUpdateSelected = ::updateSelected,
                    onStageProject = ::stage,
                    onCommitProject = ::commit,
                    onFlushSave = ::flushSave,
                    onBack = ::returnToHub,
                    onRenameTheme = { newTitle ->
                        val cleanTitle = newTitle.trim().ifBlank { "Default Screen" }
                        commit(currentProject.copy(title = cleanTitle))
                    },
                    onSaveExplicit = {
                        flushSave()
                        scope.launch { snackbar.showSnackbar("Tema \"${currentProject.title}\" salvo") }
                    },
                    onSaveAsNew = { title ->
                        val cloned = currentProject.copy(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            layers = currentProject.layers.map { it.copyDeep() }
                        )
                        commit(cloned)
                        scope.launch { snackbar.showSnackbar("Salvo como \"$title\"") }
                    },
                    onApply = { applyAndBake(currentProject, forcePicker = false) },
                    onExportFile = {
                        val safeName = PrismPackageManager.sanitizeArchiveFileName(currentProject.title)
                        exportThemePicker.launch("$safeName.prism")
                    },
                    onToggleDepth = { imageKey, enabled ->
                        val nextMap = currentProject.depthOverrides.toMutableMap().apply {
                            put(imageKey, enabled)
                        }.toMap()
                        val updated = currentProject.copy(depth = true, depthOverrides = nextMap)
                        editingProject = updated
                        saveFlow.tryEmit(updated)
                    },
                    onSelectPreview = { uriStr, idx ->
                        previewUri = uriStr
                        scope.launch {
                            if (editingProject.folderUri != null) {
                                ProjectStore.setIndex(ctx, idx, uriStr)
                            } else {
                                ProjectStore.setCurrentWallpaperUri(ctx, uriStr)
                            }
                        }
                    },
                    onPickFolder = { folderPicker.launch(null) },
                    onPickImage = { imagePicker.launch(arrayOf("image/*", "video/*")) },
                    onClearWallpaper = {
                        previewUri = null
                        rawBgBitmap = null
                        rawDepthBitmap = null
                        bgBitmap = null
                        depthBitmap = null
                        renderer.clearShaderCachesForHome()
                        commit(currentProject.copy(folderUri = null, imageUri = null))
                    },
                    onImportFont = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "*/*")) },
                    hasFontFolder = fontFolderUri != null,
                    onPickFontFolder = { fontFolderPicker.launch(null) },
                    onRefreshFontFolder = { showToast ->
                        val saved = fontFolderUri
                        if (saved == null) {
                            fontFolderPicker.launch(null)
                        } else {
                            scope.launch {
                                val count = FontManager.syncFontFolder(ctx, saved)
                                importedFonts = FontManager.availableFonts(ctx)
                                if (showToast) {
                                    snackbar.showSnackbar("$count ${if (count == 1) "fonte atualizada" else "fontes atualizadas"}")
                                }
                            }
                        }
                    },
                    onImportSvg = { svgPicker.launch(arrayOf("image/svg+xml", "*/*")) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        )

        if (isBaking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA6000000))
            )
            Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Otimizando e compilando pacote nativo...",
                        color = PrismColors.PureWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    LinearProgressIndicator(
                        progress = { bakeProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrismColors.PureWhite,
                        trackColor = PrismColors.TrackZinc
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bakeStatus,
                            color = PrismColors.MutedZinc,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(bakeProgress * 100).roundToInt().coerceIn(0, 100)}%",
                            color = PrismColors.PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
