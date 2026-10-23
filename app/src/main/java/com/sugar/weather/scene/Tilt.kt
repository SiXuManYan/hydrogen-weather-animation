package com.sugar.weather.scene

/**
 * 设备姿态，单位为度。
 * [pitchDeg] 竖直握持时约为 ±90°，平放时约为 0°；[rollDeg] 为左右横滚，范围 -90°~90°。
 *
 * 使用不可变数据类，便于通过 StateFlow 安全地分发传感器和模拟姿态。
 */
data class Tilt(
    val pitchDeg: Float = NEUTRAL_PITCH_DEG,
    val rollDeg: Float = NEUTRAL_ROLL_DEG
) {
    companion object {
        /** 俯仰角下限：手机平放、屏幕朝上。 */
        const val MIN_PITCH_DEG = 0f

        /** 俯仰角上限：手机完全翻转。 */
        const val MAX_PITCH_DEG = 180f

        /** 横滚角下限：向左倾斜到最大。 */
        const val MIN_ROLL_DEG = -90f

        /** 横滚角上限：向右倾斜到最大。 */
        const val MAX_ROLL_DEG = 90f

        /** 竖直握持时的中性俯仰，也是“重置视角”的目标值。 */
        const val NEUTRAL_PITCH_DEG = 90f

        /** 竖直握持时的中性横滚。 */
        const val NEUTRAL_ROLL_DEG = 0f
    }
}
