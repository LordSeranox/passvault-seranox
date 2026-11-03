package com.vault.pass.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vault.pass.VaultEntry
import com.vault.pass.databinding.ItemEntryBinding

class EntryAdapter(
    private var entries: List<VaultEntry>,
    private val onCopyUser: (VaultEntry) -> Unit,
    private val onCopyPass: (VaultEntry) -> Unit,
    private val onDelete: (VaultEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    private val revealed = mutableSetOf<String>()

    fun submit(list: List<VaultEntry>) {
        entries = list
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
            b.txtPassword.text = if (e.id in revealed) e.password else "•".repeat(e.password.length.coerceAtMost(16))
            b.btnCopyUser.setOnClickListener { onCopyUser(e) }
            b.btnCopyPass.setOnClickListener { onCopyPass(e) }
            b.btnDelete.setOnClickListener { onDelete(e) }
            b.btnReveal.setOnClickListener {
                if (!revealed.remove(e.id)) revealed.add(e.id)
                notifyItemChanged(bindingAdapterPosition)
            }
        }
    }
}
