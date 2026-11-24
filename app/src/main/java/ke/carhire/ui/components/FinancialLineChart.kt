package ke.carhire.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ke.carhire.domain.model.FinancialMonth
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun FinancialLineChart(
    months: List<FinancialMonth>,
    selectedSeries: Set<FinancialChartSeries>,
    onSelectedSeriesChange: (Set<FinancialChartSeries>) -> Unit,
) {
    if (months.isEmpty()) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors =
        if (dark) listOf(Color(0xFF60A5FA), Color(0xFFFB923C), Color(0xFF4ADE80))
        else listOf(Color(0xFF2563EB), Color(0xFFC2410C), Color(0xFF15803D))
    val textColor = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val series = FinancialChartSeries.entries.filter { it in selectedSeries }
    Column {
        FinancialChartLegend(selectedSeries, onSelectedSeriesChange, colors)
        if (series.isEmpty()) {
            Text("Select at least one line to display the chart.")
            return@Column
        }
        val values = series.associateWith { key -> months.map(key::amount) }
        val amounts = values.values.flatten()
        val low = minOf(0L, amounts.min()).toDouble()
        val high = maxOf(0L, amounts.max()).toDouble().let { if (it == low) it + 100 else it }
        Text("Amount (KES)", style = MaterialTheme.typography.labelSmall)
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).testTag("financial-line-chart").semantics {
                contentDescription =
                    "Monthly line graph for ${months.first().month} to ${months.last().month}. Visible lines: ${series.joinToString("; ") { it.label }}. Exact amounts are listed below."
            }
        ) {
            val left = 56.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 32.dp.toPx()
            fun x(index: Int) =
                if (months.size == 1) (left + right) / 2
                else left + (right - left) * index / (months.size - 1)
            fun y(value: Double) =
                bottom - ((value - low) / (high - low) * (bottom - top)).toFloat()
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor.toArgb()
                    textSize = 10.sp.toPx()
                }
            for (tick in 0..4) {
                val amount = low + (high - low) * tick / 4
                val position = y(amount)
                drawLine(grid, Offset(left, position), Offset(right, position))
                paint.textAlign = Paint.Align.RIGHT
                val label =
                    when {
                        abs(amount) >= 100000000 ->
                            String.format(Locale.ROOT, "%.1fm", amount / 100000000)
                        abs(amount) >= 100000 ->
                            String.format(Locale.ROOT, "%.1fk", amount / 100000)
                        else -> String.format(Locale.ROOT, "%.0f", amount / 100)
                    }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    left - 6.dp.toPx(),
                    position + 3.dp.toPx(),
                    paint,
                )
            }
            drawLine(textColor.copy(alpha = 0.5f), Offset(left, y(0.0)), Offset(right, y(0.0)))
            values.forEach { (key, amounts) ->
                val index = key.ordinal
                val path = Path()
                amounts.forEachIndexed { point, amount ->
                    val position = Offset(x(point), y(amount.toDouble()))
                    if (point == 0) path.moveTo(position.x, position.y)
                    else path.lineTo(position.x, position.y)
                    drawCircle(colors[index], 2.5.dp.toPx(), position)
                }
                val effect =
                    when (index) {
                        1 -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                        2 -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                        else -> null
                    }
                drawPath(
                    path,
                    colors[index],
                    style = Stroke(width = 2.dp.toPx(), pathEffect = effect),
                )
            }
            val interval = ceil(months.size / 3.0).toInt().coerceAtLeast(1)
            months.forEachIndexed { index, month ->
                if (index % interval == 0 || index == months.lastIndex) {
                    paint.textAlign =
                        when (index) {
                            0 -> Paint.Align.LEFT
                            months.lastIndex -> Paint.Align.RIGHT
                            else -> Paint.Align.CENTER
                        }
                    drawContext.canvas.nativeCanvas.drawText(
                        month.month.toString(),
                        x(index),
                        size.height - 6.dp.toPx(),
                        paint,
                    )
                }
            }
        }
    }
}
