package ke.carhire.ui.editor

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap

internal val editorValuesSaver =
    listSaver<SnapshotStateMap<String, String>, String>(
        save = { values -> values.entries.flatMap { listOf(it.key, it.value) } },
        restore = { entries ->
            mutableStateMapOf<String, String>().apply {
                entries.chunked(2).forEach { (key, value) -> put(key, value) }
            }
        },
    )
