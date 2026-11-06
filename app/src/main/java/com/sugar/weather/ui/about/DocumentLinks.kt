package com.sugar.weather.ui.about

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView

/** 显式 Markdown 链接与裸网址共用确认入口，避免默认 URLSpan 绕过外链提示。 */
internal fun TextView.installDocumentLinks(onOpen: (String) -> Unit) {
    val linked = SpannableStringBuilder(text)
    // Linkify 会移除已有 URLSpan，先保存显式链接的真实目标，再补充裸网址。
    val explicit = linked.getSpans(0, linked.length, URLSpan::class.java).map { span ->
        DocumentLink(span, linked.getSpanStart(span), linked.getSpanEnd(span), linked.getSpanFlags(span))
    }
    Linkify.addLinks(linked, Linkify.WEB_URLS)
    explicit.forEach { saved ->
        linked.getSpans(saved.start, saved.end, URLSpan::class.java).forEach(linked::removeSpan)
        linked.setSpan(saved.span, saved.start, saved.end, saved.flags)
    }
    linked.getSpans(0, linked.length, URLSpan::class.java).forEach { span ->
        val start = linked.getSpanStart(span)
        val end = linked.getSpanEnd(span)
        val flags = linked.getSpanFlags(span)
        linked.removeSpan(span)
        linked.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) { onOpen(span.url) }
        }, start, end, flags)
    }
    text = linked
    movementMethod = LinkMovementMethod.getInstance()
}

/** 保存 Linkify 处理前的显式链接位置与样式标记。 */
private data class DocumentLink(val span: URLSpan, val start: Int, val end: Int, val flags: Int)
