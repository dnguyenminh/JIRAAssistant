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
            Triple("Consolidating Ticket Metadata...", 0, 40),
            Triple("AI RE-ANALYZING SCOPE...", 40, 85),
            Triple("SYNCING TO KNOWLEDGE BASE...", 85, 95)
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
        val phaseLabel = when (status.phase) {
            "METADATA" -> "Consolidating Ticket Metadata..."
            "AI_ANALYZING" -> "AI RE-ANALYZING SCOPE..."
            "KB_SYNCING" -> "SYNCING TO KNOWLEDGE BASE..."
            "COMPLETE" -> "Analysis Complete"
            else -> status.phase
        }
        setPhase(phaseLabel)
        setPercent(status.progressPercent)
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
