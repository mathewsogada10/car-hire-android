package ke.carhire

import java.time.LocalDate
import ke.carhire.domain.rules.Rules
import ke.carhire.ui.components.VehicleChoices
import org.junit.Assert.*
import org.junit.Test

class VehicleChoicesTest {
    private fun vehicle(id: String, registration: String) =
        LocalRecord(
            id,
            "vehicles",
            payload = """{"registration":"$registration","make":"Toyota","model":"Axio"}""",
        )

    @Test
    fun registrationSearchIgnoresCaseAndSpacesButNeverMatchesIdsOrOtherFields() {
        val vehicle = vehicle("b10f44fe-435d-4978-b394-bcbbe5e38b8a", "KDA123A")
        val choices =
            VehicleChoices(
                listOf(
                    vehicle,
                    LocalRecord("customer", "customers", payload = """{"name":"KDA123A"}"""),
                )
            )
        assertEquals(vehicle.id, choices.matching("  kDa 12  ", 6).single().id)
        for (query in listOf(vehicle.id, "Toyota", "Axio", "not found", " ")) {
            assertTrue(choices.matching(query, 6).isEmpty())
        }
    }

    @Test
    fun historicalVehiclesStayDistinctAndSelectionDoesNotDependOnVisibleSearchResults() {
        val active = vehicle("active", "KAA001A")
        val archived = vehicle("archived", "KAA001A").copy(deletedAt = "2026-01-01T00:00:00Z")
        val later = vehicle("later", "KZZ999Z")
        val choices =
            VehicleChoices(
                listOf(
                    active,
                    archived,
                    later,
                    vehicle("purged", "KPP001A").copy(syncState = "Purged Locally"),
                )
            )
        val matches = choices.matching("kaa", 6)
        assertEquals(setOf(active.id, archived.id), matches.map { it.id }.toSet())
        assertTrue(matches.single { it.id == archived.id }.archived)
        assertEquals(later.id, choices.selected(later.id)?.id)
        assertEquals(1, choices.matching("K", 1).size)
        assertTrue(choices.matching("KPP", 6).isEmpty())
        assertNull(choices.selected("missing"))
    }

    @Test
    fun selectingARegistrationScopesFinancialResultsToItsUnderlyingVehicleId() {
        val first = vehicle("b10f44fe-435d-4978-b394-bcbbe5e38b8a", "KDA123A")
        val second = vehicle("d8256ad5-dd7c-43da-864f-df2bbf5b5231", "KDB456B")
        val rows =
            listOf(
                first,
                second,
                LocalRecord(
                    "hire1",
                    "hires",
                    payload =
                        """{"vehicleId":"${first.id}","status":"Completed","plannedStart":"2026-01-01T10:00:00Z","actualEnd":"2026-01-02T10:00:00Z","chargeMinor":100000}""",
                ),
                LocalRecord(
                    "hire2",
                    "hires",
                    payload =
                        """{"vehicleId":"${second.id}","status":"Completed","plannedStart":"2026-01-01T10:00:00Z","actualEnd":"2026-01-02T10:00:00Z","chargeMinor":900000}""",
                ),
                LocalRecord(
                    "payment1",
                    "payments",
                    payload =
                        """{"hireId":"hire1","status":"Received","kind":"Payment","date":"2026-01-02","amountMinor":75000}""",
                ),
                LocalRecord(
                    "payment2",
                    "payments",
                    payload =
                        """{"hireId":"hire2","status":"Received","kind":"Payment","date":"2026-01-02","amountMinor":850000}""",
                ),
                LocalRecord(
                    "expense1",
                    "expenses",
                    payload =
                        """{"vehicleId":"${first.id}","status":"Paid","date":"2026-01-02","paidDate":"2026-01-02","amountMinor":10000}""",
                ),
                LocalRecord(
                    "expense2",
                    "expenses",
                    payload =
                        """{"vehicleId":"${second.id}","status":"Paid","date":"2026-01-02","paidDate":"2026-01-02","amountMinor":50000}""",
                ),
            )
        val selected = VehicleChoices(rows).matching("kda 123", 6).single()
        val from = LocalDate.parse("2026-01-01")
        val through = LocalDate.parse("2026-01-31")
        val report = Rules.financials(rows, from, through, selected.id)
        assertEquals(100000L, report["recognizedMinor"])
        assertEquals(75000L, report["collectionsMinor"])
        assertEquals(10000L, report["cashExpensesMinor"])
        assertEquals(65000L, report["cashProfitMinor"])
        assertEquals(925000L, Rules.financials(rows, from, through)["collectionsMinor"])
    }
}
