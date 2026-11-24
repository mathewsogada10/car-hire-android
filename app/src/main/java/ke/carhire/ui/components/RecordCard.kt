package ke.carhire.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord

@Composable
internal fun RecordCard(
    row: LocalRecord,
    summaries: RecordSummaries,
    onEdit: (LocalRecord) -> Unit,
    onDelete: (LocalRecord) -> Unit,
) {
    val summary = summaries.summary(row)
    Card(Modifier.fillMaxWidth().testTag("record-${row.id}")) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.heading, style = MaterialTheme.typography.titleMedium)
            summary.lines.forEach { Text(it) }
            Row {
                TextButton(onClick = { onEdit(row) }) { Text("Open") }
                TextButton(onClick = { onDelete(row) }) { Text("Delete") }
            }
        }
    }
}
