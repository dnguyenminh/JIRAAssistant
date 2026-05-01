# Duplicate Deep Extraction Bugfix Design

## Overview

`AIOrchestratorImpl.analyzeTicket()` calls `jiraContentExtractor.extract()` twice for the same ticket when the map-reduce threshold is not met. The first call occurs in `tryMapReduceAnalysis()` to check linked ticket count, and the second in `buildDeepAnalysisPrompt()` to build the AI prompt. The fix caches the `StructuredTicketContent` from the first extraction and passes it through to the prompt-building step, eliminating ~4.5s of wasted time and ~36 redundant Jira API calls per analysis.

## Glossary

- **Bug_Condition (C)**: The condition where map-reduce is enabled, deep analysis is available, extraction succeeds, but linked ticket count ≤ threshold — causing the extracted content to be discarded and re-extracted
- **Property (P)**: `jiraContentExtractor.extract()` is called exactly once per `analyzeTicket()` invocation when the bug condition holds, and the `AnalysisResult` is identical to the current (double-extraction) output
- **Preservation**: All existing behaviors for non-buggy inputs (map-reduce delegation, legacy fallback, KB cache, error handling) remain unchanged
- **`tryMapReduceAnalysis()`**: Method in `AIOrchestratorImpl` (~line 98) that calls `extract()` to check linked ticket count and delegates to `MapReduceAnalyzer` if threshold is exceeded
- **`buildDeepAnalysisPrompt()`**: Method in `AIOrchestratorImpl` (~line 205) that calls `extract()` again to build the deep analysis prompt — the redundant call
- **`StructuredTicketContent`**: Data class holding all extracted Jira ticket data (metadata, sub-tasks, links, attachments, comments, changelog, classified sections)
- **`MapReduceAnalyzer.threshold`**: The linked ticket count above which map-reduce pipeline activates

## Bug Details

### Bug Condition

The bug manifests when `analyzeTicket()` is called for a ticket where map-reduce is enabled and deep analysis components are injected, but the linked ticket count does not exceed the map-reduce threshold. In this case, `tryMapReduceAnalysis()` extracts the full `StructuredTicketContent`, determines the threshold is not met, discards the result (returns `null`), and the subsequent single-prompt flow in `buildDeepAnalysisPrompt()` re-extracts the same content from scratch.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type AnalyzeTicketInput { ticketId, forceReanalyze, mapReduceEnabled, deepAnalysisAvailable, linkedTicketCount, threshold }
  OUTPUT: boolean

  RETURN input.mapReduceEnabled = true
         AND input.deepAnalysisAvailable = true
         AND extractionSucceeds(input.ticketId) = true
         AND input.linkedTicketCount <= input.threshold
END FUNCTION
```

### Examples

- **Ticket with 2 linked tickets, threshold=100**: `extract()` called in `tryMapReduceAnalysis()` → 2 ≤ 100 → returns null → `buildDeepAnalysisPrompt()` calls `extract()` again. Expected: single extraction, content reused.
- **Ticket with 0 linked tickets, threshold=50**: Same flow — extraction result discarded, re-extracted. Expected: single extraction.
- **Ticket with 50 linked tickets, threshold=50**: 50 ≤ 50 → still triggers bug. Expected: single extraction.
- **Ticket with 51 linked tickets, threshold=50**: 51 > 50 → map-reduce activates, content used directly. No bug — extraction happens once.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- When linked ticket count exceeds threshold, `MapReduceAnalyzer.analyzeWithMapReduce()` continues to receive the extracted content and produce the same result
- When `mapReduceAnalyzer` is null or disabled, the single-prompt flow proceeds directly without any map-reduce check
- When deep analysis components are not injected (`jiraContentExtractor == null`), the legacy prompt builder is used
- When `extract()` throws an exception during the map-reduce check, the error is caught and the single-prompt flow attempts its own extraction
- When a KB cache hit exists and `forceReanalyze=false`, the cached result is returned without calling `extract()`
- The `AnalysisResult` output is identical to the pre-fix output for all inputs

**Scope:**
All inputs where the bug condition does NOT hold should be completely unaffected by this fix. This includes:
- Tickets where map-reduce threshold is exceeded (map-reduce flow)
- Tickets analyzed without deep analysis components (legacy flow)
- Tickets served from KB cache
- Tickets where `mapReduceAnalyzer` is null or disabled
- Tickets where extraction fails in `tryMapReduceAnalysis()`

## Confirmed Root Cause

Based on code analysis of `AIOrchestratorImpl.kt`, the root cause is confirmed (validated by 13 passing property-based and unit tests):

1. **Discarded Extraction Result**: `tryMapReduceAnalysis()` returns `AnalysisResult?` — when linked count ≤ threshold, it returns `null`, discarding the `StructuredTicketContent` that was already extracted. There is no mechanism to pass the extracted content back to the caller.

2. **No Content Caching Between Methods**: `buildDeepAnalysisPrompt()` always calls `jiraContentExtractor!!.extract(ticketId)` unconditionally. It has no parameter to accept pre-extracted content, so it must re-extract even when the content was already available from the map-reduce check.

3. **Return Type Limitation**: The `tryMapReduceAnalysis()` method returns `AnalysisResult?` which can only convey "use this result" (`AnalysisResult`) or "fall through" (`null`). It cannot convey "fall through, but here's the content I already extracted."

## Correctness Properties

Property 1: Bug Condition - Single Extraction Per Analysis

_For any_ input where the bug condition holds (map-reduce enabled, deep analysis available, extraction succeeds, linked ticket count ≤ threshold), the fixed `analyzeTicket()` function SHALL call `jiraContentExtractor.extract()` exactly once and produce a valid `AnalysisResult` identical to the current output.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Non-Bug-Condition Behavior Unchanged

_For any_ input where the bug condition does NOT hold (map-reduce disabled, deep analysis unavailable, extraction fails, linked count > threshold, or KB cache hit), the fixed code SHALL produce exactly the same behavior as the original code, preserving map-reduce delegation, legacy fallback, KB caching, and error handling.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Implemented

**File**: `shared/src/commonMain/kotlin/com/assistant/ai/AIOrchestratorImpl.kt`

**Implemented Changes** (all verified by property-based tests):

1. **New sealed class `MapReduceCheckResult`**: Introduced a private sealed class inside `AIOrchestratorImpl` to replace the `AnalysisResult?` return type of `tryMapReduceAnalysis()`, allowing it to return extracted content alongside a "fall through" signal:
   ```kotlin
   private sealed class MapReduceCheckResult {
       data class Analyzed(val result: AnalysisResult) : MapReduceCheckResult()
       data class FallThrough(val extractedContent: StructuredTicketContent?) : MapReduceCheckResult()
   }
   ```

2. **Updated `tryMapReduceAnalysis()` return type**: Changed from `AnalysisResult?` to `MapReduceCheckResult`. When linked count ≤ threshold, returns `FallThrough(content)` instead of `null`. When extraction fails, returns `FallThrough(null)`. When analyzer is null/disabled or deep analysis unavailable, returns `FallThrough(null)`. When map-reduce succeeds, returns `Analyzed(result)`. When map-reduce fails, returns `FallThrough(content)` (content still available).

3. **Updated `analyzeTicket()` call site**: Replaced `if (mapReduceResult != null) return mapReduceResult` with `when` expression on `MapReduceCheckResult` — on `Analyzed`, returns the result; on `FallThrough`, passes `extractedContent` to `tryProvidersWithFailover()`.

4. **Updated `tryProvidersWithFailover()` signature**: Added optional `preExtractedContent: StructuredTicketContent? = null` parameter, passed through to `tryAnalyzeWithRetry()`.

5. **Updated `tryAnalyzeWithRetry()` signature**: Added optional `preExtractedContent: StructuredTicketContent? = null` parameter, passed through to `buildPromptForAnalysis()`.

6. **Updated `buildPromptForAnalysis()` and `buildDeepAnalysisPrompt()` signatures**: Added optional `preExtractedContent: StructuredTicketContent? = null` parameter. `buildDeepAnalysisPrompt()` uses `preExtractedContent ?: jiraContentExtractor!!.extract(ticketId)` — only calls `extract()` if no pre-extracted content is available.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior. **All tests have been implemented and validated (13 tests, 0 failures).**

### Exploratory Bug Condition Checking (Completed)

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm the root cause by observing double extraction calls.

**Implementation**: `DuplicateExtractionBugConditionTest.kt` — 6 tests (1 PBT with 50 iterations + 5 concrete unit tests) using `SpyJiraContentExtractor` that counts `extract()` calls and `FakeMapReduceAnalyzer` with configurable threshold.

**Test Cases** (all passing on fixed code with `extractCallCount == 1`):
1. **PBT — All bug condition inputs**: Random `linkedCount` (0..200), `threshold` (1..500), `forceReanalyze` (boolean), ensuring `linkedCount ≤ threshold`
2. **Below-Threshold Test**: threshold=100, ticket with 2 linked tickets
3. **At-Threshold Test**: threshold=5, ticket with 5 linked tickets
4. **Zero Linked Tickets Test**: threshold=50, ticket with 0 linked tickets
5. **ForceReanalyze=false Test**: threshold=100, ticket with 3 linked tickets, `forceReanalyze=false`
6. **Valid Result Test**: Confirms `AnalysisResult` has valid content alongside single extraction

### Fix Checking (Completed)

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function calls `extract()` exactly once. **Validated by `DuplicateExtractionBugConditionTest` — all 6 tests pass.**

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  spyExtractor.resetCallCount()
  result := analyzeTicket_fixed(input.ticketId, input.forceReanalyze)
  ASSERT spyExtractor.extractCallCount = 1
  ASSERT result is valid AnalysisResult
END FOR
```

### Preservation Checking (Completed)

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function. **Validated by `DuplicateExtractionPreservationTest` — all 7 tests pass.**

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT analyzeTicket_original(input) = analyzeTicket_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many combinations of map-reduce enabled/disabled, threshold values, linked ticket counts, and cache states
- It catches edge cases around threshold boundaries
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observed behavior on UNFIXED code first for non-bug-condition inputs, then wrote property-based tests capturing that behavior.

**Implementation**: `DuplicateExtractionPreservationTest.kt` — 7 tests validating all preservation requirements.

**Test Cases** (all passing):
1. **Map-Reduce Delegation Preservation**: When linked count > threshold, `MapReduceAnalyzer.analyzeWithMapReduce()` is called with the extracted content and produces the same result
2. **Legacy Fallback Preservation**: When deep analysis components are not injected, the legacy prompt builder is used unchanged
3. **KB Cache Preservation**: When KB cache hit exists and `forceReanalyze=false`, the cached result is returned without calling `extract()`
4. **Error Handling Preservation**: When `extract()` throws in `tryMapReduceAnalysis()`, the single-prompt flow still works
5. **Disabled Analyzer Preservation**: When `mapReduceAnalyzer` is disabled, single-prompt flow proceeds directly
6. **Null Analyzer Preservation**: When `mapReduceAnalyzer` is null, single-prompt flow proceeds directly
7. **Result Saved to KB**: After successful analysis, result is saved to KB

### Unit Tests

- Test `MapReduceCheckResult` sealed class construction and pattern matching
- Test `tryMapReduceAnalysis()` returns `FallThrough(content)` when linked count ≤ threshold
- Test `tryMapReduceAnalysis()` returns `FallThrough(null)` when extraction fails
- Test `tryMapReduceAnalysis()` returns `Analyzed(result)` when linked count > threshold
- Test `buildDeepAnalysisPrompt()` uses pre-extracted content when provided
- Test `buildDeepAnalysisPrompt()` calls `extract()` when no pre-extracted content

### Property-Based Tests

- Generate random linked ticket counts (0..2000) and thresholds (50..1000), verify `extract()` is called exactly once when bug condition holds
- Generate random configurations (map-reduce enabled/disabled, deep analysis available/unavailable), verify preservation of non-bug-condition behavior
- Generate random cache states and `forceReanalyze` flags, verify KB-first strategy is unchanged

### Integration Tests

- Test full `analyzeTicket()` flow with map-reduce enabled and linked count below threshold — verify single extraction
- Test full `analyzeTicket()` flow with map-reduce enabled and linked count above threshold — verify map-reduce delegation unchanged
- Test full `analyzeTicket()` flow with map-reduce disabled — verify single-prompt flow unchanged
