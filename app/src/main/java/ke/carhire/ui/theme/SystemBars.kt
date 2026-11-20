package ke.carhire.ui.theme

import android.os.Build
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
@Composable
internal fun ThemedSystemBars(window: Window) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val view = LocalView.current
    SideEffect {
        window.statusBarColor = colors.surface.toArgb()
        // API 24-25 cannot draw dark navigation icons on a light bar.
        window.navigationBarColor =
            if (Build.VERSION.SDK_INT < 26) Color(0xff0b1423).toArgb() else colors.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
