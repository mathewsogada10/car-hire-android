package ke.carhire

import java.time.ZoneId
import ke.carhire.ui.editor.ReferenceChoices
import org.junit.Assert.*
import org.junit.Test

class ReferenceChoicesTest {
    private val zone = ZoneId.of("Africa/Nairobi")
    private val vehicle =
        LocalRecord("vehicle", "vehicles", payload = """{"registration":"KAA 123A"}""")
    private val hire =
        LocalRecord(
            "hire",
            "hires",
            payload = """{"vehicleId":"vehicle","plannedStart":"2026-12-01T07:00:00Z"}""",
        )

    @Test
    fun hireChoicesShowAndSearchRegistrationWithLocalTimeAndRetainTheSelectedLabel() {
        val references = ReferenceChoices(listOf(vehicle, hire), zone)
        val option = references.options("hires", "kaa").single()
        assertEquals(hire.id, option.first)
        assertTrue(option.second.startsWith("KAA 123A | 2026-12-01 10:00"))
        assertTrue(references.options("hires", "another car").isEmpty())
        assertEquals(option.second, references.label("hires", hire.id))
        assertEquals("KAA 123A", references.options("vehicles", "").single().second)
    }

    @Test
    fun missingVehiclesAreExplicitAndDeletedHiresCannotBeNewSelections() {
        val missing = ReferenceChoices(listOf(hire), zone)
        assertTrue(missing.options("hires", "").single().second.startsWith("Vehicle unavailable |"))
        val historical =
            ReferenceChoices(
                listOf(
                    vehicle.copy(deletedAt = "2026-12-03T00:00:00Z"),
                    hire.copy(deletedAt = "2026-12-03T00:00:00Z"),
                ),
                zone,
            )
        assertTrue(historical.options("hires", "").isEmpty())
        assertTrue(historical.label("hires", hire.id).orEmpty().startsWith("KAA 123A |"))
        assertNull(historical.label("customers", hire.id))
    }
}
