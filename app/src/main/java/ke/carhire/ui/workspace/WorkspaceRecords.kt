package ke.carhire.ui.workspace

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ke.carhire.LocalRecord
import ke.carhire.ui.screens.RecordListScreen

@Composable
internal fun ColumnScope.WorkspaceRecords(
    page: String,
    all: List<LocalRecord>,
    error: String,
    onAdd: () -> Unit,
    onEdit: (LocalRecord) -> Unit,
    onDelete: (LocalRecord) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    RecordListScreen(
        page,
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
        onAdd,
        onEdit,
        onDelete,
    )
}
