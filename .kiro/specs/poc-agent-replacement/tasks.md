# Implementation Plan: POC Agent Replacement

## Overview

Replace the current `CliInteractiveStrategy` with the proven POC architecture by introducing a transport-agnostic `AiBackend` interface hierarchy supporting CLI-based backends (Gemini, Copilot, Kiro) and API-based backends (Ollama). Implementation follows the dependency chain: models → interfaces → infrastructure → base class → concrete clients → factory → bridge/prompt → loop runner → strategy → wiring.

All new code goes under `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/`.
All tests go under `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/`.

## Tasks

- [x] 1. Create data models and enums
  - [x] 1.1 Create AiBackendModels.kt with core types
    - Create `aibackend/models/AiBackendModels.kt`
    - Implement `AiCliResponse` (response, sessionId, rawJson, metadata), `AiCliType` enum (GEMINI, COPILOT, KIRO), `ProcessMode` enum (STATELESS, PERSISTENT), `NodeCliConfig` data class, `ResolvedPaths` data class, `ToolRequest` data class
    - Use `@Serializable` with `encodeDefaults = true` for protocol types
    - _Requirements: 1.7, 1.5, 1.6_

  - [x] 1.2 Create AgenticLoopModels.kt with loop types
    - Create `aibackend/models/AgenticLoopModels.kt`
    - Implement `AgenticLoopConfig` (ticketId, docType, maxToolCalls, taskTimeoutSeconds, processMode), `AgenticLoopResult` (document, toolCallLog, toolCallsExecuted, toolCallsFailed, timedOut, totalDurationMs), `ToolBridgeResult` (formattedResult, logEntry, rawResponse)
    - _Requirements: 9.1, 12.5, 12.6, 10.6_

  - [x] 1.3 Create OllamaModels.kt with Ollama API types
    - Create `aibackend/ollama/OllamaModels.kt`
    - Implement all Ollama serializable models: `OllamaChatRequest`, `OllamaChatMessage`, `OllamaChatResponse`, `OllamaOptions`, `OllamaTool`, `OllamaToolFunction`, `OllamaToolParameters`, `OllamaToolProperty`, `OllamaToolCall`, `OllamaToolCallFunction`
    - Use `@SerialName` for JSON field mapping (e.g., `tool_calls`, `done_reason`, `num_predict`)
    - _Requirements: 7.2, 7.3, 8.1_

- [x] 2. Create interfaces
  - [x] 2.1 Create AiBackend.kt interface
    - Create `aibackend/AiBackend.kt`
    - Define `AiBackend` interface with: `displayName`, stateless mode (`sendPrompt`), session mode (`startSession`, `sendMessage`, `endSession`, `isSessionActive`), tool handling (`isToolCall`, `parseToolCall`), availability (`isInstalled`, `getInstallInstructions`)
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 2.2 Create AiCliClient.kt interface
    - Create `aibackend/AiCliClient.kt`
    - Define `AiCliClient` extending `AiBackend` with `type: AiCliType` and `processMode: ProcessMode`
    - _Requirements: 1.5_

  - [x] 2.3 Create AiApiClient.kt interface
    - Create `aibackend/AiApiClient.kt`
    - Define `AiApiClient` extending `AiBackend` with `baseUrl: String` and `model: String`
    - _Requirements: 1.6_

- [x] 3. Implement NodeCliPathResolver
  - [x] 3.1 Implement NodeCliPathResolver.kt
    - Create `aibackend/NodeCliPathResolver.kt`
    - Implement cross-platform Node.js path detection: OS command resolution (`where`/`which`), script wrapper regex extraction, relative `node_modules` inference, global npm fallback paths
    - Support Windows-specific paths (Scoop, nvm-windows, `%APPDATA%/npm`) and Unix-specific paths (nvm, npm-global, `/usr/local/lib`)
    - Use SLF4J logging
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [x] 3.2 Write property test for script path regex extraction
    - **Property 2: Script path regex extraction**
    - Generate random script content with embedded JS paths in known wrapper formats, verify `extractJsPathFromScript()` extracts non-null path ending with expected JS entry filename
    - **Validates: Requirements 2.2**

- [x] 4. Implement BaseNodeCliClient and concrete CLI clients
  - [x] 4.1 Implement BaseNodeCliClient.kt
    - Create `aibackend/BaseNodeCliClient.kt`
    - Implement abstract base class with: STATELESS mode (spawn → stdin → close → stdout → exit), PERSISTENT mode (stream-json NDJSON → `"type":"result"` detection → `--resume latest`), stderr background thread, timeout enforcement via `process.waitFor()` + `destroyForcibly()`, lazy path resolution via `NodeCliPathResolver`, POC tool call parsing
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 4.2 Write property test for tool call parsing round-trip
    - **Property 1: Tool call parsing round-trip for CLI backends**
    - Generate random tool names (alphanumeric), random param maps, build valid/invalid JSON; verify `isToolCall` returns true for valid and false for invalid, `parseToolCall` returns matching `ToolRequest`
    - **Validates: Requirements 1.3, 3.7**

  - [x] 4.3 Implement GeminiCliClientImpl.kt
    - Create `aibackend/GeminiCliClientImpl.kt`
    - Extend `BaseNodeCliClient` with `type = GEMINI`, configure `NodeCliConfig` with `commandName="gemini"`, `npmPackage="@google/gemini-cli"`, `jsEntryPath="bundle/gemini.js"`
    - Implement stateless args (`-p "" --output-format json`), persistent args (`-p "" --output-format stream-json`, `--resume latest` for subsequent), `isInstalled()` via JS entry point check
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 4.4 Implement CopilotCliClientImpl.kt
    - Create `aibackend/CopilotCliClientImpl.kt`
    - Extend `BaseNodeCliClient` with `type = COPILOT`, configure `NodeCliConfig` with `commandName="copilot"`, `npmPackage="@github/copilot"`, `jsEntryPath="index.js"`
    - Implement stateless args (`-p "" -s --allow-all-tools`), persistent args (`--continue` for subsequent), plain text response handling
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 4.5 Implement KiroCliClientImpl.kt
    - Create `aibackend/KiroCliClientImpl.kt`
    - Extend `BaseNodeCliClient` with `type = KIRO`, configure `NodeCliConfig` with `commandName="kiro-cli"`, `npmPackage="@amazon/kiro-cli"`
    - Return `false` from `isInstalled()` when CLI JS entry point does not exist
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 4.6 Write unit tests for CLI clients
    - Test `buildCommandArgs`, `buildPersistentCommandArgs`, `cliConfig` values for each client
    - Test `isInstalled()` returns false when CLI not found for KiroCliClient
    - Test Copilot plain text response handling
    - _Requirements: 4.1–4.5, 5.1–5.5, 6.1–6.3_

- [x] 5. Checkpoint — Ensure models, interfaces, and CLI clients compile
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Ollama backend
  - [x] 6.1 Implement OllamaToolConverter.kt
    - Create `aibackend/ollama/OllamaToolConverter.kt`
    - Implement `ToolDescriptor.toOllamaTool()` extension function
    - Map parameter types: `"string"` → `"string"`, `"integer"/"int"` → `"integer"`, `"number"/"double"/"float"` → `"number"`, `"boolean"/"bool"` → `"boolean"`, unrecognized → `"string"`
    - Since `ToolDescriptor` only has parameter names (no types), default all to `type = "string"`
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 6.2 Write property test for ToolDescriptor to OllamaTool conversion
    - **Property 5: ToolDescriptor to OllamaTool conversion with type mapping**
    - Generate random `ToolDescriptor` objects with varying parameter counts; verify `function.name` matches, `function.description` matches, `properties` count matches parameter count, each property type is valid JSON Schema type
    - **Validates: Requirements 8.1, 8.2, 8.3**

  - [x] 6.3 Implement OllamaApiClient.kt
    - Create `aibackend/ollama/OllamaApiClient.kt`
    - Implement `AiApiClient` with Ktor `HttpClient` (CIO engine), native tool calling via `tools` field, NDJSON streaming response accumulation, conversation history management for session mode, `sendToolResult()` with `role="tool"` message, availability check via GET to baseUrl
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_

  - [x] 6.4 Write property test for Ollama tool_calls parsing
    - **Property 3: Ollama tool_calls parsing**
    - Generate valid `OllamaChatResponse` JSON with/without `tool_calls`; verify `isToolCall` returns true when tool_calls present, `parseToolCall` returns matching `ToolRequest`
    - **Validates: Requirements 7.3**

  - [x] 6.5 Write property test for conversation history preservation
    - **Property 4: Conversation history preservation in session mode**
    - Generate random message sequences (1..20); verify internal conversation history contains at least N user messages in order (requires mock HTTP via Ktor MockEngine)
    - **Validates: Requirements 7.5**

- [x] 7. Implement AiBackendFactory
  - [x] 7.1 Implement AiBackendFactory.kt
    - Create `aibackend/AiBackendFactory.kt`
    - Implement `create(cliBackend: String): Result<AiBackend>` mapping: `"gemini"` → `GeminiCliClientImpl`, `"copilot"` → `CopilotCliClientImpl`, `"kiro"` → `KiroCliClientImpl`, `"ollama"` → `OllamaApiClient` with baseUrl/model from `CliBackendResolver`
    - Return `Result.failure` for unsupported backend names
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

  - [x] 7.2 Write unit tests for AiBackendFactory
    - Test each backend name maps to correct client type
    - Test unsupported backend returns failure
    - _Requirements: 14.1–14.6_

- [x] 8. Implement ToolExecutionBridge and AgenticPromptBuilder
  - [x] 8.1 Implement ToolExecutionBridge.kt
    - Create `aibackend/ToolExecutionBridge.kt`
    - Convert `ToolRequest` → `ToolCallRequest` with generated UUID, invoke `SubprocessProxy.handleToolCallRequest()`, format result as POC protocol JSON for CLI backends or raw data for Ollama, report to `ProgressReporter.reportToolCall()`, create `ToolCallLogEntry`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 8.2 Write property test for tool call bridge conversion
    - **Property 7: Tool call bridge conversion and logging**
    - Generate random `ToolRequest` objects with non-empty tool names and arbitrary params; mock `SubprocessProxy`; verify `logEntry.toolName` matches, `durationMs >= 0`, `success` matches proxy response, valid UUID in `ToolCallRequest`
    - **Validates: Requirements 10.1, 10.6**

  - [x] 8.3 Write property test for POC protocol formatting
    - **Property 8: Tool result POC protocol formatting**
    - Generate random tool names, success booleans, data/error strings; verify formatted result is valid JSON containing `"type":"tool_result"`, tool name, success value, data/error strings
    - **Validates: Requirements 10.3**

  - [x] 8.4 Implement AgenticPromptBuilder.kt
    - Create `aibackend/AgenticPromptBuilder.kt`
    - Build initial prompt with: role instructions, tool definitions from `SubprocessProxy.getAvailableToolDescriptors()`, POC tool protocol instructions, document template, ticket ID
    - Build stateless continuation prompt with full context + all tool results
    - Build persistent continuation prompt with only latest tool result
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

  - [x] 8.5 Write property test for prompt building
    - **Property 10: Prompt building contains required sections**
    - Generate random ticket IDs, doc types, non-empty tool descriptor lists; verify built prompt contains ticket ID, at least one tool name, POC protocol format instructions, document type reference
    - **Validates: Requirements 15.1, 15.4, 15.5**

- [x] 9. Checkpoint — Ensure bridge, prompt builder, and factory compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement AgenticLoopRunner
  - [x] 10.1 Implement AgenticLoopRunner.kt
    - Create `aibackend/AgenticLoopRunner.kt`
    - Implement core agentic loop: build initial prompt → send to backend → detect tool calls → execute via bridge → send results back → repeat until final document or limits reached
    - Mode selection: `AiApiClient` → session mode, `AiCliClient` PERSISTENT → session mode, `AiCliClient` STATELESS → stateless mode
    - Enforce `maxToolCalls` — send "produce final document" when limit reached
    - Enforce `taskTimeoutSeconds` via `withTimeoutOrNull`
    - Track consecutive failures (3+ → request final document)
    - Report incremental progress between 10% and 90%
    - _Requirements: 9.4, 9.5, 9.6, 11.3_

  - [x] 10.2 Write property test for maxToolCalls enforcement
    - **Property 6: maxToolCalls enforcement**
    - Generate random `maxToolCalls` values (1..100); mock backend that always returns tool calls; verify loop executes at most M tool calls before requesting final document
    - **Validates: Requirements 9.5**

  - [x] 10.3 Write property test for BATaskResult status determination
    - **Property 9: BATaskResult status determination**
    - Generate random `AgenticLoopResult` combinations (timedOut, empty/non-empty document, toolCallsFailed counts); verify: timedOut → TIMEOUT, empty doc → FAILED, non-empty + failures → PARTIAL, non-empty + no failures → SUCCESS; verify `toolCallLog` size equals `toolCallsExecuted`
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**

- [x] 11. Implement AiBackendPipelineStrategy
  - [x] 11.1 Implement AiBackendPipelineStrategy.kt
    - Create `pipeline/AiBackendPipelineStrategy.kt` (in the existing pipeline package)
    - Implement `PipelineStrategy` interface: create backend via `AiBackendFactory`, check `isInstalled()`, report progress phases (5% starting, 10% prompt sent, 90% document received, 100% complete), run `AgenticLoopRunner`, convert `AgenticLoopResult` → `BATaskResult` with correct status
    - _Requirements: 9.1, 9.2, 9.3, 9.7, 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [x] 11.2 Write integration test for agentic loop end-to-end
    - Full loop with mocked `AiBackend` and `SubprocessProxy`
    - Verify prompt → tool calls → document flow
    - Verify progress reporting at correct percentages
    - _Requirements: 9.1–9.7, 11.1–11.5_

- [x] 12. Wire into BASubprocessOrchestrator
  - [x] 12.1 Update BASubprocessOrchestrator.kt default strategy
    - Modify `createDefaultStrategy()` to return `AiBackendPipelineStrategy` instead of `CliInteractiveStrategy`
    - Retain `MultiTurnPipelineStrategy` as fallback via `createMultiTurnStrategy()`
    - Keep constructor strategy injection for testing
    - Do NOT delete `CliInteractiveStrategy` or `cli/` sub-package — kept as fallback
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 12.2 Update BAAgentModule.kt Koin wiring
    - Ensure `AiBackendPipelineStrategy` is properly injected via the orchestrator's default strategy
    - Verify no import changes needed for existing module consumers
    - _Requirements: 13.1_

  - [x] 12.3 Write unit test for orchestrator default strategy
    - Verify `createDefaultStrategy()` returns `AiBackendPipelineStrategy`
    - Verify `createMultiTurnStrategy()` still returns `MultiTurnPipelineStrategy`
    - _Requirements: 13.1, 13.2, 13.3_

- [x] 13. Final checkpoint — Ensure all tests pass and full compilation succeeds
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate the 10 universal correctness properties from the design document
- The existing `CliInteractiveStrategy` and `cli/` sub-package are NOT deleted — kept as fallback
- All new code follows Kotlin standards: files ≤ 200 lines, functions ≤ 20 lines, SOLID principles, SLF4J logging
- Test dependencies (Kotest property, JUnit 5, Ktor MockEngine) are already in `server/build.gradle.kts`
