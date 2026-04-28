package com.assistant.server.chat

import com.assistant.chat.GraphChatContext
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Property-based tests for ChatServiceImpl.buildGraphStateContext().
 * Uses reflection to invoke the private method directly.
 *
 * **Validates: AC 19.80, AC 19.81, AC 19.82, AC 19.84, AC 19.85**
 */
@OptIn(ExperimentalKotest::class)
class BuildGraphStateContextPropertyTest {

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

    private fun invoke(gc: GraphChatContext): String =
        method.invoke(service, gc) as String

    /**
     * Property 7: Focused node prompt contains exact key with explicit instruction
     *
     * **Validates: Requirements 19.80, 19.81, 19.82**
     */
    @Test
    @Tag("Feature: ai-chat-sidebar, Property 7: Focused node prompt contains exact key with explicit instruction")
    fun `focused node prompt contains exact key with explicit instruction`() {
        val arbTicketId = arbJiraTicketId()
        runBlocking {
            checkAll(PropTestConfig(iterations = 120), arbTicketId) { key ->
                val result = invoke(GraphChatContext(focusedNodeKey = key))
                assertFocusedKeyPresent(result, key)
            }
        }
    }

    /**
     * Property 8: No-focus context preserves other graph state
     * without focused node instruction
     *
     * **Validates: Requirements 19.84, 19.85**
     */
    @Test
    @Tag("Feature: ai-chat-sidebar, Property 8: No-focus context preserves other graph state")
    fun `no-focus context preserves graph state without focused instruction`() {
        val arbContext = arbNoFocusGraphContext()
        runBlocking {
            checkAll(PropTestConfig(iterations = 120), arbContext) { gc ->
                val result = invoke(gc)
                assertNoFocusedInstruction(result)
                assertGraphStatePreserved(result, gc)
            }
        }
    }
}
