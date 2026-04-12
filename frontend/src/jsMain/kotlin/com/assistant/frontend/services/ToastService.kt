package com.assistant.frontend.services

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/** Shared toast notification service. */
object ToastService {

    fun show(message: String, type: String = "success") {
        val toast = document.createElement("div") as HTMLElement
        val bgColor = if (type == "success")
            "rgba(45,254,207,0.15)" else "rgba(255,59,48,0.15)"
        val borderColor = if (type == "success")
            "rgba(45,254,207,0.3)" else "rgba(255,59,48,0.3)"
        val textColor = if (type == "success")
            "var(--primary)" else "var(--danger)"

        toast.style.cssText = buildToastCss(bgColor, borderColor, textColor)
        toast.textContent = message
        document.body?.appendChild(toast)

        window.setTimeout({
            toast.style.opacity = "0"
            window.setTimeout({ toast.remove() }, 300)
        }, 3000)
    }

    private fun buildToastCss(
        bg: String, border: String, color: String
    ): String = """
        position:fixed;bottom:24px;right:24px;z-index:3000;
        padding:14px 24px;border-radius:12px;
        background:$bg;border:1px solid $border;
        color:$color;font-size:13px;letter-spacing:0.5px;
        backdrop-filter:blur(12px);
        animation:fadeInUp 0.3s ease;
        transition:opacity 0.3s ease;
    """.trimIndent()
}
