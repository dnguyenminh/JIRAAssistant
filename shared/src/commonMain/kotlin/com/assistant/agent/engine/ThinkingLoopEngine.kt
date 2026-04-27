package com.assistant.agent.engine

import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.tool.ToolRegistry

/**
 * Result of a complete thinking loop execution.
 */
data class ThinkingLoopResult(
    val reasoningLog: List<String> = emptyList(),
    val phasesExecuted: Int = 0,
    val totalDurationMs: Long = 0
)

/**
 * Drives an agent through its defined phases sequentially.
 * Evaluates entry/exit conditions, handles loopback, timeouts,
 * and reports progress.
 */
interface ThinkingLoopEngine {

    /**
     * Execute the thinking loop with the given phases.
     * @param phases ordered list of phase definitions
     * @param memory shared structured memory
     * @param toolRegistry tool invocation registry
     * @param reporter progress reporting callback
     * @return execution result with reasoning log and metrics
     */
    suspend fun execute(
        phases: List<PhaseDefinition>,
        memory: StructuredMemory,
        toolRegistry: ToolRegistry,
        reporter: ProgressReporter
    ): ThinkingLoopResult
}
