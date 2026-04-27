package com.assistant.agent

import com.assistant.agent.models.AgentInput
import com.assistant.agent.models.AgentOutput
import com.assistant.agent.models.AgentState

/**
 * Core contract that all specialized agents implement.
 *
 * Lifecycle: [onStart] → [execute] → [onComplete].
 * [getState] enables inspection at any point during execution.
 */
interface GenericAgent {

    /** Unique identifier for this agent instance. */
    fun getAgentId(): String

    /** Agent type name (e.g., "ba-document"). */
    fun getAgentType(): String

    /** Current execution state snapshot. */
    fun getState(): AgentState

    /** Called before the thinking loop begins. */
    suspend fun onStart(input: AgentInput)

    /** Single entry point for agent execution. */
    suspend fun execute(input: AgentInput): AgentOutput

    /** Called after the thinking loop finishes. */
    suspend fun onComplete(output: AgentOutput)
}
