package com.sugar.weather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sugar.weather.WeatherApplication

/**
 * 由 WorkManager 在联网条件满足后承载网络请求，缓存重绘由独立任务负责。
 * @param context 应用上下文，不持有桌面或 Activity。
 * @param parameters 系统恢复的任务参数与重试计数。
 */
class WeatherWidgetWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    /** 失败保留缓存；一次执行最多重试两次，之后等待下个周期或手动刷新。 */
    override suspend fun doWork(): Result {
        val controller = (applicationContext as WeatherApplication).container.weatherWidgetController
        val success = controller.refresh(
            force = inputData.getBoolean(WeatherWidgetScheduler.INPUT_FORCE_REFRESH, false)
        )
        return when {
            success -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure()
        }
    }
}
