package ke.carhire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import ke.carhire.LocalRecord
import ke.carhire.domain.rules.Rules
import ke.carhire.ui.components.DateField
import ke.carhire.ui.components.ErrorText
import ke.carhire.ui.components.VehicleChoices
import ke.carhire.ui.components.VehicleFilter
import ke.carhire.ui.components.money
import ke.carhire.ui.components.rememberReportCalculation

@Composable
fun Home(all: List<LocalRecord>) {
    var from by rememberSaveable { mutableStateOf("${LocalDate.now().year}-01-01") }
    var to by rememberSaveable {
        mutableStateOf(LocalDate.now(ZoneId.of("Africa/Nairobi")).toString())
    }
    var vehicle by rememberSaveable { mutableStateOf("") }
    val vehicles = remember(all) { VehicleChoices(all) }
    val calculation = rememberReportCalculation(all, from, to, vehicle, Rules::financials)
    val result = calculation?.getOrNull()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("dashboard")) {
        Text("Fleet overview", style = MaterialTheme.typography.headlineMedium)
        Text(
            "KES | Nairobi | Revenue: actual-return date. Cash: received date. Deposits are separate."
        )
        DateField(from, { from = it }, "From YYYY-MM-DD")
        DateField(to, { to = it }, "Through YYYY-MM-DD")
        VehicleFilter(vehicles, vehicle) { vehicle = it }
        if (calculation == null) Text("Calculating...")
        calculation?.exceptionOrNull()?.let { ErrorText("Cannot calculate report: ${it.message}") }
        if (all.any { it.syncState == "Purged Locally" })
            Text(
                "Purged history is excluded locally. Use its verified export, backend or restore for historical reports."
            )
        result?.forEach { (key, amount) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        key.removeSuffix("Minor")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replaceFirstChar { it.titlecase(Locale.ROOT) }
                    )
                    Text(
                        money(amount),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("dashboard-$key"),
                    )
                }
            }
        }
    }
}
