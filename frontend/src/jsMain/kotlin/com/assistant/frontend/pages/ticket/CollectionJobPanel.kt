package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.CollectionJobItemResponse
import com.assistant.frontend.models.CollectionJobResponse
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Collection Job monitoring panel renderer on Ticket Intelligence page.
 * Displays active Collection_Jobs with progress bars and item lists.
 *
 * Requirements: 13.7, 13.12
 */
internal object CollectionJobPanel {

    /** Render the panel with the given jobs list. */
    fun render(jobs: List<CollectionJobResponse>) {
        val panel = el("ti-collection-jobs-panel") ?: return
        val jobList = el("cj-job-list") ?: return
        val empty = el("cj-empty") ?: return
        val status = el("cj-overall-status") ?: return

        if (jobs.isEmpty()) {
            panel.style.display = "none"; return
        }

        panel.style.display = "block"
        jobList.innerHTML = ""
        empty.style.display = "none"

        val activeCount = jobs.count { it.isActive }
        status.textContent = if (activeCount > 0) "$activeCount active" else "All complete"

        for (job in jobs) {
            val card = createJobCard(job) ?: continue
            jobList.appendChild(card)
        }
    }

    /** Hide the panel. */
    fun hide() {
        el("ti-collection-jobs-panel")?.style?.display = "none"
    }

    private fun createJobCard(job: CollectionJobResponse): HTMLElement? {
        val tmpl = document.getElementById("tmpl-cj-job") as? HTMLTemplateElement ?: return null
        val card = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return null

        card.querySelector(".cj-job-icon")?.textContent = jobIcon(job.jobType)
        card.querySelector(".cj-job-type")?.textContent = job.jobTypeLabel
        applyStatusBadge(card, job.status)

        val fill = card.querySelector(".cj-progress-fill") as? HTMLElement
        fill?.style?.width = "${job.progressPercent}%"

        card.querySelector(".cj-job-summary")?.textContent = job.summary
        card.querySelector(".cj-job-percent")?.textContent = "${job.progressPercent}%"

        val itemList = card.querySelector(".cj-item-list") as? HTMLElement
        if (itemList != null) renderItems(itemList, job.items)
        return card
    }

    private fun applyStatusBadge(card: HTMLElement, status: String) {
        val el = card.querySelector(".cj-job-status") as? HTMLElement ?: return
        el.textContent = status
        el.className = "cj-job-status ${statusCssClass(status)}"
    }

    private fun renderItems(container: HTMLElement, items: List<CollectionJobItemResponse>) {
        container.innerHTML = ""
        val tmpl = document.getElementById("tmpl-cj-item") as? HTMLTemplateElement ?: return
        for (item in items) {
            val row = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: continue
            row.querySelector(".cj-item-icon")?.textContent = item.statusIcon
            row.querySelector(".cj-item-id")?.textContent = item.itemId
            row.querySelector(".cj-item-status")?.textContent = item.status
            container.appendChild(row)
        }
    }

    private fun el(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement

    private fun jobIcon(jobType: String): String = when (jobType) {
        "LINKED_TICKET_ANALYSIS" -> "🔗"
        "ATTACHMENT_PROCESSING" -> "📎"
        else -> "📋"
    }

    private fun statusCssClass(status: String): String = when (status) {
        "QUEUED" -> "cj-status-queued"
        "RUNNING" -> "cj-status-running"
        "COMPLETED" -> "cj-status-completed"
        "FAILED" -> "cj-status-failed"
        else -> ""
    }
}
