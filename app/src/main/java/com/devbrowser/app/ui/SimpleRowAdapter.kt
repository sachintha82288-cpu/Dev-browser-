package com.devbrowser.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devbrowser.app.databinding.ItemRowBinding

data class Row(val title: String, val subtitle: String, val payload: Any? = null)

class SimpleRowAdapter(
    private val onClick: (Row) -> Unit = {},
    private val onLongClick: (Row) -> Unit = {}
) : RecyclerView.Adapter<SimpleRowAdapter.VH>() {

    private val rows = mutableListOf<Row>()

    fun submit(list: List<Row>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.binding.title.text = row.title
        holder.binding.subtitle.text = row.subtitle
        holder.binding.root.setOnClickListener { onClick(row) }
        holder.binding.root.setOnLongClickListener { onLongClick(row); true }
    }
}
