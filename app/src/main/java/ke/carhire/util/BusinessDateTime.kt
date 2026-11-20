package ke.carhire.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.json.*

internal object BusinessDateTime {
    val zone: ZoneId
        get() = ZoneId.systemDefault()

    private val offsetSuffix = Regex("(Z|[+-]\\d{2}:\\d{2})$")
    private val timestamp = Regex("\\d{4}-\\d{2}-\\d{2}T.*(?:Z|[+-]\\d{2}:\\d{2})")
    private val displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ROOT)

    fun dateOrNull(value: String): LocalDate? =
        try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            null
        }

    fun date(value: String, label: String): LocalDate =
        requireNotNull(dateOrNull(value)) {
            "$label: enter a valid date as YYYY-MM-DD or choose the calendar."
        }

    fun local(value: String, label: String): LocalDateTime =
        try {
            if (offsetSuffix.containsMatchIn(value.trim()))
                OffsetDateTime.parse(value.trim()).toLocalDateTime()
            else LocalDateTime.parse(value.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(
                "$label: choose a valid date and time or enter YYYY-MM-DDTHH:mm.",
                e,
            )
        }

    fun toInstant(value: String, label: String, zone: ZoneId = this.zone): Instant {
        val local = local(value, label)
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) {
            "$label: this time does not exist in ${zone.id}; choose another time."
        }
        if (offsetSuffix.containsMatchIn(value.trim())) {
            val explicit = OffsetDateTime.parse(value.trim())
            require(explicit.offset in offsets) {
                "$label: the UTC offset does not match ${zone.id}."
            }
            return explicit.toInstant()
        }
        require(offsets.size == 1) {
            "$label: this time occurs twice in ${zone.id}; append its UTC offset, such as -04:00."
        }
        return local.atOffset(offsets.single()).toInstant()
    }

    fun toInput(value: String, zone: ZoneId = this.zone): String {
        if (value.isBlank()) return ""
        val zoned = Instant.parse(value).atZone(zone)
        return if (zone.rules.getValidOffsets(zoned.toLocalDateTime()).size > 1)
            zoned.toOffsetDateTime().toString()
        else zoned.toLocalDateTime().toString()
    }

    fun display(value: String, zone: ZoneId = this.zone): String =
        if (value.isBlank()) "Not recorded"
        else displayFormat.format(Instant.parse(value).atZone(zone))

    fun displayDate(value: String, zone: ZoneId = this.zone): String =
        Instant.parse(value).atZone(zone).toLocalDate().toString()

    fun displayPayload(value: String, zone: ZoneId = this.zone): String {
        fun convert(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject -> JsonObject(element.mapValues { convert(it.value) })
                is JsonArray -> JsonArray(element.map { convert(it) })
                is JsonPrimitive -> {
                    if (element.isString && element.content.matches(timestamp)) {
                        try {
                            JsonPrimitive(display(element.content, zone))
                        } catch (_: DateTimeParseException) {
                            element
                        }
                    } else element
                }
            }
        return convert(json.parseToJsonElement(value)).toString()
    }

    fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
