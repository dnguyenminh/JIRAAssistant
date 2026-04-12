package com.assistant.frontend.components.chat

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.svg.SVGCircleElement

/**
 * SVG circular progress indicator for context window usage.
 * Requirements: 19.24, 19.25
 */
object ContextIndicator {

    private const val CIRCUMFERENCE = 100.0

    fun update(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val circle = document.getElementById("ctx-progress-circle")
        val label = document.getElementById("ctx-percent")
        val container = document.getElementById("context-indicator") as? HTMLElement

        circle?.setAttribute("stroke-dasharray", "$clamped $CIRCUMFERENCE")
        label?.textContent = "$clamped%"

        container?.classList?.remove("warning", "danger")
        when {
            clamped > 95 -> {
                container?.classList?.add("danger")
                container?.title = "Context gần đầy. Hãy xóa lịch sử hoặc bắt đầu cuộc hội thoại mới."
            }
            clamped > 80 -> {
                container?.classList?.add("warning")
                container?.title = "Context usage: $clamped%"
            }
            else -> container?.title = "Context usage: $clamped%"
        }
    }
}
