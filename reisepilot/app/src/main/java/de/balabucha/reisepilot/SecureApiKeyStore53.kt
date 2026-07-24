package de.balabucha.reisepilot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiKeyStore53(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasKey(): Boolean = preferences.contains(CIPHERTEXT) && load().isNotBlank()

    fun save(rawKey: String) {
        val key = rawKey.trim()
        require(key.startsWith("sk-") && key.length >= 20) { "Der API-Key sieht nicht gültig aus." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_HINT, key.takeLast(4))
            .apply()
    }

    fun load(): String {
        val iv = preferences.getString(IV, null) ?: return ""
        val ciphertext = preferences.getString(CIPHERTEXT, null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            clear()
            ""
        }
    }

    fun hint(): String = preferences.getString(KEY_HINT, "").orEmpty().let { suffix ->
        if (suffix.isBlank()) "" else "••••$suffix"
    }

    fun clear() {
        preferences.edit().remove(IV).remove(CIPHERTEXT).remove(KEY_HINT).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "assistant_secret_v1"
        private const val IV = "openai_iv"
        private const val CIPHERTEXT = "openai_ciphertext"
        private const val KEY_HINT = "openai_hint"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "reisepilot_openai_test_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}