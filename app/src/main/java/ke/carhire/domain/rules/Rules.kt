package ke.carhire.domain.rules

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import ke.carhire.LocalRecord
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.util.amount
import ke.carhire.util.flag
import ke.carhire.util.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

object Rules {
    fun dueReminders(rows: List<LocalRecord>, today: LocalDate, lead: Long): List<LocalRecord> {
        val mileage = mutableMapOf<String, Long>()
        for (r in rows) {
            val d = r.data()
            if (r.deletedAt != null) continue
            if (r.kind == "vehicles")
                mileage[r.id] = maxOf(mileage[r.id] ?: 0, d.amount("odometerKm"))
            if (r.kind == "readings" && d.text("type") == "Odometer")
                mileage[d.text("vehicleId")] =
                    maxOf(mileage[d.text("vehicleId")] ?: 0, d.amount("value"))
        }
        return rows.filter { r ->
            if (r.kind != "reminders" || r.deletedAt != null) false
            else {
                val d = r.data()
                val open =
                    d.text("status") == "Open" ||
                        d.text("status") == "Snoozed" &&
                            LocalDate.parse(d.text("snoozedUntil")) <= today
                val dateDue =
                    d.text("dueDate").isNotEmpty() &&
                        LocalDate.parse(d.text("dueDate")) <= today.plusDays(lead)
                val kmDue =
                    d.containsKey("dueKm") &&
                        (mileage[d.text("vehicleId")] ?: -1) >= d.amount("dueKm")
                open && (dateDue || kmDue)
            }
        }
    }

    val kinds =
        listOf(
            "vehicles",
            "customers",
            "hires",
            "payments",
            "expenses",
            "categories",
            "providers",
            "maintenance",
            "reminders",
            "readings",
            "conditions",
            "damages",
            "settings",
        )

    private fun required(d: JsonObject, key: String): String =
        d.text(key).also {
            require(it.isNotBlank() && it.length <= 2000) {
                "$key is required (maximum 2000 characters)"
            }
        }

    private fun amount(d: JsonObject, key: String): Long =
        ((d[key] as? JsonPrimitive)?.longOrNull ?: error("$key must be an integer")).also {
            require(it in 0..1_000_000_000_000L) { "$key is outside the allowed range" }
        }

    private fun choice(d: JsonObject, key: String, vararg values: String) {
        require(required(d, key) in values) { "$key must be one of ${values.joinToString()}" }
    }

    private fun date(d: JsonObject, key: String) = LocalDate.parse(required(d, key))

    private fun instant(d: JsonObject, key: String) = Instant.parse(required(d, key))

    fun validate(
        kind: String,
        id: String,
        d: JsonObject,
        old: LocalRecord?,
        rows: List<LocalRecord>,
    ): JsonObject {
        require(kind in kinds)
        UUID.fromString(id)
        require(d.toString().length <= 32000) { "Record too large" }
        fun ref(key: String, type: String): LocalRecord {
            val target = required(d, key)
            UUID.fromString(target)
            return rows.firstOrNull { it.id == target && it.kind == type && it.deletedAt == null }
                ?: error("Referenced $type is unavailable")
        }
        when (kind) {
            "vehicles" -> {
                required(d, "registration")
                required(d, "make")
                required(d, "model")
                choice(d, "status", "Available", "Hired", "Under Service", "Inactive", "Sold")
                val reg = d.text("registration").replace("\\s+".toRegex(), "").uppercase()
                require(
                    rows.none {
                        it.kind == kind &&
                            it.id != id &&
                            it.deletedAt == null &&
                            it.data().text("registration") == reg
                    }
                ) {
                    "Registration already exists"
                }
                if (d.containsKey("odometerKm")) {
                    val km = amount(d, "odometerKm")
                    if (old != null && km < old.data().amount("odometerKm"))
                        required(d, "correctionReason")
                }
                if (d.text("status") in listOf("Inactive", "Sold"))
                    require(
                        rows.none {
                            it.kind == "hires" &&
                                it.deletedAt == null &&
                                it.data().text("vehicleId") == id &&
                                it.data().text("status") in listOf("Booked", "Active")
                        }
                    ) {
                        "Vehicle has booked or active hires"
                    }
                return JsonObject(
                    d.toMutableMap().apply { put("registration", JsonPrimitive(reg)) }
                )
            }
            "customers",
            "categories",
            "providers" -> required(d, "name")
            "hires" -> {
                val vehicle = ref("vehicleId", "vehicles")
                val customer = ref("customerId", "customers")
                val start = instant(d, "plannedStart")
                val end = instant(d, "plannedEnd")
                require(end > start) { "Return must follow pickup" }
                val charge = amount(d, "chargeMinor")
                require(charge > 0 || d.flag("zeroChargeConfirmed")) {
                    "Confirm a zero-charge hire"
                }
                choice(d, "status", "Draft", "Booked", "Active", "Completed", "Cancelled")
                val status = d.text("status")
                val transitions =
                    mapOf(
                        "Draft" to listOf("Draft", "Booked", "Cancelled"),
                        "Booked" to listOf("Booked", "Active", "Cancelled"),
                        "Active" to listOf("Active", "Completed"),
                        "Completed" to listOf("Completed"),
                        "Cancelled" to listOf("Cancelled"),
                    )
                if (old != null)
                    require(status in transitions.getValue(old.data().text("status"))) {
                        "Invalid hire status transition"
                    }
                if (status in listOf("Booked", "Active")) {
                    require(
                        vehicle.data().text("status") !in
                            listOf("Inactive", "Sold", "Under Service")
                    ) {
                        "Vehicle unavailable"
                    }
                    require(
                        customer.data().text("phone").isNotEmpty() ||
                            customer.data().text("email").isNotEmpty()
                    ) {
                        "Customer needs a contact before booking"
                    }
                }
                if (status in listOf("Active", "Completed")) instant(d, "actualStart")
                if (status == "Completed")
                    require(instant(d, "actualEnd") >= instant(d, "actualStart")) {
                        "Actual return precedes pickup"
                    }
                if (status !in listOf("Draft", "Cancelled"))
                    overlap(
                        d.text("vehicleId"),
                        if (status == "Completed" || status == "Active") instant(d, "actualStart")
                        else start,
                        if (status == "Completed") instant(d, "actualEnd") else end,
                        id,
                        rows,
                    )
            }
            "payments" -> {
                ref("hireId", "hires")
                val value = amount(d, "amountMinor")
                require(value > 0) { "Payment must be positive" }
                date(d, "date")
                required(d, "method")
                choice(d, "status", "Pending", "Received", "Failed")
                choice(
                    d,
                    "kind",
                    "Payment",
                    "Deposit",
                    "Refund",
                    "DepositApplication",
                    "DepositRefund",
                )
                require(old == null || old.data().text("status") != "Received") {
                    "Received entries are immutable; create a compensating entry"
                }
                val payments =
                    rows.filter {
                        it.kind == kind &&
                            it.id != id &&
                            it.deletedAt == null &&
                            it.data().text("hireId") == d.text("hireId")
                    }
                if (d.text("kind") in listOf("Refund", "DepositRefund")) {
                    val original = ref("originalPaymentId", "payments")
                    val od = original.data()
                    require(
                        od.text("hireId") == d.text("hireId") &&
                            od.text("kind") ==
                                if (d.text("kind") == "Refund") "Payment" else "Deposit"
                    ) {
                        "Refund does not match original"
                    }
                    require(od.text("status") == "Received")
                    val used =
                        payments
                            .filter {
                                it.data().text("originalPaymentId") == original.id &&
                                    it.data().text("status") in listOf("Pending", "Received")
                            }
                            .sumOf { it.data().amount("amountMinor") }
                    require(used + value <= od.amount("amountMinor")) {
                        "Refund exceeds original amount"
                    }
                }
                if (d.text("kind") in listOf("DepositApplication", "DepositRefund")) {
                    val balance =
                        payments.sumOf {
                            val p = it.data()
                            if (p.text("status") !in listOf("Pending", "Received")) 0
                            else
                                when (p.text("kind")) {
                                    "Deposit" ->
                                        if (p.text("status") == "Received") p.amount("amountMinor")
                                        else 0
                                    "DepositApplication",
                                    "DepositRefund" -> -p.amount("amountMinor")
                                    else -> 0
                                }
                        }
                    require(value <= balance) { "Insufficient unapplied deposit" }
                }
            }
            "expenses" -> {
                ref("vehicleId", "vehicles")
                ref("categoryId", "categories")
                required(d, "name")
                amount(d, "amountMinor")
                date(d, "date")
                choice(d, "status", "Planned", "Due", "Paid", "Cancelled")
                if (d.text("status") == "Paid") date(d, "paidDate")
                if (d.text("providerId").isNotEmpty()) ref("providerId", "providers")
            }
            "maintenance" -> {
                ref("vehicleId", "vehicles")
                required(d, "name")
                date(d, "date")
                val start = instant(d, "start")
                val end = instant(d, "end")
                require(end > start)
                overlap(d.text("vehicleId"), start, end, id, rows)
                if (d.text("expenseId").isNotEmpty())
                    require(
                        ref("expenseId", "expenses").data().text("vehicleId") == d.text("vehicleId")
                    )
            }
            "reminders" -> {
                required(d, "name")
                choice(d, "status", "Open", "Snoozed", "Completed", "Dismissed")
                require(d.text("dueDate").isNotEmpty() || d.containsKey("dueKm")) {
                    "Due date or mileage is required"
                }
                if (d.text("dueDate").isNotEmpty()) date(d, "dueDate")
                if (d.containsKey("dueKm")) {
                    amount(d, "dueKm")
                    ref("vehicleId", "vehicles")
                }
                if (d.text("status") == "Snoozed") date(d, "snoozedUntil")
            }
            "readings" -> {
                ref("vehicleId", "vehicles")
                choice(d, "type", "Odometer", "Fuel")
                val value = amount(d, "value")
                date(d, "date")
                if (d.text("type") == "Odometer") {
                    val max =
                        rows
                            .filter {
                                it.id != id &&
                                    it.kind == kind &&
                                    it.deletedAt == null &&
                                    it.data().text("vehicleId") == d.text("vehicleId") &&
                                    it.data().text("type") == "Odometer"
                            }
                            .maxOfOrNull { it.data().amount("value") } ?: 0
                    if (value < max) required(d, "correctionReason")
                }
            }
            "conditions" -> {
                ref("hireId", "hires")
                choice(d, "phase", "PRE", "POST")
                amount(d, "odometerKm")
                date(d, "date")
                required(d, "notes")
            }
            "damages" -> {
                val hire = ref("hireId", "hires")
                ref("vehicleId", "vehicles")
                require(hire.data().text("vehicleId") == d.text("vehicleId"))
                required(d, "name")
                amount(d, "amountMinor")
                date(d, "date")
                choice(d, "responsibility", "Customer", "Business", "Other")
                choice(d, "status", "Open", "Charged", "Waived", "Settled")
            }
            "settings" -> {
                require(id == SETTINGS_ID)
                require(d.text("currency") == "KES" && d.text("timeZone") == "Africa/Nairobi")
                require(amount(d, "bufferMinutes") <= 1440)
                require((d["remindersEnabled"] as? JsonPrimitive)?.booleanOrNull != null)
                if (d.flag("remindersEnabled")) {
                    require(amount(d, "reminderLeadDays") <= 365)
                    LocalTime.parse(required(d, "reminderTime"))
                    require(amount(d, "backupReminderDays") > 0)
                }
            }
        }
        return d
    }

    private fun overlap(
        vehicle: String,
        start: Instant,
        end: Instant,
        exclude: String,
        rows: List<LocalRecord>,
    ) {
        val buffer = (rows.find { it.id == SETTINGS_ID }?.data()?.amount("bufferMinutes") ?: 0) * 60
        for (r in
            rows.filter {
                it.id != exclude && it.deletedAt == null && it.data().text("vehicleId") == vehicle
            }) {
            val d = r.data()
            val span =
                when (r.kind) {
                    "hires" -> {
                        if (d.text("status") in listOf("Draft", "Cancelled")) continue
                        val a =
                            Instant.parse(d.text("actualStart").ifEmpty { d.text("plannedStart") })
                        var b = Instant.parse(d.text("actualEnd").ifEmpty { d.text("plannedEnd") })
                        if (d.text("status") == "Active" && b < Instant.now())
                            b = Instant.MAX.minusSeconds(buffer)
                        a to b
                    }
                    "maintenance" -> instant(d, "start") to instant(d, "end")
                    else -> continue
                }
            require(
                !(start < span.second.plusSeconds(buffer) && span.first < end.plusSeconds(buffer))
            ) {
                if (r.kind == "hires")
                    "Vehicle has an overlapping hire: ${HireTiming.conflictSummary(d)}"
                else "Vehicle has an overlapping ${r.kind}: ${r.id}"
            }
        }
    }

    fun deletion(row: LocalRecord, rows: List<LocalRecord>) {
        require(row.kind != "settings")
        require(row.kind != "payments" || row.data().text("status") != "Received") {
            "Use a compensating financial entry"
        }
        require(row.kind != "hires" || row.data().text("status") in listOf("Draft", "Cancelled"))
        val field =
            mapOf(
                "vehicles" to "vehicleId",
                "customers" to "customerId",
                "hires" to "hireId",
                "categories" to "categoryId",
                "providers" to "providerId",
                "expenses" to "expenseId",
                "payments" to "originalPaymentId",
            )[row.kind]
        require(
            field == null || rows.none { it.deletedAt == null && it.data().text(field) == row.id }
        ) {
            "Referenced record cannot be deleted; deactivate instead"
        }
    }

    fun financials(
        rows: List<LocalRecord>,
        from: LocalDate,
        to: LocalDate,
        vehicleId: String = "",
    ): Map<String, Long> {
        require(to >= from)
        val active = rows.filter { it.deletedAt == null }
        val hires =
            active.filter {
                it.kind == "hires" &&
                    (vehicleId.isEmpty() || it.data().text("vehicleId") == vehicleId)
            }
        val ids = hires.map { it.id }.toSet()
        var recognized = 0L
        var cash = 0L
        var accrued = 0L
        var paid = 0L
        var outstanding = 0L
        var deposits = 0L
        fun inside(date: LocalDate) = date >= from && date <= to
        val payments =
            active.filter {
                it.kind == "payments" &&
                    it.data().text("status") == "Received" &&
                    it.data().text("hireId") in ids
            }
        val paymentsByHire = payments.groupBy { it.data().text("hireId") }
        val damagesByHire =
            active
                .filter {
                    it.kind == "damages" &&
                        it.data().text("responsibility") == "Customer" &&
                        it.data().text("status") in listOf("Charged", "Settled")
                }
                .groupBy { it.data().text("hireId") }
        for (h in hires) {
            val d = h.data()
            if (d.text("status") in listOf("Draft", "Cancelled")) continue
            if (
                d.text("status") == "Completed" &&
                    inside(
                        Instant.parse(d.text("actualEnd"))
                            .atZone(ZoneId.of("Africa/Nairobi"))
                            .toLocalDate()
                    )
            )
                recognized += d.amount("chargeMinor")
            if (
                inside(
                    Instant.parse(d.text("plannedStart"))
                        .atZone(ZoneId.of("Africa/Nairobi"))
                        .toLocalDate()
                )
            ) {
                val allocated =
                    paymentsByHire[h.id].orEmpty().sumOf {
                        val p = it.data()
                        when (p.text("kind")) {
                            "Payment",
                            "DepositApplication" -> p.amount("amountMinor")
                            "Refund" -> -p.amount("amountMinor")
                            else -> 0L
                        }
                    }
                val damage = damagesByHire[h.id].orEmpty().sumOf { it.data().amount("amountMinor") }
                outstanding += d.amount("chargeMinor") + damage - allocated
            }
        }
        for (p in payments) {
            val d = p.data()
            if (inside(LocalDate.parse(d.text("date"))))
                when (d.text("kind")) {
                    "Payment" -> cash += d.amount("amountMinor")
                    "Refund" -> cash -= d.amount("amountMinor")
                    "Deposit" -> deposits += d.amount("amountMinor")
                    "DepositRefund" -> deposits -= d.amount("amountMinor")
                }
        }
        for (e in
            active.filter {
                it.kind == "expenses" &&
                    (vehicleId.isEmpty() || it.data().text("vehicleId") == vehicleId) &&
                    it.data().text("status") != "Cancelled"
            }) {
            val d = e.data()
            if (inside(LocalDate.parse(d.text("date")))) accrued += d.amount("amountMinor")
            if (d.text("status") == "Paid" && inside(LocalDate.parse(d.text("paidDate"))))
                paid += d.amount("amountMinor")
        }
        return linkedMapOf(
            "recognizedMinor" to recognized,
            "collectionsMinor" to cash,
            "outstandingMinor" to outstanding,
            "accruedExpensesMinor" to accrued,
            "cashExpensesMinor" to paid,
            "expectedProfitMinor" to recognized - accrued,
            "cashProfitMinor" to cash - paid,
            "depositCashMinor" to deposits,
        )
    }
}
