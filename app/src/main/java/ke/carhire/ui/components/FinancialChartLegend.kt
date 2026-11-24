package ke.carhire.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ke.carhire.domain.model.FinancialMonth

enum class FinancialChartSeries(val label: String) {
    Payments("Payments received, net refunds (solid)"),
    Expenses("Paid expenses (dashed)"),
    NetProfit("Net profit, cash basis (dotted)");

    fun amount(month: FinancialMonth): Long =
        when (this) {
            Payments -> month.paymentsReceivedMinor
            Expenses -> month.expensesPaidMinor
            NetProfit -> month.netProfitMinor
        }
}

@Composable
internal fun FinancialChartLegend(
    selectedSeries: Set<FinancialChartSeries>,
    onSelectedSeriesChange: (Set<FinancialChartSeries>) -> Unit,
    colors: List<Color>,
) {
    FinancialChartSeries.entries.forEach { key ->
        val selected = key in selectedSeries
        val color = colors[key.ordinal]
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { checked ->
                        onSelectedSeriesChange(
                            if (checked) selectedSeries + key else selectedSeries - key
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = color),
            )
            Text(
                key.label,
                Modifier.padding(start = 8.dp),
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
