package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.subprocess.MessageProtocol
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for TaskMessageBuilder.
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 */
class TaskMessageBuilderTest {

    private val sampleTools = listOf(
        ToolDescriptor("fetchJiraDetails", "Fetch Jira ticket details", listOf("ticketId")),
        ToolDescriptor("getLinkedIssues", "Get linked issues", listOf("ticketId")),
        ToolDescriptor("fetchComments", "Fetch ticket comments", listOf("ticketId"))
    )

    private val brdConfig = BATaskConfig(
        rootTicketId = "PROJ-123",
        docType = "BRD"
    )

    @Test
    fun `strategy hint varies by docType BRD`() {
        val hint = TaskMessageBuilder.buildStrategyHint("BRD")
        assertTrue(hint.contains("business goals"), "BRD hint should mention business goals")
        assertTrue(hint.contains("STRATEGY HINT"), "Should have section header")
    }

    @Test
    fun `strategy hint varies by docType FSD`() {
        val hint = TaskMessageBuilder.buildStrategyHint("FSD")
        assertTrue(hint.contains("technical architecture"), "FSD hint should mention technical architecture")
    }

    @Test
    fun `strategy hint varies by docType SLIDES`() {
        val hint = TaskMessageBuilder.buildStrategyHint("SLIDES")
        assertTrue(hint.contains("executive summary"), "SLIDES hint should mention executive summary")
    }

    @Test
    fun `no pre-collected data in message`() {
        val msg = TaskMessageBuilder.buildTaskMessage(brdConfig, sampleTools)
        // Must NOT contain pre-collected CONTEXT section with ticket data
        assertFalse(
            msg.contains("## CONTEXT"),
            "Should not contain CONTEXT section with pre-collected data"
        )
        // Must NOT contain actual ticket data (comments, attachments, linked details)
        assertFalse(
            msg.contains("## COMMENTS"),
            "Should not contain pre-collected comments"
        )
        assertFalse(
            msg.contains("## ATTACHMENTS DATA"),
            "Should not contain pre-collected attachments"
        )
    }

    @Test
    fun `formats via MessageProtocol`() {
        val msg = TaskMessageBuilder.buildTaskMessage(brdConfig, sampleTools)
        // Must contain the DELIMITER from MessageProtocol
        assertTrue(
            msg.contains(MessageProtocol.DELIMITER),
            "Message should be framed with MessageProtocol DELIMITER"
        )
        // First line should be valid JSON (command envelope)
        val firstLine = msg.lines().first()
        val parsed = MessageProtocol.parseStdoutLine(firstLine)
        assertNotNull(parsed, "First line should be parseable as SubprocessMessage")
        assertEquals("command", parsed!!.type, "Message type should be 'command'")
    }

    @Test
    fun `tool usage instructions include ToolCallRequest JSON format`() {
        val instructions = TaskMessageBuilder.buildToolUsageInstructions(sampleTools)
        assertTrue(
            instructions.contains("toolCall"),
            "Should include ToolCallRequest JSON format example"
        )
        assertTrue(
            instructions.contains("correlationId"),
            "Should include correlation ID in format example"
        )
    }

    @Test
    fun `tool usage instructions list all tool names`() {
        val instructions = TaskMessageBuilder.buildToolUsageInstructions(sampleTools)
        for (tool in sampleTools) {
            assertTrue(
                instructions.contains(tool.name),
                "Should list tool '${tool.name}'"
            )
        }
    }

    @Test
    fun `message contains root ticket ID and doc type`() {
        val msg = TaskMessageBuilder.buildTaskMessage(brdConfig, sampleTools)
        assertTrue(msg.contains("PROJ-123"), "Should contain root ticket ID")
        assertTrue(msg.contains("BRD"), "Should contain doc type")
    }

    @Test
    fun `message contains diagram instructions`() {
        val msg = TaskMessageBuilder.buildTaskMessage(brdConfig, sampleTools)
        assertTrue(
            msg.contains("DIAGRAM"),
            "Should contain diagram instructions section"
        )
    }

    // ── 6.3 isRealCli = true ────────────────────────────

    @Test
    fun `real CLI message includes RESPONSE PROTOCOL section`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = true
        )
        assertTrue(
            msg.contains("RESPONSE PROTOCOL"),
            "Real CLI message should include RESPONSE PROTOCOL section"
        )
    }

    @Test
    fun `real CLI message returns plain text without JSON framing`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = true
        )
        assertFalse(
            msg.contains("{\"type\":\"command\""),
            "Real CLI message should not have JSON command envelope"
        )
    }

    @Test
    fun `real CLI message is not framed with delimiter`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = true
        )
        // The message may mention ---END--- in protocol instructions,
        // but must NOT end with it as a MessageProtocol frame terminator
        val trimmed = msg.trimEnd()
        assertFalse(
            trimmed.endsWith(MessageProtocol.DELIMITER),
            "Real CLI message should not be framed with DELIMITER"
        )
    }

    // ── 6.4 isRealCli = false ───────────────────────────

    @Test
    fun `custom protocol message has no RESPONSE PROTOCOL section`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = false
        )
        assertFalse(
            msg.contains("RESPONSE PROTOCOL"),
            "Custom protocol message should NOT include RESPONSE PROTOCOL"
        )
    }

    @Test
    fun `custom protocol message is JSON-framed`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = false
        )
        assertTrue(
            msg.contains(MessageProtocol.DELIMITER),
            "Custom protocol message should be framed with DELIMITER"
        )
        val firstLine = msg.lines().first()
        val parsed = MessageProtocol.parseStdoutLine(firstLine)
        assertNotNull(parsed, "First line should be parseable JSON")
        assertEquals("command", parsed!!.type)
    }

    // ── 6.6 RESPONSE_PROTOCOL_SECTION content ───────────

    @Test
    fun `RESPONSE PROTOCOL contains END delimiter instruction`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = true
        )
        assertTrue(
            msg.contains("---END---"),
            "Protocol section should instruct AI about ---END--- delimiter"
        )
    }

    @Test
    fun `RESPONSE PROTOCOL contains toolCall format instruction`() {
        val msg = TaskMessageBuilder.buildTaskMessage(
            brdConfig, sampleTools, isRealCli = true
        )
        assertTrue(
            msg.contains("toolCall"),
            "Protocol section should instruct AI about toolCall JSON format"
        )
    }
}
