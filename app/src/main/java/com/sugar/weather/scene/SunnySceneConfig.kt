package com.sugar.weather.scene

/**
 * 晴天场景的全部可调参数。
 *
 * 光晕半径与原 App 的算法一致：
 * `radiusPx = radiusDp * density * floor(density * 5 / 3)`。
 *
 * 视差深度使用 dp 配置，运行时换算成像素；横向额外偏移为
 * `depthPx * globalHorizontalShift / 1000`，所以数值越大的层横移越明显。
 *
 * 夜间不是同一套光晕，而是 23 条大圆弧组成的流星/光带效果（见 ARC_*）。
 */
object SunnySceneConfig {

    // 8 层光晕：半径系数、初始旋转相位、自转速度、边数（内层 3 层为 10 边形）
    val HALO_RADIUS_DP = floatArrayOf(
        98f, 87f, 76f, 63f, 52.333332f, 42.333332f, 31.666666f, 20.333334f
    )
    val HALO_PHASE_DEG = floatArrayOf(15f, 0f, 15f, 0f, 15f, 30f, 10f, 25f)
    val HALO_SPIN_DEG_PER_SEC = floatArrayOf(15.6f, 13.7f, 11.7f, 9.8f, 7.8f, 5.9f, 3.9f, 2f)
    val HALO_SIDES = intArrayOf(12, 12, 12, 12, 12, 10, 10, 10)
    val HALO_DEPTH_DP = floatArrayOf(
        0f, 16.666666f, 33.333332f, 50f, 66.666664f, 83.333336f, 116.666664f, 150f
    )
    // 8 层从外到内的「起始色」与「结束色」，颜色在两者之间缓慢往返。
    val HALO_COLOR_FROM = intArrayOf(
        0xFF90C3BA.toInt(),
        0xFFB7C688.toInt(),
        0xFFCEBA63.toInt(),
        0xFFD7A74E.toInt(),
        0xFFDA9144.toInt(),
        0xFFD87D3C.toInt(),
        0xFFD56A35.toInt(),
        0xFFD14628.toInt()
    )
    val HALO_COLOR_TO = intArrayOf(
        0xFFC3D98A.toInt(),
        0xFFDDC957.toInt(),
        0xFFE4AE3F.toInt(),
        0xFFDF953A.toInt(),
        0xFFDC7B3B.toInt(),
        0xFFD76934.toInt(),
        0xFFD3572E.toInt(),
        0xFFD14628.toInt()
    )

    val CLOUD_RADIUS_DP = floatArrayOf(40f, 30f, 20f, 10f)
    val CLOUD_PHASE_DEG = floatArrayOf(0f, 35f, 70f, 0f)
    val CLOUD_SPIN_DEG_PER_SEC = floatArrayOf(32.8f, 32.8f, 30f, 32.8f)
    val CLOUD_DEPTH_DP = floatArrayOf(300f, 183.33333f, 73.333336f, -40f)
    // 云朵中心在屏幕高度上的基准比例；最大的云在下方，最小的云隐藏在上方彩色带附近。
    val CLOUD_BASE_Y_FRACTION = floatArrayOf(0.58f, 0.44f, 0.30f, 0.16f)
    const val CLOUD_MAX_ALPHA = 150

    // 倾斜映射：横滚 ±90° 对应 ±266.7dp 横移；俯仰以竖直握持为中性，前后各约 ±266.7dp
    const val HORIZONTAL_RANGE_DP = 266.66666f
    const val VERTICAL_RANGE_DP = 533.3333f
    const val DEPTH_DIVISOR = 1000f
    const val FOLLOW_MAX_SPEED_DP = 900f
    const val FOLLOW_SLOW_ZONE_DP = 133.33333f
    const val PITCH_NEUTRAL_DEG = 90f
    const val CLOUD_ALPHA_START_DEG = 30f
    const val CLOUD_ALPHA_FULL_DEG = 80f
    const val COLOR_WAVE_START = 0.8f
    const val COLOR_WAVE_HALF_PERIOD_SEC = 6.667f

    // 夜间弧线：圆心相对屏幕左上角的基准偏移，以及统一的 +1250dp 下移
    const val ARC_OFFSET_X_DP = -320f
    const val ARC_OFFSET_Y_DP = -55f
    const val ARC_EXTRA_Y_DP = 1250f

    // 夜间弧线逐项对应原实现：半径、线宽、自转角速度、起始角、扫过角度、弧段数量、颜色。
    // 多条不同半径的弧线叠加，在深蓝背景上形成斜向的流星/光带。
    val ARC_RADIUS_DP = floatArrayOf(
        362.66666f, 394.33334f, 446f, 486.66666f, 530f, 565f, 638.3333f, 654.6667f,
        846f, 963f, 377.66666f, 422.66666f, 519.3333f, 545f, 554f, 593f, 606.6667f,
        661f, 685.6667f, 707f, 746.6667f, 757.3333f, 793f
    )
    val ARC_WIDTH_DP = floatArrayOf(
        1f, 0.6666667f, 0.6666667f, 1.3333334f, 1.3333334f, 0.6666667f, 0.6666667f,
        0.6666667f, 1.3333334f, 1f, 1.1666666f, 2f, 1.3333334f, 2.6666667f,
        1.6666666f, 2.8333333f, 1.6666666f, 1.6666666f, 1.8333334f, 1f, 1f, 2f, 2f
    )
    val ARC_SPIN_DEG_PER_SEC = floatArrayOf(
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        36f, 18f, 18f, 36f, 54f, 18f, 54f, 36f, 36f, 18f, 18f, 36f, 18f
    )
    val ARC_START_DEG = floatArrayOf(
        -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f,
        10f, 16f, 29f, 5f, 5f, 33f, 11f, 43f, 6f, 21f, 14f, 4f, 18f
    )
    val ARC_SWEEP_DEG = floatArrayOf(
        180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f,
        34.5f, 17.7f, 30.5f, 50f, 37.5f, 24.6f, 24.5f, 23f, 37f, 26f, 24.5f,
        26.3f, 26f
    )
    val ARC_COUNT = intArrayOf(
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 4, 4, 2, 4, 4, 4, 2, 4, 4, 4, 2, 4
    )
    val ARC_COLOR = intArrayOf(
        0xFF82ECE1.toInt(),
        0xFF6A9B95.toInt(),
        0xFF6A9B95.toInt(),
        0xFFC8FFF9.toInt(),
        0xFFC8FFF9.toInt(),
        0xFF82ECE1.toInt(),
        0xFF6A9B95.toInt(),
        0xFF6A9B95.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF6A9B95.toInt(),
        0xFFC8FFF9.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF82ECE1.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFBDF3EE.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF82ECE1.toInt(),
        0xFFC8FFF9.toInt(),
        0xFFC8FFF9.toInt(),
        0xFF6A9B95.toInt(),
        0xFF6A9B95.toInt(),
        0xFFC8FFF9.toInt(),
        0xFF82ECE1.toInt()
    )
}
