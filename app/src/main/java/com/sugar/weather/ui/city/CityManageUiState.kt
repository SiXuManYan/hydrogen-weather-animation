package com.sugar.weather.ui.city

import com.sugar.weather.scene.SceneType

/**
 * 城市管理页状态(DESIGN.md §8.2 CityManageViewModel):
 * [cities] 已按 displayOrder 排序(Repository 保证,定位城市固定 0 位);
 * 文本由 ViewModel 按 §5 格式化,卡片无缓存时 temp/desc 为 null(整行隐藏)。
 */
data class CityManageUiState(
    val cities: List<CityCardUi> = emptyList(),
    val loading: Boolean = false
)

/** 单张城市卡(§8.2):小动画背景按 [sceneType]/[isNight] 渲染,文字取该城市天气缓存。 */
data class CityCardUi(
    val cityId: String,
    val name: String,
    val isLocated: Boolean,
    val tempText: String?,
    val descText: String?,
    val sceneType: SceneType,
    val isNight: Boolean
)
