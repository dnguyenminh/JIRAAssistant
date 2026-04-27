package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.GeneratedDocumentMeta
import com.assistant.frontend.models.GenerationJobDto
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Renders approval badges and inline progress bars for DocumentGenerationSection.
 * Extracted to keep DocumentGenerationSection under 200 lines.
 * Requirements: 9.4, 9.5, 9.6, 9.7
 */
internal object DocGenBadgeRenderer {

    fun renderBadges(docs: List<GeneratedDocumentMeta>, onClick: (String) -> Unit) {
        renderBadgeFor("brd-badge-area", "BRD", "btn-generate-brd", docs, onClick)
        renderBadgeFor("fsd-badge-area", "FSD", "btn-generate-fsd", docs, onClick)
        renderBadgeFor("slides-badge-area", "REQUIREMENT_SLIDES", "btn-generate-slides", docs, onClick)
    }

    fun renderInlineProgress(activeJobs: List<GenerationJobDto>) {
        renderProgressFor("brd-progress-area", "BRD", activeJobs)
        renderProgressFor("fsd-progress-area", "FSD", activeJobs)
        renderProgressFor("slides-progress-area", "REQUIREMENT_SLIDES", activeJobs)
    }

    private fun renderBadgeFor(
        areaId: String, docType: String, btnId: String,
        docs: List<GeneratedDocumentMeta>, onClick: (String) -> Unit
    ) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.innerHTML = ""
        val meta = docs.find { it.documentType == docType } ?: return
        val badge = cloneApprovalBadge(meta) ?: return
        area.appendChild(badge)
        updateButtonLabel(btnId, docType)
        badge.addEventListener("click", { onClick(docType) })
        if (meta.approvalStatus == "APPROVED" && meta.hasDraft) {
            val draftBadge = cloneDraftBadge() ?: return
            area.appendChild(draftBadge)
            draftBadge.addEventListener("click", { openDraftDocument(docType) })
        }
    }

    private fun cloneDraftBadge(): HTMLElement? {
        val tmpl = document.getElementById("tmpl-approval-badge") as? HTMLTemplateElement ?: return null
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return null
        val label = el.querySelector(".approval-badge-label") as? HTMLElement
        val detail = el.querySelector(".approval-badge-detail") as? HTMLElement
        el.className = "approval-badge approval-draft"
        label?.textContent = "DRAFT"
        detail?.textContent = "Pending review"
        return el
    }

    private fun openDraftDocument(docType: String) {
        val ticketId = TicketCombobox.selectedTicket?.ticketId ?: return
        DocumentGenerationFlow.fetchDraftAndPreview(ticketId, docType)
    }

    private fun cloneApprovalBadge(meta: GeneratedDocumentMeta): HTMLElement? {
        val tmpl = document.getElementById("tmpl-approval-badge") as? HTMLTemplateElement ?: return null
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return null
        val label = el.querySelector(".approval-badge-label") as? HTMLElement
        val detail = el.querySelector(".approval-badge-detail") as? HTMLElement
        applyBadgeStyle(el, label, detail, meta)
        return el
    }

    private fun applyBadgeStyle(
        el: HTMLElement, label: HTMLElement?, detail: HTMLElement?, meta: GeneratedDocumentMeta
    ) {
        when (meta.approvalStatus) {
            "DRAFT" -> {
                el.className = "approval-badge approval-draft"
                label?.textContent = "DRAFT"
            }
            "APPROVED" -> {
                el.className = "approval-badge approval-approved"
                label?.textContent = "APPROVED v${meta.versionNumber ?: ""}"
            }
            "REJECTED" -> {
                el.className = "approval-badge approval-rejected"
                label?.textContent = "REJECTED"
            }
            else -> {
                el.className = "approval-badge"
                label?.textContent = "GENERATED"
            }
        }
        detail?.textContent = formatTimestamp(meta.generatedAt)
    }

    private fun renderProgressFor(areaId: String, docType: String, jobs: List<GenerationJobDto>) {
        val job = jobs.find { it.documentType == docType }
        if (job == null) {
            InlineProgressRenderer.clearProgress(areaId)
            return
        }
        InlineProgressRenderer.renderProgress(areaId, job)
    }

    private fun updateButtonLabel(btnId: String, docType: String) {
        val btn = document.getElementById(btnId) as? HTMLElement ?: return
        btn.textContent = when (docType) {
            "BRD" -> "RE-GENERATE BRD"
            "FSD" -> "RE-GENERATE FSD"
            "REQUIREMENT_SLIDES" -> "RE-GENERATE SLIDES"
            else -> return
        }
    }

    private fun formatTimestamp(iso: String): String {
        return try {
            val d = js("new Date(iso)")
            "${d.toLocaleDateString() as String} ${d.toLocaleTimeString() as String}"
        } catch (_: dynamic) { iso }
    }
}
