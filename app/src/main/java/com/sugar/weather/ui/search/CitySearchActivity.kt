package com.sugar.weather.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import androidx.core.content.ContextCompat
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityCitySearchBinding
import kotlinx.coroutines.launch

/**
 * 搜索添加页(DESIGN.md §6.4):
 * 顶部搜索框(自动聚焦)+ 热门城市 tag 区(内置 12 城)+ 防抖搜索结果列表;
 * 点击结果/热门 tag → repository.addCity → 返回主页并切到新城市(§6.1 导航)。
 */
class CitySearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCitySearchBinding
    private val viewModel: CitySearchViewModel by viewModels { CitySearchViewModel.factory() }
    // 注意:必须在 onCreate 之后才解析 viewModel(by lazy),否则会在构造函数阶段触发
    // ComponentActivity.getViewModelStore(),导致 "not yet attached to the Application" 崩溃。
    private val adapter by lazy { SearchResultAdapter(onItemClick = viewModel::addResult) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 深色工具栏延伸到状态栏后(白图标),页面主体也是深色底,导航栏保持白色前景。
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        var toolbarBaseTop = -1
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (toolbarBaseTop < 0) toolbarBaseTop = binding.toolbar.paddingTop
            binding.toolbar.updatePadding(top = toolbarBaseTop + bars.top)
            insets
        }

        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonClear.setOnClickListener { viewModel.onQueryCleared() }
        binding.editQuery.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }
        binding.editQuery.requestFocus()
        // 输入 → ViewModel(300ms 防抖搜索);render 回写前有内容比对,不会形成回环
        binding.editQuery.doAfterTextChanged { editable ->
            viewModel.onQueryChanged(editable?.toString().orEmpty())
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CitySearchViewModel.CitySearchEvent.CityAdded -> finishWithResult(event)
                            CitySearchViewModel.CitySearchEvent.AddFailed -> Toast.makeText(
                                this@CitySearchActivity,
                                R.string.search_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: CitySearchUiState) {
        if (binding.editQuery.text.toString() != state.query) {
            binding.editQuery.setText(state.query)
            binding.editQuery.setSelection(state.query.length)
        }
        binding.buttonClear.isVisible = state.query.isNotEmpty()
        renderPopular(state.popular, showChips = state.query.isBlank())

        adapter.submitList(state.results)
        val noResults = !state.searching && state.error == null &&
            state.query.isNotBlank() && state.results.isEmpty()
        binding.progressSearching.isVisible = state.searching && state.results.isEmpty()
        binding.textError.isVisible = state.error != null
        binding.textError.text = state.error.orEmpty()
        binding.textNoResult.isVisible = noResults
    }

    /** 热门城市 tag(§6.4:ChipGroup,#FFEBEBEB 底 / 20dp 圆角 / #29000000 ripple)。 */
    private fun renderPopular(popular: List<PopularCity>, showChips: Boolean) {
        if (!showChips) {
            binding.chipGroup.removeAllViews()
            binding.chipGroup.isVisible = false
            return
        }
        binding.chipGroup.isVisible = popular.isNotEmpty()
        if (binding.chipGroup.childCount == popular.size) return
        binding.chipGroup.removeAllViews()
        val tagBg = ContextCompat.getColor(this, R.color.city_tag_bg)
        val tagText = ContextCompat.getColor(this, R.color.about_text_primary)
        for (city in popular) {
            val chip = Chip(this)
            chip.text = city.name
            chip.isCheckable = false
            chip.isCloseIconVisible = false
            chip.chipStrokeWidth = 0f
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(tagBg)
            // chipCornerRadius setter 在 Material 1.12+ 已弃用,改用 shapeAppearanceModel
            chip.shapeAppearanceModel = chip.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(resources.getDimension(R.dimen.tag_corner_radius))
                .build()
            chip.setTextColor(tagText)
            chip.rippleColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.city_tag_ripple)
            )
            chip.setOnClickListener { viewModel.onPickPopular(city) }
            binding.chipGroup.addView(chip)
        }
    }

    private fun finishWithResult(event: CitySearchViewModel.CitySearchEvent.CityAdded) {
        hideKeyboard()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ADDED_CITY_ID, event.cityId))
        finish()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { view -> manager?.hideSoftInputFromWindow(view.windowToken, 0) }
    }

    companion object {
        /** 返回给主页的结果 extra:新添加城市的 id(主页据此切页,§6.1)。 */
        const val EXTRA_ADDED_CITY_ID = "extra_added_city_id"

        fun intent(context: Context): Intent = Intent(context, CitySearchActivity::class.java)
    }
}
