package ke.carhire.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import java.io.File
import ke.carhire.LocalAttachment
import ke.carhire.data.repository.Repository
import ke.carhire.security.Crypto
import ke.carhire.util.uuid

class AttachmentFiles(private val context: Context, private val repository: Repository) {
    private val directory =
        File(context.filesDir, "attachments").apply { require(isDirectory || mkdirs()) }

    fun bytes(attachment: LocalAttachment): ByteArray =
        File(directory, attachment.sha256).readBytes().also {
            require(it.size.toLong() == attachment.size && Crypto.sha(it) == attachment.sha256) {
                "Attachment integrity check failed"
            }
        }

    fun store(bytes: ByteArray, hash: String) {
        require(hash.matches(Regex("[0-9a-f]{64}")) && Crypto.sha(bytes) == hash)
        val target = File(directory, hash)
        if (target.exists())
            require(Crypto.sha(target.readBytes()) == hash) {
                "Existing attachment content is corrupt"
            }
        else {
            val temporary = File(directory, "$hash.${uuid()}.part")
            temporary.outputStream().use {
                it.write(bytes)
                it.fd.sync()
            }
            require(temporary.renameTo(target)) { "Unable to commit private attachment file" }
        }
    }

    suspend fun add(parentId: String, uri: Uri): LocalAttachment {
        val content = DataFiles(context, repository).read(uri)
        require(content.size in 1..20 * 1024 * 1024) { "Attachment must be 1 byte to 20 MiB" }
        val mime = detect(content)
        val filename =
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "attachment" }
                ?: "attachment"
        val row =
            LocalAttachment(
                uuid(),
                parentId,
                filename.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(120),
                mime,
                content.size.toLong(),
                Crypto.sha(content),
            )
        repository.db.withTransaction {
            require(repository.dao.find(parentId)?.deletedAt == null)
            require(repository.dao.attachments().count { it.parentId == parentId } < 20) {
                "Maximum 20 attachments per record"
            }
            store(content, row.sha256)
            repository.dao.attachment(row)
            repository.audit("ATTACHMENT_CREATE", row.id, null, row.sha256)
        }
        return row
    }

    companion object {
        fun detect(b: ByteArray): String =
            when {
                b.size >= 5 && String(b, 0, 5, Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
                b.size >= 8 &&
                    b.take(8) ==
                        listOf(
                            137.toByte(),
                            80.toByte(),
                            78.toByte(),
                            71.toByte(),
                            13.toByte(),
                            10.toByte(),
                            26.toByte(),
                            10.toByte(),
                        ) -> "image/png"
                b.size >= 3 &&
                    b[0] == 255.toByte() &&
                    b[1] == 216.toByte() &&
                    b[2] == 255.toByte() -> "image/jpeg"
                else -> error("Only PDF, PNG and JPEG attachments are supported")
            }
    }
}
