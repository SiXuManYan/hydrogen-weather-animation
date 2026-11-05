package com.sugar.weather.ui.city

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
 * - 多选标识保存到 SavedStateHandle，刷新或重建页面不会丢失；定位城市始终不可选。
 *
 * @param application 提供仓库的应用实例。
 * @param savedStateHandle 保存编辑模式与已选城市标识。
 */
class CityManageViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository

    private val _loading = MutableStateFlow(false)

    /** 删除提交期间暂停手势和多次点击。 */
    private val deleting = MutableStateFlow(false)

    /** 仅持久化城市标识，不持久化 RecyclerView 位置。 */
    private val selection = MutableStateFlow(
        CitySelection(
            editing = savedStateHandle[KEY_EDITING] ?: false,
            cityIds = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED)?.toSet().orEmpty()
        )
    )

    /** 存储失败通过页面 Snackbar 告知，原选择保持可重试。 */
    private val failures = Channel<Unit>(Channel.BUFFERED)

    /** 页面可见时消费的存储失败提示。 */
    val errors = failures.receiveAsFlow()

    /** 合并天气、城市及编辑状态，卡片刷新不会重置选择。 */
    val uiState: StateFlow<CityManageUiState> =
        combine(repository.cities, repository.reports, _loading, selection, deleting) {
                cities, reports, loading, selected, removing ->
            val validSelection = selected.reconcile(cities.eligibleIds())
            CityManageUiState(
                cities = cities.map {
                    it.toCard(reports[it.id]).copy(
                        editing = validSelection.editing,
                        selected = it.id in validSelection.cityIds,
                        enabled = !removing
                    )
                },
                loading = loading,
                selection = validSelection,
                deleting = removing
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
        viewModelScope.launch {
            // 跳过冷启动空占位，避免尚未读盘就清除系统恢复的选择。
            repository.cities.first { it.isNotEmpty() }
            repository.cities.collect { cities ->
                setSelection(selection.value.reconcile(cities.eligibleIds()))
            }
        }
    }

    /** 拖拽结束提交最终顺序(§6.5:写回 displayOrder;定位城市仍钉 0 位由 Repository 保证)。 */
    fun commitOrder(orderedIds: List<String>) {
        viewModelScope.launch {
            try {
                repository.reorderCities(orderedIds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures.send(Unit)
            }
        }
    }

    /** 可见的编辑入口允许用户不依赖长按发现删除功能。 */
    fun beginSelection() {
        if (!deleting.value) setSelection(selection.value.copy(editing = true))
    }

    /** 点击或长按切换选择，定位城市即使经无障碍操作触发也会被过滤。 */
    fun toggleSelection(cityId: String) {
        if (deleting.value) return
        setSelection(selection.value.toggle(cityId, repository.cities.value.eligibleIds()))
    }

    /** 返回键或关闭按钮只退出选择模式。 */
    fun clearSelection() {
        if (!deleting.value) setSelection(CitySelection())
    }

    /** 删除确认时传入当时的选择快照，避免确认期间列表变化扩大删除范围。 */
    fun deleteCities(cityIds: Set<String>) {
        if (deleting.value || cityIds.isEmpty()) return
        deleting.value = true
        viewModelScope.launch {
            try {
                repository.removeCities(cityIds)
                setSelection(CitySelection())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures.send(Unit)
            } finally {
                deleting.value = false
            }
        }
    }

    /** 选择与系统恢复状态同步提交。 */
    private fun setSelection(value: CitySelection) {
        selection.value = value
        savedStateHandle[KEY_EDITING] = value.editing
        savedStateHandle[KEY_SELECTED] = ArrayList(value.cityIds)
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

    /** 领域城市转换为卡片展示信息。 */
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

    /** 多选资格与列表位置无关。 */
    private fun List<City>.eligibleIds(): Set<String> =
        filterNot { it.isLocated }.mapTo(HashSet()) { it.id }

    companion object {
        /** 工厂同时接入系统保存状态。 */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("CityManageViewModel 需要 Application")
                CityManageViewModel(
                    application = app as WeatherApplication,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }

        /** 批量刷新并发上限(§6.5)。 */
        private const val CONCURRENT_REFRESH = 4

        private const val NIGHT_START_HOUR = 19
        private const val NIGHT_END_HOUR = 6

        /** 多选模式及城市标识的系统恢复键。 */
        private const val KEY_EDITING = "city_editing"
        private const val KEY_SELECTED = "city_selected_ids"
    }
}
