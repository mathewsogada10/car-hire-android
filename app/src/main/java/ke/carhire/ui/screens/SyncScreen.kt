package ke.carhire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.data.repository.Repository
import ke.carhire.security.Vault
import ke.carhire.sync.SyncClient
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.LocalDisplayTimeZone
import ke.carhire.ui.components.title
import ke.carhire.util.BusinessDateTime
import ke.carhire.util.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SyncStatus(val message: String, val completedAt: String? = null)

@Composable
fun SyncScreen(repository: Repository, all: List<LocalRecord>) {
    val zone = LocalDisplayTimeZone.current
    val context = LocalContext.current
    val vault = remember { Vault(context) }
    var url by remember { mutableStateOf(vault.get("backendUrl").orEmpty()) }
    var user by remember { mutableStateOf(vault.get("backendUser") ?: "owner") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(SyncStatus("")) }
    var reason by remember { mutableStateOf("") }
    var health by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(all, status, zone) {
        val ops = repository.dao.operations()
        val lastSync =
            repository.dao.meta("lastSync")?.value?.let { BusinessDateTime.display(it, zone) }
                ?: "Never"
        val lastBackup =
            repository.dao.meta("lastBackup")?.value?.let { BusinessDateTime.display(it, zone) }
                ?: "Never"
        health =
            "Pending: ${ops.count { it.state == "Pending" || it.state == "Syncing" }} | Errors: ${ops.count { it.state == "Error" }} | Conflicts: ${all.count { it.syncState == "Conflict" }}\nLast sync: $lastSync\nLast backup: $lastBackup\nFree storage: ${context.filesDir.usableSpace / 1024 / 1024} MiB\n${repository.dao.meta("syncError")?.value.orEmpty()}\n${ops.filter { it.error != null }.joinToString("\n") { "${it.entityId.take(8)}: ${it.error}" }}"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Sync & data health", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sync is optional. All business records are saved locally first. Configure an HTTPS backend to enable sync."
        )
        Text(health)
        ErrorText(error)
        Text(
            status.message +
                status.completedAt?.let { " at ${BusinessDateTime.display(it, zone)}" }.orEmpty()
        )
        OutlinedTextField(
            url,
            { url = it },
            label = { Text("Backend URL (https://...)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            user,
            { user = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password (stored with Android Keystore)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    try {
                        require(url.startsWith("https://")) { "HTTPS is required" }
                        require(user.isNotBlank() && password.length >= 12)
                        withContext(Dispatchers.IO) {
                            vault.put("backendUrl", url.trimEnd('/'))
                            vault.put("backendUser", user)
                            vault.put("backendPassword", password)
                        }
                        password = ""
                        status = SyncStatus("Profile saved securely; background sync enabled.")
                    } catch (e: Exception) {
                        error = e.message ?: "Profile save failed"
                    }
                }
            },
        ) {
            Text("Save backend profile")
        }
        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { SyncClient(repository, vault).run() }
                        status = SyncStatus("Sync completed", now())
                        error = ""
                    } catch (e: Exception) {
                        error = e.message ?: "Sync failed; local changes retained"
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            Text("Sync / retry network failures now")
        }
        HorizontalDivider()
        Text("Explicit conflict resolution", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            reason,
            { reason = it },
            label = { Text("Resolution reason (required)") },
            modifier = Modifier.fillMaxWidth(),
        )
        all.filter { it.syncState == "Conflict" }
            .forEach { row ->
                Text("${row.kind}: ${title(row, zone)}")
                Text(BusinessDateTime.displayPayload(row.payload, zone))
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    repository.resolve(row.id, true, reason)
                                    status = SyncStatus("Server version kept")
                                } catch (e: Exception) {
                                    error = e.message ?: "Resolution failed"
                                }
                            }
                        }
                    ) {
                        Text("Keep server")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    repository.resolve(row.id, false, reason)
                                    status = SyncStatus("Local version queued as a new revision")
                                } catch (e: Exception) {
                                    error = e.message ?: "Resolution failed"
                                }
                            }
                        }
                    ) {
                        Text("Keep local (new revision)")
                    }
                }
            }
    }
}
