# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Internal Tool Calls Fail via Agentic Loop
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate internal tool calls return error instead of actual results
  - **Scoped PBT Approach**: Generate random `McpToolCallRequest` with `serverId: "jira-assistant-ui"` and random toolName from internal tools list
  - Create test file: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopInternalToolBugTest.kt`
  - Use Kotest property-based testing (`checkAll`, `Arb`) consistent with existing `McpAgenticLoopPropertyTest.kt`
  - Mock `McpProcessManager` to return `null` for `getClient("jira-assistant-ui")` (simulates real behavior)
  - Mock `InternalMcpBridge` to return successful `McpToolCallResponse` when `callTool()` is called
  - Mock `callAI` to return AI response containing `mcpToolCall` JSON with `serverId: "jira-assistant-ui"`
  - Property: For all internal tool calls (`serverId = "jira-assistant-ui"`), the agentic loop result SHALL NOT contain `"Error: server 'jira-assistant-ui' not running"`
  - Property: For all internal tool calls, the result SHALL contain the actual tool response from `InternalMcpBridge.callTool()`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS — confirms bug exists (internal tools route via `McpProcessManager` instead of `InternalMcpBridge`)
  - Document counterexamples: all internal tool calls return `"Error: server 'jira-assistant-ui' not running"`
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Internal Tool Call Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Create test file: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopPreservationTest.kt`
  - Use Kotest property-based testing consistent with project conventions
  - **Observe on UNFIXED code first, then write properties:**
  - Observe: External MCP tool call (`serverId: "jira-mcp"`) routes via `McpProcessManager.getClient()` → returns tool result
  - Observe: Local KB tool call (`serverId: "local-kb"`) routes via `LocalKBToolExecutor` → returns KB result
  - Observe: AI response without tool call → returns text response directly without tool execution
  - Observe: Per-user disabled tool (`isToolDisabledByUser` returns true) → returns "Tool disabled" message
  - **Property-based tests:**
  - Property: For all `serverId ∉ {"jira-assistant-ui"}` (generated from `Arb.string`), external tool calls route via `McpProcessManager.getClient(serverId)` and return the client's response
  - Property: For all Local KB tool calls (`serverId: "local-kb"`), calls route via `LocalKBToolExecutor.execute()` and return executor's response
  - Property: For all AI responses not containing `mcpToolCall` JSON, the agentic loop returns the AI text response directly
  - Verify all preservation tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS — confirms baseline behavior to preserve
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix internal tool routing in McpAgenticLoop

  - [x] 3.1 Add `InternalMcpBridge?` parameter to `McpAgenticLoop.execute()` overloads
    - Add `internalMcpBridge: InternalMcpBridge? = null` parameter to backward-compatible overload (overload 1) — forward to overload 2
    - Add `internalMcpBridge: InternalMcpBridge? = null` parameter to full overload (overload 2) — pass to `executeToolWithLocalRouting()`
    - Default `null` preserves backward compatibility for all existing callers
    - _Bug_Condition: isBugCondition(input) where input.serverId = "jira-assistant-ui" AND input.callSource = "agentic-loop"_
    - _Expected_Behavior: Internal tool calls route via InternalMcpBridge.callTool() from design_
    - _Preservation: Existing callers not passing bridge continue to work (default null)_
    - _Requirements: 1.2, 2.2_

  - [x] 3.2 Add `InternalMcpBridge?` parameter to `executeToolWithLocalRouting()` and fix routing logic
    - Add `bridge: InternalMcpBridge? = null` parameter to `executeToolWithLocalRouting()`
    - Fix routing: when `req.serverId == InternalMcpBridge.INTERNAL_SERVER_ID` AND `bridge != null` → route via `bridge.callTool(req.toolName, req.arguments, userId ?: "system", "READER")`
    - When `bridge == null` → return error message `"Error: no internal MCP bridge available"` (graceful degradation)
    - Extract `executeInternalTool()` private method (≤20 lines per Kotlin code standards) for bridge call + response conversion + error handling
    - Convert `McpToolCallResponse` → String: join `content.mapNotNull { it.text }` with newline, handle `isError` flag
    - Wrap in try-catch for graceful degradation matching external tool error handling pattern
    - _Bug_Condition: isBugCondition(input) where input.serverId = "jira-assistant-ui"_
    - _Expected_Behavior: executeToolWithLocalRouting routes internal calls via bridge.callTool() from design_
    - _Preservation: External tools still route via McpProcessManager.getClient(), Local KB via LocalKBToolExecutor_
    - _Requirements: 1.1, 2.1, 2.3, 2.4, 3.2, 3.3_

  - [x] 3.3 Update `ChatServiceImpl.processChat()` to pass `internalMcpBridge`
    - Add `internalMcpBridge = internalMcpBridge` to `McpAgenticLoop.execute()` call in `processChat()`
    - `ChatServiceImpl` already has `internalMcpBridge: InternalMcpBridge?` property — just wire it through
    - _Bug_Condition: ChatServiceImpl.processChat() does not pass bridge to McpAgenticLoop_
    - _Expected_Behavior: ChatServiceImpl passes internalMcpBridge to McpAgenticLoop.execute() from design_
    - _Preservation: All other processChat() behavior unchanged_
    - _Requirements: 1.2, 2.2_

  - [x] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Internal Tool Calls Execute Successfully via Agentic Loop
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior (internal tools route via bridge)
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed — internal tools now route via `InternalMcpBridge.callTool()`)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Internal Tool Call Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions — external tools, Local KB, text responses all unchanged)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint - Ensure all tests pass
  - Run full test suite: `./gradlew :server:jvmTest --tests "com.assistant.server.chat.McpAgenticLoop*"`
  - Verify bug condition exploration test passes (Property 1)
  - Verify preservation property tests pass (Property 2)
  - Verify existing `McpAgenticLoopTest`, `McpAgenticLoopLocalKBTest`, `McpAgenticLoopEmptyReplyTest`, `McpAgenticLoopPropertyTest` still pass
  - Ensure all tests pass, ask the user if questions arise.
