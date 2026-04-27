# Batch Scan Placeholder Analysis Bugfix Design

## Overview

Fix the batch scan pipeline so that when `batch_prompt_size > 1`, the AI receives real Jira ticket content instead of empty strings. Currently `fetchTicketContentForBatch()` returns `""` when `jiraContentExtractor` is available, causing the batch prompt to contain only ticket IDs. The AI then echoes back placeholder values (`"..."`, `"Placeholder requirement summary for {ticketId}"`) from the `BatchPromptBuilder` JSON template. The fix has two layers: (1) call `jiraContentExtractor.extract()` in `fetchTicketContentForBatch()` to provide real content, and (2) add placeholder detection in `saveBatchResults()` to catch and fallback any garbage results.

## Glossary

- **Bug_Condition (C)**: `batch_prompt_size > 1` AND `jiraContentExtractor != null` — batch mode sends empty content to AI
- **Property (P)**: Batch prompts contain real extracted ticket content, and KB records contain meaningful analysis
- **Preservation**: Single-ticket mode (`batch_prompt_size = 1`), KB-First caching, provider failover, retry logic — all unchanged
- **`fetchTicketContentForBatch()`**: Function in `BatchScanTicketBatchProcessor.kt` that fetches ticket content for batch prompts — currently returns `""` when `jiraContentExtractor` is available
- **`fetchTicketContent()`**: Function in `BatchScanTicketProcessor.kt` for single-ticket mode — also returns `""` but `analyzeTicket()` calls extractor internally
- **`analyzeTicketBatchImpl()`**: Function in `AIOrchestratorBatch.kt` that processes batch prompts — does NOT call `jiraContentExtractor` internally (unlike `analyzeTicket()`)
- **`BatchPromptBuilder`**: Builds batch prompts with `"..."` as JSON example values — AI copies these when content is empty
- **`StructuredTicketContent`**: Rich content object returned by `jiraContentExtractor.extract()` containing summary, description, sub-tasks, comments, etc.

## Bug Details

### Bug Condition

The bug manifests when `batch_prompt_size > 1` and `jiraContentExtractor` is available (deep analysis mode). `fetchTicketContentForBatch()` returns `""` for every ticket, so `BatchPromptBuilder.buildBatchPrompt()` generates a prompt with only ticket IDs and no content. The AI model, having no real data to analyze, echoes back the `"..."` placeholder values from the JSON template example in the prompt.

The asymmetry with single-ticket mode: `fetchTicketContent()` also returns `""`, but `analyzeTicket()` → `buildDeepAnalysisPrompt()` calls `jiraContentExtractor.extract(ticketId)` internally. The batch path (`analyzeTicketBatchImpl()`) does NOT call the extractor — it relies on the content passed in via `List<Pair<String, String>>`.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type BatchScanInput { batchPromptSize: Int, jiraContentExtractor: JiraContentExtractor? }
  OUTPUT: boolean

  RETURN input.batchPromptSize > 1
         AND input.jiraContentExtractor IS NOT NULL
END FUNCTION
```

### Examples

- `batch_prompt_size = 3`, `jiraContentExtractor` available, 3 tickets → AI receives empty content for all 3 → returns `"..."` for each `requirementSummary` → saved as useless KB records
- `batch_prompt_size = 3`, `jiraContentExtractor` is null (legacy mode), 3 tickets → `fetchLegacyTicketContent()` returns summary+description → AI gets partial content (works, but less rich)
- `batch_prompt_size = 1` → single-ticket mode via `processTicket()` → `analyzeTicket()` calls extractor internally → works correctly
- `batch_prompt_size = 5`, 5 tickets, AI returns `requirementSummary = "Placeholder requirement summary for PROJ-123"` → currently saved as valid; should be detected as placeholder and trigger fallback

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Single-ticket mode (`batch_prompt_size = 1`) continues to use `processTicket()` → `analyzeTicket()` with internal content extraction
- KB-First caching: tickets already in KB are returned from cache when `forceReanalyze = false`
- Provider failover: timeout/failure triggers next provider, then single-ticket fallback
- Retry logic: unparseable JSON triggers 1 retry, then single-ticket fallback for entire batch
- Partial results: missing tickets from batch response trigger single-ticket fallback for those tickets only
- Attachment processing after batch analysis remains unchanged

**Scope:**
All inputs where `batch_prompt_size = 1` are completely unaffected. The fix only modifies the batch content fetching path and adds a validation layer in `saveBatchResults()`.

## Hypothesized Root Cause

Based on code analysis, the confirmed root causes are:

1. **Empty content return in `fetchTicketContentForBatch()`**: Line `if (jiraContentExtractor != null) return ""` — this was written assuming `analyzeTicketBatchImpl()` would call the extractor internally (like `analyzeTicket()` does), but it doesn't. The batch path passes content through `List<Pair<String, String>>` and never calls the extractor.

2. **Asymmetric deep analysis pipeline**: `analyzeTicket()` calls `buildDeepAnalysisPrompt()` → `jiraContentExtractor.extract()` internally. `analyzeTicketBatchImpl()` uses `BatchPromptBuilder.buildBatchPrompt(tickets)` which expects content to be pre-fetched in the `Pair.second` field.

3. **No placeholder validation in `saveBatchResults()`**: `BatchResponseParser.parseOneResult()` accepts any non-empty string as valid `requirementSummary`. Values like `"..."` or `"Placeholder requirement summary for {ticketId}"` pass through unchecked.

## Correctness Properties

Property 1: Bug Condition — Batch Prompts Contain Real Ticket Content

_For any_ input where the bug condition holds (`batch_prompt_size > 1` AND `jiraContentExtractor` is available), the fixed `fetchTicketContentForBatch()` SHALL call `jiraContentExtractor.extract(ticketId)` and return a non-empty string representation of the structured content, ensuring the batch prompt sent to AI contains actual Jira ticket data.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition — Placeholder Results Detected and Rejected

_For any_ batch analysis result where `requirementSummary` matches a placeholder pattern (`"..."`, contains `"Placeholder"`, or length < 10 characters), the fixed `saveBatchResults()` SHALL treat those results as failures and fall back to single-ticket analysis for the affected tickets.

**Validates: Requirements 2.4**

Property 3: Preservation — Single-Ticket Mode Unchanged

_For any_ input where the bug condition does NOT hold (`batch_prompt_size = 1`), the fixed code SHALL produce exactly the same behavior as the original code, routing through `processTicket()` → `analyzeTicket()` with internal content extraction unchanged.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**File**: `shared/src/commonMain/kotlin/com/assistant/scan/BatchScanTicketBatchProcessor.kt`

**Function**: `fetchTicketContentForBatch()`

**Change 1 — Extract real content via `jiraContentExtractor`:**

Before:
```kotlin
private suspend fun BatchScanEngine.fetchTicketContentForBatch(ticketId: String): String {
    if (jiraContentExtractor != null) return ""
    // ... legacy fetch ...
}
```

After:
```kotlin
private suspend fun BatchScanEngine.fetchTicketContentForBatch(ticketId: String): String {
    if (jiraContentExtractor != null) {
        return try {
            val content = jiraContentExtractor.extract(ticketId)
            formatStructuredContent(content)
        } catch (e: Exception) {
            println("[BatchScanEngine] Content extraction failed for $ticketId: ${e.message}")
            fetchLegacyBatchContent(ticketId)
        }
    }
    return fetchLegacyBatchContent(ticketId)
}
```

**New helper function — `formatStructuredContent()`:**
Converts `StructuredTicketContent` to a plain-text string suitable for the batch prompt. Uses `DeepAnalysisPromptBuilder.buildPrompt()` pattern but outputs a condensed text representation (summary + description + sub-tasks + comments) capped at 3000 chars.

**Change 2 — Add placeholder detection in `saveBatchResults()`:**

Add a validation check before saving each result. If `requirementSummary` is a placeholder, collect the ticket ID for single-ticket fallback:

```kotlin
private suspend fun BatchScanEngine.saveBatchResults(
    projectKey: String,
    results: Map<String, AnalysisResult>
): List<String> {  // returns list of placeholder ticket IDs for fallback
    val placeholderTicketIds = mutableListOf<String>()
    for ((_, result) in results) {
        if (isPlaceholderResult(result)) {
            placeholderTicketIds.add(result.ticketId)
            continue  // skip saving placeholder results
        }
        // ... existing save logic ...
    }
    return placeholderTicketIds
}
```

**New helper function — `isPlaceholderResult()`:**
```kotlin
private fun isPlaceholderResult(result: AnalysisResult): Boolean {
    val summary = result.context.unified.trim()
    return summary == "..." ||
           summary.contains("Placeholder", ignoreCase = true) ||
           summary.length < 10
}
```

**Change 3 — Fallback for placeholder tickets in `processSingleBatchPrompt()`:**

After `saveBatchResults()`, process placeholder tickets via single-ticket mode:

```kotlin
val placeholderIds = saveBatchResults(projectKey, results)
if (placeholderIds.isNotEmpty()) {
    for (ticketId in placeholderIds) {
        processTicket(projectKey, ticketId)
    }
}
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm the root cause: `fetchTicketContentForBatch()` returns `""` when `jiraContentExtractor` is available.

**Test Plan**: Write unit tests that call `fetchTicketContentForBatch()` with a mock `jiraContentExtractor` and verify the returned content is empty. Run on UNFIXED code to confirm the bug.

**Test Cases**:
1. **Empty content test**: Call `fetchTicketContentForBatch("PROJ-1")` with `jiraContentExtractor` available → verify returns `""` (will confirm bug on unfixed code)
2. **Batch prompt test**: Build batch prompt with empty content pairs → verify prompt contains no ticket data (will confirm bug on unfixed code)
3. **Placeholder result test**: Parse a batch response with `"..."` values → verify `parseOneResult()` accepts them as valid (will confirm no validation on unfixed code)

**Expected Counterexamples**:
- `fetchTicketContentForBatch()` returns `""` for all tickets when `jiraContentExtractor != null`
- Batch prompt contains `"Ticket ID: PROJ-1"` followed by empty line (no content)
- `requirementSummary = "..."` passes through `parseOneResult()` without rejection

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  content := fetchTicketContentForBatch_fixed(input.ticketId)
  ASSERT content.length > 0
  ASSERT NOT content.contains("Placeholder")
  
  results := analyzeTicketBatch(input.ticketIds)
  FOR EACH (ticketId, result) IN results DO
    ASSERT result.requirementSummary != "..."
    ASSERT result.requirementSummary.length >= 10
  END FOR
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT processTicket_original(input) = processTicket_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that single-ticket mode behavior is unchanged

**Test Plan**: Observe behavior on UNFIXED code first for single-ticket mode, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Single-ticket mode preservation**: Verify `batch_prompt_size = 1` routes through `processTicket()` unchanged
2. **KB cache preservation**: Verify cached tickets are returned without re-analysis
3. **Legacy fetch preservation**: Verify `jiraContentExtractor = null` still uses `fetchLegacyBatchContent()`
4. **Retry/fallback preservation**: Verify unparseable JSON still triggers retry + single-ticket fallback

### Unit Tests

- Test `fetchTicketContentForBatch()` with mock `jiraContentExtractor` → returns non-empty content
- Test `fetchTicketContentForBatch()` with `jiraContentExtractor = null` → uses legacy fetch
- Test `fetchTicketContentForBatch()` when `jiraContentExtractor.extract()` throws → falls back to legacy
- Test `isPlaceholderResult()` with `"..."` → returns true
- Test `isPlaceholderResult()` with `"Placeholder requirement summary"` → returns true
- Test `isPlaceholderResult()` with `"short"` (< 10 chars) → returns true
- Test `isPlaceholderResult()` with real analysis text → returns false
- Test `saveBatchResults()` skips placeholder results and returns their ticket IDs

### Property-Based Tests

- Generate random ticket IDs and verify `fetchTicketContentForBatch()` always returns non-empty content when `jiraContentExtractor` is available
- Generate random `requirementSummary` strings and verify `isPlaceholderResult()` correctly identifies placeholders vs real content
- Generate random batch sizes (1..10) and verify single-ticket mode is used when size = 1

### Integration Tests

- Test full batch scan flow: `processBatchPrompt()` → `fetchBatchContent()` → `analyzeTicketBatch()` → `saveBatchResults()` with mock AI returning real results
- Test placeholder fallback flow: batch returns placeholder → detected → single-ticket fallback succeeds
- Test mixed results: batch returns 2 real + 1 placeholder → 2 saved, 1 falls back to single-ticket
