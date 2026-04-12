package com.assistant.frontend.services

import kotlinx.browser.document

/** Shared HTML utility functions used across pages. */
object HtmlUtils {

    fun escapeHtml(text: String): String {
        val div = document.createElement("div")
        div.appendChild(document.createTextNode(text))
        return div.innerHTML
    }

    fun formatNumber(n: Int): String {
        return n.asDynamic().toLocaleString() as String
    }
}
