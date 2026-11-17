package com.example.prismlock.ui

import android.graphics.Color as AColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FocusedInputDialog(
    title: String,
    initialValue: String,
    placeholder: String = "",
    exampleHint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    allowNegative: Boolean = false,
    singleLine: Boolean = true,
    confirmLabel: String = "Confirmar",
    dismissLabel: String = "Cancelar",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(0, initialValue.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(60L)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x9E000000))
                .clickable(onClick = onDismiss)
                .statusBarsPadding()
                .padding(top = 52.dp, start = 18.dp, end = 18.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .frostedChrome(
                        shape = RoundedCornerShape(24.dp),
                        dense = true,
                        elevation = 24.dp,
                        borderColor = PrismColors.GlassBorderStrong
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    color = PrismColors.PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 4,
                    placeholder = if (placeholder.isNotBlank()) {
                        { Text(placeholder, color = PrismColors.MutedZinc, fontSize = 13.sp) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (singleLine) onConfirm(fieldValue.text)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrismColors.PureWhite,
                        unfocusedBorderColor = PrismColors.GlassBorderStrong,
                        focusedTextColor = PrismColors.PureWhite,
                        unfocusedTextColor = PrismColors.PureWhite,
                        cursorColor = PrismColors.PureWhite,
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x26000000)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                if (exampleHint.isNotBlank()) {
                    Text(
                        text = exampleHint,
                        color = PrismColors.MutedZinc,
                        fontSize = 11.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allowNegative) {
                        OutlinedButton(
                            onClick = {
                                val t = fieldValue.text.trim()
                                val next = if (t.startsWith("-")) t.removePrefix("-") else "-$t"
                                fieldValue = TextFieldValue(next, TextRange(next.length))
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrismColors.GlassBorderStrong),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+/−", color = PrismColors.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissLabel, color = PrismColors.MutedZinc, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onConfirm(fieldValue.text) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrismColors.PureWhite,
                                contentColor = PrismColors.PureBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(confirmLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusedFieldTrigger(
    label: String,
    value: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x16FFFFFF))
            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = PrismColors.MutedZinc,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value.ifBlank { placeholder },
                color = if (value.isBlank()) PrismColors.MutedZinc else PrismColors.PureWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MIcons.Rounded.Edit,
                contentDescription = "Editar $label",
                tint = PrismColors.MutedZinc,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

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
    var sliderVal by remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    var dragging by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!dragging) {
            sliderVal = value.coerceIn(range.start, range.endInclusive)
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
                .clickable { showEditDialog = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = formatVal(sliderVal, decimals),
                color = PrismColors.PureWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            if (suffix.isNotEmpty()) {
                Text(suffix, fontSize = 10.sp, color = PrismColors.MutedZinc, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }

    if (showEditDialog) {
        val minTxt = formatVal(range.start, decimals)
        val maxTxt = formatVal(range.endInclusive, decimals)
        val midTxt = formatVal((range.start + range.endInclusive) * 0.5f, decimals)
        FocusedInputDialog(
            title = label,
            initialValue = formatVal(sliderVal, decimals),
            placeholder = "$minTxt .. $maxTxt",
            exampleHint = "Ex: $midTxt (Limites: $minTxt a $maxTxt${if (suffix.isNotEmpty()) " $suffix" else ""})",
            keyboardType = KeyboardType.Decimal,
            allowNegative = range.start < 0f,
            singleLine = true,
            onDismiss = { showEditDialog = false },
            onConfirm = { raw ->
                val parsed = raw.replace(',', '.').trim().toFloatOrNull()
                if (parsed != null) {
                    val clamped = parsed.coerceIn(range.start, range.endInclusive)
                    sliderVal = clamped
                    onValueChange(clamped)
                    onValueChangeFinished?.invoke()
                }
                showEditDialog = false
            }
        )
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
    var lastEmitted by remember { mutableStateOf<Int?>(null) }
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var bri by remember { mutableFloatStateOf(1f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var hexInput by remember(colorInt) { mutableStateOf("#%08X".format(colorInt)) }

    LaunchedEffect(colorInt) {
        if (lastEmitted != colorInt) {
            val hsv = FloatArray(3).also { AColor.colorToHSV(colorInt, it) }
            if (hsv[1] > 0.01f && hsv[2] > 0.01f) hue = hsv[0]
            if (hsv[2] > 0.01f) sat = hsv[1]
            bri = hsv[2]
            alpha = AColor.alpha(colorInt) / 255f
        }
    }

    fun emitHsv() {
        val base = AColor.HSVToColor((alpha * 255).roundToInt().coerceIn(0, 255), floatArrayOf(hue, sat, bri))
        lastEmitted = base
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
            var showHexDialog by remember { mutableStateOf(false) }
            FocusedFieldTrigger(
                label = "Código HEX",
                value = hexInput,
                placeholder = "#FFFFFFFF",
                onClick = { showHexDialog = true }
            )
            if (showHexDialog) {
                FocusedInputDialog(
                    title = "Código HEX ($title)",
                    initialValue = hexInput,
                    placeholder = "#FFFFFFFF",
                    exampleHint = "Ex: #FFFFFF (opaco) ou #80FFFFFF (com alpha)",
                    keyboardType = KeyboardType.Ascii,
                    singleLine = true,
                    onDismiss = { showHexDialog = false },
                    onConfirm = { raw ->
                        val clean = raw.trim().removePrefix("#")
                        if (clean.length == 6 || clean.length == 8) {
                            runCatching { AColor.parseColor("#$clean") }.getOrNull()?.let {
                                hexInput = "#${clean.uppercase()}"
                                onColorChange(it)
                            }
                        }
                        showHexDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun StudioSection(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PrismColors.CarbonBlack)
            .border(1.dp, PrismColors.GlassBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = PrismColors.PureWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = PrismColors.MutedZinc,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Icon(
                imageVector = if (expanded) MIcons.Rounded.KeyboardArrowUp else MIcons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = PrismColors.MutedZinc,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
fun QuickPresetRow(
    items: List<Pair<String, () -> Unit>>,
    activeLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (label, onClick) ->
            val selected = activeLabel != null && label == activeLabel
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) PrismColors.PureWhite else Color(0x16FFFFFF))
                    .border(
                        width = 1.dp,
                        color = if (selected) PrismColors.PureWhite else PrismColors.GlassBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) PrismColors.PureBlack else PrismColors.PureWhite,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
