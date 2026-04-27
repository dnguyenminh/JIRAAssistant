package com.assistant.server.agent.subprocess

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessMessage
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.config.JsonConfig

/**
 * Wire-format protocol for stdin/stdout communication between
 * the Orchestrator and Agent_Subprocesses.
 *
 * **Message framing:** every message sent to a subprocess's stdin
 * is a single JSON line followed by the [DELIMITER] on its own line:
 * ```
 * {"type":"command","content":"analyze PROJ-123"}
 * ---END---
 * ```
 *
 * **Parsing stdout:** each line from the subprocess is either:
 * - A JSON object parseable as [SubprocessMessage] (tool call request, etc.)
 * - Plain text (regular response content)
 * - The [DELIMITER] marking the end of a complete response
 *
 * Requirements: 13.2, 20.4, 20.8
 */
object MessageProtocol {

    /** Delimiter that marks the end of a complete message frame. */
    const val DELIMITER = "---END---"

    private val json = JsonConfig.instance

    /**
     * Formats a command for sending to a subprocess's stdin.
     *
     * Produces: `{"type":"command","content":"<command>"}\n---END---\n`
     *
     * @param command the command text to send
     * @return the framed message ready to write to stdin
     */
    fun formatCommand(command: String): String {
        val msg = SubprocessMessage(type = "command", content = command)
        return frame(json.encodeToString(SubprocessMessage.serializer(), msg))
    }

    /**
     * Formats a tool call response for sending to a subprocess's stdin.
     *
     * Produces: `{"type":"toolResult","toolResult":{...}}\n---END---\n`
     *
     * @param response the tool call result to send back
     * @return the framed message ready to write to stdin
     */
    fun formatToolResponse(response: ToolCallResponse): String {
        val msg = SubprocessMessage(type = "toolResult", toolResult = response)
        return frame(json.encodeToString(SubprocessMessage.serializer(), msg))
    }

    /**
     * Formats a tool list message for injecting available tools at session start.
     *
     * Produces: `{"type":"toolList","tools":[...]}\n---END---\n`
     *
     * @param tools the list of available tool descriptors
     * @return the framed message ready to write to stdin
     */
    fun formatToolList(tools: List<ToolDescriptor>): String {
        val msg = SubprocessMessage(type = "toolList", tools = tools)
        return frame(json.encodeToString(SubprocessMessage.serializer(), msg))
    }

    /**
     * Formats a tools-updated notification for active sessions.
     *
     * Sent when shared MCP servers change at runtime (Requirement 20.8).
     *
     * Produces: `{"type":"toolsUpdated","tools":[...]}\n---END---\n`
     *
     * @param tools the updated list of available tool descriptors
     * @return the framed message ready to write to stdin
     */
    fun formatToolsUpdated(tools: List<ToolDescriptor>): String {
        val msg = SubprocessMessage(type = "toolsUpdated", tools = tools)
        return frame(json.encodeToString(SubprocessMessage.serializer(), msg))
    }

    /**
     * Parses a single stdout line into a [SubprocessMessage].
     *
     * Returns `null` if the line is not valid JSON or cannot be
     * deserialized as a [SubprocessMessage].
     *
     * @param line a single line read from the subprocess's stdout
     * @return the parsed message, or `null` for plain-text lines
     */
    fun parseStdoutLine(line: String): SubprocessMessage? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            json.decodeFromString(SubprocessMessage.serializer(), trimmed)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether a line is the message delimiter.
     *
     * @param line a single line read from the subprocess's stdout
     * @return `true` if the trimmed line equals [DELIMITER]
     */
    fun isDelimiter(line: String): Boolean = line.trim() == DELIMITER

    /**
     * Wraps a JSON payload in the standard message frame:
     * `<json>\n---END---\n`
     */
    private fun frame(jsonPayload: String): String =
        "$jsonPayload\n$DELIMITER\n"
}
