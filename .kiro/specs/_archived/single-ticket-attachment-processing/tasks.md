# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Single Ticket Analysis Skips Attachment Processing
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate `runAnalysis()` does NOT call `AttachmentPipeline.processAttachments()`
  - **Scoped PBT Approach**: Scope the property to concrete failing case: any ticket with attachments analyzed via `AnalysisRoutes.runAnalysis()`
  - Create test file `server/src/jvmTest/kotlin/com/assistant/server/routes/AnalysisAttachmentBugTest.kt`
  - Mock `AIOrchestrator.analyzeTicket()` to return success result
  - Mock `JiraClient.getIssueDetails()` to return ticket with attachments (1-5 random attachments)
  - Mock `AttachmentPipeline.processAttachments()` to track invocations
  - Call `runAnalysis(ticketId, orchestrator, forceReanalyze=false)` on UNFIXED code
  - Assert: `attachmentPipeline.processAttachments()` WAS called with correct `(projectKey, ticketId, attachments)` — from Bug Condition `isBugCondition(input) WHERE input.source = "AnalysisRoutes.runAnalysis" AND ticketHasAttachments(input.ticketId)`
  - Assert: `projectKey` equals `ticketId.substringBefore("-")` — from Expected Behavior in design
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (processAttachments never called — confirms bug exists)
  - Document counterexample: `runAnalysis("PROJ-123")` completes AI analysis but `processAttachments()` invocation count = 0
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Attachment and Failure Cases Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Create test file `server/src/jvmTest/kotlin/com/assistant/server/routes/AnalysisPreservationTest.kt`
  - **Observe on UNFIXED code:**
  - Observe: `runAnalysis("PROJ-100")` for ticket with NO attachments → AI analysis completes, no attachment processing attempted, response contains analysis result
  - Observe: `runAnalysis("PROJ-200")` when `orchestrator.analyzeTicket()` throws → error propagates, no attachment processing attempted
  - Observe: 4-phase status tracking (FETCHING_JIRA → EXTRACTING_CONTENT → AI_ANALYZING → KB_SYNCING → COMPLETE) fires in order, then status removed in `finally` block
  - **Write property-based tests capturing observed behavior:**
  - Property 2a: For all tickets with empty attachment list, `runAnalysis()` completes with AI result and `processAttachments()` is NOT called — from Preservation Requirement 3.2
  - Property 2b: For all AI analysis failures (orchestrator throws), error propagates to caller and `processAttachments()` is NOT called — from Preservation Requirement 3.4
  - Property 2c: For all successful analyses, `AnalysisStatusTracker` phases fire in order (FETCHING_JIRA, EXTRACTING_CONTENT, AI_ANALYZING, KB_SYNCING, COMPLETE) and status is removed after completion — from design "Analysis response format và status tracking không thay đổi"
  - Verify all tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.4_

- [x] 3. Fix single ticket attachment processing in AnalysisRoutes

  - [x] 3.1 Inject AttachmentPipeline and JiraClient into analysisRoutes()
    - In `analysisRoutes()`, inject `AttachmentPipeline` via Koin: `val attachmentPipeline by inject<AttachmentPipeline>()`
    - Inject `JiraCredentialsService` and `HttpClient` to create `JiraClient` factory lambda — follow pattern from `ProjectRoutes`
    - Pass `attachmentPipeline` and `jiraClientProvider` lambda to `runAnalysis()` as new parameters
    - _Bug_Condition: isBugCondition(input) WHERE input.source = "AnalysisRoutes.runAnalysis" AND ticketHasAttachments(input.ticketId)_
    - _Expected_Behavior: After AI analysis success, processAttachments(projectKey, ticketId, attachments) is called_
    - _Preservation: Batch scan flow via BatchScanEngine unchanged; no-attachment tickets unaffected_
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2_

  - [x] 3.2 Add attachment processing call in runAnalysis() after AI analysis
    - Add `attachmentPipeline: AttachmentPipeline` and `jiraClientProvider: () -> JiraClient` parameters to `runAnalysis()`
    - After `AnalysisPhase.COMPLETE` update, BEFORE `finally` block:
    - Wrap in try-catch: fetch `jiraClientProvider().getIssueDetails(ticketId)`, extract attachments list
    - If attachments not empty: extract `projectKey = ticketId.substringBefore("-")`, call `attachmentPipeline.processAttachments(projectKey, ticketId, attachments)`
    - Catch block: log warning `[AnalysisRoutes] Attachment processing failed for $ticketId: ${e.message}` — do NOT rethrow
    - Analysis result returns normally regardless of attachment processing outcome
    - Keep function ≤ 20 lines — extract attachment processing to a separate private function if needed
    - _Bug_Condition: isBugCondition(input) WHERE input.source = "AnalysisRoutes.runAnalysis" AND ticketHasAttachments(input.ticketId)_
    - _Expected_Behavior: processAttachments() called with (projectKey, ticketId, attachments); failure logged but not propagated_
    - _Preservation: AI analysis result unchanged; error isolation ensures attachment failure doesn't fail response_
    - _Requirements: 2.1, 2.2, 2.3, 3.4_

  - [x] 3.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Single Ticket Analysis Processes Attachments
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior (processAttachments called with correct args)
    - Run `AnalysisAttachmentBugTest` on FIXED code
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed — processAttachments now called)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Attachment and Failure Cases Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run `AnalysisPreservationTest` on FIXED code
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions — no-attachment tickets, AI failures, status tracking all unchanged)
    - Confirm all preservation properties still hold after fix

- [x] 4. Checkpoint — Ensure all tests pass
  - Run all tests: `AnalysisAttachmentBugTest` (bug condition → now passes) + `AnalysisPreservationTest` (preservation → still passes)
  - Verify no regressions in existing test suites
  - Ensure all tests pass, ask the user if questions arise
