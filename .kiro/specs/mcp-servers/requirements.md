# MCP Servers — Requirements

### MCP Server Registration

**6.20** THE Frontend_App SHALL hiển thị section "MCP Servers" trên trang Integrations, bên dưới danh sách AI providers, cho phép đăng ký và quản lý Model Context Protocol (MCP) servers.

**6.21** THE Frontend_App SHALL hiển thị danh sách MCP servers đã đăng ký dưới dạng cards (tương tự provider cards), mỗi card hiển thị: server name, command, status dot (ACTIVE/OFFLINE), và nút CONFIGURE/REMOVE.

**6.22** THE Frontend_App SHALL có nút "➕ Add MCP Server" mở form đăng ký MCP server mới với các fields:
- Server Name (text, required)
- Command (text, required — ví dụ: "uvx", "npx", "node")
- Args (text, comma-separated — ví dụ: "awslabs.aws-documentation-mcp-server@latest")
- Environment Variables (key=value pairs, mỗi dòng 1 cặp)
- Auto Approve Tools (text, comma-separated — danh sách tool names tự động approve)
- Disabled (checkbox — enable/disable server)

**6.23** THE Frontend_App SHALL hỗ trợ 2 chế độ cấu hình MCP server:
- **Form mode**: Nhập qua form UI (fields ở 6.22)
- **JSON mode**: Nhập/chỉnh sửa trực tiếp JSON config (textarea với syntax highlighting) theo format `mcp.json`

**6.24** THE Frontend_App SHALL có nút toggle "Form / JSON" để chuyển đổi giữa 2 chế độ. Khi chuyển từ Form → JSON, auto-generate JSON từ form values. Khi chuyển từ JSON → Form, parse JSON và điền vào form fields.

**6.25** THE Backend_Server SHALL lưu MCP server configs trong bảng `mcp_servers` (SQLDelight): id, name, command, args (JSON array), env (JSON object), autoApprove (JSON array), disabled (boolean), created_at, updated_at.

**6.26** THE Backend_Server SHALL cung cấp endpoints:
- `GET /api/integrations/mcp` — danh sách MCP servers (merge runtime status từ McpProcessManager vào DB configs, filter bỏ internal marker records)
- `POST /api/integrations/mcp` — đăng ký MCP server mới (reject 409 Conflict nếu name hoặc ID đã tồn tại, case-insensitive)
- `PUT /api/integrations/mcp/{id}` — cập nhật config
- `DELETE /api/integrations/mcp/{id}` — xóa MCP server (nếu là markitdown, tạo suppression marker ngăn auto-recreate)
- `POST /api/integrations/mcp/{id}/test` — test connection (chạy command, kiểm tra output)

**6.27** THE Backend_Server SHALL hỗ trợ import/export MCP config từ/sang file `mcp.json` format (tương thích với Kiro/VS Code MCP config format):
- `GET /api/integrations/mcp/export` — export toàn bộ config dạng JSON
- `POST /api/integrations/mcp/import` — import từ JSON file, skip entries trùng name (case-insensitive) thay vì tạo duplicate

**6.28** WHEN MCP server được đăng ký, THE Backend_Server SHALL tự động khởi động MCP server process (nếu command hợp lệ) và giữ connection. Status cập nhật real-time trên UI.

**6.29** THE Frontend_App SHALL hiển thị danh sách tools available từ mỗi MCP server (sau khi connect thành công), cho phép user xem tool name, description, và input schema.

**6.30** RBAC: Chỉ Administrator mới có quyền thêm/sửa/xóa MCP servers. Reader và Neural_Architect chỉ xem danh sách và status.

### MCP Duplicate Prevention & Auto-Config

**6.30a** THE Backend_Server SHALL ngăn chặn tạo MCP server trùng name (case-insensitive). `POST /api/integrations/mcp` trả về 409 Conflict nếu name đã tồn tại. Import skip entries trùng name.

**6.30b** THE Backend_Server SHALL auto-configure markitdown MCP server khi startup nếu chưa có (check by name case-insensitive, không chỉ by ID). Nếu user đã xóa markitdown, tạo suppression marker (`markitdown_auto_suppressed`) ngăn auto-recreate.

**6.30c** THE Backend_Server SHALL merge runtime status từ `McpProcessManager` vào response của `GET /api/integrations/mcp`. Nếu process đang RUNNING nhưng DB ghi OFFLINE, response trả về RUNNING. Đảm bảo frontend hiển thị đúng trạng thái khi navigate giữa các trang.

**6.30d** THE Backend_Server SHALL tìm provider configs theo `ProviderType` (JIRA, OLLAMA, GEMINI, LM_STUDIO) thay vì hardcode provider ID trong routes. `ProviderConfigRepository` cung cấp `findByType()` và `existsByType()` methods.


### MCP Runtime Integration — Process Management & Protocol

**User Story:** Là một Administrator, tôi muốn hệ thống thực sự khởi động MCP server processes, giao tiếp qua MCP protocol (JSON-RPC 2.0 over stdio), khám phá tools, và cho phép AI Chat sử dụng MCP tools — để mở rộng khả năng của trợ lý AI với các công cụ bên ngoài.

#### Process Management

**6.31** WHEN một MCP server config có `disabled = false`, THE Backend_Server SHALL khởi động process tương ứng bằng `ProcessBuilder` với command, args, và environment variables từ config. Process SHALL sử dụng stdio transport (stdin/stdout pipes) để giao tiếp.

**6.32** THE Backend_Server SHALL quản lý lifecycle của MCP server processes thông qua `McpProcessManager` (interface) với các operations: `startServer(configId)`, `stopServer(configId)`, `restartServer(configId)`, `getRunningServers()`. Mỗi operation trả về `McpProcessStatus` chứa configId, processId (PID), state (STARTING/RUNNING/STOPPED/ERROR), và error message nếu có.

**6.33** WHEN Backend_Server khởi động (application startup), THE Backend_Server SHALL tự động khởi động tất cả MCP servers có `disabled = false` trong database. Quá trình khởi động SHALL chạy song song (coroutines) với timeout 30 giây cho mỗi server.

**6.34** WHEN Administrator gọi `POST /api/integrations/mcp/{id}/test`, THE Backend_Server SHALL thực sự khởi động MCP server process, thực hiện MCP initialize handshake, gọi `tools/list`, và trả về danh sách tools nếu thành công. Nếu thất bại, trả về error message chi tiết (process exit code, stderr output).

**6.35** WHEN một MCP server process kết thúc bất ngờ (crash, exit code khác 0), THE Backend_Server SHALL cập nhật status thành "ERROR" trong database, ghi log lý do crash (stderr output), và thử khởi động lại tối đa 3 lần với exponential backoff (2s, 4s, 8s). Sau 3 lần thất bại, status chuyển thành "OFFLINE".

**6.36** WHEN Administrator gọi `DELETE /api/integrations/mcp/{id}`, THE Backend_Server SHALL dừng process đang chạy (nếu có) trước khi xóa config khỏi database. Process SHALL được terminate gracefully (SIGTERM) với timeout 5 giây, sau đó force kill (SIGKILL) nếu vẫn chạy.

**6.37** THE Backend_Server SHALL cung cấp endpoint `POST /api/integrations/mcp/{id}/start` để khởi động MCP server process thủ công, và `POST /api/integrations/mcp/{id}/stop` để dừng process. Cả hai endpoints yêu cầu vai trò Administrator.

#### MCP Protocol Client (JSON-RPC 2.0 over stdio)

**6.38** THE Backend_Server SHALL triển khai `McpProtocolClient` (interface) giao tiếp với MCP server qua JSON-RPC 2.0 over stdio. Mỗi JSON-RPC message là một dòng JSON kết thúc bằng newline (`\n`) gửi qua stdin, và đọc response từ stdout.

**6.39** WHEN MCP server process được khởi động, THE McpProtocolClient SHALL gửi `initialize` request với `protocolVersion: "2024-11-05"`, `clientInfo: {name: "jira-assistant", version: "1.0.0"}`, và `capabilities: {}`. THE McpProtocolClient SHALL chờ `initialize` response chứa `serverInfo` và `capabilities`, sau đó gửi `initialized` notification.

**6.40** IF MCP server không phản hồi `initialize` request trong vòng 10 giây, THEN THE McpProtocolClient SHALL đóng process, cập nhật status thành "ERROR" với message "Initialize timeout", và ghi log chi tiết.

**6.41** THE McpProtocolClient SHALL duy trì bộ đếm `requestId` (integer, tăng dần) cho mỗi JSON-RPC request. Mỗi request gửi đi có `id` duy nhất, và response được match với request qua `id` field.

**6.42** THE McpProtocolClient SHALL xử lý JSON-RPC error responses (có `error` field thay vì `result`) bằng cách trả về `McpError` chứa error code, message, và data (nếu có). Error codes tuân theo JSON-RPC 2.0 spec: -32700 (Parse error), -32600 (Invalid Request), -32601 (Method not found), -32602 (Invalid params), -32603 (Internal error).

**6.43** THE McpProtocolClient SHALL đọc stdout của MCP server process trên một coroutine riêng biệt (reader coroutine), parse từng dòng JSON, và dispatch response về đúng caller thông qua `CompletableDeferred<JsonElement>` map theo requestId.

#### Tool Discovery

**6.44** WHEN MCP server hoàn tất initialize handshake, THE McpProtocolClient SHALL gửi `tools/list` request và lưu kết quả (danh sách tools) vào bộ nhớ (in-memory cache). Mỗi tool gồm: `name` (String), `description` (String), và `inputSchema` (JSON Schema object).

**6.45** THE Backend_Server SHALL cung cấp endpoint `GET /api/integrations/mcp/{id}/tools` trả về danh sách tools available từ MCP server đang chạy. Response format: `[{name, description, inputSchema}]`. Nếu server không chạy, trả về HTTP 409 Conflict với message "Server not running".

**6.46** THE Backend_Server SHALL cung cấp endpoint `GET /api/integrations/mcp/tools` trả về danh sách tổng hợp tools từ TẤT CẢ MCP servers đang chạy, mỗi tool kèm `serverId` và `serverName` để phân biệt nguồn.

**6.47** THE Frontend_App SHALL hiển thị danh sách tools trên MCP server card (expandable section) sau khi server ở trạng thái ACTIVE. Mỗi tool hiển thị: icon 🔧, tool name, description (truncated 80 chars), và nút "View Schema" mở modal hiển thị inputSchema dạng formatted JSON.

**6.47a** ~~THE Frontend_App SHALL hiển thị checkbox auto-approve bên cạnh mỗi tool trong danh sách. WHEN checkbox được check, tool name được thêm vào `autoApprove` list của server config và lưu qua `PUT /api/integrations/mcp/{id}`. WHEN checkbox được uncheck, tool name bị xóa khỏi `autoApprove` list. Tooltip hiển thị trạng thái: "Auto-approved — click to reject" hoặc "Not approved — click to auto-approve".~~

> ✅ **Đã thay thế bởi spec `per-user-tool-permissions` (Requirement 5)**: Toggle đọc/ghi từ `GET/PUT /api/chat/tool-permissions` per-user thay vì `mcp_servers.auto_approve` global. Tooltip: "Enabled — AI có thể sử dụng tool này" / "Disabled — AI không thể sử dụng tool này".

**6.47b** ~~THE Frontend_App SHALL hiển thị thanh bulk actions phía trên danh sách tools gồm: nút "✅ Approve All" (thêm tất cả tools vào autoApprove), nút "❌ Reject All" (xóa toàn bộ autoApprove), và counter "X / Y approved" hiển thị số tools đã approve trên tổng số.~~

> ✅ **Đã thay thế bởi spec `per-user-tool-permissions` (Requirement 5)**: Bulk actions gọi `PUT /api/chat/tool-permissions/bulk` per-user với `enable_all`/`disable_all`. Counter hiển thị "X / Y enabled".

#### Tool Execution

**6.48** THE Backend_Server SHALL cung cấp endpoint `POST /api/integrations/mcp/tools/call` nhận `{serverId, toolName, arguments}` và route tool call tới MCP server tương ứng qua `tools/call` JSON-RPC request. Response trả về kết quả từ MCP server (content array với type text/image/resource).

**6.49** IF MCP server không phản hồi `tools/call` request trong vòng 60 giây, THEN THE Backend_Server SHALL cancel request, trả về HTTP 504 Gateway Timeout với message "Tool execution timeout", và ghi log chi tiết.

**6.50** ~~IF tool name nằm trong danh sách `autoApprove` của MCP server config, THEN THE Backend_Server SHALL thực thi tool call ngay lập tức mà không cần xác nhận từ người dùng. IF tool name KHÔNG nằm trong `autoApprove`, THEN THE Backend_Server SHALL yêu cầu xác nhận trước khi thực thi (trả về `{requiresApproval: true, toolName, arguments}` để Frontend hiển thị confirmation dialog).~~

> ✅ **Đã thay thế bởi spec `per-user-tool-permissions` (Requirement 1)**: Chuyển sang mô hình enable/disable per-user. Tool enabled = AI dùng được, tool disabled = AI không dùng được (không xuất hiện trong system prompt). Không có confirmation flow. `isToolAutoApproved()` đã bị xóa khỏi `McpAgenticLoop`.

**6.51** THE Frontend_App SHALL hiển thị confirmation dialog khi tool call yêu cầu approval, gồm: tool name, server name, arguments (formatted JSON), và 2 nút "Approve" / "Deny". WHEN người dùng nhấn "Approve", THE Frontend_App SHALL gửi lại request với `{approved: true}`.

#### Tích hợp AI Chat

**6.52** WHEN AI_Chat_Sidebar gửi tin nhắn, THE Backend_Server (ChatService) SHALL bổ sung danh sách MCP tools available vào system prompt, mỗi tool mô tả dạng: `[MCP:{serverName}] {toolName}: {description}`. Danh sách chỉ bao gồm tools từ MCP servers đang ở trạng thái ACTIVE.

**6.53** WHEN AI response chứa tool call request (format: `{"mcpToolCall": {serverId, toolName, arguments}}`), THE Backend_Server SHALL parse tool call, thực thi qua McpProtocolClient, và gửi kết quả trở lại AI để tiếp tục sinh response. Quá trình tool call → result → AI response là tự động (agentic loop), tối đa 5 vòng lặp.

**6.53a** ~~WHEN agentic loop thực thi tool call cho external MCP server (không phải Internal_MCP_Server hoặc Local KB), THE Backend_Server SHALL kiểm tra `toolName` có nằm trong `autoApprove` list của server config hay không. IF tool KHÔNG nằm trong `autoApprove`, THEN agentic loop SHALL KHÔNG thực thi tool, thay vào đó trả về error message cho AI: "Tool requires user approval and cannot be executed automatically", để AI sinh response thay thế mà không dùng tool đó. Internal_MCP_Server tools bypass autoApprove check (RBAC handled by executor).~~

> ✅ **Đã thay thế bởi spec `per-user-tool-permissions` (Requirement 1, 6)**: Agentic loop kiểm tra `user_tool_permissions` per-user qua `UserToolPermissionService.isEnabled()`. Tool disabled bị skip (trả message "Tool '{toolName}' is disabled by user") và không inject vào system prompt. `isToolAutoApproved()` và `mcpServerRepo` param đã bị xóa khỏi `McpAgenticLoop`.

**6.54** THE Frontend_App SHALL hiển thị trạng thái tool execution trong AI Chat: khi tool đang chạy hiển thị indicator "🔧 Đang gọi {toolName}...", khi hoàn tất hiển thị kết quả tool trong collapsible block trước AI response.

**6.55** IF MCP tool execution thất bại trong AI Chat flow, THEN THE Backend_Server SHALL trả về error message cho AI để AI sinh response thay thế (graceful degradation), KHÔNG crash toàn bộ chat flow.

#### Health Monitoring & Status

**6.56** THE Backend_Server SHALL kiểm tra health của MCP server processes mỗi 30 giây bằng cách verify process `isAlive()`. WHEN process không còn alive, THE Backend_Server SHALL cập nhật status thành "ERROR" và trigger auto-restart logic (theo 6.35).

**6.57** THE Backend_Server SHALL cung cấp endpoint `GET /api/integrations/mcp/{id}/status` trả về trạng thái chi tiết: `{status, pid, uptime, toolCount, lastError, restartCount}`. Frontend polling endpoint này mỗi 10 giây để cập nhật UI.

**6.58** THE Frontend_App SHALL hiển thị trạng thái MCP server real-time trên card: status dot (ACTIVE=xanh, STARTING=vàng, ERROR=đỏ, OFFLINE=xám), uptime, số tools available, và error message (nếu có) trong tooltip.

**6.59** WHEN MCP server chuyển trạng thái (STARTING→ACTIVE, ACTIVE→ERROR, v.v.), THE Frontend_App SHALL hiển thị toast notification với thông tin: server name, trạng thái cũ → mới, và lý do (nếu là error).

#### Logging & Diagnostics

**6.60** THE Backend_Server SHALL ghi log tất cả JSON-RPC messages (request/response) với MCP servers vào structured log (SLF4J), bao gồm: timestamp, serverId, method, requestId, duration (ms), và error (nếu có). Log level: DEBUG cho messages thành công, WARN cho errors.

**6.61** THE Backend_Server SHALL cung cấp endpoint `GET /api/integrations/mcp/{id}/logs` trả về 100 dòng log gần nhất của MCP server process (stderr output + JSON-RPC log). Endpoint yêu cầu vai trò Administrator.

---

### Internal MCP Server — Điều khiển ứng dụng qua AI

**User Story:** Là một AI agent kết nối qua MCP, tôi muốn có thể điều khiển toàn bộ giao diện và chức năng của ứng dụng (điều hướng trang, quản lý scan, phân tích ticket, chat, cài đặt, quản lý user, knowledge graph) — để tự động hóa workflow mà không cần tương tác thủ công qua UI.

#### Glossary bổ sung

- **Internal_MCP_Server**: MCP server tích hợp sẵn trong Backend_Server, expose các tương tác UI của ứng dụng dưới dạng MCP tools. Không cần process riêng — chạy in-process, gọi trực tiếp các service layers.
- **UI_Tool**: Một MCP tool tương ứng với một hành động mà người dùng có thể thực hiện trên giao diện ứng dụng (navigate, click button, submit form, v.v.).

#### Đăng ký & Lifecycle

**6.70** WHEN Backend_Server khởi động, THE Backend_Server SHALL tự động đăng ký một Internal_MCP_Server với name `"jira-assistant-ui"`, type `"internal"`. Internal_MCP_Server SHALL luôn ở trạng thái RUNNING và không thể bị disabled, stopped, hoặc deleted bởi user.

**6.71** THE Internal_MCP_Server SHALL implement cùng interface `McpProcessManager` như external MCP servers, nhưng xử lý tool calls in-process (gọi trực tiếp service layer) thay vì qua stdio/JSON-RPC. Điều này đảm bảo tools của Internal_MCP_Server xuất hiện trong aggregated tools list (`GET /api/integrations/mcp/tools`) cùng với external MCP server tools.

**6.72** THE Backend_Server SHALL đánh dấu Internal_MCP_Server với flag `internal = true` trong database. `GET /api/integrations/mcp` SHALL trả về Internal_MCP_Server trong danh sách nhưng Frontend_App SHALL hiển thị badge "LOCAL" (class `local-kb-type-badge`) và ẩn các nút CONFIGURE/REMOVE/TEST cho server này. Frontend_App SHALL hiển thị nút START/STOP (cùng style `mcp-startstop-btn` như external MCP servers) cho phép Administrator bật/tắt Internal_MCP_Server. Trạng thái được lưu qua `PUT /api/settings/feature` với key `internal_mcp_enabled` và đọc qua `GET /api/settings/feature?key=internal_mcp_enabled`. Default: enabled.

> ✅ **Đã cập nhật**: Badge đổi từ "Built-in" → "LOCAL" bởi spec `per-user-tool-permissions` (Requirement 2). Nút START/STOP thống nhất với external MCP servers, xử lý bởi `LocalServerStartStop.kt`. Nút TEST bị ẩn cho internal server.

**6.73** THE Internal_MCP_Server SHALL tự động cập nhật danh sách tools khi có thay đổi cấu hình ứng dụng (thêm/xóa project, thay đổi permissions). Danh sách tools SHALL được rebuild khi gọi `tools/list`.

#### Navigation Tools

**6.74** THE Internal_MCP_Server SHALL cung cấp tool `navigate_to_page` nhận tham số `page` (enum: `dashboard`, `analysis`, `ticket_intelligence`, `knowledge_graph`, `integrations`, `settings`, `user_management`). WHEN tool được gọi, THE Backend_Server SHALL trả về confirmation với page URL và metadata (page title, required permission).

**6.75** THE Internal_MCP_Server SHALL cung cấp tool `get_current_page` không nhận tham số. Tool trả về trang hiện tại đang active trên frontend (dựa trên last known navigation state lưu trong session).

**6.76** THE Internal_MCP_Server SHALL cung cấp tool `list_available_pages` không nhận tham số. Tool trả về danh sách tất cả pages mà user hiện tại có quyền truy cập (dựa trên RBAC role), mỗi page gồm: route, title, description, required_permission.

#### Scan Control Tools

**6.77** THE Internal_MCP_Server SHALL cung cấp tool `start_scan` nhận tham số `projectKey` (required), `concurrency` (optional, integer), `aiConcurrency` (optional, integer), `forceReanalyze` (optional, boolean). Tool gọi trực tiếp `BatchScanEngine.startScan()` và trả về `ScanState` (status, totalTickets, processedCount, progressPercent).

**6.78** THE Internal_MCP_Server SHALL cung cấp tool `pause_scan` nhận tham số `projectKey` (required). Tool gọi `BatchScanEngine.pauseScan()` và trả về trạng thái scan sau khi pause.

**6.79** THE Internal_MCP_Server SHALL cung cấp tool `resume_scan` nhận tham số `projectKey` (required). Tool gọi `BatchScanEngine.resumeScan()` và trả về trạng thái scan sau khi resume.

**6.80** THE Internal_MCP_Server SHALL cung cấp tool `cancel_scan` nhận tham số `projectKey` (required). Tool gọi `BatchScanEngine.cancelScan()` và trả về trạng thái scan sau khi cancel.

**6.81** THE Internal_MCP_Server SHALL cung cấp tool `get_scan_status` nhận tham số `projectKey` (required). Tool trả về trạng thái scan hiện tại gồm: status, totalTickets, processedCount, progressPercent, currentTicketId, recentLog (20 entries gần nhất).

**6.82** THE Internal_MCP_Server SHALL cung cấp tool `get_scan_log` nhận tham số `projectKey` (required), `limit` (optional, default 50), `offset` (optional, default 0). Tool trả về scan log entries với pagination.

#### Ticket Analysis Tools

**6.83** THE Internal_MCP_Server SHALL cung cấp tool `analyze_ticket` nhận tham số `ticketId` (required), `forceReanalyze` (optional, boolean, default false). Tool gọi `AIOrchestrator.analyzeTicket()` và trả về kết quả phân tích AI (summary, complexity, estimation, recommendations).

**6.84** THE Internal_MCP_Server SHALL cung cấp tool `get_ticket_analysis` nhận tham số `ticketId` (required). Tool trả về kết quả phân tích đã lưu trong knowledge base mà không trigger phân tích mới.

**6.85** THE Internal_MCP_Server SHALL cung cấp tool `list_analyzed_tickets` nhận tham số `projectKey` (required), `limit` (optional, default 20), `offset` (optional, default 0). Tool trả về danh sách tickets đã được phân tích trong project.

#### Chat Tools

**6.86** THE Internal_MCP_Server SHALL cung cấp tool `send_chat_message` nhận tham số `message` (required), `conversationId` (optional), `currentScreen` (optional). Tool gọi `ChatService.processChat()` và trả về AI response. Context của tool call (userId, role) lấy từ JWT token của MCP session.

**6.87** THE Internal_MCP_Server SHALL cung cấp tool `get_chat_history` nhận tham số `conversationId` (optional), `limit` (optional, default 20), `offset` (optional, default 0). Tool trả về lịch sử chat messages.

**6.88** THE Internal_MCP_Server SHALL cung cấp tool `list_conversations` không nhận tham số bắt buộc. Tool trả về danh sách conversations của user hiện tại, mỗi conversation gồm: id, title, lastMessage, updatedAt.

#### Settings Tools

**6.89** THE Internal_MCP_Server SHALL cung cấp tool `get_settings` không nhận tham số. Tool trả về toàn bộ application settings hiện tại (key-value pairs). RBAC: yêu cầu Reader+ permission.

**6.90** THE Internal_MCP_Server SHALL cung cấp tool `update_setting` nhận tham số `key` (required), `value` (required). Tool cập nhật setting và trả về giá trị mới. RBAC: yêu cầu Administrator permission.

**6.91** THE Internal_MCP_Server SHALL cung cấp tool `get_setting` nhận tham số `key` (required). Tool trả về giá trị của một setting cụ thể. RBAC: yêu cầu Reader+ permission.

#### User Management Tools

**6.92** THE Internal_MCP_Server SHALL cung cấp tool `list_users` không nhận tham số bắt buộc. Tool trả về danh sách users gồm: id, email, displayName, role, lastLogin. RBAC: yêu cầu Administrator permission.

**6.93** THE Internal_MCP_Server SHALL cung cấp tool `update_user_role` nhận tham số `userId` (required), `role` (required, enum: `Reader`, `Neural_Architect`, `Administrator`). Tool cập nhật role của user và trả về user info sau khi cập nhật. RBAC: yêu cầu Administrator permission.

**6.94** THE Internal_MCP_Server SHALL cung cấp tool `get_user_permissions` nhận tham số `userId` (required). Tool trả về danh sách permissions hiện tại của user dựa trên role. RBAC: yêu cầu Administrator permission.

#### Integration Management Tools

**6.95** THE Internal_MCP_Server SHALL cung cấp tool `list_ai_providers` không nhận tham số. Tool trả về danh sách AI providers đã cấu hình gồm: id, name, type, status (ACTIVE/OFFLINE), model. RBAC: yêu cầu Reader+ permission.

**6.96** THE Internal_MCP_Server SHALL cung cấp tool `test_ai_provider` nhận tham số `providerId` (required). Tool test connection tới AI provider và trả về kết quả (success/failure, latency, error message). RBAC: yêu cầu Administrator permission.

**6.97** THE Internal_MCP_Server SHALL cung cấp tool `list_mcp_servers` không nhận tham số. Tool trả về danh sách MCP servers (bao gồm cả internal) gồm: id, name, type, status, toolCount. RBAC: yêu cầu Reader+ permission.

**6.98** THE Internal_MCP_Server SHALL cung cấp tool `manage_mcp_server` nhận tham số `serverId` (required), `action` (required, enum: `start`, `stop`, `restart`, `test`). Tool thực hiện action tương ứng trên MCP server và trả về status mới. RBAC: yêu cầu Administrator permission. Action KHÔNG được phép trên Internal_MCP_Server (serverId = `jira-assistant-ui`).

#### Knowledge Graph Tools

**6.99** THE Internal_MCP_Server SHALL cung cấp tool `get_graph_data` nhận tham số `projectKey` (optional), `filters` (optional, object với fields: nodeTypes, edgeTypes, minWeight). Tool trả về graph data gồm nodes và edges cho visualization. RBAC: yêu cầu Reader+ permission.

**6.100** THE Internal_MCP_Server SHALL cung cấp tool `search_graph_nodes` nhận tham số `query` (required), `nodeType` (optional), `limit` (optional, default 20). Tool tìm kiếm nodes trong knowledge graph theo keyword và trả về matching nodes với relationships.

#### Dashboard & Project Tools

**6.101** THE Internal_MCP_Server SHALL cung cấp tool `get_dashboard_metrics` nhận tham số `projectKey` (required). Tool trả về project metrics gồm: totalTickets, analyzedCount, averageComplexity, velocityTrend, scanStatus.

**6.102** THE Internal_MCP_Server SHALL cung cấp tool `list_projects` không nhận tham số. Tool trả về danh sách projects available từ Jira gồm: key, name, totalIssues, lastScanDate.

**6.103** THE Internal_MCP_Server SHALL cung cấp tool `get_project_analysis_summary` nhận tham số `projectKey` (required). Tool trả về tổng hợp phân tích project: sprint analytics, velocity trends, bottleneck radar data, top complex tickets.

#### RBAC Enforcement

**6.104** THE Internal_MCP_Server SHALL enforce RBAC permissions cho mỗi tool call giống hệt như UI. Mỗi tool có `requiredPermission` field trong tool definition. WHEN tool call được thực thi, THE Internal_MCP_Server SHALL kiểm tra permission của user (từ JWT token) trước khi thực hiện. IF user không có permission, THEN trả về error `{"code": -32603, "message": "Access denied: requires {permission}"}`.

**6.105** THE Internal_MCP_Server SHALL include `requiredPermission` và `requiredRole` trong `description` field của mỗi tool, để AI agent biết trước tool nào user hiện tại có quyền sử dụng.

**6.106** WHEN user có role Reader, THE Internal_MCP_Server SHALL chỉ cho phép gọi các tools read-only: `navigate_to_page`, `get_current_page`, `list_available_pages`, `get_scan_status`, `get_scan_log`, `get_ticket_analysis`, `list_analyzed_tickets`, `get_chat_history`, `list_conversations`, `get_settings`, `get_setting`, `list_ai_providers`, `list_mcp_servers`, `get_graph_data`, `search_graph_nodes`, `get_dashboard_metrics`, `list_projects`, `get_project_analysis_summary`.

#### Tool Discovery & Integration

**6.107** THE Internal_MCP_Server SHALL trả về danh sách tools qua cùng mechanism như external MCP servers. WHEN `GET /api/integrations/mcp/tools` được gọi, response SHALL bao gồm tools từ Internal_MCP_Server với `serverId: "jira-assistant-ui"` và `serverName: "Jira Assistant UI"` cùng với tools từ external servers.

**6.108** THE Internal_MCP_Server SHALL cung cấp inputSchema (JSON Schema) cho mỗi tool, mô tả đầy đủ parameters với type, description, required fields, và enum values (nếu có). Schema phải đủ chi tiết để AI agent tự sinh arguments hợp lệ mà không cần hướng dẫn thêm.

**6.109** WHEN AI Chat sử dụng MCP tools (agentic loop, req 6.53), THE Backend_Server SHALL ưu tiên hiển thị Internal_MCP_Server tools trước external tools trong system prompt injection, với prefix `[Internal]` thay vì `[MCP:{serverName}]`.

#### Error Handling

**6.110** IF Internal_MCP_Server tool call thất bại do lỗi nghiệp vụ (ticket not found, project not configured, scan already running), THEN THE Internal_MCP_Server SHALL trả về error response với `isError: true` và message mô tả cụ thể lỗi, KHÔNG throw exception.

**6.111** IF Internal_MCP_Server tool call thất bại do lỗi hệ thống (database error, service unavailable), THEN THE Internal_MCP_Server SHALL trả về JSON-RPC error code `-32603` (Internal error) với message chi tiết, và ghi log WARN.

**6.112** THE Internal_MCP_Server SHALL validate tất cả tool arguments trước khi thực thi. IF arguments không hợp lệ (missing required, wrong type, invalid enum value), THEN trả về JSON-RPC error code `-32602` (Invalid params) với message chỉ rõ field nào sai.

---

## Liên kết Spec — Per-User Tool Permissions (✅ Đã triển khai)

> **Per-User Tool Permissions (spec `per-user-tool-permissions`)**: Đã chuyển đổi cách quản lý tool permissions từ global sang per-user với mô hình enable/disable. Các thay đổi đã thực hiện:
> - **6.47a, 6.47b** → ✅ Đã thay thế bởi Requirement 5: Toggle và bulk actions trên Integrations page đọc/ghi `user_tool_permissions` per-user với `enable_all`/`disable_all`. `McpToolsSection.kt` gọi `GET/PUT /api/chat/tool-permissions` thay vì `PUT /api/integrations/mcp/{id}`
> - **6.50** → ✅ Đã thay thế bởi Requirement 1: Per-user enable/disable thay vì global autoApprove. `isToolAutoApproved()` đã bị xóa khỏi `McpAgenticLoop`
> - **6.53a** → ✅ Đã thay thế bởi Requirement 1, 6: Agentic loop kiểm tra per-user permissions qua `UserToolPermissionService.isEnabled()`, disabled tools bị skip và không inject vào system prompt qua `ChatMcpToolsContext`
> - **Badge**: ✅ Requirement 2 đã thống nhất badge "LOCAL" cho cả Internal_MCP_Server và Local_KB (`createBuiltinBadge()` → class `local-kb-type-badge`, text "LOCAL")
> - **AI Chat Sidebar**: ✅ Requirement 4 đã thêm section "🔧 Tool Permissions" trong chat sidebar (`ChatToolPermissions.kt`)
