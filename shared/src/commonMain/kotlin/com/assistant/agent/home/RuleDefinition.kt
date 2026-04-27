package com.assistant.agent.home

import kotlinx.serialization.Serializable

/**
 * Parsed representation of a markdown rule file from the Agent Home Directory.
 *
 * Rules define classification logic and decision criteria using a structured
 * markdown format. Each rule file in `.agent/rules/` is parsed into this model
 * at agent startup. When multiple rules match the same input, they are applied
 * in priority order (lowest number first).
 *
 * @property fileName Name of the source markdown file (e.g., "ticket-classification.md")
 * @property purpose What the rule classifies or decides — parsed from the `## Purpose` section
 * @property keywords Recognition patterns and trigger words — parsed from the `## Keywords` section
 * @property categories Possible classification outcomes — parsed from the `## Categories` section
 * @property priority Ordering when multiple rules match (lowest first, default 100) — parsed from the `## Priority` section
 * @property conflictResolution How to handle ambiguous cases — parsed from the `## Conflict Resolution` section
 * @property rawContent The original unprocessed markdown content of the rule file
 */
@Serializable
data class RuleDefinition(
    val fileName: String = "",
    val purpose: String = "",
    val keywords: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val priority: Int = 100,
    val conflictResolution: String = "",
    val rawContent: String = ""
)
