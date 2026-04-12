package com.assistant.frontend.services

import kotlinx.browser.window
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for NavigationContext — cross-page context passing.
 *
 * Requirements: 10.1, 10.2
 */
class NavigationContextTest {

    @BeforeTest
    fun setup() {
        NavigationContext.clear()
    }

    @Test
    fun consumeReturnsNullWhenEmpty() {
        val result = NavigationContext.consume("dashboard")
        assertNull(result)
    }

    @Test
    fun storeAndConsumeMatchingScreen() {
        val params = mapOf("ticketKey" to "ICL2-24", "filter" to "open")
        NavigationContext.store("ticket_intelligence", params)

        val result = NavigationContext.consume("ticket_intelligence")
        assertNotNull(result)
        assertEquals("ICL2-24", result["ticketKey"])
        assertEquals("open", result["filter"])
    }

    @Test
    fun consumeReturnsNullForMismatchedScreen() {
        NavigationContext.store("analysis", mapOf("ticketKey" to "ICL2-24"))

        val result = NavigationContext.consume("ticket_intelligence")
        assertNull(result, "Should return null when screen doesn't match")
    }

    @Test
    fun consumeClearsContextAfterRead() {
        NavigationContext.store("analysis", mapOf("ticketKey" to "ICL2-24"))

        val first = NavigationContext.consume("analysis")
        assertNotNull(first)

        val second = NavigationContext.consume("analysis")
        assertNull(second, "Context should be cleared after first consume")
    }

    @Test
    fun peekDoesNotClearContext() {
        NavigationContext.store("dashboard", mapOf("key" to "val"))

        val peeked = NavigationContext.peek()
        assertNotNull(peeked)
        assertEquals("dashboard", peeked.screen)

        // Still available after peek
        val consumed = NavigationContext.consume("dashboard")
        assertNotNull(consumed, "Context should still exist after peek")
    }

    @Test
    fun clearRemovesPendingContext() {
        NavigationContext.store("analysis", mapOf("ticketKey" to "X"))
        NavigationContext.clear()

        val result = NavigationContext.consume("analysis")
        assertNull(result)
    }

    @Test
    fun storeOverwritesPreviousContext() {
        NavigationContext.store("analysis", mapOf("ticketKey" to "OLD"))
        NavigationContext.store("analysis", mapOf("ticketKey" to "NEW"))

        val result = NavigationContext.consume("analysis")
        assertNotNull(result)
        assertEquals("NEW", result["ticketKey"])
    }

    @Test
    fun emptyParamsStoreAndConsumeCorrectly() {
        NavigationContext.store("dashboard", emptyMap())

        val result = NavigationContext.consume("dashboard")
        assertNotNull(result)
        assertEquals(0, result.size)
    }
}
