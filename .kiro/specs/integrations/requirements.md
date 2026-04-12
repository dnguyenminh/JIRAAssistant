# Integrations — Requirements

# Yêu cầu 6: Integrations — Quản lý Provider (Jira + AI) (MH6)

**User Story:** Là một Administrator, tôi muốn quản lý và kiểm tra kết nối tất cả providers (Jira Cloud và AI providers), để đảm bảo hệ thống luôn có kết nối ổn định.

## Mô tả

Trang Integrations là nơi quản lý TẤT CẢ kết nối bên ngoài của hệ thống, bao gồm:
- **Jira Cloud Services**: Cấu hình kết nối Jira hệ thống (domain, email, API token) qua modal với nút "SAVE & TEST".
- **AI Providers**: Ollama, Gemini, LM Studio, Gemini CLI — cấu hình endpoint, model, API key, v.v.

### Thay đổi so với thiết kế ban đầu:
- **Config modal có 2 nút riêng biệt**: TEST CONNECTION và SAVE. Nút SAVE bị disable cho đến khi TEST CONNECTION thành công.
- **Ollama Model Name**: Là dropdown được load từ `GET /api/integrations/ollama/models` thay vì text input.
- **MAX TOKENS**: Là slider (range 256–32768) cho tất cả AI providers, thay vì number input.
- **Status persistence**: Trạng thái provider (ACTIVE/OFFLINE) được lưu vào bảng `provider_configs` trong DB, persist qua page refresh và server restart.
- **Default 5 providers**: Luôn hiển thị 5 provider cards (Jira, Ollama, Gemini, LM Studio, Gemini CLI) ngay cả khi chưa có record trong DB. DB values override defaults.
- **TEST CONNECTION dùng form values**: Khi nhấn TEST CONNECTION, sử dụng endpoint/model từ form hiện tại (chưa lưu) thay vì đọc từ DB.

Tất cả credentials được lưu vào database (bảng `provider_configs`) với sensitive fields (API keys, tokens) được mã hóa AES-256-GCM.

## Tiêu chí chấp nhận

1. THE Frontend_App SHALL luôn hiển thị 5 thẻ provider dạng lưới responsive (min 380px): Jira Cloud Services, Ollama (Local), Google Gemini API, LM Studio, Gemini CLI Interface — ngay cả khi chưa có record trong DB. `GET /api/integrations` trả về 5 default providers merged với DB values
2. THE Frontend_App SHALL hiển thị trên mỗi thẻ: logo/icon provider, chấm trạng thái (status dot), tóm tắt cấu hình, và nút hành động
3. THE Frontend_App SHALL sử dụng 3 trạng thái cho status dot: Active (xanh lá với glow), Standby (xanh dương với glow), Offline (đỏ với glow), mỗi trạng thái kèm tooltip chi tiết (latency, lý do lỗi, thời gian session). Trạng thái được persist trong DB và survive page refresh
4. WHEN người dùng nhấn "TEST LINK" trên provider card, THE Backend_Server SHALL gửi request kiểm tra kết nối, cập nhật status (ACTIVE/OFFLINE) vào bảng `provider_configs`, và trả về kết quả kèm toast message
5. WHEN người dùng nhấn "TEST LINK", THE Frontend_App SHALL đổi text nút thành "PROBING...", hiển thị progress bar từ 0% đến 100%, và reset nút sau khi hoàn tất
6. THE Frontend_App SHALL hiển thị modal cấu hình cho **Jira Cloud** gồm: Jira Domain URL (text input), Email / Service Account (text input), API Token (password input), và nút "SAVE & TEST". Hiển thị trạng thái kết nối hiện tại (connected domain, last tested)
7. THE Frontend_App SHALL hiển thị dropdown chọn model trên thẻ Gemini với các tùy chọn: Gemini 1.5 Pro, Gemini 1.0 Ultra, Gemini 1.5 Flash
8. WHILE người dùng không có vai trò Administrator, THE Frontend_App SHALL cho phép xem nhưng vô hiệu hóa chỉnh sửa cấu hình provider
9. WHEN Administrator nhấn vào thẻ provider hoặc nút "CONFIGURE", THE Frontend_App SHALL mở modal cấu hình chi tiết riêng cho loại provider đó
10. THE Frontend_App SHALL hiển thị modal cấu hình cho Ollama gồm: Endpoint URL (text input), Model Name (**dropdown** load từ `GET /api/integrations/ollama/models`), Temperature (slider 0-1), Max Tokens (**slider** range 256–32768), và 2 nút riêng biệt: "TEST CONNECTION" và "SAVE". Nút SAVE bị disable cho đến khi TEST CONNECTION thành công
11. THE Frontend_App SHALL hiển thị modal cấu hình cho Gemini gồm: API Key (password input), Model Tier (dropdown), Temperature (slider 0-1), Max Tokens (**slider** range 256–32768), và 2 nút riêng biệt: "TEST CONNECTION" và "SAVE". Nút SAVE bị disable cho đến khi TEST CONNECTION thành công
12. THE Frontend_App SHALL hiển thị modal cấu hình cho LM Studio gồm: Endpoint URL (host:port), Model Name (text input), Temperature (slider 0-1), Max Tokens (**slider** range 256–32768), và 2 nút riêng biệt: "TEST CONNECTION" và "SAVE". Nút SAVE bị disable cho đến khi TEST CONNECTION thành công
13. THE Frontend_App SHALL hiển thị modal cấu hình cho Gemini CLI gồm: CLI Path (file path input), Model Name (text input), và 2 nút riêng biệt: "TEST CONNECTION" và "SAVE". Nút SAVE bị disable cho đến khi TEST CONNECTION thành công
14. WHEN Administrator nhấn "SAVE", THE Backend_Server SHALL lưu cấu hình vào database (bảng `provider_configs` với sensitive fields encrypted AES-256-GCM) và cập nhật status. WHEN Administrator nhấn "TEST CONNECTION", THE Backend_Server SHALL test kết nối sử dụng endpoint/model từ request body (chưa lưu vào DB) và trả về kết quả
15. WHEN cấu hình provider được lưu thành công, THE Frontend_App SHALL cập nhật status dot và hiển thị toast "Configuration saved"
16. THE Frontend_App SHALL cho phép Administrator kéo thả hoặc dùng nút mũi tên để thay đổi thứ tự ưu tiên failover giữa các AI providers
17. WHEN Jira credentials được cập nhật qua Integrations, THE Backend_Server SHALL cập nhật credentials trong database và tất cả API calls tới Jira sau đó SHALL sử dụng credentials mới (JiraClient factory pattern đọc credentials từ DB mỗi request)
18. THE Backend_Server SHALL cung cấp endpoint `GET /api/integrations/ollama/models` trả về danh sách models có sẵn từ Ollama instance (gọi `GET /api/tags` trên Ollama endpoint)
19. THE Backend_Server SHALL cung cấp endpoint `POST /api/integrations/{providerId}/test` nhận `{endpoint, model}` từ request body để test kết nối với config chưa lưu. OllamaAgent sử dụng `GET /api/tags` (lightweight) cho `testConnection()`


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

**6.47** THE Frontend_App SHALL hiển thị danh sách tools trên MCP server card (expandable section) sau khi server ở trạng thái ACTIVE. Mỗi tool hiển thị: icon 🔧, tool name, description (truncated 100 chars), và nút "View Schema" mở modal hiển thị inputSchema dạng formatted JSON.

#### Tool Execution

**6.48** THE Backend_Server SHALL cung cấp endpoint `POST /api/integrations/mcp/tools/call` nhận `{serverId, toolName, arguments}` và route tool call tới MCP server tương ứng qua `tools/call` JSON-RPC request. Response trả về kết quả từ MCP server (content array với type text/image/resource).

**6.49** IF MCP server không phản hồi `tools/call` request trong vòng 60 giây, THEN THE Backend_Server SHALL cancel request, trả về HTTP 504 Gateway Timeout với message "Tool execution timeout", và ghi log chi tiết.

**6.50** IF tool name nằm trong danh sách `autoApprove` của MCP server config, THEN THE Backend_Server SHALL thực thi tool call ngay lập tức mà không cần xác nhận từ người dùng. IF tool name KHÔNG nằm trong `autoApprove`, THEN THE Backend_Server SHALL yêu cầu xác nhận trước khi thực thi (trả về `{requiresApproval: true, toolName, arguments}` để Frontend hiển thị confirmation dialog).

**6.51** THE Frontend_App SHALL hiển thị confirmation dialog khi tool call yêu cầu approval, gồm: tool name, server name, arguments (formatted JSON), và 2 nút "Approve" / "Deny". WHEN người dùng nhấn "Approve", THE Frontend_App SHALL gửi lại request với `{approved: true}`.

#### Tích hợp AI Chat

**6.52** WHEN AI_Chat_Sidebar gửi tin nhắn, THE Backend_Server (ChatService) SHALL bổ sung danh sách MCP tools available vào system prompt, mỗi tool mô tả dạng: `[MCP:{serverName}] {toolName}: {description}`. Danh sách chỉ bao gồm tools từ MCP servers đang ở trạng thái ACTIVE.

**6.53** WHEN AI response chứa tool call request (format: `{"mcpToolCall": {serverId, toolName, arguments}}`), THE Backend_Server SHALL parse tool call, thực thi qua McpProtocolClient, và gửi kết quả trở lại AI để tiếp tục sinh response. Quá trình tool call → result → AI response là tự động (agentic loop), tối đa 5 vòng lặp.

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
