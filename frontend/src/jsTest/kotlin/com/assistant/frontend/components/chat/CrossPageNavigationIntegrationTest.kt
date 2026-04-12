package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.frontend.services.NavigationContext
import kotlinx.browser.window
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration test: Cross-page navigation with context passing.
 *
 * Verifies the full flow: ChatActionHandler "navigate" action →
 * NavigationContext stores context → destination page can consume it.
 *
 * Requirements: 10.1, 15.1
 */
class CrossPageNavigationIntegrationTest {

    @BeforeTest
    fun setup() {
        NavigationContext.clear()
        window.location.hash = ""
    }

    // -- Navigate stores context and changes hash -----------

    @Test
    fun navigateWithTicketKeyStoresAndConsumes() {
        val action = ChatAction(
            type = "navigate", label = "Ticket Intelligence",
            params = mapOf(
                "screen" to "ticket_intelligence",
                "ticketKey" to "ICL2-24"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("ticket_intelligence")
        assertNotNull(ctx)
        assertEquals("ICL2-24", ctx["ticketKey"])
    }

    @Test
    fun navigateToAnalysisWithTicketKey() {
        val action = ChatAction(
            type = "navigate", label = "Analysis",
            params = mapOf(
                "screen" to "analysis",
                "ticketKey" to "PROJ-42"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("analysis")
        assertNotNull(ctx)
        assertEquals("PROJ-42", ctx["ticketKey"])
        assertNull(ctx["screen"], "screen excluded from context")
    }

    @Test
    fun navigateWithoutExtraParamsNoContext() {
        val action = ChatAction(
            type = "navigate", label = "Dashboard",
            params = mapOf("screen" to "dashboard")
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("dashboard")
        assertNull(ctx, "No context when only screen param")
    }

    // -- Context consumed once (one-shot) -------------------

    @Test
    fun contextConsumedOnlyOnce() {
        val action = ChatAction(
            type = "navigate", label = "Graph",
            params = mapOf(
                "screen" to "knowledge_graph",
                "ticketKey" to "ICL2-50"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val first = NavigationContext.consume("knowledge_graph")
        assertNotNull(first)

        val second = NavigationContext.consume("knowledge_graph")
        assertNull(second, "Context cleared after first consume")
    }

    // -- Mismatched screen returns null ---------------------

    @Test
    fun consumeWrongScreenReturnsNull() {
        val action = ChatAction(
            type = "navigate", label = "Analysis",
            params = mapOf(
                "screen" to "analysis",
                "ticketKey" to "TK-1"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("dashboard")
        assertNull(ctx, "Wrong screen should return null")
    }

    // -- Multiple params preserved --------------------------

    @Test
    fun multipleContextParamsPreserved() {
        val action = ChatAction(
            type = "navigate", label = "Graph",
            params = mapOf(
                "screen" to "knowledge_graph",
                "ticketKey" to "ICL2-50",
                "filter" to "FEATURE"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("knowledge_graph")
        assertNotNull(ctx)
        assertEquals("ICL2-50", ctx["ticketKey"])
        assertEquals("FEATURE", ctx["filter"])
        assertEquals(2, ctx.size)
    }

    // -- Sequential navigations overwrite context -----------

    @Test
    fun sequentialNavigationsOverwriteContext() {
        val first = ChatAction(
            type = "navigate", label = "Analysis",
            params = mapOf(
                "screen" to "analysis",
                "ticketKey" to "OLD"
            )
        )
        ChatActionHandler.execute(first, null, {}, {})

        val second = ChatAction(
            type = "navigate", label = "Analysis",
            params = mapOf(
                "screen" to "analysis",
                "ticketKey" to "NEW"
            )
        )
        ChatActionHandler.execute(second, null, {}, {})

        val ctx = NavigationContext.consume("analysis")
        assertNotNull(ctx)
        assertEquals("NEW", ctx["ticketKey"])
    }

    // -- Graph action navigate stores pending action --------

    @Test
    fun graphActionFromOtherPageNavigatesToGraph() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "ICL2-24")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
        // Hash changed to knowledge_graph
        assertTrue(
            window.location.hash.contains("knowledge_graph")
        )
    }

    // -- Blank screen does not store context ----------------

    @Test
    fun blankScreenDoesNotStoreContext() {
        val action = ChatAction(
            type = "navigate", label = "Nowhere",
            params = mapOf("screen" to "")
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.peek()
        assertNull(ctx, "No context for blank screen")
    }
}
