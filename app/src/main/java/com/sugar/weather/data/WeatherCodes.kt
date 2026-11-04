package com.sugar.weather.data

import com.sugar.weather.scene.SceneType
import kotlin.math.roundToInt

/**
 * WMO weather_code 与展示映射表(DESIGN.md §4/§5.1~§5.3)。
 *
 * 全部为纯 Kotlin 函数,不依赖 Android,可直接 JVM 单测。
 * 对表外取值的兜底:场景一律回退 CLOUDY、描述回退「多云」(§4:不臆测)。
 */

/** code → 中文名。WMO 码表中昼/夜文案一致(§4),逐日/逐时条目一律用昼间文案。 */
fun wmoDesc(code: Int): String = WMO_TABLE[code]?.desc ?: FALLBACK_DESC

/** code → 场景类型(SceneType 枚举 15 值全部复用,§4)。未知码回退 CLOUDY。 */
fun wmoScene(code: Int): SceneType = WMO_TABLE[code]?.scene ?: FALLBACK_SCENE

/**
 * PM2.5 浓度(μg/m³)→ 空气质量等级文字。
 * 依据中国 HJ 633-2012 的 24 小时平均浓度限值(§5.1);pm25 为 null 时显示「—」。
 */
fun aqiLevelText(pm25: Double?): String {
    if (pm25 == null) return UNKNOWN_TEXT
    return when {
        pm25 <= 35.0 -> "优"
        pm25 <= 75.0 -> "良"
        pm25 <= 115.0 -> "轻度污染"
        pm25 <= 150.0 -> "中度污染"
        pm25 <= 250.0 -> "重度污染"
        else -> "严重污染"
    }
}

/**
 * 紫外线指数(daily.uv_index_max)→ 等级文字(§5.2)。
 * null(夜间/极地)→「—」;表区间间空隙按就近下限归档(<3 最弱、<5 弱、<7 中等、<10 强)。
 */
fun uvLevelText(uv: Double?): String {
    if (uv == null) return UNKNOWN_TEXT
    return when {
        uv < 3.0 -> "最弱"
        uv < 5.0 -> "弱"
        uv < 7.0 -> "中等"
        uv < 10.0 -> "强"
        else -> "很强"
    }
}

/**
 * 风向角度 → 16 方位中文(§5.3):idx = round(deg / 22.5) mod 16,deg 归一化到 [0,360)。
 * [speedKmh] 非 null 且 < 0.5 时显示「静风」;[deg] 缺失显示「—」。
 */
fun windDirText(deg: Double?, speedKmh: Double? = null): String {
    if (deg == null) return UNKNOWN_TEXT
    if (speedKmh != null && speedKmh < STILL_WIND_KMH) return STILL_WIND_TEXT
    val normalized = ((deg % 360.0) + 360.0) % 360.0
    val idx = (Math.round(normalized / 22.5).toInt() % 16 + 16) % 16
    return WIND_DIRECTIONS[idx]
}

/** 16 方位表,下标即 §5.3 的 idx。 */
private val WIND_DIRECTIONS = arrayOf(
    "北风", "东北偏北风", "东北风", "东北偏东风",
    "东风", "东南偏东风", "东南风", "东南偏南风",
    "南风", "西南偏南风", "西南风", "西南偏西风",
    "西风", "西北偏西风", "西北风", "西北偏北风"
)

/** 静风阈值(§5.3):风速 < 0.5 km/h 时风向显示「静风」。 */
private const val STILL_WIND_KMH = 0.5
private const val STILL_WIND_TEXT = "静风"

/** 可空数值缺失时的占位文案(§5.2)。 */
const val UNKNOWN_TEXT = "—"

private const val FALLBACK_DESC = "多云"
private val FALLBACK_SCENE = SceneType.CLOUDY

private data class WmoEntry(val desc: String, val scene: SceneType)

private val WMO_TABLE: Map<Int, WmoEntry> = mapOf(
    // code<init> 晴/少云(§4;夜间图标用月亮,由 UI 层按 isNight 决定)
    0 to WmoEntry("晴", SceneType.SUNNY),
    1 to WmoEntry("晴间少云", SceneType.SUNNY),
    2 to WmoEntry("多云", SceneType.CLOUDY),
    3 to WmoEntry("阴", SceneType.OVERCAST),
    45 to WmoEntry("雾", SceneType.FOG),
    48 to WmoEntry("雾凇", SceneType.FOG),
    51 to WmoEntry("毛毛雨(轻)", SceneType.LIGHT_RAIN),
    53 to WmoEntry("毛毛雨", SceneType.LIGHT_RAIN),
    55 to WmoEntry("毛毛雨(浓)", SceneType.LIGHT_RAIN),
    56 to WmoEntry("冻毛毛雨(轻)", SceneType.LIGHT_RAIN),
    57 to WmoEntry("冻毛毛雨(浓)", SceneType.LIGHT_RAIN),
    61 to WmoEntry("小雨", SceneType.LIGHT_RAIN),
    63 to WmoEntry("中雨", SceneType.RAIN),
    65 to WmoEntry("大雨", SceneType.HEAVY_RAIN),
    66 to WmoEntry("冻雨(轻)", SceneType.RAIN),
    67 to WmoEntry("冻雨(强)", SceneType.RAIN),
    71 to WmoEntry("小雪", SceneType.LIGHT_SNOW),
    73 to WmoEntry("中雪", SceneType.SNOW),
    75 to WmoEntry("大雪", SceneType.SNOW),
    77 to WmoEntry("雪粒", SceneType.LIGHT_SNOW),
    80 to WmoEntry("阵雨(弱)", SceneType.RAIN),
    81 to WmoEntry("阵雨", SceneType.RAIN),
    82 to WmoEntry("强阵雨", SceneType.HEAVY_RAIN),
    85 to WmoEntry("阵雪(弱)", SceneType.LIGHT_SNOW),
    86 to WmoEntry("阵雪(强)", SceneType.SNOW),
    95 to WmoEntry("雷阵雨", SceneType.THUNDER),
    96 to WmoEntry("雷阵雨伴冰雹(弱)", SceneType.HAIL),
    99 to WmoEntry("雷阵雨伴冰雹(强)", SceneType.HAIL)
)
