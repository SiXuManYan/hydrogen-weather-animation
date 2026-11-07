package com.sugar.weather.data.model

import androidx.annotation.Keep

/**
 * 城市条目(DESIGN.md §2.2)。
 *
 * 由 [com.sugar.weather.data.CityStore] 以 JSON 形式持久化到 DataStore;
 * `id` 用 UUID 生成一次并随对象持久化,之后不再变化。
 *
 * @property id 持久化唯一标识(UUID)
 * @property name 展示名;定位城市反地理失败时为「我的位置」
 * @property latitude 纬度(Open-Meteo 查询用)
 * @property longitude 经度
 * @property isLocated 是否为定位城市;定位城市固定 displayOrder=0,不可删除、不参与拖拽
 * @property displayOrder 列表顺序,由 Repository 在每次写盘时按列表下标归一化
 * @property addedAtMillis 添加时间戳(用于「最近添加」类排序,当前仅记录)
 */
@Keep
data class City(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isLocated: Boolean = false,
    val displayOrder: Int = 0,
    val addedAtMillis: Long = System.currentTimeMillis()
)
