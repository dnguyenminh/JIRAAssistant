# Bugfix Tasks — Batch Scan Placeholder Analysis

## Tasks

- [x] 1. Fix `fetchTicketContentForBatch()` to extract real content via `jiraContentExtractor`
  - [x] 1.1 In `BatchScanTicketBatchProcessor.kt`, modify `fetchTicketContentForBatch()` to call `jiraContentExtractor.extract(ticketId)` when extractor is available, converting `StructuredTicketContent` to a plain-text string (summary + description + sub-tasks + comments, capped at 3000 chars) instead of returning `""`
  - [x] 1.2 Add `formatStructuredContent(content: StructuredTicketContent): String` helper function that converts structured content to a condensed text representation suitable for batch prompts
  - [x] 1.3 Add error handling: if `jiraContentExtractor.extract()` throws, fall back to `fetchLegacyBatchContent()` (existing legacy fetch logic) with a log message

- [x] 2. Add placeholder detection and single-ticket fallback in `saveBatchResults()`
  - [x] 2.1 Add `isPlaceholderResult(result: AnalysisResult): Boolean` helper that returns true when `requirementSummary` is `"..."`, contains `"Placeholder"` (case-insensitive), or has length < 10 characters
  - [x] 2.2 Modify `saveBatchResults()` to skip saving placeholder results and return a list of affected ticket IDs for fallback
  - [x] 2.3 Modify `processSingleBatchPrompt()` to process placeholder ticket IDs via `processTicket()` (single-ticket mode) after batch save completes

- [x] 3. Write unit tests for the fix
  - [x] 3.1 Test `fetchTicketContentForBatch()` returns non-empty content when `jiraContentExtractor` is available (mock extractor returning `StructuredTicketContent`)
  - [x] 3.2 Test `fetchTicketContentForBatch()` falls back to legacy fetch when `jiraContentExtractor` is null
  - [x] 3.3 Test `fetchTicketContentForBatch()` falls back to legacy fetch when `jiraContentExtractor.extract()` throws an exception
  - [x] 3.4 Test `isPlaceholderResult()` correctly identifies placeholder patterns (`"..."`, `"Placeholder..."`, short strings) and accepts real analysis text
  - [x] 3.5 Test `saveBatchResults()` skips placeholder results and returns their ticket IDs for fallback
  - [x] 3.6 Test `processSingleBatchPrompt()` triggers single-ticket fallback for placeholder ticket IDs
