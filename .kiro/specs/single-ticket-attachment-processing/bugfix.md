# Bugfix Requirements Document

## Introduction

Khi user chạy "analyze" single ticket từ dashboard (qua `AnalysisRoutes`), hệ thống chỉ thực hiện AI analysis mà KHÔNG xử lý attachments. Trong khi đó, batch scan (`BatchScanEngine.processTicket`) luôn xử lý attachments ở Phase 3 sau AI analysis. Điều này dẫn đến inconsistency: nếu user xóa `attachment_chunks` rồi chạy single ticket analyze, attachments không được xử lý lại — mất dữ liệu embedding cho RAG/search.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN user triggers single ticket analysis via `POST /api/analysis/{ticketId}/reanalyze` hoặc `GET /api/analysis/{ticketId}` THEN the system chỉ gọi `orchestrator.analyzeTicket()` rồi return mà KHÔNG gọi attachment processing

1.2 WHEN ticket có attachments và user chạy single ticket analyze THEN the system KHÔNG download, convert, chunk, embed, hoặc store bất kỳ attachment nào

1.3 WHEN user xóa attachment_chunks (vector store data) rồi chạy single ticket analyze THEN the system KHÔNG xử lý lại attachments, dẫn đến mất dữ liệu embedding vĩnh viễn cho đến khi chạy batch scan

### Expected Behavior (Correct)

2.1 WHEN user triggers single ticket analysis via `POST /api/analysis/{ticketId}/reanalyze` hoặc `GET /api/analysis/{ticketId}` THEN the system SHALL thực hiện attachment processing sau khi AI analysis hoàn tất, consistent với batch scan flow

2.2 WHEN ticket có attachments và user chạy single ticket analyze THEN the system SHALL xử lý attachments qua AttachmentPipeline (download → convert → chunk → embed → store)

2.3 WHEN user xóa attachment_chunks rồi chạy single ticket analyze THEN the system SHALL xử lý lại attachments và khôi phục dữ liệu embedding

### Unchanged Behavior (Regression Prevention)

3.1 WHEN user chạy batch scan qua BatchScanEngine THEN the system SHALL CONTINUE TO xử lý attachments ở Phase 3 sau AI analysis như hiện tại

3.2 WHEN ticket KHÔNG có attachments và user chạy single ticket analyze THEN the system SHALL CONTINUE TO hoàn thành analysis bình thường mà không lỗi

3.3 WHEN attachment đã được xử lý và tồn tại trong vector store (KB-First check) THEN the system SHALL CONTINUE TO skip attachment đó thay vì xử lý lại (dedup behavior của AttachmentPipeline)

3.4 WHEN AI analysis fails THEN the system SHALL CONTINUE TO trả về error response mà không crash, và KHÔNG xử lý attachments cho ticket bị lỗi

3.5 WHEN attachment không eligible (vượt 50MB size limit) THEN the system SHALL CONTINUE TO skip attachment đó và log reason

---

## Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type AnalysisRequest
  OUTPUT: boolean
  
  // Bug triggers when analysis is invoked via single ticket route (AnalysisRoutes)
  // rather than batch scan (BatchScanEngine)
  RETURN X.source = "single_ticket_analysis" AND X.ticket.hasAttachments = true
END FUNCTION
```

## Property Specification

```pascal
// Property: Fix Checking — Single ticket analysis processes attachments
FOR ALL X WHERE isBugCondition(X) DO
  result ← runAnalysis'(X.ticketId)
  attachmentChunks ← vectorStore.getChunksByTicket(X.ticketId)
  ASSERT result.analysisCompleted = true
    AND attachmentChunks.count >= expectedChunksFor(X.ticket.attachments)
END FOR
```

## Preservation Goal

```pascal
// Property: Preservation Checking — Batch scan and no-attachment cases unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```
