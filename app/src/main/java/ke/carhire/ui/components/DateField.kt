package ke.carhire.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import ke.carhire.util.BusinessDateTime

@Composable
internal fun DateField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val invalid = value.isNotBlank() && BusinessDateTime.dateOrNull(value) == null
    val zone = LocalDisplayTimeZone.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        isError = invalid,
        supportingText = { Text(if (invalid) "Enter a valid date: YYYY-MM-DD" else "YYYY-MM-DD") },
        trailingIcon = { CalendarButton("Choose $label date", onClick = { picking = true }) },
    )
    if (picking)
        CalendarDialog(
            label,
            BusinessDateTime.dateOrNull(value) ?: LocalDate.now(zone),
            onDismiss = { picking = false },
            onConfirm = {
                onChange(it.toString())
                picking = false
            },
        )
}
