# Multi-Phase BRD Pipeline — Tasks

## Task 1: Create Pipeline Data Models

- [x] 1.1 Create `models/PipelineModels.kt` with `PhaseId` enum (`DATA_COLLECTION`, `BRD_WRITING`, `DIAGRAM_GENERATION`)
- [x] 1.2 Add `PhaseConfig` data class with fields: `phaseId`, `ticketId`, `docType`, `maxToolCalls`, `timeoutSeconds`
- [x] 1.3 Add `PhaseResult` data class with fields: `phaseId`, `output`, `toolCallLog`, `toolCallsExecuted`, `toolCallsFailed`, `durationMs`, `success`, `timedOut`
- [x] 1.4 Add `PipelineConfig` data class with default values: `phase1MaxToolCalls=25`, `phase1TimeoutSeconds=180`, `phase2MaxToolCalls=15`, `phase2TimeoutSeconds=120`, `phase3MaxToolCalls=10`, `phase3TimeoutSeconds=90`, `enableParallelPhases=true`, `maxRetries=1`
- [x] 1.5 Verify file ≤ 80 lines, all models in `models/` package per Kotlin code standards

## Task 2: Implement PhaseToolFilter

- [x] 2.1 Create `PhaseToolFilter.kt` with companion object containing `PHASE1_PATTERNS`, `PHASE2_PATTERNS`, `PHASE3_PATTERNS`, `EXCLUDED_PATTERNS`
- [x] 2.2 Implement `hasKbTools(tools)` — returns true if any tool name contains "kb_search", "kb_ingest", or "kb_write" (case-insensitive)
- [x] 2.3 Implement `filterForPhase1(tools)` — sort by name, exclude EXCLUDED_PATTERNS, include only PHASE1_PATTERNS matches
- [x] 2.4 Implement `filterForPhase2(tools)` — sort by name, exclude EXCLUDED_PATTERNS, include only PHASE2_PATTERNS matches
- [x] 2.5 Implement `filterForPhase3(tools)` — sort by name, exclude EXCLUDED_PATTERNS, include only PHASE3_PATTERNS matches
- [x] 2.6 Verify file ≤ 100 lines, deterministic output (sort before filter)

## Task 3: Implement BrdAssembler

- [x] 3.1 Create `BrdAssembler.kt` with companion object containing `DIAGRAM_LABELS`, `PLACEHOLDER_PREFIX`, `PLACEHOLDER_SUFFIX`, `FALLBACK_TEXT`
- [x] 3.2 Implement `parseDiagramBlocks(diagramOutput)` — find `<!-- DIAGRAM:LABEL -->` + ` ```xml ` blocks, return `Map<String, String>`
- [x] 3.3 Implement `replacePlaceholders(brdMarkdown, diagrams)` — replace placeholders with matching diagrams or fallback text
- [x] 3.4 Implement `assemble(brdMarkdown, diagramOutput?)` — handle null/blank diagramOutput gracefully, delegate to parse + replace
- [x] 3.5 Verify file ≤ 80 lines, pure functions with no side effects

## Task 4: Implement PhasePromptBuilder

- [x] 4.1 Create `PhasePromptBuilder.kt` with `buildPhase1Prompt(ticketId, tools)` — compose: appendSystemInstructions + appendToolDefinitions + appendToolProtocol + appendDataCollectionStrategy + KB memory protocol instructions
- [x] 4.2 Implement `buildPhase2Prompt(ticketId, docType, tools)` — compose: appendSystemInstructions + appendToolDefinitions + appendToolProtocol + appendBrdSections + KB data retrieval + diagram placeholder instructions
- [x] 4.3 Implement `buildPhase3Prompt(ticketId, tools)` — compose: appendSystemInstructions + appendToolDefinitions + appendToolProtocol + appendDiagramInstructions + KB data retrieval instructions
- [x] 4.4 Implement `buildPhaseContinuation(latestToolResult)` — reuse pattern from AgenticPromptBuilder.buildPersistentContinuation
- [x] 4.5 Add prompt size logging: measure output length, log warning if exceeding soft limits (Phase 1: 20K, Phase 2: 20K, Phase 3: 15K)
- [x] 4.6 Verify file ≤ 180 lines, each build method ≤ 20 lines, reuses existing appendXxx() functions

## Task 5: Implement PipelineOrchestrator

- [x] 5.1 Create `PipelineOrchestrator.kt` with constructor accepting: `AgenticLoopRunner`, `AgenticPromptBuilder`, `PhasePromptBuilder`, `PhaseToolFilter`, `BrdAssembler`
- [x] 5.2 Implement `executePipeline(backend, config, progressReporter)` — detect KB availability via `phaseToolFilter.hasKbTools()`, branch to multi-phase or single-phase
- [x] 5.3 Implement `executeSinglePhase()` — delegate to existing `AgenticLoopRunner.runLoop()` with `AgenticPromptBuilder`, identical to current behavior
- [x] 5.4 Implement `executeMultiPhase()` — run Phase 1, then Phase 2+3 (parallel or sequential based on config), then Assembly
- [x] 5.5 Implement phase execution helper: create `AgenticLoopConfig` from `PhaseConfig`, create phase-specific `AgenticPromptBuilder` wrapper, call `loopRunner.runLoop()`, convert `AgenticLoopResult` to `PhaseResult`
- [x] 5.6 Implement retry logic: if phase fails, retry once with new AI session. Max 1 retry per phase
- [x] 5.7 Implement parallel execution: use `coroutineScope { async {} }` for Phase 2 + Phase 3 when `enableParallelPhases=true`
- [x] 5.8 Implement `aggregateResults()` — combine PhaseResults into single AgenticLoopResult with summed tool calls and concatenated logs
- [x] 5.9 Implement progress reporting: Phase 1 (5-40%), Phase 2 (40-70%), Phase 3 (40-70%), Assembly (70-90%), Complete (100%)
- [x] 5.10 Add duration logging for each phase and total pipeline
- [x] 5.11 Verify file ≤ 200 lines, each method ≤ 20 lines

## Task 6: Integrate PipelineOrchestrator into AiBackendPipelineStrategy

- [x] 6.1 Modify `AiBackendPipelineStrategy.doExecute()` to create `PipelineOrchestrator` and call `executePipeline()` instead of direct `AgenticLoopRunner.runLoop()`
- [x] 6.2 Pass `allToolDescriptors` list to `PipelineConfig` so orchestrator can detect KB availability and filter tools
- [x] 6.3 Verify existing `buildTaskResult()`, `determineStatus()`, and logging code works unchanged with pipeline output
- [x] 6.4 Verify `AgenticLoopRunner`, `ToolExecutionBridge`, `AgenticPromptBuilder`, `OllamaApiClient` are NOT modified

## Task 7: Write Property-Based Tests

- [x] 7.1 Create `MultiPhasePipelinePropertyTest.kt` with Kotest property test setup and custom generators (`toolDescriptorArb`, `ticketIdArb`, `phaseResultArb`, `diagramBlockArb`, `brdWithPlaceholdersArb`)
- [x] 7.2 Implement Property 1 test: KB detection determines pipeline mode — generate random tool lists, verify `hasKbTools()` correctness
- [x] 7.3 Implement Property 2 test: Result aggregation preserves totals — generate random PhaseResults, verify aggregated sums
- [x] 7.4 Implement Property 3 test: Phase prompt content isolation — generate ticketId + tools, verify each phase prompt contains/excludes correct sections
- [x] 7.5 Implement Property 4 test: Phase tool filter correctness — generate mixed tool lists, verify each phase filter returns only matching tools
- [x] 7.6 Implement Property 5 test: Tool filter determinism — generate tool lists, shuffle, verify same output
- [x] 7.7 Implement Property 6 test: Excluded tools never pass — generate tools with "playwright"/"browser", verify excluded from all phases
- [x] 7.8 Implement Property 7 test: Assembly diagram block parsing — generate labeled diagram blocks, verify correct parsing
- [x] 7.9 Implement Property 8 test: Assembly placeholder replacement — generate BRD with placeholders + diagram map, verify replacement

## Task 8: Write Unit and Integration Tests

- [x] 8.1 Create `MultiPhasePipelineUnitTest.kt` with example-based tests for PipelineConfig defaults, BrdAssembler edge cases, and PhaseToolFilter patterns
- [x] 8.2 Write unit tests for PipelineConfig default values (phase1MaxToolCalls=25, phase1TimeoutSeconds=180, etc.)
- [x] 8.3 Write unit tests for BrdAssembler: null diagram output, empty diagram output, no placeholders in BRD
- [x] 8.4 Write unit tests for Phase 1 prompt KB memory protocol (title format, tags)
- [x] 8.5 Write unit tests for Phase 2 prompt diagram placeholder markers
- [x] 8.6 Create `MultiPhasePipelineIntegrationTest.kt` with mock backend tests for end-to-end pipeline flow
- [x] 8.7 Write integration test: multi-phase pipeline with mock backend produces valid BRD
- [x] 8.8 Write integration test: single-phase fallback produces same result as current pipeline
- [x] 8.9 Verify all existing tests pass (`./gradlew :server:jvmTest`)
