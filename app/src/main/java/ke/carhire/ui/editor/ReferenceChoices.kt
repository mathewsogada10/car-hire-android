package ke.carhire.ui.editor

import java.time.ZoneId
import ke.carhire.LocalRecord
import ke.carhire.ui.components.title
import ke.carhire.util.text

internal class ReferenceChoices(records: List<LocalRecord>, zone: ZoneId) {
    private val byId = records.associateBy { it.id }
    private val labels =
        records.associate { row ->
            val label =
                if (row.kind == "hires") {
                    val vehicle =
                        byId[row.data().text("vehicleId")]?.takeIf { it.kind == "vehicles" }
                    val registration =
                        vehicle?.data()?.text("registration").orEmpty().ifBlank {
                            "Vehicle unavailable"
                        }
                    "$registration | ${title(row, zone)}"
                } else title(row, zone)
            row.id to label
        }
    private val active = records.filter { it.deletedAt == null }.groupBy { it.kind }

    fun label(kind: String, id: String): String? =
        byId[id]?.takeIf { it.kind == kind }?.let { labels[it.id] }

    fun options(kind: String, search: String): List<Pair<String, String>> =
        active[kind]
            .orEmpty()
            .asSequence()
            .map { it.id to labels.getValue(it.id) }
            .filter { search.isBlank() || it.second.contains(search.trim(), ignoreCase = true) }
            .take(100)
            .toList()
}
