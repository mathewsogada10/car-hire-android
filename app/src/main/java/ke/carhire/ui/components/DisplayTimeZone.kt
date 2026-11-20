package ke.carhire.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.ZoneId

internal val LocalDisplayTimeZone = compositionLocalOf { ZoneId.systemDefault() }

@Composable
internal fun ProvideDisplayTimeZone(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(context, lifecycle) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    zone = ZoneId.systemDefault()
                }
            }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) zone = ZoneId.systemDefault()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycle.addObserver(observer)
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(observer)
        }
    }
    CompositionLocalProvider(LocalDisplayTimeZone provides zone, content = content)
}
