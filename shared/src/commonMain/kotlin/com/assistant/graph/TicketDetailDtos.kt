package com.assistant.graph

import kotlinx.serialization.Serializable

/**
 * DTO cho linked ticket hiển thị trong Ticket Information Panel.
 * Requirements: 20.3, 20.4
 */
@Serializable
data class LinkedTicketDTO(
    val key: String,           // e.g. "ICL2-100"
    val summary: String,
    val relationship: String   // e.g. "blocks", "is blocked by", "relates to"
)

/**
 * DTO cho sub-task hiển thị trong Ticket Information Panel.
 * Requirements: 20.5, 20.6
 */
@Serializable
data class SubTaskDTO(
    val key: String,           // e.g. "ICL2-101"
    val summary: String,
    val status: String         // e.g. "To Do", "In Progress", "Done"
)
