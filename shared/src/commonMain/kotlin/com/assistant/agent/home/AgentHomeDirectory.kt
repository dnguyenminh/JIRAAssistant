package com.assistant.agent.home

/**
 * Provides access to all configuration, skills, rules, workflows,
 * and MCP configs for a single agent's home directory.
 *
 * At agent startup the implementation scans the standard directory
 * structure (`.agent/skills/`, `.agent/rules/`, `.agent/workflows/`,
 * `.agent/mcp/`, `config.json`) and makes the parsed contents
 * available through typed accessors.
 *
 * The standard Agent Home Directory layout:
 * ```
 * <agent-home>/
 * ├── config.json          — agent configuration
 * ├── .agent/
 * │   ├── skills/          — markdown skill files
 * │   ├── rules/           — markdown rule files
 * │   ├── workflows/       — markdown workflow files
 * │   ├── mcp/             — MCP server configurations
 * │   └── memory/          — persistent memory storage
 * └── workspace/           — temporary file I/O
 * ```
 *
 * @see AgentHomeConfig
 * @see SkillDefinition
 * @see RuleDefinition
 * @see WorkflowDefinition
 * @see AgentMcpConfig
 */
interface AgentHomeDirectory {

    /**
     * Returns the agent configuration loaded from `config.json`.
     *
     * If the file is missing or invalid the implementation should
     * return a default [AgentHomeConfig] and log a warning.
     */
    fun getConfig(): AgentHomeConfig

    /**
     * Returns all parsed skill definitions found in `.agent/skills/`.
     *
     * Invalid files (missing required `## Purpose` or `## Procedure`
     * sections) are skipped with a logged warning.
     */
    fun getSkills(): List<SkillDefinition>

    /**
     * Returns only the skills that are currently active.
     *
     * When [AgentHomeConfig.activeSkills] is non-empty, only skills
     * whose [SkillDefinition.fileName] appears in that list are
     * returned. When the list is empty, all skills are active.
     */
    fun getActiveSkills(): List<SkillDefinition>

    /**
     * Returns all parsed rule definitions found in `.agent/rules/`.
     *
     * Invalid files (missing required `## Purpose` or `## Categories`
     * sections) are skipped with a logged warning.
     */
    fun getRules(): List<RuleDefinition>

    /**
     * Returns all parsed workflow definitions found in
     * `.agent/workflows/`.
     */
    fun getWorkflows(): List<WorkflowDefinition>

    /**
     * Returns all MCP server configurations found in `.agent/mcp/`.
     */
    fun getMcpConfigs(): List<AgentMcpConfig>

    /**
     * Builds a combined system prompt string from all active skills
     * and rules.
     *
     * The prompt is assembled by concatenating the raw content of
     * each active skill and rule, separated by section headers,
     * producing a single string suitable for injection into the
     * LLM's system prompt.
     */
    fun buildSystemPrompt(): String

    /**
     * Re-scans all directories and reloads configuration, skills,
     * rules, workflows, and MCP configs from disk.
     *
     * Called by the file watcher when changes are detected in the
     * agent home directory, enabling hot-reload without restarting
     * the agent.
     */
    fun reload()
}
