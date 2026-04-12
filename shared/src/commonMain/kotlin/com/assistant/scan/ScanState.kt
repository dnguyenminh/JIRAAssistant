package com.assistant.scan

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class ScanState(
    val projectKey: String,
    val status: ScanStatus,
    val totalTickets: Int,
    val processedCount: Int,
    val currentTicketId: String? = null,
    val ticketIds: List<String>,
    val startedAt: String,
    val updatedAt: String
) {
    val progressPercent: Int
        get() = when {
            status == ScanStatus.COMPLETED -> 100
            totalTickets > 0 -> ((processedCount.toDouble() / totalTickets) * 100).roundToInt()
            else -> 0
        }
}
