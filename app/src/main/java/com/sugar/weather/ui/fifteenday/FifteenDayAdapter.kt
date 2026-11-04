package com.sugar.weather.ui.fifteenday

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.databinding.ItemFifteenDayRowBinding

/** 15 天详情页列表适配器(§8.2):纯绑定,文本全部由 ViewModel 格式化完成。 */
class FifteenDayAdapter : ListAdapter<FifteenDayRowUi, FifteenDayAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemFifteenDayRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.textWeek.text = row.weekText
        holder.binding.textDate.text = row.dateText
        holder.binding.imageIcon.setImageResource(row.iconRes)
        holder.binding.textDesc.text = row.desc
        holder.binding.textPrecip.text = row.precipProbText.orEmpty()
        holder.binding.textPrecip.isVisible = row.precipProbText != null
        holder.binding.textHigh.text = row.tempHighText
        holder.binding.textLow.text = row.tempLowText
    }

    class ViewHolder(val binding: ItemFifteenDayRowBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<FifteenDayRowUi>() {
            override fun areItemsTheSame(oldItem: FifteenDayRowUi, newItem: FifteenDayRowUi): Boolean =
                oldItem.dateText == newItem.dateText && oldItem.weekText == newItem.weekText

            override fun areContentsTheSame(oldItem: FifteenDayRowUi, newItem: FifteenDayRowUi): Boolean =
                oldItem == newItem
        }
    }
}
