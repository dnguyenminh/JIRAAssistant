# Requirements Document — Native BA Tool Removal

## Introduction

The BA agent currently has 6 hardcoded native tools registered in `BAAgentModule.wireNativeBATools()`: FetchJiraDetailsTool, GetLinkedIssuesTool, FetchCommentsTool, LookupKBRecordTool, SearchKBTool, and ProcessAttachmentTool. These tools duplicate functionality already available through the dynamic MCP tool system configured via the Integrations page (Jira MCP server provides 45+ tools including issue fetching, comments, linked issues, etc.).

This spec removes all 6 native BA tool files, the `wireNativeBATools()` registration function, and all hardcoded tool name references. The BA subprocess will rely entirely on MCP tools dynamically registered through `AgentMcpManager` from the Integrations page. The `SubprocessProxy.getAvailableToolDescriptors()` becomes the single source of truth for available tools — whatever MCP servers the user configures on the Integrations page determines what tools the subprocess can use.

This aligns with the dynamic configuration philosophy: tools are configured by the user through the Integrations page, not hardcoded in the codebase.

## Glossary

- **Native_BA_Tool**: One of the 6 hardcoded `AgentTool` implementations in `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/` — FetchJiraDetailsTool, GetLinkedIssuesTool, FetchCommentsTool, LookupKBRecordTool, SearchKBTool, ProcessAttachmentTool. These are registered directly into ToolRegistry via `wireNativeBATools()` in BAAgentModule
- **MCP_Tool**: A tool provided by an MCP server configured on the Integrations page, registered dynamically into ToolRegistry via `AgentMcpManager`. MCP tools follow the `mcp_{serverName}_{toolName}` naming convention
- **Integrations_Page**: The user-facing configuration page where MCP servers (Jira, Playwright, database, knowledge base, etc.) are added, configured, and managed. Each configured MCP server auto-registers its tools into ToolRegistry at agent initialization
- **AgentMcpManager**: The component that reads MCP server configurations from `AgentHomeDirectory`, starts configured servers, and auto-registers their tools into ToolRegistry with the `mcp_{serverName}_{toolName}` prefix
- **ToolRegistry**: The central registry holding all available tools — after this change, it contains only MCP tools (no native BA tools). Tools are discoverable via `listTools()` and invocable via `invoke()`
- **SubprocessProxy**: The interface that proxies tool calls between the AI subprocess and ToolRegistry. `getAvailableToolDescriptors()` returns all available tools for the subprocess to discover and invoke
- **TaskMessageBuilder**: The component that constructs the initial task message sent to the AI subprocess, including tool usage instructions based on dynamically available tools
- **BAAgentModule**: The Koin module that registers the BA agent factory, MCP tools, and (currently) native BA tools. After this change, it registers only MCP tools

## Requirements

### Requirement 1: Delete Native BA Tool Files

**User Story:** As a developer, I want all 6 native BA tool implementation files removed from the codebase, so that there is no dead code and the system relies entirely on dynamically configured MCP tools.

#### Acceptance Criteria

1. THE Build_System SHALL compile successfully after the following 6 files are deleted from `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/`: FetchJiraDetailsTool.kt, GetLinkedIssuesTool.kt, FetchCommentsTool.kt, LookupKBRecordTool.kt, SearchKBTool.kt, ProcessAttachmentTool.kt
2. THE codebase SHALL contain zero import statements referencing `com.assistant.server.agent.ba.tools.FetchJiraDetailsTool`, `GetLinkedIssuesTool`, `FetchCommentsTool`, `LookupKBRecordTool`, `SearchKBTool`, or `ProcessAttachmentTool` after deletion
3. THE `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/` directory SHALL be empty or deleted after removal of all 6 files

### Requirement 2: Remove wireNativeBATools from BAAgentModule

**User Story:** As a developer, I want the `wireNativeBATools()` function and its invocation removed from BAAgentModule, so that the agent factory no longer registers hardcoded native tools and relies solely on MCP tools from the Integrations page.

#### Acceptance Criteria

1. THE BAAgentModule SHALL NOT contain the `wireNativeBATools()` function or any call to it
2. THE BAAgentModule SHALL NOT import `JiraClient`, `KBRepository`, or `VectorStore` for the purpose of native tool construction
3. THE BAAgentModule SHALL retain the `registerMcpTools()` function call that registers MCP tools from AgentMcpManager into ToolRegistry — this becomes the only tool registration path
4. THE BAAgentModule SHALL retain the BASubprocessOrchestrator singleton registration and the BADocumentAgent factory registration unchanged
5. WHEN the BA agent factory creates a new BADocumentAgent, THE factory SHALL pass a ToolRegistry that contains only MCP tools registered via AgentMcpManager

### Requirement 3: Dynamic Tool Usage Instructions in TaskMessageBuilder

**User Story:** As a developer, I want TaskMessageBuilder to generate tool usage instructions based on dynamically available MCP tools instead of referencing hardcoded native tool names, so that the subprocess AI discovers and uses whatever tools are configured on the Integrations page.

#### Acceptance Criteria

1. THE TaskMessageBuilder.buildToolUsageInstructions() method SHALL generate tool usage examples based solely on the `List<ToolDescriptor>` parameter passed to it — no hardcoded tool names
2. THE TaskMessageBuilder.buildStrategyHint() method SHALL provide document-type-specific guidance without referencing specific tool names — WHEN the docType is "BRD", THE hint SHALL instruct the AI to explore available tools for fetching ticket details, comments, and linked issues without naming specific tools
3. FOR ALL non-empty lists of ToolDescriptor objects, THE TaskMessageBuilder.buildToolUsageInstructions() output SHALL contain every tool name from the input list
4. FOR ALL empty lists of ToolDescriptor objects, THE TaskMessageBuilder.buildToolUsageInstructions() output SHALL indicate that no tools are currently available and instruct the AI to produce the document using only the information provided in the task message

### Requirement 4: Remove Native BA Tool Tests

**User Story:** As a developer, I want all test files that directly test the 6 native BA tools removed, so that the test suite does not reference deleted production code.

#### Acceptance Criteria

1. THE test file `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/BAAgentToolsTest.kt` SHALL be deleted because it directly tests the 6 deleted native BA tool classes
2. THE test doubles file `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/BAToolTestDoubles.kt` SHALL be deleted because it provides fakes exclusively for the deleted native BA tools
3. THE `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/` directory SHALL be empty or deleted after removal
4. THE test file `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BAAgentModuleRegistrationTest.kt` SHALL be updated to remove assertions about native BA tool registration while retaining assertions about BASubprocessOrchestrator registration and BA agent type registration
5. THE test doubles file `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentIntegrationTestDoubles.kt` SHALL be updated so that `FakeSubprocessProxy.getAvailableToolDescriptors()` returns MCP-style tool descriptors (e.g., `mcp_jira_get_issue`) instead of native tool names (e.g., `fetchJiraDetails`)

### Requirement 5: Update Integration Test Tool Call References

**User Story:** As a developer, I want integration test tool call fixtures updated to use MCP-style tool names, so that tests reflect the real runtime behavior where only MCP tools are available.

#### Acceptance Criteria

1. THE `toolCallStdoutProvider()` function in BADocumentAgentIntegrationTestDoubles.kt SHALL use MCP-style tool names (e.g., `mcp_jira_get_issue`) instead of native tool names (e.g., `fetchJiraDetails`) in its simulated subprocess stdout
2. THE BADocumentAgentIntegrationTest SHALL pass with MCP-style tool names in all tool call simulation scenarios
3. WHEN integration tests simulate tool call requests from the subprocess, THE tool names used SHALL follow the `mcp_{serverName}_{toolName}` naming convention

### Requirement 6: Graceful Behavior with No MCP Servers Configured

**User Story:** As a system operator, I want the BA agent to handle the case where no MCP servers are configured on the Integrations page, so that the system fails gracefully instead of crashing.

#### Acceptance Criteria

1. WHEN no MCP servers are configured on the Integrations page, THE ToolRegistry SHALL contain zero tools after agent initialization
2. WHEN no MCP servers are configured, THE SubprocessProxy.getAvailableToolDescriptors() SHALL return an empty list
3. WHEN no tools are available, THE TaskMessageBuilder SHALL generate a task message that instructs the AI subprocess to produce the document using only the root ticket ID and document type provided in the task message — no tool calls possible
4. WHEN the AI subprocess emits a ToolCallRequest but no matching tool exists in ToolRegistry, THE SubprocessProxy SHALL return a ToolCallResponse with `success = false` and error message "Tool not found" — the AI subprocess SHALL decide how to proceed

### Requirement 7: Update Upstream Spec References

**User Story:** As a developer, I want the agent-subprocess-orchestration spec updated to reflect that native BA tools no longer exist, so that spec documentation stays consistent with the codebase.

#### Acceptance Criteria

1. THE agent-subprocess-orchestration requirements.md Requirement 5 ("Native BA Tool Registration for Subprocess") SHALL be marked as superseded by this native-tool-removal spec, with a note that native BA tools have been deleted and the system relies entirely on MCP tools
2. THE agent-subprocess-orchestration design.md component dependency graph SHALL be updated to remove the "Native BA Tools (6 tools)" node from the ToolRegistry dependencies
3. THE agent-subprocess-orchestration requirements.md acceptance criterion 2.3 (tool usage instructions referencing specific BA tool names) SHALL be updated to reference dynamically available MCP tools instead of hardcoded tool names
