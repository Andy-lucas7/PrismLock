package com.example.prismlock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prismlock.model.MotionAxes
import com.example.prismlock.model.Project
import com.example.prismlock.wallpaper.MotionSource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun Motion3DControlSection(
    project: Project,
    hasSubject3d: Boolean,
    liveSensorPreview: Boolean,
    onToggleLiveSensorPreview: () -> Unit,
    onStage: (Project) -> Unit,
    onCommit: (Project) -> Unit,
    onSaveFinished: () -> Unit,
    switchColors: SwitchColors
) {
    val ctx = LocalContext.current
    val sensorProbe = remember(ctx) { MotionSource(ctx) }
    val supported = sensorProbe.isSupported
    val systemBlocked = sensorProbe.isSystemMotionBlocked()
    val motion = project.motion

    StudioSection(
        title = "Movimento 3D (Cena & Paralaxe)",
        summary = when {
            !supported -> "Sem sensor"
            !motion.enabled -> "Desligado"
            systemBlocked -> "Pausado pelo sistema"
            else -> "${(motion.intensity * 100).roundToInt()}%"
        },
        initiallyExpanded = motion.enabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Movimento 3D", fontSize = 13.sp, color = PrismColors.PureWhite, fontWeight = FontWeight.Medium)
                if (!supported) {
                    Text("Este aparelho não tem sensor de movimento", fontSize = 10.sp, color = PrismColors.MutedZinc)
                } else if (systemBlocked) {
                    Text("Pausado: Economia de bateria ou animações do sistema desativadas", fontSize = 10.sp, color = PrismColors.MutedZinc)
                } else if (motion.enabled) {
                    Text("Arraste fora das camadas no preview para inclinar a cena", fontSize = 10.sp, color = PrismColors.MutedZinc)
                }
            }
            Switch(
                checked = supported && motion.enabled,
                enabled = supported,
                onCheckedChange = { onCommit(project.copy(motion = motion.copy(enabled = it))) },
                colors = switchColors
            )
        }

        if (supported && motion.enabled) {
            Text("Intensidade", fontSize = 11.sp, color = PrismColors.MutedZinc)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Sutil" to 0.4f, "Padrão" to 1.0f, "Intenso" to 1.6f).forEach { (label, valI) ->
                    val sel = abs(motion.intensity - valI) < 0.08f
                    FilterChip(
                        selected = sel,
                        onClick = { onCommit(project.copy(motion = motion.copy(intensity = valI))) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = prismFilterChipColors(),
                        border = prismFilterChipBorder(sel)
                    )
                }
            }
            PrecisionSliderField(
                label = "Intensidade",
                value = motion.intensity * 100f,
                range = 10f..200f,
                suffix = "%",
                decimals = 0,
                onValueChangeFinished = onSaveFinished
            ) { v -> onStage(project.copy(motion = motion.copy(intensity = (v / 100f).coerceIn(0.1f, 2.0f)))) }

            Text("Vista Lateral (Longe ↔ Tela ↔ Perto)", fontSize = 11.sp, color = PrismColors.MutedZinc)
            MotionSideViewCanvas(
                bgDepth = motion.backgroundDepth,
                itemsDepth = motion.itemsDepth,
                subjectDepth = motion.subjectDepth,
                showSubject = hasSubject3d,
                onChangeDepth = { bg, items, subj ->
                    onStage(
                        project.copy(
                            motion = motion.copy(
                                backgroundDepth = bg,
                                itemsDepth = items,
                                subjectDepth = subj
                            )
                        )
                    )
                },
                onFinishDrag = onSaveFinished
            )
            if (!hasSubject3d) {
                Text(
                    "Ative o recorte 3D em uma foto para separar o destaque.",
                    fontSize = 10.sp,
                    color = PrismColors.MutedZinc
                )
            }

            OutlinedButton(
                onClick = onToggleLiveSensorPreview,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (liveSensorPreview) PrismColors.PureWhite else PrismColors.GlassBorder
                )
            ) {
                Text(
                    if (liveSensorPreview) "Parar teste com sensor do celular" else "Usar movimento do celular no preview",
                    fontSize = 11.sp,
                    color = PrismColors.PureWhite
                )
            }

            StudioSection(
                title = "Avançado (Movimento 3D)",
                summary = when (motion.axes) {
                    MotionAxes.BOTH -> "Ambos eixos"
                    MotionAxes.HORIZONTAL -> "Só horizontal"
                    MotionAxes.VERTICAL -> "Só vertical"
                },
                initiallyExpanded = false
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        MotionAxes.BOTH to "Ambos",
                        MotionAxes.HORIZONTAL to "Horizontal",
                        MotionAxes.VERTICAL to "Vertical"
                    ).forEach { (ax, label) ->
                        val sel = motion.axes == ax
                        FilterChip(
                            selected = sel,
                            onClick = { onCommit(project.copy(motion = motion.copy(axes = ax))) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = prismFilterChipColors(),
                            border = prismFilterChipBorder(sel)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Inverter direção", fontSize = 12.sp, color = PrismColors.PureWhite)
                    Switch(
                        checked = motion.invert,
                        onCheckedChange = { onCommit(project.copy(motion = motion.copy(invert = it))) },
                        colors = switchColors
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ativo na Tela Inicial", fontSize = 12.sp, color = PrismColors.PureWhite)
                    Switch(
                        checked = motion.homeScreen,
                        onCheckedChange = { onCommit(project.copy(motion = motion.copy(homeScreen = it))) },
                        colors = switchColors
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionSideViewCanvas(
    bgDepth: Float,
    itemsDepth: Float,
    subjectDepth: Float,
    showSubject: Boolean,
    onChangeDepth: (Float, Float, Float) -> Unit,
    onFinishDrag: () -> Unit
) {
    // Axis maps depth in [+1.5 (Longe/left) .. -1.0 (Perto/right)] across X
    val minD = -1.0f
    val maxD = 1.5f
    var activePlane by remember { mutableStateOf(-1) } // 0=bg, 1=items, 2=subject
    var curBg by remember(bgDepth) { mutableStateOf(bgDepth) }
    var curItems by remember(itemsDepth) { mutableStateOf(itemsDepth) }
    var curSubj by remember(subjectDepth) { mutableStateOf(subjectDepth) }

    fun depthToFrac(d: Float): Float = ((maxD - d) / (maxD - minD)).coerceIn(0.06f, 0.94f)
    fun fracToDepth(f: Float): Float {
        val raw = maxD - f.coerceIn(0.06f, 0.94f) * (maxD - minD)
        return if (abs(raw) < 0.08f) 0f else raw.coerceIn(minD, maxD)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrismColors.CarbonBlack)
            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Longe", fontSize = 9.sp, color = PrismColors.MutedZinc)
            Text("Tela (0)", fontSize = 9.sp, color = PrismColors.PureWhite, fontWeight = FontWeight.SemiBold)
            Text("Perto", fontSize = 9.sp, color = PrismColors.MutedZinc)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .pointerInput(showSubject) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val xBg = depthToFrac(curBg) * w
                            val xItems = depthToFrac(curItems) * w
                            val xSubj = depthToFrac(curSubj) * w
                            val candidates = buildList {
                                add(0 to abs(pos.x - xBg))
                                add(1 to abs(pos.x - xItems))
                                if (showSubject) add(2 to abs(pos.x - xSubj))
                            }
                            activePlane = candidates.minByOrNull { it.second }?.first ?: -1
                        },
                        onDragEnd = {
                            activePlane = -1
                            onFinishDrag()
                        },
                        onDragCancel = {
                            activePlane = -1
                            onFinishDrag()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val nextD = fracToDepth(change.position.x / w)
                            when (activePlane) {
                                0 -> curBg = nextD
                                1 -> curItems = nextD
                                2 -> curSubj = nextD
                            }
                            onChangeDepth(curBg, curItems, curSubj)
                        }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val screenX = depthToFrac(0f) * w
                // Horizontal axis
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 2f
                )
                // Dashed vertical screen plane line at depth = 0
                drawLine(
                    color = Color(0xAAFFFFFF),
                    start = Offset(screenX, 4f),
                    end = Offset(screenX, h - 4f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                fun drawPlaneCard(xFrac: Float, yOffset: Float, cardColor: Color, selected: Boolean) {
                    val cx = xFrac * w
                    val cw = 18.dp.toPx()
                    val ch = 46.dp.toPx()
                    val top = (h - ch) * 0.5f + yOffset
                    drawRoundRect(
                        color = cardColor,
                        topLeft = Offset(cx - cw * 0.5f, top),
                        size = Size(cw, ch),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (selected) Color.White else Color(0x66FFFFFF),
                        topLeft = Offset(cx - cw * 0.5f, top),
                        size = Size(cw, ch),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = if (selected) 2.5f else 1.5f)
                    )
                }

                drawPlaneCard(depthToFrac(curBg), -4f, Color(0xFF3F3F46), activePlane == 0)
                drawPlaneCard(depthToFrac(curItems), 0f, Color(0xFFA1A1AA), activePlane == 1)
                if (showSubject) {
                    drawPlaneCard(depthToFrac(curSubj), 4f, Color(0xFFFFFFFF), activePlane == 2)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("Fundo ${"%.2f".format(curBg)}", fontSize = 9.sp, color = PrismColors.MutedZinc)
            Text("Itens ${"%.2f".format(curItems)}", fontSize = 9.sp, color = PrismColors.PureWhite)
            if (showSubject) {
                Text("Destaque ${"%.2f".format(curSubj)}", fontSize = 9.sp, color = PrismColors.PureWhite)
            }
        }
    }
}
