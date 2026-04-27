package com.assistant.server.agent.ba.models

import kotlinx.serialization.Serializable

/**
 * Payload keys for BA Agent's AgentInput.payload map.
 */
object BAAgentPayload {
    const val TICKET_ID = "ticketId"
    const val DOC_TYPE = "docType"       // "BRD", "FSD", "SLIDES"
    const val CLI_BACKEND = "cliBackend" // "gemini", "copilot", "kiro", "ollama"
    const val JOB_ID = "jobId"
}

/**
 * Serializable snapshot of a CollectionStrategy's configuration.
 * Requirements: 4.1, 4.2, 4.3
 */
@Serializable
data class CollectionStrategyConfig(
    val docType: String,
    val sufficiencyThreshold: Double,
    val maxPromptChars: Int,
    val maxLinkedTicketDepth: Int,
    val prioritizedSlots: List<String>,
    val relevanceThreshold: Double = 0.3
)

/**
 * Result of Master Prompt assembly from structured memory.
 * Requirements: 5.1, 5.6
 */
@Serializable
data class MasterPromptResult(
    val prompt: String,
    val promptSizeChars: Int,
    val sourceTicketIds: List<String>,
    val memorySlotsUsed: List<String>,
    val compressionRatio: Double,
    val truncationApplied: Boolean = false
)

/**
 * Relevance score for a linked ticket during Expand phase.
 * Requirements: 4.4, 4.5
 */
@Serializable
data class RelevanceScore(
    val ticketId: String,
    val score: Double,
    val issueType: String,
    val relationshipType: String,
    val included: Boolean
)
