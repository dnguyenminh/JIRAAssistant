# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** — Local KB Tools Missing From Pipeline Tool Collection
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate Local KB tools are absent from `collectToolDescriptors()` and `ToolExecutionBridge` cannot route Local KB tool calls
  - **Scoped PBT Approach**: Scope the property to the concrete failing cases:
    - Case A: `collectToolDescriptors()` with Local KB enabled → assert at least one tool name contains `kb_search` (from Bug Condition: `collectToolDescriptors().none { it.name.contains("kb_search") }`)
    - Case B: `ToolExecutionBridge.execute()` with tool name `mcp_local-knowledge-base_kb_search_knowledge` → assert `success == true` (from Bug Condition: bridge returns "MCP tool not found")
    - Case C: Pass `collectToolDescriptors()` output to `PhaseToolFilter.hasKbTools()` → assert returns `true` (from Bug Condition: always returns `false`)
  - Test assertions match Expected Behavior: when Local KB enabled, tool list includes 3 KB-compatible descriptors AND `hasKbTools()` returns `true` AND bridge routes to `LocalKBToolExecutor`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct — it proves the bug exists: no KB tools in list, bridge cannot route, hasKbTools returns false)
  - Document counterexamples found to understand root cause
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** — Existing Tool Routing and KB Detection Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **Step 1 — Observe on UNFIXED code**:
    - Observe: `ToolExecutionBridge.execute()` with internal MCP tool (e.g., `mcp_jira-assistant_navigate`) routes to `InternalMcpBridge.callTool()` and returns success
    - Observe: `ToolExecutionBridge.execute()` with external MCP tool (e.g., `mcp_markitdown_convert_to_markdown`) routes to `McpProcessManager.getClient().callTool()` and returns success
    - Observe: `ToolExecutionBridge.execute()` with unknown tool (e.g., `mcp_unknown_some_tool`) returns "MCP tool not found" error
    - Observe: `PhaseToolFilter.hasKbTools()` with external KB tools (names containing `kb_search`, `kb_ingest`, `kb_write`) returns `true`
    - Observe: `PhaseToolFilter.hasKbTools()` with tool list containing NO KB patterns returns `false`
    - Observe: `collectToolDescriptors()` with Local KB disabled returns same list as current (no KB tools)
  - **Step 2 — Write property-based tests capturing observed behavior**:
    - Property 2a: _For all_ tool requests where server name != `local-knowledge-base`, routing behavior is identical (internal → `InternalMcpBridge`, external → `McpProcessManager`, unknown → error)
    - Property 2b: _For all_ tool lists containing external KB tools with `kb_search`/`kb_ingest`/`kb_write` patterns, `hasKbTools()` returns `true` (validates Req 3.2)
    - Property 2c: _For all_ pipeline contexts where Local KB is disabled, `collectToolDescriptors()` excludes Local KB tools and pipeline runs single-phase (validates Req 3.1)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix: Integrate Local KB tools into BRD pipeline

  - [x] 3.1 Create `LocalKBToolDescriptorProvider.kt` helper object
    - Create new file at `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/LocalKBToolDescriptorProvider.kt`
    - Implement `getDescriptors(): List<ToolDescriptor>` returning 3 descriptors with KB-compatible names:
      - `mcp_local-knowledge-base_kb_search_knowledge` (description: semantic search in KB)
      - `mcp_local-knowledge-base_kb_get_ticket_info` (description: lookup ticket analysis)
      - `mcp_local-knowledge-base_kb_search_relationships` (description: search relationships)
    - Implement `mapAliasToOriginal(aliasName: String): String` to strip `kb_` prefix → original tool names for `LocalKBToolExecutor.execute()`
      - `kb_search_knowledge` → `search_knowledge`
      - `kb_get_ticket_info` → `get_ticket_info`
      - `kb_search_relationships` → `search_relationships`
    - File must be ≤ 200 lines, functions ≤ 20 lines
    - _Bug_Condition: isBugCondition(input) where collectToolDescriptors().none { it.name.contains("kb_search") }_
    - _Expected_Behavior: getDescriptors() returns 3 ToolDescriptor with names containing "kb_search" or "kb_" prefix_
    - _Preservation: No existing files modified in this sub-task_
    - _Requirements: 2.1, 2.2_

  - [x] 3.2 Modify `AiBackendPipelineStrategy.kt` — Add Local KB tool collection
    - Add constructor parameters: `settingsRepository: SettingsRepository? = null`, `localKBToolExecutor: LocalKBToolExecutor? = null` (with defaults for backward compatibility)
    - Modify `collectToolDescriptors()`: after collecting internal + external tools, check `ChatLocalKBContext.isEnabled(settingsRepository)` — if enabled and `localKBToolExecutor != null`, append `LocalKBToolDescriptorProvider.getDescriptors()`
    - In `doExecute()`, pass `localKBToolExecutor` to `ToolExecutionBridge` constructor
    - File must remain ≤ 200 lines, functions ≤ 20 lines
    - _Bug_Condition: collectToolDescriptors() returns list without any kb_search/kb_ingest/kb_write tools_
    - _Expected_Behavior: collectToolDescriptors() includes 3 Local KB descriptors when enabled, excludes when disabled_
    - _Preservation: Internal + external tool collection logic unchanged; backward-compatible constructor defaults_
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.4_

  - [x] 3.3 Modify `ToolExecutionBridge.kt` — Add Local KB routing
    - Add constructor parameter: `localKBToolExecutor: LocalKBToolExecutor? = null`
    - In `executeMcpTool()`, after internal tool check and BEFORE external tool check: if `serverName == LocalKBToolExecutor.SERVER_ID` ("local-knowledge-base") and `localKBToolExecutor != null`, route to `executeLocalKBTool(toolName, request.params)`
    - Add private helper `executeLocalKBTool(toolName: String, params: Map<String, String>): ToolCallResponse` — maps alias via `LocalKBToolDescriptorProvider.mapAliasToOriginal()`, calls `localKBToolExecutor.execute()`, returns `ToolCallResponse`
    - File must remain ≤ 200 lines, functions ≤ 20 lines
    - _Bug_Condition: executeMcpTool() returns "MCP tool not found" for Local KB tool calls_
    - _Expected_Behavior: executeMcpTool() routes Local KB tools to LocalKBToolExecutor.execute() and returns result_
    - _Preservation: Internal MCP routing (InternalMcpBridge) and external MCP routing (McpProcessManager) unchanged_
    - _Requirements: 1.3, 2.3_

  - [x] 3.4 Modify `BASubprocessOrchestrator.kt` — Wire DI dependencies
    - Add `localKBToolExecutor: LocalKBToolExecutor? = null` parameter to `createDefaultStrategy()`
    - Pass `settingsRepository` and `localKBToolExecutor` to `AiBackendPipelineStrategy` constructor
    - Update the class-level default strategy creation to pass `settingsRepository` (already available) and `localKBToolExecutor` (new param, nullable)
    - File must remain ≤ 200 lines, functions ≤ 20 lines
    - _Bug_Condition: createDefaultStrategy() creates AiBackendPipelineStrategy without settingsRepository/localKBToolExecutor_
    - _Expected_Behavior: createDefaultStrategy() passes settingsRepository + localKBToolExecutor to strategy_
    - _Preservation: Legacy createMultiTurnStrategy() unchanged; backward-compatible nullable defaults_
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** — Local KB Tools Included When Enabled
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms: `collectToolDescriptors()` includes KB tools, `hasKbTools()` returns `true`, `ToolExecutionBridge` routes Local KB calls successfully
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** — Existing Tool Routing Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix: internal routing, external routing, KB detection, disabled-KB behavior
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run full test suite: `./gradlew :server:jvmTest`
  - Verify exploration test (Property 1) passes — bug is fixed
  - Verify preservation tests (Property 2) pass — no regressions
  - Verify existing tests pass — no breakage
  - Ask the user if questions arise
