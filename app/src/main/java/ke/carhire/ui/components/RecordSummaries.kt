package ke.carhire.ui.components

import java.time.Instant
import java.time.ZoneId
import ke.carhire.LocalRecord
import ke.carhire.domain.rules.HireTiming
import ke.carhire.util.BusinessDateTime
import ke.carhire.util.amount
import ke.carhire.util.text

internal data class RecordSummary(val heading: String, val lines: List<String>)

internal class RecordSummaries(records: List<LocalRecord>, private val zone: ZoneId) {
    private val byId = records.associateBy { it.id }

    fun registration(row: LocalRecord): String {
        val hire = if (row.kind == "payments") byId[row.data().text("hireId")] else row
        return byId[hire?.data()?.text("vehicleId")]
            ?.takeIf { it.kind == "vehicles" }
            ?.data()
            ?.text("registration")
            .orEmpty()
            .ifBlank { "Vehicle unavailable" }
    }

    fun hireChoice(row: LocalRecord): String =
        "${period(row)} | ${registration(row)} | ${money(row.data().amount("chargeMinor"))}"

    private fun period(row: LocalRecord): String =
        "${BusinessDateTime.display(row.data().text("plannedStart"), zone)} - ${BusinessDateTime.display(row.data().text("plannedEnd"), zone)}"

    fun summary(row: LocalRecord): RecordSummary {
        val data = row.data()
        return when (row.kind) {
            "hires" ->
                RecordSummary(
                    registration(row),
                    listOf(
                        money(data.amount("chargeMinor")),
                        period(row),
                        "Days:${HireTiming.days(Instant.parse(data.text("plannedStart")), Instant.parse(data.text("plannedEnd")))}",
                    ),
                )
            "payments" ->
                RecordSummary(
                    registration(row),
                    listOf(money(data.amount("amountMinor")), "Payment date: ${data.text("date")}"),
                )
            else ->
                RecordSummary(
                    title(row, zone),
                    buildList {
                        data.text("status").takeIf { it.isNotBlank() }?.let { add("Status: $it") }
                        if (data.containsKey("amountMinor")) add(money(data.amount("amountMinor")))
                        if (row.kind == "expenses")
                            add("Paid date: ${data.text("paidDate").ifBlank { data.text("date") }}")
                    },
                )
        }
    }
}
