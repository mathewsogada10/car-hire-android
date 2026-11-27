package ke.carhire.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import ke.carhire.LocalRecord
import ke.carhire.util.BusinessDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun <T> rememberReportCalculation(
    records: List<LocalRecord>,
    from: String,
    to: String,
    vehicleId: String,
    calculate: (List<LocalRecord>, LocalDate, LocalDate, String) -> T,
): Result<T>? {
    var result by remember(records, from, to, vehicleId) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(records, from, to, vehicleId) {
        result = null
        result =
            withContext(Dispatchers.Default) {
                runCatching {
                    calculate(
                        records,
                        BusinessDateTime.date(from, "From"),
                        BusinessDateTime.date(to, "Through"),
                        vehicleId,
                    )
                }
            }
    }
    return result
}
