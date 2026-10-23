package com.sugar.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sugar.weather.data.TiltSensorRepository
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        // 模拟开启时忽略传感器；关闭模拟后重新采用最新传感器值。
        viewModelScope.launch {
            repository.tilt.collect { sensorTilt ->
                if (!_sceneState.value.simulateTilt) {
                    _tilt.value = sensorTilt
                }
            }
        }
    }

    /**
     * 一次性初始化页面状态。
     * Activity 配置变更时 ViewModel 会存活，[initialized] 保证不会覆盖已有状态；
     * 进程重建时由 Activity 传入保存的场景和姿态。
     *
     * @param simulateTilt null 表示没有显式指定，此时有传感器就走传感器，没有传感器则自动开启触摸模拟。
     */
    fun initialize(
        sceneType: SceneType,
        isNight: Boolean,
        simulateTilt: Boolean?,
        pitchDeg: Float,
        rollDeg: Float,
        showDebug: Boolean
    ) {
        if (initialized) return
        initialized = true
        val simulate = simulateTilt ?: !repository.hasSensor
        _sceneState.value = SceneUiState(
            sceneType = sceneType,
            isNight = isNight,
            simulateTilt = simulate,
            showDebug = showDebug
        )
        if (simulate) {
            simulatedPitch = pitchDeg.coerceIn(Tilt.MIN_PITCH_DEG, Tilt.MAX_PITCH_DEG)
            simulatedRoll = rollDeg.coerceIn(Tilt.MIN_ROLL_DEG, Tilt.MAX_ROLL_DEG)
            _tilt.value = Tilt(simulatedPitch, simulatedRoll)
        }
    }

    /** 切换天气场景和昼夜；不会重置当前传感器/模拟姿态。 */
    fun selectScene(sceneType: SceneType, isNight: Boolean) {
        _sceneState.update { it.copy(sceneType = sceneType, isNight = isNight) }
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

    /** Activity 进入 onStart 时注册传感器。 */
    fun startSensors() = repository.start()

    /** Activity 进入 onStop 时注销传感器，避免后台耗电。 */
    fun stopSensors() = repository.stop()

    override fun onCleared() {
        repository.stop()
        super.onCleared()
    }
}
