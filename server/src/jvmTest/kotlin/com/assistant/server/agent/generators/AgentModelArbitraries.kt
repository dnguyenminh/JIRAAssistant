package com.assistant.server.agent.generators

import com.assistant.agent.config.AgentConfig
import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.models.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// ── AgentConfig ─────────────────────────────────────────────────────

fun Arb.Companion.agentConfig(): Arb<AgentConfig> = arbitrary {
    val schemas = uniqueSlotSchemas(Arb.int(0..4).bind())
    AgentConfig(
        memorySchema = schemas,
        phaseNames = uniqueNames(Arb.int(0..5).bind()),
        toolNames = uniqueNames(Arb.int(0..5).bind()),
        maxTotalDurationSeconds = Arb.int(10..300).bind(),
        maxToolCalls = Arb.int(1..100).bind(),
        maxIterations = Arb.int(1..10).bind(),
        maxConcurrentTools = Arb.int(1..10).bind(),
        defaultErrorStrategy = Arb.errorStrategy().bind(),
        toolErrorStrategies = Arb.map(
            Arb.string(1..15, Codepoint.alphanumeric()),
            Arb.errorStrategy(), 0, 3
        ).bind(),
        homeDirectoryPath = Arb.string(0..50, Codepoint.alphanumeric()).bind()
    )
}

// ── AgentInput ──────────────────────────────────────────────────────

fun Arb.Companion.agentInput(): Arb<AgentInput> = arbitrary {
    AgentInput(
        requestId = Arb.string(1..30, Codepoint.alphanumeric()).bind(),
        agentType = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
        payload = Arb.map(
            Arb.string(1..15, Codepoint.alphanumeric()),
            Arb.string(0..30, Codepoint.alphanumeric()), 0, 5
        ).bind(),
        config = Arb.agentConfig().bind()
    )
}

// ── AgentOutput ─────────────────────────────────────────────────────

fun Arb.Companion.agentOutput(): Arb<AgentOutput> = arbitrary {
    AgentOutput(
        requestId = Arb.string(1..30, Codepoint.alphanumeric()).bind(),
        agentType = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
        result = Arb.string(0..50, Codepoint.alphanumeric()).bind(),
        metadata = Arb.map(
            Arb.string(1..15, Codepoint.alphanumeric()),
            Arb.string(0..20, Codepoint.alphanumeric()), 0, 3
        ).bind(),
        reasoningLog = Arb.list(
            Arb.string(1..40, Codepoint.alphanumeric()), 0..5
        ).bind(),
        toolCallCount = Arb.int(0..100).bind(),
        totalDurationMs = Arb.long(0L..100_000L).bind(),
        status = Arb.enum<AgentStatus>().bind(),
        metrics = Arb.agentMetrics().bind()
    )
}

// ── AgentState ──────────────────────────────────────────────────────

fun Arb.Companion.agentState(): Arb<AgentState> = arbitrary {
    AgentState(
        agentId = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
        agentType = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
        currentPhase = Arb.string(0..20, Codepoint.alphanumeric()).bind(),
        phaseIndex = Arb.int(0..10).bind(),
        iterationCount = Arb.int(0..10).bind(),
        memorySnapshot = Arb.string(0..50, Codepoint.alphanumeric()).bind(),
        toolCallHistory = Arb.list(Arb.toolCallRecord(), 0..5).bind(),
        reasoningLog = Arb.list(
            Arb.string(1..40, Codepoint.alphanumeric()), 0..10
        ).bind(),
        elapsedTimeMs = Arb.long(0L..100_000L).bind(),
        status = Arb.enum<AgentStateStatus>().bind()
    )
}

// ── Helpers ─────────────────────────────────────────────────────────

private suspend fun ArbitraryBuilderContext.uniqueSlotSchemas(
    count: Int
): List<SlotSchema> {
    val names = mutableSetOf<String>()
    return buildList {
        repeat(count) {
            var s = Arb.slotSchema().bind()
            while (s.name in names) s = Arb.slotSchema().bind()
            names.add(s.name)
            add(s)
        }
    }
}

private suspend fun ArbitraryBuilderContext.uniqueNames(
    count: Int
): List<String> {
    val names = mutableSetOf<String>()
    return buildList {
        repeat(count) {
            var n = Arb.string(1..20, Codepoint.alphanumeric()).bind()
            while (n in names) {
                n = Arb.string(1..20, Codepoint.alphanumeric()).bind()
            }
            names.add(n)
            add(n)
        }
    }
}
