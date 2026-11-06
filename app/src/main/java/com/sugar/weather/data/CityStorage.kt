package com.sugar.weather.data

import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import kotlinx.coroutines.flow.Flow

/** 城市与天气的持久化边界，便于独立验证删除和刷新之间的竞态。 */
interface CityStorage {
    /** 已持久化的城市列表。 */
    val cities: Flow<List<City>>

    /** 在一次事务内转换城市列表，清理已移除或坐标变化城市的磁盘天气；返回最终列表。 */
    suspend fun updateCities(transform: (List<City>) -> List<City>): List<City>

    /** 保存一份天气报告；城市存活检查由仓库与删除共用的互斥区保证。 */
    suspend fun saveWeather(cityId: String, report: WeatherReport)

    /** 加载启动预热所需的全部磁盘天气。 */
    suspend fun loadAllCachedWeather(): Map<String, WeatherReport>
}

/** 天气缓存归属于具体坐标及本次城市记录；改名和排序不影响已有天气。 */
internal fun City.hasSameWeatherIdentity(other: City): Boolean =
    id == other.id && latitude == other.latitude && longitude == other.longitude &&
        addedAtMillis == other.addedAtMillis
