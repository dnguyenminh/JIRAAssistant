# Document Approve/Reject Bugfix Design

## Overview

Khi người dùng click "APPROVE" hoặc "REJECT" trên document DRAFT, thao tác thất bại vì frontend không có database primary key (`id: Long`) của document. Frontend tự tạo composite string `"ticketId:docType"` (ví dụ `"ICL2-15:BRD"`) làm `documentId`, nhưng backend route `POST /api/documents/{documentId}/approve` yêu cầu `documentId` là `Long`. Khi `toLongOrNull()` parse composite string → `null` → route `return@post` không trả response → frontend hiển thị lỗi.

Fix approach: Thêm `id: Long? = null` vào shared model `GeneratedDocument` và frontend model `GeneratedDocumentFull`, populate từ DB trong `PgDocumentRepository.mapRow()`, và sửa `DocumentPreviewPanel.extractDocId()` trả về `doc.id?.toString()`. Thêm error response khi `toLongOrNull()` fails trong `DocumentRoutes.kt`.

## Glossary

- **Bug_Condition (C)**: Frontend gửi approve/reject request với composite string `"ticketId:docType"` thay vì numeric database ID → backend `toLongOrNull()` trả về `null`
- **Property (P)**: Approve/reject request phải dùng numeric database ID (`Long`) → backend xử lý thành công và trả về document đã cập nhật
- **Preservation**: Tất cả document operations khác (generate, list, get, versions, diff) phải hoạt động đúng như trước
- **GeneratedDocument**: Shared data class trong `shared/` module, serialize/deserialize qua JSON giữa backend và frontend
- **GeneratedDocumentFull**: Frontend-only data class mirror của `GeneratedDocument`, dùng cho UI rendering
- **extractDocId()**: Method trong `DocumentPreviewPanel` trích xuất document ID để gửi approve/reject API call
- **mapRow()**: Method trong `PgDocumentRepository` map `ResultSet` row thành `GeneratedDocument` object

## Bug Details

### Bug Condition

Bug xảy ra khi user click APPROVE hoặc REJECT trên document DRAFT. `DocumentPreviewPanel.extractDocId()` luôn trả về composite string `"${doc.ticketId}:${doc.documentType}"` vì `GeneratedDocumentFull` không có field `id`. Backend route parse `documentId` bằng `toLongOrNull()` → `null` → `return@post` không response.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ApproveRejectRequest
  OUTPUT: boolean

  documentId ← extractDocId(input.document)
  RETURN documentId contains non-numeric characters (e.g., ":" character)
         AND toLongOrNull(documentId) = null
END FUNCTION
```

### Examples

- User click APPROVE trên BRD của ticket ICL2-15 → frontend gửi `POST /api/documents/ICL2-15:BRD/approve` → backend `toLongOrNull("ICL2-15:BRD")` = `null` → `return@post` → no response → frontend hiển thị "❌ Approve thất bại"
- User click REJECT trên FSD của ticket PROJ-42 → frontend gửi `POST /api/documents/PROJ-42:FSD/reject` → tương tự fail
- User click APPROVE trên REQUIREMENT_SLIDES → `POST /api/documents/ICL2-15:REQUIREMENT_SLIDES/approve` → fail
- Edge case: Document mới vừa generate chưa có `id` trong response → `extractDocId()` trả về `null` → ReviewPanel ẩn buttons (acceptable behavior)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `GET /api/analysis/{ticketId}/documents` tiếp tục trả về danh sách metadata documents
- `GET /api/analysis/{ticketId}/documents/{type}` tiếp tục trả về full document content (giờ có thêm field `id`)
- `GET /api/analysis/{ticketId}/documents/{type}/versions` tiếp tục trả về danh sách versions
- `POST /api/analysis/{ticketId}/generate-brd` và `generate-fsd` tiếp tục tạo document thành công
- Document có `approvalStatus` khác "DRAFT" tiếp tục bị từ chối approve/reject với HTTP 409
- User role Reader tiếp tục không thấy approve/reject buttons
- JSON serialization/deserialization backward-compatible vì `id` là nullable với default `null`

**Scope:**
Tất cả inputs không liên quan đến approve/reject flow không bị ảnh hưởng. Field `id: Long? = null` mới là nullable với default value nên backward-compatible với existing JSON payloads.

## Hypothesized Root Cause

Based on code analysis, root causes đã được xác nhận và đã fix:

1. **Missing `id` field in shared model** ✅ FIXED: `GeneratedDocument` data class trong `shared/` giờ có field `val id: Long? = null` là parameter đầu tiên. Default `null` đảm bảo backward-compatible.

2. **Missing `id` mapping in `mapRow()`** ✅ FIXED: `PgDocumentRepository.mapRow()` giờ đọc `id = rs.getLong("id")` từ `ResultSet` → `GeneratedDocument` objects luôn có database ID.

3. **Missing `id` field in frontend model** ✅ FIXED: `GeneratedDocumentFull` trong frontend giờ có field `val id: Long? = null` → khi deserialize API response, `id` được populate.

4. **Composite key workaround in `extractDocId()`** ✅ FIXED: `DocumentPreviewPanel.extractDocId()` giờ trả về `doc.id?.toString()` thay vì composite string `"${doc.ticketId}:${doc.documentType}"`.

5. **Silent failure in route** ✅ FIXED: `DocumentRoutes.kt` giờ dùng `call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID: must be a numeric value"))` thay vì silent `return@post`.

## Correctness Properties

Property 1: Bug Condition - Approve/Reject uses numeric database ID

_For any_ document that exists in the database with a valid primary key, when the frontend calls approve or reject, the request SHALL use the numeric database ID (Long) as the `documentId` path parameter, and the backend SHALL successfully process the request and return the updated document with new `approvalStatus`.

**Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.6**

Property 2: Preservation - Non-approve/reject document operations unchanged

_For any_ document operation that is NOT approve/reject (generate, list, get by type, get versions, get by version, diff), the fixed code SHALL produce the same results as the original code, preserving all existing API behavior. The addition of `id` field in JSON responses is additive and backward-compatible.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

**File**: `shared/src/commonMain/kotlin/com/assistant/document/models/GeneratedDocument.kt`

**Change**: Add `id: Long? = null` field to `GeneratedDocument` data class
- Add as first parameter with default `null` for backward compatibility
- Existing JSON without `id` field will deserialize correctly (default `null`)

**File**: `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgDocumentRepository.kt`

**Function**: `mapRow()`

**Change**: Map `id` column from `ResultSet` into `GeneratedDocument.id`
- Add `id = rs.getLong("id")` to the `GeneratedDocument` constructor call in `mapRow()`

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/DocumentModels.kt`

**Change**: Add `id: Long? = null` field to `GeneratedDocumentFull` data class
- Add as first parameter with default `null` for backward compatibility

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DocumentPreviewPanel.kt`

**Function**: `extractDocId()`

**Change**: Return `doc.id?.toString()` instead of composite string
- When `id` is `null` (edge case), return `null` → ReviewPanel hides approve/reject buttons

**File**: `server/src/jvmMain/kotlin/com/assistant/server/routes/DocumentRoutes.kt`

**Change**: Add error response when `toLongOrNull()` fails for approve/reject routes
- Instead of silent `return@post`, respond with `HttpStatusCode.BadRequest` and error message

## Testing Strategy

### Validation Approach

Testing follows two phases: first surface counterexamples demonstrating the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples demonstrating the bug BEFORE implementing the fix. Confirm root cause analysis.

**Test Plan**: Call approve/reject API endpoints with composite string IDs (simulating current frontend behavior). Verify that backend returns no response or error.

**Test Cases**:
1. **Composite ID Approve Test**: `POST /api/documents/ICL2-15:BRD/approve` → verify silent failure (will fail on unfixed code)
2. **Composite ID Reject Test**: `POST /api/documents/PROJ-42:FSD/reject` → verify silent failure (will fail on unfixed code)
3. **Missing ID in Response Test**: `GET /api/analysis/{ticketId}/documents/{type}` → verify response JSON lacks `id` field (will fail on unfixed code)
4. **Frontend extractDocId Test**: Verify `extractDocId()` returns composite string instead of numeric ID (will fail on unfixed code)

**Expected Counterexamples**:
- Backend route returns no HTTP response when `toLongOrNull()` fails on composite string
- API response JSON does not contain `id` field
- Possible causes: missing `id` field in model, missing `id` mapping in `mapRow()`

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  // After fix: GeneratedDocument has id field populated from DB
  doc ← GET /api/analysis/{ticketId}/documents/{type}
  ASSERT doc.id != null AND doc.id > 0
  docId ← extractDocId'(doc)  // F' returns numeric ID string
  ASSERT docId.toLongOrNull() != null
  result ← POST /api/documents/{docId}/approve
  ASSERT result.status = 200 AND result.body.approvalStatus = "APPROVED"
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT F(input) = F'(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for document list/get/versions/diff operations, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Document List Preservation**: Verify `GET /api/analysis/{ticketId}/documents` returns same metadata list (with additive `id` field)
2. **Document Get Preservation**: Verify `GET /api/analysis/{ticketId}/documents/{type}` returns same content
3. **Version History Preservation**: Verify versions endpoint returns same data
4. **Generate Preservation**: Verify document generation still works correctly
5. **Non-DRAFT Reject Preservation**: Verify APPROVED documents still return 409 on approve/reject

### Unit Tests

- Test `GeneratedDocument` serialization/deserialization with and without `id` field
- Test `PgDocumentRepository.mapRow()` correctly maps `id` column
- Test `extractDocId()` returns numeric ID string when `id` is present
- Test `extractDocId()` returns `null` when `id` is `null`
- Test approve route returns 400 when `documentId` is not a valid Long
- Test approve route returns 200 with updated document when `documentId` is valid

### Property-Based Tests

- Generate random `GeneratedDocument` instances with various `id` values (null, positive Long) and verify serialization roundtrip preserves all fields
- Generate random document types and ticket IDs, verify `extractDocId()` always returns numeric string or null (never composite)
- Generate random non-approve/reject API calls and verify responses are unchanged

### Integration Tests

- Full flow: generate document → get document (verify `id` present) → approve document → verify status APPROVED
- Full flow: generate document → get document → reject document with reason → verify status REJECTED
- Verify frontend `ReviewPanel` correctly uses numeric ID from `GeneratedDocumentFull.id`
