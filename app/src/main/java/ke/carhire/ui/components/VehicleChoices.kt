package ke.carhire.ui.components

import java.util.Locale
import ke.carhire.LocalRecord
import ke.carhire.util.text

internal data class VehicleChoice(
    val id: String,
    val registration: String,
    val description: String,
    val archived: Boolean,
) {
    val label: String
        get() = registration + if (archived) " (archived)" else ""
}

internal class VehicleChoices(records: List<LocalRecord>) {
    private val searchable =
        records
            .asSequence()
            .filter { it.kind == "vehicles" && it.syncState != "Purged Locally" }
            .mapNotNull { row ->
                val data = row.data()
                val registration = data.text("registration")
                if (registration.isBlank()) null
                else
                    VehicleChoice(
                        row.id,
                        registration,
                        listOf(data.text("make"), data.text("model"))
                            .filter { it.isNotBlank() }
                            .joinToString(" "),
                        row.deletedAt != null,
                    )
            }
            .sortedWith(compareBy<VehicleChoice> { it.registration }.thenBy { it.id })
            .map { it to normalize(it.registration) }
            .toList()
    private val byId = searchable.associate { it.first.id to it.first }

    fun selected(id: String): VehicleChoice? = byId[id]

    fun matching(query: String, limit: Int): List<VehicleChoice> {
        val needle = normalize(query)
        if (needle.isEmpty()) return emptyList()
        return searchable
            .asSequence()
            .filter { (_, registration) -> registration.contains(needle) }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun normalize(value: String) =
        value.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
}
