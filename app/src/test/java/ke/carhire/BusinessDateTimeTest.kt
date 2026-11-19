package ke.carhire

import java.time.Instant
import java.time.LocalDate
import ke.carhire.ui.editor.BusinessDateTime
import org.junit.Assert.*
import org.junit.Test

class BusinessDateTimeTest {
    @Test
    fun nairobiSelectionsRoundTripAcrossTheUtcDateBoundary() {
        val input = "2026-01-02T00:30"
        val instant = BusinessDateTime.toInstant(input, "Planned pickup")
        assertEquals(Instant.parse("2026-01-01T21:30:00Z"), instant)
        assertEquals(input, BusinessDateTime.toInput(instant.toString()))
        val date = LocalDate.parse("2026-01-02")
        val millis = BusinessDateTime.pickerMillis(date)
        assertEquals(Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), millis)
        assertEquals(date, BusinessDateTime.pickerDate(millis))
    }

    @Test
    fun existingSecondsSurviveOpeningAndSaving() {
        val stored = "2026-11-19T07:15:45Z"
        val input = BusinessDateTime.toInput(stored)
        assertEquals("2026-11-19T10:15:45", input)
        assertEquals(stored, BusinessDateTime.toInstant(input, "Actual pickup").toString())
    }

    @Test
    fun invalidDatesAndCityNamesIdentifyTheAffectedField() {
        for (input in listOf("Nairobi", "2026-02-30T10:00", "2026-11-19", "")) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    BusinessDateTime.toInstant(input, "Planned pickup")
                }
            assertTrue(error.message.orEmpty().startsWith("Planned pickup:"))
            assertTrue(error.message.orEmpty().contains("choose a valid date and time"))
            assertFalse(error.message.orEmpty().contains("index 0"))
        }
    }
}
