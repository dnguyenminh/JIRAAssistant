package com.assistant.frontend.pages.ticket

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Pure helper functions for document generation button state management.
 * Extracted from DocumentGenerationFlow to keep files under 200 lines.
 * Requirements: 8.1, 8.2, 8.4
 */
internal object DocGenButtonHelper {

    private val TERMINAL_STATUSES = listOf("COMPLETED", "FAILED", "CANCELLED")

    /** Pure logic: returns true if button should be enabled for given status. */
    fun shouldEnableButton(jobStatus: String): Boolean {
        return jobStatus in TERMINAL_STATUSES
    }

    fun buttonIdForDocType(docType: String): String? = when (docType) {
        "BRD" -> "btn-generate-brd"
        "FSD" -> "btn-generate-fsd"
        "REQUIREMENT_SLIDES" -> "btn-generate-slides"
        else -> null
    }

    fun progressAreaId(docType: String): String = when (docType) {
        "BRD" -> "brd-progress-area"
        "FSD" -> "fsd-progress-area"
        "REQUIREMENT_SLIDES" -> "slides-progress-area"
        else -> "brd-progress-area"
    }

    fun enableGenerateButton(docType: String) {
        val btnId = buttonIdForDocType(docType) ?: return
        val btn = document.getElementById(btnId) as? HTMLElement ?: return
        btn.removeAttribute("disabled")
        btn.style.opacity = "1"
        btn.style.cursor = "pointer"
    }

    fun enableCancelButton(docType: String) {
        val areaId = progressAreaId(docType)
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        val cancelBtn = area.querySelector(".btn-cancel-job") as? HTMLElement
        cancelBtn?.removeAttribute("disabled")
    }

    fun disableButton(btn: HTMLElement) {
        btn.setAttribute("disabled", "true")
        btn.style.opacity = "0.5"
        btn.style.cursor = "not-allowed"
    }

    fun enableButton(btn: HTMLElement) {
        btn.removeAttribute("disabled")
        btn.style.opacity = "1"
        btn.style.cursor = "pointer"
    }
}
