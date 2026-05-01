# Implementation Plan: CLI Interactive BA Agent

## Overview

Implement a new `CliInteractiveStrategy` that replaces the multi-turn pipeline with a single-prompt, interactive CLI approach. The implementation follows dependency order: models → protocol → session context → tool executor → prompt builder → engine → strategy → wiring.

All new code lives under `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/cli/` with models in a `models/` sub-package. Kotlin code standards apply: files ≤ 200 lines, functions ≤ 20 lines, SOLID principles, `encodeDefaults = true` for serialization.

## Tasks

- [x] 1. Create data models and supporting types
  - [x] 1.1 Create `CliInteractiveModels.kt` in `pipeline/cli/models/`
    - Define `ParsedToolCall(name: String, arguments: Map<String, String>)`
    - Define `LoopConfig(maxToolCalls: Int, timeoutSeconds: Int)`
    - Define `LoopResult(document: String, timedOut: Boolean, toolCallsExecuted: Int, toolCallsFailed: Int)`
    - Define `SessionSummary(totalToolCalls: Int, failedToolCalls: Int, documentSizeChars: Int, totalDurationMs: Long, consecutiveFailures: Int)`
    - _Requirements: 3.5, 3.6, 8.5_

  - [x] 1.2 Create `InteractiveSessionContext.kt` in `pipeline/cli/models/`
    - Implement mutable session state: tool call log, document lines, timing, consecutive failure tracking
    - `recordToolCall(entry: ToolCallLogEntry)` — adds to log, updates failed count if `!success`
    - `appendDocumentLine(line: String)` — accumulates document content
    - `recordConsecutiveFailure()` / `resetConsecutiveFailures()` — circuit breaker tracking
    - `toSummary(): SessionSummary` — immutable snapshot of metrics
    - `toBATaskResult(status: BATaskStatus): BATaskResult` — converts to existing result type
    - Enforce immutability after completion (reject mutations after `toSummary()` called)
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 1.3 Write property test for InteractiveSessionContext
    - **Property 7: Session context accurately tracks all state**
    - Generate random sequences of `recordToolCall` (with random name/duration/success), `appendDocumentLine` (random strings), `recordConsecutiveFailure`, and `resetConsecutiveFailures`
    - Assert `toSummary()` produces correct `totalToolCalls`, `failedToolCalls`, `documentSizeChars`, `consecutiveFailures`
    - **Validates: Requirements 8.1, 8.2, 8.4, 8.5**

- [x] 2. Implement ToolCallProtocol (JSON parse/format)
  - [x] 2.1 Create `ToolCallProtocol.kt` in `pipeline/cli/`
    - `object ToolCallProtocol` with two functions
    - `parseToolCall(line: String): ParsedToolCall?` — detect `"toolCall"` substring, extract JSON from `{"toolCall"`, parse name + arguments, return null on any failure
    - `formatToolResult(name: String, success: Boolean, data: String, error: String): String` — produce `{"toolResult":{...}}` JSON with proper escaping
    - Use `kotlinx.serialization.json.Json` with `encodeDefaults = true` for formatting
    - Never throw exceptions from `parseToolCall` — catch all and return null
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 2.2 Write property test: Tool call parsing extracts correct fields
    - **Property 1: Tool call parsing extracts correct fields from noisy lines**
    - Generate random alphanumeric tool names + random `Map<String, String>` arguments + random prefix strings
    - Construct valid `{"toolCall":{"name":"...","arguments":{...}}}` with prefix
    - Assert `parseToolCall()` returns `ParsedToolCall` with matching name and arguments
    - **Validates: Requirements 2.1, 2.3**

  - [x] 2.3 Write property test: Tool result round-trip
    - **Property 2: Tool result formatting round-trip preserves data**
    - Generate random name, success boolean, data string (with special chars: quotes, backslashes, newlines, unicode), error string
    - Format via `formatToolResult()`, parse output as JSON
    - Assert parsed JSON fields match original inputs
    - **Validates: Requirements 2.2, 2.5, 2.6**

  - [x] 2.4 Write property test: Malformed input produces null
    - **Property 3: Malformed input produces null without exceptions**
    - Generate random strings, partial JSON, wrong-structure JSON
    - Assert `parseToolCall()` returns null and does not throw
    - **Validates: Requirements 2.4**

- [x] 3. Checkpoint — Ensure models and protocol compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement CliToolExecutor (tool execution bridge)
  - [x] 4.1 Create `CliToolExecutor.kt` in `pipeline/cli/`
    - Constructor takes `SubprocessProxy`
    - `suspend fun execute(toolCall: ParsedToolCall): String` — generates UUID for `ToolCallRequest.id`, delegates to `SubprocessProxy.handleToolCallRequest()`, converts `ToolCallResponse` to protocol JSON via `ToolCallProtocol.formatToolResult()`
    - Catch all exceptions → return `{"toolResult":{"name":"...","success":false,"data":"","error":"<message>"}}`
    - Log each tool call: name, duration, success, response size
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 9.2_

  - [x] 4.2 Write property test for CliToolExecutor
    - **Property 5: Tool executor converts any response to valid protocol JSON**
    - Generate random `ToolCallResponse` values (random id, success/failure, random data, random error)
    - Mock `SubprocessProxy` to return generated response
    - Assert `execute()` produces valid JSON matching `{"toolResult":{...}}` format
    - Also test exception case: mock `SubprocessProxy` to throw, assert result has `success=false`
    - **Validates: Requirements 4.2, 4.3, 9.2**

- [x] 5. Implement MasterPromptBuilder (prompt construction)
  - [x] 5.1 Create `MasterPromptBuilder.kt` in `pipeline/cli/`
    - `object MasterPromptBuilder` with `fun build(ticketId, docType, availableTools, customInstructions?): String`
    - Include sections: role instructions, strategy/reasoning, available tools with JSON format, tool result format, template structure (BRD vs FSD), output constraints, `---END---` delimiter, task description with ticketId
    - Append custom instructions when provided
    - Support BRD and FSD document types with appropriate template sections
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 5.2 Write property test for MasterPromptBuilder
    - **Property 6: Master prompt contains all required sections for any input**
    - Generate random non-blank ticket IDs, doc types ("BRD"/"FSD"), lists of `ToolDescriptor` (0+), optional custom instructions
    - Assert output contains: ticket ID, each tool name, `---END---`, custom instructions (when provided)
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.6**

- [x] 6. Implement CliInteractiveEngine (process lifecycle + interactive loop)
  - [x] 6.1 Create `CliInteractiveEngine.kt` in `pipeline/cli/`
    - `fun startProcess(config: SubprocessConfig): Process` — use `ProcessBuilder` with `redirectErrorStream(true)`, configure stdin/stdout streams
    - `suspend fun sendToStdin(writer: BufferedWriter, text: String)` — write text + newline + flush in `Dispatchers.IO`
    - `suspend fun runInteractiveLoop(reader, writer, toolExecutor, sessionContext, config: LoopConfig): LoopResult` — read stdout line-by-line, use `ToolCallProtocol.parseToolCall()` to detect tool calls, execute via `CliToolExecutor`, accumulate document lines, stop on `---END---` or timeout
    - `fun terminateProcess(process: Process)` — graceful shutdown: `destroy()`, wait 5s, `destroyForcibly()` if still alive
    - Wrap loop in `withTimeoutOrNull(config.timeoutSeconds * 1000L)`
    - Track tool call limit — send error response when exceeded
    - Support coroutine cancellation — process cleanup in finally block
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 9.3, 9.4, 9.6_

  - [x] 6.2 Write property test for CliInteractiveEngine interactive loop
    - **Property 4: Interactive loop correctly separates tool calls from content**
    - Generate random sequences of: tool call lines (valid JSON), document content lines, terminating `---END---`
    - Mock `CliToolExecutor` to return canned responses (some success, some failure)
    - Use `PipedInputStream`/`PipedOutputStream` to simulate CLI process I/O
    - Assert: tool call count matches, document content matches non-tool-call lines, `toolCallsExecuted` and `toolCallsFailed` are accurate
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

- [x] 7. Checkpoint — Ensure all components compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement CliInteractiveStrategy and wire into orchestrator
  - [x] 8.1 Create `CliInteractiveStrategy.kt` in `pipeline/`
    - Implement `PipelineStrategy` interface
    - Constructor takes `SubprocessProxy` and `CliBackendResolver`
    - `execute(config, progressReporter)`: resolve backend → build master prompt → spawn CLI → send prompt → run interactive loop → terminate process → return `BATaskResult`
    - Report progress at milestones: CLI spawn (5%), prompt sent (10%), tool calls (15-80%), document received (90%), complete (100%)
    - Return `BATaskResult` with SUCCESS/FAILED/TIMEOUT/PARTIAL status based on loop outcome
    - Handle all errors: CLI path missing, process crash, timeout — return FAILED with details
    - Process cleanup in finally block (always terminate)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 7.1, 7.2, 9.1, 9.5, 10.1, 10.5, 11.1, 11.4_

  - [x] 8.2 Update `BASubprocessOrchestrator.kt` — change default strategy
    - Modify `createDefaultStrategy()` companion function to return `CliInteractiveStrategy` instead of `MultiTurnPipelineStrategy`
    - Pass `SubprocessProxy` and a new `CliBackendResolver` instance to `CliInteractiveStrategy`
    - Keep `MultiTurnPipelineStrategy` available for fallback (do not delete)
    - _Requirements: 10.1, 10.6_

  - [x] 8.3 Update `BAAgentModule.kt` — wire CliInteractiveStrategy dependencies
    - Ensure `CliBackendResolver` is available (already created inline in orchestrator, verify it works with new strategy)
    - Verify `SubprocessProxy` singleton is injected correctly for the new strategy path
    - _Requirements: 10.2, 10.3, 10.4_

  - [x] 8.4 Write unit tests for CliInteractiveStrategy
    - Test: returns FAILED when CLI path missing (mock `CliBackendResolver` to return failure)
    - Test: returns FAILED when process crashes (mock engine to throw)
    - Test: returns SUCCESS with valid document
    - Test: returns TIMEOUT when loop times out
    - Test: reports progress at milestones
    - Test: tool call limit sends error response
    - _Requirements: 6.3, 6.4, 6.5, 7.6, 9.1_

  - [x] 8.5 Write integration tests for end-to-end flow
    - Test: full CLI interactive loop with mock process (PipedInputStream/PipedOutputStream)
    - Test: process termination on completion
    - Test: process termination on timeout
    - Test: drop-in replacement in BASubprocessOrchestrator
    - _Requirements: 1.7, 6.2, 9.4, 10.1_

- [x] 9. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major component group
- Property tests validate universal correctness properties from the design document (P1–P7)
- Unit tests validate specific examples and edge cases
- The implementation order follows dependency chain: models → protocol → executor → prompt → engine → strategy → wiring
- Existing `MultiTurnPipelineStrategy` is preserved as fallback — not deleted
