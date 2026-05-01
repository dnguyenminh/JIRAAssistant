# Core Platform — Master Requirements

## Tổng quan

Domain Core Platform bao gồm nền tảng kỹ thuật cốt lõi của Jira Assistant: Ktor backend server với REST API, Kotlin/JS frontend với HTML Templates + Vite bundler, shared module Kotlin Multiplatform cho domain models, hệ thống xác thực JWT với RBAC 3 vai trò, Knowledge Base persistence layer (đã migrate từ SQLite sang PostgreSQL + pgvector), design system Obsidian Kinetic, và các cross-cutting concerns (error handling, serialization, deployment).

Hệ thống đã trải qua migration lớn từ SQLite sang PostgreSQL với pgvector extension cho vector search (HNSW indexing), xóa toàn bộ legacy BA pipeline (4-phase thinking loop) để chỉ giữ subprocess orchestration, và chuẩn hóa Jira API v3 compatibility.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `backend-core` | Feature | ✅ Archived | Ktor server, REST API, KB persistence, AI orchestration |
| `cross-cutting` | Feature | ✅ Archived | Frontend-backend integration, serialization, deployment, security |
| `auth-and-rbac` | Feature | ✅ Archived | JWT authentication, RBAC engine, login/project select flow |
| `postgresql-pgvector-migration` | Feature | ✅ Archived | PostgreSQL + pgvector migration, Flyway, HikariCP |
| `legacy-pipeline-removal` | Feature | ✅ Archived | Xóa legacy 4-phase BA pipeline, giữ subprocess only |
| `design-system-ux` | Feature | ✅ Archived | Obsidian Kinetic design system, UX standards |

## Requirements tổng hợp

### Backend Server & REST API

- Ktor framework + Kotlin Serialization (JSON) + Koin (DI)
- Endpoint groups: `/api/auth`, `/api/projects`, `/api/analysis`, `/api/estimation`, `/api/graph`, `/api/users`, `/api/integrations`, `/api/chat`, `/api/mcp`
- HTTP status codes chuẩn: 200, 201, 400, 401, 403, 404, 500
- Input validation trả 400 với mô tả chi tiết, internal errors trả 500 không tiết lộ nội bộ
- `/health` endpoint: trạng thái Jira API, AI provider, Knowledge Base
- Config từ environment variables, không hardcode

### Authentication & RBAC

- Login page standalone (không Shell): username/password, 2 default users (admin/admin123 → ADMINISTRATOR, user/user123 → READER)
- JWT token 24 giờ, chứa userId, email, role — lưu sessionStorage
- Project Selection page: table với search/filter/sort/pagination, lưu project key vào sessionStorage
- First-launch flow: no JWT → login, no project → project_select, no Jira config + Admin → integrations
- RBAC 3 vai trò: Administrator (toàn quyền), Neural_Architect (AI + KB + dashboard), Reader (chỉ xem)
- Server-side enforcement + UI-level disable, thay đổi quyền áp dụng ngay lập tức
- Jira API v3: `/rest/api/3/search/jql`, cursor-based pagination, ADF description format

### Knowledge Base Persistence

- `KBRepository`: ticket_id, requirement_summary, evolution_history, scrum_points, confidence_score, rationale
- Lưu kết quả trong 1 giây, truy vấn < 200ms cho 10,000+ records
- RE-ANALYZE ghi đè bản ghi cũ, retry 3 lần khi ghi lỗi
- Graph data (nodes, edges) lưu per project

### AI Orchestration & Failover

- Multi-provider: Ollama, Gemini, LM Studio, Gemini CLI
- Failover tự động theo priority, timeout 30 giây
- Dynamic provider config từ DB mỗi lần gọi (không cần restart)
- Enriched ticket analysis: summary + description + status + resolution (cap 3000 chars)
- Default fallback: Ollama tại localhost:11434

### PostgreSQL + pgvector Migration

- Docker Compose: `pgvector/pgvector:pg16`, health check, named volume
- HikariCP connection pool (default 10 connections), validate on startup
- Flyway schema migration: versioned SQL files, auto-run on startup
- `attachment_chunks.embedding`: `vector(768)` type + HNSW index (cosine distance)
- `PgVectorStoreImpl`: native pgvector `<=>` operator, parameterized queries
- Tất cả repositories chuyển sang PostgreSQL-backed implementations
- SQLite dependencies đã xóa hoàn toàn (SqliteModule, DB_PATH, sqlite-driver)
- Environment: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`

### Legacy Pipeline Removal

- Xóa 4-phase thinking loop: CollectPhase, ExpandPhase, VisualizePhase, SynthesizePhase
- Xóa legacy orchestrators: CustomKotlinOrchestrator, LangChain4jOrchestrator
- Xóa MasterPromptBuilder (giữ MasterPromptSections cho TaskMessageBuilder)
- BADocumentAgent chỉ dùng Subprocess_Orchestrator, không feature flags
- JobExecutor chỉ subprocess path, không fallback chain
- Giữ CLI agent classes cho provider test connections

### Design System — Obsidian Kinetic

- Glassmorphism: transparency 0.7-0.8, backdrop-blur 10-20px
- Font: Be Vietnam Pro (100/300/400/600/700), JetBrains Mono (monospace)
- Hover effects: translateY -5px, scale 1.02
- Glass-styled tooltips, progress bars, status tickers
- Kiến trúc: Kotlin/JS + HTML Templates + CSS + Vite (HMR)

### UX Standards

- Error handling: error banner + RETRY + navigation button, không fail silently
- Empty states: message giải thích + gợi ý hành động
- BlockingOverlay cho async operations (> 200ms): spinner + message + chặn interaction
- Toast notifications cho HTTP errors, 401 → redirect login
- Scan log: timestamp HH:mm:ss, ticket ID, status badge, message chi tiết

### Serialization & Cross-cutting

- Round-trip serialize/deserialize cho: ScrumEstimation, NetworkGraph, AIResult, JiraIssue, KBRecord
- JSON parser trả lỗi rõ ràng khi thiếu trường bắt buộc
- Shared module Kotlin Multiplatform cho type-safety end-to-end

### Deployment & Security

- Docker multi-stage: build (Gradle + JDK) → runtime (JRE minimal)
- docker-compose.yml: Backend + Frontend + PostgreSQL
- Air-gapped mode: Ollama on-premise, không cần internet
- Credentials encrypted AES-256-GCM, JWT secret từ env
- Audit log cho thao tác nhạy cảm

## Resolved Issues

_Không có bugfix specs trong domain này._
