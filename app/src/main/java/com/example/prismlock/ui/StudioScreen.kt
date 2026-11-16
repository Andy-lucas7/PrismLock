package com.example.prismlock.ui

import android.graphics.Bitmap
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
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
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.model.Align
import com.example.prismlock.model.Anchor
import com.example.prismlock.model.Blend
import com.example.prismlock.model.Cond
import com.example.prismlock.model.Dir
import com.example.prismlock.model.Fill
import com.example.prismlock.model.GradientKind
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Presets
import com.example.prismlock.model.Project
import com.example.prismlock.model.ShapeKind
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.find
import com.example.prismlock.model.flatten
import com.example.prismlock.model.insertAfter
import com.example.prismlock.model.mapNode
import com.example.prismlock.model.move
import com.example.prismlock.model.removeNode
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

private enum class StudioTab(val label: String) {
    BACKGROUND("Fundo & 3D"),
    LAYERS("Camadas"),
    CONTENT("Conteúdo"),
    TYPE("Tipografia"),
    PAINT("Vidro / Cor")
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
    onImportFont: () -> Unit,
    onImportSvg: () -> Unit
) {
    var tab by remember(project.id) { mutableStateOf(StudioTab.BACKGROUND) }
    var sheetExpanded by remember(project.id) { mutableStateOf(true) }
    var showAddMenu by remember(project.id) { mutableStateOf(false) }
    var showOverflowMenu by remember(project.id) { mutableStateOf(false) }
    var showRenameDialog by remember(project.id) { mutableStateOf(false) }
    var showSaveAsDialog by remember(project.id) { mutableStateOf(false) }
    var showPresetsModal by remember(project.id) { mutableStateOf(false) }
    var pendingPreset by remember(project.id) { mutableStateOf<Pair<String, () -> List<LayerNode>>?>(null) }
    var isActivelyDraggingLayer by remember(project.id) { mutableStateOf(false) }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (isActivelyDraggingLayer) 0f else 1f,
        animationSpec = tween(180),
        label = "EditorChromeAlpha"
    )

    val selected = project.layers.find(selectedId)

    Box(Modifier.fillMaxSize().background(PrismColors.PureBlack)) {
        InteractiveLockPreview(
            renderer = renderer,
            project = project,
            selectedId = selectedId,
            bgBitmap = bgBitmap,
            depthBitmap = depthBitmap,
            activePreviewUri = activePreviewUri,
            env = env,
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

                // Right: Collapse inspector, Layer Lock, 3-dots menu
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
                                leadingIcon = { Icon(MIcons.Rounded.FileDownload, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(18.dp)) },
                                text = { Text("Exportar Arquivo (.prism)", color = PrismColors.MutedZinc, fontSize = 13.sp) },
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
                            onUpdateNode = onUpdateSelected,
                            onStage = onStageProject,
                            onCommit = onCommitProject,
                            onSaveFinished = onFlushSave,
                            onToggleDepth = onToggleDepth,
                            onSelectPreview = onSelectPreview,
                            onPickFolder = onPickFolder,
                            onPickImage = onPickImage,
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
                            env = env,
                            update = onUpdateSelected,
                            onImportSvg = onImportSvg
                        )
                        StudioTab.TYPE -> TypeTab(
                            node = selected,
                            importedFonts = importedFonts,
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave,
                            onImportFont = onImportFont
                        )
                        StudioTab.PAINT -> PaintTab(
                            allLayers = project.layers,
                            node = selected,
                            wallPalette = wallPalette,
                            onSelect = { onSelectLayer(it) },
                            update = onUpdateSelected,
                            onSaveFinished = onFlushSave
                        )
                    }
                }
            }
        }

        // Rename Current Screen dialog
        if (showRenameDialog) {
            var editedTitle by remember(project.id, project.title) { mutableStateOf(project.title) }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = Color(0xEB111115),
                titleContentColor = PrismColors.PureWhite,
                textContentColor = PrismColors.MutedZinc,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Renomear Tela", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                text = {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        placeholder = { Text("Default Screen", color = PrismColors.MutedZinc) },
                        singleLine = true,
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
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRenameTheme(editedTitle.trim().ifBlank { "Default Screen" })
                            showRenameDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Salvar Nome", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancelar", color = PrismColors.MutedZinc)
                    }
                }
            )
        }

        // Save as New Theme dialog
        if (showSaveAsDialog) {
            var newTitle by remember(project.title) { mutableStateOf("${project.title} (Cópia)") }
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = false },
                containerColor = PrismColors.CarbonBlack,
                titleContentColor = PrismColors.PureWhite,
                shape = RoundedCornerShape(22.dp),
                title = { Text("Salvar como Novo Tema", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                text = {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Nome do tema") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveAsNew(newTitle.trim().ifEmpty { "Novo Tema" })
                            showSaveAsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismColors.PureWhite,
                            contentColor = PrismColors.PureBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Salvar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAsDialog = false }) {
                        Text("Cancelar", color = PrismColors.MutedZinc)
                    }
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

private fun layerSubtitle(node: LayerNode, env: Env): String = when (node.type) {
    LayerType.TEXT -> "${node.text}  →  ${Dynamic.eval(node.text, env, LocalDateTime.now())}"
    LayerType.STACK -> "Pilha ${dirLabel(node.dir).lowercase()} · ${node.children.size} itens"
    LayerType.SHAPE -> "Forma ${shapeLabel(node.shape)}"
    LayerType.ICON -> "Ícone ${iconLabel(node.icon)}"
    LayerType.SVG -> "Vetor SVG"
    LayerType.DEPTH -> "Recorte 3D automático"
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
                        Text(layerSubtitle(node, env), color = PrismColors.MutedZinc, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun ContentTab(node: LayerNode?, env: Env, update: ((LayerNode) -> LayerNode) -> Unit, onImportSvg: () -> Unit) {
    if (node == null) {
        Text("Selecione uma camada na tela ou na aba Camadas", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = node.name,
            onValueChange = { v -> update { it.copy(name = v) } },
            label = { Text("Nome da camada") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (node.type == LayerType.TEXT) {
            OutlinedTextField(
                value = node.text,
                onValueChange = { v -> update { it.copy(text = v) } },
                label = { Text("Texto / Fórmula Dinâmica") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Resultado ao vivo: ${Dynamic.eval(node.text, env, LocalDateTime.now())}", fontSize = 12.sp, color = PrismColors.PureWhite)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dynamic.tags.forEach { t ->
                    AssistChip(
                        onClick = { update { it.copy(text = it.text + t) } },
                        label = { Text(t, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x16FFFFFF), labelColor = PrismColors.PureWhite)
                    )
                }
            }
        }
        if (node.type == LayerType.SHAPE) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShapeKind.entries.forEach { k ->
                    val sel = node.shape == k
                    FilterChip(selected = sel, onClick = { update { it.copy(shape = k) } }, label = { Text(shapeLabel(k)) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
            if (node.shape == ShapeKind.BAR || node.shape == ShapeKind.RING) {
                OutlinedTextField(value = node.value, onValueChange = { v -> update { it.copy(value = v) } }, label = { Text("Variável 0..100 (bat, dayprog, hourprog)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (node.type == LayerType.ICON) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icons.names.forEach { ic ->
                    val sel = node.icon == ic
                    FilterChip(selected = sel, onClick = { update { it.copy(icon = ic) } }, label = { Text(iconLabel(ic)) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
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
    if (node == null) {
        Text("Selecione uma camada", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (node.type == LayerType.TEXT) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onImportFont,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("+ Fonte TTF/OTF", fontSize = 11.sp, color = PrismColors.PureWhite) }
                (FontManager.systemFamilies + importedFonts).forEach { f ->
                    val sel = node.font == f
                    FilterChip(selected = sel, onClick = { update { it.copy(font = f) }; onSaveFinished() }, label = { Text(fontDisplayLabel(f), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
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
                Dir.entries.forEach { d ->
                    val sel = node.dir == d
                    FilterChip(selected = sel, onClick = { update { it.copy(dir = d) }; onSaveFinished() }, label = { Text(dirLabel(d)) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
            PrecisionSliderField("Espaçamento", node.spacing, -60f..160f, "px", 0, onSaveFinished) { v -> update { it.copy(spacing = v) } }
        }
        if (node.type == LayerType.SHAPE || node.type == LayerType.ICON || node.type == LayerType.SVG) {
            PrecisionSliderField("Largura", node.w, 16f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(w = v) } }
            PrecisionSliderField("Altura", node.h, 2f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(h = v) } }
            PrecisionSliderField("Canto", node.corner, 0f..300f, "px", 0, onSaveFinished) { v -> update { it.copy(corner = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Align.entries.forEach { a ->
                val sel = node.align == a
                FilterChip(selected = sel, onClick = { update { it.copy(align = a) }; onSaveFinished() }, label = { Text(alignLabel(a), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
            }
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
                    val sel = item.id == node?.id
                    FilterChip(selected = sel, onClick = { onSelect(item.id) }, label = { Text(item.name, fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
        }
        if (node == null) {
            Text("Selecione uma camada", color = PrismColors.MutedZinc, fontSize = 12.sp)
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Fill.SOLID to "Sólido",
                Fill.GRADIENT to "Gradiente",
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
            PrecisionSliderField("Blur Gauss", node.glass.blur, 0f..80f, "px", 1, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
            PrecisionSliderField("Saturação", node.glass.saturation, 1f..1.8f, "x", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(saturation = v)) } }
            PrecisionSliderField("Granulação", node.glass.grain, 0f..0.15f, "", 3, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(grain = v)) } }
            PrecisionSliderField("Borda Suave", node.glass.rim.coerceIn(0f, 1f), 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
            ColorPickerPanel("Tint Translúcido", node.glass.tint, wallPalette) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
        }
        if (node.fill == Fill.CRYSTAL) {
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

        PrecisionSliderField("Opacidade", node.opacity, 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(opacity = v) } }

        Text("Contorno / Borda", fontSize = 12.sp, color = PrismColors.MutedZinc, fontWeight = FontWeight.Medium)
        PrecisionSliderField("Espessura", node.strokeWidth, 0f..24f, "px", 1, onSaveFinished) { v -> update { it.copy(strokeWidth = v) } }
        if (node.strokeWidth > 0f || node.fill == Fill.HOLLOW) {
            ColorPickerPanel("Cor Contorno", node.strokeColor, wallPalette) { c -> update { it.copy(strokeColor = c) } }
        }

        Text("Sombra Projetada", fontSize = 12.sp, color = PrismColors.MutedZinc, fontWeight = FontWeight.Medium)
        PrecisionSliderField("Raio Sombra", node.shadowRadius, 0f..50f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowRadius = v) } }
        if (node.shadowRadius > 0f) {
            PrecisionSliderField("Sombra X", node.shadowDx, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDx = v) } }
            PrecisionSliderField("Sombra Y", node.shadowDy, -40f..40f, "px", 1, onSaveFinished) { v -> update { it.copy(shadowDy = v) } }
            ColorPickerPanel("Cor Sombra", node.shadowColor, wallPalette) { c -> update { it.copy(shadowColor = c) } }
        }

        Text("Modo de Mesclagem", fontSize = 12.sp, color = PrismColors.MutedZinc)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Blend.entries.forEach { b ->
                val sel = node.blend == b
                FilterChip(selected = sel, onClick = { update { it.copy(blend = b) }; onSaveFinished() }, label = { Text(blendLabel(b), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
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

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PrismColors.PureBlack,
        checkedTrackColor = PrismColors.PureWhite,
        uncheckedThumbColor = PrismColors.MutedZinc,
        uncheckedTrackColor = PrismColors.TrackZinc
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val is3dOn = !isVideo && project.isDepthEnabledFor(imageKey)
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

        if (node != null) {
            HorizontalDivider(color = PrismColors.GlassBorder)
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
            Text("Regra de Visibilidade", fontSize = 12.sp, color = PrismColors.MutedZinc)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cond.entries.forEach { c ->
                    val sel = node.cond == c
                    FilterChip(selected = sel, onClick = { onUpdateNode { it.copy(cond = c) }; onSaveFinished() }, label = { Text(condLabel(c), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                }
            }
            HorizontalDivider(color = PrismColors.GlassBorder)
        }

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

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = cityInput, onValueChange = { cityInput = it }, label = { Text("Cidade (Open-Meteo)") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        val g = EnvProvider.geocode(cityInput)
                        if (g != null) onCommit(project.copy(lat = g.first, lon = g.second, city = g.third))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack)
            ) { Text("Buscar", fontWeight = FontWeight.SemiBold) }
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
