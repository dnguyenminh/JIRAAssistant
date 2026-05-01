# Requirements Document

## Introduction

Trước khi sinh tài liệu BRD/FSD/Slides, hệ thống cần kiểm tra tình trạng sẵn sàng của tất cả MCP server (cả internal và external) đang được cấu hình. Nếu có MCP server nào đang active nhưng không phản hồi được (connection failed, server chưa start, process crashed), hệ thống sẽ cảnh báo cho user biết cụ thể server nào không sẵn sàng. User có thể chọn tiếp tục sinh tài liệu (với các tool khả dụng) hoặc hủy để sửa cấu hình MCP trước.

Hiện tại, khi MCP server không sẵn sàng, tool call fail silently và AI sinh ra BRD/FSD rỗng hoặc thiếu dữ liệu — user không biết nguyên nhân. Feature này giải quyết vấn đề đó bằng cách chủ động kiểm tra và thông báo trước khi bắt đầu quá trình sinh tài liệu.

## Glossary

- **MCP_Server**: Một MCP (Model Context Protocol) server được cấu hình trong hệ thống, có thể là internal (Jira Assistant UI) hoặc external (Knowledge Base, Markitdown, Database). Mỗi server được quản lý bởi McpProcessManager.
- **Readiness_Check**: Quá trình kiểm tra tất cả MCP server đang active để xác nhận chúng có thể nhận và xử lý tool call hay không.
- **Health_Check_API**: Endpoint backend mới (`GET /api/mcp/health`) trả về trạng thái sẵn sàng của từng MCP server.
- **Readiness_Dialog**: Modal dialog hiển thị trên frontend khi phát hiện MCP server không sẵn sàng, cho phép user chọn tiếp tục hoặc hủy.
- **Document_Generation_Flow**: Luồng sinh tài liệu BRD/FSD/Slides, bắt đầu khi user click button GENERATE trên Ticket Intelligence page.
- **Internal_MCP_Server**: MCP server tích hợp sẵn trong ứng dụng (Jira Assistant UI), luôn sẵn sàng khi app đang chạy.
- **External_MCP_Server**: MCP server bên ngoài (Knowledge Base, Markitdown, Database) được quản lý qua McpProcessManager, có thể ở trạng thái RUNNING, STOPPED, ERROR, hoặc OFFLINE.
- **Tool_Ping**: Thao tác gọi thử `tools/list` trên MCP client để xác nhận server phản hồi được.

## Requirements

### Requirement 1: Backend Health Check Endpoint

**User Story:** As a frontend client, I want to call a single API endpoint to get the readiness status of all configured MCP servers, so that I can warn the user before starting document generation.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/mcp/health`, THE Health_Check_API SHALL return a JSON response containing the readiness status of each active MCP server within 5 seconds.
2. THE Health_Check_API SHALL include the following fields for each server: `configId`, `serverName`, `ready` (boolean), `toolCount` (integer), and `error` (string, nullable).
3. WHEN an External_MCP_Server has a running process and its MCP client responds to a Tool_Ping within 3 seconds, THE Health_Check_API SHALL report that server as `ready: true`.
4. WHEN an External_MCP_Server has no running process, no MCP client, or the Tool_Ping times out after 3 seconds, THE Health_Check_API SHALL report that server as `ready: false` with a descriptive `error` message.
5. THE Health_Check_API SHALL always report the Internal_MCP_Server as `ready: true` when the application is running.
6. THE Health_Check_API SHALL include a top-level `allReady` boolean field that is `true` only when every active server has `ready: true`.
7. THE Health_Check_API SHALL require authentication (valid JWT token) and the `ANALYZE_AI` permission.
8. IF the Health_Check_API encounters an unexpected error during the check, THEN THE Health_Check_API SHALL return HTTP 500 with a descriptive error message.

### Requirement 2: Frontend Readiness Check Before Generation

**User Story:** As a user, I want the system to automatically check MCP server readiness when I click a GENERATE button, so that I know upfront if any tools are unavailable.

#### Acceptance Criteria

1. WHEN the user clicks any document generation button (GENERATE BRD, RE-GENERATE BRD, GENERATE FSD, RE-GENERATE FSD, GENERATE SLIDES, or GENERATE ALL), THE Document_Generation_Flow SHALL call the Health_Check_API before starting the generation job.
2. WHILE the Readiness_Check is in progress, THE Document_Generation_Flow SHALL display a BlockingOverlay with the message "Checking MCP tools..." on the document generation section.
3. WHEN the Health_Check_API returns `allReady: true`, THE Document_Generation_Flow SHALL proceed with document generation without showing the Readiness_Dialog.
4. WHEN the Health_Check_API returns `allReady: false`, THE Document_Generation_Flow SHALL display the Readiness_Dialog listing the unavailable servers before proceeding.
5. IF the Health_Check_API call fails (network error, timeout, HTTP error), THEN THE Document_Generation_Flow SHALL display an error toast with the message "Không thể kiểm tra MCP tools — vui lòng thử lại" and re-enable the generation button.

### Requirement 3: Readiness Warning Dialog

**User Story:** As a user, I want to see which MCP servers are unavailable and choose whether to continue or cancel, so that I can make an informed decision about document quality.

#### Acceptance Criteria

1. WHEN the Readiness_Dialog is displayed, THE Readiness_Dialog SHALL show a list of all MCP servers with their readiness status: a green checkmark icon for ready servers and a red warning icon for unavailable servers.
2. THE Readiness_Dialog SHALL display the server name and error reason for each unavailable server.
3. THE Readiness_Dialog SHALL display a warning message explaining that generating documents with unavailable tools may result in incomplete or lower-quality output.
4. THE Readiness_Dialog SHALL provide two action buttons: "Tiếp tục" (Continue) and "Hủy" (Cancel).
5. WHEN the user clicks "Tiếp tục", THE Document_Generation_Flow SHALL proceed with document generation using only the available MCP tools.
6. WHEN the user clicks "Hủy", THE Document_Generation_Flow SHALL cancel the generation request and re-enable the generation button.
7. THE Readiness_Dialog SHALL be dismissible by clicking outside the dialog or pressing the Escape key, with the same behavior as clicking "Hủy".

### Requirement 4: Health Check Performance

**User Story:** As a user, I want the readiness check to be fast, so that it does not noticeably delay the document generation workflow.

#### Acceptance Criteria

1. THE Health_Check_API SHALL complete the readiness check for all active MCP servers within 5 seconds total, regardless of the number of servers.
2. THE Health_Check_API SHALL execute Tool_Ping checks for all External_MCP_Servers concurrently, not sequentially.
3. WHEN an individual Tool_Ping exceeds 3 seconds, THE Health_Check_API SHALL mark that server as not ready and continue checking the remaining servers.
4. THE Readiness_Check on the frontend (from button click to dialog display or generation start) SHALL complete within 6 seconds under normal network conditions.

### Requirement 5: Health Check for Specific Document Types

**User Story:** As a user, I want the system to identify which document types will be most affected by unavailable tools, so that I can understand the impact on my specific generation request.

#### Acceptance Criteria

1. THE Health_Check_API response SHALL categorize each server by its role: `knowledge_base`, `database`, `markitdown`, `jira_internal`, or `other`.
2. WHEN the Readiness_Dialog is displayed, THE Readiness_Dialog SHALL indicate which unavailable servers are critical for the requested document type (BRD requires Knowledge Base and Database; FSD requires Knowledge Base and Database; Slides require Knowledge Base).
3. WHEN all critical servers for the requested document type are unavailable, THE Readiness_Dialog SHALL display an additional strong warning: "Các tool quan trọng cho [document type] đều không khả dụng — tài liệu sinh ra có thể thiếu dữ liệu nghiêm trọng."
