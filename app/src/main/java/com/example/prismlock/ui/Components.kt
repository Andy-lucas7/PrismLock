package com.example.prismlock.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PrecisionSliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    decimals: Int = 1,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    val focus = LocalFocusManager.current
    var sliderVal by remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    var dragging by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(formatVal(value, decimals)) }

    LaunchedEffect(value) {
        if (!dragging) {
            val clamped = value.coerceIn(range.start, range.endInclusive)
            sliderVal = clamped
            text = formatVal(clamped, decimals)
        }
    }

    fun commitText(raw: String) {
        val parsed = raw.replace(',', '.').toFloatOrNull()
        if (parsed != null) {
            val clamped = parsed.coerceIn(range.start, range.endInclusive)
            sliderVal = clamped
            onValueChange(clamped)
            onValueChangeFinished?.invoke()
            text = formatVal(clamped, decimals)
        } else {
            text = formatVal(sliderVal, decimals)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(96.dp), fontSize = 11.sp, color = PrismColors.MutedZinc, maxLines = 1)
        Slider(
            value = sliderVal,
            onValueChange = { v ->
                dragging = true
                sliderVal = v
                text = formatVal(v, decimals)
                onValueChange(v)
            },
            onValueChangeFinished = {
                dragging = false
                onValueChangeFinished?.invoke()
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = PrismColors.PureWhite,
                activeTrackColor = PrismColors.PureWhite,
                inactiveTrackColor = PrismColors.TrackZinc
            ),
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier
                .width(74.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrismColors.CarbonBlack)
                .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { s ->
                    text = s
                    s.replace(',', '.').toFloatOrNull()?.let { parsed ->
                        if (parsed in range.start..range.endInclusive) onValueChange(parsed)
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = PrismColors.PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                cursorBrush = SolidColor(PrismColors.PureWhite),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitText(text); focus.clearFocus() }),
                modifier = Modifier.weight(1f)
            )
            if (suffix.isNotEmpty()) {
                Text(suffix, fontSize = 10.sp, color = PrismColors.MutedZinc, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

private fun formatVal(v: Float, decimals: Int): String = when (decimals) {
    0 -> v.roundToInt().toString()
    else -> "%.${decimals}f".format(v)
}

@Composable
fun ColorPickerPanel(
    title: String,
    colorInt: Int,
    palette: List<Int>,
    onColorChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hsv = remember(colorInt) {
        FloatArray(3).also { AColor.colorToHSV(colorInt, it) }
    }
    var hue by remember(colorInt) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(colorInt) { mutableFloatStateOf(hsv[1]) }
    var bri by remember(colorInt) { mutableFloatStateOf(hsv[2]) }
    var alpha by remember(colorInt) { mutableFloatStateOf(AColor.alpha(colorInt) / 255f) }
    var hexInput by remember(colorInt) { mutableStateOf("#%08X".format(colorInt)) }

    fun emitHsv() {
        val base = AColor.HSVToColor((alpha * 255).roundToInt().coerceIn(0, 255), floatArrayOf(hue, sat, bri))
        onColorChange(base)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrismColors.CarbonBlack)
            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(colorInt)).border(1.dp, PrismColors.GlassBorderStrong, CircleShape))
                Text(title, fontSize = 12.sp, color = PrismColors.PureWhite, fontWeight = FontWeight.Medium)
            }
            Text("#%08X".format(colorInt), fontSize = 11.sp, color = PrismColors.MutedZinc)
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            // Wallpaper Palette + Quick Swatches
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (palette + listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())).distinct().take(8).forEach { c ->
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (c == colorInt) 2.dp else 1.dp, if (c == colorInt) PrismColors.PureWhite else PrismColors.TrackZinc, CircleShape)
                            .clickable { onColorChange(c) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // 2D Saturation / Brightness Spectrum Box
            val pureHue = Color(AColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(hue) {
                        detectTapGestures { pos ->
                            sat = (pos.x / size.width).coerceIn(0f, 1f)
                            bri = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                            emitHsv()
                        }
                    }
                    .pointerInput(hue) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            sat = (change.position.x / size.width).coerceIn(0f, 1f)
                            bri = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            emitHsv()
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(sat * size.width, (1f - bri) * size.height),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // Hue Bar
            val hueStops = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            hue = ((pos.x / size.width).coerceIn(0f, 1f)) * 360f
                            emitHsv()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = ((change.position.x / size.width).coerceIn(0f, 1f)) * 360f
                            emitHsv()
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.horizontalGradient(hueStops))
                    val x = (hue / 360f) * size.width
                    drawCircle(Color.White, radius = size.height / 2f, center = Offset(x, size.height / 2f), style = Stroke(3f))
                }
            }
            Spacer(Modifier.height(6.dp))

            // R, G, B, Alpha precision controls + direct HEX input
            val r = AColor.red(colorInt).toFloat()
            val g = AColor.green(colorInt).toFloat()
            val b = AColor.blue(colorInt).toFloat()
            val a = AColor.alpha(colorInt).toFloat()
            PrecisionSliderField("Vermelho (R)", r, 0f..255f, decimals = 0) {
                onColorChange(AColor.argb(a.roundToInt(), it.roundToInt(), g.roundToInt(), b.roundToInt()))
            }
            PrecisionSliderField("Verde (G)", g, 0f..255f, decimals = 0) {
                onColorChange(AColor.argb(a.roundToInt(), r.roundToInt(), it.roundToInt(), b.roundToInt()))
            }
            PrecisionSliderField("Azul (B)", b, 0f..255f, decimals = 0) {
                onColorChange(AColor.argb(a.roundToInt(), r.roundToInt(), g.roundToInt(), it.roundToInt()))
            }
            PrecisionSliderField("Alpha (A)", a, 0f..255f, decimals = 0) {
                alpha = it / 255f
                onColorChange(AColor.argb(it.roundToInt(), r.roundToInt(), g.roundToInt(), b.roundToInt()))
            }
            OutlinedTextField(
                value = hexInput,
                onValueChange = { s ->
                    hexInput = s
                    val clean = s.trim().removePrefix("#")
                    if (clean.length == 6 || clean.length == 8) {
                        runCatching { AColor.parseColor("#$clean") }.getOrNull()?.let(onColorChange)
                    }
                },
                label = { Text("HEX (#RRGGBB ou #AARRGGBB)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
