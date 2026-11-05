package com.sugar.weather.ui.city

import com.sugar.weather.scene.SceneType

/**
 * 城市管理页状态(DESIGN.md §8.2 CityManageViewModel):
 * [cities] 已按 displayOrder 排序(Repository 保证,定位城市固定 0 位);
 * 文本由 ViewModel 按 §5 格式化,卡片无缓存时 temp/desc 为 null(整行隐藏)。
 *
 * @property cities 已排序的卡片。
 * @property loading 是否正在后台刷新天气。
 * @property selection 多选状态，与天气刷新互不影响。
 * @property deleting 删除提交期间锁定交互，防止重复提交。
 */
data class CityManageUiState(
    val cities: List<CityCardUi> = emptyList(),
    val loading: Boolean = false,
    val selection: CitySelection = CitySelection(),
    val deleting: Boolean = false
)

/**
 * 单张城市卡，文字取天气缓存，多选状态参与 Diff 以刷新描边及无障碍描述。
 *
 * @property cityId 城市持久化标识。
 * @property name 展示名。
 * @property isLocated 定位城市标记，同时决定删除和排序资格。
 * @property tempText 缓存温度，无缓存时为空。
 * @property descText 缓存天气描述。
 * @property sceneType 卡片动画类型。
 * @property isNight 是否使用夜间动画。
 * @property editing 当前是否为多选模式。
 * @property selected 当前城市是否选中。
 * @property enabled 删除提交期间暂停卡片操作。
 */
data class CityCardUi(
    val cityId: String,
    val name: String,
    val isLocated: Boolean,
    val tempText: String?,
    val descText: String?,
    val sceneType: SceneType,
    val isNight: Boolean,
    val editing: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = true
)
