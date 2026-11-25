package com.example.prismlock.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
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
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.example.prismlock.model.isAboveSubject
import com.example.prismlock.model.mapNode
import com.example.prismlock.model.move
import com.example.prismlock.model.parentOf
import com.example.prismlock.model.removeNode
import com.example.prismlock.model.setAboveSubject
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

private enum class StudioTab(val label: String) {
    LAYERS("Camadas"),
    CONTENT("Conteúdo"),
    SHAPE_FONT("Forma & Fonte"),
    GLASS_COLOR("Vidro & Cor"),
    POSITION_TOUCH("Posição & Toque")
}

private enum class ProjectContextPanel {
    BACKGROUND_3D,
    GLOBALS,
    WEATHER_AUTOMATION
}

internal fun anchorLabel(a: Anchor): String = when (a) {
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

internal fun condLabel(c: Cond): String = when (c) {
    Cond.ALWAYS -> "Sempre"
    Cond.LOCK_ONLY -> "Apenas Bloqueio"
    Cond.HOME_ONLY -> "Apenas Início"
    Cond.CHARGING -> "Carregando"
    Cond.NOT_CHARGING -> "Na Bateria"
    Cond.LOW_BATTERY -> "Bateria Baixa"
    Cond.DAY -> "Durante o Dia"
    Cond.NIGHT -> "À Noite"
}

internal fun blendLabel(b: Blend): String = when (b) {
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

internal fun gradientLabel(g: GradientKind): String = when (g) {
    GradientKind.LINEAR -> "Linear"
    GradientKind.RADIAL -> "Radial"
    GradientKind.SWEEP -> "Circular"
}

internal fun shapeLabel(s: ShapeKind): String = when (s) {
    ShapeKind.RECT -> "Retângulo"
    ShapeKind.CIRCLE -> "Círculo"
    ShapeKind.LINE -> "Linha"
    ShapeKind.BAR -> "Barra"
    ShapeKind.RING -> "Anel"
}

internal fun dirLabel(d: Dir): String = when (d) {
    Dir.VERTICAL -> "Vertical"
    Dir.HORIZONTAL -> "Horizontal"
}

internal fun alignLabel(a: Align): String = when (a) {
    Align.START -> "Esquerda"
    Align.CENTER -> "Centro"
    Align.END -> "Direita"
}

internal fun iconLabel(ic: String): String = Icons.label(ic)

internal fun fontDisplayLabel(f: String): String = when (f) {
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
    onPickImageFile: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onImportFont: () -> Unit,
    hasFontFolder: Boolean = false,
    onPickFontFolder: () -> Unit = {},
    onRefreshFontFolder: (Boolean) -> Unit = {},
    onImportSvg: () -> Unit,
    onPickLayerImage: () -> Unit = {},
    onAddCustomAsset: (String) -> Unit = {},
    onBokehDragStateChange: (Boolean) -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onNextWallpaper: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var tab by remember(project.id) { mutableStateOf(StudioTab.LAYERS) }
    var activeContextPanel by remember(project.id) { mutableStateOf<ProjectContextPanel?>(null) }
    var sheetExpanded by remember(project.id) { mutableStateOf(true) }
    val globalsMap = remember(project.globals) { project.globalsMap() }
    var showAddMenu by remember(project.id) { mutableStateOf(false) }
    var showOverflowMenu by remember(project.id) { mutableStateOf(false) }
    var showRenameDialog by remember(project.id) { mutableStateOf(false) }
    var showSaveAsDialog by remember(project.id) { mutableStateOf(false) }
    var showPresetsModal by remember(project.id) { mutableStateOf(false) }
    var pendingPreset by remember(project.id) { mutableStateOf<Pair<String, () -> List<LayerNode>>?>(null) }
    var isActivelyDraggingLayer by remember(project.id) { mutableStateOf(false) }
    var blurRefreshTick by remember(project.id) { mutableIntStateOf(0) }
    var liveSensorPreview by remember(project.id) { mutableStateOf(false) }
    val previewMotionSource = remember(ctx) { com.example.prismlock.wallpaper.MotionSource(ctx) }
    val liveMotionOffset by previewMotionSource.offset.collectAsState()
    val sensorActiveNow = !project.isWidget && project.motion.enabled && liveSensorPreview &&
        activeContextPanel == ProjectContextPanel.BACKGROUND_3D
    DisposableEffect(sensorActiveNow, project.motion) {
        if (sensorActiveNow) {
            previewMotionSource.start(project.motion)
        } else {
            previewMotionSource.stop()
        }
        onDispose { previewMotionSource.stop() }
    }
    val onBlurDragStateChange: (Boolean) -> Unit = remember(renderer) {
        { dragging ->
            renderer.interactiveBlur = dragging
            if (!dragging) blurRefreshTick++
        }
    }
    DisposableEffect(renderer) {
        onDispose { renderer.interactiveBlur = false }
    }
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
        if (project.isWidget) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp, start = 18.dp, end = 18.dp)
                    .aspectRatio(project.widgetAspect.coerceIn(com.example.prismlock.model.WIDGET_ASPECT_MIN, com.example.prismlock.model.WIDGET_ASPECT_MAX))
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, PrismColors.GlassBorderStrong, RoundedCornerShape(28.dp))
            ) {
                InteractiveLockPreview(
                    renderer = renderer,
                    project = project,
                    selectedId = selectedId,
                    bgBitmap = bgBitmap,
                    depthBitmap = depthBitmap,
                    activePreviewUri = activePreviewUri,
                    env = env,
                    previewLocked = false,
                    testTouchMode = testTouchMode && activeContextPanel == null && tab == StudioTab.POSITION_TOUCH,
                    onTriggerLayerTouch = ::runLayerTouch,
                    onSelectLayer = onSelectLayer,
                    onUpdateLayer = onUpdateLayer,
                    onFlushSave = onFlushSave,
                    onDragStateChange = { isActivelyDraggingLayer = it },
                    blurRefreshTick = blurRefreshTick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            InteractiveLockPreview(
                renderer = renderer,
                project = project,
                selectedId = selectedId,
                bgBitmap = bgBitmap,
                depthBitmap = depthBitmap,
                activePreviewUri = activePreviewUri,
                env = env,
                previewLocked = previewLocked,
                testTouchMode = testTouchMode && activeContextPanel == null && tab == StudioTab.POSITION_TOUCH,
                onTriggerLayerTouch = ::runLayerTouch,
                onSelectLayer = onSelectLayer,
                onUpdateLayer = onUpdateLayer,
                onFlushSave = onFlushSave,
                onDragStateChange = { isActivelyDraggingLayer = it },
                blurRefreshTick = blurRefreshTick,
                motionNormX = if (sensorActiveNow) liveMotionOffset.first else 0f,
                motionNormY = if (sensorActiveNow) liveMotionOffset.second else 0f,
                allowTiltDrag = !project.isWidget && project.motion.enabled && activeContextPanel == ProjectContextPanel.BACKGROUND_3D,
                modifier = Modifier.fillMaxSize()
            )
        }

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

                // Right: Undo/Redo, Layer Lock, Collapse inspector, 3-dots menu
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canUndo || canRedo) {
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo,
                            modifier = Modifier
                                .size(36.dp)
                                .frostedChrome(shape = CircleShape, elevation = 6.dp)
                        ) {
                            Icon(
                                MIcons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Desfazer",
                                tint = if (canUndo) PrismColors.PureWhite else PrismColors.TrackZinc,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onRedo,
                            enabled = canRedo,
                            modifier = Modifier
                                .size(36.dp)
                                .frostedChrome(shape = CircleShape, elevation = 6.dp)
                        ) {
                            Icon(
                                MIcons.AutoMirrored.Rounded.Redo,
                                contentDescription = "Refazer",
                                tint = if (canRedo) PrismColors.PureWhite else PrismColors.TrackZinc,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
                                text = { Text(if (project.isWidget) "Aplicar & Fixar Widget" else "Aplicar na Tela de Bloqueio", color = PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
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
                // Barra de Contexto do Projeto (3 pílulas fixas acima das abas do item)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val contextPills = listOf(
                        ProjectContextPanel.BACKGROUND_3D to (if (project.isWidget) "Formato & Fundo" else "Fundo & 3D"),
                        ProjectContextPanel.GLOBALS to (if (project.globals.isEmpty()) "Globais (\$gv)" else "Globais (${project.globals.size})"),
                        ProjectContextPanel.WEATHER_AUTOMATION to "Clima & Automação"
                    )
                    contextPills.forEach { (panel, pillLabel) ->
                        val activePill = activeContextPanel == panel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (activePill) PrismColors.PureWhite else Color(0x16FFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (activePill) PrismColors.PureWhite else PrismColors.GlassBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    activeContextPanel = if (activePill) null else panel
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pillLabel,
                                color = if (activePill) PrismColors.PureBlack else PrismColors.PureWhite,
                                fontSize = 10.5.sp,
                                fontWeight = if (activePill) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = PrismColors.GlassBorder)

                PrimaryScrollableTabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = PrismColors.PureWhite,
                    edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = PrismColors.GlassBorder) },
                    indicator = {
                        if (activeContextPanel == null) {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tab.ordinal, matchContentSize = true),
                                height = 2.dp,
                                color = PrismColors.PureWhite
                            )
                        }
                    }
                ) {
                    StudioTab.entries.forEach { t ->
                        val active = activeContextPanel == null && tab == t
                        Tab(
                            selected = active,
                            onClick = {
                                activeContextPanel = null
                                tab = t
                            },
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
                        .height(268.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val panel = activeContextPanel
                    if (panel != null) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (panel) {
                                        ProjectContextPanel.BACKGROUND_3D -> if (project.isWidget) "Formato & Wallpaper do Widget" else "Fundo, Carrossel & Profundidade 3D"
                                        ProjectContextPanel.GLOBALS -> "Variáveis Globais (\$gv)"
                                        ProjectContextPanel.WEATHER_AUTOMATION -> "Clima & Automação do Projeto"
                                    },
                                    color = PrismColors.PureWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Voltar às Abas",
                                    color = PrismColors.PureWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0x1EFFFFFF))
                                        .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(9.dp))
                                        .clickable { activeContextPanel = null }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (panel) {
                                    ProjectContextPanel.BACKGROUND_3D -> BackgroundContextPanel(
                                        project = project,
                                        activePreviewUri = activePreviewUri,
                                        onStage = onStageProject,
                                        onCommit = onCommitProject,
                                        onSaveFinished = onFlushSave,
                                        onToggleDepth = onToggleDepth,
                                        onSelectPreview = onSelectPreview,
                                        onPickFolder = onPickFolder,
                                        onPickImage = onPickImage,
                                        onPickImageFile = onPickImageFile,
                                        onClearWallpaper = onClearWallpaper,
                                        onBokehDragStateChange = onBokehDragStateChange,
                                        liveSensorPreview = liveSensorPreview,
                                        onToggleLiveSensorPreview = { liveSensorPreview = !liveSensorPreview },
                                        onApplyWallpaper = onApply
                                    )
                                    ProjectContextPanel.GLOBALS -> GlobalsTab(
                                        project = project,
                                        wallPalette = wallPalette,
                                        onSelectLayer = {
                                            activeContextPanel = null
                                            onSelectLayer(it)
                                        },
                                        onUpdateLayer = onUpdateLayer,
                                        onStageProject = onStageProject,
                                        onCommitProject = onCommitProject,
                                        onSaveFinished = onFlushSave
                                    )
                                    ProjectContextPanel.WEATHER_AUTOMATION -> WeatherAutomationContextPanel(
                                        project = project,
                                        onStage = onStageProject,
                                        onCommit = onCommitProject,
                                        onSaveFinished = onFlushSave
                                    )
                                }
                            }
                        }
                    } else {
                        val effectiveEnv = env.copy(locked = previewLocked)
                        val reducedTabMotion = remember(ctx) { PrismMotion.isPowerSaveOrReducedMotion(ctx) }
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                if (reducedTabMotion) {
                                    fadeIn(PrismMotion.fastTween()) togetherWith fadeOut(PrismMotion.fastTween())
                                } else {
                                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                    (slideInHorizontally(
                                        animationSpec = tween(PrismMotion.Duration.BASE, easing = PrismMotion.Easing.Decelerate)
                                    ) { fullW -> dir * (fullW / 5) } + fadeIn(PrismMotion.baseTween())) togetherWith
                                        (slideOutHorizontally(
                                            animationSpec = tween(PrismMotion.Duration.FAST, easing = PrismMotion.Easing.Accelerate)
                                        ) { fullW -> -dir * (fullW / 6) } + fadeOut(PrismMotion.fastTween()))
                                }
                            },
                            label = "StudioTabTransition"
                        ) { activeTab ->
                        when (activeTab) {
                            StudioTab.LAYERS -> LayersTab(
                                project = project,
                                env = effectiveEnv,
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
                                env = effectiveEnv,
                                globalsMap = globalsMap,
                                wallPalette = wallPalette,
                                update = onUpdateSelected,
                                onSaveFinished = onFlushSave,
                                onImportSvg = onImportSvg,
                                onPickLayerImage = onPickLayerImage,
                                onAddCustomAsset = onAddCustomAsset,
                                onRenameCustomAsset = { oldAlias, newAlias ->
                                    val clean = newAlias.trim().lowercase()
                                    val oldVal = project.customAssets[oldAlias]
                                    if (clean.isNotEmpty() && clean != oldAlias && oldVal != null) {
                                        val nextMap = (project.customAssets - oldAlias) + (clean to oldVal)
                                        val nextLayers = project.layers.mapAllNodes { ln ->
                                            when {
                                                ln.imageUri == oldAlias -> ln.copy(imageUri = clean)
                                                else -> ln
                                            }
                                        }
                                        onCommitProject(project.copy(customAssets = nextMap, layers = nextLayers))
                                    }
                                },
                                onRemoveCustomAsset = { alias ->
                                    onCommitProject(project.copy(customAssets = project.customAssets - alias))
                                }
                            )
                            StudioTab.SHAPE_FONT -> ShapeAndFontTab(
                                node = selected,
                                project = project,
                                env = effectiveEnv,
                                globalsMap = globalsMap,
                                importedFonts = importedFonts,
                                hasFontFolder = hasFontFolder,
                                update = onUpdateSelected,
                                onSaveFinished = onFlushSave,
                                onImportFont = onImportFont,
                                onPickFontFolder = onPickFontFolder,
                                onRefreshFontFolder = onRefreshFontFolder
                            )
                            StudioTab.GLASS_COLOR -> GlassAndColorTab(
                                allLayers = project.layers,
                                project = project,
                                env = effectiveEnv,
                                globalsMap = globalsMap,
                                colorGlobals = project.globals.filter { it.type == GlobalType.COLOR },
                                node = selected,
                                wallPalette = wallPalette,
                                onSelect = { onSelectLayer(it) },
                                update = onUpdateSelected,
                                onSaveFinished = onFlushSave,
                                onBlurDragStateChange = onBlurDragStateChange
                            )
                            StudioTab.POSITION_TOUCH -> PositionAndTouchTab(
                                node = selected,
                                project = project,
                                env = effectiveEnv,
                                globalsMap = globalsMap,
                                previewLocked = previewLocked,
                                testTouchMode = testTouchMode,
                                onTogglePreviewLocked = { previewLocked = !previewLocked },
                                onToggleTestTouch = { testTouchMode = !testTouchMode },
                                onTestAction = ::runLayerTouch,
                                onOpenGlobalsPanel = { activeContextPanel = ProjectContextPanel.GLOBALS },
                                onSelectLayer = onSelectLayer,
                                onCommit = onCommitProject,
                                update = onUpdateSelected,
                                onSaveFinished = onFlushSave
                            )
                        }
                        }
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
                    Text(if (project.isWidget) "Modelos de Widget" else "Modelos Prontos", color = PrismColors.PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Escolha um layout base profissional. Você poderá ajustar cada camada depois.",
                        color = PrismColors.MutedZinc,
                        fontSize = 12.sp
                    )
                    (if (project.isWidget) Presets.allWidgets else Presets.all).forEach { preset ->
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

