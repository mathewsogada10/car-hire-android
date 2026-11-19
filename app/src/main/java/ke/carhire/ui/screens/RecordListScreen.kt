package ke.carhire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.RecordCard
import ke.carhire.ui.editor.Forms
import ke.carhire.util.text

@Composable
fun ColumnScope.RecordListScreen(
    page: String,
    kind: String,
    all: List<LocalRecord>,
    query: String,
    onQuery: (String) -> Unit,
    status: String,
    onStatus: (String) -> Unit,
    from: String,
    onFrom: (String) -> Unit,
    to: String,
    onTo: (String) -> Unit,
    error: String,
    onAdd: () -> Unit,
    onEdit: (LocalRecord) -> Unit,
    onDelete: (LocalRecord) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (page == "calendar") "Hire calendar" else Forms.titles[kind].orEmpty(),
            style = MaterialTheme.typography.titleLarge,
        )
        Button(onClick = onAdd) { Text(if (kind == "settings") "Configure" else "+ Add") }
    }
    OutlinedTextField(
        query,
        onQuery,
        label = { Text("Search records") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    OutlinedTextField(
        status,
        onStatus,
        label = { Text("Status filter (optional)") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    if (kind in listOf("hires", "payments", "expenses", "maintenance", "readings", "damages"))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                from,
                onFrom,
                label = { Text("From YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                to,
                onTo,
                label = { Text("Through YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
            )
        }
    ErrorText(error)
    val filtered =
        all.filter { row ->
                val d = row.data()
                val date = d.text("date").ifEmpty { d.text("plannedStart").take(10) }
                row.kind == kind &&
                    row.deletedAt == null &&
                    (query.isBlank() || row.payload.contains(query, true)) &&
                    (status.isBlank() || d.text("status") == status) &&
                    (from.isBlank() || date >= from) &&
                    (to.isBlank() || date <= to)
            }
            .let {
                if (page == "calendar") it.sortedBy { r -> r.data().text("plannedStart") } else it
            }
    LazyColumn(
        Modifier.weight(1f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filtered.isEmpty()) item { Text("No matching records. Add one or adjust filters.") }
        items(filtered, key = { it.id }) { row -> RecordCard(row, onEdit, onDelete) }
    }
}
