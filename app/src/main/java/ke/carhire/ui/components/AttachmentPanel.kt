package ke.carhire.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ke.carhire.LocalAttachment
import ke.carhire.data.files.AttachmentFiles
import ke.carhire.data.files.DataFiles
import ke.carhire.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AttachmentPanel(parentId: String, repository: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachments by remember { mutableStateOf<List<LocalAttachment>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<LocalAttachment?>(null) }
    var busy by remember { mutableStateOf(false) }
    val storage = remember { AttachmentFiles(context, repository) }
    LaunchedEffect(parentId, refresh) {
        attachments = repository.dao.attachments().filter { it.parentId == parentId }
    }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null && selected != null)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            DataFiles(context, repository)
                                .write(uri, storage.bytes(requireNotNull(selected)))
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Attachment export failed"
                    }
                }
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) { storage.add(parentId, uri) }
                        refresh++
                        error = ""
                    } catch (e: Exception) {
                        error = e.message ?: "Attachment not saved"
                    } finally {
                        busy = false
                    }
                }
        }
    Column {
        Text("Receipts & attachments", style = MaterialTheme.typography.titleMedium)
        ErrorText(error)
        OutlinedButton(
            enabled = !busy,
            onClick = { open.launch(arrayOf("image/png", "image/jpeg", "application/pdf")) },
        ) {
            Text("Attach PDF / photo (private offline storage)")
        }
        attachments.forEach { a ->
            TextButton(
                onClick = {
                    selected = a
                    save.launch(a.filename)
                }
            ) {
                Text("${a.filename} | ${a.syncState} | Save copy")
            }
        }
    }
}
