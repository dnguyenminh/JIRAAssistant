package com.assistant.server.agent.state

import com.assistant.agent.GenericAgent
import com.assistant.agent.models.AgentState
import com.assistant.config.JsonConfig
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Manages pause/resume of agent execution by serializing
 * and deserializing [AgentState] snapshots.
 *
 * - **pause**: captures current state, serializes to JSON,
 *   stores via the provided callback
 * - **resume**: deserializes stored JSON back to [AgentState]
 */
class AgentStateManager {

    private val logger = LoggerFactory.getLogger(
        AgentStateManager::class.java
    )

    private val json = JsonConfig.instance

    /**
     * Serialize the agent's current state and persist it
     * via the provided callback.
     */
    suspend fun pause(
        agent: GenericAgent,
        persistCallback: suspend (String) -> Unit
    ) {
        val state = agent.getState()
        val serialized = json.encodeToString(
            AgentState.serializer(), state
        )
        persistCallback(serialized)
        logger.info(
            "Paused agent {} at phase '{}'",
            state.agentId, state.currentPhase
        )
    }

    /**
     * Deserialize a previously stored state snapshot.
     * The caller is responsible for restoring memory and
     * resuming the thinking loop from the returned state.
     */
    fun resume(serializedState: String): AgentState {
        val state = json.decodeFromString(
            AgentState.serializer(), serializedState
        )
        logger.info(
            "Resumed agent {} at phase '{}'",
            state.agentId, state.currentPhase
        )
        return state
    }
}
