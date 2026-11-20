package ke.carhire.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun StatusFilter(
    status: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("record-status-filter"),
        ) {
            Text("Status: ${status.ifEmpty { "All statuses" }}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf("") + options).forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.ifEmpty { "All statuses" }) },
                    onClick = {
                        onChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
