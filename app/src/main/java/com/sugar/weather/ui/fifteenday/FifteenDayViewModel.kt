package com.sugar.weather.ui.fifteenday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sugar.weather.R
import com.sugar.weather.WeatherApplication
import com.sugar.weather.data.formatTemperatureC
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.localToday
import com.sugar.weather.data.wmoDesc
import com.sugar.weather.ui.weather.iconRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 15 天详情页状态持有者(DESIGN.md §3/D9):
 * 数据入口 [com.sugar.weather.data.WeatherRepository.cachedOrRefresh] —— 优先内存/磁盘缓存,
 * 新鲜缓存直接复用，过期则刷新；失败仍可展示旧报告中尚未过期的日期。
 * @param application 提供资源及应用级仓库。
 * @property cityId 要展示预报的城市标识。
 */
class FifteenDayViewModel(application: Application, private val cityId: String) :
    AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository

    private val _uiState = MutableStateFlow(FifteenDayUiState())
    val uiState: StateFlow<FifteenDayUiState> = _uiState.asStateFlow()

    /** 避免连续重试产生重叠请求。 */
    private var loadJob: Job? = null

    init {
        // 首页或其他页面刷新后同步最新报告，避免详情一直停留在进入时的快照。
        viewModelScope.launch {
            repository.reports.map { it[cityId] }.distinctUntilChanged()
                .collect { report ->
                    if (report != null) {
                        _uiState.update { it.copy(days = buildRows(report), error = false) }
                    } else {
                        // 原定位城市坐标更新后不继续展示旧坐标的未来天气。
                        _uiState.update { it.copy(days = emptyList()) }
                    }
                }
        }
        load()
    }

    /** 用户重试强制请求，避免不完整但仍新鲜的缓存让重试失效。 */
    fun retry() = load(forceRefresh = true)

    /** 进入或恢复页面时重新筛选有效日期，并由仓库检查 30 分钟缓存。 */
    fun refreshIfStale() = load()

    /** 先按当前日期展示缓存，过期才请求网络；同一时刻只保留一个加载任务。 */
    private fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            try {
                val cached = repository.cachedReport(cityId)
                val cityName = repository.citySnapshot()
                    .firstOrNull { it.id == cityId }?.name.orEmpty()
                val cachedDays = cached?.let(::buildRows).orEmpty()
                // 即使离线也先移除已经过去的日期，不等待网络返回才纠正“明天”。
                _uiState.update {
                    it.copy(
                        cityName = cityName,
                        days = cachedDays,
                        loading = cachedDays.isEmpty(),
                        error = false
                    )
                }
                val result = if (forceRefresh) repository.refreshWeather(cityId)
                else repository.cachedOrRefresh(cityId)
                result.fold(
                    onSuccess = { report ->
                        _uiState.update { it.copy(days = buildRows(report), error = false) }
                    },
                    onFailure = {
                        // 有仍有效的离线日期就继续展示；没有内容时保留重试入口。
                        _uiState.update { it.copy(error = it.days.isEmpty()) }
                    }
                )
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    /** 只展示城市本地今天之后的日期，避免旧缓存把过去的日期标成明天。 */
    private fun buildRows(report: WeatherReport): List<FifteenDayRowUi> {
        val app = getApplication<Application>()
        val today = report.localToday()
        return report.daily.filter { it.date.isAfter(today) }.take(DAY_COUNT).map { day ->
            FifteenDayRowUi(
                weekText = if (day.date == today.plusDays(1)) app.getString(R.string.tomorrow)
                else WEEKDAYS[day.date.dayOfWeek.value - 1],
                dateText = app.getString(R.string.date_md, day.date.monthValue, day.date.dayOfMonth),
                // 摘要与首页一致，详情另起一行说明凌晨、白天和夜间的变化。
                desc = buildList {
                    val summary = day.summaryText ?: wmoDesc(day.weatherCode)
                    add(summary)
                    day.conditionText?.takeIf { it != summary }?.let { add(it) }
                }.joinToString("\n"),
                iconRes = iconRes(day.weatherCode, night = false),
                precipProbText = day.precipProbabilityMaxPercent
                    ?.let { app.getString(R.string.precip_probability, it) },
                tempHighText = formatTemperatureC(day.tempMaxC),
                tempLowText = formatTemperatureC(day.tempMinC)
            )
        }
    }

    companion object {
        fun factory(cityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("FifteenDayViewModel 需要 Application")
                FifteenDayViewModel(app as WeatherApplication, cityId)
            }
        }

        private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** 明天 + 未来 14 天(§3;实测 Mapper 只保留有效数据,daily 15 条时自动收缩)。 */
        private const val DAY_COUNT = 15
    }
}
