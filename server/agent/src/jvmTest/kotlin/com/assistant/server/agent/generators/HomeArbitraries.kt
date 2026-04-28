package com.assistant.server.agent.generators

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.home.RuleDefinition
import com.assistant.agent.home.SkillDefinition
import com.assistant.agent.home.WorkflowDefinition
import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.session.CommandHistoryEntry
import com.assistant.agent.session.SessionContext
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// ── Primitive helpers ───────────────────────────────────────────────

private val safeStr = Arb.string(1..30, Codepoint.alphanumeric())
private val optStr = Arb.string(0..20, Codepoint.alphanumeric())

// ── AgentHomeConfig ─────────────────────────────────────────────────

fun Arb.Companion.agentHomeConfig(): Arb<AgentHomeConfig> = arbitrary {
    AgentHomeConfig(
        agentType = safeStr.bind(),
        model = safeStr.bind(),
        maxTokens = Arb.int(256..8192).bind(),
        apiEndpoint = optStr.bind(),
        activeSkills = Arb.list(safeStr, 0..3).bind(),
        activeRules = Arb.list(safeStr, 0..3).bind(),
        cliCommand = safeStr.bind(),
        cliArgs = Arb.list(safeStr, 0..3).bind(),
        environment = Arb.map(safeStr, optStr, 0, 3).bind()
    )
}

// ── SkillDefinition ─────────────────────────────────────────────────

fun Arb.Companion.skillDefinition(): Arb<SkillDefinition> = arbitrary {
    SkillDefinition(
        fileName = safeStr.bind(),
        purpose = optStr.bind(),
        availableTools = Arb.list(safeStr, 0..4).bind(),
        procedure = optStr.bind(),
        outputFormat = optStr.bind(),
        constraints = optStr.bind(),
        rawContent = optStr.bind()
    )
}

// ── RuleDefinition ──────────────────────────────────────────────────

fun Arb.Companion.ruleDefinition(): Arb<RuleDefinition> = arbitrary {
    RuleDefinition(
        fileName = safeStr.bind(),
        purpose = optStr.bind(),
        keywords = Arb.list(safeStr, 0..5).bind(),
        categories = Arb.list(safeStr, 0..5).bind(),
        priority = Arb.int(1..200).bind(),
        conflictResolution = optStr.bind(),
        rawContent = optStr.bind()
    )
}

// ── WorkflowDefinition ─────────────────────────────────────────────

fun Arb.Companion.workflowDefinition(): Arb<WorkflowDefinition> = arbitrary {
    WorkflowDefinition(
        fileName = safeStr.bind(),
        name = safeStr.bind(),
        description = optStr.bind(),
        steps = Arb.list(safeStr, 0..5).bind(),
        rawContent = optStr.bind()
    )
}

// ── CommandHistoryEntry ─────────────────────────────────────────────

fun Arb.Companion.commandHistoryEntry(): Arb<CommandHistoryEntry> = arbitrary {
    CommandHistoryEntry(
        command = safeStr.bind(),
        responseSummary = optStr.bind(),
        timestamp = optStr.bind(),
        isSummary = Arb.boolean().bind()
    )
}

// ── AgentMcpConfig ──────────────────────────────────────────────────

fun Arb.Companion.agentMcpConfig(): Arb<AgentMcpConfig> = arbitrary {
    AgentMcpConfig(
        serverName = safeStr.bind(),
        command = safeStr.bind(),
        args = Arb.list(safeStr, 0..4).bind(),
        env = Arb.map(safeStr, optStr, 0, 3).bind(),
        toolDescriptions = Arb.map(safeStr, optStr, 0, 3).bind()
    )
}

// ── SessionContext ──────────────────────────────────────────────────

fun Arb.Companion.sessionContext(): Arb<SessionContext> = arbitrary {
    val schema = listOf(
        SlotSchema("slot1", SlotType.LIST, Arb.int(1..10).bind())
    )
    SessionContext(
        agentType = safeStr.bind(),
        memory = StructuredMemory(schema),
        commandHistory = Arb.list(Arb.commandHistoryEntry(), 0..5).bind()
            .toMutableList(),
        startedAt = optStr.bind(),
        commandCount = Arb.int(0..100).bind()
    )
}
