package com.sugar.weather.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * 设备姿态传感器封装。
 *
 * 优先使用 TYPE_ORIENTATION（与原天气 App 一致，加速度计 + 磁力计融合），
 * 设备没有该传感器时回退到 TYPE_ROTATION_VECTOR。
 * pitch 在竖直握持时约为 ±90°，平放时约为 0°；roll 为左右横滚。
 * 注册/注销由 View 的生命周期调用，避免后台耗电与泄漏。
 */
class TiltSensorController(
    context: Context,
    private val onTilt: (pitchDeg: Float, rollDeg: Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // 优先使用原 App 同款的方向传感器；部分设备（尤其模拟器）只有旋转矢量传感器。
    private val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val angles = FloatArray(3)

    // 记录当前已注册的传感器，避免重复注册和重复注销。
    private var registeredSensor: Sensor? = null

    /** 当前设备是否存在可用的方向传感器或旋转矢量传感器。 */
    val hasSensor: Boolean
        get() = orientationSensor != null || rotationVectorSensor != null

    /** 注册传感器；重复调用安全，已有注册时直接返回。 */
    fun start() {
        if (registeredSensor != null) return
        val sensor = orientationSensor ?: rotationVectorSensor ?: return
        // SENSOR_DELAY_GAME 约 50Hz，和原 App 的采样率一致。
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        if (registered) {
            registeredSensor = sensor
        }
    }

    /** 注销传感器；重复调用安全。 */
    fun stop() {
        if (registeredSensor == null) return
        sensorManager.unregisterListener(this)
        registeredSensor = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val pitch: Float
        val roll: Float
        if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            // values = [azimuth, pitch, roll]，与原天气 App 的取值方式一致。
            pitch = event.values[1]
            roll = event.values[2]
        } else {
            // 旋转矢量回退：先转成旋转矩阵，再换算成欧拉角。
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, angles)
            pitch = Math.toDegrees(angles[1].toDouble()).toFloat()
            roll = Math.toDegrees(angles[2].toDouble()).toFloat()
        }
        // 部分设备启动瞬间会给出全 0 的无效值，直接丢弃。
        if (abs(pitch) < INVALID_SENSOR_EPSILON && abs(roll) < INVALID_SENSOR_EPSILON) return
        onTilt(pitch, roll)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** 用于过滤启动瞬间全 0 的无效传感器值。 */
        const val INVALID_SENSOR_EPSILON = 0.001f
    }
}
