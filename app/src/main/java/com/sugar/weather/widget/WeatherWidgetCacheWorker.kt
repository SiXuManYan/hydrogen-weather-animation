package com.sugar.weather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sugar.weather.WeatherApplication

/**
 * 无需联网即可重算城市当地日期，避免离线跨日后继续把今天标为明天。
 * @param context 应用上下文，不持有桌面或 Activity。
 * @param parameters 系统保存的任务参数，区分缓存检查与用户强制刷新。
 */
class WeatherWidgetCacheWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    /** 本机重绘不进入网络重试；天气请求交由联网约束队列等待。 */
    override suspend fun doWork(): Result {
        val controller = (applicationContext as WeatherApplication).container.weatherWidgetController
        if (controller.redrawCached()) {
            WeatherWidgetScheduler.refreshNetwork(
                context = applicationContext,
                force = inputData.getBoolean(WeatherWidgetScheduler.INPUT_FORCE_REFRESH, false)
            )
        }
        return Result.success()
    }
}
