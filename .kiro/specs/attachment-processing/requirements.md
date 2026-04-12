# Attachment Processing Pipeline — Requirements

# Yêu cầu 22: Attachment Processing Pipeline — Chuyển đổi & Lưu trữ Vector

**User Story:** Là một Product Owner, tôi muốn hệ thống tự động tải attachment từ Jira tickets, chuyển đổi chúng thành markdown qua markitdown MCP, và lưu vào vector database để AI Chat có thể tham khảo nội dung attachment khi trả lời câu hỏi.

## Mô tả

Attachment Processing Pipeline mở rộng Batch Scan Engine để xử lý file đính kèm trong Jira tickets. Pipeline sử dụng Ollama Agent điều khiển markitdown MCP server để chuyển đổi các loại file (PDF, DOCX, XLSX, PPTX, images) thành markdown text, sau đó lưu vào vector database (embeddings) trong Knowledge Base để hỗ trợ semantic search và AI context enrichment.

## Thuật ngữ bổ sung

- **Attachment_Pipeline**: Thành phần backend xử lý attachment: download từ Jira → chuyển đổi qua markitdown MCP → tạo embeddings → lưu vector DB
- **Markitdown_MCP**: MCP server chạy markitdown tool, chuyển đổi file binary (PDF, DOCX, XLSX, PPTX, images) thành markdown text
- **Vector_Store**: Bảng SQLDelight lưu trữ text chunks với embeddings vector cho semantic search
- **Embedding_Service**: Service tạo text embeddings qua Ollama API (`POST /api/embeddings`)

## Tiêu chí chấp nhận

### Jira Attachment Download

22.1 THE JiraAttachment model SHALL chứa field `content` (download URL) từ Jira API v3 response, cho phép tải nội dung file attachment.

22.2 THE Attachment_Pipeline SHALL tải file attachment từ Jira API sử dụng URL trong field `content` với authentication header (Basic auth từ Jira credentials trong DB).

22.3 THE Attachment_Pipeline SHALL chấp nhận TẤT CẢ loại file (không filter theo extension) để tương lai có thể tích hợp các MCP khác xử lý các format mới. Chỉ giới hạn theo kích thước file (22.4). Nếu markitdown MCP không convert được file → fail gracefully với log entry, không block pipeline.

22.4 THE Attachment_Pipeline SHALL giới hạn kích thước file tải về tối đa 50MB. File vượt quá SHALL bị bỏ qua với log entry ghi rõ lý do.

### Markitdown MCP Integration

22.5 THE Backend_Server SHALL cấu hình markitdown MCP server (Microsoft markitdown) trong danh sách MCP servers, với command `uvx markitdown` hoặc tương đương.

22.6 THE Attachment_Pipeline SHALL gọi markitdown MCP tool `convert_to_markdown` với file path làm input, nhận markdown text làm output.

22.7 IF markitdown MCP server không khả dụng (STOPPED/ERROR), THEN THE Attachment_Pipeline SHALL thử tự động start markitdown process. Nếu start thất bại, bỏ qua bước chuyển đổi và log warning. Nếu markitdown crash giữa chừng, SHALL tự động restart và retry 1 lần trước khi skip.

22.8 THE Attachment_Pipeline SHALL lưu file tạm vào thư mục `data/attachments/{projectKey}/{ticketKey}/` trước khi gọi markitdown, và xóa file tạm sau khi chuyển đổi xong.

### Markitdown MCP ID Resolution

22.8a THE Attachment_Pipeline SHALL tìm markitdown MCP server theo name (case-insensitive) thay vì hardcode ID "markitdown", vì user có thể tạo MCP server với ID tùy chỉnh (auto-generated). Sử dụng `markitdownIdResolver` lambda để resolve actual DB ID.

22.8b THE Embedding_Service SHALL đọc Ollama endpoint dynamically từ DB mỗi lần embed (qua `endpointProvider` lambda), không cache endpoint lúc startup. Tìm provider theo `ProviderType.OLLAMA` thay vì hardcode ID "ollama".

### Ollama Embeddings & Vector Storage

22.9 THE Embedding_Service SHALL tạo text embeddings bằng Ollama API endpoint `POST /api/embed` (Ollama v0.20+) với model và endpoint đọc từ DB config (`ProviderType.EMBEDDING`). Request format: `{model, input}`. Response format: `{embeddings: [[float, ...]]}`. Fallback support cho legacy `{embedding: [float, ...]}` response format.

22.9a THE Backend_Server SHALL tự động tạo default EMBEDDING provider config khi startup nếu chưa có, với model `nomic-embed-text` và endpoint lấy từ Ollama config. User có thể thay đổi model/endpoint qua trang Integrations.

22.9b THE Frontend_App SHALL hiển thị card "Embedding Model" (🧬) trên trang Integrations với fields: Ollama Endpoint URL và Embedding Model name. Card hiển thị hint: "Run: ollama pull nomic-embed-text".

22.9c WHEN hệ thống sử dụng cả AI inference model (gemma4) và embedding model (nomic-embed-text) đồng thời, Ollama SHOULD được cấu hình `OLLAMA_MAX_LOADED_MODELS=2` và `OLLAMA_NUM_PARALLEL=2` để giữ cả 2 models trong VRAM, tránh model swapping chậm. Thông tin này SHALL được ghi trong README và .env.example.

22.10 THE Vector_Store SHALL lưu trữ mỗi attachment dưới dạng chunks (tối đa 1000 tokens/chunk) với: ticketId, attachmentId, filename, chunkIndex, chunkText, embedding vector (JSON array of floats), createdAt.

22.11 THE Vector_Store SHALL hỗ trợ semantic search: nhận query text → tạo embedding → tính cosine similarity với tất cả chunks → trả về top-K chunks có similarity cao nhất.

22.12 THE SQLDelight schema SHALL thêm bảng `attachment_chunks` với columns: id, ticket_id, attachment_id, filename, chunk_index, chunk_text, embedding (TEXT — JSON array), created_at.

### Tích hợp với Batch Scan Engine

22.13 WHEN BatchScanEngine xử lý một ticket có attachments, THE Attachment_Pipeline SHALL tự động tải và chuyển đổi tất cả attachments của ticket đó sau khi AI analysis hoàn tất.

22.14 THE Attachment_Pipeline SHALL log tiến trình xử lý attachment vào scan log: "Processing attachment {filename} for {ticketKey}" (ANALYZING), "Attachment converted: {filename} ({chunks} chunks)" (COMPLETED), hoặc "Attachment failed: {filename}: {error}" (FAILED).

22.15 THE Attachment_Pipeline SHALL sử dụng KB-First strategy: bỏ qua attachment đã có trong Vector_Store (kiểm tra theo attachmentId). Khi chạy lại scan, attachments đã xử lý thành công sẽ được skip với log "already in KB", tiết kiệm thời gian xử lý đáng kể cho các lần scan lặp lại.

### Tích hợp với AI Chat

22.16 WHEN AI Chat nhận câu hỏi liên quan đến nội dung file, THE ChatService SHALL query Vector_Store với semantic search để tìm attachment chunks liên quan, và đưa vào AI prompt context.

22.17 THE ChatService SHALL thêm attachment context vào prompt với format: "--- ATTACHMENT CONTEXT ---\n[{filename}] {chunkText}\n..."

22.18 THE ChatService SHALL giới hạn tối đa 5 attachment chunks trong context (top-5 similarity) để tránh prompt quá dài.

### Frontend — Attachment Status Display

22.19 THE Frontend_App SHALL hiển thị số lượng attachments đã xử lý trong scan log: "Processed {count} attachments for {ticketKey}".

22.20 WHEN người dùng xem chi tiết ticket trong Knowledge Graph detail panel, THE Frontend_App SHALL hiển thị danh sách attachments với trạng thái (✅ Converted, ⏳ Pending, ❌ Failed).

---

**Tổng: 20 tiêu chí chấp nhận**
