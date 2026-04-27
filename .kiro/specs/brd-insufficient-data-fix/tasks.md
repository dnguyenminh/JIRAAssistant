# Implementation Plan: BRD Insufficient Data Fix

## Overview

Fix BRD generation producing "⚠️ Insufficient data" for 4/7 sections by expanding data serialization, adding data mapping instructions for all 7 sections, improving section completion instructions, expanding PromptSectionBuilder, increasing prompt budget, and adding fallback logic when linkedTicketAnalyses is empty.

Changes span two modules:
- **shared** (`BrdPromptSections.kt`, `BrdPromptMappingInstructions.kt`, new helper files)
- **server** (`PromptSectionBuilder.kt`, `PromptSectionHelpers.kt`, `PromptAssemblyLogic.kt`, `JobExecutor.kt`)

## Tasks

- [x] 1. Expand BrdPromptSections data serialization (shared module)
  - [x] 1.1 Create `BrdPromptSectionsTechnical.kt` with helper functions for technicalDetails, diagrams serialization
    - Create file `shared/src/commonMain/kotlin/com/assistant/document/BrdPromptSectionsTechnical.kt`
    - Implement `StringBuilder.appendTechnicalDetails(details: TechnicalDetails)` — serialize apiSpecifications, databaseChanges, externalIntegrations
    - Implement `StringBuilder.appendApiSpecifications(specs: List<ApiSpecification>)` — format "API Specifications: method path: description"
    - Implement `StringBuilder.appendDatabaseChanges(changes: List<DatabaseChange>)` — format "Database Changes: operationType tableName: description"
    - Implement `StringBuilder.appendExternalIntegrations(integrations: List<ExternalIntegration>)` — format "External Integrations: serviceName (protocol): description"
    - Implement `StringBuilder.appendDiagrams(diagrams: List<DiagramData>)` — format "Diagrams: [type] title"
    - Skip empty lists gracefully (no output for empty collections)
    - _Requirements: 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 Expand `appendMainTicketData()` in `BrdPromptSections.kt` to include all KBRecord fields
    - Add calls to `appendTechnicalDetails(ticket.technicalDetails)` after `appendAcceptanceCriteria`
    - Add call to `appendDiagrams(ticket.diagrams)`
    - Add `appendLine("Requirement Summary: ${ticket.requirementSummary}")` when not blank
    - Add `appendLine("Affected Modules: ...")` when `affectedModules` is not empty
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.3 Expand `appendLinkedTicketsData()` in `BrdPromptSections.kt` to include expanded fields
    - Add `asIsState`, `toBeState` serialization for each linked ticket (when not blank)
    - Add `appendAcceptanceCriteria(ticket)` call for each linked ticket
    - Add `appendTechnicalDetails(ticket.technicalDetails)` call for each linked ticket
    - _Requirements: 2.1_

  - [ ]* 1.4 Write property tests for KBRecord serialization completeness (Property 1, Property 2)
    - **Property 1: KBRecord serialization completeness — Main ticket**
    - **Property 2: Linked ticket serialization completeness**
    - Create `shared/src/jvmTest/kotlin/com/assistant/document/BrdPromptSerializationPropertyTest.kt`
    - Use Kotest Arb generators for KBRecord with random non-empty fields
    - Verify output contains ALL non-empty fields from KBRecord
    - **Validates: Requirements 1.1-1.5, 2.1**

- [x] 2. Add data mapping instructions for all 7 BRD sections (shared module)
  - [x] 2.1 Add Revision History mapping in `BrdPromptMappingInstructions.kt`
    - Implement `appendRevisionHistoryMapping()` — map ticketId, timestamp, source ticket IDs, sprint metadata
    - Add call in `appendDataMappingInstructions()` before existing mappings
    - _Requirements: 3.1_

  - [x] 2.2 Add Acronyms mapping in `BrdPromptMappingInstructions.kt`
    - Implement `appendAcronymsMapping()` — map technicalDetails, extractedRequirements, businessSummary, attachment content
    - Add call in `appendDataMappingInstructions()` after Project Overview
    - _Requirements: 3.2_

  - [x] 2.3 Add Sign Off mapping in `BrdPromptMappingInstructions.kt`
    - Implement `appendSignOffMapping()` — map dependencies.blockingIssues, linked ticket assignees, sprintMetadata
    - Add call in `appendDataMappingInstructions()` after Project Requirements
    - _Requirements: 3.3_

  - [x] 2.4 Expand existing Project Overview, Existing Processes, Project Requirements, and Appendix mappings
    - Project Overview: add toBeState → In Scope, dependencies.externalDependencies + technicalDetails → Out of Scope, linked ticket assignees → Contributors
    - Existing Processes: add rawComments → Problems, attachment content → Screenshots
    - Project Requirements: add technicalDetails.apiSpecifications → Functional Requirements, databaseChanges → Data Requirements, linked ticket acceptanceCriteria → cross-ticket requirements
    - Appendix: add diagrams → Mock-ups, technicalDetails → Business Rules, source ticket IDs → Document References
    - _Requirements: 3.4, 3.5, 3.6, 3.7_

  - [ ]* 2.5 Write unit tests for data mapping instructions coverage
    - Verify `appendDataMappingInstructions()` output contains mapping text for all 7 BRD sections
    - Verify each section has at least one USE directive
    - Create test in `shared/src/jvmTest/kotlin/com/assistant/document/BrdPromptMappingInstructionsTest.kt`
    - **Validates: Requirements 3.1-3.7**

- [x] 3. Improve section completion instructions (shared module)
  - [x] 3.1 Expand `appendSectionCompletionRules()` in `BrdPromptSections.kt`
    - Add instruction: check ALL data sources before marking "Insufficient data" (Req 6.1)
    - Add instruction: infer from indirect data — Revision History from metadata, Acronyms from technical terms, Sign Off from stakeholders (Req 6.2)
    - Add instruction: each section MUST have at least 3 lines of real content, mark inferred content with [INFERRED] tag (Req 6.3)
    - Add instruction: use linked ticket comments as supplementary data source (Req 6.4)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [ ]* 3.2 Write unit tests for section completion instructions
    - Verify output contains all 4 new instruction strings
    - Verify "[INFERRED]" tag instruction is present
    - Verify "Insufficient data" last-resort instruction is present
    - **Validates: Requirements 6.1-6.4**

- [x] 4. Checkpoint — Ensure shared module compiles
  - Compile shared module: `./gradlew :shared:compileKotlinJvm`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Expand PromptSectionBuilder and helpers (server module)
  - [x] 5.1 Expand `appendKbFields()` in `PromptSectionHelpers.kt` to include all KBRecord fields
    - Add dependencies serialization (blocking, related, external) via `appendKbDependencies(kb)`
    - Add technicalDetails serialization (apiSpecifications, databaseChanges, externalIntegrations) via `appendKbTechnicalDetails(kb)`
    - Add diagrams serialization via `appendKbDiagrams(kb)`
    - Add affectedModules serialization when not empty
    - Skip empty fields gracefully
    - _Requirements: 4.1_

  - [x] 5.2 Expand `appendTicketRaw()` in `PromptSectionHelpers.kt` to include metadata fields
    - Add status, priority, labels, components, fixVersions from StructuredTicketContent (when available in allTickets)
    - Add relationship type annotation between ticket and root (from ticketRelationships)
    - _Requirements: 4.2, 4.3_

  - [x] 5.3 Expand `buildRootRaw()` in `PromptSectionBuilder.kt` to include root ticket metadata
    - Add status, priority, labels, components from StructuredTicketContent (lookup in allTickets)
    - _Requirements: 4.2_

  - [x] 5.4 Expand `buildTicketsRaw()` to include comments for depth-1 tickets
    - Ensure comments from rawComments are included for each depth-1 ticket
    - Include relationship type for each ticket
    - _Requirements: 4.3_

  - [ ]* 5.5 Write property tests for PromptSectionBuilder (Property 5, Property 6)
    - **Property 5: Root raw data includes StructuredTicketContent metadata**
    - **Property 6: Depth-1 tickets include expanded fields**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/document/prompt/PromptSectionBuilderPropertyTest.kt`
    - **Validates: Requirements 4.2, 4.3**

- [x] 6. Increase prompt budget and improve truncation (server module)
  - [x] 6.1 Increase prompt budget in `JobExecutor.kt` from 100,000 to 200,000 chars
    - Change `100_000` to `200_000` in `buildDocPrompt()` for enriched path
    - _Requirements: 5.1_

  - [x] 6.2 Improve truncation annotation in `PromptAssemblyLogic.kt`
    - Update `truncationAnnotation()` to include: keptFullTickets, keptSummaryTickets, removedTickets, removedChunks, originalSize, budget
    - Format: "[TRUNCATED: Giữ lại {N1} tickets đầy đủ, {N2} tickets chỉ summary, cắt {N3} tickets và {M} attachment chunks. Tổng data gốc: {originalSize} chars, budget: {budget} chars]"
    - _Requirements: 5.3_

  - [x] 6.3 Verify skeleton (role, template, mapping, instructions) is never truncated
    - Confirm in `PromptAssembler.buildPrompt()` that budget is calculated as `maxPromptChars - skeleton.headerSize - skeleton.footerSize`
    - Add comment documenting this invariant
    - _Requirements: 5.4_

  - [ ]* 6.4 Write property tests for truncation (Property 7, Property 8, Property 9)
    - **Property 7: Truncation preserves priority ordering**
    - **Property 8: Truncation annotation chứa thông tin chi tiết**
    - **Property 9: Skeleton sections không bao giờ bị truncate**
    - Create `server/src/jvmTest/kotlin/com/assistant/server/document/prompt/PromptAssemblyPropertyTest.kt`
    - **Validates: Requirements 5.2, 5.3, 5.4**

- [x] 7. Checkpoint — Ensure server module compiles
  - Compile server module: `./gradlew :server:compileKotlinJvm`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Add fallback logic when linkedTicketAnalyses is empty
  - [x] 8.1 Create `BrdPromptSectionsFallback.kt` with fallback serialization functions
    - Create file `shared/src/commonMain/kotlin/com/assistant/document/BrdPromptSectionsFallback.kt`
    - Implement `StringBuilder.appendFallbackLinkedTickets(allTickets, rootTicketId)` — serialize raw ticket data (summary, description, status, priority) for non-root tickets
    - Add annotation "[Note: Ticket {ticketId} chưa có deep analysis — sử dụng raw Jira data. Recommend chạy deep analysis cho ticket này để có dữ liệu đầy đủ hơn.]"
    - Implement `StringBuilder.appendEnrichedComments(rawComments)` — serialize comments from all tickets with format "[Comment by {author} on {date} for {ticketId}]: {body}"
    - _Requirements: 7.1, 7.4, 2.2, 2.3_

  - [x] 8.2 Integrate fallback logic into prompt building flow
    - In server module: update `PromptSectionBuilder.buildTicketsRaw()` to use fallback when ticket has no KBRecord
    - In server module: update `PromptSectionBuilder.buildRootRaw()` to include comments from ALL tickets (not just root)
    - Ensure `PromptSectionBuilder` includes attachment content from all tickets regardless of KBRecord presence
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 8.3 Wire fallback into Basic Prompt Path (`BrdPromptSections.appendContextData`)
    - Since `BrdPromptSections` is in shared module and cannot import `EnrichedContext` from server, pass fallback data through `GenerationContext` interface or handle in `BrdPromptBuilder`
    - Alternative: add fallback parameters to `appendContextData()` for raw ticket data and comments
    - Ensure backward compatibility — basic path still works without enriched data
    - _Requirements: 2.2, 7.1_

  - [ ]* 8.4 Write property tests for fallback logic (Property 3, Property 4, Property 10, Property 11)
    - **Property 3: Fallback to raw ticket data khi linkedTicketAnalyses rỗng**
    - **Property 4: Comments inclusion cho tất cả tickets**
    - **Property 10: Attachment inclusion từ tất cả tickets**
    - **Property 11: Fallback annotation cho tickets chưa analyze**
    - Create `shared/src/jvmTest/kotlin/com/assistant/document/BrdPromptFallbackPropertyTest.kt`
    - **Validates: Requirements 2.2, 2.3, 7.1, 7.2, 7.3, 7.4**

- [x] 9. Final checkpoint — Ensure all modules compile and tests pass
  - Compile all: `./gradlew :shared:compileKotlinJvm :server:compileKotlinJvm`
  - Run tests: `./gradlew :shared:jvmTest :server:jvmTest`
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each module's changes
- Property tests validate universal correctness properties from the design document
- The design is backward-compatible — all changes are additive, no existing behavior is removed
- Shared module cannot import server module classes — fallback logic that needs `EnrichedContext` must live in server module or use interface abstraction
- File size limit: ≤200 lines per file (Kotlin code standards) — new helper files are created to respect this
