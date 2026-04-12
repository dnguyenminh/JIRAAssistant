package com.assistant.frontend.models

import com.assistant.scan.ScanStatus
import kotlinx.serialization.Serializable

/** Shared scan status response — used by Dashboard, Analysis, KnowledgeGraph. */
@Serializable
data class ScanStatusResponse(
    val projectKey: String = "",
    val status: ScanStatus = ScanStatus.IDLE,
    val totalTickets: Int = 0,
    val processedCount: Int = 0,
    val progressPercent: Int = 0,
    val currentTicketId: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val recentLog: List<ScanLogEntryDTO> = emptyList()
)

@Serializable
data class ScanLogEntryDTO(
    val id: Long = 0,
    val ticketId: String = "",
    val status: String = "",
    val message: String = "",
    val timestamp: String = ""
)
