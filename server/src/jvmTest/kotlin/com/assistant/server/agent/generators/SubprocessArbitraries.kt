package com.assistant.server.agent.generators

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessConfig
import com.assistant.agent.subprocess.SubprocessMessage
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// ── Primitive helpers ───────────────────────────────────────────────

private val safeStr = Arb.string(1..30, Codepoint.alphanumeric())
private val optStr = Arb.string(0..20, Codepoint.alphanumeric())

// ── ToolCallRequest ─────────────────────────────────────────────────

fun Arb.Companion.toolCallRequest(): Arb<ToolCallRequest> = arbitrary {
    ToolCallRequest(
        id = safeStr.bind(),
        name = safeStr.bind(),
        arguments = Arb.map(safeStr, optStr, 0, 4).bind()
    )
}

// ── ToolCallResponse ────────────────────────────────────────────────

fun Arb.Companion.toolCallResponse(): Arb<ToolCallResponse> = arbitrary {
    ToolCallResponse(
        id = safeStr.bind(),
        success = Arb.boolean().bind(),
        data = optStr.bind(),
        error = optStr.bind()
    )
}

// ── ToolDescriptor (local helper) ───────────────────────────────────

private fun toolDescriptorArb(): Arb<ToolDescriptor> = arbitrary {
    ToolDescriptor(
        name = safeStr.bind(),
        description = optStr.bind(),
        parameterNames = Arb.list(safeStr, 0..3).bind()
    )
}

// ── SubprocessMessage ───────────────────────────────────────────────

private val messageTypes = Arb.of("command", "toolCall", "toolResult", "toolsUpdated")

fun Arb.Companion.subprocessMessage(): Arb<SubprocessMessage> = arbitrary {
    SubprocessMessage(
        type = messageTypes.bind(),
        toolCall = Arb.of(null).bind().let {
            if (Arb.boolean().bind()) Arb.toolCallRequest().bind() else null
        },
        toolResult = if (Arb.boolean().bind()) Arb.toolCallResponse().bind() else null,
        tools = if (Arb.boolean().bind()) Arb.list(toolDescriptorArb(), 0..3).bind() else null,
        content = if (Arb.boolean().bind()) optStr.bind() else null
    )
}

// ── SubprocessConfig ────────────────────────────────────────────────

fun Arb.Companion.subprocessConfig(): Arb<SubprocessConfig> = arbitrary {
    SubprocessConfig(
        agentType = safeStr.bind(),
        cliCommand = safeStr.bind(),
        cliArgs = Arb.list(safeStr, 0..4).bind(),
        environment = Arb.map(safeStr, optStr, 0, 3).bind(),
        workingDirectory = safeStr.bind(),
        unresponsiveTimeoutMs = Arb.long(1000L..120_000L).bind(),
        shutdownTimeoutMs = Arb.long(1000L..10_000L).bind()
    )
}
