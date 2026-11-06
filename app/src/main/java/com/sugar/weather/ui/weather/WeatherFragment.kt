package com.sugar.weather.ui.weather

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sugar.weather.R
import com.sugar.weather.databinding.FragmentWeatherBinding
import com.sugar.weather.ui.applySafeInsets
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

    /** 配置随窗口变化的留白、安全区及用户操作。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val headerHeight = resources.getDimensionPixelSize(R.dimen.home_header_height)
        binding.swipeRefresh.applySafeInsets(top = true, extraTop = headerHeight)
        binding.scrollContent.applySafeInsets(bottom = true, horizontal = false)
        binding.swipeRefresh.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTopSpacing(headerHeight)
        }
        binding.scrollContent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTopSpacing(headerHeight)
            updateBackgroundDimming()
        }
        binding.scrollContent.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
            updateBackgroundDimming()
        })

        binding.swipeRefresh.setColorSchemeResources(R.color.white)
        binding.recyclerDaily.layoutManager = GridLayoutManager(requireContext(), DAILY_COLUMNS)
        binding.recyclerDaily.adapter = dailyAdapter
        binding.recyclerHourly.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerHourly.adapter = hourlyAdapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.buttonRetry.setOnClickListener { viewModel.refresh() }
        binding.buttonFifteenDay.setOnClickListener {
            // 应用内预报优先复用新鲜缓存，过期时由仓库负责更新。
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

    /** 页面视图销毁时断开适配器，避免保留对旧 RecyclerView 的引用。 */
    override fun onDestroyView() {
        binding.recyclerDaily.adapter = null
        binding.recyclerHourly.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /** 每次回到当前城市页都复查缓存，覆盖后台恢复及 ViewPager 保留页面的情况。 */
    override fun onResume() {
        super.onResume()
        updateBackgroundDimming()
        viewModel.refreshIfStale()
    }

    /** 按当前页的上滑距离轻微压暗动画；回顶、恢复滚动或切换城市时自然匹配页面状态。 */
    private fun updateBackgroundDimming() {
        val current = _binding ?: return
        val fadeDistance = (current.scrollContent.height * SCRIM_SCROLL_WINDOW_FRACTION)
            .coerceAtLeast(MIN_SCRIM_SCROLL_DP * resources.displayMetrics.density)
        val progress = (current.scrollContent.scrollY / fadeDistance).coerceIn(0f, 1f)
        current.backgroundScrim.alpha = if (current.scrollContent.isVisible) progress * MAX_SCRIM_ALPHA else 0f
    }

    /** 按实际可用窗口上移温度区；尺寸变化时重算，短窗口仍为头部保留间距。 */
    private fun updateTopSpacing(headerHeight: Int) {
        val current = _binding ?: return
        val usableHeight = current.swipeRefresh.height -
            (current.swipeRefresh.paddingTop - headerHeight) - current.scrollContent.paddingBottom
        val density = resources.displayMetrics.density
        val height = (usableHeight * TOP_SPACING_WINDOW_FRACTION - headerHeight).toInt()
            .coerceIn((32 * density).toInt(), (280 * density).toInt())
        if (current.topSpacing.layoutParams.height != height) {
            current.topSpacing.updateLayoutParams { this.height = height }
        }
    }

    /** 状态 → 视图:加载/错误/内容三态互斥,卡片按 §6.2 固定顺序绑定。 */
    private fun render(state: WeatherUiState) {
        val showContent = state.status == WeatherStatus.SUCCESS
        binding.progressLoading.isVisible = state.status == WeatherStatus.LOADING
        binding.errorContainer.isVisible = state.status == WeatherStatus.ERROR
        binding.scrollContent.isVisible = showContent
        binding.swipeRefresh.isEnabled = showContent
        updateBackgroundDimming()

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

        /** 温度区位于可用窗口约 36% 处，兼顾动画留白与首屏预报可见性。 */
        private const val TOP_SPACING_WINDOW_FRACTION = 0.36f

        /** 背景最多压暗 14%，保留天气氛围并降低阅读预报时的动画干扰。 */
        private const val MAX_SCRIM_ALPHA = 0.14f

        /** 上滑约三分之一窗口时达到目标暗度，短窗口至少保留 160dp 渐变距离。 */
        private const val SCRIM_SCROLL_WINDOW_FRACTION = 0.35f
        private const val MIN_SCRIM_SCROLL_DP = 160f

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
