package ke.carhire

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.CopyOnWriteArrayList
import ke.carhire.ui.components.rememberReportCalculation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportCalculationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Query(
        val vehicle: String = "",
        val from: String = "2026-01-01",
        val to: String = "2026-11-28",
        val records: List<LocalRecord> = emptyList(),
    ) {
        val expected: String
            get() = "${records.size}|$from|$to|$vehicle"
    }

    @Test
    fun changingVehicleDatesOrRecordsNeverPublishesThePreviousScopesAmounts() {
        var query by mutableStateOf(Query())
        val displayed = CopyOnWriteArrayList<Pair<Query, String>>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                activity.setContent {
                    val current = query
                    val calculation =
                        rememberReportCalculation(
                            current.records,
                            current.from,
                            current.to,
                            current.vehicle,
                        ) { rows, from, to, vehicle ->
                            "${rows.size}|$from|$to|$vehicle"
                        }
                    val value = calculation?.getOrThrow()
                    SideEffect { if (value != null) displayed.add(current to value) }
                    Text(value ?: "Calculating...")
                }
            }
            fun awaitCurrent() {
                compose.waitUntil(10000) {
                    compose.onAllNodesWithText(query.expected).fetchSemanticsNodes().isNotEmpty()
                }
            }
            awaitCurrent()
            for (next in
                listOf(
                    Query(vehicle = "first"),
                    Query(vehicle = "second"),
                    Query(vehicle = "second", from = "2026-02-01"),
                    Query(vehicle = "second", to = "2026-10-31"),
                    Query(
                        vehicle = "second",
                        records = listOf(LocalRecord("new", "expenses", payload = "{}")),
                    ),
                    Query(),
                )) {
                compose.runOnIdle { query = next }
                awaitCurrent()
            }
        }
        assertEquals(
            emptyList<Pair<Query, String>>(),
            displayed.filter { (scope, value) -> scope.expected != value },
        )
    }
}
