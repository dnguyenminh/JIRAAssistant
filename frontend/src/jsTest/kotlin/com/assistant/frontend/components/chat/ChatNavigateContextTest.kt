package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.frontend.services.NavigationContext
import kotlinx.browser.window
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests that ChatActionHandler "navigate" action stores context
 * for destination pages to consume.
 *
 * Requirements: 10.1, 10.2
 */
class ChatNavigateContextTest {

    @BeforeTest
    fun setup() {
        NavigationContext.clear()
        window.location.hash = ""
    }

    @Test
    fun navigateWithTicketKeyStoresContext() {
        val action = ChatAction(
            type = "navigate",
            label = "Ticket Intelligence",
            params = mapOf(
                "screen" to "ticket_intelligence",
                "ticketKey" to "ICL2-24"
            )
        )
        // Execute navigate — this stores context + sets hash
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("ticket_intelligence")
        assertNotNull(ctx, "Context should be stored for destination")
        assertEquals("ICL2-24", ctx["ticketKey"])
    }

    @Test
    fun navigateWithoutExtraParamsDoesNotStoreContext() {
        val action = ChatAction(
            type = "navigate",
            label = "Dashboard",
            params = mapOf("screen" to "dashboard")
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("dashboard")
        assertNull(ctx, "No context when only screen param present")
    }

    @Test
    fun navigateScreenParamExcludedFromContext() {
        val action = ChatAction(
            type = "navigate",
            label = "Analysis",
            params = mapOf(
                "screen" to "analysis",
                "ticketKey" to "TK-99"
            )
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.consume("analysis")
        assertNotNull(ctx)
        assertNull(ctx["screen"], "screen param should not be in context")
        assertEquals("TK-99", ctx["ticketKey"])
    }

    @Test
    fun navigateWithMultipleContextParams() {
        val action = ChatAction(
            type = "navigate",
            label = "Knowledge Graph",
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

    @Test
    fun navigateWithBlankScreenDoesNotStoreContext() {
        val action = ChatAction(
            type = "navigate",
            label = "Nowhere",
            params = mapOf("screen" to "")
        )
        ChatActionHandler.execute(action, null, {}, {})

        val ctx = NavigationContext.peek()
        assertNull(ctx, "No context stored when screen is blank")
    }
}
