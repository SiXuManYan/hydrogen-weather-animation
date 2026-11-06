package com.sugar.weather

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sugar.weather.data.LocationFix
import com.sugar.weather.databinding.ActivityMainBinding
import com.sugar.weather.databinding.DialogScenePickerBinding
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.ui.BackdropState
import com.sugar.weather.ui.CityPagesAdapter
import com.sugar.weather.ui.CityPagesState
import com.sugar.weather.ui.HeaderUiState
import com.sugar.weather.ui.MainViewModel
import com.sugar.weather.ui.SceneUiState
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
import com.sugar.weather.ui.about.AboutActivity
import com.sugar.weather.ui.city.CityManageActivity
import com.sugar.weather.ui.search.CitySearchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 主页 View 层入口(DESIGN.md §6.2):
 * - 单实例 [WeatherBackdropView] 渲染当前选中城市的场景,ViewPager2 承载每城市一页;
 * - 头部 overlay(图钉 + 城市名/更新时间 + 告警占位 + 城市管理 + 溢出菜单)由 [HeaderUiState] 驱动;
 * - 定位:权限申请(ActivityResultContracts)→ LocationDataSource.currentLocation → Geocoder 命名
 *   → MainViewModel.submitLocatedPlace(§2.4);Activity 不直接写 Repository;
 * - 溢出菜单「动画调试」使用 M3 弹窗、下拉菜单与开关，确认后提交预览配置(§10.3)。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var pagesAdapter: CityPagesAdapter

    /** 调试文本最多 5Hz 更新一次，避免 50Hz 传感器事件频繁刷新 TextView。 */
    private var lastDebugUpdate = 0L

    /** 权限弹窗每 Activity 实例只请求一次,拒绝后停留在空状态。 */
    private var permissionAsked = false

    /** 一次性定位去重。 */
    private var locateInFlight = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) locate()
        }

    /** 城市管理/搜索页共用回传通道:结果带 cityId 时回主页并切到该城市(§6.1 导航)。 */
    private val cityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onCityPickerResult(result)
        }

    /** 建立共用动画背景、城市分页与安全区内的头部动作。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 只给覆盖层加系统栏内边距，背景 View 仍然铺满全屏。
        binding.uiOverlay.applySafeInsets(top = true, bottom = true)

        binding.weatherBackdrop.onSimulatedTiltChange = { pitch, roll ->
            viewModel.updateSimulatedTilt(pitch, roll)
        }

        pagesAdapter = CityPagesAdapter(supportFragmentManager, lifecycle)
        binding.viewPager.adapter = pagesAdapter
        binding.viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.selectPage(position)
            }
        })

        binding.imageCityManage.setOnClickListener {
            // 城市管理页(§6.1):返回 setResult(cityId) → onCityListChanged() 重建页序并切到该城市
            cityResultLauncher.launch(CityManageActivity.intent(this))
        }
        binding.imageMore.setOnClickListener { anchor -> showOverflowMenu(anchor) }
        binding.buttonAddCity.setOnClickListener {
            // 搜索添加页(§6.1):添加成功(repository.addCity 自动出页)后回主页切到新城市
            cityResultLauncher.launch(CitySearchActivity.intent(this))
        }

        initializeViewModel(savedInstanceState)
        observeState()
    }

    /**
     * 优先级：进程重建保存的状态 > adb/外部启动参数 > 默认晴天日间。
     * 具体的默认姿态、模拟开关由 ViewModel 统一处理。
     */
    private fun initializeViewModel(savedInstanceState: Bundle?) {
        // 调试预览:adb 带场景参数启动,或重建前用户在「动画调试」里选过场景。
        val previewing = savedInstanceState?.getBoolean(KEY_PREVIEW, false) == true ||
            intent.hasExtra(EXTRA_SCENE)
        val sceneName = savedInstanceState?.getString(KEY_SCENE)
            ?: intent.getStringExtra(EXTRA_SCENE)
            ?: SceneType.SUNNY.name
        val sceneType = SceneType.entries.firstOrNull {
            it.name.equals(sceneName, ignoreCase = true)
        } ?: SceneType.SUNNY
        val isNight = savedInstanceState?.getBoolean(KEY_NIGHT)
            ?: intent.getBooleanExtra(EXTRA_NIGHT, false)
        val showDebug = savedInstanceState?.getBoolean(KEY_DEBUG)
            ?: intent.getBooleanExtra(EXTRA_DEBUG, false)
        val simulate = when {
            savedInstanceState != null -> savedInstanceState.getBoolean(KEY_SIMULATE, false)
            intent.hasExtra(EXTRA_SIMULATE) -> intent.getBooleanExtra(EXTRA_SIMULATE, false)
            else -> null
        }
        val pitch = savedInstanceState?.getFloat(KEY_PITCH, Tilt.NEUTRAL_PITCH_DEG)
            ?: intent.getFloatExtra(EXTRA_PITCH, Tilt.NEUTRAL_PITCH_DEG)
        val roll = savedInstanceState?.getFloat(KEY_ROLL, Tilt.NEUTRAL_ROLL_DEG)
            ?: intent.getFloatExtra(EXTRA_ROLL, Tilt.NEUTRAL_ROLL_DEG)
        viewModel.initialize(sceneType, isNight, simulate, pitch, roll, showDebug, previewing)
    }

    /**
     * 低频状态(pages/header/backdrop/sceneState)和高频姿态分开收集:
     * 姿态可能来自 50Hz 传感器;页面状态只在翻页/刷新/翻页时变化。
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pages.collect { state -> renderPages(state) } }
                launch { viewModel.header.collect { state -> renderHeader(state) } }
                launch { viewModel.backdrop.collect { state -> renderBackdrop(state) } }
                launch { viewModel.sceneState.collect { state -> renderDebug(state) } }
                launch {
                    viewModel.tilt.collect { tilt ->
                        binding.weatherBackdrop.setTilt(tilt)
                        maybeUpdateDebug(tilt)
                    }
                }
            }
        }
    }

    /** 页序:空状态 ↔ ViewPager 互斥;城市身份(如定位城市插入 0 位)保持当前页不跳。 */
    private fun renderPages(state: CityPagesState) {
        val hasCities = state.cities.isNotEmpty()
        binding.emptyState.visibility = if (hasCities) View.GONE else View.VISIBLE
        binding.headerTitle.visibility = if (hasCities) View.VISIBLE else View.INVISIBLE
        binding.headerActions.visibility = if (hasCities) View.VISIBLE else View.INVISIBLE

        val previousCityId = pagesAdapter.cityIdAt(binding.viewPager.currentItem)
        pagesAdapter.submit(state.cities)
        if (!hasCities) return
        val targetIndex = previousCityId
            ?.let { id -> state.cities.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: state.selectedIndex
        if (binding.viewPager.currentItem != targetIndex) {
            binding.viewPager.setCurrentItem(targetIndex, false)
        }
    }

    /** 头部:城市名 + 图钉 + 更新时间。 */
    private fun renderHeader(state: HeaderUiState) {
        binding.textCityName.text = state.cityName
        binding.textUpdated.text = state.updatedText
        binding.imagePin.visibility = if (state.isLocated) View.VISIBLE else View.GONE
    }

    /** 背景跟随天气;调试预览激活时交由 renderDebug 驱动(§7.6)。 */
    private fun renderBackdrop(state: BackdropState) {
        if (viewModel.sceneState.value.previewing) return
        binding.weatherBackdrop.setScene(state.sceneType, state.isNight)
    }

    /** 把 ViewModel 的低频调试状态映射到背景 View 与调试文本。 */
    private fun renderDebug(state: SceneUiState) {
        // 触摸模拟开关跟随 ViewModel 状态(View 内部对相同值短路,不会打断进行中的动画)。
        binding.weatherBackdrop.simulateTilt = state.simulateTilt
        binding.textDebug.visibility = if (state.showDebug) View.VISIBLE else View.GONE
        if (state.previewing) {
            binding.weatherBackdrop.setScene(state.sceneType, state.isNight)
        } else {
            // 退出调试预览后立即恢复天气驱动背景(§7.6);backdrop 流只在天气变化时重发,这里补一次当前值。
            val backdrop = viewModel.backdrop.value
            binding.weatherBackdrop.setScene(backdrop.sceneType, backdrop.isNight)
        }
        if (state.showDebug) {
            lastDebugUpdate = 0L
            maybeUpdateDebug(viewModel.tilt.value)
        }
    }

    /** 调试文本节流更新，并标注数据来源是真实传感器还是触摸模拟。 */
    private fun maybeUpdateDebug(tilt: Tilt) {
        val state = viewModel.sceneState.value
        if (!state.showDebug) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDebugUpdate < DEBUG_INTERVAL_MS) return
        lastDebugUpdate = now
        val source = getString(
            if (state.simulateTilt) R.string.simulated else R.string.sensor
        )
        binding.textDebug.text = getString(
            R.string.debug_format,
            tilt.pitchDeg,
            tilt.rollDeg,
            source
        )
    }

    /**
     * 城市管理/搜索页返回(多页协作契约):
     * onCityListChanged() 重建页序(城市流本身已自动回流),结果带 cityId 时切到该城市;
     * 城市可能已被删除( indexOfFirst = -1)→ 只保持当前位置。
     */
    private fun onCityPickerResult(result: ActivityResult) {
        viewModel.onCityListChanged()
        val cityId = result.data?.getStringExtra(CityManageActivity.EXTRA_SELECTED_CITY_ID)
            ?: result.data?.getStringExtra(CitySearchActivity.EXTRA_ADDED_CITY_ID)
            ?: return
        val index = viewModel.pages.value.cities.indexOfFirst { it.id == cityId }
        if (index >= 0 && binding.viewPager.currentItem != index) {
            binding.viewPager.setCurrentItem(index, false)
        }
    }

    // ———————————————————————— 定位(§2.4 回退链) ————————————————————————

    /**
     * 进入页面时按需定位:
     * - 无任何城市(首启)→ 请求定位权限;拒绝则停留在空状态(D8:不预置假城市);
     * - 有城市但尚无定位城市且已授权 → 静默定位插入(§2.4 回退链第 2 条)。
     */
    private fun maybeLocate() {
        lifecycleScope.launch {
            // 等城市列表磁盘预热完成(冷启动瞬间为空),最多 1.5s,避免首启误判。
            val state = withTimeoutOrNull(CITY_LIST_WAIT_MS) {
                viewModel.pages.first { it.cities.isNotEmpty() }
            } ?: viewModel.pages.value
            val container = (application as WeatherApplication).container
            when {
                state.cities.isEmpty() -> {
                    if (permissionAsked) return@launch
                    permissionAsked = true
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                state.cities.none { it.isLocated } &&
                    container.locationDataSource.hasPermission() -> locate()
            }
        }
    }

    /** 一次性定位(Activity 按 ask 约定调 LocationDataSource);命名回退 Geocoder,失败交「我的位置」兜底。 */
    private fun locate() {
        if (locateInFlight) return
        locateInFlight = true
        lifecycleScope.launch {
            val container = (application as WeatherApplication).container
            val fix = runCatching { container.locationDataSource.currentLocation() }.getOrNull()
            if (fix is LocationFix.Fixed) {
                val name = withContext(Dispatchers.IO) {
                    resolvePlaceName(fix.latitude, fix.longitude)
                }
                viewModel.submitLocatedPlace(name, fix.latitude, fix.longitude)
            }
            locateInFlight = false
        }
    }

    /** 框架 Geocoder 反地理(§2.4 第 3 条):locality → subAdminArea → adminArea,任一步失败 → null(兜底名由 Repository 处理)。 */
    @Suppress("DEPRECATION")
    private fun resolvePlaceName(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        Geocoder(this, Locale.CHINA)
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { place -> place.locality ?: place.subAdminArea ?: place.adminArea }
    }.getOrNull()

    /** 使用 M3 弹窗上下文构建场景面板，确认后才把输入提交给 ViewModel。 */
    private fun showSceneDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        val dialogBinding = DialogScenePickerBinding.inflate(LayoutInflater.from(builder.context))
        val state = viewModel.sceneState.value

        val labels = SceneType.entries.map { it.label }
        var selectedScene = state.sceneType
        dialogBinding.dropdownScene.setSimpleItems(labels.toTypedArray())
        dialogBinding.dropdownScene.setText(selectedScene.label, false)
        dialogBinding.dropdownScene.setOnItemClickListener { _, _, position, _ ->
            selectedScene = SceneType.entries[position]
        }
        dialogBinding.switchNight.isChecked = state.isNight
        dialogBinding.switchSimulate.isChecked = state.simulateTilt
        dialogBinding.switchDebug.isChecked = state.showDebug

        if (!viewModel.hasTiltSensor) {
            dialogBinding.switchSimulate.isChecked = true
            dialogBinding.textHint.text = getString(
                R.string.scene_picker_no_sensor_hint,
                getString(R.string.no_sensor_hint),
                getString(R.string.scene_picker_hint)
            )
        }

        var resetRequested = false
        dialogBinding.buttonReset.setOnClickListener {
            // 只记录“待重置”，等用户点“应用”后再真正修改，取消时不影响当前画面。
            resetRequested = true
            dialogBinding.switchSimulate.isChecked = true
        }
        var restoreWeatherRequested = false
        dialogBinding.buttonRestoreWeather.setOnClickListener {
            // 只记录“待恢复”，等用户点“应用”后退出调试预览，取消时不影响当前画面。
            restoreWeatherRequested = true
        }

        val dialog = builder
            .setTitle(R.string.scene_picker_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.selectScene(
                    sceneType = selectedScene,
                    isNight = dialogBinding.switchNight.isChecked
                )
                if (resetRequested) {
                    viewModel.resetSimulatedTilt()
                } else {
                    viewModel.setSimulateTilt(dialogBinding.switchSimulate.isChecked)
                }
                viewModel.setDebugVisible(dialogBinding.switchDebug.isChecked)
                if (restoreWeatherRequested) {
                    viewModel.exitPreview()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** 头部溢出菜单(§6.2):关于 / 动画调试。 */
    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    // 关于页(§7):静态页,无结果回传
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }

                R.id.action_anim_debug -> {
                    showSceneDialog()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    override fun onStart() {
        super.onStart()
        // 传感器只在页面可见时注册，避免后台耗电。
        viewModel.startSensors()
        maybeLocate()
    }

    override fun onStop() {
        viewModel.stopSensors()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.weatherBackdrop.start()
    }

    override fun onPause() {
        binding.weatherBackdrop.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存最新 UI 状态和姿态，进程被回收后由 initializeViewModel 恢复。
        val state = viewModel.sceneState.value
        val tilt = viewModel.tilt.value
        outState.putString(KEY_SCENE, state.sceneType.name)
        outState.putBoolean(KEY_NIGHT, state.isNight)
        outState.putBoolean(KEY_SIMULATE, state.simulateTilt)
        outState.putFloat(KEY_PITCH, tilt.pitchDeg)
        outState.putFloat(KEY_ROLL, tilt.rollDeg)
        outState.putBoolean(KEY_DEBUG, state.showDebug)
        outState.putBoolean(KEY_PREVIEW, state.previewing)
    }

    companion object {
        /** adb/外部启动参数：场景枚举名，例如 SUNNY、RAIN。 */
        const val EXTRA_SCENE = "scene"

        /** adb/外部启动参数：是否夜间。 */
        const val EXTRA_NIGHT = "night"

        /** adb/外部启动参数：是否开启触摸模拟。 */
        const val EXTRA_SIMULATE = "simulate"

        /** adb/外部启动参数：触摸模拟的俯仰角。 */
        const val EXTRA_PITCH = "pitch"

        /** adb/外部启动参数：触摸模拟的横滚角。 */
        const val EXTRA_ROLL = "roll"

        /** adb/外部启动参数：是否显示调试信息。 */
        const val EXTRA_DEBUG = "debug"

        /** 进程重建时保存的场景名。 */
        private const val KEY_SCENE = "state_scene"

        /** 进程重建时保存的昼夜状态。 */
        private const val KEY_NIGHT = "state_night"

        /** 进程重建时保存的触摸模拟开关。 */
        private const val KEY_SIMULATE = "state_simulate"

        /** 进程重建时保存的俯仰角。 */
        private const val KEY_PITCH = "state_pitch"

        /** 进程重建时保存的横滚角。 */
        private const val KEY_ROLL = "state_roll"

        /** 进程重建时保存的调试开关。 */
        private const val KEY_DEBUG = "state_debug"

        /** 进程重建时保存的调试预览标记(背景是否跟随调试场景)。 */
        private const val KEY_PREVIEW = "state_preview"

        /** 调试数值刷新间隔，避免每帧格式化字符串。 */
        private const val DEBUG_INTERVAL_MS = 200L

        /** 等待城市列表磁盘预热的上限,超时按真实(可能为空)列表继续。 */
        private const val CITY_LIST_WAIT_MS = 1_500L
    }
}
