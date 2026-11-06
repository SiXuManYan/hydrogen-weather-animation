package com.sugar.weather.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** 缓存重绘不依赖网络，天气请求在独立的联网队列中等待系统执行。 */
internal object WeatherWidgetScheduler {
    /** 持久周期任务的唯一名称；删除最后一个微件时取消。 */
    const val PERIODIC_WORK_NAME = "weather-widget-periodic"

    /** 旧版即时队列名，仅用于升级时取消不带联网约束的遗留请求。 */
    const val IMMEDIATE_WORK_NAME = "weather-widget-immediate"

    /** 添加、尺寸变化和手动刷新先进入缓存队列，离线也能立即重绘。 */
    private const val CACHE_WORK_NAME = "weather-widget-cache"

    /** 全部实例共用的联网队列；强制刷新使用独立后缀以免被缓存检查吞掉。 */
    private const val NETWORK_WORK_NAME = "weather-widget-network"

    /** 手动刷新可绕过新鲜缓存，其余首次加载与尺寸变化复用缓存。 */
    const val INPUT_FORCE_REFRESH = "force_refresh"

    /** 周期重算当地日期，不因离线停摆；30 分钟目标仍受系统省电调度影响。 */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWidgetCacheWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).apply {
            // UPDATE 让已有安装移除旧周期的联网约束，同时保留周期进度。
            enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelUniqueWork("$IMMEDIATE_WORK_NAME-force")
        }
    }

    /** 即时入口只读取本机缓存，再安排受联网约束保护的天气请求。 */
    fun refresh(context: Context, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<WeatherWidgetCacheWorker>()
            .setInputData(workDataOf(INPUT_FORCE_REFRESH to force))
            .build()
        val name = if (force) "$CACHE_WORK_NAME-force" else CACHE_WORK_NAME
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    /** 离线只等待联网，不发请求或消耗失败重试次数；联网后仍复用新鲜缓存。 */
    fun refreshNetwork(context: Context, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
            .setInputData(workDataOf(INPUT_FORCE_REFRESH to force))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val name = if (force) "$NETWORK_WORK_NAME-force" else NETWORK_WORK_NAME
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    /** 系统重启无需额外闹钟或常驻服务；没有微件时则取消全部相关任务。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelUniqueWork("$IMMEDIATE_WORK_NAME-force")
            cancelUniqueWork(CACHE_WORK_NAME)
            cancelUniqueWork("$CACHE_WORK_NAME-force")
            cancelUniqueWork(NETWORK_WORK_NAME)
            cancelUniqueWork("$NETWORK_WORK_NAME-force")
        }
    }
}
