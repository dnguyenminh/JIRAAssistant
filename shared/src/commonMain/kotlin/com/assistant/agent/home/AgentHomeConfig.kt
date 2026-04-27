package com.assistant.agent.home

import kotlinx.serialization.Serializable

/**
 * Configuration loaded from `config.json` in an Agent Home Directory.
 *
 * Defines the agent's identity, LLM settings, active capabilities, and
 * CLI subprocess spawning parameters. All fields have sensible defaults
 * so that a minimal `config.json` (or even an empty one) produces a
 * valid configuration.
 *
 * @property agentType Unique identifier for the agent type (e.g., "ba-agent", "qa-agent")
 * @property model LLM model identifier used by this agent (e.g., "gemini-2.0-flash", "llama3")
 * @property maxTokens Maximum token limit for LLM responses
 * @property apiEndpoint API endpoint URL for the LLM provider
 * @property activeSkills List of skill file names to activate; empty means all skills are active
 * @property activeRules List of rule file names to activate; empty means all rules are active
 * @property cliCommand The CLI executable to run for subprocess spawning (e.g., "gemini", "ollama")
 * @property cliArgs Command-line arguments passed to the CLI process
 * @property environment Environment variables injected into the subprocess
 */
@Serializable
data class AgentHomeConfig(
    val agentType: String = "",
    val model: String = "",
    val maxTokens: Int = 4096,
    val apiEndpoint: String = "",
    val activeSkills: List<String> = emptyList(),
    val activeRules: List<String> = emptyList(),
    val cliCommand: String = "",
    val cliArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap()
)
