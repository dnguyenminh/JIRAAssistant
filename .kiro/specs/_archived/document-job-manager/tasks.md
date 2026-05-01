# Implementation Plan: Document Job Manager

## Overview

Extends the existing `ai-generated-brd-fsd` implementation with persistent background job management, dependency chain enforcement, generation lock, review/approve workflow, document versioning, and a global job indicator. Tasks are organized by module layer: shared models → database migration → server persistence → server logic → server routes → frontend models → frontend components → frontend integration → tests.

## Tasks

- [x] 1. Shared Module — New Job Models
  - [x] 1.1 Create `shared/.../document/models/JobStatus.kt` — `@Serializable enum class JobStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }`
    - _Requirements: 2.1_
  - [x] 1.2 Create `shared/.../document/models/ApprovalStatus.kt` — `@Serializable enum class ApprovalStatus { DRAFT, APPROVED, REJECTED }`
    - _Requirements: 6.1, 7.1_
  - [x] 1.3 Create `shared/.../document/models/GenerationJob.kt` — data class with jobId, ticketId, documentType, status, progressPercent, phase, chainId, createdBy, createdAt, updatedAt, errorMessage
    - _Requirements: 2.1_
  - [x] 1.4 Create `shared/.../document/models/JobChainResponse.kt` — data class with chainId + List\<GenerationJob\>
    - _Requirements: 1.4, 8.4_

- [x] 2. Shared Module — Extend Existing Document Models
  - [x] 2.1 Update `shared/.../document/models/GeneratedDocument.kt` — add fields: approvalStatus (default "DRAFT"), versionNumber (nullable Int), rejectReason (nullable), reviewedBy (nullable), reviewedAt (nullable)
    - _Requirements: 7.1, 6.1_
  - [x] 2.2 Update `server/.../db/DocumentRepository.kt` — update `GeneratedDocumentMeta` to include approvalStatus, versionNumber, hasDraft fields
    - _Requirements: 8.6_

- [x] 3. Database Migration — Job Manager & Versioning Schema
  - [x] 3.1 Create Flyway migration `server/.../resources/db/migration/V4__add_job_manager_and_versioning.sql` — CREATE TABLE `generation_jobs` (job_id UUID PK, ticket_id, document_type, status, progress_percent, phase, chain_id, created_by, created_at, updated_at, error_message) with indexes on ticket_id, status, chain_id. ALTER TABLE `generated_documents`: drop UNIQUE constraint (ticket_id, document_type), add columns approval_status, version_number, reject_reason, reviewed_by, reviewed_at with indexes
    - _Requirements: 2.1, 7.1_

- [x] 4. Server Module — Job Repository (Persistence Layer)
  - [x] 4.1 Create `server/.../db/JobRepository.kt` — interface with methods: create, findById, findByTicketIdAndTypeActive, findActiveByTicketId, findByUser, findByChainId, updateStatus, findRunningJobs
    - _Requirements: 2.1, 2.5, 2.6_
  - [x] 4.2 Create `server/.../db/pg/PgJobSql.kt` — SQL constants for INSERT, SELECT by id, SELECT active by ticket+type, SELECT active by ticket, SELECT by user with status filter, SELECT by chain_id, UPDATE status, SELECT running jobs
    - _Requirements: 2.1_
  - [x] 4.3 Create `server/.../db/pg/PgJobRepository.kt` — PostgreSQL implementation of JobRepository using PgJobSql constants and DataSource
    - _Requirements: 2.1, 2.5_

- [x] 5. Checkpoint — Verify shared models compile and migration runs
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Server Module — Update Document Repository for Versioning
  - [x] 6.1 Update `server/.../db/DocumentRepository.kt` interface — add methods: findLatestByTicketIdAndType, findAllVersions, findByVersion, updateApprovalStatus, getNextVersionNumber
    - _Requirements: 7.1, 7.2, 7.3, 7.4_
  - [x] 6.2 Update `server/.../db/pg/PgDocumentSql.kt` — replace UPSERT with INSERT (no ON CONFLICT), add SQL for: find latest approved or draft, find all versions, find by version number, update approval status, get next version number
    - _Requirements: 7.1, 7.2_
  - [x] 6.3 Update `server/.../db/pg/PgDocumentRepository.kt` — implement new methods, update save() to INSERT without upsert, update mapRow() to include approval/version fields
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 7. Server Module — JobManager Core Logic
  - [x] 7.1 Create `server/.../jobs/JobExecutor.kt` — extracts generation pipeline from DocumentRouteHandlers: aggregate → prompt → AI → parse → save. Updates job progress via JobRepository instead of DocumentStatusTracker. Saves document with approvalStatus=DRAFT
    - _Requirements: 2.3, 6.1_
  - [x] 7.2 Create `server/.../jobs/JobChainOrchestrator.kt` — manages Job_Chain lifecycle: when job COMPLETED → start next QUEUED job in chain, when FAILED → cancel remaining QUEUED/PAUSED jobs in chain
    - _Requirements: 1.4, 1.5, 3.4_
  - [x] 7.3 Create `server/.../jobs/DependencyChecker.kt` — pure function: given ticket's existing documents (with approval statuses), validates whether a requested documentType can be generated. BRD always allowed; FSD requires BRD with DRAFT or APPROVED; Slides requires BRD with DRAFT or APPROVED
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 7.4 Create `server/.../jobs/JobManager.kt` — singleton with CoroutineScope(Dispatchers.Default + SupervisorJob). Methods: createJob (with generation lock check), createChain (Generate All), executeJob (delegates to JobExecutor), pauseJob, resumeJob, cancelJob, recoverOnStartup (RUNNING → QUEUED). Inject: JobRepository, DocumentRepository, JobExecutor, JobChainOrchestrator, DependencyChecker
    - _Requirements: 2.2, 2.4, 2.7, 3.1, 3.2, 3.3, 3.5, 5.1_

- [x] 8. Server Module — Delete DocumentStatusTracker & Update DI
  - [x] 8.1 Delete `server/.../routes/DocumentStatusTracker.kt` — replaced by JobManager + JobRepository
    - _Requirements: (design decision 4)_
  - [x] 8.2 Update `server/.../di/PostgresModule.kt` — register JobRepository → PgJobRepository binding
    - _Requirements: 2.1_
  - [x] 8.3 Update `server/.../di/ServerModule.kt` — register JobManager, JobExecutor, JobChainOrchestrator, DependencyChecker as singletons
    - _Requirements: 2.2_

- [x] 9. Server Module — Update Document Routes & Add Job Routes
  - [x] 9.1 Update `server/.../routes/DocumentRoutes.kt` — refactor generate-brd/fsd/slides endpoints to create GenerationJob via JobManager (return jobId+status). Add: POST generate-all, GET active-jobs, GET documents/{type}/versions, GET documents/{type}/versions/{n}, GET documents/{type}/diff, POST /api/documents/{documentId}/approve, POST /api/documents/{documentId}/reject
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 6.6, 7.3, 7.4, 7.8_
  - [x] 9.2 Update `server/.../routes/DocumentRouteHandlers.kt` — refactor: remove generation pipeline logic (moved to JobExecutor), add handlers for approve (validate DRAFT, set APPROVED, assign version), reject (validate reason ≥10 chars, set REJECTED), versions list, version by number, diff between two versions
    - _Requirements: 6.4, 6.5, 7.2, 7.3, 7.4, 7.8_
  - [x] 9.3 Create `server/.../routes/JobRoutes.kt` — GET /api/jobs (with status filter: active/completed/all), GET /api/jobs/{jobId}, POST /api/jobs/{jobId}/pause, POST /api/jobs/{jobId}/resume, POST /api/jobs/{jobId}/cancel. Register in Ktor routing
    - _Requirements: 2.5, 2.6, 3.1, 3.2, 3.3, 3.5, 3.6_

- [x] 10. Checkpoint — Verify server compiles, routes registered, migration works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Frontend — Extended Document Models
  - [x] 11.1 Update `frontend/.../models/DocumentModels.kt` — update GeneratedDocumentMeta to include approvalStatus, versionNumber, hasDraft. Update GeneratedDocumentFull to include approvalStatus, versionNumber, rejectReason, reviewedBy, reviewedAt. Add GenerationJobDto (jobId, ticketId, documentType, status, progressPercent, phase, chainId, errorMessage). Add VersionMeta (versionNumber, generatedAt, reviewedBy, reviewedAt, aiProviderUsed)
    - _Requirements: 8.5, 8.6, 2.5, 7.3_

- [x] 12. Frontend — Global Job Indicator Component
  - [x] 12.1 Update `frontend/.../resources/templates/ticket-intelligence.html` — add GlobalJobIndicator area in navbar template (badge + dropdown panel with job list, pause/resume/cancel buttons per job). Add "Generate All" button with chain icon in docgen section. Add inline progress bar areas per document type. Add approval badge templates (DRAFT yellow, APPROVED green, REJECTED red). Add version history dropdown and approve/reject buttons in doc-preview-modal metadata bar. Add reject reason dialog
    - _Requirements: 4.1, 4.3, 1.6, 9.2, 9.4, 9.5, 9.6, 9.7, 6.2, 6.3, 7.6, 7.9_
  - [x] 12.2 Create `frontend/.../components/GlobalJobIndicator.kt` — renders badge on navbar showing active job count. Poll GET /api/jobs?status=active every 3s when jobs active. Show/hide badge based on active count. Click opens dropdown panel
    - _Requirements: 4.1, 4.2, 4.4_
  - [x] 12.3 Create `frontend/.../components/GlobalJobIndicatorPanel.kt` — dropdown panel listing active jobs with ticketId, documentType, status, progressPercent. Pause/Resume and Cancel buttons per job. Toast notifications for COMPLETED and FAILED jobs
    - _Requirements: 4.3, 4.5, 4.6, 3.6_

- [x] 13. Frontend — Review Panel & Version History
  - [x] 13.1 Create `frontend/.../pages/ticket/ReviewPanel.kt` — Approve and Reject buttons in doc-preview-modal metadata bar (visible only for DRAFT documents, hidden for Reader role). Reject shows dialog requiring reason ≥10 chars. POST /api/documents/{id}/approve or /reject. Toast on success
    - _Requirements: 6.3, 6.4, 6.5, 6.7, 6.8_
  - [x] 13.2 Create `frontend/.../pages/ticket/VersionHistoryPanel.kt` — dropdown in doc-preview-modal showing version list (GET versions endpoint). Click version loads that version's content with "Version N" label and "HISTORICAL" badge. "Compare Versions" button opens side-by-side diff view (GET diff endpoint). Only shown when ≥2 approved versions exist
    - _Requirements: 7.5, 7.6, 7.7, 7.8, 7.9_

- [x] 14. Frontend — Update Existing Components
  - [x] 14.1 Update `frontend/.../components/Navbar.kt` — add GlobalJobIndicator to nav-actions area
    - _Requirements: 4.1_
  - [x] 14.2 Update `frontend/.../pages/ticket/DocumentGenerationSection.kt` — add "Generate All" button (enabled when analyzed + no active jobs). Add inline progress bars for active jobs. Apply generation lock (disable buttons when active job exists, show "Đang sinh..." label). Show dependency tooltips ("Cần sinh BRD trước" on FSD/Slides when no BRD). Render approval badges (DRAFT/APPROVED/REJECTED) with correct colors. Call GET /api/analysis/{ticketId}/active-jobs on load
    - _Requirements: 1.6, 1.7, 5.2, 5.3, 5.5, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
  - [x] 14.3 Update `frontend/.../pages/ticket/DocumentGenerationFlow.kt` — refactor: POST generate returns jobId, poll via GET /api/jobs/{jobId} instead of document-status. Handle new response format. Support "Generate All" flow (POST generate-all, poll chain jobs)
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [x] 14.4 Update `frontend/.../pages/ticket/DocumentPreviewPanel.kt` — integrate ReviewPanel (approve/reject buttons for DRAFT docs). Integrate VersionHistoryPanel (version dropdown + diff view). Show active version by default, fallback to latest DRAFT
    - _Requirements: 6.3, 7.5, 7.6_

- [x] 15. Frontend — CSS Updates
  - [x] 15.1 Update `frontend/.../resources/ticket-intelligence.css` — add styles for: global job indicator badge + dropdown panel, inline job progress bars, approval badges (DRAFT yellow border rgba(255,180,50,0.3), APPROVED green border rgba(45,254,207,0.3), REJECTED red border rgba(255,80,80,0.3)), version history panel, side-by-side diff view, reject reason dialog, "Generate All" button
    - _Requirements: 9.5, 9.6, 9.7, 6.2_

- [x] 16. Checkpoint — Verify frontend compiles, UI renders correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 17. Property-Based Tests — Job State Machine & Dependencies
  - [x] 17.1 ⬡ PBT: Property 1 — Dependency chain enforcement
    - **Property 1: Dependency chain enforcement**
    - For any ticket state and requested document type, verify DependencyChecker allows FSD only when BRD with DRAFT/APPROVED exists, Slides only when BRD with DRAFT/APPROVED exists, BRD always allowed
    - **Validates: Requirements 1.1, 1.2, 1.3**
  - [x] 17.2 ⬡ PBT: Property 2 — Job chain creation structure
    - **Property 2: Job chain creation structure**
    - For any valid ticketId, createChain produces exactly 3 jobs [BRD, FSD, REQUIREMENT_SLIDES] sharing same chainId, first RUNNING, rest QUEUED
    - **Validates: Requirements 1.4**
  - [x] 17.3 ⬡ PBT: Property 3 — Chain failure and cancellation propagation
    - **Property 3: Chain failure and cancellation propagation**
    - For any chain where job at position K fails/cancelled, all QUEUED/PAUSED jobs after K become CANCELLED, COMPLETED jobs before K unchanged
    - **Validates: Requirements 1.5, 3.4**
  - [x] 17.4 ⬡ PBT: Property 4 — Job state transition validity
    - **Property 4: Job state transition validity**
    - For any GenerationJob status, only valid transitions allowed (QUEUED→RUNNING/PAUSED/CANCELLED, PAUSED→QUEUED/CANCELLED, RUNNING→COMPLETED/FAILED). All others rejected
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.5**
  - [x] 17.5 ⬡ PBT: Property 5 — GenerationJob serialization round-trip
    - **Property 5: GenerationJob serialization round-trip**
    - For any valid GenerationJob, serialize to JSON then deserialize produces equivalent object
    - **Validates: Requirements 2.1**
  - [x] 17.6 ⬡ PBT: Property 6 — New jobs always start as QUEUED
    - **Property 6: New jobs always start as QUEUED**
    - For any (ticketId, documentType), new job has status QUEUED, progressPercent 0, phase "QUEUED"
    - **Validates: Requirements 2.2**

- [x] 18. Property-Based Tests — Recovery, Filtering, Locking, Versioning
  - [x] 18.1 ⬡ PBT: Property 7 — Server recovery restores RUNNING jobs to QUEUED
    - **Property 7: Server recovery restores RUNNING jobs to QUEUED**
    - For any set of jobs with mixed statuses, after recovery all RUNNING become QUEUED, others unchanged
    - **Validates: Requirements 2.7**
  - [x] 18.2 ⬡ PBT: Property 8 — Job filter returns correct results
    - **Property 8: Job filter returns correct results**
    - For any list of jobs, "active" filter returns QUEUED/RUNNING/PAUSED, "completed" returns COMPLETED/FAILED/CANCELLED, "all" returns everything
    - **Validates: Requirements 2.5**
  - [x] 18.3 ⬡ PBT: Property 9 — Generation lock prevents duplicate jobs
    - **Property 9: Generation lock prevents duplicate jobs**
    - For any (ticketId, documentType) with active job, creating another for same pair rejected. Different pair succeeds
    - **Validates: Requirements 5.1, 5.4**
  - [x] 18.4 ⬡ PBT: Property 10 — Approve increments version number monotonically
    - **Property 10: Approve increments version number monotonically**
    - For N approve operations on same (ticketId, documentType), version_numbers are exactly 1, 2, ..., N
    - **Validates: Requirements 6.4, 7.2**
  - [x] 18.5 ⬡ PBT: Property 11 — Active version selection
    - **Property 11: Active version selection**
    - For any set of documents with mixed approval statuses, active version is highest version_number among APPROVED. If none approved, returns latest DRAFT
    - **Validates: Requirements 7.5, 8.5**

- [x] 19. Unit Tests — Example-Based
  - [x] 19.1 Test: Chain creation with valid ticket — verify 3 jobs created in order BRD→FSD→Slides
    - _Requirements: 1.4_
  - [x] 19.2 Test: FSD blocked without BRD — HTTP 400 response with correct message
    - _Requirements: 1.2_
  - [x] 19.3 Test: Slides blocked without BRD — HTTP 400 response with correct message
    - _Requirements: 1.3_
  - [x] 19.4 Test: Pause QUEUED job succeeds — status transitions to PAUSED
    - _Requirements: 3.1_
  - [x] 19.5 Test: Cancel RUNNING job returns 409 — Conflict response with status message
    - _Requirements: 3.5_
  - [x] 19.6 Test: Approve DRAFT document — status → APPROVED, version_number assigned
    - _Requirements: 6.4_
  - [x] 19.7 Test: Reject with short reason — HTTP 400 (reason < 10 chars)
    - _Requirements: 6.5_
  - [x] 19.8 Test: Reject with valid reason — status → REJECTED, reason saved
    - _Requirements: 6.5_
  - [x] 19.9 Test: Active version returns latest approved — correct document returned
    - _Requirements: 7.5_
  - [x] 19.10 Test: Active version falls back to draft — when no approved exists
    - _Requirements: 7.5_
  - [x] 19.11 Test: Diff between two versions — valid unified diff output
    - _Requirements: 7.8_
  - [x] 19.12 Test: Server recovery resets RUNNING to QUEUED — recovery function test
    - _Requirements: 2.7_
  - [x] 19.13 Test: Generation lock blocks duplicate — HTTP 409 with existing jobId
    - _Requirements: 5.1_
  - [x] 19.14 Test: Lock allows different ticket — no conflict for different ticketId
    - _Requirements: 5.4_

- [x] 20. Final Checkpoint — Full integration verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests (⬡) validate universal correctness properties from design.md
- Unit tests validate specific examples and edge cases
- `DocumentStatusTracker` is fully replaced by `JobManager` + `JobRepository` (task 8.1)
- Database migration (task 3.1) must run before any server persistence tasks
- Frontend tasks depend on server routes being available
