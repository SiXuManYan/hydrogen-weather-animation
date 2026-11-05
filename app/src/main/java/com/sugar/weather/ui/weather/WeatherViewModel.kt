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
import com.sugar.weather.data.model.WeatherReport
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
import java.time.LocalTime
import java.util.Locale

/**
 * 单城市页状态持有者(DESIGN.md §8.2):每个 ViewPager2 页面一个实例。
 *
 * - 数据只来自 [com.sugar.weather.data.WeatherRepository] 的 `reports` / `cities` StateFlow;
 * - 进入页面:有缓存直接渲染,缓存超时([STALE_MS],§9「进入主页面缓存 > 30 分钟自动刷新」)才发起网络;
 * - 下拉刷新走 `refreshWeather`;forecast 失败整体失败 → 有缓存保持 Success + Toast,无缓存转 Error;
 * - 所有文本格式化(§5 表)在本类完成,Fragment 零业务逻辑。
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

    /** 下拉刷新(§6.2):强制走网络,air-quality 失败不拖垮整体(§9,由 Repository 保证)。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = repository.refreshWeather(cityId)
            _events.send(WeatherEvent.RefreshFinished)
            result.fold(
                onSuccess = { report -> applyReport(report) },
                onFailure = {
                    // 无缓存且失败 → Error 态(重试按钮);有缓存保持 Success,仅提示(§9)
                    if (_uiState.value.status != WeatherStatus.SUCCESS) {
                        _uiState.update { it.copy(status = WeatherStatus.ERROR) }
                    }
                    _events.send(WeatherEvent.RefreshFailed)
                }
            )
        }
    }

    private fun refreshIfStale() {
        val cached = repository.reports.value[cityId]
        val stale = cached == null ||
            System.currentTimeMillis() - cached.fetchedAtMillis > STALE_MS
        if (stale) refresh()
    }

    // ———————————————————————— 状态合成(§5 全部格式化) ————————————————————————

    private fun applyReport(report: WeatherReport) {
        val current = report.current
        val today = report.daily.firstOrNull()
        _uiState.update { state ->
            state.copy(
                status = WeatherStatus.SUCCESS,
                fetchedAtMillis = report.fetchedAtMillis,
                tempText = current?.let { formatTemperatureC(it.temperatureC) },
                conditionText = current?.let { wmoDesc(it.weatherCode) },
                highLowText = today?.let { formatHighLowC(it.tempMaxC, it.tempMinC) },
                trend = report.daily.take(TREND_DAYS),
                dailyRows = buildDailyRows(report),
                hourlyRows = buildHourlyRows(report),
                detailCards = buildDetailCards(report),
                sceneType = current?.let { wmoScene(it.weatherCode) } ?: state.sceneType,
                isNight = current?.isNight ?: state.isNight
            )
        }
    }

    /** 日报条(§6.2 第 3 条):今天列固定「今天」,其余星期;日期「M月d日」。 */
    private fun buildDailyRows(report: WeatherReport): List<DailyRowUi> {
        val today = report.observedAt?.toLocalDate() ?: java.time.LocalDate.now()
        return report.daily.take(TREND_DAYS).map { day ->
            DailyRowUi(
                weekText = if (day.date == today) appContext.getString(R.string.today)
                else WEEKDAYS[day.date.dayOfWeek.value - 1],
                dateText = appContext.getString(
                    R.string.date_md, day.date.monthValue, day.date.dayOfMonth
                ),
                // 首页 6 天列宽有限,使用白天代表天气的短文案;转折描述仅在 15 天页展示。
                desc = wmoDesc(day.weatherCode),
                iconRes = iconRes(day.weatherCode, night = false)
            )
        }
    }

    /**
     * 逐小时条(§6.2 第 5 条):`hourlyWindow(24)` 起点 = 实况下一整点;
     * 日出/日落节点由 daily[0].sunrise/sunset 生成,插到第一个晚于该时刻的时辰前,
     * 节点温度取其后第一个整点(截图 005332:06:25 显示 14°);窗口起点之前的节点舍弃。
     */
    private fun buildHourlyRows(report: WeatherReport): List<HourRowUi> {
        val window = report.hourlyWindow(HOURS_COUNT)
        if (window.isEmpty()) return emptyList()
        val rows = ArrayList<HourRowUi>(window.size + 2)
        val sunEvents = report.daily.firstOrNull()?.let { today ->
            listOf(today.sunrise to R.drawable.ic_sunrise, today.sunset to R.drawable.ic_sunset)
                .sortedBy { it.first }
        } ?: emptyList()
        var sunIndex = 0
        val windowStart = window.first().time
        for (hour in window) {
            while (sunIndex < sunEvents.size) {
                val (sunTime, icon) = sunEvents[sunIndex]
                if (sunTime < windowStart.toLocalTime()) {
                    sunIndex++ // 窗口开始前的太阳节点(如下午才打开页面时的今日日出)不展示
                    continue
                }
                if (!hour.time.toLocalTime().isAfter(sunTime)) break
                rows += HourRowUi(
                    timeText = formatTime(sunTime),
                    iconRes = icon,
                    tempText = formatTemperatureC(hour.temperatureC)
                )
                sunIndex++
            }
            rows += HourRowUi(
                timeText = formatTime(hour.time.toLocalTime()),
                iconRes = iconRes(hour.weatherCode, night = false),
                tempText = formatTemperatureC(hour.temperatureC)
            )
        }
        return rows
    }

    /** 8 张详情卡(§6.2 第 6 条,数值格式 §5.4;DetailCardUi 用 UNIT_* 常量拆 value/unit)。 */
    private fun buildDetailCards(report: WeatherReport): List<DetailCardUi> {
        val current = report.current ?: return emptyList()
        val today = report.daily.firstOrNull()
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

        /** 缓存过期阈值:进入页面超 30 分钟自动刷新(§9)。 */
        private const val STALE_MS = 30L * 60_000L
    }
}
