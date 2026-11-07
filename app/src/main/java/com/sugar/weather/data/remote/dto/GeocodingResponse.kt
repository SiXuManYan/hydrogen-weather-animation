package com.sugar.weather.data.remote.dto

import androidx.annotation.Keep

/**
 * Open-Meteo `/v1/search` 城市搜索响应(DESIGN.md §1.1/§6.4)。
 *
 * 零结果时服务端**省略** results 键,故可空。
 */
@Keep
data class GeocodingResponse(
    val results: List<GeocodingPlace>?
)

/** 保留选城接口的坐标和地名字段，缺失内容由映射层处理。 */
@Keep
data class GeocodingPlace(
    val id: Long?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val admin1: String?,
    val country: String?,
    val population: Long?,
    val timezone: String?
)
