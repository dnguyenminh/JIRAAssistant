package com.assistant.frontend.components

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Reusable blocking overlay for async operations.
 * Prevents double-clicks and shows progress feedback.
 * Requirements: 20.1 (UX Standards — loading feedback)
 */
object BlockingOverlay {

    private const val OVERLAY_CLASS = "blocking-overlay"

    /** Show blocking overlay inside a container element. */
    fun show(containerId: String, message: String = "Processing...") {
        val container = document.getElementById(containerId) as? HTMLElement ?: return
        remove(containerId)
        container.style.position = "relative"
        val overlay = document.createElement("div") as HTMLElement
        overlay.className = OVERLAY_CLASS
        overlay.id = "${containerId}-blocking"
        appendSpinner(overlay)
        appendMessage(overlay, message)
        container.appendChild(overlay)
    }

    /** Remove blocking overlay from container. */
    fun remove(containerId: String) {
        document.getElementById("${containerId}-blocking")?.remove()
    }

    private fun appendSpinner(overlay: HTMLElement) {
        val spinner = document.createElement("div") as HTMLElement
        spinner.className = "blocking-spinner"
        overlay.appendChild(spinner)
    }

    private fun appendMessage(overlay: HTMLElement, message: String) {
        val msg = document.createElement("div") as HTMLElement
        msg.className = "blocking-message"
        msg.textContent = message
        overlay.appendChild(msg)
    }
}
