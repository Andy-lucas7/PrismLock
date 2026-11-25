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
internal fun BackgroundContextPanel(
    project: Project,
    activePreviewUri: String?,
    onStage: (Project) -> Unit,
    onCommit: (Project) -> Unit,
    onSaveFinished: () -> Unit,
    onToggleDepth: (String, Boolean) -> Unit,
    onSelectPreview: (String, Int) -> Unit,
    onPickFolder: () -> Unit,
    onPickImage: () -> Unit,
    onPickImageFile: () -> Unit = {},
    onClearWallpaper: () -> Unit,
    onBokehDragStateChange: (Boolean) -> Unit = {},
    liveSensorPreview: Boolean = false,
    onToggleLiveSensorPreview: () -> Unit = {},
    onApplyWallpaper: () -> Unit
) {
    val ctx = LocalContext.current
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
        if (project.isWidget) {
            StudioSection(
                title = "Formato do Widget (KWGT / Home)",
                summary = "Proporção ${"%.1f".format(project.widgetAspect)}:1",
                initiallyExpanded = true
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Quadrado 2×2" to 1.05f,
                        "Card 4×2" to 2.0f,
                        "Mídia 4×1.8" to 2.2f,
                        "Faixa 4×1" to 3.4f
                    ).forEach { (label, ratio) ->
                        val sel = kotlin.math.abs(project.widgetAspect - ratio) < 0.1f
                        FilterChip(
                            selected = sel,
                            onClick = { onCommit(project.copy(widgetAspect = ratio)) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(sel)
                        )
                    }
                }
                Text(
                    text = "Escolha abaixo uma foto de referência (ou use o papel de parede ativo do PrismLock) para o Cristal Óptico e Vidro Fosco refratarem com fidelidade.",
                    fontSize = 10.sp,
                    color = PrismColors.MutedZinc
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x18FFFFFF), contentColor = PrismColors.PureWhite),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Galeria", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onPickImageFile,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder)
            ) {
                Text("Arquivos", fontSize = 12.sp, color = PrismColors.PureWhite, maxLines = 1)
            }
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
        if (!project.isWidget) {
            OutlinedButton(
                onClick = onPickFolder,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder)
            ) { Text("Pasta Carrossel SAF", fontSize = 12.sp, color = PrismColors.PureWhite) }
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
                modifier = Modifier.fillMaxWidth().height(136.dp),
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
                            .width(82.dp)
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
                        } else if (!project.isWidget) {
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
        val activeImage3dOn = !project.isWidget && activeGalleryItem != null && !activeIsVideo && project.depth && project.isDepthEnabledFor(activeGalleryItem.second)

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
                PrecisionSliderField("Bokeh Fundo", project.depthBgBlur * 100f, 0f..100f, "%", 0, onSaveFinished, onDragStateChange = onBokehDragStateChange) { v ->
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

        if (!project.isWidget) {
            Motion3DControlSection(
                project = project,
                hasSubject3d = project.depth && project.layers.any { it.type == LayerType.DEPTH && !it.hidden },
                liveSensorPreview = liveSensorPreview,
                onToggleLiveSensorPreview = onToggleLiveSensorPreview,
                onStage = onStage,
                onCommit = onCommit,
                onSaveFinished = onSaveFinished,
                switchColors = switchColors
            )
        }

        val applyInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Button(
            onClick = onApplyWallpaper,
            interactionSource = applyInteraction,
            modifier = Modifier.fillMaxWidth().pressScale(applyInteraction),
            colors = ButtonDefaults.buttonColors(containerColor = PrismColors.PureWhite, contentColor = PrismColors.PureBlack),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (project.isWidget) "Aplicar & Fixar Widget na Tela" else "Aplicar na Tela de Bloqueio",
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            if (project.isWidget) {
                "Dica: Widgets com Vidro/Cristal usam o wallpaper selecionado como mapa de refração óptica e operam com consumo zero em repouso."
            } else {
                "Dica Samsung: no Good Lock / LockStar, oculte o relógio nativo do sistema para exibir apenas suas camadas."
            },
            fontSize = 11.sp, color = PrismColors.MutedZinc
        )
    }
}

@Composable
internal fun WeatherAutomationContextPanel(
    project: Project,
    onStage: (Project) -> Unit,
    onCommit: (Project) -> Unit,
    onSaveFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cityInput by remember(project.city) { mutableStateOf(project.city) }
    var showCityDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(ctx, "Permissão de localização negada", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val loc = EnvProvider.lastLocation(ctx)
        if (loc == null) {
            android.widget.Toast.makeText(ctx, "Localização indisponível no momento", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val label = cityInput.ifBlank { "Localização atual" }
        cityInput = label
        onCommit(project.copy(lat = loc.first, lon = loc.second, city = label))
        scope.launch {
            EnvProvider.refreshWeather(ctx, loc.first, loc.second, label, force = true)
        }
    }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PrismColors.PureBlack,
        checkedTrackColor = PrismColors.PureWhite,
        uncheckedThumbColor = PrismColors.MutedZinc,
        uncheckedTrackColor = PrismColors.TrackZinc
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusedFieldTrigger(
            label = "Cidade (Clima Open-Meteo)",
            value = cityInput,
            placeholder = "Toque para definir cidade",
            onClick = { showCityDialog = true }
        )
        OutlinedButton(
            onClick = { locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorder)
        ) {
            Text("Usar minha localização", fontSize = 12.sp, color = PrismColors.PureWhite)
        }

        if (!project.isWidget) {
            StudioSection(
                title = "Regras do Carrossel de Wallpapers",
                summary = if (project.intervalMin > 0) "A cada ${project.intervalMin}m" else "Automático",
                initiallyExpanded = true
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
            }
        }

        StudioSection(
            title = "Escurecimento do Fundo",
            summary = "${(project.dim * 100).roundToInt()}%",
            initiallyExpanded = true
        ) {
            PrecisionSliderField("Escurecer (Dim)", project.dim * 100f, 0f..70f, "%", 0, onSaveFinished) { v -> onStage(project.copy(dim = v / 100f)) }
        }
    }

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
                        if (g != null) {
                            cityInput = g.third
                            onCommit(project.copy(lat = g.first, lon = g.second, city = g.third))
                        } else {
                            android.widget.Toast.makeText(ctx, "Cidade não encontrada ou sem internet", android.widget.Toast.LENGTH_SHORT).show()
                            showCityDialog = true
                        }
                    }
                }
            }
        )
    }
}
