package com.example.prismlock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PrismColors {
    val PureBlack = Color(0xFF000000)
    val CarbonBlack = Color(0xFF0D0D0F)
    val SurfaceElevated = Color(0xFF141418)
    val GlassCarbon = Color(0xCC0D0D0F)
    val GlassCarbonDense = Color(0xE80D0D0F)
    val GlassHighlight = Color(0x16FFFFFF)
    val GlassBorder = Color(0x1FFFFFFF) // White 12%
    val GlassBorderStrong = Color(0x38FFFFFF)
    val PureWhite = Color(0xFFFFFFFF)
    val MutedZinc = Color(0xFFA1A1AA)
    val TrackZinc = Color(0xFF27272A)
    val DangerRed = Color(0xFFFF6B6B)
}

val PrismMonochromeColorScheme = darkColorScheme(
    primary = PrismColors.PureWhite,
    onPrimary = PrismColors.PureBlack,
    primaryContainer = PrismColors.TrackZinc,
    onPrimaryContainer = PrismColors.PureWhite,
    secondary = PrismColors.MutedZinc,
    onSecondary = PrismColors.PureBlack,
    secondaryContainer = PrismColors.CarbonBlack,
    onSecondaryContainer = PrismColors.PureWhite,
    surface = PrismColors.CarbonBlack,
    onSurface = PrismColors.PureWhite,
    surfaceVariant = PrismColors.TrackZinc,
    onSurfaceVariant = PrismColors.MutedZinc,
    background = PrismColors.PureBlack,
    onBackground = PrismColors.PureWhite,
    outline = PrismColors.GlassBorder
)

@Composable
fun PrismLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PrismMonochromeColorScheme,
        content = content
    )
}

fun Modifier.frostedChrome(
    shape: Shape = RoundedCornerShape(22.dp),
    dense: Boolean = false,
    elevation: Dp = 14.dp,
    borderColor: Color = PrismColors.GlassBorder
): Modifier = this
    .shadow(elevation = elevation, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                if (dense) Color(0xED151519) else Color(0xD4141418),
                if (dense) Color(0xEA0B0B0D) else Color(0xC80D0D0F)
            )
        )
    )
    .border(1.dp, borderColor, shape)

@Composable
fun prismFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = PrismColors.CarbonBlack,
    labelColor = PrismColors.MutedZinc,
    selectedContainerColor = PrismColors.PureWhite,
    selectedLabelColor = PrismColors.PureBlack
)

@Composable
fun prismFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = PrismColors.GlassBorder,
    selectedBorderColor = PrismColors.PureWhite,
    borderWidth = 1.dp
)
