package com.sugar.weather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.sugar.weather.MainActivity
import com.sugar.weather.R
import com.sugar.weather.data.UNKNOWN_TEXT
import com.sugar.weather.ui.weather.iconRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 使用系统支持的 RemoteViews 控件和矢量背景，桌面无需运行动画或传输大位图。 */
internal object WeatherWidgetRenderer {
    /** 月日与时间同时展示，离线数日后也不会把旧天气误认为今天刚更新。 */
    private val updatedFormat = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.CHINA)

    /** 构建一份完整快照；全量提交替换旧状态，避免部分更新遗留旧预报。 */
    fun create(
        context: Context,
        state: WeatherWidgetState,
        stage: WidgetRefreshStage = WidgetRefreshStage.IDLE
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_weather).apply {
        setInt(R.id.widget_root, "setBackgroundResource", backgroundRes(state))
        setTextViewText(R.id.widget_city, if (state.cityId == null) {
            context.getString(R.string.app_name)
        } else {
            listOf(state.cityName, state.condition).filter { it.isNotBlank() }.joinToString(" ")
        })
        setTextViewText(R.id.widget_temperature, state.temperature)
        setTextViewText(R.id.widget_high_low, state.highLow)
        setTextViewText(R.id.widget_updated, updatedText(context = context, state = state, stage = stage))

        val hasReport = state.cityId != null && state.hasWeather
        setViewVisibility(R.id.widget_forecast, if (hasReport) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_message, if (hasReport) View.GONE else View.VISIBLE)
        setTextViewText(R.id.widget_message, context.getString(when {
            state.cityId == null -> R.string.widget_add_city_hint
            stage == WidgetRefreshStage.REFRESHING -> R.string.widget_loading_hint
            stage == WidgetRefreshStage.FAILED -> R.string.widget_no_weather_hint
            else -> R.string.widget_loading_hint
        }))
        removeAllViews(R.id.widget_forecast)
        state.days.forEachIndexed { index, day ->
            val label = if (index == 0) context.getString(R.string.tomorrow)
                else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
            val forecast = day.forecast
            addView(R.id.widget_forecast, RemoteViews(context.packageName, R.layout.item_widget_forecast).apply {
                setTextViewText(R.id.widget_day_label, label)
                setTextViewText(R.id.widget_day_temperature, forecast?.widgetHighLow() ?: UNKNOWN_TEXT)
                setImageViewResource(R.id.widget_day_icon, forecast?.let { iconRes(code = it.weatherCode, night = false) }
                    ?: R.drawable.ic_cloudy)
                setViewVisibility(R.id.widget_day_icon, if (forecast == null) View.INVISIBLE else View.VISIBLE)
                setContentDescription(R.id.widget_day_icon, forecast?.let {
                    it.summaryText ?: com.sugar.weather.data.wmoDesc(it.weatherCode)
                })
                setContentDescription(R.id.widget_day_root, listOfNotNull(
                    day.date.toString(), forecast?.summaryText,
                    forecast?.widgetHighLow() ?: context.getString(R.string.widget_missing_forecast)
                ).joinToString("，"))
            })
        }

        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(WeatherWidgetProvider.EXTRA_CITY_ID, state.cityId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        val refresh = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_REFRESH
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(
            context, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        setContentDescription(R.id.widget_refresh, context.getString(
            if (stage == WidgetRefreshStage.REFRESHING) R.string.widget_updating else R.string.widget_refresh
        ))
    }

    /** 成功时间始终来自报告；失败只加状态标记，不把本次尝试时间当作数据更新时间。 */
    private fun updatedText(context: Context, state: WeatherWidgetState, stage: WidgetRefreshStage): String {
        val time = state.fetchedAtMillis?.let {
            updatedFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
        return when {
            state.cityId == null -> context.getString(R.string.widget_name)
            stage == WidgetRefreshStage.REFRESHING -> context.getString(R.string.widget_updating)
            stage == WidgetRefreshStage.FAILED && time != null -> context.getString(R.string.widget_update_failed_at, time)
            stage == WidgetRefreshStage.FAILED -> context.getString(R.string.widget_update_failed)
            time != null -> context.getString(R.string.widget_updated_at, time)
            else -> context.getString(R.string.widget_waiting)
        }
    }

    /** 配色跟随实况与昼夜，晴天色带采用独立绘制的几何矢量。 */
    private fun backgroundRes(state: WeatherWidgetState): Int = when {
        state.isNight -> R.drawable.bg_widget_night
        state.weatherCode == null || state.weatherCode in 0..2 -> R.drawable.bg_widget_sunny
        state.weatherCode in 71..77 || state.weatherCode == 85 || state.weatherCode == 86 -> R.drawable.bg_widget_snow
        state.weatherCode in 51..67 || state.weatherCode in 80..82 || state.weatherCode in 95..99 -> R.drawable.bg_widget_rain
        else -> R.drawable.bg_widget_cloudy
    }
}
