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

private val Brand = Color(0xFF0F6E56)
private val BrandDark = Color(0xFF5DCAA5)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F0E1),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4A635A),
    secondaryContainer = Color(0xFFCCE8DC),
    onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFFB4541A),
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF3A1400),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceContainer = Color(0xFFEBF0ED),
    surfaceContainerHigh = Color(0xFFE5EBE8),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFC9F0E1),
    secondary = Color(0xFFB1CCC1),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCCE8DC),
    tertiary = Color(0xFFFFB68F),
    tertiaryContainer = Color(0xFF8A3D06),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
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
