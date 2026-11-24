package ke.carhire.ui.workspace

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.carhire.LocalRecord
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.data.repository.Repository
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.LocalDisplayTimeZone
import ke.carhire.ui.components.title
import ke.carhire.ui.editor.RecordEditor
import ke.carhire.ui.screens.AuditScreen
import ke.carhire.ui.screens.DataScreen
import ke.carhire.ui.screens.Home
import ke.carhire.ui.screens.SettingsScreen
import ke.carhire.ui.screens.SyncScreen
import kotlinx.coroutines.launch

@Composable
fun Workspace(repository: Repository, appearance: AppearancePreferences) {
    val zone = LocalDisplayTimeZone.current
    val all by repository.records.collectAsStateWithLifecycle(initialValue = emptyList())
    val history = rememberSaveable(saver = WorkspaceHistory.saver) { WorkspaceHistory() }
    val stateHolder = rememberSaveableStateHolder()
    val entry = history.current
    val page = entry.page
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LocalRecord?>(null) }
    fun close(key: String = entry.key) {
        stateHolder.removeState(key)
        history.close(key)
    }
    BackHandler(enabled = page != "home" || entry.editor) { close() }
    LaunchedEffect(repository) {
        try {
            repository.seedCategories()
        } catch (e: Exception) {
            error = e.message ?: "Unable to initialize expense categories"
        }
    }
    WorkspaceNavigation(
        page,
        { target ->
            if (target == "home") history.entries.forEach { stateHolder.removeState(it.key) }
            history.navigate(target)
        },
    ) { openNavigation, navigationOpen ->
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ErrorText(error)
            WorkspaceHeader(page, openNavigation) { history.navigate("settings") }
            val kind = page
            stateHolder.SaveableStateProvider(entry.key) {
                when {
                    entry.editor ->
                        RecordEditor(
                            kind,
                            entry.row,
                            all,
                            repository,
                            { close(entry.key) },
                            navigationOpen,
                        )
                    page == "home" -> Home(all)
                    page == "data" -> DataScreen(repository)
                    page == "sync" -> SyncScreen(repository, all)
                    page == "audit" -> AuditScreen(repository, all)
                    page == "settings" ->
                        SettingsScreen(appearance) {
                            history.edit(all.find { it.id == SETTINGS_ID })
                        }
                    else ->
                        WorkspaceRecords(
                            kind,
                            all,
                            error,
                            onAdd = { history.edit() },
                            onEdit = { history.edit(it) },
                            onDelete = { deleting = it },
                        )
                }
            }
        }
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${title(row, zone)}?") },
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
