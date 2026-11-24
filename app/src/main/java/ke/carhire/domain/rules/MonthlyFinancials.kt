package ke.carhire.domain.rules

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import ke.carhire.LocalRecord
import ke.carhire.domain.model.FinancialMonth
import ke.carhire.util.amount
import ke.carhire.util.text

object MonthlyFinancials {
    fun calculate(
        rows: List<LocalRecord>,
        from: LocalDate,
        to: LocalDate,
        vehicleId: String = "",
    ): List<FinancialMonth> {
        require(to >= from) { "End date precedes start date" }
        val first = YearMonth.from(from)
        val last = YearMonth.from(to)
        require(ChronoUnit.MONTHS.between(first, last) < 1200) {
            "Select a period covering at most 1200 months (100 years)"
        }
        val active = rows.filter { it.deletedAt == null }
        val hires =
            active
                .filter {
                    it.kind == "hires" &&
                        (vehicleId.isEmpty() || it.data().text("vehicleId") == vehicleId)
                }
                .map { it.id }
                .toSet()
        val totals = linkedMapOf<YearMonth, LongArray>()
        var month = first
        while (month <= last) {
            totals[month] = LongArray(2)
            month = month.plusMonths(1)
        }
        for (row in active) {
            val data = row.data()
            var payment = 0L
            var expense = 0L
            val date: LocalDate
            if (
                row.kind == "payments" &&
                    data.text("status") == "Received" &&
                    data.text("hireId") in hires
            ) {
                val kind = data.text("kind")
                if (kind !in listOf("Payment", "Refund")) continue
                payment = data.amount("amountMinor")
                if (kind == "Refund") payment = Math.negateExact(payment)
                date = LocalDate.parse(data.text("date"))
            } else if (
                row.kind == "expenses" &&
                    data.text("status") == "Paid" &&
                    (vehicleId.isEmpty() || data.text("vehicleId") == vehicleId)
            ) {
                expense = data.amount("amountMinor")
                date = LocalDate.parse(data.text("paidDate").ifBlank { data.text("date") })
            } else continue
            if (date < from || date > to) continue
            val bucket = totals.getValue(YearMonth.from(date))
            bucket[0] = Math.addExact(bucket[0], payment)
            bucket[1] = Math.addExact(bucket[1], expense)
        }
        return totals.map { (key, value) -> FinancialMonth(key, value[0], value[1]) }
    }
}
