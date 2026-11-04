package com.sugar.weather.ui.weather

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sugar.weather.R
import com.sugar.weather.databinding.FragmentWeatherBinding
import com.sugar.weather.ui.fifteenday.FifteenDayActivity
import kotlinx.coroutines.launch

/**
 * 单城市天气页(DESIGN.md §6.2,D10 方案每城市一页):
 * View 层只做 ViewBinding、insets、下拉/重试/跳转的用户输入提交,状态全部来自 WeatherUiState。
 */
class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WeatherViewModel by viewModels {
        WeatherViewModel.factory(requireArguments().getString(ARG_CITY_ID).orEmpty())
    }

    private val dailyAdapter = DailyTrendAdapter()
    private val hourlyAdapter = HourlyAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 让出状态栏(背景由 Activity 级 WeatherBackdropView 铺满,本页只负责内容排版)。
        ViewCompat.setOnApplyWindowInsetsListener(binding.swipeRefresh) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top)
            insets
        }
        // §6.2 第 1 条:温度块前约半屏纯背景动画留白(截图 005322 中 14° 位于屏幕约 55-60% 处)。
        binding.topSpacing.layoutParams.height = topSpacingHeight()

        binding.swipeRefresh.setColorSchemeResources(R.color.white)
        binding.recyclerDaily.layoutManager = GridLayoutManager(requireContext(), DAILY_COLUMNS)
        binding.recyclerDaily.adapter = dailyAdapter
        binding.recyclerHourly.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerHourly.adapter = hourlyAdapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.buttonRetry.setOnClickListener { viewModel.refresh() }
        binding.buttonFifteenDay.setOnClickListener {
            // D9:应用内 15 天详情页,数据入口 cachedOrRefresh(零额外请求时的缓存直读)
            FifteenDayActivity.start(requireContext(), viewModel.uiState.value.cityId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            WeatherViewModel.WeatherEvent.RefreshFinished ->
                                binding.swipeRefresh.isRefreshing = false
                            WeatherViewModel.WeatherEvent.RefreshFailed -> {
                                binding.swipeRefresh.isRefreshing = false
                                Toast.makeText(
                                    requireContext(), R.string.offline_toast, Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** 顶部留白高度 = 0.5 × 当前窗口屏高,onViewCreated(首帧布局前)写入。 */
    private fun topSpacingHeight(): Int =
        (resources.displayMetrics.heightPixels * TOP_SPACING_SCREEN_FRACTION).toInt()

    /** 状态 → 视图:加载/错误/内容三态互斥,卡片按 §6.2 固定顺序绑定。 */
    private fun render(state: WeatherUiState) {
        val showContent = state.status == WeatherStatus.SUCCESS
        binding.progressLoading.isVisible = state.status == WeatherStatus.LOADING
        binding.errorContainer.isVisible = state.status == WeatherStatus.ERROR
        binding.scrollContent.isVisible = showContent
        binding.swipeRefresh.isEnabled = showContent

        binding.textTemp.text = state.tempText.orEmpty()
        binding.textCondition.text = state.conditionText.orEmpty()
        binding.textHighLow.text = state.highLowText.orEmpty()
        dailyAdapter.submitList(state.dailyRows)
        hourlyAdapter.submitList(state.hourlyRows)
        binding.trendView.setDays(state.trend)

        val cards = state.detailCards
        binding.cardAirQuality.bind(cards.getOrNull(INDEX_AIR_QUALITY))
        binding.cardPm25.bind(cards.getOrNull(INDEX_PM25))
        binding.cardWind.bind(cards.getOrNull(INDEX_WIND))
        binding.cardFeelsLike.bind(cards.getOrNull(INDEX_FEELS_LIKE))
        binding.cardHumidity.bind(cards.getOrNull(INDEX_HUMIDITY))
        binding.cardVisibility.bind(cards.getOrNull(INDEX_VISIBILITY))
        binding.cardUv.bind(cards.getOrNull(INDEX_UV))
        binding.cardPressure.bind(cards.getOrNull(INDEX_PRESSURE))
    }

    companion object {
        private const val ARG_CITY_ID = "arg_city_id"

        /** 顶部留白占整屏高度比例(§6.2 第 1 条,对齐截图 005322 的半屏动画留白)。 */
        private const val TOP_SPACING_SCREEN_FRACTION = 0.5f

        fun newInstance(cityId: String): WeatherFragment =
            WeatherFragment().apply {
                arguments = Bundle().apply { putString(ARG_CITY_ID, cityId) }
            }

        private const val DAILY_COLUMNS = 6
        private const val INDEX_AIR_QUALITY = 0
        private const val INDEX_PM25 = 1
        private const val INDEX_WIND = 2
        private const val INDEX_FEELS_LIKE = 3
        private const val INDEX_HUMIDITY = 4
        private const val INDEX_VISIBILITY = 5
        private const val INDEX_UV = 6
        private const val INDEX_PRESSURE = 7
    }
}
