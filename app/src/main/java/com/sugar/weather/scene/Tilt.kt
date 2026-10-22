package com.sugar.weather.scene

/**
 * 设备姿态，单位为度。
 * [pitchDeg] 竖直握持时约为 ±90°，平放时约为 0°；[rollDeg] 为左右横滚，范围 -90°~90°。
 */
class Tilt(
    var pitchDeg: Float = 90f,
    var rollDeg: Float = 0f
)
