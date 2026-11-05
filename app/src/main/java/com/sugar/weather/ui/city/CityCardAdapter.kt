package com.sugar.weather.ui.city

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.R
import com.sugar.weather.databinding.ItemCityCardBinding

/**
 * 城市卡列表适配器(§6.5)。
 *
 * 故意不用 ListAdapter:拖拽排序需要 onMove 期间列表引用与 RecyclerView 已派发的
 * 移动动画严格同步,这里改用手动列表 + DiffUtil.calculateDiff(同步、主线程,
 * 城市数量小,开销可忽略),拖拽/左滑窗口内不会出现 diff 异步错位。
 *
 * @param onItemClick 普通点击切城，多选模式下切换选择。
 * @param onItemLongClick 长按卡片进入多选。
 * @param onDragStart 长按右侧手柄开始拖拽。
 * @param onOrderClick 点击手柄打开可访问的排序菜单。
 */
class CityCardAdapter(
    private val onItemClick: (cityId: String) -> Unit,
    private val onItemLongClick: (cityId: String) -> Unit,
    private val onDragStart: (ViewHolder) -> Unit,
    private val onOrderClick: (View, cityId: String) -> Unit
) : RecyclerView.Adapter<CityCardAdapter.ViewHolder>() {

    private val items = mutableListOf<CityCardUi>()

    /** 当前顺序(供 ItemTouchHelper 提交最终排序)。 */
    fun currentItems(): List<CityCardUi> = items.toList()

    /** 手势回调可能已失去位置，越界时返回空。 */
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

    /** 创建带选择描边及独立排序手柄的城市卡。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemCityCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    /** 绑定天气、选择和无障碍状态，手柄手势与卡片长按各自独立。 */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = items[position]
        holder.binding.backdrop.setScene(card.sceneType, card.isNight)
        holder.binding.imagePin.isVisible = card.isLocated
        holder.binding.textName.text = card.name
        val subtitle = listOfNotNull(card.tempText, card.descText).joinToString(separator = " ")
        holder.binding.textTempDesc.text = subtitle
        holder.binding.textTempDesc.isVisible = subtitle.isNotEmpty()
        holder.binding.root.setOnClickListener { onItemClick(card.cityId) }
        holder.binding.root.setOnLongClickListener {
            onItemLongClick(card.cityId)
            true
        }
        holder.binding.root.isEnabled = card.enabled
        holder.binding.root.isSelected = card.selected
        holder.binding.selectionOutline.isVisible = card.selected
        ViewCompat.setStateDescription(
            holder.binding.root,
            holder.itemView.context.getString(
                when {
                    card.isLocated -> R.string.city_located_locked
                    card.selected -> R.string.city_selected
                    card.editing -> R.string.city_not_selected
                    else -> R.string.city_long_press_hint
                }
            )
        )
        holder.binding.buttonReorder.apply {
            isVisible = !card.editing && !card.isLocated
            isEnabled = card.enabled
            contentDescription = context.getString(R.string.city_reorder_description, card.name)
            setOnLongClickListener {
                onDragStart(holder)
                true
            }
            setOnClickListener { onOrderClick(it, card.cityId) }
        }
    }

    override fun getItemCount(): Int = items.size

    /** @property binding 卡片视图绑定，供拖拽入口取得同一 ViewHolder。 */
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
