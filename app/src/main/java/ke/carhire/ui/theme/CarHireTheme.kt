package ke.carhire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ke.carhire.domain.model.ThemePreference

private val lightColors =
    lightColorScheme(
        primary = Color(0xff17634f),
        onPrimary = Color.White,
        primaryContainer = Color(0xffb0efcf),
        onPrimaryContainer = Color(0xff002116),
        secondary = Color(0xff536c45),
        background = Color(0xfff3f5f2),
        onBackground = Color(0xff193e37),
        surface = Color.White,
        onSurface = Color(0xff193e37),
        surfaceVariant = Color(0xffe0e7e1),
        onSurfaceVariant = Color(0xff465f51),
        outline = Color(0xff6b7c73),
    )
private val darkColors =
    darkColorScheme(
        primary = Color(0xff8fd4b7),
        onPrimary = Color(0xff003826),
        primaryContainer = Color(0xff18513f),
        onPrimaryContainer = Color(0xffafeed0),
        secondary = Color(0xffb3d09f),
        onSecondary = Color(0xff203617),
        background = Color(0xff101c18),
        onBackground = Color(0xffe3eee8),
        surface = Color(0xff182b26),
        onSurface = Color(0xffe3eee8),
        surfaceVariant = Color(0xff3d4c43),
        onSurfaceVariant = Color(0xffbfcec3),
        outline = Color(0xff8a9e90),
    )

@Composable
fun CarHireTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark =
        when (preference) {
            ThemePreference.System -> isSystemInDarkTheme()
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, content = content)
}
