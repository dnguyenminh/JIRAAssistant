package com.assistant.server.agent.home

import com.assistant.agent.home.RuleDefinition
import org.slf4j.LoggerFactory

/**
 * Parses markdown rule files into [RuleDefinition] instances.
 *
 * Rule files use a structured markdown format with `## Section` headers.
 * Required sections are `## Purpose` and `## Categories` — files missing
 * either section are skipped with a logged warning.
 *
 * List sections (`## Keywords`, `## Categories`) are parsed line-by-line,
 * stripping optional bullet markers (`-`, `*`, `•`).
 *
 * `## Priority` is parsed as an integer; defaults to 100 if missing or invalid.
 */
object RuleParser {

    private val logger = LoggerFactory.getLogger(RuleParser::class.java)

    private const val DEFAULT_PRIORITY = 100
    private const val SECTION_PREFIX = "## "

    /**
     * Parses a markdown rule file into a [RuleDefinition].
     *
     * @param fileName name of the source markdown file
     * @param content raw markdown content of the file
     * @return parsed [RuleDefinition], or null if required sections are missing
     */
    fun parse(fileName: String, content: String): RuleDefinition? {
        val sections = extractSections(content)

        if (!hasRequiredSections(fileName, sections)) {
            return null
        }

        return RuleDefinition(
            fileName = fileName,
            purpose = sections["Purpose"]?.trim().orEmpty(),
            keywords = parseList(sections["Keywords"]),
            categories = parseList(sections["Categories"]),
            priority = parsePriority(sections["Priority"]),
            conflictResolution = sections["Conflict Resolution"]?.trim().orEmpty(),
            rawContent = content
        )
    }

    /**
     * Extracts `## Section` headers and their content from markdown text.
     */
    private fun extractSections(content: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        var currentName: String? = null
        val buffer = StringBuilder()

        for (line in content.lines()) {
            if (line.startsWith(SECTION_PREFIX)) {
                flushSection(currentName, buffer, sections)
                currentName = line.removePrefix(SECTION_PREFIX).trim()
                buffer.clear()
            } else {
                buffer.appendLine(line)
            }
        }
        flushSection(currentName, buffer, sections)

        return sections
    }

    private fun flushSection(
        name: String?,
        buffer: StringBuilder,
        sections: MutableMap<String, String>
    ) {
        if (name != null) {
            sections[name] = buffer.toString()
        }
    }

    /**
     * Validates that required sections (`Purpose`, `Categories`) are present.
     */
    private fun hasRequiredSections(
        fileName: String,
        sections: Map<String, String>
    ): Boolean {
        val missing = mutableListOf<String>()
        if ("Purpose" !in sections) missing += "Purpose"
        if ("Categories" !in sections) missing += "Categories"

        if (missing.isNotEmpty()) {
            logger.warn(
                "Skipping rule file '{}': missing required sections {}",
                fileName, missing
            )
            return false
        }
        return true
    }

    /**
     * Parses a section body as a list of items, one per line.
     * Strips leading bullet markers (`-`, `*`, `•`) and whitespace.
     */
    private fun parseList(sectionContent: String?): List<String> {
        if (sectionContent.isNullOrBlank()) return emptyList()
        return sectionContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripBullet(it) }
            .filter { it.isNotEmpty() }
    }

    private fun stripBullet(line: String): String {
        val trimmed = line.trimStart()
        return when {
            trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
            trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
            trimmed.startsWith("• ") -> trimmed.removePrefix("• ").trim()
            else -> trimmed
        }
    }

    /**
     * Parses the `## Priority` section as an integer.
     * Returns [DEFAULT_PRIORITY] (100) if the section is missing or not a valid integer.
     */
    private fun parsePriority(sectionContent: String?): Int {
        if (sectionContent.isNullOrBlank()) return DEFAULT_PRIORITY
        return sectionContent.trim().toIntOrNull() ?: DEFAULT_PRIORITY
    }
}
