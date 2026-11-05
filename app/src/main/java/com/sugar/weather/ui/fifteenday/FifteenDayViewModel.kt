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
import com.sugar.weather.data.wmoDesc
import com.sugar.weather.ui.weather.iconRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 15 天详情页状态持有者(DESIGN.md §3/D9):
 * 数据入口 [com.sugar.weather.data.WeatherRepository.cachedOrRefresh] —— 优先内存/磁盘缓存,
 * 无缓存才现场刷新;daily 全量 16 天已在 forecast 响应内,零额外请求。
 */
class FifteenDayViewModel(application: Application, private val cityId: String) :
    AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository

    private val _uiState = MutableStateFlow(FifteenDayUiState())
    val uiState: StateFlow<FifteenDayUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 失败重试(§3:无缓存且网络失败 → 页内「加载失败,请检查网络」+ 重试)。 */
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = false) }
            val cityName = repository.cities.first()
                .firstOrNull { it.id == cityId }?.name.orEmpty()
            repository.cachedOrRefresh(cityId).fold(
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

    /** UI 取 daily[1..15](§3):首页「明天」,其余星期;降水概率缺失整段隐藏(§8.2)。 */
    private fun buildRows(report: WeatherReport): List<FifteenDayRowUi> {
        val app = getApplication<Application>()
        return report.daily.drop(1).take(DAY_COUNT).mapIndexed { index, day ->
            FifteenDayRowUi(
                weekText = if (index == 0) app.getString(R.string.tomorrow)
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
