package com.assistant.agent.subprocess

import kotlinx.serialization.Serializable

/**
 * Configuration for spawning and managing an Agent_Subprocess.
 *
 * The Orchestrator uses this to start agent CLI processes via ProcessBuilder,
 * configure their environment, and manage their lifecycle (timeouts, shutdown).
 *
 * @property agentType Unique identifier for the agent type (e.g., "ba-agent", "qa-agent")
 * @property cliCommand The CLI executable to run (e.g., "gemini", "ollama")
 * @property cliArgs Command-line arguments passed to the CLI process
 * @property environment Environment variables injected into the subprocess
 * @property workingDirectory Working directory for the subprocess
 * @property unresponsiveTimeoutMs Timeout in ms before considering the subprocess unresponsive (default: 60s)
 * @property shutdownTimeoutMs Grace period in ms to wait for graceful shutdown before force-killing (default: 5s)
 * @property isRealCli Whether this subprocess is a real CLI tool (e.g., Gemini CLI, Copilot CLI) that expects
 *   plain text stdin instead of MessageProtocol JSON framing. Default `false` preserves existing behavior
 *   for custom protocol subprocesses. Only `CliBackendResolver` sets this to `true`.
 */
@Serializable
data class SubprocessConfig(
    val agentType: String,
    val cliCommand: String,
    val cliArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String = ".",
    val unresponsiveTimeoutMs: Long = 60_000L,
    val shutdownTimeoutMs: Long = 5_000L,
    val isRealCli: Boolean = false
)
