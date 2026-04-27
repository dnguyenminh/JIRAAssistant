package com.assistant.agent.session

import com.assistant.agent.memory.StructuredMemory
import kotlinx.serialization.Serializable

/**
 * Maintains state across multiple commands within an agent session.
 *
 * Each Agent_Subprocess has an associated [SessionContext] that persists
 * across sequential commands. Follow-up commands can reference previous
 * results through the shared [memory] and [commandHistory] without
 * re-sending all context.
 *
 * When [commandHistory] exceeds [MAX_HISTORY_SIZE], older entries are
 * summarized into a single [CommandHistoryEntry] with `isSummary = true`,
 * preventing unbounded memory growth.
 *
 * @property agentType Unique identifier for the agent type (e.g., "ba-agent", "qa-agent")
 * @property memory Structured memory populated by previous commands in this session
 * @property commandHistory Ordered list of commands sent and response summaries received
 * @property startedAt ISO-8601 timestamp of when the session was created
 * @property commandCount Total number of commands processed in this session
 */
@Serializable
data class SessionContext(
    val agentType: String,
    val memory: StructuredMemory,
    val commandHistory: MutableList<CommandHistoryEntry> = mutableListOf(),
    val startedAt: String = "",
    val commandCount: Int = 0
) {
    companion object {
        /** Maximum number of command history entries before summarization. */
        const val MAX_HISTORY_SIZE = 50
    }
}
