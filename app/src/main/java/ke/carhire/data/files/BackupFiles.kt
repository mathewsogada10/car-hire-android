package ke.carhire.data.files

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import ke.carhire.Meta
import ke.carhire.data.repository.Repository
import ke.carhire.domain.rules.Rules
import ke.carhire.security.Crypto
import ke.carhire.sync.SyncGate
import ke.carhire.util.json
import ke.carhire.util.now
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject

internal class BackupFiles(
    private val context: Context,
    private val repository: Repository,
    private val documents: Documents,
) {
    private val dao = repository.dao

    suspend fun backup(passphrase: CharArray): ByteArray =
        repository.db.withTransaction {
            val attachments = dao.attachments()
            val storage = AttachmentFiles(context, repository)
            val snapshot =
                Backup(
                    records = dao.all(),
                    outbox = dao.operations(),
                    evidence = dao.evidence(),
                    metadata = dao.allMeta(),
                    attachments = attachments,
                    files =
                        attachments
                            .distinctBy { it.sha256 }
                            .associate {
                                it.sha256 to
                                    android.util.Base64.encodeToString(
                                        storage.bytes(it),
                                        android.util.Base64.NO_WRAP,
                                    )
                            },
                )
            val plain = json.encodeToString(snapshot).toByteArray()
            require(plain.size <= 75 * 1024 * 1024) { "Backup exceeds supported size" }
            val bytes = Crypto.encrypt(plain, passphrase)
            plain.fill(0)
            bytes
        }

    suspend fun restore(bytes: ByteArray, passphrase: CharArray, safetyUri: Uri) =
        SyncGate.mutex.withLock { restoreLocked(bytes, passphrase, safetyUri) }

    private suspend fun restoreLocked(bytes: ByteArray, passphrase: CharArray, safetyUri: Uri) {
        val plain = Crypto.decrypt(bytes, passphrase)
        val snapshot =
            try {
                json.decodeFromString<Backup>(String(plain))
            } finally {
                plain.fill(0)
            }
        require(snapshot.format == "car-hire-encrypted-backup" && snapshot.schema == 1) {
            "Unsupported backup schema"
        }
        require(snapshot.records.map { it.id }.toSet().size == snapshot.records.size) {
            "Duplicate record IDs"
        }
        for (r in snapshot.records) {
            java.util.UUID.fromString(r.id)
            require(r.kind in Rules.kinds)
            r.data()
            if (r.deletedAt == null) Rules.validate(r.kind, r.id, r.data(), null, snapshot.records)
        }
        require(snapshot.outbox.map { it.id }.toSet().size == snapshot.outbox.size) {
            "Duplicate outbox IDs"
        }
        snapshot.outbox.forEach {
            java.util.UUID.fromString(it.id)
            require(snapshot.records.any { row -> row.id == it.entityId })
            val p = json.parseToJsonElement(it.payload).jsonObject
            require(p.text("operationId") == it.id && p.text("id") == it.entityId) {
                "Corrupt outbox operation"
            }
        }
        snapshot.evidence.forEach { json.parseToJsonElement(it.payload) }
        require(context.filesDir.usableSpace > bytes.size * 3L) {
            "Insufficient space for safe restore"
        }
        val decoded =
            snapshot.files.mapValues { (hash, encoded) ->
                android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP).also {
                    require(Crypto.sha(it) == hash) { "Attachment checksum mismatch" }
                }
            }
        snapshot.attachments.forEach {
            require(snapshot.records.any { row -> row.id == it.parentId })
            val content = requireNotNull(decoded[it.sha256])
            require(content.size.toLong() == it.size && AttachmentFiles.detect(content) == it.mime)
        }
        val safety = backup(passphrase)
        writeBackup(safetyUri, safety)
        val storage = AttachmentFiles(context, repository)
        decoded.forEach { (hash, content) -> storage.store(content, hash) }
        repository.db.withTransaction {
            dao.clearOperations()
            dao.clearRecords()
            dao.clearEvidence()
            dao.clearMeta()
            dao.clearAttachments()
            snapshot.records.forEach { dao.put(it) }
            snapshot.outbox.forEach { dao.putOperation(it) }
            snapshot.evidence.forEach { dao.evidence(it) }
            snapshot.metadata.forEach { dao.meta(it) }
            dao.meta(Meta("lastBackup", now()))
            snapshot.attachments.forEach { dao.attachment(it) }
            dao.meta(Meta("cursor", ""))
            dao.meta(Meta("installation", uuid()))
            repository.audit("RESTORE", uuid(), null, Crypto.sha(bytes))
        }
    }

    suspend fun writeBackup(uri: Uri, bytes: ByteArray) {
        documents.write(uri, bytes)
        dao.meta(Meta("lastBackup", now()))
    }
}
