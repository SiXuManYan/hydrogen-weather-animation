package com.sugar.weather.data.remote.dto

/**
 * Open-Meteo `/v1/search` 城市搜索响应(DESIGN.md §1.1/§6.4)。
 *
 * 零结果时服务端**省略** results 键,故可空。
 */
data class GeocodingResponse(
    val results: List<GeocodingPlace>?
)

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
