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


@Composable
internal fun ContentTab(
    node: LayerNode?,
    project: Project,
    env: Env,
    globalsMap: Map<String, String>,
    wallPalette: List<Int>,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onImportSvg: () -> Unit,
    onPickLayerImage: () -> Unit = {},
    onAddCustomAsset: (String) -> Unit = {},
    onRenameCustomAsset: (String, String) -> Unit = { _, _ -> },
    onRemoveCustomAsset: (String) -> Unit = {}
) {
    if (node == null) {
        Text("Selecione uma camada na tela ou na aba Camadas", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
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

        when (node.type) {
            LayerType.TEXT -> {
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
                val liveResult = remember(node.text, env, liveNow, globalsMap) {
                    Dynamic.eval(node.text, env, liveNow, globalsMap)
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
            LayerType.ICON -> {
                StudioSection(
                    title = "Ícones Vetoriais Nativos (${Icons.names.size})",
                    summary = iconLabel(node.icon),
                    initiallyExpanded = true
                ) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(114.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gridItems(Icons.names, key = { it }) { ic ->
                            val sel = node.icon == ic && node.fxAsset.isBlank()
                            val resolvedGlyph = if (ic == "weather") Icons.weatherIcon(env.weatherCode, env.isDay) else ic
                            val pathStr = Icons.builtIn[resolvedGlyph] ?: Icons.builtIn.getValue("star")
                            Column(
                                modifier = Modifier
                                    .width(74.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) PrismColors.PureWhite else Color(0x14FFFFFF))
                                    .border(1.dp, if (sel) PrismColors.PureWhite else PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        update { it.copy(icon = ic, fxAsset = "") }
                                        onSaveFinished()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                VectorPathPreview(
                                    paths = listOf(pathStr),
                                    srcW = 24f,
                                    srcH = 24f,
                                    tint = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = iconLabel(ic),
                                    fontSize = 9.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x16FFFFFF))
                            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { showIconFormulaDialog = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Fórmula direta no Ícone (opcional)", fontSize = 10.sp, color = PrismColors.MutedZinc)
                            Text(node.icon, fontSize = 12.sp, color = PrismColors.PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FxCalculatorButton(active = '$' in node.icon || '{' in node.icon, onClick = { showIconFormulaDialog = true })
                    }
                }
                NamedAssetsLibrarySection(
                    node = node,
                    project = project,
                    env = env,
                    globalsMap = globalsMap,
                    update = update,
                    onSaveFinished = onSaveFinished,
                    onAddCustomAsset = onAddCustomAsset,
                    onRenameCustomAsset = onRenameCustomAsset,
                    onRemoveCustomAsset = onRemoveCustomAsset
                )
            }
            LayerType.SVG -> {
                StudioSection(
                    title = "Vetor SVG da Camada",
                    summary = "${node.svgData.size} traço(s)",
                    initiallyExpanded = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x16FFFFFF))
                                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (node.svgData.isNotEmpty()) {
                                VectorPathPreview(
                                    paths = node.svgData,
                                    srcW = node.svgW,
                                    srcH = node.svgH,
                                    tint = PrismColors.PureWhite,
                                    modifier = Modifier.size(34.dp)
                                )
                            } else {
                                Icon(MIcons.Rounded.Code, null, tint = PrismColors.MutedZinc)
                            }
                        }
                        Button(
                            onClick = onImportSvg,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Substituir arquivo SVG", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    }
                }
                NamedAssetsLibrarySection(
                    node = node,
                    project = project,
                    env = env,
                    globalsMap = globalsMap,
                    update = update,
                    onSaveFinished = onSaveFinished,
                    onAddCustomAsset = onAddCustomAsset,
                    onRenameCustomAsset = onRenameCustomAsset,
                    onRemoveCustomAsset = onRemoveCustomAsset
                )
            }
            LayerType.IMAGE -> {
                Button(
                    onClick = onPickLayerImage,
                    colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Escolher Foto / PNG / WebP para Camada", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                NamedAssetsLibrarySection(
                    node = node,
                    project = project,
                    env = env,
                    globalsMap = globalsMap,
                    update = update,
                    onSaveFinished = onSaveFinished,
                    onAddCustomAsset = onAddCustomAsset,
                    onRenameCustomAsset = onRenameCustomAsset,
                    onRemoveCustomAsset = onRemoveCustomAsset
                )
            }
            LayerType.SHAPE -> {
                if (node.shape == ShapeKind.BAR || node.shape == ShapeKind.RING) {
                    StudioSection(
                        title = "Dado de Progresso (${shapeLabel(node.shape)})",
                        summary = node.value,
                        initiallyExpanded = true
                    ) {
                        FocusedFieldTrigger(
                            label = "Variável / Fórmula Progresso (0..100)",
                            value = node.value,
                            placeholder = "\$bi(level)\$ ou bat",
                            onClick = { showValueDialog = true }
                        )
                        QuickPresetRow(
                            items = listOf(
                                "Bateria" to { update { it.copy(value = "\$bi(level)\$") }; onSaveFinished() },
                                "Dia %" to { update { it.copy(value = "dayprog") }; onSaveFinished() },
                                "Hora %" to { update { it.copy(value = "hourprog") }; onSaveFinished() }
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
                } else {
                    Text(
                        text = "Forma \"${shapeLabel(node.shape)}\" selecionada. Ajuste Geometria, Largura, Altura e Cantos na aba Forma & Fonte.",
                        fontSize = 11.sp,
                        color = PrismColors.MutedZinc
                    )
                }
            }
            LayerType.STACK -> {
                StudioSection(title = "Organização da Pilha", summary = "${dirLabel(node.dir)} · ${node.children.size} itens", initiallyExpanded = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dir.entries.forEach { d ->
                            val sel = node.dir == d
                            FilterChip(selected = sel, onClick = { update { it.copy(dir = d) }; onSaveFinished() }, label = { Text(dirLabel(d), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
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
            LayerType.DEPTH -> {
                Text(
                    text = "Camada de recorte 3D automático em primeiro plano. Configure os efeitos de Bokeh/Color Pop no botão \"Fundo & 3D\" acima.",
                    fontSize = 11.sp,
                    color = PrismColors.MutedZinc
                )
            }
        }
    }

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

    if (showIconFormulaDialog) {
        FxExpressionDialog(
            title = "Fórmula de Ícone",
            initialValue = node.icon,
            placeholder = "weather",
            exampleHint = "Ex: \$if(bi(charging), \"bolt\", \"battery\")\$ ou \$if(si(mplaying), \"music\", \"lock\")\$",
            env = env,
            globals = globalsMap,
            projectGlobals = project.globals,
            onDismiss = { showIconFormulaDialog = false },
            onConfirm = { v ->
                update { it.copy(icon = v.trim().ifEmpty { "weather" }) }
                onSaveFinished()
                showIconFormulaDialog = false
            }
        )
    }
}

@Composable
internal fun TouchControlsBlock(
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

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

