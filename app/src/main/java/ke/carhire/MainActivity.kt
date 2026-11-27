package ke.carhire

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.carhire.ui.components.ProvideDisplayTimeZone
import ke.carhire.ui.lock.LockScreen
import ke.carhire.ui.theme.CarHireTheme
import ke.carhire.ui.theme.ThemedSystemBars
import ke.carhire.ui.workspace.Workspace

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as CarHireApp
            val theme by app.appearance.theme.collectAsStateWithLifecycle()
            val unlocked by app.session.unlocked.collectAsStateWithLifecycle()
            ProvideDisplayTimeZone {
                CarHireTheme(theme) {
                    ThemedSystemBars(window)
                    Surface(Modifier.fillMaxSize()) { Workspace(app.repository, app.appearance) }
                    if (!unlocked)
                        Dialog(
                            onDismissRequest = {},
                            properties =
                                DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = false,
                                ),
                        ) {
                            Surface(Modifier.fillMaxSize()) {
                                LockScreen(this@MainActivity, app.session::unlock)
                            }
                        }
                }
            }
        }
    }
}
