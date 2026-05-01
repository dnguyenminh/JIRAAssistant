# Implementation Plan: Document Generation UX Improvement

## Overview

Cải thiện UX của quá trình sinh tài liệu (BRD/FSD/Slides) trên trang Ticket Intelligence. Implementation chia thành 3 lớp: backend (ProgressTracker, PhaseLabelMapper, DB migration), shared models (startedAt field), và frontend (InlineProgressRenderer, elapsed timer, cancel, error panel, animations, button recovery). Mỗi task build on previous tasks, kết thúc bằng wiring tất cả components lại.

## Tasks

- [x] 1. Database migration và shared model changes
  - [x] 1.1 Add `started_at` column to `generation_jobs` table
    - Add SQL migration: `ALTER TABLE generation_jobs ADD COLUMN started_at TEXT`
    - Update `PgJobSql.kt` — add `started_at` to all SELECT column lists (FIND_BY_ID, FIND_ACTIVE_BY_TICKET_AND_TYPE, FIND_ACTIVE_BY_TICKET, FIND_BY_CHAIN, FIND_RUNNING, findByUser dynamic SQL)
    - Update `PgJobSql.INSERT` to include `started_at` as 12th parameter
    - Update `PgJobSql.UPDATE_STATUS` to optionally set `started_at`
    - _Requirements: 1.6, 7.1_

  - [x] 1.2 Add `startedAt` field to shared `GenerationJob` model
    - Add `val startedAt: String? = null` to `shared/src/commonMain/kotlin/com/assistant/document/models/GenerationJob.kt`
    - _Requirements: 1.6, 7.1_

  - [x] 1.3 Update `PgJobRepository` to read/write `started_at`
    - Update `mapRow()` to read `rs.getString("started_at")` into `startedAt`
    - Update `create()` to bind `job.startedAt` as 12th parameter
    - Add `updateStartedAt(jobId, startedAt)` method to `JobRepository` interface and `PgJobRepository`
    - _Requirements: 1.6, 7.1_

  - [x] 1.4 Write property test for startedAt on RUNNING transition (Property 3)
    - **Property 3: startedAt is set on RUNNING transition**
    - For any GenerationJob transitioning QUEUED → RUNNING, startedAt SHALL be non-null and valid ISO-8601
    - Use Kotest `checkAll` with random job creation + transition
    - **Validates: Requirements 1.6**

- [x] 2. Backend — PhaseLabelMapper and ProgressTracker
  - [x] 2.1 Create `PhaseLabelMapper` object
    - Create `server/src/jvmMain/kotlin/com/assistant/server/jobs/PhaseLabelMapper.kt`
    - Implement `fun getLabel(phase: String): String` mapping: QUEUED → "Đang chờ xử lý...", AGGREGATING_DATA → "Thu thập dữ liệu ticket...", GENERATING_DOCUMENT → "Đang gọi AI sinh tài liệu...", PARSING_RESPONSE → "Phân tích kết quả AI...", SAVING → "Đang lưu tài liệu...", COMPLETE → "Hoàn tất", FAILED → "Thất bại", else → phase
    - _Requirements: 2.1_

  - [x] 2.2 Write property test for PhaseLabelMapper (Property 4)
    - **Property 4: Phase label mapping is total and correct**
    - For any valid phase in {QUEUED, AGGREGATING_DATA, GENERATING_DOCUMENT, PARSING_RESPONSE, SAVING, COMPLETE, FAILED}, getLabel SHALL return non-empty Vietnamese string ≠ phase code
    - Use Kotest `Arb.of(validPhases)` with `checkAll`
    - **Validates: Requirements 2.1**

  - [x] 2.3 Create `ProgressTracker` class
    - Create `server/src/jvmMain/kotlin/com/assistant/server/jobs/ProgressTracker.kt`
    - Constructor: `(jobId: String, jobRepository: JobRepository, scope: CoroutineScope)`
    - Implement `suspend fun markStarted()` — calls `jobRepository.updateStartedAt(jobId, Instant.now().toString())`
    - Implement `suspend fun updateProgress(percent: Int, phase: String)` — calls `jobRepository.updateStatus(jobId, "RUNNING", percent, phase)`
    - Implement `fun startHeartbeat(fromPercent: Int, maxPercent: Int, intervalMs: Long)` — launches coroutine incrementing progress 1% every intervalMs, capped at maxPercent
    - Implement `fun stopHeartbeat()` — cancels heartbeat coroutine
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x] 2.4 Write property test for aggregation progress (Property 1)
    - **Property 1: Aggregation progress is monotonically increasing and bounded**
    - For any sequence of aggregation sub-steps, progress_percent values SHALL be strictly monotonically increasing and within [0, 30]
    - Use Kotest with random subsequences of [5, 15, 25, 30]
    - **Validates: Requirements 1.1**

  - [x] 2.5 Write property test for heartbeat bounded (Property 2)
    - **Property 2: Heartbeat progress is bounded and never exceeds cap**
    - For any startPercent in [35, 80] and any ticks (0..100), result SHALL equal min(startPercent + ticks, 80)
    - Use Kotest `Arb.int(35..80)` × `Arb.int(0..100)`
    - **Validates: Requirements 1.2**

- [x] 3. Backend — Integrate ProgressTracker into JobExecutor and JobRoutes
  - [x] 3.1 Modify `JobExecutor` to use `ProgressTracker`
    - Replace direct `updateProgress()` calls with `ProgressTracker` methods
    - Call `tracker.markStarted()` at job start
    - Use granular milestones: 5% start, 15% main ticket, 25% linked tickets, 30% aggregation done
    - Call `tracker.startHeartbeat(35, 80, 15_000)` before AI call
    - Call `tracker.stopHeartbeat()` after AI response, then `tracker.updateProgress(85, "PARSING_RESPONSE")`
    - Update SAVING phase: 90% start, 95% saved, 100% complete
    - Ensure `stopHeartbeat()` in finally block
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x] 3.2 Modify `JobRoutes` GET `/{jobId}` to include `phaseLabel` and `startedAt`
    - After fetching job from repository, compute `phaseLabel` via `PhaseLabelMapper.getLabel(job.phase)`
    - Build response DTO including `startedAt` from job and computed `phaseLabel`
    - Ensure all job list endpoints also include the new fields
    - _Requirements: 2.1, 7.1, 7.2_

- [x] 4. Checkpoint — Backend complete
  - Ensure all backend tests pass, ask the user if questions arise.

- [x] 5. Frontend — DTO and HTML template updates
  - [x] 5.1 Update `GenerationJobDto` with new fields
    - Add `val startedAt: String? = null` and `val phaseLabel: String? = null` to `GenerationJobDto` in `DocumentModels.kt`
    - _Requirements: 7.3_

  - [x] 5.2 Expand `tmpl-inline-progress` HTML template
    - Update template in `frontend/src/jsMain/resources/templates/ticket-intelligence.html`
    - Add `.inline-progress-info` div with `.inline-progress-phase-label` span and `.inline-progress-elapsed` span
    - Add `.inline-progress-actions` div with `.btn-cancel-job` button (icon ✕, title "Hủy")
    - Add `.inline-progress-timeout-warning` div (hidden by default) with warning text
    - _Requirements: 2.2, 3.1, 4.1_

  - [x] 5.3 Add `tmpl-error-panel` HTML template
    - Add new template to `ticket-intelligence.html`
    - Include `.docgen-error-panel` with error icon, message, close button, retry button, and optional integrations link
    - _Requirements: 5.1, 5.3, 5.5_

  - [x] 5.4 Add CSS styles for progress animations, timeout warning, and error panel
    - Add to `frontend/src/jsMain/resources/ticket-intelligence.css`:
    - Shimmer animation keyframes and `.inline-progress-bar-fill.shimmer` class
    - Smooth transition: `.inline-progress-bar-fill { transition: width 0.5s ease-in-out; }`
    - Success state: `.inline-progress-bar-fill.success { background: var(--accent-green); }`
    - Failed state: `.inline-progress-bar-fill.failed { background: var(--accent-red); }`
    - Timeout warning styles with `var(--warning-color)`
    - Error panel styles: `.docgen-error-panel` with dark theme colors
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 5.1_

- [x] 6. Frontend — InlineProgressRenderer
  - [x] 6.1 Create `InlineProgressRenderer` object
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/InlineProgressRenderer.kt`
    - Implement `fun renderProgress(areaId: String, job: GenerationJobDto)` — clone tmpl-inline-progress, populate progress bar fill width, phaseLabel text, elapsed timer, cancel button, timeout warning, shimmer class
    - Implement `fun renderError(areaId: String, job: GenerationJobDto, onRetry: () -> Unit)` — clone tmpl-error-panel, populate error message, retry button, integrations link (if error contains "provider"), auto-dismiss after 30s
    - Implement `fun renderSuccess(areaId: String, onComplete: () -> Unit)` — show 100% green bar for 1.5s then call onComplete
    - Implement `fun startElapsedTimer(areaId: String, startedAt: String): Int` — setInterval(1000) updating elapsed display, returns intervalId
    - Implement `fun stopElapsedTimer(intervalId: Int)` — clearInterval
    - Implement `fun clearProgress(areaId: String)` — remove all progress UI from area
    - _Requirements: 1.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 4.1, 5.1, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4_

  - [x] 6.2 Write property test for elapsed time formatting (Property 5)
    - **Property 5: Elapsed time formatting**
    - For any duration 0..600 seconds, format function SHALL produce "Xm Ys" where X = floor(s/60), Y = s%60
    - Use Kotest `Arb.int(0..600)` — test the formatting logic as a pure function
    - **Validates: Requirements 3.1**

  - [x] 6.3 Write property test for timeout warning threshold (Property 6)
    - **Property 6: Timeout warning threshold**
    - For any elapsed 0..600, shouldShowTimeoutWarning SHALL return true iff elapsed > 240
    - Use Kotest `Arb.int(0..600)`
    - **Validates: Requirements 3.2**

- [x] 7. Frontend — Wire DocumentGenerationFlow and DocumentGenerationSection
  - [x] 7.1 Modify `DocumentGenerationFlow` to use `InlineProgressRenderer`
    - Replace `BlockingOverlay` usage with `InlineProgressRenderer.renderProgress()` during polling
    - Update `pollJobUntilComplete()` to use `phaseLabel` and `startedAt` from response
    - Call `InlineProgressRenderer.startElapsedTimer()` when polling starts
    - Call `InlineProgressRenderer.stopElapsedTimer()` when job reaches terminal status
    - On COMPLETED: call `InlineProgressRenderer.renderSuccess()` then refresh badges
    - On FAILED: call `InlineProgressRenderer.renderError()` with retry callback
    - Add `cancelJob(jobId: String)` method — POST /api/jobs/{jobId}/cancel, handle success (hide progress, enable button, toast), handle 409 (toast + refresh), handle network error (re-enable cancel after 2s)
    - Handle poll 404 and network errors: stop polling, enable button, show toast
    - _Requirements: 4.2, 4.3, 4.4, 5.1, 5.3, 6.3, 6.4, 7.4, 7.5, 8.3_

  - [x] 7.2 Modify `DocGenBadgeRenderer.renderProgressFor()` to delegate to `InlineProgressRenderer`
    - Replace current simple progress rendering with call to `InlineProgressRenderer.renderProgress()`
    - Keep badge rendering logic unchanged
    - _Requirements: 2.2, 6.1, 6.2_

  - [x] 7.3 Modify `DocumentGenerationSection` for button state recovery
    - Add button state recovery logic in `fetchActiveJobsAndDocs()` — check for terminal jobs and enable buttons
    - On section render, check for FAILED/CANCELLED jobs and ensure buttons are enabled
    - Wire cancel button click to `DocumentGenerationFlow.cancelJob()`
    - Disable cancel button for 2s after click to prevent double-click
    - _Requirements: 4.5, 8.1, 8.2, 8.4_

  - [x] 7.4 Write property test for button recovery on terminal status (Property 7)
    - **Property 7: Button recovery on terminal job status**
    - For any job with status in {COMPLETED, FAILED, CANCELLED} × docType in {BRD, FSD, REQUIREMENT_SLIDES}, Generate button SHALL be enabled
    - Use Kotest `Arb.of(terminalStatuses)` × `Arb.of(docTypes)`
    - **Validates: Requirements 8.1, 8.2**

- [x] 8. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Implementation language: Kotlin (Kotlin/JVM for backend, Kotlin/JS for frontend) — matching existing codebase
- All Kotlin files must stay under 200 lines, all functions under 20 lines per workspace coding standards
- HTML templates in `src/jsMain/resources/templates/` — no HTML strings in Kotlin code
- CSS follows Obsidian Kinetic dark theme conventions
