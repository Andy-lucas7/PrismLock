package com.example.prismlock.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.prismlock.data.Env
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.engine.Dynamic
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.Icons
import com.example.prismlock.engine.KustomFormulaEngine
import com.example.prismlock.engine.LaunchableApp
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.TouchActionExecutor
import com.example.prismlock.engine.WallpaperBaker
import com.example.prismlock.model.Align
import com.example.prismlock.model.Anchor
import com.example.prismlock.model.Blend
import com.example.prismlock.model.Cond
import com.example.prismlock.model.Dir
import com.example.prismlock.model.Fill
import com.example.prismlock.model.Glass
import com.example.prismlock.model.GlobalType
import com.example.prismlock.model.GlobalVar
import com.example.prismlock.model.GradientKind
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Presets
import com.example.prismlock.model.Project
import com.example.prismlock.model.ShapeKind
import com.example.prismlock.model.TouchAction
import com.example.prismlock.model.TouchActionType
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.find
import com.example.prismlock.model.flatten
import com.example.prismlock.model.insertAfter
import com.example.prismlock.model.mapNode
import com.example.prismlock.model.move
import com.example.prismlock.model.parentOf
import com.example.prismlock.model.removeNode
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

private enum class StudioTab(val label: String) {
    BACKGROUND("Fundo & 3D"),
    LAYERS("Camadas"),
    CONTENT("Conteúdo"),
    TYPE("Tipografia"),
    PAINT("Vidro / Cor"),
    TOUCH("Toque"),
    GLOBALS("Globais")
}

private fun anchorLabel(a: Anchor): String = when (a) {
    Anchor.TOP_LEFT -> "Topo Esquerda"
    Anchor.TOP_CENTER -> "Topo Centro"
    Anchor.TOP_RIGHT -> "Topo Direita"
    Anchor.CENTER_LEFT -> "Centro Esquerda"
    Anchor.CENTER -> "Centro"
    Anchor.CENTER_RIGHT -> "Centro Direita"
    Anchor.BOTTOM_LEFT -> "Base Esquerda"
    Anchor.BOTTOM_CENTER -> "Base Centro"
    Anchor.BOTTOM_RIGHT -> "Base Direita"
}

private fun condLabel(c: Cond): String = when (c) {
    Cond.ALWAYS -> "Sempre"
    Cond.LOCK_ONLY -> "Apenas Bloqueio"
    Cond.HOME_ONLY -> "Apenas Início"
    Cond.CHARGING -> "Carregando"
    Cond.NOT_CHARGING -> "Na Bateria"
    Cond.LOW_BATTERY -> "Bateria Baixa"
    Cond.DAY -> "Durante o Dia"
    Cond.NIGHT -> "À Noite"
}

private fun blendLabel(b: Blend): String = when (b) {
    Blend.NORMAL -> "Normal"
    Blend.MULTIPLY -> "Multiplicar"
    Blend.SCREEN -> "Tela"
    Blend.OVERLAY -> "Sobrepor"
    Blend.DARKEN -> "Escurecer"
    Blend.LIGHTEN -> "Clarear"
    Blend.COLOR_DODGE -> "Subexposição de Cor"
    Blend.COLOR_BURN -> "Superexposição de Cor"
    Blend.SOFT_LIGHT -> "Luz Suave"
    Blend.HARD_LIGHT -> "Luz Dura"
    Blend.DIFFERENCE -> "Diferença"
    Blend.EXCLUSION -> "Exclusão"
    Blend.PLUS -> "Adicionar"
    Blend.HUE -> "Matiz"
    Blend.SATURATION -> "Saturação"
    Blend.COLOR -> "Cor"
    Blend.LUMINOSITY -> "Luminosidade"
}

private fun gradientLabel(g: GradientKind): String = when (g) {
    GradientKind.LINEAR -> "Linear"
    GradientKind.RADIAL -> "Radial"
    GradientKind.SWEEP -> "Circular"
}

private fun shapeLabel(s: ShapeKind): String = when (s) {
    ShapeKind.RECT -> "Retângulo"
    ShapeKind.CIRCLE -> "Círculo"
    ShapeKind.LINE -> "Linha"
    ShapeKind.BAR -> "Barra"
    ShapeKind.RING -> "Anel"
}

private fun dirLabel(d: Dir): String = when (d) {
    Dir.VERTICAL -> "Vertical"
    Dir.HORIZONTAL -> "Horizontal"
}

private fun alignLabel(a: Align): String = when (a) {
    Align.START -> "Esquerda"
    Align.CENTER -> "Centro"
    Align.END -> "Direita"
}

private fun iconLabel(ic: String): String = when (ic) {
    "weather" -> "Clima Auto"
    "sun" -> "Sol"
    "moon" -> "Lua"
    "cloud" -> "Nuvem"
    "cloud_sun" -> "Parcial"
    "rain" -> "Chuva"
    "snow" -> "Neve"
    "storm" -> "Tempestade"
    "fog" -> "Neblina"
    "battery" -> "Bateria"
    "bolt" -> "Raio"
    "alarm" -> "Alarme"
    "lock" -> "Cadeado"
    "unlock" -> "Aberto"
    "music" -> "Música"
    "heart" -> "Coração"
    "steps" -> "Passos"
    "location" -> "Local"
    "star" -> "Estrela"
    else -> ic
}

private fun fontDisplayLabel(f: String): String = when (f) {
    "sans-serif" -> "Sans Padrão"
    "sans-serif-light" -> "Sans Leve"
    "sans-serif-condensed" -> "Sans Condensada"
    "sans-serif-medium" -> "Sans Média"
    "serif" -> "Serifada"
    "monospace" -> "Monoespaçada"
    "cursive" -> "Cursiva"
    "casual" -> "Casual"
    "serif-monospace" -> "Mono Serifada"
    else -> FontManager.label(f)
}

@Composable
fun StudioScreen(
    renderer: LiquidGlassRenderer,
    project: Project,
    selectedId: String?,
    bgBitmap: Bitmap?,
    depthBitmap: Bitmap?,
    activePreviewUri: String?,
    importedFonts: List<String>,
    wallPalette: List<Int>,
    env: Env,
    onSelectLayer: (String?) -> Unit,
    onUpdateLayer: (String, (LayerNode) -> LayerNode) -> Unit,
    onUpdateSelected: ((LayerNode) -> LayerNode) -> Unit,
    onStageProject: (Project) -> Unit,
    onCommitProject: (Project) -> Unit,
    onFlushSave: () -> Unit,
    onBack: () -> Unit,
    onRenameTheme: (String) -> Unit,
    onSaveExplicit: () -> Unit,
    onSaveAsNew: (String) -> Unit,
    onApply: () -> Unit,
    onExportFile: () -> Unit,
    onToggleDepth: (String, Boolean) -> Unit,
    onSelectPreview: (String, Int) -> Unit,
    onPickFolder: () -> Unit,
    onPickImage: () -> Unit,
    onClearWallpaper: () -> Unit = {},
    onImportFont: () -> Unit,
    hasFontFolder: Boolean = false,
    onPickFontFolder: () -> Unit = {},
    onRefreshFontFolder: (Boolean) -> Unit = {},
    onImportSvg: () -> Unit,
    onNextWallpaper: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var tab by remember(project.id) { mutableStateOf(StudioTab.BACKGROUND) }
    var sheetExpanded by remember(project.id) { mutableStateOf(true) }
    var showAddMenu by remember(project.id) { mutableStateOf(false) }
    var showOverflowMenu by remember(project.id) { mutableStateOf(false) }
    var showRenameDialog by remember(project.id) { mutableStateOf(false) }
    var showSaveAsDialog by remember(project.id) { mutableStateOf(false) }
    var showPresetsModal by remember(project.id) { mutableStateOf(false) }
    var pendingPreset by remember(project.id) { mutableStateOf<Pair<String, () -> List<LayerNode>>?>(null) }
    var isActivelyDraggingLayer by remember(project.id) { mutableStateOf(false) }
    var previewLocked by remember(project.id) { mutableStateOf(true) }
    var testTouchMode by remember(project.id) { mutableStateOf(false) }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (isActivelyDraggingLayer) 0f else 1f,
        animationSpec = tween(180),
        label = "EditorChromeAlpha"
    )

    val selected = project.layers.find(selectedId)

    fun runLayerTouch(targetNode: LayerNode) {
        TouchActionExecutor.execute(
            ctx = ctx,
            node = targetNode,
            project = project,
            env = env.copy(locked = previewLocked),
            now = LocalDateTime.now(),
            onMutateProject = { updated -> onCommitProject(updated) },
            onNextWallpaper = { onNextWallpaper?.invoke() },
            onFeedback = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        )
    }

    Box(Modifier.fillMaxSize().background(PrismColors.PureBlack)) {
        InteractiveLockPreview(
            renderer = renderer,
            project = project,
            selectedId = selectedId,
            bgBitmap = bgBitmap,
            depthBitmap = depthBitmap,
            activePreviewUri = activePreviewUri,
            env = env,
            previewLocked = previewLocked,
            testTouchMode = testTouchMode && tab == StudioTab.TOUCH,
            onTriggerLayerTouch = ::runLayerTouch,
            onSelectLayer = onSelectLayer,
            onUpdateLayer = onUpdateLayer,
            onFlushSave = onFlushSave,
            onDragStateChange = { isActivelyDraggingLayer = it },
            modifier = Modifier.fillMaxSize()
        )

        // Floating monochrome TopBar with subtle dark gradient scrim
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xDD000000), Color(0x6E000000), Color.Transparent)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back to Theme Hub + Theme Name Pill
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .frostedChrome(shape = CircleShape, elevation = 8.dp)
                    ) {
                        Icon(
                            MIcons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Voltar ao Carrossel",
                            tint = PrismColors.PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .frostedChrome(shape = RoundedCornerShape(18.dp), elevation = 6.dp)
                            .clickable { showRenameDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = project.title,
                                color = PrismColors.PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                MIcons.Rounded.Edit,
                                contentDescription = "Renomear Tela",
                                tint = PrismColors.MutedZinc,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Right: Layer Lock, Collapse inspector, 3-dots menu
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selected != null) {
                        IconButton(
                            onClick = { onUpdateLayer(selected.id) { it.copy(locked = !it.locked) } },
                            modifier = Modifier
                                .size(40.dp)
                                .frostedChrome(
                                    shape = CircleShape,
                                    elevation = 8.dp,
                                    borderColor = if (selected.locked) PrismColors.PureWhite else PrismColors.GlassBorder
                                )
                                .background(if (selected.locked) PrismColors.PureWhite else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                if (selected.locked) MIcons.Rounded.Lock else MIcons.Rounded.LockOpen,
                                contentDescription = "Travar camada",
                                tint = if (selected.locked) PrismColors.PureBlack else PrismColors.PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { sheetExpanded = !sheetExpanded },
                        modifier = Modifier
                            .size(40.dp)
                            .frostedChrome(shape = CircleShape, elevation = 8.dp)
                    ) {
                        Icon(
                            if (sheetExpanded) MIcons.Rounded.KeyboardArrowDown else MIcons.Rounded.Tune,
                            contentDescription = if (sheetExpanded) "Recolher painel" else "Abrir painel",
                            tint = PrismColors.PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .frostedChrome(shape = CircleShape, elevation = 8.dp)
                        ) {
                            Icon(
                                MIcons.Rounded.MoreVert,
                                contentDescription = "Ações do Tema",
                                tint = PrismColors.PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = Color(0xF00D0D0F),
                            modifier = Modifier.border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(18.dp))
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.Edit, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Renomear Tela", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.Save, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Salvar", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onSaveExplicit()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.AutoMirrored.Rounded.NoteAdd, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Salvar como Novo Tema", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    showSaveAsDialog = true
                                }
                            )
                            HorizontalDivider(color = PrismColors.GlassBorder)
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.CheckCircle, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Aplicar na Tela de Bloqueio", color = PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showOverflowMenu = false
                                    onApply()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(MIcons.Rounded.FileUpload, null, tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp)) },
                                text = { Text("Exportar Pacote (.prism)", color = PrismColors.PureWhite, fontSize = 13.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onExportFile()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Frosted Monochrome Inspector Sheet
        AnimatedVisibility(
            visible = sheetExpanded,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = chromeAlpha }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .frostedChrome(
                        shape = RoundedCornerShape(26.dp),
                        dense = true,
                        elevation = 20.dp
                    )
                    .padding(bottom = 8.dp)
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = PrismColors.PureWhite,
                    edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = PrismColors.GlassBorder) },
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tab.ordinal, matchContentSize = true),
                            height = 2.dp,
                            color = PrismColors.PureWhite
                        )
                    }
                ) {
                    StudioTab.entries.forEach { t ->
                        val active = tab == t
                        Tab(
                            selected = active,
                            onClick = { tab = t },
                            text = {
                                Text(
                                    text = t.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (active) PrismColors.PureWhite else PrismColors.MutedZinc
                                )
                            }
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(274.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    when (tab) {
                        StudioTab.BACKGROUND -> PositionAndSafTab(
                            node = selected,
                            project = project,
                            activePreviewUri = activePreviewUri,
                            onSelectLayer = onSelectLayer,
                            onUpdateNode = onUpdateSelected,
                            onStage = onStageProject,
                            onCommit = onCommitProject,
                            onSaveFinished = onFlushSave,
                            onToggleDepth = onToggleDepth,
                            onSelectPreview = onSelectPreview,
                            onPickFolder = onPickFolder,
                            onPickImage = onPickImage,
                            onClearWallpaper = onClearWallpaper,
                            onApplyWallpaper = onApply
                        )
                        StudioTab.LAYERS -> LayersTab(
                            project = project,
                            env = env,
                            selectedId = selectedId,
                            onSelect = onSelectLayer,
                            onCommit = onCommitProject,
                            showAdd = showAddMenu,
                            onToggleAdd = { showAddMenu = !showAddMenu },
                            onOpenPresets = { showPresetsModal = true },
                            onImportSvg = onImportSvg
                        )
                        StudioTab.CONTENT -> ContentTab(
                            node = selected,
                            project = project,
                            env = env.copy(locked = previewLocked),
                            wallPalette = wallPalette,
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave,
                            onImportSvg = onImportSvg
                        )
                        StudioTab.TYPE -> TypeTab(
                            node = selected,
                            importedFonts = importedFonts,
                            hasFontFolder = hasFontFolder,
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave,
                            onImportFont = onImportFont,
                            onPickFontFolder = onPickFontFolder,
                            onRefreshFontFolder = onRefreshFontFolder
                        )
                        StudioTab.PAINT -> PaintTab(
                            allLayers = project.layers,
                            colorGlobals = project.globals.filter { it.type == GlobalType.COLOR },
                            node = selected,
                            wallPalette = wallPalette,
                            onSelect = { onSelectLayer(it) },
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave
                        )
                        StudioTab.TOUCH -> TouchTab(
                            node = selected,
                            project = project,
                            previewLocked = previewLocked,
                            testTouchMode = testTouchMode,
                            onTogglePreviewLocked = { previewLocked = !previewLocked },
                            onToggleTestTouch = { testTouchMode = !testTouchMode },
                            onTestAction = ::runLayerTouch,
                            onOpenGlobalsTab = { tab = StudioTab.GLOBALS },
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave
                        )
                        StudioTab.GLOBALS -> GlobalsTab(
                            project = project,
                            wallPalette = wallPalette,
                            onSelectLayer = onSelectLayer,
                            onUpdateLayer = onUpdateLayer,
                            onStageProject = onStageProject,
                            onCommitProject = onCommitProject,
                            onSaveFinished = onFlushSave
                        )
                    }
                }
            }
        }

        // Rename Current Screen dialog
        if (showRenameDialog) {
            FocusedInputDialog(
                title = "Renomear Tela",
                initialValue = project.title,
                placeholder = "Default Screen",
                exampleHint = "Ex: Minimal Cristal, Relógio Editorial",
                confirmLabel = "Salvar Nome",
                onDismiss = { showRenameDialog = false },
                onConfirm = { raw ->
                    onRenameTheme(raw.trim().ifBlank { "Default Screen" })
                    showRenameDialog = false
                }
            )
        }

        // Save as New Theme dialog
        if (showSaveAsDialog) {
            FocusedInputDialog(
                title = "Salvar como Novo Tema",
                initialValue = "${project.title} (Cópia)",
                placeholder = "Novo Tema",
                exampleHint = "Ex: Minha Tela V2",
                confirmLabel = "Salvar",
                onDismiss = { showSaveAsDialog = false },
                onConfirm = { raw ->
                    onSaveAsNew(raw.trim().ifEmpty { "Novo Tema" })
                    showSaveAsDialog = false
                }
            )
        }

        // Separate Presets Modal Sheet
        if (showPresetsModal) {
            Dialog(onDismissRequest = { showPresetsModal = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Modelos Prontos", color = PrismColors.PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Escolha um layout base profissional. Você poderá ajustar cada camada depois.",
                        color = PrismColors.MutedZinc,
                        fontSize = 12.sp
                    )
                    Presets.all.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    showPresetsModal = false
                                    pendingPreset = preset
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.first, color = PrismColors.PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Icon(MIcons.Rounded.AutoAwesomeMotion, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPresetsModal = false }) {
                            Text("Fechar", color = PrismColors.MutedZinc)
                        }
                    }
                }
            }
        }

        // Explicit confirmation AlertDialog before overwriting layers with a preset
        pendingPreset?.let { (presetName, builder) ->
            AlertDialog(
                onDismissRequest = { pendingPreset = null },
                containerColor = PrismColors.CarbonBlack,
                titleContentColor = PrismColors.PureWhite,
                textContentColor = PrismColors.MutedZinc,
                shape = RoundedCornerShape(22.dp),
                title = { Text("Aplicar modelo \"$presetName\"?", fontWeight = FontWeight.SemiBold) },
                text = { Text("As camadas atuais deste tema serão substituídas pelas camadas do modelo escolhido.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val ls = builder()
                            onCommitProject(project.copy(layers = ls))
                            onSelectLayer(ls.firstOrNull()?.id)
                            pendingPreset = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Substituir", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPreset = null }) {
                        Text("Cancelar", color = PrismColors.MutedZinc)
                    }
                }
            )
        }
    }
}

private fun layerTypeIcon(type: LayerType): ImageVector = when (type) {
    LayerType.TEXT -> MIcons.Rounded.TextFields
    LayerType.STACK -> MIcons.Rounded.ViewAgenda
    LayerType.SHAPE -> MIcons.Rounded.Category
    LayerType.ICON -> MIcons.Rounded.WbSunny
    LayerType.SVG -> MIcons.Rounded.Code
    LayerType.DEPTH -> MIcons.Rounded.Layers
}

private fun layerSubtitle(node: LayerNode, env: Env, globals: Map<String, String> = emptyMap()): String {
    val base = when (node.type) {
        LayerType.TEXT -> "${node.text}  →  ${Dynamic.eval(node.text, env, LocalDateTime.now(), globals)}"
        LayerType.STACK -> "Pilha ${dirLabel(node.dir).lowercase()} · ${node.children.size} itens"
        LayerType.SHAPE -> "Forma ${shapeLabel(node.shape)}"
        LayerType.ICON -> "Ícone ${iconLabel(node.icon)}"
        LayerType.SVG -> "Vetor SVG"
        LayerType.DEPTH -> "Recorte 3D automático"
    }
    return if (node.touch.isActive) "$base  · ⚡ ${TouchActionExecutor.describe(node.touch)}" else base
}

@Composable
private fun LayersTab(
    project: Project,
    env: Env,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCommit: (Project) -> Unit,
    showAdd: Boolean,
    onToggleAdd: () -> Unit,
    onOpenPresets: () -> Unit,
    onImportSvg: () -> Unit
) {
    val flat = remember(project.layers) { project.layers.flatten() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrismColors.PureWhite,
                    contentColor = PrismColors.PureBlack
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(MIcons.Rounded.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adicionar Item", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onOpenPresets,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(MIcons.Rounded.AutoAwesomeMotion, null, tint = PrismColors.PureWhite, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Modelos Prontos", color = PrismColors.PureWhite, fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            visible = showAdd,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fun add(node: LayerNode) {
                    val parent = selectedId?.let { project.layers.find(it) }?.takeIf { it.type == LayerType.STACK }
                    val updated = if (parent != null) {
                        project.layers.mapNode(parent.id) { it.copy(children = it.children + node) }
                    } else project.layers + node
                    onCommit(project.copy(layers = updated))
                    onSelect(node.id)
                    onToggleAdd()
                }
                val chipColors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0x16FFFFFF),
                    labelColor = PrismColors.PureWhite
                )
                AssistChip(onClick = { add(LayerNode(name = "Texto", text = "{HH}:{mm}", size = 120f)) }, label = { Text("Texto/Código", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Pilha V", type = LayerType.STACK, dir = Dir.VERTICAL, spacing = 10f)) }, label = { Text("Pilha Vertical", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Pilha H", type = LayerType.STACK, dir = Dir.HORIZONTAL, spacing = 12f)) }, label = { Text("Pilha Horizontal", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Forma", type = LayerType.SHAPE, shape = ShapeKind.RECT, w = 300f, h = 120f, corner = 32f)) }, label = { Text("Forma", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Barra", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 320f, h = 18f, corner = 9f, value = "bat")) }, label = { Text("Progresso", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Ícone", type = LayerType.ICON, icon = "weather", w = 72f, h = 72f)) }, label = { Text("Ícone", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { onToggleAdd(); onImportSvg() }, label = { Text("SVG", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { add(LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)) }, label = { Text("Profundidade 3D", fontSize = 11.sp) }, colors = chipColors)
            }
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(flat, key = { it.first.id }) { (node, depth) ->
                val sel = node.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * 14).dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Color(0x22FFFFFF) else PrismColors.CarbonBlack)
                        .border(
                            width = 1.dp,
                            color = if (sel) PrismColors.PureWhite else PrismColors.GlassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(node.id) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) PrismColors.PureWhite else PrismColors.TrackZinc),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = layerTypeIcon(node.type),
                            contentDescription = null,
                            tint = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.name, color = PrismColors.PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(layerSubtitle(node, env, project.globalsMap()), color = PrismColors.MutedZinc, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.move(node.id, -1))) }, Modifier.size(26.dp)) {
                        Icon(MIcons.Rounded.ArrowUpward, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.move(node.id, 1))) }, Modifier.size(26.dp)) {
                        Icon(MIcons.Rounded.ArrowDownward, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.mapNode(node.id) { it.copy(hidden = !it.hidden) })) }, Modifier.size(26.dp)) {
                        Icon(
                            if (node.hidden) MIcons.Rounded.VisibilityOff else MIcons.Rounded.Visibility,
                            null,
                            tint = if (node.hidden) PrismColors.TrackZinc else PrismColors.PureWhite,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.mapNode(node.id) { it.copy(locked = !it.locked) })) }, Modifier.size(26.dp)) {
                        Icon(
                            if (node.locked) MIcons.Rounded.Lock else MIcons.Rounded.LockOpen,
                            null,
                            tint = if (node.locked) PrismColors.PureWhite else PrismColors.MutedZinc,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { onCommit(project.copy(layers = project.layers.insertAfter(node.id, node.copyDeep()))) }, Modifier.size(26.dp)) {
                        Icon(MIcons.Rounded.ContentCopy, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = {
                        onCommit(project.copy(layers = project.layers.removeNode(node.id)))
                        if (selectedId == node.id) onSelect(null)
                    }, Modifier.size(26.dp)) {
                        Icon(MIcons.Rounded.DeleteOutline, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTab(
    node: LayerNode?,
    project: Project,
    env: Env,
    wallPalette: List<Int>,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onImportSvg: () -> Unit
) {
    if (node == null) {
        Text("Selecione uma camada na tela ou na aba Camadas", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
    val globals = remember(project.globals) { project.globalsMap() }
    var activeCategoryId by remember { mutableStateOf(KustomFormulaEngine.categories.first().id) }
    val liveNow by produceState(initialValue = LocalDateTime.now(), node.text) {
        val period = if (Dynamic.hasSeconds(node)) 1000L else 15_000L
        while (true) {
            value = LocalDateTime.now()
            delay(period)
        }
    }

    var showNameDialog by remember(node.id) { mutableStateOf(false) }
    var showFormulaDialog by remember(node.id) { mutableStateOf(false) }
    var showValueDialog by remember(node.id) { mutableStateOf(false) }
    var showIconFormulaDialog by remember(node.id) { mutableStateOf(false) }
    var fxDialogField by remember(node.id) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusedFieldTrigger(
            label = "Nome da camada",
            value = node.name,
            placeholder = "Camada",
            onClick = { showNameDialog = true }
        )
        if (showNameDialog) {
            FocusedInputDialog(
                title = "Nome da Camada",
                initialValue = node.name,
                placeholder = "Relógio Principal",
                exampleHint = "Ex: Relógio, Data Editorial, Bateria",
                onDismiss = { showNameDialog = false },
                onConfirm = { v ->
                    update { it.copy(name = v.trim().ifBlank { it.name }) }
                    onSaveFinished()
                    showNameDialog = false
                }
            )
        }
        if (node.type == LayerType.TEXT) {
            FocusedFieldTrigger(
                label = "Fórmula de Texto / KWGT (\$...\$)",
                value = node.text,
                placeholder = "\$df(HH)\$:\$df(mm)\$",
                onClick = { showFormulaDialog = true }
            )
            if (showFormulaDialog) {
                FocusedInputDialog(
                    title = "Fórmula de Texto (\$...\$)",
                    initialValue = node.text,
                    placeholder = "\$df(HH)\$:\$df(mm)\$",
                    exampleHint = "Ex: \$df(HH)\$:\$df(mm)\$ · \$if(bi(level) < 20, \"Bateria Baixa\", \"Normal\")\$ · {HH}:{mm}",
                    singleLine = false,
                    onDismiss = { showFormulaDialog = false },
                    onConfirm = { v ->
                        update { it.copy(text = v) }
                        onSaveFinished()
                        showFormulaDialog = false
                    }
                )
            }
            // Real-time evaluated formula preview card
            val liveResult = remember(node.text, env, liveNow, globals) {
                Dynamic.eval(node.text, env, liveNow, globals)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x16FFFFFF))
                    .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Resultado ao vivo", fontSize = 11.sp, color = PrismColors.MutedZinc)
                Text(
                    text = liveResult.ifBlank { "(vazio)" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrismColors.PureWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StudioSection(
                title = "Assistente de Fórmulas (KWGT)",
                summary = "Toque para inserir",
                initiallyExpanded = true
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KustomFormulaEngine.categories.forEach { cat ->
                        val sel = cat.id == activeCategoryId
                        FilterChip(
                            selected = sel,
                            onClick = { activeCategoryId = cat.id },
                            label = { Text(cat.label, fontSize = 11.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(sel)
                        )
                    }
                }
                val currentCat = KustomFormulaEngine.categories.firstOrNull { it.id == activeCategoryId }
                    ?: KustomFormulaEngine.categories.first()
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentCat.items.forEach { snippet ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    update { it.copy(text = if (it.text.isBlank()) snippet.code else it.text + snippet.code) }
                                    onSaveFinished()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(snippet.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrismColors.PureWhite)
                                Text(snippet.code, fontSize = 9.sp, color = PrismColors.MutedZinc, maxLines = 1)
                            }
                        }
                    }
                }
                QuickPresetRow(
                    items = listOf(
                        "Substituir Relógio" to { update { it.copy(text = "\$df(HH)\$:\$df(mm)\$") }; onSaveFinished() },
                        "Data Completa" to { update { it.copy(text = "\$tc(cap, df(EEEE))\$, \$df(dd)\$ de \$df(MMMM)\$") }; onSaveFinished() },
                        "Clima & Bat" to { update { it.copy(text = "\$wi(temp)\$° · \$wi(cond)\$ · \$bi(level)\$%") }; onSaveFinished() },
                        "Limpar" to { update { it.copy(text = "") }; onSaveFinished() }
                    )
                )
                if (project.globals.isNotEmpty()) {
                    Text("Inserir Global do Projeto", fontSize = 11.sp, color = PrismColors.MutedZinc)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        project.globals.forEach { g ->
                            val snippet = "\$gv(${g.name})\$"
                            FilterChip(
                                selected = snippet in node.text,
                                onClick = {
                                    update { it.copy(text = if (it.text.isBlank()) snippet else it.text + snippet) }
                                    onSaveFinished()
                                },
                                label = { Text("${g.name} (${g.value})", fontSize = 10.sp) },
                                colors = prismFilterChipColors(),
                                border = prismFilterChipBorder(snippet in node.text)
                            )
                        }
                    }
                }
            }
        }
        if (node.type == LayerType.SHAPE) {
            StudioSection(title = "Geometria da Forma", summary = shapeLabel(node.shape), initiallyExpanded = true) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShapeKind.entries.forEach { k ->
                        val sel = node.shape == k
                        FilterChip(selected = sel, onClick = { update { it.copy(shape = k) }; onSaveFinished() }, label = { Text(shapeLabel(k), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                if (node.shape == ShapeKind.BAR || node.shape == ShapeKind.RING) {
                    FocusedFieldTrigger(
                        label = "Variável / Fórmula Progresso (0..100)",
                        value = node.value,
                        placeholder = "\$bi(level)\$ ou bat",
                        onClick = { showValueDialog = true }
                    )
                    if (showValueDialog) {
                        FocusedInputDialog(
                            title = "Fórmula / Variável (0..100)",
                            initialValue = node.value,
                            placeholder = "\$bi(level)\$",
                            exampleHint = "Ex: \$bi(level)\$, bat, dayprog, hourprog, \$=hour/24*100\$",
                            onDismiss = { showValueDialog = false },
                            onConfirm = { v ->
                                update { it.copy(value = v.trim()) }
                                onSaveFinished()
                                showValueDialog = false
                            }
                        )
                    }
                    QuickPresetRow(
                        items = listOf(
                            "Bateria" to { update { it.copy(value = "\$bi(level)\$") }; onSaveFinished() },
                            "Dia %" to { update { it.copy(value = "dayprog") }; onSaveFinished() },
                            "Hora %" to { update { it.copy(value = "hourprog") }; onSaveFinished() },
                            "Passos" to { update { it.copy(value = "steps") }; onSaveFinished() }
                        )
                    )
                    if (node.shape == ShapeKind.RING) {
                        PrecisionSliderField("Espessura Anel", node.strokeWidth.coerceAtLeast(2f), 2f..48f, "px", 0, onSaveFinished) { v ->
                            update { it.copy(strokeWidth = v) }
                        }
                    }
                    ColorPickerPanel("Cor da Trilha Fundo", node.trackColor, wallPalette) { c ->
                        update { it.copy(trackColor = c) }
                    }
                }
            }
        }
        if (node.type == LayerType.ICON) {
            StudioSection(title = "Ícone Vetorial", summary = iconLabel(node.icon), initiallyExpanded = true) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icons.names.forEach { ic ->
                        val sel = node.icon == ic
                        FilterChip(selected = sel, onClick = { update { it.copy(icon = ic) }; onSaveFinished() }, label = { Text(iconLabel(ic), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                FocusedFieldTrigger(
                    label = "Fórmula de Ícone Dinâmico (opcional)",
                    value = node.icon,
                    placeholder = "weather",
                    onClick = { showIconFormulaDialog = true }
                )
                if (showIconFormulaDialog) {
                    FocusedInputDialog(
                        title = "Ícone / Fórmula (\$...\$)",
                        initialValue = node.icon,
                        placeholder = "weather",
                        exampleHint = "Ex: \$if(bi(charging), \"bolt\", \"battery\")\$ ou \$if(si(mplaying), \"music\", \"lock\")\$",
                        onDismiss = { showIconFormulaDialog = false },
                        onConfirm = { v ->
                            update { it.copy(icon = v.trim().ifEmpty { "weather" }) }
                            onSaveFinished()
                            showIconFormulaDialog = false
                        }
                    )
                }
            }
        }
        if (node.type == LayerType.STACK) {
            StudioSection(title = "Organização da Pilha", summary = "${dirLabel(node.dir)} · ${node.children.size} itens", initiallyExpanded = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dir.entries.forEach { d ->
                        val sel = node.dir == d
                        FilterChip(selected = sel, onClick = { update { it.copy(dir = d) }; onSaveFinished() }, label = { Text(dirLabel(d), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                PrecisionSliderField("Espaçamento", node.spacing, -60f..160f, "px", 0, onSaveFinished) { v -> update { it.copy(spacing = v) } }
            }
        }
        if (node.type == LayerType.SVG) {
            Button(
                onClick = onImportSvg,
                colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Substituir arquivo SVG", fontWeight = FontWeight.SemiBold) }
        }
        StudioSection(title = "Condição & Comportamento", summary = condLabel(node.cond), initiallyExpanded = false) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cond.entries.forEach { c ->
                    val sel = node.cond == c
                    FilterChip(selected = sel, onClick = { update { it.copy(cond = c) }; onSaveFinished() }, label = { Text(condLabel(c), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
        }

        val fxEntries = listOf(
            Triple("vis", "Visibilidade por Fórmula", node.visibilityExpr to "Ex: \$gv(painel) = 1\$ ou \$bi(level) < 30\$"),
            Triple("color", "Cor Dinâmica FX (HEX)", node.fxColor to "Ex: \$if(bi(level) <= 20, \"#FFFF5252\", \"#FFFFFFFF\")\$"),
            Triple("opacity", "Opacidade FX (0..100%)", node.fxOpacity to "Ex: \$if(gv(modo) = 1, 100, 35)\$"),
            Triple("rot", "Rotação FX (°)", node.fxRotation to "Ex: \$df(ss) * 6\$ ou \$bi(level) * 3.6\$"),
            Triple("scale", "Escala FX (x)", node.fxScale to "Ex: \$if(gv(zoom) = 1, 1.25, 1.0)\$"),
            Triple("dx", "Deslocamento X FX (px)", node.fxOffsetX to "Ex: \$if(gv(aberto) = 1, 0, -140)\$"),
            Triple("dy", "Deslocamento Y FX (px)", node.fxOffsetY to "Ex: \$if(gv(menu) = 1, 0, 80)\$")
        )
        val activeFxCount = fxEntries.count { it.third.first.isNotBlank() }
        StudioSection(
            title = "Fórmulas Avançadas FX (KLWP)",
            summary = if (activeFxCount == 0) "Nenhuma ativa" else "$activeFxCount ativa(s)",
            initiallyExpanded = activeFxCount > 0
        ) {
            fxEntries.forEach { (key, label, valAndHint) ->
                FocusedFieldTrigger(
                    label = label,
                    value = valAndHint.first,
                    placeholder = "Opcional (\$...\$)",
                    onClick = { fxDialogField = key }
                )
            }
            val activeFx = fxEntries.firstOrNull { it.first == fxDialogField }
            if (activeFx != null) {
                val (key, label, valAndHint) = activeFx
                FocusedInputDialog(
                    title = label,
                    initialValue = valAndHint.first,
                    placeholder = "\$...\$ (vazio desativa)",
                    exampleHint = valAndHint.second,
                    singleLine = false,
                    onDismiss = { fxDialogField = null },
                    onConfirm = { raw ->
                        val v = raw.trim()
                        update { n ->
                            when (key) {
                                "vis" -> n.copy(visibilityExpr = v)
                                "color" -> n.copy(fxColor = v)
                                "opacity" -> n.copy(fxOpacity = v)
                                "rot" -> n.copy(fxRotation = v)
                                "scale" -> n.copy(fxScale = v)
                                "dx" -> n.copy(fxOffsetX = v)
                                "dy" -> n.copy(fxOffsetY = v)
                                else -> n
                            }
                        }
                        onSaveFinished()
                        fxDialogField = null
                    }
                )
            }
        }
    }
}

@Composable
private fun TouchTab(
    node: LayerNode?,
    project: Project,
    previewLocked: Boolean,
    testTouchMode: Boolean,
    onTogglePreviewLocked: () -> Unit,
    onToggleTestTouch: () -> Unit,
    onTestAction: (LayerNode) -> Unit,
    onOpenGlobalsTab: () -> Unit,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit
) {
    val ctx = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    var showAppSearchDialog by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var showPackageDialog by remember(node?.id) { mutableStateOf(false) }
    var showUrlDialog by remember(node?.id) { mutableStateOf(false) }
    var showGlobalNameDialog by remember(node?.id) { mutableStateOf(false) }
    var showGlobalValueDialog by remember(node?.id) { mutableStateOf(false) }

    val apps by produceState<List<LaunchableApp>>(initialValue = emptyList(), showAppPicker) {
        if (showAppPicker) {
            value = withContext(Dispatchers.IO) { TouchActionExecutor.queryLaunchableApps(ctx) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = testTouchMode,
                onClick = onToggleTestTouch,
                label = { Text(if (testTouchMode) "Testar Toques na Tela: ATIVO" else "Testar Toques na Tela", fontSize = 11.sp) },
                colors = prismFilterChipColors(),
                border = prismFilterChipBorder(testTouchMode)
            )
            FilterChip(
                selected = !previewLocked,
                onClick = onTogglePreviewLocked,
                label = { Text(if (previewLocked) "Preview: Bloqueio" else "Preview: Início", fontSize = 11.sp) },
                colors = prismFilterChipColors(),
                border = prismFilterChipBorder(!previewLocked)
            )
        }

        Text(
            text = "No Papel de Parede Vivo, toques executam na Tela Inicial (Launcher). Ative \"Testar Toques na Tela\" acima para clicar nos itens direto no preview.",
            fontSize = 10.sp,
            color = PrismColors.MutedZinc,
            lineHeight = 14.sp
        )

        if (node == null) {
            Text("Selecione uma camada na tela ou em Camadas para configurar o clique.", color = PrismColors.MutedZinc, fontSize = 12.sp)
        } else {
            StudioSection(
                title = "Ação ao Clicar · ${node.name}",
                summary = TouchActionExecutor.describe(node.touch),
                initiallyExpanded = true
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TouchActionType.entries.forEach { t ->
                        val sel = node.touch.type == t
                        FilterChip(
                            selected = sel,
                            onClick = {
                                update { it.copy(touch = it.touch.copy(type = t)) }
                                onSaveFinished()
                            },
                            label = { Text(TouchActionExecutor.labelOf(t), fontSize = 11.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(sel)
                        )
                    }
                }

                when (node.touch.type) {
                    TouchActionType.OPEN_APP -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAppPicker = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = node.touch.targetLabel.ifBlank { "Escolher Aplicativo" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { showPackageDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder)
                            ) { Text("Pacote", fontSize = 11.sp, color = PrismColors.PureWhite) }
                        }
                        if (node.touch.targetPackage.isNotBlank()) {
                            Text("Pacote: ${node.touch.targetPackage}", fontSize = 10.sp, color = PrismColors.MutedZinc)
                        }
                    }
                    TouchActionType.OPEN_URL -> {
                        FocusedFieldTrigger(
                            label = "Link / URL / Deep Link",
                            value = node.touch.url,
                            placeholder = "https://...",
                            onClick = { showUrlDialog = true }
                        )
                        QuickPresetRow(
                            items = listOf(
                                "Google" to { update { it.copy(touch = it.touch.copy(url = "https://www.google.com")) }; onSaveFinished() },
                                "YouTube" to { update { it.copy(touch = it.touch.copy(url = "https://www.youtube.com")) }; onSaveFinished() },
                                "Spotify" to { update { it.copy(touch = it.touch.copy(url = "spotify:")) }; onSaveFinished() },
                                "Clima Busca" to { update { it.copy(touch = it.touch.copy(url = "https://www.google.com/search?q=clima+\$wi(city)\$")) }; onSaveFinished() }
                            )
                        )
                    }
                    TouchActionType.TOGGLE_GLOBAL, TouchActionType.SET_GLOBAL, TouchActionType.NEXT_GLOBAL_LIST -> {
                        if (project.globals.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                project.globals.forEach { g ->
                                    val sel = node.touch.globalName.equals(g.name, ignoreCase = true)
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            update { it.copy(touch = it.touch.copy(globalName = g.name)) }
                                            onSaveFinished()
                                        },
                                        label = { Text("${g.name} (${g.value})", fontSize = 10.sp) },
                                        colors = prismFilterChipColors(),
                                        border = prismFilterChipBorder(sel)
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                FocusedFieldTrigger(
                                    label = "Global Alvo",
                                    value = node.touch.globalName,
                                    placeholder = "painel",
                                    onClick = { showGlobalNameDialog = true }
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenGlobalsTab,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong)
                            ) { Text("Globais →", fontSize = 11.sp, color = PrismColors.PureWhite) }
                        }
                        if (node.touch.type == TouchActionType.SET_GLOBAL) {
                            FocusedFieldTrigger(
                                label = "Valor a Definir (aceita \$...\$)",
                                value = node.touch.globalValue,
                                placeholder = "1 ou \$if(gv(x)=1, 0, 1)\$",
                                onClick = { showGlobalValueDialog = true }
                            )
                        }
                    }
                    else -> Unit
                }

                if (node.touch.isActive) {
                    OutlinedButton(
                        onClick = { onTestAction(node) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong)
                    ) { Text("Executar Ação Agora", fontSize = 12.sp, color = PrismColors.PureWhite, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }

    if (showPackageDialog && node != null) {
        FocusedInputDialog(
            title = "Pacote do App",
            initialValue = node.touch.targetPackage,
            placeholder = "com.whatsapp",
            exampleHint = "Ex: com.whatsapp, com.spotify.music, com.android.chrome",
            onDismiss = { showPackageDialog = false },
            onConfirm = { v ->
                update { it.copy(touch = it.touch.copy(targetPackage = v.trim(), targetLabel = v.trim())) }
                onSaveFinished()
                showPackageDialog = false
            }
        )
    }

    if (showUrlDialog && node != null) {
        FocusedInputDialog(
            title = "Link ou Deep Link",
            initialValue = node.touch.url,
            placeholder = "https://www.google.com",
            exampleHint = "Aceita URL, tel:, mailto: ou fórmulas \$...\$",
            onDismiss = { showUrlDialog = false },
            onConfirm = { v ->
                update { it.copy(touch = it.touch.copy(url = v.trim())) }
                onSaveFinished()
                showUrlDialog = false
            }
        )
    }

    if (showGlobalNameDialog && node != null) {
        FocusedInputDialog(
            title = "Nome da Variável Global",
            initialValue = node.touch.globalName,
            placeholder = "painel",
            exampleHint = "Ex: painel, cor_tema, modo (sem \$ ou espaços)",
            onDismiss = { showGlobalNameDialog = false },
            onConfirm = { v ->
                update { it.copy(touch = it.touch.copy(globalName = v.trim())) }
                onSaveFinished()
                showGlobalNameDialog = false
            }
        )
    }

    if (showGlobalValueDialog && node != null) {
        FocusedInputDialog(
            title = "Valor da Global",
            initialValue = node.touch.globalValue,
            placeholder = "1",
            exampleHint = "Texto, HEX (#FF7AA7FF), número ou fórmula \$...\$",
            onDismiss = { showGlobalValueDialog = false },
            onConfirm = { v ->
                update { it.copy(touch = it.touch.copy(globalValue = v)) }
                onSaveFinished()
                showGlobalValueDialog = false
            }
        )
    }

    if (showAppPicker && node != null) {
        Dialog(onDismissRequest = { showAppPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp)
                    .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Escolher Aplicativo (${apps.size})", color = PrismColors.PureWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row {
                        IconButton(onClick = { showAppSearchDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(MIcons.Rounded.Search, contentDescription = "Buscar", tint = PrismColors.PureWhite, modifier = Modifier.size(18.dp))
                        }
                        TextButton(onClick = { showAppPicker = false }) { Text("Fechar", color = PrismColors.MutedZinc) }
                    }
                }
                val filtered = remember(apps, appQuery) {
                    val q = appQuery.trim().lowercase()
                    if (q.isEmpty()) apps else apps.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val sel = node.touch.targetPackage == app.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) PrismColors.PureWhite else Color(0x16FFFFFF))
                                .border(1.dp, if (sel) PrismColors.PureWhite else PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    update {
                                        it.copy(touch = it.touch.copy(type = TouchActionType.OPEN_APP, targetPackage = app.packageName, targetLabel = app.label))
                                    }
                                    onSaveFinished()
                                    showAppPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = if (sel) PrismColors.PureBlack else PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = if (sel) PrismColors.PureBlack.copy(alpha = 0.7f) else PrismColors.MutedZinc, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (showAppSearchDialog) {
            FocusedInputDialog(
                title = "Filtrar Aplicativos",
                initialValue = appQuery,
                placeholder = "Spotify, WhatsApp, Câmera...",
                exampleHint = "Deixe vazio para mostrar todos",
                onDismiss = { showAppSearchDialog = false },
                onConfirm = { q -> appQuery = q; showAppSearchDialog = false }
            )
        }
    }
}

private fun globalTypeLabel(t: GlobalType): String = when (t) {
    GlobalType.COLOR -> "Cor"
    GlobalType.SWITCH -> "Interruptor"
    GlobalType.NUMBER -> "Número"
    GlobalType.TEXT -> "Texto"
    GlobalType.LIST -> "Lista"
}

private fun List<LayerNode>.mapAllNodes(f: (LayerNode) -> LayerNode): List<LayerNode> = map { n ->
    val next = f(n)
    if (next.children.isEmpty()) next else next.copy(children = next.children.mapAllNodes(f))
}

@Composable
private fun GlobalsTab(
    project: Project,
    wallPalette: List<Int>,
    onSelectLayer: (String?) -> Unit,
    onUpdateLayer: (String, (LayerNode) -> LayerNode) -> Unit,
    onStageProject: (Project) -> Unit,
    onCommitProject: (Project) -> Unit,
    onSaveFinished: () -> Unit
) {
    var createPresetType by remember { mutableStateOf<GlobalType?>(null) }
    var editingTextGlobal by remember { mutableStateOf<GlobalVar?>(null) }
    var editingColorGlobal by remember { mutableStateOf<GlobalVar?>(null) }
    val targetLayers = remember(project.layers) {
        project.layers.flatten().map { it.first }.filter { it.type != LayerType.DEPTH }
    }
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PrismColors.PureBlack,
        checkedTrackColor = PrismColors.PureWhite,
        uncheckedThumbColor = PrismColors.MutedZinc,
        uncheckedTrackColor = PrismColors.TrackZinc
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { createPresetType = GlobalType.COLOR },
                colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(MIcons.Rounded.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nova Global", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${project.globals.size} variável(is)", fontSize = 11.sp, color = PrismColors.MutedZinc)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                GlobalType.COLOR to "+ Cor",
                GlobalType.SWITCH to "+ Interruptor",
                GlobalType.TEXT to "+ Texto",
                GlobalType.NUMBER to "+ Número",
                GlobalType.LIST to "+ Lista"
            ).forEach { (gt, lbl) ->
                AssistChip(
                    onClick = { createPresetType = gt },
                    label = { Text(lbl, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x16FFFFFF), labelColor = PrismColors.PureWhite)
                )
            }
        }

        if (project.globals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x12FFFFFF))
                    .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Crie globais de Cor, Texto, Número, Interruptor ou Lista para controlar várias camadas ao mesmo tempo via \$gv(nome)\$.",
                    fontSize = 11.sp,
                    color = PrismColors.MutedZinc,
                    lineHeight = 16.sp
                )
            }
        } else {
            project.globals.forEach { g ->
                val gvExpr = "\$gv(${g.name})\$"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(g.name, color = PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    globalTypeLabel(g.type),
                                    fontSize = 9.sp,
                                    color = PrismColors.MutedZinc,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x1EFFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(gvExpr, color = PrismColors.MutedZinc, fontSize = 10.sp)
                        }

                        when (g.type) {
                            GlobalType.SWITCH -> {
                                val checked = g.value == "1" || g.value.equals("true", ignoreCase = true)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { onCommitProject(project.setGlobalValue(g.name, if (it) "1" else "0")) },
                                    colors = switchColors
                                )
                            }
                            GlobalType.COLOR -> {
                                val parsed = KustomFormulaEngine.evalColor(g.value, Env(), LocalDateTime.now()) ?: 0xFFFFFFFF.toInt()
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(parsed))
                                        .border(1.5.dp, PrismColors.PureWhite, CircleShape)
                                        .clickable { editingColorGlobal = g }
                                )
                            }
                            GlobalType.NUMBER -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(
                                        onClick = { onCommitProject(project.cycleGlobal(g.name, -1)) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) { Text("-", fontSize = 12.sp, color = PrismColors.PureWhite) }
                                    Text(
                                        g.value,
                                        color = PrismColors.PureWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { editingTextGlobal = g }.padding(horizontal = 4.dp)
                                    )
                                    OutlinedButton(
                                        onClick = { onCommitProject(project.cycleGlobal(g.name, 1)) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) { Text("+", fontSize = 12.sp, color = PrismColors.PureWhite) }
                                }
                            }
                            GlobalType.TEXT -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1EFFFFFF))
                                        .clickable { editingTextGlobal = g }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(g.value.ifBlank { "(vazio)" }, color = PrismColors.PureWhite, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                            GlobalType.LIST -> Unit
                        }

                        IconButton(
                            onClick = { onCommitProject(project.copy(globals = project.globals.filterNot { it.name.equals(g.name, ignoreCase = true) })) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(MIcons.Rounded.DeleteOutline, contentDescription = "Excluir global", tint = PrismColors.MutedZinc, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (g.type == GlobalType.LIST && g.options.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.options.forEach { opt ->
                                val sel = g.value == opt
                                FilterChip(
                                    selected = sel,
                                    onClick = { onCommitProject(project.setGlobalValue(g.name, opt)) },
                                    label = { Text(opt, fontSize = 10.sp) },
                                    colors = prismFilterChipColors(),
                                    border = prismFilterChipBorder(sel)
                                )
                            }
                        }
                    }

                    if (targetLayers.isNotEmpty()) {
                        val compatibleLayers = when (g.type) {
                            GlobalType.COLOR, GlobalType.SWITCH -> targetLayers
                            GlobalType.TEXT, GlobalType.LIST -> targetLayers.filter { it.type == LayerType.TEXT }
                            GlobalType.NUMBER -> targetLayers.filter {
                                it.type == LayerType.TEXT || (it.type == LayerType.SHAPE && (it.shape == ShapeKind.BAR || it.shape == ShapeKind.RING))
                            }
                        }
                        if (compatibleLayers.isNotEmpty()) {
                            Text("Aplicar em:", fontSize = 10.sp, color = PrismColors.MutedZinc)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (g.type == GlobalType.COLOR) {
                                    val allBound = compatibleLayers.all { it.fxColor == gvExpr }
                                    FilterChip(
                                        selected = allBound,
                                        onClick = {
                                            val nextExpr = if (allBound) "" else gvExpr
                                            onCommitProject(
                                                project.copy(
                                                    layers = project.layers.mapAllNodes { n ->
                                                        if (n.type == LayerType.DEPTH) n else n.copy(fxColor = nextExpr)
                                                    }
                                                )
                                            )
                                        },
                                        label = { Text("Todas", fontSize = 10.sp) },
                                        colors = prismFilterChipColors(),
                                        border = prismFilterChipBorder(allBound)
                                    )
                                }
                                compatibleLayers.forEach { layer ->
                                    val bound = when (g.type) {
                                        GlobalType.COLOR -> layer.fxColor == gvExpr
                                        GlobalType.SWITCH -> layer.visibilityExpr == "\$gv(${g.name}) = 1\$"
                                        GlobalType.TEXT, GlobalType.LIST -> gvExpr in layer.text
                                        GlobalType.NUMBER -> if (layer.type == LayerType.SHAPE) layer.value == gvExpr else gvExpr in layer.text
                                    }
                                    FilterChip(
                                        selected = bound,
                                        onClick = {
                                            onSelectLayer(layer.id)
                                            when (g.type) {
                                                GlobalType.COLOR -> onUpdateLayer(layer.id) {
                                                    it.copy(fxColor = if (bound) "" else gvExpr)
                                                }
                                                GlobalType.SWITCH -> {
                                                    val visExpr = "\$gv(${g.name}) = 1\$"
                                                    onUpdateLayer(layer.id) {
                                                        it.copy(visibilityExpr = if (bound) "" else visExpr)
                                                    }
                                                }
                                                GlobalType.TEXT, GlobalType.LIST -> onUpdateLayer(layer.id) {
                                                    it.copy(
                                                        text = if (bound) {
                                                            it.text.replace(gvExpr, "").trim()
                                                        } else if (it.text.isBlank()) {
                                                            gvExpr
                                                        } else {
                                                            it.text + gvExpr
                                                        }
                                                    )
                                                }
                                                GlobalType.NUMBER -> onUpdateLayer(layer.id) {
                                                    if (it.type == LayerType.SHAPE) {
                                                        it.copy(value = if (bound) "bat" else gvExpr)
                                                    } else {
                                                        it.copy(
                                                            text = if (bound) {
                                                                it.text.replace(gvExpr, "").trim()
                                                            } else if (it.text.isBlank()) {
                                                                gvExpr
                                                            } else {
                                                                it.text + gvExpr
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            onSaveFinished()
                                        },
                                        label = { Text(layer.name, fontSize = 10.sp) },
                                        colors = prismFilterChipColors(),
                                        border = prismFilterChipBorder(bound)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    createPresetType?.let { initialType ->
        CreateGlobalDialog(
            initialType = initialType,
            wallPalette = wallPalette,
            onDismiss = { createPresetType = null },
            onCreate = { newVar ->
                val nextList = project.globals.filterNot { it.name.equals(newVar.name, ignoreCase = true) } + newVar
                onCommitProject(project.copy(globals = nextList))
                createPresetType = null
            }
        )
    }

    editingTextGlobal?.let { g ->
        FocusedInputDialog(
            title = "Valor de ${g.name}",
            initialValue = g.value,
            placeholder = if (g.type == GlobalType.NUMBER) "100" else "Valor",
            exampleHint = "Altera todos os itens que usam \$gv(${g.name})\$",
            keyboardType = if (g.type == GlobalType.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
            allowNegative = true,
            onDismiss = { editingTextGlobal = null },
            onConfirm = { raw ->
                onCommitProject(project.setGlobalValue(g.name, raw.trim()))
                editingTextGlobal = null
            }
        )
    }

    editingColorGlobal?.let { g ->
        val liveGlobal = project.globals.firstOrNull { it.name.equals(g.name, ignoreCase = true) } ?: g
        val curColor = KustomFormulaEngine.evalColor(liveGlobal.value, Env(), LocalDateTime.now()) ?: 0xFFFFFFFF.toInt()
        Dialog(onDismissRequest = { onSaveFinished(); editingColorGlobal = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Cor Global · ${g.name}", color = PrismColors.PureWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                ColorPickerPanel(g.name, curColor, wallPalette, initiallyExpanded = true) { newArgb ->
                    val hex = String.format("#%08X", newArgb)
                    val updated = project.setGlobalValue(g.name, hex)
                    onStageProject(updated)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onSaveFinished(); editingColorGlobal = null }) { Text("Concluir", color = PrismColors.PureWhite) }
                }
            }
        }
    }
}

@Composable
private fun CreateGlobalDialog(
    initialType: GlobalType = GlobalType.SWITCH,
    wallPalette: List<Int> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (GlobalVar) -> Unit
) {
    var name by remember {
        mutableStateOf(
            when (initialType) {
                GlobalType.COLOR -> "cor_destaque"
                GlobalType.SWITCH -> "painel"
                GlobalType.NUMBER -> "valor"
                GlobalType.TEXT -> "titulo"
                GlobalType.LIST -> "modo"
            }
        )
    }
    var type by remember { mutableStateOf(initialType) }
    var value by remember {
        mutableStateOf(
            when (initialType) {
                GlobalType.COLOR -> "#FFFFFFFF"
                GlobalType.SWITCH -> "0"
                GlobalType.NUMBER -> "50"
                GlobalType.TEXT -> "PrismLock"
                GlobalType.LIST -> ""
            }
        )
    }
    var pickedColor by remember { mutableStateOf(wallPalette.firstOrNull() ?: 0xFFFFFFFF.toInt()) }
    var optionsRaw by remember { mutableStateOf("Clima,Bateria,Música") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nova Variável Global (\$gv\$)", color = PrismColors.PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlobalType.entries.forEach { gt ->
                    val sel = type == gt
                    FilterChip(
                        selected = sel,
                        onClick = {
                            type = gt
                            value = when (gt) {
                                GlobalType.COLOR -> String.format("#%08X", pickedColor)
                                GlobalType.SWITCH -> "0"
                                GlobalType.NUMBER -> "50"
                                GlobalType.TEXT -> "PrismLock"
                                GlobalType.LIST -> ""
                            }
                        },
                        label = { Text(globalTypeLabel(gt), fontSize = 11.sp) },
                        colors = prismFilterChipColors(),
                        border = prismFilterChipBorder(sel)
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome (sem espaços, ex: cor_destaque)", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrismColors.PureWhite,
                    unfocusedTextColor = PrismColors.PureWhite,
                    focusedBorderColor = PrismColors.PureWhite,
                    unfocusedBorderColor = PrismColors.GlassBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
            when (type) {
                GlobalType.COLOR -> {
                    ColorPickerPanel("Cor Inicial", pickedColor, wallPalette, initiallyExpanded = true) { argb ->
                        pickedColor = argb
                        value = String.format("#%08X", argb)
                    }
                }
                GlobalType.LIST -> {
                    OutlinedTextField(
                        value = optionsRaw,
                        onValueChange = { optionsRaw = it },
                        label = { Text("Opções separadas por vírgula", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrismColors.PureWhite,
                            unfocusedTextColor = PrismColors.PureWhite,
                            focusedBorderColor = PrismColors.PureWhite,
                            unfocusedBorderColor = PrismColors.GlassBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Valor inicial", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrismColors.PureWhite,
                            unfocusedTextColor = PrismColors.PureWhite,
                            focusedBorderColor = PrismColors.PureWhite,
                            unfocusedBorderColor = PrismColors.GlassBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = PrismColors.MutedZinc) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        val cleanName = name.trim().replace(" ", "_")
                        if (cleanName.isNotEmpty()) {
                            val opts = if (type == GlobalType.LIST) optionsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                            val initVal = when (type) {
                                GlobalType.LIST -> opts.firstOrNull().orEmpty()
                                GlobalType.COLOR -> String.format("#%08X", pickedColor)
                                else -> value.trim()
                            }
                            onCreate(GlobalVar(name = cleanName, type = type, value = initVal, options = opts))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack)
                ) { Text("Criar Global", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun TypeTab(
    node: LayerNode?,
    importedFonts: List<String>,
    hasFontFolder: Boolean,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onImportFont: () -> Unit,
    onPickFontFolder: () -> Unit,
    onRefreshFontFolder: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    if (node == null) {
        Text("Selecione uma camada", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
    var showFontListDialog by remember { mutableStateOf(false) }
    val allFonts = remember(importedFonts) { FontManager.availableFonts(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (node.type == LayerType.TEXT) {
            StudioSection(title = "Fonte & Peso", summary = fontDisplayLabel(node.font), initiallyExpanded = true) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasFontFolder) onRefreshFontFolder(false)
                            showFontListDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x18FFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Ver Lista (${allFonts.size})", fontSize = 11.sp, color = PrismColors.PureWhite, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onPickFontFolder,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(MIcons.Rounded.FolderOpen, contentDescription = null, tint = PrismColors.PureWhite, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Pasta de Fontes", fontSize = 11.sp, color = PrismColors.PureWhite)
                    }
                    if (hasFontFolder) {
                        IconButton(
                            onClick = { onRefreshFontFolder(true) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(10.dp))
                        ) {
                            Icon(MIcons.Rounded.Refresh, contentDescription = "Atualizar fontes", tint = PrismColors.PureWhite, modifier = Modifier.size(15.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = onImportFont,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ TTF/OTF", fontSize = 11.sp, color = PrismColors.PureWhite) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    allFonts.forEach { f ->
                        val sel = node.font == f
                        val previewFamily = remember(f) { FontManager.composeFontFamily(f, 400) }
                        FilterChip(
                            selected = sel,
                            onClick = { update { it.copy(font = f) }; onSaveFinished() },
                            label = {
                                Text(
                                    text = fontDisplayLabel(f),
                                    fontFamily = previewFamily,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(sel)
                        )
                    }
                }
                if (showFontListDialog) {
                    Dialog(onDismissRequest = { showFontListDialog = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                                .frostedChrome(shape = RoundedCornerShape(24.dp), dense = true, elevation = 24.dp)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Seletor Tipográfico (${allFonts.size})", color = PrismColors.PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { showFontListDialog = false }) {
                                    Text("Fechar", color = PrismColors.MutedZinc)
                                }
                            }
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(allFonts, key = { it }) { f ->
                                    val sel = node.font == f
                                    val previewFamily = remember(f) { FontManager.composeFontFamily(f, 400) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (sel) PrismColors.PureWhite else Color(0x16FFFFFF))
                                            .border(1.dp, if (sel) PrismColors.PureWhite else PrismColors.GlassBorder, RoundedCornerShape(14.dp))
                                            .clickable {
                                                update { it.copy(font = f) }
                                                onSaveFinished()
                                                showFontListDialog = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = fontDisplayLabel(f),
                                            fontFamily = previewFamily,
                                            color = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "12:45",
                                            fontFamily = previewFamily,
                                            color = if (sel) PrismColors.PureBlack else PrismColors.MutedZinc,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                QuickPresetRow(
                    items = listOf(
                        "Leve 300" to { update { it.copy(weight = 300) }; onSaveFinished() },
                        "Regular 400" to { update { it.copy(weight = 400) }; onSaveFinished() },
                        "Médio 500" to { update { it.copy(weight = 500) }; onSaveFinished() },
                        "Negrito 700" to { update { it.copy(weight = 700) }; onSaveFinished() },
                        "Extra 800" to { update { it.copy(weight = 800) }; onSaveFinished() }
                    )
                )
                PrecisionSliderField("Tamanho", node.size, 18f..420f, "px", 0, onSaveFinished) { v -> update { it.copy(size = v) } }
                PrecisionSliderField("Peso", node.weight.toFloat(), 100f..900f, "", 0, onSaveFinished) { v -> update { it.copy(weight = v.roundToInt()) } }
            }

            StudioSection(
                title = "Elasticidade & Espaçamento",
                summary = "${"%.2f".format(node.stretch)}x",
                initiallyExpanded = true
            ) {
                QuickPresetRow(
                    items = listOf(
                        "Clássico 1.0x" to { update { it.copy(stretch = 1.0f) }; onSaveFinished() },
                        "Alto 1.4x" to { update { it.copy(stretch = 1.4f, strokeCompensation = 1.0f) }; onSaveFinished() },
                        "Editorial 1.8x" to { update { it.copy(stretch = 1.8f, strokeCompensation = 1.0f) }; onSaveFinished() },
                        "Ultra 2.4x" to { update { it.copy(stretch = 2.4f, strokeCompensation = 1.0f) }; onSaveFinished() }
                    )
                )
                PrecisionSliderField("Elasticidade", node.stretch, 1f..3f, "x", 2, onSaveFinished) { v -> update { it.copy(stretch = v) } }
                PrecisionSliderField("Comp. Traço", node.strokeCompensation, 0f..1.2f, "x", 2, onSaveFinished) { v -> update { it.copy(strokeCompensation = v) } }
                PrecisionSliderField("Kerning", node.letterSpacing, -0.15f..0.6f, "em", 2, onSaveFinished) { v -> update { it.copy(letterSpacing = v) } }
                PrecisionSliderField("Entrelinha", node.lineSpacing, -0.6f..1f, "", 2, onSaveFinished) { v -> update { it.copy(lineSpacing = v) } }
                FontManager.axes(node.font).forEach { ax ->
                    val cur = node.axes[ax.tag] ?: ax.def
                    PrecisionSliderField("Eixo ${ax.tag}", cur, ax.min..ax.max, "", 1, onSaveFinished) { v -> update { it.copy(axes = it.axes + (ax.tag to v)) } }
                }
            }
        }
        if (node.type == LayerType.STACK) {
            StudioSection(title = "Pilha & Espaçamento", summary = dirLabel(node.dir), initiallyExpanded = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dir.entries.forEach { d ->
                        val sel = node.dir == d
                        FilterChip(selected = sel, onClick = { update { it.copy(dir = d) }; onSaveFinished() }, label = { Text(dirLabel(d)) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                QuickPresetRow(
                    items = listOf(
                        "Justo 0px" to { update { it.copy(spacing = 0f) }; onSaveFinished() },
                        "Compacto 8px" to { update { it.copy(spacing = 8f) }; onSaveFinished() },
                        "Médio 16px" to { update { it.copy(spacing = 16f) }; onSaveFinished() },
                        "Aberto 32px" to { update { it.copy(spacing = 32f) }; onSaveFinished() }
                    )
                )
                PrecisionSliderField("Espaçamento", node.spacing, -60f..160f, "px", 0, onSaveFinished) { v -> update { it.copy(spacing = v) } }
            }
        }
        if (node.type == LayerType.SHAPE || node.type == LayerType.ICON || node.type == LayerType.SVG) {
            StudioSection(title = "Dimensões & Cantos", summary = "${node.w.roundToInt()}×${node.h.roundToInt()}", initiallyExpanded = true) {
                QuickPresetRow(
                    items = listOf(
                        "Quadrado" to { update { it.copy(w = 180f, h = 180f, corner = 32f) }; onSaveFinished() },
                        "Pílula" to { update { it.copy(w = 340f, h = 68f, corner = 34f) }; onSaveFinished() },
                        "Card" to { update { it.copy(w = 460f, h = 180f, corner = 36f) }; onSaveFinished() },
                        "Barra Fina" to { update { it.copy(w = 400f, h = 16f, corner = 8f) }; onSaveFinished() }
                    )
                )
                PrecisionSliderField("Largura", node.w, 16f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(w = v) } }
                PrecisionSliderField("Altura", node.h, 2f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(h = v) } }
                PrecisionSliderField("Canto", node.corner, 0f..300f, "px", 0, onSaveFinished) { v -> update { it.copy(corner = v) } }
            }
        }
        StudioSection(
            title = "Alinhamento & Transformação",
            summary = "${alignLabel(node.align)} · ${"%.2f".format(node.scale)}x",
            initiallyExpanded = node.type != LayerType.TEXT
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Align.entries.forEach { a ->
                    val sel = node.align == a
                    FilterChip(selected = sel, onClick = { update { it.copy(align = a) }; onSaveFinished() }, label = { Text(alignLabel(a), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
            QuickPresetRow(
                items = listOf(
                    "Resetar Escala" to { update { it.copy(scale = 1f) }; onSaveFinished() },
                    "0°" to { update { it.copy(rotation = 0f) }; onSaveFinished() },
                    "-90°" to { update { it.copy(rotation = -90f) }; onSaveFinished() },
                    "90°" to { update { it.copy(rotation = 90f) }; onSaveFinished() }
                )
            )
            PrecisionSliderField("Escala", node.scale, 0.25f..4f, "x", 2, onSaveFinished) { v -> update { it.copy(scale = v) } }
            PrecisionSliderField("Rotação", node.rotation, -180f..180f, "°", 1, onSaveFinished) { v -> update { it.copy(rotation = v) } }
        }
    }
}

@Composable
private fun PaintTab(
    allLayers: List<LayerNode>,
    colorGlobals: List<GlobalVar> = emptyList(),
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
                    val sel = item.id == node?.id
                    FilterChip(selected = sel, onClick = { onSelect(item.id) }, label = { Text(item.name, fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
        }
        if (node == null) {
            Text("Selecione uma camada", color = PrismColors.MutedZinc, fontSize = 12.sp)
            return@Column
        }

        StudioSection(
            title = "Preenchimento & Vidro",
            summary = when (node.fill) {
                Fill.SOLID -> "Sólido"
                Fill.GRADIENT -> "Gradiente"
                Fill.ADAPTIVE -> "Adaptativo"
                Fill.GLASS -> "Blur Clássico"
                Fill.CRYSTAL -> "Cristal Óptico"
                Fill.HOLLOW -> "Vazado"
            },
            initiallyExpanded = true
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Fill.SOLID to "Sólido",
                    Fill.GRADIENT to "Gradiente",
                    Fill.ADAPTIVE to "Adaptativo",
                    Fill.GLASS to "Blur Clássico",
                    Fill.CRYSTAL to "Cristal Óptico",
                    Fill.HOLLOW to "Vazado"
                ).forEach { (f, label) ->
                    val sel = node.fill == f
                    FilterChip(selected = sel, onClick = { update { it.copy(fill = f) }; onSaveFinished() }, label = { Text(label, fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }

            if (node.fill == Fill.SOLID || node.fill == Fill.GRADIENT || node.fill == Fill.HOLLOW) {
                ColorPickerPanel("Cor Principal", node.color, wallPalette) { c -> update { it.copy(color = c) } }
            }
            if (colorGlobals.isNotEmpty()) {
                Text("Globais de Cor (\$gv\$)", fontSize = 11.sp, color = PrismColors.MutedZinc)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorGlobals.forEach { cg ->
                        val expr = "\$gv(${cg.name})\$"
                        val bound = node.fxColor == expr
                        val argb = KustomFormulaEngine.evalColor(cg.value, Env(), LocalDateTime.now()) ?: 0xFFFFFFFF.toInt()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (bound) Color(0x28FFFFFF) else Color(0x14FFFFFF))
                                .border(1.dp, if (bound) PrismColors.PureWhite else PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    update { it.copy(fxColor = if (bound) "" else expr) }
                                    onSaveFinished()
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(argb)).border(1.dp, PrismColors.PureWhite, CircleShape))
                            Text(cg.name, fontSize = 11.sp, color = PrismColors.PureWhite)
                        }
                    }
                }
            }
            if (node.fill == Fill.GRADIENT || (node.fill == Fill.HOLLOW && node.color2 != node.color)) {
                ColorPickerPanel("Cor Secundária", node.color2, wallPalette) { c -> update { it.copy(color2 = c) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientKind.entries.forEach { gk ->
                        val sel = node.gradientKind == gk
                        FilterChip(selected = sel, onClick = { update { it.copy(gradientKind = gk) }; onSaveFinished() }, label = { Text(gradientLabel(gk)) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                if (node.gradientKind == GradientKind.LINEAR) {
                    PrecisionSliderField("Ângulo Grad", node.gradientAngle, 0f..360f, "°", 0, onSaveFinished) { v -> update { it.copy(gradientAngle = v) } }
                }
            }
            if (node.fill == Fill.GLASS) {
                QuickPresetRow(
                    items = listOf(
                        "Blur Suave" to {
                            update { it.copy(glass = it.glass.copy(blur = 18f, saturation = 1.25f, tint = 0x22FFFFFF, rim = 0.65f)) }
                            onSaveFinished()
                        },
                        "Fosco Intenso" to {
                            update { it.copy(glass = it.glass.copy(blur = 42f, saturation = 1.45f, tint = 0x33FFFFFF, rim = 0.9f)) }
                            onSaveFinished()
                        },
                        "Vidro Fumê" to {
                            update { it.copy(glass = it.glass.copy(blur = 28f, saturation = 1.15f, tint = 0x55000000, rim = 0.55f)) }
                            onSaveFinished()
                        }
                    )
                )
                PrecisionSliderField("Blur Gauss", node.glass.blur, 0f..80f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
                PrecisionSliderField("Saturação", node.glass.saturation, 1f..1.8f, "x", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(saturation = v)) } }
                PrecisionSliderField("Granulação", node.glass.grain, 0f..0.15f, "", 3, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(grain = v)) } }
                PrecisionSliderField("Borda Suave", node.glass.rim.coerceIn(0f, 1f), 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
                ColorPickerPanel("Tint Translúcido", node.glass.tint, wallPalette) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
            }
            if (node.fill == Fill.CRYSTAL) {
                QuickPresetRow(
                    items = listOf(
                        "Cristal Limpo" to {
                            update { it.copy(glass = Glass(refraction = 38f, bevelWidth = 22f, chromaticAberration = 0.005f, frostAmount = 0.04f, rim = 0.85f, tint = 0x18FFFFFF)) }
                            onSaveFinished()
                        },
                        "Vidro Fosco" to {
                            update { it.copy(glass = Glass(refraction = 32f, bevelWidth = 24f, chromaticAberration = 0.004f, frostAmount = 0.55f, blur = 28f, rim = 0.8f, tint = 0x26FFFFFF)) }
                            onSaveFinished()
                        },
                        "Prisma Forte" to {
                            update { it.copy(glass = Glass(refraction = 68f, bevelWidth = 30f, chromaticAberration = 0.014f, frostAmount = 0.12f, rim = 1.0f, tint = 0x20FFFFFF)) }
                            onSaveFinished()
                        },
                        "Zero Tint" to {
                            update { it.copy(glass = it.glass.copy(tint = 0x00FFFFFF)) }
                            onSaveFinished()
                        }
                    )
                )
                PrecisionSliderField("Refração", node.glass.refraction.coerceIn(0f, 100f), 0f..100f, "%", 0, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(refraction = v)) } }
                PrecisionSliderField("Chanfro", node.glass.bevelWidth.coerceIn(1f, 48f), 1f..48f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(bevelWidth = v)) } }
                PrecisionSliderField("Dispersão RGB", node.glass.chromaticAberration * 1000f, 0f..20f, "‰", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(chromaticAberration = v / 1000f)) } }
                PrecisionSliderField("Brilho Fresnel", node.glass.rim.coerceIn(0f, 1f), 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
                PrecisionSliderField("Ângulo Luz", node.glass.lightAngle, 0f..360f, "°", 0, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(lightAngle = v)) } }
                PrecisionSliderField("Jateamento", node.glass.frostAmount.coerceIn(0f, 1f), 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(frostAmount = v)) } }
                if (node.glass.frostAmount > 0.01f) {
                    PrecisionSliderField("Blur Jato", node.glass.blur, 0f..80f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
                }
                ColorPickerPanel("Tint Cristal", node.glass.tint, wallPalette) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
            }
        }

        StudioSection(
            title = "Contorno / Borda",
            summary = if (node.strokeWidth <= 0f) "Nenhum" else "${node.strokeWidth.roundToInt()}px",
            initiallyExpanded = false
        ) {
            QuickPresetRow(
                items = listOf(
                    "Sem Borda" to { update { it.copy(strokeWidth = 0f) }; onSaveFinished() },
                    "Fino 2px" to { update { it.copy(strokeWidth = 2f) }; onSaveFinished() },
                    "Médio 6px" to { update { it.copy(strokeWidth = 6f) }; onSaveFinished() },
                    "Forte 12px" to { update { it.copy(strokeWidth = 12f) }; onSaveFinished() }
                )
            )
            PrecisionSliderField("Espessura", node.strokeWidth, 0f..24f, "px", 1, onSaveFinished) { v -> update { it.copy(strokeWidth = v) } }
            if (node.strokeWidth > 0f || node.fill == Fill.HOLLOW) {
                ColorPickerPanel("Cor Contorno", node.strokeColor, wallPalette) { c -> update { it.copy(strokeColor = c) } }
            }
        }

        StudioSection(
            title = "Sombra Projetada",
            summary = if (node.shadowRadius <= 0f) "Desativada" else "${node.shadowRadius.roundToInt()}px",
            initiallyExpanded = false
        ) {
            QuickPresetRow(
                items = listOf(
                    "Sem Sombra" to { update { it.copy(shadowRadius = 0f) }; onSaveFinished() },
                    "Suave" to { update { it.copy(shadowRadius = 14f, shadowDx = 0f, shadowDy = 6f, shadowColor = 0x70000000) }; onSaveFinished() },
                    "Dramática" to { update { it.copy(shadowRadius = 32f, shadowDx = 0f, shadowDy = 14f, shadowColor = 0xB8000000.toInt()) }; onSaveFinished() }
                )
            )
            PrecisionSliderField("Raio Sombra", node.shadowRadius, 0f..50f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowRadius = v) } }
            if (node.shadowRadius > 0f) {
                PrecisionSliderField("Sombra X", node.shadowDx, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDx = v) } }
                PrecisionSliderField("Sombra Y", node.shadowDy, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDy = v) } }
                ColorPickerPanel("Cor Sombra", node.shadowColor, wallPalette) { c -> update { it.copy(shadowColor = c) } }
            }
        }

        StudioSection(
            title = "Opacidade & Mesclagem",
            summary = "${(node.opacity * 100).roundToInt()}% · ${blendLabel(node.blend)}",
            initiallyExpanded = false
        ) {
            QuickPresetRow(
                items = listOf(
                    "100%" to { update { it.copy(opacity = 1f) }; onSaveFinished() },
                    "85%" to { update { it.copy(opacity = 0.85f) }; onSaveFinished() },
                    "60%" to { update { it.copy(opacity = 0.6f) }; onSaveFinished() },
                    "35%" to { update { it.copy(opacity = 0.35f) }; onSaveFinished() }
                )
            )
            PrecisionSliderField("Opacidade", node.opacity, 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(opacity = v) } }
            Text("Modo de Mesclagem", fontSize = 12.sp, color = PrismColors.MutedZinc)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Blend.entries.forEach { b ->
                    val sel = node.blend == b
                    FilterChip(selected = sel, onClick = { update { it.copy(blend = b) }; onSaveFinished() }, label = { Text(blendLabel(b), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
        }
    }
}

@Composable
private fun PositionAndSafTab(
    node: LayerNode?,
    project: Project,
    activePreviewUri: String?,
    onSelectLayer: (String?) -> Unit,
    onUpdateNode: ((LayerNode) -> LayerNode) -> Unit,
    onStage: (Project) -> Unit,
    onCommit: (Project) -> Unit,
    onSaveFinished: () -> Unit,
    onToggleDepth: (String, Boolean) -> Unit,
    onSelectPreview: (String, Int) -> Unit,
    onPickFolder: () -> Unit,
    onPickImage: () -> Unit,
    onClearWallpaper: () -> Unit,
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

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PrismColors.PureBlack,
        checkedTrackColor = PrismColors.PureWhite,
        uncheckedThumbColor = PrismColors.MutedZinc,
        uncheckedTrackColor = PrismColors.TrackZinc
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPickFolder,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x18FFFFFF), contentColor = PrismColors.PureWhite),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Pasta Carrossel SAF", fontSize = 12.sp) }
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder)
            ) { Text("Foto / Vídeo", fontSize = 12.sp, color = PrismColors.PureWhite) }
            if (project.folderUri != null || project.imageUri != null) {
                IconButton(
                    onClick = onClearWallpaper,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x16FFFFFF))
                        .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(MIcons.Rounded.DeleteOutline, contentDescription = "Remover fundo", tint = PrismColors.MutedZinc, modifier = Modifier.size(18.dp))
                }
            }
        }

        val activeIsVideo by produceState(initialValue = false, activePreviewUri) {
            value = activePreviewUri != null && withContext(Dispatchers.IO) {
                WallpaperCarousel.isVideoUri(ctx, activePreviewUri)
            }
        }

        if (galleryItems.isNotEmpty()) {
            Text(
                if (project.folderUri != null) "Wallpapers (${galleryItems.size}) — toque na mídia para testar · canto 3D ON/OFF" else "Wallpaper Ativo — toque no selo para alternar 3D",
                fontSize = 11.sp, color = PrismColors.MutedZinc
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(148.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(galleryItems, key = { _, item -> item.first }) { idx, (uriStr, imageKey) ->
                    val thumb by produceState<Bitmap?>(initialValue = null, uriStr) {
                        value = WallpaperCarousel.loadThumbnail(ctx, uriStr)
                    }
                    val isVideo by produceState(initialValue = false, uriStr) {
                        value = withContext(Dispatchers.IO) { WallpaperCarousel.isVideoUri(ctx, uriStr) }
                    }
                    val isSelected = uriStr == activePreviewUri
                    val is3dOn = !isVideo && project.depth && project.isDepthEnabledFor(imageKey)
                    Box(
                        modifier = Modifier
                            .width(86.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrismColors.CarbonBlack)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PrismColors.PureWhite else PrismColors.GlassBorder,
                                shape = RoundedCornerShape(14.dp)
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
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .zIndex(2f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC0D0D0F))
                                    .border(1.dp, PrismColors.GlassBorderStrong, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    MIcons.Rounded.PlayArrow,
                                    contentDescription = "Vídeo",
                                    tint = PrismColors.PureWhite,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .zIndex(2f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (is3dOn) PrismColors.PureWhite else Color(0xCC0D0D0F))
                                    .border(
                                        width = 1.dp,
                                        color = if (is3dOn) PrismColors.PureWhite else PrismColors.GlassBorderStrong,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleDepth(imageKey, !is3dOn) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (is3dOn) "3D ON" else "3D OFF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (is3dOn) PrismColors.PureBlack else PrismColors.MutedZinc
                                )
                            }
                        }
                        if (project.folderUri != null) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrismColors.PureWhite,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xAA000000))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            if (activeIsVideo) {
                Text(
                    text = "3D indisponível em vídeo — Cristal Óptico, Blur e Fonte Elástica ativos.",
                    fontSize = 11.sp,
                    color = PrismColors.MutedZinc
                )
            }
        }

        val activeGalleryItem = galleryItems.firstOrNull { it.first == activePreviewUri } ?: galleryItems.firstOrNull()
        val activeImage3dOn = activeGalleryItem != null && !activeIsVideo && project.depth && project.isDepthEnabledFor(activeGalleryItem.second)

        if (activeImage3dOn) {
            StudioSection(
                title = "Profundidade 3D (Bokeh & Color Pop)",
                summary = if (project.hasDepthPostProcessing()) "Personalizado" else "Natural",
                initiallyExpanded = true
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Proteção Cobertura Relógio", fontSize = 12.sp, color = PrismColors.MutedZinc)
                    Switch(checked = project.depthGuard, onCheckedChange = { onCommit(project.copy(depthGuard = it)) }, colors = switchColors)
                }
                    QuickPresetRow(
                        items = listOf(
                            "Original" to {
                                onCommit(project.copy(depthBgBlur = 0f, depthBgDesaturate = 0f, depthInvertColorPop = false, depthSubjectPop = 0f, depthVignette = 0f))
                            },
                            "Modo Retrato" to {
                                onCommit(project.copy(depthBgBlur = 0.55f, depthBgDesaturate = 0f, depthInvertColorPop = false, depthSubjectPop = 0.25f, depthVignette = 0.28f))
                            },
                            "Color Pop P&B" to {
                                onCommit(project.copy(depthBgBlur = 0.25f, depthBgDesaturate = 1f, depthInvertColorPop = false, depthSubjectPop = 0.30f, depthVignette = 0.25f))
                            },
                            "Cine 3D" to {
                                onCommit(project.copy(depthBgBlur = 0.72f, depthBgDesaturate = 0.65f, depthInvertColorPop = false, depthSubjectPop = 0.35f, depthVignette = 0.45f))
                            }
                        )
                    )
                    PrecisionSliderField("Bokeh Fundo", project.depthBgBlur * 100f, 0f..100f, "%", 0, onSaveFinished) { v ->
                        onStage(project.copy(depthBgBlur = (v / 100f).coerceIn(0f, 1f)))
                    }
                    PrecisionSliderField("Fundo P&B", project.depthBgDesaturate * 100f, 0f..100f, "%", 0, onSaveFinished) { v ->
                        onStage(project.copy(depthBgDesaturate = (v / 100f).coerceIn(0f, 1f)))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Inverter Color Pop (Sujeito P&B)", fontSize = 12.sp, color = PrismColors.MutedZinc)
                        Switch(checked = project.depthInvertColorPop, onCheckedChange = { onCommit(project.copy(depthInvertColorPop = it)) }, colors = switchColors)
                    }
                    PrecisionSliderField("Destaque Sujeito", project.depthSubjectPop * 100f, 0f..100f, "%", 0, onSaveFinished) { v ->
                        onStage(project.copy(depthSubjectPop = (v / 100f).coerceIn(0f, 1f)))
                    }
                    PrecisionSliderField("Vinheta & Halo 3D", project.depthVignette * 100f, 0f..100f, "%", 0, onSaveFinished) { v ->
                        onStage(project.copy(depthVignette = (v / 100f).coerceIn(0f, 1f)))
                    }
            }
        }

        if (node != null) {
            val parentStack = remember(node.id, project.layers) { project.layers.parentOf(node.id) }
            StudioSection(
                title = "Posição, Âncora & Visibilidade",
                summary = if (parentStack != null) "Na pilha \"${parentStack.name}\"" else "${(node.x * 100).roundToInt()}%, ${(node.y * 100).roundToInt()}%",
                initiallyExpanded = false
            ) {
                if (parentStack != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x16FFFFFF))
                            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Camada filha da pilha \"${parentStack.name}\". Posição e âncora seguem o grupo pai.",
                            fontSize = 11.sp,
                            color = PrismColors.MutedZinc
                        )
                        OutlinedButton(
                            onClick = { onSelectLayer(parentStack.id) },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Selecionar Pilha Pai", fontSize = 11.sp, color = PrismColors.PureWhite)
                        }
                    }
                } else {
                    QuickPresetRow(
                        items = listOf(
                            "Topo Centro" to { onUpdateNode { it.copy(x = 0.5f, y = 0.16f, anchor = Anchor.TOP_CENTER) }; onSaveFinished() },
                            "Centro" to { onUpdateNode { it.copy(x = 0.5f, y = 0.5f, anchor = Anchor.CENTER) }; onSaveFinished() },
                            "Rodapé" to { onUpdateNode { it.copy(x = 0.5f, y = 0.84f, anchor = Anchor.BOTTOM_CENTER) }; onSaveFinished() }
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrecisionSliderField("Posição X", node.x * 100f, 0f..100f, "%", 1, onSaveFinished) { v -> onUpdateNode { it.copy(x = v / 100f) } }
                        }
                        OutlinedButton(onClick = { onUpdateNode { it.copy(x = (it.x - 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("-1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                        OutlinedButton(onClick = { onUpdateNode { it.copy(x = (it.x + 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("+1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrecisionSliderField("Posição Y", node.y * 100f, 0f..100f, "%", 1, onSaveFinished) { v -> onUpdateNode { it.copy(y = v / 100f) } }
                        }
                        OutlinedButton(onClick = { onUpdateNode { it.copy(y = (it.y - 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("-1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                        OutlinedButton(onClick = { onUpdateNode { it.copy(y = (it.y + 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) { Text("+1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                    }
                    Text("Âncora da Camada", fontSize = 12.sp, color = PrismColors.MutedZinc)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Anchor.entries.forEach { a ->
                            val sel = node.anchor == a
                            FilterChip(selected = sel, onClick = { onUpdateNode { it.copy(anchor = a) }; onSaveFinished() }, label = { Text(anchorLabel(a), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                        }
                    }
                }
                Text("Regra de Visibilidade", fontSize = 12.sp, color = PrismColors.MutedZinc)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cond.entries.forEach { c ->
                        val sel = node.cond == c
                        FilterChip(selected = sel, onClick = { onUpdateNode { it.copy(cond = c) }; onSaveFinished() }, label = { Text(condLabel(c), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
            }
        }

        StudioSection(
            title = "Automação, Clima & Escurecer",
            summary = "${(project.dim * 100).roundToInt()}% dim",
            initiallyExpanded = false
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Trocar ao apagar tela", fontSize = 13.sp, color = PrismColors.PureWhite)
                Switch(checked = project.changeOnWake, onCheckedChange = { onCommit(project.copy(changeOnWake = it)) }, colors = switchColors)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Trocar com toque duplo", fontSize = 13.sp, color = PrismColors.PureWhite)
                Switch(checked = project.changeOnDoubleTap, onCheckedChange = { onCommit(project.copy(changeOnDoubleTap = it)) }, colors = switchColors)
            }
            PrecisionSliderField("Intervalo", project.intervalMin.toFloat(), 0f..120f, "m", 0, onSaveFinished) { v -> onStage(project.copy(intervalMin = v.roundToInt())) }
            PrecisionSliderField("Escurecer", project.dim * 100f, 0f..70f, "%", 0, onSaveFinished) { v -> onStage(project.copy(dim = v / 100f)) }

            var showCityDialog by remember { mutableStateOf(false) }
            FocusedFieldTrigger(
                label = "Cidade (Clima Open-Meteo)",
                value = cityInput,
                placeholder = "Ex: São Paulo",
                onClick = { showCityDialog = true }
            )
            if (showCityDialog) {
                FocusedInputDialog(
                    title = "Cidade para Clima (Open-Meteo)",
                    initialValue = cityInput,
                    placeholder = "São Paulo",
                    exampleHint = "Ex: São Paulo, Rio de Janeiro, Curitiba",
                    confirmLabel = "Buscar Clima",
                    onDismiss = { showCityDialog = false },
                    onConfirm = { raw ->
                        val trimmed = raw.trim()
                        cityInput = trimmed
                        showCityDialog = false
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                val g = EnvProvider.geocode(trimmed)
                                if (g != null) onCommit(project.copy(lat = g.first, lon = g.second, city = g.third))
                            }
                        }
                    }
                )
            }
        }
        Button(
            onClick = onApplyWallpaper,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Aplicar na Tela de Bloqueio", fontWeight = FontWeight.Bold)
        }
        Text(
            "Dica Samsung: no Good Lock / LockStar, oculte o relógio nativo do sistema para exibir apenas suas camadas.",
            fontSize = 11.sp, color = PrismColors.MutedZinc
        )
    }
}
