package com.assistant.server.agent.ba.subprocess

/**
 * String constants used by [TaskMessageBuilder] for prompt construction.
 *
 * Extracted to respect the 200-line file limit.
 */

internal const val RESPONSE_PROTOCOL_SECTION = """## RESPONSE PROTOCOL

You MUST follow these rules when responding:

1. **End-of-response delimiter**: When you have finished your complete response, output the following delimiter on a separate line by itself:
---END---

2. **Tool call format**: When you need to call a tool, output a single-line JSON object in this exact format:
{"toolCall":{"id":"<correlationId>","name":"<toolName>","arguments":{"param":"value"}}}

Each tool call MUST be on its own line. Do NOT wrap tool calls in markdown code blocks.
After each tool call, wait for the tool result before continuing."""

internal const val TOOL_CALL_FORMAT_EXAMPLE = """```json
{"toolCall":{"id":"<correlationId>","name":"<toolName>","arguments":{"param":"value"}}}
```"""

internal const val NO_TOOLS_MESSAGE =
    "No tools are currently available. Produce the document " +
        "using only the root ticket ID and document type provided " +
        "in this task message. Do not attempt tool calls."

internal const val BRD_STRATEGY_HINT =
    "Prioritize business goals, stakeholder needs, and acceptance criteria. " +
        "Start by exploring the root ticket details, then look for linked issues " +
        "and comments for requirements context. Use available tools to gather data."

internal const val FSD_STRATEGY_HINT =
    "Prioritize technical architecture, data models, and API specifications. " +
        "Start by exploring the root ticket details, then look for technical " +
        "details and linked implementation tickets. Use available tools to gather data."

internal const val SLIDES_STRATEGY_HINT =
    "Prioritize executive summary, key metrics, and visual elements. " +
        "Start by exploring the root ticket details, then gather high-level " +
        "business context and key decisions from comments. Use available tools to gather data."

/** Read-only tool prefixes allowed in prompts for document generation. */
internal val READ_ONLY_TOOL_PREFIXES = listOf(
    "mcp_local_knowledge_base_search",
    "mcp_local_knowledge_base_get",
    "mcp_jira_get_issue",
    "mcp_jira_search",
    "mcp_jira_get_project",
    "mcp_jira_get_sprint",
    "mcp_jira_get_board",
    "mcp_jira_get_agile",
    "mcp_jira_get_link_types",
    "mcp_jira_get_field",
    "mcp_jira_search_fields",
    "mcp_jira_download_attachments",
    "mcp_jira_get_user_profile",
    "mcp_jira_get_all_projects",
    "mcp_jira_get_worklog"
)
