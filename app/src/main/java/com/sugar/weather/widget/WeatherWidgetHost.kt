package com.sugar.weather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** 桌面与调度的系统边界，后台刷新规则可在不依赖设备的环境中验证。 */
internal interface WeatherWidgetHost {
    /** 每次查询真实实例，备份恢复和删除后不沿用旧 id。 */
    val hasWidgets: Boolean

    /** 全量替换桌面的天气与临时刷新状态。 */
    fun show(state: WeatherWidgetState, stage: WidgetRefreshStage)

    /** 保证离线缓存周期任务存在，并迁移已有任务的约束。 */
    fun ensureScheduled()

    /** 首城变更后安排一次缓存检查。 */
    fun requestRefresh()

    /** 最后一个微件消失时取消相关工作。 */
    fun cancel()
}

/**
 * 平台实现只负责 RemoteViews 和 WorkManager，网络与缓存纪律留在 Controller。
 * @param context 应用上下文。
 */
internal class AndroidWeatherWidgetHost(private val context: Context) : WeatherWidgetHost {
    /** 系统实例 id 不进入持久存储。 */
    override val hasWidgets: Boolean
        get() = widgetIds().isNotEmpty()

    /** id 查询与更新之间可能发生删除，忽略系统已移除实例产生的参数异常。 */
    override fun show(state: WeatherWidgetState, stage: WidgetRefreshStage) {
        val ids = widgetIds()
        if (ids.isEmpty()) return
        try {
            AppWidgetManager.getInstance(context).updateAppWidget(
                ids, WeatherWidgetRenderer.create(context = context, state = state, stage = stage)
            )
        } catch (_: IllegalArgumentException) {
            // 下一次广播或状态变更会重新查询存活实例。
        }
    }

    /** 全部微件共用无需联网的缓存重绘周期。 */
    override fun ensureScheduled() = WeatherWidgetScheduler.ensureScheduled(context)

    /** 城市变化使用非强制刷新，允许复用已存在的天气。 */
    override fun requestRefresh() = WeatherWidgetScheduler.refresh(context = context, force = false)

    /** 删除最后一个实例后停止后台工作。 */
    override fun cancel() = WeatherWidgetScheduler.cancel(context)

    /** 从系统读取当前微件身份，避免进程缓存过时。 */
    private fun widgetIds(): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, WeatherWidgetProvider::class.java)
    )
}
