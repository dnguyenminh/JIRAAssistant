# Implementation Plan: Agent Subprocess Orchestration

## Overview

This plan implements the BA agent subprocess orchestration architecture using a bottom-up approach: data models first (shared module), then server-side components (CliBackendResolver → TaskMessageBuilder → ToolCallLoopEngine → BASubprocessOrchestrator), then BA agent integration (BADocumentAgent, BAAgentConfig, BAAgentModule), and finally wiring/fallback logic. Each task builds on the previous, ensuring no orphaned code.

All code is Kotlin Multiplatform (shared models in `commonMain`, server components in `jvmMain`). Property-based tests use Kotest Property Testing.

## Tasks

- [x] 1. Create shared data models (BATaskConfig, BATaskResult, ToolCallLogEntry)
  - [x] 1.1 Create BATaskConfig data class
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/ba/models/BATaskConfig.kt`
    - Implement `@Serializable data class BATaskConfig` with fields: `rootTicketId` (String), `docType` (String, default "BRD"), `maxToolCalls` (Int, default 30), `taskTimeoutSeconds` (Int, default 180), `cliBackend` (String, default "gemini")
    - Add validation: `rootTicketId` non-blank, `docType` in {"BRD", "FSD", "SLIDES"}, `maxToolCalls` > 0, `taskTimeoutSeconds` > 0, `cliBackend` in {"gemini", "copilot", "kiro", "ollama"}
    - _Requirements: 6.1, 6.6_

  - [x] 1.2 Create BATaskResult and BATaskStatus
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/ba/models/BATaskResult.kt`
    - Implement `@Serializable data class BATaskResult` with fields: `document` (String), `toolCallsExecuted` (Int), `toolCallsFailed` (Int), `totalDurationMs` (Long), `status` (BATaskStatus), `toolCallLog` (List<ToolCallLogEntry>, default emptyList())
    - Implement `@Serializable enum class BATaskStatus { SUCCESS, PARTIAL, TIMEOUT, FAILED }`
    - _Requirements: 6.2, 6.6_

  - [x] 1.3 Create ToolCallLogEntry data class
    - Create `shared/src/commonMain/kotlin/com/assistant/agent/ba/models/ToolCallLogEntry.kt`
    - Implement `@Serializable data class ToolCallLogEntry` with fields: `toolName` (String), `durationMs` (Long), `success` (Boolean), `resultSizeChars` (Int)
    - _Requirements: 6.3, 6.6_

  - [x] 1.4 Write property test for BATaskConfig JSON round-trip
    - Create `shared/src/commonTest/kotlin/com/assistant/agent/ba/models/BATaskConfigPropertyTest.kt`
    - **Property 9: BATaskConfig JSON serialization round-trip**
    - Generate random BATaskConfig with: arbitrary non-blank `rootTicketId`, random `docType` from {"BRD", "FSD", "SLIDES"}, positive `maxToolCalls`, positive `taskTimeoutSeconds`, random `cliBackend` from {"gemini", "copilot", "kiro", "ollama"}
    - Assert: `Json.decodeFromString<BATaskConfig>(Json.encodeToString(config)) == config`
    - Minimum 100 iterations
    - **Validates: Requirements 6.1, 6.4**

  - [x] 1.5 Write property test for BATaskResult JSON round-trip
    - Create `shared/src/commonTest/kotlin/com/assistant/agent/ba/models/BATaskResultPropertyTest.kt`
    - **Property 10: BATaskResult JSON serialization round-trip**
    - Generate random BATaskResult with: arbitrary `document` string, non-negative `toolCallsExecuted`, non-negative `toolCallsFailed`, non-negative `totalDurationMs`, random `status` from BATaskStatus values, random-length list of valid ToolCallLogEntry objects
    - Assert: `Json.decodeFromString<BATaskResult>(Json.encodeToString(result)) == result`
    - Minimum 100 iterations
    - **Validates: Requirements 6.2, 6.3, 6.5**

- [x] 2. Checkpoint — Ensure shared models compile and tests pass
  - Compile shared module: `./gradlew :shared:compileKotlinJvm`
  - Run property tests for models if implemented
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Implement CliBackendResolver
  - [x] 3.1 Create CliBackendResolver class
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/CliBackendResolver.kt`
    - Implement `class CliBackendResolver(private val settingsRepository: SettingsRepository)` with method `suspend fun resolve(cliBackend: String): SubprocessConfig`
    - Map each backend to its settings keys and CLI args pattern: gemini → `ai_cli_path` + `ai_cli_model` + `[cliPath, "-m", model]`; copilot → `copilot_cli_path` + `[cliPath]`; kiro → `kiro_cli_path` + `[cliPath]`; ollama → `ollama_cli_path` + `ollama_cli_model` + `[cliPath, "run", model]`
    - Return error result if CLI path is missing or backend is invalid
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 3.2 Write unit tests for CliBackendResolver
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/CliBackendResolverTest.kt`
    - Test cases: Gemini config resolution, Copilot config resolution, Ollama config resolution, Kiro config resolution, invalid backend returns error, missing CLI path returns error
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 4. Implement TaskMessageBuilder
  - [x] 4.1 Create TaskMessageBuilder object
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/TaskMessageBuilder.kt`
    - Implement `object TaskMessageBuilder` with methods: `fun buildTaskMessage(config: BATaskConfig, tools: List<ToolDescriptor>): String`, `fun buildToolUsageInstructions(tools: List<ToolDescriptor>): String`, `fun buildStrategyHint(docType: String): String`
    - Reuse existing `MasterPromptSections.buildRoleInstruction()`, `MasterPromptSections.buildTemplateStructure()`, `MasterPromptSections.buildOutputFormat()`, `MasterPromptSections.buildDiagramInstructions()`
    - Compose message: role instruction + template structure + output format + diagram instructions + tool usage instructions + strategy hint + root ticket ID
    - Do NOT include pre-collected ticket data, comments, attachments, or linked ticket details
    - Format final message via `MessageProtocol.formatCommand()`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 4.2 Write property test: Task message contains all required sections
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/TaskMessageBuilderPropertyTest.kt`
    - **Property 1: Task message contains all required sections**
    - Generate random BATaskConfig with arbitrary `rootTicketId`, random `docType` from {"BRD", "FSD", "SLIDES"}, and arbitrary `cliBackend`
    - Assert: output contains role instruction section, document template structure section, output format section, the `rootTicketId` value, and the `docType` value
    - Minimum 100 iterations
    - **Validates: Requirements 2.1**

  - [x] 4.3 Write property test: Tool usage instructions reference all available tools
    - Add to `TaskMessageBuilderPropertyTest.kt`
    - **Property 2: Tool usage instructions reference all available tools**
    - Generate random non-empty list of ToolDescriptor objects (1–20 tools, arbitrary names and descriptions)
    - Assert: output of `buildToolUsageInstructions()` contains every tool name from the input list
    - Minimum 100 iterations
    - **Validates: Requirements 2.3**

  - [x] 4.4 Write property test: Task message MessageProtocol round-trip
    - Add to `TaskMessageBuilderPropertyTest.kt`
    - **Property 3: Task message MessageProtocol round-trip**
    - Generate random BATaskConfig, call `buildTaskMessage()`, split output by newlines
    - Assert: each line is either parseable by `MessageProtocol.parseStdoutLine()` as a valid SubprocessMessage, is plain text (returns null), or is the `MessageProtocol.DELIMITER`
    - Minimum 100 iterations
    - **Validates: Requirements 2.5, 2.6**

  - [x] 4.5 Write unit tests for TaskMessageBuilder
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/TaskMessageBuilderTest.kt`
    - Test cases: strategy hint varies by docType (BRD/FSD/SLIDES), no pre-collected data in message, formats via MessageProtocol, tool usage instructions include ToolCallRequest JSON format examples
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 5. Checkpoint — Ensure CliBackendResolver and TaskMessageBuilder compile and tests pass
  - Compile server module: `./gradlew :server:compileKotlinJvm`
  - Run tests if implemented
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement ToolCallLoopEngine
  - [x] 6.1 Create ToolCallLoopEngine class and ToolCallLoopResult
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/ToolCallLoopEngine.kt`
    - Implement `class ToolCallLoopEngine(private val subprocessProxy: SubprocessProxy, private val progressReporter: ProgressReporter)` with method `suspend fun runLoop(stdoutFlow: Flow<String>, stdinWriter: suspend (String) -> Unit, maxToolCalls: Int = 30, timeoutSeconds: Int = 180): ToolCallLoopResult`
    - Implement internal `data class ToolCallLoopResult(val document: String, val toolCallsExecuted: Int, val toolCallsFailed: Int, val toolCallLog: List<ToolCallLogEntry>, val timedOut: Boolean)`
    - Read each line from stdout Flow: JSON lines → `MessageProtocol.parseStdoutLine()` → if type "toolCall", extract ToolCallRequest and proxy via `SubprocessProxy.handleToolCallRequest()`; plain text → accumulate as document; `---END---` → end loop
    - Send ToolCallResponse back via stdinWriter using `MessageProtocol.formatToolResponse()`
    - Enforce max tool call limit: after limit reached, send error response without invoking SubprocessProxy
    - Enforce total timeout: on timeout, return partial document accumulated so far
    - Report each tool call to ProgressReporter with progress in 15–80% range
    - Forward error responses (success=false) to subprocess — AI decides next action
    - Support parallel tool calls matched by correlation ID
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [x] 6.2 Write property test: Tool call proxying with correlation ID matching
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/ToolCallLoopEnginePropertyTest.kt`
    - **Property 4: Tool call proxying with correlation ID matching**
    - Generate random N (1–30) ToolCallRequest messages with unique correlation IDs, emit them on a mock stdout Flow followed by `---END---`
    - Mock SubprocessProxy to capture responses written to stdinWriter
    - Assert: exactly N ToolCallResponse messages written, each response's `id` matches exactly one request's `id` (bijection)
    - Minimum 100 iterations
    - **Validates: Requirements 3.1, 3.4, 3.8**

  - [x] 6.3 Write property test: Plain text accumulation preserves content and order
    - Add to `ToolCallLoopEnginePropertyTest.kt`
    - **Property 5: Plain text accumulation preserves content and order**
    - Generate random K (1–50) plain text lines (no `{` prefix, not `---END---`), emit them on mock stdout Flow followed by `---END---`
    - Assert: returned document contains all K lines in original order
    - Minimum 100 iterations
    - **Validates: Requirements 3.2, 3.3**

  - [x] 6.4 Write property test: Max tool call limit enforcement
    - Add to `ToolCallLoopEnginePropertyTest.kt`
    - **Property 6: Max tool call limit enforcement**
    - Generate random N (1–10) as max limit, emit N+K (K=1–5) ToolCallRequest messages on mock stdout Flow followed by `---END---`
    - Mock SubprocessProxy to track invocations
    - Assert: SubprocessProxy.handleToolCallRequest() invoked exactly N times; remaining K requests receive error responses with `success = false`
    - Minimum 100 iterations
    - **Validates: Requirements 3.5**

  - [x] 6.5 Write unit tests for ToolCallLoopEngine
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/ToolCallLoopEngineTest.kt`
    - Test cases: delimiter ends loop, timeout returns partial document, progress reported per tool call, error responses forwarded to subprocess, empty stdout returns empty document
    - _Requirements: 3.1, 3.2, 3.3, 3.6, 3.7, 3.8_

- [x] 7. Checkpoint — Ensure ToolCallLoopEngine compiles and tests pass
  - Compile server module: `./gradlew :server:compileKotlinJvm`
  - Run tests if implemented
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement BASubprocessOrchestrator
  - [x] 8.1 Create BASubprocessOrchestrator class
    - Create `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/BASubprocessOrchestrator.kt`
    - Implement `class BASubprocessOrchestrator(private val subprocessManager: SubprocessManager, private val subprocessProxy: SubprocessProxy, private val progressReporter: ProgressReporter, private val settingsRepository: SettingsRepository)` with method `suspend fun executeTask(taskConfig: BATaskConfig): BATaskResult`
    - Resolve CLI backend via CliBackendResolver
    - Inject tool list into subprocess context via `SubprocessProxy.buildToolListMessage()` before sending task
    - Build task message via TaskMessageBuilder
    - Delegate stdout reading loop to ToolCallLoopEngine
    - Report progress milestones: 5% (subprocess started), 10% (task sent), 15–80% (tool call loop), 85% (response received), 95% (parsing complete)
    - On failure, return BATaskResult with status FAILED
    - Capture stderr from AI subprocess in separate coroutine, log as WARN
    - Log metrics: total duration, tool calls executed/failed, document size, CLI backend, execution mode
    - Log performance warning when task exceeds 120 seconds with time breakdown
    - Log crash details on subprocess crash (exit code, last stderr, tool calls completed)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 9.1, 9.2, 9.3, 9.5, 9.6_

  - [x] 8.2 Write unit tests for BASubprocessOrchestrator
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/BASubprocessOrchestratorTest.kt`
    - Test cases: delegates to SubprocessManager, injects tool list before task, reports progress milestones, returns FAILED on subprocess crash, returns FAILED on CLI not found, logs metrics on completion, logs performance warning on slow tasks, captures stderr as warnings
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 9.1, 9.2, 9.3, 9.5, 9.6_

- [x] 9. Implement native BA tool registration and SubprocessProxy integration
  - [x] 9.1 Register all native BA tools in ToolRegistry via BAAgentModule
    - Modify `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt`
    - Register all six native BA tools at agent creation time: FetchJiraDetailsTool, GetLinkedIssuesTool, FetchCommentsTool, LookupKBRecordTool, SearchKBTool, ProcessAttachmentTool
    - Ensure ToolRegistry also contains MCP tools from McpToolBridge
    - Ensure tool registration priority: native BA tools (Local) > Agent Home MCP > Shared MCP Bridge
    - _Requirements: 5.1, 5.2, 5.5_

  - [x] 9.2 Write property test: Combined tool descriptors completeness
    - Create `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/SubprocessProxyPropertyTest.kt`
    - **Property 7: Combined tool descriptors completeness**
    - Generate random sets of native BA tool descriptors and MCP tool descriptors (with unique names)
    - Assert: `SubprocessProxy.getAvailableToolDescriptors()` returns a list containing all native BA tool names and all MCP tool names
    - Minimum 100 iterations
    - **Validates: Requirements 5.3**

  - [x] 9.3 Write property test: Tool registration priority ordering
    - Add to `SubprocessProxyPropertyTest.kt`
    - **Property 8: Tool registration priority ordering**
    - Generate random tool names, register from both Local (native) and MCP sources with the same name
    - Assert: `ToolRegistry` resolves to the Local native tool; MCP tool with duplicate name does not appear in `listTools()`
    - Minimum 100 iterations
    - **Validates: Requirements 5.5**

- [x] 10. Checkpoint — Ensure orchestrator and tool registration compile and tests pass
  - Compile server module: `./gradlew :server:compileKotlinJvm`
  - Run tests if implemented
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Integrate with BADocumentAgent and add feature flag routing
  - [x] 11.1 Add buildSubprocessConfig() to BAAgentConfig
    - Modify `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentConfig.kt`
    - Add `fun buildSubprocessConfig(): SubprocessConfig` method that reads CLI path, model, and arguments from SettingsRepository
    - _Requirements: 4.2_

  - [x] 11.2 Modify BADocumentAgent.execute() for subprocess delegation
    - Modify `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BADocumentAgent.kt`
    - Check `ba_subprocess_enabled` setting at execution time (not startup)
    - When enabled: delegate to `BASubprocessOrchestrator.executeTask()` with BATaskConfig built from root ticket ID and document type
    - When disabled or absent: use existing Legacy Pipeline (4-phase architecture)
    - When subprocess fails (FAILED status): automatically fall back to Legacy Pipeline, log warning with failure reason
    - Retain existing `onStart()` and `onComplete()` lifecycle hooks unchanged
    - Log which execution mode was used for each document generation job
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 7.1, 7.2, 7.3, 7.5, 7.6_

  - [x] 11.3 Register BASubprocessOrchestrator in Koin via BAAgentModule
    - Modify `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt`
    - Register BASubprocessOrchestrator as singleton in Koin, wiring SubprocessManager, SubprocessProxy (with ToolRegistry containing all BA native tools and MCP tools), ProgressReporter, and SettingsRepository
    - _Requirements: 1.5, 4.3_

  - [x] 11.4 Add MCP tool update notification during active sessions
    - When a new MCP server is started or stopped via the Integrations page during an active BA subprocess session, call `SubprocessProxy.buildToolsUpdatedMessage()` and write the updated tool list to the subprocess stdin
    - _Requirements: 5.4_

  - [x] 11.5 Write unit tests for BADocumentAgent feature flag routing
    - Test cases: feature flag enabled routes to subprocess orchestrator, feature flag disabled routes to legacy pipeline, feature flag absent routes to legacy pipeline, subprocess failure falls back to legacy pipeline, onStart/onComplete unchanged, correct BATaskConfig passed, execution mode logged
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 7.1, 7.2, 7.5, 7.6_

- [x] 12. Wire fallback chain in JobExecutor
  - [x] 12.1 Update JobExecutor.resolvePrompt() for subprocess orchestration tier
    - Modify `JobExecutor.resolvePrompt()` to support subprocess orchestration as highest-priority pipeline tier
    - Fallback chain: subprocess orchestrator → agent pipeline → curation pipeline → legacy prompt
    - Each tier falls back to the next on failure
    - _Requirements: 7.4_

  - [x] 12.2 Write unit tests for JobExecutor fallback chain
    - Test cases: subprocess tier succeeds, subprocess tier fails falls to agent pipeline, full fallback chain, runtime feature flag switch without restart
    - _Requirements: 7.4, 7.5_

- [x] 13. Checkpoint — Ensure full integration compiles and all tests pass
  - Compile both modules: `./gradlew :shared:compileKotlinJvm :server:compileKotlinJvm`
  - Run all tests: `./gradlew :shared:jvmTest :server:jvmTest`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Final checkpoint — Full build and verification
  - Run full project build: `./gradlew build`
  - Verify no compilation errors across all modules
  - Verify all property-based tests pass (Properties 1–10)
  - Verify all unit tests pass
  - Verify legacy pipeline still works (non-regression)
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major component
- Property tests validate the 10 universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The bottom-up build order ensures no orphaned code: models → utilities → engine → orchestrator → agent integration → wiring
- All new server components go under `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/`
- All shared models go under `shared/src/commonMain/kotlin/com/assistant/agent/ba/models/`
- Legacy pipeline code (CollectPhase, ExpandPhase, VisualizePhase, SynthesizePhase, MasterPromptBuilder) is NOT modified or deleted
