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


internal fun layerTypeIcon(type: LayerType): ImageVector = when (type) {
    LayerType.TEXT -> MIcons.Rounded.TextFields
    LayerType.STACK -> MIcons.Rounded.ViewAgenda
    LayerType.SHAPE -> MIcons.Rounded.Category
    LayerType.ICON -> MIcons.Rounded.WbSunny
    LayerType.SVG -> MIcons.Rounded.Code
    LayerType.IMAGE -> MIcons.Rounded.Wallpaper
    LayerType.DEPTH -> MIcons.Rounded.Layers
}

internal fun layerSubtitle(node: LayerNode, env: Env, globals: Map<String, String> = emptyMap()): String {
    val base = when (node.type) {
        LayerType.TEXT -> "${node.text}  →  ${Dynamic.eval(node.text, env, LocalDateTime.now(), globals)}"
        LayerType.STACK -> "Pilha ${dirLabel(node.dir).lowercase()} · ${node.children.size} itens"
        LayerType.SHAPE -> "Forma ${shapeLabel(node.shape)}"
        LayerType.ICON -> "Ícone ${iconLabel(node.icon)}"
        LayerType.SVG -> "Vetor SVG"
        LayerType.IMAGE -> "Imagem / Ativo Dinâmico"
        LayerType.DEPTH -> "Recorte 3D · camadas abaixo ficam à frente"
    }
    return if (node.touch.isActive) "$base  · ⚡ ${TouchActionExecutor.describe(node.touch)}" else base
}

@Composable
internal fun LayersTab(
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
            enter = fadeIn(PrismMotion.fastTween()) + scaleIn(PrismMotion.fastTween(), initialScale = PrismMotion.Scale.ENTER),
            exit = fadeOut(PrismMotion.fastTween()) + scaleOut(PrismMotion.fastTween(), targetScale = PrismMotion.Scale.ENTER)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val hasDepthLayer = project.layers.any { it.type == LayerType.DEPTH }
                fun add(node: LayerNode) {
                    if (node.type == LayerType.DEPTH) {
                        val existing = project.layers.firstOrNull { it.type == LayerType.DEPTH }
                        if (existing != null) {
                            onSelect(existing.id)
                            onToggleAdd()
                            return
                        }
                        onCommit(project.copy(layers = project.layers + node))
                        onSelect(node.id)
                        onToggleAdd()
                        return
                    }
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
                AssistChip(onClick = { add(LayerNode(name = "Imagem Dinâmica", type = LayerType.IMAGE, w = 220f, h = 220f, corner = 36f)) }, label = { Text("Imagem / Ativo", fontSize = 11.sp) }, colors = chipColors)
                AssistChip(onClick = { onToggleAdd(); onImportSvg() }, label = { Text("SVG", fontSize = 11.sp) }, colors = chipColors)
                if (!project.isWidget && !hasDepthLayer) {
                    AssistChip(onClick = { add(LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)) }, label = { Text("Profundidade 3D", fontSize = 11.sp) }, colors = chipColors)
                }
            }
        }

        val depthActiveInList = !project.isWidget && project.depth && project.layers.any { it.type == LayerType.DEPTH && !it.hidden }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(flat, key = { it.first.id }) { (node, depth) ->
                val sel = node.id == selectedId
                val isFrontOfSubject = depthActiveInList && node.type != LayerType.DEPTH && depth == 0 && project.layers.isAboveSubject(node.id)
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                node.name,
                                color = PrismColors.PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isFrontOfSubject) {
                                Text(
                                    "À frente",
                                    color = PrismColors.PureWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x26FFFFFF))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
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
                    if (node.type != LayerType.DEPTH) {
                        IconButton(onClick = { onCommit(project.copy(layers = project.layers.insertAfter(node.id, node.copyDeep()))) }, Modifier.size(26.dp)) {
                            Icon(MIcons.Rounded.ContentCopy, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = {
                        val remaining = project.layers.removeNode(node.id)
                        onCommit(project.copy(layers = remaining))
                        if (selectedId != null && remaining.find(selectedId) == null) onSelect(null)
                    }, Modifier.size(26.dp)) {
                        Icon(MIcons.Rounded.DeleteOutline, null, tint = PrismColors.MutedZinc, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun VectorPathPreview(
    paths: List<String>,
    srcW: Float,
    srcH: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val composePath = remember(paths) {
        Icons.path(paths).asComposePath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(modifier = modifier) {
        val sw = srcW.coerceAtLeast(1f)
        val sh = srcH.coerceAtLeast(1f)
        val s = minOf(size.width / sw, size.height / sh) * 0.86f
        val dx = (size.width - sw * s) * 0.5f
        val dy = (size.height - sh * s) * 0.5f
        translate(left = dx, top = dy) {
            scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = composePath, color = tint)
            }
        }
    }
}

@Composable
internal fun NamedAssetsLibrarySection(
    node: LayerNode,
    project: Project,
    env: Env,
    globalsMap: Map<String, String>,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onAddCustomAsset: (String) -> Unit,
    onRenameCustomAsset: (String, String) -> Unit,
    onRemoveCustomAsset: (String) -> Unit
) {
    val ctx = LocalContext.current
    var showNewAliasDialog by remember { mutableStateOf(false) }
    var renameAliasTarget by remember { mutableStateOf<String?>(null) }
    var showFxAssetDialog by remember(node.id) { mutableStateOf(false) }

    StudioSection(
        title = "Biblioteca Visual de Ativos Nomeados",
        summary = if (node.fxAsset.isNotBlank()) "ƒx Ativo" else "${project.customAssets.size} ativo(s)",
        initiallyExpanded = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SVGs, PNGs e Fotos para \$if(...)\$",
                fontSize = 11.sp,
                color = PrismColors.MutedZinc,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showNewAliasDialog = true },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(MIcons.Rounded.Add, null, tint = PrismColors.PureWhite, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("Adicionar Ativo", fontSize = 10.sp, color = PrismColors.PureWhite)
            }
        }

        if (project.customAssets.isNotEmpty()) {
            val entries = remember(project.customAssets) { project.customAssets.entries.toList() }
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.key }) { (alias, spec) ->
                    val decodedSvg = remember(spec) { Icons.decodeSvgAsset(spec) }
                    val thumb by produceState<Bitmap?>(initialValue = null, spec) {
                        value = if (decodedSvg != null) null else withContext(Dispatchers.IO) {
                            WallpaperCarousel.loadCustomAssetBitmap(ctx, spec, 128, 128)
                        }
                    }
                    val sel = node.fxAsset == alias ||
                        node.imageUri == alias ||
                        (node.type == LayerType.ICON && node.icon == alias)
                    Column(
                        modifier = Modifier
                            .width(82.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) PrismColors.PureWhite else Color(0x16FFFFFF))
                            .border(1.dp, if (sel) PrismColors.PureWhite else PrismColors.GlassBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                update { cur ->
                                    when (cur.type) {
                                        LayerType.IMAGE -> cur.copy(imageUri = alias, fxAsset = "")
                                        LayerType.SVG -> if (decodedSvg != null) {
                                            cur.copy(svgData = decodedSvg.data, svgW = decodedSvg.w, svgH = decodedSvg.h, fxAsset = "")
                                        } else {
                                            cur.copy(fxAsset = alias)
                                        }
                                        LayerType.ICON -> cur.copy(fxAsset = alias)
                                        else -> cur.copy(fxAsset = alias)
                                    }
                                }
                                onSaveFinished()
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (decodedSvg != null) {
                                VectorPathPreview(
                                    paths = decodedSvg.data,
                                    srcW = decodedSvg.w,
                                    srcH = decodedSvg.h,
                                    tint = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                    modifier = Modifier.size(34.dp).align(Alignment.Center)
                                )
                            } else if (thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(),
                                    contentDescription = alias,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .align(Alignment.Center)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    MIcons.Rounded.Wallpaper,
                                    contentDescription = null,
                                    tint = if (sel) PrismColors.PureBlack else PrismColors.MutedZinc,
                                    modifier = Modifier.size(24.dp).align(Alignment.Center)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (sel) Color(0x22000000) else Color(0x66000000))
                                    .clickable { onRemoveCustomAsset(alias) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    MIcons.Rounded.DeleteOutline,
                                    contentDescription = "Remover ativo",
                                    tint = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { renameAliasTarget = alias }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = alias,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) PrismColors.PureBlack else PrismColors.PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                MIcons.Rounded.Edit,
                                contentDescription = "Renomear apelido",
                                tint = if (sel) PrismColors.PureBlack.copy(alpha = 0.7f) else PrismColors.MutedZinc,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Controle Ativo Dinâmico (fxAsset) com botão calculadora ƒx
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x16FFFFFF))
                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                .clickable { showFxAssetDialog = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ativo / Ícone Dinâmico (fxAsset)", fontSize = 10.sp, color = PrismColors.MutedZinc)
                Text(
                    text = node.fxAsset.ifBlank { "Opcional: \$if(wi(code) > 50, \"chuva\", \"sol\")\$" },
                    fontSize = 12.sp,
                    color = if (node.fxAsset.isBlank()) PrismColors.MutedZinc else PrismColors.PureWhite,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FxCalculatorButton(
                active = node.fxAsset.isNotBlank(),
                onClick = { showFxAssetDialog = true }
            )
        }
    }

    if (showNewAliasDialog) {
        FocusedInputDialog(
            title = "Apelido do Ativo (SVG / Imagem)",
            initialValue = "",
            placeholder = "sol",
            exampleHint = "Ex: sol, lua, chuva, capa (em seguida escolha SVG, PNG ou WebP)",
            confirmLabel = "Escolher Arquivo",
            onDismiss = { showNewAliasDialog = false },
            onConfirm = { raw ->
                val alias = raw.trim().lowercase().replace(" ", "_").ifBlank { "ativo_${project.customAssets.size + 1}" }
                showNewAliasDialog = false
                onAddCustomAsset(alias)
            }
        )
    }

    renameAliasTarget?.let { oldAlias ->
        FocusedInputDialog(
            title = "Editar Apelido do Ativo",
            initialValue = oldAlias,
            placeholder = "chuva",
            exampleHint = "Use este apelido em \$if(..., \"$oldAlias\", \"sol\")\$",
            confirmLabel = "Renomear",
            onDismiss = { renameAliasTarget = null },
            onConfirm = { raw ->
                onRenameCustomAsset(oldAlias, raw)
                renameAliasTarget = null
            }
        )
    }

    if (showFxAssetDialog) {
        FxExpressionDialog(
            title = "Ativo / Ícone Dinâmico",
            initialValue = node.fxAsset,
            placeholder = "\$if(ai(isday), \"sol\", \"lua\")\$",
            exampleHint = "Retorne o apelido de um ativo (ex: \"sol\", \"chuva\") ou nome de ícone (ex: \"sun\", \"rain\")",
            env = env,
            globals = globalsMap,
            projectGlobals = project.globals,
            onDismiss = { showFxAssetDialog = false },
            onConfirm = { next ->
                update { it.copy(fxAsset = next) }
                onSaveFinished()
                showFxAssetDialog = false
            }
        )
    }
}

