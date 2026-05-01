# Implementation Plan: Map-Reduce Analysis Pipeline

## Overview

Implement a Map-Reduce analysis pipeline that splits large ticket graphs (>200 tickets) into batches, sends each batch to AI for summarization (Map phase), then combines all summaries into a final AnalysisResult (Reduce phase). The pipeline integrates seamlessly into the existing `AIOrchestratorImpl.analyzeTicket()` flow, reusing AI agent infrastructure and provider failover. Small ticket graphs (≤200) continue using the existing single-prompt flow unchanged.

Implementation language: **Kotlin** (matching the existing codebase and design document).

## Tasks

- [x] 1. Foundation — Data models and configuration
  - [x] 1.1 Create MapReduceConfig data class with validated() clamping
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/models/MapReduceConfig.kt`
    - Implement `@Serializable data class MapReduceConfig` with fields: `mapReduceEnabled`, `maxBatchSize`, `maxConcurrentBatches`, `mapReduceThreshold`, `batchTimeoutMs`, `reduceTimeoutMs`, `maxPromptChars`
    - Implement `validated()` method that clamps `maxBatchSize` to 5..100, `maxConcurrentBatches` to 1..5, `mapReduceThreshold` to 50..1000
    - _Requirements: 2.7, 9.1, 9.3_

  - [x]* 1.2 Write property test for MapReduceConfig clamping (Property 2)
    - **Property 2: MapReduceConfig Clamping — Valid Ranges**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/models/MapReduceConfigPropertyTest.kt`
    - For any integer values, `MapReduceConfig(...).validated()` returns config with `maxBatchSize in 5..100`, `maxConcurrentBatches in 1..5`, `mapReduceThreshold in 50..1000`
    - Use Kotest property testing with `Arb.int()` generators
    - **Validates: Requirements 2.7, 9.1, 9.3**

  - [x] 1.3 Create BatchSummary data class with tolerant serialization
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/models/BatchSummary.kt`
    - Implement `@Serializable data class BatchSummary` with fields: `batchIndex`, `ticketIds`, `requirementsSummary`, `technicalInsights`, `dependencySummary`, `keyFindings`, `openQuestions`
    - All fields have default values (empty string, empty list) for tolerant parsing
    - _Requirements: 3.5, 8.1, 8.2, 8.3_

  - [x]* 1.4 Write property test for BatchSummary serialization round-trip (Property 7)
    - **Property 7: BatchSummary Serialization Round-trip**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/models/BatchSummarySerializationPropertyTest.kt`
    - For any valid BatchSummary, `Json.decodeFromString(Json.encodeToString(summary))` produces an equivalent object
    - Use Kotest Arb generators for all BatchSummary fields
    - **Validates: Requirements 3.5, 8.4**

  - [x]* 1.5 Write property test for BatchSummary parsing tolerance (Property 8)
    - **Property 8: BatchSummary Parsing Tolerance**
    - Add tests to `BatchSummarySerializationPropertyTest.kt` or create separate file
    - Test markdown fence stripping (`\`\`\`json ... \`\`\``), unknown field tolerance (`ignoreUnknownKeys`), and missing optional field defaults
    - **Validates: Requirements 8.1, 8.2, 8.3**

  - [x] 1.6 Create MapReduceResult and MapReduceInfo data classes
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/models/MapReduceResult.kt`
    - Implement `@Serializable data class MapReduceResult` with `analysisResult: AnalysisResult` and `mapReduceInfo: MapReduceInfo`
    - Implement `@Serializable data class MapReduceInfo` with `totalBatches`, `successfulBatches`, `failedBatches`, `totalTicketsAnalyzed`, `mapPhaseTimeMs`, `reducePhaseTimeMs`, `reduceSkipped`
    - _Requirements: 4.5_

  - [x]* 1.7 Write property test for MapReduceInfo consistency (Property 10)
    - **Property 10: MapReduceInfo Consistency**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/models/MapReduceInfoPropertyTest.kt`
    - For any MapReduceInfo: `successfulBatches + failedBatches == totalBatches`, `successfulBatches >= 1`, `totalTicketsAnalyzed >= 0`, `mapPhaseTimeMs >= 0`, `reducePhaseTimeMs >= 0`
    - **Validates: Requirements 4.5**

  - [x] 1.8 Create BatchInfo data class
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/models/BatchInfo.kt`
    - Implement `@Serializable data class BatchInfo` with `batchIndex`, `totalBatches`, `tickets: List<TicketNode>`, `depthLevels: List<Int>`
    - Add computed property `ticketIds: List<String>` via `tickets.map { it.ticketId }`
    - _Requirements: 2.1-2.6, 3.7_

  - [x] 1.9 Extend TraversalConfig clamp ranges
    - Update `server/src/jvmMain/kotlin/com/assistant/server/document/models/TraversalConfig.kt`
    - Change `validated()` maxDepth clamp from `1..10` to `1..20`
    - Change `validated()` maxTickets clamp from `1..200` to `1..1000`
    - Add `disableEarlyTermination: Boolean = false` field
    - _Requirements: 1.2, 1.5_

  - [x]* 1.10 Write property test for TraversalConfig extended clamping (Property 1)
    - **Property 1: TraversalConfig Clamping — Extended Ranges**
    - Update existing `server/src/jvmTest/kotlin/com/assistant/server/document/models/TraversalConfigPropertyTest.kt`
    - For any integer values, `TraversalConfig(...).validated()` returns `maxDepth in 1..20` and `maxTickets in 1..1000`
    - Verify other fields (maxCommentsPerTicket 10..1000, cacheTtlMinutes 5..1440) unchanged
    - **Validates: Requirements 1.2**

  - [x] 1.11 Extend AnalysisMetadata with optional mapReduceInfo field
    - Update `shared/src/commonMain/kotlin/com/assistant/ai/deepanalysis/models/AnalysisMetadata.kt`
    - Add `val mapReduceInfo: MapReduceInfo? = null` field
    - Ensure backward compatibility — existing serialized data without this field deserializes correctly
    - _Requirements: 4.5_

- [x] 2. Checkpoint — Foundation models complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Core — BatchStrategy implementation
  - [x] 3.1 Implement BatchStrategy with depth-level grouping
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/BatchStrategy.kt`
    - Implement `partition(graph: TicketGraph): List<BatchInfo>` method
    - Group tickets by depth level, split oversized groups respecting `maxBatchSize`
    - Root ticket always in batch 0 with highest-relevance depth-1 tickets
    - Keep parent-child relationships in same batch when possible
    - Ensure no empty batches, no duplicate tickets, no lost tickets
    - _Requirements: 2.1-2.6_

  - [x]* 3.2 Write property test for batch partition conservation (Property 4)
    - **Property 4: Batch Partition — Conservation and Invariants**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/BatchPartitionPropertyTest.kt`
    - For any TicketGraph with N nodes and maxBatchSize M (5..100): (1) total tickets across batches = N, (2) no duplicate ticket IDs across batches, (3) no empty batches, (4) each batch ≤ M tickets, (5) root ticket in batch 0
    - Use Arb generators for TicketGraph with varying sizes and depths
    - **Validates: Requirements 2.1, 2.3, 2.5, 2.6**

  - [x]* 3.3 Write property test for batch depth grouping (Property 5)
    - **Property 5: Batch Depth Grouping — Tickets cùng depth ưu tiên cùng batch**
    - Add to `BatchPartitionPropertyTest.kt`
    - For any batch, `max(depthLevels) - min(depthLevels) ≤ 1` or batch contains overflow from adjacent depth level
    - **Validates: Requirements 2.2**

- [x] 4. Core — Prompt builders
  - [x] 4.1 Implement BatchPromptBuilder for Map phase prompts
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/BatchPromptBuilder.kt`
    - Implement `buildPrompt(batchInfo, rootTicket, edges): String`
    - Include root ticket summary/description in every batch prompt
    - Include full ticket data (summary, description, comments, status, priority, labels) for batch tickets
    - Include relationship info between tickets in the batch
    - Include batch metadata: "Batch {X} of {Y}, containing {N} tickets at depth levels {D1, D2, ...}"
    - Include JSON output schema for BatchSummary
    - Cap prompt at `maxPromptChars`, truncate comments/attachments before descriptions
    - _Requirements: 3.1, 3.2, 3.6, 3.7_

  - [x]* 4.2 Write property test for batch prompt content (Property 6)
    - **Property 6: Batch Prompt Content — Root Context và Metadata**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/BatchPromptPropertyTest.kt`
    - For any BatchInfo and root ticket: prompt contains root ticket summary, all ticket IDs, "Batch {X} of {Y}" pattern, JSON schema instruction, and `prompt.length ≤ maxPromptChars`
    - **Validates: Requirements 3.1, 3.2, 3.6, 3.7**

  - [x] 4.3 Implement ReducePromptBuilder for Reduce phase prompts
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/ReducePromptBuilder.kt`
    - Implement `buildPrompt(rootTicket, summaries, graphMetadata, totalBatches): String`
    - Include root ticket full data (summary, description, status, priority)
    - Include all BatchSummaries sorted by batchIndex ascending
    - Include ticket graph metadata (total tickets, max depth, relationship overview)
    - Include JSON output schema matching AnalysisResult format
    - Add "WARNING: incomplete analysis" annotation when successfulBatches < totalBatches / 2
    - _Requirements: 4.1, 4.4, 4.6, 7.4_

  - [x]* 4.4 Write property test for reduce prompt content and ordering (Property 9)
    - **Property 9: Reduce Prompt — Content và Ordering**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/ReducePromptPropertyTest.kt`
    - For any list of BatchSummaries (≥1) and root ticket: prompt contains root ticket data, all summaries in batchIndex ascending order, graph metadata, and AnalysisResult JSON schema
    - **Validates: Requirements 4.1, 4.6**

  - [x]* 4.5 Write property test for incomplete analysis warning (Property 12)
    - **Property 12: Incomplete Analysis Warning**
    - Add to `ReducePromptPropertyTest.kt`
    - When `successfulBatches < totalBatches / 2`, prompt contains "WARNING:" or "incomplete" indicator
    - When `successfulBatches >= totalBatches / 2`, prompt does NOT contain warning annotation
    - **Validates: Requirements 7.4**

- [x] 5. Checkpoint — Core components complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Orchestration — MapReduceOrchestrator and ProgressTracker
  - [x] 6.1 Implement ProgressTracker with phase-based progress calculation
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/ProgressTracker.kt`
    - Implement callback-based progress: TRAVERSAL (0-20%), MAP (20-80%), REDUCE (80-95%), PARSING (95-100%)
    - Implement methods: `onTraversalStart`, `onTraversalComplete`, `onMapStart`, `onBatchComplete`, `onBatchFailed`, `onReduceStart`, `onReduceComplete`, `onParsingStart`, `onComplete`
    - Map phase progress: `20 + (completedBatches * 60 / totalBatches)`
    - _Requirements: 5.1-5.5_

  - [x]* 6.2 Write property test for progress calculation (Property 11)
    - **Property 11: Progress Calculation — Map Phase**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/ProgressTrackerPropertyTest.kt`
    - For any completedBatches (0..total) and totalBatches (≥1): progress = `20 + (completedBatches * 60 / totalBatches)`, always in range 20..80
    - **Validates: Requirements 5.1, 5.2**

  - [x] 6.3 Implement MapReduceOrchestrator — analyze, mapPhase, reducePhase
    - Create `server/src/jvmMain/kotlin/com/assistant/server/analysis/MapReduceOrchestrator.kt`
    - Constructor takes: `batchStrategy`, `batchPromptBuilder`, `reducePromptBuilder`, `responseParser`, `configProvider`, `aiAnalysisSemaphore`, `progressTracker`
    - Implement `analyze()`: partition → mapPhase → reducePhase → attach MapReduceInfo
    - Implement `mapPhase()`: concurrent batch processing with `maxConcurrentBatches` limit, respecting `aiAnalysisSemaphore`
    - Implement `reducePhase()`: combine summaries via AI, skip if only 1 batch
    - Retry logic: 2 retries with exponential backoff (2s, 4s); batch 0 gets 4 total retries
    - Fallback: if all batches fail → single-prompt with root + depth-1; if batch 0 fails → extra retries then fallback
    - Parse BatchSummary from AI response: strip markdown fences, `ignoreUnknownKeys`, default values for missing fields
    - Validate ticketIds match (log warning on mismatch, still accept)
    - Log config at start, log per-phase progress at INFO level, log failures with specific reasons
    - Timeout per batch: `batchTimeoutMs`; timeout for reduce: `reduceTimeoutMs`
    - _Requirements: 1.4, 3.3, 3.4, 4.2, 4.3, 4.7, 6.4, 7.1-7.6, 8.1-8.5, 9.4, 10.3, 10.5_

  - [x]* 6.4 Write property test for pipeline selection threshold (Property 3)
    - **Property 3: Pipeline Selection — Threshold Decision**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/analysis/PipelineSelectionPropertyTest.kt`
    - For any ticket count N and threshold T: if N > T AND enabled AND orchestrator != null → map-reduce; otherwise → single-prompt. Flows are mutually exclusive.
    - **Validates: Requirements 1.4, 6.1, 6.2, 6.3, 10.2**

- [x] 7. Checkpoint — Orchestration complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Integration — Wire into existing analysis flow
  - [x] 8.1 Update DeepJiraContentExtractor with unlimited traversal config
    - Update `server/src/jvmMain/kotlin/com/assistant/server/document/DeepJiraContentExtractor.kt`
    - Change `analysisConfig()` to use `maxDepth=20`, `maxTickets=1000`, `totalTimeoutMs=600_000`
    - Set `disableEarlyTermination=true` in analysis config for map-reduce mode
    - Handle traversal timeout gracefully — keep collected tickets and continue
    - _Requirements: 1.1, 1.3, 1.5, 1.6_

  - [x] 8.2 Update TraversalEngine for conditional early termination
    - Update `TraversalState.isEarlyTermination()` to check `config.disableEarlyTermination` flag
    - When `disableEarlyTermination=true`, skip the `dataSize > maxPromptChars * 3` check
    - _Requirements: 1.5_

  - [x] 8.3 Update AIOrchestratorImpl to delegate to MapReduceOrchestrator
    - Update `shared/src/commonMain/kotlin/com/assistant/ai/AIOrchestratorImpl.kt`
    - Add optional `mapReduceOrchestrator: MapReduceOrchestrator? = null` constructor parameter
    - In `analyzeTicket()`: after KB-First check, check ticket count vs threshold
    - When `linkedTickets.size > threshold` AND `mapReduceEnabled` AND orchestrator != null → delegate to `mapReduceOrchestrator.analyze()`
    - Pass `currentAgents()` and `getActiveProvidersByPriority()` as lambda providers
    - Attach `mapReduceInfo` to `AnalysisMetadata` in the result
    - Save result to KB via existing `saveToKB()` logic
    - When orchestrator is null or disabled → existing single-prompt flow unchanged
    - _Requirements: 6.1-6.6, 10.1-10.4_

  - [x] 8.4 Wire MapReduceOrchestrator in DeepCollectionModule DI
    - Update `server/src/jvmMain/kotlin/com/assistant/server/di/DeepCollectionModule.kt`
    - Add `single { MapReduceConfig().validated() }` binding
    - Add `single { BatchStrategy(get()) }` binding
    - Add `single { BatchPromptBuilder() }` binding
    - Add `single { ReducePromptBuilder() }` binding
    - Add `single { ProgressTracker() }` binding
    - Add `single { MapReduceOrchestrator(...) }` binding with all dependencies injected
    - Update AIOrchestratorImpl binding to inject `mapReduceOrchestrator`
    - _Requirements: 10.1, 10.5_

  - [x]* 8.5 Write integration tests for end-to-end map-reduce flow
    - Test full pipeline: partition → map → reduce → parse with mock AI agents
    - Test backward compatibility: ticket count ≤ threshold uses single-prompt
    - Test fallback: all batches fail → single-prompt with root context
    - Test single-batch optimization: 1 batch → skip reduce
    - Test KB cache hit → no map-reduce triggered
    - _Requirements: 1.4, 4.7, 6.1, 6.5, 7.5, 10.4_

- [x] 9. Final checkpoint — All tests pass, full integration verified
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each phase
- Property tests validate all 12 correctness properties from the design document
- All new components go in `server` module under `com.assistant.server.analysis` package
- Models go in `com.assistant.server.analysis.models` sub-package
- Existing property test conventions (Kotest `@OptIn(ExperimentalKotest::class)`, `PropTestConfig`) are followed
- `AnalysisMetadata` extension is in `shared` module since it's a shared model
- DI wiring extends the existing `deepCollectionModule` in `DeepCollectionModule.kt`
