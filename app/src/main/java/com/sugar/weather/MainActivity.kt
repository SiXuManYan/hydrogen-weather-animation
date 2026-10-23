package com.sugar.weather

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sugar.weather.databinding.ActivityMainBinding
import com.sugar.weather.databinding.DialogScenePickerBinding
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.ui.MainViewModel
import com.sugar.weather.ui.SceneUiState
import kotlinx.coroutines.launch

/**
 * View 层入口：
 * - ViewBinding 负责布局绑定，不再使用 findViewById；
 * - 观察 MainViewModel 的 StateFlow，把场景/昼夜/模拟状态交给 WeatherBackdropView；
 * - 把触摸模拟产生的姿态回传给 ViewModel；
 * - 场景面板只负责收集用户输入，提交给 ViewModel 处理。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    /** 调试文本最多 5Hz 更新一次，避免 50Hz 传感器事件频繁刷新 TextView。 */
    private var lastDebugUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏绘制到状态栏和导航栏后面；系统栏透明，图标保持白色。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 只给覆盖层加系统栏内边距，背景 View 仍然铺满全屏。
        ViewCompat.setOnApplyWindowInsetsListener(binding.uiOverlay) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        binding.weatherBackdrop.onSimulatedTiltChange = { pitch, roll ->
            viewModel.updateSimulatedTilt(pitch, roll)
        }
        binding.buttonScene.setOnClickListener {
            showSceneDialog()
        }

        initializeViewModel(savedInstanceState)
        observeState()
    }

    /**
     * 优先级：进程重建保存的状态 > adb/外部启动参数 > 默认晴天日间。
     * 具体的默认姿态、模拟开关由 ViewModel 统一处理。
     */
    private fun initializeViewModel(savedInstanceState: Bundle?) {
        // 优先级：进程重建保存的状态 > adb/外部启动参数 > 默认晴天日间。
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
        viewModel.initialize(sceneType, isNight, simulate, pitch, roll, showDebug)
    }

    /**
     * 低频场景状态和高频姿态分开收集：
     * 场景状态只在用户操作时变化，姿态可能来自 50Hz 传感器；分开可以避免整页 UI 重组。
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 低频 UI 状态和 50Hz 姿态分开收集，避免每帧刷新标签/开关。
                launch {
                    viewModel.sceneState.collect { state -> render(state) }
                }
                launch {
                    viewModel.tilt.collect { tilt ->
                        binding.weatherBackdrop.setTilt(tilt)
                        maybeUpdateDebug(tilt)
                    }
                }
            }
        }
    }

    /** 把 ViewModel 的低频状态映射到 ViewBinding 和背景 View。 */
    private fun render(state: SceneUiState) {
        binding.weatherBackdrop.setScene(state.sceneType, state.isNight)
        binding.weatherBackdrop.simulateTilt = state.simulateTilt
        binding.textDebug.visibility = if (state.showDebug) View.VISIBLE else View.GONE
        val dayPart = getString(if (state.isNight) R.string.night else R.string.day)
        binding.textScene.text = getString(
            R.string.scene_format,
            state.sceneType.label,
            dayPart
        )
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

    /** 场景面板只负责收集输入，状态变更全部提交给 ViewModel。 */
    private fun showSceneDialog() {
        val dialogBinding = DialogScenePickerBinding.inflate(layoutInflater)
        val state = viewModel.sceneState.value

        val labels = SceneType.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerScene.adapter = adapter
        dialogBinding.spinnerScene.setSelection(state.sceneType.ordinal)
        dialogBinding.switchNight.isChecked = state.isNight
        dialogBinding.switchSimulate.isChecked = state.simulateTilt
        dialogBinding.switchDebug.isChecked = state.showDebug

        if (!viewModel.hasTiltSensor) {
            dialogBinding.switchSimulate.isChecked = true
            dialogBinding.textHint.text =
                getString(R.string.no_sensor_hint) + "\n" + getString(R.string.scene_picker_hint)
        }

        var resetRequested = false
        dialogBinding.buttonReset.setOnClickListener {
            // 只记录“待重置”，等用户点“应用”后再真正修改，取消时不影响当前画面。
            resetRequested = true
            dialogBinding.switchSimulate.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scene_picker_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = SceneType.entries[dialogBinding.spinnerScene.selectedItemPosition]
                viewModel.selectScene(selected, dialogBinding.switchNight.isChecked)
                if (resetRequested) {
                    viewModel.resetSimulatedTilt()
                } else {
                    viewModel.setSimulateTilt(dialogBinding.switchSimulate.isChecked)
                }
                viewModel.setDebugVisible(dialogBinding.switchDebug.isChecked)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        // 传感器只在页面可见时注册，避免后台耗电。
        viewModel.startSensors()
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

        /** 调试数值刷新间隔，避免每帧格式化字符串。 */
        private const val DEBUG_INTERVAL_MS = 200L
    }
}
