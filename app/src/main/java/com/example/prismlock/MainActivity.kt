package com.example.prismlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.prismlock.data.Env
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.Dynamic
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.Icons
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.*
import com.example.prismlock.ui.ColorPickerPanel
import com.example.prismlock.ui.InteractiveLockPreview
import com.example.prismlock.ui.PrecisionSliderField
import com.example.prismlock.wallpaper.PrismWallpaperService
import com.example.prismlock.wallpaper.SubjectDepthEngine
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FontManager.init(this)
        EnvProvider.refreshBattery(this)
        EnvProvider.refreshAlarm(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8AB4F8),
                    surface = Color(0xFF11141B),
                    surfaceVariant = Color(0xFF1B202B),
                    background = Color(0xFF0A0C10)
                )
            ) {
                PrismStudio()
            }
        }
    }
}

private enum class Tab(val label: String) {
    LAYERS("Camadas"), CONTENT("Conteúdo"), TYPE("Tipografia"), PAINT("Pintura/Vidro"), POSITION("Posição/SAF")
}

@OptIn(FlowPreview::class)
@Composable
private fun PrismStudio() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { LiquidGlassRenderer() }
    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    var project by remember { mutableStateOf(Project()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val saveFlow = remember { MutableSharedFlow<Project>(extraBufferCapacity = 64) }

    LaunchedEffect(Unit) {
        val loaded = ProjectStore.flow(ctx).first()
        project = loaded
        if (selectedId == null) selectedId = loaded.layers.firstOrNull()?.id
    }

    LaunchedEffect(Unit) {
        saveFlow.debounce(400).collectLatest { pending ->
            ProjectStore.save(ctx, pending)
        }
    }

    val env by EnvProvider.env.collectAsState()
    var tab by remember { mutableStateOf(Tab.LAYERS) }
    var sheetExpanded by remember { mutableStateOf(true) }
    var showAddMenu by remember { mutableStateOf(false) }

    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var depthBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf(FontManager.imported(ctx)) }
    val wallPalette = remember(bgBitmap) { renderer.extractPalette(bgBitmap) }

    var isBaking by remember { mutableStateOf(false) }
    var bakeProgress by remember { mutableStateOf(0f) }
    var bakeStatus by remember { mutableStateOf("Otimizando e compilando pacote nativo...") }

    val currentProject by rememberUpdatedState(project)
    val currentSelectedId by rememberUpdatedState(selectedId)

    LaunchedEffect(
        project.folderUri,
        project.imageUri,
        project.depth,
        project.defaultDepthForNewImages,
        project.depthOverrides,
        previewUri
    ) {
        val uri = previewUri ?: WallpaperCarousel.currentUri(ctx, project, advance = false)
        if (uri != null && previewUri != uri) previewUri = uri
        val bmp = WallpaperCarousel.loadBitmap(ctx, uri, 1080, 2400)
        bgBitmap = bmp
        depthBitmap = SubjectDepthEngine.getOrCreateMaskIfEnabled(ctx, uri, bmp, project)
    }

    LaunchedEffect(project.lat, project.lon, project.city) {
        EnvProvider.refreshWeather(ctx, project.lat, project.lon, project.city)
    }

    fun stage(newProj: Project) {
        project = newProj
    }

    fun flushSave() {
        val snapshot = currentProject
        scope.launch { ProjectStore.save(ctx, snapshot) }
    }

    fun commit(newProj: Project) {
        project = newProj
        saveFlow.tryEmit(newProj)
        scope.launch { ProjectStore.save(ctx, newProj) }
    }

    fun applyAndBake(forcePicker: Boolean = false) {
        if (isBaking) return
        val snapshot = currentProject
        isBaking = true
        bakeProgress = 0.04f
        bakeStatus = "Otimizando e compilando pacote nativo..."
        scope.launch {
            ProjectStore.save(ctx, snapshot)
            WallpaperBaker.bakeAll(ctx, snapshot, renderer) { p, msg ->
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
            commit(project.copy(folderUri = uri.toString()))
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val s = uri.toString()
            previewUri = s
            commit(project.copy(imageUri = s, folderUri = null))
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = FontManager.import(ctx, uri)
            if (f != null) {
                importedFonts = FontManager.imported(ctx)
                updateSelected { it.copy(font = f) }
            }
        }
    }
    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val svg = Icons.importSvg(ctx, uri)
            if (svg != null) {
                val n = LayerNode(name = "SVG", type = LayerType.SVG, svgData = svg.data, svgW = svg.w, svgH = svg.h, w = 140f, h = 140f)
                commit(project.copy(layers = project.layers + n))
                selectedId = n.id
            }
        }
    }

    val selected = project.layers.find(selectedId)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        InteractiveLockPreview(
            renderer = renderer,
            project = project,
            selectedId = selectedId,
            bgBitmap = bgBitmap,
            depthBitmap = depthBitmap,
            env = env,
            onSelectLayer = { selectedId = it },
            onUpdateLayer = ::updateLayer,
            onFlushSave = ::flushSave,
            modifier = Modifier.fillMaxSize()
        )

        // Floating translucent TopBar with subtle dark vertical scrim
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xD906080D), Color(0x7306080D), Color.Transparent)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0xCC11141B), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        "PrismLock",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) {
                        FilledTonalIconButton(
                            onClick = { updateLayer(selected.id) { it.copy(locked = !it.locked) } },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (selected.locked) Color(0xFF8AB4F8) else Color(0xCC1B202B),
                                contentColor = if (selected.locked) Color.Black else Color.White
                            )
                        ) {
                            Icon(if (selected.locked) MIcons.Rounded.Lock else MIcons.Rounded.LockOpen, contentDescription = "Travar camada")
                        }
                    }
                    FilledTonalIconButton(onClick = { sheetExpanded = !sheetExpanded }) {
                        Icon(if (sheetExpanded) MIcons.Rounded.KeyboardArrowDown else MIcons.Rounded.Tune, contentDescription = null)
                    }
                    Button(
                        onClick = { applyAndBake(forcePicker = false) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Aplicar", fontSize = 13.sp)
                    }
                }
            }
        }

        // Bottom contextual KLWP-style Inspector
        if (sheetExpanded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = Color(0xEE11141B),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        divider = {}
                    ) {
                        Tab.entries.forEach { t ->
                            Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label, fontSize = 12.sp) })
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        when (tab) {
                            Tab.LAYERS -> LayersTab(
                                project = project,
                                selectedId = selectedId,
                                onSelect = { selectedId = it },
                                onCommit = ::commit,
                                showAdd = showAddMenu,
                                onToggleAdd = { showAddMenu = !showAddMenu },
                                onImportSvg = { svgPicker.launch(arrayOf("image/svg+xml", "*/*")) }
                            )
                            Tab.CONTENT -> ContentTab(selected, env, ::updateSelected) {
                                svgPicker.launch(arrayOf("image/svg+xml", "*/*"))
                            }
                            Tab.TYPE -> TypeTab(selected, importedFonts, ::updateSelected, ::flushSave) {
                                fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
                            }
                            Tab.PAINT -> PaintTab(project.layers, selected, wallPalette, { selectedId = it }, ::updateSelected, ::flushSave)
                            Tab.POSITION -> PositionAndSafTab(
                                node = selected,
                                project = project,
                                activePreviewUri = previewUri,
                                onUpdateNode = ::updateSelected,
                                onStage = ::stage,
                                onCommit = ::commit,
                                onSaveFinished = ::flushSave,
                                onToggleDepth = { imageKey, enabled ->
                                    val nextMap = currentProject.depthOverrides.toMutableMap().apply {
                                        put(imageKey, enabled)
                                    }.toMap()
                                    val updated = currentProject.copy(depthOverrides = nextMap)
                                    project = updated
                                    saveFlow.tryEmit(updated)
                                },
                                onSelectPreview = { uriStr, idx ->
                                    previewUri = uriStr
                                    scope.launch {
                                        if (project.folderUri != null) {
                                            ProjectStore.setIndex(ctx, idx, uriStr)
                                        } else {
                                            ProjectStore.setCurrentWallpaperUri(ctx, uriStr)
                                        }
                                    }
                                },
                                onPickFolder = { folderPicker.launch(null) },
                                onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                                onApplyWallpaper = { applyAndBake(forcePicker = true) }
                            )
                        }
                    }
                }
            }
        }

        if (isBaking) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xF5141923),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Otimizando e compilando pacote nativo...",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        LinearProgressIndicator(
                            progress = { bakeProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF8AB4F8),
                            trackColor = Color(0xFF252D3D)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bakeStatus,
                                color = Color(0xFF9FB0C7),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(bakeProgress * 100).roundToInt().coerceIn(0, 100)}%",
                                color = Color(0xFF8AB4F8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayersTab(
    project: Project,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCommit: (Project) -> Unit,
    showAdd: Boolean,
    onToggleAdd: () -> Unit,
    onImportSvg: () -> Unit
) {
    val flat = remember(project.layers) { project.layers.flatten() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onToggleAdd, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(MIcons.Rounded.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adicionar", fontSize = 12.sp)
            }
            Presets.all.forEach { (name, builder) ->
                OutlinedButton(
                    onClick = {
                        val ls = builder()
                        onCommit(project.copy(layers = ls))
                        onSelect(ls.firstOrNull()?.id)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text(name, fontSize = 11.sp) }
            }
        }
        if (showAdd) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fun add(node: LayerNode) {
                    val parent = selectedId?.let { project.layers.find(it) }?.takeIf { it.type == LayerType.STACK }
                    val updated = if (parent != null) {
                        project.layers.mapNode(parent.id) { it.copy(children = it.children + node) }
                    } else project.layers + node
                    onCommit(project.copy(layers = updated))
                    onSelect(node.id)
                    onToggleAdd()
                }
                AssistChip(onClick = { add(LayerNode(name = "Texto", text = "{HH}:{mm}", size = 120f)) }, label = { Text("Texto/Código") })
                AssistChip(onClick = { add(LayerNode(name = "Pilha V", type = LayerType.STACK, dir = Dir.VERTICAL, spacing = 10f)) }, label = { Text("Pilha Vertical") })
                AssistChip(onClick = { add(LayerNode(name = "Pilha H", type = LayerType.STACK, dir = Dir.HORIZONTAL, spacing = 12f)) }, label = { Text("Pilha Horizontal") })
                AssistChip(onClick = { add(LayerNode(name = "Forma", type = LayerType.SHAPE, shape = ShapeKind.RECT, w = 300f, h = 120f, corner = 32f)) }, label = { Text("Forma") })
                AssistChip(onClick = { add(LayerNode(name = "Barra", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 320f, h = 18f, corner = 9f, value = "bat")) }, label = { Text("Progresso") })
                AssistChip(onClick = { add(LayerNode(name = "Ícone", type = LayerType.ICON, icon = "weather", w = 72f, h = 72f)) }, label = { Text("Ícone") })
                AssistChip(onClick = { onToggleAdd(); onImportSvg() }, label = { Text("SVG") })
                AssistChip(onClick = { add(LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)) }, label = { Text("Profundidade 3D") })
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(flat, key = { it.first.id }) { (node, depth) ->
                val sel = node.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color(0xFF23324D) else Color(0xFF171B24))
                        .clickable { onSelect(node.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(node.name, modifier = Modifier.weight(1f), color = Color.White, fontSize = 13.sp, maxLines = 1)
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.move(node.id, -1))) }, Modifier.size(28.dp)) {
                        Icon(MIcons.Rounded.ArrowUpward, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.move(node.id, 1))) }, Modifier.size(28.dp)) {
                        Icon(MIcons.Rounded.ArrowDownward, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.mapNode(node.id) { it.copy(hidden = !it.hidden) })) }, Modifier.size(28.dp)) {
                        Icon(if (node.hidden) MIcons.Rounded.VisibilityOff else MIcons.Rounded.Visibility, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.mapNode(node.id) { it.copy(locked = !it.locked) })) }, Modifier.size(28.dp)) {
                        Icon(if (node.locked) MIcons.Rounded.Lock else MIcons.Rounded.LockOpen, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.insertAfter(node.id, node.copyDeep()))) }, Modifier.size(28.dp)) {
                        Icon(MIcons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        onCommit(project.copy(layers = project.layers.removeNode(node.id)))
                        if (selectedId == node.id) onSelect(null)
                    }, Modifier.size(28.dp)) {
                        Icon(MIcons.Rounded.Delete, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTab(node: LayerNode?, env: Env, update: ((LayerNode) -> LayerNode) -> Unit, onImportSvg: () -> Unit) {
    if (node == null) { Text("Selecione uma camada na tela ou na aba Camadas", color = Color.Gray); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = node.name, onValueChange = { v -> update { it.copy(name = v) } }, label = { Text("Nome da camada") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (node.type == LayerType.TEXT) {
            OutlinedTextField(value = node.text, onValueChange = { v -> update { it.copy(text = v) } }, label = { Text("Texto / Fórmula Dinâmica") }, modifier = Modifier.fillMaxWidth())
            Text("Resultado ao vivo: ${Dynamic.eval(node.text, env, LocalDateTime.now())}", fontSize = 12.sp, color = Color(0xFF8AB4F8))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dynamic.tags.forEach { t ->
                    AssistChip(onClick = { update { it.copy(text = it.text + t) } }, label = { Text(t, fontSize = 11.sp) })
                }
            }
        }
        if (node.type == LayerType.SHAPE) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShapeKind.entries.forEach { k ->
                    FilterChip(selected = node.shape == k, onClick = { update { it.copy(shape = k) } }, label = { Text(k.name) })
                }
            }
            if (node.shape == ShapeKind.BAR || node.shape == ShapeKind.RING) {
                OutlinedTextField(value = node.value, onValueChange = { v -> update { it.copy(value = v) } }, label = { Text("Variável 0..100 (bat, dayprog, hourprog)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (node.type == LayerType.ICON) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icons.names.forEach { ic ->
                    FilterChip(selected = node.icon == ic, onClick = { update { it.copy(icon = ic) } }, label = { Text(ic) })
                }
            }
        }
        if (node.type == LayerType.SVG) {
            Button(onClick = onImportSvg) { Text("Substituir arquivo SVG") }
        }
    }
}

@Composable
private fun TypeTab(
    node: LayerNode?,
    importedFonts: List<String>,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onImportFont: () -> Unit
) {
    if (node == null) { Text("Selecione uma camada", color = Color.Gray); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (node.type == LayerType.TEXT) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onImportFont) { Text("+ Fonte TTF/OTF", fontSize = 11.sp) }
                (FontManager.systemFamilies + importedFonts).forEach { f ->
                    FilterChip(selected = node.font == f, onClick = { update { it.copy(font = f) }; onSaveFinished() }, label = { Text(FontManager.label(f), fontSize = 11.sp) })
                }
            }
            PrecisionSliderField("Tamanho", node.size, 18f..420f, "px", 0, onSaveFinished) { v -> update { it.copy(size = v) } }
            PrecisionSliderField("Peso", node.weight.toFloat(), 100f..900f, "", 0, onSaveFinished) { v -> update { it.copy(weight = v.roundToInt()) } }
            PrecisionSliderField("Elasticidade", node.stretch, 1f..3f, "x", 2, onSaveFinished) { v -> update { it.copy(stretch = v) } }
            PrecisionSliderField("Comp. Traço", node.strokeCompensation, 0f..1.2f, "x", 2, onSaveFinished) { v -> update { it.copy(strokeCompensation = v) } }
            PrecisionSliderField("Kerning", node.letterSpacing, -0.15f..0.6f, "em", 2, onSaveFinished) { v -> update { it.copy(letterSpacing = v) } }
            PrecisionSliderField("Entrelinha", node.lineSpacing, -0.6f..1f, "", 2, onSaveFinished) { v -> update { it.copy(lineSpacing = v) } }
            FontManager.axes(node.font).forEach { ax ->
                val cur = node.axes[ax.tag] ?: ax.def
                PrecisionSliderField("Eixo ${ax.tag}", cur, ax.min..ax.max, "", 1, onSaveFinished) { v -> update { it.copy(axes = it.axes + (ax.tag to v)) } }
            }
        }
        if (node.type == LayerType.STACK) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dir.entries.forEach { d -> FilterChip(selected = node.dir == d, onClick = { update { it.copy(dir = d) }; onSaveFinished() }, label = { Text(d.name) }) }
            }
            PrecisionSliderField("Espaçamento", node.spacing, -60f..160f, "px", 0, onSaveFinished) { v -> update { it.copy(spacing = v) } }
        }
        if (node.type == LayerType.SHAPE || node.type == LayerType.ICON || node.type == LayerType.SVG) {
            PrecisionSliderField("Largura", node.w, 16f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(w = v) } }
            PrecisionSliderField("Altura", node.h, 2f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(h = v) } }
            PrecisionSliderField("Canto", node.corner, 0f..300f, "px", 0, onSaveFinished) { v -> update { it.copy(corner = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Align.entries.forEach { a -> FilterChip(selected = node.align == a, onClick = { update { it.copy(align = a) }; onSaveFinished() }, label = { Text(a.name, fontSize = 11.sp) }) }
        }
        PrecisionSliderField("Escala", node.scale, 0.25f..4f, "x", 2, onSaveFinished) { v -> update { it.copy(scale = v) } }
        PrecisionSliderField("Rotação", node.rotation, -180f..180f, "°", 1, onSaveFinished) { v -> update { it.copy(rotation = v) } }
    }
}

@Composable
private fun PaintTab(
    allLayers: List<LayerNode>,
    node: LayerNode?,
    wallPalette: List<Int>,
    onSelect: (String) -> Unit,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit
) {
    val paintable = remember(allLayers) { allLayers.flatten().map { it.first }.filter { it.type != LayerType.DEPTH } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paintable.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                paintable.forEach { item ->
                    FilterChip(selected = item.id == node?.id, onClick = { onSelect(item.id) }, label = { Text(item.name, fontSize = 11.sp) })
                }
            }
        }
        if (node == null) { Text("Selecione uma camada", color = Color.Gray); return@Column }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Fill.SOLID to "Sólido",
                Fill.GRADIENT to "Gradiente",
                Fill.GLASS to "Blur Clássico",
                Fill.CRYSTAL to "Cristal Óptico",
                Fill.HOLLOW to "Vazado"
            ).forEach { (f, label) ->
                FilterChip(selected = node.fill == f, onClick = { update { it.copy(fill = f) }; onSaveFinished() }, label = { Text(label, fontSize = 11.sp) })
            }
        }

        if (node.fill == Fill.SOLID || node.fill == Fill.GRADIENT || node.fill == Fill.HOLLOW) {
            ColorPickerPanel("Cor Principal", node.color, wallPalette) { c -> update { it.copy(color = c) } }
        }
        if (node.fill == Fill.GRADIENT || (node.fill == Fill.HOLLOW && node.color2 != node.color)) {
            ColorPickerPanel("Cor Secundária", node.color2, wallPalette) { c -> update { it.copy(color2 = c) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientKind.entries.forEach { gk ->
                    FilterChip(selected = node.gradientKind == gk, onClick = { update { it.copy(gradientKind = gk) }; onSaveFinished() }, label = { Text(gk.name) })
                }
            }
            if (node.gradientKind == GradientKind.LINEAR) {
                PrecisionSliderField("Ângulo Grad", node.gradientAngle, 0f..360f, "°", 0, onSaveFinished) { v -> update { it.copy(gradientAngle = v) } }
            }
        }
        if (node.fill == Fill.GLASS) {
            PrecisionSliderField("Blur Gauss", node.glass.blur, 0f..80f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
            PrecisionSliderField("Saturação", node.glass.saturation, 1f..1.8f, "x", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(saturation = v)) } }
            PrecisionSliderField("Granulação", node.glass.grain, 0f..0.15f, "", 3, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(grain = v)) } }
            PrecisionSliderField("Borda Suave", node.glass.rim, 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
            ColorPickerPanel("Tint Translúcido", node.glass.tint, wallPalette) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
        }
        if (node.fill == Fill.CRYSTAL) {
            PrecisionSliderField("Refração", node.glass.refraction, 0f..100f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(refraction = v)) } }
            PrecisionSliderField("Chanfro", node.glass.bevelWidth, 2f..60f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(bevelWidth = v)) } }
            PrecisionSliderField("Dispersão RGB", node.glass.chromaticAberration * 1000f, 0f..20f, "‰", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(chromaticAberration = v / 1000f)) } }
            PrecisionSliderField("Brilho Fresnel", node.glass.rim, 0f..1.5f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
            PrecisionSliderField("Ângulo Luz", node.glass.lightAngle, 0f..360f, "°", 0, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(lightAngle = v)) } }
            PrecisionSliderField("Jateamento", node.glass.frostAmount, 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(frostAmount = v)) } }
            if (node.glass.frostAmount > 0.01f) {
                PrecisionSliderField("Blur Jato", node.glass.blur, 0f..80f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
            }
            ColorPickerPanel("Tint Cristal", node.glass.tint, wallPalette) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
        }

        PrecisionSliderField("Opacidade", node.opacity, 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(opacity = v) } }

        Text("Contorno / Borda", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
        PrecisionSliderField("Espessura", node.strokeWidth, 0f..24f, "px", 1, onSaveFinished) { v -> update { it.copy(strokeWidth = v) } }
        if (node.strokeWidth > 0f || node.fill == Fill.HOLLOW) {
            ColorPickerPanel("Cor Contorno", node.strokeColor, wallPalette) { c -> update { it.copy(strokeColor = c) } }
        }

        Text("Sombra Projetada", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
        PrecisionSliderField("Raio Sombra", node.shadowRadius, 0f..50f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowRadius = v) } }
        if (node.shadowRadius > 0f) {
            PrecisionSliderField("Sombra X", node.shadowDx, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDx = v) } }
            PrecisionSliderField("Sombra Y", node.shadowDy, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDy = v) } }
            ColorPickerPanel("Cor Sombra", node.shadowColor, wallPalette) { c -> update { it.copy(shadowColor = c) } }
        }

        Text("Modo de Mesclagem", fontSize = 12.sp, color = Color.LightGray)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Blend.entries.forEach { b ->
                FilterChip(selected = node.blend == b, onClick = { update { it.copy(blend = b) }; onSaveFinished() }, label = { Text(b.name, fontSize = 10.sp) })
            }
        }
    }
}

@Composable
private fun PositionAndSafTab(
    node: LayerNode?,
    project: Project,
    activePreviewUri: String?,
    onUpdateNode: ((LayerNode) -> LayerNode) -> Unit,
    onStage: (Project) -> Unit,
    onCommit: (Project) -> Unit,
    onSaveFinished: () -> Unit,
    onToggleDepth: (String, Boolean) -> Unit,
    onSelectPreview: (String, Int) -> Unit,
    onPickFolder: () -> Unit,
    onPickImage: () -> Unit,
    onApplyWallpaper: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cityInput by remember(project.city) { mutableStateOf(project.city) }
    val galleryItems by produceState<List<Pair<String, String>>>(
        initialValue = emptyList(),
        project.folderUri,
        project.imageUri
    ) {
        value = withContext(Dispatchers.IO) {
            val uris = if (project.folderUri != null) {
                WallpaperCarousel.listImages(ctx, project.folderUri).map { it.toString() }
            } else {
                listOfNotNull(project.imageUri)
            }
            uris.map { uriStr -> uriStr to WallpaperCarousel.imageKey(ctx, uriStr) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (node != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) {
                    PrecisionSliderField("Posição X", node.x * 100f, 0f..100f, "%", 1, onSaveFinished) { v -> onUpdateNode { it.copy(x = v / 100f) } }
                }
                OutlinedButton(onClick = { onUpdateNode { it.copy(x = (it.x - 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("-1px", fontSize = 10.sp) }
                OutlinedButton(onClick = { onUpdateNode { it.copy(x = (it.x + 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("+1px", fontSize = 10.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) {
                    PrecisionSliderField("Posição Y", node.y * 100f, 0f..100f, "%", 1, onSaveFinished) { v -> onUpdateNode { it.copy(y = v / 100f) } }
                }
                OutlinedButton(onClick = { onUpdateNode { it.copy(y = (it.y - 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("-1px", fontSize = 10.sp) }
                OutlinedButton(onClick = { onUpdateNode { it.copy(y = (it.y + 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("+1px", fontSize = 10.sp) }
            }
            Text("Âncora da Camada", fontSize = 12.sp, color = Color.LightGray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Anchor.entries.forEach { a ->
                    FilterChip(selected = node.anchor == a, onClick = { onUpdateNode { it.copy(anchor = a) }; onSaveFinished() }, label = { Text(a.name, fontSize = 10.sp) })
                }
            }
            Text("Regra de Visibilidade", fontSize = 12.sp, color = Color.LightGray)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cond.entries.forEach { c ->
                    FilterChip(selected = node.cond == c, onClick = { onUpdateNode { it.copy(cond = c) }; onSaveFinished() }, label = { Text(c.name, fontSize = 10.sp) })
                }
            }
            HorizontalDivider(color = Color(0xFF232936))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) { Text("Pasta Carrossel SAF", fontSize = 12.sp) }
            OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) { Text("Foto Única", fontSize = 12.sp) }
        }
        if (galleryItems.isNotEmpty()) {
            Text(
                if (project.folderUri != null) "Fotos do Carrossel (${galleryItems.size}) — toque para testar, canto para 3D" else "Foto Ativa — toque no canto para 3D",
                fontSize = 11.sp, color = Color(0xFF9FB0C7)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(148.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(galleryItems, key = { _, item -> item.first }) { idx, (uriStr, imageKey) ->
                    val thumb by produceState<Bitmap?>(initialValue = null, uriStr) {
                        value = WallpaperCarousel.loadThumbnail(ctx, uriStr)
                    }
                    val isSelected = uriStr == activePreviewUri
                    val is3dOn = project.isDepthEnabledFor(imageKey)
                    Box(
                        modifier = Modifier
                            .width(86.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A202C))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF8AB4F8) else Color(0xFF2D3648),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onSelectPreview(uriStr, idx) }
                        ) {
                            thumb?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(2f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (is3dOn) Color(0xE62563EB) else Color(0xCC2A303C))
                                .border(
                                    width = 1.dp,
                                    color = if (is3dOn) Color(0xFF93C5FD) else Color(0xFF4B5563),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggleDepth(imageKey, !is3dOn) }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (is3dOn) "3D ON" else "3D OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (is3dOn) Color.White else Color(0xFFD1D5DB)
                            )
                        }
                        if (project.folderUri != null) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trocar ao acender tela", fontSize = 13.sp, color = Color.White)
            Switch(checked = project.changeOnWake, onCheckedChange = { onCommit(project.copy(changeOnWake = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trocar com toque duplo", fontSize = 13.sp, color = Color.White)
            Switch(checked = project.changeOnDoubleTap, onCheckedChange = { onCommit(project.copy(changeOnDoubleTap = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Profundidade 3D (ML Kit) + trava 35%", fontSize = 13.sp, color = Color.White)
            Switch(checked = project.depth, onCheckedChange = { onCommit(project.copy(depth = it)) })
        }
        PrecisionSliderField("Intervalo", project.intervalMin.toFloat(), 0f..120f, "m", 0, onSaveFinished) { v -> onStage(project.copy(intervalMin = v.roundToInt())) }
        PrecisionSliderField("Escurecer", project.dim * 100f, 0f..70f, "%", 0, onSaveFinished) { v -> onStage(project.copy(dim = v / 100f)) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = cityInput, onValueChange = { cityInput = it }, label = { Text("Cidade (Open-Meteo)") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    val g = EnvProvider.geocode(cityInput)
                    if (g != null) onCommit(project.copy(lat = g.first, lon = g.second, city = g.third))
                }
            }) { Text("Buscar") }
        }
        Button(onClick = onApplyWallpaper, modifier = Modifier.fillMaxWidth()) {
            Text("Definir como Papel de Parede ao Vivo")
        }
        Text(
            "Dica Samsung: no Good Lock / LockStar, oculte o relógio nativo do sistema para exibir apenas suas camadas.",
            fontSize = 11.sp, color = Color.Gray
        )
    }
}
