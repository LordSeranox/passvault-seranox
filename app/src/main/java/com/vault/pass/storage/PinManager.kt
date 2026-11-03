package com.vault.pass.storage

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pin_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPin(): Boolean = prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        if (!hasPin()) return false
        val salt = Base64.decode(prefs.getString(KEY_SALT, ""), Base64.NO_WRAP)
        val expected = prefs.getString(KEY_HASH, "")!!
        return hash(pin, salt) == expected
    }

    private fun hash(pin: String, salt: ByteArray): String = Base64.encodeToString(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN))
            .encoded,
        Base64.NO_WRAP
    )

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val ITERATIONS = 120_000
        private const val KEY_LEN = 256
    }
}
