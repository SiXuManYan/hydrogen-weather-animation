package com.sugar.weather.widget

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.sugar.weather.R
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真机验证 RemoteViews、系统宿主和持久调度；不修改已保存的城市与桌面布局。 */
@RunWith(AndroidJUnit4::class)
class WeatherWidgetInteractionTest {
    /** 原生 RemoteViews 能在常规、最小和加高尺寸中显示预报，并导出供人工检查的截图。 */
    @Test fun remoteViewsRenderAtSupportedSizes() = onMain {
        for ((width, height) in listOf(360 to 100, 250 to 72, 360 to 160)) {
            val view = render(state = sample(), width = width, height = height)
            assertEquals("27°", view.findViewById<TextView>(R.id.widget_temperature).text.toString())
            assertEquals(3, view.findViewById<LinearLayout>(R.id.widget_forecast).childCount)
            val forecast = view.findViewById<LinearLayout>(R.id.widget_forecast)
            assertTrue(forecast.height > 0)
            assertTrue(forecast.getChildAt(0).findViewById<View>(R.id.widget_day_icon).height > 0)
            save(view = view, name = "sunny-${width}x$height.png")
        }
    }

    /** 空列表显示添加城市提示；失败状态仍展示原来的成功时间和天气。 */
    @Test fun emptyAndFailedStatesKeepHonestWeather() = onMain {
        val empty = render(state = WeatherWidgetState.from(city = null, report = null))
        assertEquals(View.VISIBLE, empty.findViewById<View>(R.id.widget_message).visibility)
        assertEquals("点击添加城市", empty.findViewById<TextView>(R.id.widget_message).text.toString())
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_forecast).visibility)
        save(view = empty, name = "empty.png")

        val normal = render(state = sample())
        val failed = render(state = sample(), stage = WidgetRefreshStage.FAILED)
        val time = normal.findViewById<TextView>(R.id.widget_updated).text.toString().removeSuffix(" 更新")
        assertTrue(failed.findViewById<TextView>(R.id.widget_updated).text.toString().contains(time))
        assertEquals("27°", failed.findViewById<TextView>(R.id.widget_temperature).text.toString())
        save(view = failed, name = "failed.png")
        save(view = render(state = sample().copy(isNight = true)), name = "night.png")
        save(view = render(state = sample().copy(weatherCode = 63, condition = "中雨")), name = "rain.png")
        save(view = render(state = sample().copy(weatherCode = 73, condition = "中雪")), name = "snow.png")
    }

    /** 真正绑定一个测试宿主，检查系统识别、全量提交和多个实例共享周期任务。 */
    @Test fun systemHostAcceptsWidgetAndPeriodicWorkIsUnique() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, WeatherWidgetProvider::class.java)
        val info = manager.installedProviders.firstOrNull { it.provider == component }
        assertNotNull("天气微件必须被系统发现", info)
        val host = AppWidgetHost(context, 0x574541)
        val ids = IntArray(2)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            for (index in ids.indices) {
                ids[index] = host.allocateAppWidgetId()
                assertTrue("测试宿主绑定失败", manager.bindAppWidgetIdIfAllowed(ids[index], component))
            }
            host.startListening()
            onMain {
                val hostView = host.createView(context, ids.first(), info)
                hostView.updateAppWidget(WeatherWidgetRenderer.create(context = context, state = sample()))
                measure(view = hostView, width = 360, height = 100)
                assertEquals("27°", hostView.findViewById<TextView>(R.id.widget_temperature).text.toString())
                // 同时向系统提交，覆盖真实 Binder 序列化和 RemoteViews 校验。
                manager.updateAppWidget(ids, WeatherWidgetRenderer.create(context = context, state = sample()))
                save(view = hostView, name = "system-host.png")
            }
            val workManager = WorkManager.getInstance(context)
            WeatherWidgetScheduler.ensureScheduled(context)
            val first = workManager.getWorkInfosForUniqueWork(WeatherWidgetScheduler.PERIODIC_WORK_NAME)
                .get(10, TimeUnit.SECONDS).single { !it.state.isFinished }
            WeatherWidgetScheduler.ensureScheduled(context)
            val second = workManager.getWorkInfosForUniqueWork(WeatherWidgetScheduler.PERIODIC_WORK_NAME)
                .get(10, TimeUnit.SECONDS).single { !it.state.isFinished }
            assertEquals(first.id, second.id)
            assertEquals(30 * 60_000L, second.periodicityInfo!!.repeatIntervalMillis)
            assertEquals(NetworkType.NOT_REQUIRED, second.constraints.requiredNetworkType)
        } finally {
            ids.filter { it != 0 }.forEach { host.deleteAppWidgetId(it) }
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        // 删除最后一个测试实例后，系统发送 onDisabled，所有相关工作都应取消。
        if (manager.getAppWidgetIds(component).isEmpty()) {
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            var active = true
            while (active && SystemClock.elapsedRealtime() < deadline) {
                active = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WeatherWidgetScheduler.PERIODIC_WORK_NAME)
                    .get(5, TimeUnit.SECONDS).any { !it.state.isFinished }
                if (active) SystemClock.sleep(50)
            }
            assertTrue("移除最后一个微件后应停止周期任务", !active)
        }
    }

    /** 使用系统 RemoteViews.inflate 路径，避免普通 XML 加载掩盖不支持的控件。 */
    private fun render(
        state: WeatherWidgetState,
        stage: WidgetRefreshStage = WidgetRefreshStage.IDLE,
        width: Int = 360,
        height: Int = 100
    ): View {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = WeatherWidgetRenderer.create(context = context, state = state, stage = stage)
            .apply(context, FrameLayout(context))
        measure(view = view, width = width, height = height)
        return view
    }

    /** 用设备实际密度测量，让自动字号和 View 权重参与布局。 */
    private fun measure(view: View, width: Int, height: Int) {
        val density = view.resources.displayMetrics.density
        val widthPx = (width * density).toInt()
        val heightPx = (height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, widthPx, heightPx)
    }

    /** 截图仅写入应用的测试缓存目录，可用 run-as 导出，不改用户天气数据。 */
    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File(view.context.cacheDir, "widget-verification").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** 所有 View 和 AppWidgetHostView 操作使用主线程。 */
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** 示例只用于隔离渲染检查，不写入仓库或出现在真实天气状态中。 */
    private fun sample(): WeatherWidgetState = WeatherWidgetState(
        cityId = "preview", cityName = "杭州市", condition = "晴", temperature = "27°",
        highLow = "28°/21°", weatherCode = 0, isNight = false,
        fetchedAtMillis = 1_793_942_400_000L,
        days = (1L..3L).map { offset ->
            WidgetForecastDay(
                date = LocalDate.of(2026, 11, 6).plusDays(offset),
                forecast = com.sugar.weather.data.model.DailyForecast(
                    date = LocalDate.of(2026, 11, 6).plusDays(offset), weatherCode = 2,
                    tempMaxC = 28.0, tempMinC = 21.0, precipProbabilityMaxPercent = null,
                    uvIndexMax = null, sunrise = java.time.LocalTime.of(6, 0),
                    sunset = java.time.LocalTime.of(18, 0), summaryText = "多云"
                )
            )
        }
    )
}
