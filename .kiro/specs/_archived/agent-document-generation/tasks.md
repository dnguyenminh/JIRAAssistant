# Implementation Plan: Agent-Based Document Generation Pipeline

## Overview

This plan implements the BA Document Agent — the first specialized agent built on the Generic Agent Framework. All tasks assume the Generic Agent Framework interfaces and implementations (`generic-agent-framework` spec) are already available. Tasks cover only BA-Agent-specific domain code: memory schema, tools, collection strategies, phase definitions, prompt builder, and JobExecutor integration.

Code follows Kotlin standards: max 200 lines/file, max 20 lines/function, models in separate packages, SOLID principles.

## Tasks

- [x] 1. Define JiraContextMemory schema and extension functions
  - [x] 1.1 Create `JiraContextMemorySchema` object with 9 typed `SlotSchema` definitions
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/memory/JiraContextMemorySchema.kt`
    - Define slots: summary (STRING/10000), description (STRING/10000), comments (LIST/50), attachmentsData (LIST/30), linkedTickets (MAP/20), businessGoals (STRING/10000), kbRecords (MAP/20), technicalDetails (STRING/10000), acceptanceCriteria (LIST/50)
    - Provide `createMemory()` factory method returning `StructuredMemory(SLOTS)`
    - _Requirements: 1.1, 1.5_

  - [x] 1.2 Create extension functions for typed memory access
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/memory/JiraContextMemoryExtensions.kt`
    - Implement: `storeSummary()`, `storeComment()`, `storeLinkedTicket()`, `getLinkedTicketIds()`, `hasKBRecord()`, `storeAttachmentData()`, `storeKBRecord()`, `storeTechnicalDetails()`, `storeBusinessGoals()`, `storeAcceptanceCriteria()`
    - Each extension wraps `StructuredMemory.store()` with proper `MemoryEntry` metadata (source, toolName, timestamp)
    - _Requirements: 1.1, 1.2_

  - [x] 1.3 Write property test for memory entry metadata recording
    - **Property 1: Memory entry metadata recording**
    - **Validates: Requirements 1.2**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/memory/JiraContextMemoryPropertyTest.kt`
    - Use custom `Arb.jiraContextMemory()` generator

- [x] 2. Implement CollectionStrategy interface and 3 implementations
  - [x] 2.1 Create `CollectionStrategy` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategy.kt`
    - Define: `docType`, `sufficiencyThreshold`, `maxPromptChars`, `maxLinkedTicketDepth`, `scoreRelevance()`, `isAttachmentRelevant()`, `getPrioritizedSlots()`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 2.2 Implement `BrdCollectionStrategy`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/BrdCollectionStrategy.kt`
    - Threshold: 0.70, maxPrompt: 60000, depth: 2
    - Priority slots: businessGoals, acceptanceCriteria, comments
    - Relevance scoring: prioritize Stories, Epics; deprioritize Bugs, Tasks with technical labels
    - Attachment filter: requirement docs, business docs, mockups
    - _Requirements: 4.1, 4.4, 4.5_

  - [x] 2.3 Implement `FsdCollectionStrategy`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/FsdCollectionStrategy.kt`
    - Threshold: 0.60, maxPrompt: 70000, depth: 2
    - Priority slots: technicalDetails, kbRecords, linkedTickets
    - Relevance scoring: prioritize Tasks, Bugs with technical labels; include API specs
    - Attachment filter: technical specs, architecture diagrams, API docs
    - _Requirements: 4.2, 4.4, 4.5_

  - [x] 2.4 Implement `SlidesCollectionStrategy`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/SlidesCollectionStrategy.kt`
    - Threshold: 0.50, maxPrompt: 30000, depth: 1
    - Priority slots: summary, businessGoals, attachmentsData
    - Relevance scoring: prioritize Epics, Stories; limit to direct links only
    - Attachment filter: presentations, mockups, diagrams
    - _Requirements: 4.3, 4.4, 4.5_

  - [x] 2.5 Write property test for relevance score range invariant
    - **Property 5: Relevance score range invariant**
    - **Validates: Requirements 4.4**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategyPropertyTest.kt`
    - Verify `scoreRelevance()` returns [0.0, 1.0] for all 3 strategies with arbitrary inputs

  - [x] 2.6 Write property tests for relevance threshold filtering and attachment filtering
    - **Property 2: Relevance threshold filtering**
    - **Property 3: Attachment relevance filtering**
    - **Validates: Requirements 3.3, 3.4, 4.5**
    - Add to `CollectionStrategyPropertyTest.kt`

- [x] 3. Implement 6 BA Agent Tools
  - [x] 3.1 Create `FetchJiraDetailsTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/FetchJiraDetailsTool.kt`
    - Delegates to existing `JiraClient` to fetch ticket summary, description, status, priority, metadata
    - Returns `ToolResult` with structured ticket content
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 Create `GetLinkedIssuesTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/GetLinkedIssuesTool.kt`
    - Delegates to `JiraClient` to fetch linked ticket keys and relationship types
    - _Requirements: 2.1, 3.3_

  - [x] 3.3 Create `FetchCommentsTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/FetchCommentsTool.kt`
    - Delegates to `JiraClient`, returns max 20 most recent comments
    - _Requirements: 2.1, 2.3_

  - [x] 3.4 Create `ProcessAttachmentTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/ProcessAttachmentTool.kt`
    - Delegates to existing `VectorStore` for text extraction from files/images
    - _Requirements: 2.1, 3.4_

  - [x] 3.5 Create `LookupKBRecordTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/LookupKBRecordTool.kt`
    - Delegates to existing `KBRepository` to retrieve pre-analyzed KB record
    - _Requirements: 2.1, 5.3_

  - [x] 3.6 Create `SearchKBTool` implementing `AgentTool`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/tools/SearchKBTool.kt`
    - Delegates to `KBRepository` for KB search by query string
    - _Requirements: 2.1_

  - [x] 3.7 Write unit tests for all 6 BA Agent tools
    - Test each tool with mocked dependencies (JiraClient, KBRepository, VectorStore)
    - Test error handling: network failure returns `ToolResult(success=false)`
    - Test parameter validation
    - _Requirements: 2.2, 2.3, 2.5_

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement 4 phase definitions
  - [x] 5.1 Create `CollectPhase` object
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/CollectPhase.kt`
    - Calls `fetchJiraDetails` for root ticket + `lookupKBRecord` + `fetchComments`
    - Stores results in memory via extension functions
    - Exit condition: summary slot is not empty
    - _Requirements: 3.1, 3.2_

  - [x] 5.2 Create `ExpandPhase` object
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/ExpandPhase.kt`
    - Calls `getLinkedIssues` for root ticket
    - Scores each linked ticket via `CollectionStrategy.scoreRelevance()`
    - Batches `fetchJiraDetails` calls for relevant tickets (score >= 0.3) via `ParallelToolExecutor`
    - Stores one-line references for low-relevance tickets
    - Entry condition: summary slot is not empty
    - _Requirements: 3.3, 4.4, 4.5, 6.1_

  - [x] 5.3 Create `VisualizePhase` object
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/VisualizePhase.kt`
    - Filters attachments via `CollectionStrategy.isAttachmentRelevant()`
    - Batches `processAttachment` calls for relevant attachments via `ParallelToolExecutor`
    - Entry condition: attachmentsData slot is empty (not yet processed)
    - _Requirements: 3.4, 6.1_

  - [x] 5.4 Create `SynthesizePhase` object
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/SynthesizePhase.kt`
    - Performs sufficiency check on prioritized slots
    - If below threshold → signals loopback to Expand
    - If sufficient → delegates to `MasterPromptBuilder`
    - _Requirements: 3.5, 3.6_

  - [x] 5.5 Write property test for sufficiency check loopback decision
    - **Property 4: Sufficiency check loopback decision**
    - **Validates: Requirements 3.5**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategyPropertyTest.kt`

  - [x] 5.6 Write unit tests for each phase
    - Test CollectPhase with mocked tools
    - Test ExpandPhase relevance filtering and parallel batching
    - Test VisualizePhase attachment filtering
    - Test SynthesizePhase sufficiency check logic
    - Files: `server/src/test/kotlin/com/assistant/server/agent/ba/phases/CollectPhaseTest.kt`, `ExpandPhaseTest.kt`, `VisualizePhaseTest.kt`, `SynthesizePhaseTest.kt`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 6. Implement BAAgentConfig DSL definition
  - [x] 6.1 Create `BAAgentConfig` with `buildBAPhaseConfig()` function
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentConfig.kt`
    - Wire 4 phases (collect, expand, visualize, synthesize) with entry/exit conditions
    - Configure limits: maxTotalDuration=90s, maxToolCalls=50, maxIterations=3, maxConcurrentTools=5
    - Configure error strategies per tool (fetchJiraDetails=RETRY, others=SKIP)
    - Set loopbackTarget="expand" on synthesize phase
    - _Requirements: 3.1, 3.5, 3.6, 11.1, 11.2_

- [x] 7. Implement MasterPromptBuilder and PromptTruncator
  - [x] 7.1 Create `MasterPromptBuilder` class
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Build sections in order: Role instruction → Context → Template structure → Output format → Diagram instructions (BRD/FSD only)
    - Use KB records as primary data source (skip raw data when KB exists)
    - Add source attribution markers per section (ticket ID + slot name)
    - Delegate to `PromptTruncator` when over limit
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 7.2 Create `PromptTruncator` class
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/PromptTruncator.kt`
    - Progressive truncation order: (1) linked ticket details → summaries, (2) attachment previews → 500 chars, (3) comment summaries → 200 chars
    - Never truncate: root ticket data, role instruction, template structure
    - _Requirements: 5.5_

  - [x] 7.3 Write property test for Master Prompt size limit invariant
    - **Property 6: Master Prompt size limit invariant**
    - **Validates: Requirements 5.1**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptPropertyTest.kt`

  - [x] 7.4 Write property test for Master Prompt section ordering
    - **Property 7: Master Prompt section ordering**
    - **Validates: Requirements 5.2**
    - Add to `MasterPromptPropertyTest.kt`

  - [x] 7.5 Write property test for KB-first data source priority
    - **Property 8: KB-first data source priority**
    - **Validates: Requirements 5.3**
    - Add to `MasterPromptPropertyTest.kt`

  - [x] 7.6 Write property test for source attribution
    - **Property 9: Master Prompt source attribution**
    - **Validates: Requirements 5.4**
    - Add to `MasterPromptPropertyTest.kt`

  - [x] 7.7 Write property test for progressive truncation preserving protected sections
    - **Property 10: Progressive truncation preserves protected sections**
    - **Validates: Requirements 5.5**
    - Add to `MasterPromptPropertyTest.kt`

- [x] 8. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement BADocumentAgent and data models
  - [x] 9.1 Create BA-specific data models
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/models/BAAgentModels.kt`
    - Define: `CollectionStrategyConfig`, `MasterPromptResult`, `BAAgentPayload` (constants), `RelevanceScore`, `AgentPipelineMetrics`, `PhaseMetric`
    - All `@Serializable` with `encodeDefaults = true` convention
    - _Requirements: 5.6, 10.1, 10.2_

  - [x] 9.2 Create `BADocumentAgent` implementing `GenericAgent`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BADocumentAgent.kt`
    - Constructor: toolRegistry, memory, engineFactory, orchestratorBackend, progressReporter, strategyFactory, promptBuilder
    - `onStart()`: extract ticketId/docType from payload, select strategy, init memory, register tools
    - `execute()`: delegate to `orchestratorBackend.runThinkingLoop()`
    - `onComplete()`: log metrics (duration, tool calls, memory size, compression ratio)
    - `getAgentType()` returns `"ba-document"`
    - _Requirements: 3.1, 3.7, 7.1, 10.1, 10.2_

  - [x] 9.3 Write property test for BA Agent reasoning log cap
    - **Property 11: BA Agent reasoning log cap**
    - **Validates: Requirements 7.6**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/state/BAAgentStatePropertyTest.kt`

  - [x] 9.4 Write unit tests for BADocumentAgent lifecycle
    - Test `onStart` initializes memory and selects correct strategy per docType
    - Test `onComplete` logs expected metrics
    - Test `execute` delegates to orchestrator
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/BADocumentAgentTest.kt`
    - _Requirements: 3.1, 7.1, 10.1_

- [x] 10. Implement AgentJobExecutorBridge and progress mapping
  - [x] 10.1 Create `AgentJobExecutorBridge`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/integration/AgentJobExecutorBridge.kt`
    - Build `AgentInput` with ticketId, docType, jobId in payload
    - Create `DocGenProgressAdapter` wrapping tracker
    - Get `BADocumentAgent` from `AgentRegistry`
    - Execute agent, return masterPrompt from `AgentOutput.result`
    - On unrecoverable error, throw to trigger JobExecutor fallback
    - _Requirements: 9.1, 9.3, 9.6_

  - [x] 10.2 Create progress phase label mapping in `DocGenProgressAdapter`
    - Map agent phases: collect→AGGREGATING_DATA, expand→AGGREGATING_DATA, visualize→AGGREGATING_DATA, synthesize→GENERATING_DOCUMENT
    - Ensure no unmapped or null labels
    - _Requirements: 9.7_

  - [x] 10.3 Write property test for progress phase label mapping
    - **Property 12: Progress phase label mapping**
    - **Validates: Requirements 9.7**
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/progress/ProgressMappingPropertyTest.kt`

- [x] 11. Integrate with JobExecutor via feature flag
  - [x] 11.1 Modify `JobExecutor.execute()` to check `agent_pipeline_enabled` setting
    - When enabled: replace `aggregateData` + `buildDocPrompt` with `AgentJobExecutorBridge.generate()`
    - When disabled: existing pipeline unchanged (backward compatibility)
    - On agent pipeline failure: fall back to existing pipeline with warning log
    - _Requirements: 9.1, 9.2, 9.3, 9.6_

  - [x] 11.2 Add `agent_pipeline_enabled` setting to `SettingsRepository`
    - Default: `false` (disabled)
    - _Requirements: 9.1, 9.2_

  - [x] 11.3 Write unit tests for feature flag routing and fallback
    - Test enabled → uses agent pipeline
    - Test disabled → uses existing pipeline
    - Test agent failure → falls back to existing pipeline
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/integration/AgentJobExecutorBridgeTest.kt`
    - _Requirements: 9.1, 9.2, 9.6_

- [x] 12. Register BADocumentAgent in Koin module
  - [x] 12.1 Create `BAAgentModule` for Koin registration
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt`
    - Register `BADocumentAgent` factory in `AgentRegistry` with type `"ba-document"`
    - Register `AgentJobExecutorBridge` as singleton
    - Register all 6 BA tools with their dependencies (JiraClient, KBRepository, VectorStore)
    - Register `MasterPromptBuilder` as singleton
    - Register `CollectionStrategy` factories for BRD, FSD, Slides
    - Wire into existing `ServerModule.kt` includes
    - _Requirements: 9.1, 9.4_

- [x] 13. Create custom Arb generators for property tests
  - [x] 13.1 Create `BAAgentArbitraries.kt` with all custom generators
    - File: `server/src/test/kotlin/com/assistant/server/agent/ba/generators/BAAgentArbitraries.kt`
    - Implement: `Arb.jiraContextMemory()`, `Arb.collectionStrategy()`, `Arb.linkedTicketSet()`, `Arb.attachmentSet()`, `Arb.issueMetadata()`, `Arb.reasoningLogEntries(n)`, `Arb.agentPhaseName()`
    - _Requirements: all property tests_

- [x] 14. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All code assumes the Generic Agent Framework (`generic-agent-framework` spec) is already implemented
- The BA Agent only provides domain-specific code — zero framework modifications
