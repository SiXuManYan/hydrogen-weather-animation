package com.template.framework.ui.recyclerview

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * 同步可变列表基类，适合小列表和拖拽；大列表使用 BindingListAdapter。
 * @param diffCallback 只比较非空条目；空占位由基类统一处理。
 */
abstract class MutableDataAdapter<T, VH : RecyclerView.ViewHolder>(
    private val diffCallback: DiffUtil.ItemCallback<T>? = null
) : RecyclerView.Adapter<VH>() {
    protected var context: Context? = null
    protected val dataList: MutableList<T> = mutableListOf()

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    override fun getItemCount(): Int = dataList.size

    /** 先复制快照并处理空条目，避免同列表误清空及向 DiffUtil 非空接口传入 null。 */
    @MainThread
    @SuppressLint("NotifyDataSetChanged")
    fun setNewDataList(list: List<T>?) {
        val next = list?.toList().orEmpty()
        val old = dataList.toList()
        val callback = diffCallback
        val diff = callback?.let {
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = old.size
                override fun getNewListSize(): Int = next.size
                /** 与 ListAdapter 一致：两个空条目相同，单侧为空时不调用业务比较器。 */
                override fun areItemsTheSame(a: Int, b: Int): Boolean {
                    val previous = old[a]
                    val current = next[b]
                    return if (previous == null || current == null) previous == current
                    else it.areItemsTheSame(previous, current)
                }

                /** Kotlin 新空值约束要求比较器只接收已确认非空的条目。 */
                override fun areContentsTheSame(a: Int, b: Int): Boolean {
                    val previous = old[a]
                    val current = next[b]
                    return if (previous == null || current == null) previous == current
                    else it.areContentsTheSame(previous, current)
                }

                /** 空条目变化由完整绑定处理，非空内容变化才委托 payload。 */
                override fun getChangePayload(a: Int, b: Int): Any? {
                    val previous = old[a]
                    val current = next[b]
                    return if (previous == null || current == null) null
                    else it.getChangePayload(previous, current)
                }
            })
        }
        dataList.clear()
        dataList.addAll(next)
        if (diff != null) diff.dispatchUpdatesTo(this) else notifyDataSetChanged()
    }

    @MainThread
    fun addDataList(list: List<T>?) {
        val next = list?.toList().orEmpty()
        if (next.isEmpty()) return
        val start = dataList.size
        dataList.addAll(next)
        notifyItemRangeInserted(start, next.size)
    }

    @MainThread
    fun addItem(item: T) = insertItem(dataList.size, item)

    @MainThread
    fun insertItem(position: Int, item: T) {
        if (position !in 0..dataList.size) return
        dataList.add(position, item)
        notifyItemInserted(position)
    }

    @MainThread
    fun deleteItem(position: Int) {
        if (position !in dataList.indices) return
        dataList.removeAt(position)
        notifyItemRemoved(position)
    }

    @MainThread
    fun updateItem(position: Int, item: T, payload: Any? = null) {
        if (position !in dataList.indices) return
        dataList[position] = item
        notifyItemChanged(position, payload)
    }

    /** 同步移动数据再通知 RecyclerView，供 ItemTouchHelper 使用。 */
    @MainThread
    fun moveItem(from: Int, to: Int): Boolean {
        if (from !in dataList.indices || to !in dataList.indices || from == to) return false
        dataList.add(to, dataList.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }

    @MainThread
    fun clearAllData() {
        val count = dataList.size
        if (count == 0) return
        dataList.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun getItemOrNull(position: Int): T? = dataList.getOrNull(position)
    fun getAllData(): List<T> = dataList.toList()
    fun isEmpty(): Boolean = dataList.isEmpty()
    fun isNotEmpty(): Boolean = dataList.isNotEmpty()
    fun getString(@StringRes resId: Int): String = context?.getString(resId) ?: ""

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        context = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
