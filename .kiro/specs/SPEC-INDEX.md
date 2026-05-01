# Jira Assistant — Spec Index

## Tổng quan

Tất cả specs đã hoàn thành và được archive. Thư mục `.kiro/specs/_archived/` chứa toàn bộ lịch sử specs theo domain.

## Master Requirements (10 documents tổng hợp)

| # | Domain | File | Specs gốc |
|---|--------|------|-----------|
| 1 | 🏗️ Core Platform | [01-core-platform.md](01-core-platform.md) | 6 specs |
| 2 | 📊 Dashboard & Project | [02-dashboard-project.md](02-dashboard-project.md) | 2 features + 4 bugs |
| 3 | 🔍 Ticket Intelligence | [03-ticket-intelligence.md](03-ticket-intelligence.md) | 6 features + 6 bugs |
| 4 | 📄 Document Generation | [04-document-generation.md](04-document-generation.md) | 8 features + 5 bugs |
| 5 | 🤖 AI Agent Framework | [05-ai-agent-framework.md](05-ai-agent-framework.md) | 9 features + 3 bugs |
| 6 | 💬 AI Chat | [06-ai-chat.md](06-ai-chat.md) | 2 features + 2 bugs |
| 7 | 🔌 MCP & Integrations | [07-mcp-integrations.md](07-mcp-integrations.md) | 3 features + 3 bugs |
| 8 | 🕸️ Knowledge Graph | [08-knowledge-graph.md](08-knowledge-graph.md) | 4 features + 1 bug |
| 9 | 👥 User Management | [09-user-management.md](09-user-management.md) | 1 feature + 1 bug |
| 10 | 🧪 Testing | [10-testing.md](10-testing.md) | 1 feature + 1 bug |

---

## Archived Specs — Tham chiếu theo Domain

### 1. 🏗️ Core Platform
- `backend-core` ✅ — Ktor server, routing, DI, middleware
- `cross-cutting` ✅ — Logging, error handling, shared concerns
- `auth-and-rbac` ✅ — Authentication, JWT, roles, permissions
- `postgresql-pgvector-migration` ✅ — PostgreSQL + pgvector migration
- `legacy-pipeline-removal` ✅ — Xóa code SQLite cũ
- `design-system-ux` ✅ — UI design system

### 2. 📊 Dashboard & Project
- `dashboard` ✅ — Dashboard page, metrics, scan control
- `project-analysis` ✅ — Project analysis, sprint analytics
- 🐛 project-select-display-bug, no-jira-redirect-to-integrations, page-slow-restore-fix, scan-progress-race-condition

### 3. 🔍 Ticket Intelligence & Analysis
- `ticket-intelligence` ✅ — Ticket analysis page, deep analysis UI
- `batch-scan-engine` ✅ — Batch scanning, concurrent analysis
- `map-reduce-analysis` ✅ — Map-reduce pipeline cho large ticket graphs
- `deep-ticket-data-collection` ✅ — Deep BFS traversal, comment/attachment collection
- `attachment-processing` ✅ — Attachment pipeline, markitdown, embeddings
- `linked-ticket-attachments` ✅ — Linked ticket attachment processing
- 🐛 ticket-intelligence-ux-bugs, batch-scan-placeholder-analysis, linked-ticket-analysis-bug, single-ticket-attachment-processing, duplicate-deep-extraction, invalid-jira-key-fetch

### 4. 📄 Document Generation
- `ai-generated-brd-fsd` ✅ — BRD/FSD/Slides generation core
- `document-job-manager` ✅ — Job manager, versioning, review workflow
- `deep-brd-generation` ✅ — Deep BRD generation with full context
- `multi-phase-brd-pipeline` ✅ — Multi-phase AI pipeline
- `prompt-curation-pipeline` ✅ — Prompt curation, AS-IS/TO-BE classification
- `streaming-generation-progress` ✅ — Ollama streaming progress
- `docgen-ux-improvement` ✅ — Document generation UX improvements
- `drawio-template-diagrams` ✅ — Draw.io diagram templates
- 🐛 brd-diagram-and-sections-fix, brd-pipeline-local-kb-integration, document-approve-fix, zombie-job-recovery, brd-insufficient-data-fix

### 5. 🤖 AI Agent Framework
- `generic-agent-framework` ✅ — Core agent framework: interfaces, memory, tools, engine
- `agent-document-generation` ✅ — BA Document Agent implementation
- `agent-subprocess-orchestration` ✅ — Subprocess management, stdin/stdout
- `agent-mcp-tool-bridge` — MCP tool bridge for agents (spec only)
- `cli-interactive-ba-agent` ✅ — CLI interactive mode
- `copilot-kiro-cli-integration` ✅ — Copilot/Kiro CLI integration
- `multi-turn-ba-orchestration` ✅ — Multi-turn orchestration
- `poc-agent-replacement` ✅ — POC replacement
- `native-tool-removal` ✅ — Remove native tools
- 🐛 cli-protocol-bypass, stdout-blocking-timeout-fix, ba-agent-mcp-tools-fix

### 6. 💬 AI Chat
- `ai-chat-sidebar` ✅ — AI chat sidebar, MCP tool integration, popup forms
- `per-user-tool-permissions` ✅ — Per-user tool enable/disable
- 🐛 chat-empty-reply-bug, mcp-agentic-loop-fix

### 7. 🔌 MCP & Integrations
- `mcp-servers` ✅ — MCP server management, internal MCP
- `integrations` ✅ — Integrations page, AI providers
- `mcp-readiness-check` ✅ — MCP readiness validation
- `remove-jira-cloud-badge` ✅ — Loại bỏ Jira Cloud Services card khỏi UI (backend Jira infrastructure giữ nguyên)
- 🐛 mcp-tool-prompt-injection, jira-modal-consistency, real-mcp-integration-test

### 8. 🕸️ Knowledge Graph
- `graph-filter-focus-mode` ✅ — Filter, focus mode, search
- `incremental-graph-rendering` ✅ — Incremental rendering, fade-in
- `knowledge-graph-3d` ✅ — 3D visualization
- `knowledge-graph-optimization` ✅ — Performance optimization
- 🐛 graph-build-on-scan-complete

### 9. 👥 User Management
- `user-management` ✅ — User management page
- 🐛 user-management-audit-fix

### 10. 🧪 Testing
- `serenity-report-feature-hierarchy` ✅ — Serenity BDD test reporting
- 🐛 e2e-test-element-interaction-fixes

---

## Thống kê

- **Active specs:** 0
- **Archived specs:** 67+ (all features + bugfixes)
- **Tổng tasks hoàn thành:** ~2,400+
