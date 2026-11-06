package com.template.framework.ui.recyclerview

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/** 可变适配器的拖拽/滑动桥接；排序持久化与删除业务由调用方决定。 */
class AdapterTouchCallback(
    private val adapter: MutableDataAdapter<*, *>,
    dragDirections: Int = ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    swipeDirections: Int = 0,
    private val onMoved: (from: Int, to: Int) -> Unit = { _, _ -> },
    private val onSwiped: (position: Int) -> Unit = {},
    private val longPressDragEnabled: Boolean = true
) : ItemTouchHelper.SimpleCallback(dragDirections, swipeDirections) {
    override fun isLongPressDragEnabled(): Boolean = longPressDragEnabled

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // ConcatAdapter 的其他子适配器不可参与本列表的拖拽/滑动。
        return if (viewHolder.bindingAdapter === adapter) super.getMovementFlags(recyclerView, viewHolder) else 0
    }

    override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (source.bindingAdapter !== adapter || target.bindingAdapter !== adapter) return false
        val from = source.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (!adapter.moveItem(from, to)) return false
        onMoved(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (viewHolder.bindingAdapter !== adapter || position == RecyclerView.NO_POSITION) return
        // 默认恢复被滑走的视图；业务回调可删除数据或弹出确认。
        adapter.notifyItemChanged(position)
        onSwiped(position)
    }
}
