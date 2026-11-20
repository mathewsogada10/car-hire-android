package ke.carhire.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDateTime
import ke.carhire.ui.components.CalendarDialog
import ke.carhire.ui.components.LocalDisplayTimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BusinessDateTimePicker(
    label: String,
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    val zone = LocalDisplayTimeZone.current
    val time =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
    var choosingTime by remember { mutableStateOf(false) }
    if (!choosingTime) {
        CalendarDialog(label, date, onDismiss, "Next: time") {
            date = it
            choosingTime = true
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(label) },
            text = {
                Column {
                    Text("${zone.id} (24-hour)")
                    TimeInput(state = time, modifier = Modifier.testTag("date-time-clock"))
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(date.atTime(time.hour, time.minute)) }) {
                    Text("Use date and time")
                }
            },
            dismissButton = {
                TextButton(onClick = { choosingTime = false }) { Text("Back to date") }
            },
        )
    }
}
