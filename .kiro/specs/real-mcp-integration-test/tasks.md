# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** — Fake Tool Layer Bypasses Real MCP Pipeline
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the fake tool layer bypasses the real MCP pipeline
  - **Scoped PBT Approach**: Scope the property to the concrete failing cases in the current test setup
  - Create a new test class `BADocumentAgentMcpExplorationTest.kt` in `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/`
  - Test 1 — Tool count: Assert `subprocessProxy.getAvailableToolDescriptors().size >= 6` (Bug Condition: `FakeSubprocessProxy` returns exactly 1 hardcoded tool)
  - Test 2 — Tool invocation: Assert `toolRegistry.invoke("mcp_jira_get_issue", emptyMap()).success == true` (Bug Condition: `IntegrationNoOpToolRegistry` returns `success = false`)
  - Test 3 — Tool routing: Assert `subprocessProxy.handleToolCallRequest(req)` response data contains `"[MCP stub]"` (Bug Condition: `FakeSubprocessProxy` returns canned lambda response)
  - Test 4 — Config loading: Assert `AgentHomeDirectoryLoader(testConfigPath).getMcpConfigs().isNotEmpty()` (Bug Condition: no test MCP config JSON file exists)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: All 4 tests FAIL (this is correct — it proves the bug exists)
  - Document counterexamples found: `getAvailableToolDescriptors()` returns 1 tool instead of 6+, `invoke()` returns `success = false`, no config file exists
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** — Non-Tool Test Infrastructure Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Create preservation tests in `BADocumentAgentPreservationTest.kt` in `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/`
  - Observe on UNFIXED code: `FakeSubprocessManager` with `simpleDocStdoutProvider()` produces `Flow<String>` containing `"---END---"` and `"# BRD Document"`
  - Observe on UNFIXED code: `InMemoryProviderConfigRepo.addProvider()` + `findByType()` returns the added provider
  - Observe on UNFIXED code: `FakeSettingsRepo.put("key", "value")` + `get("key")` returns `"value"`
  - Observe on UNFIXED code: `IntegrationNoOpReporter.reportPhase()` completes without error (returns `Unit`)
  - Observe on UNFIXED code: `CliBackendResolver` with `InMemoryProviderConfigRepo` resolves CLI path correctly
  - Write property-based tests asserting these observed behaviors are preserved across the input domain
  - Verify tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix for fake tool layer bypassing real MCP pipeline

  - [x] 3.1 Create test MCP config JSON file
    - Create directory `server/src/jvmTest/resources/test-mcp-config/.agent/mcp/`
    - Create `jira-mcp.json` with `AgentMcpConfig` format: `serverName = "jira"`, `command = "echo"`, `args = ["stub"]`, `toolDescriptions` mapping 6 tools: `get_issue`, `search_issues`, `get_project`, `add_comment`, `create_issue`, `get_transitions`
    - Verify JSON is valid and loadable by `AgentHomeDirectoryLoader`
    - _Bug_Condition: isBugCondition(testSetup) where testSetup.mcpConfigFile NOT EXISTS_
    - _Expected_Behavior: Config file exists and is loadable, returns non-empty list of AgentMcpConfig_
    - _Preservation: No production code changes — test resources only_
    - _Requirements: 1.4, 2.4_

  - [x] 3.2 Add `buildRealToolLayer()` helper and `RealToolLayer` data class
    - Add to `BADocumentAgentIntegrationTestDoubles.kt`
    - Create `RealToolLayer` data class holding `toolRegistry: ToolRegistryImpl` and `subprocessProxy: SubprocessProxyImpl`
    - Create `suspend fun buildRealToolLayer(): RealToolLayer` that: creates `ToolRegistryImpl`, loads `AgentHomeDirectoryLoader` from test resources path, creates `AgentMcpManager(homeDirectory, toolRegistry)`, calls `mcpManager.initialize()`, creates `SubprocessProxyImpl(toolRegistry, parallelToolExecutor = null, agentType = "ba-agent")`, returns `RealToolLayer`
    - _Bug_Condition: isBugCondition(testSetup) where testSetup.subprocessProxy IS FakeSubprocessProxy AND testSetup.toolRegistry IS IntegrationNoOpToolRegistry_
    - _Expected_Behavior: buildRealToolLayer() returns real ToolRegistryImpl with MCP tools registered and real SubprocessProxyImpl_
    - _Preservation: FakeSubprocessProxy, IntegrationNoOpToolRegistry, and all other test doubles remain unchanged_
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.3 Wire real tool layer into integration tests
    - Update `buildOrchestrator()` in `BADocumentAgentIntegrationTest.kt` to accept `SubprocessProxy` (interface type) instead of `FakeSubprocessProxy`
    - Update `buildAgent()` to accept `ToolRegistry` (interface type) instead of always creating `IntegrationNoOpToolRegistry()`
    - Update test 1 (`full pipeline produces document via configured CLI`) to use `buildRealToolLayer()`
    - Update test 4 (`pipeline handles tool calls with configured CLI`) to use `buildRealToolLayer()`
    - Keep tests 2, 3, 5, 6 unchanged — they test CLI resolution and config update, not the tool pipeline
    - _Bug_Condition: isBugCondition(testSetup) where testSetup.subprocessProxy IS FakeSubprocessProxy AND testSetup.toolRegistry IS IntegrationNoOpToolRegistry_
    - _Expected_Behavior: Tests 1 and 4 use real SubprocessProxyImpl + ToolRegistryImpl + AgentMcpManager pipeline_
    - _Preservation: Tests 2, 3, 5, 6 continue using FakeSubprocessProxy, FakeSettingsRepo, InMemoryProviderConfigRepo_
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** — MCP Tools Registered and Invocable
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied:
      - `getAvailableToolDescriptors().size >= 6` ✓
      - `invoke("mcp_jira_get_issue", emptyMap()).success == true` ✓
      - `handleToolCallRequest(req)` response data contains `"[MCP stub]"` ✓
      - `AgentHomeDirectoryLoader(testConfigPath).getMcpConfigs().isNotEmpty()` ✓
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** — Non-Tool Test Infrastructure Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run full compile: `./gradlew :server:compileKotlinJvm`
  - Run all BA agent integration tests: `./gradlew :server:jvmTest --tests "com.assistant.server.agent.ba.*"`
  - Verify exploration tests (task 1) PASS
  - Verify preservation tests (task 2) PASS
  - Verify all 6 original `BADocumentAgentIntegrationTest` tests PASS
  - Ensure zero production code changes — only test code and test resources modified
  - Ask the user if questions arise
