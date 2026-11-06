package com.sugar.weather

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.sugar.weather.databinding.DialogScenePickerBinding
import com.sugar.weather.ui.about.AboutActivity
import com.sugar.weather.view.WeatherSwipeRefreshLayout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 使用无网络的测试内容验证真实 Android 事件分发，不修改用户城市或缓存。 */
@RunWith(AndroidJUnit4::class)
class WeatherInteractionTest {
    /** 在轻微横向偏移的下拉中仍触发刷新，圆圈停在固定标题下方。 */
    @Test
    fun downwardDragRefreshesWithoutChangingCity() = withPages { fixture ->
        val page = fixture.pages.first()
        fixture.drag(startX = 0.45f, startY = 0.25f, endX = 0.51f, endY = 0.82f)
        awaitRefresh(fixture.refreshes)
        onMain {
            assertEquals(1, fixture.refreshes.get())
            assertEquals(0, fixture.pager.currentItem)
            assertTrue(page.refresh.isRefreshing)
            assertEquals(page.refresh.paddingTop - page.refresh.progressCircleDiameter, page.refresh.progressViewStartOffset)
            assertTrue(page.refresh.progressViewEndOffset > page.refresh.paddingTop)
        }
    }

    /** 未回到顶部时先滚动正文，不触发刷新。 */
    @Test
    fun downwardDragBelowTopOnlyScrollsContent() = withPages { fixture ->
        onMain { fixture.pages.first().scroll.scrollTo(0, dp(fixture.pager.context, 600)) }
        fixture.drag(startX = 0.45f, startY = 0.25f, endX = 0.48f, endY = 0.36f)
        onMain {
            assertEquals(0, fixture.refreshes.get())
            assertTrue(fixture.pages.first().scroll.scrollY > 0)
            assertFalse(fixture.pages.first().refresh.isRefreshing)
        }
    }

    /** 普通内容横向拖动仍能切换城市，不误触刷新。 */
    @Test
    fun horizontalDragChangesCityWithoutRefreshing() = withPages { fixture ->
        fixture.drag(startX = 0.88f, startY = 0.22f, endX = 0.12f, endY = 0.25f)
        onMain {
            assertEquals(1, fixture.pager.currentItem)
            assertEquals(0, fixture.refreshes.get())
        }
    }

    /** 逐小时列表的横滑优先留在列表中，不提前切走城市。 */
    @Test
    fun hourlyDragScrollsHoursWithoutChangingCity() = withPages { fixture ->
        val page = fixture.pages.first()
        onMain { page.scroll.scrollTo(0, dp(fixture.pager.context, 180)) }
        fixture.drag(startX = 0.88f, startY = 0.18f, endX = 0.12f, endY = 0.19f)
        onMain {
            assertEquals(0, fixture.pager.currentItem)
            assertTrue(page.hourly.computeHorizontalScrollOffset() > 0)
            assertEquals(0, fixture.refreshes.get())
        }
    }

    /** 取消下拉后下一次横滑仍能切页，避免父层一直被禁止拦截。 */
    @Test
    fun cancelledPullReleasesPager() = withPages { fixture ->
        fixture.drag(startX = 0.45f, startY = 0.24f, endX = 0.47f, endY = 0.30f, cancel = true)
        fixture.drag(startX = 0.88f, startY = 0.22f, endX = 0.12f, endY = 0.25f)
        onMain {
            assertEquals(1, fixture.pager.currentItem)
            assertEquals(0, fixture.refreshes.get())
        }
    }

    /** 浅深主题的全部页面与自定义弹窗均可创建，弹窗使用 M3 的 28dp 圆角。 */
    @Test
    fun material3PagesAndDialogsInflateInBothModes() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                    val config = Configuration(activity.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                    }
                    val context = ContextThemeWrapper(activity, R.style.Theme_Weather).apply {
                        applyOverrideConfiguration(config)
                    }
                    val inflater = LayoutInflater.from(context)
                    for (layout in listOf(R.layout.activity_main, R.layout.fragment_weather,
                        R.layout.activity_fifteen_day, R.layout.activity_city_manage,
                        R.layout.activity_city_search, R.layout.activity_about, R.layout.activity_document)) {
                        inflater.inflate(layout, null)
                    }
                    val builder = MaterialAlertDialogBuilder(context)
                    val content = DialogScenePickerBinding.inflate(LayoutInflater.from(builder.context))
                    val dialog = builder.setTitle(R.string.scene_picker_title)
                        .setView(content.root).setPositiveButton(R.string.apply, null).create()
                    dialog.show()
                    val inset = dialog.window!!.decorView.background as android.graphics.drawable.InsetDrawable
                    val shape = inset.drawable as MaterialShapeDrawable
                    assertEquals(dp(context, 28).toFloat(), shape.topLeftCornerResolvedSize, 0.5f)
                    dialog.dismiss()
                }
            }
        }
    }

    /** 复用现有静态 Activity 承载隔离内容，不启动天气请求或定位。 */
    private fun withPages(test: (PageFixture) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var fixture: PageFixture
            scenario.onActivity { activity ->
                fixture = PageFixture(context = activity)
                activity.setContentView(fixture.pager)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            test(fixture)
        }
    }

    /** 刷新回调在圆圈过渡动画完成后触发，限制等待时间并报告确定的计数。 */
    private fun awaitRefresh(refreshes: AtomicInteger) {
        val deadline = SystemClock.uptimeMillis() + 2000
        while (refreshes.get() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
    }

    /** 所有 View 操作都回到主线程，保持与真实页面相同的线程约束。 */
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** 将测试几何换算为设备像素。 */
    private fun dp(context: Context, value: Int): Int = (context.resources.displayMetrics.density * value).toInt()

    /**
     * 两页独立的滚动 / 刷新层级，复现主页与小时列表的三层手势关系。
     * @param context 静态 Activity 的 M3 主题。
     */
    private inner class PageFixture(context: Context) {
        /** 刷新次数由回调记录，和圆圈是否可见分别断言。 */
        val refreshes = AtomicInteger()

        /** 已创建页面，便于检查各自的滚动状态。 */
        val pages = mutableListOf<Page>()

        /** 保留真实 ViewPager2 的父层拦截和页面切换行为。 */
        val pager = ViewPager2(context).apply {
            offscreenPageLimit = 1
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                /** 构造无网络的天气内容，小时列表的位置固定可计算。 */
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val refresh = WeatherSwipeRefreshLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        setPadding(0, dp(context, 96), 0, 0)
                        setOnRefreshListener { refreshes.incrementAndGet() }
                    }
                    val scroll = NestedScrollView(context)
                    val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    val hourly = RecyclerView(context).apply {
                        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                        isNestedScrollingEnabled = false
                        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                            /** 固定列宽制造真实横向滚动范围。 */
                            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                                object : RecyclerView.ViewHolder(TextView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(dp(context, 64), dp(context, 72))
                                }) {}

                            /** 测试内容无需文字，避免将文案与手势断言耦合。 */
                            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

                            /** 足够覆盖左右滚动的 24 列。 */
                            override fun getItemCount(): Int = 24
                        }
                    }
                    content.addView(Space(context), ViewGroup.LayoutParams(-1, dp(context, 200)))
                    content.addView(hourly, ViewGroup.LayoutParams(-1, dp(context, 72)))
                    content.addView(Space(context), ViewGroup.LayoutParams(-1, dp(context, 1800)))
                    scroll.addView(content)
                    refresh.addView(scroll)
                    refresh.horizontalScrollChild = hourly
                    pages += Page(refresh = refresh, scroll = scroll, hourly = hourly)
                    return object : RecyclerView.ViewHolder(refresh) {}
                }

                /** 页面内容已在创建阶段绑定，不接入真实仓库。 */
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

                /** 两个城市足以覆盖分页与内部列表的冲突。 */
                override fun getItemCount(): Int = 2
            }
        }

        /** 将连续触摸从 ViewPager2 根部派发，父子控件均参与拦截判定。 */
        fun drag(startX: Float, startY: Float, endX: Float, endY: Float, cancel: Boolean = false) {
            val downTime = SystemClock.uptimeMillis()
            for (step in 0..20) {
                onMain {
                    val fraction = step / 20f
                    val action = when (step) {
                        0 -> MotionEvent.ACTION_DOWN
                        20 -> if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                        else -> MotionEvent.ACTION_MOVE
                    }
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                        (startX + (endX - startX) * fraction) * pager.width,
                        (startY + (endY - startY) * fraction) * pager.height, 0)
                    pager.dispatchTouchEvent(event)
                    event.recycle()
                }
                SystemClock.sleep(16)
            }
            SystemClock.sleep(400)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    /**
     * 单页待断言的视图，不持有生产仓库。
     * @property refresh 刷新与手势协调入口。
     * @property scroll 纵向滚动容器。
     * @property hourly 横向小时列表。
     */
    private data class Page(val refresh: WeatherSwipeRefreshLayout, val scroll: NestedScrollView, val hourly: RecyclerView)
}
