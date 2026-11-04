package com.sugar.weather.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 一个城市的完整天气报告(DESIGN.md §8.2/§9)。
 *
 * 由 [com.sugar.weather.data.WeatherMapper] 从 Open-Meteo 响应映射而来,
 * 持久化为 `weather_cache_{cityId}`(经 [com.sugar.weather.data.CityStore] 的 JSON 包装)。
 *
 * @property cityId 所属城市(对应 [City.id])
 * @property fetchedAtMillis 本次抓取完成时间,用于「刚刚更新 / X 分钟前更新」
 * @property timezone 城市本地时区名(timezone=auto 时由服务端返回,如 Asia/Shanghai)
 * @property observedAt 实况时刻的城市本地时间(来自 current.time);DESIGN.md 字段清单之外的附加字段,
 *   供逐小时切片([hourlyWindow])在时区换算上使用
 * @property current 实况;服务端缺关键响应块时为 null(离线展示「—」)
 * @property daily 逐日预报,forecast_days=16:今天 + 未来 15 天(『15 天天气预报』数据源,DESIGN.md §3)
 * @property hourly 逐小时预报(全量 384 点,裁剪由 [hourlyWindow] / ViewModel 完成)
 */
data class WeatherReport(
    val cityId: String,
    val fetchedAtMillis: Long,
    val timezone: String? = null,
    val observedAt: LocalDateTime? = null,
    val current: CurrentWeather? = null,
    val daily: List<DailyForecast> = emptyList(),
    val hourly: List<HourForecast> = emptyList()
)

/**
 * 实况天气。除 [visibilityKm]、[pm25] 外的字段为 API 必返字段,映射时对缺失值给保守默认。
 *
 * @property temperatureC 气温(°C)
 * @property apparentTemperatureC 体感温度(°C)
 * @property humidityPercent 相对湿度(%)
 * @property pressureHpa 海平面气压(hPa)
 * @property windKmh 风速(km/h,请求参数 wind_speed_unit=kmh)
 * @property windDirectionDeg 风向角度(0=北,顺时针)
 * @property visibilityKm 能见度(km,取当前小时的 hourly.visibility 米值换算);缺失为 null
 * @property weatherCode WMO weather_code(映射见 WeatherCodes)
 * @property isNight 夜间(current.is_day == 0)
 * @property pm25 PM2.5 浓度(μg/m³,来自 air-quality 接口;该接口失败时沿用旧值或 null)
 */
data class CurrentWeather(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Double,
    val pressureHpa: Double,
    val windKmh: Double,
    val windDirectionDeg: Double,
    val visibilityKm: Double?,
    val weatherCode: Int,
    val isNight: Boolean,
    val pm25: Double?
)

/**
 * 单日预报(今天 + 未来 15 天)。
 *
 * @property date 日期(城市本地)
 * @property weatherCode WMO weather_code
 * @property tempMaxC 最高气温(°C)
 * @property tempMinC 最低气温(°C)
 * @property precipProbabilityMaxPercent 当日最大降水概率(%);API 缺失为 null
 * @property uvIndexMax 当日紫外线指数最大值;夜间/极地等缺失为 null → 界面显示「—」
 * @property sunrise 日出时刻(城市本地);API 异常缺失时回退 00:00
 * @property sunset 日落时刻(城市本地);API 异常缺失时回退 23:59
 */
data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipProbabilityMaxPercent: Int?,
    val uvIndexMax: Double?,
    val sunrise: LocalTime,
    val sunset: LocalTime
)

/**
 * 单小时预报。
 *
 * @property time 时刻(城市本地,整点)
 * @property temperatureC 气温(°C)
 * @property weatherCode WMO weather_code
 */
data class HourForecast(
    val time: LocalDateTime,
    val temperatureC: Double,
    val weatherCode: Int
)

/**
 * 城市搜索结果(geocoding 接口单条,已按 population 优先排序,DESIGN.md §6.4)。
 *
 * @property name 地名(如「大连市」)
 * @property admin1 一级行政区(省/直辖市),可空
 * @property country 国家,可空
 * @property lat 纬度
 * @property lon 经度
 * @property population 人口;同名小地点为 null,排序时排在有值条目之后
 */
data class CitySearchResult(
    val name: String,
    val admin1: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
    val population: Long?
)
