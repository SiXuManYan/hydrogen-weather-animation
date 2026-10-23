package com.sugar.weather.scene

/**
 * 晴天场景的全部可调参数。
 *
 * 光晕半径按参考视觉比例计算：
 * `radiusPx = radiusDp * density * floor(density * 5 / 3)`。
 *
 * 视差深度使用 dp 配置，运行时换算成像素；横向额外偏移为
 * `depthPx * globalHorizontalShift / 1000`，所以数值越大的层横移越明显。
 *
 * 夜间不是同一套光晕，而是 23 条大圆弧组成的流星/光带效果（见 ARC_*）。
 */
object SunnySceneConfig {

    /** 8 层光晕的半径系数（dp），从最外层到最内层。 */
    val HALO_RADIUS_DP = floatArrayOf(
        98f, 87f, 76f, 63f, 52.333332f, 42.333332f, 31.666666f, 20.333334f
    )

    /** 每层光晕的初始旋转相位（度）。 */
    val HALO_PHASE_DEG = floatArrayOf(15f, 0f, 15f, 0f, 15f, 30f, 10f, 25f)

    /** 每层光晕的自转角速度（度/秒），外层快、内层慢。 */
    val HALO_SPIN_DEG_PER_SEC = floatArrayOf(15.6f, 13.7f, 11.7f, 9.8f, 7.8f, 5.9f, 3.9f, 2f)

    /** 每层光晕的边数：外层 5 层为 12 边形，内层 3 层为 10 边形。 */
    val HALO_SIDES = intArrayOf(12, 12, 12, 12, 12, 10, 10, 10)

    /** 每层光晕的视差深度（dp），数值越大横向移动越明显。 */
    val HALO_DEPTH_DP = floatArrayOf(
        0f, 16.666666f, 33.333332f, 50f, 66.666664f, 83.333336f, 116.666664f, 150f
    )

    /** 8 层光晕从外到内的起始色（ARGB）。 */
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

    /** 8 层光晕从外到内的结束色（ARGB）；颜色会在起始色与结束色之间往返。 */
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

    /** 4 个六边形云的半径（dp）。 */
    val CLOUD_RADIUS_DP = floatArrayOf(40f, 30f, 20f, 10f)

    /** 4 个六边形云的初始旋转相位（度）。 */
    val CLOUD_PHASE_DEG = floatArrayOf(0f, 35f, 70f, 0f)

    /** 六边形云的自转角速度（度/秒）。 */
    val CLOUD_SPIN_DEG_PER_SEC = floatArrayOf(32.8f, 32.8f, 30f, 32.8f)

    /** 六边形云的视差深度（dp），负值表示反向横移。 */
    val CLOUD_DEPTH_DP = floatArrayOf(300f, 183.33333f, 73.333336f, -40f)

    /** 云朵中心在屏幕高度上的基准比例；最大的云在下方，最小的云隐藏在彩色带附近。 */
    val CLOUD_BASE_Y_FRACTION = floatArrayOf(0.58f, 0.44f, 0.30f, 0.16f)

    /** 六边形云的最大透明度（0~255）。 */
    const val CLOUD_MAX_ALPHA = 150

    /** 横滚 ±90° 对应的最大横向位移（dp）。 */
    const val HORIZONTAL_RANGE_DP = 266.66666f

    /** 俯仰从 -90° 到 +90° 对应的总纵向范围（dp）。 */
    const val VERTICAL_RANGE_DP = 533.3333f

    /** 深度视差除数：额外横移 = depthPx × 全局横移 / 1000。 */
    const val DEPTH_DIVISOR = 1000f

    /** 视差跟随的最大速度（dp/秒）。 */
    const val FOLLOW_MAX_SPEED_DP = 900f

    /** 进入慢速吸附的距离阈值（dp）。 */
    const val FOLLOW_SLOW_ZONE_DP = 133.33333f

    /** 俯仰中性角（度）：竖直握持时保持初始构图。 */
    const val PITCH_NEUTRAL_DEG = 90f

    /** 低于该俯仰角时云朵开始淡出。 */
    const val CLOUD_ALPHA_START_DEG = 30f

    /** 达到该俯仰角时云朵达到最大透明度。 */
    const val CLOUD_ALPHA_FULL_DEG = 80f

    /** 颜色呼吸的初始相位（0~1）。 */
    const val COLOR_WAVE_START = 0.8f

    /** 颜色从起始色走到结束色所需秒数。 */
    const val COLOR_WAVE_HALF_PERIOD_SEC = 6.667f

    /** 夜间弧线圆心相对屏幕左上角的横向基准偏移（dp）。 */
    const val ARC_OFFSET_X_DP = -320f

    /** 夜间弧线圆心相对屏幕左上角的纵向基准偏移（dp）。 */
    const val ARC_OFFSET_Y_DP = -55f

    /** 夜间弧线统一的向下额外偏移（dp），让大圆弧只露出局部。 */
    const val ARC_EXTRA_Y_DP = 1250f

    // 夜间弧线逐项对应参考配置：半径、线宽、自转角速度、起始角、扫过角度、弧段数量、颜色。
    // 多条不同半径的弧线叠加，在深蓝背景上形成斜向的流星/光带。

    /** 每条夜间弧线的半径（dp）。 */
    val ARC_RADIUS_DP = floatArrayOf(
        362.66666f, 394.33334f, 446f, 486.66666f, 530f, 565f, 638.3333f, 654.6667f,
        846f, 963f, 377.66666f, 422.66666f, 519.3333f, 545f, 554f, 593f, 606.6667f,
        661f, 685.6667f, 707f, 746.6667f, 757.3333f, 793f
    )

    /** 每条夜间弧线的线宽（dp）。 */
    val ARC_WIDTH_DP = floatArrayOf(
        1f, 0.6666667f, 0.6666667f, 1.3333334f, 1.3333334f, 0.6666667f, 0.6666667f,
        0.6666667f, 1.3333334f, 1f, 1.1666666f, 2f, 1.3333334f, 2.6666667f,
        1.6666666f, 2.8333333f, 1.6666666f, 1.6666666f, 1.8333334f, 1f, 1f, 2f, 2f
    )

    /** 每条夜间弧线的自转角速度（度/秒），0 表示静止。 */
    val ARC_SPIN_DEG_PER_SEC = floatArrayOf(
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        36f, 18f, 18f, 36f, 54f, 18f, 54f, 36f, 36f, 18f, 18f, 36f, 18f
    )

    /** 每条夜间弧线的起始角（度）。 */
    val ARC_START_DEG = floatArrayOf(
        -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f, -90f,
        10f, 16f, 29f, 5f, 5f, 33f, 11f, 43f, 6f, 21f, 14f, 4f, 18f
    )

    /** 每条夜间弧线扫过的角度（度）。 */
    val ARC_SWEEP_DEG = floatArrayOf(
        180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f,
        34.5f, 17.7f, 30.5f, 50f, 37.5f, 24.6f, 24.5f, 23f, 37f, 26f, 24.5f,
        26.3f, 26f
    )

    /** 同一半径上绘制的弧段数量。 */
    val ARC_COUNT = intArrayOf(
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 4, 4, 2, 4, 4, 4, 2, 4, 4, 4, 2, 4
    )

    /** 每条夜间弧线的颜色（ARGB）。 */
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
