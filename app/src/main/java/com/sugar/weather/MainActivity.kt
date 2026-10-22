package com.sugar.weather

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.sugar.weather.scene.SceneType
import com.sugar.weather.view.WeatherBackdropView

/**
 * 单 Activity 演示入口：全屏动态天气背景，右下角“场景”按钮用于切换天气、昼夜和传感器模式。
 * 支持通过 Intent extra 直接打开指定场景，方便截图与验收（见 README）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var backdropView: WeatherBackdropView
    private lateinit var debugText: TextView
    private lateinit var sceneLabel: TextView

    private var debugVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val debugUpdater = object : Runnable {
        override fun run() {
            if (!debugVisible) return
            val source = getString(if (backdropView.isSimulating) R.string.simulated else R.string.sensor)
            debugText.text = getString(
                R.string.debug_format,
                backdropView.tiltPitch,
                backdropView.tiltRoll,
                source
            )
            handler.postDelayed(this, 200L)
        }
    }

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

        setContentView(R.layout.activity_main)
        backdropView = findViewById(R.id.weather_backdrop)
        debugText = findViewById(R.id.text_debug)
        sceneLabel = findViewById(R.id.text_scene)

        val overlay = findViewById<View>(R.id.ui_overlay)
        // 只给覆盖层加系统栏内边距，背景 View 仍然铺满全屏。
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { view, insets ->
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

        restoreState(savedInstanceState)
        updateSceneLabel()
        setDebugVisible(debugVisible)
        findViewById<MaterialButton>(R.id.button_scene).setOnClickListener {
            showSceneDialog()
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        // 优先级：进程重建保存的状态 > adb/外部启动参数 > 默认晴天日间。
        val sceneName = savedInstanceState?.getString(KEY_SCENE)
            ?: intent.getStringExtra(EXTRA_SCENE)
            ?: SceneType.SUNNY.name
        val type = SceneType.entries.firstOrNull { it.name.equals(sceneName, ignoreCase = true) }
            ?: SceneType.SUNNY
        val night = savedInstanceState?.getBoolean(KEY_NIGHT)
            ?: intent.getBooleanExtra(EXTRA_NIGHT, false)
        backdropView.setScene(type, night)

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_SIMULATE, backdropView.isSimulating)) {
                backdropView.setSimulatedTilt(
                    savedInstanceState.getFloat(KEY_PITCH, 90f),
                    savedInstanceState.getFloat(KEY_ROLL, 0f)
                )
            } else {
                backdropView.simulateTilt = false
            }
            debugVisible = savedInstanceState.getBoolean(KEY_DEBUG, false)
        } else {
            if (intent.hasExtra(EXTRA_SIMULATE)) {
                if (intent.getBooleanExtra(EXTRA_SIMULATE, false)) {
                    backdropView.setSimulatedTilt(
                        intent.getFloatExtra(EXTRA_PITCH, 90f),
                        intent.getFloatExtra(EXTRA_ROLL, 0f)
                    )
                } else {
                    backdropView.simulateTilt = false
                }
            }
            debugVisible = intent.getBooleanExtra(EXTRA_DEBUG, false)
        }
    }

    private fun updateSceneLabel() {
        val dayPart = getString(if (backdropView.isNight) R.string.night else R.string.day)
        sceneLabel.text = getString(
            R.string.scene_format,
            backdropView.currentScene.label,
            dayPart
        )
    }

    private fun setDebugVisible(visible: Boolean) {
        debugVisible = visible
        debugText.visibility = if (visible) View.VISIBLE else View.GONE
        handler.removeCallbacks(debugUpdater)
        if (visible) {
            debugUpdater.run()
        }
    }

    private fun showSceneDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_scene_picker, null)
        val spinner = content.findViewById<Spinner>(R.id.spinner_scene)
        val labels = SceneType.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(backdropView.currentScene.ordinal)

        val nightSwitch = content.findViewById<SwitchCompat>(R.id.switch_night)
        nightSwitch.isChecked = backdropView.isNight

        val simulateSwitch = content.findViewById<SwitchCompat>(R.id.switch_simulate)
        simulateSwitch.isChecked = backdropView.isSimulating

        val debugSwitch = content.findViewById<SwitchCompat>(R.id.switch_debug)
        debugSwitch.isChecked = debugVisible

        if (!backdropView.hasTiltSensor) {
            simulateSwitch.isChecked = true
            content.findViewById<TextView>(R.id.text_hint).text =
                getString(R.string.no_sensor_hint) + "\n" + getString(R.string.scene_picker_hint)
        }

        var resetRequested = false
        content.findViewById<MaterialButton>(R.id.button_reset).setOnClickListener {
            // 只记录“待重置”，等用户点“应用”后再真正修改，取消时不影响当前画面。
            resetRequested = true
            simulateSwitch.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scene_picker_title)
            .setView(content)
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = SceneType.entries[spinner.selectedItemPosition]
                val wantNight = nightSwitch.isChecked
                val wantSimulate = simulateSwitch.isChecked
                // 先切换模拟/重置姿态，再换场景，避免新场景用旧姿态做首帧。
                if (resetRequested) {
                    backdropView.resetSimulatedTilt()
                } else if (wantSimulate != backdropView.isSimulating) {
                    backdropView.simulateTilt = wantSimulate
                }
                backdropView.setScene(selected, wantNight)
                setDebugVisible(debugSwitch.isChecked)
                updateSceneLabel()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // 帧循环和传感器只在可见时运行。
        backdropView.start()
        if (debugVisible) {
            debugUpdater.run()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(debugUpdater)
        backdropView.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCENE, backdropView.currentScene.name)
        outState.putBoolean(KEY_NIGHT, backdropView.isNight)
        outState.putBoolean(KEY_SIMULATE, backdropView.isSimulating)
        outState.putFloat(KEY_PITCH, backdropView.tiltPitch)
        outState.putFloat(KEY_ROLL, backdropView.tiltRoll)
        outState.putBoolean(KEY_DEBUG, debugVisible)
    }

    companion object {
        const val EXTRA_SCENE = "scene"
        const val EXTRA_NIGHT = "night"
        const val EXTRA_SIMULATE = "simulate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_ROLL = "roll"
        const val EXTRA_DEBUG = "debug"

        private const val KEY_SCENE = "state_scene"
        private const val KEY_NIGHT = "state_night"
        private const val KEY_SIMULATE = "state_simulate"
        private const val KEY_PITCH = "state_pitch"
        private const val KEY_ROLL = "state_roll"
        private const val KEY_DEBUG = "state_debug"
    }
}
