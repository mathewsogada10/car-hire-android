package ke.carhire

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import ke.carhire.util.BusinessDateTime
import org.junit.Assert.*
import org.junit.Test

class BusinessDateTimeTest {
    private val nairobi = ZoneId.of("Africa/Nairobi")

    @Test
    fun nairobiSelectionsRoundTripAcrossTheUtcDateBoundary() {
        val input = "2026-01-02T00:30"
        val instant = BusinessDateTime.toInstant(input, "Planned pickup", nairobi)
        assertEquals(Instant.parse("2026-01-01T21:30:00Z"), instant)
        assertEquals(input, BusinessDateTime.toInput(instant.toString(), nairobi))
        val date = LocalDate.parse("2026-01-02")
        val millis = BusinessDateTime.pickerMillis(date)
        assertEquals(Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), millis)
        assertEquals(date, BusinessDateTime.pickerDate(millis))
    }

    @Test
    fun existingSecondsSurviveOpeningAndSaving() {
        val stored = "2026-11-19T07:15:45Z"
        val input = BusinessDateTime.toInput(stored, nairobi)
        assertEquals("2026-11-19T10:15:45", input)
        assertEquals(stored, BusinessDateTime.toInstant(input, "Actual pickup", nairobi).toString())
    }

    @Test
    fun invalidDatesAndCityNamesIdentifyTheAffectedField() {
        for (input in listOf("Nairobi", "2026-02-30T10:00", "2026-11-19", "")) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    BusinessDateTime.toInstant(input, "Planned pickup", nairobi)
                }
            assertTrue(error.message.orEmpty().startsWith("Planned pickup:"))
            assertTrue(error.message.orEmpty().contains("choose a valid date and time"))
            assertFalse(error.message.orEmpty().contains("index 0"))
        }
    }

    @Test
    fun deviceTimezoneChangesConvertBothTheDateAndTime() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val stored = "2026-01-02T00:30:00Z"
            assertEquals("2026-01-01T16:30", BusinessDateTime.toInput(stored))
            assertEquals("2026-01-01", BusinessDateTime.displayDate(stored))
            assertTrue(BusinessDateTime.display(stored).startsWith("2026-01-01 16:30"))
            assertEquals(
                stored,
                BusinessDateTime.toInstant("2026-01-01T16:30", "Pickup").toString(),
            )
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("2026-01-02T09:30", BusinessDateTime.toInput(stored))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun daylightSavingGapsAreRejectedAndRepeatedTimesPreserveTheirOffsets() {
        val zone = ZoneId.of("America/New_York")
        assertThrows(IllegalArgumentException::class.java) {
            BusinessDateTime.toInstant("2026-03-08T02:30", "Pickup", zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessDateTime.toInstant("2026-11-01T01:30", "Pickup", zone)
        }
        for (stored in listOf("2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z")) {
            val input = BusinessDateTime.toInput(stored, zone)
            assertEquals(stored, BusinessDateTime.toInstant(input, "Pickup", zone).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusinessDateTime.toInstant("2026-01-01T10:00+03:00", "Pickup", zone)
        }
    }

    @Test
    fun dateOnlyEntryIsStrictAndHistoryConversionDoesNotShiftBusinessDates() {
        assertEquals(LocalDate.of(2024, 2, 29), BusinessDateTime.date("2024-02-29", "Paid date"))
        for (value in listOf("2026-02-29", "2026-1-2", "Nairobi", "")) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    BusinessDateTime.date(value, "Paid date")
                }
            assertTrue(error.message.orEmpty().startsWith("Paid date:"))
        }
        val history =
            BusinessDateTime.displayPayload(
                """{"when":"2026-01-02T00:30:00Z","date":"2026-01-02","nested":[{"when":"2026-01-02T00:30:00Z"}]}""",
                ZoneId.of("America/Los_Angeles"),
            )
        assertTrue(history.contains("2026-01-01 16:30"))
        assertTrue(history.contains("\"date\":\"2026-01-02\""))
        assertFalse(history.contains("2026-01-02T00:30:00Z"))
    }
}
