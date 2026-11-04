package com.sugar.weather.ui.city

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.databinding.ItemCityCardBinding

/**
 * 城市卡列表适配器(§6.5)。
 *
 * 故意不用 ListAdapter:拖拽排序需要 onMove 期间列表引用与 RecyclerView 已派发的
 * 移动动画严格同步,这里改用手动列表 + DiffUtil.calculateDiff(同步、主线程,
 * 城市数量小,开销可忽略),拖拽/左滑窗口内不会出现 diff 异步错位。
 */
class CityCardAdapter(
    private val onItemClick: (cityId: String) -> Unit
) : RecyclerView.Adapter<CityCardAdapter.ViewHolder>() {

    private val items = mutableListOf<CityCardUi>()

    /** 当前顺序(供 ItemTouchHelper 提交最终排序)。 */
    fun currentItems(): List<CityCardUi> = items

    fun itemAt(position: Int): CityCardUi? = items.getOrNull(position)

    /** 外部数据回流:与当前列表做全量 diff,增删/重排/内容变化都按最小动作派发。 */
    fun submit(cards: List<CityCardUi>) {
        val old = items.toList()
        if (old == cards) return
        items.clear()
        items.addAll(cards)
        DiffUtil.calculateDiff(DiffCallback(old, cards)).dispatchUpdatesTo(this)
    }

    /** 拖拽换位:同步修改内部列表并派发 move 动画(顺序最终经 ViewModel 写回后回流校准)。 */
    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    /** 左滑删除的本地回弹(如目标不可删除):仅重置该位置视图状态。 */
    fun revertAt(position: Int) {
        if (position in items.indices) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemCityCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = items[position]
        holder.binding.backdrop.setScene(card.sceneType, card.isNight)
        holder.binding.imagePin.isVisible = card.isLocated
        holder.binding.textName.text = card.name
        val subtitle = listOfNotNull(card.tempText, card.descText).joinToString(separator = "")
        holder.binding.textTempDesc.text = subtitle
        holder.binding.textTempDesc.isVisible = subtitle.isNotEmpty()
        holder.binding.root.setOnClickListener { onItemClick(card.cityId) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemCityCardBinding) : RecyclerView.ViewHolder(binding.root)

    private class DiffCallback(
        private val old: List<CityCardUi>,
        private val new: List<CityCardUi>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
            old[oldPos].cityId == new[newPos].cityId

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
            old[oldPos] == new[newPos]
    }
}
