# Bugfix Requirements Document

## Introduction

The `BADocumentAgentIntegrationTest` integration tests use fake test doubles (`FakeSubprocessProxy`, `IntegrationNoOpToolRegistry`) for the tool layer instead of real MCP tool infrastructure. This means the tool registration, discovery, and invocation pipeline is never exercised in integration tests:

- `FakeSubprocessProxy.getAvailableToolDescriptors()` returns a hardcoded list with just 1 tool (`mcp_jira_get_issue`) — not real MCP tools loaded from configuration
- `IntegrationNoOpToolRegistry` always returns `success=false` for all tool invocations — tools never actually execute through `ToolRegistry.invoke()`
- `FakeSubprocessProxy.handleToolCallRequest()` returns a canned response — never routes through real `ToolRegistry`
- There is no MCP configuration JSON file for tests — the test has no way to load real MCP tool definitions

The fix should replace the fake tool layer with real implementations (`ToolRegistryImpl`, `SubprocessProxyImpl`, `AgentMcpManager`) backed by MCP configs in `test.properties` (each MCP server as a `test.mcp.<name>=<json>` property), while keeping the subprocess manager (`FakeSubprocessManager`) fake since we are not running a real AI CLI.

**Scope update**: This fix now includes production code changes to `AgentMcpManager` and a new `AgentMcpManagerHelpers.kt`. The `McpToolWrapper` calls `McpProtocolClient.callTool()` for real MCP communication. `AgentMcpManager.startServer()` starts the MCP server process via `ProcessBuilder` and creates a `McpProtocolClientImpl`. Tools are discovered dynamically via `client.listTools()` — no `toolDescriptions` fallback. If the MCP server binary is not available, no tools are registered and tests that require MCP are skipped via `assumeTrue`.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `BADocumentAgentIntegrationTest` creates a `FakeSubprocessProxy` THEN the system returns a hardcoded list containing only `ToolDescriptor("mcp_jira_get_issue", "Get Jira issue details")` from `getAvailableToolDescriptors()`, regardless of any MCP configuration

1.2 WHEN `BADocumentAgentIntegrationTest` creates an `IntegrationNoOpToolRegistry` THEN the system returns `ToolResult(success = false)` for every `invoke()` call and `emptyList()` for `listTools()`, meaning no tool ever executes

1.3 WHEN `FakeSubprocessProxy.handleToolCallRequest()` is called during a test THEN the system returns a canned `ToolCallResponse` from the lambda without routing through `ToolRegistry.invoke()` → `McpToolWrapper.execute()`

1.4 WHEN the integration test suite runs THEN there is no test MCP config JSON file in test resources, so no real MCP tool definitions can be loaded or validated

### Expected Behavior (Correct)

2.1 WHEN `BADocumentAgentIntegrationTest` sets up the tool layer THEN the system SHALL use a real `ToolRegistryImpl` with MCP tools registered by `AgentMcpManager` from MCP configs defined in `test.properties` as `test.mcp.<serverName>=<AgentMcpConfig JSON>`

2.2 WHEN `BADocumentAgentIntegrationTest` queries available tools via `SubprocessProxyImpl.getAvailableToolDescriptors()` THEN the system SHALL return the actual MCP tool descriptors discovered dynamically via `client.listTools()` from the running MCP server (not a hardcoded list)

2.3 WHEN a tool call request is handled during an integration test THEN the system SHALL route through real `SubprocessProxyImpl.handleToolCallRequest()` → `ToolRegistryImpl.invoke()` → `McpToolWrapper.execute()`, returning a real `ToolResult` with `success = true`

2.4 WHEN the integration test suite runs THEN `test.properties` SHALL contain at least one `test.mcp.<serverName>` entry with a valid `AgentMcpConfig` JSON string defining the MCP server connection parameters

### Unchanged Behavior (Regression Prevention)

3.1 WHEN `BADocumentAgentIntegrationTest` runs the subprocess layer THEN the system SHALL CONTINUE TO use `FakeSubprocessManager` for AI CLI subprocess simulation (we are not running a real AI CLI)

3.2 WHEN `BADocumentAgentIntegrationTest` tests CLI resolution via `CliBackendResolver` THEN the system SHALL CONTINUE TO use `InMemoryProviderConfigRepo` and `FakeSettingsRepo` for provider configuration

3.3 WHEN `BADocumentAgentIntegrationTest` tests the full pipeline THEN the system SHALL CONTINUE TO use `FakeSubprocessManager` with configurable stdout providers (`simpleDocStdoutProvider`, `toolCallStdoutProvider`) for simulating CLI output

3.4 WHEN `BADocumentAgentIntegrationTest` tests progress reporting THEN the system SHALL CONTINUE TO use `IntegrationNoOpReporter` for no-op progress reporting

3.5 WHEN production code is compiled and deployed THEN the changes SHALL be limited to `AgentMcpManager.kt` (real MCP client wiring), new `AgentMcpManagerHelpers.kt` (process starter, tool discovery, name resolver), and logging additions in `BASubprocessOrchestrator.kt` (prompt, tools, document logging) and `ToolCallLoopEngine.kt` (tool call request/response logging) — no changes to business logic, routes, or other modules
