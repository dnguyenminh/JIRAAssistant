# BA Agent MCP Tools Fix — Bugfix Design

## Overview

The `ba-document` agent is never registered in the `AgentRegistry` at application startup because the Koin `single` block in `BAAgentModule.kt` is lazy-initialized, returns `Unit`, and no component ever requests that bean. This causes `AgentNotFoundException` when `AgentJobExecutorBridge.generate()` tries to resolve the agent, silently falling back to the curation pipeline which lacks MCP tool support (e.g., markitdown for attachment parsing). The fix adds `createdAtStart = true` to the registration `single` block so Koin eagerly executes it during module loading.

## Glossary

- **Bug_Condition (C)**: The Koin `single` block that registers `BADocumentAgent` in `AgentRegistry` is lazy-initialized and never requested, so the registration code never executes
- **Property (P)**: After startup, `AgentRegistry.listAgentTypes()` contains `"ba-document"` and `AgentJobExecutorBridge.generate()` resolves the agent without throwing `AgentNotFoundException`
- **Preservation**: All other Koin beans (MasterPromptBuilder, AgentJobExecutorBridge, CollectionStrategy factory, curation components), the curation fallback path when agent execution fails, and the legacy pipeline when agent pipeline is disabled — all must remain unchanged
- **BAAgentModule**: The Koin module defined in `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` that declares DI bindings for the BA agent subsystem
- **AgentRegistry**: Singleton registry (`AgentRegistryImpl`) mapping agent type strings to factory functions, declared in `agentModule`
- **AgentJobExecutorBridge**: Bridge class that resolves the BA agent from the registry and executes the agent pipeline, called by `JobExecutor`
- **createdAtStart**: Koin `single` parameter that forces eager initialization when the Koin application starts, rather than waiting for first `get()` call

## Bug Details

### Bug Condition

The bug manifests when the application starts and the Koin `single` block in `BAAgentModule.kt` that registers the `BADocumentAgent` factory in `AgentRegistry` is never executed. The block is lazy (Koin default), returns `Unit` (no explicit return value), and no other component depends on or requests this `Unit` bean. As a result, `AgentRegistry` remains empty.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type KoinModuleInitialization
  OUTPUT: boolean
  
  LET registrationBlock = input.baAgentModule.singleBlocks
      .find(block => block.registersAgentType("ba-document"))
  
  RETURN registrationBlock.isLazy = true
     AND registrationBlock.returnType = Unit
     AND registrationBlock.requestedByOtherBeans = false
     AND AgentRegistry.listAgentTypes().contains("ba-document") = false
END FUNCTION
```

### Examples

- **Startup → Empty Registry**: Application starts, `baAgentModule` is included in `serverModule`, but the registration `single` block is never invoked. `AgentRegistry.listAgentTypes()` returns `[]`. Expected: `["ba-document"]`
- **BRD Generation → AgentNotFoundException**: User triggers BRD generation with agent pipeline enabled. `AgentJobExecutorBridge.generate()` calls `agentRegistry.getAgent("ba-document", config)` which throws `AgentNotFoundException("ba-document", [])`. Expected: agent resolves successfully
- **Silent Fallback → Degraded Documents**: `JobExecutor.tryAgentPipeline()` catches the exception, logs a warning, and falls back to curation pipeline. Documents are generated without MCP tools, producing "Insufficient data" warnings. Expected: agent pipeline generates documents with MCP tool support
- **Edge Case — Agent Pipeline Disabled**: When `AGENT_PIPELINE_ENABLED = "false"`, `JobExecutor` skips agent pipeline entirely. This path is unaffected by the bug and should remain unchanged after the fix

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- All other `single` and `factory` beans in `BAAgentModule` (TemporalClassifier, CommentSummarizer, AttachmentCurator, BudgetEnforcer, McpToolRegistrar, MasterPromptBuilder, AgentJobExecutorBridge, CollectionStrategy factory) must continue to resolve with the same behavior
- When agent pipeline is disabled (`AGENT_PIPELINE_ENABLED = "false"`), `JobExecutor` must continue to skip agent resolution and use curation/legacy pipeline
- When agent pipeline is enabled but agent execution fails (runtime error, not registration error), `JobExecutor` must continue to fall back to curation pipeline
- `AgentRegistryImpl.register()` must continue to log "Registered agent type: ba-document" and store the factory correctly
- The `agentModule` singleton `AgentRegistry` bean must remain unchanged

**Scope:**
All inputs that do NOT involve the BA agent registration `single` block's initialization timing should be completely unaffected by this fix. This includes:
- Curation pipeline behavior (temporal classification, comment summarization, budget enforcement)
- Legacy prompt pipeline behavior
- Other Koin module bean resolution
- Agent execution logic (once resolved from registry)

## Hypothesized Root Cause

Based on the code analysis, there are **two** root causes:

### Root Cause 1: Lazy Initialization (Fixed by this spec)

1. **Lazy Initialization (Primary Cause)**: Koin `single` blocks are lazy by default — they only execute when another component calls `get()` for that bean type. The registration block in `BAAgentModule.kt` (lines 51-62) returns `Unit` implicitly (the last expression `registry.register(...)` returns `Unit`). No other bean depends on this `Unit` type, so Koin never invokes the block.

2. **No Explicit Return Type**: The `single` block has no type parameter (`single { ... }` instead of `single<SomeType> { ... }`). Koin infers the type as `Unit`. Even if another component tried to `get<Unit>()`, it would be unusual and fragile.

3. **Side-Effect-Only Block**: The block's purpose is purely a side effect (registering a factory in the registry). Koin's lazy model assumes beans produce values that are consumed — side-effect-only blocks need `createdAtStart = true` to ensure execution.

4. **No Startup Verification**: There is no startup health check that calls `agentRegistry.listAgentTypes()` to verify registration, so the empty registry goes undetected until a document generation job runs.

### Root Cause 2: Missing OrchestratorBackend Interface Binding (Fixed post-spec)

Even after fixing Root Cause 1, the agent factory inside the registration block calls `orchestratorBackend = get()` which resolves `OrchestratorBackend` interface from Koin. However, `AgentModule.kt` registered `CustomKotlinOrchestrator` as a **concrete factory** (`factory { CustomKotlinOrchestrator(get(), get()) }`) without binding it to the `OrchestratorBackend` interface. This caused a second Koin resolution error:

```
No definition found for type 'com.assistant.agent.orchestrator.OrchestratorBackend'
```

**Fix applied in `AgentModule.kt`**: Changed `factory { CustomKotlinOrchestrator(get(), get()) }` to `factory<OrchestratorBackend> { CustomKotlinOrchestrator(get(), get()) }`, so Koin resolves the interface correctly when `BADocumentAgent` is instantiated.

### Root Cause 3: BA Agent Tools Not Wired into ToolRegistry (Fixed post-spec)

> **SUPERSEDED by [native-tool-removal spec](../native-tool-removal/requirements.md).** All 6 native BA tools and the `wireNativeBATools()` function have been deleted. The system now relies entirely on MCP tools from the Integrations page. The root cause and fix described below are historical context only.

Even after fixing Root Causes 1 and 2, the agent factory passed an empty `ToolRegistry` to `BADocumentAgent`. The `ToolRegistryImpl` is a Koin `factory` (new empty instance per `get()` call), but no code registered the actual `AgentTool` instances (`FetchJiraDetailsTool`, `GetLinkedIssuesTool`, `FetchCommentsTool`, `LookupKBRecordTool`, `SearchKBTool`, `ProcessAttachmentTool`) into it. The `BAAgentConfig.tools { register("fetchJiraDetails") }` only records tool **names** as metadata — it does not instantiate or register tool objects.

This caused `CollectPhase` to fail with: `Tool 'fetchJiraDetails' is not registered`, resulting in empty memory (`avg=0.00`) and a near-empty 368-char prompt.

**Fix applied in `BAAgentModule.kt`**: Added `wireBATools()` helper that instantiates all 6 BA tools with their Koin dependencies (`JiraClient`, `KBRepository`, `VectorStore`) and calls `toolRegistry.registerAll()` before passing the registry to `BADocumentAgent`. *(Note: `wireBATools()` was later renamed to `wireNativeBATools()` and subsequently deleted per the native-tool-removal spec.)*

### Root Cause 4: CollectPhase No Fallback When fetchJiraDetails Fails (Fixed post-spec)

> **SUPERSEDED by [legacy-pipeline-removal spec](../legacy-pipeline-removal/requirements.md) and [native-tool-removal spec](../native-tool-removal/requirements.md).** `CollectPhase`, `ExpandPhase`, and all native BA tools have been deleted. The BA agent now uses subprocess orchestration with MCP tools. The root cause and fix described below are historical context only.

Even after fixing Root Causes 1–3, when `fetchJiraDetails` returns null (e.g., ticket exists in local KB but not on the configured Jira instance), the "summary" memory slot remains empty. This causes `ExpandPhase` to be skipped entirely because its entry condition is `memory.getSlot("summary").isNotEmpty()`. Without ExpandPhase, no linked tickets are fetched, and the prompt contains minimal data (~2K chars), resulting in 3/7 BRD sections showing "Insufficient data".

**Fix applied in `CollectPhase.kt`**: Added `fallbackSummaryFromKB()` — when `fetchJiraDetails` fails but `lookupKBRecord` succeeds, the method extracts the business summary from the KB record and stores it in the "summary" slot. This ensures ExpandPhase's entry condition is met and the thinking loop continues.

~~**Remaining issue**: `ExpandPhase` calls `getLinkedIssues` which also depends on `jiraClient.getIssueDetails()`. When Jira API is unavailable for the ticket, linked ticket discovery fails too. A KB-based fallback for `GetLinkedIssuesTool` (parsing dependency data from KB records) is needed but not yet implemented.~~ **No longer applicable** — the legacy pipeline and native tools have been removed.

### Root Cause 5: jiraModule Singleton Overrides ServerModule Factory (Confirmed via diagnostic logging)

The definitive root cause for `NoOpJiraClient` being injected into BA agent tools:

`jiraModule` (shared module, `shared/.../jira/JiraModule.kt`) registers `single<JiraClient> { NoOpJiraClient() }` — a **singleton** created at startup. `ServerModule` registers `factory<JiraClient> { ... }` that reads credentials from DB and returns `JiraRestClient` when configured. However, Koin **singleton takes priority over factory** for the same type. The singleton `NoOpJiraClient` is created once at startup and returned for every subsequent `get<JiraClient>()` call, completely bypassing the ServerModule factory.

**Evidence** (confirmed via diagnostic logging):
- `[wireBATools] JiraClient type=com.assistant.jira.NoOpJiraClient` — BA agent receives NoOpJiraClient
- `[NoOpJiraClient] getIssueDetails called for ICL2-15 — Jira credentials not configured` — NoOpJiraClient println appears
- `[JiraCredentialsService] jira config found: ...` — **never appears** because ServerModule factory code never executes
- Route handlers (ProjectRoutes, TicketDetailRoutes) work because they call `createJiraClientFromDb()` directly, bypassing Koin entirely

**Fix applied** (two changes):
1. `shared/.../jira/JiraModule.kt`: Changed `single<JiraClient> { NoOpJiraClient() }` to `factory<JiraClient> { NoOpJiraClient() }` — eliminates the singleton that was overriding ServerModule's factory
2. `server/.../di/ServerModule.kt`: Removed `jiraModule` from `includes(...)` — ServerModule's own `factory<JiraClient>` now reads credentials from DB each request without conflict

**Verified result** (from server logs after fix):
- Prompt size: 7,549 → 16,637 chars (2.2x increase — linked ticket data now included)
- TO-BE classified tickets: 0 → 13 (ExpandPhase successfully fetches linked tickets via Jira API)
- Agent duration: 141ms → 3,940ms (real Jira API calls instead of instant NoOp returns)

## Correctness Properties

Property 1: Bug Condition — BA Agent Registered at Startup

_For any_ application startup where `baAgentModule` is included in the Koin module graph, the `AgentRegistry` SHALL contain the agent type `"ba-document"` immediately after Koin initialization completes, and `AgentJobExecutorBridge.generate()` SHALL successfully resolve the agent without throwing `AgentNotFoundException`.

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation — Non-Registration Beans Unchanged

_For any_ Koin bean resolution that does NOT involve the BA agent registration block (MasterPromptBuilder, AgentJobExecutorBridge, CollectionStrategy factory, curation components, legacy pipeline), the fixed module SHALL produce the same beans with the same behavior as the original module, preserving all existing DI wiring and fallback paths.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt`

**Block**: The last `single { ... }` block (lines 51-62) that registers the BA agent factory

**Specific Changes**:
1. **Add `createdAtStart = true`**: Change `single {` to `single(createdAtStart = true) {` on the registration block. This tells Koin to execute the block eagerly during module loading, not lazily on first request.

2. **No other changes needed**: The block's internal logic (resolving `AgentRegistry`, `MasterPromptBuilder`, `CollectionStrategy` factory, and calling `registry.register()`) is correct. Only the initialization timing is wrong.

**Before:**
```kotlin
single {
    val registry: AgentRegistry = get()
    val promptBuilder: MasterPromptBuilder = get()
    val strategyFactory: (String) -> CollectionStrategy = get()
    registry.register(BADocumentAgent.AGENT_TYPE) { _ ->
        BADocumentAgent(
            toolRegistry = get(),
            memory = JiraContextMemorySchema.createMemory(),
            orchestratorBackend = get(),
            progressReporter = get(),
            strategyFactory = strategyFactory,
            promptBuilder = promptBuilder
        )
    }
}
```

**After:**
```kotlin
single(createdAtStart = true) {
    val registry: AgentRegistry = get()
    val promptBuilder: MasterPromptBuilder = get()
    val strategyFactory: (String) -> CollectionStrategy = get()
    registry.register(BADocumentAgent.AGENT_TYPE) { _ ->
        BADocumentAgent(
            toolRegistry = get(),
            memory = JiraContextMemorySchema.createMemory(),
            orchestratorBackend = get(),
            progressReporter = get(),
            strategyFactory = strategyFactory,
            promptBuilder = promptBuilder
        )
    }
}
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm that the registration block is indeed never executed due to lazy initialization.

**Test Plan**: Write a Koin test that loads `baAgentModule` + `agentModule`, then checks `AgentRegistry.listAgentTypes()` without explicitly requesting the `Unit` bean. Run on UNFIXED code to observe the empty registry.

**Test Cases**:
1. **Empty Registry Test**: Load modules, check `agentRegistry.listAgentTypes()` — expect empty list on unfixed code (will fail assertion that it should contain "ba-document")
2. **AgentNotFoundException Test**: Load modules, call `agentRegistry.getAgent("ba-document", config)` — expect `AgentNotFoundException` on unfixed code
3. **Fallback Path Test**: Simulate `JobExecutor.tryAgentPipeline()` flow — expect it catches exception and returns null on unfixed code

**Expected Counterexamples**:
- `AgentRegistry.listAgentTypes()` returns `[]` instead of `["ba-document"]`
- `agentRegistry.getAgent("ba-document", config)` throws `AgentNotFoundException`
- Possible cause: Koin lazy initialization of side-effect-only `single` block

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  koinApp := startKoin { modules(agentModule, baAgentModule) }
  registry := koinApp.get<AgentRegistry>()
  ASSERT "ba-document" IN registry.listAgentTypes()
  ASSERT registry.getAgent("ba-document", testConfig) does NOT throw
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT baAgentModule_fixed.resolve(MasterPromptBuilder) = baAgentModule_original.resolve(MasterPromptBuilder)
  ASSERT baAgentModule_fixed.resolve(AgentJobExecutorBridge) = baAgentModule_original.resolve(AgentJobExecutorBridge)
  ASSERT baAgentModule_fixed.resolve(CollectionStrategy("BRD")) = baAgentModule_original.resolve(CollectionStrategy("BRD"))
  ASSERT baAgentModule_fixed.resolve(TemporalClassifier) = baAgentModule_original.resolve(TemporalClassifier)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many bean resolution scenarios automatically
- It catches edge cases where eager initialization might affect other beans' resolution order
- It provides strong guarantees that non-registration beans are unchanged

**Test Plan**: Observe bean resolution behavior on UNFIXED code for all non-registration beans, then write tests to verify identical behavior after fix.

**Test Cases**:
1. **MasterPromptBuilder Preservation**: Verify MasterPromptBuilder resolves with same curation-awareness logic before and after fix
2. **AgentJobExecutorBridge Preservation**: Verify bridge resolves with same AgentRegistry and config provider
3. **CollectionStrategy Preservation**: Verify strategy factory returns correct strategy for each docType (BRD, FSD, SLIDES)
4. **Curation Components Preservation**: Verify TemporalClassifier, CommentSummarizer, AttachmentCurator, BudgetEnforcer, McpToolRegistrar all resolve identically

### Unit Tests

- Test that `AgentRegistry` contains `"ba-document"` after Koin startup with fixed module
- Test that `AgentJobExecutorBridge.generate()` does not throw `AgentNotFoundException` with fixed module
- Test that `createdAtStart = true` causes the registration block to execute during `startKoin`
- Test edge case: multiple `startKoin`/`stopKoin` cycles don't cause duplicate registration issues

### Property-Based Tests

- Generate random Koin module configurations (with/without optional dependencies like SettingsRepository) and verify BA agent is always registered
- Generate random docType strings and verify CollectionStrategy factory returns correct strategy type (preservation)
- Test across many startup scenarios that non-registration beans resolve identically

### Integration Tests

- Full `serverModule` startup test verifying `AgentRegistry` contains `"ba-document"`
- End-to-end test: trigger BRD generation with agent pipeline enabled, verify agent pipeline is used (not curation fallback)
- Verify log output contains "Registered agent type: ba-document" during startup
