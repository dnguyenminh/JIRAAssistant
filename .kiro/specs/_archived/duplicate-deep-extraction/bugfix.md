# Bugfix Requirements Document

## Introduction

When a user clicks the "re-analyze" button for a ticket, the server performs deep extraction (`jiraContentExtractor.extract()`) **twice** for the same ticket within a single analysis flow. This is caused by `AIOrchestratorImpl.analyzeTicket()` calling `extract()` once in `tryMapReduceAnalysis()` to check the linked ticket count, and then again in `buildDeepAnalysisPrompt()` when building the AI prompt — discarding the first result entirely when the map-reduce threshold is not met.

**Impact:**
- ~4.5 seconds wasted per re-analyze request
- ~36+ unnecessary Jira API calls per re-analyze
- Attachment pipeline runs twice (second run shows "already in KB" for all items)
- Doubled load on Jira server, risking rate limits under concurrent usage

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `analyzeTicket()` is called with `forceReanalyze=true` AND the ticket has linked tickets below the map-reduce threshold THEN the system calls `jiraContentExtractor.extract(ticketId)` twice — once in `tryMapReduceAnalysis()` and once in `buildDeepAnalysisPrompt()` — performing duplicate BFS traversal, duplicate Jira API calls, and duplicate attachment pipeline processing

1.2 WHEN `analyzeTicket()` is called with `forceReanalyze=false` AND no KB cache exists AND the ticket has linked tickets below the map-reduce threshold THEN the system calls `jiraContentExtractor.extract(ticketId)` twice — same duplicate extraction as 1.1

1.3 WHEN `tryMapReduceAnalysis()` successfully extracts `StructuredTicketContent` but the linked ticket count is at or below the threshold THEN the system discards the extracted content (returns `null`) and the subsequent single-prompt flow re-extracts the same content from scratch

### Expected Behavior (Correct)

2.1 WHEN `analyzeTicket()` is called with `forceReanalyze=true` AND the ticket has linked tickets below the map-reduce threshold THEN the system SHALL call `jiraContentExtractor.extract(ticketId)` exactly once, caching the result from `tryMapReduceAnalysis()` and reusing it in `buildDeepAnalysisPrompt()`

2.2 WHEN `analyzeTicket()` is called with `forceReanalyze=false` AND no KB cache exists AND the ticket has linked tickets below the map-reduce threshold THEN the system SHALL call `jiraContentExtractor.extract(ticketId)` exactly once, reusing the cached extraction result

2.3 WHEN `tryMapReduceAnalysis()` successfully extracts `StructuredTicketContent` but the linked ticket count is at or below the threshold THEN the system SHALL return the extracted content alongside the `null` analysis result so that the single-prompt flow can reuse it without re-extraction

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the ticket has linked tickets exceeding the map-reduce threshold THEN the system SHALL CONTINUE TO delegate to `MapReduceAnalyzer.analyzeWithMapReduce()` using the extracted content, producing the same analysis result as before

3.2 WHEN `mapReduceAnalyzer` is null or disabled THEN the system SHALL CONTINUE TO skip the map-reduce check entirely and proceed directly to the single-prompt flow with deep extraction

3.3 WHEN deep analysis components are not injected (`jiraContentExtractor == null`) THEN the system SHALL CONTINUE TO fall back to the legacy prompt builder without attempting any deep extraction

3.4 WHEN `jiraContentExtractor.extract()` throws an exception during the map-reduce check THEN the system SHALL CONTINUE TO catch the error, log it, and fall through to the single-prompt flow (which will attempt its own extraction)

3.5 WHEN a KB cache hit exists and `forceReanalyze=false` THEN the system SHALL CONTINUE TO return the cached result without calling `jiraContentExtractor.extract()` at all

3.6 WHEN the analysis completes successfully THEN the system SHALL CONTINUE TO save the result to KB and process ticket attachments, producing identical `AnalysisResult` output as before the fix

---

## Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type AnalyzeTicketInput { ticketId, forceReanalyze, mapReduceEnabled, linkedTicketCount, threshold }
  OUTPUT: boolean

  // Bug triggers when map-reduce is enabled, deep analysis is available,
  // extraction succeeds, but linked count doesn't exceed threshold —
  // causing the extracted content to be discarded and re-extracted.
  RETURN X.mapReduceEnabled = true
     AND deepAnalysisAvailable = true
     AND extractionSucceeds(X.ticketId) = true
     AND X.linkedTicketCount <= X.threshold
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — Single Extraction
FOR ALL X WHERE isBugCondition(X) DO
  extractCallCount ← countCalls(jiraContentExtractor.extract, X.ticketId)
  result ← analyzeTicket'(X.ticketId, X.forceReanalyze)
  ASSERT extractCallCount = 1
  ASSERT result is valid AnalysisResult (same as before fix)
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Identical Output
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT analyzeTicket(X) = analyzeTicket'(X)
  // Original and fixed function produce identical results for non-buggy inputs
END FOR
```
