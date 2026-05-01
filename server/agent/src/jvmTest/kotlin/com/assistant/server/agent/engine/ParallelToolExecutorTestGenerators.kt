package com.assistant.server.agent.engine

import com.assistant.agent.models.ToolCall
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/** Generate a batch of 0-10 tool calls with unique names. */
fun arbToolCallBatch(): Arb<List<ToolCall>> = arbitrary {
    val count = Arb.int(0..10).bind()
    (1..count).map { idx ->
        val suffix = Arb.string(1..8, Codepoint.alphanumeric()).bind()
        ToolCall(toolName = "tool_${idx}_$suffix")
    }
}

/** Batch with a mix of succeeding and failing tool names. */
data class MixedBatch(
    val calls: List<ToolCall>,
    val failNames: Set<String>
)

fun arbMixedBatch(): Arb<MixedBatch> = arbitrary {
    val count = Arb.int(2..8).bind()
    val calls = (1..count).map { idx ->
        ToolCall(toolName = "tool_$idx")
    }
    val failCount = Arb.int(1..count / 2).bind()
    val failNames = calls.shuffled()
        .take(failCount)
        .map { it.toolName }
        .toSet()
    MixedBatch(calls, failNames)
}
