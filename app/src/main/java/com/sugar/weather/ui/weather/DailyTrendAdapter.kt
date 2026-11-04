package com.sugar.weather.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.databinding.ItemDailyForecastBinding

/**
 * 6 天日报条适配器(§6.2 第 3 条):星期(今天)/日期/图标/天气文字。
 * 由 GridLayoutManager(spanCount=6) 等分宽度,列中心与 WeatherTrendView 曲线点对齐。
 */
class DailyTrendAdapter : ListAdapter<DailyRowUi, DailyTrendAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemDailyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.textWeek.text = row.weekText
        holder.binding.textDate.text = row.dateText
        holder.binding.imageIcon.setImageResource(row.iconRes)
        holder.binding.textDesc.text = row.desc
    }

    class ViewHolder(val binding: ItemDailyForecastBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DailyRowUi>() {
            override fun areItemsTheSame(oldItem: DailyRowUi, newItem: DailyRowUi): Boolean =
                oldItem.dateText == newItem.dateText && oldItem.weekText == newItem.weekText

            override fun areContentsTheSame(oldItem: DailyRowUi, newItem: DailyRowUi): Boolean =
                oldItem == newItem
        }
    }
}
