package com.assistant.server.agent.home

import com.assistant.agent.home.SkillDefinition
import org.slf4j.LoggerFactory

/**
 * Parses markdown skill files into [SkillDefinition] instances.
 *
 * Skill files use a structured markdown format with `## Section Name` headers.
 * Required sections are `## Purpose` and `## Procedure` — files missing either
 * are skipped with a logged warning. Optional sections include `## Available Tools`,
 * `## Output Format`, and `## Constraints`.
 *
 * The `## Available Tools` section is parsed as a list of tool names (one per line,
 * bullet markers stripped). All other sections are returned as plain text content.
 */
object SkillParser {

    private val logger = LoggerFactory.getLogger(SkillParser::class.java)

    private const val SECTION_PURPOSE = "Purpose"
    private const val SECTION_AVAILABLE_TOOLS = "Available Tools"
    private const val SECTION_PROCEDURE = "Procedure"
    private const val SECTION_OUTPUT_FORMAT = "Output Format"
    private const val SECTION_CONSTRAINTS = "Constraints"

    /**
     * Parses a markdown skill file into a [SkillDefinition].
     *
     * @param fileName Name of the source markdown file (e.g., "analysis.md")
     * @param content The raw markdown content of the skill file
     * @return Parsed [SkillDefinition], or null if required sections are missing
     */
    fun parse(fileName: String, content: String): SkillDefinition? {
        val sections = extractSections(content)

        if (!validateRequiredSections(fileName, sections)) {
            return null
        }

        return SkillDefinition(
            fileName = fileName,
            purpose = sections[SECTION_PURPOSE]?.trim() ?: "",
            availableTools = parseToolList(sections[SECTION_AVAILABLE_TOOLS]),
            procedure = sections[SECTION_PROCEDURE]?.trim() ?: "",
            outputFormat = sections[SECTION_OUTPUT_FORMAT]?.trim() ?: "",
            constraints = sections[SECTION_CONSTRAINTS]?.trim() ?: "",
            rawContent = content
        )
    }

    /**
     * Extracts `## Section Name` headers and their content from markdown text.
     *
     * @param content Raw markdown content
     * @return Map of section name to section content (text between headers)
     */
    private fun extractSections(content: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        val lines = content.lines()
        var currentSection: String? = null
        val currentContent = StringBuilder()

        for (line in lines) {
            val sectionName = parseSectionHeader(line)
            if (sectionName != null) {
                savePendingSection(sections, currentSection, currentContent)
                currentSection = sectionName
                currentContent.clear()
            } else if (currentSection != null) {
                currentContent.appendLine(line)
            }
        }

        savePendingSection(sections, currentSection, currentContent)
        return sections
    }

    /**
     * Parses a line as a `## Section Name` header.
     *
     * @return The section name if the line is a level-2 header, null otherwise
     */
    private fun parseSectionHeader(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.startsWith("## ")) {
            return trimmed.removePrefix("## ").trim()
        }
        return null
    }

    /**
     * Saves accumulated content for the current section into the sections map.
     */
    private fun savePendingSection(
        sections: MutableMap<String, String>,
        sectionName: String?,
        content: StringBuilder
    ) {
        if (sectionName != null && content.isNotEmpty()) {
            sections[sectionName] = content.toString()
        }
    }

    /**
     * Validates that required sections (`## Purpose`, `## Procedure`) are present.
     *
     * @return true if all required sections exist, false otherwise (with logged warning)
     */
    private fun validateRequiredSections(
        fileName: String,
        sections: Map<String, String>
    ): Boolean {
        val missingPurpose = SECTION_PURPOSE !in sections
        val missingProcedure = SECTION_PROCEDURE !in sections

        if (missingPurpose || missingProcedure) {
            val missing = buildMissingSectionsList(missingPurpose, missingProcedure)
            logger.warn("Skipping skill file '{}': missing required sections: {}", fileName, missing)
            return false
        }
        return true
    }

    /**
     * Builds a human-readable list of missing required section names.
     */
    private fun buildMissingSectionsList(
        missingPurpose: Boolean,
        missingProcedure: Boolean
    ): String {
        val missing = mutableListOf<String>()
        if (missingPurpose) missing.add("## $SECTION_PURPOSE")
        if (missingProcedure) missing.add("## $SECTION_PROCEDURE")
        return missing.joinToString(", ")
    }

    /**
     * Parses the `## Available Tools` section content into a list of tool names.
     *
     * Each line is treated as a tool name. Bullet markers (`-`, `*`, `•`) and
     * leading/trailing whitespace are stripped. Empty lines are skipped.
     *
     * @param sectionContent Raw content of the Available Tools section, or null
     * @return List of parsed tool names
     */
    private fun parseToolList(sectionContent: String?): List<String> {
        if (sectionContent.isNullOrBlank()) return emptyList()

        return sectionContent.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
    }
}
