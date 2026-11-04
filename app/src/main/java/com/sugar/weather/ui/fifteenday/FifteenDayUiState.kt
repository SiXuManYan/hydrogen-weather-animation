package com.sugar.weather.ui.fifteenday

/**
 * 15 天详情页状态(DESIGN.md §8.2 FifteenDayViewModel):days = daily[1..15](明天起 15 天)。
 */
data class FifteenDayUiState(
    val cityName: String = "",
    val days: List<FifteenDayRowUi> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false
)

/** 单行:星期(首行「明天」)/日期/图标/描述/降水概率(可空)/高低温。 */
data class FifteenDayRowUi(
    val weekText: String,
    val dateText: String,
    val desc: String,
    val iconRes: Int,
    val precipProbText: String?,
    val tempHighText: String,
    val tempLowText: String
)
