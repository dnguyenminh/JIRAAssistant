package com.assistant.scan

import kotlinx.serialization.Serializable

@Serializable
enum class ScanLogStatus {
    ANALYZING, COMPLETED, FAILED
}

@Serializable
data class ScanLogEntry(
    val id: Long = 0,
    val projectKey: String,
    val ticketId: String,
    val status: ScanLogStatus,
    val message: String,
    val timestamp: String
)
