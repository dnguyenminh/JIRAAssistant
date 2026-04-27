package com.assistant.frontend.models

import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import kotlinx.serialization.Serializable

/**
 * Active tab state for Ticket Intelligence result tabs.
 * Used by TicketStateManager for session persistence.
 * Requirements: 22.1-22.3, 23.1
 */
enum class TicketTab(val id: String, val label: String) {
    CONTEXT("context", "CONTEXT"),
    EVOLUTION("evolution", "EVOLUTION"),
    COMPLEXITY("complexity", "COMPLEXITY");

    companion object {
        fun fromId(id: String): TicketTab =
            entries.firstOrNull { it.id == id } ?: CONTEXT
    }
}

/**
 * Persisted state for Ticket Intelligence page.
 * Saved to sessionStorage for restore on navigation.
 * Requirements: 23.1, 23.2
 */
@Serializable
data class TicketPageState(
    val selectedTicketId: String = "",
    val selectedTicketSummary: String = "",
    val activeTab: String = "context",
    val analysisResult: AnalysisResponse? = null
)

/**
 * Display helper for confidence badge rendering.
 * Maps ExtractionConfidence to UI colors and labels.
 * Requirements: 22.4, 22.5
 */
data class ConfidenceDisplay(
    val label: String,
    val cssColor: String,
    val cssBg: String,
    val cssBorder: String,
    val showWarning: Boolean
) {
    companion object {
        fun from(confidence: ExtractionConfidence) = when (confidence) {
            ExtractionConfidence.HIGH -> ConfidenceDisplay(
                label = "HIGH",
                cssColor = "var(--primary)",
                cssBg = "rgba(45,254,207,0.1)",
                cssBorder = "rgba(45,254,207,0.3)",
                showWarning = false
            )
            ExtractionConfidence.MEDIUM -> ConfidenceDisplay(
                label = "MEDIUM",
                cssColor = "#ffb432",
                cssBg = "rgba(255,180,50,0.1)",
                cssBorder = "rgba(255,180,50,0.3)",
                showWarning = false
            )
            ExtractionConfidence.LOW -> ConfidenceDisplay(
                label = "LOW",
                cssColor = "#ff5050",
                cssBg = "rgba(255,80,80,0.1)",
                cssBorder = "rgba(255,80,80,0.3)",
                showWarning = true
            )
        }
    }
}

/**
 * Display helper for metadata badge rendering.
 * Requirements: 22.5
 */
data class MetadataBadgeInfo(
    val analyzedAt: String,
    val provider: String,
    val confidence: ConfidenceDisplay
)
