package com.sugar.weather.data.remote.dto

/**
 * Open-Meteo `/v1/air-quality` 响应(DESIGN.md §1.1 实测:current={time, interval, pm2_5, us_aqi})。
 *
 * us_aqi v1 不进 UI(§5.1),仅保留在 DTO 备用。
 */
data class AirQualityResponse(
    val current: AirCurrentBlock?
)

data class AirCurrentBlock(
    val time: String?,
    val pm2_5: Double?,
    val us_aqi: Double?
)
