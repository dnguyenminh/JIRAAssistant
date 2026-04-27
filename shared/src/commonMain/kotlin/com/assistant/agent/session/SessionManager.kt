package com.assistant.agent.session

/**
 * Manages multi-command session context per agent subprocess.
 *
 * Each agent type has an associated [SessionContext] that persists across
 * sequential commands within a session. Follow-up commands can reference
 * previous results through the shared memory and command history without
 * re-sending all context.
 *
 * Key contract:
 * - One [SessionContext] per agent type, created lazily on first access.
 * - [addCommandToHistory] records each command and its response summary,
 *   capping history at [SessionContext.MAX_HISTORY_SIZE] entries.
 * - [resetSession] clears Working_Memory slots and command history.
 * - [buildContextSummary] produces a text summary of previous commands
 *   and current memory state for inclusion in command payloads.
 *
 * @see SessionContext
 * @see CommandHistoryEntry
 */
interface SessionManager {

    /**
     * Returns the current session context for the given agent type.
     *
     * If no session exists for [agentType], a new empty [SessionContext]
     * is created and returned.
     *
     * @param agentType unique identifier for the agent type (e.g., "ba-agent")
     * @return the current [SessionContext] for the agent
     */
    fun getSessionContext(agentType: String): SessionContext

    /**
     * Records a command and its response summary in the session's
     * command history.
     *
     * When the history exceeds [SessionContext.MAX_HISTORY_SIZE],
     * older entries are condensed into a single summary entry to
     * prevent unbounded memory growth.
     *
     * @param agentType unique identifier for the agent type
     * @param command the command text sent to the agent subprocess
     * @param response a brief summary of the agent's response
     */
    fun addCommandToHistory(agentType: String, command: String, response: String)

    /**
     * Returns the ordered list of command history entries for the
     * given agent type's session.
     *
     * @param agentType unique identifier for the agent type
     * @return ordered list of [CommandHistoryEntry], empty if no session exists
     */
    fun getCommandHistory(agentType: String): List<CommandHistoryEntry>

    /**
     * Clears the session for the given agent type.
     *
     * Resets Working_Memory slots, clears command history, and
     * resets the command count. Equivalent to starting a fresh
     * session for the agent.
     *
     * @param agentType unique identifier for the agent type
     */
    fun resetSession(agentType: String)

    /**
     * Builds a text summary of the session's command history and
     * current memory state.
     *
     * The summary is intended for inclusion in command payloads so
     * that the agent subprocess can maintain continuity across
     * sequential commands without receiving the full history.
     *
     * @param agentType unique identifier for the agent type
     * @return a human-readable context summary string
     */
    fun buildContextSummary(agentType: String): String
}
