package com.assistant.agent.engine

import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.models.ErrorStrategy
import com.assistant.agent.tool.ToolRegistry
import kotlinx.serialization.Serializable

/**
 * Runtime phase definition with function references.
 * Not serializable — constructed via the AgentConfig DSL.
 */
class PhaseDefinition(
    val name: String,
    val entryCondition: (StructuredMemory) -> Boolean,
    val phaseAction: suspend (StructuredMemory, ToolRegistry) -> Unit,
    val exitCondition: (StructuredMemory) -> Boolean,
    val maxDurationSeconds: Int = 30,
    val errorStrategy: ErrorStrategy = ErrorStrategy.SKIP,
    val loopbackTarget: String? = null
)

/**
 * Serializable phase metadata for storage and inspection.
 * Does not contain function references.
 */
@Serializable
data class PhaseConfig(
    val phases: List<String> = emptyList(),
    val maxIterations: Int = 3,
    val totalTimeoutSeconds: Int = 120
)
