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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.vault.pass.databinding.ActivityMainBinding
import com.vault.pass.databinding.DialogEntryBinding
import com.vault.pass.storage.VaultRepository
import com.vault.pass.ui.EntryAdapter
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: VaultRepository
    private lateinit var adapter: EntryAdapter
    private var entries = mutableListOf<VaultEntry>()
    private var unlocked = false
    private val handler = Handler(Looper.getMainLooper())
    private var lockAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = VaultRepository(this)
        adapter = EntryAdapter(
            entries,
            onCopyUser = { copyToClipboard(getString(R.string.app_name), it.username) },
            onCopyPass = { copyToClipboard(getString(R.string.app_name), it.password) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        binding.btnUnlock.setOnClickListener { showBiometricPrompt() }
        binding.fabAdd.setOnClickListener { showEntryDialog(null) }

        if (canBiometric()) showBiometricPrompt() else {
            binding.lockView.visibility = View.VISIBLE
            Toast.makeText(this, R.string.biometric_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        lock()
    }

    override fun onUserLeaveHint() {
        lockAt = System.currentTimeMillis()
    }

    private fun canBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    }
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(info)
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
                Snackbar.make(dialogBinding.root, "Site et mot de passe requis", Snackbar.LENGTH_SHORT).show()
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
            .setMessage("Supprimer « ${entry.site} » ?")
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
}
