package com.sugar.weather.ui.fifteenday

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityFifteenDayBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
import kotlinx.coroutines.launch

/**
 * 15 天天气预报详情页(DESIGN.md §3,D9:应用内页,替代原版外跳 mobileLink):
 * 通过 Repository.cachedOrRefresh(cityId) 展示今天之后最多 15 个日期，失败或缺有效日期时可重试。
 */
class FifteenDayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFifteenDayBinding
    private val viewModel: FifteenDayViewModel by viewModels {
        FifteenDayViewModel.factory(intent.getStringExtra(EXTRA_CITY_ID).orEmpty())
    }
    private val adapter = FifteenDayAdapter()

    /** 初始化预报列表，并为工具栏与末行预留系统安全区。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        binding = ActivityFifteenDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.applySafeInsets(top = true)
        binding.recyclerRows.applySafeInsets(bottom = true)

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

    /** 显示加载、错误或有效预报；旧缓存没有未来日期时给出重试入口。 */
    private fun render(state: FifteenDayUiState) {
        binding.textTitle.text = state.cityName.ifEmpty { getString(R.string.fifteen_day_forecast) }
        adapter.submitList(state.days)
        binding.progressLoading.isVisible = state.loading
        val empty = !state.loading && !state.error && state.days.isEmpty()
        binding.errorContainer.isVisible = state.error || empty
        binding.textError.setText(if (empty) R.string.no_future_forecast else R.string.load_failed)
        binding.recyclerRows.isVisible = !state.loading && !state.error && !empty
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
