# Implementation Plan: Linked Ticket Attachments

## Overview

Mở rộng attachment processing pipeline để xử lý attachments từ tất cả tickets trong TicketGraph — bao gồm root ticket và linked tickets. Đồng thời bổ sung `comments` và `attachments` vào `LinkedTicketContent` model để AI prompt có đầy đủ ngữ cảnh. Implementation theo thứ tự: model changes → extraction updates → prompt updates → processing logic → integration → tests.

## Tasks

- [x] 1. Mở rộng data models
  - [x] 1.1 Mở rộng `AttachmentInfo` thêm `id` và `content` fields
    - Thêm `val id: String = ""` và `val content: String = ""` (download URL) vào `AttachmentInfo` trong `shared/src/commonMain/kotlin/com/assistant/ai/deepanalysis/models/TicketContentModels.kt`
    - Giữ default values `""` để backward compatible với existing deserialization
    - _Requirements: 1.2, 3.1_

  - [x] 1.2 Mở rộng `LinkedTicketContent` thêm `comments` và `attachments` fields
    - Thêm `val comments: List<CommentInfo> = emptyList()` và `val attachments: List<AttachmentInfo> = emptyList()` vào `LinkedTicketContent` trong `shared/src/commonMain/kotlin/com/assistant/ai/deepanalysis/models/TicketContentModels.kt`
    - Default `emptyList()` đảm bảo backward compatibility — existing code và old JSON deserialization không bị ảnh hưởng
    - _Requirements: 11.1, 11.2, 11.5_

  - [x] 1.3 Tạo `TicketAttachmentGroup` model
    - Tạo file `server/src/jvmMain/kotlin/com/assistant/server/attachment/models/TicketAttachmentGroup.kt`
    - Data class chứa: `ticketId`, `projectKey`, `depth`, `relevanceScore`, `attachments: List<JiraAttachment>`
    - _Requirements: 1.2, 3.4_

  - [x] 1.4 Tạo extension function `AttachmentInfo.toJiraAttachment()`
    - Tạo file `server/src/jvmMain/kotlin/com/assistant/server/attachment/AttachmentInfoExtensions.kt`
    - Convert `AttachmentInfo` (from StructuredTicketContent) sang `JiraAttachment` (for AttachmentPipeline)
    - _Requirements: 4.1_

- [x] 2. Cập nhật extraction layer — `DeepJiraContentExtractor`
  - [x] 2.1 Cập nhật `nodeToLinkedContent()` để map `comments` và `attachments`
    - Trong `server/src/jvmMain/kotlin/com/assistant/server/document/DeepJiraContentExtractor.kt`, cập nhật `nodeToLinkedContent(node)` thêm `comments = node.issue.comments` và `attachments = node.issue.attachments`
    - _Requirements: 11.3_

  - [ ]* 2.2 Write property test cho `nodeToLinkedContent` preserves comments and attachments
    - **Property 8: nodeToLinkedContent Preserves Comments and Attachments**
    - **Validates: Requirements 11.3**

- [x] 3. Cập nhật prompt layer — `appendLinkedTicketsContext()`
  - [x] 3.1 Cập nhật `appendLinkedTicketsContext()` để include comments và attachment filenames
    - Trong `shared/src/commonMain/kotlin/com/assistant/ai/deepanalysis/PromptSectionTicketData.kt`, thêm section hiển thị comments (author, date, content) và attachments (filename, mimeType, size) cho mỗi linked ticket
    - _Requirements: 11.4_

  - [ ]* 3.2 Write property test cho prompt includes linked ticket comments and attachments
    - **Property 9: Prompt Includes Linked Ticket Comments and Attachments**
    - **Validates: Requirements 11.4**

- [x] 4. Checkpoint — Ensure model và extraction changes compile
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement `LinkedAttachmentProcessor` — core logic
  - [x] 5.1 Tạo `LinkedAttachmentProcessor` class với `collectAttachments()` method
    - Tạo file `server/src/jvmMain/kotlin/com/assistant/server/attachment/LinkedAttachmentProcessor.kt`
    - Constructor nhận: `attachmentPipeline`, `vectorStore`, `collectionJobManager`, `scanLogRepository`
    - Implement `collectAttachments(graph, rootTicketId)`: duyệt tất cả `TicketNode` trong graph, thu thập attachments, dedup theo `attachmentId`, KB-First dedup via `vectorStore.existsByAttachmentId()`, sắp xếp theo depth ascending + relevanceScore descending (root first)
    - Log tổng kết collection: total attachments, ticket count, new vs skipped
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.4_

  - [ ]* 5.2 Write property test cho attachment collection completeness
    - **Property 1: Attachment Collection Completeness**
    - **Validates: Requirements 1.1, 1.2**

  - [ ]* 5.3 Write property test cho attachment deduplication
    - **Property 2: Attachment Deduplication**
    - **Validates: Requirements 1.3, 1.4**

  - [ ]* 5.4 Write property test cho processing order by relevance
    - **Property 3: Processing Order by Relevance**
    - **Validates: Requirements 3.4**

  - [x] 5.5 Implement `processLinkedAttachments()` method
    - Implement main orchestration: collect attachments → create CollectionJob (if background) → sequential processing via `AttachmentPipeline.processAttachments()` per ticket → update job progress
    - Truyền đúng `ticketKey` và `projectKey` (extracted via `substringBefore("-")`) cho mỗi ticket
    - Sequential processing (không parallel) — markitdown MCP là single-process
    - Yield control giữa các tickets (`delay(100)`)
    - _Requirements: 2.1, 2.2, 4.1, 4.2, 4.3, 5.1, 5.3_

  - [ ]* 5.6 Write property test cho correct pipeline parameters
    - **Property 4: Correct Pipeline Parameters**
    - **Validates: Requirements 4.2, 4.3**

- [x] 6. Implement error handling và timeout trong `LinkedAttachmentProcessor`
  - [x] 6.1 Implement error isolation per-ticket
    - Wrap `AttachmentPipeline.processAttachments()` call trong try-catch per ticket
    - Log error với ticketId, skip ticket, tiếp tục tickets còn lại
    - Catch all exceptions ở top-level `processLinkedAttachments()` — KHÔNG propagate ra ngoài
    - _Requirements: 4.4, 6.1, 6.4_

  - [x] 6.2 Implement timeout handling
    - Track elapsed time, break loop khi vượt `timeoutMs`
    - Log warning "Linked attachment processing timeout after {elapsed}ms, processed {completed}/{total} tickets"
    - _Requirements: 5.4_

  - [x] 6.3 Implement embedding service detection và job status
    - Khi `processAttachments()` returns 0 chunks cho non-empty attachments, test embedding service availability
    - Nếu embedding unavailable → dừng processing, log error, cập nhật CollectionJob → FAILED
    - Job status: COMPLETED nếu ≥1 item thành công, FAILED nếu tất cả fail
    - _Requirements: 6.3, 6.5_

  - [ ]* 6.4 Write property test cho error isolation and containment
    - **Property 5: Error Isolation and Containment**
    - **Validates: Requirements 4.4, 6.4**

  - [ ]* 6.5 Write property test cho job status determination
    - **Property 6: Job Status Determination**
    - **Validates: Requirements 6.5**

  - [x] 6.6 Implement duplicate job prevention
    - Khi `asBackground = true`, check `CollectionJobManager` cho active `ATTACHMENT_PROCESSING` job cùng `parentTicketId` → skip tạo job mới
    - Khi graph chỉ có root node (no linked tickets) → KHÔNG tạo background job
    - _Requirements: 2.4, 7.5_

  - [ ]* 6.7 Write property test cho no background job for single-node graph
    - **Property 7: No Background Job for Single-Node Graph**
    - **Validates: Requirements 7.5**

- [x] 7. Checkpoint — Ensure LinkedAttachmentProcessor compiles và unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement `TicketGraphHolder` và tích hợp `AnalysisRoutes`
  - [x] 8.1 Tạo `TicketGraphHolder` class
    - Tạo file `server/src/jvmMain/kotlin/com/assistant/server/document/TicketGraphHolder.kt`
    - `ConcurrentHashMap<String, TicketGraph>` với `store(ticketId, graph)` và `take(ticketId): TicketGraph?`
    - Dùng để pass graph từ `DeepJiraContentExtractor` sang `AnalysisRoutes` mà không thay đổi `AIOrchestratorImpl` interface
    - _Requirements: 3.2_

  - [x] 8.2 Cập nhật `DeepJiraContentExtractor` để store graph vào `TicketGraphHolder`
    - Inject `TicketGraphHolder` vào constructor
    - Sau khi `traverseTicketGraph()` hoàn tất, gọi `ticketGraphHolder.store(ticketId, graph)`
    - _Requirements: 3.2_

  - [x] 8.3 Cập nhật `AnalysisRoutes.runAnalysis()` để tích hợp linked attachment processing
    - Inject `LinkedAttachmentProcessor` và `TicketGraphHolder` vào route
    - Sau khi analysis hoàn tất, gọi `ticketGraphHolder.take(ticketId)` để lấy graph
    - Root ticket attachments: giữ nguyên `processTicketAttachments()` đồng bộ
    - Linked ticket attachments: `scope.launch { linkedAttachmentProcessor.processLinkedAttachments(graph, ticketId, asBackground = true) }` — background, không block response
    - Khi deep extraction không available (graph == null): chỉ xử lý root ticket — giữ nguyên behavior hiện tại
    - _Requirements: 2.1, 2.2, 2.3, 3.2, 3.3, 7.1, 7.4_

- [x] 9. Implement observability — logging
  - [x] 9.1 Thêm structured logging vào `LinkedAttachmentProcessor`
    - Sử dụng `LoggerFactory.getLogger()` (SLF4J) consistent với `DeepJiraContentExtractor`
    - INFO: start message (total tickets, total attachments, new count), completion message (chunks created, elapsed time)
    - DEBUG: per-ticket processing (ticketId, depth, attachment count)
    - WARN: per-ticket failure, timeout, embedding unavailable
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 9.2 Tích hợp `ScanLogRepository` cho frontend visibility
    - Gọi `scanLogRepository.addEntry()` cho mỗi ticket processed/failed
    - Consistent với logging format hiện tại của `AttachmentPipeline`
    - _Requirements: 8.5, 4.5_

- [x] 10. Tích hợp Batch Scan flow
  - [x] 10.1 Cập nhật `BatchScanTicketProcessor.processTicket()` để gọi `LinkedAttachmentProcessor`
    - Trong `shared/src/commonMain/kotlin/com/assistant/scan/BatchScanTicketProcessor.kt`, sau Phase 3 (relationships + attachments), thêm linked attachment processing
    - Gọi `linkedAttachmentProcessor.processLinkedAttachments(graph, ticketId, asBackground = false)` — đồng bộ, KHÔNG tạo CollectionJob
    - Cần truyền `TicketGraph` từ deep extraction — sử dụng `TicketGraphHolder.take(ticketId)`
    - Error isolation: catch exception, log error, tiếp tục scan ticket tiếp theo
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [x] 11. Đăng ký DI — Koin modules
  - [x] 11.1 Đăng ký `TicketGraphHolder`, `LinkedAttachmentProcessor` trong Koin module
    - Đăng ký `TicketGraphHolder` as singleton
    - Đăng ký `LinkedAttachmentProcessor` with injected dependencies (`AttachmentPipeline`, `VectorStore`, `CollectionJobManager`, `ScanLogRepository`)
    - Cập nhật `DeepJiraContentExtractor` factory để inject `TicketGraphHolder`
    - _Requirements: 3.2, 7.2, 7.3_

- [x] 12. Checkpoint — Ensure full integration compiles và existing tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 13. Write integration tests
  - [ ]* 13.1 Write integration test cho AnalysisRoutes linked attachment flow
    - Test full flow: analysis → root attachments (sync) → linked attachments (background via CollectionJob)
    - Verify deep extraction enabled → graph passed to processor
    - Verify deep extraction disabled → chỉ root ticket
    - _Requirements: 3.2, 3.3, 7.1, 7.4_

  - [ ]* 13.2 Write integration test cho BatchScan linked attachment flow
    - Test batch scan processes linked ticket attachments synchronously (no CollectionJob)
    - Verify error isolation — failure in one ticket doesn't fail batch
    - _Requirements: 9.1, 9.2, 9.4_

  - [ ]* 13.3 Write unit test cho `LinkedTicketContent` backward compatibility
    - Deserialize old JSON (without comments/attachments fields) → emptyList() defaults
    - Verify existing code creating `LinkedTicketContent` without new fields still compiles
    - _Requirements: 11.5_

- [x] 14. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1-9)
- Unit tests validate specific examples and edge cases
- Implementation language: **Kotlin** (consistent with existing codebase — Kotlin Multiplatform)
- Sequential attachment processing is required because markitdown MCP server is single-process
- `TicketGraphHolder` pattern avoids changing `AIOrchestratorImpl` interface while passing graph data
