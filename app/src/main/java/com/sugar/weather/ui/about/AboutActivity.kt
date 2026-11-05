package com.sugar.weather.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sugar.weather.BuildConfig
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityAboutBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge

/** 关于页展示原创标识、构建版本及五份可离线阅读的说明文档。 */
class AboutActivity : AppCompatActivity() {

    /** 文档使用同一阅读页；版本直接读取 Gradle 生成的 BuildConfig。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(lightNavigationBar = true)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.applySafeInsets(top = true)
        binding.scrollContent.applySafeInsets(bottom = true)
        binding.buttonBack.setOnClickListener { finish() }
        binding.textVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)
        mapOf(
            binding.rowChangelog to LocalDocument.CHANGELOG,
            binding.rowUserGuide to LocalDocument.USER_GUIDE,
            binding.rowUserAgreement to LocalDocument.USER_AGREEMENT,
            binding.rowPrivacyPolicy to LocalDocument.PRIVACY_POLICY,
            binding.rowThirdParty to LocalDocument.THIRD_PARTY_NOTICES
        ).forEach { (row, document) ->
            row.setOnClickListener {
                startActivity(DocumentActivity.intent(context = this, document = document))
            }
        }
    }
}
