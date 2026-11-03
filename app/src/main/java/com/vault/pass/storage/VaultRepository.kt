package com.vault.pass.storage

import android.content.Context
import com.vault.pass.VaultEntry
import com.vault.pass.crypto.VaultCrypto
import com.vault.pass.vaultFromJson
import com.vault.pass.toJson
import java.io.File

class VaultRepository(context: Context) {
    private val file: File = File(context.filesDir, "vault.bin")

    fun load(): MutableList<VaultEntry> = try {
        if (file.exists()) vaultFromJson(String(VaultCrypto.decrypt(file.readBytes()), Charsets.UTF_8))
        else mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun save(entries: List<VaultEntry>) {
        val plain = entries.toJson().toByteArray(Charsets.UTF_8)
        file.writeBytes(VaultCrypto.encrypt(plain))
    }
}
