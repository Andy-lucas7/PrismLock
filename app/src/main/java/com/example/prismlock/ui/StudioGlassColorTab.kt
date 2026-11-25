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
internal fun GlassAndColorTab(
    allLayers: List<LayerNode>,
    project: Project,
    env: Env,
    globalsMap: Map<String, String>,
    colorGlobals: List<GlobalVar> = emptyList(),
    node: LayerNode?,
    wallPalette: List<Int>,
    onSelect: (String) -> Unit,
    update: ((LayerNode) -> LayerNode) -> Unit,
    onSaveFinished: () -> Unit,
    onBlurDragStateChange: (Boolean) -> Unit = {}
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

            if (node.fill == Fill.SOLID || node.fill == Fill.GRADIENT || node.fill == Fill.HOLLOW || node.fill == Fill.ADAPTIVE) {
                ColorPickerPanel(
                    title = "Cor Principal",
                    colorInt = node.color,
                    palette = wallPalette,
                    fxExpression = node.fxColor,
                    fxHint = "Ex: \$if(bi(level) <= 20, \"#FFFF5252\", \"#FFFFFFFF\")\$ ou \$gv(cor_destaque)\$",
                    env = env,
                    globals = globalsMap,
                    projectGlobals = project.globals,
                    onFxChange = { expr ->
                        update { it.copy(fxColor = expr) }
                        onSaveFinished()
                    }
                ) { c -> update { it.copy(color = c) } }
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
                PrecisionSliderField("Blur Gauss", node.glass.blur, 0f..80f, "px", 1, onSaveFinished, onDragStateChange = onBlurDragStateChange) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
                PrecisionSliderField("Saturação", node.glass.saturation, 1f..1.8f, "x", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(saturation = v)) } }
                PrecisionSliderField("Granulação", node.glass.grain, 0f..0.15f, "", 3, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(grain = v)) } }
                PrecisionSliderField("Borda Suave", node.glass.rim.coerceIn(0f, 1f), 0f..1f, "", 2, onSaveFinished) { v -> update { it.copy(glass = it.glass.copy(rim = v)) } }
                ColorPickerPanel(
                    title = "Tint Translúcido",
                    colorInt = node.glass.tint,
                    palette = wallPalette,
                    fxExpression = node.fxColor,
                    fxHint = "Ex: \$if(ai(isday), \"#30FFFFFF\", \"#44101624\")\$",
                    env = env,
                    globals = globalsMap,
                    projectGlobals = project.globals,
                    onFxChange = { expr ->
                        update { it.copy(fxColor = expr) }
                        onSaveFinished()
                    }
                ) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
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
                    PrecisionSliderField("Blur Jato", node.glass.blur, 0f..80f, "px", 1, onSaveFinished, onDragStateChange = onBlurDragStateChange) { v -> update { it.copy(glass = it.glass.copy(blur = v)) } }
                }
                ColorPickerPanel(
                    title = "Tint Cristal",
                    colorInt = node.glass.tint,
                    palette = wallPalette,
                    fxExpression = node.fxColor,
                    fxHint = "Ex: \$if(ai(isday), \"#24FFFFFF\", \"#38162038\")\$",
                    env = env,
                    globals = globalsMap,
                    projectGlobals = project.globals,
                    onFxChange = { expr ->
                        update { it.copy(fxColor = expr) }
                        onSaveFinished()
                    }
                ) { c -> update { it.copy(glass = it.glass.copy(tint = c)) } }
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
            PrecisionSliderField(
                label = "Opacidade",
                value = node.opacity,
                range = 0f..1f,
                suffix = "",
                decimals = 2,
                onValueChangeFinished = onSaveFinished,
                fxExpression = node.fxOpacity,
                fxHint = "Retorna 0..100%. Ex: \$if(gv(modo) = 1, 100, 35)\$",
                env = env,
                globals = globalsMap,
                projectGlobals = project.globals,
                onFxChange = { expr ->
                    update { it.copy(fxOpacity = expr) }
                    onSaveFinished()
                }
            ) { v -> update { it.copy(opacity = v) } }
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

