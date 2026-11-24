package ke.carhire.ui.editor

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.ZoneId
import ke.carhire.domain.rules.HireTiming
import ke.carhire.util.BusinessDateTime

internal fun hireFieldValue(values: Map<String, String>, key: String): String =
    values[key].orEmpty().ifBlank { HireTiming.plannedFields[key]?.let { values[it] }.orEmpty() }

internal fun hireDays(values: Map<String, String>, zone: ZoneId): Long? =
    try {
        HireTiming.days(
            BusinessDateTime.toInstant(values["plannedStart"].orEmpty(), "Planned pickup", zone),
            BusinessDateTime.toInstant(values["plannedEnd"].orEmpty(), "Planned return", zone),
        )
    } catch (_: IllegalArgumentException) {
        null
    }

@Composable
internal fun HireDaysField(values: Map<String, String>, zone: ZoneId) {
    OutlinedTextField(
        value = hireDays(values, zone)?.toString().orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text("Number of days") },
        supportingText = {
            Text(
                "Planned duration; partial 24-hour days round up. Enter a valid pickup and return."
            )
        },
        modifier = Modifier.testTag("hire-days"),
    )
}
