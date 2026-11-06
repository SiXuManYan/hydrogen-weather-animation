package com.sugar.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** 系统桌面入口，只调度持久任务，不在广播生命周期内请求网络。 */
class WeatherWidgetProvider : AppWidgetProvider() {
    /** 首次添加和系统恢复时立即加载；多个实例共用同一刷新队列。 */
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherWidgetScheduler.ensureScheduled(context)
        WeatherWidgetScheduler.refresh(context = context, force = false)
    }

    /** 刷新按钮使用显式、不可变 PendingIntent，外部无需接收器导出权限。 */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            WeatherWidgetScheduler.refresh(context = context, force = true)
        } else {
            super.onReceive(context, intent)
        }
    }

    /** 尺寸变化使用同一组可伸缩原生控件，缓存命中时不请求天气。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WeatherWidgetScheduler.refresh(context = context, force = false)
    }

    /** 最后一个实例移除后停止所有周期及即时任务。 */
    override fun onDisabled(context: Context) {
        WeatherWidgetScheduler.cancel(context)
    }

    /** 恢复时不保存旧实例 id，改由系统当前 id 重新加载。 */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        onUpdate(context = context, manager = AppWidgetManager.getInstance(context), appWidgetIds = newWidgetIds)
    }

    companion object {
        /** 刷新动作仅供本应用创建的显式 PendingIntent 使用。 */
        internal const val ACTION_REFRESH = "com.sugar.weather.widget.REFRESH"

        /** 微件打开主页时只传城市标识，不接受外部组件或跳转地址。 */
        const val EXTRA_CITY_ID = "widget_city_id"
    }
}
