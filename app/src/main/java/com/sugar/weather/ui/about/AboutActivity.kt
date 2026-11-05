package com.sugar.weather.ui.about

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityAboutBinding
import com.sugar.weather.di.AppContainer
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge

/**
 * 关于页(DESIGN.md §7,截图 005425):白底居中云朵 logo +「氢天气 V{版本}」+ 更新日志 +
 * 数据来源署名(Open-Meteo CC-BY 4.0、中国区前 5 天 CMA、白天逐小时聚合)+ 隐私政策摘要。
 * 静态页面:无 ViewModel、无状态(§8.2),版本号取 [AppContainer.APP_VERSION]。
 */
class AboutActivity : AppCompatActivity() {

    /** 展示版本与说明，保证底部隐私入口可完整滚入安全区。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(lightNavigationBar = true)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.applySafeInsets(top = true)
        binding.scrollContent.applySafeInsets(bottom = true)

        binding.buttonBack.setOnClickListener { finish() }
        binding.textPrivacyPolicy.setOnClickListener { showPrivacyPolicy() }
        binding.textVersion.text = getString(R.string.about_version_format, AppContainer.APP_VERSION)
    }

    /** 隐私政策:应用内摘要;完整政策见仓库 docs/PRIVACY.md。 */
    private fun showPrivacyPolicy() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_privacy_policy)
            .setMessage(R.string.about_privacy_summary)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
