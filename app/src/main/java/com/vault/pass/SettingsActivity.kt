package com.vault.pass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.vault.pass.databinding.ActivitySettingsBinding
import com.vault.pass.storage.CsvVault
import com.vault.pass.storage.PinManager
import com.vault.pass.storage.SettingsPrefs
import com.vault.pass.storage.VaultRepository

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SettingsPrefs
    private lateinit var pinManager: PinManager
    private lateinit var repo: VaultRepository

    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importCsv(it) }
    }

    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { exportCsv(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SettingsPrefs(this)
        applyTheme(prefs.theme)
        applyLocale(prefs.language)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinManager = PinManager(this)
        repo = VaultRepository(this)

        binding.btnBack.setOnClickListener { finish() }

        updateSelfDestructLabel()
        binding.btnSelfDestruct.setOnClickListener { configureSelfDestruct() }

        binding.btnImport.setOnClickListener {
            openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }
        binding.btnExport.setOnClickListener { createCsv.launch("passvault_export.csv") }

        when (prefs.language) {
            "fr" -> binding.langFr.isChecked = true
            else -> binding.langEn.isChecked = true
        }
        binding.langGroup.setOnCheckedChangeListener { _, id ->
            val lang = if (id == R.id.langFr) "fr" else "en"
            if (lang != prefs.language) {
                prefs.language = lang
                applyLocale(lang)
                recreate()
            }
        }

        when (prefs.theme) {
            "dark" -> binding.themeDark.isChecked = true
            "light" -> binding.themeLight.isChecked = true
            else -> binding.themeSystem.isChecked = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, _ ->
            val theme = when {
                binding.themeDark.isChecked -> "dark"
                binding.themeLight.isChecked -> "light"
                else -> "system"
            }
            prefs.theme = theme
            applyTheme(theme)
        }

        binding.switchAudit.isChecked = prefs.auditEnabled
        binding.switchAudit.setOnCheckedChangeListener { _, checked ->
            prefs.auditEnabled = checked
        }
    }

    private fun applyTheme(theme: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun applyLocale(lang: String) {
        AppCompatDelegate.setApplicationLocales(
            if (lang == "en") LocaleListCompat.forLanguageTags("en")
            else LocaleListCompat.forLanguageTags("fr")
        )
    }

    private fun updateSelfDestructLabel() {
        val n = prefs.selfDestructThreshold
        binding.btnSelfDestruct.text = if (n > 0)
            getString(R.string.settings_self_destruct_on, n)
        else getString(R.string.settings_self_destruct_configure)
    }

    private fun configureSelfDestruct() {
        val options = resources.getIntArray(R.array.self_destruct_options)
        val labels = options.map {
            getString(R.string.settings_self_destruct_after_n, it)
        }.toTypedArray()
        var chosen = options.first()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_self_destruct)
            .setSingleChoiceItems(labels, -1) { _, which -> chosen = options[which] }
            .setPositiveButton(R.string.action_continue) { _, _ -> confirmSelfDestruct(chosen) }
            .setNeutralButton(R.string.settings_self_destruct_off) { _, _ ->
                prefs.selfDestructThreshold = 0
                prefs.wrongAttempts = 0
                updateSelfDestructLabel()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmSelfDestruct(threshold: Int) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.hint_pin)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_confirm_pin)
            .setMessage(getString(R.string.settings_confirm_pin_desc, threshold))
            .setView(input)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                val entered = input.text?.toString().orEmpty()
                if (pinManager.verify(entered)) {
                    prefs.selfDestructThreshold = threshold
                    prefs.wrongAttempts = 0
                    updateSelfDestructLabel()
                    Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun importCsv(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            val imported = CsvVault.import(content)
            if (imported.isEmpty()) {
                Toast.makeText(this, R.string.import_empty, Toast.LENGTH_LONG).show()
                return
            }
            val entries = repo.load()
            entries.addAll(imported)
            repo.save(entries)
            Toast.makeText(this, getString(R.string.import_done, imported.size), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportCsv(uri: Uri) {
        try {
            val data = CsvVault.export(repo.load())
            contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, R.string.export_done, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }
}
