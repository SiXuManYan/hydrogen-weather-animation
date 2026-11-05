package com.sugar.weather.ui.city

/**
 * 独立于列表位置的多选状态，拖拽和天气刷新不会改变选中的城市。
 *
 * @property editing 是否处于选择模式；取消最后一项后仍保留模式，供用户继续选择。
 * @property cityIds 已选择的手动城市标识，不接受定位城市或过期标识。
 */
data class CitySelection(
    val editing: Boolean = false,
    val cityIds: Set<String> = emptySet()
) {
    /** 点击或长按可删除城市时切换选中状态，同时清理已经失效的选择。 */
    fun toggle(cityId: String, eligibleIds: Set<String>): CitySelection {
        val valid = cityIds.intersect(eligibleIds)
        if (cityId !in eligibleIds) return copy(cityIds = valid)
        return CitySelection(
            editing = true,
            cityIds = if (cityId in valid) valid - cityId else valid + cityId
        )
    }

    /** 列表变化后只保留当前仍可删除的城市。 */
    fun reconcile(eligibleIds: Set<String>): CitySelection =
        copy(cityIds = cityIds.intersect(eligibleIds))
}
