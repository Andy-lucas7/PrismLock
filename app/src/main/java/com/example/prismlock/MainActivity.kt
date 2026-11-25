package com.example.prismlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.MediaImporter
import com.example.prismlock.data.PrismPackageManager
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.GlyphAtlasBaker
import com.example.prismlock.engine.Icons
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.StemStretch
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.find
import com.example.prismlock.model.mapNode
import com.example.prismlock.ui.PrismColors
import com.example.prismlock.ui.PrismHeroScreen
import com.example.prismlock.ui.PrismLockTheme
import com.example.prismlock.ui.PrismMotion
import com.example.prismlock.ui.StudioScreen
import com.example.prismlock.ui.ThemeHubScreen
import com.example.prismlock.ui.ViewExpandController
import com.example.prismlock.ui.ViewExpandOverlay
import com.example.prismlock.ui.clearHubPreviewCache
import com.example.prismlock.ui.frostedChrome
import kotlinx.coroutines.CancellationException
import com.example.prismlock.wallpaper.PrismWallpaperService
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import com.example.prismlock.widget.PrismWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.roundToInt

internal object PendingSave {
    private val ref = AtomicReference<Project?>(null)

    fun set(p: Project) {
        ref.set(p)
    }

    suspend fun flush(ctx: Context) {
        val p = ref.getAndSet(null) ?: return
        withContext(NonCancellable) {
            if (p.isWidget) ProjectStore.upsertWidget(ctx, p) else ProjectStore.upsertTheme(ctx, p)
        }
    }
}

private val bakeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FontManager.init(this)
        EnvProvider.refreshBattery(this)
        EnvProvider.refreshAlarm(this)
        val appCtx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ProjectStore.ensureIpcMirrors(appCtx) }
        }
        setContent {
            PrismLockTheme {
                PrismApp()
            }
        }
    }

    private fun flushPendingSaveBlocking() {
        val appCtx = applicationContext
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(2000L) { PendingSave.flush(appCtx) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        flushPendingSaveBlocking()
        if (!isChangingConfigurations) {
            releaseUiMemory()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN && !isChangingConfigurations) {
            releaseUiMemory()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        if (!isChangingConfigurations) {
            releaseUiMemory()
        }
    }

    private fun releaseUiMemory() {
        if (WallpaperBaker.isBakeRunning) return
        WallpaperBaker.clearMemoryCache()
        GlyphAtlasBaker.clearCache()
        SubjectDepthEngine.releaseMemory()
        WallpaperCarousel.clearMemoryCache()
        StemStretch.clearCaches()
        clearHubPreviewCache()
    }

    override fun onDestroy() {
        flushPendingSaveBlocking()
        if (!isChangingConfigurations) {
            releaseUiMemory()
        }
        super.onDestroy()
    }
}

private enum class AppScreen { HUB, STUDIO }

@OptIn(FlowPreview::class)
@Composable
private fun PrismApp() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val renderer = remember { LiquidGlassRenderer().apply { attachContext(ctx) } }
    var rawBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawDepthBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var depthBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }
    DisposableEffect(lifecycleOwner, renderer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    renderer.releaseOffscreenResources()
                    renderer.clearShaderCachesForHome()
                    rawBgBitmap = null
                    rawDepthBitmap = null
                    bgBitmap = null
                    depthBitmap = null
                }
                Lifecycle.Event.ON_START -> {
                    reloadTrigger++
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themes by ProjectStore.themesFlow(ctx).collectAsState(initial = ProjectStore.defaultStarterThemes())
    val widgets by ProjectStore.widgetsFlow(ctx).collectAsState(initial = ProjectStore.defaultStarterWidgets())
    val activeApplied by ProjectStore.flow(ctx).collectAsState(initial = Project())
    val activeWidget by ProjectStore.activeWidgetFlow(ctx).collectAsState(initial = ProjectStore.defaultStarterWidgets().first())
    val heroSeenState by ProjectStore.heroSeenFlow(ctx).map<Boolean, Boolean?> { it }.collectAsState(initial = null)
    val batteryDismissedState by ProjectStore.batteryPromptDismissedFlow(ctx).map<Boolean, Boolean?> { it }.collectAsState(initial = null)
    var replayingHero by remember { mutableStateOf(false) }
    var heroIntroSettled by remember { mutableStateOf(false) }
    val deferHubPreviews = (heroSeenState == null) || ((heroSeenState == false || replayingHero) && !heroIntroSettled)

    var screenName by rememberSaveable { mutableStateOf(AppScreen.HUB.name) }
    val screen = remember(screenName) { runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.HUB) }
    var savedEditingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf(Project()) }
    var selectedLayerId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAssetAlias by remember { mutableStateOf<String?>(null) }
    val undoStack = remember(editingProject.id) { mutableStateListOf<Project>() }
    val redoStack = remember(editingProject.id) { mutableStateListOf<Project>() }
    var stageSnapshotBase by remember(editingProject.id) { mutableStateOf<Project?>(null) }
    val saveFlow = remember { MutableSharedFlow<Project>(extraBufferCapacity = 64) }

    LaunchedEffect(screen, savedEditingId, themes, widgets) {
        if (screen == AppScreen.STUDIO && savedEditingId != null && editingProject.id != savedEditingId) {
            val restored = (themes + widgets).firstOrNull { it.id == savedEditingId }
            if (restored != null) {
                editingProject = restored
                if (selectedLayerId == null || restored.layers.find(selectedLayerId) == null) {
                    selectedLayerId = restored.layers.firstOrNull()?.id
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        saveFlow.debounce(400).collectLatest {
            PendingSave.flush(ctx.applicationContext)
        }
    }

    var previewUri by remember { mutableStateOf<String?>(null) }
    val env by EnvProvider.env.collectAsState()
    var fontFolderUri by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember {
        FontManager.init(ctx)
        mutableStateOf(FontManager.availableFonts(ctx))
    }

    LaunchedEffect(deferHubPreviews) {
        if (deferHubPreviews) return@LaunchedEffect
        EnvProvider.refreshAudio(ctx)
        val savedFolder = ProjectStore.fontFolderUri(ctx)
        fontFolderUri = savedFolder
        if (savedFolder != null) {
            FontManager.syncFontFolder(ctx, savedFolder)
        }
        importedFonts = FontManager.availableFonts(ctx)
    }
    val wallPalette = remember(rawBgBitmap) { renderer.extractPalette(rawBgBitmap) }

    var isBaking by remember { mutableStateOf(false) }
    var bakeProgress by remember { mutableFloatStateOf(0f) }
    var bakeStatus by remember { mutableStateOf("Otimizando e compilando pacote nativo...") }

    LaunchedEffect(
        screen,
        editingProject.id,
        editingProject.folderUri,
        editingProject.imageUri,
        editingProject.isWidget,
        editingProject.depth,
        editingProject.defaultDepthForNewImages,
        editingProject.depthOverrides,
        previewUri,
        reloadTrigger
    ) {
        if (screen != AppScreen.STUDIO) return@LaunchedEffect
        val (fullW, fullH) = WallpaperBaker.resolveDisplaySize(ctx)
        if (editingProject.isWidget) {
            val uri = previewUri ?: WallpaperCarousel.currentUri(ctx, editingProject, advance = false)
            if (uri != null && previewUri != uri) previewUri = uri
            val wW = fullW.coerceAtMost(720)
            val bmp = PrismWidgetProvider.resolveReferenceBg(ctx, editingProject, wW, (wW / editingProject.widgetAspect.coerceIn(0.6f, 4.5f)).roundToInt().coerceAtLeast(200))
            rawBgBitmap = bmp
            rawDepthBitmap = null
        } else {
            val uri = previewUri ?: WallpaperCarousel.currentUri(ctx, editingProject, advance = false)
            if (uri != null && previewUri != uri) previewUri = uri
            val needsHdForDepth = editingProject.depth && uri != null &&
                editingProject.isDepthEnabledFor(WallpaperCarousel.imageKey(ctx, uri))
            val reqW = if (needsHdForDepth) fullW else fullW.coerceAtMost(720)
            val reqH = if (needsHdForDepth) fullH else (reqW * (fullH.toFloat() / fullW.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)
            val bmp = WallpaperCarousel.loadBitmap(ctx, uri, reqW, reqH)
            rawBgBitmap = bmp
            rawDepthBitmap = SubjectDepthEngine.getOrCreateMaskIfEnabled(ctx, uri, bmp, editingProject)
        }
    }

    var isDraggingBokeh by remember { mutableStateOf(false) }
    LaunchedEffect(
        rawBgBitmap,
        rawDepthBitmap,
        editingProject.depth,
        editingProject.depthBgBlur,
        editingProject.depthBgDesaturate,
        editingProject.depthInvertColorPop,
        editingProject.depthSubjectPop,
        editingProject.depthVignette,
        isDraggingBokeh
    ) {
        val rBg = rawBgBitmap
        val rDepth = rawDepthBitmap
        val proj = editingProject
        if (rBg != null && rDepth != null && proj.hasDepthPostProcessing()) {
            val interactive = isDraggingBokeh
            if (!interactive) kotlinx.coroutines.delay(45L)
            val (fullW, fullH) = WallpaperBaker.resolveDisplaySize(ctx)
            val targetW = if (interactive) (fullW / 2).coerceAtLeast(180) else fullW
            val targetH = if (interactive) (fullH / 2).coerceAtLeast(180) else fullH
            val (compBg, compDepth) = withContext(Dispatchers.Default) {
                val base = WallpaperBaker.centerCropOpaque(rBg, targetW, targetH)
                val bgOut = SubjectDepthEngine.composeProcessedWallpaper(base, rDepth, proj) { s, r ->
                    renderer.bakeBlurredBitmap(s, targetW, targetH, r, lowRes = interactive)
                }
                if (base !== rBg && base !== bgOut && !base.isRecycled) base.recycle()
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

    LaunchedEffect(screen, editingProject.lat, editingProject.lon, editingProject.city) {
        if (screen != AppScreen.STUDIO) return@LaunchedEffect
        EnvProvider.refreshWeather(ctx, editingProject.lat, editingProject.lon, editingProject.city)
    }

    fun pushHistory(prev: Project) {
        if (undoStack.lastOrNull() != prev) {
            undoStack.add(prev)
            while (undoStack.size > 30) undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun stage(newProj: Project) {
        if (stageSnapshotBase == null && newProj.id == editingProject.id && newProj != editingProject) {
            stageSnapshotBase = editingProject
        }
        editingProject = newProj
        PendingSave.set(newProj)
        saveFlow.tryEmit(newProj)
    }

    fun persistProject(snapshot: Project) {
        PendingSave.set(snapshot)
        scope.launch { PendingSave.flush(ctx.applicationContext) }
    }

    fun flushSave() {
        stageSnapshotBase?.let { base ->
            if (base.id == editingProject.id && base != editingProject) pushHistory(base)
        }
        stageSnapshotBase = null
        persistProject(editingProject)
    }

    fun commit(newProj: Project) {
        val base = stageSnapshotBase ?: editingProject
        if (newProj.id == base.id && newProj != base) {
            pushHistory(base)
        }
        stageSnapshotBase = null
        savedEditingId = newProj.id
        editingProject = newProj
        persistProject(newProj)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(editingProject)
        stageSnapshotBase = null
        editingProject = prev
        if (selectedLayerId != null && prev.layers.find(selectedLayerId) == null) {
            selectedLayerId = prev.layers.firstOrNull()?.id
        }
        persistProject(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(editingProject)
        stageSnapshotBase = null
        editingProject = next
        if (selectedLayerId != null && next.layers.find(selectedLayerId) == null) {
            selectedLayerId = next.layers.firstOrNull()?.id
        }
        persistProject(next)
    }

    fun openStudio(theme: Project) {
        renderer.clearShaderCachesForHome()
        rawBgBitmap = null
        rawDepthBitmap = null
        bgBitmap = null
        depthBitmap = null
        previewUri = null
        stageSnapshotBase = null
        savedEditingId = theme.id
        editingProject = theme
        selectedLayerId = theme.layers.firstOrNull()?.id
        screenName = AppScreen.STUDIO.name
        fontFolderUri?.let { saved ->
            scope.launch {
                FontManager.syncFontFolder(ctx, saved)
                importedFonts = FontManager.availableFonts(ctx)
            }
        }
    }

    fun returnToHub() {
        val snapshot = editingProject
        persistProject(snapshot)
        renderer.release()
        WallpaperCarousel.clearMemoryCache()
        SubjectDepthEngine.releaseMemory()
        GlyphAtlasBaker.clearCache()
        rawBgBitmap = null
        rawDepthBitmap = null
        bgBitmap = null
        depthBitmap = null
        focusThemeId = snapshot.id
        savedEditingId = null
        screenName = AppScreen.HUB.name
    }

    fun applyAndBake(target: Project, forcePicker: Boolean = false) {
        if (target.isWidget) {
            scope.launch {
                ProjectStore.saveActiveWidget(ctx, target)
                if (PrismWidgetProvider.hasPinnedWidgets(ctx)) {
                    PrismWidgetProvider.updateAll(ctx)
                    snackbar.showSnackbar("Widget \"${target.title}\" atualizado na Tela Inicial!")
                } else {
                    val pinned = PrismWidgetProvider.requestPin(ctx)
                    snackbar.showSnackbar(
                        if (pinned) "Widget \"${target.title}\" pronto para fixar!"
                        else "Adicione o widget PrismLock manualmente pela lista de widgets"
                    )
                }
            }
            return
        }
        if (isBaking || WallpaperBaker.isBakeRunning) return
        isBaking = true
        bakeProgress = 0.04f
        bakeStatus = "Otimizando e compilando pacote nativo..."
        val appCtx = ctx.applicationContext
        bakeScope.launch {
            val ok = try {
                ProjectStore.save(appCtx, target)
                WallpaperBaker.bakeAll(appCtx, target, renderer) { p, msg ->
                    bakeProgress = p
                    bakeStatus = msg
                }
                appCtx.sendBroadcast(Intent(WallpaperBaker.ACTION_BAKED_READY).setPackage(appCtx.packageName))
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("PrismBake", "bake failed", e)
                false
            } finally {
                isBaking = false
            }
            if (!scope.isActive) return@launch
            scope.launch {
                if (!ok) {
                    snackbar.showSnackbar("Falha ao compilar o pacote nativo")
                    return@launch
                }
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
    }

    fun updateLayer(id: String, f: (LayerNode) -> LayerNode) {
        stage(editingProject.copy(layers = editingProject.layers.mapNode(id, f)))
    }

    fun updateSelected(f: (LayerNode) -> LayerNode) {
        val id = selectedLayerId ?: return
        updateLayer(id, f)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val oldUri = editingProject.imageUri
            val updated = editingProject.copy(folderUri = uri.toString(), imageUri = null)
            previewUri = null
            commit(updated)
            if (oldUri != null) {
                val appCtx = ctx.applicationContext
                scope.launch { MediaImporter.deleteIfUnused(appCtx, oldUri, MediaImporter.collectProjectUris(updated)) }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val targetProjectId = editingProject.id
            val appCtx = ctx.applicationContext
            scope.launch {
                val imported = MediaImporter.importToAppStorage(appCtx, uri)
                if (imported == null) {
                    snackbar.showSnackbar("Não foi possível importar a mídia selecionada")
                    return@launch
                }
                val s = imported.toString()
                if (editingProject.id == targetProjectId) {
                    val oldUri = editingProject.imageUri
                    val updated = editingProject.copy(imageUri = s, folderUri = null)
                    previewUri = s
                    commit(updated)
                    if (oldUri != null && oldUri != s) {
                        MediaImporter.deleteIfUnused(appCtx, oldUri, MediaImporter.collectProjectUris(updated))
                    }
                } else {
                    MediaImporter.deleteIfUnused(appCtx, s, MediaImporter.collectProjectUris(editingProject))
                }
            }
        }
    }

    val imageFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val s = uri.toString()
            val oldUri = editingProject.imageUri
            val updated = editingProject.copy(imageUri = s, folderUri = null)
            previewUri = s
            commit(updated)
            if (oldUri != null && oldUri != s) {
                val appCtx = ctx.applicationContext
                scope.launch { MediaImporter.deleteIfUnused(appCtx, oldUri, MediaImporter.collectProjectUris(updated)) }
            }
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
                commit(editingProject.copy(layers = editingProject.layers + n))
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
                    if (imported.isWidget) {
                        ProjectStore.upsertWidget(ctx, imported)
                        focusThemeId = imported.id
                        snackbar.showSnackbar("Widget \"${imported.title}\" importado")
                    } else {
                        ProjectStore.upsertTheme(ctx, imported)
                        focusThemeId = imported.id
                        snackbar.showSnackbar("Tema \"${imported.title}\" importado")
                    }
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
                    if (imported.isWidget) {
                        ProjectStore.upsertWidget(ctx, imported)
                        focusThemeId = imported.id
                        snackbar.showSnackbar("Widget \"${imported.title}\" importado da pasta")
                    } else {
                        ProjectStore.upsertTheme(ctx, imported)
                        focusThemeId = imported.id
                        snackbar.showSnackbar("Pasta \"${imported.title}\" importada")
                    }
                } else {
                    snackbar.showSnackbar("Pasta sem theme.json ou widget.json válido")
                }
            }
        }
    }

    val exportThemePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val exporting = editingProject
                val ok = PrismPackageManager.exportPackage(ctx, exporting, uri)
                snackbar.showSnackbar(
                    if (ok) {
                        if (exporting.isWidget) "Widget .prism exportado com ativos e fontes"
                        else "Tema .prism exportado com mídia e fontes"
                    } else "Erro ao exportar pacote"
                )
            }
        }
    }

    val layerImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val targetProjectId = editingProject.id
            val targetLayerId = selectedLayerId
            val appCtx = ctx.applicationContext
            scope.launch {
                val imported = MediaImporter.importToAppStorage(appCtx, uri)
                if (imported == null) {
                    snackbar.showSnackbar("Não foi possível importar a imagem da camada")
                    return@launch
                }
                val s = imported.toString()
                if (editingProject.id == targetProjectId && targetLayerId != null) {
                    val oldLayerUri = editingProject.layers.find(targetLayerId)?.imageUri
                    val updated = editingProject.copy(
                        layers = editingProject.layers.mapNode(targetLayerId) { it.copy(imageUri = s) }
                    )
                    commit(updated)
                    if (!oldLayerUri.isNullOrBlank() && oldLayerUri != s) {
                        MediaImporter.deleteIfUnused(appCtx, oldLayerUri, MediaImporter.collectProjectUris(updated))
                    }
                } else {
                    MediaImporter.deleteIfUnused(appCtx, s, MediaImporter.collectProjectUris(editingProject))
                }
            }
        }
    }

    val customAssetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val alias = pendingAssetAlias
        pendingAssetAlias = null
        if (uri != null && !alias.isNullOrBlank()) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = ctx.contentResolver.getType(uri).orEmpty().lowercase()
            val svg = if ("svg" in mime || uri.toString().lowercase().endsWith(".svg")) {
                Icons.importSvg(ctx, uri)
            } else {
                val bmp = WallpaperCarousel.loadCustomAssetBitmap(ctx, uri.toString(), 64, 64)
                if (bmp == null) Icons.importSvg(ctx, uri) else null
            }
            if (svg != null) {
                val encoded = Icons.encodeSvgAsset(svg)
                commit(editingProject.copy(customAssets = editingProject.customAssets + (alias to encoded)))
                updateSelected { node ->
                    when (node.type) {
                        LayerType.SVG -> if (node.svgData.isEmpty()) node.copy(svgData = svg.data, svgW = svg.w, svgH = svg.h) else node
                        LayerType.ICON -> if (node.fxAsset.isBlank()) node.copy(fxAsset = alias) else node
                        else -> node
                    }
                }
                scope.launch { snackbar.showSnackbar("SVG \"$alias\" salvo para uso em fórmulas") }
            } else {
                val s = uri.toString()
                commit(editingProject.copy(customAssets = editingProject.customAssets + (alias to s)))
                updateSelected { it.copy(imageUri = if (it.imageUri.isBlank()) alias else it.imageUri) }
                scope.launch { snackbar.showSnackbar("Ativo \"$alias\" salvo para uso em fórmulas") }
            }
        }
    }

    val powerManager = remember { ctx.getSystemService(PowerManager::class.java) }
    var batteryRestricted by remember { mutableStateOf(false) }
    LaunchedEffect(heroSeenState, replayingHero, reloadTrigger) {
        batteryRestricted = powerManager?.isIgnoringBatteryOptimizations(ctx.packageName) == false
    }
    val showBatteryPrompt = heroSeenState == true && !replayingHero && screen == AppScreen.HUB &&
        batteryRestricted && batteryDismissedState == false

    val reducedMotion = remember(ctx) { PrismMotion.isReducedMotionEnabled(ctx) }
    val expandController = remember(scope) { ViewExpandController(scope) }

    PredictiveBackHandler(enabled = screen == AppScreen.STUDIO) { progress ->
        try {
            progress.collect { event ->
                if (!reducedMotion) expandController.updatePredictiveBack(event.progress)
            }
            expandController.collapseToHub(reducedMotion) { returnToHub() }
        } catch (_: CancellationException) {
            expandController.cancelPredictiveBack(reducedMotion)
        }
    }

    Box(Modifier.fillMaxSize().background(PrismColors.PureBlack)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (reducedMotion || expandController.isVisible) {
                    fadeIn(PrismMotion.fastTween()).togetherWith(fadeOut(PrismMotion.fastTween()))
                } else if (targetState == AppScreen.STUDIO) {
                    (slideInVertically(animationSpec = PrismMotion.TactileIntOffsetSpring) { it / 6 } + fadeIn(PrismMotion.baseTween()))
                        .togetherWith(fadeOut(PrismMotion.fastTween()))
                } else {
                    fadeIn(PrismMotion.baseTween())
                        .togetherWith(slideOutVertically(animationSpec = PrismMotion.TactileIntOffsetSpring) { it / 6 } + fadeOut(PrismMotion.fastTween()))
                }
            },
            label = "ScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                AppScreen.HUB -> ThemeHubScreen(
                    themes = themes,
                    activeThemeId = activeApplied.id,
                    widgets = widgets,
                    activeWidgetId = activeWidget.id,
                    focusThemeId = focusThemeId,
                    onFocusConsumed = { focusThemeId = null },
                    expandController = expandController,
                    deferPreviewRender = deferHubPreviews,
                    env = env,
                    onCreateTheme = { title ->
                        val finalTitle = title.trim().ifBlank { "Default Screen" }
                        val fresh = ProjectStore.newTheme(finalTitle)
                        scope.launch { ProjectStore.upsertTheme(ctx, fresh) }
                        focusThemeId = fresh.id
                        openStudio(fresh)
                    },
                    onCreateWidget = { title ->
                        val finalTitle = title.trim().ifBlank { "Prism Widget" }
                        val fresh = ProjectStore.newWidget(finalTitle)
                        scope.launch { ProjectStore.upsertWidget(ctx, fresh) }
                        focusThemeId = fresh.id
                        openStudio(fresh)
                    },
                    onImportTheme = { importThemePicker.launch(arrayOf("*/*")) },
                    onImportFolderTheme = { importFolderThemePicker.launch(null) },
                    onEditTheme = { openStudio(it) },
                    onEditWidget = { openStudio(it) },
                    onApplyTheme = { applyAndBake(it, forcePicker = false) },
                    onApplyWidget = { applyAndBake(it, forcePicker = false) },
                    onDeleteTheme = { t ->
                        scope.launch {
                            ProjectStore.deleteTheme(ctx, t.id)
                            snackbar.showSnackbar("Tema \"${t.title}\" removido")
                        }
                    },
                    onDeleteWidget = { w ->
                        scope.launch {
                            ProjectStore.deleteWidget(ctx, w.id)
                            snackbar.showSnackbar("Widget \"${w.title}\" removido")
                        }
                    },
                    onReplayHero = {
                        heroIntroSettled = false
                        replayingHero = true
                    },
                    showBatteryPrompt = showBatteryPrompt,
                    onDismissBatteryPrompt = {
                        scope.launch { ProjectStore.setBatteryPromptDismissed(ctx, true) }
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        runCatching { ctx.startActivity(intent) }
                        scope.launch { ProjectStore.setBatteryPromptDismissed(ctx, true) }
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
                    onBack = { expandController.collapseToHub(reducedMotion) { returnToHub() } },
                    onRenameTheme = { newTitle ->
                        val cleanTitle = newTitle.trim().ifBlank {
                            if (editingProject.isWidget) "Prism Widget" else "Default Screen"
                        }
                        commit(editingProject.copy(title = cleanTitle))
                    },
                    onSaveExplicit = {
                        flushSave()
                        scope.launch { snackbar.showSnackbar("\"${editingProject.title}\" salvo") }
                    },
                    onSaveAsNew = { title ->
                        val cloned = editingProject.copy(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            layers = editingProject.layers.map { it.copyDeep() }
                        )
                        commit(cloned)
                        scope.launch { snackbar.showSnackbar("Salvo como \"$title\"") }
                    },
                    onApply = { applyAndBake(editingProject, forcePicker = false) },
                    onExportFile = {
                        val safeName = PrismPackageManager.sanitizeArchiveFileName(editingProject.title)
                        val extName = if (editingProject.isWidget) "$safeName.widget.prism" else "$safeName.prism"
                        exportThemePicker.launch(extName)
                    },
                    onToggleDepth = { imageKey, enabled ->
                        val nextMap = editingProject.depthOverrides.toMutableMap().apply {
                            put(imageKey, enabled)
                        }.toMap()
                        val nextLayers = if (enabled) {
                            val existing = editingProject.layers.firstOrNull { it.type == LayerType.DEPTH }
                            when {
                                existing == null -> {
                                    val depthNode = LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)
                                    val insertIdx = if (editingProject.layers.isNotEmpty()) 1 else 0
                                    editingProject.layers.toMutableList().apply { add(insertIdx, depthNode) }
                                }
                                existing.hidden -> editingProject.layers.mapNode(existing.id) { it.copy(hidden = false) }
                                else -> editingProject.layers
                            }
                        } else editingProject.layers
                        val updated = editingProject.copy(depth = true, depthOverrides = nextMap, layers = nextLayers)
                        commit(updated)
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
                    onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onPickImageFile = { imageFilePicker.launch(arrayOf("image/*", "video/*")) },
                    onClearWallpaper = {
                        val oldUri = editingProject.imageUri
                        val updated = editingProject.copy(folderUri = null, imageUri = null)
                        previewUri = null
                        rawBgBitmap = null
                        rawDepthBitmap = null
                        bgBitmap = null
                        depthBitmap = null
                        renderer.clearShaderCachesForHome()
                        commit(updated)
                        if (oldUri != null) {
                            val appCtx = ctx.applicationContext
                            scope.launch { MediaImporter.deleteIfUnused(appCtx, oldUri, MediaImporter.collectProjectUris(updated)) }
                        }
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
                    onImportSvg = { svgPicker.launch(arrayOf("image/svg+xml", "*/*")) },
                    onPickLayerImage = { layerImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onAddCustomAsset = { alias ->
                        pendingAssetAlias = alias
                        customAssetPicker.launch(arrayOf("image/*", "image/svg+xml", "*/*"))
                    },
                    onBokehDragStateChange = { isDraggingBokeh = it },
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onNextWallpaper = {
                        scope.launch {
                            val next = WallpaperCarousel.currentUri(ctx, editingProject, advance = true)
                            if (next != null) previewUri = next
                        }
                    }
                )
            }
        }

        ViewExpandOverlay(
            controller = expandController,
            reducedMotion = reducedMotion
        )

        if (heroSeenState == null) {
            Box(Modifier.fillMaxSize().background(PrismColors.PureBlack))
        } else if (heroSeenState == false || replayingHero) {
            PrismHeroScreen(
                allowReducedMotionSkip = !replayingHero,
                onIntroSettled = { heroIntroSettled = true },
                onEnterComplete = {
                    heroIntroSettled = true
                    replayingHero = false
                    if (heroSeenState == false) {
                        scope.launch { ProjectStore.setHeroSeen(ctx, true) }
                    }
                }
            )
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
