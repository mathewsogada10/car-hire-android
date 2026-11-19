package ke.carhire.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDateTime

@Composable
internal fun DateTimeField(field: Field, value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        Text(
            field.label + if (field.optional) " (optional)" else "",
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().testTag("instant-${field.key}"),
        ) {
            Text(if (value.isBlank()) "Choose date and time" else value.replace('T', ' '))
        }
        Text("Nairobi time (UTC+03:00)", style = MaterialTheme.typography.bodySmall)
        if (field.optional && value.isNotBlank()) {
            TextButton(onClick = { onChange("") }) { Text("Clear ${field.label}") }
        }
    }
    if (picking) {
        val initial = remember {
            if (value.isBlank()) LocalDateTime.now(BusinessDateTime.zone)
            else BusinessDateTime.local(value, field.label)
        }
        BusinessDateTimePicker(
            field.label,
            initial,
            onDismiss = { picking = false },
            onConfirm = {
                onChange(it.toString())
                picking = false
            },
        )
    }
}
