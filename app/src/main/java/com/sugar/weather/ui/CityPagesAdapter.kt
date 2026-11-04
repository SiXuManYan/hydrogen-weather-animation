package com.sugar.weather.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sugar.weather.data.model.City
import com.sugar.weather.ui.weather.WeatherFragment

/**
 * 主页多城市页适配器(DESIGN.md §6.2/D10):每城市一个 WeatherFragment。
 *
 * 以 cityId 的 hash 作为稳定 itemId:城市增删/重排时,已存在的页面 Fragment 被
 * FragmentStateAdapter 保留复用,只有消失城市的页面被销毁,滑动手感不抖动。
 */
class CityPagesAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val items = mutableListOf<City>()

    /** 当前位置对应的城市 id;列表为空或越界返回 null(供城市身份保持的切页同步)。 */
    fun cityIdAt(position: Int): String? = items.getOrNull(position)?.id

    fun submit(cities: List<City>) {
        if (items.map { it.id } == cities.map { it.id }) return
        items.clear()
        items.addAll(cities)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment =
        WeatherFragment.newInstance(items[position].id)

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun containsItem(itemId: Long): Boolean =
        items.any { it.id.hashCode().toLong() == itemId }
}
