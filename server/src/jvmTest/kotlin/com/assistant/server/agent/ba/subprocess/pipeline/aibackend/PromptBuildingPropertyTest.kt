package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 10: Prompt building contains required sections

/**
 * Property 10: Prompt building contains required sections
 *
 * For any ticket ID, document type, and non-empty tool descriptor list,
 * the built initial prompt SHALL contain:
 * (a) the ticket ID,
 * (b) at least one tool name from the descriptor list,
 * (c) the POC tool protocol format instructions,
 * (d) the document type template reference.
 *
 * **Validates: Requirements 15.1, 15.4, 15.5**
 */
class PromptBuildingPropertyTest {

    @Test
    fun `Property 10 - initial prompt contains required sections`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbTicketId(),
                arbDocType(),
                arbToolDescriptors()
            ) { ticketId, docType, tools ->
                val proxy = mockProxy(tools)
                val builder = AgenticPromptBuilder(proxy)

                val prompt = builder.buildInitialPrompt(ticketId, docType)

                // (a) Contains ticket ID
                assertTrue(
                    prompt.contains(ticketId),
                    "Prompt should contain ticket ID: $ticketId"
                )

                // (b) Contains at least one tool name
                val hasToolName = tools.any { prompt.contains(it.name) }
                assertTrue(
                    hasToolName,
                    "Prompt should contain at least one tool name"
                )

                // (c) Contains POC protocol format instructions
                assertTrue(
                    prompt.contains("tool_call"),
                    "Prompt should contain tool_call protocol"
                )

                // (d) Contains document type reference
                assertTrue(
                    prompt.contains(docType),
                    "Prompt should contain doc type: $docType"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefix = Arb.string(2..5, Codepoint.az()).bind()
            .uppercase()
        val number = Arb.int(1..9999).bind()
        "$prefix-$number"
    }

    private fun arbDocType(): Arb<String> = Arb.choice(
        Arb.constant("BRD"),
        Arb.constant("FSD"),
        Arb.constant("TechnicalSpec"),
        Arb.constant("UserStory"),
        Arb.string(3..15, Codepoint.alphanumeric())
    )

    private fun arbToolDescriptors(): Arb<List<ToolDescriptor>> = arbitrary {
        val size = Arb.int(1..5).bind()
        (0 until size).map {
            val name = Arb.string(3..15, Codepoint.alphanumeric()).bind()
            val desc = Arb.string(5..30, Codepoint.alphanumeric()).bind()
            val paramCount = Arb.int(0..3).bind()
            val params = (0 until paramCount).map {
                Arb.string(3..10, Codepoint.alphanumeric()).bind()
            }
            ToolDescriptor(name = name, description = desc, parameterNames = params)
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun mockProxy(
        tools: List<ToolDescriptor>
    ): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse {
            return ToolCallResponse(request.id, true, "", "")
        }
        override fun getAvailableToolDescriptors() = tools
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }
}
