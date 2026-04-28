package com.assistant.server.chat

import com.assistant.chat.GraphChatContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Unit tests for ChatServiceImpl.buildGraphStateContext().
 * Uses reflection to invoke the private method directly.
 * Validates: AC 19.80, 19.81, 19.82, 19.83, 19.84, 19.85
 */
class BuildGraphStateContextTest {

    private lateinit var service: ChatServiceImpl
    private lateinit var method: Method

    @BeforeEach
    fun setup() {
        service = ChatServiceImpl(
            aiAgentProvider = { CapturingAIAgent() },
            kbRepository = StubKBRepository(),
            graphEngine = StubGraphEngine()
        )
        method = ChatServiceImpl::class.java
            .getDeclaredMethod("buildGraphStateContext", GraphChatContext::class.java)
        method.isAccessible = true
    }

    private fun invoke(gc: GraphChatContext?): String =
        method.invoke(service, gc) as String

    /** Validates: AC 19.83 — null context → not on graph page */
    @Test
    fun `null context returns not-on-graph message`() {
        val result = invoke(null)
        assertEquals("User is NOT on the Knowledge Graph page.", result)
    }

    /** Validates: AC 19.80 — focused ICL2-400 contains EXACT and Do NOT split */
    @Test
    fun `focused ICL2-400 contains exact key and instructions`() {
        val gc = GraphChatContext(focusedNodeKey = "ICL2-400")
        val result = invoke(gc)
        assertTrue(result.contains("ICL2-400"), "should contain key")
        assertTrue(result.contains("EXACT"), "should contain EXACT instruction")
        assertTrue(result.contains("Do NOT split"), "should contain Do NOT split")
    }

    /** Validates: AC 19.81 — focused ITCM-129 preserved intact */
    @Test
    fun `focused ITCM-129 preserved intact in output`() {
        val gc = GraphChatContext(focusedNodeKey = "ITCM-129")
        val result = invoke(gc)
        assertTrue(result.contains("ITCM-129"), "key must appear intact")
    }

    /** Validates: AC 19.84, 19.85 — no focus, filters only */
    @Test
    fun `no focus with filters contains filter values without EXACT`() {
        val gc = GraphChatContext(
            focusedNodeKey = null,
            activeTypeFilters = listOf("Bug", "Story")
        )
        val result = invoke(gc)
        assertTrue(result.contains("Bug"), "should contain Bug filter")
        assertTrue(result.contains("Story"), "should contain Story filter")
        assertFalse(result.contains("EXACT"), "no EXACT without focus")
        assertFalse(result.contains("focused on Jira ticket"), "no focused instruction")
    }

    /** Validates: AC 19.85 — search query present in output */
    @Test
    fun `no focus with search query contains query text`() {
        val gc = GraphChatContext(
            focusedNodeKey = null,
            searchQuery = "test query"
        )
        val result = invoke(gc)
        assertTrue(result.contains("test query"), "should contain search query")
    }
}
