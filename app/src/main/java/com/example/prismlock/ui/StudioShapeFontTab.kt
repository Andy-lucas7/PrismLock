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
internal fun ShapeAndFontTab(
    node: LayerNode?,
    project: Project,
    env: Env,
    globalsMap: Map<String, String>,
    importedFonts: List<String>,
    hasFontFolder: Boolean,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onImportFont: () -> Unit,
    onPickFontFolder: () -> Unit,
    onRefreshFontFolder: (Boolean) -> Unit
) {
    if (node == null) {
        Text("Selecione uma camada", color = PrismColors.MutedZinc, fontSize = 12.sp)
        return
    }
    var showFontListDialog by remember { mutableStateOf(false) }
    // Already resolved off the main thread by the caller; rescanning here stalled every recomposition.
    val allFonts = importedFonts

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (node.type) {
            LayerType.TEXT -> {
                StudioSection(title = "Fonte, Peso & Tamanho", summary = fontDisplayLabel(node.font), initiallyExpanded = true) {
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
                    PrecisionSliderField(
                        label = "Tamanho",
                        value = node.size,
                        range = 18f..420f,
                        suffix = "px",
                        decimals = 0,
                        onValueChangeFinished = onSaveFinished,
                        fxExpression = node.fxSize,
                        fxHint = "Ex: \$if(bi(charging), 220, 180)\$ ou \$gv(tamanho)\$",
                        env = env,
                        globals = globalsMap,
                        projectGlobals = project.globals,
                        onFxChange = { expr ->
                            update { it.copy(fxSize = expr) }
                            onSaveFinished()
                        }
                    ) { v -> update { it.copy(size = v) } }
                    val fontAxes = remember(node.font) { FontManager.axes(node.font) }
                    val wghtAxis = fontAxes.firstOrNull { it.tag == "wght" }
                    val weightRange = if (wghtAxis != null) {
                        val minW = wghtAxis.min.coerceIn(1f, 999f)
                        val maxW = wghtAxis.max.coerceIn(minW + 1f, 1000f)
                        minW..maxW
                    } else 100f..900f
                    PrecisionSliderField("Peso", node.weight.toFloat().coerceIn(weightRange), weightRange, "", 0, onSaveFinished) { v ->
                        update { it.copy(weight = v.roundToInt(), axes = if ("wght" in it.axes) it.axes - "wght" else it.axes) }
                    }
                }

                StudioSection(
                    title = "Elasticidade, Kerning & Alinhamento",
                    summary = "${"%.2f".format(node.stretch)}x · ${alignLabel(node.align)}",
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
                    FontManager.axes(node.font).filter { it.tag != "wght" }.forEach { ax ->
                        val cur = node.axes[ax.tag] ?: ax.def
                        PrecisionSliderField("Eixo ${ax.tag}", cur, ax.min..ax.max, "", 1, onSaveFinished) { v -> update { it.copy(axes = it.axes + (ax.tag to v)) } }
                    }
                    Text("Alinhamento", fontSize = 11.sp, color = PrismColors.MutedZinc)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Align.entries.forEach { a ->
                            val sel = node.align == a
                            FilterChip(selected = sel, onClick = { update { it.copy(align = a) }; onSaveFinished() }, label = { Text(alignLabel(a), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                        }
                    }
                }
            }
            LayerType.SHAPE, LayerType.ICON, LayerType.SVG, LayerType.IMAGE -> {
                if (node.type == LayerType.SHAPE) {
                    StudioSection(title = "Geometria da Forma", summary = shapeLabel(node.shape), initiallyExpanded = true) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShapeKind.entries.forEach { k ->
                                val sel = node.shape == k
                                FilterChip(selected = sel, onClick = { update { it.copy(shape = k) }; onSaveFinished() }, label = { Text(shapeLabel(k), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                            }
                        }
                    }
                }
                StudioSection(title = "Dimensões & Cantos", summary = "${node.w.roundToInt()}×${node.h.roundToInt()}", initiallyExpanded = true) {
                    QuickPresetRow(
                        items = listOf(
                            "Quadrado" to { update { it.copy(w = 180f, h = 180f, corner = 32f) }; onSaveFinished() },
                            "Pílula" to { update { it.copy(w = 340f, h = 68f, corner = 34f) }; onSaveFinished() },
                            "Card" to { update { it.copy(w = 460f, h = 180f, corner = 36f) }; onSaveFinished() },
                            "Barra Fina" to { update { it.copy(w = 400f, h = 16f, corner = 8f) }; onSaveFinished() }
                        )
                    )
                    PrecisionSliderField(
                        label = "Largura",
                        value = node.w,
                        range = 16f..1080f,
                        suffix = "px",
                        decimals = 0,
                        onValueChangeFinished = onSaveFinished,
                        fxExpression = node.fxSize,
                        fxHint = "Ex: \$if(bi(charging), 260, 200)\$ ou \$bi(level) * 4\$ (escala largura e altura)",
                        env = env,
                        globals = globalsMap,
                        projectGlobals = project.globals,
                        onFxChange = { expr ->
                            update { it.copy(fxSize = expr) }
                            onSaveFinished()
                        }
                    ) { v -> update { it.copy(w = v) } }
                    PrecisionSliderField("Altura", node.h, 2f..1080f, "px", 0, onSaveFinished) { v -> update { it.copy(h = v) } }
                    PrecisionSliderField("Cantos", node.corner, 0f..300f, "px", 0, onSaveFinished) { v -> update { it.copy(corner = v) } }
                }
            }
            LayerType.STACK -> {
                StudioSection(title = "Pilha, Espaçamento & Alinhamento", summary = "${dirLabel(node.dir)} · ${alignLabel(node.align)}", initiallyExpanded = true) {
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
                    Text("Alinhamento", fontSize = 11.sp, color = PrismColors.MutedZinc)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Align.entries.forEach { a ->
                            val sel = node.align == a
                            FilterChip(selected = sel, onClick = { update { it.copy(align = a) }; onSaveFinished() }, label = { Text(alignLabel(a), fontSize = 11.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                        }
                    }
                }
            }
            LayerType.DEPTH -> {
                Text("Camada de profundidade 3D automática não possui forma manual.", color = PrismColors.MutedZinc, fontSize = 12.sp)
            }
        }
    }
}

