package com.assistant.agent.registry

import com.assistant.agent.GenericAgent
import com.assistant.agent.config.AgentConfig

/**
 * Thrown when a requested agent type is not registered.
 */
class AgentNotFoundException(
    val requestedType: String,
    val availableTypes: List<String>
) : RuntimeException(
    "Agent type '$requestedType' not found. Available: $availableTypes"
)

/**
 * Central registry for agent discovery and instantiation.
 * Maps agent type names to factory functions.
 */
interface AgentRegistry {

    /** Register a factory for the given agent type. */
    fun register(
        agentType: String,
        factory: (AgentConfig) -> GenericAgent
    )

    /**
     * Instantiate an agent by type name.
     * @throws AgentNotFoundException if type is not registered
     */
    fun getAgent(agentType: String, config: AgentConfig): GenericAgent

    /** List all registered agent type names. */
    fun listAgentTypes(): List<String>
}
