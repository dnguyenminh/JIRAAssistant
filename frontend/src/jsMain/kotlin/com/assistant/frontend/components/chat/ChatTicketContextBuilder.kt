package com.assistant.frontend.components.chat

import com.assistant.chat.TicketChatContext
import com.assistant.frontend.pages.ticket.TicketCombobox
import com.assistant.frontend.pages.ticket.TicketResultTabs
import kotlinx.browser.window

/**
 * Builds [TicketChatContext] from current Ticket Intelligence page state.
 * Mirrors [ChatGraphContextBuilder] pattern for ticket screen context.
 *
 * Requirements: 19.5, 22.1
 */
internal object ChatTicketContextBuilder {

    /** Return ticket context only when user is on ticket_intelligence page. */
    fun current(): TicketChatContext? {
        if (currentScreen() != "ticket_intelligence") return null
        val ticket = TicketCombobox.selectedTicket ?: return null
        return TicketChatContext(
            selectedTicketId = ticket.ticketId,
            ticketSummary = ticket.ticketSummary,
            analysisState = ticket.analysisState.name,
            hasAnalysisResult = TicketResultTabs.currentAnalysis != null,
            activeTab = TicketResultTabs.activeTab
        )
    }

    private fun currentScreen(): String =
        window.location.hash.removePrefix("#").ifBlank { "dashboard" }
}
