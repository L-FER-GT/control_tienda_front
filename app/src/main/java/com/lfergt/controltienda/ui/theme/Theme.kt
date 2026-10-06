package com.lfergt.controltienda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Blue + Slate de Radix Colors, adaptados a los roles de Material 3.
// https://www.radix-ui.com/colors/docs/palette-composition/scales
internal val LightColors = lightColorScheme(
    primary = Color(0xFF005EA8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFFF), onPrimaryContainer = Color(0xFF113264),
    inversePrimary = Color(0xFF70B8FF),
    secondary = Color(0xFF355F80), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4FE), onSecondaryContainer = Color(0xFF113264),
    tertiary = Color(0xFF005E78), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF4FF), onTertiaryContainer = Color(0xFF00364A),
    background = Color(0xFFFBFDFF), onBackground = Color(0xFF1C2024),
    surface = Color(0xFFFBFDFF), onSurface = Color(0xFF1C2024),
    surfaceVariant = Color(0xFFE0E1E6), onSurfaceVariant = Color(0xFF4A515B),
    surfaceTint = Color(0xFF005EA8),
    inverseSurface = Color(0xFF1C2024), inverseOnSurface = Color(0xFFF0F0F3),
    outline = Color(0xFF60646C), outlineVariant = Color(0xFFCDCED6),
    surfaceBright = Color(0xFFFBFDFF), surfaceDim = Color(0xFFE0E1E6),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4FAFF),
    surfaceContainer = Color(0xFFF0F4F8), surfaceContainerHigh = Color(0xFFE8EEF4),
    surfaceContainerHighest = Color(0xFFE0E8F0),
    error = Color(0xFFAA2429), onError = Color.White,
    errorContainer = Color(0xFFFFDAD8), onErrorContainer = Color(0xFF410008),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF70B8FF), onPrimary = Color(0xFF102A43),
    primaryContainer = Color(0xFF004074), onPrimaryContainer = Color(0xFFC2E6FF),
    inversePrimary = Color(0xFF005EA8),
    secondary = Color(0xFFACD8FC), onSecondary = Color(0xFF102A43),
    secondaryContainer = Color(0xFF243F55), onSecondaryContainer = Color(0xFFD5EFFF),
    tertiary = Color(0xFF7CDCF5), onTertiary = Color(0xFF00364A),
    tertiaryContainer = Color(0xFF004D63), onTertiaryContainer = Color(0xFFDDF4FF),
    background = Color(0xFF111822), onBackground = Color(0xFFEDF2F7),
    surface = Color(0xFF111822), onSurface = Color(0xFFEDF2F7),
    surfaceVariant = Color(0xFF394553), onSurfaceVariant = Color(0xFFB7C5D3),
    surfaceTint = Color(0xFF70B8FF),
    inverseSurface = Color(0xFFEDF2F7), inverseOnSurface = Color(0xFF1C2024),
    outline = Color(0xFF91A2B4), outlineVariant = Color(0xFF394553),
    surfaceBright = Color(0xFF344354), surfaceDim = Color(0xFF111822),
    surfaceContainerLowest = Color(0xFF0B1119), surfaceContainerLow = Color(0xFF17212D),
    surfaceContainer = Color(0xFF1C2938), surfaceContainerHigh = Color(0xFF243446),
    surfaceContainerHighest = Color(0xFF2C3E51),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD8),
)

private val AppTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** Advertencia y éxito por tema; el peligro usa `error` del esquema. */
internal class StatusPalette(val warning: Color, val ok: Color)

internal val LightStatus = StatusPalette(warning = Color(0xFF785000), ok = Color(0xFF176044))
internal val DarkStatus = StatusPalette(warning = Color(0xFFFFD580), ok = Color(0xFF8DDEBB))

/** Colores de estado reutilizados (alertas de stock y consumo). */
object StatusColors {
    private val palette: StatusPalette @Composable get() =
        if (MaterialTheme.colorScheme.surface == DarkColors.surface) DarkStatus else LightStatus
    val warning: Color @Composable get() = palette.warning
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val ok: Color @Composable get() = palette.ok
}

@Composable
fun ControlTiendaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
