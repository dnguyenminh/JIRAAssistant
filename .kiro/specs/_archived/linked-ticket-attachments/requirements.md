# Linked Ticket Attachments — Requirements

## Introduction

Hiện tại, khi phân tích một ticket (single ticket analysis qua `AnalysisRoutes`), chỉ có **attachments của root ticket** được xử lý bởi `AttachmentPipeline`. Các linked tickets được phát hiện trong quá trình deep extraction (BFS traversal qua `TraversalEngine`) có attachments bị **bỏ qua hoàn toàn**.

Cụ thể có 4 điểm mất dữ liệu:

1. **`processTicketAttachments()` trong `AnalysisRoutes.kt`** chỉ gọi `getIssueDetails(ticketId)` cho root ticket — không iterate qua linked tickets trong `TicketGraph`.

2. **`DeepJiraContentExtractor.nodeToLinkedContent()`** chỉ trích xuất `summary`, `description`, `status`, `linkType` từ linked tickets — **không trích xuất attachments và comments**. Model `LinkedTicketContent` hiện tại thiếu cả hai fields này.

3. **`StructuredTicketContent`** từ deep extraction đã chứa `attachments: List<AttachmentInfo>` và `comments: List<CommentInfo>` cho mỗi `TicketNode.issue`, nhưng dữ liệu này bị discard khi convert sang `LinkedTicketContent`.

4. **`LinkedTicketContent` model** chỉ có 5 fields (`ticketId`, `summary`, `description`, `status`, `linkType`) — thiếu `comments` và `attachments`, khiến AI prompt không có thông tin comments và attachments từ linked tickets.

Feature này mở rộng attachment processing pipeline để xử lý attachments từ **TẤT CẢ tickets trong TicketGraph** — bao gồm root ticket và tất cả linked tickets đã được discover bởi `TraversalEngine`. Sau khi xử lý, tất cả attachment content sẽ khả dụng cho RAG/semantic search khi phân tích ticket.

## Glossary

- **Attachment_Pipeline**: Component hiện tại (`AttachmentPipeline.kt`) orchestrate quá trình: download → markitdown → chunk → embed → store cho attachments của một ticket
- **Linked_Attachment_Processor**: Component mới chịu trách nhiệm thu thập và xử lý attachments từ tất cả tickets trong TicketGraph, sử dụng Attachment_Pipeline cho từng ticket
- **TicketGraph**: Đồ thị các ticket liên quan được xây dựng bởi `TraversalEngine` trong quá trình deep extraction (BFS). Mỗi node là một `TicketNode` chứa `StructuredTicketContent` (bao gồm `attachments: List<AttachmentInfo>`)
- **Root_Ticket**: Ticket gốc mà user yêu cầu phân tích, là điểm bắt đầu của BFS traversal
- **VectorStore**: Interface lưu trữ attachment chunks với embeddings, hỗ trợ `existsByAttachmentId()` cho KB-First deduplication và `findByTicketId()` để lấy tất cả chunks của một ticket
- **Collection_Job_Manager**: Component quản lý background jobs (`CollectionJobManager.kt`), đã hỗ trợ tạo `ATTACHMENT_PROCESSING` jobs cho linked tickets
- **AttachmentInfo**: Model chứa metadata attachment trong `StructuredTicketContent`: id, filename, mimeType, size, content URL
- **KB_First_Dedup**: Chiến lược deduplication hiện tại — `VectorStore.existsByAttachmentId(att.id)` skip attachments đã được xử lý, tránh download/convert lại

## Requirements

### Requirement 1: Thu thập Attachment Metadata từ tất cả Linked Tickets

**User Story:** Là một BA/PM, tôi muốn hệ thống tự động thu thập metadata attachments từ TẤT CẢ linked tickets trong TicketGraph (không chỉ root ticket), để tất cả tài liệu đính kèm liên quan đều được xử lý và khả dụng cho AI analysis.

#### Acceptance Criteria

1.1 WHEN `TraversalEngine` hoàn tất BFS traversal và trả về `TicketGraph`, THE Linked_Attachment_Processor SHALL duyệt qua TẤT CẢ `TicketNode` trong `TicketGraph.nodes` và thu thập `attachments: List<AttachmentInfo>` từ `TicketNode.issue` (StructuredTicketContent) của mỗi node.

1.2 THE Linked_Attachment_Processor SHALL tạo danh sách tổng hợp tất cả attachments cần xử lý, mỗi entry bao gồm: attachment metadata (id, filename, mimeType, size, content URL) và ticketId của ticket chứa attachment đó.

1.3 THE Linked_Attachment_Processor SHALL loại bỏ attachments trùng lặp theo `attachmentId` — nếu cùng một attachment xuất hiện trong nhiều tickets (do Jira link structure), attachment đó chỉ được xử lý một lần.

1.4 THE Linked_Attachment_Processor SHALL sử dụng KB-First deduplication (`VectorStore.existsByAttachmentId()`) để skip attachments đã có trong VectorStore, consistent với behavior hiện tại của Attachment_Pipeline.

1.5 WHEN thu thập hoàn tất, THE Linked_Attachment_Processor SHALL log tổng kết: "Linked attachment collection for {rootTicketId}: {totalAttachments} attachments from {ticketCount} tickets, {newAttachments} new (not in KB), {skippedAttachments} already processed".

### Requirement 2: Xử lý Attachments của Linked Tickets — Đồng bộ cho Ticket Intelligence

**User Story:** Là một BA/PM, tôi muốn khi phân tích ticket trên trang Ticket Intelligence, hệ thống xử lý xong TẤT CẢ (bao gồm linked ticket attachments) trước khi trả kết quả — để blocking layer chỉ tắt khi mọi thứ hoàn tất.

#### Acceptance Criteria

2.1 WHEN single ticket analysis hoàn tất với kết quả mới từ AI (`source == FRESH_AI`) trong `AnalysisRoutes.runAnalysis()`, THE Linked_Attachment_Processor SHALL xử lý attachments từ linked tickets **đồng bộ** (trước khi trả response). WHEN kết quả từ KB cache (`source == KB_CACHE`), attachment processing SHALL được skip — attachments đã được xử lý trong lần analyze trước. *(Cập nhật: chỉ chạy attachment processing cho FRESH_AI, skip cho KB_CACHE để tránh delay khi polling fallback fetch kết quả)*

2.2 THE `AnalysisRoutes.runAnalysis()` SHALL gọi `processLinkedAttachmentsSynchronously()` — chạy `LinkedAttachmentProcessor.processLinkedAttachments(graph, ticketId, asBackground = false)` trực tiếp trong suspend context, KHÔNG dùng `CoroutineScope.launch`. Response chỉ trả về sau khi linked attachment processing hoàn tất. *(Cập nhật: trước đó dùng background coroutine launch)*

2.3 THE Linked_Attachment_Processor SHALL xử lý attachments của root ticket **đồng bộ** (trước khi trả response) — giữ nguyên behavior hiện tại của `processTicketAttachments()`. Linked ticket attachments cũng chạy đồng bộ ngay sau đó.

2.4 WHEN `BatchScanEngine` xử lý linked ticket attachments, THE processing SHALL cũng chạy **đồng bộ** (`asBackground = false`) — consistent với Ticket Intelligence flow. *(Batch scan đã là background process nên đồng bộ bên trong là hợp lý)*

### Requirement 3: Tích hợp với TicketGraph trong Deep Extraction Flow

**User Story:** Là một developer, tôi muốn Linked_Attachment_Processor sử dụng dữ liệu attachment đã có sẵn trong TicketGraph (từ deep extraction), thay vì fetch lại từ Jira API, để tránh duplicate API calls và tận dụng dữ liệu đã thu thập.

#### Acceptance Criteria

3.1 THE Linked_Attachment_Processor SHALL nhận `TicketGraph` làm input (từ kết quả deep extraction), KHÔNG gọi thêm Jira API để fetch attachment metadata. Dữ liệu `StructuredTicketContent.attachments` trong mỗi `TicketNode.issue` đã chứa đầy đủ thông tin cần thiết (id, filename, mimeType, size, content URL).

3.2 WHEN `DeepJiraContentExtractor` được sử dụng (deep extraction enabled), THE AnalysisRoutes SHALL truyền `TicketGraph` kết quả cho Linked_Attachment_Processor thay vì chỉ gọi `processTicketAttachments()` cho root ticket.

3.3 WHEN deep extraction KHÔNG được sử dụng (fallback mode hoặc `deep_collection_enabled = false`), THE AnalysisRoutes SHALL giữ nguyên behavior hiện tại — chỉ xử lý attachments của root ticket qua `processTicketAttachments()`.

3.4 THE Linked_Attachment_Processor SHALL sắp xếp thứ tự xử lý attachments theo relevance: (1) root ticket attachments trước, (2) depth-1 linked ticket attachments, (3) deeper ticket attachments. Trong cùng depth, tickets có `relevanceScore` cao hơn được xử lý trước.

### Requirement 4: Xử lý từng Attachment qua Attachment_Pipeline hiện tại

**User Story:** Là một developer, tôi muốn Linked_Attachment_Processor tái sử dụng `AttachmentPipeline` hiện tại cho việc download → markitdown → chunk → embed → store, để không duplicate logic và đảm bảo consistency với single-ticket attachment processing.

#### Acceptance Criteria

4.1 THE Linked_Attachment_Processor SHALL gọi `AttachmentPipeline.processAttachments(projectKey, ticketKey, attachments)` cho mỗi ticket trong TicketGraph có attachments cần xử lý, tái sử dụng toàn bộ pipeline hiện tại (download, markitdown conversion, chunking, embedding, storage).

4.2 THE Linked_Attachment_Processor SHALL truyền đúng `ticketKey` (ticket chứa attachment) cho `AttachmentPipeline.processAttachments()`, đảm bảo chunks được lưu với `ticketId` chính xác trong VectorStore — cho phép `VectorStore.findByTicketId()` trả về đúng chunks cho mỗi ticket.

4.3 THE Linked_Attachment_Processor SHALL truyền đúng `projectKey` (extracted từ ticketKey bằng `ticketKey.substringBefore("-")`) cho logging và file path purposes.

4.4 IF `AttachmentPipeline.processAttachments()` throw exception cho một ticket, THEN THE Linked_Attachment_Processor SHALL log error với ticketId và exception message, skip ticket đó, và tiếp tục xử lý tickets còn lại (error isolation).

4.5 THE Linked_Attachment_Processor SHALL log kết quả cho mỗi ticket: "Processed attachments for {ticketId}: {chunksCreated} chunks from {attachmentCount} attachments".

### Requirement 5: Concurrency Control — Giới hạn tải Markitdown MCP

**User Story:** Là một developer, tôi muốn quá trình xử lý attachments của linked tickets tuân thủ concurrency limits hiện tại (markitdown MCP server, Jira API), để không overwhelm external services và gây lỗi.

#### Acceptance Criteria

5.1 THE Linked_Attachment_Processor SHALL xử lý attachments **tuần tự** (sequential) cho mỗi ticket — gọi `AttachmentPipeline.processAttachments()` lần lượt cho từng ticket, KHÔNG parallel. Lý do: markitdown MCP server là single-process, không hỗ trợ concurrent calls.

5.2 THE Linked_Attachment_Processor SHALL sử dụng cùng `jiraApiSemaphore` hiện tại khi download attachments từ Jira API (thông qua `AttachmentPipeline` đã sử dụng Jira auth), đảm bảo không vượt quá concurrency limit cho Jira API calls.

5.3 WHEN xử lý attachments cho linked tickets ở background, THE Linked_Attachment_Processor SHALL yield control (delay nhỏ giữa các tickets, ví dụ 100ms) để không monopolize resources và cho phép các operations khác (manual analysis, batch scan) chạy xen kẽ.

5.4 THE Linked_Attachment_Processor SHALL tuân thủ `total_timeout_ms` từ `TraversalConfig` — nếu tổng thời gian xử lý attachments vượt timeout, dừng xử lý và log warning "Linked attachment processing timeout after {elapsed}ms, processed {completed}/{total} tickets".

### Requirement 6: Error Handling và Resilience

**User Story:** Là một developer, tôi muốn quá trình xử lý linked ticket attachments có error handling robust — lỗi ở một ticket không ảnh hưởng đến tickets khác, và lỗi attachment processing không ảnh hưởng đến analysis result.

#### Acceptance Criteria

6.1 IF một attachment download thất bại (network error, 404, 403), THEN THE Attachment_Pipeline SHALL skip attachment đó, log FAILED entry, và tiếp tục attachment tiếp theo trong cùng ticket (behavior hiện tại được giữ nguyên).

6.2 IF markitdown MCP server không khả dụng hoặc crash, THEN THE Attachment_Pipeline SHALL thử restart một lần. Nếu vẫn thất bại, skip tất cả attachments cần markitdown conversion và log warning (behavior hiện tại được giữ nguyên).

6.3 IF embedding service (Ollama) không khả dụng, THEN THE Linked_Attachment_Processor SHALL dừng xử lý tất cả remaining attachments (vì không thể tạo embeddings), log error "Embedding service unavailable, stopping linked attachment processing", và cập nhật Collection_Job status thành FAILED.

6.4 THE Linked_Attachment_Processor SHALL KHÔNG throw exception ra ngoài — tất cả errors được catch và log. Analysis response KHÔNG bị ảnh hưởng bởi linked attachment processing failures.

6.5 WHEN Collection_Job hoàn tất (tất cả items processed hoặc failed), THE Collection_Job_Manager SHALL cập nhật job status: COMPLETED nếu ít nhất 1 item thành công, FAILED nếu tất cả items thất bại.

### Requirement 7: Backward Compatibility — Không break Single-Ticket Flow

**User Story:** Là một developer, tôi muốn feature mới không thay đổi behavior hiện tại của single-ticket attachment processing, để các flow đã hoạt động ổn định không bị regression.

#### Acceptance Criteria

7.1 THE `processTicketAttachments()` function hiện tại trong `AnalysisRoutes.kt` SHALL tiếp tục hoạt động cho root ticket — download và process attachments đồng bộ trước khi trả response, giữ nguyên behavior hiện tại.

7.2 THE `AttachmentPipeline.processAttachments()` interface và behavior SHALL KHÔNG thay đổi — method signature, KB-First deduplication, error handling, logging đều giữ nguyên.

7.3 THE `BatchScanEngine` attachment processing flow SHALL KHÔNG bị ảnh hưởng — `processAttachmentsIfAvailable()` trong `BatchScanTicketProcessor.kt` tiếp tục hoạt động như hiện tại.

7.4 WHEN deep extraction KHÔNG available (fallback mode), THE AnalysisRoutes SHALL chỉ xử lý root ticket attachments — KHÔNG cố gắng xử lý linked ticket attachments.

7.5 FOR ALL single-ticket analysis requests không có linked tickets (TicketGraph chỉ có root node), THE Linked_Attachment_Processor SHALL KHÔNG tạo background Collection_Job — behavior tương đương với flow hiện tại.

### Requirement 8: Observability — Logging và Monitoring

**User Story:** Là một developer/admin, tôi muốn quá trình xử lý linked ticket attachments có logging chi tiết, để tôi có thể debug khi có vấn đề và monitor hiệu suất.

#### Acceptance Criteria

8.1 THE Linked_Attachment_Processor SHALL log ở level INFO khi bắt đầu: "Starting linked attachment processing for {rootTicketId}: {totalTickets} tickets, {totalAttachments} attachments ({newAttachments} new)".

8.2 THE Linked_Attachment_Processor SHALL log ở level DEBUG cho mỗi ticket được xử lý: "Processing attachments for linked ticket {ticketId} (depth={depth}): {attachmentCount} attachments".

8.3 THE Linked_Attachment_Processor SHALL log ở level INFO khi hoàn tất: "Linked attachment processing completed for {rootTicketId}: {totalChunks} chunks created from {processedAttachments} attachments across {processedTickets} tickets, elapsed={T}ms".

8.4 THE Linked_Attachment_Processor SHALL log ở level WARN khi: attachment processing thất bại cho một ticket, timeout xảy ra, embedding service không khả dụng.

8.5 THE Linked_Attachment_Processor SHALL cập nhật `ScanLogRepository` với entries cho quá trình xử lý, cho phép user xem tiến trình trên frontend scan log (consistent với logging format hiện tại của AttachmentPipeline).

### Requirement 9: Tích hợp với Batch Scan Flow

**User Story:** Là một BA/PM, tôi muốn batch scan cũng xử lý attachments của linked tickets (không chỉ root ticket), để khi scan toàn bộ project, tất cả attachment content đều được index cho semantic search.

#### Acceptance Criteria

9.1 WHEN `BatchScanEngine` xử lý một ticket có linked tickets (deep extraction enabled), THE BatchScanEngine SHALL gọi Linked_Attachment_Processor với TicketGraph kết quả, xử lý attachments từ tất cả linked tickets.

9.2 THE BatchScanEngine SHALL xử lý linked ticket attachments **đồng bộ** (trong cùng scan flow), KHÔNG tạo background Collection_Job — vì batch scan đã là background process.

9.3 THE BatchScanEngine SHALL tuân thủ existing semaphore limits — linked attachment processing chạy trong cùng concurrency context với batch scan, không tạo thêm concurrent load.

9.4 IF linked attachment processing thất bại cho một ticket trong batch scan, THEN THE BatchScanEngine SHALL log error và tiếp tục scan ticket tiếp theo — KHÔNG fail toàn bộ batch scan.

### Requirement 10: Attachment Data khả dụng cho RAG/Semantic Search

**User Story:** Là một BA/PM, tôi muốn sau khi linked ticket attachments được xử lý, nội dung của chúng khả dụng cho AI Chat semantic search, để khi tôi hỏi về nội dung trong attachment của linked ticket, AI có thể trả lời chính xác.

#### Acceptance Criteria

10.1 WHEN linked ticket attachments đã được xử lý và lưu vào VectorStore, THE ChatService semantic search (`VectorStore.search()`) SHALL tìm thấy chunks từ linked ticket attachments — không cần thay đổi ChatService code vì search đã hoạt động trên toàn bộ VectorStore.

10.2 THE VectorStore.findByTicketId() SHALL trả về attachment chunks cho linked tickets — mỗi chunk được lưu với `ticketId` của ticket chứa attachment (không phải root ticket), cho phép truy vấn chính xác theo ticket.

10.3 FOR ALL attachments đã được xử lý (root hoặc linked), `VectorStore.existsByAttachmentId(attachmentId)` SHALL trả về `true` — đảm bảo KB-First deduplication hoạt động đúng cho cả root và linked ticket attachments (idempotence — xử lý lại cùng TicketGraph không tạo duplicate chunks).

### Requirement 11: Mở rộng LinkedTicketContent Model — Bổ sung Comments và Attachments

**User Story:** Là một BA/PM, tôi muốn AI prompt bao gồm comments và attachment metadata từ linked tickets, để AI có đầy đủ ngữ cảnh khi phân tích ticket — bao gồm thảo luận và tài liệu đính kèm từ các ticket liên quan.

#### Acceptance Criteria

11.1 THE `LinkedTicketContent` data class SHALL được mở rộng thêm field `comments: List<CommentInfo>` (default = emptyList()) — chứa comments từ linked ticket, cho phép AI prompt bao gồm thảo luận từ linked tickets.

11.2 THE `LinkedTicketContent` data class SHALL được mở rộng thêm field `attachments: List<AttachmentInfo>` (default = emptyList()) — chứa attachment metadata từ linked ticket, cho phép AI prompt biết linked ticket có những tài liệu đính kèm nào.

11.3 THE `DeepJiraContentExtractor.nodeToLinkedContent()` SHALL được cập nhật để map `comments` và `attachments` từ `TicketNode.issue` (StructuredTicketContent) sang `LinkedTicketContent`, thay vì chỉ map `summary`, `description`, `status`, `linkType` như hiện tại.

11.4 THE `DeepAnalysisPromptBuilder` SHALL được cập nhật để include comments và attachment filenames từ linked tickets trong AI prompt section "RELATED TICKETS CONTEXT" — giúp AI có thêm ngữ cảnh từ thảo luận và tài liệu đính kèm.

11.5 THE default values (`emptyList()`) cho `comments` và `attachments` trong `LinkedTicketContent` SHALL đảm bảo backward compatibility — existing code tạo `LinkedTicketContent` không cần thay đổi, và deserialization từ old JSON (không có fields mới) vẫn hoạt động đúng.
