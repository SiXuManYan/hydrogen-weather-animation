package com.sugar.weather.ui.fifteenday

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityFifteenDayBinding
import kotlinx.coroutines.launch

/**
 * 15 天天气预报详情页(DESIGN.md §3,D9:应用内页,替代原版外跳 mobileLink):
 * 只调 Repository.cachedOrRefresh(cityId),展示 daily[1..15];无缓存且失败 → 页内错误 + 重试。
 */
class FifteenDayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFifteenDayBinding
    private val viewModel: FifteenDayViewModel by viewModels {
        FifteenDayViewModel.factory(intent.getStringExtra(EXTRA_CITY_ID).orEmpty())
    }
    private val adapter = FifteenDayAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFifteenDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 深蓝夜空底全屏绘制,工具栏让出状态栏(以 XML 内边距为基准,幂等,避免 insets 重复派发时累加)。
        var toolbarBaseTop = -1
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (toolbarBaseTop < 0) toolbarBaseTop = view.paddingTop
            view.updatePadding(top = toolbarBaseTop + bars.top)
            insets
        }

        binding.recyclerRows.layoutManager = LinearLayoutManager(this)
        binding.recyclerRows.adapter = adapter
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: FifteenDayUiState) {
        binding.textTitle.text = state.cityName.ifEmpty { getString(R.string.fifteen_day_forecast) }
        adapter.submitList(state.days)
        binding.progressLoading.isVisible = state.loading
        binding.errorContainer.isVisible = state.error
        binding.recyclerRows.isVisible = !state.loading && !state.error
    }

    companion object {
        const val EXTRA_CITY_ID = "extra_city_id"

        /** 主页「15 天天气预报」按钮入口(WeatherFragment)。 */
        fun start(context: Context, cityId: String) {
            context.startActivity(
                Intent(context, FifteenDayActivity::class.java).putExtra(EXTRA_CITY_ID, cityId)
            )
        }
    }
}
