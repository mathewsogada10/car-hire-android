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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.ui.components.DateField
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.LocalDisplayTimeZone
import ke.carhire.ui.components.RecordCard
import ke.carhire.ui.components.StatusFilter
import ke.carhire.ui.components.filterRecords
import ke.carhire.ui.editor.Forms

@Composable
fun ColumnScope.RecordListScreen(
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
    val zone = LocalDisplayTimeZone.current
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Forms.titles[kind].orEmpty(), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onAdd) { Text(if (kind == "settings") "Configure" else "+ Add") }
    }
    OutlinedTextField(
        query,
        onQuery,
        label = { Text("Search records") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    val statuses = Forms.fields.getValue(kind).firstOrNull { it.key == "status" }?.options.orEmpty()
    if (statuses.isNotEmpty())
        StatusFilter(
            status,
            statuses,
            onStatus,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    if (kind == "hires")
        Text("Ordered by planned pickup (earliest first)", Modifier.padding(horizontal = 16.dp))
    if (kind in listOf("hires", "payments", "expenses", "maintenance", "readings", "damages"))
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateField(from, onFrom, label = "From", modifier = Modifier.weight(1f))
            DateField(to, onTo, label = "Through", modifier = Modifier.weight(1f))
        }
    ErrorText(error)
    val filtered = filterRecords(all, kind, query, status, from, to, zone)
    LazyColumn(
        Modifier.weight(1f).padding(16.dp).testTag("record-list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filtered.isEmpty()) item { Text("No matching records. Add one or adjust filters.") }
        items(filtered, key = { it.id }) { row -> RecordCard(row, onEdit, onDelete) }
    }
}
