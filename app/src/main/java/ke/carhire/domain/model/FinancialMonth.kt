package ke.carhire.domain.model

import java.time.YearMonth

data class FinancialMonth(
    val month: YearMonth,
    val paymentsReceivedMinor: Long,
    val expensesPaidMinor: Long,
) {
    val netProfitMinor: Long = Math.subtractExact(paymentsReceivedMinor, expensesPaidMinor)
}
