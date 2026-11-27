package ke.carhire

import java.time.Instant
import java.time.ZoneId
import ke.carhire.domain.rules.HireTiming
import ke.carhire.ui.components.RecordSummaries
import ke.carhire.ui.components.filterRecords
import ke.carhire.ui.components.filteredAmountTotal
import ke.carhire.ui.editor.hireDays
import ke.carhire.ui.editor.hireFieldValue
import ke.carhire.util.json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class HireUsabilityTest {
    private val zone = ZoneId.of("Africa/Nairobi")
    private val vehicle =
        LocalRecord(
            "vehicle",
            "vehicles",
            payload = """{"registration":"KAA 123A","status":"Available"}""",
        )
    private val hire =
        LocalRecord(
            "hire",
            "hires",
            payload =
                """{"vehicleId":"vehicle","plannedStart":"2026-12-01T07:00:00Z","plannedEnd":"2026-12-02T09:00:00Z","chargeMinor":250000,"status":"Booked"}""",
        )

    @Test
    fun daysRoundPartialElapsedDaysUpAndInvalidDraftsHaveNoDuration() {
        val start = Instant.parse("2026-12-01T07:00:00Z")
        assertEquals(1L, HireTiming.days(start, start.plusSeconds(86400)))
        assertEquals(2L, HireTiming.days(start, start.plusSeconds(86400).plusNanos(1)))
        assertEquals(1L, HireTiming.days(start, start.plusSeconds(1)))
        assertNull(HireTiming.days(start, start))
        assertNull(hireDays(mapOf("plannedStart" to "invalid"), zone))
        assertEquals(
            2L,
            hireDays(
                mapOf("plannedStart" to "2026-12-01T10:00", "plannedEnd" to "2026-12-02T12:00"),
                zone,
            ),
        )
    }

    @Test
    fun hireConflictsShowLocalDatesRoundedDaysAndTheAgreedCharge() {
        assertEquals(
            "2026-12-01 10:00 - 2026-12-02 12:00 | Days:2 | KES 2,500.00",
            HireTiming.conflictSummary(hire.data(), zone),
        )
        val actual =
            json
                .parseToJsonElement(
                    """{"plannedStart":"2026-12-01T07:00:00Z","plannedEnd":"2026-12-02T09:00:00Z","actualStart":"2026-12-02T07:00:00Z","actualEnd":"2026-12-02T08:00:00Z","chargeMinor":250099}"""
                )
                .jsonObject
        assertEquals(
            "2026-12-02 10:00 - 2026-12-02 11:00 | Days:1 | KES 2,500.99",
            HireTiming.conflictSummary(actual, zone),
        )
    }

    @Test
    fun onlyMissingActualValuesDefaultToThePlan() {
        val data =
            json
                .parseToJsonElement(
                    """{"plannedStart":"2026-12-01T07:00:00Z","plannedEnd":"2026-12-02T09:00:00Z","actualStart":"2026-12-01T08:00:00Z","actualEnd":null}"""
                )
                .jsonObject
        val result = HireTiming.defaults(data)
        assertEquals(data["actualStart"], result["actualStart"])
        assertEquals(data["plannedEnd"], result["actualEnd"])
        assertEquals(result, HireTiming.defaults(result))
        val draft = mapOf("plannedStart" to "2026-12-01T10:00", "actualEnd" to "explicit")
        assertEquals(draft["plannedStart"], hireFieldValue(draft, "actualStart"))
        assertEquals("explicit", hireFieldValue(draft, "actualEnd"))
    }

    @Test
    fun cardsContainOnlyRequestedBusinessDetailsAndAnUnambiguousStatus() {
        val payment =
            LocalRecord(
                "payment",
                "payments",
                payload =
                    """{"hireId":"hire","amountMinor":120000,"date":"2026-12-01","status":"Received"}""",
            )
        val summaries = RecordSummaries(listOf(vehicle, hire, payment), zone)
        val summary = summaries.summary(hire)
        assertEquals("KAA 123A", summary.heading)
        assertEquals(
            listOf("KES 2,500.00", "2026-12-01 10:00 - 2026-12-02 12:00", "Days:2"),
            summary.lines,
        )
        assertEquals(
            listOf("KES 1,200.00", "Payment date: 2026-12-01"),
            summaries.summary(payment).lines,
        )
        assertEquals("KAA 123A", summaries.summary(payment).heading)
        assertEquals(listOf("Status: Available"), summaries.summary(vehicle).lines)
        assertFalse(summary.lines.any { it.contains("GMT") || it.contains("Pending") })
    }

    @Test
    fun expensePaidDateFallsBackOnlyWhenMissing() {
        fun expense(paid: String) =
            LocalRecord(
                "expense",
                "expenses",
                payload =
                    """{"name":"Fuel","amountMinor":20000,"date":"2026-12-01","paidDate":$paid}""",
            )
        val summaries = RecordSummaries(emptyList(), zone)
        assertTrue(summaries.summary(expense("null")).lines.contains("Paid date: 2026-12-01"))
        assertTrue(
            summaries.summary(expense("\"2026-12-03\"")).lines.contains("Paid date: 2026-12-03")
        )
    }

    @Test
    fun totalsUseOnlyTheRowsMatchingAllFilters() {
        for (kind in listOf("hires", "payments", "expenses")) {
            val rows =
                listOf(
                    LocalRecord(
                        "one",
                        kind,
                        payload =
                            """{"status":"Selected","notes":"match","date":"2026-12-01","plannedStart":"2026-12-01T07:00:00Z","amountMinor":125,"chargeMinor":250}""",
                    ),
                    LocalRecord(
                        "two",
                        kind,
                        payload =
                            """{"status":"Other","notes":"match","date":"2026-12-01","plannedStart":"2026-12-01T08:00:00Z","amountMinor":900,"chargeMinor":900}""",
                    ),
                )
            val filtered =
                filterRecords(rows, kind, "match", "Selected", "2026-12-01", "2026-12-01", zone)
            assertEquals(if (kind == "hires") 250L else 125L, filteredAmountTotal(kind, filtered))
            assertEquals(0L, filteredAmountTotal(kind, emptyList()))
        }
    }
}
