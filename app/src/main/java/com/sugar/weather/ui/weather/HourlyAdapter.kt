package com.sugar.weather.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.databinding.ItemHourForecastBinding

/**
 * 逐小时条适配器(§6.2 第 5 条):时间/图标/温度,固定 64dp 列宽;
 * 日出/日落节点与普通时列同构,由 ViewModel 生成后直接绑定。
 */
class HourlyAdapter : ListAdapter<HourRowUi, HourlyAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemHourForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.textTime.text = row.timeText
        holder.binding.imageIcon.setImageResource(row.iconRes)
        holder.binding.textTemp.text = row.tempText
    }

    class ViewHolder(val binding: ItemHourForecastBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<HourRowUi>() {
            override fun areItemsTheSame(oldItem: HourRowUi, newItem: HourRowUi): Boolean =
                oldItem.timeText == newItem.timeText

            override fun areContentsTheSame(oldItem: HourRowUi, newItem: HourRowUi): Boolean =
                oldItem == newItem
        }
    }
}
