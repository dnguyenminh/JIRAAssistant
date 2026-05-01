# Document Generation — Master Requirements

## Tổng quan

Domain Document Generation bao gồm toàn bộ pipeline sinh tài liệu BRD (Business Requirements Document), FSD (Functional Specification Document), và Slides từ kết quả phân tích ticket. Hệ thống sử dụng AI agents (Ollama, Gemini CLI) với agentic loop, MCP tools cho data collection, và multi-phase pipeline chia nhỏ prompt để tránh "lost in the middle" problem. Document Job Manager quản lý background jobs với dependency chain (BRD → FSD → Slides), versioning, và review/approve workflow.

Pipeline đã trải qua nhiều cải tiến: từ single-prompt generation sang multi-phase pipeline (3 AI phases + assembly), prompt curation pipeline phân loại AS-IS/TO-BE/OUTDATED, streaming progress từ Ollama, draw.io template-based diagrams, và UX improvements (granular progress, cancel, timeout warning).

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `ai-generated-brd-fsd` | Feature | ✅ Archived | BRD/FSD/Slides generation core, templates, preview/export |
| `document-job-manager` | Feature | ✅ Archived | Job manager, dependency chain, versioning, review workflow |
| `deep-brd-generation` | Feature | ✅ Archived | Recursive deep exploration, KB caching, draw.io diagrams |
| `multi-phase-brd-pipeline` | Feature | ✅ Archived | Multi-phase AI pipeline (3 phases + assembly) |
| `prompt-curation-pipeline` | Feature | ✅ Archived | Prompt curation: AS-IS/TO-BE classification, budget management |
| `streaming-generation-progress` | Feature | ✅ Archived | Ollama streaming progress (NDJSON) |
| `docgen-ux-improvement` | Feature | ✅ Archived | Granular progress, elapsed time, cancel, error detail |
| `drawio-template-diagrams` | Feature | ✅ Archived | Draw.io template-based diagrams (deployment, BPMN) |
| `brd-diagram-and-sections-fix` | Bugfix | ✅ Archived | Fix draw.io viewer offline + "Insufficient data" in sections |
| `brd-pipeline-local-kb-integration` | Bugfix | ✅ Archived | Fix multi-phase pipeline không detect Local KB tools |
| `document-approve-fix` | Bugfix | ✅ Archived | Fix approve/reject fail do thiếu document ID trong API response |
| `zombie-job-recovery` | Bugfix | ✅ Archived | Fix zombie jobs block generation (crashed job status stuck RUNNING) |
| `brd-insufficient-data-fix` | Bugfix | ✅ Archived | Fix BRD sections hiển thị "Insufficient data" do heading parsing sai |

## Requirements tổng hợp

### Document Generation Core

- BRD template: Carleton University ITS (7 sections), FSD template: FECredit (11 sections)
- Document_Aggregator: thu thập KBRecord + linked tickets + sub-tasks + VectorStore chunks
- AI prompt chứa Generation_Context + template, yêu cầu trích dẫn nguồn dữ liệu
- Preview rendered Markdown, export Markdown/PDF
- Dependency chain: BRD → FSD → Slides, "Generate All" chạy tuần tự

### Multi-Phase BRD Pipeline

- Phase 1 (Data Collection): Jira + KB tools, thu thập data vào KB memory
- Phase 2 (BRD Writing): KB tools, sinh BRD markdown từ collected data
- Phase 3 (Diagram Generation): diagram + KB tools, sinh draw.io XML
- Assembly Step: merge Phase 2 + Phase 3 outputs
- Single-phase fallback khi KB không available
- Mỗi phase: prompt ~15-20K chars (thay vì ~84K single prompt)

### Prompt Curation Pipeline

- KB-First: dùng KB_Record fields thay vì raw ticket data
- Temporal classification: AS-IS (existing), TO-BE (new requirements), OUTDATED (superseded)
- Comment summarization: dedup + summarize raw comments
- Prompt budget: 80K-120K chars, truncation strategy
- MCP KB Lookup: on-demand query thay vì pre-load tất cả

### Deep BRD Generation

- Recursive exploration: linked tickets → attachments → linked tickets of linked tickets
- Configurable depth via `TraversalConfig.maxDepth` (default 5, analysis mode 20), `maxTickets` (default 50, analysis 1000)
- Visited_Set + Queued_Set cho cycle detection — `TraversalState` check cả `visited` VÀ `queued` trước khi enqueue, ngăn duplicate fetches
- `TicketIdExtractor` validate ticket keys: filter bỏ false positives (month names, quarter refs, protocol prefixes, tech terms UTF/AES/SHA, generic keys UC/BD/TMPL/DATA/USER), issue numbers > 99999, project key length 2-8 chars
- KB-First fetching: `KBFirstTicketFetcher` check KBRepository trước khi gọi Jira API — nếu KB có data, dùng luôn mà không gọi Jira. Write-through: sau khi fetch từ Jira thành công, tự động lưu vào KB để lần sau skip Jira API. Được dùng trong cả `DeepCollectorPhases` (document generation) và `DeepJiraContentExtractor` (analysis/map-reduce)
- KB Cache Strategy: check KB → dùng nếu có → gọi Jira nếu không → lưu KB
- Draw.io diagrams: Sequence, Class, Activity, Deployment (XML format)

### Document Job Manager

- Background jobs: QUEUED → RUNNING → COMPLETED/FAILED/CANCELLED
- Generation Lock: ngăn duplicate job cho cùng ticket + type
- Review workflow: DRAFT → APPROVED/REJECTED
- Document versioning: version number tăng dần, active version = latest approved
- Global Job Indicator trên navbar, pause/cancel từ bất kỳ trang nào
- `JobManager` track coroutine references trong `activeCoroutines` map — `cancelJob()` gọi `.cancel()` trên coroutine đang chạy để dừng thực sự (không chỉ update DB status)
- `executeJobSafe()` handle `CancellationException` riêng biệt, cleanup coroutine reference trong `finally` block
- `TraversalEngine` BFS loop và `processNextLevel` gọi `coroutineContext.ensureActive()` mỗi iteration — khi coroutine bị cancel, throw `CancellationException` ngay lập tức thay vì chờ hết BFS level
- `AiBackendPipelineStrategy` catch `CancellationException` riêng, gọi `OllamaApiClient.cancel()` để close HTTP client và abort in-flight Ollama request — cancel có hiệu lực ngay lập tức kể cả khi AI đang generate response
- `AiBackend` interface: `sendPrompt()` và `sendMessage()` là `suspend fun` — cho phép coroutine cancellation propagate tự nhiên qua HTTP calls thay vì bị block bởi `runBlocking`
- `OllamaApiClientHelpers.sendChatRequest()` là `suspend fun` (không dùng `runBlocking`) — khi coroutine bị cancel, HTTP request bị abort ngay lập tức
- `OllamaApiClient.ensureCancelledOrActive()` check cả coroutine `Job.isActive` lẫn volatile `cancelled` flag trước mỗi HTTP call — double-check mechanism đảm bảo cancel được phát hiện dù từ coroutine scope hay từ `cancel()` method
- Streaming response loop (`readStreamingResponse`) check coroutine cancellation mỗi line — abort sớm khi job bị cancel giữa chừng streaming
- `InlineProgressRenderer` dùng **event delegation** pattern: `installCancelDelegate()` gắn click listener lên parent area element **1 lần duy nhất**, listener tồn tại qua mọi lần `innerHTML` rebuild (mỗi 2s poll). Thay thế cách cũ gắn listener trực tiếp lên button clone mỗi lần render (bị mất khi DOM rebuild). `removeCancelDelegate()` cleanup khi job kết thúc

### Streaming & Progress

- Ollama streaming: `stream: true`, NDJSON lines, progress callback
- Progress range: AGGREGATING_DATA (0-30%), GENERATING_DOCUMENT (30-85%), PARSING (85-90%), SAVING (90-100%)
- Elapsed time + estimated remaining, phase detail labels
- Timeout warning gần 5 phút, cancel button, animated progress bar

### Draw.io Template Diagrams

- Template-based: AI sinh JSON metadata, merge vào XML template
- Template Registry: flow, deployment, component, dependency, BPMN
- Hybrid: Mermaid cho simple diagrams, draw.io cho rich diagrams
- Draw.io Viewer: CDN on-demand, offline fallback

### UX Improvements

- Granular sub-phase tracking thay vì progress đứng yên
- Error detail cụ thể khi generation fail
- Nút Generate disabled khi job đang chạy (generation lock)
- Elapsed time + estimated remaining hiển thị cho user
- Timeout warning khi gần 5 phút
- Cancel button cho job đang chạy
- Animated progress bar (smooth transitions)

### Document Templates

- **BRD (Carleton University ITS)**: 7 sections — Revision History, Project Overview (Sponsor, Contributors, Scope), Acronyms, Existing Processes (Narrative, Timing, Volume, Screenshots, Problems), Project Requirements (Process Overview, Functional/Non-Functional/Data Requirements), Sign Off, Appendix (Mock-ups, Glossary, Business Rules, References)
- **FSD (FECredit)**: 11 sections — Introduction, System Overview, Functional Specifications (Use Cases, Mock-ups, Field Specs), System Configurations, Non-Functional Requirements, Reporting, Integration (Exception Handling), Data Migration, References, Open Issues, Appendix
- **Slides**: Executive-level presentation từ BRD content

### API Endpoints

- `POST /api/analysis/{ticketId}/generate-brd` — sinh BRD
- `POST /api/analysis/{ticketId}/generate-fsd` — sinh FSD
- `POST /api/analysis/{ticketId}/generate-slides` — sinh Slides
- `POST /api/analysis/{ticketId}/generate-all` — sinh tất cả (chain)
- `GET /api/jobs/{jobId}` — trạng thái job (polling mỗi 3s)
- `POST /api/jobs/{jobId}/cancel` — hủy job
- `GET /api/documents/{ticketId}` — danh sách documents
- `POST /api/documents/{documentId}/approve` — approve document
- `POST /api/documents/{documentId}/reject` — reject document

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `brd-diagram-and-sections-fix` | Fix draw.io viewer offline fallback + "Insufficient data" trong Project Requirements section |
| `brd-pipeline-local-kb-integration` | Fix multi-phase pipeline không detect Local KB tools — thêm LocalKBToolExecutor vào tool collection |
| `document-approve-fix` | Fix approve/reject fail — thêm document `id` (Long) vào API response, frontend dùng numeric ID |
| `zombie-job-recovery` | Fix zombie jobs (crashed RUNNING status) block generation — auto-detect và recover stale jobs |
| `brd-insufficient-data-fix` | Fix BRD sections "Insufficient data" — flexible heading parsing (numbering, casing, H1/H2), deep analysis validation |
