package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.ba.prompt.MasterPromptSections
import com.assistant.server.agent.subprocess.MessageProtocol

/**
 * Constructs the initial task message sent to the AI subprocess.
 *
 * Reuses existing MasterPromptSections for role instructions,
 * template structure, and output format — but does NOT include
 * pre-collected ticket data. The AI subprocess fetches data
 * on demand via tool calls.
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
object TaskMessageBuilder {

    /**
     * Builds the complete task message for the AI subprocess.
     *
     * Composes: role instruction + template structure + output format
     * + diagram instructions + tool usage instructions + strategy hint
     * + root ticket ID.
     *
     * When [isRealCli] is `true`, returns plain text content with
     * embedded response protocol instructions (no JSON framing).
     * When `false` (default), wraps via [MessageProtocol.formatCommand].
     */
    fun buildTaskMessage(
        config: BATaskConfig,
        tools: List<ToolDescriptor>,
        isRealCli: Boolean = false
    ): String {
        val content = buildMessageContent(config, tools, isRealCli)
        return if (isRealCli) "$content\n" else MessageProtocol.formatCommand(content)
    }

    /**
     * Builds tool usage instructions explaining the ToolCallRequest
     * JSON format with examples for each available tool.
     *
     * When no tools are available, outputs a message instructing
     * the AI to produce the document without tool calls.
     *
     * Requirements: 3.1, 3.4, 6.3
     */
    fun buildToolUsageInstructions(
        tools: List<ToolDescriptor>
    ): String = buildString {
        appendLine("## TOOL USAGE INSTRUCTIONS")
        if (tools.isEmpty()) {
            appendLine(NO_TOOLS_MESSAGE)
            return@buildString
        }
        appendLine("You can request tool calls using JSON format:")
        appendLine(TOOL_CALL_FORMAT_EXAMPLE)
        appendLine()
        appendLine("Available tools:")
        for (tool in tools) {
            appendToolEntry(this, tool)
        }
    }.trimEnd()

    /**
     * Builds a strategy hint based on the document type.
     * BRD → business goals; FSD → technical architecture;
     * SLIDES → executive summary.
     */
    fun buildStrategyHint(docType: String): String {
        val hint = when (docType) {
            "BRD" -> BRD_STRATEGY_HINT
            "FSD" -> FSD_STRATEGY_HINT
            "SLIDES" -> SLIDES_STRATEGY_HINT
            else -> BRD_STRATEGY_HINT
        }
        return "## STRATEGY HINT\n$hint"
    }

    private fun buildMessageContent(
        config: BATaskConfig,
        tools: List<ToolDescriptor>,
        isRealCli: Boolean = false
    ): String = buildString {
        if (isRealCli) {
            appendLine(RESPONSE_PROTOCOL_SECTION)
            appendLine()
        }
        appendLine(MasterPromptSections.buildRoleInstruction(config.docType))
        appendLine()
        appendLine(MasterPromptSections.buildTemplateStructure(config.docType))
        appendLine()
        appendLine(MasterPromptSections.buildOutputFormat())
        appendLine()
        appendLine(MasterPromptSections.buildDiagramInstructions())
        appendLine()
        val filtered = filterRelevantTools(tools, config.docType)
        appendLine(buildToolUsageInstructions(filtered))
        appendLine()
        appendLine(buildStrategyHint(config.docType))
        appendLine()
        appendLine(buildStepByStepGuide(config))
        appendLine()
        appendLine("## ROOT TICKET")
        appendLine("Analyze ticket: ${config.rootTicketId}")
        appendLine("Document type: ${config.docType}")
        if (isRealCli) {
            appendLine()
            appendLine("REMINDER: Output ---END--- on its own line when done.")
        }
    }.trimEnd()

    private fun appendToolEntry(
        sb: StringBuilder,
        tool: ToolDescriptor
    ) {
        val shortDesc = truncateDescription(tool.description)
        sb.appendLine("- **${tool.name}**: $shortDesc")
        if (tool.parameterNames.isNotEmpty()) {
            val params = tool.parameterNames.joinToString(", ")
            sb.appendLine("  Parameters: $params")
        }
    }

    /**
     * Truncates tool description to first sentence or 120 chars.
     * Strips Python docstring details (Args, Returns, Raises).
     */
    private fun truncateDescription(desc: String): String {
        val firstLine = desc.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: return desc
        val cutoff = firstLine.indexOf(". ")
        val summary = if (cutoff > 0) firstLine.substring(0, cutoff + 1)
            else firstLine
        return if (summary.length > 120) summary.take(117) + "..."
            else summary
    }

    /**
     * Filters tools to only those relevant for document generation.
     * Removes write/mutation tools that the AI should not call.
     */
    /**
     * Filters tools to only those relevant for document generation.
     * Removes write/mutation tools that the AI should not call.
     */
    private fun filterRelevantTools(
        tools: List<ToolDescriptor>, docType: String
    ): List<ToolDescriptor> {
        return tools.filter { tool ->
            READ_ONLY_TOOL_PREFIXES.any { prefix ->
                tool.name.startsWith(prefix)
            }
        }
    }

    /**
     * Builds step-by-step guide for the AI to follow.
     */
    private fun buildStepByStepGuide(
        config: BATaskConfig
    ): String = buildString {
        appendLine("## STEP-BY-STEP GUIDE")
        appendLine("Follow these steps in order:")
        appendLine("1. Call mcp_jira_get_issue with issue_key=\"${config.rootTicketId}\"")
        appendLine("2. Call mcp_jira_search to find linked tickets")
        appendLine("3. Call mcp_local_knowledge_base_get_ticket_info with ticketId=\"${config.rootTicketId}\"")
        appendLine("4. Call mcp_local_knowledge_base_search_relationships for dependencies")
        appendLine("5. Write the complete ${config.docType} document")
        appendLine("6. Output ---END--- on its own line when finished")
    }.trimEnd()
}
