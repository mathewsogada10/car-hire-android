package ke.carhire

import java.time.ZoneId
import ke.carhire.ui.components.filterRecords
import ke.carhire.ui.editor.Forms
import org.junit.Assert.*
import org.junit.Test

class RecordFilteringTest {
    private fun hire(id: String, status: String, start: String, notes: String = "keep") =
        LocalRecord(
            id,
            "hires",
            payload = """{"status":"$status","plannedStart":"$start","notes":"$notes"}""",
        )

    private fun filtered(
        rows: List<LocalRecord>,
        status: String = "",
        from: String = "",
        to: String = "",
        query: String = "",
        zone: ZoneId = ZoneId.of("Africa/Nairobi"),
    ) = filterRecords(rows, "hires", query, status, from, to, zone)

    @Test
    fun eachEditorStatusFiltersExactlyAndAllStatusesRestoresTheRegister() {
        val statuses = Forms.fields.getValue("hires").single { it.key == "status" }.options
        val rows =
            statuses.mapIndexed { index, status ->
                hire("hire-$index", status, "2026-01-02T00:00:00Z")
            }
        for ((index, status) in statuses.withIndex()) {
            assertEquals(listOf("hire-$index"), filtered(rows, status).map { it.id })
        }
        assertEquals(rows.map { it.id }.toSet(), filtered(rows).map { it.id }.toSet())
    }

    @Test
    fun pickupOrderingUsesInstantsAndDeterministicIdsRatherThanTimestampText() {
        val rows =
            listOf(
                hire("c", "Booked", "2026-01-02T00:00:00.500Z"),
                hire("b", "Booked", "2026-01-02T00:00:00Z"),
                hire("a", "Booked", "2026-01-02T03:00:00+03:00"),
                hire("d", "Booked", "2026-01-01T22:00:00Z"),
            )
        assertEquals(listOf("d", "a", "b", "c"), filtered(rows).map { it.id })
    }

    @Test
    fun searchStatusAndLocalDateFiltersCombineAndExcludeDeletedOrUnrelatedRows() {
        val first = hire("first", "Booked", "2026-01-02T00:30:00Z")
        val rows =
            listOf(
                first,
                first.copy(id = "deleted", deletedAt = "2026-01-03T00:00:00Z"),
                first.copy(id = "payment", kind = "payments"),
                hire("other-date", "Booked", "2026-01-02T12:00:00Z"),
                hire("other-status", "Draft", "2026-01-02T00:30:00Z"),
                hire("other-query", "Booked", "2026-01-02T00:30:00Z", "discard"),
            )
        assertEquals(
            listOf(first.id),
            filtered(
                    rows,
                    "Booked",
                    "2026-01-01",
                    "2026-01-01",
                    "KEEP",
                    ZoneId.of("America/Los_Angeles"),
                )
                .map { it.id },
        )
    }
}
