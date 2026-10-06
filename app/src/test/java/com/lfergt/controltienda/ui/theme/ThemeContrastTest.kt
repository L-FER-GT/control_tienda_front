package com.lfergt.controltienda.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Contraste WCAG de los pares texto/fondo que usa la app, en ambos temas. */
class ThemeContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun backgrounds(s: ColorScheme) = mapOf(
        "background" to s.background,
        "surface" to s.surface,
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
    )

    private fun textColors(s: ColorScheme, status: StatusPalette) = mapOf(
        "onSurface" to s.onSurface,
        "onSurfaceVariant" to s.onSurfaceVariant,
        "primary" to s.primary,
        "secondary" to s.secondary,
        "tertiary" to s.tertiary,
        "error" to s.error,
        "warning" to status.warning,
        "ok" to status.ok,
    )

    private fun onPairs(s: ColorScheme) = mapOf(
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary/secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onError/error" to (s.onError to s.error),
        "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
        "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
        "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
    )

    private fun failures(scheme: ColorScheme, status: StatusPalette): List<String> {
        val texts = textColors(scheme, status).flatMap { (fg, fgColor) ->
            backgrounds(scheme).map { (bg, bgColor) -> Triple("$fg/$bg", contrast(fgColor, bgColor), 4.5) }
        }
        val pairs = onPairs(scheme).map { (name, pair) -> Triple(name, contrast(pair.first, pair.second), 4.5) }
        // `outline` solo dibuja bordes e iconos: componentes no textuales requieren 3:1.
        val outlines = backgrounds(scheme).map { (bg, bgColor) -> Triple("outline/$bg", contrast(scheme.outline, bgColor), 3.0) }
        return (texts + pairs + outlines)
            .filter { it.second < it.third }
            .map { "${it.first}: %.2f:1 (mínimo %.1f)".format(it.second, it.third) }
    }

    @Test
    fun `el tema claro cumple el contraste mínimo en texto pequeño y controles`() {
        val failed = failures(LightColors, LightStatus)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `el tema oscuro cumple el contraste mínimo en texto pequeño y controles`() {
        val failed = failures(DarkColors, DarkStatus)
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `el azul principal claro supera 5 a 1 incluso sobre el contenedor más oscuro`() {
        assertTrue(contrast(LightColors.primary, LightColors.surfaceContainerHighest) >= 5.0)
    }
}
