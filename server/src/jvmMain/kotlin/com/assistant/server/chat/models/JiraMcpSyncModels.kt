package com.assistant.server.chat.models

/**
 * Result of a Jira MCP graph sync operation.
 * Requirements: 18.1, 18.2, 18.3, 18.4, 18.5
 */
data class SyncResult(
    val success: Boolean,
    val syncType: SyncType,
    val ticketKey: String? = null,
    val warningMessage: String? = null
)

/** Types of Jira MCP sync operations. */
enum class SyncType {
    CREATE_TICKET, UPDATE_TICKET, LINK_TICKETS, NONE
}
