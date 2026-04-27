# Per-User Tool Permissions — Requirements

## Giới thiệu

Feature này thay đổi cách quản lý quyền sử dụng MCP tools từ cấu hình global (trên server config) sang cấu hình per-user (lưu trong database theo từng user). Mỗi user có thể enable/disable bất kỳ MCP tool nào — tool disabled sẽ không được AI sử dụng. Ngoài ra, thống nhất badge hiển thị cho các server local/built-in và thêm UI quản lý tool permissions trong AI Chat sidebar.

Feature này ảnh hưởng trực tiếp đến các requirements hiện có trong spec `mcp-servers`: **6.47a**, **6.47b**, **6.50**, **6.53a**. Các requirements mới sẽ thay thế hoặc mở rộng logic tương ứng.

## Glossary

- **Tool_Permission**: Cấu hình quyền sử dụng một MCP tool cụ thể của một user. Có 2 trạng thái: `enabled` (AI có thể sử dụng tool) hoặc `disabled` (AI không thể sử dụng tool).
- **User_Tool_Permissions**: Bảng database lưu trữ danh sách tool permissions per-user. Mỗi user có một bản ghi riêng chứa JSON map `{serverId::toolName → enabled|disabled}`.
- **Frontend_App**: Ứng dụng frontend Kotlin/JS.
- **Backend_Server**: Ktor backend server.
- **AI_Chat_Sidebar**: Panel chat AI bên phải màn hình.
- **Integrations_Page**: Trang quản lý MCP servers và AI providers.
- **Internal_MCP_Server**: MCP server tích hợp sẵn (Jira Assistant UI), type `internal`.
- **Local_KB**: Local Knowledge Base tool, server local tích hợp sẵn.

## Requirements

### Requirement 1: Enable/Disable tool per-user

**User Story:** Là một user, tôi muốn có thể bật/tắt bất kỳ MCP tool nào — khi tool bị tắt, AI sẽ không sử dụng tool đó — để tôi kiểm soát được AI dùng những tool nào.

> **Thay thế requirement 6.50 và 6.53a** trong spec `mcp-servers`.

#### Acceptance Criteria

1. WHEN một MCP tool có trạng thái `enabled` trong User_Tool_Permissions của user hiện tại, THE Backend_Server SHALL cho phép AI sử dụng tool đó trong agentic loop.

2. WHEN một MCP tool có trạng thái `disabled` trong User_Tool_Permissions của user hiện tại, THE Backend_Server SHALL KHÔNG cho phép AI sử dụng tool đó. Agentic loop SHALL skip tool call và trả về message cho AI: "Tool '{toolName}' is disabled by user" để AI sinh response thay thế.

3. THE Backend_Server SHALL không inject tool đã disabled vào system prompt của AI Chat, để AI không biết tool đó tồn tại và không cố gọi tool bị tắt.

4. WHEN tool thuộc Internal_MCP_Server hoặc Local_KB, user vẫn có thể enable/disable tool đó giống external tools.


### Requirement 2: Thống nhất badge LOCAL cho server local/built-in

**User Story:** Là một user, tôi muốn tất cả MCP servers tích hợp sẵn (Local Knowledge Base và Jira Assistant UI) đều hiển thị badge "LOCAL" thống nhất — thay vì một cái hiện "LOCAL" và một cái hiện "Built-in" — để giao diện nhất quán và dễ phân biệt với external servers.

#### Acceptance Criteria

1. THE Frontend_App SHALL hiển thị badge "LOCAL" trên card của Internal_MCP_Server (Jira Assistant UI) thay vì badge "Built-in" hiện tại.

2. THE Frontend_App SHALL hiển thị badge "LOCAL" trên card của Local_KB, giữ nguyên như hiện tại.

3. THE Frontend_App SHALL sử dụng cùng CSS class `local-kb-type-badge` cho badge "LOCAL" trên cả Internal_MCP_Server card và Local_KB card, đảm bảo style thống nhất.


### Requirement 3: Lưu trữ Tool Permissions per-user trong database

**User Story:** Là một user, tôi muốn cấu hình tool permissions (enable/disable) được lưu riêng cho tôi — không ảnh hưởng đến user khác — để mỗi người có thể tùy chỉnh danh sách tools theo nhu cầu cá nhân.

> **Thay thế logic autoApprove global** trong requirement 6.47a của spec `mcp-servers`. Cột `auto_approve` trên bảng `mcp_servers` vẫn giữ làm default enabled list cho user mới.

#### Acceptance Criteria

1. THE Backend_Server SHALL tạo bảng `user_tool_permissions` trong database (SQLDelight) với cấu trúc: `user_id TEXT NOT NULL PRIMARY KEY, permissions_json TEXT NOT NULL DEFAULT '{}', updated_at TEXT NOT NULL`. Trường `permissions_json` lưu JSON object dạng `{"serverId::toolName": "enabled"|"disabled", ...}`.

2. THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/tool-permissions` trả về danh sách tool permissions của user hiện tại (từ JWT token). Response format: `{permissions: {"serverId::toolName": "enabled"|"disabled"}, defaults: {"serverId::toolName": "enabled"}}`. Field `defaults` chứa danh sách tools enabled từ cột `auto_approve` của bảng `mcp_servers` (global default).

3. THE Backend_Server SHALL cung cấp endpoint `PUT /api/chat/tool-permissions` nhận JSON body `{permissions: {"serverId::toolName": "enabled"|"disabled"}}` và lưu vào bảng `user_tool_permissions` cho user hiện tại. Endpoint yêu cầu JWT authentication (Reader+ role).

4. WHEN user chưa có bản ghi trong `user_tool_permissions`, THE Backend_Server SHALL sử dụng danh sách `auto_approve` từ bảng `mcp_servers` làm default permissions. Tools nằm trong `auto_approve` list có trạng thái `enabled`, tools không nằm trong list cũng có trạng thái `enabled` (mặc định tất cả tools đều enabled cho user mới).

5. WHEN Administrator thay đổi `auto_approve` list trên MCP server config (qua Integrations_Page), THE Backend_Server SHALL chỉ cập nhật global default, KHÔNG ghi đè permissions đã được user tùy chỉnh trong `user_tool_permissions`.

6. THE Backend_Server SHALL cung cấp endpoint `PUT /api/chat/tool-permissions/bulk` nhận `{serverId, action: "enable_all"|"disable_all"}` để enable hoặc disable tất cả tools của một server cho user hiện tại.

7. THE Backend_Server SHALL validate `permissions_json` trước khi lưu: mỗi key phải có format `serverId::toolName`, mỗi value phải là `enabled` hoặc `disabled`. IF validation thất bại, THEN trả về HTTP 400 Bad Request với message chỉ rõ field sai.

### Requirement 4: UI quản lý Tool Permissions trong AI Chat Sidebar

**User Story:** Là một user, tôi muốn có một section trong AI Chat sidebar để xem và cập nhật tool permissions cá nhân — biết tool nào đang bật, tool nào đang tắt — mà không cần mở trang Integrations.

#### Acceptance Criteria

1. THE Frontend_App SHALL hiển thị section "🔧 Tool Permissions" trong AI_Chat_Sidebar, đặt bên dưới section "MCP Tools Available" hiện có. Section này collapsible (mặc định collapsed).

2. WHEN user expand section "Tool Permissions", THE Frontend_App SHALL gọi `GET /api/chat/tool-permissions` và hiển thị danh sách tools grouped theo server name. Mỗi tool hiển thị: tool name, server name, và toggle switch (ON = enabled, OFF = disabled).

3. WHEN user thay đổi toggle switch của một tool, THE Frontend_App SHALL gọi `PUT /api/chat/tool-permissions` để cập nhật permission cho tool đó. UI SHALL hiển thị trạng thái saving (spinner) và confirmation (toast) sau khi lưu thành công.

4. THE Frontend_App SHALL hiển thị nút "Enable All" và "Disable All" cho mỗi server group trong section Tool Permissions. WHEN user nhấn "Enable All", THE Frontend_App SHALL gọi `PUT /api/chat/tool-permissions/bulk` với `action: "enable_all"`. WHEN user nhấn "Disable All", gọi với `action: "disable_all"`.

5. THE Frontend_App SHALL hiển thị counter "X / Y enabled" cho mỗi server group, trong đó X là số tools có trạng thái `enabled` và Y là tổng số tools của server đó.

6. WHEN user chưa tùy chỉnh permissions (chưa có bản ghi trong `user_tool_permissions`), THE Frontend_App SHALL hiển thị tất cả tools là enabled (default) với label "(default)" bên cạnh mỗi toggle.

7. THE Frontend_App SHALL thêm section Tool Permissions vào HTML template `ai-chat-sidebar.html` theo đúng quy tắc tách biệt VIEW/CONTROLLER. Logic xử lý nằm trong file Kotlin riêng `ChatToolPermissions.kt` trong package `components/chat/`.

### Requirement 5: Đồng bộ Tool Permissions giữa Integrations Page và AI Chat Sidebar

**User Story:** Là một user, tôi muốn khi thay đổi tool permissions ở Integrations page hoặc AI Chat sidebar thì cả hai nơi đều cập nhật — để tránh nhầm lẫn khi cấu hình ở một nơi mà nơi kia hiển thị khác.

> **Mở rộng requirement 6.47a** trong spec `mcp-servers`: checkbox trên Integrations_Page giờ đọc/ghi từ `user_tool_permissions` thay vì `mcp_servers.auto_approve`.

#### Acceptance Criteria

1. WHEN user thay đổi toggle trên Integrations_Page (McpToolsSection), THE Frontend_App SHALL gọi `PUT /api/chat/tool-permissions` để cập nhật permission per-user thay vì gọi `PUT /api/integrations/mcp/{id}` để cập nhật `auto_approve` global.

2. WHEN user mở Integrations_Page và expand tools section của một MCP server, THE Frontend_App SHALL gọi `GET /api/chat/tool-permissions` để hiển thị trạng thái toggle theo permissions per-user của user hiện tại, thay vì đọc từ `auto_approve` field của server config.

3. WHEN user nhấn "Enable All" hoặc "Disable All" trên Integrations_Page, THE Frontend_App SHALL gọi `PUT /api/chat/tool-permissions/bulk` thay vì cập nhật `auto_approve` global.

4. THE Frontend_App SHALL hiển thị tooltip trên toggle: "Enabled — AI có thể sử dụng tool này" (khi ON) hoặc "Disabled — AI không thể sử dụng tool này" (khi OFF).

5. WHEN Administrator thay đổi `auto_approve` global trên server config (qua JSON mode hoặc import), THE Backend_Server SHALL giữ nguyên permissions per-user đã tùy chỉnh. Chỉ user chưa có bản ghi trong `user_tool_permissions` mới bị ảnh hưởng bởi thay đổi global default.

### Requirement 6: Cập nhật Agentic Loop sử dụng per-user permissions

**User Story:** Là một developer, tôi muốn agentic loop trong AI Chat kiểm tra tool permissions per-user thay vì global autoApprove — để mỗi user có trải nghiệm khác nhau tùy theo cấu hình cá nhân.

> **Thay thế logic kiểm tra autoApprove** trong `McpAgenticLoop.isToolAutoApproved()`.

#### Acceptance Criteria

1. WHEN agentic loop cần thực thi tool call, THE Backend_Server SHALL kiểm tra `user_tool_permissions` của user hiện tại (userId từ JWT). IF tool có trạng thái `disabled`, THEN skip tool call và trả message "Tool '{toolName}' is disabled by user" cho AI.

2. WHEN user chưa có bản ghi trong `user_tool_permissions`, THE Backend_Server SHALL coi tất cả tools là `enabled` (backward compatible — mặc định tất cả tools đều bật).

3. THE Backend_Server SHALL truyền `userId` vào `McpAgenticLoop.execute()` để agentic loop có thể query `user_tool_permissions` cho đúng user.

4. THE Backend_Server SHALL filter danh sách MCP tools trong system prompt injection: chỉ inject tools có trạng thái `enabled` cho user hiện tại. Tools `disabled` SHALL KHÔNG xuất hiện trong system prompt.

5. IF `user_tool_permissions` query thất bại (database error), THEN THE Backend_Server SHALL coi tất cả tools là `enabled` (fallback an toàn) và ghi log WARN.
