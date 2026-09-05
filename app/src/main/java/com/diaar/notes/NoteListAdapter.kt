package com.diaar.notes

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NoteListAdapter(
    private val files: List<Pair<String, Uri>>, // display name to uri
    private val onPick: (Uri) -> Unit
) : RecyclerView.Adapter<NoteListAdapter.VH>() {

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context).inflate(
            android.R.layout.simple_list_item_1, parent, false
        ) as TextView
        tv.setTextColor(0xFFDFCBC9.toInt())
        tv.setBackgroundResource(R.drawable.ripple_toolbar_item)
        tv.setPadding(28, 28, 28, 28)
        return VH(tv)
    }

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, uri) = files[position]
        holder.text.text = name
        holder.text.setOnClickListener { onPick(uri) }
    }
}
