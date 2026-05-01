# BA Agent Test Fixes — Bugfix Design

## Overview

Six BA agent tests fail when external infrastructure (Gemini CLI, Ollama, MCP servers) is unavailable. The root causes are: (1) integration tests call `buildRealToolLayer()` inside `runBlocking` before MCP availability is checked, causing hangs or hard failures instead of graceful skips; (2) MCP exploration tests have no skip guards at all; (3) the property test omits `BAAgentPayload.CLI_BACKEND` from the input payload, causing `BADocumentAgent.execute()` to return `FAILED`. The fix adds `assumeMcpAvailable()` guards before `buildRealToolLayer()` calls, adds `CLI_BACKEND` to the property test payload, and reduces excessive timeouts.

## Glossary

- **Bug_Condition (C)**: Tests that depend on external infrastructure (CLI paths, MCP servers) but lack proper skip guards, OR tests with incomplete input payloads
- **Property (P)**: Tests skip instantly via JUnit 5 `assumeTrue` when infrastructure is unavailable, or pass with correct payloads when infrastructure is available
- **Preservation**: Tests that already work correctly (e.g., `fails when no CLI configured`, `test MCP config loads from test properties`) must remain unchanged
- **`buildRealToolLayer()`**: Suspend function in `BADocumentAgentIntegrationTestDoubles.kt` that reads `test.properties`, parses MCP configs, initializes `AgentMcpManager`, and returns a `RealToolLayer` — can hang if MCP servers are unreachable
- **`skipNoCli(backend)`**: Helper in `BADocumentAgentTestConfig.kt` that calls `assumeTrue(false, ...)` to skip tests when CLI path is not configured
- **`isMcpEnabled()`**: Helper in `BADocumentAgentTestConfig.kt` that checks for explicit opt-in via `test.mcp.enabled=true` in system properties, env vars, or `test.properties`. Returns `false` by default — MCP tests only run when explicitly enabled.
- **`assumeMcpAvailable()`**: Helper that calls `assumeTrue(isMcpEnabled(), ...)` to skip tests when MCP is not explicitly enabled

## Bug Details

### Bug Condition

The bug manifests in three scenarios: (1) integration tests that call `buildRealToolLayer()` inside `runBlocking` even though the CLI skip guard passed — `buildRealToolLayer()` connects to MCP infrastructure which may hang; (2) MCP exploration tests that call `buildRealToolLayer()` with no skip guard at all; (3) property test that builds `AgentInput` without `CLI_BACKEND` in the payload map.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type TestExecution
  OUTPUT: boolean

  RETURN (input.testClass = "BADocumentAgentIntegrationTest"
          AND input.testMethod IN ["full pipeline produces document via configured CLI",
                                    "full pipeline produces BRD via Ollama with tool calls",
                                    "pipeline handles tool calls with configured CLI"]
          AND input.callsBuildRealToolLayer = true
          AND NOT input.mcpExplicitlyEnabled)
      OR (input.testClass = "BADocumentAgentMcpExplorationTest"
          AND input.testMethod IN ["builtin KB tools are registered",
                                    "KB tool invocation returns success",
                                    "KB tool call routing goes through MCP tool wrapper",
                                    "Jira MCP tools registered when server available"]
          AND NOT input.mcpExplicitlyEnabled)
      OR (input.testClass = "BADocumentAgentSubprocessOnlyPropertyTest"
          AND "CLI_BACKEND" NOT IN input.payloadKeys)
END FUNCTION
```

### Examples

- **Integration test hangs**: `full pipeline produces document via configured CLI` — CLI skip guard passes (cliPath returns non-null), but `buildRealToolLayer()` inside `runBlocking` hangs connecting to MCP servers → test waits 180s before timeout
- **Integration test excessive wait**: `full pipeline produces BRD via Ollama with tool calls` — Ollama not configured, skip guard fires, but `@Timeout(600)` means 10-minute wait if `buildRealToolLayer()` is reached before skip
- **MCP test hard failure**: `builtin KB tools are registered` — no `test.mcp.*` entries in `test.properties`, `buildRealToolLayer()` returns empty tool list, `kbTools shouldHaveAtLeastSize 3` throws `AssertionError`
- **Property test wrong status**: `Property 1 - execute always delegates to subprocess exactly once` — payload missing `CLI_BACKEND`, `BADocumentAgent.onStart()` sets `cliBackend = ""`, `execute()` returns `FAILED` instead of delegating to `CountingOrchestrator`

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Tests that do NOT call `buildRealToolLayer()` (e.g., `fails when no CLI configured in any source`, `ProviderConfig takes priority over SettingsRepository`, `falls back to SettingsRepository when no ProviderConfig`, `picks up updated config on each resolution`) must continue to pass as before
- Tests that already have correct `assumeTrue` guards (e.g., `test MCP config loads from test properties`) must continue to pass without requiring `test.mcp.enabled`
- When external infrastructure IS available AND `test.mcp.enabled=true` is set, all integration and MCP tests must continue to execute fully and assert on results
- The property test must continue to verify `executeTask()` is called exactly once with 100 iterations

**Scope:**
All inputs that do NOT involve missing MCP infrastructure or missing `CLI_BACKEND` payload should be completely unaffected by this fix. This includes:
- Tests that use `FakeSubprocessManager` / `FakeSubprocessProxy` (no real MCP)
- Tests that only exercise `CliBackendResolver` logic
- Tests that already have correct skip guards

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Missing MCP availability guard in integration tests**: `full pipeline produces document via configured CLI` (line 55) and `full pipeline produces BRD via Ollama with tool calls` (line 181) both call `buildRealToolLayer()` inside `runBlocking`. The CLI skip guard (`cliPath("gemini") ?: return skipNoCli("gemini")`) only checks CLI path availability, not MCP infrastructure. When CLI path is configured but MCP servers are unreachable, `buildRealToolLayer()` → `AgentMcpManager.initialize()` hangs.

2. **No skip guard in MCP exploration tests**: `builtin KB tools are registered`, `KB tool invocation returns success`, and `KB tool call routing goes through MCP tool wrapper` all call `buildRealToolLayer()` directly inside `runBlocking` with zero precondition checks. When `test.properties` lacks `test.mcp.*` entries, the tool layer returns empty results and assertions fail.

3. **Missing `CLI_BACKEND` in property test payload**: `buildInput(ticketId, docType)` creates `AgentInput` with only `TICKET_ID` and `DOC_TYPE` in the payload map. `BADocumentAgent.onStart()` reads `CLI_BACKEND` from payload, gets `null`, sets `cliBackend = ""`. Then `execute()` checks `cliBackend.isBlank()` and returns `FAILED` immediately without calling `CountingOrchestrator.executeTask()`.

4. **Excessive timeout on Ollama test**: `@Timeout(600)` on the Ollama test means a 10-minute wait even when the test should skip instantly. Should be reduced to `@Timeout(30)` since the skip guard fires before any real work.

## Correctness Properties

Property 1: Bug Condition — Tests skip instantly when infrastructure unavailable

_For any_ test execution where external infrastructure is not available (no MCP configs in `test.properties`, no CLI path configured) AND the test depends on that infrastructure, the fixed test SHALL be skipped via `assumeTrue` within seconds (not waiting for timeout), with a descriptive skip message.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.7**

Property 2: Bug Condition — Property test delegates to orchestrator

_For any_ valid `(ticketId, docType)` pair generated by kotest property-based testing, the fixed `buildInput()` SHALL include `BAAgentPayload.CLI_BACKEND` in the payload, causing `BADocumentAgent.execute()` to delegate to `CountingOrchestrator.executeTask()` exactly once and return `AgentStatus.SUCCESS`.

**Validates: Requirements 2.6**

Property 3: Preservation — Configured tests still execute fully

_For any_ test execution where external infrastructure IS available (MCP configs present, CLI paths configured), the fixed tests SHALL produce the same results as the original tests, executing the full pipeline and asserting on results.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentTestConfig.kt`

**Function**: Add `isMcpEnabled()` and `assumeMcpAvailable()`

**Specific Changes**:
1. **Add `isMcpEnabled()` function**: Checks system property `test.mcp.enabled`, env var `TEST_MCP_ENABLED`, or `test.properties` entry `test.mcp.enabled`. Returns `true` only when explicitly set to `true`. This is an opt-in flag — having `test.mcp.*` config entries alone is not sufficient, because MCP servers may be configured but unreachable.
2. **Add `assumeMcpAvailable()` function**: Calls `assumeTrue(isMcpEnabled(), "Skipped: set test.mcp.enabled=true to run MCP tests")`. This provides a reusable guard for all tests that depend on MCP infrastructure.

---

**File**: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentIntegrationTest.kt`

**Function**: `full pipeline produces document via configured CLI`

**Specific Changes**:
1. **Add MCP guard before `runBlocking`**: Insert `assumeMcpAvailable()` after the CLI path check but before `runBlocking`. This prevents `buildRealToolLayer()` from being called when MCP infrastructure is unavailable.

**Function**: `full pipeline produces BRD via Ollama with tool calls`

**Specific Changes**:
1. **Add MCP guard before `runBlocking`**: Insert `assumeMcpAvailable()` after the Ollama CLI path check but before `runBlocking`.
2. **Reduce timeout**: Change `@Timeout(600)` to `@Timeout(30)` since the test should skip instantly when Ollama is not configured.

**Function**: `pipeline handles tool calls with configured CLI`

**Specific Changes**:
1. **Add MCP guard before `runBlocking`**: Insert `assumeMcpAvailable()` after the CLI path check but before `runBlocking`, since this test also calls `buildRealToolLayer()`.

---

**File**: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentMcpExplorationTest.kt`

**Functions**: `builtin KB tools are registered`, `KB tool invocation returns success`, `KB tool call routing goes through MCP tool wrapper`, `Jira MCP tools registered when server available`

**Specific Changes**:
1. **Add `assumeMcpAvailable()` guard**: Insert `assumeMcpAvailable()` as the first line inside each test method, BEFORE the `runBlocking` block. This skips the test immediately when MCP is not explicitly enabled. For `Jira MCP tools registered when server available`, this guard replaces the previous behavior where the test would call `buildRealToolLayer()` first and only skip later via an inner `assumeTrue` on the Jira tool list — the new guard prevents the `buildRealToolLayer()` hang entirely.

---

**File**: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentSubprocessOnlyPropertyTest.kt`

**Function**: `buildInput(ticketId, docType)`

**Specific Changes**:
1. **Add `CLI_BACKEND` to payload**: Add `BAAgentPayload.CLI_BACKEND to "gemini"` to the payload map in `buildInput()`. This ensures `BADocumentAgent.onStart()` sets `cliBackend = "gemini"` and `execute()` delegates to the orchestrator.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, confirm the bugs exist on unfixed code by running the failing tests, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis.

**Test Plan**: Run the 6 failing tests on unfixed code and observe the failure modes.

**Test Cases**:
1. **Integration test hang**: Run `full pipeline produces document via configured CLI` without MCP configs — observe timeout or assertion failure (will fail on unfixed code)
2. **Ollama test excessive wait**: Run `full pipeline produces BRD via Ollama with tool calls` without Ollama — observe 600s timeout (will fail on unfixed code)
3. **MCP exploration no guard**: Run `builtin KB tools are registered` without MCP configs — observe `AssertionError` on `shouldHaveAtLeastSize 3` (will fail on unfixed code)
4. **MCP tool invocation**: Run `KB tool invocation returns success` without MCP — observe `success shouldBe true` failure (will fail on unfixed code)
5. **MCP routing**: Run `KB tool call routing goes through MCP tool wrapper` without MCP — observe assertion failure (will fail on unfixed code)
6. **Property test missing payload**: Run `Property 1 - execute always delegates to subprocess exactly once` — observe `assertEquals(AgentStatus.SUCCESS, output.status)` failure (will fail on unfixed code)

**Expected Counterexamples**:
- Integration tests: timeout or `AssertionFailedError` due to `buildRealToolLayer()` hanging
- MCP tests: `AssertionError` due to empty tool lists
- Property test: `AssertionFailedError` — expected `SUCCESS` but got `FAILED`

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed tests skip or pass correctly.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := runTest'(input)
  ASSERT result.status IN {SKIPPED, PASSED}
  ASSERT result.duration < 10_seconds
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed tests produce the same result as the original tests.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT runTest(input) = runTest'(input)
END FOR
```

**Testing Approach**: Property-based testing is used for the property test (Property 2) — kotest generates 100 random `(ticketId, docType)` pairs and verifies `executeTask()` is called exactly once with `SUCCESS` status.

**Test Plan**: Run the full `ba-agent-integration` tagged test suite after applying fixes.

**Test Cases**:
1. **Skip guard works**: Run integration tests without MCP configs — verify tests are SKIPPED (not FAILED)
2. **MCP guard works**: Run MCP exploration tests without MCP configs — verify tests are SKIPPED
3. **Property test passes**: Run property test — verify 100 iterations all pass with `SUCCESS`
4. **Non-affected tests unchanged**: Run `fails when no CLI configured in any source` — verify still PASSES
5. **Already-guarded tests unchanged**: Run `test MCP config loads from test properties` — verify still SKIPPED gracefully

### Unit Tests

- Verify `isMcpEnabled()` returns `false` when `test.mcp.enabled` is not set in any source
- Verify `isMcpEnabled()` returns `true` when `test.mcp.enabled=true` is set in system properties, env vars, or `test.properties`
- Verify `assumeMcpAvailable()` throws `TestAbortedException` when MCP is not explicitly enabled
- Verify `buildInput()` includes `CLI_BACKEND` in payload

### Property-Based Tests

- Generate random `(ticketId, docType)` pairs and verify `CountingOrchestrator.executeTask()` is called exactly once (existing test, now fixed)
- Verify `AgentStatus.SUCCESS` for all generated inputs (existing test, now fixed)

### Integration Tests

- Run full `ba-agent-integration` suite and verify no test takes longer than 30 seconds when infrastructure is unavailable
- Verify total suite duration under 2 minutes without external services
- Verify tests execute fully when infrastructure IS available
