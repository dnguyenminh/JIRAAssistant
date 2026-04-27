package com.assistant.agent.home

import kotlinx.serialization.Serializable

/**
 * Parsed representation of a markdown skill file from the Agent Home Directory.
 *
 * Skills define agent capabilities using a structured markdown format with
 * sections for purpose, available tools, step-by-step procedure, output format,
 * and constraints. Each skill file in `.agent/skills/` is parsed into this model
 * at agent startup.
 *
 * @property fileName Name of the source markdown file (e.g., "analysis.md")
 * @property purpose What the skill does — parsed from the `## Purpose` section
 * @property availableTools Tools the skill can use — parsed from the `## Available Tools` section
 * @property procedure Step-by-step instructions — parsed from the `## Procedure` section
 * @property outputFormat Expected output structure — parsed from the `## Output Format` section
 * @property constraints Limitations and rules — parsed from the `## Constraints` section
 * @property rawContent The original unprocessed markdown content of the skill file
 */
@Serializable
data class SkillDefinition(
    val fileName: String = "",
    val purpose: String = "",
    val availableTools: List<String> = emptyList(),
    val procedure: String = "",
    val outputFormat: String = "",
    val constraints: String = "",
    val rawContent: String = ""
)
