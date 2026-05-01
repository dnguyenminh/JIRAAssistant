package com.assistant.server.agent.registry

import com.assistant.agent.GenericAgent
import com.assistant.agent.config.AgentConfig
import com.assistant.agent.registry.AgentNotFoundException
import com.assistant.agent.registry.AgentRegistry
import org.slf4j.LoggerFactory

/**
 * Singleton registry mapping agent type names to factory functions.
 * Duplicate registration replaces the existing factory with a
 * logged warning. Unknown types throw [AgentNotFoundException].
 */
class AgentRegistryImpl : AgentRegistry {

    private val logger = LoggerFactory.getLogger(
        AgentRegistryImpl::class.java
    )

    private val factories =
        mutableMapOf<String, (AgentConfig) -> GenericAgent>()

    override fun register(
        agentType: String,
        factory: (AgentConfig) -> GenericAgent
    ) {
        if (factories.containsKey(agentType)) {
            logger.warn("Replacing agent type: {}", agentType)
        }
        factories[agentType] = factory
        logger.info("Registered agent type: {}", agentType)
    }

    override fun getAgent(
        agentType: String,
        config: AgentConfig
    ): GenericAgent {
        val factory = factories[agentType]
            ?: throw AgentNotFoundException(
                agentType, factories.keys.toList()
            )
        return factory(config)
    }

    override fun listAgentTypes(): List<String> =
        factories.keys.toList()
}
