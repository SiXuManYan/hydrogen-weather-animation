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
import kotlinx.coroutines.flow.first
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
        load()
    }

    /** 用户重试强制请求，避免不完整但仍新鲜的缓存让重试失效。 */
    fun retry() = load(forceRefresh = true)

    /** 进入时复用缓存，显式重试时更新网络；同一时刻只保留一个加载任务。 */
    private fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = false) }
            val result = if (forceRefresh) repository.refreshWeather(cityId)
            else repository.cachedOrRefresh(cityId)
            val cityName = repository.cities.first()
                .firstOrNull { it.id == cityId }?.name.orEmpty()
            result.fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            cityName = cityName,
                            days = buildRows(report),
                            loading = false,
                            error = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            )
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
                // 15 天页空间充足,优先展示白天转折(如「小雨转晴」);旧缓存无该字段时回退短文案。
                desc = day.conditionText ?: wmoDesc(day.weatherCode),
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
