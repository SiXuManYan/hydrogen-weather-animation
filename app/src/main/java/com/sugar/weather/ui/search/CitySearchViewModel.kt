package com.sugar.weather.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sugar.weather.R
import com.sugar.weather.WeatherApplication
import com.sugar.weather.data.WeatherDataException
import com.sugar.weather.data.model.City
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * 城市搜索页状态持有者(DESIGN.md §6.4/§8.2):
 * TextWatcher 300ms 防抖 → repository.searchCity(population 优先排序);热门城市直接按内置坐标添加。
 * 添加动作经 repository.addCity(同 id/同坐标判重),成功后发一次性事件由 Activity 收尾返回。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CitySearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WeatherApplication).container.weatherRepository
    private val appContext = application

    private val _uiState = MutableStateFlow(CitySearchUiState())
    val uiState: StateFlow<CitySearchUiState> = _uiState.asStateFlow()

    /** 一次性事件:城市已加入(携带城市 id,主页据此切页)。 */
    private val _events = Channel<CitySearchEvent>(Channel.BUFFERED)
    val events: Flow<CitySearchEvent> = _events.receiveAsFlow()

    /** 防抖源:输入即时进状态,搜索延迟 300ms。 */
    private val queryFlow = MutableStateFlow("")
    private var addJob: Job? = null

    init {
        viewModelScope.launch {
            queryFlow.debounce(DEBOUNCE_MS).collectLatest { query -> doSearch(query) }
        }
    }

    /** 搜索框文本变化(TextWatcher 提交)。 */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    /** 清空输入。 */
    fun onQueryCleared() = onQueryChanged("")

    /** 点击热门城市 tag:按内置坐标直接添加(§6.4,不搜索)。 */
    fun onPickPopular(city: PopularCity) {
        addCity(city.name, city.latitude, city.longitude)
    }

    /** 点击搜索结果行。 */
    fun addResult(result: CitySearchResultUi) {
        addCity(result.name, result.lat, result.lon)
    }

    // ———————————————————————— 内部实现 ————————————————————————

    private suspend fun doSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searching = false, results = emptyList(), error = null) }
            return
        }
        _uiState.update { it.copy(searching = true, error = null) }
        try {
            val results = repository.searchCity(query).map { place ->
                CitySearchResultUi(
                    name = place.name,
                    admin1 = place.admin1,
                    country = place.country,
                    lat = place.lat,
                    lon = place.lon
                )
            }
            // 结果回来时输入可能已变化:过期结果直接丢弃(下一轮防抖自会刷新)
            _uiState.update { state ->
                if (state.query.trim() == query) {
                    state.copy(searching = false, results = results, error = null)
                } else {
                    state
                }
            }
        } catch (error: WeatherDataException) {
            // WeatherDataException 是仓库层的唯一失败类型(ask 约束),这里归一为文案
            _uiState.update { state ->
                if (state.query.trim() == query) {
                    state.copy(
                        searching = false,
                        results = emptyList(),
                        error = appContext.getString(R.string.search_failed)
                    )
                } else {
                    state
                }
            }
        }
    }

    /** 统一添加入口:稳定 id(UUID v3,同名同坐标重复点击幂等),成功即发事件。 */
    private fun addCity(name: String, latitude: Double, longitude: Double) {
        if (addJob?.isActive == true) return
        addJob = viewModelScope.launch {
            val id = UUID.nameUUIDFromBytes(
                "city:$name:$latitude,$longitude".toByteArray()
            ).toString()
            runCatching {
                repository.addCity(
                    City(id = id, name = name, latitude = latitude, longitude = longitude)
                )
            }
                .onSuccess { city ->
                    _events.send(CitySearchEvent.CityAdded(name = city.name, cityId = city.id))
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _events.send(CitySearchEvent.AddFailed)
                }
        }
    }

    /** 一次性事件。 */
    sealed interface CitySearchEvent {
        data class CityAdded(val name: String, val cityId: String) : CitySearchEvent
        data object AddFailed : CitySearchEvent
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("CitySearchViewModel 需要 Application")
                CitySearchViewModel(app as WeatherApplication)
            }
        }

        /** 文本搜索防抖间隔(§6.4:300ms)。 */
        private const val DEBOUNCE_MS = 300L
    }
}
