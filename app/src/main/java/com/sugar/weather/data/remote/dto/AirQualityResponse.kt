package com.sugar.weather.data.remote.dto

import androidx.annotation.Keep

/**
 * Open-Meteo `/v1/air-quality` 响应(DESIGN.md §1.1 实测:current={time, interval, pm2_5, us_aqi})。
 *
 * us_aqi v1 不进 UI(§5.1),仅保留在 DTO 备用。
 */
@Keep
data class AirQualityResponse(
    val current: AirCurrentBlock?
)

/** 保留空气质量实况字段，缺测值不作为有效零值。 */
@Keep
data class AirCurrentBlock(
    val time: String?,
    val pm2_5: Double?,
    val us_aqi: Double?
)
