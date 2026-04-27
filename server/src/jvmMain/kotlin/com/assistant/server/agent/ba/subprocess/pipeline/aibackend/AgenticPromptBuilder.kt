package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.mcp.McpProcessManager
import com.assistant.server.mcp.internal.InternalMcpBridge
import org.slf4j.LoggerFactory

/** Maximum total chars for collected tool results in continuation prompt. */
internal const val MAX_TOOL_RESULTS_CHARS = 80_000

/**
 * Builds prompts for the agentic loop.
 * Gets tools from InternalMcpBridge + McpProcessManager (database),
 * falls back to SubprocessProxy.
 */
class AgenticPromptBuilder(
    private val subprocessProxy: SubprocessProxy,
    private val mcpProcessManager: McpProcessManager? = null,
    private val internalMcpBridge: InternalMcpBridge? = null
) {

    private val log = LoggerFactory.getLogger(AgenticPromptBuilder::class.java)

    private fun getAllToolDescriptors(): List<ToolDescriptor> {
        val internalTools = internalMcpBridge?.getAggregatedTools() ?: emptyList()
        val externalTools = mcpProcessManager?.getActiveTools() ?: emptyList()
        val allMcp = internalTools + externalTools
        if (allMcp.isNotEmpty()) {
            return allMcp.map { tool ->
                ToolDescriptor(
                    name = "mcp_${tool.serverName}_${tool.name}",
                    description = tool.description
                )
            }
        }
        return subprocessProxy.getAvailableToolDescriptors()
    }

    fun buildInitialPrompt(ticketId: String, docType: String): String {
        val tools = getAllToolDescriptors()
        log.info(
            "Building prompt: ticket=$ticketId, docType=$docType, tools=${tools.size}"
        )
        return if (tools.isEmpty()) {
            buildDirectGenerationPrompt(ticketId, docType)
        } else {
            buildToolCallingPrompt(ticketId, docType, tools)
        }
    }

    fun buildStatelessContinuation(
        ticketId: String,
        docType: String,
        toolResults: List<String>
    ): String {
        val allTools = getAllToolDescriptors()
        val tools = filterExcludedTools(allTools)
        val detected = detectToolCategories(tools)
        val truncated = truncateToolResults(toolResults)
        return buildString {
            appendContextHeader()
            appendLine()
            appendToolDefinitions(tools)
            appendLine()
            appendToolProtocol(tools)
            appendLine()
            appendBrdSections(docType)
            appendLine()
            appendDiagramInstructions(detected)
            appendLine()
            appendCollectedData(truncated)
            appendLine()
            appendNextStep(truncated, tools)
        }
    }

    fun buildPersistentContinuation(latestToolResult: String): String {
        return buildString {
            appendLine("Tool result:")
            appendLine("```json")
            appendLine(latestToolResult)
            appendLine("```")
            appendLine()
            appendLine("Continue: if you need more data, call another tool.")
            appendLine("If you have enough data, produce the final BRD.")
        }
    }

    companion object {
        /** Patterns to exclude — tools irrelevant for BA work. */
        internal val EXCLUDED_PATTERNS = listOf(
            "playwright", "browser", "convert_to_markdown"
        )
    }
}

/**
 * Filter out tools irrelevant for BA work.
 * No hardcoded whitelist — pass all tools, let AI choose.
 * Only exclude tools clearly not useful (browser, etc.).
 */
internal fun filterExcludedTools(
    tools: List<ToolDescriptor>
): List<ToolDescriptor> {
    return tools.filter { tool ->
        AgenticPromptBuilder.EXCLUDED_PATTERNS.none { p ->
            tool.name.contains(p, ignoreCase = true)
        }
    }
}

/**
 * Truncate tool results to fit within [maxChars].
 * Keeps first (main ticket) + last (latest) results.
 * Removes from index 1 upward until under limit.
 */
internal fun truncateToolResults(
    toolResults: List<String>,
    maxChars: Int = MAX_TOOL_RESULTS_CHARS
): List<String> {
    if (toolResults.size < 2) return toolResults
    val total = toolResults.sumOf { it.length }
    if (total <= maxChars) return toolResults

    val first = toolResults.first()
    val last = toolResults.last()
    if (first.length + last.length >= maxChars) return listOf(first, last)

    val remaining = maxChars - first.length - last.length
    val kept = mutableListOf<String>()
    var used = 0
    for (i in (toolResults.size - 2) downTo 1) {
        val r = toolResults[i]
        if (used + r.length <= remaining) {
            kept.add(0, r)
            used += r.length
        }
    }
    val removed = toolResults.size - 2 - kept.size
    val annotation =
        "[TRUNCATED: $removed earlier tool results omitted due to prompt size limit]"
    return listOf(first, annotation) + kept + listOf(last)
}
