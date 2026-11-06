package com.sugar.weather.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sugar.weather.BuildConfig
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityAboutBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
import com.sugar.weather.ui.confirmAndOpenExternalLink

/** 关于页展示版本、作者公开资料及五份可离线阅读的说明文档。 */
class AboutActivity : AppCompatActivity() {

    /** 页面入口与二维码弹窗共用当前视图。 */
    private lateinit var binding: ActivityAboutBinding

    /** 文档使用同一阅读页；版本直接读取 Gradle 生成的 BuildConfig。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(surfaceBackground = true)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.applySafeInsets(top = true)
        binding.scrollContent.applySafeInsets(bottom = true)
        binding.buttonBack.setOnClickListener { finish() }
        binding.textVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)
        binding.rowGithub.setOnClickListener { confirmAndOpenExternalLink(DEVELOPER_GITHUB_URL) }
        binding.rowDouyin.setOnClickListener { showDouyinQr() }
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

    /** 沿用作者在拼图工坊提供的二维码，不根据抖音号猜测主页地址。 */
    private fun showDouyinQr() {
        val image = ImageView(this).apply {
            setImageResource(R.drawable.douyin_qr)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            maxHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            contentDescription = getString(R.string.about_douyin_qr)
            val padding = resources.getDimensionPixelSize(R.dimen.space_medium)
            setPadding(padding, padding, padding, padding)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_douyin)
            .setView(image)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.about_copy_douyin) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.about_douyin), DOUYIN_ACCOUNT))
                Snackbar.make(binding.root, R.string.about_douyin_copied, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private companion object {
        /** 与拼图工坊保持一致的公开作者资料。 */
        const val DEVELOPER_GITHUB_URL = "https://github.com/SiXuManYan"
        const val DOUYIN_ACCOUNT = "688021896"
    }
}
