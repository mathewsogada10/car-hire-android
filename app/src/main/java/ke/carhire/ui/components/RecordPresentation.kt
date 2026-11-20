package ke.carhire.ui.components

import java.time.ZoneId
import ke.carhire.LocalRecord
import ke.carhire.util.BusinessDateTime
import ke.carhire.util.text

fun money(minor: Long) = "KES " + java.text.DecimalFormat("#,##0.00").format(minor / 100.0)

fun title(row: LocalRecord, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = row.data()
    return d.text("registration")
        .ifEmpty { d.text("name") }
        .ifEmpty {
            d.text("plannedStart")
                .takeIf { it.isNotBlank() }
                ?.let { BusinessDateTime.display(it, zone) }
                .orEmpty()
        }
        .ifEmpty { "${d.text("date")} ${row.id.take(8)}" }
}
