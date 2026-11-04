package com.vault.pass.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vault.pass.R
import com.vault.pass.Totp
import com.vault.pass.VaultEntry
import com.vault.pass.databinding.ItemEntryBinding
import kotlin.math.abs

class EntryAdapter(
    private var entries: List<VaultEntry>,
    private val onCopyUser: (VaultEntry) -> Unit,
    private val onCopyPass: (VaultEntry) -> Unit,
    private val onCopyTotp: (String) -> Unit,
    private val onEdit: (VaultEntry) -> Unit,
    private val onDelete: (VaultEntry) -> Unit,
    private val onTogglePin: (VaultEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    private val revealed = mutableSetOf<String>()
    private val avatarColors = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
        R.color.avatar_4, R.color.avatar_5, R.color.avatar_6
    )

    fun submit(list: List<VaultEntry>, query: String = "") {
        val q = query.trim().lowercase()
        entries = list.filter {
            q.isBlank() || it.site.lowercase().contains(q) ||
                it.username.lowercase().contains(q)
        }
        revealed.retainAll(list.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(entries[position])

    inner class VH(private val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: VaultEntry) {
            b.txtSite.text = e.site.ifBlank { "(sans nom)" }
            b.txtUsername.text = e.username.ifBlank { "—" }
            b.txtPassword.text =
                if (e.id in revealed) e.password
                else "•".repeat(e.password.length.coerceAtMost(16))

            val letter = e.site.trim().firstOrNull()?.uppercaseChar() ?: '?'
            b.avatarLetter.text = letter.toString()
            b.avatarLetter.setBackgroundColor(
                ContextCompat.getColor(
                    b.root.context,
                    avatarColors[abs(e.site.hashCode()) % avatarColors.size]
                )
            )

            if (e.totpSecret.isNotBlank()) {
                val code = Totp.code(e.totpSecret)
                if (code == null) {
                    b.txtTotp.visibility = View.GONE
                } else {
                    b.txtTotp.visibility = View.VISIBLE
                    b.txtTotp.text = "$code · ${Totp.remainingSeconds()}s"
                    b.txtTotp.setOnClickListener { onCopyTotp(code) }
                }
            } else {
                b.txtTotp.visibility = View.GONE
            }

            b.btnPin.setColorFilter(
                if (e.pinned) ContextCompat.getColor(b.root.context, R.color.vault_accent)
                else ContextCompat.getColor(b.root.context, R.color.vault_text_secondary)
            )
            b.btnPin.setOnClickListener { onTogglePin(e) }
            b.btnCopyUser.setOnClickListener { onCopyUser(e) }
            b.btnCopyPass.setOnClickListener { onCopyPass(e) }
            b.btnEdit.setOnClickListener { onEdit(e) }
            b.btnDelete.setOnClickListener { onDelete(e) }
            b.btnReveal.setOnClickListener {
                if (!revealed.remove(e.id)) revealed.add(e.id)
                notifyItemChanged(bindingAdapterPosition)
            }
        }
    }
}
