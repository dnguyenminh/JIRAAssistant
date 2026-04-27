# Implementation Plan: Native BA Tool Removal

## Overview

Remove all 6 hardcoded native BA tools and associated registration/reference code using a bottom-up deletion pattern. After this change, the BA subprocess relies entirely on dynamically configured MCP tools from the Integrations page. Implementation follows 4 layers: delete tool files → update consumers → update TaskMessageBuilder → update tests, with a final compilation and test checkpoint.

## Tasks

- [x] 1. Delete native BA tool implementation files (Layer 1)
  - [x] 1.1 Delete the 6 native BA tool files and the tools/ directory
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/FetchJiraDetailsTool.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/GetLinkedIssuesTool.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/FetchCommentsTool.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/LookupKBRecordTool.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/SearchKBTool.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/ProcessAttachmentTool.kt`
    - Verify the `tools/` directory is empty or deleted
    - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Update BAAgentModule and BAAgentConfig (Layer 2)
  - [x] 2.1 Remove wireNativeBATools from BAAgentModule
    - Delete the `wireNativeBATools()` private function entirely
    - Remove the `wireNativeBATools(toolRegistry)` call from the agent factory lambda
    - Remove the `import com.assistant.server.agent.ba.tools.*` wildcard import
    - Keep `registerMcpTools(toolRegistry)` as the sole tool registration path
    - Keep BASubprocessOrchestrator singleton and BADocumentAgent factory unchanged
    - Update the KDoc comment to reflect MCP-only tool registration
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 2.2 Clean BAAgentConfig tool registrations
    - Remove the `tools { register("fetchJiraDetails") ... register("searchKB") }` block from `buildBAAgentConfig()`
    - Remove the `forTool("fetchJiraDetails", ErrorStrategy.RETRY)` entry from the `errorStrategy` block
    - Keep `memorySchema`, `phases`, `limits`, and `buildSubprocessConfig()` unchanged
    - _Requirements: 2.1_

- [x] 3. Update TaskMessageBuilder for dynamic tool instructions (Layer 3)
  - [x] 3.1 Add empty tool list handling to buildToolUsageInstructions
    - Add `if (tools.isEmpty())` check at the start of `buildToolUsageInstructions()`
    - When tools list is empty, output a `NO_TOOLS_MESSAGE` constant instructing the AI to produce the document using only the root ticket ID and document type — no tool calls possible
    - When tools list is non-empty, keep existing behavior (generate tool call format + list all tools)
    - Add the `NO_TOOLS_MESSAGE` private constant
    - _Requirements: 3.1, 3.4, 6.3_

  - [x] 3.2 Make strategy hint constants tool-agnostic
    - Update `BRD_STRATEGY_HINT` to say "Start by exploring the root ticket details" and "Use available tools to gather data" instead of referencing specific tool names
    - Update `FSD_STRATEGY_HINT` similarly — remove any specific tool name references
    - Update `SLIDES_STRATEGY_HINT` similarly — remove any specific tool name references
    - Verify no native tool names (`fetchJiraDetails`, `getLinkedIssues`, `fetchComments`, `lookupKBRecord`, `searchKB`, `processAttachment`) appear anywhere in TaskMessageBuilder.kt
    - _Requirements: 3.2_

- [x] 4. Checkpoint — Compile production code
  - Ensure `./gradlew :server:compileKotlinJvm` passes with zero errors
  - Ask the user if questions arise

- [x] 5. Delete native BA tool test files and update test doubles (Layer 4)
  - [x] 5.1 Delete BAAgentToolsTest and BAToolTestDoubles
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/BAAgentToolsTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/BAToolTestDoubles.kt`
    - Verify the `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/tools/` directory is empty or deleted
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 5.2 Update BADocumentAgentIntegrationTestDoubles with MCP-style tool names
    - Update `FakeSubprocessProxy.getAvailableToolDescriptors()` to return MCP-style descriptors: `ToolDescriptor("mcp_jira_get_issue", "Get Jira issue details")` instead of `ToolDescriptor("fetchJiraDetails", "Fetch Jira ticket details")`
    - Update `toolCallStdoutProvider()` to use `"name":"mcp_jira_get_issue"` instead of `"name":"fetchJiraDetails"` in the simulated subprocess stdout JSON
    - _Requirements: 4.5, 5.1, 5.3_

  - [x] 5.3 Update BAAgentModuleRegistrationTest
    - Review and remove any assertions about native BA tool registration (if present)
    - Keep assertions about BASubprocessOrchestrator registration and `ba-document` agent type registration
    - _Requirements: 4.4_

  - [x] 5.4 Verify BADocumentAgentIntegrationTest passes with MCP-style tool names
    - Run integration tests to confirm they pass with updated MCP-style tool names in fixtures
    - No structural changes needed — the test logic is tool-name-agnostic
    - _Requirements: 5.2_

  - [x] 5.5 Write property test for tool usage instructions (Property 1)
    - **Property 1: Tool usage instructions contain all provided tools and no hardcoded native names**
    - Add a new test to `TaskMessageBuilderPropertyTest.kt` that generates random lists of `ToolDescriptor` (1–20 tools with `mcp_*` style names)
    - Verify the output of `buildToolUsageInstructions()` contains every tool name from the input list
    - Verify the output does NOT contain any of the 6 deleted native tool names: `fetchJiraDetails`, `getLinkedIssues`, `fetchComments`, `lookupKBRecord`, `searchKB`, `processAttachment`
    - Use Kotest Property Testing with minimum 100 iterations
    - Annotate with `// Feature: native-tool-removal, Property 1: Tool usage instructions contain all provided tools and no hardcoded native names`
    - **Validates: Requirements 3.1, 3.3**

- [x] 6. Update upstream spec references (Requirement 7)
  - [x] 6.1 Update agent-subprocess-orchestration requirements.md
    - Mark Requirement 5 ("Native BA Tool Registration for Subprocess") as superseded by this native-tool-removal spec
    - Add a note that native BA tools have been deleted and the system relies entirely on MCP tools
    - Update acceptance criterion 2.3 to reference dynamically available MCP tools instead of hardcoded BA tool names (`fetchJiraDetails`, `getLinkedIssues`, etc.)
    - _Requirements: 7.1, 7.3_

  - [x] 6.2 Update agent-subprocess-orchestration design.md
    - Update the component dependency graph to remove the "Native BA Tools (6 tools)" node from ToolRegistry dependencies
    - Update any references to `wireNativeBATools()` or native tool registration
    - _Requirements: 7.2_

- [x] 7. Final checkpoint — Compile and run all tests
  - Run `./gradlew :server:compileKotlinJvm` to verify production code compiles
  - Run `./gradlew :server:compileTestKotlinJvm` to verify test code compiles
  - Run `./gradlew :server:test --tests "com.assistant.server.agent.ba.*"` to verify all BA agent tests pass
  - Ensure all tests pass, ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- The bottom-up deletion order (Layer 1 → 2 → 3 → 4) prevents intermediate compilation errors
- Files explicitly left unchanged: `JiraContextMemoryExtensions.kt`, `AttachmentCurationStep.kt`, `BASubprocessOrchestrator.kt`, `SubprocessProxy`, `AgentMcpManager` — see design document for rationale
- Property test uses Kotest Property Testing (already in project dependencies)
- Checkpoint at task 4 validates production code before modifying tests
