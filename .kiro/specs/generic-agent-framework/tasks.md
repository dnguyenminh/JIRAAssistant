# Implementation Plan: Generic Agent Framework

## Overview

Build a domain-agnostic agent framework with core interfaces in the `shared` module (KMP-compatible) and runtime implementations in the `server` module. The implementation follows a bottom-up approach: data models → core interfaces → memory → tools → engine → orchestrator → registry → DI wiring. Each task builds on the previous, with property-based tests validating correctness properties from the design.

**Language:** Kotlin (shared module: Kotlin Multiplatform commonMain; server module: Kotlin/JVM)
**Testing:** Kotest with `kotest-property` for PBT, Kotest for unit tests
**DI:** Koin, following existing `ServerModule.kt` patterns
**Serialization:** `kotlinx.serialization` with `encodeDefaults = true` via `JsonConfig.instance`

## Tasks

- [x] 1. Create data models in shared module
  - [x] 1.1 Create error models and enums
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/ErrorModels.kt`
    - Define `ErrorStrategy` enum (RETRY, SKIP, ABORT, FALLBACK), `RetryConfig` data class, `ErrorClassification` enum (RECOVERABLE, UNRECOVERABLE)
    - All classes annotated with `@Serializable`
    - _Requirements: 9.1, 9.7_
  - [x] 1.2 Create tool models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/ToolModels.kt`
    - Define `ToolResult`, `ToolCall`, `ToolCallRecord`, `ToolDescriptor` data classes
    - All fields with defaults, annotated `@Serializable`
    - _Requirements: 3.2, 3.7_
  - [x] 1.3 Create agent metrics model
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/AgentMetrics.kt`
    - Define `AgentMetrics` data class with all fields defaulting to 0
    - _Requirements: 11.4, 11.5_
  - [x] 1.4 Create agent state model
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/AgentState.kt`
    - Define `AgentState` data class with `MAX_REASONING_LOG_ENTRIES = 100` companion constant
    - Define `AgentStateStatus` enum (RUNNING, PAUSED, COMPLETED, FAILED)
    - _Requirements: 6.1, 6.2, 6.5_
  - [x] 1.5 Create agent input and output models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/AgentInput.kt`
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/models/AgentOutput.kt`
    - Define `AgentInput` data class, `AgentOutput` data class, `AgentStatus` enum (SUCCESS, PARTIAL, FAILED)
    - _Requirements: 1.4, 1.5_

- [x] 2. Create memory system in shared module
  - [x] 2.1 Create memory slot models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/memory/MemorySlot.kt`
    - Define `SlotSchema` (name, type, maxSize), `SlotType` enum (STRING, LIST, MAP), `MemoryEntry` (data, source, toolName, timestamp), `SlotFullResult` (slotName, currentSize)
    - _Requirements: 2.1, 2.2_
  - [x] 2.2 Implement StructuredMemory class
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/memory/StructuredMemory.kt`
    - Implement `store()` with capacity enforcement returning `SlotFullResult` when full
    - Implement `getSlot()`, `getCompleteness()` (clamped 0.0–1.0), `getTotalSize()` (sum of entry.data.length), `clear()`
    - Use custom serializer for `kotlinx.serialization` compatibility
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6, 2.7_
  - [x] 2.3 Write property tests for StructuredMemory serialization round-trip
    - **Property 3: StructuredMemory serialization round-trip**
    - **Validates: Requirements 2.4, 2.8**
  - [x] 2.4 Write property tests for StructuredMemory completeness calculation
    - **Property 6: StructuredMemory completeness calculation**
    - **Validates: Requirements 2.3**
  - [x] 2.5 Write property tests for StructuredMemory slot capacity enforcement
    - **Property 7: StructuredMemory slot capacity enforcement**
    - **Validates: Requirements 2.5**
  - [x] 2.6 Write property tests for StructuredMemory clear and getTotalSize
    - **Property 8: StructuredMemory clear resets all slots**
    - **Property 9: StructuredMemory getTotalSize invariant**
    - **Validates: Requirements 2.6, 2.7**

- [x] 3. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Create core interfaces in shared module
  - [x] 4.1 Create AgentTool interface and ToolRegistry interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/tool/AgentTool.kt` — interface with `name`, `description`, `parameterNames`, `suspend fun execute(params): ToolResult`
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/tool/ToolRegistry.kt` — interface with `register()`, `registerAll()`, `listTools()`, `invoke()`, `getRemainingCalls()`, `resetCallCount()`
    - _Requirements: 3.1, 3.7_
  - [x] 4.2 Create ThinkingLoopEngine interface and PhaseDefinition
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/engine/PhaseDefinition.kt` — `PhaseDefinition` class (non-serializable, holds function refs), `PhaseConfig` data class (serializable metadata)
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/engine/ThinkingLoopEngine.kt` — interface with `suspend fun execute(phases, memory, toolRegistry, reporter): ThinkingLoopResult`
    - _Requirements: 4.1, 4.2_
  - [x] 4.3 Create OrchestratorBackend interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/orchestrator/OrchestratorBackend.kt`
    - Define `getBackendName()` and `suspend fun runThinkingLoop(memory, tools, phases): AgentOutput`
    - No LLM-specific types in the interface
    - _Requirements: 7.1, 7.6_
  - [x] 4.4 Create ProgressReporter interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/progress/ProgressReporter.kt`
    - Define `reportPhase()`, `reportProgress()`, `reportToolCall()`
    - _Requirements: 8.1_
  - [x] 4.5 Create AgentRegistry interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/registry/AgentRegistry.kt`
    - Define `register()`, `getAgent()`, `listAgentTypes()`
    - Define `AgentNotFoundException` exception class
    - _Requirements: 12.1, 12.3, 12.4_
  - [x] 4.6 Create GenericAgent interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/GenericAgent.kt`
    - Define `getAgentId()`, `getAgentType()`, `getState()`, `suspend fun onStart()`, `suspend fun execute()`, `suspend fun onComplete()`
    - _Requirements: 1.1, 1.2_

- [x] 5. Create AgentConfig DSL in shared module
  - [x] 5.1 Implement AgentConfig data class and DSL builders
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/config/AgentConfigDsl.kt`
    - Define serializable `AgentConfig` data class with all fields
    - Implement `agentConfig { }` DSL with builders: `memorySchema { }`, `phases { }`, `tools { }`, `limits { }`, `errorStrategy { }`
    - Validate at construction: unique phase names, valid loopback targets, unique slot names
    - Throw `InvalidAgentConfigException` listing all errors on validation failure
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  - [x] 5.2 Write property tests for AgentConfig serialization round-trip
    - **Property 5: AgentConfig serialization round-trip**
    - **Validates: Requirements 10.4, 10.6**
  - [x] 5.3 Write property test for AgentConfig validation rejects invalid configurations
    - **Property 20: AgentConfig validation rejects invalid configurations**
    - **Validates: Requirements 10.2, 10.3**

- [x] 6. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement serialization property tests for data models
  - [x] 7.1 Create custom Arb generators for all framework types
    - Create `server/src/test/kotlin/com/assistant/server/agent/generators/AgentArbitraries.kt`
    - Implement `Arb` generators for: `AgentInput`, `AgentOutput`, `AgentState`, `AgentConfig`, `StructuredMemory`, `MemoryEntry`, `SlotSchema`, `ToolResult`, `ToolCall`, `ToolCallRecord`, `AgentMetrics`
    - Minimum 100 iterations per property test
    - _Requirements: 1.6, 1.7, 2.8, 6.6_
  - [x] 7.2 Write property tests for AgentInput serialization round-trip
    - **Property 1: AgentInput serialization round-trip**
    - **Validates: Requirements 1.4, 1.6**
  - [x] 7.3 Write property tests for AgentOutput serialization round-trip
    - **Property 2: AgentOutput serialization round-trip**
    - **Validates: Requirements 1.5, 1.7**
  - [x] 7.4 Write property tests for AgentState serialization round-trip
    - **Property 4: AgentState serialization round-trip**
    - **Validates: Requirements 6.2, 6.6**
  - [x] 7.5 Write property test for AgentState reasoning log cap
    - **Property 17: AgentState reasoning log cap**
    - **Validates: Requirements 6.5**

- [x] 8. Implement ToolRegistryImpl in server module
  - [x] 8.1 Implement ToolRegistryImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/tool/ToolRegistryImpl.kt`
    - Implement `register()` with duplicate-name replacement and warning log
    - Implement `invoke()` that never throws — wraps all errors in `ToolResult(success=false)`
    - Implement rate limiting with configurable max calls (default: 50), returning `RATE_LIMIT_EXCEEDED` error
    - Implement per-invocation timeout (default: 30s) via `withTimeout`
    - Log each invocation: tool name, truncated params (200 chars), execution time, result size, success/failure
    - Implement `getRemainingCalls()`, `resetCallCount()`, `listTools()`, `registerAll()`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_
  - [x] 8.2 Write property tests for ToolRegistry registration and listing
    - **Property 10: ToolRegistry registration and listing**
    - **Validates: Requirements 3.1, 3.7, 3.8**
  - [x] 8.3 Write property test for ToolRegistry invoke never throws
    - **Property 11: ToolRegistry invoke never throws**
    - **Validates: Requirements 3.2, 3.3**
  - [x] 8.4 Write property test for ToolRegistry rate limiting
    - **Property 12: ToolRegistry rate limiting**
    - **Validates: Requirements 3.4**

- [x] 9. Implement ParallelToolExecutor in server module
  - [x] 9.1 Implement ParallelToolExecutor
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/engine/ParallelToolExecutor.kt`
    - Use `coroutineScope` + `async` for concurrent execution
    - Use `Semaphore(maxConcurrency)` for throttling (default: 5)
    - Individual failures don't cancel siblings — each `async` catches its own errors
    - Return results in same order as input calls (order preservation)
    - Log wall-clock time for each batch and individual execution times
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  - [x] 9.2 Write property test for ParallelToolExecutor order preservation and count
    - **Property 15: ParallelToolExecutor order preservation and count**
    - **Validates: Requirements 5.1, 5.6**
  - [x] 9.3 Write property test for ParallelToolExecutor failure isolation
    - **Property 16: ParallelToolExecutor failure isolation**
    - **Validates: Requirements 5.3**

- [x] 10. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement ThinkingLoopEngineImpl in server module
  - [x] 11.1 Implement ThinkingLoopEngineImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/engine/ThinkingLoopEngineImpl.kt`
    - Execute phases sequentially: evaluate `entryCondition` → run `phaseAction` → check `exitCondition`
    - Implement loopback logic with `maxIterations` guard
    - Implement per-phase timeout via `withTimeout(maxDurationSeconds)`
    - Implement total execution timeout (default: 120s) — force-skip to final phase
    - Report phase transitions to `ProgressReporter`
    - Maintain reasoning log of all decisions
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  - [x] 11.2 Write property test for ThinkingLoopEngine phase execution order
    - **Property 13: ThinkingLoopEngine phase execution order**
    - **Validates: Requirements 4.1, 4.6**
  - [x] 11.3 Write property test for ThinkingLoopEngine progress reporting
    - **Property 14: ThinkingLoopEngine progress reporting**
    - **Validates: Requirements 4.5, 8.3**

- [x] 12. Implement error handling in server module
  - [x] 12.1 Implement error classification and strategy resolution
    - Add error classification logic to `ToolRegistryImpl` or a dedicated `ErrorHandler` helper
    - Classify: tool timeout, network error, rate limit → RECOVERABLE; auth failure, invalid config → UNRECOVERABLE
    - Implement strategy resolution order: tool-level → phase-level → agent-level default
    - Implement RETRY with configurable `maxRetries` (default: 2) and `delayMs` (default: 2000ms)
    - Implement FALLBACK execution with escalation to SKIP on failure
    - Log every error: type, message, tool name, phase name, strategy, outcome
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
  - [x] 12.2 Write property test for ErrorStrategy RETRY invocation count
    - **Property 18: ErrorStrategy RETRY invocation count**
    - **Validates: Requirements 9.4**
  - [x] 12.3 Write property test for error classification correctness
    - **Property 19: Error classification correctness**
    - **Validates: Requirements 9.7**

- [x] 13. Implement ProgressReporter implementations in server module
  - [x] 13.1 Implement DocGenProgressAdapter and NoOpProgressReporter
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/progress/DocGenProgressAdapter.kt`
    - Map agent phases to existing labels: AGGREGATING_DATA, GENERATING_DOCUMENT, PARSING_RESPONSE, SAVING
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/progress/NoOpProgressReporter.kt`
    - Silently discard all events
    - _Requirements: 8.2, 8.3, 8.4, 8.5_

- [x] 14. Implement OrchestratorBackend implementations in server module
  - [x] 14.1 Implement CustomKotlinOrchestrator
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/orchestrator/CustomKotlinOrchestrator.kt`
    - Delegate LLM calls to existing `AIAgent` interface
    - Wire ThinkingLoopEngineImpl, ToolRegistry, StructuredMemory, ProgressReporter
    - No external AI framework dependencies
    - _Requirements: 7.2, 7.6_
  - [x] 14.2 Implement LangChain4jOrchestrator (stub)
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/orchestrator/LangChain4jOrchestrator.kt`
    - Optional implementation — loaded only when LangChain4j is on classpath
    - Falls back to CustomKotlinOrchestrator if initialization fails with logged warning
    - _Requirements: 7.3, 7.5_

- [x] 15. Implement AgentRegistryImpl and AgentStateManager in server module
  - [x] 15.1 Implement AgentRegistryImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/registry/AgentRegistryImpl.kt`
    - Singleton registry mapping agent type names to factory functions
    - `getAgent()` instantiates via factory and injects dependencies via Koin
    - Throw `AgentNotFoundException` with available types if type not found
    - Duplicate registration replaces with logged warning
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_
  - [x] 15.2 Write property tests for AgentRegistry registration and retrieval
    - **Property 21: AgentRegistry registration and retrieval**
    - **Validates: Requirements 12.1, 12.4, 12.5**
  - [x] 15.3 Write property test for AgentRegistry unknown type exception
    - **Property 22: AgentRegistry unknown type exception**
    - **Validates: Requirements 12.3**
  - [x] 15.4 Implement AgentStateManager
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/state/AgentStateManager.kt`
    - Implement pause: serialize current AgentState, store via callback, stop execution gracefully
    - Implement resume: deserialize state, restore memory and phase position, continue loop
    - _Requirements: 6.3, 6.4_

- [x] 16. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 17. Create Koin DI module and wire everything together
  - [x] 17.1 Create AgentModule for Koin
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/di/AgentModule.kt`
    - Register all framework components: ToolRegistryImpl, ThinkingLoopEngineImpl, ParallelToolExecutor, CustomKotlinOrchestrator, LangChain4jOrchestrator, AgentRegistryImpl, DocGenProgressAdapter, NoOpProgressReporter, AgentStateManager
    - Follow existing `ServerModule.kt` patterns (factory for per-request, single for singletons)
    - Backend selection driven by `agent_orchestrator_type` setting in SettingsRepository
    - _Requirements: 1.3, 7.4, 8.5, 12.6_
  - [x] 17.2 Include AgentModule in ServerModule
    - Add `includes(agentModule)` to `ServerModule.kt`
    - Ensure framework components are resolvable from the main Koin graph
    - _Requirements: 12.6_

- [x] 18. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
  - Verify all 12 requirements are covered by implementation tasks
  - Verify all 22 correctness properties have corresponding property test tasks

- [x] 18.1 Verify all 12 requirements (1–12) are covered by tasks 1–17
- [x] 18.2 Verify all 22 correctness properties (1–22) have corresponding property test tasks

---

## Phase 2: Agent-as-Subprocess Architecture (Requirements 13–20)

### Overview

Extend the framework with subprocess management, agent home directories, markdown-based skills/rules, streaming output, multi-command sessions, MCP integration, and orchestrator MCP proxy. Implementation follows the same bottom-up approach: data models → interfaces → implementations → tests → DI wiring.

- [ ] 19. Create new data models in shared module (subprocess, home, session, streaming)
  - [x] 19.1 Create subprocess message models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessModels.kt`
    - Define `ToolCallRequest` (`id`, `name`, `arguments`), `ToolCallResponse` (`id`, `success`, `data`, `error`), `SubprocessMessage` (`type`, `toolCall?`, `toolResult?`, `tools?`, `content?`)
    - All classes annotated with `@Serializable`
    - _Requirements: 20.1, 20.4_
  - [x] 19.2 Create subprocess config model
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessConfig.kt`
    - Define `SubprocessConfig` (`agentType`, `cliCommand`, `cliArgs`, `environment`, `workingDirectory`, `unresponsiveTimeoutMs` default 60000, `shutdownTimeoutMs` default 5000)
    - Annotated with `@Serializable`
    - _Requirements: 13.1, 13.4, 13.7_
  - [x] 19.3 Create agent home config model
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/AgentHomeConfig.kt`
    - Define `AgentHomeConfig` (`agentType`, `model`, `maxTokens`, `apiEndpoint`, `activeSkills`, `activeRules`, `cliCommand`, `cliArgs`, `environment`) with defaults
    - Annotated with `@Serializable`
    - _Requirements: 14.4, 14.5_
  - [x] 19.4 Create skill and rule definition models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/SkillDefinition.kt`
    - Define `SkillDefinition` (`fileName`, `purpose`, `availableTools`, `procedure`, `outputFormat`, `constraints`, `rawContent`)
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/RuleDefinition.kt`
    - Define `RuleDefinition` (`fileName`, `purpose`, `keywords`, `categories`, `priority` default 100, `conflictResolution`, `rawContent`)
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/WorkflowDefinition.kt`
    - Define `WorkflowDefinition` (`fileName`, `name`, `description`, `steps`, `rawContent`)
    - All annotated with `@Serializable`
    - _Requirements: 15.1, 16.1_
  - [x] 19.5 Create session context and command history models
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/session/SessionContext.kt`
    - Define `SessionContext` (`agentType`, `memory: StructuredMemory`, `commandHistory`, `startedAt`, `commandCount`) with `MAX_HISTORY_SIZE = 50` companion constant
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/session/CommandHistoryEntry.kt`
    - Define `CommandHistoryEntry` (`command`, `responseSummary`, `timestamp`, `isSummary`)
    - All annotated with `@Serializable`
    - _Requirements: 18.1, 18.4_
  - [x] 19.6 Create MCP config model
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/AgentMcpConfig.kt`
    - Define `AgentMcpConfig` (`serverName`, `command`, `args`, `env`, `toolDescriptions`)
    - Annotated with `@Serializable`
    - _Requirements: 19.1_
  - [x] 19.7 Create streaming output interfaces
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/streaming/StreamingOutput.kt`
    - Define `StreamingCallback` functional interface with `onUpdate(chunk: String, progress: Int)`
    - Define `StreamingConfig` data class with `enabled`, `bufferSize` fields
    - _Requirements: 17.1, 17.2_

- [ ] 20. Create Arb generators for new data models
  - [x] 20.1 Create SubprocessArbitraries generator file
    - Create `server/src/test/kotlin/com/assistant/server/agent/generators/SubprocessArbitraries.kt`
    - Implement `Arb` generators for: `ToolCallRequest`, `ToolCallResponse`, `SubprocessMessage`, `SubprocessConfig`, `AgentHomeConfig`, `SkillDefinition`, `RuleDefinition`, `WorkflowDefinition`, `SessionContext`, `CommandHistoryEntry`, `AgentMcpConfig`
    - Minimum 100 iterations per property test
    - _Requirements: 14.5, 20.4_

- [ ] 21. Write serialization property tests for new data models
  - [x] 21.1 Write property test for AgentHomeConfig serialization round-trip
    - **Property 23: AgentHomeConfig serialization round-trip**
    - **Validates: Requirements 14.5**
  - [x] 21.2 Write property test for ToolCallRequest/ToolCallResponse serialization round-trip
    - **Property 37: ToolCallRequest/ToolCallResponse serialization round-trip**
    - **Validates: Requirements 20.1, 20.4**

- [x] 22. Checkpoint — Ensure all new data model tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 23. Create new interfaces in shared module
  - [x] 23.1 Create SubprocessManager interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessManager.kt`
    - Define `suspend fun sendCommand(agentType: String, command: String): Flow<String>`, `suspend fun isRunning(agentType: String): Boolean`, `suspend fun terminate(agentType: String)`, `suspend fun terminateAll()`, `fun getRunningAgentTypes(): List<String>`
    - _Requirements: 13.1, 13.5_
  - [x] 23.2 Create SubprocessProxy interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessProxy.kt`
    - Define `suspend fun handleToolCallRequest(request: ToolCallRequest): ToolCallResponse`, `fun getAvailableToolDescriptors(): List<ToolDescriptor>`, `fun buildToolListMessage(): String`, `fun buildToolsUpdatedMessage(): String`
    - _Requirements: 20.1, 20.2_
  - [x] 23.3 Create AgentHomeDirectory interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/home/AgentHomeDirectory.kt`
    - Define `fun getConfig(): AgentHomeConfig`, `fun getSkills(): List<SkillDefinition>`, `fun getActiveSkills(): List<SkillDefinition>`, `fun getRules(): List<RuleDefinition>`, `fun getWorkflows(): List<WorkflowDefinition>`, `fun getMcpConfigs(): List<AgentMcpConfig>`, `fun buildSystemPrompt(): String`, `fun reload()`
    - _Requirements: 14.1, 14.2, 14.6_
  - [x] 23.4 Create SessionManager interface
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/session/SessionManager.kt`
    - Define `fun getSessionContext(agentType: String): SessionContext`, `fun addCommandToHistory(agentType: String, command: String, response: String)`, `fun getCommandHistory(agentType: String): List<CommandHistoryEntry>`, `fun resetSession(agentType: String)`, `fun buildContextSummary(agentType: String): String`
    - _Requirements: 18.1, 18.2, 18.3_

- [ ] 24. Implement Agent Home Directory system in server module
  - [x] 24.1 Implement SkillParser
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/home/SkillParser.kt`
    - Parse markdown files extracting `## Purpose`, `## Available Tools`, `## Procedure`, `## Output Format`, `## Constraints` sections
    - Return `SkillDefinition` with parsed sections; skip files missing required sections (`## Purpose`, `## Procedure`) with logged warning
    - _Requirements: 15.1, 15.4, 15.5_
  - [x] 24.2 Implement RuleParser
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/home/RuleParser.kt`
    - Parse markdown files extracting `## Purpose`, `## Keywords`, `## Categories`, `## Priority`, `## Conflict Resolution` sections
    - Parse `## Priority` as Int (default 100); skip files missing required sections (`## Purpose`, `## Categories`) with logged warning
    - _Requirements: 16.1, 16.4, 16.5_
  - [x] 24.3 Implement AgentHomeDirectoryLoader
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentHomeDirectoryLoader.kt`
    - Implement `AgentHomeDirectory` interface
    - Scan `.agent/skills/`, `.agent/rules/`, `.agent/workflows/`, `.agent/mcp/` directories
    - Parse `config.json` into `AgentHomeConfig`
    - Validate directory structure — create missing directories with defaults and log warning
    - Implement `getActiveSkills()` filtering by `config.json` `activeSkills` array (empty = all)
    - Implement `buildSystemPrompt()` combining all active skills and rules
    - Implement `reload()` to re-scan all directories
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 15.2, 15.3_
  - [x] 24.4 Implement AgentHomeDirectoryWatcher
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentHomeDirectoryWatcher.kt`
    - Use `java.nio.file.WatchService` to watch `.agent/skills/`, `.agent/rules/`, `.agent/workflows/` for file changes
    - On file add/modify/delete, call `AgentHomeDirectoryLoader.reload()`
    - Run on dedicated `Dispatchers.IO` coroutine
    - _Requirements: 14.6_
  - [x] 24.5 Write property test for agent home directory scan and load
    - **Property 28: Agent home directory scan and load**
    - **Validates: Requirements 14.1, 14.2**
  - [x] 24.6 Write property tests for skill parsing and prompt composition
    - **Property 29: Skill parsing and prompt composition**
    - **Validates: Requirements 15.1, 15.2**
  - [x] 24.7 Write property test for skill activation filtering
    - **Property 30: Skill activation filtering**
    - **Validates: Requirements 15.3**
  - [x] 24.8 Write property test for markdown file validation — invalid files skipped
    - **Property 31: Markdown file validation — invalid files are skipped**
    - **Validates: Requirements 15.4, 16.4**
  - [x] 24.9 Write property test for rule parsing and priority ordering
    - **Property 32: Rule parsing and priority ordering**
    - **Validates: Requirements 16.1, 16.2, 16.3**

- [x] 25. Checkpoint — Ensure agent home directory tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 26. Implement Subprocess Management in server module
  - [x] 26.1 Create ManagedSubprocess state holder
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/ManagedSubprocess.kt`
    - Define class holding `agentType`, `process: Process`, `stdin: BufferedWriter`, `stdout: BufferedReader`, `stderr: BufferedReader`, `commandMutex: Mutex`, `lastActivityTimestamp`, `restartCount`
    - _Requirements: 13.1, 13.3_
  - [x] 26.2 Implement MessageProtocol
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/MessageProtocol.kt`
    - Implement `object MessageProtocol` with `DELIMITER = "---END---"`
    - Implement `formatCommand()`, `formatToolResponse()`, `formatToolList()`, `formatToolsUpdated()`, `parseStdoutLine()`, `isDelimiter()`
    - Use `kotlinx.serialization` for JSON formatting/parsing
    - _Requirements: 13.2, 20.4, 20.8_
  - [x] 26.3 Implement SubprocessManagerImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessManagerImpl.kt`
    - Implement `SubprocessManager` interface
    - Use `ConcurrentHashMap<String, ManagedSubprocess>` for singleton subprocess per agent type
    - Spawn subprocesses via `ProcessBuilder` with `redirectErrorStream(false)`
    - Implement `sendCommand()`: acquire `Command_Mutex` → write to stdin → read stdout as `Flow<String>` → release mutex
    - Implement crash detection: monitor `process.isAlive`, auto-restart on next `sendCommand()`
    - Implement graceful shutdown: SIGTERM → 5s wait → `process.destroyForcibly()`
    - Capture stderr on dedicated `Dispatchers.IO` coroutine, log as warnings
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_
  - [x] 26.4 Write property test for subprocess singleton reuse
    - **Property 24: Subprocess singleton reuse**
    - **Validates: Requirements 13.1**
  - [x] 26.5 Write property test for Command_Mutex sequential execution
    - **Property 25: Command_Mutex sequential execution**
    - **Validates: Requirements 13.3**
  - [x] 26.6 Write property test for message protocol round-trip
    - **Property 26: Message protocol round-trip**
    - **Validates: Requirements 13.2**

- [ ] 27. Implement Streaming Output in server module
  - [x] 27.1 Implement StreamingOutputAdapter
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/streaming/StreamingOutputAdapter.kt`
    - Implement Flow-based streaming with `onUpdate` callback mechanism
    - Forward stdout chunks to registered callback within 100ms
    - Integrate with `ProgressReporter` for both phase-level and chunk-level updates
    - Handle interrupted connections gracefully — continue agent execution, discard undelivered chunks
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_
  - [x] 27.2 Write property test for streaming output order preservation
    - **Property 27: Streaming output order preservation**
    - **Validates: Requirements 13.5, 17.1**

- [x] 28. Checkpoint — Ensure subprocess and streaming tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 29. Implement Session Management in server module
  - [x] 29.1 Implement SessionManagerImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/session/SessionManagerImpl.kt`
    - Implement `SessionManager` interface
    - Maintain `ConcurrentHashMap<String, SessionContext>` per agent type
    - Implement `addCommandToHistory()` with cap at `MAX_HISTORY_SIZE = 50` — summarize older entries when exceeded
    - Implement `resetSession()` clearing Working_Memory slots and command history
    - Implement `buildContextSummary()` producing text summary of previous commands and current memory state
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5_
  - [x] 29.2 Write property test for session memory persistence across commands
    - **Property 33: Session memory persistence across commands**
    - **Validates: Requirements 18.1**
  - [x] 29.3 Write property test for session reset clears working memory
    - **Property 34: Session reset clears working memory**
    - **Validates: Requirements 18.2**
  - [x] 29.4 Write property test for command history cap
    - **Property 35: Command history cap**
    - **Validates: Requirements 18.4, 18.5**

- [ ] 30. Implement MCP Integration in server module
  - [x] 30.1 Implement AgentMcpManager
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt`
    - Read MCP server configurations from `.agent/mcp/` directory
    - Start configured MCP servers at agent initialization
    - Auto-register MCP tools in `ToolRegistry` with server name prefix (e.g., `mcp_jira_search`)
    - Handle MCP server start failure: log error, mark tools as unavailable, continue with remaining tools
    - Implement lifecycle management: graceful shutdown when agent session ends
    - Apply same rate limiting, timeout, and logging policies as native `AgentTool` invocations
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6_
  - [x] 30.2 Write property test for MCP tool registration with server prefix
    - **Property 36: MCP tool registration with server prefix**
    - **Validates: Requirements 19.1, 19.3**

- [ ] 31. Implement Orchestrator MCP Proxy in server module
  - [x] 31.1 Implement SubprocessProxyImpl
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessProxyImpl.kt`
    - Implement `SubprocessProxy` interface
    - Parse `ToolCallRequest` JSON from subprocess stdout
    - Execute tool via `ToolRegistry.invoke()` — transparent routing (local, MCP bridge, agent MCP)
    - Return `ToolCallResponse` JSON to subprocess stdin
    - Support parallel proxying of multiple concurrent requests via `ParallelToolExecutor` with correlation ID matching
    - Implement `getAvailableToolDescriptors()` returning all tools from ToolRegistry
    - Implement `buildToolListMessage()` for tool list injection at session start
    - Implement `buildToolsUpdatedMessage()` for runtime tool list updates
    - Implement three-tier tool priority: Local > Agent Home MCP > Shared MCP Bridge
    - Log each proxied call: agent type, tool name, tool source, execution time, result size, success/failure
    - Error handling: return error `ToolCallResponse`, never kill subprocess
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.9_
  - [x] 31.2 Write property test for parallel tool call proxying with correlation ID matching
    - **Property 38: Parallel tool call proxying with correlation ID matching**
    - **Validates: Requirements 20.5**
  - [x] 31.3 Write property test for tool call error isolation — subprocess survives failures
    - **Property 39: Tool call error isolation — subprocess survives failures**
    - **Validates: Requirements 20.6**
  - [x] 31.4 Write property test for tool registration priority ordering
    - **Property 40: Tool registration priority ordering**
    - **Validates: Requirements 20.9**

- [ ] 32. Update Koin DI module and wire new components
  - [x] 32.1 Create SubprocessModule for Koin
    - Create or extend `server/src/jvmMain/kotlin/com/assistant/server/agent/di/AgentModule.kt`
    - Register all new components: `SubprocessManagerImpl` (single — singleton), `SubprocessProxyImpl` (factory), `AgentHomeDirectoryLoader` (factory), `AgentHomeDirectoryWatcher` (factory), `AgentMcpManager` (factory), `SessionManagerImpl` (single — singleton), `StreamingOutputAdapter` (factory), `MessageProtocol` (single)
    - Follow existing `AgentModule.kt` patterns
    - Ensure new components are resolvable from the main Koin graph
    - _Requirements: 13.1, 14.1, 18.1, 19.2, 20.2_
  - [x] 32.2 Update AgentConfig DSL to support home directory path
    - Extend `AgentConfig` or `AgentConfigDsl` to include `homeDirectoryPath` field
    - Enable the framework to load skills, rules, workflows, and MCP configurations from the file system at agent startup
    - _Requirements: 10.7_

- [x] 33. Final checkpoint — Ensure all new tests pass
  - Ensure all tests pass, ask the user if questions arise.
  - Verify Requirements 13–20 are covered by implementation tasks
  - Verify all new correctness properties (23–40) have corresponding property test tasks

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major component
- Property tests validate the 40 universal correctness properties from the design document (1–22 in Phase 1, 23–40 in Phase 2)
- Unit tests validate specific examples and edge cases
- All files must respect the 200-line limit and 20-line function limit per project coding standards
- All `@Serializable` data classes use `JsonConfig.instance` with `encodeDefaults = true`
- Phase 2 tasks (19–33) follow the same bottom-up pattern as Phase 1: data models → interfaces → implementations → tests → DI wiring
- Interfaces live in `shared` module, implementations in `server` module
