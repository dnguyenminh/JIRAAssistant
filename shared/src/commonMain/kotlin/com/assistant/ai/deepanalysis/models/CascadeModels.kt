package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Status of a single cascade log entry.
 * Maps to log prefixes: [DISCOVERED], [ANALYZING], [COMPLETED], [SKIPPED], [FAILED], [CASCADE], [DONE].
 * Requirements: 26.9
 */
@Serializable
enum class CascadeLogStatus {
    DISCOVERED,
    ANALYZING,
    COMPLETED,
    SKIPPED,
    FAILED,
    CASCADE,
    DONE
}

/**
 * Overall state of the cascading analysis process.
 * Requirements: 26.8
 */
@Serializable
enum class CascadeStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * A single log entry from the cascading analysis process.
 * Requirements: 26.8, 26.9
 */
@Serializable
data class CascadeLogEntry(
    val status: CascadeLogStatus = CascadeLogStatus.DISCOVERED,
    val ticketKey: String = "",
    val message: String = "",
    val timestamp: String = ""
)

/**
 * Overall result of cascading analysis.
 * Contains log entries and progress counters for the frontend panel.
 * Requirements: 26.8, 26.9
 */
@Serializable
data class CascadeResult(
    val status: CascadeStatus = CascadeStatus.IDLE,
    val logEntries: List<CascadeLogEntry> = emptyList(),
    val totalTickets: Int = 0,
    val completedTickets: Int = 0,
    val failedTickets: Int = 0
)
