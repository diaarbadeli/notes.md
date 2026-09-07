package com.diaar.notes

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Curated, pinnable recents list shown in the Load menu (not a live folder scan). */
class NoteListAdapter(
    private val entries: List<RecentEntry>,
    private val onPick: (RecentEntry) -> Unit,
    private val onTogglePin: (RecentEntry) -> Unit,
    private val onRemove: (RecentEntry) -> Unit
) : RecyclerView.Adapter<NoteListAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val pinBtn: ImageButton = view.findViewById(R.id.pinBtn)
        val removeBtn: ImageButton = view.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_note, parent, false)
        return VH(v)
    }

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        holder.name.text = entry.name.removeSuffix(".md")
        holder.name.setOnClickListener { onPick(entry) }
        holder.pinBtn.setImageResource(if (entry.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
        holder.pinBtn.setOnClickListener { onTogglePin(entry) }
        holder.removeBtn.visibility = if (entry.pinned) android.view.View.INVISIBLE else android.view.View.VISIBLE
        holder.removeBtn.setOnClickListener { onRemove(entry) }
    }
}
