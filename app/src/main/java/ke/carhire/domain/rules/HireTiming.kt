package ke.carhire.domain.rules

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import ke.carhire.util.BusinessDateTime
import ke.carhire.util.amount
import ke.carhire.util.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object HireTiming {
    val plannedFields = mapOf("actualStart" to "plannedStart", "actualEnd" to "plannedEnd")

    fun defaults(data: JsonObject): JsonObject =
        JsonObject(
            data.toMutableMap().apply {
                plannedFields.forEach { (actual, planned) ->
                    if (data.text(actual).isBlank() && data.text(planned).isNotBlank())
                        put(actual, JsonPrimitive(data.text(planned)))
                }
            }
        )

    fun days(start: Instant, end: Instant): Long? {
        if (end <= start) return null
        val duration = Duration.between(start, end)
        val wholeDays = duration.toDays()
        return wholeDays + if (duration.minusDays(wholeDays).isZero) 0 else 1
    }

    fun conflictSummary(data: JsonObject, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = data.text("actualStart").ifBlank { data.text("plannedStart") }
        val end = data.text("actualEnd").ifBlank { data.text("plannedEnd") }
        val days = days(Instant.parse(start), Instant.parse(end)) ?: 0
        val charge = java.math.BigDecimal.valueOf(data.amount("chargeMinor"), 2)
        return "${BusinessDateTime.display(start, zone)} - ${BusinessDateTime.display(end, zone)} | Days:$days | ${String.format(Locale.ROOT, "KES %,.2f", charge)}"
    }
}
