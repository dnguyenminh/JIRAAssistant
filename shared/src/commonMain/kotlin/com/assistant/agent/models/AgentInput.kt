package com.assistant.agent.models

import com.assistant.agent.config.AgentConfig
import kotlinx.serialization.Serializable

/**
 * Typed input envelope for agent execution.
 * Carries the request payload and agent configuration.
 */
@Serializable
data class AgentInput(
    val requestId: String,
    val agentType: String,
    val payload: Map<String, String> = emptyMap(),
    val config: AgentConfig = AgentConfig()
)
