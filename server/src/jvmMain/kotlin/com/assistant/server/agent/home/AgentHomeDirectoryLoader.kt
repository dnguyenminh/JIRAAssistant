package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.home.RuleDefinition
import com.assistant.agent.home.SkillDefinition
import com.assistant.agent.home.WorkflowDefinition
import com.assistant.config.JsonConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * File-system-backed implementation of [AgentHomeDirectory].
 *
 * Scans the standard Agent Home Directory layout at [basePath], parsing
 * skills, rules, workflows, MCP configs, and `config.json`. Missing
 * directories are created automatically with a logged warning.
 *
 * @param basePath root path of the agent home directory
 */
class AgentHomeDirectoryLoader(private val basePath: Path) : AgentHomeDirectory {

    private val logger = LoggerFactory.getLogger(AgentHomeDirectoryLoader::class.java)

    private var config: AgentHomeConfig = AgentHomeConfig()
    private var skills: List<SkillDefinition> = emptyList()
    private var rules: List<RuleDefinition> = emptyList()
    private var workflows: List<WorkflowDefinition> = emptyList()
    private var mcpConfigs: List<AgentMcpConfig> = emptyList()

    init {
        AgentHomeDirectoryHelpers.ensureDirectories(basePath, logger)
        loadAll()
    }

    override fun getConfig(): AgentHomeConfig = config

    override fun getSkills(): List<SkillDefinition> = skills

    override fun getActiveSkills(): List<SkillDefinition> {
        val active = config.activeSkills
        if (active.isEmpty()) return skills
        return skills.filter { it.fileName in active }
    }

    override fun getRules(): List<RuleDefinition> = rules

    override fun getWorkflows(): List<WorkflowDefinition> = workflows

    override fun getMcpConfigs(): List<AgentMcpConfig> = mcpConfigs

    override fun buildSystemPrompt(): String {
        val builder = StringBuilder()
        appendSkillsSection(builder)
        appendRulesSection(builder)
        return builder.toString().trim()
    }

    override fun reload() {
        logger.info("Reloading agent home directory: {}", basePath)
        loadAll()
    }

    // ── Private loading methods ──────────────────────────────────

    private fun loadAll() {
        config = loadConfig()
        skills = loadMarkdownFiles(".agent/skills") { name, content ->
            SkillParser.parse(name, content)
        }
        rules = loadMarkdownFiles(".agent/rules") { name, content ->
            RuleParser.parse(name, content)
        }
        workflows = loadMarkdownFiles(".agent/workflows") { name, content ->
            AgentHomeDirectoryHelpers.parseWorkflow(name, content)
        }
        mcpConfigs = loadJsonFiles(".agent/mcp")
    }

    private fun loadConfig(): AgentHomeConfig {
        val configFile = basePath.resolve("config.json")
        if (!Files.exists(configFile)) {
            logger.warn("config.json not found at {}; using defaults", configFile)
            return AgentHomeConfig()
        }
        return parseConfigFile(configFile)
    }

    private fun parseConfigFile(configFile: Path): AgentHomeConfig {
        return try {
            val content = Files.readString(configFile)
            JsonConfig.instance.decodeFromString<AgentHomeConfig>(content)
        } catch (e: Exception) {
            logger.warn("Failed to parse config.json: {}; using defaults", e.message)
            AgentHomeConfig()
        }
    }

    /**
     * Loads and parses all `.md` files from a subdirectory using [parser].
     */
    private fun <T> loadMarkdownFiles(
        subDir: String,
        parser: (String, String) -> T?
    ): List<T> {
        val dir = basePath.resolve(subDir)
        if (!Files.isDirectory(dir)) return emptyList()

        return Files.list(dir).use { stream ->
            stream.filter { it.toString().endsWith(".md") }
                .toList()
                .mapNotNull { parseFile(it, parser) }
        }
    }

    private fun <T> parseFile(
        path: Path,
        parser: (String, String) -> T?
    ): T? {
        return try {
            val content = Files.readString(path)
            parser(path.fileName.toString(), content)
        } catch (e: Exception) {
            logger.warn("Failed to read file '{}': {}", path.fileName, e.message)
            null
        }
    }

    /**
     * Loads and parses all `.json` files from the MCP config directory.
     */
    private fun loadJsonFiles(subDir: String): List<AgentMcpConfig> {
        val dir = basePath.resolve(subDir)
        if (!Files.isDirectory(dir)) return emptyList()

        return Files.list(dir).use { stream ->
            stream.filter { it.toString().endsWith(".json") }
                .toList()
                .mapNotNull { loadMcpFile(it) }
        }
    }

    private fun loadMcpFile(path: Path): AgentMcpConfig? {
        return try {
            val content = Files.readString(path)
            AgentHomeDirectoryHelpers.parseMcpConfig(
                path.fileName.toString(), content, logger
            )
        } catch (e: Exception) {
            logger.warn("Failed to read MCP config '{}': {}", path.fileName, e.message)
            null
        }
    }

    // ── System prompt building ───────────────────────────────────

    private fun appendSkillsSection(builder: StringBuilder) {
        val active = getActiveSkills()
        if (active.isEmpty()) return
        builder.appendLine("## Skills\n")
        for (skill in active) {
            builder.appendLine("### ${skill.fileName}\n")
            builder.appendLine(skill.rawContent)
            builder.appendLine()
        }
    }

    private fun appendRulesSection(builder: StringBuilder) {
        if (rules.isEmpty()) return
        builder.appendLine("## Rules\n")
        for (rule in rules) {
            builder.appendLine("### ${rule.fileName}\n")
            builder.appendLine(rule.rawContent)
            builder.appendLine()
        }
    }
}
