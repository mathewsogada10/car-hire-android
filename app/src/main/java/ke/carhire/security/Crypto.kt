package ke.carhire.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun derive(passphrase: CharArray, salt: ByteArray) =
        SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(PBEKeySpec(passphrase, salt, 210000, 256))
                .encoded,
            "AES",
        )

    fun encrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Recovery passphrase must be at least 12 characters" }
        val salt = random(16)
        val nonce = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD("CHBK1".toByteArray())
        return "CHBK1".toByteArray() + salt + nonce + cipher.doFinal(bytes)
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        require(bytes.size in 49..100 * 1024 * 1024 && String(bytes.copyOfRange(0, 5)) == "CHBK1") {
            "Unsupported or oversized backup"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            derive(passphrase, bytes.copyOfRange(5, 21)),
            GCMParameterSpec(128, bytes.copyOfRange(21, 33)),
        )
        cipher.updateAAD("CHBK1".toByteArray())
        return cipher.doFinal(bytes.copyOfRange(33, bytes.size))
    }
}
