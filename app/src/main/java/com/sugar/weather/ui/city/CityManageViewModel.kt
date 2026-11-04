package com.sugar.weather.ui.city

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sugar.weather.WeatherApplication
import com.sugar.weather.data.formatTemperatureC
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.wmoDesc
import com.sugar.weather.data.wmoScene
import com.sugar.weather.scene.SceneType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalTime

/**
 * 城市管理页状态持有者(DESIGN.md §6.5/§8.2):
 *
 * - 卡片列表来自 Repository 的 cities + reports StateFlow(进程启动时已从磁盘缓存预热,
 *   离线也能显示每个城市的上次天气);
 * - 打开页面时对全部城市并行刷新(并发限 4,Semaphore),结果照常写回 reports,
 *   卡片文字随之更新;单城市失败不影响其它卡片(§6.5);
 * - 拖拽排序/删除经 [commitOrder]/[removeCity] 提交 Repository,列表由 cities 流自动回流。
 */
class CityManageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository

    private val _loading = MutableStateFlow(false)

    val uiState: StateFlow<CityManageUiState> =
        combine(repository.cities, repository.reports, _loading) { cities, reports, loading ->
            CityManageUiState(
                cities = cities.map { it.toCard(reports[it.id]) },
                loading = loading
            )
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), CityManageUiState()
        )

    init {
        // 等城市列表磁盘预热完成(冷启动瞬间为空)后批量刷新一次;重排/删除引起的再发射不重刷。
        viewModelScope.launch {
            val cities = repository.cities.first { it.isNotEmpty() }
            refreshAll(cities)
        }
    }

    /** 拖拽结束提交最终顺序(§6.5:写回 displayOrder;定位城市仍钉 0 位由 Repository 保证)。 */
    fun commitOrder(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderCities(orderedIds) }
    }

    /** 左滑删除(§6.5):定位城市不可删,Repository 静默忽略,列表回流后自动回弹。 */
    fun removeCity(cityId: String) {
        viewModelScope.launch { repository.removeCity(cityId) }
    }

    /** 全量刷新,并发限 4(§6.5 Semaphore)。 */
    private suspend fun refreshAll(cities: List<City>) {
        _loading.update { true }
        coroutineScope {
            val gate = Semaphore(CONCURRENT_REFRESH)
            cities.map { city ->
                launch {
                    gate.withPermit {
                        // 失败(Result.failure)按 §6.5 只保持缓存展示,不打断其它城市
                        repository.refreshWeather(city.id)
                    }
                }
            }.joinAll()
        }
        _loading.update { false }
    }

    private fun City.toCard(report: WeatherReport?): CityCardUi {
        val current = report?.current
        return CityCardUi(
            cityId = id,
            name = name,
            isLocated = isLocated,
            tempText = current?.let { formatTemperatureC(it.temperatureC) },
            descText = current?.let { wmoDesc(it.weatherCode) },
            sceneType = current?.let { wmoScene(it.weatherCode) } ?: SceneType.SUNNY,
            isNight = current?.isNight ?: estimateNight()
        )
    }

    /** 无实况数据时按时钟粗估昼夜(与 MainViewModel 同窗口:19:00 起或 06:00 前为夜)。 */
    private fun estimateNight(): Boolean {
        val hour = LocalTime.now().hour
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("CityManageViewModel 需要 Application")
                CityManageViewModel(app as WeatherApplication)
            }
        }

        /** 批量刷新并发上限(§6.5)。 */
        private const val CONCURRENT_REFRESH = 4

        private const val NIGHT_START_HOUR = 19
        private const val NIGHT_END_HOUR = 6
    }
}
