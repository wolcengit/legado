package io.legado.app.ui.opds

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.OpdsSource
import io.legado.app.databinding.ItemOpdsSourceBinding

class OpdsSourceAdapter(
    context: Context,
    private val callback: Callback
) : RecyclerAdapter<OpdsSource, ItemOpdsSourceBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemOpdsSourceBinding {
        return ItemOpdsSourceBinding.inflate(inflater, parent, false)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemOpdsSourceBinding) {
        binding.root.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { source ->
                callback.edit(source)
            }
        }
        binding.ivEdit.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { source ->
                callback.edit(source)
            }
        }
        binding.ivDelete.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { source ->
                callback.delete(source)
            }
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemOpdsSourceBinding,
        item: OpdsSource,
        payloads: MutableList<Any>
    ) {
        binding.tvName.text = item.sourceName
        binding.tvUrl.text = item.sourceUrl
    }

    interface Callback {
        fun edit(source: OpdsSource)
        fun delete(source: OpdsSource)
    }
}
