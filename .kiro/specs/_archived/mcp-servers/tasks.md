# MCP Servers — Tasks

Status: ✅ All completed

# MCP Server Registration — Tasks 100–107

Đăng ký và quản lý MCP servers trên trang Integrations.

## Task 100: Backend — SQLDelight Schema cho MCP Servers
- [x] 100.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `mcp_servers`
    - _Requirements: AC 6.25_
- [x] 100.2 Tạo `McpServerConfig` data class + `McpServerRepository` interface + impl
    - _Requirements: AC 6.25, AC 6.26_

## Task 101: Backend — MCP Server API Routes
- [x] 101.1 Tạo `McpRoutes.kt`: CRUD endpoints (GET/POST/PUT/DELETE `/api/integrations/mcp`)
    - _Requirements: AC 6.26_
- [x] 101.2 Thêm `POST /api/integrations/mcp/{id}/test`: chạy command, kiểm tra output
    - _Requirements: AC 6.26, AC 6.28_
- [x] 101.3 Thêm `GET /export` và `POST /import`: JSON config import/export
    - _Requirements: AC 6.27_
- [x] 101.4 RBAC: Administrator cho write operations, Reader+ cho read
    - _Requirements: AC 6.30_

## Task 102: Frontend — MCP Server Section trên Integrations
- [x] 102.1 Cập nhật `integrations.html`: thêm MCP Servers section + "Add MCP Server" button
    - _Requirements: AC 6.20, AC 6.21_
- [x] 102.2 Tạo `integrations/McpServerCards.kt`: render MCP server cards, status dots, actions
    - _Requirements: AC 6.21, AC 6.29_

## Task 103: Frontend — MCP Config Modal (Form + JSON)
- [x] 103.1 Tạo `templates/mcp-config-modal.html`: form fields + JSON textarea + toggle
    - _Requirements: AC 6.22, AC 6.23_
- [x] 103.2 Tạo `integrations/McpConfigModal.kt`: form/JSON toggle, save, test, validate
    - _Requirements: AC 6.22, AC 6.23, AC 6.24_

## Task 104: Frontend — MCP Import/Export
- [x] 104.1 Thêm nút 📥 Import + 📤 Export trên MCP section header
    - _Requirements: AC 6.27_
- [x] 104.2 Import: file picker → POST /api/integrations/mcp/import → refresh list
    - _Requirements: AC 6.27_
- [x] 104.3 Export: GET /api/integrations/mcp/export → download mcp.json
    - _Requirements: AC 6.27_

## Task 105: Backend — MCP Server Process Management
- [x] 105.1 Tạo `McpServerManager.kt`: start/stop MCP server processes, health check
    - _Requirements: AC 6.28_
- [x] 105.2 Tạo `McpToolDiscovery.kt`: discover available tools từ running MCP server
    - _Requirements: AC 6.29_

## Task 106: Frontend — MCP Tools Viewer
- [x] 106.1 Hiển thị danh sách tools available từ mỗi MCP server (expandable section trong card)
    - _Requirements: AC 6.29_

## Task 107: Checkpoint — MCP Server Registration
- [x] 107. CRUD, Form/JSON config, import/export, process management, tools discovery, RBAC

---

# MCP Runtime Integration — Tasks 108–125

Triển khai MCP Runtime: process management, JSON-RPC 2.0 protocol, tool discovery/execution, AI Chat agentic loop, health monitoring.

## Tổng quan

Phần 1 (Registration — Tasks 100–107) đã hoàn thành: CRUD, Form/JSON config, import/export. Phần 2 này triển khai runtime thực sự: spawn processes, giao tiếp JSON-RPC 2.0 over stdio, discover/execute tools, tích hợp AI Chat, health monitoring.

## Tasks

- [x] 108. Data models cho MCP Runtime
  - [x] 108.1 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/models/McpProcessStatus.kt`: data class `McpProcessStatus` (configId, pid, state, uptime, toolCount, lastError, restartCount) + enum `McpServerState` (STOPPED, STARTING, RUNNING, ERROR, OFFLINE)
    - _Requirements: AC 6.32, AC 6.57_
  - [x] 108.2 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/models/McpToolInfo.kt`: data class `McpToolInfo` (name, description, inputSchema: JsonElement) + `McpAggregatedTool` (serverId, serverName, name, description, inputSchema)
    - _Requirements: AC 6.44, AC 6.46_
  - [x] 108.3 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/models/McpToolCall.kt`: data classes `McpToolCallRequest` (serverId, toolName, arguments, approved) + `McpToolCallResponse` (content, isError, requiresApproval, toolName, arguments) + `McpContent` (type, text, data, mimeType)
    - _Requirements: AC 6.48, AC 6.50_
  - [x] 108.4 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/models/JsonRpc.kt`: data classes `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError`, `McpInitializeResult`, `McpServerInfo` + `McpError` exception class
    - _Requirements: AC 6.38, AC 6.39, AC 6.42_

- [x] 109. McpProcessManager interface + McpProtocolClient interface
  - [x] 109.1 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/McpProcessManager.kt`: interface với startServer, stopServer, restartServer, getRunningServers, getStatus, startAllEnabled, stopAll, getActiveTools, getClient
    - _Requirements: AC 6.31, AC 6.32, AC 6.33, AC 6.37_
  - [x] 109.2 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/McpProtocolClient.kt`: interface với initialize, sendRequest, sendNotification, listTools, callTool, close
    - _Requirements: AC 6.38, AC 6.39, AC 6.44, AC 6.48_

- [x] 110. McpProtocolClientImpl — JSON-RPC 2.0 over stdio
  - [x] 110.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/McpProtocolClientImpl.kt`: constructor nhận stdin (OutputStream), stdout (BufferedReader), scope (CoroutineScope). Triển khai requestId counter (AtomicInteger), pending map (ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>), reader coroutine đọc stdout line-by-line và dispatch responses
    - _Requirements: AC 6.38, AC 6.41, AC 6.43_
  - [x] 110.2 Triển khai `initialize()`: gửi initialize request (protocolVersion "2024-11-05", clientInfo), chờ response với timeout 10s, gửi notifications/initialized notification
    - _Requirements: AC 6.39, AC 6.40_
  - [x] 110.3 Triển khai `sendRequest()` với timeout handling (withTimeout), `sendNotification()`, error response parsing (JsonRpcError → McpError)
    - _Requirements: AC 6.41, AC 6.42, AC 6.49_
  - [x] 110.4 Triển khai `listTools()` với in-memory cache, `callTool()` với 60s timeout
    - _Requirements: AC 6.44, AC 6.48, AC 6.49_

  - [x] 110.5 Write property test: JSON-RPC request/response ID matching
    - **Property 2: JSON-RPC request/response ID matching**
    - **Validates: Requirements AC 6.41, AC 6.43**

  - [x] 110.6 Write property test: Timeout enforcement
    - **Property 4: Timeout enforcement**
    - **Validates: Requirements AC 6.40, AC 6.49**

- [x] 111. McpProcessManagerImpl — Process lifecycle
  - [x] 111.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/McpProcessManagerImpl.kt`: constructor nhận McpServerRepository, CoroutineScope. Sử dụng ConcurrentHashMap<String, ManagedProcess> lưu processes. Triển khai `startServer()`: đọc config từ repo, spawn process via ProcessBuilder (command, args, env), tạo McpProtocolClientImpl, gọi initialize + listTools, lưu ManagedProcess
    - _Requirements: AC 6.31, AC 6.32_
  - [x] 111.2 Triển khai `stopServer()`: graceful shutdown (process.destroy() → waitFor 5s → destroyForcibly()), cancel reader/health jobs, remove from map, update status DB
    - _Requirements: AC 6.36_
  - [x] 111.3 Triển khai `startAllEnabled()`: đọc tất cả configs có disabled=false, launch coroutine cho mỗi server với timeout 30s. Triển khai `stopAll()` cho shutdown hook
    - _Requirements: AC 6.33_
  - [x] 111.4 Triển khai auto-restart: detect crash qua health check hoặc reader exit, retry ≤3 lần với exponential backoff (2s, 4s, 8s), status → OFFLINE sau max retries
    - _Requirements: AC 6.35_

  - [x] 111.5 Write property test: Process lifecycle state machine validity
    - **Property 1: Process lifecycle state machine validity**
    - **Validates: Requirements AC 6.31, AC 6.32**

  - [x] 111.6 Write property test: Auto-restart bounded retries
    - **Property 3: Auto-restart bounded retries**
    - **Validates: Requirements AC 6.35**

- [x] 112. Checkpoint — MCP Process Management core
  - Ensure all tests pass, ask the user if questions arise.

- [x] 113. Koin registration + Application hooks
  - [x] 113.1 Cập nhật `ServerModule.kt`: đăng ký `McpProcessManager` singleton (McpProcessManagerImpl với McpServerRepository + CoroutineScope)
    - _Requirements: AC 6.32_
  - [x] 113.2 Cập nhật `Application.kt`: thêm auto-start hook (launch { processManager.startAllEnabled() }) và shutdown hook (monitor.subscribe(ApplicationStopped) { runBlocking { processManager.stopAll() } })
    - _Requirements: AC 6.33, AC 6.36_

- [x] 114. McpRuntimeRoutes — API endpoints
  - [x] 114.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/routes/McpRuntimeRoutes.kt`: POST /{id}/start, POST /{id}/stop (Administrator only)
    - _Requirements: AC 6.37_
  - [x] 114.2 Thêm GET /{id}/tools (Reader+), GET /tools aggregated (Reader+)
    - _Requirements: AC 6.45, AC 6.46_
  - [x] 114.3 Thêm POST /tools/call với autoApprove check: nếu toolName ∈ autoApprove → execute ngay, nếu không → trả về {requiresApproval: true}. Hỗ trợ approved=true để execute sau approval
    - _Requirements: AC 6.48, AC 6.50_
  - [x] 114.4 Thêm GET /{id}/status (Reader+), GET /{id}/logs (Administrator only, 100 dòng gần nhất)
    - _Requirements: AC 6.57, AC 6.61_

  - [x] 114.5 Write property test: AutoApprove routing correctness
    - **Property 5: AutoApprove routing correctness**
    - **Validates: Requirements AC 6.50**

- [x] 115. Cập nhật test endpoint + delete endpoint
  - [x] 115.1 Cập nhật `McpRoutes.kt` POST /{id}/test: thay vì chỉ mark ACTIVE, thực sự spawn process → initialize handshake → tools/list → stop process → trả về tools hoặc error chi tiết
    - _Requirements: AC 6.34_
  - [x] 115.2 Cập nhật `McpRoutes.kt` DELETE /{id}: gọi processManager.stopServer() trước khi xóa config
    - _Requirements: AC 6.36_
  - [x] 115.3 Đăng ký `mcpRuntimeRoutes()` trong Application routing setup
    - _Requirements: AC 6.37_

- [x] 116. Checkpoint — MCP Runtime API
  - Ensure all tests pass, ask the user if questions arise.

- [x] 117. Health monitoring
  - [x] 117.1 Triển khai health check coroutine trong McpProcessManagerImpl: mỗi 30s kiểm tra process.isAlive(), nếu dead → status ERROR → trigger auto-restart
    - _Requirements: AC 6.56_
  - [x] 117.2 Triển khai structured logging (SLF4J): log JSON-RPC messages (timestamp, serverId, method, requestId, duration, error). DEBUG cho success, WARN cho errors
    - _Requirements: AC 6.60_
  - [x] 117.3 Triển khai in-memory log buffer (CircularBuffer 100 entries) cho GET /{id}/logs endpoint
    - _Requirements: AC 6.61_

- [x] 118. ChatService — MCP tools injection + agentic loop
  - [x] 118.1 Cập nhật `ChatServiceImpl.kt`: inject McpProcessManager dependency. Trong buildFullPrompt(), gọi processManager.getActiveTools() và append MCP tools vào system prompt dạng `[MCP:{serverName}] {toolName}: {description}`
    - _Requirements: AC 6.52_
  - [x] 118.2 Triển khai parseMcpToolCall(): parse AI response tìm `{"mcpToolCall": {...}}`, extract serverId/toolName/arguments
    - _Requirements: AC 6.53_
  - [x] 118.3 Triển khai agentic loop trong processChat(): sau khi nhận AI response, check mcpToolCall → execute via processManager.getClient() → callTool() → append result vào prompt → gọi AI lại. Max 5 rounds. Graceful degradation khi tool fail
    - _Requirements: AC 6.53, AC 6.55_

  - [x] 118.4 Write property test: Agentic loop termination and graceful degradation
    - **Property 6: Agentic loop termination and graceful degradation**
    - **Validates: Requirements AC 6.53, AC 6.55**

- [x] 119. Checkpoint — AI Chat MCP Integration
  - Ensure all tests pass, ask the user if questions arise.

- [x] 120. Frontend — MCP server status real-time
  - [x] 120.1 Cập nhật `McpServerCards.kt`: thêm polling GET /api/integrations/mcp/{id}/status mỗi 10s, cập nhật status dot (RUNNING=xanh, STARTING=vàng, ERROR=đỏ, OFFLINE=xám), hiển thị uptime và toolCount
    - _Requirements: AC 6.57, AC 6.58_
  - [x] 120.2 Triển khai state transition toasts: khi status thay đổi giữa 2 lần poll, hiển thị toast notification (server name, old→new state, error message nếu có)
    - _Requirements: AC 6.59_

- [x] 121. Frontend — MCP tools expandable section
  - [x] 121.1 Cập nhật MCP server card: thêm expandable "Tools" section, gọi GET /api/integrations/mcp/{id}/tools khi expand, hiển thị 🔧 tool name + description (truncated 100 chars) + nút "View Schema"
    - _Requirements: AC 6.47_
  - [x] 121.2 Triển khai "View Schema" modal: hiển thị inputSchema dạng formatted JSON (pre + code block)
    - _Requirements: AC 6.47_

- [x] 122. Frontend — Tool execution + confirmation dialog
  - [x] 122.1 Triển khai confirmation dialog khi tool call trả về requiresApproval=true: hiển thị tool name, server name, arguments (formatted JSON), nút Approve/Deny. Approve → gửi lại request với approved=true
    - _Requirements: AC 6.51_
  - [x] 122.2 Triển khai tool execution indicator trong AI Chat: khi tool đang chạy hiển thị "🔧 Đang gọi {toolName}...", khi hoàn tất hiển thị kết quả trong collapsible block
    - _Requirements: AC 6.54_

- [x] 123. Frontend — Start/Stop controls
  - [x] 123.1 Thêm nút Start/Stop trên MCP server card (Administrator only), gọi POST /{id}/start hoặc POST /{id}/stop, cập nhật UI sau response
    - _Requirements: AC 6.37_

- [x] 124. Checkpoint — Frontend MCP Runtime
  - Ensure all tests pass, ask the user if questions arise.

- [x] 125. Final checkpoint — MCP Runtime Integration
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: process spawn, initialize handshake, tool discovery, tool execution, AI Chat agentic loop, health monitoring, auto-restart, graceful shutdown, frontend status/tools/confirmation UI

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Mỗi task tham chiếu requirements cụ thể (AC 6.31–AC 6.61) để truy vết
- Checkpoints đảm bảo kiểm tra tăng dần sau mỗi nhóm logic
- Property tests validate correctness properties từ design document
- Code tuân thủ: max 200 dòng/file, max 20 dòng/hàm, models tách package `models/`


---

# Internal MCP Server — Tasks 126–145

Triển khai Internal MCP Server: tool registry, tool executor, RBAC enforcement, argument validation, tích hợp với McpProcessManager và ChatService.

## Tổng quan

Phần 1 (Registration — Tasks 100–107) và Phần 2 (Runtime — Tasks 108–125) đã hoàn thành. Phần 3 này triển khai Internal MCP Server: expose toàn bộ chức năng ứng dụng dưới dạng MCP tools, chạy in-process, gọi trực tiếp service layers.

## Tasks

- [x] 126. Data models cho Internal MCP Server
  - [x] 126.1 Tạo `shared/src/commonMain/kotlin/com/assistant/mcp/models/InternalToolDefinition.kt`: data class `InternalToolDefinition` (name, description, inputSchema: JsonElement, requiredPermission, requiredRole, category) + enum `ToolCategory` (NAVIGATION, SCAN, ANALYSIS, CHAT, SETTINGS, USER_MANAGEMENT, INTEGRATIONS, KNOWLEDGE_GRAPH, DASHBOARD)
    - _Requirements: AC 6.108_
  - [x] 126.2 Cập nhật `KnowledgeBase.sq`: thêm cột `internal INTEGER NOT NULL DEFAULT 0` vào bảng `mcp_servers`. Cập nhật `McpServerConfig` data class thêm field `internal: Boolean = false`
    - _Requirements: AC 6.72_
  - [x] 126.3 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/UserContext.kt`: data class `UserContext` (userId, userRole, email?)
    - _Requirements: AC 6.104_

- [x] 127. InternalToolRegistry — Đăng ký tool definitions
  - [x] 127.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/InternalToolRegistry.kt`: class với `getAllTools()`, `getTool(name)`. Init đăng ký Navigation tools (navigate_to_page, get_current_page, list_available_pages) với inputSchema JSON Schema đầy đủ
    - _Requirements: AC 6.74, AC 6.75, AC 6.76, AC 6.108_
  - [x] 127.2 Đăng ký Scan tools (start_scan, pause_scan, resume_scan, cancel_scan, get_scan_status, get_scan_log) với inputSchema
    - _Requirements: AC 6.77–6.82, AC 6.108_
  - [x] 127.3 Đăng ký Analysis tools (analyze_ticket, get_ticket_analysis, list_analyzed_tickets) với inputSchema
    - _Requirements: AC 6.83–6.85, AC 6.108_
  - [x] 127.4 Đăng ký Chat tools (send_chat_message, get_chat_history, list_conversations) với inputSchema
    - _Requirements: AC 6.86–6.88, AC 6.108_
  - [x] 127.5 Đăng ký Settings + User Management tools (get_settings, update_setting, get_setting, list_users, update_user_role, get_user_permissions) với inputSchema
    - _Requirements: AC 6.89–6.94, AC 6.108_
  - [x] 127.6 Đăng ký Integration + KnowledgeGraph + Dashboard tools (list_ai_providers, test_ai_provider, list_mcp_servers, manage_mcp_server, get_graph_data, search_graph_nodes, get_dashboard_metrics, list_projects, get_project_analysis_summary) với inputSchema
    - _Requirements: AC 6.95–6.103, AC 6.108_

  - [x] 127.7 Write property test: Tool definitions completeness
    - **Property 9: Tool definitions completeness**
    - **Validates: Requirements AC 6.105, AC 6.108**

- [x] 128. InternalMcpToolExecutor — Core executor
  - [x] 128.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/InternalMcpToolExecutor.kt`: class nhận tất cả service dependencies (BatchScanEngine, AIOrchestrator, ChatService, SettingsRepository, AuthService, RBACEngine, ProviderConfigRepository, McpProcessManager, etc.). Triển khai `execute(toolName, arguments, userId, userRole)` với flow: validate → RBAC check → dispatch → wrap response
    - _Requirements: AC 6.104, AC 6.112_
  - [x] 128.2 Triển khai `validateArguments(toolDef, arguments)`: kiểm tra required fields, type checking, enum validation. Trả về McpError(-32602) nếu invalid
    - _Requirements: AC 6.112_
  - [x] 128.3 Triển khai RBAC check: lookup requiredPermission từ tool definition, kiểm tra qua RBACEngine. Trả về McpError(-32603, "Access denied: requires {permission}") nếu không đủ quyền
    - _Requirements: AC 6.104, AC 6.106_

  - [x] 128.4 Write property test: RBAC enforcement cho mọi tool call
    - **Property 8: RBAC enforcement cho mọi tool call**
    - **Validates: Requirements AC 6.104, AC 6.106**

  - [x] 128.5 Write property test: Argument validation
    - **Property 12: Argument validation**
    - **Validates: Requirements AC 6.112**

- [x] 129. Tool handlers — Navigation
  - [x] 129.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/NavigationHandlers.kt`: triển khai `handleNavigateToPage(page)` trả về page URL + metadata (title, requiredPermission), `handleGetCurrentPage()` trả về last known page từ session, `handleListAvailablePages(userRole)` trả về RBAC-filtered page list
    - _Requirements: AC 6.74, AC 6.75, AC 6.76_

  - [x] 129.2 Write property test: RBAC-filtered page listing
    - **Property 10: RBAC-filtered page listing**
    - **Validates: Requirements AC 6.76**

- [x] 130. Tool handlers — Scan Control
  - [x] 130.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/ScanHandlers.kt`: triển khai handlers cho start_scan, pause_scan, resume_scan, cancel_scan (delegate tới BatchScanEngine), get_scan_status, get_scan_log. Wrap ScanConflictException thành isError response
    - _Requirements: AC 6.77–6.82, AC 6.110_

- [x] 131. Tool handlers — Analysis
  - [x] 131.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/AnalysisHandlers.kt`: triển khai handlers cho analyze_ticket (delegate tới AIOrchestrator.analyzeTicket), get_ticket_analysis (lookup từ KB), list_analyzed_tickets. Wrap exceptions thành isError response
    - _Requirements: AC 6.83–6.85, AC 6.110_

- [x] 132. Tool handlers — Chat
  - [x] 132.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/ChatHandlers.kt`: triển khai handlers cho send_chat_message (delegate tới ChatService.processChat với UserContext), get_chat_history, list_conversations
    - _Requirements: AC 6.86–6.88_

- [x] 133. Tool handlers — Settings & User Management
  - [x] 133.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/SettingsHandlers.kt`: triển khai handlers cho get_settings, update_setting, get_setting (delegate tới SettingsRepository)
    - _Requirements: AC 6.89–6.91_
  - [x] 133.2 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/UserManagementHandlers.kt`: triển khai handlers cho list_users, update_user_role, get_user_permissions (delegate tới AuthService + RBACEngine)
    - _Requirements: AC 6.92–6.94_

- [x] 134. Tool handlers — Integrations & KnowledgeGraph & Dashboard
  - [x] 134.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/IntegrationHandlers.kt`: triển khai handlers cho list_ai_providers, test_ai_provider, list_mcp_servers, manage_mcp_server. manage_mcp_server SHALL reject actions trên internal server (serverId = "jira-assistant-ui")
    - _Requirements: AC 6.95–6.98, AC 6.110_
  - [x] 134.2 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/KnowledgeGraphHandlers.kt`: triển khai handlers cho get_graph_data, search_graph_nodes
    - _Requirements: AC 6.99–6.100_
  - [x] 134.3 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/handlers/DashboardHandlers.kt`: triển khai handlers cho get_dashboard_metrics, list_projects, get_project_analysis_summary
    - _Requirements: AC 6.101–6.103_

  - [x] 134.4 Write property test: Business error handling
    - **Property 11: Business error handling**
    - **Validates: Requirements AC 6.110**

- [x] 135. Checkpoint — Tool Handlers
  - Ensure all tool handlers compile, ask the user if questions arise.

- [x] 136. InternalMcpBridge — Tích hợp với hệ thống MCP
  - [x] 136.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/InternalMcpBridge.kt`: class với `ensureRegistered()` (tạo/update record trong DB với internal=true, status=RUNNING), `isInternalServer(serverId)`, `getAggregatedTools()` (convert InternalToolDefinition → McpAggregatedTool), `callTool()` (delegate tới executor), `getStatus()` (luôn trả về RUNNING)
    - _Requirements: AC 6.70, AC 6.71, AC 6.107_
  - [x] 136.2 Cập nhật `McpServerRepository`: thêm method `findByName(name)` case-insensitive, `isInternal(id)`. Cập nhật `findAll()` để include internal servers
    - _Requirements: AC 6.70, AC 6.72_

  - [x] 136.3 Write property test: Tool aggregation includes internal tools
    - **Property 7: Tool aggregation includes internal tools**
    - **Validates: Requirements AC 6.71, AC 6.107**

- [x] 137. Koin registration + Startup hooks
  - [x] 137.1 Cập nhật `ServerModule.kt`: đăng ký `InternalToolRegistry` (singleton), `InternalMcpToolExecutor` (singleton, inject tất cả services), `InternalMcpBridge` (singleton)
    - _Requirements: AC 6.70_
  - [x] 137.2 Cập nhật `Application.kt`: thêm startup hook gọi `internalMcpBridge.ensureRegistered()` trước `processManager.startAllEnabled()`
    - _Requirements: AC 6.70_

- [x] 138. Cập nhật McpToolsHandlers — Routing internal vs external
  - [x] 138.1 Cập nhật `McpToolsHandlers.kt` `handleToolCall()`: kiểm tra `internalBridge.isInternalServer(serverId)`, nếu true → delegate tới `internalBridge.callTool()` với userId/role từ JWT. Nếu false → existing external logic
    - _Requirements: AC 6.71_
  - [x] 138.2 Cập nhật `McpToolsHandlers.kt` `handleAggregatedTools()`: merge `internalBridge.getAggregatedTools()` + `processManager.getActiveTools()`, internal tools đầu tiên
    - _Requirements: AC 6.107_
  - [x] 138.3 Cập nhật `McpRoutes.kt` hoặc `McpRuntimeHandlers.kt`: protect internal server khỏi DELETE, STOP, DISABLE operations. Trả về 400 "Cannot modify Internal MCP Server"
    - _Requirements: AC 6.70_

- [x] 139. Cập nhật ChatService — System prompt injection
  - [x] 139.1 Cập nhật `ChatServiceImpl.kt` `buildFullPrompt()`: inject InternalMcpBridge dependency, lấy internal tools trước, external tools sau. Internal tools dùng prefix `[Internal]`, external tools dùng `[MCP:{serverName}]`
    - _Requirements: AC 6.109_

- [x] 140. Checkpoint — Internal MCP Server Backend
  - Ensure all tests pass, verify: internal server auto-register, tool discovery, tool execution, RBAC enforcement, argument validation, error handling, aggregated tools, ChatService injection.

- [x] 141. Frontend — Internal MCP Server card
  - [x] 141.1 Cập nhật `McpServerCards.kt`: kiểm tra `server.internal` flag, nếu true → hiển thị badge "Built-in" (span với class `mcp-badge-builtin`), ẩn nút CONFIGURE/REMOVE, ẩn nút STOP. Giữ nút TEST và expandable tools section
    - _Requirements: AC 6.72_
  - [x] 141.2 Cập nhật `integrations.css`: thêm style cho `.mcp-badge-builtin` (background: var(--accent), border-radius: 4px, font-size: 10px, padding: 2px 6px)
    - _Requirements: AC 6.72_

- [x] 142. Frontend — Internal tools trong AI Chat
  - [x] 142.1 Cập nhật AI Chat tool execution indicator: khi tool từ internal server (serverId = "jira-assistant-ui"), hiển thị icon 🏠 thay vì 🔧, label "Đang thực hiện {toolName}..."
    - _Requirements: AC 6.109_

- [x] 143. Checkpoint — Frontend Internal MCP
  - Ensure frontend displays internal server correctly with Built-in badge, tools expandable, no CONFIGURE/REMOVE/STOP buttons.

- [x] 144. Cập nhật McpServerInfo model
  - [x] 144.1 Cập nhật `shared/src/commonMain/kotlin/com/assistant/mcp/models/` hoặc frontend model `McpServerInfo`: thêm field `internal: Boolean = false` để frontend biết server nào là internal
    - _Requirements: AC 6.72_

- [x] 145. Final checkpoint — Internal MCP Server
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: auto-register on startup, 30 tools registered, tool execution via /tools/call, RBAC enforcement, argument validation, business error handling, system error handling, aggregated tools list, ChatService [Internal] prefix, frontend Built-in badge, immutable (no delete/stop/disable)

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Mỗi task tham chiếu requirements cụ thể (AC 6.70–AC 6.112) để truy vết
- Checkpoints đảm bảo kiểm tra tăng dần sau mỗi nhóm logic
- Property tests validate correctness properties từ design document (P7–P12)
- Code tuân thủ: max 200 dòng/file, max 20 dòng/hàm, models tách package `models/`
- Handler files tách theo category (NavigationHandlers, ScanHandlers, etc.) để giữ <200 dòng/file
