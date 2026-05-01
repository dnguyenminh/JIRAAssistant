# Bugfix Requirements Document

## Introduction

Khi người dùng click nút "APPROVE" hoặc "REJECT" trên một tài liệu BRD/FSD đang ở trạng thái DRAFT trong trang Ticket Intelligence, thao tác thất bại với thông báo "❌ Approve thất bại". Nguyên nhân gốc là frontend không có database ID (Long) của document — model `GeneratedDocument` và `GeneratedDocumentFull` thiếu field `id`. Frontend phải tự tạo composite key dạng `"ticketId:documentType"` (ví dụ `"ICL2-15:BRD"`), nhưng backend route `POST /api/documents/{documentId}/approve` yêu cầu `documentId` là `Long`. Khi `toLongOrNull()` parse composite string → trả về `null` → route `return@post` không response → frontend hiển thị lỗi.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN người dùng click "APPROVE" trên một document DRAFT THEN hệ thống gửi request `POST /api/documents/ICL2-15:BRD/approve` với composite string key thay vì numeric database ID, backend `toLongOrNull()` trả về `null`, route thực hiện `return@post` không trả response, và frontend hiển thị "❌ Approve thất bại"

1.2 WHEN người dùng click "REJECT" trên một document DRAFT và nhập lý do reject THEN hệ thống gửi request `POST /api/documents/ICL2-15:BRD/reject` với composite string key, backend `toLongOrNull()` trả về `null`, route thực hiện `return@post` không trả response, và frontend hiển thị "❌ Reject thất bại"

1.3 WHEN backend trả về `GeneratedDocument` qua API response (GET documents, GET documents/{type}) THEN response JSON không chứa field `id` (database primary key), khiến frontend không có cách nào lấy được numeric ID cần thiết cho approve/reject

1.4 WHEN `PgDocumentRepository.save()` lưu document mới vào database THEN method trả về `Unit` — tuy nhiên đây không phải root cause vì frontend lấy `id` từ API response khi GET document (qua `mapRow()`), không cần `id` ngay sau `save()`

### Expected Behavior (Correct)

2.1 WHEN backend trả về `GeneratedDocument` qua bất kỳ API response nào THEN response JSON SHALL chứa field `id` (Long, nullable) là database primary key từ bảng `generated_documents`

2.2 ~~REMOVED~~ — `PgDocumentRepository.save()` vẫn trả về `Unit`. Không cần thay đổi vì frontend lấy `id` từ API response (qua `mapRow()` khi GET document), không cần `id` ngay sau `save()`.

2.3 WHEN `PgDocumentRepository.mapRow()` đọc document từ ResultSet THEN hệ thống SHALL map column `id` vào field `id` của `GeneratedDocument`

2.4 WHEN frontend nhận `GeneratedDocumentFull` từ API THEN model SHALL chứa field `id` (Long, nullable) và `DocumentPreviewPanel.extractDocId()` SHALL trả về `id.toString()` thay vì composite string

2.5 WHEN người dùng click "APPROVE" trên một document DRAFT THEN hệ thống SHALL gửi request `POST /api/documents/{numericId}/approve` với numeric database ID, backend xử lý thành công và trả về document đã cập nhật status "APPROVED"

2.6 WHEN người dùng click "REJECT" trên một document DRAFT và nhập lý do reject hợp lệ (≥10 ký tự) THEN hệ thống SHALL gửi request `POST /api/documents/{numericId}/reject` với numeric database ID, backend xử lý thành công và trả về document đã cập nhật status "REJECTED"

### Unchanged Behavior (Regression Prevention)

3.1 WHEN người dùng gọi `GET /api/analysis/{ticketId}/documents` THEN hệ thống SHALL CONTINUE TO trả về danh sách metadata documents cho ticket đó

3.2 WHEN người dùng gọi `GET /api/analysis/{ticketId}/documents/{type}` THEN hệ thống SHALL CONTINUE TO trả về full document content cho document type chỉ định

3.3 WHEN người dùng gọi `GET /api/analysis/{ticketId}/documents/{type}/versions` THEN hệ thống SHALL CONTINUE TO trả về danh sách versions của document

3.4 WHEN người dùng gọi `POST /api/analysis/{ticketId}/generate-brd` hoặc `generate-fsd` THEN hệ thống SHALL CONTINUE TO tạo document mới và lưu vào database thành công

3.5 WHEN document có `approvalStatus` khác "DRAFT" (ví dụ "APPROVED" hoặc "REJECTED") THEN hệ thống SHALL CONTINUE TO từ chối approve/reject với HTTP 409 Conflict

3.6 WHEN người dùng có vai trò Reader THEN ReviewPanel SHALL CONTINUE TO ẩn các nút approve/reject

3.7 WHEN `GeneratedDocument` được serialize/deserialize qua JSON THEN tất cả existing fields (documentType, ticketId, markdownContent, approvalStatus, versionNumber, v.v.) SHALL CONTINUE TO hoạt động đúng — field `id` mới là nullable và có default `null` nên backward-compatible

---

### Bug Condition (Formal)

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type ApproveRejectRequest
  OUTPUT: boolean
  
  // The bug triggers when frontend attempts approve/reject,
  // because extractDocId() always returns a composite string "ticketId:docType"
  // instead of a numeric database ID
  RETURN X.documentId is NOT a valid Long (i.e., contains ":" character)
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — Approve/Reject uses numeric database ID
FOR ALL X WHERE isBugCondition(X) DO
  // After fix: GeneratedDocument has id field, frontend uses it
  docId ← extractDocId'(X.document)  // F' returns numeric ID string
  ASSERT docId.toLongOrNull() != null
  result ← POST /api/documents/{docId}/approve
  ASSERT result.status = 200 AND result.body.approvalStatus = "APPROVED"
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — All other document operations unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  // Non-approve/reject operations: generate, list, get, versions, diff
  ASSERT F(X) = F'(X)
END FOR
```
