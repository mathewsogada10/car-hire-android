package ke.carhire.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import ke.carhire.Evidence
import ke.carhire.data.files.DataFiles
import ke.carhire.data.files.PurgePreview
import ke.carhire.data.repository.Repository
import ke.carhire.security.Vault
import ke.carhire.ui.components.ErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataScreen(repository: Repository) {
    val context = LocalContext.current
    val files = remember { DataFiles(context, repository) }
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var exportId by remember { mutableStateOf("") }
    var through by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportWriteId by remember { mutableStateOf("") }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<PurgePreview?>(null) }
    var exports by remember { mutableStateOf<List<Evidence>>(emptyList()) }
    LaunchedEffect(status) {
        exports = repository.dao.evidence().filter { it.kind == "export-created" }
    }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = ""
            try {
                action()
            } catch (e: Exception) {
                error = e.message ?: "Data operation failed; no success confirmed"
            } finally {
                busy = false
            }
        }
    }
    val saveFile =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null && bytes != null)
                run {
                    withContext(Dispatchers.IO) {
                        if (exportWriteId.isNotEmpty()) {
                            files.write(uri, requireNotNull(bytes))
                            files.verifyExportWritten(exportWriteId, uri)
                        } else files.writeBackup(uri, requireNotNull(bytes))
                    }
                    status =
                        if (exportWriteId.isNotEmpty())
                            "Export reopened and verified. Store it safely."
                        else "Encrypted backup written and verified."
                    bytes = null
                    exportWriteId = ""
                }
        }
    val safetyDestination =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null && restoreBytes != null)
                run {
                    require(Vault(context).unlock(pin)) { "Incorrect PIN" }
                    pin = ""
                    withContext(Dispatchers.IO) {
                        files.restore(requireNotNull(restoreBytes), passphrase.toCharArray(), uri)
                    }
                    restoreBytes = null
                    passphrase = ""
                    status =
                        "Restore completed. Encrypted safety backup retained at your selected destination."
                }
        }
    val openBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                run {
                    restoreBytes = withContext(Dispatchers.IO) { files.read(uri) }
                    safetyDestination.launch("car-hire-safety-backup.chbk")
                }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Data management", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No automatic deletion. Portable exports are not encrypted full backups. External copies cannot be revoked."
        )
        ErrorText(error)
        if (status.isNotEmpty()) Text(status)
        BackupControls(
            passphrase,
            { passphrase = it },
            pin,
            { pin = it },
            busy,
            onCreate = {
                run {
                    bytes = withContext(Dispatchers.IO) { files.backup(passphrase.toCharArray()) }
                    exportWriteId = ""
                    saveFile.launch("car-hire-backup.chbk")
                }
            },
            onRestore = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
        )
        HorizontalDivider()
        Text("Portable export", style = MaterialTheme.typography.titleLarge)
        Button(
            enabled = !busy,
            onClick = {
                run {
                    val result = withContext(Dispatchers.IO) { files.portable() }
                    exportWriteId = result.first
                    exportId = result.first
                    bytes = result.second
                    saveFile.launch(
                        "car-hire-export_${java.time.LocalDate.now()}_${result.first}.zip"
                    )
                }
            },
        ) {
            Text("Create portable ZIP")
        }
        Text("Export history", style = MaterialTheme.typography.titleMedium)
        exports.take(10).forEach { e ->
            TextButton(
                onClick = {
                    exportId = e.id
                    preview = null
                }
            ) {
                Text("${e.createdAt.take(10)} | ${e.id.take(8)}")
            }
        }
        OutlinedTextField(
            exportId,
            {
                exportId = it
                preview = null
            },
            label = { Text("Selected export batch UUID") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            confirmation,
            { confirmation = it },
            label = { Text("Typed safe-storage / PURGE confirmation") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Safe-storage phrase: I VERIFIED AND SAFELY STORED THIS EXPORT")
        OutlinedButton(
            enabled = !busy,
            onClick = {
                run {
                    require(Vault(context).unlock(pin)) { "Incorrect PIN" }
                    pin = ""
                    files.acknowledge(exportId, confirmation)
                    status = "Safe-storage acknowledgement recorded."
                    confirmation = ""
                }
            },
        ) {
            Text("Acknowledge verified safe storage")
        }
        OutlinedTextField(
            through,
            {
                through = it
                preview = null
            },
            label = { Text("Purge through YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = !busy,
            onClick = { run { preview = files.preview(exportId, through) } },
        ) {
            Text("Preview eligible purge")
        }
        preview?.let { p ->
            Text(
                "${p.ids.size} eligible; ${p.excluded} protected, changed, unsynced or outside scope. Masters, current reminders, evidence and unsettled deposits are retained. Type PURGE ${p.ids.size}. No undo except restore/import."
            )
            Button(
                enabled = !busy && p.ids.isNotEmpty(),
                onClick = {
                    run {
                        require(Vault(context).unlock(pin)) { "Incorrect PIN" }
                        pin = ""
                        files.purge(p, confirmation)
                        status = "Eligible local-only payloads purged; evidence retained."
                        preview = null
                    }
                },
            ) {
                Text("Permanently purge eligible payloads")
            }
        }
    }
}
