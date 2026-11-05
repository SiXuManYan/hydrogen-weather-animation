package com.sugar.weather.data

/**
 * 中国境内坐标判定(用于选择 CMA GRAPES 模型)。
 *
 * 采用覆盖中国大陆、海南与台湾的粗略经纬度包围盒:北纬 18°–54°、东经 73°–135°。
 * 邻国(朝鲜半岛、蒙古等)也会落入该范围;CMA 是 Open-Meteo 提供的全球模型,
 * 边界外的少量误判不会导致请求失败,仅影响模型偏好。
 */
fun isInChina(latitude: Double, longitude: Double): Boolean =
    latitude in CHINA_MIN_LATITUDE..CHINA_MAX_LATITUDE &&
        longitude in CHINA_MIN_LONGITUDE..CHINA_MAX_LONGITUDE

private const val CHINA_MIN_LATITUDE = 18.0
private const val CHINA_MAX_LATITUDE = 54.0
private const val CHINA_MIN_LONGITUDE = 73.0
private const val CHINA_MAX_LONGITUDE = 135.0
