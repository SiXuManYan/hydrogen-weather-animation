package com.sugar.weather.ui.city

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityCityManageBinding
import com.sugar.weather.ui.search.CitySearchActivity
import kotlinx.coroutines.launch

/**
 * 城市管理页(DESIGN.md §6.5,截图 005350):
 * 深色工具栏「选择城市」+ 浅灰页面底 + 全宽圆角城市卡(小动画背景)+ 白色圆形 FAB;
 *
 * - 点击卡片 → setResult(cityId) 返回主页切页;
 * - 长按拖拽排序(定位城市钉 0 位,不参与拖拽),左滑删除(定位城市除外);
 * - FAB「+」→ [CitySearchActivity]。
 */
class CityManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCityManageBinding
    private val viewModel: CityManageViewModel by viewModels { CityManageViewModel.factory() }
    private val adapter = CityCardAdapter(onItemClick = ::onCityClicked)

    private val touchCallback = object : ItemTouchHelper.Callback() {
        private var dragFrom = RecyclerView.NO_POSITION

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position == LOCATED_POSITION) {
                // 定位城市固定 0 位:不可拖拽、不可左滑(§2.4 第 4 条)
                return 0
            }
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START)
        }

        override fun isLongPressDragEnabled(): Boolean = true
        override fun isItemViewSwipeEnabled(): Boolean = true

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == LOCATED_POSITION || to == LOCATED_POSITION) return false
            if (dragFrom == RecyclerView.NO_POSITION) dragFrom = from
            adapter.moveItem(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val card = adapter.itemAt(position) ?: return
            if (card.isLocated) {
                adapter.revertAt(position)
            } else {
                viewModel.removeCity(card.cityId)
            }
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder != null) {
                dragFrom = viewHolder.bindingAdapterPosition
                viewHolder.itemView.elevation =
                    DRAG_ELEVATION_DP * viewHolder.itemView.resources.displayMetrics.density
                viewHolder.itemView.alpha = DRAG_ALPHA
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.elevation = 0f
            viewHolder.itemView.alpha = 1f
            if (dragFrom != RecyclerView.NO_POSITION) {
                dragFrom = RecyclerView.NO_POSITION
                // 拖拽结束把最终顺序写回 displayOrder(§6.5);列表随后由 cities 流回流校准
                viewModel.commitOrder(adapter.currentItems().map { it.cityId })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 深色工具栏延伸到状态栏后(白图标保持),内容区是浅色底 → 导航栏手势用深色前景。
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        var toolbarBaseTop = -1
        var listBaseBottom = -1
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (toolbarBaseTop < 0) toolbarBaseTop = binding.toolbar.paddingTop
            binding.toolbar.updatePadding(top = toolbarBaseTop + bars.top)
            if (listBaseBottom < 0) listBaseBottom = binding.content.paddingBottom
            binding.content.updatePadding(bottom = listBaseBottom + bars.bottom)
            insets
        }

        binding.recyclerCities.layoutManager = LinearLayoutManager(this)
        binding.recyclerCities.adapter = adapter
        ItemTouchHelper(touchCallback).attachToRecyclerView(binding.recyclerCities)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAdd.setOnClickListener {
            startActivity(CitySearchActivity.intent(this))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: CityManageUiState) {
        adapter.submit(state.cities)
        binding.textEmpty.isVisible = state.cities.isEmpty()
        // 批量刷新是后台增强:已有缓存卡片照常展示,仅工具栏尾部的圈指示刷新中(§6.5)
        binding.progressLoading.isVisible = state.loading && state.cities.isEmpty()
    }

    /** 卡片点击 → 返回主页并切到该城市(§6.1 导航)。 */
    private fun onCityClicked(cityId: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_CITY_ID, cityId))
        finish()
    }

    companion object {
        /** 返回给主页的结果 extra:被选中的城市 id。 */
        const val EXTRA_SELECTED_CITY_ID = "extra_selected_city_id"

        fun intent(context: Context): Intent = Intent(context, CityManageActivity::class.java)

        /** 定位城市固定 0 位(§2.4)。 */
        private const val LOCATED_POSITION = 0

        /** 拖拽中的卡片浮起高度。 */
        private const val DRAG_ELEVATION_DP = 8f

        /** 拖拽中的卡片透明度。 */
        private const val DRAG_ALPHA = 0.92f
    }
}
