# Implementation Plan: Deep Ticket Data Collection

## Tổng quan

Triển khai hệ thống Deep Ticket Data Collection thay thế DocumentAggregatorImpl, giải quyết 4 vấn đề mất dữ liệu: comments bị cắt, attachment chỉ có metadata, linked tickets bị giới hạn, và text references bị bỏ qua. Triển khai theo thứ tự: Foundation, Core, Orchestration, Prompt, Background Jobs, Cache và Security, Integration, Frontend.

## Tasks

- [x] 1. Foundation — Data models, GenerationContext migration, TicketIdExtractor
  - [x] 1.1 Chuyển GenerationContext từ data class sang open class
    - Mở file shared/src/commonMain/kotlin/com/assistant/document/models/GenerationContext.kt
    - Chuyển data class GenerationContext thành open class GenerationContext với constructor tương đương
    - Implement manual copy() method giữ nguyên signature như data class
    - Implement equals(), hashCode(), toString() giống hệt behavior data class
    - Đảm bảo code hiện tại sử dụng copy() không bị break
    - _Requirements: 5.2_

  - [x] 1.2 Property test cho GenerationContext open class compatibility
    - **Property 24: GenerationContext Open Class Compatibility**
    - Verify copy() tạo object mới với equals() trả về true so với original
    - Verify hashCode() nhất quán với equals()
    - Sử dụng Kotest property testing với arbGenerationContext generator
    - File: shared/src/jvmTest/kotlin/com/assistant/document/models/GenerationContextPropertyTest.kt
    - **Validates: Requirements 5.2**

  - [x] 1.3 Tạo TraversalConfig data model với validation
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/models/TraversalConfig.kt
    - Implement data class TraversalConfig với các fields: maxDepth, maxTickets, projectScope, requestTimeoutMs, totalTimeoutMs, maxConcurrency, commentPageSize, maxPromptChars, maxCommentsPerTicket, cacheTtlMinutes
    - Implement method validated() clamp values về phạm vi hợp lệ
    - _Requirements: 7.1, 7.3, 3.7, 15.2_

  - [x] 1.4 Property test cho TraversalConfig validation clamping
    - **Property 12: TraversalConfig Validation — Clamping**
    - Verify validated() luôn trả về config với maxDepth in 1..10, maxTickets in 1..200, maxCommentsPerTicket in 10..1000, cacheTtlMinutes in 5..1440
    - Sử dụng arbTraversalConfig generator với random values ngoài phạm vi
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/models/TraversalConfigPropertyTest.kt
    - **Validates: Requirements 7.1, 7.3, 3.7, 15.2**

  - [x] 1.5 Tạo TicketGraph, TicketNode, TicketEdge, TraversalMetadata data models
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/models/TicketGraph.kt
    - Implement TicketGraph (rootTicketId, nodes, edges, metadata)
    - Implement TicketNode (ticketId, depth, discoveredVia, parentDiscoveryId, issue, relevanceScore)
    - Implement RelationshipType enum (ROOT, ISSUE_LINK, SUB_TASK, PARENT, TEXT_REFERENCE)
    - Implement TicketEdge (sourceId, targetId, relationshipType, linkDescription)
    - Implement TraversalMetadata (totalDiscovered, totalFetched, totalSkipped, maxDepthReached, traversalTimeMs, skippedTicketIds, permissionDeniedCount, earlyTerminated)
    - _Requirements: 1.3, 1.9, 1.10_

  - [x] 1.6 Tạo FullComment, CommentCollectionResult data models
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/models/CommentModels.kt
    - Implement FullComment (author, createdDate, updatedDate, body) với Serializable
    - Implement CommentCollectionResult (comments, totalReported, totalFetched, hasPartialFailure)
    - _Requirements: 3.3_

  - [x] 1.7 Tạo CollectionJob, CollectionJobItem data models
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/models/CollectionJobModels.kt
    - Implement CollectionJob với progressPercent computed property
    - Implement CollectionJobType enum (LINKED_TICKET_ANALYSIS, ATTACHMENT_PROCESSING)
    - Implement CollectionJobStatus enum (QUEUED, RUNNING, COMPLETED, FAILED)
    - Implement CollectionJobItem với CollectionJobItemStatus enum
    - Implement optimistic locking via version field
    - _Requirements: 13.3, 14.7_

  - [x] 1.8 Tạo TicketIdExtractor — pure function trích xuất ticket IDs
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/extraction/TicketIdExtractor.kt
    - Implement object TicketIdExtractor với regex pattern [A-Z][A-Z0-9]+-\d+
    - Implement extract(text, excludeIds, projectScope) trả về deduplicated, filtered list
    - Implement internal fun projectKey(ticketId) trích xuất project key
    - Pure function — không I/O, không side effects
    - _Requirements: 2.1, 2.4, 2.5, 2.6_

  - [x] 1.9 Property tests cho TicketIdExtractor
    - **Property 3: TicketIdExtractor — Comprehensive Filter**
    - Verify mỗi ID match regex, không thuộc excludeIds, project key trong projectScope, deduplicated
    - **Property 4: TicketIdExtractor — Round-trip**
    - Verify format ticket IDs thành text rồi parse lại cho ra cùng tập IDs
    - Sử dụng arbTicketId và arbTextWithTicketIds generators
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/extraction/TicketIdExtractorPropertyTest.kt
    - **Validates: Requirements 2.1, 2.4, 2.5, 2.6, 2.7**

- [x] 2. Checkpoint — Đảm bảo foundation hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Core — TraversalEngine, CommentCollector, AttachmentContentCollector
  - [x] 3.1 Mở rộng JiraClient interface với getIssueComments method
    - Mở file shared/src/commonMain/kotlin/com/assistant/jira/JiraClient.kt
    - Thêm method getIssueComments(issueKey, startAt, maxResults): JiraCommentPageResponse
    - Tạo JiraCommentPageResponse data class (startAt, maxResults, total, comments)
    - Implement method trong JiraClientImpl ở server module
    - _Requirements: 9.1_

  - [x] 3.2 Implement CommentCollector — thu thập toàn bộ comments qua pagination
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/collection/CommentCollector.kt
    - Implement collectAll(ticketId) với Jira API pagination
    - Page size từ config.commentPageSize (default 50)
    - Cap tại config.maxCommentsPerTicket (default 200) — lấy N comments gần nhất
    - Retry max 2 lần với exponential backoff (1s, 2s) per page
    - Partial success: giữ comments đã fetch khi page failure
    - Sort comments theo createdDate tăng dần (oldest first)
    - Xử lý total thay đổi giữa pages bằng startAt + maxResults
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.7, 9.2, 9.3, 9.4_

  - [x] 3.3 Property tests cho CommentCollector
    - **Property 5: Comment Collection — Pagination Completeness và Ordering**
    - Verify gọi API đúng ceil(N/pageSize) lần, comments sorted oldest first
    - **Property 6: Comment Count Metamorphic — Không thu thập nhiều hơn tồn tại**
    - Verify totalFetched <= totalReported
    - **Property 22: Comment Capping — maxCommentsPerTicket**
    - Verify trả về min(N, M) comments, chỉ M comments gần nhất khi N > M
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/collection/CommentCollectorPropertyTest.kt
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.7, 9.2, 9.5**

  - [x] 3.4 Implement AttachmentContentCollector — thu thập toàn bộ attachment chunks
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/collection/AttachmentContentCollector.kt
    - Implement collectAll(ticketId) sử dụng VectorStore.findByTicketId
    - Group chunks theo filename, sort theo chunkIndex tăng dần
    - Deduplicate theo attachmentId across tickets
    - Trích xuất ticket IDs từ chunk text cho TicketIdExtractor
    - _Requirements: 4.1, 4.3, 4.5, 4.6_

  - [x] 3.5 Property test cho AttachmentContentCollector
    - **Property 7: Attachment Collection — Grouping, Sorting, và Deduplication**
    - Verify chunks cùng filename nhóm liền, sorted theo chunkIndex, deduplicated by (attachmentId, chunkIndex)
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/collection/AttachmentCollectorPropertyTest.kt
    - **Validates: Requirements 4.3, 4.5**

  - [x] 3.6 Implement TraversalEngine — BFS traversal với cycle detection
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/traversal/TraversalEngine.kt
    - Implement traverse(rootTicketId) với BFS algorithm
    - Cycle detection via visitedSet
    - Concurrent fetch per BFS level sử dụng jiraApiSemaphore (max 5)
    - Priority ordering: parent > blocking > other links > sub-tasks > text refs
    - Respect max_depth, max_tickets, total_timeout
    - Implement computeRelevanceScore(node) dựa trên depth, relationship type, recency, status
    - Track 403 responses vào permissionDeniedTickets (count only, no IDs exposed)
    - Early termination khi data > 3x maxPromptChars
    - Log mỗi ticket fetched ở level DEBUG
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.9, 1.10, 7.4, 7.6, 10.1, 10.2, 10.3, 10.5_

  - [x] 3.7 Property tests cho TraversalEngine
    - **Property 1: BFS Traversal — Cycle Detection và Node Metadata Invariant**
    - Verify mỗi ticketId xuất hiện tối đa 1 lần, mỗi node có metadata hợp lệ
    - **Property 2: Traversal Config Limits — Không vượt max_depth và max_tickets**
    - Verify graph.nodes.size <= config.maxTickets và all depths <= config.maxDepth
    - **Property 13: BFS Traversal Priority Ordering**
    - Verify thứ tự xử lý: parent > blocking > other links > sub-tasks > text refs
    - **Property 19: Early Termination — Data Size Threshold**
    - Verify dừng thu thập khi data > 3x maxPromptChars, earlyTerminated = true
    - **Property 20: Relevance Scoring — Monotonicity**
    - Verify depth thấp hơn có score >= depth cao hơn (ceteris paribus)
    - **Property 21: Permission-Denied Isolation — 403 Ticket IDs Not Exposed**
    - Verify permissionDeniedCount đúng, không có 403 ticket IDs trong API response
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/traversal/TraversalEnginePropertyTest.kt
    - **Validates: Requirements 1.2, 1.3, 1.5, 1.7, 1.9, 1.10, 7.6**

- [x] 4. Checkpoint — Đảm bảo core components hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Orchestration — EnrichedContext, DeepCollector
  - [x] 5.1 Tạo EnrichedContext data model kế thừa GenerationContext
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/models/EnrichedContext.kt
    - Implement EnrichedContext extends GenerationContext
    - Thêm fields: allTickets, ticketRelationships, rawComments, allAttachmentChunks, traversalMetadata, ticketDepthMap
    - Giữ backward compatibility: mainTicket, linkedTicketAnalyses, attachmentChunks, sprintMetadata vẫn populated
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 5.2 Property test cho EnrichedContext serialization round-trip
    - **Property 8: EnrichedContext — Serialization Round-trip**
    - Verify Json.decodeFromString(Json.encodeToString(context)) cho ra object tương đương
    - Sử dụng arbEnrichedContext generator
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/models/EnrichedContextPropertyTest.kt
    - **Validates: Requirements 5.5**

  - [x] 5.3 Implement DeepCollector — orchestrator chính (implements DocumentAggregator)
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/DeepCollector.kt
    - Implement DocumentAggregator interface
    - Phase 0: Rate limit check + cache check
    - Phase 1: BFS Traversal via TraversalEngine (progress 5-25%)
    - Phase 2: Collect comments + attachments (progress 25-30%)
    - Phase 3: Build EnrichedContext + create Collection_Jobs
    - Collection-level lock: coalesce concurrent requests cho same root ticket
    - Progress callback integration: (progressPercent, phase) -> Unit
    - Log traversal config ở đầu mỗi lần traversal
    - Log tổng kết khi hoàn tất
    - Populate linkedTicketAnalyses từ KBRecords (backward compat)
    - Throw exception khi root ticket fetch thất bại
    - Sử dụng cùng JiraClient instance đã configure
    - _Requirements: 1.8, 5.3, 8.1, 8.2, 8.3, 8.5, 8.6, 10.4, 10.6, 10.7, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 12.3, 12.5_

  - [x] 5.4 Property test cho DeepCollector backward compatibility
    - **Property 14: Backward Compatibility — linkedTicketAnalyses Populated**
    - Verify linkedTicketAnalyses chứa KBRecords của tất cả tickets có KBRecord trong KB
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/DeepCollectorPropertyTest.kt
    - **Validates: Requirements 12.3**

- [x] 6. Checkpoint — Đảm bảo orchestration hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Prompt — PromptAssembler với priority-based truncation
  - [x] 7.1 Implement PromptAssembler — xây dựng prompt từ EnrichedContext
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/prompt/PromptAssembler.kt
    - Implement buildPrompt(context, maxPromptChars, docType)
    - Priority order cho BRD: root raw > root KB > depth-1 raw > root attachments > depth-1 attachments > deeper tickets > deeper attachments
    - Priority order cho FSD: root raw > root attachments > root KB > depth-1 raw > depth-1 attachments > deeper tickets > deeper attachments
    - Truncation strategy: cắt từ priority thấp nhất trước
    - Annotation khi truncate: "[TRUNCATED: N tickets, M chunks removed]"
    - Format comments: "[Comment by {author} on {date}]: {body}"
    - Format attachments: "[Attachment: {filename} from {ticketId}]: {content}"
    - Format ticket graph metadata
    - Giữ nguyên role, template, instructions từ BrdPromptSections/FsdPromptSections
    - Within each priority level, sort tickets by relevance score
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9_

  - [x] 7.2 Property tests cho PromptAssembler
    - **Property 9: Prompt Size Limit và No Unnecessary Truncation**
    - Verify prompt.length <= maxPromptChars, và không truncate khi content < maxPromptChars
    - **Property 10: Prompt Priority-based Truncation**
    - Verify root ticket data luôn được giữ, depth>=2 bị cắt trước depth-1
    - **Property 11: Prompt Formatting — Comment và Attachment Format**
    - Verify formatted output chứa author, date, body cho comments và filename, ticketId, content cho attachments
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/prompt/PromptAssemblerPropertyTest.kt
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.8**

- [x] 8. Checkpoint — Đảm bảo prompt assembly hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Background Jobs — CollectionJobManager, Collection Job API
  - [x] 9.1 Tạo database schema cho collection_jobs, traversal_cache, rate_limits
    - Thêm CREATE TABLE collection_jobs với job_id, parent_ticket_id, job_type, status, total_items, completed_items, failed_items, items_json, created_at, updated_at, version
    - Thêm CREATE TABLE traversal_cache với root_ticket_id, graph_json, cached_at, root_updated_at
    - Thêm CREATE TABLE deep_collection_rate_limits với user_id, requested_at
    - Tạo indexes cho performance
    - _Requirements: 13.3, 15.1, 16.1_

  - [x] 9.2 Implement CollectionJobRepository — CRUD cho collection_jobs table
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/jobs/CollectionJobRepository.kt
    - Implement save, findByParentTicketId, findActive, updateItemStatus, updateJobStatus
    - Implement optimistic locking via version field
    - _Requirements: 13.3, 13.5, 13.6, 14.7_

  - [x] 9.3 Implement CollectionJobManager — quản lý background jobs
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/jobs/CollectionJobManager.kt
    - Implement createJobs(parentTicketId, ticketGraph, missingKBTicketIds, unprocessedAttachments)
    - Implement executeJob(jobId) — xử lý từng item độc lập
    - KB-First check trước mỗi item
    - Implement preemptItem(jobId, ticketId) cho manual analysis preemption
    - Implement isTicketProcessing(ticketId) cho conflict detection
    - Tích hợp ScanLogRepository cho logging
    - Sử dụng aiAnalysisSemaphore và jiraApiSemaphore
    - _Requirements: 13.1, 13.2, 13.4, 13.10, 13.11, 13.13, 14.1, 14.2, 14.4_

  - [x] 9.4 Property tests cho CollectionJobManager
    - **Property 15: Collection_Job Progress Tracking — Invariant**
    - Verify completedItems + failedItems == K sau K items xử lý, progressPercent đúng
    - **Property 16: Conflict Resolution — Manual Preemption và KB-First**
    - Verify PENDING items bị skip khi manual analysis triggered hoặc KB đã có record
    - **Property 23: Collection_Job Fault Isolation — Independent Item Processing**
    - Verify K items fail không ảnh hưởng N-K items còn lại, job FAILED chỉ khi K == N
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/jobs/CollectionJobManagerPropertyTest.kt
    - **Validates: Requirements 13.3, 13.4, 13.10, 14.1, 14.2**

  - [x] 9.5 Implement Collection Job API endpoints (Ktor routes)
    - Thêm routes vào server routing module
    - GET /api/collection-jobs?ticketId={ticketId} — danh sách jobs cho ticket
    - GET /api/collection-jobs/active — tất cả jobs đang RUNNING hoặc QUEUED
    - Response format: CollectionJobResponse với items, progress, status
    - _Requirements: 13.5, 13.6_

- [x] 10. Checkpoint — Đảm bảo background jobs hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. Cache và Security — TraversalCache, RateLimiter
  - [x] 11.1 Implement TraversalCacheRepository — CRUD cho traversal_cache table
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/cache/TraversalCacheRepository.kt
    - Implement get, put, invalidate operations
    - Serialize/deserialize TicketGraph to/from JSON
    - _Requirements: 15.1_

  - [x] 11.2 Implement TraversalCache — cache TicketGraph với TTL validation
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/cache/TraversalCache.kt
    - Implement get(rootTicketId, cacheTtlMinutes) — check TTL + root updated_at
    - Implement put(rootTicketId, graph) — cache sau traversal thành công
    - Implement invalidate(rootTicketId) — cho RE-ANALYZE
    - Log khi sử dụng cache ở level INFO
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

  - [x] 11.3 Property test cho TraversalCache
    - **Property 17: Cache TTL và Invalidation**
    - Verify cache hit khi within TTL + root unchanged, cache miss khi expired hoặc root changed, null sau invalidate
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/cache/TraversalCachePropertyTest.kt
    - **Validates: Requirements 15.1, 15.2, 15.3, 15.4**

  - [x] 11.4 Implement RateLimitRepository — CRUD cho rate_limits table
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/security/RateLimitRepository.kt
    - Implement countInLastHour(userId), record(userId), cleanup operations
    - _Requirements: 16.1_

  - [x] 11.5 Implement RateLimiter — per-user hourly rate limiting
    - Tạo file server/src/jvmMain/kotlin/com/assistant/server/document/security/RateLimiter.kt
    - Implement check(userId) — throw RateLimitExceededException khi vượt limit
    - Implement record(userId) — ghi nhận request
    - Implement filterProjectScope(projectScope, userId) — validate project access
    - maxPerUserPerHour default 10
    - _Requirements: 16.1, 16.2_

  - [x] 11.6 Property test cho RateLimiter
    - **Property 18: Rate Limiting — Per-user Hourly Cap**
    - Verify throw exception khi N >= maxPerUserPerHour, pass khi N < maxPerUserPerHour
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/security/RateLimiterPropertyTest.kt
    - **Validates: Requirements 16.1**

- [x] 12. Checkpoint — Đảm bảo cache và security hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Pipeline Integration — Feature flag, JobExecutor wiring, conflict resolution
  - [x] 13.1 Thêm setting deep_collection_enabled vào application settings
    - Thêm setting deep_collection_enabled (boolean, default true) vào settings mechanism hiện tại
    - Cho phép thay đổi qua API hoặc settings page
    - Không yêu cầu restart server
    - _Requirements: 12.1_

  - [x] 13.2 Wire DeepCollector vào JobExecutor qua feature flag
    - Cập nhật DI/configuration để inject DeepCollector hoặc DocumentAggregatorImpl dựa trên deep_collection_enabled
    - Khi true: inject DeepCollector, sử dụng PromptAssembler
    - Khi false: inject DocumentAggregatorImpl, sử dụng BrdPromptBuilder/FsdPromptBuilder
    - Cập nhật JobExecutor.saveDocument() để lưu tất cả source ticket IDs từ TicketGraph
    - _Requirements: 8.1, 8.2, 8.4, 12.1, 12.2, 12.4_

  - [x] 13.3 Implement conflict resolution giữa Collection_Jobs và manual Ticket Intelligence analysis
    - Cập nhật Ticket Intelligence analysis endpoint để check isTicketProcessing trước khi analyze
    - Return HTTP 409 khi ticket đang PROCESSING trong Collection_Job
    - Preempt PENDING items khi manual analysis triggered
    - Cho phép parallel attachment processing + manual analysis
    - _Requirements: 14.1, 14.3, 14.4, 14.5, 14.6_

  - [x] 13.4 Integration tests cho feature flag và backward compatibility
    - Test toggle deep_collection_enabled, verify correct aggregator
    - Test EnrichedContext với existing BrdPromptBuilder/FsdPromptBuilder
    - Test concurrent deep collection coalesce pattern
    - Test dual semaphores không block lẫn nhau
    - File: server/src/jvmTest/kotlin/com/assistant/server/document/DeepCollectorIntegrationTest.kt
    - _Requirements: 8.1, 8.2, 10.6, 10.7, 12.1, 12.2_

- [x] 14. Checkpoint — Đảm bảo pipeline integration hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Frontend — Collection Job monitoring panel
  - [x] 15.1 Implement Collection Job monitoring panel trên trang Ticket Intelligence
    - Hiển thị danh sách Collection_Jobs đang chạy cho ticket hiện tại
    - Progress bar với % hoàn thành cho mỗi job
    - Danh sách items: ticket ID hoặc filename, trạng thái (Pending, Processing, Completed, Failed, Skipped)
    - Tổng kết: "{completed}/{total} items, {failed} failed"
    - _Requirements: 13.7_

  - [x] 15.2 Implement polling cho Collection Job status updates
    - Poll GET /api/collection-jobs?ticketId={ticketId} mỗi 5 giây khi có jobs active
    - Cập nhật progress bars và item statuses
    - Dừng polling khi tất cả jobs hoàn tất
    - _Requirements: 13.8, 13.12_

  - [x] 15.3 Implement conflict resolution UI banners
    - Hiển thị banner khi ticket đang được phân tích bởi background job
    - Disable nút ANALYZE khi ticket đang PROCESSING (không disable khi PENDING)
    - Hiển thị warning cho permission-denied tickets (count only, no IDs)
    - Auto-refresh kết quả khi Collection_Job hoàn tất
    - _Requirements: 1.10, 14.3, 14.8, 14.9_

- [x] 16. Final checkpoint — Đảm bảo toàn bộ hệ thống hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks đánh dấu `*` là optional và có thể skip để đẩy nhanh MVP
- Mỗi task reference requirements cụ thể để traceability
- Checkpoints đảm bảo incremental validation sau mỗi phase
- Property tests verify 24 correctness properties từ design document
- Unit tests verify specific scenarios và edge cases
- Kotlin code standards: file <= 200 dòng, function <= 20 dòng, models ở package models/ riêng
- Property-based tests sử dụng Kotest (io.kotest:kotest-property)
