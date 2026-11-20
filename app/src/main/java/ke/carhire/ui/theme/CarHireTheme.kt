package ke.carhire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import ke.carhire.domain.model.ThemePreference

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
