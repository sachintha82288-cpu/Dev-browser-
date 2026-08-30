package com.devbrowser.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devbrowser.app.browser.Tab
import com.devbrowser.app.databinding.ItemTabBinding

class TabStripAdapter(
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabStripAdapter.VH>() {

    private var tabs: List<Tab> = emptyList()
    private var selected: Int = -1

    fun submit(list: List<Tab>, selectedIndex: Int) {
        tabs = list.toList()
        selected = selectedIndex
        notifyDataSetChanged()
    }

    class VH(val binding: ItemTabBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = tabs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabs[position]
        holder.binding.tabTitle.text =
            (if (tab.isPrivate) "🕶 " else "") + tab.title.ifBlank { "New tab" }
        holder.binding.root.isSelected = position == selected
        holder.binding.root.setOnClickListener { onSelect(holder.bindingAdapterPosition) }
        holder.binding.tabClose.setOnClickListener { onClose(holder.bindingAdapterPosition) }
        holder.binding.root.layoutParams =
            (holder.binding.root.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginEnd = 8
            }
    }
}
