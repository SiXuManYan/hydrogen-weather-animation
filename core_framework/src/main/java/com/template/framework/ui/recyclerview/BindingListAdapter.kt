package com.template.framework.ui.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/** 异步差分 ViewBinding 列表；提交不可变条目，不与可变列表或拖拽数据源混用。 */
abstract class BindingListAdapter<T, VB : ViewBinding>(diffCallback: DiffUtil.ItemCallback<T>) :
    ListAdapter<T, BindingListAdapter.BindingViewHolder<VB>>(diffCallback) {

    var onItemClick: ((item: T, position: Int, view: View) -> Unit)? = null
    var onItemLongClick: ((item: T, position: Int, view: View) -> Boolean)? = null
    private var currentHolder: BindingViewHolder<VB>? = null

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    protected abstract fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB
    protected abstract fun onBind(binding: VB, item: T, position: Int)

    /** 多布局可重写 getItemViewType 并在 onCreateBinding 分发 ViewBinding。 */
    protected open fun onBindPayloads(binding: VB, item: T, position: Int, payloads: List<Any>) {
        currentHolder?.let { clearClicks(it); installItemClicks(it, true) }
        onBind(binding, item, position)
    }

    protected open fun onBindingRecycled(binding: VB) {}

    @MainThread
    override fun submitList(list: List<T>?) { super.submitList(list?.toList()) }
    @MainThread
    override fun submitList(list: List<T>?, commitCallback: Runnable?) {
        super.submitList(list?.toList(), commitCallback)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<VB> =
        BindingViewHolder(onCreateBinding(LayoutInflater.from(parent.context), parent, viewType))

    override fun onBindViewHolder(holder: BindingViewHolder<VB>, position: Int) {
        clearClicks(holder)
        installItemClicks(holder, true)
        bindHolder(holder) { onBind(holder.binding, getItem(position), position) }
    }

    override fun onBindViewHolder(holder: BindingViewHolder<VB>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position)
        else bindHolder(holder) { onBindPayloads(holder.binding, getItem(position), position, payloads) }
    }

    private fun bindHolder(holder: BindingViewHolder<VB>, bind: () -> Unit) {
        currentHolder = holder
        try { bind() } finally { currentHolder = null }
        installItemClicks(holder)
    }

    private fun installItemClicks(holder: BindingViewHolder<VB>, installDefaults: Boolean = false) {
        // 先安装动态默认回调，再允许绑定钩子覆盖；已配置回调仍由框架统一分派。
        if (installDefaults || onItemClick != null) holder.itemView.setOnClickListener { view ->
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION && index in currentList.indices) {
                onItemClick?.invoke(getItem(index), index, view)
            }
        }
        if (installDefaults || onItemLongClick != null) holder.itemView.setOnLongClickListener { view ->
            val index = holder.bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION || index !in currentList.indices) false
            else onItemLongClick?.invoke(getItem(index), index, view) ?: false
        }
    }

    /** 在绑定钩子中注册子视图，事件触发时读取最新条目与位置。 */
    protected fun bindChildClick(view: View, action: (item: T, position: Int) -> Unit) {
        val holder = currentHolder ?: return
        if (view !in holder.childClickViews) holder.childClickViews.add(view)
        view.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION && index in currentList.indices) action(getItem(index), index)
        }
    }

    override fun onViewRecycled(holder: BindingViewHolder<VB>) {
        clearClicks(holder)
        onBindingRecycled(holder.binding)
        super.onViewRecycled(holder)
    }

    private fun clearClicks(holder: BindingViewHolder<VB>) {
        holder.childClickViews.forEach { it.setOnClickListener(null) }
        holder.childClickViews.clear()
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    class BindingViewHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root) {
        internal val childClickViews = mutableListOf<View>()
    }
}
