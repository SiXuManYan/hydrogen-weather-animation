package com.sugar.weather.ui.weather

import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.scene.SceneType

/**
 * 单城市页状态(DESIGN.md §8.2 WeatherViewModel)。
 *
 * 文本字段全部由 [WeatherViewModel] 按 §5 格式化完成,Fragment 只做绑定;
 * [trend] 保留原始 [DailyForecast] 供 WeatherTrendView 自绘坐标归一化。
 * @property isRefreshing 当前城市是否有刷新请求进行中，视图重建后仍可恢复刷新反馈。
 */
data class WeatherUiState(
    val cityId: String,
    val cityName: String = "",
    val isLocated: Boolean = false,
    val status: WeatherStatus = WeatherStatus.LOADING,
    val isRefreshing: Boolean = false,
    val fetchedAtMillis: Long? = null,
    val tempText: String? = null,
    val conditionText: String? = null,
    val highLowText: String? = null,
    val trend: List<DailyForecast> = emptyList(),
    val dailyRows: List<DailyRowUi> = emptyList(),
    val hourlyRows: List<HourRowUi> = emptyList(),
    val detailCards: List<DetailCardUi> = emptyList(),
    val sceneType: SceneType = SceneType.SUNNY,
    val isNight: Boolean = false
)

/** 状态三态:加载中(无缓存)/ 正常 / 错误(无缓存且请求失败,§9)。 */
enum class WeatherStatus { LOADING, SUCCESS, ERROR }

/** 日报条单列(星期/日期/图标/天气文字,今天列 weekText=「今天」)。 */
data class DailyRowUi(
    val weekText: String,
    val dateText: String,
    val desc: String,
    val iconRes: Int
)

/**
 * 逐小时条单列(时间/图标/温度)。
 * 日出/日落节点由 ViewModel 插入(§6.2 第 5 条),[tempText] 取其后第一个整点的温度(对齐截图 005332)。
 */
data class HourRowUi(
    val timeText: String,
    val iconRes: Int,
    val tempText: String
)

/** 详情卡(label 次要色 + value 亮色 + unit 小号;unit 为空则不显示,§5.4)。 */
data class DetailCardUi(
    val label: String,
    val value: String,
    val unit: String
)
