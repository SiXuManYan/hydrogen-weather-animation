package com.sugar.weather.ui.weather

import com.sugar.weather.R

/**
 * WMO weather_code → 图标资源(DESIGN.md §6.6)。
 *
 * 图标全部为手写 vector(细白描边风格,对齐截图 005322/005332),不复制原 APK 资源。
 * 夜间且 code ∈ {0,1} 返回月亮(§4);逐日/逐时条目一律传 night=false 用昼间样式。
 */
fun iconRes(code: Int, night: Boolean): Int = when {
    night && (code == 0 || code == 1) -> R.drawable.ic_moon
    code == 0 || code == 1 -> R.drawable.ic_sun
    code == 2 -> R.drawable.ic_partly_cloudy
    code == 3 -> R.drawable.ic_overcast
    code == 45 || code == 48 -> R.drawable.ic_fog
    code in 51..57 -> R.drawable.ic_drizzle
    code == 61 || code == 63 || code == 66 || code == 67 ||
        code == 80 || code == 81 -> R.drawable.ic_rain
    code == 65 || code == 82 -> R.drawable.ic_heavy_rain
    code in 71..77 || code == 85 || code == 86 -> R.drawable.ic_snow
    code == 95 -> R.drawable.ic_thunder
    code == 96 || code == 99 -> R.drawable.ic_hail
    else -> R.drawable.ic_cloudy // 表外码兜底,与 wmoDesc 回退「多云」一致(§4)
}
