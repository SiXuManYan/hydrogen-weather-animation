package com.sugar.weather.ui.city

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityCityManageBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
import com.sugar.weather.ui.search.CitySearchActivity
import kotlinx.coroutines.launch

/** 城市管理：卡片长按多选删除，独立手柄排序，定位城市固定置顶。 */
class CityManageActivity : AppCompatActivity() {

    /** 当前页面的视图绑定。 */
    private lateinit var binding: ActivityCityManageBinding

    /** 保存天气、选择和提交状态。 */
    private val viewModel: CityManageViewModel by viewModels { CityManageViewModel.factory() }

    /** 拖拽期间暂缓列表回流，避免天气刷新将卡片拉回旧位置。 */
    private var dragging = false

    /** 卡片点击、长按与排序分别分发，避免长按操作冲突。 */
    private val adapter: CityCardAdapter = CityCardAdapter(
        onItemClick = ::onCityClicked,
        onItemLongClick = { viewModel.toggleSelection(it) },
        onDragStart = { holder -> touchHelper.startDrag(holder) },
        onOrderClick = ::showOrderMenu
    )

    /** 只在选择模式拦截系统返回，第一次返回退出多选。 */
    private val selectionBack = object : OnBackPressedCallback(false) {
        /** 删除提交期间由 ViewModel 阻止退出，避免重复操作。 */
        override fun handleOnBackPressed() = viewModel.clearSelection()
    }

    /** 拖拽由手柄显式发起，普通卡片长按留给多选。 */
    private val touchCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        /** 仅普通模式下的手动城市接受排序和左滑。 */
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val state = viewModel.uiState.value
            if (state.selection.editing || state.deleting ||
                adapter.itemAt(viewHolder.bindingAdapterPosition)?.isLocated != false
            ) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START)
        }

        /** 卡片长按由选择逻辑处理，手柄调用 startDrag。 */
        override fun isLongPressDragEnabled(): Boolean = false

        /** 手动城市只能在定位卡之后交换位置。 */
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (adapter.itemAt(from)?.isLocated != false ||
                adapter.itemAt(to)?.isLocated != false
            ) return false
            adapter.moveItem(from = from, to = to)
            return true
        }

        /** 左滑仍可删除，但先复位卡片并确认，取消后不会残留滑出状态。 */
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val card = adapter.itemAt(position) ?: return
            adapter.revertAt(position)
            if (!card.isLocated) confirmDelete(setOf(card.cityId))
        }

        /** 拖拽时锁住列表回流，并给当前卡片浮起反馈。 */
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragging = true
                viewHolder.itemView.elevation =
                    DRAG_ELEVATION_DP * viewHolder.itemView.resources.displayMetrics.density
                viewHolder.itemView.alpha = DRAG_ALPHA
            }
        }

        /** 松手恢复外观并提交快照，滑动结束不会误提交排序。 */
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.elevation = 0f
            viewHolder.itemView.alpha = 1f
            if (dragging) {
                dragging = false
                viewModel.commitOrder(adapter.currentItems().map { it.cityId })
            }
        }
    }

    /** 手柄回调触发时才访问拖拽控制器，避免适配器初始化循环。 */
    private val touchHelper: ItemTouchHelper by lazy { ItemTouchHelper(touchCallback) }

    /** 安装安全区、城市手势及随生命周期收集的状态。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(lightNavigationBar = true)
        binding = ActivityCityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.applySafeInsets(top = true)
        binding.textHint.applySafeInsets()
        binding.content.applySafeInsets(bottom = true)
        onBackPressedDispatcher.addCallback(this, selectionBack)

        binding.recyclerCities.layoutManager = LinearLayoutManager(this)
        binding.recyclerCities.adapter = adapter
        touchHelper.attachToRecyclerView(binding.recyclerCities)
        binding.buttonBack.setOnClickListener {
            if (viewModel.uiState.value.selection.editing) viewModel.clearSelection() else finish()
        }
        binding.buttonEdit.setOnClickListener { viewModel.beginSelection() }
        binding.buttonDelete.setOnClickListener {
            confirmDelete(viewModel.uiState.value.selection.cityIds)
        }
        binding.buttonAdd.setOnClickListener { startActivity(CitySearchActivity.intent(this)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.errors.collect {
                        dragging = false
                        render(viewModel.uiState.value)
                        Snackbar.make(binding.root, R.string.city_operation_failed, Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    /** 多选顶栏、蓝框与按钮状态由同一份选择数据驱动。 */
    private fun render(state: CityManageUiState) {
        if (!dragging) adapter.submit(state.cities)
        val editing = state.selection.editing
        selectionBack.isEnabled = editing || state.deleting
        binding.textEmpty.isVisible = state.cities.isEmpty()
        binding.progressLoading.isVisible = state.deleting || (state.loading && !editing)
        binding.buttonEdit.isVisible = !editing && state.cities.any { !it.isLocated }
        binding.buttonDelete.isVisible = editing
        binding.buttonDelete.isEnabled = state.selection.cityIds.isNotEmpty() && !state.deleting
        binding.buttonDelete.alpha = if (binding.buttonDelete.isEnabled) 1f else 0.35f
        binding.buttonBack.isEnabled = !state.deleting
        binding.buttonBack.setImageResource(if (editing) R.drawable.ic_close else R.drawable.ic_back)
        binding.buttonBack.setContentDescription(
            getString(if (editing) R.string.city_selection_close else R.string.back)
        )
        binding.textTitle.text = if (editing) {
            getString(R.string.city_selected_count, state.selection.cityIds.size)
        } else getString(R.string.choose_city)
        binding.textHint.setText(
            if (editing) R.string.city_selection_hint else R.string.city_manage_hint
        )
        binding.textHint.isVisible = state.cities.isNotEmpty()
        binding.buttonAdd.isVisible = !editing
    }

    /** 普通模式返回主页；多选模式只切换选择，不离开当前页。 */
    private fun onCityClicked(cityId: String) {
        if (viewModel.uiState.value.selection.editing) {
            viewModel.toggleSelection(cityId)
        } else {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_CITY_ID, cityId))
            finish()
        }
    }

    /** 删除确认绑定城市标识快照，后台刷新不会改变目标。 */
    private fun confirmDelete(cityIds: Set<String>) {
        if (cityIds.isEmpty() || viewModel.uiState.value.deleting) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.city_delete_title, cityIds.size))
            .setMessage(R.string.city_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.city_delete) { _, _ -> viewModel.deleteCities(cityIds) }
            .show()
    }

    /** 手柄的单击菜单为无需拖拽的用户提供同等排序操作。 */
    private fun showOrderMenu(anchor: View, cityId: String) {
        val items = adapter.currentItems()
        val index = items.indexOfFirst { it.cityId == cityId }
        if (index < 0 || viewModel.uiState.value.selection.editing) return
        PopupMenu(this, anchor).apply {
            menu.add(0, MOVE_UP, 0, R.string.city_move_up).isEnabled =
                items.getOrNull(index - 1)?.isLocated == false
            menu.add(0, MOVE_DOWN, 1, R.string.city_move_down).isEnabled = index < items.lastIndex
            setOnMenuItemClickListener { item ->
                val target = index + if (item.itemId == MOVE_UP) -1 else 1
                adapter.moveItem(from = index, to = target)
                viewModel.commitOrder(adapter.currentItems().map { it.cityId })
                true
            }
        }.show()
    }

    companion object {
        /** 返回给主页的城市标识。 */
        const val EXTRA_SELECTED_CITY_ID = "extra_selected_city_id"

        /** 构造城市管理页入口。 */
        fun intent(context: Context): Intent = Intent(context, CityManageActivity::class.java)

        /** 卡片拖拽中的视觉反馈。 */
        private const val DRAG_ELEVATION_DP = 8f
        private const val DRAG_ALPHA = 0.92f

        /** 手柄排序菜单标识。 */
        private const val MOVE_UP = 1
        private const val MOVE_DOWN = 2
    }
}
