package com.assistant.frontend.components

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GenerationJobDto
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Dropdown panel listing active jobs with pause/resume/cancel buttons.
 * Requirements: 4.3, 4.5, 4.6, 3.6
 */
object GlobalJobIndicatorPanel {

    private val scope = MainScope()

    /** Update the panel with current active jobs. */
    fun updateJobs(jobs: List<GenerationJobDto>) {
        val list = document.getElementById("global-job-list") as? HTMLElement ?: return
        val empty = document.getElementById("global-job-empty") as? HTMLElement
        val countLabel = document.getElementById("global-job-count-label") as? HTMLElement
        list.innerHTML = ""
        countLabel?.textContent = "${jobs.size} job(s)"
        if (jobs.isEmpty()) {
            empty?.style?.display = "block"
            return
        }
        empty?.style?.display = "none"
        jobs.forEach { job -> list.appendChild(createJobItem(job)) }
    }

    private fun createJobItem(job: GenerationJobDto): HTMLElement {
        val tmpl = document.getElementById("tmpl-job-indicator-item") as? HTMLTemplateElement
        val el = tmpl?.content?.firstElementChild?.cloneNode(true) as? HTMLElement
            ?: return createFallbackItem(job)
        populateJobItem(el, job)
        bindJobActions(el, job)
        return el
    }

    private fun populateJobItem(el: HTMLElement, job: GenerationJobDto) {
        el.querySelector(".job-indicator-ticket")?.textContent = job.ticketId
        el.querySelector(".job-indicator-type")?.textContent = job.documentType
        el.querySelector(".job-indicator-status")?.textContent = job.status
        val fill = el.querySelector(".job-indicator-progress-fill") as? HTMLElement
        fill?.style?.width = "${job.progressPercent}%"
    }

    private fun bindJobActions(el: HTMLElement, job: GenerationJobDto) {
        val pauseBtn = el.querySelector(".btn-job-pause") as? HTMLElement
        val cancelBtn = el.querySelector(".btn-job-cancel") as? HTMLElement
        if (job.status == "PAUSED") {
            pauseBtn?.textContent = "▶"
            pauseBtn?.title = "Resume"
            pauseBtn?.addEventListener("click", { resumeJob(job.jobId) })
        } else if (job.status == "QUEUED") {
            pauseBtn?.addEventListener("click", { pauseJob(job.jobId) })
        } else {
            pauseBtn?.style?.display = "none"
        }
        if (job.status in listOf("QUEUED", "PAUSED", "RUNNING")) {
            cancelBtn?.addEventListener("click", { cancelJob(job.jobId) })
        } else {
            cancelBtn?.style?.display = "none"
        }
    }

    private fun pauseJob(jobId: String) {
        scope.launch {
            try {
                ApiClient.post("/api/jobs/$jobId/pause")
            } catch (_: Exception) { /* handled by polling */ }
        }
    }

    private fun resumeJob(jobId: String) {
        scope.launch {
            try {
                ApiClient.post("/api/jobs/$jobId/resume")
            } catch (_: Exception) { /* handled by polling */ }
        }
    }

    private fun cancelJob(jobId: String) {
        scope.launch {
            try {
                ApiClient.post("/api/jobs/$jobId/cancel")
            } catch (_: Exception) { /* handled by polling */ }
        }
    }

    private fun createFallbackItem(job: GenerationJobDto): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "job-indicator-item"
        el.textContent = "${job.ticketId} — ${job.documentType} (${job.status})"
        return el
    }
}
