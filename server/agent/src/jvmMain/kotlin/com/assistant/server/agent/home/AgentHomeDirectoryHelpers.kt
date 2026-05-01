package com.assistant.server.agent.home

import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.home.WorkflowDefinition
import com.assistant.config.JsonConfig
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Helper functions for [AgentHomeDirectoryLoader].
 *
 * Extracted to keep the main loader file within the 200-line limit.
 * Provides directory validation, workflow parsing, and MCP config parsing.
 */
object AgentHomeDirectoryHelpers {

    /** Standard subdirectories expected inside an Agent Home Directory. */
    val REQUIRED_DIRS = listOf(
        ".agent/skills",
        ".agent/rules",
        ".agent/workflows",
        ".agent/mcp",
        ".agent/memory",
        "workspace"
    )

    /**
     * Ensures all required subdirectories exist, creating missing ones.
     *
     * @param basePath root of the agent home directory
     * @param logger logger for warning messages
     */
    fun ensureDirectories(basePath: Path, logger: Logger) {
        for (dirName in REQUIRED_DIRS) {
            val dir = basePath.resolve(dirName)
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
                logger.warn("Created missing directory: {}", dir)
            }
        }
    }

    /**
     * Parses a markdown workflow file into a [WorkflowDefinition].
     *
     * Extracts `# Title` as name, first paragraph as description,
     * and `## Steps` section items as the steps list.
     *
     * @param fileName name of the source markdown file
     * @param content raw markdown content
     * @return parsed [WorkflowDefinition]
     */
    fun parseWorkflow(fileName: String, content: String): WorkflowDefinition {
        val lines = content.lines()
        val name = extractTitle(lines)
        val description = extractDescription(lines)
        val steps = extractSteps(lines)

        return WorkflowDefinition(
            fileName = fileName,
            name = name,
            description = description,
            steps = steps,
            rawContent = content
        )
    }

    /**
     * Extracts the `# Title` from the first level-1 header line.
     */
    private fun extractTitle(lines: List<String>): String {
        return lines.firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")?.trim().orEmpty()
    }

    /**
     * Extracts the first non-blank paragraph after the title line.
     */
    private fun extractDescription(lines: List<String>): String {
        val titleIndex = lines.indexOfFirst { it.startsWith("# ") }
        if (titleIndex < 0) return ""

        return lines.drop(titleIndex + 1)
            .dropWhile { it.isBlank() }
            .takeWhile { it.isNotBlank() && !it.startsWith("#") }
            .joinToString(" ") { it.trim() }
    }

    /**
     * Extracts step items from the `## Steps` section.
     */
    private fun extractSteps(lines: List<String>): List<String> {
        val stepsIdx = lines.indexOfFirst {
            it.trim().equals("## Steps", ignoreCase = true)
        }
        if (stepsIdx < 0) return emptyList()

        return lines.drop(stepsIdx + 1)
            .takeWhile { !it.startsWith("## ") }
            .map { stripBullet(it) }
            .filter { it.isNotBlank() }
    }

    private fun stripBullet(line: String): String {
        val trimmed = line.trim()
        return when {
            trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
            trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
            trimmed.startsWith("• ") -> trimmed.removePrefix("• ").trim()
            trimmed.matches(Regex("^\\d+\\.\\s.*")) ->
                trimmed.replaceFirst(Regex("^\\d+\\.\\s"), "").trim()
            else -> trimmed
        }
    }

    /**
     * Parses a JSON file into an [AgentMcpConfig].
     *
     * @param fileName name of the source JSON file
     * @param content raw JSON content
     * @param logger logger for warning messages
     * @return parsed [AgentMcpConfig], or null if parsing fails
     */
    fun parseMcpConfig(
        fileName: String,
        content: String,
        logger: Logger
    ): AgentMcpConfig? {
        return try {
            JsonConfig.instance.decodeFromString<AgentMcpConfig>(content)
        } catch (e: Exception) {
            logger.warn("Failed to parse MCP config '{}': {}", fileName, e.message)
            null
        }
    }
}
