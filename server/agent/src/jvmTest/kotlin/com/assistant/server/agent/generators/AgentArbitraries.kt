package com.assistant.server.agent.generators

import com.assistant.agent.config.AgentConfig
import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.models.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// ── Primitive helpers ───────────────────────────────────────────────

private val safeString = Arb.string(1..30, Codepoint.alphanumeric())
private val optString = Arb.string(0..20, Codepoint.alphanumeric())

// ── SlotSchema & MemoryEntry ────────────────────────────────────────

fun Arb.Companion.slotType(): Arb<SlotType> = Arb.enum<SlotType>()

fun Arb.Companion.slotSchema(): Arb<SlotSchema> = arbitrary {
    SlotSchema(
        name = safeString.bind(),
        type = Arb.slotType().bind(),
        maxSize = Arb.int(1..100).bind()
    )
}

fun Arb.Companion.memoryEntry(): Arb<MemoryEntry> = arbitrary {
    MemoryEntry(
        data = safeString.bind(),
        source = optString.bind(),
        toolName = optString.bind(),
        timestamp = optString.bind()
    )
}

// ── Error models ────────────────────────────────────────────────────

fun Arb.Companion.errorStrategy(): Arb<ErrorStrategy> =
    Arb.enum<ErrorStrategy>()

// ── Tool models ─────────────────────────────────────────────────────

fun Arb.Companion.toolResult(): Arb<ToolResult> = arbitrary {
    ToolResult(
        toolName = safeString.bind(),
        data = optString.bind(),
        executionTimeMs = Arb.long(0L..10_000L).bind(),
        dataSizeChars = Arb.int(0..5000).bind(),
        success = Arb.boolean().bind(),
        errorType = Arb.of(null, "TIMEOUT", "RATE_LIMIT").bind(),
        errorMessage = Arb.of(null, "err").bind()
    )
}

fun Arb.Companion.toolCall(): Arb<ToolCall> = arbitrary {
    ToolCall(
        toolName = safeString.bind(),
        params = Arb.map(safeString, optString, 0, 3).bind()
    )
}

fun Arb.Companion.toolCallRecord(): Arb<ToolCallRecord> = arbitrary {
    ToolCallRecord(
        toolName = safeString.bind(),
        params = Arb.map(safeString, optString, 0, 3).bind(),
        executionTimeMs = Arb.long(0L..10_000L).bind(),
        dataSizeChars = Arb.int(0..5000).bind(),
        success = Arb.boolean().bind(),
        timestamp = optString.bind()
    )
}

// ── AgentMetrics ────────────────────────────────────────────────────

fun Arb.Companion.agentMetrics(): Arb<AgentMetrics> = arbitrary {
    AgentMetrics(
        totalDurationMs = Arb.long(0L..100_000L).bind(),
        phaseCount = Arb.int(0..20).bind(),
        toolCallCount = Arb.int(0..100).bind(),
        parallelBatchCount = Arb.int(0..50).bind(),
        memoryTotalChars = Arb.int(0..50_000).bind(),
        outputSizeChars = Arb.int(0..50_000).bind(),
        retryCount = Arb.int(0..10).bind(),
        errorCount = Arb.int(0..10).bind()
    )
}
