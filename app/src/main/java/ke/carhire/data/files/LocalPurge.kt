package ke.carhire.data.files

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import ke.carhire.Evidence
import ke.carhire.Meta
import ke.carhire.data.repository.Repository
import ke.carhire.security.Crypto
import ke.carhire.security.Vault
import ke.carhire.util.amount
import ke.carhire.util.json
import ke.carhire.util.now
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class LocalPurge(private val context: Context, private val repository: Repository) {
    private val dao = repository.dao

    suspend fun preview(exportId: String, through: String): PurgePreview =
        repository.db.withTransaction {
            val date = LocalDate.parse(through)
            val evidence = dao.evidence()
            require(
                evidence.any {
                    it.kind == "export-safe" &&
                        json.parseToJsonElement(it.payload).jsonObject.text("exportId") == exportId
                }
            ) {
                "Export must be verified and deliberately acknowledged safe"
            }
            val localOnly = Vault(context).get("backendUrl").isNullOrEmpty()
            val pending =
                dao.operations().filter { it.state != "Superseded" }.map { it.entityId }.toSet()
            val exported =
                json
                    .parseToJsonElement(evidence.first { it.id == exportId }.payload)
                    .jsonObject
                    .getValue("snapshot")
                    .jsonArray
                    .associate { it.jsonObject.text("id") to it.jsonObject }
            val rows = dao.all().filter { it.deletedAt == null }
            val eligible = linkedSetOf<String>()
            for (r in rows) {
                val d = r.data()
                if (
                    (!localOnly && r.id in pending) ||
                        exported[r.id]?.text("dataHash") != Crypto.sha(r.payload.toByteArray()) ||
                        exported[r.id]?.text("updatedAt") != r.updatedAt
                )
                    continue
                if (r.kind == "hires" && d.text("status") in listOf("Completed", "Cancelled")) {
                    val end = d.text("actualEnd").ifEmpty { d.text("plannedEnd") }
                    if (
                        Instant.parse(end).atZone(ZoneId.of("Africa/Nairobi")).toLocalDate() <= date
                    )
                        eligible.add(r.id)
                }
                if (
                    r.kind == "expenses" &&
                        d.text("status") in listOf("Paid", "Cancelled") &&
                        LocalDate.parse(d.text("date")) <= date
                )
                    eligible.add(r.id)
            }
            for (r in rows) if (
                r.kind in listOf("payments", "conditions", "damages") &&
                    r.data().text("hireId") in eligible &&
                    (localOnly || r.id !in pending) &&
                    exported[r.id]?.text("dataHash") == Crypto.sha(r.payload.toByteArray()) &&
                    exported[r.id]?.text("updatedAt") == r.updatedAt
            )
                eligible.add(r.id)
            var changed: Boolean
            dao.attachments().forEach { eligible.remove(it.parentId) }
            do {
                changed = false
                for (r in rows) {
                    val d = r.data()
                    for (key in listOf("hireId", "expenseId", "originalPaymentId")) if (
                        r.id !in eligible && eligible.remove(d.text(key))
                    )
                        changed = true
                    if (
                        r.kind in listOf("payments", "conditions", "damages") &&
                            d.text("hireId") !in eligible &&
                            eligible.remove(r.id)
                    )
                        changed = true
                }
            } while (changed)
            for (hire in rows.filter { it.kind == "hires" && it.id in eligible }) {
                val deposit =
                    rows
                        .filter {
                            it.kind == "payments" &&
                                it.data().text("hireId") == hire.id &&
                                it.data().text("status") == "Received"
                        }
                        .sumOf {
                            val d = it.data()
                            when (d.text("kind")) {
                                "Deposit" -> d.amount("amountMinor")
                                "DepositRefund",
                                "DepositApplication" -> -d.amount("amountMinor")
                                else -> 0L
                            }
                        }
                if (deposit != 0L) {
                    eligible.remove(hire.id)
                    rows
                        .filter { it.data().text("hireId") == hire.id }
                        .forEach { eligible.remove(it.id) }
                }
            }
            val ids = eligible.sorted()
            PurgePreview(
                exportId,
                through,
                ids,
                Crypto.sha(
                    "$exportId|$through|${rows.filter { it.id in eligible }.joinToString { it.wire().toString() }}"
                        .toByteArray()
                ),
                rows.size - ids.size,
            )
        }

    suspend fun purge(preview: PurgePreview, confirmation: String) =
        repository.db.withTransaction {
            val fresh = this@LocalPurge.preview(preview.exportId, preview.through)
            require(fresh.hash == preview.hash) { "Purge preview is stale" }
            require(fresh.ids.isNotEmpty() && confirmation == "PURGE ${fresh.ids.size}") {
                "Typed confirmation does not match"
            }
            dao.operations()
                .filter { it.entityId in fresh.ids }
                .forEach { dao.deleteOperation(it.id) }
            fresh.ids.forEach { id ->
                val row = requireNotNull(dao.find(id))
                dao.put(
                    row.copy(
                        payload = "{}",
                        deletedAt = now(),
                        updatedAt = now(),
                        syncState = "Purged Locally",
                    )
                )
                dao.meta(Meta("purged:$id", row.revision.toString()))
            }
            dao.evidence(
                Evidence(
                    uuid(),
                    "purge",
                    buildJsonObject {
                            put("exportId", fresh.exportId)
                            put("count", fresh.ids.size)
                            put("previewHash", fresh.hash)
                        }
                        .toString(),
                )
            )
        }
}
