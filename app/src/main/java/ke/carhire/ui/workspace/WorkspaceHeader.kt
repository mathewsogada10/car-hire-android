package ke.carhire.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ke.carhire.R

@Composable
fun WorkspaceHeader(page: String, onNavigation: () -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigation) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = "Open navigation menu")
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("Car Hire", style = MaterialTheme.typography.titleMedium)
            Text(
                workspaceTitle(page),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("workspace-page-title"),
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        expanded = false
                        onSettings()
                    },
                )
            }
        }
    }
}
