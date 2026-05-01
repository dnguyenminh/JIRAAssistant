# MCP & Integrations — Master Requirements

## Tổng quan

Domain MCP & Integrations quản lý tất cả kết nối bên ngoài của hệ thống: AI Providers (Ollama, Gemini, LM Studio, Gemini CLI, Copilot CLI, Kiro CLI, Embedding), và MCP (Model Context Protocol) Servers. Jira Cloud Services card đã bị loại bỏ khỏi UI (xem spec `remove-jira-cloud-badge`) — backend Jira infrastructure (JiraCredentialsService, JiraRestClient, IntegrationRoutes) vẫn được giữ nguyên cho các module analysis, batch scan, docgen, project routes. Trang Integrations cung cấp UI thống nhất để cấu hình, test connection, và monitor status của tất cả providers và MCP servers. MCP servers giao tiếp qua JSON-RPC 2.0 over stdio, hỗ trợ tool discovery, execution, và health monitoring. Internal MCP Server (`jira-assistant-ui`) expose 30+ tools điều khiển ứng dụng qua AI. MCP Readiness Check kiểm tra sẵn sàng trước khi sinh tài liệu.

Các bugfix đã giải quyết: Jira modal không nhất quán với AI provider modals, MCP tool prompt injection (chỉ 2/101+ tools trong prompt), và integration test dùng fake tool layer thay vì real MCP infrastructure.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `integrations` | Feature | ✅ Archived | Trang Integrations — Jira + AI providers management |
| `mcp-servers` | Feature | ✅ Archived | MCP server registration, protocol, tools, Internal MCP Server |
| `mcp-readiness-check` | Feature | ✅ Archived | Health check MCP servers trước document generation |
| `jira-modal-consistency` | Bugfix | ✅ Archived | Fix Jira config modal không nhất quán với AI provider modals (modal đã bị loại bỏ hoàn toàn trong spec `remove-jira-cloud-badge`) |
| `mcp-tool-prompt-injection` | Bugfix | ✅ Archived | Fix BA agent prompt chỉ có 2/101+ MCP tools |
| `real-mcp-integration-test` | Bugfix | ✅ Archived | Thay fake tool layer bằng real MCP infrastructure trong integration tests |

## Requirements tổng hợp

### AI Provider Management

- Provider cards hiển thị trên Integrations page: Ollama, Gemini API, LM Studio, Gemini CLI, Copilot CLI, Kiro CLI, Embedding (Jira Cloud Services card đã bị loại bỏ khỏi UI — xem spec `remove-jira-cloud-badge`; backend Jira infrastructure vẫn được giữ nguyên)
- Status dot: Active (xanh), Standby (xanh dương), Offline (đỏ) — persist trong DB
- Config modal riêng cho mỗi provider: 2 nút "TEST CONNECTION" + "SAVE" (SAVE disabled đến khi test pass)
- Ollama model: dropdown từ `GET /api/integrations/ollama/models`
- Max Tokens: slider range 256–32768 cho tất cả providers
- Gemini CLI: spawn process per request, cross-platform, model dropdown (Auto/Pro/Flash)
- Credentials encrypted AES-256-GCM trong bảng `provider_configs`
- Drag-and-drop thay đổi thứ tự failover priority
- START/STOP buttons trên mỗi card (Administrator only)
- RBAC: Administrator chỉnh sửa, Reader/Neural_Architect chỉ xem

### MCP Server Registration

- Section "MCP Servers" trên Integrations page, cards tương tự provider cards
- Form mode + JSON mode (toggle), tương thích mcp.json format
- Fields: Server Name, Command, Args, Env Variables, Auto Approve Tools, Disabled
- Import/export: `GET /api/integrations/mcp/export`, `POST /api/integrations/mcp/import`
- Duplicate prevention: reject 409 Conflict nếu name trùng (case-insensitive)
- Auto-configure markitdown khi startup, suppression marker khi user xóa
- Merge runtime status từ McpProcessManager vào API response

### MCP Process Management

- `McpProcessManager`: startServer, stopServer, restartServer, getRunningServers
- Auto-start tất cả enabled servers khi app startup (parallel, timeout 30s)
- Process crash: auto-restart tối đa 3 lần (exponential backoff 2s/4s/8s)
- Graceful terminate: SIGTERM → 5s timeout → SIGKILL
- Health check mỗi 30 giây (process isAlive)

### MCP Protocol Client (JSON-RPC 2.0)

- Giao tiếp qua stdio (stdin/stdout pipes)
- Initialize handshake: protocolVersion "2024-11-05", clientInfo, capabilities
- Initialize timeout: 10 giây
- RequestId counter tăng dần, response match qua id
- Error handling: JSON-RPC error codes (-32700 đến -32603)
- Reader coroutine đọc stdout, dispatch qua CompletableDeferred map

### Tool Discovery & Execution

- `tools/list` sau initialize → cache in-memory
- `GET /api/integrations/mcp/{id}/tools`, `GET /api/integrations/mcp/tools` (aggregated)
- Tool card: expandable section, icon 🔧, name, description, "View Schema" modal
- `POST /api/integrations/mcp/tools/call`: route tool call, timeout 60s (504 Gateway Timeout)
- AI Chat integration: inject tools vào system prompt, agentic loop max 5 rounds

### Internal MCP Server

- Auto-register `jira-assistant-ui` (type internal), luôn RUNNING
- In-process tool execution qua service layer (không qua stdio)
- 32+ tools: Navigation (3), Scan Control (6), Ticket Analysis (3), Chat (3), Settings (3), User Management (3), Integration Management (4), Knowledge Graph (2), Dashboard & Project (3), Diagram (2)
- Diagram tools: `create_drawio_diagram` (tạo file .drawio từ mxGraphModel XML, validate XML structure, lưu vào `diagrams/`), `list_drawio_diagrams` (liệt kê files .drawio)
- RBAC enforcement per tool, inputSchema JSON Schema đầy đủ
- Badge "LOCAL", ẩn CONFIGURE/REMOVE/TEST buttons
- Error handling: business errors (isError: true), system errors (-32603), argument validation (-32602)

### MCP Readiness Check

- `GET /api/mcp/health`: readiness status tất cả active servers (< 5 giây)
- Tool Ping: gọi `tools/list` với timeout 3 giây per server (concurrent)
- Internal server luôn ready, external check process + client + ping
- Frontend: check trước document generation, BlockingOverlay "Checking MCP tools..."
- Readiness Dialog: list servers (✅/⚠️), warning message, "Tiếp tục"/"Hủy"
- Categorize servers theo role: knowledge_base, database, markitdown, jira_internal

### Logging & Diagnostics

- Structured log (SLF4J): timestamp, serverId, method, requestId, duration
- `tools/call` log ở INFO level: `Agent {serverId} call {toolName}: OK/FAILED in Xms`
- `GET /api/integrations/mcp/{id}/logs`: 100 dòng log gần nhất (Administrator only)
- `GET /api/integrations/mcp/{id}/status`: status, pid, uptime, toolCount, lastError, restartCount

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `jira-modal-consistency` | Fix Jira modal: tách "SAVE & TEST" thành 2 nút riêng, dark theme cho input fields, SAVE disabled đến khi test pass (modal đã bị loại bỏ hoàn toàn trong spec `remove-jira-cloud-badge`) |
| `mcp-tool-prompt-injection` | Fix BA agent prompt chỉ có 2 KB tools — query ToolRegistry.listTools() + McpProcessManager.getActiveTools() để inject tất cả MCP tools |
| `real-mcp-integration-test` | Thay FakeSubprocessProxy + IntegrationNoOpToolRegistry bằng real ToolRegistryImpl + AgentMcpManager với MCP configs từ test.properties |
