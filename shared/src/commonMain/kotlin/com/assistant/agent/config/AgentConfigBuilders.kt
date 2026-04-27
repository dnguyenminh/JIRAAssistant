package com.assistant.agent.config

import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.models.ErrorStrategy

/**
 * DSL builder for memory schema slot declarations.
 */
class MemorySchemaBuilder {
    private val slots = mutableListOf<SlotSchema>()

    fun stringSlot(name: String, maxChars: Int) {
        slots.add(SlotSchema(name, SlotType.STRING, maxChars))
    }

    fun listSlot(name: String, maxEntries: Int) {
        slots.add(SlotSchema(name, SlotType.LIST, maxEntries))
    }

    fun mapSlot(name: String, maxEntries: Int) {
        slots.add(SlotSchema(name, SlotType.MAP, maxEntries))
    }

    fun build(): List<SlotSchema> = slots.toList()
}

/**
 * DSL builder for phase name declarations.
 */
class PhasesBuilder {
    private val names = mutableListOf<String>()

    fun phase(name: String) {
        names.add(name)
    }

    fun build(): List<String> = names.toList()
}

/**
 * DSL builder for tool name declarations.
 */
class ToolsBuilder {
    private val names = mutableListOf<String>()

    fun register(name: String) {
        names.add(name)
    }

    fun build(): List<String> = names.toList()
}

/**
 * DSL builder for execution limits.
 */
class LimitsBuilder {
    var maxTotalDurationSeconds: Int = 120
    var maxToolCalls: Int = 50
    var maxIterations: Int = 3
    var maxConcurrentTools: Int = 5
}

/**
 * DSL builder for error strategy configuration.
 */
class ErrorStrategyBuilder {
    var default: ErrorStrategy = ErrorStrategy.SKIP
    internal val toolStrategies = mutableMapOf<String, ErrorStrategy>()

    fun forTool(toolName: String, strategy: ErrorStrategy) {
        toolStrategies[toolName] = strategy
    }
}
