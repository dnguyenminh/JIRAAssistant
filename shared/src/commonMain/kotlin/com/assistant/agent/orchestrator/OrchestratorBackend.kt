package com.assistant.agent.orchestrator

import com.assistant.agent.engine.PhaseDefinition
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.models.AgentOutput
import com.assistant.agent.tool.ToolRegistry

/**
 * Pluggable abstraction over the LLM interaction layer.
 * No LLM-specific types leak through this interface — all
 * communication uses framework-defined types.
 */
interface OrchestratorBackend {

    /** Human-readable backend name (e.g., "custom-kotlin"). */
    fun getBackendName(): String

    /**
     * Run the full thinking loop and return the agent output.
     * Delegates to the ThinkingLoopEngine internally.
     */
    suspend fun runThinkingLoop(
        memory: StructuredMemory,
        tools: ToolRegistry,
        phases: List<PhaseDefinition>
    ): AgentOutput
}
