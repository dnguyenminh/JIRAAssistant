# Implementation Plan: Prompt Curation Pipeline (Integrated into Agent Pipeline)

## Overview

This plan integrates prompt curation logic directly INTO the existing BADocumentAgent pipeline — specifically into `MasterPromptBuilder`, `MasterPromptSections`, and `ExpandPhase`. There is NO separate `CurationPipeline` class or separate path in `JobExecutor`. Instead:

- **MasterPromptSections.buildContext()** classifies tickets as AS-IS/TO-BE/OUTDATED and builds sections accordingly
- **MasterPromptBuilder** integrates CommentSummarizer, AttachmentCurator, and BudgetEnforcer when assembling the prompt
- **ExpandPhase** uses TemporalClassifier to classify linked tickets during collection
- **CollectionStrategy** incorporates comment summarization and attachment curation thresholds

Additionally: `GeminiCliAgent` TIMEOUT_MS increases 120s → 240s, `JobExecutor` MAX_RETRIES decreases 2 → 1.

Code follows Kotlin standards: max 200 lines/file, max 20 lines/function, models in separate packages, SOLID principles.

## Tasks

- [x] 1. Define curation data models and configuration constants
  - [x] 1.1 Create `CurationConfig` object with all budget/threshold constants
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/CurationConfig.kt`
    - Define: MAX_PROMPT_CHARS=80000, TARGET_MIN_CHARS=50000, MAX_COMMENT_CHARS_PER_TICKET=2000, MAX_ATTACHMENT_PREVIEW_CHARS=3000, MAX_REQUIREMENT_DOC_PREVIEW_CHARS=5000, MAX_TOTAL_ATTACHMENT_CHARS=15000, MAX_MCP_LOOKUPS=20, COMMENT_THRESHOLD=10, GEMINI_TIMEOUT_MS=240000L, MAX_RETRIES=1, JOB_FAIL_FAST_MS=280000L
    - _Requirements: 6.1, 6.2, 7.1, 7.2, 7.5_

  - [x] 1.2 Create classification models: `TemporalRelation`, `ContentClassification`, `TicketClassification`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/models/ClassificationModels.kt`
    - TemporalRelation: OLDER, NEWER, CONCURRENT
    - ContentClassification: AS_IS, TO_BE, OUTDATED
    - TicketClassification: ticketId, temporalRelation, contentClassification, supersededBy
    - _Requirements: 2.1, 2.3, 2.4, 2.5_

  - [x] 1.3 Create `CommentSummary` data class
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/models/CommentModels.kt`
    - Fields: decisions, clarifications, blockers, recentComments, botSummary, totalChars
    - _Requirements: 4.1, 4.5_

  - [x] 1.4 Create `CuratedAttachment` data class
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/models/CommentModels.kt`
    - Fields: filename, ticketId, preview, priority, isRequirementDoc
    - _Requirements: 5.1_

  - [x] 1.5 Create `CurationMetrics` data class for observability logging
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/models/CurationMetrics.kt`
    - Fields: originalContextSizeChars, curatedContextSizeChars, ticketsAsIs, ticketsToBe, ticketsOutdated, commentsSummarized, attachmentsCurated, curationTimeMs
    - _Requirements: 9.2, 9.3_

- [x] 2. Implement TemporalClassifier component
  - [x] 2.1 Create `TemporalClassifier` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/TemporalClassifier.kt`
    - Method: `classify(rootTicketCreatedDate, linkedTicketCreatedDate, linkedTicketStatus, linkedTicketResolvedDate, hasConflictingRequirements): TicketClassification`
    - _Requirements: 2.1_

  - [x] 2.2 Implement `DefaultTemporalClassifier`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/DefaultTemporalClassifier.kt`
    - Compare creation dates to determine OLDER/NEWER/CONCURRENT
    - Use status (Closed/Done) as secondary signal for AS-IS classification
    - Detect requirement conflicts for OUTDATED classification
    - Safe default: classify as TO-BE when uncertain
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 2.3 Write property test for temporal classification correctness
    - **Property 4: Temporal Classification Correctness**
    - **Validates: Requirements 2.1, 2.3, 2.4, 2.5**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/TemporalClassifierPropertyTest.kt`
    - Verify: older+non-conflicting→AS_IS, older+conflicting→OUTDATED, newer/concurrent→TO_BE

  - [x] 2.4 Write unit tests for TemporalClassifier edge cases
    - Test: same-day tickets, missing dates, null resolution dates, all status combinations
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/TemporalClassifierTest.kt`
    - _Requirements: 2.2, 2.6_

- [x] 3. Implement CommentSummarizer component
  - [x] 3.1 Create `CommentSummarizer` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/CommentSummarizer.kt`
    - Method: `summarize(comments: List<FullComment>, hasKbRecord: Boolean): CommentSummary`
    - _Requirements: 4.1_

  - [x] 3.2 Implement `DefaultCommentSummarizer`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/DefaultCommentSummarizer.kt`
    - Skip entirely if hasKbRecord is true (return empty CommentSummary)
    - If ≤10 comments → include all (no summarization)
    - If >10 → extract decisions/clarifications/blockers + keep 3 most recent substantive
    - Deduplicate bot comments (ScriptRunner, status bots) → count + date range
    - Enforce ≤2000 chars output per ticket
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 3.3 Write property test for comment summarization correctness
    - **Property 7: Comment Summarization Correctness**
    - **Validates: Requirements 4.1, 4.3, 4.4, 4.5**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/CommentSummarizerPropertyTest.kt`
    - Verify: output ≤2000 chars, preserves 3 most recent substantive, bot comments replaced with count+range

  - [x] 3.4 Write unit tests for bot comment detection patterns
    - Test: ScriptRunner patterns, status update bots, Jira automation comments
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/CommentSummarizerTest.kt`
    - _Requirements: 4.3_

- [x] 4. Implement AttachmentCurator component
  - [x] 4.1 Create `AttachmentCurator` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/AttachmentCurator.kt`
    - Method: `curate(rootAttachments, linkedAttachments, kbReferencedFilenames): List<CuratedAttachment>`
    - _Requirements: 5.1_

  - [x] 4.2 Implement `DefaultAttachmentCurator`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/DefaultAttachmentCurator.kt`
    - Exclude attachments referenced in KB records
    - Preview: first 3000 chars (5000 for BRD/FRD/FSD/requirement docs)
    - Priority: root > linked (by depth)
    - Total budget: 15000 chars max, truncate from deepest first
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 4.3 Write property test for attachment curation correctness
    - **Property 8: Attachment Curation Correctness**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/AttachmentCuratorPropertyTest.kt`
    - Verify: preview ≤3000 (≤5000 for requirement docs), KB-referenced excluded, root priority > linked, total ≤15000

- [x] 5. Checkpoint — Ensure all curation component tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Integrate TemporalClassifier into ExpandPhase
  - [x] 6.1 Add temporal classification during linked ticket collection in `ExpandPhase`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/ExpandPhase.kt`
    - After scoring relevance, classify each linked ticket as OLDER/NEWER/CONCURRENT using TemporalClassifier
    - Store `TicketClassification` in StructuredMemory alongside ticket data (new memory slot: `ticketClassifications`)
    - Pass ticket creation dates from fetched Jira details to TemporalClassifier
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

  - [x] 6.2 Update `BAAgentConfig` memory schema to include `ticketClassifications` slot
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentConfig.kt`
    - Add `mapSlot("ticketClassifications", 20)` to memorySchema
    - _Requirements: 2.1_

  - [x] 6.3 Write unit tests for ExpandPhase temporal classification integration
    - Verify: linked tickets get classified and stored in memory
    - Verify: classification data is accessible by MasterPromptSections
    - File: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/phases/ExpandPhaseClassificationTest.kt`
    - _Requirements: 2.1, 2.5_

- [x] 7. Integrate curation into MasterPromptSections.buildContext()
  - [x] 7.1 Refactor `MasterPromptSections.buildContext()` to build AS-IS/TO-BE/OUTDATED sections
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptSections.kt`
    - Read `ticketClassifications` from memory to determine each ticket's classification
    - Build TO-BE section first (root ticket + NEWER/CONCURRENT linked tickets)
    - Build AS-IS section second (OLDER resolved tickets with non-conflicting requirements)
    - Build OUTDATED metadata section (one-line references only: ticketId + superseded-by)
    - Preserve existing `shouldSkipRawEntry()` KB-first logic within each section
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 7.2 Extract section building into helper: `MasterPromptContextBuilder`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptContextBuilder.kt`
    - Separate class to keep MasterPromptSections under 200 lines
    - Methods: `buildToBeSection(memory, kbSources)`, `buildAsIsSection(memory, kbSources)`, `buildOutdatedMetadata(memory)`
    - Each method reads ticketClassifications from memory and filters entries accordingly
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 7.3 Write property test for section placement correctness
    - **Property 5: Section Placement Correctness**
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.6**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptContextBuilderPropertyTest.kt`
    - Verify: AS-IS tickets only in AS-IS section, TO-BE only in TO-BE, OUTDATED only as one-line refs

  - [x] 7.4 Write property test for TO-BE before AS-IS ordering
    - **Property 6: TO-BE Before AS-IS Ordering**
    - **Validates: Requirements 3.5**
    - Add to `MasterPromptContextBuilderPropertyTest.kt`
    - Verify: TO-BE section appears before AS-IS section in final prompt string

- [x] 8. Integrate CommentSummarizer into MasterPromptBuilder
  - [x] 8.1 Add CommentSummarizer dependency to `MasterPromptBuilder`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Add constructor parameter: `private val commentSummarizer: CommentSummarizer?`
    - When building prompt, summarize comments from memory (>10 per ticket → condensed ≤2000 chars)
    - Deduplicate bot comments (ScriptRunner, status bots)
    - Skip comment processing when KB record exists for that ticket
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 8.2 Create `CommentCurationStep` helper to process comments before section building
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/CommentCurationStep.kt`
    - Reads raw comments from memory, applies CommentSummarizer per ticket
    - Replaces raw comment entries in memory with summarized versions (or stores in separate slot)
    - _Requirements: 4.1, 4.5, 4.6_

  - [x] 8.3 Write property test for KB-first data selection (comments skipped when KB exists)
    - **Property 1: KB-First Data Selection**
    - **Validates: Requirements 1.1, 1.2, 1.5, 4.6**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilderPropertyTest.kt`
    - Verify: tickets with KB records use only KB fields, no raw description/comments

- [x] 9. Integrate AttachmentCurator into MasterPromptBuilder
  - [x] 9.1 Add AttachmentCurator dependency to `MasterPromptBuilder`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Add constructor parameter: `private val attachmentCurator: AttachmentCurator?`
    - When building prompt, curate attachments: preview only (3000 chars max, 5000 for requirement docs)
    - Exclude attachments already referenced in KB records
    - Total attachment budget: 15000 chars
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 9.2 Create `AttachmentCurationStep` helper to process attachments before section building
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/AttachmentCurationStep.kt`
    - Reads raw attachment data from memory, applies AttachmentCurator
    - Replaces raw attachment entries with curated previews
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 10. Integrate BudgetEnforcer into MasterPromptBuilder
  - [x] 10.1 Create `BudgetEnforcer` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/BudgetEnforcer.kt`
    - Method: `enforce(rawPrompt: String, maxChars: Int, protectedSections: Set<String>): BudgetResult`
    - _Requirements: 6.1_

  - [x] 10.2 Implement `DefaultBudgetEnforcer`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/DefaultBudgetEnforcer.kt`
    - Progressive truncation order: (1) deeper ticket details, (2) AS-IS section details, (3) attachment previews, (4) comment summaries
    - Never truncate: root ticket KB record, TO-BE section, prompt skeleton (role, template, instructions, diagram instructions)
    - Append truncation annotation when truncation occurs
    - _Requirements: 6.1, 6.3, 6.4, 6.5_

  - [x] 10.3 Replace `PromptTruncator` usage in `MasterPromptBuilder.truncateIfNeeded()` with BudgetEnforcer
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Add constructor parameter: `private val budgetEnforcer: BudgetEnforcer?`
    - When budgetEnforcer is available, use it instead of PromptTruncator
    - Enforce 50K-80K char budget (override strategy.maxPromptChars when curation is active)
    - Protected content: root ticket, TO-BE section, prompt skeleton
    - _Requirements: 6.1, 6.3, 6.4_

  - [x] 10.4 Write property test for budget enforcement invariant
    - **Property 9: Budget Enforcement Invariant**
    - **Validates: Requirements 6.1**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/BudgetEnforcerPropertyTest.kt`
    - Verify: final assembled prompt ≤80000 chars for any input

  - [x] 10.5 Write property test for progressive truncation correctness
    - **Property 10: Progressive Truncation Correctness**
    - **Validates: Requirements 6.3, 6.5**
    - Add to `BudgetEnforcerPropertyTest.kt`
    - Verify: truncation order respected, annotation appended when truncation occurs

- [x] 11. Checkpoint — Ensure all integration tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Integrate McpToolRegistrar into MasterPromptBuilder
  - [x] 12.1 Create `McpToolRegistrar` interface
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/McpToolRegistrar.kt`
    - Methods: `buildToolBlock(referenceOnlyTickets: List<String>): String`, `isToolUseSupported(agentType: String): Boolean`
    - _Requirements: 10.2, 10.3, 10.4_

  - [x] 12.2 Implement `DefaultMcpToolRegistrar`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/DefaultMcpToolRegistrar.kt`
    - Register kb_search and kb_read tools with ticketId + fieldName params
    - Only for agents supporting function calling (GeminiCliAgent)
    - Max 20 lookups per generation (bounded)
    - Fallback: return empty string if agent doesn't support tools
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 12.3 Add McpToolRegistrar to `MasterPromptBuilder` prompt assembly
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Add constructor parameter: `private val mcpToolRegistrar: McpToolRegistrar?`
    - Include MCP tool block in prompt for reference-only tickets (tickets with brief summary only)
    - Only include when agent supports function calling
    - _Requirements: 10.2, 10.3, 10.4_

  - [x] 12.4 Write unit tests for McpToolRegistrar
    - Test: tool-capable agent gets tool block, non-tool agent gets empty string, tool block format
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/McpToolRegistrarTest.kt`
    - _Requirements: 10.3, 10.4_

- [x] 13. Update CollectionStrategy with curation budget
  - [x] 13.1 Update `CollectionStrategy` interface with curation-aware budget
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/CollectionStrategy.kt`
    - Add property: `val curationEnabled: Boolean` (default false for backward compat)
    - When curationEnabled=true, `maxPromptChars` returns CurationConfig.MAX_PROMPT_CHARS (80000) instead of strategy-specific value (60000)
    - _Requirements: 6.1, 6.2_

  - [x] 13.2 Update `BrdCollectionStrategy` and `FsdCollectionStrategy` to support curation budget
    - Files: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/strategy/BrdCollectionStrategy.kt`, `FsdCollectionStrategy.kt`
    - Override maxPromptChars to return 80000 when curation is enabled
    - _Requirements: 6.1, 6.2_

- [x] 14. Update GeminiCliAgent timeout and JobExecutor retry
  - [x] 14.1 Update `GeminiCliAgent` TIMEOUT_MS from 120s to 240s
    - File: `server/src/jvmMain/kotlin/com/assistant/server/ai/GeminiCliAgent.kt`
    - Change TIMEOUT_MS constant to 240_000L (already done — verify)
    - _Requirements: 7.1_

  - [x] 14.2 Update `JobExecutor` MAX_RETRIES to 1 when agent pipeline is active
    - File: `server/src/jvmMain/kotlin/com/assistant/server/jobs/JobExecutor.kt`
    - When agent_pipeline_enabled=true, use MAX_RETRIES=1 (2 total attempts)
    - Add fail-fast logic at 280s total job time
    - Log prompt size, elapsed time, and attempt number on timeout
    - _Requirements: 7.2, 7.3, 7.5_

  - [x] 14.3 Write unit tests for timeout and retry changes
    - Test: 240s timeout applied, fail-fast at 280s, max 1 retry (2 total attempts)
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/GeminiTimeoutTest.kt`
    - _Requirements: 7.1, 7.2, 7.5_

- [x] 15. Add observability logging
  - [x] 15.1 Add curation metrics logging to `MasterPromptBuilder`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilder.kt`
    - Log: AS-IS/TO-BE/OUTDATED ticket counts, comment summarization count, prompt size before/after curation
    - Log warning if post-curation prompt exceeds 80K chars
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [x] 15.2 Add curation timing metrics to `BADocumentAgent.execute()`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BADocumentAgent.kt`
    - Log curation step duration (time spent in MasterPromptBuilder curation steps)
    - Log total prompt size in final output metrics
    - _Requirements: 9.1, 9.3_

- [x] 16. Wire curation components into BADocumentAgent via dependency injection
  - [x] 16.1 Update `BADocumentAgent` constructor to accept curation-enabled `MasterPromptBuilder`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BADocumentAgent.kt`
    - The existing `promptBuilder: MasterPromptBuilder` parameter already supports this — just ensure the injected instance has curation components wired
    - _Requirements: 8.1_

  - [x] 16.2 Update Koin module to wire curation components into MasterPromptBuilder
    - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/integration/BAAgentModule.kt` (or equivalent DI config)
    - Register: DefaultTemporalClassifier, DefaultCommentSummarizer, DefaultAttachmentCurator, DefaultBudgetEnforcer, DefaultMcpToolRegistrar
    - Inject into MasterPromptBuilder constructor
    - _Requirements: 8.1_

  - [x] 16.3 Add feature flag check: when `prompt_curation_enabled=false`, inject MasterPromptBuilder WITHOUT curation components (null dependencies)
    - Ensures backward compatibility — curation components are optional constructor params
    - When null, MasterPromptBuilder uses existing behavior (no classification, no summarization, PromptTruncator for truncation)
    - _Requirements: 8.2, 8.3, 8.6_

- [x] 17. Property tests for pipeline-level invariants
  - [x] 17.1 Write property test for root ticket and protected content preservation
    - **Property 3: Root Ticket and Protected Content Preservation**
    - **Validates: Requirements 1.4, 6.4**
    - File: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/MasterPromptBuilderPropertyTest.kt`
    - Verify: root ticket KB record always complete, TO-BE section never removed by budget enforcement

  - [x] 17.2 Write property test for KB fallback for missing records
    - **Property 2: KB Fallback for Missing Records**
    - **Validates: Requirements 1.3**
    - Add to `MasterPromptBuilderPropertyTest.kt`
    - Verify: tickets without KB records get summarized raw data (description + top 5 comments)

  - [x] 17.3 Write property test for determinism
    - **Property 11: Determinism**
    - **Validates: Requirements 8.5**
    - Add to `MasterPromptBuilderPropertyTest.kt`
    - Verify: calling buildPrompt() twice with same memory produces identical output (exclude timing)

- [x] 18. Create shared Arb generators for property tests
  - [x] 18.1 Create `CurationArbitraries.kt` with all custom generators
    - File: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/generators/CurationArbitraries.kt`
    - Implement: `arbKBRecord()`, `arbStructuredTicketContent()`, `arbStructuredMemory()`, `arbFullComment()`, `arbAttachmentChunkInfo()`, `arbTicketDates()`, `arbCommentList(size)`
    - Generators should produce varying sizes to stress-test budget enforcement
    - _Requirements: all property tests_

- [x] 19. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- **ARCHITECTURE**: Curation logic is distributed across existing agent components (MasterPromptBuilder, MasterPromptSections, ExpandPhase) — NO separate CurationPipeline class
- **Backward compatibility**: All curation components are optional constructor params (nullable). When null, existing behavior is preserved.
- **Feature flag**: `prompt_curation_enabled` controls whether curation components are injected (via DI) — not a runtime check in MasterPromptBuilder
- Property tests validate universal correctness properties from the design document (11 properties)
- Unit tests validate specific examples and edge cases
- All code follows Kotlin standards: max 200 lines/file, max 20 lines/function, models in separate packages
- Curation component package: `server/src/jvmMain/kotlin/com/assistant/server/document/curation/`
- Modified agent components: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/`
- Modified agent phases: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/`
- Test location: `server/src/jvmTest/kotlin/com/assistant/server/document/curation/` and `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/prompt/`
