package com.assistant.server.chat

import com.assistant.chat.TicketChatContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ChatTicketStateContext].
 * Requirements: 19.5, 22.1
 */
class ChatTicketStateContextTest {

    @Test
    fun `null context returns NOT on Ticket Intelligence page`() {
        val result = ChatTicketStateContext.build(null)
        assertTrue(result.contains("NOT on the Ticket Intelligence page"))
    }

    @Test
    fun `selected ticket ID appears in context`() {
        val tc = TicketChatContext(
            selectedTicketId = "ICL2-1135",
            ticketSummary = "[CRP-3250][ETL] Clear force read",
            analysisState = "SCANNED"
        )
        val result = ChatTicketStateContext.build(tc)
        assertTrue(result.contains("ICL2-1135"))
        assertTrue(result.contains("[CRP-3250][ETL] Clear force read"))
        assertTrue(result.contains("SCANNED"))
    }

    @Test
    fun `context includes instruction to use ticket key for tools`() {
        val tc = TicketChatContext(
            selectedTicketId = "PROJ-42",
            ticketSummary = "Test ticket"
        )
        val result = ChatTicketStateContext.build(tc)
        assertTrue(result.contains("PROJ-42"))
        assertTrue(result.contains("get_ticket_info"))
        assertTrue(result.contains("getJiraIssue"))
    }

    @Test
    fun `analysis result displayed adds active tab info`() {
        val tc = TicketChatContext(
            selectedTicketId = "ICL2-100",
            ticketSummary = "Some ticket",
            hasAnalysisResult = true,
            activeTab = "complexity"
        )
        val result = ChatTicketStateContext.build(tc)
        assertTrue(result.contains("complexity"))
        assertTrue(result.contains("Analysis results are displayed"))
    }

    @Test
    fun `no analysis result omits tab info`() {
        val tc = TicketChatContext(
            selectedTicketId = "ICL2-100",
            ticketSummary = "Some ticket",
            hasAnalysisResult = false
        )
        val result = ChatTicketStateContext.build(tc)
        assertFalse(result.contains("Analysis results are displayed"))
    }

    @Test
    fun `Vietnamese context hints are included`() {
        val tc = TicketChatContext(
            selectedTicketId = "ABC-1",
            ticketSummary = "Test"
        )
        val result = ChatTicketStateContext.build(tc)
        assertTrue(result.contains("ticket đang mở"))
        assertTrue(result.contains("ticket đang chọn"))
    }
}
