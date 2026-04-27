package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import org.slf4j.LoggerFactory

/**
 * Builds phase-specific prompts by composing existing appendXxx() functions.
 * Each phase gets only the instructions and tool definitions it needs.
 *
 * Reuses: appendSystemInstructions(), appendToolDefinitions(),
 * appendToolProtocol(), appendBrdSections(), appendDiagramInstructions(),
 * appendDataCollectionStrategy() from existing prompt section files.
 */
class PhasePromptBuilder {

    private val log = LoggerFactory.getLogger(PhasePromptBuilder::class.java)

    /**
     * Phase 1: Data collection — Jira exploration + KB cache.
     * Includes: system instructions, tools, protocol, data strategy, KB memory protocol.
     */
    fun buildPhase1Prompt(ticketId: String, tools: List<ToolDescriptor>): String {
        val prompt = buildString {
            appendSystemInstructions()
            appendLine()
            appendToolDefinitions(tools)
            appendLine()
            appendToolProtocol(tools)
            appendLine()
            appendDataCollectionStrategy(ticketId, tools)
            appendLine()
            appendKbMemoryProtocol(ticketId)
            appendLine()
            appendPhase1Task(ticketId)
        }
        logPromptSize("Phase1", prompt, PHASE1_SOFT_LIMIT)
        return prompt
    }

    /**
     * Phase 2: BRD writing — read KB data, write BRD with placeholders.
     * Includes: system instructions, tools, protocol, BRD sections, KB retrieval, placeholders.
     * When phase1Output is provided, it's injected as fallback data context.
     */
    fun buildPhase2Prompt(
        ticketId: String,
        docType: String,
        tools: List<ToolDescriptor>,
        phase1Output: String = ""
    ): String {
        val prompt = buildString {
            appendSystemInstructions()
            appendLine()
            appendToolDefinitions(tools)
            appendLine()
            appendToolProtocol(tools)
            appendLine()
            appendBrdSections(docType)
            appendLine()
            appendKbDataRetrieval(ticketId)
            appendLine()
            if (phase1Output.isNotBlank()) {
                appendPhase1DataContext(phase1Output)
                appendLine()
            }
            appendDiagramPlaceholderInstructions()
            appendLine()
            appendPhase2Task(ticketId)
        }
        logPromptSize("Phase2", prompt, PHASE2_SOFT_LIMIT)
        return prompt
    }

    /**
     * Phase 3: Diagram generation — read KB data, generate draw.io XML.
     * Includes: system instructions, tools, protocol, diagram instructions, KB retrieval.
     * When phase1Output is provided, it's injected as fallback data context.
     */
    fun buildPhase3Prompt(
        ticketId: String,
        tools: List<ToolDescriptor>,
        phase1Output: String = ""
    ): String {
        val detected = detectToolCategories(tools)
        val prompt = buildString {
            appendSystemInstructions()
            appendLine()
            appendToolDefinitions(tools)
            appendLine()
            appendToolProtocol(tools)
            appendLine()
            appendDiagramInstructions(detected)
            appendLine()
            appendKbDataRetrieval(ticketId)
            appendLine()
            if (phase1Output.isNotBlank()) {
                appendPhase1DataContext(phase1Output)
                appendLine()
            }
            appendPhase3Task(ticketId)
        }
        logPromptSize("Phase3", prompt, PHASE3_SOFT_LIMIT)
        return prompt
    }

    /**
     * Continuation prompt for agentic loop — same pattern as
     * [AgenticPromptBuilder.buildPersistentContinuation].
     */
    fun buildPhaseContinuation(latestToolResult: String): String = buildString {
        appendLine("Tool result:")
        appendLine("```json")
        appendLine(latestToolResult)
        appendLine("```")
        appendLine()
        appendLine("Continue collecting data. Call the NEXT tool from your plan.")
        appendLine("Have you explored linked tickets? Have you searched KB with different queries?")
        appendLine("Only produce final output when you have exhausted all data sources.")
    }

    private fun logPromptSize(phase: String, prompt: String, softLimit: Int) {
        val len = prompt.length
        if (len > softLimit) {
            log.warn("{} prompt size {}chars exceeds soft limit {}chars", phase, len, softLimit)
        } else {
            log.debug("{} prompt size {}chars (limit {}chars)", phase, len, softLimit)
        }
    }

    companion object {
        internal const val PHASE1_SOFT_LIMIT = 20_000
        internal const val PHASE2_SOFT_LIMIT = 20_000
        internal const val PHASE3_SOFT_LIMIT = 15_000
    }
}
