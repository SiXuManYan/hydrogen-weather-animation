package com.sugar.weather.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityCitySearchBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
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

    /** 初始化搜索与列表，内容同时避让导航栏、刘海和输入法。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        binding = ActivityCitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applySafeInsets(bottom = true, ime = true)
        binding.toolbar.applySafeInsets(top = true, horizontal = false)

        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.queryInput.setEndIconOnClickListener { viewModel.onQueryCleared() }
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

    /** 根据输入切换热门城市和结果，滚动区始终受键盘安全区约束。 */
    private fun render(state: CitySearchUiState) {
        if (binding.editQuery.text.toString() != state.query) {
            binding.editQuery.setText(state.query)
            binding.editQuery.setSelection(state.query.length)
        }
        renderPopular(state.popular, showChips = state.query.isBlank())

        binding.recyclerResults.isVisible = state.query.isNotBlank()
        adapter.submitList(state.results)
        val noResults = !state.searching && state.error == null &&
            state.query.isNotBlank() && state.results.isEmpty()
        binding.progressSearching.isVisible = state.searching && state.results.isEmpty()
        binding.textError.isVisible = state.error != null
        binding.textError.text = state.error.orEmpty()
        binding.textNoResult.isVisible = noResults
    }

    /** 热门城市使用搜索页深色 M3 Assist Chip，输入切换时复用视图与监听器。 */
    private fun renderPopular(popular: List<PopularCity>, showChips: Boolean) {
        binding.popularContainer.isVisible = showChips && popular.isNotEmpty()
        if (!showChips) return
        if (binding.chipGroup.childCount == popular.size) return
        binding.chipGroup.removeAllViews()
        for (city in popular) {
            val chip = Chip(binding.chipGroup.context)
            chip.text = city.name
            chip.isCheckable = false
            chip.isCloseIconVisible = false
            chip.setEnsureMinTouchTargetSize(true)
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
