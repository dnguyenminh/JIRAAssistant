package com.assistant.server.agent.engine

import com.assistant.agent.engine.PhaseDefinition
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.tool.ToolRegistry

/**
 * Mutable context carried through a thinking loop execution.
 * Holds references to phases, memory, tools, reporter,
 * and accumulates reasoning log entries and metrics.
 */
internal class LoopContext(
    val phases: List<PhaseDefinition>,
    val memory: StructuredMemory,
    val toolRegistry: ToolRegistry,
    val reporter: ProgressReporter
) {
    val reasoningLog = mutableListOf<String>()
    var phasesExecuted = 0
    var iterationCount = 0

    fun log(message: String) {
        reasoningLog.add(message)
    }
}
