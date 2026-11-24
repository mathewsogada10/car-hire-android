package ke.carhire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import ke.carhire.LocalRecord
import ke.carhire.domain.model.FinancialMonth
import ke.carhire.domain.rules.MonthlyFinancials
import ke.carhire.ui.components.*
import ke.carhire.util.BusinessDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FinancialReportScreen(all: List<LocalRecord>) {
    val today = remember { LocalDate.now(ZoneId.of("Africa/Nairobi")) }
    var from by rememberSaveable { mutableStateOf("${today.year}-01-01") }
    var to by rememberSaveable { mutableStateOf(today.toString()) }
    var vehicle by rememberSaveable { mutableStateOf("") }
    val vehicles = remember(all) { VehicleChoices(all) }
    var calculation by remember { mutableStateOf<Result<List<FinancialMonth>>?>(null) }
    LaunchedEffect(all, from, to, vehicle) {
        calculation = null
        calculation =
            withContext(Dispatchers.Default) {
                runCatching {
                    MonthlyFinancials.calculate(
                        all,
                        BusinessDateTime.date(from, "From"),
                        BusinessDateTime.date(to, "Through"),
                        vehicle,
                    )
                }
            }
    }
    LazyColumn(Modifier.testTag("financial-report"), contentPadding = PaddingValues(16.dp)) {
        item {
            Column {
                Text("Monthly financial trend", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Cash basis (KES): received payments less refunds, paid expenses, and net profit = payments - expenses. Security deposits are excluded."
                )
                DateField(from, { from = it }, "From YYYY-MM-DD")
                DateField(to, { to = it }, "Through YYYY-MM-DD")
                VehicleFilter(vehicles, vehicle) { vehicle = it }
                Text(
                    "Any financial year is supported. First and last months use only the selected dates; empty months show zero."
                )
                if (all.any { it.syncState == "Purged Locally" })
                    Text(
                        "Purged local history is excluded; restore or use the backend for that history."
                    )
                if (calculation == null) Text("Calculating monthly trend...")
                calculation?.exceptionOrNull()?.let {
                    ErrorText("Cannot calculate report: ${it.message}")
                }
            }
        }
        calculation?.getOrNull()?.let { months ->
            item { FinancialLineChart(months) }
            item { Text("Monthly amounts (KES)", style = MaterialTheme.typography.titleLarge) }
            items(months, key = { it.month.toString() }) { month ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(month.month.toString(), style = MaterialTheme.typography.titleMedium)
                        Text("Payments received: ${money(month.paymentsReceivedMinor)}")
                        Text("Paid expenses: ${money(month.expensesPaidMinor)}")
                        Text("Net profit (cash basis): ${money(month.netProfitMinor)}")
                    }
                }
            }
        }
    }
}
