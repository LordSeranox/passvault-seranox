package com.vault.pass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.vault.pass.databinding.ActivityMainBinding
import com.vault.pass.databinding.DialogEntryBinding
import com.vault.pass.storage.PinManager
import com.vault.pass.storage.VaultRepository
import com.vault.pass.ui.EntryAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: VaultRepository
    private lateinit var pinManager: PinManager
    private lateinit var adapter: EntryAdapter
    private var entries = mutableListOf<VaultEntry>()
    private var unlocked = false
    private var failedAttempts = 0
    private var cooldownUntil = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = VaultRepository(this)
        pinManager = PinManager(this)
        adapter = EntryAdapter(
            entries,
            onCopyUser = { copyToClipboard(getString(R.string.app_name), it.username) },
            onCopyPass = { copyToClipboard(getString(R.string.app_name), it.password) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        binding.btnUnlock.setOnClickListener { onSubmitPin() }
        binding.fabAdd.setOnClickListener { showEntryDialog(null) }
        binding.pinInput.doAfterTextChanged { binding.txtPinError.text = "" }
        binding.pinConfirmInput.doAfterTextChanged { binding.txtPinError.text = "" }

        if (pinManager.hasPin()) {
            binding.pinConfirmLayout.visibility = View.GONE
            binding.unlockSubtitle.setText(R.string.pin_enter_subtitle)
            binding.btnUnlock.setText(R.string.btn_unlock)
        } else {
            binding.unlockSubtitle.setText(R.string.pin_create_subtitle)
            binding.btnUnlock.setText(R.string.btn_create)
        }
    }

    override fun onStop() {
        super.onStop()
        lock()
    }

    private fun onSubmitPin() {
        if (System.currentTimeMillis() < cooldownUntil) {
            val sec = ((cooldownUntil - System.currentTimeMillis()) / 1000).toInt() + 1
            binding.txtPinError.text = getString(R.string.pin_cooldown, sec)
            return
        }
        val pin = binding.pinInput.text?.toString().orEmpty()

        if (pin.length != PIN_LENGTH || !pin.all { it.isDigit() }) {
            binding.txtPinError.setText(R.string.pin_invalid_format)
            return
        }

        if (pinManager.hasPin()) {
            if (pinManager.verify(pin)) {
                failedAttempts = 0
                unlock()
            } else {
                failedAttempts++
                binding.pinInput.setText("")
                if (failedAttempts >= MAX_ATTEMPTS) {
                    cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                    failedAttempts = 0
                    binding.txtPinError.setText(R.string.pin_cooldown_start)
                } else {
                    binding.txtPinError.setText(R.string.pin_wrong)
                }
            }
        } else {
            val confirm = binding.pinConfirmInput.text?.toString().orEmpty()
            if (confirm != pin) {
                binding.txtPinError.setText(R.string.pin_mismatch)
                return
            }
            pinManager.setPin(pin)
            unlock()
        }
    }

    private fun unlock() {
        unlocked = true
        entries = repo.load()
        adapter.submit(entries)
        binding.lockView.visibility = View.GONE
        binding.mainView.visibility = View.VISIBLE
        binding.emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun lock() {
        if (!unlocked) return
        unlocked = false
        entries.clear()
        adapter.submit(entries)
        binding.pinInput.setText("")
        binding.pinConfirmInput.setText("")
        binding.mainView.visibility = View.GONE
        binding.lockView.visibility = View.VISIBLE
    }

    private fun copyToClipboard(label: String, value: String) {
        if (value.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ cm.clearPrimaryClip() }, 30_000)
    }

    private fun showEntryDialog(existing: VaultEntry?) {
        val dialogBinding = DialogEntryBinding.inflate(LayoutInflater.from(this))
        dialogBinding.inputSite.setText(existing?.site)
        dialogBinding.inputUsername.setText(existing?.username)
        dialogBinding.inputPassword.setText(existing?.password)
        dialogBinding.inputNotes.setText(existing?.notes)

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
            if (existing == null) {
                entries.add(
                    VaultEntry(
                        site = site,
                        username = dialogBinding.inputUsername.text?.toString()?.trim().orEmpty(),
                        password = pass,
                        notes = dialogBinding.inputNotes.text?.toString()?.trim().orEmpty()
                    )
                )
            } else {
                existing.site = site
                existing.username = dialogBinding.inputUsername.text?.toString()?.trim().orEmpty()
                existing.password = pass
                existing.notes = dialogBinding.inputNotes.text?.toString()?.trim().orEmpty()
            }
            repo.save(entries)
            adapter.submit(entries)
            binding.emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            dialog.dismiss()
        }
    }

    private fun confirmDelete(entry: VaultEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.delete_confirm, entry.site))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                entries.removeAll { it.id == entry.id }
                repo.save(entries)
                adapter.submit(entries)
                binding.emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
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
