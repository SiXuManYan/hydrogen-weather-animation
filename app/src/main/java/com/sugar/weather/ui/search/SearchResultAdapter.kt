package com.sugar.weather.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.databinding.ItemSearchResultBinding

/** 搜索结果列表适配器(§6.4):行 = name · admin1 · country;纯绑定。 */
class SearchResultAdapter(
    private val onItemClick: (CitySearchResultUi) -> Unit
) : ListAdapter<CitySearchResultUi, SearchResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.textName.text = row.name
        holder.binding.textSub.text =
            listOfNotNull(row.admin1, row.country).filter { it.isNotBlank() }.joinToString(" · ")
        holder.binding.root.setOnClickListener { onItemClick(row) }
    }

    class ViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CitySearchResultUi>() {
            override fun areItemsTheSame(
                oldItem: CitySearchResultUi,
                newItem: CitySearchResultUi
            ): Boolean = oldItem.name == newItem.name && oldItem.lat == newItem.lat &&
                oldItem.lon == newItem.lon

            override fun areContentsTheSame(
                oldItem: CitySearchResultUi,
                newItem: CitySearchResultUi
            ): Boolean = oldItem == newItem
        }
    }
}
