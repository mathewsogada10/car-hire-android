package ke.carhire.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import ke.carhire.util.BusinessDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDialog(
    label: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    confirmLabel: String = "Use date",
    onConfirm: (LocalDate) -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = BusinessDateTime.pickerMillis(initial),
            yearRange = minOf(1900, initial.year)..maxOf(2100, initial.year),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    onConfirm(BusinessDateTime.pickerDate(requireNotNull(state.selectedDateMillis)))
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, title = { Text(label, Modifier.padding(24.dp)) })
    }
}
