package com.sugar.weather.ui.about

import android.graphics.Rect
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.sugar.weather.R
import com.sugar.weather.databinding.ActivityDocumentBinding
import io.noties.markwon.core.spans.HeadingSpan

/** 基于渲染后的标题偏移提供章节跳转，避免 Markdown 标记和重复标题造成定位偏差。 */
internal class DocumentNavigation(private val binding: ActivityDocumentBinding) {
    /** 字符偏移与胶囊视图一一对应，正文重试时统一重建。 */
    private val anchors = mutableListOf<Pair<Int, TextView>>()

    /** 只在当前章节变化时横向跟随，保留用户手动浏览目录的操作。 */
    private var activeAnchor: TextView? = null

    init {
        binding.buttonBackToTop.setOnClickListener { binding.scrollContent.smoothScrollTo(0, 0) }
        binding.scrollContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateActiveAnchor() }
        )
    }

    /** 二级标题作为章节；没有二级标题的短文档退回一级标题。 */
    fun render() {
        anchors.clear()
        activeAnchor = null
        binding.documentAnchorRow.removeAllViews()
        val rendered = binding.textBody.text as? Spanned ?: return
        val headings = rendered.getSpans(0, rendered.length, HeadingSpan::class.java)
        val chapters = headings.filter { it.level == 2 }.ifEmpty {
            headings.filter { it.level == 1 }
        }
        chapters.sortedBy(rendered::getSpanStart).forEach { heading ->
            val offset = rendered.getSpanStart(heading)
            val title = rendered.subSequence(offset, rendered.getSpanEnd(heading)).toString().trim()
            val chip = createAnchor(title = title, offset = offset)
            binding.documentAnchorRow.addView(chip)
            anchors += offset to chip
        }
        binding.documentNavigation.isVisible = anchors.isNotEmpty()
        binding.textBody.doOnLayout { updateActiveAnchor() }
    }

    /** 保持 48dp 触控高度，字号与胶囊视觉尺寸独立设置。 */
    private fun createAnchor(title: String, offset: Int): TextView = TextView(binding.root.context).apply {
        text = title
        setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.document_anchor_text_size)
        )
        setTextColor(ContextCompat.getColorStateList(context, R.color.document_anchor_text))
        setBackgroundResource(R.drawable.bg_document_anchor)
        gravity = Gravity.CENTER
        minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
        val horizontal = resources.getDimensionPixelSize(R.dimen.space_medium)
        val vertical = resources.getDimensionPixelSize(R.dimen.space_small)
        setPaddingRelative(horizontal, vertical, horizontal, vertical)
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = vertical }
        setOnClickListener { scrollToAnchor(offset) }
    }

    /** 用 TextView 实际行位置定位，兼容换行、系统字体缩放及屏幕宽度变化。 */
    private fun scrollToAnchor(offset: Int) {
        val body = binding.textBody
        val layout = body.layout ?: return
        val gap = body.resources.getDimensionPixelSize(R.dimen.space_small)
        val target = body.top + layout.getLineTop(layout.getLineForOffset(offset)) - gap
        binding.scrollContent.smoothScrollTo(0, target.coerceAtLeast(0))
    }

    /** 滚动正文时同步选中章节，目录只在选中项改变后自动滚入可见区域。 */
    private fun updateActiveAnchor() {
        if (anchors.isEmpty()) return
        val body = binding.textBody
        val layout = body.layout ?: return
        val threshold = binding.scrollContent.scrollY +
            body.resources.getDimensionPixelSize(R.dimen.space_medium)
        val active = anchors.lastOrNull { (offset, _) ->
            body.top + layout.getLineTop(layout.getLineForOffset(offset)) <= threshold
        }?.second ?: anchors.first().second
        anchors.forEach { (_, chip) -> chip.isSelected = chip === active }
        if (activeAnchor === active) return
        activeAnchor = active
        binding.documentAnchorRow.doOnLayout {
            if (activeAnchor === active) {
                active.requestRectangleOnScreen(Rect(0, 0, active.width, active.height), true)
            }
        }
    }
}
