package com.sugar.weather.ui.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sugar.weather.databinding.ActivityDocumentBinding
import com.sugar.weather.ui.applySafeInsets
import com.sugar.weather.ui.configureEdgeToEdge
import com.sugar.weather.ui.confirmAndOpenExternalLink
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 协议、隐私、版本历史及帮助共用的离线 Markdown 阅读页。 */
class DocumentActivity : AppCompatActivity() {
    /** 文章、重试与工具栏视图。 */
    private lateinit var binding: ActivityDocumentBinding

    /** 只允许清单内的文档；无效路由直接结束页面。 */
    private lateinit var document: LocalDocument

    /** 防止连续点击重试启动重复文件读取。 */
    private var loadJob: Job? = null

    /** 文档章节导航只依赖渲染后的正文与视图位置。 */
    private lateinit var navigation: DocumentNavigation

    /** 14sp 正文配合克制的标题倍率，所有网页链接先提示再使用浏览器标签页。 */
    private val markwon by lazy {
        Markwon.builder(this)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingTextSizeMultipliers(floatArrayOf(1.5f, 1.25f, 1.15f, 1.1f, 1f, 1f))
                }

                /** Markdown 网页链接统一使用带确认的浏览器入口。 */
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> confirmAndOpenExternalLink(link) }
                }
            })
            .build()
    }

    /** 安装固定工具栏与可滚动正文，离线读取不触发网络或定位权限。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = LocalDocument.entries.firstOrNull {
            it.name == intent.getStringExtra(EXTRA_DOCUMENT)
        }
        if (selected == null) {
            finish()
            return
        }
        document = selected
        configureEdgeToEdge(lightNavigationBar = true)
        binding = ActivityDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.applySafeInsets(top = true)
        binding.documentNavigation.applySafeInsets()
        binding.scrollContent.applySafeInsets(bottom = true)
        binding.textTitle.setText(document.titleRes)
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRetry.setOnClickListener { loadDocument() }
        navigation = DocumentNavigation(binding)
        loadDocument()
    }

    /** IO 线程读取包内正文，失败可重试，成功后安装统一链接与章节导航。 */
    private fun loadDocument() {
        if (loadJob?.isActive == true) return
        loadJob = lifecycleScope.launch {
            binding.progressLoading.isVisible = true
            binding.textError.isVisible = false
            binding.buttonRetry.isVisible = false
            val markdown = withContext(Dispatchers.IO) {
                try {
                    assets.open(document.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: IOException) {
                    null
                }
            }
            binding.progressLoading.isVisible = false
            val failed = markdown.isNullOrBlank()
            binding.textError.isVisible = failed
            binding.buttonRetry.isVisible = failed
            if (!failed) {
                markwon.setMarkdown(binding.textBody, markdown!!)
                binding.textBody.installDocumentLinks { link -> confirmAndOpenExternalLink(link) }
                navigation.render()
            }
        }
    }

    companion object {
        /** 页面内部文档路由键。 */
        private const val EXTRA_DOCUMENT = "local_document"

        /** 通过枚举构造入口，禁止传入未经验证的文件名。 */
        fun intent(context: Context, document: LocalDocument): Intent =
            Intent(context, DocumentActivity::class.java).putExtra(EXTRA_DOCUMENT, document.name)
    }
}
