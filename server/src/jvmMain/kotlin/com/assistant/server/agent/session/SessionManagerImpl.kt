package com.assistant.server.agent.session

import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.session.CommandHistoryEntry
import com.assistant.agent.session.SessionContext
import com.assistant.agent.session.SessionManager
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe [SessionManager] implementation backed by a
 * [ConcurrentHashMap]. Each agent type gets a lazily-created
 * [SessionContext] that persists across sequential commands.
 *
 * When command history exceeds [SessionContext.MAX_HISTORY_SIZE],
 * the oldest entries are condensed into a single summary entry
 * with `isSummary = true`.
 */
class SessionManagerImpl : SessionManager {

    private val logger = LoggerFactory.getLogger(
        SessionManagerImpl::class.java
    )

    private val sessions =
        ConcurrentHashMap<String, SessionContext>()

    override fun getSessionContext(agentType: String): SessionContext =
        sessions.getOrPut(agentType) {
            logger.info("Creating new session for '{}'", agentType)
            createEmptySession(agentType)
        }

    override fun addCommandToHistory(
        agentType: String,
        command: String,
        response: String
    ) {
        val session = getSessionContext(agentType)
        val entry = CommandHistoryEntry(
            command = command,
            responseSummary = response,
            timestamp = Instant.now().toString(),
            isSummary = false
        )
        session.commandHistory.add(entry)
        condensIfExceeded(session)
        logger.debug(
            "Added history entry for '{}', size={}",
            agentType, session.commandHistory.size
        )
    }

    override fun getCommandHistory(
        agentType: String
    ): List<CommandHistoryEntry> =
        sessions[agentType]?.commandHistory?.toList() ?: emptyList()

    override fun resetSession(agentType: String) {
        val session = sessions[agentType] ?: return
        session.memory.clear()
        session.commandHistory.clear()
        logger.info("Reset session for '{}'", agentType)
    }

    override fun buildContextSummary(agentType: String): String {
        val session = sessions[agentType]
            ?: return "No active session for '$agentType'."
        return buildString {
            appendLine("=== Session Context: $agentType ===")
            appendCommandHistory(session.commandHistory)
            appendMemoryState(session.memory)
        }.trimEnd()
    }

    // ── private helpers ──────────────────────────────────

    private fun createEmptySession(
        agentType: String
    ): SessionContext = SessionContext(
        agentType = agentType,
        memory = StructuredMemory(schema = emptyList()),
        commandHistory = mutableListOf(),
        startedAt = Instant.now().toString(),
        commandCount = 0
    )

    /**
     * When history exceeds [SessionContext.MAX_HISTORY_SIZE],
     * condense the oldest half into a single summary entry.
     */
    private fun condensIfExceeded(session: SessionContext) {
        val max = SessionContext.MAX_HISTORY_SIZE
        if (session.commandHistory.size <= max) return
        val half = max / 2
        val oldest = session.commandHistory.take(half)
        val summaryText = buildOldEntrySummary(oldest)
        val summaryEntry = CommandHistoryEntry(
            command = "[condensed $half entries]",
            responseSummary = summaryText,
            timestamp = Instant.now().toString(),
            isSummary = true
        )
        repeat(half) { session.commandHistory.removeAt(0) }
        session.commandHistory.add(0, summaryEntry)
        logger.debug(
            "Condensed {} old entries for '{}'",
            half, session.agentType
        )
    }

    private fun buildOldEntrySummary(
        entries: List<CommandHistoryEntry>
    ): String = entries.joinToString("; ") { e ->
        "${e.command} → ${e.responseSummary.take(80)}"
    }

    private fun StringBuilder.appendCommandHistory(
        history: List<CommandHistoryEntry>
    ) {
        if (history.isEmpty()) {
            appendLine("No previous commands.")
            return
        }
        appendLine("Previous commands (${history.size}):")
        history.forEachIndexed { i, e ->
            val prefix = if (e.isSummary) "[summary]" else "#${i + 1}"
            appendLine("  $prefix ${e.command} → ${e.responseSummary}")
        }
    }

    private fun StringBuilder.appendMemoryState(
        memory: StructuredMemory
    ) {
        val completeness = memory.getCompleteness()
        if (completeness.isEmpty()) {
            appendLine("Memory: empty")
            return
        }
        appendLine("Memory state:")
        completeness.forEach { (slot, fill) ->
            val pct = "%.0f%%".format(fill * 100)
            appendLine("  $slot: $pct filled")
        }
        appendLine("Total memory size: ${memory.getTotalSize()} chars")
    }
}
