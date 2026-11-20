package ke.carhire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.domain.model.ThemePreference
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.LocalDisplayTimeZone
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(appearance: AppearancePreferences, onConfigure: () -> Unit) {
    val zone = LocalDisplayTimeZone.current
    val selected by appearance.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LazyColumn(
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge) }
        ThemePreference.entries.forEach { option ->
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = selected == option,
                            enabled = !busy,
                            role = Role.RadioButton,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        appearance.setTheme(option)
                                        error = ""
                                    } catch (e: IOException) {
                                        error = e.message ?: "Unable to save appearance"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected == option, onClick = null, enabled = !busy)
                    Text(option.label)
                }
            }
        }
        item {
            Text("Current theme: ${if (dark) "Dark" else "Light"}")
            Text("Saved on this device only. System default follows device appearance.")
            Text(
                "Time display: ${zone.id}. Timestamps are stored in UTC; business dates and reminder schedules retain the configured business timezone."
            )
            ErrorText(error)
        }
        item { Text("Business settings", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("Configure reminders, advance notice, daily time and the turnaround buffer.")
            Button(onClick = onConfigure) { Text("Configure business settings") }
        }
    }
}
