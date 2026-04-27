package com.assistant.server.agent.tool

import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlinx.coroutines.delay

// ── Test tool implementations ───────────────────────────────────────

/** A tool that always succeeds with given data. */
fun successTool(name: String, desc: String = "ok") =
    object : AgentTool {
        override val name = name
        override val description = desc
        override val parameterNames = emptyList<String>()
        override suspend fun execute(
            params: Map<String, String>
        ) = ToolResult(toolName = name, data = "ok", success = true)
    }

/** A tool that always throws an exception. */
fun throwingTool(name: String) = object : AgentTool {
    override val name = name
    override val description = "throws"
    override val parameterNames = emptyList<String>()
    override suspend fun execute(
        params: Map<String, String>
    ): ToolResult = throw RuntimeException("boom")
}

/** A tool that delays longer than any reasonable timeout. */
fun slowTool(name: String) = object : AgentTool {
    override val name = name
    override val description = "slow"
    override val parameterNames = emptyList<String>()
    override suspend fun execute(
        params: Map<String, String>
    ): ToolResult {
        delay(60_000)
        return ToolResult(toolName = name, success = true)
    }
}

// ── Arb generators ──────────────────────────────────────────────────

data class ToolBehavior(val tool: AgentTool)

/** Generate a list of 1-8 tools with possible duplicate names. */
fun arbToolList(): Arb<List<AgentTool>> = arbitrary {
    val count = Arb.int(1..8).bind()
    (1..count).map { _ ->
        val name = Arb.string(1..10, Codepoint.alphanumeric()).bind()
        val desc = Arb.string(1..20, Codepoint.alphanumeric()).bind()
        successTool(name, desc)
    }
}

/** Generate tools with various behaviors: success, throw, slow. */
fun arbToolBehavior(): Arb<ToolBehavior> = arbitrary {
    val name = Arb.string(1..10, Codepoint.alphanumeric()).bind()
    val kind = Arb.int(0..2).bind()
    val tool = when (kind) {
        0 -> successTool(name)
        1 -> throwingTool(name)
        else -> slowTool(name)
    }
    ToolBehavior(tool)
}
