package com.sugar.weather.data

import android.content.Context
import com.sugar.weather.scene.Tilt
import com.sugar.weather.sensor.TiltSensorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 方向传感器的数据层。
 *
 * 把回调式的 [TiltSensorController] 封装成 [StateFlow]，ViewModel 不需要直接接触 SensorManager；
 * 注册/注销由 ViewModel 在 Activity 生命周期中调用 start/stop 控制。
 */
class TiltSensorRepository(context: Context) {

    private val _tilt = MutableStateFlow(Tilt())

    /** 最新的设备姿态；没有传感器事件时保持 Tilt 的中性默认值。 */
    val tilt: StateFlow<Tilt> = _tilt.asStateFlow()

    private val sensorController = TiltSensorController(context) { pitchDeg, rollDeg ->
        _tilt.value = Tilt(pitchDeg, rollDeg)
    }

    /** 当前设备是否存在可用的方向传感器或旋转矢量传感器。 */
    val hasSensor: Boolean
        get() = sensorController.hasSensor

    /** 注册传感器；重复调用安全，由 ViewModel 在页面可见时触发。 */
    fun start() = sensorController.start()

    /** 注销传感器；重复调用安全，由 ViewModel 在页面不可见时触发。 */
    fun stop() = sensorController.stop()
}
