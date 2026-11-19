package ke.carhire.data.files

import android.content.Context
import android.net.Uri
import ke.carhire.data.repository.Repository

class DataFiles(context: Context, repository: Repository) {
    private val documents = Documents(context)
    private val backups = BackupFiles(context, repository, documents)
    private val exports = PortableExports(repository, documents)
    private val purging = LocalPurge(context, repository)

    suspend fun backup(passphrase: CharArray): ByteArray = backups.backup(passphrase)

    suspend fun restore(bytes: ByteArray, passphrase: CharArray, safetyUri: Uri) =
        backups.restore(bytes, passphrase, safetyUri)

    suspend fun writeBackup(uri: Uri, bytes: ByteArray) = backups.writeBackup(uri, bytes)

    fun write(uri: Uri, bytes: ByteArray) = documents.write(uri, bytes)

    fun read(uri: Uri): ByteArray = documents.read(uri)

    suspend fun portable(): Pair<String, ByteArray> = exports.portable()

    suspend fun verifyExportWritten(id: String, uri: Uri) = exports.verifyExportWritten(id, uri)

    suspend fun acknowledge(id: String, confirmation: String) =
        exports.acknowledge(id, confirmation)

    suspend fun preview(exportId: String, through: String): PurgePreview =
        purging.preview(exportId, through)

    suspend fun purge(preview: PurgePreview, confirmation: String) =
        purging.purge(preview, confirmation)
}
