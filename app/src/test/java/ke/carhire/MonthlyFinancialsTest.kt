package ke.carhire

import java.time.LocalDate
import java.time.YearMonth
import ke.carhire.domain.model.FinancialMonth
import ke.carhire.domain.rules.MonthlyFinancials
import org.junit.Assert.*
import org.junit.Test

class MonthlyFinancialsTest {
    private fun row(kind: String, data: String, id: String = data) =
        LocalRecord(id, kind, payload = data)

    private val rows =
        listOf(
            row("hires", """{"vehicleId":"vehicle"}""", "hire"),
            row(
                "payments",
                """{"hireId":"hire","kind":"Payment","status":"Received","amountMinor":10000,"date":"2025-07-01"}""",
            ),
            row(
                "payments",
                """{"hireId":"hire","kind":"Refund","status":"Received","amountMinor":2000,"date":"2025-07-31"}""",
            ),
            row(
                "payments",
                """{"hireId":"hire","kind":"Payment","status":"Pending","amountMinor":99999,"date":"2025-07-01"}""",
            ),
            row(
                "payments",
                """{"hireId":"hire","kind":"Deposit","status":"Received","amountMinor":99999,"date":"2025-07-01"}""",
            ),
            row(
                "expenses",
                """{"vehicleId":"vehicle","status":"Paid","amountMinor":11000,"date":"2025-06-30","paidDate":"2025-07-15"}""",
            ),
            row(
                "expenses",
                """{"vehicleId":"vehicle","status":"Due","amountMinor":99999,"date":"2025-07-01"}""",
            ),
            row(
                "expenses",
                """{"vehicleId":"vehicle","status":"Paid","amountMinor":1000,"date":"2026-06-30","paidDate":null}""",
            ),
        )

    @Test
    fun financialYearIncludesZeroMonthsRefundsPaidDateAndNegativeCashProfit() {
        val result =
            MonthlyFinancials.calculate(
                rows,
                LocalDate.parse("2025-07-01"),
                LocalDate.parse("2026-06-30"),
                "vehicle",
            )
        assertEquals(12, result.size)
        assertEquals(FinancialMonth(YearMonth.of(2025, 7), 8000, 11000), result.first())
        assertEquals(-3000L, result.first().netProfitMinor)
        assertEquals(FinancialMonth(YearMonth.of(2025, 8), 0, 0), result[1])
        assertEquals(FinancialMonth(YearMonth.of(2026, 6), 0, 1000), result.last())
        assertTrue(
            MonthlyFinancials.calculate(
                    rows,
                    LocalDate.parse("2025-07-01"),
                    LocalDate.parse("2026-06-30"),
                    "other",
                )
                .all { it.netProfitMinor == 0L }
        )
    }

    @Test
    fun partialMonthsAndInvalidRangesAreHandled() {
        val result =
            MonthlyFinancials.calculate(
                rows,
                LocalDate.parse("2025-07-02"),
                LocalDate.parse("2025-07-30"),
            )
        assertEquals(1, result.size)
        assertEquals(-11000L, result.first().netProfitMinor)
        assertThrows(IllegalArgumentException::class.java) {
            MonthlyFinancials.calculate(
                rows,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-01-01"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonthlyFinancials.calculate(
                rows,
                LocalDate.parse("1900-01-01"),
                LocalDate.parse("2026-01-01"),
            )
        }
    }
}
