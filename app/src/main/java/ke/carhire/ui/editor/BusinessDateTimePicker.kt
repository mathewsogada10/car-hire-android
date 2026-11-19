package ke.carhire.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BusinessDateTimePicker(
    label: String,
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val date =
        rememberDatePickerState(
            initialSelectedDateMillis = BusinessDateTime.pickerMillis(initial.toLocalDate())
        )
    val time =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
    var choosingTime by remember { mutableStateOf(false) }
    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { choosingTime = true },
                    enabled = date.selectedDateMillis != null,
                ) {
                    Text("Next: time")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = date, title = { Text(label, Modifier.padding(24.dp)) })
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(label) },
            text = {
                Column {
                    Text("Nairobi time (24-hour)")
                    TimeInput(state = time, modifier = Modifier.testTag("date-time-clock"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            BusinessDateTime.pickerDate(requireNotNull(date.selectedDateMillis))
                                .atTime(time.hour, time.minute)
                        )
                    }
                ) {
                    Text("Use date and time")
                }
            },
            dismissButton = {
                TextButton(onClick = { choosingTime = false }) { Text("Back to date") }
            },
        )
    }
}
