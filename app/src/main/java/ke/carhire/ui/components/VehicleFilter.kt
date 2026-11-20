package ke.carhire.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag

@Composable
internal fun VehicleFilter(
    choices: VehicleChoices,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val matches = remember(choices, query) { choices.matching(query, 6) }
    val selected = choices.selected(selectedId)
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search vehicle registration") },
            supportingText = {
                Text("Type a registration, then select a matching vehicle to filter the report.")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("dashboard-vehicle-search"),
        )
        if (query.isNotBlank()) {
            if (matches.isEmpty())
                Text("No matching registrations. The report filter has not changed.")
            matches.take(5).forEach { vehicle ->
                TextButton(
                    onClick = {
                        onSelect(vehicle.id)
                        query = ""
                        focus.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dashboard-vehicle-${vehicle.id}"),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(vehicle.label)
                        Text(
                            listOf(vehicle.description, "ID: ${vehicle.id.take(8)}")
                                .filter { it.isNotBlank() }
                                .joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (matches.size > 5) Text("More vehicles match. Refine the registration to see them.")
        }
        Text(
            "Report vehicle: " +
                when {
                    selectedId.isEmpty() -> "All vehicles"
                    selected != null -> selected.label
                    else ->
                        "Vehicle unavailable (${selectedId.take(8)}). Choose another vehicle or All vehicles."
                },
            modifier = Modifier.testTag("dashboard-selected-vehicle"),
        )
        if (selectedId.isNotEmpty() || query.isNotEmpty()) {
            TextButton(
                onClick = {
                    onSelect("")
                    query = ""
                    focus.clearFocus()
                }
            ) {
                Text("All vehicles")
            }
        }
    }
}
