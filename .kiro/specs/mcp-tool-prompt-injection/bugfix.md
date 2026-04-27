# Bugfix Requirements Document

## Introduction

> **PARTIALLY SUPERSEDED by [native-tool-removal spec](../native-tool-removal/requirements.md) and [agent-subprocess-orchestration spec](../agent-subprocess-orchestration/requirements.md).** The 6 native BA tools (`fetchJiraDetails`, `getLinkedIssues`, `fetchComments`, `lookupKBRecord`, `searchKB`, `processAttachment`) referenced below have been deleted from the codebase. The BA agent now uses subprocess orchestration with `TaskMessageBuilder` generating tool instructions dynamically from MCP tools registered via the Integrations page. The `DefaultMcpToolRegistrar.buildToolBlock()` issue described below applied to the legacy prompt pipeline which has also been removed. References to native BA tools below are historical context only.

`DefaultMcpToolRegistrar.buildToolBlock()` hardcodes only 2 KB lookup tools (`kb_search`, `kb_read`) in the BA agent prompt's "AVAILABLE TOOLS" section. It does not inject any MCP tools from the Integrations page (Jira MCP with 45 tools, Playwright with 22 tools, markitdown with 1 tool, etc.) ~~nor does it list the BA agent's own native tools (fetchJiraDetails, getLinkedIssues, fetchComments, lookupKBRecord, searchKB, processAttachment)~~ *(native BA tools have been deleted per the native-tool-removal spec)*.

This was written before the Generic Agent Framework was implemented and was never updated to use the framework's `ToolRegistry.listTools()` or `McpProcessManager.getActiveTools()` for dynamic tool discovery. As a result, the BA agent's LLM only knows about 2 KB lookup tools during BRD/FSD generation, severely limiting its data collection capabilities — it cannot use markitdown for attachment parsing, Jira MCP for direct queries, or any other shared MCP tool.

**Impact**: BA agent cannot leverage 99+ available MCP tools during document generation. The prompt tells the AI about only 2 tools out of 101+ available, making the agent unable to parse attachments (markitdown), query Jira directly (Jira MCP), interact with browser (Playwright), or use any other configured MCP server tools.

**Evidence**: The generated prompt file `server/data/prompts/BRD_ICL2-15_2026-04-21T10-25-57_1e41125f.txt` shows only:
```
=== AVAILABLE TOOLS ===
Tool: kb_search
Tool: kb_read
Available tickets for lookup: CRP-84, ICL2-26, ...
```
While the Integrations page shows 5 MCP servers RUNNING with 101 total tools.

**Related Requirements**: Generic Agent Framework Requirement 19 (MCP Integration) states "MCP tools SHALL be invocable through the same ToolRegistry.invoke() interface as native AgentTools". Requirement 20 (Orchestrator MCP Proxy) states "THE Orchestrator SHALL inject the list of available tools into the agent subprocess's context at session start".

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `DefaultMcpToolRegistrar.buildToolBlock()` is called THEN the system outputs a hardcoded block containing only `kb_search` and `kb_read` tool descriptions, ignoring all other registered tools in the `ToolRegistry`

1.2 WHEN MCP servers are running with active tools (e.g., Jira MCP with 45 tools, markitdown with 1 tool, Playwright with 22 tools) THEN the system does not read from `McpProcessManager.getActiveTools()` and none of these tools appear in the prompt's "AVAILABLE TOOLS" section

1.3 ~~WHEN native BA agent tools are registered in `ToolRegistry` (fetchJiraDetails, getLinkedIssues, fetchComments, lookupKBRecord, searchKB, processAttachment) THEN the system does not read from `ToolRegistry.listTools()` and none of these tools appear in the prompt's "AVAILABLE TOOLS" section~~ **No longer applicable** — native BA tools have been deleted per the [native-tool-removal spec](../native-tool-removal/requirements.md). ToolRegistry now contains only MCP tools.

1.4 WHEN the BA agent's LLM receives the generated prompt THEN it can only attempt to call `kb_search` and `kb_read`, and cannot discover or use any other available tool (markitdown for attachment parsing, Jira MCP for direct queries, etc.)

### Expected Behavior (Correct)

2.1 WHEN `McpToolRegistrar.buildToolBlock()` is called THEN the system SHALL query `ToolRegistry.listTools()` to discover all registered MCP tools and include each tool's name, description, and parameter names in the "AVAILABLE TOOLS" section *(Updated: native tools no longer exist — ToolRegistry contains only MCP tools)*

2.2 WHEN MCP servers are running with active tools THEN the system SHALL query `McpProcessManager.getActiveTools()` to discover all shared MCP tools and include each tool's name, description, and input schema in the "AVAILABLE TOOLS" section

2.3 ~~WHEN both native tools and MCP tools are available THEN the system SHALL group tools by source (Native BA Tools, Shared MCP Tools) in the prompt for clarity, listing each tool with its name, description, and parameters~~ **No longer applicable** — only MCP tools exist. No grouping by source needed.

2.4 WHEN the BA agent's LLM receives the generated prompt THEN it SHALL see ALL available tools from ToolRegistry and McpProcessManager, enabling it to choose the most appropriate tool for each data collection task (e.g., markitdown for parsing .docx attachments, Jira MCP for querying linked issues directly)

2.5 WHEN an MCP server is not running or has no active tools THEN the system SHALL gracefully omit that server's tools from the prompt without error, and SHALL still include all other available tools

2.6 WHEN no tools are available at all (no MCP servers running) THEN the system SHALL return an empty string or minimal block without crashing *(Updated: "no native tools registered" condition removed — native tools no longer exist)*

### Unchanged Behavior (Regression Prevention)

3.1 WHEN `referenceOnlyTickets` are provided THEN the system SHALL CONTINUE TO include the "Available tickets for lookup" line with the ticket list and the max-lookups warning, preserving the existing KB lookup guidance

3.2 WHEN `referenceOnlyTickets` is empty THEN the system SHALL CONTINUE TO return an empty string (no tool block added to prompt)

3.3 WHEN `isToolUseSupported()` is called with an agent type THEN the system SHALL CONTINUE TO correctly identify tool-capable agents (GeminiCliAgent, gemini, gemini-cli)

3.4 WHEN curation is disabled (no `McpToolRegistrar` injected into `MasterPromptBuilder`) THEN the system SHALL CONTINUE TO skip the MCP tool block entirely, preserving the existing opt-in behavior

3.5 WHEN the prompt exceeds the budget limit after tool block injection THEN the system SHALL CONTINUE TO apply `PromptTruncator.truncate()` correctly, and the tool block SHALL NOT be in the protected sections set (allowing it to be trimmed if needed to stay within budget)
