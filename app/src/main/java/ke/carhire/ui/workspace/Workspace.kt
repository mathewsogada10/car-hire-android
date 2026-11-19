package ke.carhire.ui.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.carhire.LocalRecord
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.data.repository.Repository
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.title
import ke.carhire.ui.editor.RecordEditor
import ke.carhire.ui.screens.AuditScreen
import ke.carhire.ui.screens.DataScreen
import ke.carhire.ui.screens.Home
import ke.carhire.ui.screens.RecordListScreen
import ke.carhire.ui.screens.SettingsScreen
import ke.carhire.ui.screens.SyncScreen
import kotlinx.coroutines.launch

@Composable
fun Workspace(repository: Repository, appearance: AppearancePreferences) {
    val all by repository.records.collectAsStateWithLifecycle(initialValue = emptyList())
    var page by remember { mutableStateOf("home") }
    var editing by remember { mutableStateOf<LocalRecord?>(null) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LocalRecord?>(null) }
    fun navigate(key: String) {
        page = key
        editing = null
        creating = false
        query = ""
        status = ""
        from = ""
        to = ""
    }
    LaunchedEffect(repository) {
        try {
            repository.seedCategories()
        } catch (e: Exception) {
            error = e.message ?: "Unable to initialize expense categories"
        }
    }
    WorkspaceNavigation(page, ::navigate) { openNavigation ->
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ErrorText(error)
            WorkspaceHeader(page, openNavigation) { navigate("settings") }
            val kind = if (page == "calendar") "hires" else page
            when {
                creating || editing != null ->
                    RecordEditor(
                        kind,
                        editing,
                        all,
                        repository,
                        {
                            creating = false
                            editing = null
                        },
                    )
                page == "home" -> Home(all)
                page == "data" -> DataScreen(repository)
                page == "sync" -> SyncScreen(repository, all)
                page == "audit" -> AuditScreen(repository, all)
                page == "settings" ->
                    SettingsScreen(appearance) {
                        editing = all.find { it.id == SETTINGS_ID }
                        creating = editing == null
                    }
                else ->
                    RecordListScreen(
                        page,
                        kind,
                        all,
                        query,
                        { query = it },
                        status,
                        { status = it },
                        from,
                        { from = it },
                        to,
                        { to = it },
                        error,
                        onAdd = {
                            if (kind == "settings") editing = all.find { it.id == SETTINGS_ID }
                            creating = editing == null
                        },
                        onEdit = { editing = it },
                        onDelete = { deleting = it },
                    )
            }
        }
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${title(row)}?") },
            text = {
                Text("This creates a tombstone; protected or referenced records cannot be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repository.save(row.kind, row.id, row.data(), row.updatedAt, true)
                                deleting = null
                            } catch (e: Exception) {
                                error = e.message ?: "Delete failed"
                                deleting = null
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
