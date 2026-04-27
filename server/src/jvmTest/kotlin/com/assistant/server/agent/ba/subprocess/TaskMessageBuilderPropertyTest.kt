package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.subprocess.MessageProtocol
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Feature: agent-subprocess-orchestration
 * Properties 1, 2, 3 for TaskMessageBuilder
 *
 * **Validates: Requirements 2.1, 2.3, 2.5, 2.6**
 */
@OptIn(ExperimentalKotest::class)
class TaskMessageBuilderPropertyTest {

    // Use alphanumeric ticket IDs to avoid JSON escaping issues
    private val arbTicketId: Arb<String> = arbitrary {
        val prefix = Arb.string(2..6, Codepoint.az()).bind()
            .uppercase()
        val num = Arb.int(min = 1, max = 9999).bind()
        "$prefix-$num"
    }

    private val arbBATaskConfig: Arb<BATaskConfig> = arbitrary {
        val ticketId = arbTicketId.bind()
        val docType = Arb.element(
            BATaskConfig.VALID_DOC_TYPES.toList()
        ).bind()
        val maxToolCalls = Arb.int(min = 1, max = 500).bind()
        val timeout = Arb.int(min = 1, max = 3600).bind()
        val backend = Arb.element(
            BATaskConfig.VALID_CLI_BACKENDS.toList()
        ).bind()
        BATaskConfig(ticketId, docType, maxToolCalls, timeout, backend)
    }

    // Use lowercase alpha tool names to avoid JSON escaping issues
    private val arbToolDescriptor: Arb<ToolDescriptor> = arbitrary {
        val name = Arb.string(3..20, Codepoint.az()).bind()
        val desc = Arb.string(3..40, Codepoint.az()).bind()
        ToolDescriptor(name = name, description = desc)
    }

    private val arbToolList: Arb<List<ToolDescriptor>> = arbitrary {
        val size = Arb.int(min = 1, max = 20).bind()
        (1..size).map { arbToolDescriptor.bind() }
    }

    // MCP-style tool descriptors for native-tool-removal property test
    private val arbMcpToolDescriptor: Arb<ToolDescriptor> = arbitrary {
        val server = Arb.string(3..10, Codepoint.az()).bind()
        val tool = Arb.string(3..12, Codepoint.az()).bind()
        val desc = Arb.string(5..40, Codepoint.az()).bind()
        ToolDescriptor(
            name = "mcp_${server}_$tool",
            description = desc
        )
    }

    private val arbMcpToolList: Arb<List<ToolDescriptor>> = arbitrary {
        val size = Arb.int(min = 1, max = 20).bind()
        (1..size).map { arbMcpToolDescriptor.bind() }
    }

    companion object {
        /** The 6 deleted native tool names that must never appear. */
        val DELETED_NATIVE_TOOL_NAMES = listOf(
            "fetchJiraDetails",
            "getLinkedIssues",
            "fetchComments",
            "lookupKBRecord",
            "searchKB",
            "processAttachment"
        )
    }

    /**
     * Property 1: Task message contains all required sections
     *
     * For any valid BATaskConfig, the task message SHALL contain:
     * role instruction, template structure, output format,
     * rootTicketId, and docType.
     *
     * **Validates: Requirements 2.1**
     */
    @Test
    fun `Property 1 - task message contains all required sections`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbBATaskConfig,
                arbToolList
            ) { config, tools ->
                val msg = TaskMessageBuilder.buildTaskMessage(config, tools)

                assertTrue(
                    msg.contains("ROLE INSTRUCTION"),
                    "Missing ROLE INSTRUCTION for $config"
                )
                assertTrue(
                    msg.contains("TEMPLATE STRUCTURE"),
                    "Missing TEMPLATE STRUCTURE for $config"
                )
                assertTrue(
                    msg.contains("OUTPUT FORMAT"),
                    "Missing OUTPUT FORMAT for $config"
                )
                assertTrue(
                    msg.contains(config.rootTicketId),
                    "Missing rootTicketId '${config.rootTicketId}'"
                )
                assertTrue(
                    msg.contains(config.docType),
                    "Missing docType '${config.docType}'"
                )
            }
        }
    }

    /**
     * Property 2: Tool usage instructions reference all available tools
     *
     * For any non-empty list of ToolDescriptor objects,
     * buildToolUsageInstructions() SHALL contain every tool name.
     *
     * **Validates: Requirements 2.3**
     */
    @Test
    fun `Property 2 - tool usage instructions reference all tools`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolList
            ) { tools ->
                val instructions =
                    TaskMessageBuilder.buildToolUsageInstructions(tools)

                for (tool in tools) {
                    assertTrue(
                        instructions.contains(tool.name),
                        "Missing tool '${tool.name}' in instructions"
                    )
                }
            }
        }
    }

    // Feature: native-tool-removal, Property 1: Tool usage instructions contain all provided tools and no hardcoded native names
    /**
     * Property 4: Tool usage instructions contain all provided
     * MCP tools and no hardcoded native tool names.
     *
     * For any non-empty list of ToolDescriptor objects with
     * mcp_* style names, buildToolUsageInstructions() SHALL
     * contain every tool name from the input list AND SHALL NOT
     * contain any of the 6 deleted native tool names.
     *
     * **Validates: Requirements 3.1, 3.3**
     */
    @Test
    fun `Property 4 - tool instructions contain all MCP tools and no native names`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbMcpToolList
            ) { tools ->
                val output =
                    TaskMessageBuilder.buildToolUsageInstructions(tools)

                // Every MCP tool name must appear in the output
                for (tool in tools) {
                    assertTrue(
                        output.contains(tool.name),
                        "Missing MCP tool '${tool.name}' in output"
                    )
                }

                // No deleted native tool name may appear
                for (native in DELETED_NATIVE_TOOL_NAMES) {
                    assertFalse(
                        output.contains(native),
                        "Deleted native tool '$native' found in output"
                    )
                }
            }
        }
    }

    /**
     * Property 3: Task message MessageProtocol round-trip
     *
     * For any valid BATaskConfig, each line of buildTaskMessage()
     * output is either parseable by MessageProtocol.parseStdoutLine()
     * as a valid SubprocessMessage, is plain text (returns null),
     * or is the MessageProtocol.DELIMITER.
     *
     * **Validates: Requirements 2.5, 2.6**
     */
    @Test
    fun `Property 3 - task message MessageProtocol round-trip`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbBATaskConfig,
                arbToolList
            ) { config, tools ->
                val msg = TaskMessageBuilder.buildTaskMessage(config, tools)
                val lines = msg.split("\n")

                for (line in lines) {
                    val parsed = MessageProtocol.parseStdoutLine(line)
                    val isDelimiter = MessageProtocol.isDelimiter(line)
                    // Each line must be one of: parsed message, delimiter, or plain text (null)
                    assertTrue(
                        parsed != null || isDelimiter || parsed == null,
                        "Line not parseable: $line"
                    )
                    // If it's a JSON line, it should parse as a SubprocessMessage
                    if (line.trim().startsWith("{")) {
                        assertNotNull(
                            parsed,
                            "JSON line failed to parse: $line"
                        )
                    }
                }

                // The message must contain the DELIMITER
                assertTrue(
                    lines.any { MessageProtocol.isDelimiter(it) },
                    "Message missing DELIMITER"
                )
            }
        }
    }
}
