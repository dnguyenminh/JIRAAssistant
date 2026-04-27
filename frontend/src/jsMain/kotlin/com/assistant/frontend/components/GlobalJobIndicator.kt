package com.assistant.frontend.components

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GenerationJobDto
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Badge on navbar showing active job count.
 * Polls GET /api/jobs?status=active every 3s when jobs active.
 * Requirements: 4.1, 4.2, 4.4
 */
object GlobalJobIndicator {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var previousJobs: Map<String, String> = emptyMap()
    private const val POLL_MS = 3000L

    /** Render the indicator badge into nav-actions. */
    fun render(navActions: HTMLElement) {
        val badge = createBadgeElement()
        navActions.insertBefore(badge, navActions.firstChild)
        badge.addEventListener("click", { it.stopPropagation(); togglePanel() })
        startPolling()
    }

    /** Stop polling and clean up. */
    fun cleanup() {
        pollingJob?.cancel()
        pollingJob = null
        previousJobs = emptyMap()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch { pollLoop() }
    }

    private suspend fun pollLoop() {
        while (true) {
            try { fetchAndUpdate() } catch (_: Exception) { }
            delay(POLL_MS)
        }
    }

    private suspend fun fetchAndUpdate() {
        val resp = ApiClient.get("/api/jobs?status=active")
        if (ApiClient.handleUnauthorized(resp)) return
        val body = resp.bodyAsText()
        val jobs: List<GenerationJobDto> = json.decodeFromString(body)
        updateBadge(jobs.size)
        detectCompletedOrFailed(jobs)
        GlobalJobIndicatorPanel.updateJobs(jobs)
    }

    private fun detectCompletedOrFailed(currentJobs: List<GenerationJobDto>) {
        val currentMap = currentJobs.associate { it.jobId to it.status }
        for ((jobId, oldStatus) in previousJobs) {
            if (jobId !in currentMap && oldStatus in listOf("QUEUED", "RUNNING", "PAUSED")) {
                showCompletionToast(jobId)
            }
        }
        previousJobs = currentMap
    }

    private fun showCompletionToast(jobId: String) {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/jobs/$jobId")
                val job: GenerationJobDto = json.decodeFromString(resp.bodyAsText())
                notifyJobResult(job)
            } catch (_: Exception) { /* silent */ }
        }
    }

    private fun notifyJobResult(job: GenerationJobDto) {
        val msg = when (job.status) {
            "COMPLETED" -> "✅ ${job.documentType} cho ${job.ticketId} đã sinh xong"
            "FAILED" -> "❌ ${job.documentType} cho ${job.ticketId} thất bại: ${job.errorMessage ?: "Unknown error"}"
            "CANCELLED" -> "⚠️ ${job.documentType} cho ${job.ticketId} đã bị hủy"
            else -> return
        }
        val duration = if (job.status == "FAILED") 10000 else 5000
        showToastNotification(msg, duration, job.status == "FAILED")
    }

    private fun updateBadge(count: Int) {
        val badge = document.getElementById("global-job-badge") as? HTMLElement ?: return
        if (count > 0) {
            badge.style.display = "flex"
            badge.querySelector(".job-badge-count")?.textContent = "$count"
        } else {
            badge.style.display = "none"
        }
    }

    private fun togglePanel() {
        val panel = document.getElementById("global-job-panel") as? HTMLElement ?: return
        panel.style.display = if (panel.style.display == "none") "block" else "none"
    }

    private fun createBadgeElement(): HTMLElement {
        val badge = document.createElement("div") as HTMLElement
        badge.id = "global-job-badge"
        badge.className = "global-job-badge"
        badge.style.display = "none"
        val icon = document.createElement("span") as HTMLElement
        icon.textContent = "⚡"
        icon.className = "job-badge-icon"
        badge.appendChild(icon)
        val count = document.createElement("span") as HTMLElement
        count.className = "job-badge-count"
        count.textContent = "0"
        badge.appendChild(count)
        return badge
    }

    private fun showToastNotification(msg: String, duration: Int, isError: Boolean) {
        val toast = document.createElement("div") as HTMLElement
        toast.className = if (isError) "job-toast job-toast-error" else "job-toast job-toast-success"
        toast.textContent = msg
        if (isError) toast.addEventListener("click", { toast.remove() })
        document.body?.appendChild(toast)
        window.setTimeout({ toast.style.opacity = "1" }, 50)
        window.setTimeout({
            toast.style.opacity = "0"
            window.setTimeout({ toast.remove() }, 400)
        }, duration)
    }
}
