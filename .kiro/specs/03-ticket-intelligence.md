# Ticket Intelligence & Analysis — Master Requirements

## Tổng quan

Domain Ticket Intelligence bao gồm toàn bộ pipeline phân tích ticket: từ single ticket analysis trên trang Ticket Intelligence đến batch scan engine quét toàn bộ project, deep ticket data collection (BFS traversal đệ quy), map-reduce analysis cho ticket graphs lớn, attachment processing pipeline (download → markitdown → chunk → embed → store), và linked ticket attachment processing. Hệ thống sử dụng KB-First strategy, AI failover, và progressive loading.

Deep Analysis nâng cấp pipeline: trích xuất dữ liệu Jira có cấu trúc (ADF parsing, comments, changelog, sub-tasks, issue links), xây dựng prompt AI chi tiết, và mở rộng data model. Map-Reduce pipeline cho phép phân tích ticket graphs > 200 tickets bằng cách chia batches và tổng hợp kết quả.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `ticket-intelligence` | Feature | ✅ Archived | Ticket Intelligence page, deep analysis pipeline, combobox UI |
| `batch-scan-engine` | Feature | ✅ Archived | Batch scan engine, parallel processing, scan state management |
| `map-reduce-analysis` | Feature | ✅ Archived | Map-reduce pipeline cho large ticket graphs |
| `deep-ticket-data-collection` | Feature | ✅ Archived | Deep BFS traversal, comment/attachment collection |
| `attachment-processing` | Feature | ✅ Archived | Attachment pipeline: Jira download → markitdown → embeddings |
| `linked-ticket-attachments` | Feature | ✅ Archived | Linked ticket attachment processing |
| `ticket-intelligence-ux-bugs` | Bugfix | ✅ Archived | Fix combobox state restore + auto-select on blur |
| `batch-scan-placeholder-analysis` | Bugfix | ✅ Archived | Fix batch prompt gửi ticket ID không có content |
| `linked-ticket-analysis-bug` | Bugfix | ✅ Archived | Fix linked tickets bị bỏ qua + attachment bottleneck |
| `single-ticket-attachment-processing` | Bugfix | ✅ Archived | Fix single ticket analyze không xử lý attachments |
| `duplicate-deep-extraction` | Bugfix | ✅ Archived | Fix deep extraction chạy 2 lần per analyze |
| `invalid-jira-key-fetch` | Bugfix | ✅ Archived | Fix invalid Jira keys gây 404 errors |

## Requirements tổng hợp

### Ticket Intelligence Page

- Combobox searchable dropdown: tìm ticket theo ID hoặc summary, hỗ trợ cross-project tickets
- Trạng thái phân tích: NOT_ANALYZED, SCANNED, ANALYZED, HAS_UPDATES, ANALYZING
- Nút hành động động: ANALYZE / RE-ANALYZE / ANALYZING... (disabled)
- 3 tab kết quả: Context (requirement summary, modules), Evolution (timeline changelog), Complexity (Scrum point, KB references)
- Fire-and-forget pattern: POST /reanalyze → 202 Accepted, polling status mỗi 3s
- Progress 4 giai đoạn: Fetching Jira (0-20%), Extracting (20-35%), AI Analyzing (35-85%), Syncing KB (85-100%)
- SessionStorage state restore: hiển thị ngay khi quay lại trang, auto-select on blur

### Deep Analysis Pipeline

- `JiraContentExtractor`: fetch full issue details + ADF parsing + section classification
- Trích xuất: summary, description, status, priority, story points, type, assignee, reporter, labels, components
- Sub-tasks: key, summary, status
- Issue links: key, summary, relationship type (blocks, relates to, duplicates)
- Comments: tối đa 20 gần nhất (author, date, content)
- Changelog: status, priority, story points, assignee, summary changes
- Attachments: filename, mimeType, size metadata
- Ticket ID validation: regex `[A-Z][A-Z0-9]+-\d+` trước API calls

### Deep Ticket Data Collection (BFS Traversal)

- BFS traversal từ root ticket qua 4 loại quan hệ: issue links, sub-tasks, parent, text references
- Visited_Set cho cycle detection, Ticket_Graph lưu nodes + metadata
- Traversal_Config: maxDepth (1-20), maxTickets (1-1000), timeout 600s
- Priority: parent → blocking links → other links → sub-tasks → text references
- Graceful error handling: skip failed tickets, continue traversal
- Ticket_ID_Extractor: regex trích xuất ticket IDs từ text fields

### Map-Reduce Analysis

- Tự động kích hoạt khi > 200 tickets (backward compatible với single-prompt)
- Map Phase: chia tickets thành batches theo depth/cluster, gửi AI tạo Batch_Summary
- Reduce Phase: tổng hợp tất cả summaries thành AnalysisResult cuối cùng
- Unlimited traversal: maxDepth=20, maxTickets=1000, timeout=600s
- Concurrent batch processing, progress tracking

### Batch Scan Engine

- Scan state per project: IDLE, SCANNING, PAUSED, CANCELLED, COMPLETED
- API endpoints: start, pause, resume, cancel, status, log
- Parallel batch processing: concurrency 1-10 (default 3), AI Semaphore (default 1)
- Pipeline per ticket: Jira fetch → AI analysis → relationships + attachments
- KB-First strategy: skip tickets đã có kết quả (trừ force re-analyze)
- Multi-project scan visibility: `GET /api/scan/active`, stacked progress bars
- Error resilience: skip failed tickets, auto-pause on restart, max 1 scan/project

### Attachment Processing Pipeline

- Download từ Jira API (max 50MB), tất cả file types
- Markitdown MCP: `convert_to_markdown` với file URI, auto-start/restart
- Chunking: tối đa 1000 tokens/chunk
- Embedding: Ollama `POST /api/embed` (nomic-embed-text), dynamic endpoint từ DB
- Vector Store: attachment_chunks table, cosine similarity search
- KB-First dedup: `existsByAttachmentId()` skip đã xử lý
- Linked ticket attachments: xử lý TẤT CẢ tickets trong TicketGraph (đồng bộ)

### Scrum Estimation

- Thang điểm: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40
- AI parse: Requirement Summary, Evolution History, Complexity Assessment + Scrum point
- Retry 2 lần khi JSON không hợp lệ

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `ticket-intelligence-ux-bugs` | Fix combobox state restore chậm (sessionStorage) + auto-select khi gõ đúng ticket ID và blur |
| `batch-scan-placeholder-analysis` | Fix batch prompt gửi ticket ID không có content → AI trả placeholder text |
| `linked-ticket-analysis-bug` | Fix linked tickets bị bỏ qua khi scan + attachment processing đồng bộ gây bottleneck |
| `single-ticket-attachment-processing` | Fix single ticket analyze không xử lý attachments (chỉ batch scan mới xử lý) |
| `duplicate-deep-extraction` | Fix deep extraction chạy 2 lần per analyze — cache kết quả từ tryMapReduce |
| `invalid-jira-key-fetch` | Fix invalid Jira keys (e.g. "active-jobs") gây 404 errors — validate regex trước API call |
