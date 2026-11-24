package ke.carhire.domain.rules

import java.time.Duration
import java.time.Instant
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
}
