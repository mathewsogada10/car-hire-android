package ke.carhire

import java.time.LocalDate
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.domain.rules.Rules
import ke.carhire.security.Crypto
import ke.carhire.util.json
import ke.carhire.util.uuid
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test
    fun fiftyThousandRecordsReconcileWithoutPerHireFullScans() {
        val rows = buildList {
            repeat(10000) { index ->
                val id = "hire-$index"
                add(
                    LocalRecord(
                        id,
                        "hires",
                        payload =
                            """{"vehicleId":"fleet","status":"Completed","plannedStart":"2026-01-01T10:00:00Z","actualEnd":"2026-01-02T10:00:00Z","chargeMinor":10000}""",
                    )
                )
                add(
                    LocalRecord(
                        "payment-$index",
                        "payments",
                        payload =
                            """{"hireId":"$id","kind":"Payment","status":"Received","date":"2026-01-02","amountMinor":2000}""",
                    )
                )
                add(
                    LocalRecord(
                        "damage-$index",
                        "damages",
                        payload =
                            """{"hireId":"$id","responsibility":"Customer","status":"Charged","amountMinor":500}""",
                    )
                )
                add(
                    LocalRecord(
                        "expense-$index",
                        "expenses",
                        payload =
                            """{"vehicleId":"fleet","status":"Paid","date":"2026-01-02","paidDate":"2026-01-02","amountMinor":1000}""",
                    )
                )
                add(LocalRecord("customer-$index", "customers", payload = "{}"))
            }
        }
        val report =
            Rules.financials(
                rows,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                "fleet",
            )
        assertEquals(100000000L, report["recognizedMinor"])
        assertEquals(85000000L, report["outstandingMinor"])
        assertEquals(90000000L, report["expectedProfitMinor"])
        assertEquals(10000000L, report["cashProfitMinor"])
    }

    @Test
    fun dateMileageAndSnoozeRemindersAreEvaluatedTogether() {
        val vehicle = vehicle().copy(payload = """{"odometerKm":50000}""")
        fun reminder(d: String) = LocalRecord(uuid(), "reminders", payload = d)
        val date = reminder("""{"status":"Open","dueDate":"2026-01-03"}""")
        val mileage = reminder("""{"status":"Open","vehicleId":"${vehicle.id}","dueKm":50000}""")
        val snoozed =
            reminder("""{"status":"Snoozed","dueDate":"2026-01-01","snoozedUntil":"2026-02-01"}""")
        val done = reminder("""{"status":"Completed","dueDate":"2026-01-01"}""")
        assertEquals(
            setOf(date.id, mileage.id),
            Rules.dueReminders(
                    listOf(vehicle, date, mileage, snoozed, done),
                    LocalDate.parse("2026-01-01"),
                    3,
                )
                .map { it.id }
                .toSet(),
        )
    }

    private fun data(value: String) = json.parseToJsonElement(value).jsonObject

    private fun vehicle() =
        LocalRecord(
            uuid(),
            "vehicles",
            payload =
                """{"registration":"KAA001A","make":"Toyota","model":"Axio","status":"Available"}""",
        )

    private fun customer() =
        LocalRecord(uuid(), "customers", payload = """{"name":"Test","phone":"0700000000"}""")

    private fun hire(
        v: LocalRecord,
        c: LocalRecord,
        status: String = "Completed",
        start: String = "2026-01-01T10:00:00Z",
        end: String = "2026-01-03T10:00:00Z",
    ): LocalRecord =
        LocalRecord(
            uuid(),
            "hires",
            payload =
                buildJsonObject {
                        put("vehicleId", v.id)
                        put("customerId", c.id)
                        put("chargeMinor", 4000000)
                        put("status", status)
                        put("plannedStart", start)
                        put("plannedEnd", end)
                        if (status in listOf("Active", "Completed")) put("actualStart", start)
                        if (status == "Completed") put("actualEnd", end)
                    }
                    .toString(),
        )

    @Test
    fun recognitionCollectionsAndProfitRemainSeparate() {
        val v = vehicle()
        val c = customer()
        val h = hire(v, c)
        val p =
            LocalRecord(
                uuid(),
                "payments",
                payload =
                    """{"hireId":"${h.id}","amountMinor":1500000,"date":"2026-01-03","kind":"Payment","status":"Received","method":"Cash"}""",
            )
        val e =
            LocalRecord(
                uuid(),
                "expenses",
                payload =
                    """{"vehicleId":"${v.id}","amountMinor":800000,"date":"2026-01-02","paidDate":"2026-01-03","status":"Paid"}""",
            )
        val rows = listOf(v, c, h, p, e)
        val result =
            Rules.financials(rows, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        assertEquals(2500000L, result["outstandingMinor"])
        assertEquals(3200000L, result["expectedProfitMinor"])
        assertEquals(700000L, result["cashProfitMinor"])
        assertEquals(
            0L,
            Rules.financials(rows, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"))[
                    "recognizedMinor"],
        )
    }

    @Test
    fun overlappingBookingRejectedAndAdjacentAllowed() {
        val v = vehicle()
        val c = customer()
        val first = hire(v, c, "Booked")
        val adjacent = hire(v, c, "Booked", "2026-01-03T10:00:00Z", "2026-01-04T10:00:00Z")
        val rows = listOf(v, c, first)
        Rules.validate("hires", adjacent.id, adjacent.data(), null, rows)
        val overlap = hire(v, c, "Booked", "2026-01-02T10:00:00Z", "2026-01-04T10:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            Rules.validate("hires", overlap.id, overlap.data(), null, rows)
        }
    }

    @Test
    fun enabledRemindersRequireTimeAndIntervals() {
        val d =
            data(
                """{"currency":"KES","timeZone":"Africa/Nairobi","bufferMinutes":0,"remindersEnabled":true}"""
            )
        assertThrows(Exception::class.java) {
            Rules.validate("settings", SETTINGS_ID, d, null, emptyList())
        }
    }

    @Test
    fun duplicateNormalizedRegistrationRejected() {
        val v = vehicle()
        val d =
            JsonObject(
                v.data().toMutableMap().apply { put("registration", JsonPrimitive("kaa 001a")) }
            )
        assertThrows(IllegalArgumentException::class.java) {
            Rules.validate("vehicles", uuid(), d, null, listOf(v))
        }
    }

    @Test
    fun incompleteCustomerCannotBeBooked() {
        val v = vehicle()
        val c = customer().copy(payload = """{"name":"Draft customer"}""")
        val h = hire(v, c, "Booked")
        assertThrows(IllegalArgumentException::class.java) {
            Rules.validate("hires", h.id, h.data(), null, listOf(v, c))
        }
    }

    @Test
    fun encryptedBackupRejectsWrongPassphraseAndTampering() {
        val bytes = "private vehicle and customer records".toByteArray()
        val encrypted = Crypto.encrypt(bytes, "long-recovery-passphrase".toCharArray())
        assertArrayEquals(
            bytes,
            Crypto.decrypt(encrypted, "long-recovery-passphrase".toCharArray()),
        )
        assertFalse(String(encrypted).contains("private vehicle"))
        assertThrows(Exception::class.java) {
            Crypto.decrypt(encrypted, "wrong-recovery-passphrase".toCharArray())
        }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            Crypto.decrypt(encrypted, "long-recovery-passphrase".toCharArray())
        }
    }

    @Test
    fun paymentsRequirePositiveIntegerMinorUnits() {
        val v = vehicle()
        val c = customer()
        val h = hire(v, c)
        val d =
            data(
                """{"hireId":"${h.id}","amountMinor":0,"date":"2026-01-03","kind":"Payment","status":"Received","method":"Cash"}"""
            )
        assertThrows(IllegalArgumentException::class.java) {
            Rules.validate("payments", uuid(), d, null, listOf(v, c, h))
        }
    }
}
