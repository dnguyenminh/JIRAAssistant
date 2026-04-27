package com.assistant.agent.session

import kotlinx.serialization.Serializable

/**
 * Records a single command sent to an agent and a summary of its response
 * within a multi-command session.
 *
 * Each time the Orchestrator sends a command to an Agent_Subprocess, a
 * [CommandHistoryEntry] is appended to the session's command history.
 * When the history exceeds [SessionContext.MAX_HISTORY_SIZE], older entries
 * are condensed into a single summary entry with [isSummary] set to `true`.
 *
 * @property command The command text sent to the agent subprocess
 * @property responseSummary A brief summary of the agent's response to the command
 * @property timestamp ISO-8601 timestamp of when the command was sent
 * @property isSummary Whether this entry is a summarized version of older entries
 *   (created when history exceeds [SessionContext.MAX_HISTORY_SIZE])
 */
@Serializable
data class CommandHistoryEntry(
    val command: String = "",
    val responseSummary: String = "",
    val timestamp: String = "",
    val isSummary: Boolean = false
)
