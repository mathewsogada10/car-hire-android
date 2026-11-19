package ke.carhire.ui.editor

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal object BusinessDateTime {
    val zone: ZoneId = ZoneId.of("Africa/Nairobi")

    fun local(value: String, label: String): LocalDateTime =
        try {
            LocalDateTime.parse(value.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException(
                "$label: choose a valid date and time in Nairobi time.",
                e,
            )
        }

    fun toInstant(value: String, label: String): Instant =
        local(value, label).atZone(zone).toInstant()

    fun toInput(value: String): String =
        if (value.isBlank()) "" else Instant.parse(value).atZone(zone).toLocalDateTime().toString()

    fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
