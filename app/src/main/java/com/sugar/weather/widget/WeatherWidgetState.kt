package com.sugar.weather.widget

import com.sugar.weather.data.UNKNOWN_TEXT
import com.sugar.weather.data.formatTemperatureC
import com.sugar.weather.data.localToday
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.wmoDesc
import java.time.Instant
import java.time.LocalDate

/**
 * 微件只保留绘制需要的数据，页面切换和动画调试不改变桌面内容。
 * @property cityId 当前首城的标识，供点击时回到对应天气页。
 * @property cityName 城市名称；无城市时由渲染层显示添加提示。
 * @property condition 实况天气描述。
 * @property temperature 当前温度，缺测时显示破折号。
 * @property highLow 城市当地今天的高低温，过期日报不能冒充今天。
 * @property weatherCode 实况天气码，用于选择微件背景。
 * @property isNight 实况昼夜标记。
 * @property fetchedAtMillis 最近一次成功获取时间，失败不得改写。
 * @property days 明天起连续三个日期，缺测日期保留空位。
 */
internal data class WeatherWidgetState(
    val cityId: String?,
    val cityName: String,
    val condition: String,
    val temperature: String,
    val highLow: String,
    val weatherCode: Int?,
    val isNight: Boolean,
    val fetchedAtMillis: Long?,
    val days: List<WidgetForecastDay>
) {
    /** 成功响应缺少实况和有效未来日报时，仍需显示重试入口而不是永久加载。 */
    val hasWeather: Boolean
        get() = weatherCode != null || days.any { it.forecast != null }

    companion object {
        /** 以真实时钟与城市时区筛选日报，离线跨日后也不会错标日期。 */
        fun from(city: City?, report: WeatherReport?, now: Instant = Instant.now()): WeatherWidgetState {
            // 城市排序或删除时可能收到交错快照，其他城市的报告不可借用。
            val weather = report?.takeIf { city != null && it.cityId == city.id }
            val current = weather?.current
            val today = weather?.localToday(now) ?: now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val byDate = weather?.daily.orEmpty().associateBy { it.date }
            return WeatherWidgetState(
                cityId = city?.id,
                cityName = city?.name.orEmpty(),
                condition = current?.let { wmoDesc(it.weatherCode) }.orEmpty(),
                temperature = current?.let { formatTemperatureC(it.temperatureC) } ?: UNKNOWN_TEXT,
                highLow = byDate[today]?.widgetHighLow() ?: UNKNOWN_TEXT,
                weatherCode = current?.weatherCode,
                isNight = current?.isNight ?: false,
                fetchedAtMillis = weather?.fetchedAtMillis,
                days = (1L..3L).map { offset ->
                    val date = today.plusDays(offset)
                    WidgetForecastDay(date = date, forecast = byDate[date])
                }
            )
        }
    }
}

/**
 * 日期槽独立于数据存在，服务缺一天时不把后天错标为明天。
 * @property date 城市当地的预报日期。
 * @property forecast 对应日报，缺测时为 null。
 */
internal data class WidgetForecastDay(val date: LocalDate, val forecast: DailyForecast?)

/** 微件用紧凑的双温度形式，所有数值仍复用应用的取整规则。 */
internal fun DailyForecast.widgetHighLow(): String =
    "${formatTemperatureC(tempMaxC)}/${formatTemperatureC(tempMinC)}"
