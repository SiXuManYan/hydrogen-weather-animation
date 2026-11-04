package com.sugar.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sugar.weather.R
import com.sugar.weather.WeatherApplication
import com.sugar.weather.data.TiltSensorRepository
import com.sugar.weather.data.wmoScene
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/** 多城市页序(§8.2 MainViewModel):cities 与 ViewPager2 页一一对应,selectedIndex 为当前位置。 */
data class CityPagesState(val cities: List<City>, val selectedIndex: Int)

/** 背景驱动状态(§8.2):由当前城市的实况天气合成,交给 WeatherBackdropView.setScene。 */
data class BackdropState(val sceneType: SceneType, val isNight: Boolean)

/** 头部 overlay 状态:城市名 + 图钉 + 更新时间文案。 */
data class HeaderUiState(
    val cityName: String = "",
    val isLocated: Boolean = false,
    val updatedText: String = "",
    val hasCities: Boolean = false
)

/**
 * 页面的状态持有者。
 *
 * - [sceneState]：场景、昼夜、模拟开关、调试开关等低频 UI 状态；
 * - [tilt]：传感器或触摸模拟产生的姿态，高频更新；
 * - 传感器通过 [TiltSensorRepository] 以 Flow 形式注入，View 层不直接依赖 SensorManager；
 * - [initialize] 只执行一次，Activity 重建时 ViewModel 状态会自然保留，进程重建则由 Activity 传入保存值。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TiltSensorRepository(application)
    private val weatherRepository =
        (application as WeatherApplication).container.weatherRepository

    private val _sceneState = MutableStateFlow(
        SceneUiState(simulateTilt = !repository.hasSensor)
    )
    val sceneState: StateFlow<SceneUiState> = _sceneState.asStateFlow()

    private val _tilt = MutableStateFlow(Tilt())
    val tilt: StateFlow<Tilt> = _tilt.asStateFlow()

    private var simulatedPitch = Tilt.NEUTRAL_PITCH_DEG
    private var simulatedRoll = Tilt.NEUTRAL_ROLL_DEG
    private var initialized = false

    /** 设备是否存在可用的方向/旋转矢量传感器。 */
    val hasTiltSensor: Boolean
        get() = repository.hasSensor

    /** 多城市页序(§8.2):cities 变化时自动重建,selectedIndex 夹紧到有效范围。 */
    private val _pages = MutableStateFlow(CityPagesState(emptyList(), 0))
    val pages: StateFlow<CityPagesState> = _pages.asStateFlow()

    /**
     * 头部 30s 心跳:「X 分钟前更新」需要按当前时钟重算,
     * 共享一条 ticker 供 header/backdrop 两个 combine 使用,避免重复协程。
     */
    private val headerTicker = flow {
        while (true) {
            emit(Unit)
            delay(HEADER_TICK_MS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** 背景驱动(§6.2 数据流):当前页城市的实况 → sceneType/isNight → setScene。 */
    val backdrop: StateFlow<BackdropState> =
        combine(_pages, weatherRepository.reports, headerTicker) { pages, reports, _ ->
            computeBackdrop(pages, reports)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            BackdropState(SceneType.SUNNY, isNight = false)
        )

    /** 头部 overlay:城市名 / 图钉 / 「刚刚更新 / X 分钟前更新」。 */
    val header: StateFlow<HeaderUiState> =
        combine(_pages, weatherRepository.reports, headerTicker) { pages, reports, _ ->
            computeHeader(pages, reports)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeaderUiState())

    init {
        // 模拟开启时忽略传感器；关闭模拟后重新采用最新传感器值。
        viewModelScope.launch {
            repository.tilt.collect { sensorTilt ->
                if (!_sceneState.value.simulateTilt) {
                    _tilt.value = sensorTilt
                }
            }
        }
        // 城市列表(含冷启动磁盘预热)变化时重建页序;定位城市写入会自动出现在 0 位。
        viewModelScope.launch {
            weatherRepository.cities.collect { cities ->
                _pages.value = CityPagesState(cities, clampIndex(_pages.value.selectedIndex, cities))
            }
        }
    }

    /**
     * 一次性初始化页面状态。
     * Activity 配置变更时 ViewModel 会存活，[initialized] 保证不会覆盖已有状态；
     * 进程重建时由 Activity 传入保存的场景和姿态。
     *
     * @param simulateTilt null 表示没有显式指定，此时有传感器就走传感器，没有传感器则自动开启触摸模拟。
     * @param previewing adb/外部启动带场景参数,或进程重建前处于调试预览时为 true:背景跟随调试场景。
     */
    fun initialize(
        sceneType: SceneType,
        isNight: Boolean,
        simulateTilt: Boolean?,
        pitchDeg: Float,
        rollDeg: Float,
        showDebug: Boolean,
        previewing: Boolean
    ) {
        if (initialized) return
        initialized = true
        val simulate = simulateTilt ?: !repository.hasSensor
        _sceneState.value = SceneUiState(
            sceneType = sceneType,
            isNight = isNight,
            simulateTilt = simulate,
            showDebug = showDebug,
            previewing = previewing
        )
        if (simulate) {
            simulatedPitch = pitchDeg.coerceIn(Tilt.MIN_PITCH_DEG, Tilt.MAX_PITCH_DEG)
            simulatedRoll = rollDeg.coerceIn(Tilt.MIN_ROLL_DEG, Tilt.MAX_ROLL_DEG)
            _tilt.value = Tilt(simulatedPitch, simulatedRoll)
        }
    }

    /** 切换天气场景和昼夜；不会重置当前传感器/模拟姿态。进入调试预览,天气背景暂停接管。 */
    fun selectScene(sceneType: SceneType, isNight: Boolean) {
        _sceneState.update { it.copy(sceneType = sceneType, isNight = isNight, previewing = true) }
    }

    /** 退出调试预览:背景恢复由天气驱动(§7.6);sceneType/isNight 保留,下次预览仍从上次选择开始。 */
    fun exitPreview() {
        _sceneState.update { it.copy(previewing = false) }
    }

    /**
     * 开启/关闭触摸模拟。
     * 开启时从当前姿态接管，避免画面突变；关闭时切回传感器最新值。
     */
    fun setSimulateTilt(enabled: Boolean) {
        if (_sceneState.value.simulateTilt == enabled) return
        _sceneState.update { it.copy(simulateTilt = enabled) }
        if (enabled) {
            simulatedPitch = _tilt.value.pitchDeg
            simulatedRoll = _tilt.value.rollDeg
        } else {
            _tilt.value = repository.tilt.value
        }
    }

    /** 触摸拖动产生的姿态更新；只在模拟倾斜开启时生效。 */
    fun updateSimulatedTilt(pitchDeg: Float, rollDeg: Float) {
        if (!_sceneState.value.simulateTilt) return
        simulatedPitch = pitchDeg.coerceIn(Tilt.MIN_PITCH_DEG, Tilt.MAX_PITCH_DEG)
        simulatedRoll = rollDeg.coerceIn(Tilt.MIN_ROLL_DEG, Tilt.MAX_ROLL_DEG)
        _tilt.value = Tilt(simulatedPitch, simulatedRoll)
    }

    /** 把模拟姿态恢复到竖直握持，并确保触摸模拟处于开启状态。 */
    fun resetSimulatedTilt() {
        setSimulateTilt(true)
        simulatedPitch = Tilt.NEUTRAL_PITCH_DEG
        simulatedRoll = Tilt.NEUTRAL_ROLL_DEG
        _tilt.value = Tilt(Tilt.NEUTRAL_PITCH_DEG, Tilt.NEUTRAL_ROLL_DEG)
    }

    /** 控制左上角调试信息是否显示，实际文本格式化在 Activity 中完成。 */
    fun setDebugVisible(visible: Boolean) {
        _sceneState.update { it.copy(showDebug = visible) }
    }

    // ———————————————————————— 多城市页(§8.2) ————————————————————————

    /** ViewPager2 翻页回调(§6.2 数据流:onPageSelected → selectPage)。 */
    fun selectPage(index: Int) {
        _pages.update { it.copy(selectedIndex = clampIndex(index, it.cities)) }
    }

    /** 从城市管理/搜索页返回后重建页序(§8.2);城市流本身已自动更新,这里仅按需夹紧当前位置。 */
    fun onCityListChanged() {
        val cities = weatherRepository.cities.value
        _pages.value = CityPagesState(cities, clampIndex(_pages.value.selectedIndex, cities))
    }

    /**
     * 定位成功后写入定位城市(§2.4 回退链):Activity 只提交结果,业务写入经 Repository;
     * 存储失败静默(主页面绝不崩溃),下次冷启动定位会重试。
     */
    fun submitLocatedPlace(name: String?, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            runCatching { weatherRepository.upsertLocatedCity(name, latitude, longitude) }
        }
    }

    private fun clampIndex(index: Int, cities: List<City>): Int =
        index.coerceIn(0, cities.lastIndex.coerceAtLeast(0))

    private fun computeBackdrop(
        pages: CityPagesState,
        reports: Map<String, WeatherReport>
    ): BackdropState {
        val city = pages.cities.getOrNull(pages.selectedIndex)
            ?: return BackdropState(SceneType.SUNNY, isNight = false)
        val current = reports[city.id]?.current
        return BackdropState(
            sceneType = current?.let { wmoScene(it.weatherCode) } ?: SceneType.SUNNY,
            isNight = current?.isNight ?: estimateNight()
        )
    }

    private fun computeHeader(
        pages: CityPagesState,
        reports: Map<String, WeatherReport>
    ): HeaderUiState {
        val city = pages.cities.getOrNull(pages.selectedIndex)
        val report = city?.let { reports[it.id] }
        val app = getApplication<Application>()
        return HeaderUiState(
            cityName = city?.name.orEmpty(),
            isLocated = city?.isLocated ?: false,
            updatedText = report?.fetchedAtMillis?.let { formatUpdatedAt(app, it) }.orEmpty(),
            hasCities = pages.cities.isNotEmpty()
        )
    }

    /** 「刚刚更新」(< 1 分钟)/「X 分钟前更新」(§6.2 头部)。 */
    private fun formatUpdatedAt(app: Application, fetchedAtMillis: Long): String {
        val diffMillis = System.currentTimeMillis() - fetchedAtMillis
        return if (diffMillis < JUST_NOW_MS) {
            app.getString(R.string.just_now)
        } else {
            app.getString(R.string.updated_min_ago, diffMillis / 60_000)
        }
    }

    /** 无实况数据时按时钟粗估昼夜(§8.2 isNight 字段注释):19:00 起或 06:00 前为夜。 */
    private fun estimateNight(): Boolean {
        val hour = LocalTime.now().hour
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    /** Activity 进入 onStart 时注册传感器。 */
    fun startSensors() = repository.start()

    /** Activity 进入 onStop 时注销传感器，避免后台耗电。 */
    fun stopSensors() = repository.stop()

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }

    private companion object {
        /** 头部更新时间文案重算心跳(30s,分钟级文案足够)。 */
        const val HEADER_TICK_MS = 30_000L

        /** 1 分钟内视为「刚刚更新」。 */
        const val JUST_NOW_MS = 60_000L

        /** 无数据时的昼夜粗估窗口:19:00 前为昼、06:00 起为昼。 */
        const val NIGHT_START_HOUR = 19
        const val NIGHT_END_HOUR = 6
    }
}
