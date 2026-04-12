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

  - [x]* 110.5 Write property test: JSON-RPC request/response ID matching
    - **Property 2: JSON-RPC request/response ID matching**
    - **Validates: Requirements AC 6.41, AC 6.43**

  - [x]* 110.6 Write property test: Timeout enforcement
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

  - [x]* 111.5 Write property test: Process lifecycle state machine validity
    - **Property 1: Process lifecycle state machine validity**
    - **Validates: Requirements AC 6.31, AC 6.32**

  - [x]* 111.6 Write property test: Auto-restart bounded retries
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

  - [x]* 114.5 Write property test: AutoApprove routing correctness
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

  - [x]* 118.4 Write property test: Agentic loop termination and graceful degradation
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
