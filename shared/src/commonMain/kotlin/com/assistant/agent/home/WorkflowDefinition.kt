package com.assistant.agent.home

import kotlinx.serialization.Serializable

/**
 * Parsed representation of a markdown workflow file from the Agent Home Directory.
 *
 * Workflows define multi-step processes that an agent follows to complete
 * complex tasks. Each workflow file in `.agent/workflows/` is parsed into
 * this model at agent startup. Workflows may reference skills and rules
 * used at each step.
 *
 * @property fileName Name of the source markdown file (e.g., "analysis-workflow.md")
 * @property name Human-readable name of the workflow
 * @property description Brief description of what the workflow accomplishes
 * @property steps Ordered list of step descriptions the agent follows
 * @property rawContent The original unprocessed markdown content of the workflow file
 */
@Serializable
data class WorkflowDefinition(
    val fileName: String = "",
    val name: String = "",
    val description: String = "",
    val steps: List<String> = emptyList(),
    val rawContent: String = ""
)
