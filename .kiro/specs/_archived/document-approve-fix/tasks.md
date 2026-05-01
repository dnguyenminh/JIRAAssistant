# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Approve/Reject sends composite string instead of numeric database ID
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Scope the property to concrete failing cases:
    - `extractDocId(doc)` returns composite string `"ticketId:docType"` (e.g., `"ICL2-15:BRD"`) instead of numeric ID
    - `GeneratedDocument` deserialized from API response has `id = null` (field missing)
    - `POST /api/documents/{compositeString}/approve` → `toLongOrNull()` returns `null` → silent `return@post`
  - Test that for any `GeneratedDocumentFull` received from API, `extractDocId(doc)` returns a value where `toLongOrNull() != null` (expected behavior)
  - Test that for any document with a valid DB primary key, `POST /api/documents/{docId}/approve` returns HTTP 200 with `approvalStatus = "APPROVED"`
  - Test that `GeneratedDocument` serialized from backend includes non-null `id` field
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists):
    - `extractDocId()` returns `"ICL2-15:BRD"` which fails `toLongOrNull()`
    - API response JSON lacks `id` field → deserialized `id` is `null`
    - Approve route silently returns without response when given composite string
  - Document counterexamples found to understand root cause
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.3, 2.4, 2.5_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-approve/reject document operations unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **Step 1 — Observe on UNFIXED code**:
    - Observe: `GET /api/analysis/{ticketId}/documents` returns list of `GeneratedDocumentMeta` with fields `documentType`, `generatedAt`, `aiProviderUsed`, `approvalStatus`, `versionNumber`, `hasDraft`
    - Observe: `GET /api/analysis/{ticketId}/documents/{type}` returns `GeneratedDocument` with all existing fields populated correctly
    - Observe: `GET /api/analysis/{ticketId}/documents/{type}/versions` returns list of version metadata
    - Observe: `POST /api/analysis/{ticketId}/generate-brd` creates document and returns `jobId` + `status`
    - Observe: `POST /api/documents/{id}/approve` on non-DRAFT document returns HTTP 409 Conflict
    - Observe: `GeneratedDocument` JSON serialization roundtrip preserves all existing fields (`documentType`, `ticketId`, `markdownContent`, `approvalStatus`, `versionNumber`, `rejectReason`, `reviewedBy`, `reviewedAt`, `sourceTicketIds`, `attachmentSources`, `aiProviderUsed`, `generatedAt`)
  - **Step 2 — Write property-based tests**:
    - Property: For all `GeneratedDocument` instances, JSON serialize → deserialize roundtrip preserves all existing fields (backward compatibility with `id: Long? = null`)
    - Property: For all non-approve/reject API calls (list, get, versions, generate), response structure and content are unchanged
    - Property: For all documents with `approvalStatus != "DRAFT"`, approve/reject returns HTTP 409
    - Property: `mapRow()` correctly maps all existing columns (`document_type`, `ticket_id`, `generated_at`, `markdown_content`, `source_ticket_ids`, `attachment_sources`, `ai_provider_used`, `approval_status`, `version_number`, `reject_reason`, `reviewed_by`, `reviewed_at`)
  - Verify tests pass on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 3. Fix for document approve/reject sending composite string instead of numeric database ID

  - [x] 3.1 Add `id: Long? = null` field to shared `GeneratedDocument` model
    - File: `shared/src/commonMain/kotlin/com/assistant/document/models/GeneratedDocument.kt`
    - Add `val id: Long? = null` as first parameter in data class constructor
    - Default `null` ensures backward compatibility — existing JSON without `id` deserializes correctly
    - _Bug_Condition: isBugCondition(input) where GeneratedDocument lacks `id` field → frontend cannot extract numeric ID_
    - _Expected_Behavior: GeneratedDocument.id is populated from DB primary key, serialized in API responses_
    - _Preservation: All existing fields unchanged, `id = null` default is backward-compatible_
    - _Requirements: 2.1, 3.7_

  - [x] 3.2 Add `id: Long? = null` field to frontend `GeneratedDocumentFull` model
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/DocumentModels.kt`
    - Add `val id: Long? = null` as first parameter in `GeneratedDocumentFull` data class constructor
    - Frontend deserializer (`ignoreUnknownKeys = true`) will now pick up `id` from API response
    - _Bug_Condition: GeneratedDocumentFull lacks `id` → extractDocId() falls back to composite string_
    - _Expected_Behavior: GeneratedDocumentFull.id populated from API response JSON_
    - _Preservation: All existing fields unchanged_
    - _Requirements: 2.4, 3.7_

  - [x] 3.3 Map `id` column in `PgDocumentRepository.mapRow()`
    - File: `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgDocumentRepository.kt`
    - Add `id = rs.getLong("id")` to the `GeneratedDocument` constructor call in `mapRow()`
    - This ensures every document loaded from DB has its primary key populated
    - _Bug_Condition: mapRow() does not read `id` column → GeneratedDocument.id always null_
    - _Expected_Behavior: mapRow() maps `id` column → GeneratedDocument.id = database primary key_
    - _Preservation: All other column mappings unchanged_
    - _Requirements: 2.1, 2.3_

  - [x] 3.4 Fix `extractDocId()` to return `doc.id?.toString()`
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DocumentPreviewPanel.kt`
    - Change `extractDocId()` from `return "${doc.ticketId}:${doc.documentType}"` to `return doc.id?.toString()`
    - When `id` is `null` (edge case: newly generated doc not yet persisted), returns `null` → `ReviewPanel` hides approve/reject buttons (acceptable behavior per design)
    - _Bug_Condition: extractDocId() returns composite string "ticketId:docType" → toLongOrNull() fails_
    - _Expected_Behavior: extractDocId() returns numeric ID string → toLongOrNull() succeeds_
    - _Preservation: When id is null, ReviewPanel hides buttons (same as current behavior for missing docId)_
    - _Requirements: 2.4, 2.5, 2.6_

  - [x] 3.5 Add error response when `toLongOrNull()` fails in approve/reject routes
    - File: `server/src/jvmMain/kotlin/com/assistant/server/routes/DocumentRoutes.kt`
    - Change approve route from `val docId = call.parameters["documentId"]?.toLongOrNull() ?: return@post` to respond with `HttpStatusCode.BadRequest` and `ErrorResponse("Invalid document ID: must be a numeric value")`
    - Apply same change to reject route
    - This prevents silent failure — frontend receives clear error message instead of empty response
    - _Bug_Condition: toLongOrNull() fails on composite string → silent return@post with no response_
    - _Expected_Behavior: Backend responds with 400 Bad Request and descriptive error message_
    - _Preservation: Valid numeric IDs continue to work as before_
    - _Requirements: 2.5, 2.6_

  - [x] 3.6 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Approve/Reject uses numeric database ID
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied:
      - `extractDocId()` returns numeric ID string (e.g., `"42"`)
      - `GeneratedDocument` from API includes non-null `id` field
      - `POST /api/documents/{numericId}/approve` returns HTTP 200 with `approvalStatus = "APPROVED"`
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.7 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-approve/reject document operations unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 4. Checkpoint - Ensure all tests pass
  - Run full test suite to verify no regressions
  - Verify bug condition exploration test passes (Property 1)
  - Verify preservation property tests pass (Property 2)
  - Verify serialization backward compatibility (JSON with and without `id` field)
  - Ensure all tests pass, ask the user if questions arise
