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
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.util.amount
import ke.carhire.util.text

@Composable
fun RecordCard(row: LocalRecord, onEdit: (LocalRecord) -> Unit, onDelete: (LocalRecord) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title(row), style = MaterialTheme.typography.titleMedium)
            Text("${row.data().text("status")} | ${row.syncState}")
            if (row.data().containsKey("amountMinor")) Text(money(row.data().amount("amountMinor")))
            if (row.kind == "hires")
                Text("${row.data().text("plannedStart")} -> ${row.data().text("plannedEnd")}")
            Row {
                TextButton(onClick = { onEdit(row) }) { Text("Open") }
                TextButton(onClick = { onDelete(row) }) { Text("Delete") }
            }
        }
    }
}
