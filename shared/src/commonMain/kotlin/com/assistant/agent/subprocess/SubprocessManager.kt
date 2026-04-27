package com.assistant.agent.subprocess

import kotlinx.coroutines.flow.Flow

/**
 * Manages the lifecycle of agent CLI subprocesses.
 *
 * Each agent type has at most one running subprocess (singleton pattern).
 * The Orchestrator uses this interface to send commands, monitor status,
 * and terminate subprocesses.
 *
 * Key contract:
 * - [sendCommand] spawns a subprocess if none exists for the agent type,
 *   reusing the existing process for subsequent commands.
 * - A `Command_Mutex` per subprocess ensures sequential command execution.
 * - Crash detection triggers auto-restart on the next [sendCommand] call.
 * - Graceful shutdown sends SIGTERM, waits up to 5 seconds, then force-kills.
 */
interface SubprocessManager {

    /**
     * Sends a command to the agent subprocess's stdin and returns
     * a [Flow] of streaming response chunks from stdout.
     *
     * If no subprocess exists for [agentType], one is spawned automatically.
     * If the existing subprocess has crashed, a new one is spawned.
     *
     * @param agentType unique identifier for the agent type (e.g., "ba-agent")
     * @param command the command text to send via stdin
     * @return a [Flow] emitting response chunks as they arrive
     */
    suspend fun sendCommand(agentType: String, command: String): Flow<String>

    /**
     * Checks whether a subprocess is currently running for the given agent type.
     *
     * @param agentType unique identifier for the agent type
     * @return true if a live subprocess exists for [agentType]
     */
    suspend fun isRunning(agentType: String): Boolean

    /**
     * Terminates the subprocess for the given agent type.
     *
     * Performs graceful shutdown: sends termination signal, waits up to
     * the configured shutdown timeout, then force-kills if still running.
     * No-op if no subprocess exists for [agentType].
     *
     * @param agentType unique identifier for the agent type
     */
    suspend fun terminate(agentType: String)

    /**
     * Terminates all running agent subprocesses.
     *
     * Called during application shutdown to clean up resources.
     * Each subprocess follows the same graceful shutdown sequence
     * as [terminate].
     */
    suspend fun terminateAll()

    /**
     * Returns the list of agent types that currently have a running subprocess.
     *
     * @return agent type identifiers with active subprocesses
     */
    fun getRunningAgentTypes(): List<String>
}
