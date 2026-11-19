package ke.carhire.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class Vault(val context: Context) {
    private val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)

    private fun key(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("carhire-secrets", null) as? javax.crypto.SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "carhire-secrets",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(
            prefs
                .edit()
                .putString(
                    name,
                    Base64.encodeToString(
                        cipher.iv + cipher.doFinal(value.toByteArray()),
                        Base64.NO_WRAP,
                    ),
                )
                .commit()
        ) {
            "Secret storage failed"
        }
    }

    fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }

    fun hasPin() = prefs.contains("pinHash")

    fun setPin(pin: String) {
        require(pin.matches(Regex("\\d{6,12}"))) { "PIN must contain 6 to 12 digits" }
        val salt = Crypto.random(16)
        val hash =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 210000, 256))
                .encoded
        put("pinHash", Base64.encodeToString(salt + hash, Base64.NO_WRAP))
    }

    @Synchronized
    fun unlock(pin: String): Boolean {
        val blocked = prefs.getLong("blockedUntil", 0)
        require(System.currentTimeMillis() >= blocked) {
            "Too many attempts. Retry after five minutes."
        }
        val bytes = Base64.decode(requireNotNull(get("pinHash")), Base64.NO_WRAP)
        val hash =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(
                    PBEKeySpec(pin.toCharArray(), bytes.copyOfRange(0, 16), 210000, 256)
                )
                .encoded
        val valid = MessageDigest.isEqual(bytes.copyOfRange(16, bytes.size), hash)
        val attempts = if (valid) 0 else prefs.getInt("failures", 0) + 1
        require(
            prefs
                .edit()
                .putInt("failures", attempts)
                .putLong(
                    "blockedUntil",
                    if (attempts >= 5) System.currentTimeMillis() + 300000 else 0,
                )
                .commit()
        )
        return valid
    }
}
