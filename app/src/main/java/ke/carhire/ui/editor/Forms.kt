package ke.carhire.ui.editor

object Forms {
    private fun f(key: String, label: String, type: String = "text", optional: Boolean = false) =
        Field(key, label, type, optional = optional)

    private fun ref(key: String, label: String, kind: String, optional: Boolean = false) =
        Field(key, label, reference = kind, optional = optional)

    private fun options(key: String, label: String, vararg values: String) =
        Field(key, label, options = values.toList())

    private val vehicle = ref("vehicleId", "Vehicle", "vehicles")
    private val name = f("name", "Name")
    private val date = f("date", "Business date (YYYY-MM-DD)", "date")
    private val amount = f("amountMinor", "Amount (KES)", "money")
    private val notes = f("notes", "Notes", optional = true)
    val fields =
        linkedMapOf(
            "vehicles" to
                listOf(
                    f("registration", "Registration"),
                    f("make", "Make"),
                    f("model", "Model"),
                    f("year", "Year", "number", true),
                    options(
                        "status",
                        "Status",
                        "Available",
                        "Hired",
                        "Under Service",
                        "Inactive",
                        "Sold",
                    ),
                    f("odometerKm", "Odometer km", "number", true),
                    f("correctionReason", "Odometer correction reason", optional = true),
                    notes,
                ),
            "customers" to
                listOf(
                    name,
                    f("phone", "Phone", optional = true),
                    f("email", "Email", optional = true),
                    f("licenceRef", "Licence reference (optional)", optional = true),
                    f("emergencyContact", "Emergency contact", optional = true),
                    notes,
                ),
            "hires" to
                listOf(
                    vehicle,
                    ref("customerId", "Customer", "customers"),
                    f("plannedStart", "Planned pickup (Nairobi)", "instant"),
                    f("plannedEnd", "Planned return (Nairobi)", "instant"),
                    options(
                        "status",
                        "Status",
                        "Draft",
                        "Booked",
                        "Active",
                        "Completed",
                        "Cancelled",
                    ),
                    f("chargeMinor", "Agreed charge (KES)", "money"),
                    f("zeroChargeConfirmed", "Confirm a zero-charge hire", "boolean"),
                    f("actualStart", "Actual pickup (Nairobi)", "instant", true),
                    f("actualEnd", "Actual return (Nairobi)", "instant", true),
                    f("itinerary", "Itinerary", optional = true),
                    notes,
                ),
            "payments" to
                listOf(
                    ref("hireId", "Hire", "hires"),
                    options(
                        "kind",
                        "Entry kind",
                        "Payment",
                        "Deposit",
                        "Refund",
                        "DepositApplication",
                        "DepositRefund",
                    ),
                    amount,
                    date,
                    options("status", "Status", "Pending", "Received", "Failed"),
                    options("method", "Method", "Cash", "M-Pesa (manual)", "Bank", "Card", "Other"),
                    f("reference", "Reference", optional = true),
                    ref("originalPaymentId", "Original payment (refunds)", "payments", true),
                    notes,
                ),
            "expenses" to
                listOf(
                    vehicle,
                    name,
                    ref("categoryId", "Category", "categories"),
                    ref("providerId", "Provider", "providers", true),
                    amount,
                    date,
                    options("status", "Status", "Planned", "Due", "Paid", "Cancelled"),
                    f("paidDate", "Paid date (required when paid)", "date", true),
                    notes,
                ),
            "categories" to listOf(name, f("active", "Active", "boolean")),
            "providers" to listOf(name, f("phone", "Phone", optional = true), notes),
            "maintenance" to
                listOf(
                    vehicle,
                    name,
                    date,
                    f("start", "Service start (Nairobi)", "instant"),
                    f("end", "Service end (Nairobi)", "instant"),
                    ref("expenseId", "Linked expense", "expenses", true),
                    f("nextDueDate", "Next due date", "date", true),
                    f("nextDueKm", "Next due mileage", "number", true),
                    notes,
                ),
            "reminders" to
                listOf(
                    name,
                    ref("vehicleId", "Vehicle", "vehicles", true),
                    options(
                        "sourceType",
                        "Source",
                        "Service",
                        "Insurance",
                        "Tracker",
                        "Inspection",
                        "Licence",
                        "HireReturn",
                        "Balance",
                        "Backup",
                        "Export",
                        "Other",
                    ),
                    f("dueDate", "Due date", "date", true),
                    f("dueKm", "Due mileage (requires vehicle)", "number", true),
                    options("status", "Status", "Open", "Snoozed", "Completed", "Dismissed"),
                    f("snoozedUntil", "Snoozed until", "date", true),
                    notes,
                ),
            "readings" to
                listOf(
                    vehicle,
                    options("type", "Reading type", "Odometer", "Fuel"),
                    f("value", "Reading value", "number"),
                    date,
                    f("correctionReason", "Correction reason", optional = true),
                    notes,
                ),
            "conditions" to
                listOf(
                    ref("hireId", "Hire", "hires"),
                    options("phase", "Phase", "PRE", "POST"),
                    f("odometerKm", "Odometer km", "number"),
                    date,
                    f("fuel", "Fuel level", optional = true),
                    f("notes", "Inspection notes"),
                ),
            "damages" to
                listOf(
                    ref("hireId", "Hire", "hires"),
                    vehicle,
                    name,
                    amount,
                    date,
                    options("responsibility", "Responsibility", "Customer", "Business", "Other"),
                    options("status", "Status", "Open", "Charged", "Waived", "Settled"),
                    notes,
                ),
            "settings" to
                listOf(
                    options("currency", "Currency", "KES"),
                    options("timeZone", "Timezone", "Africa/Nairobi"),
                    f("bufferMinutes", "Turnaround buffer minutes", "number"),
                    f("remindersEnabled", "Enable reminders", "boolean"),
                    f("reminderLeadDays", "Advance notice days", "number", true),
                    f("reminderTime", "Daily reminder time (HH:mm Nairobi)", "text", true),
                    f("backupReminderDays", "Backup reminder interval days", "number", true),
                ),
        )
    val titles =
        mapOf(
            "vehicles" to "Vehicles",
            "customers" to "Customers",
            "hires" to "Hires",
            "payments" to "Payments & deposits",
            "expenses" to "Expenses",
            "categories" to "Categories",
            "providers" to "Providers",
            "maintenance" to "Maintenance",
            "reminders" to "Reminders",
            "readings" to "Odometer & fuel",
            "conditions" to "Condition checks",
            "damages" to "Fines & damages",
            "settings" to "Settings",
        )
}
