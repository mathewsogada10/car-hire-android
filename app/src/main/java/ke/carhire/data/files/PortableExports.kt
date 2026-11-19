package ke.carhire.data.files

import android.net.Uri
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import ke.carhire.Evidence
import ke.carhire.data.repository.Repository
import ke.carhire.domain.rules.Rules
import ke.carhire.security.Crypto
import ke.carhire.util.json
import ke.carhire.util.now
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class PortableExports(
    private val repository: Repository,
    private val documents: Documents,
) {
    private val dao = repository.dao

    suspend fun portable(): Pair<String, ByteArray> =
        repository.db.withTransaction {
            val rows = dao.all()
            val id = uuid()
            val files = sortedMapOf<String, ByteArray>()
            val names =
                mapOf(
                    "maintenance" to "maintenance_events",
                    "conditions" to "condition_checks",
                    "damages" to "fines_damages",
                )
            fun csv(value: String?) =
                if (value == null) "\\N" else "\"${value.replace("\"", "\"\"")}\""
            for (kind in Rules.kinds.sorted()) {
                val selected = rows.filter { it.kind == kind }
                val name = names[kind] ?: kind
                if (kind == "conditions")
                    files["data/$name.jsonl"] =
                        selected
                            .joinToString("\n", postfix = if (selected.isEmpty()) "" else "\n") {
                                it.wire().toString()
                            }
                            .toByteArray()
                else {
                    val fields = selected.flatMap { it.data().keys }.toSortedSet()
                    val headers =
                        listOf("id", "revision", "created_at", "updated_at", "deleted_at") +
                            fields.map { it.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase() }
                    val output = StringBuilder(headers.joinToString(",")).append('\n')
                    for (r in selected) {
                        val cells =
                            listOf(
                                r.id,
                                r.revision.toString(),
                                r.createdAt,
                                r.updatedAt,
                                r.deletedAt,
                            ) +
                                fields.map { key ->
                                    (r.data()[key] as? JsonPrimitive)?.contentOrNull
                                }
                        output.append(cells.joinToString(",") { csv(it) }).append('\n')
                    }
                    files["data/$name.csv"] = output.toString().toByteArray()
                }
            }
            files["data/records.jsonl"] =
                rows.joinToString("\n", postfix = "\n") { it.wire().toString() }.toByteArray()
            files["README.txt"] =
                "Private business data. UTF-8 CSV; \\N=null. KES integer minor units. records.jsonl is required for lossless typed import. Attachments excluded. Store safely.\n"
                    .toByteArray()
            val manifest = buildJsonObject {
                put("format", "car-hire-portable-export")
                put("schemaVersion", "1.0.0")
                put("exportBatchId", id)
                put("createdAt", now())
                put("sourceApp", "car-hire-android")
                put("sourceVersion", "0.1.0")
                put("installationId", repository.installation())
                put("currency", "KES")
                put("timeZone", "Africa/Nairobi")
                put("scope", "Full snapshot; all dates and vehicles")
                put(
                    "files",
                    JsonArray(
                        files.map { (path, bytes) ->
                            buildJsonObject {
                                put("path", path)
                                put("size", bytes.size)
                                put("sha256", Crypto.sha(bytes))
                            }
                        }
                    ),
                )
                put(
                    "entityCounts",
                    buildJsonObject {
                        Rules.kinds.sorted().forEach { kind ->
                            put(kind, rows.count { it.kind == kind })
                        }
                    },
                )
                put(
                    "financialTotals",
                    buildJsonObject {
                        Rules.financials(rows, LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31))
                            .forEach { (k, v) -> put(k, v) }
                    },
                )
                put("attachmentPolicy", "Excluded")
                put("attachments", JsonArray(emptyList()))
                put("verificationAlgorithm", "SHA-256")
                put("minimumImporter", "0.1.0")
                put("privacy", "Private business/customer data")
            }
            files["manifest.json"] = manifest.toString().toByteArray()
            files["checksums.sha256"] =
                files.entries
                    .joinToString("\n", postfix = "\n") { "${Crypto.sha(it.value)}  ${it.key}" }
                    .toByteArray()
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                files.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            val bytes = output.toByteArray()
            dao.evidence(
                Evidence(
                    id,
                    "export-created",
                    buildJsonObject {
                            put("manifest", manifest)
                            put(
                                "snapshot",
                                JsonArray(
                                    rows.map { r ->
                                        buildJsonObject {
                                            put("id", r.id)
                                            put("dataHash", Crypto.sha(r.payload.toByteArray()))
                                            put("updatedAt", r.updatedAt)
                                        }
                                    }
                                ),
                            )
                            put("zipHash", Crypto.sha(bytes))
                        }
                        .toString(),
                )
            )
            id to bytes
        }

    suspend fun verifyExportWritten(id: String, uri: Uri) {
        val e = dao.evidence().first { it.id == id }
        val expected = json.parseToJsonElement(e.payload).jsonObject.text("zipHash")
        require(Crypto.sha(documents.read(uri)) == expected)
        dao.evidence(
            Evidence(uuid(), "export-verified", buildJsonObject { put("exportId", id) }.toString())
        )
    }

    suspend fun acknowledge(id: String, confirmation: String) {
        require(confirmation == "I VERIFIED AND SAFELY STORED THIS EXPORT")
        require(
            dao.evidence().any {
                it.kind == "export-verified" &&
                    json.parseToJsonElement(it.payload).jsonObject.text("exportId") == id
            }
        )
        dao.evidence(
            Evidence(uuid(), "export-safe", buildJsonObject { put("exportId", id) }.toString())
        )
    }
}
