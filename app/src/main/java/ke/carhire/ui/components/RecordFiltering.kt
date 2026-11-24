package ke.carhire.ui.components

import java.time.Instant
import java.time.ZoneId
import ke.carhire.LocalRecord
import ke.carhire.util.BusinessDateTime
import ke.carhire.util.amount
import ke.carhire.util.text

internal fun filteredAmountTotal(kind: String, records: List<LocalRecord>): Long =
    records.fold(0L) { total, row ->
        Math.addExact(
            total,
            row.data().amount(if (kind == "hires") "chargeMinor" else "amountMinor"),
        )
    }

internal fun filterRecords(
    all: List<LocalRecord>,
    kind: String,
    query: String,
    status: String,
    from: String,
    to: String,
    zone: ZoneId,
): List<LocalRecord> {
    val summaries =
        if (query.isNotBlank() && kind in listOf("hires", "payments", "expenses"))
            RecordSummaries(all, zone)
        else null
    val filtered =
        all.filter { row ->
            if (row.kind != kind || row.deletedAt != null) false
            else {
                val data = row.data()
                val dateMatches =
                    if (from.isBlank() && to.isBlank()) true
                    else {
                        val date =
                            data.text("date").ifEmpty {
                                data
                                    .text("plannedStart")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { BusinessDateTime.displayDate(it, zone) }
                                    .orEmpty()
                            }
                        (from.isBlank() || date >= from) && (to.isBlank() || date <= to)
                    }
                (query.isBlank() ||
                    row.payload.contains(query, true) ||
                    summaries?.registration(row)?.contains(query.trim(), true) == true) &&
                    (status.isBlank() || data.text("status") == status) &&
                    dateMatches
            }
        }
    return if (kind == "hires")
        filtered
            .map { it to Instant.parse(it.data().text("plannedStart")) }
            .sortedWith(compareBy<Pair<LocalRecord, Instant>> { it.second }.thenBy { it.first.id })
            .map { it.first }
    else filtered
}
