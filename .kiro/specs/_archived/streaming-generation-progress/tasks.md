# Implementation Plan: Streaming Generation Progress

## Overview

Thêm streaming support vào quá trình sinh tài liệu BRD/FSD qua Ollama API, cho phép progress bar tăng dần mượt mà từ 35% → 85% thay vì đứng yên rồi nhảy đột ngột. Thay đổi giới hạn ở 3 file backend: thêm NDJSON model, thêm `analyzeStreaming()` vào OllamaAgent, và cập nhật JobExecutor với streaming-aware retry + throttle.

## Tasks

- [x] 1. Create OllamaStreamLine data model
  - [x] 1.1 Create `shared/src/commonMain/kotlin/com/assistant/ai/models/OllamaStreamLine.kt` with `@Serializable data class OllamaStreamLine` containing fields: `model`, `response`, `done`, `doneReason` (`@SerialName("done_reason")`), `createdAt` (`@SerialName("created_at")`) — all with safe defaults
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 Write property test for NDJSON accumulation (Property 1)
    - **Property 1: NDJSON accumulation preserves all response text**
    - Generate random sequences of `OllamaStreamLine` objects with random `response` strings, verify concatenation of all `response` fields equals the accumulated result
    - **Validates: Requirements 1.2, 1.3**

  - [x] 1.3 Write property test for stream error producing no partial success (Property 2)
    - **Property 2: Stream error never produces partial success**
    - Generate random NDJSON sequences, inject failure at random index before `done: true`, verify result is always `AIResult.Failure`
    - **Validates: Requirements 1.4**

- [x] 2. Implement `analyzeStreaming()` in OllamaAgent
  - [x] 2.1 Add `analyzeStreaming(prompt: String, onProgress: (Int) -> Unit, context: AIContext? = null): AIResult` method to `OllamaAgent`
    - Use Ktor `HttpClient.preparePost().execute { }` with `stream = true` in `OllamaRequest`
    - Read `response.bodyAsChannel()` as `ByteReadChannel`, parse each line as `OllamaStreamLine`
    - Accumulate `response` fields into `StringBuilder`
    - On `done == true`: call `onProgress(100)`, return `AIResult.Success(accumulated)`
    - On exception: return `AIResult.Failure(message)`, never return partial text
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_

  - [x] 2.2 Implement progress estimation heuristic in `analyzeStreaming()`
    - `estimatedTotalLines` starts at 1000
    - Progress formula: `min(95, (linesReceived * 100) / estimatedTotalLines)`
    - If `linesReceived > estimatedTotalLines`: double the estimate
    - Cap at 95% until `done: true`, then set 100%
    - Call `onProgress(progress)` for each parsed line
    - _Requirements: 1.5, 3.1, 3.2, 3.3_

  - [x] 2.3 Write property test for streaming progress bounds and monotonicity (Property 3)
    - **Property 3: Streaming progress is bounded and monotonically non-decreasing**
    - Generate random `linesReceived` / `estimatedTotalLines` pairs, verify progress ∈ [0, 95] before done, equals 100 at done, never decreases
    - **Validates: Requirements 1.5, 3.2, 3.3**

  - [x] 2.4 Write unit tests for OllamaAgent streaming
    - Test: `analyzeStreaming()` returns `AIResult.Success` with full accumulated text on normal stream
    - Test: `analyzeStreaming()` returns `AIResult.Failure` on network error
    - Test: default `estimatedTotalLines` is 1000
    - Test: existing `analyze()` behavior unchanged (still uses `stream: false`)
    - _Requirements: 1.1, 1.4, 3.1, 4.2_

- [x] 3. Checkpoint — Verify OllamaAgent streaming logic
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Update JobExecutor with streaming-aware callAIWithRetry
  - [x] 4.1 Modify `callAIWithRetry()` in `JobExecutor` to detect `agent is OllamaAgent` and call `analyzeStreaming()` with progress callback
    - Progress callback maps streaming progress (0–100) → job progress (35–85): `35 + (streamingProgress * 50) / 100`
    - If agent is not OllamaAgent, use existing `analyze()` path unchanged
    - _Requirements: 2.1, 2.2, 2.5, 3.4_

  - [x] 4.2 Implement throttle logic for DB progress writes in `JobExecutor`
    - Track `lastWrittenProgress` and `lastWriteTime` as local variables per invocation
    - Only write to DB when both: progress changed ≥1% AND ≥2000ms elapsed since last write
    - Final write at 85% (streaming done) bypasses throttle — always writes immediately
    - _Requirements: 2.3, 5.1, 5.2, 5.3_

  - [x] 4.3 Implement fallback from streaming to non-streaming in `JobExecutor`
    - Wrap `analyzeStreaming()` call in try-catch
    - On exception: log warning "Streaming failed, falling back to non-streaming: {error}", call `analyze()` instead
    - Retry logic preserved: each retry resets streaming progress to 35%, tries streaming first then fallback
    - _Requirements: 2.6, 4.1, 4.4_

  - [x] 4.4 Write property test for progress mapping range (Property 4)
    - **Property 4: Progress mapping stays within [35, 85] range**
    - Generate random streaming progress values (0–100), verify mapped job progress always ∈ [35, 85]
    - **Validates: Requirements 2.2, 3.4**

  - [x] 4.5 Write property test for throttle logic (Property 5)
    - **Property 5: Throttle permits DB write only when both conditions met**
    - Generate random `(progress, timestamp)` sequences, verify DB write occurs only when progress delta ≥1% AND time delta ≥2000ms, except final 85% write which always passes
    - **Validates: Requirements 2.3, 5.1, 5.2**

  - [x] 4.6 Write unit tests for JobExecutor streaming integration
    - Test: OllamaAgent type check dispatches to `analyzeStreaming()`
    - Test: non-OllamaAgent uses `analyze()`
    - Test: streaming completion sets progress to 85%
    - Test: retry resets progress to 35%
    - Test: fallback logs warning message
    - Test: final 85% write bypasses throttle
    - _Requirements: 2.1, 2.4, 2.5, 2.6, 4.4, 5.2_

- [x] 5. Checkpoint — Verify full streaming pipeline
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Integration wiring and final verification
  - [x] 6.1 Verify existing `analyze()` behavior unchanged after all modifications
    - Ensure `OllamaAgent.analyze()` still sends `stream: false` and returns full response
    - Ensure `AIAgent` interface has no changes
    - Ensure non-OllamaAgent agents (GeminiAgent, LMStudioAgent) are not affected
    - _Requirements: 4.2, 4.3_

  - [x] 6.2 Write integration test for full streaming pipeline
    - Test end-to-end: JobExecutor → OllamaAgent.analyzeStreaming() → mock Ollama HTTP server returning NDJSON lines → progress updates in JobRepository
    - Verify progress increases from 35% to 85% with throttled DB writes
    - _Requirements: 1.1, 2.2, 2.3, 5.1_

- [x] 7. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests use Kotest property testing (`io.kotest:kotest-property`)
- Implementation language: Kotlin (Kotlin Multiplatform — shared module + server JVM module)
- Checkpoints ensure incremental validation after each major component
- No frontend changes needed — existing polling mechanism automatically reflects backend progress updates
