package ke.carhire.data.files

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import ke.carhire.security.Crypto

internal class Documents(private val context: Context) {
    fun write(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(bytes)
            it.flush()
        } ?: error("Cannot open destination")
        val verified = read(uri)
        require(Crypto.sha(verified) == Crypto.sha(bytes)) {
            "Written file verification failed; do not use this copy"
        }
    }

    fun read(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                require(out.size() + n <= 100 * 1024 * 1024) { "File exceeds supported limit" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: error("Cannot open selected file")
}
