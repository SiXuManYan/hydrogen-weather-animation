package com.template.framework.ui.base

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.template.framework.ui.recyclerview.MutableDataAdapter

/** 单一 ViewBinding 适配器；可选同步 DiffUtil，保留旧类名及 BindingViewHolder 类型。 */
abstract class CommonAdapter<T, VB : ViewBinding>(diffCallback: DiffUtil.ItemCallback<T>? = null) :
    MutableDataAdapter<T, CommonAdapter<T, VB>.BindingViewHolder>(diffCallback) {

    private var currentHolder: BindingViewHolder? = null
    private var onItemClickListener: OnItemClickListener<T>? = null
    private var onItemLongClickListener: OnItemLongClickListener<T>? = null
    private var onItemChildClickListener: OnItemChildClickListener<T>? = null

    protected abstract fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB
    protected abstract fun onBind(binding: VB, item: T, position: Int, viewType: Int)

    /** 未处理的 payload 默认回退完整绑定，子类可只更新收藏图标或选中状态。 */
    protected open fun onBindPayloads(binding: VB, item: T, position: Int, viewType: Int, payloads: List<Any>) {
        currentHolder?.let { clearClicks(it); installItemClicks(it, true) }
        onBind(binding, item, position, viewType)
    }

    /** 回收时取消图片任务或清理资源，不在 Adapter 中长期持有页面。 */
    protected open fun onBindingRecycled(binding: VB) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        context = parent.context
        return BindingViewHolder(onCreateBinding(LayoutInflater.from(parent.context), parent, viewType), viewType)
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        if (position !in dataList.indices) return
        clearClicks(holder)
        installItemClicks(holder, true)
        bindHolder(holder) { onBind(holder.binding, dataList[position], position, holder.viewType) }
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else if (position in dataList.indices) {
            bindHolder(holder) {
                onBindPayloads(holder.binding, dataList[position], position, holder.viewType, payloads)
            }
        }
    }

    private fun bindHolder(holder: BindingViewHolder, bind: () -> Unit) {
        currentHolder = holder
        try { bind() } finally { currentHolder = null }
        installItemClicks(holder)
        holder.childClickViews.forEach { child ->
            child.setOnClickListener { view ->
                withCurrentItem(holder) { item, index -> onItemChildClickListener?.onItemChildClick(item, index, view, view.id) }
            }
        }
    }

    private fun installItemClicks(holder: BindingViewHolder, installDefaults: Boolean = false) {
        // 默认分派在业务绑定前安装，支持绑定后设置监听；业务仍可覆盖未注册的事件。
        if (installDefaults || onItemClickListener != null) {
            holder.itemView.setOnClickListener { view ->
                withCurrentItem(holder) { item, index -> onItemClickListener?.onItemClick(item, index, view) }
            }
        }
        if (installDefaults || onItemLongClickListener != null) {
            holder.itemView.setOnLongClickListener { view ->
                val listener = onItemLongClickListener ?: return@setOnLongClickListener false
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION || index !in dataList.indices) false else {
                    listener.onItemLongClick(dataList[index], index, view)
                    true
                }
            }
        }
    }

    private fun withCurrentItem(holder: BindingViewHolder, action: (T, Int) -> Unit) {
        // 插入、删除和拖动后，绑定时的位置已过时；ConcatAdapter 使用子适配器位置。
        val index = holder.bindingAdapterPosition
        if (index != RecyclerView.NO_POSITION && index in dataList.indices) action(dataList[index], index)
    }

    override fun onViewRecycled(holder: BindingViewHolder) {
        clearClicks(holder)
        onBindingRecycled(holder.binding)
        super.onViewRecycled(holder)
    }

    private fun clearClicks(holder: BindingViewHolder) {
        holder.childClickViews.forEach { it.setOnClickListener(null) }
        holder.childClickViews.clear()
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    /** 只在 onBind / onBindPayloads 中注册；重复视图不会重复添加。 */
    protected fun bindChildClickListener(vararg views: View) { currentHolder?.bindChildClickListener(*views) }

    fun setOnItemClickListener(listener: OnItemClickListener<T>?) { onItemClickListener = listener }
    fun setOnItemLongListener(listener: OnItemLongClickListener<T>?) { onItemLongClickListener = listener }
    fun setOnItemChildClickListener(listener: OnItemChildClickListener<T>?) { onItemChildClickListener = listener }

    inner class BindingViewHolder(val binding: VB, val viewType: Int) : RecyclerView.ViewHolder(binding.root) {
        val childClickViews: MutableList<View> = mutableListOf()
        fun bindChildClickListener(vararg views: View) {
            views.forEach { if (it !in childClickViews) childClickViews.add(it) }
        }
    }

    fun interface OnItemClickListener<T> { fun onItemClick(item: T, position: Int, v: View) }
    fun interface OnItemChildClickListener<T> { fun onItemChildClick(item: T, position: Int, view: View, viewId: Int) }
    fun interface OnItemLongClickListener<T> { fun onItemLongClick(item: T, position: Int, v: View) }
}
