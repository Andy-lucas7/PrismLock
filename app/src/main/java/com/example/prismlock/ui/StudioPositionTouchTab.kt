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
internal fun PositionAndTouchTab(
    node: LayerNode?,
    project: Project,
    env: Env,
    globalsMap: Map<String, String>,
    previewLocked: Boolean,
    testTouchMode: Boolean,
    onTogglePreviewLocked: () -> Unit,
    onToggleTestTouch: () -> Unit,
    onTestAction: (LayerNode) -> Unit,
    onOpenGlobalsPanel: () -> Unit,
    onSelectLayer: (String?) -> Unit,
    onCommit: (Project) -> Unit,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit
) {
    var showVisibilityFxDialog by remember(node?.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (node != null) {
            val parentStack = remember(node.id, project.layers) { project.layers.parentOf(node.id) }
            StudioSection(
                title = "Posição, Âncora & Transformação",
                summary = if (parentStack != null) "Pilha \"${parentStack.name}\"" else "${(node.x * 100).roundToInt()}%, ${(node.y * 100).roundToInt()}% · ${anchorLabel(node.anchor)}",
                initiallyExpanded = true
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
                            text = "Camada filha da pilha \"${parentStack.name}\". Posição base segue o grupo pai; use ƒx para deslocamento dinâmico.",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        var showOffsetXFx by remember { mutableStateOf(false) }
                        var showOffsetYFx by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showOffsetXFx = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (node.fxOffsetX.isBlank()) "ƒx Desloc. X" else "ƒx X Ativo", fontSize = 10.sp, color = PrismColors.PureWhite)
                        }
                        OutlinedButton(
                            onClick = { showOffsetYFx = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (node.fxOffsetY.isBlank()) "ƒx Desloc. Y" else "ƒx Y Ativo", fontSize = 10.sp, color = PrismColors.PureWhite)
                        }
                        if (showOffsetXFx) {
                            FxExpressionDialog(
                                title = "Deslocamento X FX (px)",
                                initialValue = node.fxOffsetX,
                                exampleHint = "Ex: \$if(gv(aberto) = 1, 0, -140)\$",
                                env = env,
                                globals = globalsMap,
                                projectGlobals = project.globals,
                                onDismiss = { showOffsetXFx = false },
                                onConfirm = { next ->
                                    update { it.copy(fxOffsetX = next) }
                                    onSaveFinished()
                                    showOffsetXFx = false
                                }
                            )
                        }
                        if (showOffsetYFx) {
                            FxExpressionDialog(
                                title = "Deslocamento Y FX (px)",
                                initialValue = node.fxOffsetY,
                                exampleHint = "Ex: \$if(gv(menu) = 1, 0, 80)\$",
                                env = env,
                                globals = globalsMap,
                                projectGlobals = project.globals,
                                onDismiss = { showOffsetYFx = false },
                                onConfirm = { next ->
                                    update { it.copy(fxOffsetY = next) }
                                    onSaveFinished()
                                    showOffsetYFx = false
                                }
                            )
                        }
                    }
                } else {
                    QuickPresetRow(
                        items = listOf(
                            "Topo Centro" to { update { it.copy(x = 0.5f, y = 0.16f, anchor = Anchor.TOP_CENTER) }; onSaveFinished() },
                            "Centro" to { update { it.copy(x = 0.5f, y = 0.5f, anchor = Anchor.CENTER) }; onSaveFinished() },
                            "Rodapé" to { update { it.copy(x = 0.5f, y = 0.84f, anchor = Anchor.BOTTOM_CENTER) }; onSaveFinished() }
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrecisionSliderField(
                                label = "Posição X",
                                value = node.x * 100f,
                                range = 0f..100f,
                                suffix = "%",
                                decimals = 1,
                                onValueChangeFinished = onSaveFinished,
                                fxExpression = node.fxOffsetX,
                                fxHint = "Deslocamento X em px. Ex: \$if(gv(aberto) = 1, 0, -140)\$",
                                env = env,
                                globals = globalsMap,
                                projectGlobals = project.globals,
                                onFxChange = { expr ->
                                    update { it.copy(fxOffsetX = expr) }
                                    onSaveFinished()
                                }
                            ) { v -> update { it.copy(x = v / 100f) } }
                        }
                        OutlinedButton(onClick = { update { it.copy(x = (it.x - 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(28.dp)) { Text("-1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                        OutlinedButton(onClick = { update { it.copy(x = (it.x + 1f / 1080f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(28.dp)) { Text("+1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrecisionSliderField(
                                label = "Posição Y",
                                value = node.y * 100f,
                                range = 0f..100f,
                                suffix = "%",
                                decimals = 1,
                                onValueChangeFinished = onSaveFinished,
                                fxExpression = node.fxOffsetY,
                                fxHint = "Deslocamento Y em px. Ex: \$if(gv(menu) = 1, 0, 80)\$",
                                env = env,
                                globals = globalsMap,
                                projectGlobals = project.globals,
                                onFxChange = { expr ->
                                    update { it.copy(fxOffsetY = expr) }
                                    onSaveFinished()
                                }
                            ) { v -> update { it.copy(y = v / 100f) } }
                        }
                        OutlinedButton(onClick = { update { it.copy(y = (it.y - 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(28.dp)) { Text("-1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                        OutlinedButton(onClick = { update { it.copy(y = (it.y + 1f / 2400f).coerceIn(0f, 1f)) }; onSaveFinished() }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(28.dp)) { Text("+1px", fontSize = 10.sp, color = PrismColors.PureWhite) }
                    }
                    Text("Âncora da Camada", fontSize = 12.sp, color = PrismColors.MutedZinc)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Anchor.entries.forEach { a ->
                            val sel = node.anchor == a
                            FilterChip(selected = sel, onClick = { update { it.copy(anchor = a) }; onSaveFinished() }, label = { Text(anchorLabel(a), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                        }
                    }
                }

                val hasDepthLayer = !project.isWidget && project.depth && project.layers.any { it.type == LayerType.DEPTH && !it.hidden }
                if (hasDepthLayer && node.type != LayerType.DEPTH) {
                    val isAbove = project.layers.isAboveSubject(node.id)
                    Text("Em relação ao sujeito 3D", fontSize = 12.sp, color = PrismColors.MutedZinc)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isAbove,
                            onClick = { onCommit(project.copy(layers = project.layers.setAboveSubject(node.id, above = false))) },
                            label = { Text("Atrás", fontSize = 11.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(!isAbove)
                        )
                        FilterChip(
                            selected = isAbove,
                            onClick = { onCommit(project.copy(layers = project.layers.setAboveSubject(node.id, above = true))) },
                            label = { Text("À frente", fontSize = 11.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(isAbove)
                        )
                    }
                }

                if (!project.isWidget && project.motion.enabled && node.type != LayerType.DEPTH) {
                    Text("Movimento 3D da Camada", fontSize = 12.sp, color = PrismColors.MutedZinc)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            com.example.prismlock.model.LayerMotion.FOLLOW_PLANE to "Seguir plano",
                            com.example.prismlock.model.LayerMotion.STILL to "Parado",
                            com.example.prismlock.model.LayerMotion.CUSTOM to "Personalizado"
                        ).forEach { (mode, label) ->
                            val sel = node.motion == mode
                            FilterChip(
                                selected = sel,
                                onClick = { update { it.copy(motion = mode) }; onSaveFinished() },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = prismFilterChipColors(),
                                border = prismFilterChipBorder(sel)
                            )
                        }
                    }
                    if (node.motion == com.example.prismlock.model.LayerMotion.CUSTOM) {
                        PrecisionSliderField("Profundidade", node.motionDepth, -1f..1.5f, "", 2, onSaveFinished) { v ->
                            update { it.copy(motionDepth = v) }
                        }
                        if ((node.fill == Fill.GLASS || node.fill == Fill.CRYSTAL) &&
                            kotlin.math.abs(node.motionDepth - project.motion.backgroundDepth) > 0.15f
                        ) {
                            Text(
                                "O vidro pode parecer descolado do fundo quando usa profundidade diferente.",
                                fontSize = 10.sp,
                                color = PrismColors.MutedZinc
                            )
                        }
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
                PrecisionSliderField(
                    label = "Rotação",
                    value = node.rotation,
                    range = -180f..180f,
                    suffix = "°",
                    decimals = 1,
                    onValueChangeFinished = onSaveFinished,
                    fxExpression = node.fxRotation,
                    fxHint = "Ex: \$df(ss) * 6\$ ou \$bi(level) * 3.6\$",
                    env = env,
                    globals = globalsMap,
                    projectGlobals = project.globals,
                    onFxChange = { expr ->
                        update { it.copy(fxRotation = expr) }
                        onSaveFinished()
                    }
                ) { v -> update { it.copy(rotation = v) } }
                PrecisionSliderField(
                    label = "Escala",
                    value = node.scale,
                    range = 0.25f..4f,
                    suffix = "x",
                    decimals = 2,
                    onValueChangeFinished = onSaveFinished,
                    fxExpression = node.fxScale,
                    fxHint = "Ex: \$if(gv(zoom) = 1, 1.25, 1.0)\$",
                    env = env,
                    globals = globalsMap,
                    projectGlobals = project.globals,
                    onFxChange = { expr ->
                        update { it.copy(fxScale = expr) }
                        onSaveFinished()
                    }
                ) { v -> update { it.copy(scale = v) } }
            }

            StudioSection(
                title = "Visibilidade da Camada",
                summary = if (node.visibilityExpr.isNotBlank()) "ƒx · ${condLabel(node.cond)}" else condLabel(node.cond),
                initiallyExpanded = node.visibilityExpr.isNotBlank() || node.cond != Cond.ALWAYS
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val allowedConds = if (project.isWidget) {
                        Cond.entries.filter { it != Cond.LOCK_ONLY && it != Cond.HOME_ONLY }
                    } else Cond.entries
                    allowedConds.forEach { c ->
                        val sel = node.cond == c
                        FilterChip(selected = sel, onClick = { update { it.copy(cond = c) }; onSaveFinished() }, label = { Text(condLabel(c), fontSize = 10.sp) }, colors = prismFilterChipColors(), border = prismFilterChipBorder(sel))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x16FFFFFF))
                        .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
                        .clickable { showVisibilityFxDialog = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Visibilidade por Expressão (visibilityExpr)", fontSize = 10.sp, color = PrismColors.MutedZinc)
                        Text(
                            text = node.visibilityExpr.ifBlank { "Opcional: \$gv(painel) = 1\$ ou \$bi(level) < 30\$" },
                            fontSize = 12.sp,
                            color = if (node.visibilityExpr.isBlank()) PrismColors.MutedZinc else PrismColors.PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FxCalculatorButton(
                        active = node.visibilityExpr.isNotBlank(),
                        onClick = { showVisibilityFxDialog = true }
                    )
                }
            }
        }

        TouchControlsBlock(
            node = node,
            project = project,
            previewLocked = previewLocked,
            testTouchMode = testTouchMode,
            onTogglePreviewLocked = onTogglePreviewLocked,
            onToggleTestTouch = onToggleTestTouch,
            onTestAction = onTestAction,
            onOpenGlobalsTab = onOpenGlobalsPanel,
            update = update,
            onSaveFinished = onSaveFinished
        )
    }

    if (showVisibilityFxDialog && node != null) {
        FxExpressionDialog(
            title = "Visibilidade da Camada",
            initialValue = node.visibilityExpr,
            placeholder = "\$gv(painel) = 1\$",
            exampleHint = "Mostra a camada quando verdadeiro (ex: \$gv(painel) = 1\$, \$bi(charging) = 1\$, \$ai(isday) = 0\$)",
            env = env,
            globals = globalsMap,
            projectGlobals = project.globals,
            onDismiss = { showVisibilityFxDialog = false },
            onConfirm = { next ->
                update { it.copy(visibilityExpr = next) }
                onSaveFinished()
                showVisibilityFxDialog = false
            }
        )
    }
}

