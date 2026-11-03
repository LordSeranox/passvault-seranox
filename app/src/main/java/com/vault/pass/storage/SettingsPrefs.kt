package com.vault.pass.storage

import android.content.Context
import android.content.SharedPreferences

class SettingsPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANG, "fr") ?: "fr"
        set(v) = prefs.edit().putString(KEY_LANG, v).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var selfDestructThreshold: Int
        get() = prefs.getInt(KEY_SD, 0)
        set(v) = prefs.edit().putInt(KEY_SD, v).apply()

    var wrongAttempts: Int
        get() = prefs.getInt(KEY_WRONG, 0)
        set(v) = prefs.edit().putInt(KEY_WRONG, v).apply()

    var auditEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUDIT, v).apply()

    companion object {
        private const val KEY_LANG = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_SD = "self_destruct_threshold"
        private const val KEY_WRONG = "wrong_attempts"
        private const val KEY_AUDIT = "audit_enabled"
    }
}
