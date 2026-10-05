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

// Paleta del logo (#004BE4, igual que R.color.logo_background). Tonos de Material 3 (HCT) calculados con
// material-color-utilities a partir de ese azul; el naranja terciario es un acento independiente de la marca.
private val LogoBlue = Color(0xFF004BE4)

private val LightColors = lightColorScheme(
    primary = LogoBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF001551),
    inversePrimary = Color(0xFFB7C4FF),
    secondary = Color(0xFF555D7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF121A37),
    tertiary = Color(0xFFB4541A),
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF3A1400),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B24),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF191B24),
    surfaceVariant = Color(0xFFE0E1F5),
    onSurfaceVariant = Color(0xFF434655),
    surfaceTint = LogoBlue,
    inverseSurface = Color(0xFF2E3039),
    inverseOnSurface = Color(0xFFF0F0FC),
    outline = Color(0xFF747687),
    outlineVariant = Color(0xFFC3C5D8),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFD9D9E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F2FF),
    surfaceContainer = Color(0xFFEDEDF9),
    surfaceContainerHigh = Color(0xFFE7E7F4),
    surfaceContainerHighest = Color(0xFFE2E1EE),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002780),
    primaryContainer = Color(0xFF0039B4),
    onPrimaryContainer = Color(0xFFDCE1FF),
    inversePrimary = LogoBlue,
    secondary = Color(0xFFBDC5EB),
    onSecondary = Color(0xFF272F4D),
    secondaryContainer = Color(0xFF3E4565),
    onSecondaryContainer = Color(0xFFDCE1FF),
    tertiary = Color(0xFFFFB68F),
    tertiaryContainer = Color(0xFF8A3D06),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = Color(0xFF11131B),
    onBackground = Color(0xFFE2E1EE),
    surface = Color(0xFF11131B),
    onSurface = Color(0xFFE2E1EE),
    surfaceVariant = Color(0xFF434655),
    onSurfaceVariant = Color(0xFFC3C5D8),
    surfaceTint = Color(0xFFB7C4FF),
    inverseSurface = Color(0xFFE2E1EE),
    inverseOnSurface = Color(0xFF2E3039),
    outline = Color(0xFF8D90A1),
    outlineVariant = Color(0xFF434655),
    surfaceBright = Color(0xFF373942),
    surfaceDim = Color(0xFF11131B),
    surfaceContainerLowest = Color(0xFF0C0E16),
    surfaceContainerLow = Color(0xFF191B24),
    surfaceContainer = Color(0xFF1D1F28),
    surfaceContainerHigh = Color(0xFF282933),
    surfaceContainerHighest = Color(0xFF33343E),
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

/** Colores de estado reutilizados (alertas de stock y consumo). */
object StatusColors {
    val warning = Color(0xFFE6A100)
    val danger = Color(0xFFD64545)
    val ok = Color(0xFF2E9E6A)
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
