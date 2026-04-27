package com.assistant.server.chat

import com.assistant.chat.TicketChatContext

/**
 * Build ticket state context string for AI prompt.
 * Extracted from ChatServiceImpl for file size compliance.
 * Requirements: 19.5, 22.1
 */
internal object ChatTicketStateContext {

    /** Build context from Ticket Intelligence screen state. */
    fun build(tc: TicketChatContext?): String {
        if (tc == null) return "User is NOT on the Ticket Intelligence page."
        val parts = mutableListOf("User is viewing the Ticket Intelligence page.")
        parts.add(
            "The user has selected Jira ticket \"${tc.selectedTicketId}\" " +
            "(summary: \"${tc.ticketSummary}\"). " +
            "\"${tc.selectedTicketId}\" is the COMPLETE Jira issue key. " +
            "Use this EXACT value when calling any Jira or KB tools. " +
            "Analysis state: ${tc.analysisState}."
        )
        if (tc.hasAnalysisResult) {
            parts.add("Analysis results are displayed, active tab: ${tc.activeTab}.")
        }
        parts.add(
            "When user asks about 'the current ticket', 'ticket đang mở', " +
            "'ticket đang chọn', or similar — they mean \"${tc.selectedTicketId}\". " +
            "ALWAYS use get_ticket_info or getJiraIssue with this key to answer."
        )
        return parts.joinToString(" ")
    }
}
