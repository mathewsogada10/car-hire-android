package ke.carhire.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun BackupControls(
    passphrase: String,
    onPassphrase: (String) -> Unit,
    pin: String,
    onPin: (String) -> Unit,
    busy: Boolean,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
) {
    Text("Encrypted backup / replace restore", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        passphrase,
        onPassphrase,
        label = { Text("Recovery passphrase (12+ characters)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        pin,
        onPin,
        label = { Text("Current PIN (required for restore/purge)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(enabled = !busy, onClick = onCreate) { Text("Create encrypted full backup") }
    OutlinedButton(enabled = !busy, onClick = onRestore) {
        Text("Restore backup (replaces local data)")
    }
}
