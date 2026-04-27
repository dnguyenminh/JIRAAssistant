# Implementation Plan: Deep BRD Generation

## Overview

Cải thiện chất lượng BRD bằng cách thay đổi prompt content gửi cho AI agent. Tất cả thay đổi nằm trong prompt building layer — không thay đổi AgenticLoopRunner, ToolExecutionBridge, hay AiBackendPipelineStrategy. Tạo 3 file mới (AgenticToolDetector, AgenticKbCacheStrategy, AgenticDiagramSections) và sửa 3 file hiện tại (AgenticDataStrategy, AgenticPromptSections, AgenticPromptBuilder).

All source files in: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/`
All test files in: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/`

## Tasks

- [x] 1. Create AgenticToolDetector.kt — Dynamic tool detection from ToolDescriptor list
  - [x] 1.1 Create `AgenticToolDetector.kt` with `DetectedTools` data class and `detectToolCategories()` function
    - Define `DetectedTools` data class with fields: `getIssueTool`, `searchTool`, `analyzeTool`, `getAnalysisTool`, `kbSearchTool`, `kbReadTool`, `kbContextTool`, `kbIngestTool`, `hasKbTools`, `hasJiraTools`, `hasAnalysisTools`
    - Implement `detectToolCategories(tools: List<ToolDescriptor>): DetectedTools` as top-level internal function
    - Sort tools by name first for deterministic ordering, then scan with case-insensitive pattern matching
    - Jira patterns: `"get_issue"`, `"search"` + `"jira"`, `"analyze_ticket"`, `"get_ticket_analysis"`
    - KB patterns: `"kb_search_smart"` (preferred over `"kb_search"`), `"kb_read"`, `"kb_context"`, `"kb_ingest"`, `"kb_write"`
    - Derive boolean flags: `hasKbTools`, `hasJiraTools`, `hasAnalysisTools` from detected tool presence
    - Keep file under 80 lines, each function under 20 lines
    - _Requirements: 3.1, 3.4, 3.5_

  - [x] 1.2 Write property test: Tool detection correctness (Property 3)
    - **Property 3: Tool detection correctness**
    - For any list of ToolDescriptors containing tools with KB/Jira/analysis patterns, `detectToolCategories()` correctly identifies them in corresponding DetectedTools fields using case-insensitive matching
    - Use Kotest `checkAll` with `PropTestConfig(iterations = 100)` and custom `Arb` generators for ToolDescriptor lists mixing KB, Jira, analysis, and unrelated tools
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 2.2, 3.1, 3.4**

  - [x] 1.3 Write property test: Tool detection determinism (Property 4)
    - **Property 4: Tool detection determinism**
    - For any list of ToolDescriptors, `detectToolCategories(tools) == detectToolCategories(tools.shuffled())` for any permutation
    - Use Kotest `checkAll` with shuffled input lists to verify order-independence
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 3.5**

- [x] 2. Create AgenticKbCacheStrategy.kt — KB-first, Jira-fallback prompt instructions
  - [x] 2.1 Create `AgenticKbCacheStrategy.kt` with KB caching prompt logic
    - Implement `StringBuilder.appendKbCacheInstructions(detected: DetectedTools, tools: List<ToolDescriptor>)` as internal extension function
    - Implement private helpers: `appendKbLookupStep(detected)`, `appendJiraFallbackStep(detected)`, `appendKbSaveStep(detected)`
    - KB lookup: instruct AI to call `kb_context` first (if available), then `kb_search_smart` or `kb_search` with ticket ID
    - Jira fallback: if KB data insufficient, call Jira `get_issue` for full data
    - KB save: after Jira call, instruct AI to call `kb_ingest` or `kb_write` to cache result
    - Use actual detected tool names from `DetectedTools` fields — no hardcoded tool names
    - Keep file under 90 lines, each function under 20 lines
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.7_

  - [x] 2.2 Write property test: KB cache prompt completeness (Property 2)
    - **Property 2: KB cache prompt completeness**
    - For any tools list containing at least one KB tool, the output of `appendDataCollectionStrategy()` contains complete KB cache instructions: KB lookup step, data evaluation criteria, Jira fallback step, KB save step, and actual detected KB tool names
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.7**

  - [x] 2.3 Write property test: KB fallback backward compatibility (Property 5)
    - **Property 5: KB fallback backward compatibility**
    - For any tools list with NO KB tools, the output of `appendDataCollectionStrategy()` shall NOT contain KB-specific instructions and SHALL contain direct Jira call instructions
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 2.6, 7.1**

- [x] 3. Create AgenticDiagramSections.kt — Draw.io XML templates and instructions
  - [x] 3.1 Create `AgenticDiagramSections.kt` with diagram prompt sections
    - Implement `StringBuilder.appendDiagramInstructions()` as internal extension function
    - Implement private helpers: `appendSequenceDiagramTemplate()`, `appendClassDiagramTemplate()`, `appendActivityDiagramTemplate()`, `appendDeploymentDiagramTemplate()`, `appendDiagramPlacementRules()`, `appendDiagramFallbackRules()`
    - Each template provides minimal but valid draw.io XML with `<mxGraphModel>` root, unique `id` attributes, valid `source`/`target` on edges
    - Placement rules: Sequence → Business Process/Use Cases, Class → Data Requirements, Activity → Functional Requirements, Deployment → Technical Architecture/System Overview
    - Fallback rule: `[Diagram không khả dụng: thiếu dữ liệu về {topic}]` when data insufficient
    - Keep file under 190 lines, each function under 20 lines
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 3.2 Write property test: Draw.io XML template validity (Property 7)
    - **Property 7: Draw.io XML template validity**
    - For each of the 4 draw.io XML templates in `appendDiagramInstructions()` output, verify: (a) `<mxGraphModel>` as root, (b) every `<mxCell>` has unique `id`, (c) every edge has valid `source`/`target` referencing existing cell IDs
    - Parse XML templates from the output string and validate structure
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 4.2, 4.4, 4.8**

  - [x] 3.3 Write unit tests for diagram sections
    - Verify `appendDiagramInstructions()` output contains all 4 diagram types (Sequence, Class, Activity, Deployment)
    - Verify diagram placement rules map to correct BRD sections
    - Verify fallback text pattern `[Diagram không khả dụng...]` is present
    - Verify each XML template is parseable as valid XML
    - Test file: `DeepBrdGenerationUnitTest.kt`
    - _Requirements: 4.1, 4.4, 4.5, 4.7_

- [x] 4. Checkpoint — Verify new files compile and tests pass
  - Ensure all tests pass with `./gradlew :server:jvmTest --tests "com.assistant.server.agent.ba.subprocess.pipeline.aibackend.*"`, ask the user if questions arise.

- [x] 5. Refactor AgenticDataStrategy.kt — Recursive exploration orchestration with KB cache and diagram refs
  - [x] 5.1 Refactor `AgenticDataStrategy.kt` to replace current 5-step strategy with new orchestration
    - Replace current `appendDataCollectionStrategy(ticketId, getIssueTool, hasAnalyzeTool, hasSearchTool, hasGetAnalysis, tools)` with new signature: `appendDataCollectionStrategy(ticketId: String, tools: List<ToolDescriptor>)`
    - New function orchestrates: call `detectToolCategories(tools)` → `appendKbCacheInstructions(detected, tools)` → `appendRecursiveExplorationInstructions(ticketId, detected, tools)` → `appendFinalWriteStep()`
    - Define constants: `DEPTH_LIMIT = 3`, `MAX_TICKETS = 30`
    - Implement `appendRecursiveExplorationInstructions()` with: visited set tracking, depth-based summarization (depth ≥ 2 = summary only), priority order (parent → blocking → relates-to → subtasks → mentioned IDs), early termination, max tickets limit
    - Implement `appendExplorationPriority()` and `appendDepthGuidelines()` helpers
    - Use actual tool names from `DetectedTools` — no hardcoded names
    - Keep file under 180 lines, each function under 20 lines
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 3.1, 3.2, 3.3_

  - [x] 5.2 Write property test: Recursive exploration prompt completeness (Property 1)
    - **Property 1: Recursive exploration prompt completeness**
    - For any valid ticketId and tools list with at least one Jira tool, the output contains: depth tracking, visited set, attachment reading, priority-based exploration, early termination, depth-based summarization, data priority ordering
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.5, 1.6, 6.1, 6.2**

  - [x] 5.3 Write property test: No hardcoded tool names in prompt (Property 6)
    - **Property 6: No hardcoded tool names in prompt**
    - For any tools list, every tool name referenced in `appendDataCollectionStrategy()` output shall be present in the input tools list. No hardcoded names like `"mcp_jira_get_issue"`
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 3.3**

- [x] 6. Update AgenticPromptSections.kt — Integrate diagram instructions and new data strategy
  - [x] 6.1 Update `buildToolCallingPrompt()` to add `appendDiagramInstructions()` call
    - Add `appendDiagramInstructions()` call after `appendBrdSections(docType)` and before `appendToolTask(ticketId, tools)`
    - Ensure prompt section ordering: BRD structure → Diagram instructions → Data collection strategy
    - _Requirements: 5.1, 5.3_

  - [x] 6.2 Update `appendToolTask()` to use new `appendDataCollectionStrategy()` signature
    - Remove inline tool detection logic (getIssueTool, hasAnalyzeTool, hasSearchTool, hasGetAnalysis variables)
    - Replace call to old `appendDataCollectionStrategy(ticketId, getIssueTool, hasAnalyzeTool, hasSearchTool, hasGetAnalysis, tools)` with new `appendDataCollectionStrategy(ticketId, tools)`
    - Tool detection is now handled inside `AgenticDataStrategy` via `detectToolCategories()`
    - _Requirements: 5.2, 5.4_

  - [x] 6.3 Write property test: Prompt section ordering (Property 8)
    - **Property 8: Prompt section ordering**
    - For any valid ticketId, docType, and non-empty tools list, verify: `indexOf("BRD STRUCTURE") < indexOf("DIAGRAM") < indexOf("DATA COLLECTION")`
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 5.3**

- [x] 7. Update AgenticPromptBuilder.kt — Prompt size management and truncation
  - [x] 7.1 Add `MAX_TOOL_RESULTS_CHARS` constant and `truncateToolResults()` function
    - Define `internal const val MAX_TOOL_RESULTS_CHARS = 80_000`
    - Implement `internal fun truncateToolResults(toolResults: List<String>, maxChars: Int = MAX_TOOL_RESULTS_CHARS): List<String>`
    - Truncation algorithm: if total ≤ max → return as-is; always keep first (main ticket) + last (latest); remove from index 1 upward; insert annotation `"[TRUNCATED: {N} earlier tool results omitted due to prompt size limit]"`
    - _Requirements: 6.3, 6.4_

  - [x] 7.2 Update `buildStatelessContinuation()` to include diagram instructions and use truncation
    - Add `appendDiagramInstructions()` call in continuation prompt building
    - Apply `truncateToolResults()` to tool results before appending collected data
    - Ensure protected sections (system instructions, tool definitions, tool protocol, BRD structure, diagram instructions) are never truncated
    - _Requirements: 5.7, 6.3, 6.4, 6.5_

  - [x] 7.3 Write property test: Truncation preserves size limit and key data (Property 10)
    - **Property 10: Truncation preserves size limit and key data**
    - For any list of 2+ tool result strings exceeding MAX_TOOL_RESULTS_CHARS, verify: (a) combined length ≤ MAX_TOOL_RESULTS_CHARS, (b) first element preserved, (c) last element preserved, (d) truncation annotation included
    - Use Kotest `Arb.string()` generators for tool results of varying sizes
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 6.3, 6.4**

  - [x] 7.4 Write property test: Protected sections invariant (Property 11)
    - **Property 11: Protected sections invariant**
    - For any valid inputs (ticketId, docType, toolResults of any size), `buildStatelessContinuation()` output always contains: system/context instructions, tool definitions, tool protocol, BRD structure, diagram instructions
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 6.5**

  - [x] 7.5 Write property test: Continuation prompt includes diagram instructions (Property 9)
    - **Property 9: Continuation prompt includes diagram instructions**
    - For any valid ticketId, docType, and tool results list, `buildStatelessContinuation()` output contains diagram instruction keywords
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 5.7**

  - [x] 7.6 Write property test: KB tools not excluded by filter (Property 12)
    - **Property 12: KB tools not excluded by filter**
    - For any ToolDescriptor with KB patterns in name, `filterExcludedTools(listOf(tool))` includes that tool in output
    - Test file: `DeepBrdGenerationPropertyTest.kt`
    - **Validates: Requirements 7.5**

- [x] 8. Checkpoint — Full test suite verification
  - Ensure all tests pass with `./gradlew :server:jvmTest --tests "com.assistant.server.agent.ba.subprocess.pipeline.aibackend.*"`, ask the user if questions arise.

- [x] 9. Write unit tests for constants and backward compatibility
  - [x] 9.1 Write unit tests for constants and API compatibility
    - Verify `DEPTH_LIMIT == 3`, `MAX_TICKETS == 30`, `MAX_TOOL_RESULTS_CHARS == 80_000`
    - Verify new `appendDataCollectionStrategy(ticketId, tools)` accepts `List<ToolDescriptor>` parameter
    - Test file: `DeepBrdGenerationUnitTest.kt`
    - _Requirements: 1.4, 1.7, 1.8, 6.3_

  - [x] 9.2 Write integration test: existing AgenticPromptBuilder tests still pass
    - Run existing `PromptBuildingPropertyTest` to verify backward compatibility
    - Verify `buildInitialPrompt` still works with realistic tool lists
    - Verify `buildStatelessContinuation` with large data triggers truncation correctly
    - Test file: `DeepBrdGenerationUnitTest.kt`
    - _Requirements: 7.1, 7.7_

- [x] 10. Final checkpoint — All tests pass, backward compatibility verified
  - Ensure all tests pass with `./gradlew :server:jvmTest`, ask the user if questions arise.
  - Verify no changes to AgenticLoopRunner.kt, ToolExecutionBridge.kt, or AiBackendPipelineStrategy (Req 7.2, 7.3, 7.4)

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (12 properties)
- Unit tests validate specific examples, constants, and edge cases
- All files must stay under 200 lines, all functions under 20 lines per Kotlin code standards
- AgenticToolDetector.kt MUST be created first — other components depend on DetectedTools
- AgenticDiagramSections.kt is independent of KB strategy and can be created in parallel with task 2
- No changes to AgenticLoopRunner, ToolExecutionBridge, or AiBackendPipelineStrategy
