package ke.carhire.ui.workspace

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.toMutableStateList
import ke.carhire.LocalRecord
import ke.carhire.util.json
import ke.carhire.util.uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal data class WorkspaceEntry(
    val page: String,
    val key: String = uuid(),
    val editor: Boolean = false,
    val row: LocalRecord? = null,
)

internal class WorkspaceHistory(initial: List<WorkspaceEntry> = listOf(WorkspaceEntry("home"))) {
    val entries = initial.toMutableStateList()
    val current
        get() = entries.last()

    fun navigate(page: String) {
        if (page == current.page && !current.editor) return
        if (page == "home") entries.clear()
        entries.add(WorkspaceEntry(page))
    }

    fun edit(row: LocalRecord? = null) {
        entries.add(WorkspaceEntry(current.page, editor = true, row = row))
    }

    fun close(key: String = current.key) {
        if (entries.size > 1) entries.removeAll { it.key == key }
    }

    companion object {
        val saver =
            Saver<WorkspaceHistory, String>(
                save = { json.encodeToString(it.entries.toList()) },
                restore = { WorkspaceHistory(json.decodeFromString<List<WorkspaceEntry>>(it)) },
            )
    }
}
