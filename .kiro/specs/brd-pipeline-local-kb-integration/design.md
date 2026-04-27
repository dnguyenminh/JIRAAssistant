# BRD Pipeline Local KB Integration — Bugfix Design

## Overview

BRD pipeline multi-phase mode never activates because `PipelineOrchestrator` cannot detect Local Knowledge Base tools. The 4 Local KB tools (`search_knowledge`, `get_ticket_info`, `search_relationships`, `ingest_knowledge` on server `local-knowledge-base`) are only injected into `ChatServiceImpl` — they never reach the BRD pipeline's tool collection.

**Fix approach**: Inject Local KB tool descriptors into `AiBackendPipelineStrategy.collectToolDescriptors()` and add Local KB routing to `ToolExecutionBridge.executeMcpTool()`. Tool descriptor names must use KB-compatible naming (containing `kb_search`, `kb_read`, etc.) so `PhaseToolFilter.hasKbTools()` can detect them without modifying its existing patterns.

**Impact**: Minimal — 4 files modified (`AiBackendPipelineStrategy.kt`, `ToolExecutionBridge.kt`, `BASubprocessOrchestrator.kt`, `BAAgentModule.kt`) plus 1 new helper file (`LocalKBToolDescriptorProvider.kt`). No changes to `PhaseToolFilter`, `PhasePromptBuilder`, `BrdAssembler`, `AgenticLoopRunner`, `ChatServiceImpl`, or `McpAgenticLoop`.

## Glossary

- **Bug_Condition (C)**: Local KB is enabled (`local_kb_tool_enabled` != "false") AND `collectToolDescriptors()` is called — Local KB tools are absent from the returned list
- **Property (P)**: When Local KB is enabled, `collectToolDescriptors()` includes 4 Local KB tool descriptors with KB-compatible names, AND `ToolExecutionBridge` can route calls to `LocalKBToolExecutor`
- **Preservation**: Existing internal MCP routing, external MCP routing, single-phase fallback when KB disabled, external KB tool detection, and Chat's Local KB integration remain unchanged
- **`collectToolDescriptors()`**: Private method in `AiBackendPipelineStrategy` that builds the `List<ToolDescriptor>` passed to `PipelineOrchestrator`
- **`executeMcpTool()`**: Private method in `ToolExecutionBridge` that routes MCP tool calls to internal bridge, external MCP, or (after fix) Local KB executor
- **`hasKbTools()`**: Method in `PhaseToolFilter` that checks if any tool name contains `kb_search`, `kb_ingest`, or `kb_write` — determines multi-phase vs single-phase mode
- **`LocalKBToolExecutor`**: In-process executor for 4 Local KB operations (`search_knowledge`, `get_ticket_info`, `search_relationships`, `ingest_knowledge`), already registered in DI (`ServerModule.kt`)

## Bug Details

### Bug Condition

The bug manifests when Local Knowledge Base is enabled and the BRD pipeline executes. `AiBackendPipelineStrategy.collectToolDescriptors()` only collects tools from `InternalMcpBridge.getAggregatedTools()` (30 internal tools) and `McpProcessManager.getActiveTools()` (external MCP tools). Neither source contains Local KB tools, so `PhaseToolFilter.hasKbTools()` always returns `false`, forcing single-phase mode.

Additionally, even if Local KB tools were somehow in the list, `ToolExecutionBridge.executeMcpTool()` would fail with "MCP tool not found" because it only checks `InternalMcpBridge` and `McpProcessManager` — it has no routing path to `LocalKBToolExecutor`.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type PipelineExecutionContext
  OUTPUT: boolean

  RETURN isLocalKBEnabled(input.settingsRepository)
         AND input.localKBToolExecutor != null
         AND collectToolDescriptors(input.internalMcpBridge, input.mcpProcessManager)
             .none { it.name.contains("kb_search") OR it.name.contains("kb_write") OR it.name.contains("kb_ingest") }
END FUNCTION
```

### Examples

- **Example 1**: Local KB enabled, pipeline runs → `collectToolDescriptors()` returns 57 tools (30 internal + 27 external), none contain `kb_search`/`kb_ingest`/`kb_write` → `hasKbTools()` returns `false` → single-phase mode. **Expected**: 61 tools (57 + 4 Local KB), `hasKbTools()` returns `true` → multi-phase mode.
- **Example 2**: Multi-phase mode hypothetically triggered, AI calls `mcp_local-knowledge-base_search_knowledge` → `ToolExecutionBridge` checks internal tools (no match), checks external tools (no match) → returns "MCP tool not found". **Expected**: Routes to `LocalKBToolExecutor.execute("search_knowledge", args)`.
- **Example 3**: Local KB disabled (`local_kb_tool_enabled` == "false"), pipeline runs → 57 tools, no KB tools → single-phase mode. **Expected**: Same behavior (no change).
- **Edge case**: External KB MCP server (Obsidian) also available with tools like `mcp_knowledge-base_kb_search` → both external KB and Local KB tools present → `hasKbTools()` returns `true` from either source.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Internal MCP tool routing via `InternalMcpBridge.callTool()` continues to work for all 30 internal tools
- External MCP tool routing via `McpProcessManager.getClient().callTool()` continues to work for all external tools
- `PhaseToolFilter.hasKbTools()` still detects external KB tools (patterns `kb_search`, `kb_ingest`, `kb_write` unchanged)
- `ChatServiceImpl` and `McpAgenticLoop` Local KB integration is completely unaffected
- Single-phase fallback when no KB tools are available works identically
- `PhaseToolFilter.filterForPhase1/2/3()` patterns and logic are unchanged
- `AgenticLoopRunner`, `AgenticPromptBuilder`, `PhasePromptBuilder`, `BrdAssembler` are not modified

**Scope:**
All inputs that do NOT involve Local KB tool collection or Local KB tool execution routing should be completely unaffected by this fix. This includes:
- All internal MCP tool calls (navigate, scan, analyze, chat, settings, etc.)
- All external MCP tool calls (markitdown, playwright, jira, drawio, etc.)
- Chat AI sessions using Local KB tools
- Pipeline execution when Local KB is disabled

## Confirmed Root Cause

The root cause analysis was confirmed by exploration tests (Task 1). The root causes are:

1. **Missing Local KB tool collection**: `AiBackendPipelineStrategy.collectToolDescriptors()` (line ~100) only iterates `internalMcpBridge?.getAggregatedTools()` and `mcpProcessManager?.getActiveTools()`. `LocalKBToolExecutor` is a separate in-process component not exposed through either bridge. The strategy class doesn't have access to `LocalKBToolExecutor` or `SettingsRepository` to check if Local KB is enabled.

2. **Missing Local KB routing in ToolExecutionBridge**: `ToolExecutionBridge.executeMcpTool()` (line ~65) parses `mcp_{serverName}_{toolName}`, then checks `internalMcpBridge` tools and `mcpProcessManager` tools. There's no third check for `LocalKBToolExecutor`. The bridge class doesn't have a reference to `LocalKBToolExecutor`.

3. **Tool name incompatibility**: Local KB tools are named `search_knowledge`, `get_ticket_info`, `search_relationships` on server `local-knowledge-base`. When formatted as `mcp_local-knowledge-base_search_knowledge`, none contain `kb_search`, `kb_ingest`, or `kb_write` — so even if added to the list, `hasKbTools()` wouldn't detect them. The fix must use KB-compatible alias names in descriptors (e.g., `kb_search_knowledge` → contains `kb_search`).

4. **DI gap**: `AiBackendPipelineStrategy` is created in `BASubprocessOrchestrator.createDefaultStrategy()` with only `SubprocessProxy`, `CliBackendResolver`, `McpProcessManager`, and `InternalMcpBridge`. Neither `LocalKBToolExecutor` nor `SettingsRepository` is passed through. Additionally, `BAAgentModule.kt` (Koin module) did not pass `localKBToolExecutor` to `BASubprocessOrchestrator` — even after fixing the strategy and bridge code, the executor remained `null` at runtime until the DI wiring was updated.

## Correctness Properties

Property 1: Bug Condition — Local KB Tools Included When Enabled

_For any_ pipeline execution context where Local KB is enabled (`local_kb_tool_enabled` != "false") and `LocalKBToolExecutor` is available, the fixed `collectToolDescriptors()` SHALL include 4 additional tool descriptors whose names contain KB-compatible patterns (at least one containing `kb_search` and one containing `kb_ingest`), causing `PhaseToolFilter.hasKbTools()` to return `true` and triggering multi-phase mode.

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation — Existing Tool Routing Unchanged

_For any_ tool call where the tool name does NOT match Local KB patterns (server name != `local-knowledge-base`), the fixed `ToolExecutionBridge` SHALL produce exactly the same routing behavior as the original — internal tools route to `InternalMcpBridge`, external tools route to `McpProcessManager`, and unknown tools return "MCP tool not found".

**Validates: Requirements 3.3, 3.4**

Property 3: Local KB Tool Routing Correctness

_For any_ tool call where the tool name matches Local KB server pattern (`mcp_local-knowledge-base_*`), the fixed `ToolExecutionBridge` SHALL route the call to `LocalKBToolExecutor.execute()` with the correct tool name and arguments, returning the executor's result.

**Validates: Requirements 2.3**

Property 4: Preservation — KB Detection for External KB Tools

_For any_ tool list containing external KB tools with names matching `kb_search`, `kb_ingest`, or `kb_write` patterns, `PhaseToolFilter.hasKbTools()` SHALL continue to return `true` regardless of whether Local KB tools are also present. The existing detection patterns are NOT modified.

**Validates: Requirements 3.2**

Property 5: Local KB Tools Excluded When Disabled

_For any_ pipeline execution context where Local KB is disabled (`local_kb_tool_enabled` == "false"), the fixed `collectToolDescriptors()` SHALL NOT include any Local KB tool descriptors, and pipeline behavior SHALL be identical to the unfixed code.

**Validates: Requirements 2.4, 3.1**

## Fix Implementation

### Changes Implemented

The fix was implemented as follows (root cause confirmed by exploration tests):

**File 1**: `AiBackendPipelineStrategy.kt` — Add Local KB tool collection

**Changes**:
1. **Add constructor parameters**: Add `settingsRepository: SettingsRepository? = null` and `localKBToolExecutor: LocalKBToolExecutor? = null` to the constructor (with defaults for backward compatibility)
2. **Modify `collectToolDescriptors()`**: Collect only external MCP tools (internal MCP tools like Jira Assistant UI are excluded — they control the app UI, not data sources). When Local KB is enabled, append 4 Local KB tool descriptors with KB-compatible names:
   - `mcp_local-knowledge-base_kb_search_knowledge` (contains `kb_search` → detected by `hasKbTools()`)
   - `mcp_local-knowledge-base_kb_get_ticket_info` (contains `kb_` prefix for phase filtering)
   - `mcp_local-knowledge-base_kb_search_relationships` (contains `kb_search` → detected by `hasKbTools()`)
   - `mcp_local-knowledge-base_kb_ingest_knowledge` (contains `kb_ingest` → enables Phase 1 to write data to KB)
3. **Pass `localKBToolExecutor` to `ToolExecutionBridge`**: In `doExecute()`, pass the executor to the bridge constructor

**File 2**: `ToolExecutionBridge.kt` — Add Local KB routing

**Changes**:
1. **Add constructor parameter**: Add `localKBToolExecutor: LocalKBToolExecutor? = null`
2. **Add Local KB check in `executeMcpTool()`**: After internal tool check and before external tool check, add: if server name matches `LocalKBToolExecutor.SERVER_ID` and executor is available, route to `localKBToolExecutor.execute(toolName, args)`. Map the KB-aliased tool name back to the original name (strip `kb_` prefix).
3. **Add helper method**: `executeLocalKBTool()` — similar pattern to `McpAgenticLoop.executeLocalKBTool()` but adapted for `ToolCallResponse` return type

**File 3** (new): `LocalKBToolDescriptorProvider.kt` — Helper to build Local KB tool descriptors

**Changes**:
1. **Create helper object**: `LocalKBToolDescriptorProvider` with method `getDescriptors(): List<ToolDescriptor>` that returns 4 tool descriptors with KB-compatible names and descriptions
2. **Add name mapping**: `mapAliasToOriginal(aliasName: String): String` to convert KB-aliased names back to original tool names for `LocalKBToolExecutor.execute()`

**File 4**: `BASubprocessOrchestrator.kt` — Pass new dependencies

**Changes**:
1. **Add parameters to `createDefaultStrategy()`**: Add `settingsRepository` and `localKBToolExecutor` parameters
2. **Pass to `AiBackendPipelineStrategy` constructor**: Forward the new dependencies

**File 5**: `BAAgentModule.kt` — Wire `localKBToolExecutor` in Koin DI

**Changes**:
1. **Add `localKBToolExecutor = getOrNull()`** to the `BASubprocessOrchestrator` constructor call in the Koin module. Without this, `localKBToolExecutor` was always `null` at runtime despite the code changes in Files 1-4, because the DI container never injected it. Discovered during manual testing — pipeline still showed `single-phase, tools=57` until this was added.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that call `collectToolDescriptors()` with mock `InternalMcpBridge` and `McpProcessManager`, verify Local KB tools are absent. Write tests that call `ToolExecutionBridge.executeMcpTool()` with a Local KB tool name, verify it returns "MCP tool not found".

**Test Cases**:
1. **Tool Collection Test**: Create `AiBackendPipelineStrategy` with mocked bridges, call `collectToolDescriptors()` via reflection → verify no tool name contains `kb_search` (will confirm bug on unfixed code)
2. **Tool Routing Test**: Create `ToolExecutionBridge` without `LocalKBToolExecutor`, call `execute()` with `mcp_local-knowledge-base_search_knowledge` → verify "MCP tool not found" error (will confirm bug on unfixed code)
3. **KB Detection Test**: Create tool list from `collectToolDescriptors()` output, pass to `PhaseToolFilter.hasKbTools()` → verify returns `false` (will confirm bug on unfixed code)

**Expected Counterexamples**:
- `collectToolDescriptors()` returns list with 0 tools matching KB patterns
- `ToolExecutionBridge` returns error for Local KB tool calls
- Possible causes: missing DI wiring, missing tool collection source, missing routing branch

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := collectToolDescriptors_fixed(input)
  ASSERT result.any { it.name.contains("kb_search") }
  ASSERT PhaseToolFilter.hasKbTools(result) == true

  toolResult := ToolExecutionBridge_fixed.execute(localKBToolRequest)
  ASSERT toolResult.success == true
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT collectToolDescriptors_original(input) == collectToolDescriptors_fixed(input)
  ASSERT ToolExecutionBridge_original.execute(input) == ToolExecutionBridge_fixed.execute(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for internal/external MCP tool routing, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Internal Tool Routing Preservation**: Verify internal MCP tools (navigate, scan, analyze) continue routing to `InternalMcpBridge` after fix
2. **External Tool Routing Preservation**: Verify external MCP tools (markitdown, jira, drawio) continue routing to `McpProcessManager` after fix
3. **KB Detection Preservation**: Verify external KB tools (Obsidian `kb_search`, `kb_ingest`) still trigger multi-phase mode
4. **Disabled KB Preservation**: Verify when Local KB disabled, tool list and pipeline mode are identical to unfixed code

### Unit Tests

- Test `LocalKBToolDescriptorProvider.getDescriptors()` returns 3 descriptors with KB-compatible names
- Test `LocalKBToolDescriptorProvider.mapAliasToOriginal()` correctly maps aliased names to original names
- Test `collectToolDescriptors()` includes Local KB tools when enabled, excludes when disabled
- Test `ToolExecutionBridge` routes Local KB tools to `LocalKBToolExecutor`
- Test `ToolExecutionBridge` returns error when `LocalKBToolExecutor` is null but Local KB tool is called
- Test `PhaseToolFilter.hasKbTools()` returns `true` when Local KB descriptors are in the list

### Property-Based Tests

- Generate random tool lists with/without Local KB descriptors, verify `hasKbTools()` correctness
- Generate random tool requests for internal/external/Local KB tools, verify routing correctness
- Generate random enabled/disabled settings, verify tool collection includes/excludes Local KB tools
- Generate random tool lists, verify existing phase filter behavior is unchanged with Local KB tools added

### Integration Tests

- Test full pipeline execution with mocked `LocalKBToolExecutor` — verify multi-phase mode activates
- Test full pipeline execution with Local KB disabled — verify single-phase fallback
- Test `ToolExecutionBridge` end-to-end with all 3 routing paths (internal, external, Local KB)
