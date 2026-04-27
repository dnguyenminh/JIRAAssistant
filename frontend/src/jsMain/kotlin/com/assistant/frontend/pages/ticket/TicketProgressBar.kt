package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.AnalysisStatus
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLElement

/**
 * Progress bar simulation and status-driven updates.
 */
internal object TicketProgressBar {

    suspend fun simulateProgress() {
        val phases = listOf(
            Triple("Fetching Jira Data...", 0, 20),
            Triple("Extracting Content...", 20, 35),
            Triple("AI Analyzing Scope...", 35, 85),
            Triple("Syncing to Knowledge Base...", 85, 95)
        )
        for ((label, start, end) in phases) {
            setPhase(label)
            for (p in start..end) {
                setPercent(p)
                delay(((10000.0 / 95) * 1).toLong())
            }
        }
    }

    fun updateFromStatus(status: AnalysisStatus) {
        setPhase(phaseLabel(status.phase))
        setPercent(status.progressPercent)
    }

    private fun phaseLabel(phase: String): String = when (phase) {
        "FETCHING_JIRA", "METADATA" -> "Fetching Jira Data..."
        "EXTRACTING_CONTENT" -> "Extracting Content..."
        "AI_ANALYZING" -> "AI Analyzing Scope..."
        "KB_SYNCING" -> "Syncing to Knowledge Base..."
        "COMPLETE" -> "Analysis Complete"
        else -> phase
    }

    fun show() {
        (document.getElementById("ti-progress-section") as? HTMLElement)?.style?.display = "block"
        setPercent(0)
        setPhase("Consolidating Ticket Metadata...")
    }

    fun hide() {
        (document.getElementById("ti-progress-section") as? HTMLElement)?.style?.display = "none"
    }

    fun complete() {
        setPercent(100)
        setPhase("Analysis Complete")
    }

    private fun setPhase(label: String) {
        (document.getElementById("ti-progress-phase") as? HTMLElement)?.textContent = label
    }

    private fun setPercent(percent: Int) {
        (document.getElementById("ti-progress-bar") as? HTMLElement)?.style?.width = "$percent%"
        (document.getElementById("ti-progress-percent") as? HTMLElement)?.textContent = "$percent%"
    }
}
