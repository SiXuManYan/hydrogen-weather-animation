package com.sugar.weather.data

/**
 * 从系统地址的行政字段中优先选取区县名，不使用道路、门牌或完整地址。
 *
 * 不同 Geocoder 提供方会把区县放入不同字段；明确的中文区县名先于城市或社区别名。
 * @param subLocality 市内分区，国内提供方通常在此返回区名，也可能返回街道或乡镇。
 * @param locality 城市或县级市，部分提供方也在此返回区县。
 * @param subAdminArea 省以下行政区域，可能是地级市、区县或海外 county。
 * @param adminArea 省级回退名称。
 */
internal fun locatedPlaceName(
    subLocality: String?,
    locality: String?,
    subAdminArea: String?,
    adminArea: String?
): String? {
    val district = subLocality.usableAreaName()
    val city = locality.usableAreaName()
    val county = subAdminArea.usableAreaName()
    return listOfNotNull(district, city, county).firstOrNull { it.isChineseCountyLevelName() }
        ?: district
        ?: city
        ?: county
        ?: adminArea.usableAreaName()
}

/** 空白、分类占位及明确低于区县的名称不能阻断后续行政区域回退。 */
private fun String?.usableAreaName(): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && it !in GENERIC_AREA_NAMES && !it.isBelowDistrict()
}

/** 排除“地区”“自治区”和“特别行政区”，它们的“区”不是县级区后缀。 */
private fun String.isChineseCountyLevelName(): Boolean =
    endsWith("县") || endsWith("旗") ||
        (endsWith("区") && !endsWith("地区") && !endsWith("自治区") && !endsWith("行政区"))

/** 部分系统把街道、乡镇或社区填入行政字段，天气标题仍保持区县层级。 */
private fun String.isBelowDistrict(): Boolean =
    SUBDISTRICT_SUFFIXES.any { endsWith(it) }

/** 通用行政分类不是具体地点；“郊区”等真实区名不在过滤范围内。 */
private val GENERIC_AREA_NAMES = setOf("市辖区", "县", "区", "旗")

/** 仅过滤明确低于区县的中文后缀，保留系统返回的其他语言分区名。 */
private val SUBDISTRICT_SUFFIXES = listOf("街道", "街道办事处", "镇", "乡", "村", "社区", "小区")
