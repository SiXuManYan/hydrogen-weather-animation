package com.sugar.weather

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import com.sugar.weather.data.locatedPlaceName
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
import com.sugar.weather.widget.WeatherWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

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

    /** 空城市首次请求权限，重建时保留已请求标记，拒绝后停留在空状态。 */
    private var permissionAsked = false

    /** 当前可见期间的定位任务，离开主页后取消系统监听。 */
    private var locationJob: Job? = null

    /** 等待城市磁盘快照的任务，避免多次进入时重复判断权限。 */
    private var locationPreparationJob: Job? = null

    /** 仅观察手势首指，不接管分页、下拉刷新或逐时横向滚动。 */
    private var simulatedPointerId = MotionEvent.INVALID_POINTER_ID

    /** 模拟拖动开始的横坐标，使用窗口坐标避免子 View 滚动影响。 */
    private var simulatedStartX = 0f

    /** 模拟拖动开始的纵坐标。 */
    private var simulatedStartY = 0f

    /** 模拟拖动开始时的姿态，移动量始终相对同一基准计算。 */
    private var simulatedStartTilt = Tilt()

    /** 小于系统触摸容差的位移视为普通点击。 */
    private val simulatedTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    /** 微件目标先暂存，等磁盘城市加载完毕再切页。 */
    private var pendingWidgetCityId: String? = null

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
        // 主题背景保留给启动阶段，运行时由全幅天气 Canvas 提供背景。
        window.setBackgroundDrawable(null)
        permissionAsked = savedInstanceState?.getBoolean(KEY_PERMISSION_ASKED, false) == true

        // 只给覆盖层加系统栏内边距，背景 View 仍然铺满全屏。
        binding.uiOverlay.applySafeInsets(top = true, bottom = true)

        pagesAdapter = CityPagesAdapter(supportFragmentManager, lifecycle)
        binding.viewPager.adapter = pagesAdapter
        binding.viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.selectPage(position)
            }
        })

        binding.imageCityManage.setOnClickListener {
            // 城市管理页返回后等待存储快照，再重建页序并切到所选城市。
            cityResultLauncher.launch(CityManageActivity.intent(this))
        }
        binding.imageMore.setOnClickListener { anchor -> showOverflowMenu(anchor) }
        binding.buttonAddCity.setOnClickListener {
            // 搜索添加页(§6.1):添加成功(repository.addCity 自动出页)后回主页切到新城市
            cityResultLauncher.launch(CitySearchActivity.intent(this))
        }

        initializeViewModel(savedInstanceState)
        if (intent.hasExtra(WeatherWidgetProvider.EXTRA_CITY_ID)) viewModel.exitPreview()
        pendingWidgetCityId = intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_ID)
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
        val widgetIndex = pendingWidgetCityId?.let { id -> state.cities.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        if (widgetIndex != null) pendingWidgetCityId = null
        val targetIndex = widgetIndex ?: previousCityId
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
        if (!state.simulateTilt) simulatedPointerId = MotionEvent.INVALID_POINTER_ID
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
     * 等待最新存储快照重建页序，结果带 cityId 时平滑切到该城市；
     * 城市可能已被删除( indexOfFirst = -1)→ 只保持当前位置。
     */
    private fun onCityPickerResult(result: ActivityResult) {
        lifecycleScope.launch {
            val state = try {
                viewModel.syncCityListFromStorage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 读取失败时保留当前页，避免结果回调导致主页崩溃。
                return@launch
            }
            // 结果可能先于共享城市流到达；用同一快照先补齐适配器，再按标识切页。
            renderPages(state)
            if (result.resultCode != RESULT_OK) return@launch
            val cityId = result.data?.getStringExtra(CityManageActivity.EXTRA_SELECTED_CITY_ID)
                ?: result.data?.getStringExtra(CitySearchActivity.EXTRA_ADDED_CITY_ID)
                ?: return@launch
            val index = state.cities.indexOfFirst { it.id == cityId }
            if (index >= 0 && binding.viewPager.currentItem != index) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }
    }

    /** 在窗口入口旁路观察模拟拖动，原始事件继续完整分发给现有控件。 */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::binding.isInitialized) observeSimulatedTilt(event)
        return super.dispatchTouchEvent(event)
    }

    /** 只更新姿态，不消费事件或要求父容器停止拦截，保留原有滚动手势。 */
    private fun observeSimulatedTilt(event: MotionEvent) {
        if (!viewModel.sceneState.value.simulateTilt) {
            simulatedPointerId = MotionEvent.INVALID_POINTER_ID
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                simulatedPointerId = event.getPointerId(0)
                simulatedStartX = event.x
                simulatedStartY = event.y
                simulatedStartTilt = viewModel.tilt.value
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(simulatedPointerId)
                if (pointerIndex < 0) return
                val dx = event.getX(pointerIndex) - simulatedStartX
                val dy = event.getY(pointerIndex) - simulatedStartY
                val width = binding.weatherBackdrop.width
                val height = binding.weatherBackdrop.height
                if (width <= 0 || height <= 0 ||
                    max(abs(dx), abs(dy)) < simulatedTouchSlop
                ) return
                viewModel.updateSimulatedTilt(
                    pitchDeg = simulatedStartTilt.pitchDeg - dy / height * TOUCH_FULL_SCALE_DEG,
                    rollDeg = simulatedStartTilt.rollDeg + dx / width * TOUCH_FULL_SCALE_DEG
                )
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 首指抬起后不跳到另一根手指，避免姿态突然跳变。
                if (event.getPointerId(event.actionIndex) == simulatedPointerId) {
                    simulatedPointerId = MotionEvent.INVALID_POINTER_ID
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                simulatedPointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
    }

    // ———————————————————————— 定位(§2.4 回退链) ————————————————————————

    /**
     * 进入页面时按需定位:
     * - 无任何城市(首启)→ 请求定位权限;拒绝则停留在空状态(D8:不预置假城市);
     * - 已授权时静默定位，已有定位城市按成功 / 失败节流规则检查移动；
     * - 有手动城市但尚未授权时不再次弹权限，定位始终只发生在前台。
     */
    private fun maybeLocate() {
        if (locationPreparationJob?.isActive == true) return
        locationPreparationJob = lifecycleScope.launch {
            // 读取真实磁盘快照，空列表不再与尚未完成预热的占位混淆。
            val cities = try {
                viewModel.awaitCitiesForLocation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 无法读取存储时不把未知状态当作首启，也不因此重复申请权限。
                return@launch
            }
            val container = (application as WeatherApplication).container
            when {
                container.locationDataSource.hasPermission() -> locate()
                cities.isEmpty() -> {
                    if (permissionAsked) return@launch
                    permissionAsked = true
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }

    /** 前台一次性定位；原地也补齐区县名，足够大的位移才更新坐标和天气。 */
    private fun locate() {
        if (locationJob?.isActive == true || !viewModel.beginLocationCheck()) return
        locationJob = lifecycleScope.launch {
            var succeeded = false
            try {
                val container = (application as WeatherApplication).container
                val fix = container.locationDataSource.currentLocation()
                if (fix !is LocationFix.Fixed) return@launch
                val locatedCity = viewModel.awaitCitiesForLocation().firstOrNull { it.isLocated }
                if (locatedCity != null) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        locatedCity.latitude, locatedCity.longitude,
                        fix.latitude, fix.longitude, distance
                    )
                    // 大致定位的精度半径也计入阈值，避免正常漂移反复清除天气缓存。
                    if (distance[0] < max(LOCATION_MOVE_THRESHOLD_M, fix.accuracyMeters)) {
                        // 按保存坐标补齐区县，避免粗略位置漂移使名称与天气地点不一致。
                        val name = withContext(Dispatchers.IO) {
                            resolvePlaceName(
                                latitude = locatedCity.latitude,
                                longitude = locatedCity.longitude
                            )
                        }
                        succeeded = viewModel.updateLocatedPlaceName(city = locatedCity, name = name)
                        return@launch
                    }
                }
                val name = withContext(Dispatchers.IO) {
                    resolvePlaceName(latitude = fix.latitude, longitude = fix.longitude)
                }
                succeeded = viewModel.submitLocatedPlace(
                    name = name,
                    latitude = fix.latitude,
                    longitude = fix.longitude
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 系统服务或存储暂不可用时保留当前城市，下次前台按失败间隔重试。
            } finally {
                viewModel.finishLocationCheck(succeeded = succeeded)
            }
        }
    }

    /** 系统 Geocoder 优先解析区县；字段选择交给纯规则，失败回退名由仓库处理。 */
    @Suppress("DEPRECATION")
    private fun resolvePlaceName(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        Geocoder(this, Locale.CHINA)
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { place ->
                locatedPlaceName(
                    subLocality = place.subLocality,
                    locality = place.locality,
                    subAdminArea = place.subAdminArea,
                    adminArea = place.adminArea
                )
            }
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

    /** 头部溢出菜单：关于、系统微件添加入口和动画调试。 */
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

                R.id.action_add_widget -> {
                    val manager = AppWidgetManager.getInstance(this)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(ComponentName(this, WeatherWidgetProvider::class.java), null, null)
                    } else {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.widget_pin)
                            .setMessage(R.string.widget_pin_hint)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /** 已打开的应用收到微件点击后复用主页，并切到微件对应的城市。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetCityId = intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_ID)
        if (intent.hasExtra(WeatherWidgetProvider.EXTRA_CITY_ID)) viewModel.exitPreview()
        renderPages(viewModel.pages.value)
    }

    override fun onStart() {
        super.onStart()
        // 传感器只在页面可见时注册，避免后台耗电。
        viewModel.startSensors()
        maybeLocate()
    }

    /** 离开主页可见阶段即取消定位，并停止传感器监听。 */
    override fun onStop() {
        locationPreparationJob?.cancel()
        locationJob?.cancel()
        viewModel.stopSensors()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.weatherBackdrop.start()
    }

    /** 暂停渲染时清除模拟手势，不把旧手指状态带到下一次恢复。 */
    override fun onPause() {
        simulatedPointerId = MotionEvent.INVALID_POINTER_ID
        binding.weatherBackdrop.stop()
        super.onPause()
    }

    /** 保存预览、姿态和权限请求标记，系统重建后继续使用同一状态。 */
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
        outState.putBoolean(KEY_PERMISSION_ASKED, permissionAsked)
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

        /** 权限请求状态跨页面重建保留，拒绝后不会因旋转再次弹窗。 */
        private const val KEY_PERMISSION_ASKED = "state_permission_asked"

        /** 调试数值刷新间隔，避免每帧格式化字符串。 */
        private const val DEBUG_INTERVAL_MS = 200L

        /** 整个窗口宽 / 高的拖动量分别对应 180° 横滚 / 俯仰。 */
        private const val TOUCH_FULL_SCALE_DEG = 180f

        /** 城市天气忽略公里内的细小移动；大致定位还需超出其精度半径。 */
        private const val LOCATION_MOVE_THRESHOLD_M = 1_000f
    }
}
