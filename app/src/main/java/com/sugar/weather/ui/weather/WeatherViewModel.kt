package com.sugar.weather.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sugar.weather.WeatherApplication
import com.sugar.weather.R
import com.sugar.weather.data.UNIT_HUMIDITY
import com.sugar.weather.data.UNIT_PRESSURE
import com.sugar.weather.data.UNIT_TEMP
import com.sugar.weather.data.UNIT_VISIBILITY
import com.sugar.weather.data.UNIT_WIND_SPEED
import com.sugar.weather.data.UNKNOWN_TEXT
import com.sugar.weather.data.aqiLevelText
import com.sugar.weather.data.formatHighLowC
import com.sugar.weather.data.formatOneDecimal
import com.sugar.weather.data.formatPm25
import com.sugar.weather.data.formatRoundInt
import com.sugar.weather.data.formatTemperatureC
import com.sugar.weather.data.hourlyWindow
import com.sugar.weather.data.isStale
import com.sugar.weather.data.localToday
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.sunEventsInWindow
import com.sugar.weather.data.uvLevelText
import com.sugar.weather.data.wmoDesc
import com.sugar.weather.data.wmoScene
import com.sugar.weather.data.windDirText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 单城市页状态持有者(DESIGN.md §8.2):每个 ViewPager2 页面一个实例。
 *
 * - 数据只来自 [com.sugar.weather.data.WeatherRepository] 的 `reports` / `cities` StateFlow;
 * - 进入或恢复页面时复查缓存，有缓存直接渲染，超过 30 分钟才发起网络;
 * - 下拉刷新走 `refreshWeather`;forecast 失败整体失败 → 有缓存保持 Success + Toast,无缓存转 Error;
 * - 所有文本格式化(§5 表)在本类完成,Fragment 零业务逻辑。
 * @param application 提供应用资源与仓库。
 * @property cityId 当前页面绑定的城市标识。
 */
class WeatherViewModel(application: Application, private val cityId: String) :
    AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository
    private val appContext = application

    private val _uiState = MutableStateFlow(WeatherUiState(cityId = cityId))
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    /** 一次性事件:刷新结束(停下拉圈)/ 刷新失败(Toast)。 */
    private val _events = Channel<WeatherEvent>(Channel.BUFFERED)
    val events: Flow<WeatherEvent> = _events.receiveAsFlow()

    private var refreshJob: Job? = null
    /** 合并同一页面短时间内重复触发的缓存检查。 */
    private var freshnessJob: Job? = null

    /** 上次渲染的城市本地日期；跨日恢复时，即使更新失败也需重新标记日期。 */
    private var renderedDate: LocalDate? = null

    init {
        viewModelScope.launch {
            repository.cities.collect { cities ->
                val city = cities.firstOrNull { it.id == cityId } ?: return@collect
                _uiState.update { it.copy(cityName = city.name, isLocated = city.isLocated) }
            }
        }
        viewModelScope.launch {
            repository.reports
                .map { it[cityId] }
                .distinctUntilChanged()
                .collect { report -> if (report != null) applyReport(report) }
        }
        refreshIfStale()
    }

    /** 强制刷新；成功报告统一由 reports 流渲染，避免重复构建列表与详情卡。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (_uiState.value.status != WeatherStatus.SUCCESS) {
                _uiState.update { it.copy(status = WeatherStatus.LOADING) }
            }
            val result = repository.refreshWeather(cityId)
            _events.send(WeatherEvent.RefreshFinished)
            if (result.isFailure) {
                // 无缓存且失败进入错误态；已有缓存保持内容，仅提示刷新失败。
                if (_uiState.value.status != WeatherStatus.SUCCESS) {
                    _uiState.update { it.copy(status = WeatherStatus.ERROR) }
                }
                _events.send(WeatherEvent.RefreshFailed)
            }
        }
    }

    /** 首次进入和恢复到前台时检查缓存；先等待磁盘预热，未过期时不请求网络。 */
    fun refreshIfStale() {
        if (freshnessJob?.isActive == true) return
        freshnessJob = viewModelScope.launch {
            val cached = repository.cachedReport(cityId)
            if (cached != null && renderedDate != cached.localToday()) applyReport(cached)
            if (cached == null || cached.isStale()) refresh()
        }
    }

    // ———————————————————————— 状态合成(§5 全部格式化) ————————————————————————

    /** 由报告合成页面状态，日报按城市当前日期过滤过期项。 */
    private fun applyReport(report: WeatherReport) {
        val current = report.current
        val localToday = report.localToday()
        renderedDate = localToday
        val upcoming = report.daily.filter { !it.date.isBefore(localToday) }.take(TREND_DAYS)
        val today = upcoming.firstOrNull { it.date == localToday }
        _uiState.update { state ->
            state.copy(
                status = WeatherStatus.SUCCESS,
                fetchedAtMillis = report.fetchedAtMillis,
                tempText = current?.let { formatTemperatureC(it.temperatureC) },
                conditionText = current?.let { wmoDesc(it.weatherCode) },
                highLowText = today?.let { formatHighLowC(it.tempMaxC, it.tempMinC) },
                trend = upcoming,
                dailyRows = buildDailyRows(report),
                hourlyRows = buildHourlyRows(report),
                detailCards = buildDetailCards(report),
                sceneType = current?.let { wmoScene(it.weatherCode) } ?: state.sceneType,
                isNight = current?.isNight ?: state.isNight
            )
        }
    }

    /** 六列日报使用紧凑月 / 日，避免窄屏中相邻日期挤在一起。 */
    private fun buildDailyRows(report: WeatherReport): List<DailyRowUi> {
        val today = report.localToday()
        return report.daily.filter { !it.date.isBefore(today) }.take(TREND_DAYS).map { day ->
            DailyRowUi(
                weekText = if (day.date == today) appContext.getString(R.string.today)
                else WEEKDAYS[day.date.dayOfWeek.value - 1],
                dateText = appContext.getString(
                    R.string.date_short_md, day.date.monthValue, day.date.dayOfMonth
                ),
                // 转折前优先换行，窄列中尽量完整呈现两段天气；内容与详情摘要一致。
                desc = (day.summaryText ?: wmoDesc(day.weatherCode)).replace("转", "\n转"),
                iconRes = iconRes(day.weatherCode, night = false)
            )
        }
    }

    /**
     * 逐小时条(§6.2 第 5 条):`hourlyWindow(24)` 起点 = 实况下一整点;
     * 按完整日期提取窗口内的日出/日落，插在该时刻或下一整点之前，温度取该整点。
     */
    private fun buildHourlyRows(report: WeatherReport): List<HourRowUi> {
        val window = report.hourlyWindow(HOURS_COUNT)
        if (window.isEmpty()) return emptyList()
        val rows = ArrayList<HourRowUi>(window.size + 2)
        val sunEvents = report.sunEventsInWindow(
            start = window.first().time,
            endInclusive = window.last().time
        )
        var sunIndex = 0
        for (hour in window) {
            while (sunIndex < sunEvents.size && !sunEvents[sunIndex].time.isAfter(hour.time)) {
                val event = sunEvents[sunIndex]
                rows += HourRowUi(
                    timeText = formatTime(event.time.toLocalTime()),
                    iconRes = if (event.isSunrise) R.drawable.ic_sunrise else R.drawable.ic_sunset,
                    tempText = formatTemperatureC(hour.temperatureC)
                )
                sunIndex++
            }
            rows += HourRowUi(
                timeText = formatTime(hour.time.toLocalTime()),
                iconRes = iconRes(hour.weatherCode, night = isNightHour(report, hour.time)),
                tempText = formatTemperatureC(hour.temperatureC)
            )
        }
        return rows
    }

    /** 根据对应日期的日出日落选择夜间图标，不把跨日的凌晨显示成太阳。 */
    private fun isNightHour(report: WeatherReport, time: LocalDateTime): Boolean {
        val day = report.daily.firstOrNull { it.date == time.toLocalDate() } ?: return false
        val localTime = time.toLocalTime()
        return localTime.isBefore(day.sunrise) || !localTime.isBefore(day.sunset)
    }

    /** 8 张详情卡(§6.2 第 6 条,数值格式 §5.4;DetailCardUi 用 UNIT_* 常量拆 value/unit)。 */
    private fun buildDetailCards(report: WeatherReport): List<DetailCardUi> {
        val current = report.current ?: return emptyList()
        val today = report.daily.firstOrNull { it.date == report.localToday() }
        return listOf(
            DetailCardUi(
                label = appContext.getString(R.string.card_air_quality),
                value = aqiLevelText(current.pm25),
                unit = ""
            ),
            DetailCardUi(label = PM25_LABEL, value = formatPm25(current.pm25), unit = ""),
            DetailCardUi(
                label = windDirText(current.windDirectionDeg, current.windKmh),
                value = formatOneDecimal(current.windKmh),
                unit = UNIT_WIND_SPEED
            ),
            DetailCardUi(
                label = appContext.getString(R.string.card_feels_like),
                value = formatRoundInt(current.apparentTemperatureC),
                unit = UNIT_TEMP
            ),
            DetailCardUi(
                label = appContext.getString(R.string.card_humidity),
                value = formatOneDecimal(current.humidityPercent),
                unit = UNIT_HUMIDITY
            ),
            DetailCardUi(
                label = appContext.getString(R.string.card_visibility),
                value = current.visibilityKm?.let(::formatOneDecimal) ?: UNKNOWN_TEXT,
                unit = if (current.visibilityKm == null) "" else UNIT_VISIBILITY
            ),
            DetailCardUi(
                label = appContext.getString(R.string.card_uv),
                value = uvLevelText(today?.uvIndexMax),
                unit = ""
            ),
            DetailCardUi(
                label = appContext.getString(R.string.card_pressure),
                value = formatRoundInt(current.pressureHpa),
                unit = UNIT_PRESSURE
            )
        )
    }

    private fun formatTime(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    /** 一次性事件(下拉圈停止 / 失败 Toast)。 */
    sealed interface WeatherEvent {
        data object RefreshFinished : WeatherEvent
        data object RefreshFailed : WeatherEvent
    }

    companion object {
        /** 每页 ViewModel 工厂:从 CreationExtras 取 Application,零新增依赖(§10.2)。 */
        fun factory(cityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = getApplicationFromExtras(this)
                WeatherViewModel(app, cityId)
            }
        }

        private fun getApplicationFromExtras(extras: CreationExtras): WeatherApplication {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                ?: error("WeatherViewModel 需要 Application")
            return app as WeatherApplication
        }

        private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        private const val PM25_LABEL = "PM2.5"

        /** 趋势列数:daily[0..5](§6.3)。 */
        private const val TREND_DAYS = 6

        /** 逐时列数:下一整点起 24 小时(§6.2 第 5 条)。 */
        private const val HOURS_COUNT = 24
    }
}
