package com.vault.pass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.vault.pass.databinding.ActivityMainBinding
import com.vault.pass.databinding.DialogEntryBinding
import com.vault.pass.storage.PinManager
import com.vault.pass.storage.SettingsPrefs
import com.vault.pass.storage.VaultRepository
import com.vault.pass.ui.EntryAdapter
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: VaultRepository
    private lateinit var pinManager: PinManager
    private lateinit var prefs: SettingsPrefs
    private lateinit var adapter: EntryAdapter
    private var entries = mutableListOf<VaultEntry>()
    private var unlocked = false
    private var setupStage = 0
    private var firstPin = ""
    private val pinBuffer = StringBuilder()
    private var failedAttempts = 0
    private var cooldownUntil = 0L
    private var verifying = false
    private var skipNextLock = false
    private val handler = Handler(Looper.getMainLooper())

    private val cooldownTicker = object : Runnable {
        override fun run() {
            val remaining = cooldownUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                binding.txtPinError.text = ""
            } else {
                binding.txtPinError.text = getString(R.string.pin_cooldown, (remaining / 1000).toInt() + 1)
                handler.postDelayed(this, 500)
            }
        }
    }

    private val totpTicker = object : Runnable {
        override fun run() {
            if (!unlocked) return
            if (entries.any { !it.deleted && it.totpSecret.isNotBlank() }) {
                adapter.submit(visibleEntries(), binding.searchInput.text?.toString().orEmpty())
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val pinErrors = intArrayOf(
        R.string.pin_error_1, R.string.pin_error_2, R.string.pin_error_3,
        R.string.pin_error_4, R.string.pin_error_5, R.string.pin_error_6,
        R.string.pin_error_7, R.string.pin_error_8, R.string.pin_error_9,
        R.string.pin_error_10
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SettingsPrefs(this)
        applyLocale(prefs.language)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = VaultRepository(this)
        pinManager = PinManager(this)
        adapter = EntryAdapter(
            entries,
            onCopyUser = { copyToClipboard(it.username) },
            onCopyPass = { copyToClipboard(it.password) },
            onCopyTotp = { copyToClipboard(it) },
            onEdit = { showEntryDialog(it) },
            onDelete = { confirmDelete(it) },
            onTogglePin = { togglePin(it) }
        )
        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        val digitIds = listOf(
            binding.pad0, binding.pad1, binding.pad2, binding.pad3, binding.pad4,
            binding.pad5, binding.pad6, binding.pad7, binding.pad8, binding.pad9
        )
        digitIds.forEachIndexed { d, btn ->
            btn.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDigit(d.toString())
            }
        }
        binding.padDel.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onDeleteDigit()
        }

        binding.fabAdd.setOnClickListener { showEntryDialog(null) }
        binding.btnSettings.setOnClickListener {
            skipNextLock = true
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.searchInput.doAfterTextChanged { refreshList() }
        updateLockUi()
    }

    override fun onStop() {
        super.onStop()
        if (skipNextLock) {
            skipNextLock = false
            return
        }
        lock()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) {
            entries = repo.load()
            refreshList()
        }
    }

    private fun applyLocale(lang: String) {
        AppCompatDelegate.setApplicationLocales(
            if (lang == "en") LocaleListCompat.forLanguageTags("en")
            else LocaleListCompat.forLanguageTags("fr")
        )
    }

    private fun updateLockUi() {
        pinBuffer.clear()
        binding.txtPinError.text = ""
        updateDots()
        if (pinManager.hasPin()) {
            setupStage = 0
            binding.unlockSubtitle.setText(R.string.pin_enter_subtitle)
        } else {
            setupStage = 1
            binding.unlockSubtitle.setText(R.string.pin_create_subtitle)
        }
    }

    private fun updateDots() {
        val sb = StringBuilder()
        repeat(PIN_LENGTH) { i ->
            sb.append(if (i < pinBuffer.length) "●" else "○")
            if (i < PIN_LENGTH - 1) sb.append(" ")
        }
        binding.pinDots.text = sb.toString()
    }

    private fun onDigit(digit: String) {
        if (System.currentTimeMillis() < cooldownUntil) {
            showCooldown()
            return
        }
        if (verifying) return
        if (pinBuffer.length >= PIN_LENGTH) return
        pinBuffer.append(digit)
        binding.txtPinError.text = ""
        updateDots()
        if (pinBuffer.length == PIN_LENGTH) submitPin()
    }

    private fun onDeleteDigit() {
        if (pinBuffer.isNotEmpty()) {
            pinBuffer.deleteCharAt(pinBuffer.length - 1)
            updateDots()
        }
    }

    private fun submitPin() {
        val pin = pinBuffer.toString()

        if (pinManager.hasPin()) {
            verifying = true
            Thread {
                val ok = pinManager.verify(pin)
                runOnUiThread {
                    verifying = false
                    if (ok) {
                        prefs.wrongAttempts = 0
                        failedAttempts = 0
                        unlock()
                    } else {
                        handleWrongPin()
                    }
                }
            }.start()
            return
        }

        if (setupStage == 1) {
            firstPin = pin
            setupStage = 2
            pinBuffer.clear()
            updateDots()
            binding.unlockSubtitle.setText(R.string.pin_confirm_subtitle)
        } else {
            if (pin == firstPin) {
                pinManager.setPin(pin)
                unlock()
            } else {
                setupStage = 1
                firstPin = ""
                pinBuffer.clear()
                updateDots()
                binding.unlockSubtitle.setText(R.string.pin_create_subtitle)
                binding.txtPinError.setText(R.string.pin_mismatch)
            }
        }
    }

    private fun handleWrongPin() {
        failedAttempts++
        prefs.wrongAttempts = prefs.wrongAttempts + 1

        if (prefs.selfDestructThreshold > 0 &&
            prefs.wrongAttempts >= prefs.selfDestructThreshold
        ) {
            selfDestruct()
            return
        }

        pinBuffer.clear()
        updateDots()
        if (failedAttempts >= MAX_ATTEMPTS) {
            cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
            failedAttempts = 0
            binding.txtPinError.setText(R.string.pin_cooldown_start)
            handler.postDelayed(cooldownTicker, 500)
        } else {
            binding.txtPinError.setText(pinErrors[Random.nextInt(pinErrors.size)])
        }
    }

    private fun selfDestruct() {
        entries.clear()
        repo.save(entries)
        prefs.wrongAttempts = 0
        failedAttempts = 0
        cooldownUntil = 0L
        handler.removeCallbacks(cooldownTicker)
        pinBuffer.clear()
        updateDots()
        binding.txtPinError.setText(R.string.self_destruct_done)
    }

    private fun showCooldown() {
        handler.removeCallbacks(cooldownTicker)
        handler.post(cooldownTicker)
    }

    private fun unlock() {
        unlocked = true
        entries = repo.load()
        binding.lockView.visibility = View.GONE
        binding.mainView.visibility = View.VISIBLE
        refreshList()
        handler.removeCallbacks(totpTicker)
        handler.postDelayed(totpTicker, 1000)
    }

    private fun lock() {
        if (!unlocked) return
        unlocked = false
        handler.removeCallbacks(totpTicker)
        entries.clear()
        adapter.submit(entries)
        binding.searchInput.setText("")
        binding.mainView.visibility = View.GONE
        binding.lockView.visibility = View.VISIBLE
        updateLockUi()
    }

    private fun visibleEntries(): List<VaultEntry> {
        val active = entries.filter { !it.deleted }
        return when (prefs.sortOrder) {
            "recent" -> active.sortedWith(
                compareByDescending<VaultEntry> { it.pinned }.thenByDescending { it.createdAt }
            )
            else -> active.sortedWith(
                compareByDescending<VaultEntry> { it.pinned }.thenBy { it.site.lowercase() }
            )
        }
    }

    private fun refreshList() {
        adapter.submit(visibleEntries(), binding.searchInput.text?.toString().orEmpty())
        binding.emptyView.visibility =
            if (entries.none { !it.deleted }) View.VISIBLE else View.GONE
        updateAuditCard()
    }

    private fun updateAuditCard() {
        if (!prefs.auditEnabled) {
            binding.auditCard.visibility = View.GONE
            return
        }
        val active = entries.filter { !it.deleted }
        val weak = active.count { PasswordStrength.isWeak(it.password) }
        val reused = active.groupBy { it.password.lowercase() }
            .count { (_, v) -> v.size > 1 }
        binding.auditCard.visibility = View.VISIBLE
        binding.auditText.text = getString(R.string.audit_summary, weak, reused)
    }

    private fun togglePin(e: VaultEntry) {
        e.pinned = !e.pinned
        repo.save(entries)
        refreshList()
    }

    private fun copyToClipboard(value: String) {
        if (value.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PassVault", value))
        Snackbar.make(binding.mainView, R.string.copied, Snackbar.LENGTH_SHORT).show()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ cm.clearPrimaryClip() }, 30_000)
    }

    private fun showEntryDialog(existing: VaultEntry?) {
        val dialogBinding = DialogEntryBinding.inflate(LayoutInflater.from(this))
        dialogBinding.inputSite.setText(existing?.site)
        dialogBinding.inputUsername.setText(existing?.username)
        dialogBinding.inputPassword.setText(existing?.password)
        dialogBinding.inputTotp.setText(existing?.totpSecret)
        dialogBinding.inputNotes.setText(existing?.notes)

        val updateStrength = { p: String ->
            val s = PasswordStrength.score(p)
            val bar = dialogBinding.strengthBar as ProgressBar
            bar.progress = PasswordStrength.progress(s)
            bar.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(PasswordStrength.colorHex(s)))
            dialogBinding.strengthLabel.setText(PasswordStrength.labelRes(s))
        }
        dialogBinding.inputPassword.doAfterTextChanged { updateStrength(it?.toString().orEmpty()) }
        updateStrength(existing?.password.orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) getString(R.string.btn_add) else existing.site.ifBlank { getString(R.string.btn_add) })
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_generate, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            dialogBinding.inputPassword.setText(PasswordGenerator.generate())
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val site = dialogBinding.inputSite.text?.toString()?.trim().orEmpty()
            val pass = dialogBinding.inputPassword.text?.toString().orEmpty()
            if (site.isBlank() || pass.isBlank()) {
                Snackbar.make(dialogBinding.root, R.string.error_required_fields, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val totp = dialogBinding.inputTotp.text?.toString()?.trim().orEmpty()
            if (totp.isNotEmpty() && Totp.code(totp) == null) {
                Snackbar.make(dialogBinding.root, R.string.error_invalid_totp, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (existing == null) {
                entries.add(
                    VaultEntry(
                        site = site,
                        username = dialogBinding.inputUsername.text?.toString()?.trim().orEmpty(),
                        password = pass,
                        totpSecret = totp,
                        notes = dialogBinding.inputNotes.text?.toString()?.trim().orEmpty()
                    )
                )
            } else {
                existing.site = site
                existing.username = dialogBinding.inputUsername.text?.toString()?.trim().orEmpty()
                existing.password = pass
                existing.totpSecret = totp
                existing.notes = dialogBinding.inputNotes.text?.toString()?.trim().orEmpty()
            }
            repo.save(entries)
            refreshList()
            dialog.dismiss()
        }
    }

    private fun confirmDelete(entry: VaultEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.delete_confirm, entry.site))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                entry.deleted = true
                repo.save(entries)
                refreshList()
                Snackbar.make(binding.mainView, R.string.moved_to_trash, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) {
                        entry.deleted = false
                        repo.save(entries)
                        refreshList()
                    }
                    .show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val PIN_LENGTH = 6
        private const val MAX_ATTEMPTS = 5
        private const val COOLDOWN_MS = 30_000L
    }
}
