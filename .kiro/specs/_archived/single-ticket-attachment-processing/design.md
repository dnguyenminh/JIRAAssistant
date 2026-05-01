# Single Ticket Attachment Processing — Bugfix Design

## Overview

`AnalysisRoutes.runAnalysis()` thực hiện AI analysis cho single ticket nhưng KHÔNG gọi attachment processing sau đó. Trong khi `BatchScanEngine.processTicket()` luôn chạy `processAttachmentsIfAvailable()` ở Phase 3 sau AI analysis. Bug này khiến single ticket analyze không xử lý attachments — mất embedding data cho RAG/search.

Fix approach: Inject `AttachmentPipeline` và `JiraClient` vào `AnalysisRoutes`, gọi `processAttachments()` sau khi AI analysis hoàn tất thành công. Attachment failure không được fail analysis response. KB-First dedup trong `AttachmentPipeline` đã ngăn re-processing.

## Glossary

- **Bug_Condition (C)**: Single ticket analysis qua `AnalysisRoutes` cho ticket có attachments — attachments không được xử lý
- **Property (P)**: Sau AI analysis thành công, attachments SHALL được xử lý qua `AttachmentPipeline` (download → convert → chunk → embed → store)
- **Preservation**: Batch scan flow, no-attachment tickets, KB-First dedup, error handling — tất cả phải unchanged
- **runAnalysis()**: Private function trong `AnalysisRoutes.kt` thực hiện 4-phase analysis (FETCHING_JIRA → EXTRACTING_CONTENT → AI_ANALYZING → KB_SYNCING)
- **AttachmentPipeline**: Orchestrator class xử lý attachments: download → markitdown MCP → chunk → embed → store. Đã có KB-First dedup (`existsByAttachmentId`)
- **attachmentProcessor**: Lambda `(String, String, List<JiraAttachment>) -> Int` trong `BatchScanEngine` — wraps `AttachmentPipeline.processAttachments()`

## Bug Details

### Bug Condition

Bug xảy ra khi user analyze single ticket qua `AnalysisRoutes` (GET/POST `/api/analysis/{ticketId}`) — `runAnalysis()` chỉ gọi `orchestrator.analyzeTicket()` rồi return, KHÔNG gọi attachment processing. Trong khi `BatchScanEngine.processTicket()` luôn gọi `processAttachmentsIfAvailable()` ở Phase 3.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type AnalysisRequest
  OUTPUT: boolean
  
  RETURN input.source = "AnalysisRoutes.runAnalysis"
         AND ticketHasAttachments(input.ticketId)
         AND attachmentsNotProcessed(input.ticketId)
END FUNCTION
```

### Examples

- User GET `/api/analysis/PROJ-123` (ticket có 3 PDF attachments) → AI analysis completes, nhưng 0 attachment chunks trong vector store
- User POST `/api/analysis/PROJ-456/reanalyze` (ticket có 1 DOCX) → Reanalyze thành công, nhưng attachment DOCX không được convert/embed
- User xóa `attachment_chunks` rồi GET `/api/analysis/PROJ-789` → AI analysis OK, nhưng embeddings không được khôi phục (phải chạy batch scan)
- User GET `/api/analysis/PROJ-100` (ticket KHÔNG có attachments) → Hoạt động bình thường, không lỗi (edge case — không phải bug condition)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Batch scan qua `BatchScanEngine.processTicket()` tiếp tục xử lý attachments ở Phase 3 như hiện tại
- Tickets không có attachments hoàn thành analysis bình thường, không lỗi
- KB-First dedup trong `AttachmentPipeline` tiếp tục skip attachments đã tồn tại trong vector store
- AI analysis failure tiếp tục trả về error response, KHÔNG xử lý attachments cho ticket lỗi
- Attachments vượt 50MB size limit tiếp tục bị skip với log reason
- Analysis response format và status tracking (4-phase progress) không thay đổi

**Scope:**
Tất cả inputs KHÔNG liên quan đến single ticket analysis với attachments phải hoàn toàn không bị ảnh hưởng:
- Batch scan flow (`BatchScanEngine`)
- Analysis cho tickets không có attachments
- Analysis status polling (`GET /api/analysis/{ticketId}/status`)
- Các API routes khác (chat, estimation, graph, etc.)

## Hypothesized Root Cause

`AnalysisRoutes.runAnalysis()` được viết chỉ để thực hiện AI analysis — không có awareness về attachment processing:

1. **Missing dependency**: `runAnalysis()` không có access đến `AttachmentPipeline` hoặc `JiraClient` — chỉ inject `AIOrchestrator` qua Koin
2. **Missing call**: Sau `orchestrator.analyzeTicket()` thành công, không có code gọi attachment processing — function return ngay sau `KB_SYNCING` phase
3. **Design gap**: Khi `AttachmentPipeline` được implement (spec `attachment-processing`), nó chỉ được tích hợp vào `BatchScanEngine` — `AnalysisRoutes` bị bỏ sót

Root cause rõ ràng: thiếu integration code trong `runAnalysis()`. Không phải bug logic trong `AttachmentPipeline` hay `BatchScanEngine`.

## Correctness Properties

Property 1: Bug Condition — Single Ticket Analysis Processes Attachments

_For any_ single ticket analysis request qua `AnalysisRoutes` (GET hoặc POST) cho ticket có attachments, sau khi AI analysis hoàn tất thành công, `AttachmentPipeline.processAttachments()` SHALL được gọi với đúng `projectKey`, `ticketId`, và danh sách attachments từ Jira API. Số chunks trả về SHALL >= 0 (0 nếu tất cả đã dedup hoặc ineligible).

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation — Attachment Failure Does Not Fail Analysis

_For any_ single ticket analysis request mà attachment processing throws exception, analysis response SHALL vẫn trả về thành công với AI analysis result. Attachment error SHALL được log nhưng KHÔNG propagate lên caller.

**Validates: Requirements 3.4**

Property 3: Preservation — Batch Scan Unchanged

_For any_ batch scan request qua `BatchScanEngine`, attachment processing flow SHALL hoạt động identically với trước fix — `processAttachmentsIfAvailable()` vẫn được gọi ở Phase 3, không bị ảnh hưởng bởi changes trong `AnalysisRoutes`.

**Validates: Requirements 3.1**

Property 4: Preservation — KB-First Dedup Unchanged

_For any_ attachment đã tồn tại trong vector store (theo `attachmentId`), `AttachmentPipeline` SHALL skip processing (no download, no embed, no store) — behavior này unchanged cho cả single ticket và batch scan paths.

**Validates: Requirements 3.3**

## Fix Implementation

### Changes Required

Assuming root cause analysis đúng (thiếu integration code trong `AnalysisRoutes`):

**File**: `server/src/jvmMain/kotlin/com/assistant/server/routes/AnalysisRoutes.kt`

**Function**: `runAnalysis()` và `analysisRoutes()`

**Specific Changes**:

1. **Inject dependencies**: Trong `analysisRoutes()`, inject `AttachmentPipeline` và `JiraClient` (factory) qua Koin — tương tự pattern trong `ProjectRoutes` (`JiraCredentialsService` + `HttpClient` → tạo `JiraClient`)

2. **Pass dependencies to runAnalysis()**: Thêm `AttachmentPipeline` và `jiraClientProvider` lambda vào `runAnalysis()` parameters

3. **Add attachment processing after AI analysis**: Sau `AnalysisPhase.KB_SYNCING` thành công, gọi:
   - Fetch ticket details qua `JiraClient.getIssueDetails(ticketId)` để lấy attachments
   - Extract `projectKey` từ `ticketId` (phần trước `-`, e.g. `PROJ-123` → `PROJ`)
   - Gọi `attachmentPipeline.processAttachments(projectKey, ticketId, attachments)`

4. **Wrap in try-catch**: Attachment processing PHẢI được wrap trong try-catch riêng — failure chỉ log warning, KHÔNG throw lên caller. Analysis result vẫn return bình thường

5. **Run after analysis completes**: Attachment processing chạy SAU `AnalysisPhase.COMPLETE` update, TRƯỚC `finally` block remove status — đảm bảo analysis response không bị delay bởi attachment processing. Hoặc chạy fire-and-forget trong coroutine scope

**Pseudocode:**
```
FUNCTION runAnalysis'(ticketId, orchestrator, pipeline, jiraClientProvider, forceReanalyze)
  // ... existing 4-phase analysis ...
  result ← orchestrator.analyzeTicket(ticketId, forceReanalyze)
  updatePhase(KB_SYNCING)
  updatePhase(COMPLETE)
  
  // NEW: attachment processing (fire-and-forget, error-safe)
  TRY
    issue ← jiraClientProvider().getIssueDetails(ticketId)
    attachments ← issue?.fields?.attachment ?? emptyList
    IF attachments.isNotEmpty THEN
      projectKey ← ticketId.substringBefore("-")
      pipeline.processAttachments(projectKey, ticketId, attachments)
    END IF
  CATCH e
    log("[AnalysisRoutes] Attachment processing failed for $ticketId: ${e.message}")
  END TRY
  
  RETURN result
END FUNCTION
```

## Testing Strategy

### Validation Approach

Two-phase approach: (1) surface counterexamples trên unfixed code chứng minh bug tồn tại, (2) verify fix hoạt động đúng và preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Chứng minh bug tồn tại trên unfixed code — `runAnalysis()` KHÔNG gọi attachment processing.

**Test Plan**: Viết integration test mock `AttachmentPipeline` và verify nó KHÔNG được gọi khi `runAnalysis()` chạy trên unfixed code.

**Test Cases**:
1. **Single ticket with attachments**: Mock `AIOrchestrator` + `AttachmentPipeline`, gọi `runAnalysis()` → verify `processAttachments()` NOT called (will fail on unfixed code — confirms bug)
2. **Reanalyze with attachments**: POST `/api/analysis/{ticketId}/reanalyze` → verify attachments NOT processed (will fail on unfixed code)

**Expected Counterexamples**:
- `AttachmentPipeline.processAttachments()` never invoked from `runAnalysis()`
- Cause: missing integration code — `runAnalysis()` chỉ gọi `orchestrator.analyzeTicket()`

### Fix Checking

**Goal**: Verify rằng cho tất cả inputs thỏa bug condition, fixed `runAnalysis'()` gọi attachment processing.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := runAnalysis'(input.ticketId)
  ASSERT result.analysisCompleted = true
  ASSERT attachmentPipeline.processAttachments WAS CALLED
    WITH (projectKey, ticketId, attachments)
END FOR
```

### Preservation Checking

**Goal**: Verify rằng cho tất cả inputs KHÔNG thỏa bug condition, fixed function hoạt động identically.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT runAnalysis(input) = runAnalysis'(input)
END FOR
```

**Testing Approach**: Property-based testing recommended cho preservation checking vì:
- Generate nhiều test cases tự động (tickets không có attachments, AI analysis failures, etc.)
- Catch edge cases mà manual tests có thể miss
- Strong guarantees rằng behavior unchanged cho non-buggy inputs

**Test Plan**: Observe behavior trên unfixed code cho non-attachment cases, viết property tests capturing behavior đó.

**Test Cases**:
1. **No-attachment ticket preservation**: Verify analysis cho tickets không có attachments hoạt động identically trước và sau fix
2. **AI analysis failure preservation**: Verify khi `orchestrator.analyzeTicket()` throws, response vẫn là error và attachments KHÔNG được xử lý
3. **Status tracking preservation**: Verify 4-phase progress tracking (FETCHING_JIRA → COMPLETE) không thay đổi
4. **Batch scan preservation**: Verify `BatchScanEngine.processTicket()` flow không bị ảnh hưởng

### Unit Tests

- Test `runAnalysis()` gọi `processAttachments()` sau AI analysis thành công
- Test `runAnalysis()` KHÔNG gọi `processAttachments()` khi ticket không có attachments
- Test `runAnalysis()` KHÔNG gọi `processAttachments()` khi AI analysis fails
- Test attachment processing failure không fail analysis response
- Test `projectKey` extraction từ `ticketId` (e.g. `PROJ-123` → `PROJ`)

### Property-Based Tests

- Generate random ticketIds và verify `projectKey` extraction luôn đúng (substringBefore `-`)
- Generate random attachment lists (0-N items, mixed eligible/ineligible) và verify pipeline called correctly
- Generate random AI analysis outcomes (success/failure) và verify attachment processing chỉ chạy khi success

### Integration Tests

- Full flow: mock Jira API trả về ticket với attachments → verify `AttachmentPipeline.processAttachments()` called với đúng args
- Error isolation: mock `AttachmentPipeline` throw exception → verify analysis response vẫn OK
- Koin DI: verify `AttachmentPipeline` và `JiraClient` injectable trong `analysisRoutes()` context
