package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.models.ToolDescriptor
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [MasterPromptBuilder].
 *
 * Property 6: Master prompt contains all required sections for any input.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.6**
 */
@OptIn(ExperimentalKotest::class)
class MasterPromptBuilderPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    // -- Generators --

    private fun arbTicketId(): Arb<String> =
        Arb.string(1..20, Codepoint.alphanumeric())
            .filter { it.isNotBlank() }

    private fun arbDocType(): Arb<String> =
        Arb.element("BRD", "FSD")

    private fun arbToolDescriptor(): Arb<ToolDescriptor> = arbitrary {
        ToolDescriptor(
            name = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
            description = Arb.string(1..40, Codepoint.alphanumeric()).bind(),
            parameterNames = Arb.list(
                Arb.string(1..15, Codepoint.alphanumeric()),
                0..5
            ).bind()
        )
    }

    private fun arbToolDescriptors(): Arb<List<ToolDescriptor>> =
        Arb.list(arbToolDescriptor(), 0..5)

    private fun arbCustomInstructions(): Arb<String?> = arbitrary {
        if (Arb.boolean().bind()) {
            Arb.string(1..100, Codepoint.printableAscii())
                .filter { it.isNotBlank() }
                .bind()
        } else {
            null
        }
    }

    // -- Property 6 --

    /**
     * **Validates: Requirements 5.1, 5.2, 5.3, 5.6**
     *
     * For any valid combination of ticket ID, doc type, tool
     * descriptors, and optional custom instructions, the built
     * prompt contains: ticket ID, each tool name, ---END---,
     * and custom instructions when provided.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P6 - master prompt contains all required sections`() {
        runBlocking {
            checkAll(
                cfg,
                arbTicketId(),
                arbDocType(),
                arbToolDescriptors(),
                arbCustomInstructions()
            ) { ticketId, docType, tools, customInstructions ->
                val output = MasterPromptBuilder.build(
                    ticketId = ticketId,
                    docType = docType,
                    availableTools = tools,
                    customInstructions = customInstructions
                )

                // 1. Output contains the ticket ID
                assertTrue(output.contains(ticketId)) {
                    "Prompt must contain ticketId=$ticketId"
                }

                // 2. Output contains each tool descriptor's name
                for (tool in tools) {
                    assertTrue(output.contains(tool.name)) {
                        "Prompt must contain tool name=${tool.name}"
                    }
                }

                // 3. Output contains ---END---
                assertTrue(output.contains("---END---")) {
                    "Prompt must contain ---END--- delimiter"
                }

                // 4. When custom instructions provided, output contains them
                if (customInstructions != null) {
                    assertTrue(output.contains(customInstructions.trim())) {
                        "Prompt must contain custom instructions"
                    }
                }

                // 5. When custom instructions null, no CUSTOM INSTRUCTIONS header
                if (customInstructions == null) {
                    assertFalse(output.contains("## CUSTOM INSTRUCTIONS")) {
                        "Prompt must NOT contain CUSTOM INSTRUCTIONS header when null"
                    }
                }
            }
        }
    }
}
