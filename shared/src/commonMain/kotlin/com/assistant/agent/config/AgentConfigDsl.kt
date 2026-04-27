package com.assistant.agent.config

import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.models.ErrorStrategy
import kotlinx.serialization.Serializable

/**
 * Serializable agent configuration.
 * Built via the [agentConfig] DSL or constructed directly.
 */
@Serializable
data class AgentConfig(
    val memorySchema: List<SlotSchema> = emptyList(),
    val phaseNames: List<String> = emptyList(),
    val toolNames: List<String> = emptyList(),
    val maxTotalDurationSeconds: Int = 120,
    val maxToolCalls: Int = 50,
    val maxIterations: Int = 3,
    val maxConcurrentTools: Int = 5,
    val defaultErrorStrategy: ErrorStrategy = ErrorStrategy.SKIP,
    val toolErrorStrategies: Map<String, ErrorStrategy> = emptyMap(),
    val homeDirectoryPath: String = ""
)

/**
 * Thrown when DSL validation detects configuration errors.
 */
class InvalidAgentConfigException(
    val errors: List<String>
) : RuntimeException(
    "Invalid agent config: ${errors.joinToString("; ")}"
)

/**
 * Top-level DSL entry point for building an [AgentConfig].
 */
fun agentConfig(block: AgentConfigBuilder.() -> Unit): AgentConfig {
    val builder = AgentConfigBuilder()
    builder.block()
    return builder.build()
}

/**
 * Main builder for [AgentConfig].
 * Delegates to sub-builders for each configuration section.
 */
class AgentConfigBuilder {
    private val memorySchemas = mutableListOf<SlotSchema>()
    private val phaseNames = mutableListOf<String>()
    private val toolNames = mutableListOf<String>()
    private var maxTotalDurationSeconds = 120
    private var maxToolCalls = 50
    private var maxIterations = 3
    private var maxConcurrentTools = 5
    private var defaultErrorStrategy = ErrorStrategy.SKIP
    private val toolErrorStrategies = mutableMapOf<String, ErrorStrategy>()

    /** Path to the agent's home directory on disk. */
    var homeDirectoryPath: String = ""

    fun memorySchema(block: MemorySchemaBuilder.() -> Unit) {
        val b = MemorySchemaBuilder()
        b.block()
        memorySchemas.addAll(b.build())
    }

    fun phases(block: PhasesBuilder.() -> Unit) {
        val b = PhasesBuilder()
        b.block()
        phaseNames.addAll(b.build())
    }

    fun tools(block: ToolsBuilder.() -> Unit) {
        val b = ToolsBuilder()
        b.block()
        toolNames.addAll(b.build())
    }

    fun limits(block: LimitsBuilder.() -> Unit) {
        val b = LimitsBuilder()
        b.block()
        maxTotalDurationSeconds = b.maxTotalDurationSeconds
        maxToolCalls = b.maxToolCalls
        maxIterations = b.maxIterations
        maxConcurrentTools = b.maxConcurrentTools
    }

    fun errorStrategy(block: ErrorStrategyBuilder.() -> Unit) {
        val b = ErrorStrategyBuilder()
        b.block()
        defaultErrorStrategy = b.default
        toolErrorStrategies.putAll(b.toolStrategies)
    }

    fun build(): AgentConfig {
        validate()
        return AgentConfig(
            memorySchema = memorySchemas.toList(),
            phaseNames = phaseNames.toList(),
            toolNames = toolNames.toList(),
            maxTotalDurationSeconds = maxTotalDurationSeconds,
            maxToolCalls = maxToolCalls,
            maxIterations = maxIterations,
            maxConcurrentTools = maxConcurrentTools,
            defaultErrorStrategy = defaultErrorStrategy,
            toolErrorStrategies = toolErrorStrategies.toMap(),
            homeDirectoryPath = homeDirectoryPath
        )
    }

    private fun validate() {
        val errors = mutableListOf<String>()
        validateUniquePhaseNames(errors)
        validateUniqueSlotNames(errors)
        if (errors.isNotEmpty()) {
            throw InvalidAgentConfigException(errors)
        }
    }

    private fun validateUniquePhaseNames(errors: MutableList<String>) {
        val dupes = phaseNames.groupBy { it }
            .filter { it.value.size > 1 }.keys
        if (dupes.isNotEmpty()) {
            errors.add("Duplicate phase names: $dupes")
        }
    }

    private fun validateUniqueSlotNames(errors: MutableList<String>) {
        val dupes = memorySchemas.groupBy { it.name }
            .filter { it.value.size > 1 }.keys
        if (dupes.isNotEmpty()) {
            errors.add("Duplicate slot names: $dupes")
        }
    }
}
