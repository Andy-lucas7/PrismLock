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


internal fun globalTypeLabel(t: GlobalType): String = when (t) {
    GlobalType.COLOR -> "Cor"
    GlobalType.SWITCH -> "Interruptor"
    GlobalType.NUMBER -> "Número"
    GlobalType.TEXT -> "Texto"
    GlobalType.LIST -> "Lista"
}

internal fun List<LayerNode>.mapAllNodes(f: (LayerNode) -> LayerNode): List<LayerNode> = map { n ->
    val next = f(n)
    if (next.children.isEmpty()) next else next.copy(children = next.children.mapAllNodes(f))
}

@Composable
internal fun GlobalsTab(
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
                            GlobalType.TEXT, GlobalType.LIST -> targetLayers.filter { it.type == LayerType.TEXT || it.type == LayerType.IMAGE || it.type == LayerType.ICON }
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
                                        GlobalType.TEXT, GlobalType.LIST -> {
                                            if (layer.type == LayerType.IMAGE || layer.type == LayerType.ICON) layer.fxAsset == gvExpr else gvExpr in layer.text
                                        }
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
                                                    if (it.type == LayerType.IMAGE || it.type == LayerType.ICON) {
                                                        it.copy(fxAsset = if (bound) "" else gvExpr)
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
internal fun CreateGlobalDialog(
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

