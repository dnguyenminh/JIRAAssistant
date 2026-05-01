# Bugfix Requirements Document

## Introduction

Six out of twenty-one tests in the `server:jvmTest` suite are failing across three BADocumentAgent test classes. The failures stem from three distinct root causes: (1) integration tests that depend on external CLI paths or MCP infrastructure fail with assertion errors instead of being properly skipped via JUnit 5 `assumeTrue`, (2) MCP exploration tests call `buildRealToolLayer()` which depends on `test.properties` MCP configs that may not exist, causing hard failures instead of graceful skips, and (3) the property test's `CountingOrchestrator` extends `BASubprocessOrchestrator` but the test input omits the required `CLI_BACKEND` payload key, causing `BADocumentAgent.execute()` to return `FAILED` instead of the asserted `SUCCESS`. Additionally, the test suite takes over 10 minutes due to long timeouts on tests that attempt to connect to unavailable external services (Ollama, Gemini CLI).

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `BADocumentAgentIntegrationTest` runs `full pipeline produces document via configured CLI` and the system property `test.gemini.cli.path` is not set, env var `TEST_GEMINI_CLI_PATH` is not set, and `test.properties` does not contain `test.gemini.cli.path` THEN the test calls `cliPath("gemini")` which returns `null`, then calls `return skipNoCli("gemini")` which calls `assumeTrue(false, ...)` throwing `TestAbortedException`, but the test still fails with `AssertionFailedError` at line 55 because `buildRealToolLayer()` is called before the skip check and may hang or fail connecting to MCP infrastructure.

1.2 WHEN `BADocumentAgentIntegrationTest` runs `full pipeline produces BRD via Ollama with tool calls` and the system property `test.ollama.cli.path` is not set THEN the test calls `cliPath("ollama")` which returns `null`, then calls `return skipNoCli("ollama")`, but the test still fails with `AssertionFailedError` at line 181 because `buildRealToolLayer()` is called inside the `runBlocking` block after the skip guard and may hang or fail, and the `@Timeout(600)` annotation causes a 10-minute wait before timeout.

1.3 WHEN `BADocumentAgentMcpExplorationTest` runs `builtin KB tools are registered` and `test.properties` does not exist or does not contain `test.mcp.*` entries THEN `buildRealToolLayer()` returns a `RealToolLayer` with no MCP tools registered, and the assertion `kbTools shouldHaveAtLeastSize 3` fails with `AssertionError` instead of the test being skipped.

1.4 WHEN `BADocumentAgentMcpExplorationTest` runs `KB tool invocation returns success` and MCP tools are not available THEN `real.toolRegistry.invoke("mcp_local_knowledge_base_search_knowledge", ...)` returns `ToolResult(success = false)` and the assertion `result.success shouldBe true` fails with `AssertionFailedError` instead of the test being skipped.

1.5 WHEN `BADocumentAgentMcpExplorationTest` runs `KB tool call routing goes through MCP tool wrapper` and MCP tools are not available THEN `real.subprocessProxy.handleToolCallRequest(request)` returns a response with `success = false` or throws, and the assertion `response.success shouldBe true` fails with `AssertionFailedError` instead of the test being skipped.

1.6 WHEN `BADocumentAgentSubprocessOnlyPropertyTest` runs `Property 1 - execute always delegates to subprocess exactly once` THEN `buildInput(ticketId, docType)` creates an `AgentInput` without `BAAgentPayload.CLI_BACKEND` in the payload, causing `BADocumentAgent.onStart()` to set `cliBackend = ""`, and `execute()` returns `AgentOutput(status = FAILED, result = "No AI backend configured...")` instead of delegating to the orchestrator, so the assertion `assertEquals(AgentStatus.SUCCESS, output.status)` fails.

1.7 WHEN the full test suite runs and external services (Ollama, Gemini CLI, MCP servers) are not available THEN tests with `@Timeout(180)` and `@Timeout(600)` annotations wait for their full timeout duration before failing, causing the entire suite to take over 10 minutes.

### Expected Behavior (Correct)

2.1 WHEN `BADocumentAgentIntegrationTest` runs `full pipeline produces document via configured CLI` and MCP infrastructure is not explicitly enabled (`test.mcp.enabled` is not `true`) THEN the test SHALL be skipped via JUnit 5 `assumeTrue` before any attempt to build real tool layers or connect to external services.

2.2 WHEN `BADocumentAgentIntegrationTest` runs `full pipeline produces BRD via Ollama with tool calls` and MCP infrastructure is not explicitly enabled THEN the test SHALL be skipped via JUnit 5 `assumeTrue` before any attempt to build real tool layers or connect to external services.

2.3 WHEN `BADocumentAgentMcpExplorationTest` runs `builtin KB tools are registered` and MCP infrastructure is not explicitly enabled (no `test.mcp.enabled=true` in system properties, env vars, or `test.properties`) THEN the test SHALL be skipped via JUnit 5 `assumeTrue` with a message indicating MCP is not enabled.

2.4 WHEN `BADocumentAgentMcpExplorationTest` runs `KB tool invocation returns success` and MCP infrastructure is not explicitly enabled THEN the test SHALL be skipped via JUnit 5 `assumeTrue` with a message indicating MCP is not enabled.

2.5 WHEN `BADocumentAgentMcpExplorationTest` runs `KB tool call routing goes through MCP tool wrapper` and MCP infrastructure is not explicitly enabled THEN the test SHALL be skipped via JUnit 5 `assumeTrue` with a message indicating MCP is not enabled.

2.6 WHEN `BADocumentAgentSubprocessOnlyPropertyTest` runs `Property 1 - execute always delegates to subprocess exactly once` with any valid ticketId and docType THEN the test input SHALL include a valid `BAAgentPayload.CLI_BACKEND` value (e.g., `"gemini"`) in the payload, the `CountingOrchestrator.executeTask()` SHALL be called exactly once, and the output status SHALL be `AgentStatus.SUCCESS`.

2.7 WHEN the full test suite runs and external services are not available THEN all tests that depend on external services SHALL be skipped within seconds (not waiting for timeout), and the entire suite SHALL complete in under 2 minutes.

### Unchanged Behavior (Regression Prevention)

3.1 WHEN `BADocumentAgentIntegrationTest` runs and CLI paths ARE properly configured via system properties, env vars, or `test.properties` THEN the integration tests SHALL CONTINUE TO execute the full pipeline and assert on results as before.

3.2 WHEN `BADocumentAgentMcpExplorationTest` runs and `test.mcp.enabled=true` is set AND `test.properties` contains valid `test.mcp.*` entries with reachable MCP servers THEN the MCP exploration tests SHALL CONTINUE TO verify tool registration, invocation, and routing as before.

3.3 WHEN `BADocumentAgentSubprocessOnlyPropertyTest` runs THEN the property test SHALL CONTINUE TO verify that `executeTask()` is called exactly once for any valid input combination, using kotest property-based testing with 100 iterations.

3.4 WHEN `BADocumentAgentIntegrationTest` runs tests that do not require external CLI paths (e.g., `fails when no CLI configured in any source`) THEN those tests SHALL CONTINUE TO pass without being skipped.

3.5 WHEN `BADocumentAgentMcpExplorationTest` runs `test MCP config loads from test properties` THEN that test SHALL CONTINUE TO pass when `test.properties` exists with `test.mcp.*` entries, without requiring `test.mcp.enabled=true`.

---

## Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type TestExecution
  OUTPUT: boolean
  
  // Returns true when external infrastructure is not available
  // OR when test input is missing required payload fields
  RETURN (X.testClass = "BADocumentAgentIntegrationTest" 
          AND X.testRequiresMcpInfra AND NOT X.mcpExplicitlyEnabled)
      OR (X.testClass = "BADocumentAgentMcpExplorationTest"
          AND X.testRequiresMcpInfra AND NOT X.mcpExplicitlyEnabled)
      OR (X.testClass = "BADocumentAgentSubprocessOnlyPropertyTest"
          AND X.inputPayload.missingKey("CLI_BACKEND"))
END FUNCTION
```

### Fix Checking

```pascal
// Property: Fix Checking — Tests skip or pass correctly
FOR ALL X WHERE isBugCondition(X) DO
  result ← runTest'(X)
  ASSERT result.status IN {SKIPPED, PASSED}
  ASSERT result.duration < 10_seconds
END FOR
```

### Preservation Checking

```pascal
// Property: Preservation Checking — Configured tests still work
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT runTest(X) = runTest'(X)
END FOR
```
