package com.sugar.weather.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityAboutBinding
import com.sugar.weather.di.AppContainer

/**
 * 关于页(DESIGN.md §7,截图 005425):白底居中云朵 logo +「氢天气 V{版本}」+ 更新日志 +
 * 数据来源署名(Open-Meteo CC-BY 4.0 合规必须)+ 底部占位文案。
 * 静态页面:无 ViewModel、无状态(§8.2),版本号取 [AppContainer.APP_VERSION]。
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 深色工具栏延伸到状态栏后(白图标),内容区白底 → 导航栏手势用深色前景。
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
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

        binding.buttonBack.setOnClickListener { finish() }
        binding.textVersion.text = getString(R.string.about_version_format, AppContainer.APP_VERSION)
    }
}
