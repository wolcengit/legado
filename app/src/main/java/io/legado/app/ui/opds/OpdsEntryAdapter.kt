package io.legado.app.ui.opds

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isGone
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.OpdsEntry
import io.legado.app.databinding.ItemOpdsEntryBinding

class OpdsEntryAdapter(
    context: Context,
    private val callback: Callback
) : RecyclerAdapter<OpdsEntry, ItemOpdsEntryBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemOpdsEntryBinding {
        return ItemOpdsEntryBinding.inflate(inflater, parent, false)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemOpdsEntryBinding) {
        binding.root.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { entry ->
                callback.onEntryClick(entry)
            }
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemOpdsEntryBinding,
        item: OpdsEntry,
        payloads: MutableList<Any>
    ) {
        binding.tvTitle.text = item.title

        val subtitle = item.author ?: item.summary
        binding.tvSubtitle.isGone = subtitle.isNullOrBlank()
        binding.tvSubtitle.text = subtitle

        if (item.isNavigation) {
            binding.ivIcon.setImageResource(R.drawable.ic_folder)
        } else {
            binding.ivIcon.setImageResource(R.drawable.ic_book_has)
        }
    }

    interface Callback {
        fun onEntryClick(entry: OpdsEntry)
    }
}
