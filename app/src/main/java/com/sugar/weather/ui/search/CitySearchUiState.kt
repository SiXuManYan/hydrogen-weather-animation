package com.sugar.weather.ui.search

/**
 * 城市搜索页状态(DESIGN.md §8.2 CitySearchViewModel)。
 *
 * [results] 已按 population 优先排序(Repository.sortedResults);[popular] 为内置热门城市。
 */
data class CitySearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<CitySearchResultUi> = emptyList(),
    val popular: List<PopularCity> = PopularCity.ALL,
    val error: String? = null
)

/** 搜索结果单行(§6.4:name · admin1 · country)。 */
data class CitySearchResultUi(
    val name: String,
    val admin1: String?,
    val country: String?,
    val lat: Double,
    val lon: Double
)

/**
 * 热门城市(DESIGN.md §6.4:内置常量表,不走搜索——实测「大连」等短名只命中同名村镇)。
 * 坐标取各市市政府/中心城区的常识级城市坐标。
 */
data class PopularCity(val name: String, val latitude: Double, val longitude: Double) {
    companion object {
        val ALL = listOf(
            PopularCity("北京", 39.9042, 116.4074),
            PopularCity("上海", 31.2304, 121.4737),
            PopularCity("广州", 23.1291, 113.2644),
            PopularCity("深圳", 22.5431, 114.0579),
            PopularCity("成都", 30.5728, 104.0668),
            PopularCity("杭州", 30.2741, 120.1551),
            PopularCity("武汉", 30.5928, 114.3055),
            PopularCity("西安", 34.3416, 108.9398),
            PopularCity("沈阳", 41.8057, 123.4315),
            PopularCity("大连", 38.9140, 121.6147),
            PopularCity("哈尔滨", 45.8038, 126.5350),
            PopularCity("乌鲁木齐", 43.8256, 87.6168)
        )
    }
}
