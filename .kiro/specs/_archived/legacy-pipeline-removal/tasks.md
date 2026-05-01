# Implementation Plan: Legacy Pipeline Removal

## Overview

Remove the legacy BA document generation pipeline (4-phase thinking loop, MasterPromptBuilder, feature flags, fallback chains) making `BASubprocessOrchestrator` the single execution path. Follows a strict bottom-up deletion order: leaf files → consumer updates → DI cleanup → config cleanup → test updates. Each layer includes a compilation checkpoint to catch dangling references early.

## Tasks

- [ ] 1. Layer 1 — Delete leaf dependency files
  - [x] 1.1 Delete the 4 legacy phase files
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/CollectPhase.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/ExpandPhase.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/VisualizePhase.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/SynthesizePhase.kt`
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 Delete the 2 legacy orchestrator backend files
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/orchestrator/CustomKotlinOrchestrator.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/orchestrator/LangChain4jOrchestrator.kt`
    - _Requirements: 2.1, 2.3_

  - [x] 1.3 Delete MasterPromptBuilder.kt (keep MasterPromptSections.kt)
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Verify `MasterPromptSections.kt` remains untouched in the same directory
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 1.4 Delete JobExecutorCallHelper.kt
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/jobs/JobExecutorCallHelper.kt`
    - _Requirements: 11.1, 11.3_

- [ ] 2. Layer 2 — Simplify consumer classes
  - [x] 2.1 Simplify BADocumentAgent to subprocess-only
    - Remove constructor parameters: `orchestratorBackend`, `promptBuilder`, `strategyFactory`, `settingsRepository`
    - Keep constructor parameters: `toolRegistry`, `memory`, `progressReporter`, `subprocessOrchestrator`
    - Remove `executeLegacyPipeline()` method and `isSubprocessEnabled()` feature flag check
    - Remove `SUBPROCESS_FLAG_KEY` constant from companion object
    - Rewrite `execute()` to call subprocess directly, return FAILED on null orchestrator or FAILED status (no fallback)
    - Remove imports for `OrchestratorBackend`, `MasterPromptBuilder`, `CollectionStrategy`, `BAAgentConfig`, `SettingsRepository`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 2.2 Simplify JobExecutor to subprocess-only
    - Remove constructor parameters: `agentBridge`, `curationPipeline`, `mcpToolRegistrar`, `providerConfigRepo`, `httpClient`
    - Keep constructor parameters: `aggregator`, `documentRepository`, `jobRepository`, `settingsRepository`, `subprocessOrchestrator`
    - Remove methods: `resolveLegacyPrompt()`, `resolvePrompt()`, `handleLegacyPrompt()`, `trySubprocessDirect()`, `isSubprocessEnabled()`, `isAgentPipelineEnabled()`, `isCurationEnabled()`, `tryCurationPipeline()`, `tryAgentPipeline()`, `legacyPrompt()`, `buildDocPrompt()`, `aggregateData()`
    - Remove `callHelper` field and all references to `JobExecutorCallHelper`
    - Rewrite `execute()` to call subprocess directly, mark job FAILED on error (no fallback)
    - Remove imports for `AgentJobExecutorBridge`, `BAAgentSettings`, `CurationPipeline`, `CuratedPromptAssembler`, `PromptAssembler`, `FeatureFlagAggregator`, `EnrichedContext`, `CurationSettings`, `McpToolRegistrar`, `ProviderConfigRepository`, `HttpClient`, `BrdPromptBuilder`, `FsdPromptBuilder`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 11.2_

  - [x] 2.3 Simplify JobExecutorAIHelper to SubprocessAgentStub only
    - Remove `resolveAgentFromConfig()` function
    - Remove `supportedProviderTypes()` and `buildAgent()` helper functions
    - Remove `MAX_RETRIES`, `MAX_RETRIES_CURATION` constants and logger
    - Remove imports for `GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`, `ProviderConfigRepository`, `HttpClient`, `CurationConfig`, `CurationPipeline`
    - Keep only the `SubprocessAgentStub` object
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 3. Checkpoint — Compile after Layer 1+2 changes
  - Run `./gradlew :server:compileKotlinJvm` to verify no dangling references from deleted files and simplified consumers
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Layer 3 — Clean DI modules
  - [x] 4.1 Clean BAAgentModule
    - Remove `MasterPromptBuilder` singleton registration
    - Remove `CollectionStrategy` factory registration (`factory<(String) -> CollectionStrategy>`)
    - Remove `AgentJobExecutorBridge` singleton registration
    - Remove curation dependency singletons (`TemporalClassifier`, `CommentSummarizer`, `AttachmentCurator`, `BudgetEnforcer`, `McpToolRegistrar`) — these belong in `CurationModule`
    - Remove `resolveCurationEnabled()`, `buildPromptBuilder()`, `resolveStrategy()` private functions
    - Simplify `BADocumentAgent` factory registration: inject only `ToolRegistry`, `ProgressReporter`, `BASubprocessOrchestrator` (no `OrchestratorBackend`, `MasterPromptBuilder`, `CollectionStrategy`)
    - Remove imports for `MasterPromptBuilder`, `CollectionStrategy`, `BrdCollectionStrategy`, `FsdCollectionStrategy`, `SlidesCollectionStrategy`, `AgentJobExecutorBridge`, curation classes
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 4.2 Clean AgentModule
    - Remove `factory<OrchestratorBackend> { CustomKotlinOrchestrator(get(), get()) }` registration
    - Remove `factory { LangChain4jOrchestrator(get()) }` registration
    - Remove imports for `OrchestratorBackend`, `CustomKotlinOrchestrator`, `LangChain4jOrchestrator`
    - Verify subprocess-related registrations (`SubprocessManager`, `SubprocessProxy`, `SessionManager`) remain intact
    - _Requirements: 7.1, 7.2, 7.3_

- [ ] 5. Layer 4 — Config and bridge cleanup
  - [x] 5.1 Delete BAAgentSettings.kt
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/integration/BAAgentSettings.kt`
    - All three constants (`SUBPROCESS_ENABLED`, `AGENT_PIPELINE_ENABLED`, `DEFAULT_ENABLED`) are removed with the file
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 5.2 Clean BAAgentConfig — remove phase configuration
    - Remove `buildBAPhaseConfig()` method
    - Remove private phase builder methods: `buildCollectPhase()`, `buildExpandPhase()`, `buildVisualizePhase()`, `buildSynthesizePhase()`
    - Remove imports for `CollectPhase`, `ExpandPhase`, `VisualizePhase`, `SynthesizePhase`, `CollectionStrategy`
    - Keep `buildSubprocessConfig()` and `buildBAAgentConfig()` methods
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [x] 5.3 Delete AgentJobExecutorBridge.kt
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/integration/AgentJobExecutorBridge.kt`
    - _Requirements: 5.4_

  - [x] 5.4 Delete CollectionStrategy files (4 files)
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategy.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/BrdCollectionStrategy.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/FsdCollectionStrategy.kt`
    - Delete `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/SlidesCollectionStrategy.kt`
    - _Requirements: 6.2_

- [x] 6. Checkpoint — Compile after Layer 3+4 changes
  - Run `./gradlew :server:compileKotlinJvm` to verify no dangling references from DI and config cleanup
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Layer 5 — Delete legacy test files
  - [x] 7.1 Delete phase test files (6 files)
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/CollectPhaseTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/ExpandPhaseTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/ExpandPhaseClassificationTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/VisualizePhaseTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/SynthesizePhaseTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/FakeToolRegistry.kt`
    - _Requirements: 12.5_

  - [x] 7.2 Delete legacy prompt and strategy test files (4 files)
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilderPropertyTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptPropertyTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptContentPropertyTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategyPropertyTest.kt`
    - _Requirements: 12.5_

  - [x] 7.3 Delete AgentJobExecutorBridge test and legacy BADocumentAgent test
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/integration/AgentJobExecutorBridgeTest.kt`
    - Delete `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BADocumentAgentTest.kt`
    - _Requirements: 12.5_

- [ ] 8. Layer 5 — Rewrite tests for subprocess-only path
  - [x] 8.1 Rewrite BADocumentAgentFeatureFlagTest as BADocumentAgentSubprocessTest
    - Rename file to `BADocumentAgentSubprocessTest.kt`
    - Test case: subprocess success returns document (no legacy fallback)
    - Test case: subprocess FAILED returns AgentOutput with FAILED status (no fallback)
    - Test case: null orchestrator returns AgentOutput with FAILED status
    - Test case: subprocess exception returns AgentOutput with FAILED status
    - Remove all feature flag routing tests and legacy fallback scenarios
    - Update test helpers to match simplified BADocumentAgent constructor (4 params)
    - _Requirements: 12.1, 12.2_

  - [x] 8.2 Rewrite JobExecutorFallbackChainTest as JobExecutorSubprocessDirectTest
    - Rename file to `JobExecutorSubprocessDirectTest.kt`
    - Test case: subprocess success saves document with `aiProviderUsed = "BA Subprocess Orchestrator"`
    - Test case: subprocess FAILED marks job as failed (no fallback)
    - Test case: null orchestrator marks job as failed
    - Test case: subprocess exception marks job as failed
    - Remove all multi-tier fallback chain tests and agent pipeline tests
    - Update `TestableExecutor` to match simplified JobExecutor constructor (5 params)
    - _Requirements: 12.3, 12.4_

  - [x] 8.3 Simplify FallbackChainTestDoubles
    - Remove `FakeBridge` class (no more `AgentJobExecutorBridge`)
    - Remove `fallbackStubRegistry()` function (only used by `FakeBridge`)
    - Keep `FakeOrchestrator`, `TrackingAggregator`, `NoOpJobRepo`, `NoOpDocRepo`
    - Keep `fallbackStubManager()`, `fallbackStubProxy()` (used by `FakeOrchestrator`)
    - Update `FakeOrchestrator` if constructor changes needed
    - _Requirements: 12.3_

  - [x] 8.4 Write property test: BADocumentAgent always delegates to subprocess
    - **Property 1: BADocumentAgent subprocess-only delegation**
    - Create `BADocumentAgentSubprocessOnlyPropertyTest.kt`
    - For any valid AgentInput with random ticket IDs and doc types (BRD, FSD, SLIDES), when `execute()` is called with a non-null orchestrator, `executeTask()` is invoked exactly once
    - Use Kotest property testing with minimum 100 iterations
    - Tag: `Feature: legacy-pipeline-removal, Property 1: BADocumentAgent subprocess-only`
    - **Validates: Requirements 4.1, 4.2, 4.3**

  - [x] 8.5 Write property test: JobExecutor subprocess-direct save
    - **Property 2: JobExecutor subprocess-direct save**
    - Create `JobExecutorSubprocessDirectPropertyTest.kt`
    - For any valid job parameters where subprocess returns SUCCESS, the document is saved directly with `aiProviderUsed = "BA Subprocess Orchestrator"` and no `AIAgent.analyze()` call is made
    - Use Kotest property testing with minimum 100 iterations
    - Tag: `Feature: legacy-pipeline-removal, Property 2: JobExecutor subprocess-direct save`
    - **Validates: Requirements 5.1, 5.2, 5.4, 5.6**

- [x] 9. Checkpoint — Compile tests and run full test suite
  - Run `./gradlew :server:compileTestKotlinJvm` to verify test compilation
  - Run `./gradlew :server:test` to verify all tests pass
  - Verify CLI agent classes (`GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`) are preserved and `IntegrationRoutes` still compiles
  - Ensure all tests pass, ask the user if questions arise.
  - _Requirements: 12.6, 13.1, 13.2, 13.3_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation — compile between layers to catch issues early
- The bottom-up deletion order (leaf → consumer → DI → config → tests) prevents intermediate compilation errors
- Property tests validate the 2 correctness properties from the design document
- CLI agent classes and `MasterPromptSections.kt` are explicitly preserved per design decisions
