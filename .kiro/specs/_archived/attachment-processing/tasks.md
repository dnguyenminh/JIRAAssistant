# Attachment Processing Pipeline — Tasks

Status: ✅ All completed

# Attachment Processing Pipeline — Tasks 147–170

Triển khai Attachment Processing Pipeline: download từ Jira → chuyển đổi qua markitdown MCP → chunking → embeddings → vector store → tích hợp BatchScanEngine + AI Chat.

## Tasks

- [x] 147. Data models cho Attachment Processing
  - [x] 147.1 Cập nhật `shared/.../jira/JiraClient.kt`: thêm field `content: String? = null` vào `JiraAttachment` data class
    - _Requirements: AC 22.1_
  - [x] 147.2 Tạo `server/.../attachment/models/AttachmentChunk.kt`: data class `AttachmentChunk` (id, ticketId, attachmentId, filename, chunkIndex, chunkText, embedding: List<Float>, createdAt)
    - _Requirements: AC 22.10, AC 22.12_
  - [x] 147.3 Tạo `server/.../attachment/models/AttachmentStatus.kt`: data class `AttachmentStatusResponse` (attachmentId, filename, status, chunkCount, error) + enum `AttachmentProcessingStatus` (CONVERTED, PENDING, FAILED)
    - _Requirements: AC 22.19, AC 22.20_
  - [x] 147.4 Tạo `server/.../attachment/models/EmbeddingModels.kt`: data classes `OllamaEmbeddingRequest` (model, prompt) + `OllamaEmbeddingResponse` (embedding: List<Float>)
    - _Requirements: AC 22.9_
  - [x] 147.5 Tạo `server/.../attachment/models/TextChunk.kt`: data class `TextChunk` (index, text, tokenCount)
    - _Requirements: AC 22.10_

  - [x]* 147.6 Write property test: JiraAttachment serialization round-trip — verify content field preserved
    - **Property 1: JiraAttachment serialization round-trip**
    - **Validates: Requirements AC 22.1**

- [x] 148. SQLDelight schema — bảng `attachment_chunks`
  - [x] 148.1 Thêm vào `KnowledgeBase.sq`: CREATE TABLE `attachment_chunks` (id, ticket_id, attachment_id, filename, chunk_index, chunk_text, embedding TEXT, created_at) + indexes trên ticket_id và attachment_id
    - _Requirements: AC 22.12_
  - [x] 148.2 Thêm queries: insertAttachmentChunk, findChunksByTicketId, findChunksByAttachmentId, existsAttachmentChunks, getAllChunks, deleteChunksByTicketId, deleteChunksByAttachmentId
    - _Requirements: AC 22.12_
  - [x] 148.3 Cập nhật `ServerModule.kt` `runIncrementalMigrations()`: thêm migration tạo bảng `attachment_chunks` + indexes
    - _Requirements: AC 22.12_

- [x] 149. TextChunker — Chia text thành chunks
  - [x] 149.1 Tạo `server/.../attachment/TextChunker.kt`: object với fun `chunk(text: String, maxTokens: Int = 1000): List<TextChunk>`. Thuật toán: split theo paragraph → sentence → word. Ước lượng 1 token ≈ 0.75 words → maxWords = 750
    - _Requirements: AC 22.10_

  - [x]* 149.2 Write property test: Text chunking preserves content and respects size limit
    - **Property 3: Text chunking preserves content and respects size limit**
    - **Validates: Requirements AC 22.10**

- [x] 150. CosineSimilarity — Thuật toán tính similarity
  - [x] 150.1 Tạo `server/.../attachment/CosineSimilarity.kt`: object với fun `compute(a: FloatArray, b: FloatArray): Float`. Trả về dot(a,b) / (norm(a) * norm(b)), handle zero vectors
    - _Requirements: AC 22.11_

  - [x]* 150.2 Write property test: Cosine similarity search returns correctly ordered results
    - **Property 4: Cosine similarity search returns correctly ordered results**
    - **Validates: Requirements AC 22.11**

- [x] 151. Checkpoint — Core utilities
  - Ensure TextChunker + CosineSimilarity tests pass.

- [x] 152. EmbeddingService — Tạo embeddings qua Ollama
  - [x] 152.1 Tạo `server/.../attachment/EmbeddingService.kt`: interface với fun `embed(text: String): FloatArray?`
    - _Requirements: AC 22.9_
  - [x] 152.2 Tạo `server/.../attachment/EmbeddingServiceImpl.kt`: implementation gọi Ollama `POST /api/embeddings` với model `nomic-embed-text`. Sử dụng Ktor HttpClient từ Koin. Parse `OllamaEmbeddingResponse`
    - _Requirements: AC 22.9_

- [x] 153. VectorStore — Lưu trữ và tìm kiếm vector
  - [x] 153.1 Tạo `server/.../attachment/VectorStore.kt`: interface với saveChunk, existsByAttachmentId, search(queryEmbedding, topK), deleteByTicketId
    - _Requirements: AC 22.10, AC 22.11_
  - [x] 153.2 Tạo `server/.../attachment/VectorStoreImpl.kt`: SQLDelight-backed implementation. `search()`: load all chunks → parse embedding JSON → compute cosine similarity → sort desc → take topK
    - _Requirements: AC 22.11_

  - [x]* 153.3 Write property test: KB-First deduplication skips existing attachments
    - **Property 5: KB-First deduplication skips existing attachments**
    - **Validates: Requirements AC 22.15**

- [x] 154. AttachmentDownloader — Tải file từ Jira
  - [x] 154.1 Tạo `server/.../attachment/AttachmentDownloader.kt`: interface với fun `download(contentUrl: String, destPath: String, authHeader: String): Boolean`
    - _Requirements: AC 22.2_
  - [x] 154.2 Tạo `server/.../attachment/AttachmentDownloaderImpl.kt`: Ktor HttpClient streaming download, tạo parent directories, ghi file. Error handling: catch exceptions, return false
    - _Requirements: AC 22.2_

- [x] 155. AttachmentPipeline — Orchestrator
  - [x] 155.1 Tạo `server/.../attachment/AttachmentPipeline.kt`: class với constructor (downloader, embeddingService, vectorStore, mcpProcessManager, scanLogRepository, jiraAuthProvider). Companion object: SUPPORTED_EXTENSIONS, MAX_FILE_SIZE, MARKITDOWN_SERVER_ID
    - _Requirements: AC 22.3, AC 22.4, AC 22.6_
  - [x] 155.2 Triển khai `isEligible(attachment)`: kiểm tra extension ∈ SUPPORTED_EXTENSIONS VÀ size ≤ MAX_FILE_SIZE. Triển khai `getExtension(filename)`
    - _Requirements: AC 22.3, AC 22.4_
  - [x] 155.3 Triển khai `processAttachments(projectKey, ticketKey, attachments)`: loop qua attachments, KB-First check (existsByAttachmentId), eligibility check, download → markitdown → chunk → embed → store. Log mỗi bước vào ScanLogRepository. Xóa temp files sau khi xong
    - _Requirements: AC 22.6, AC 22.8, AC 22.13, AC 22.14, AC 22.15_
  - [x] 155.4 Triển khai markitdown MCP integration: kiểm tra McpProcessManager.getStatus("markitdown"), nếu RUNNING → gọi convert_to_markdown tool, nếu không → skip với warning log
    - _Requirements: AC 22.5, AC 22.6, AC 22.7_

  - [x]* 155.5 Write property test: Attachment eligibility filter
    - **Property 2: Attachment eligibility filter**
    - **Validates: Requirements AC 22.3, AC 22.4**

- [x] 156. Checkpoint — Attachment Pipeline core
  - Ensure all pipeline components work together. Mock external dependencies.

- [x] 157. Koin registration
  - [x] 157.1 Cập nhật `ServerModule.kt`: đăng ký EmbeddingService, VectorStore, AttachmentDownloader, AttachmentPipeline singletons. EmbeddingService cần HttpClient + ProviderConfigRepository (đọc Ollama endpoint). AttachmentPipeline cần tất cả dependencies
    - _Requirements: AC 22.2, AC 22.9, AC 22.10, AC 22.11_
  - [x] 157.2 Cập nhật BatchScanEngine registration trong `ServerModule.kt`: thêm AttachmentPipeline dependency (nullable/optional)
    - _Requirements: AC 22.13_

- [x] 158. Tích hợp BatchScanEngine
  - [x] 158.1 Cập nhật `BatchScanEngine` constructor: thêm `attachmentPipeline: AttachmentPipeline? = null` parameter
    - _Requirements: AC 22.13_
  - [x] 158.2 Cập nhật `processTicket()`: sau khi lưu KBRecord, nếu attachmentPipeline != null, gọi `getIssueDetails(ticketId)` lấy attachments, gọi `attachmentPipeline.processAttachments()`. Wrap trong try-catch để không block scan
    - _Requirements: AC 22.13, AC 22.14_

- [x] 159. Checkpoint — BatchScanEngine integration
  - Verify attachments processed after AI analysis. Mock AttachmentPipeline.

- [x] 160. Tích hợp ChatService
  - [x] 160.1 Cập nhật `ChatServiceImpl` constructor: thêm `embeddingService: EmbeddingService? = null`, `vectorStore: VectorStore? = null`
    - _Requirements: AC 22.16_
  - [x] 160.2 Triển khai `buildAttachmentContext(projectKey, message)`: gọi embeddingService.embed(message) → vectorStore.search(embedding, topK=5) → format chunks dạng `[{filename}] {chunkText}`. Trả về "" nếu service null hoặc lỗi
    - _Requirements: AC 22.16, AC 22.17, AC 22.18_
  - [x] 160.3 Cập nhật `buildFullPrompt()`: thêm `--- ATTACHMENT CONTEXT ---\n$attachmentCtx` vào prompt
    - _Requirements: AC 22.17_
  - [x] 160.4 Cập nhật Koin registration cho ChatServiceImpl: inject EmbeddingService + VectorStore
    - _Requirements: AC 22.16_

  - [x]* 160.5 Write property test: Attachment context building — format và giới hạn
    - **Property 6: Attachment context building — format và giới hạn**
    - **Validates: Requirements AC 22.17, AC 22.18**

- [x] 161. Checkpoint — ChatService integration
  - Verify attachment context appears in AI prompt. Mock VectorStore.

- [x] 162. Markitdown MCP auto-configuration
  - [x] 162.1 Triển khai logic trong AttachmentPipeline hoặc startup hook: kiểm tra McpServerRepository có config "markitdown" chưa, nếu chưa → tự động tạo config (id="markitdown", name="Markitdown", command="uvx", args=["markitdown"], disabled=false)
    - _Requirements: AC 22.5_

- [x] 163. API endpoint — Attachment status
  - [x] 163.1 Thêm `GET /api/projects/{key}/tickets/{ticketKey}/attachments` route: query VectorStore + scan_log để trả về danh sách AttachmentStatusResponse (CONVERTED/PENDING/FAILED) cho mỗi attachment
    - _Requirements: AC 22.20_

- [x] 164. Frontend — Scan log attachment count
  - [x] 164.1 Cập nhật scan log UI: parse scan log entries liên quan đến attachments, hiển thị "Processed {count} attachments for {ticketKey}" summary
    - _Requirements: AC 22.19_

- [x] 165. Frontend — Attachment status trong detail panel
  - [x] 165.1 Cập nhật Knowledge Graph detail panel: gọi `GET /api/projects/{key}/tickets/{ticketKey}/attachments`, hiển thị danh sách attachments với status icons (✅ Converted, ⏳ Pending, ❌ Failed)
    - _Requirements: AC 22.20_

- [x] 166. Checkpoint — Frontend integration
  - Verify scan log shows attachment count, detail panel shows attachment status.

- [x] 167. Unit tests
  - [x] 167.1 Test AttachmentDownloader: mock HttpClient, verify URL + auth header
    - _Requirements: AC 22.2_
  - [x] 167.2 Test AttachmentPipeline: markitdown MCP fallback (server down → skip, no crash)
    - _Requirements: AC 22.7_
  - [x] 167.3 Test EmbeddingService: mock Ollama API, verify request format
    - _Requirements: AC 22.9_
  - [x] 167.4 Test scan log entries: verify ANALYZING/COMPLETED/FAILED messages format
    - _Requirements: AC 22.14_

- [x] 168. Integration tests
  - [x] 168.1 Test full pipeline: mock download + MCP + Ollama → verify chunks saved in VectorStore
    - _Requirements: AC 22.2–AC 22.12_
  - [x] 168.2 Test BatchScanEngine + AttachmentPipeline: verify attachments processed after AI analysis
    - _Requirements: AC 22.13_
  - [x] 168.3 Test ChatService + VectorStore: verify attachment context in prompt
    - _Requirements: AC 22.16–AC 22.18_

- [x] 169. Checkpoint — All tests
  - Ensure all unit + integration + property tests pass.

- [x] 170. Final checkpoint — Attachment Processing Pipeline
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: JiraAttachment content field, download, markitdown conversion, chunking, embeddings, vector store, semantic search, BatchScanEngine integration, ChatService integration, frontend display

## Notes

- Tasks marked with `*` are optional property tests, can be skipped for faster MVP
- Mỗi task tham chiếu requirements cụ thể (AC 22.1–AC 22.20) để truy vết
- Checkpoints đảm bảo kiểm tra tăng dần sau mỗi nhóm logic
- Code tuân thủ: max 200 dòng/file, max 20 dòng/hàm, models tách package `models/`
- AttachmentPipeline là optional dependency trong BatchScanEngine (nullable) để backward compatible
