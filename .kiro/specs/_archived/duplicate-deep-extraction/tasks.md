# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Duplicate Deep Extraction When Below Map-Reduce Threshold
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate `jiraContentExtractor.extract()` is called twice
  - **Scoped PBT Approach**: Scope the property to concrete failing cases: map-reduce enabled, deep analysis available, extraction succeeds, linked ticket count ≤ threshold
  - Test that `analyzeTicket(ticketId, forceReanalyze=true)` calls `extract()` exactly once when linked count ≤ threshold (from Bug Condition in design: `isBugCondition(input)` where `mapReduceEnabled=true AND deepAnalysisAvailable=true AND extractionSucceeds=true AND linkedTicketCount <= threshold`)
  - Run test on UNFIXED code - expect FAILURE (extractCallCount will be 2 instead of 1, confirming the bug exists)
  - Document counterexamples found (e.g., "extract() called 2 times for ticket with 2 linked tickets and threshold=100")
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Bug-Condition Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: When `mapReduceAnalyzer` is null, single-prompt flow proceeds directly on unfixed code
  - Observe: When deep analysis components are not injected, legacy prompt builder is used on unfixed code
  - Observe: When KB cache hit exists and `forceReanalyze=false`, cached result returned without calling `extract()` on unfixed code
  - Observe: When linked count > threshold, `MapReduceAnalyzer.analyzeWithMapReduce()` is called with extracted content on unfixed code
  - Write property-based tests: for all non-bug-condition inputs, behavior is preserved (from Preservation Requirements in design: Requirements 3.1-3.6)
  - Verify tests pass on UNFIXED code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix for duplicate deep extraction in AIOrchestratorImpl

  - [x] 3.1 Add sealed class MapReduceCheckResult and import StructuredTicketContent
    - Add `import com.assistant.ai.deepanalysis.models.StructuredTicketContent` to imports
    - Add private sealed class `MapReduceCheckResult` inside `AIOrchestratorImpl` with two variants:
      - `Analyzed(val result: AnalysisResult)` — map-reduce produced a final result
      - `FallThrough(val extractedContent: StructuredTicketContent?)` — fall through to single-prompt, optionally with pre-extracted content
    - _Bug_Condition: isBugCondition(input) where tryMapReduceAnalysis returns null, discarding extracted content_
    - _Expected_Behavior: tryMapReduceAnalysis returns FallThrough(content) preserving extracted content_
    - _Preservation: Analyzed variant preserves map-reduce delegation behavior_
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 Update tryMapReduceAnalysis() return type to MapReduceCheckResult
    - Change return type from `AnalysisResult?` to `MapReduceCheckResult`
    - When analyzer is null/disabled/no deep analysis → return `FallThrough(null)`
    - When extraction fails → return `FallThrough(null)`
    - When linked count ≤ threshold → return `FallThrough(content)` (KEY FIX — pass content back)
    - When map-reduce succeeds → return `Analyzed(result)`
    - When map-reduce fails → return `FallThrough(content)` (still have the content)
    - _Bug_Condition: isBugCondition(input) where linkedTicketCount <= threshold_
    - _Expected_Behavior: FallThrough(content) returned instead of null, preserving extracted content_
    - _Preservation: Analyzed path unchanged; FallThrough(null) for disabled/unavailable cases_
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4_

  - [x] 3.3 Update analyzeTicket() to handle MapReduceCheckResult
    - Replace `val mapReduceResult = tryMapReduceAnalysis(...)` / `if (mapReduceResult != null) return mapReduceResult` with `when` expression on `MapReduceCheckResult`
    - On `Analyzed` → return `checkResult.result`
    - On `FallThrough` → pass `checkResult.extractedContent` to `tryProvidersWithFailover()`
    - _Bug_Condition: isBugCondition(input) where FallThrough carries pre-extracted content_
    - _Expected_Behavior: pre-extracted content flows to single-prompt path_
    - _Preservation: Analyzed path returns result directly as before_
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.4 Update tryProvidersWithFailover() to accept preExtractedContent
    - Add `preExtractedContent: StructuredTicketContent? = null` parameter
    - Pass it through to `tryAnalyzeWithRetry()`
    - _Preservation: Default null preserves existing callers_
    - _Requirements: 2.1, 2.2_

  - [x] 3.5 Update tryAnalyzeWithRetry() to accept preExtractedContent
    - Add `preExtractedContent: StructuredTicketContent? = null` parameter
    - Pass it to `buildPromptForAnalysis()`
    - _Preservation: Default null preserves existing callers_
    - _Requirements: 2.1, 2.2_

  - [x] 3.6 Update buildPromptForAnalysis() and buildDeepAnalysisPrompt() to use preExtractedContent
    - Add `preExtractedContent: StructuredTicketContent? = null` parameter to both methods
    - In `buildDeepAnalysisPrompt()`: use `preExtractedContent ?: jiraContentExtractor!!.extract(ticketId)` instead of always calling `extract()`
    - _Bug_Condition: When preExtractedContent is non-null, extract() is NOT called again_
    - _Expected_Behavior: extract() called exactly once per analyzeTicket() invocation_
    - _Preservation: When preExtractedContent is null, extract() is called as before (legacy behavior)_
    - _Requirements: 2.1, 2.2, 2.3, 3.2, 3.3_

  - [x] 3.7 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Single Extraction Per Analysis
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior (extractCallCount = 1)
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed — extract() called exactly once)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.8 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Bug-Condition Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
