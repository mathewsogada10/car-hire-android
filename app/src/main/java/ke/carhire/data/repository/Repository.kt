package ke.carhire.data.repository

import androidx.room.withTransaction
import ke.carhire.CarDatabase
import ke.carhire.Evidence
import ke.carhire.LocalRecord
import ke.carhire.Meta
import ke.carhire.Operation
import ke.carhire.domain.rules.Rules
import ke.carhire.security.Crypto
import ke.carhire.sync.SyncGate
import ke.carhire.util.amount
import ke.carhire.util.json
import ke.carhire.util.now
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class Repository(val db: CarDatabase) {
    val dao = db.dao()
    val records = dao.observe()

    suspend fun seedCategories() {
        listOf(
                "Service",
                "Repairs",
                "Tyres",
                "Insurance",
                "Tracker",
                "Cleaning",
                "Inspection",
                "Parking",
                "Accessories",
                "Other",
            )
            .forEachIndexed { index, name ->
                val id = "10000000-0000-4000-8000-${(index+1).toString().padStart(12,'0')}"
                if (dao.find(id) == null)
                    save(
                        "categories",
                        id,
                        buildJsonObject {
                            put("name", name)
                            put("active", true)
                            put("systemKey", name.lowercase())
                        },
                    )
            }
    }

    suspend fun installation(): String =
        db.withTransaction {
            dao.meta("installation")?.value ?: uuid().also { dao.meta(Meta("installation", it)) }
        }

    suspend fun save(
        kind: String,
        id: String,
        data: JsonObject,
        expectedUpdatedAt: String? = null,
        deleted: Boolean = false,
    ) =
        db.withTransaction {
            val old = dao.find(id)
            require(old?.deletedAt == null) { "Deleted records cannot be resurrected" }
            require(old?.updatedAt == expectedUpdatedAt) {
                "Record changed while you were editing. Reopen it before saving."
            }
            val all = dao.all()
            val validated =
                if (deleted) {
                    requireNotNull(old)
                    Rules.deletion(old, all)
                    old.data()
                } else Rules.validate(kind, id, data, old, all)
            val row =
                LocalRecord(
                    id,
                    kind,
                    old?.revision ?: 0,
                    validated.toString(),
                    old?.createdAt ?: now(),
                    now(),
                    if (deleted) now() else null,
                )
            val operationId = uuid()
            val payload = buildJsonObject {
                put("operationId", operationId)
                put("id", id)
                put("kind", kind)
                put("baseRevision", row.revision)
                put("data", validated)
                put("deleted", deleted)
            }
            dao.put(row)
            dao.operations()
                .filter { it.entityId == id && it.state == "Error" }
                .forEach {
                    audit("SUPERSEDED_REJECTION", it.id, null, it.error)
                    dao.deleteOperation(it.id)
                }
            dao.putOperation(Operation(operationId, id, payload.toString()))
            audit(if (deleted) "DELETE" else "SAVE", id, old?.payload, row.payload)
        }

    suspend fun audit(action: String, id: String, before: String?, after: String?) {
        dao.evidence(
            Evidence(
                uuid(),
                "audit",
                buildJsonObject {
                        put("actor", "local-owner")
                        put("action", action)
                        put("entityId", id)
                        before?.let { put("beforeHash", Crypto.sha(it.toByteArray())) }
                        after?.let { put("afterHash", Crypto.sha(it.toByteArray())) }
                        put("at", now())
                    }
                    .toString(),
            )
        )
    }

    suspend fun applyPull(items: JsonArray, cursor: String) =
        db.withTransaction {
            for (n in items) {
                val row = fromWire(n.jsonObject)
                val pending = dao.operations().any { it.entityId == row.id }
                val local = dao.find(row.id)
                val purged = dao.meta("purged:${row.id}")?.takeIf { it.value.isNotEmpty() }
                if (purged != null && local != null && row.revision <= local.revision) continue
                if (
                    (pending ||
                        purged != null ||
                        local?.deletedAt != null && row.deletedAt == null) &&
                        local != null &&
                        (local.revision != row.revision || local.payload != row.payload)
                ) {
                    dao.put(local.copy(syncState = "Conflict"))
                    dao.evidence(Evidence(uuid(), "conflict", n.toString()))
                } else if (!pending) dao.put(row)
            }
            dao.meta(Meta("cursor", cursor))
            dao.meta(Meta("lastPull", now()))
        }

    suspend fun resolve(id: String, keepServer: Boolean, reason: String) =
        SyncGate.mutex.withLock { resolveLocked(id, keepServer, reason) }

    private suspend fun resolveLocked(id: String, keepServer: Boolean, reason: String) =
        db.withTransaction {
            require(reason.isNotBlank()) { "A conflict-resolution reason is required" }
            val local = requireNotNull(dao.find(id))
            val conflict =
                dao.evidence().firstOrNull {
                    it.kind == "conflict" &&
                        json.parseToJsonElement(it.payload).jsonObject.text("id") == id
                } ?: error("Fetch server changes before resolving")
            val server = fromWire(json.parseToJsonElement(conflict.payload).jsonObject)
            dao.operations().filter { it.entityId == id }.forEach { dao.deleteOperation(it.id) }
            if (keepServer) {
                dao.put(server)
                dao.meta(Meta("purged:$id", ""))
            } else {
                require(server.deletedAt == null) { "Cannot resurrect a server tombstone" }
                Rules.validate(local.kind, local.id, local.data(), server, dao.all())
                val op = uuid()
                dao.putOperation(
                    Operation(
                        op,
                        id,
                        buildJsonObject {
                                put("operationId", op)
                                put("id", id)
                                put("kind", local.kind)
                                put("baseRevision", server.revision)
                                put("data", local.data())
                                put("deleted", local.deletedAt != null)
                                put("reason", reason)
                            }
                            .toString(),
                    )
                )
                dao.put(local.copy(revision = server.revision, syncState = "Pending"))
            }
            audit(
                "CONFLICT_RESOLUTION",
                id,
                local.payload,
                if (keepServer) server.payload else reason,
            )
        }

    companion object {
        fun fromWire(n: JsonObject) =
            LocalRecord(
                n.text("id"),
                n.text("kind"),
                n.amount("revision"),
                n.getValue("data").toString(),
                n.text("createdAt"),
                n.text("updatedAt"),
                n.text("deletedAt").ifEmpty { null },
                "Synced",
            )
    }
}
