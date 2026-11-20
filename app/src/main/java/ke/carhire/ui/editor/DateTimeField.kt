package ke.carhire.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDateTime
import ke.carhire.ui.components.CalendarButton
import ke.carhire.ui.components.LocalDisplayTimeZone
import ke.carhire.util.BusinessDateTime

@Composable
internal fun DateTimeField(field: Field, value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val zone = LocalDisplayTimeZone.current
    val error =
        remember(value, zone) {
            if (value.isBlank()) null
            else
                try {
                    BusinessDateTime.toInstant(value, field.label, zone)
                    null
                } catch (e: IllegalArgumentException) {
                    e.message
                }
        }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label + if (field.optional) " (optional)" else "") },
            modifier = Modifier.fillMaxWidth().testTag("instant-${field.key}"),
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            supportingText = { Text(error ?: "YYYY-MM-DDTHH:mm | ${zone.id} (24-hour)") },
            trailingIcon = {
                CalendarButton(
                    "Choose ${field.label} date and time",
                    Modifier.testTag("calendar-${field.key}"),
                ) {
                    picking = true
                }
            },
        )
        if (field.optional && value.isNotBlank()) {
            TextButton(onClick = { onChange("") }) { Text("Clear ${field.label}") }
        }
    }
    if (picking) {
        key(zone) {
            val initial = remember {
                if (value.isBlank() || error != null) LocalDateTime.now(zone)
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
}
