package ke.carhire

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
import ke.carhire.ui.theme.darkColors
import ke.carhire.ui.theme.lightColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun assertReadable(scheme: ColorScheme) {
        val pairs =
            listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onBackground to scheme.background,
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.inverseOnSurface to scheme.inverseSurface,
                scheme.onSurface to scheme.surfaceContainerLowest,
                scheme.onSurface to scheme.surfaceContainerLow,
                scheme.onSurface to scheme.surfaceContainer,
                scheme.onSurface to scheme.surfaceContainerHigh,
                scheme.onSurface to scheme.surfaceContainerHighest,
            )
        pairs.forEachIndexed { index, (foreground, background) ->
            val a = foreground.luminance().toDouble()
            val b = background.luminance().toDouble()
            val contrast = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
            assertTrue(
                "Text/surface pair $index has insufficient contrast: $contrast",
                contrast >= 4.5,
            )
        }
    }

    @Test fun lightBlueTextRemainsReadableAcrossSurfaces() = assertReadable(lightColors)

    @Test fun navyTextRemainsReadableAcrossSurfaces() = assertReadable(darkColors)
}
