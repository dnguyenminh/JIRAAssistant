# Per-User Tool Permissions — Tasks

## Tổng quan

Chuyển đổi hệ thống quản lý quyền MCP tools từ global (`mcp_servers.auto_approve`) sang per-user (`user_tool_permissions`). Mô hình enable/disable đơn giản: tool enabled = AI dùng được, tool disabled = AI không dùng được.

## Tasks

- [x] 1. Database Schema + Repository
  - [x] 1.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `user_tool_permissions` (user_id TEXT PK, permissions_json TEXT, updated_at TEXT) + queries (find, upsert, delete)
    - _Requirements: 3.1_
  - [x] 1.2 Cập nhật `DatabaseMigrations.kt`: thêm CREATE TABLE IF NOT EXISTS + ALTER TABLE migration cho existing DB
    - _Requirements: 3.1_
  - [x] 1.3 Tạo `shared/src/commonMain/kotlin/com/assistant/chat/UserToolPermissionRepository.kt`: interface với `findByUserId()`, `save()`, `delete()`
    - _Requirements: 3.1, 3.2_
  - [x] 1.4 Tạo `shared/src/jvmMain/kotlin/com/assistant/chat/UserToolPermissionRepositoryImpl.kt`: impl dùng JiraDatabase
    - _Requirements: 3.1, 3.2_

- [x] 2. Shared DTOs
  - [x] 2.1 Tạo `shared/src/commonMain/kotlin/com/assistant/chat/ToolPermissionDtos.kt`: `ToolPermissionsResponse`, `ToolPermissionsUpdateRequest`, `ToolPermissionsBulkRequest`
    - _Requirements: 3.2, 3.3, 3.6_

- [x] 3. UserToolPermissionService
  - [x] 3.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/chat/UserToolPermissionService.kt`: class với `getEffectivePermissions()`, `isEnabled()`, `getDisabledTools()`, `savePermissions()`, `bulkUpdate()`, `validate()`
    - _Requirements: 3.2, 3.3, 3.4, 3.6, 3.7, 6.1, 6.2_
  - [x] 3.2 Write property test: Permissions round-trip (Property 3)
    - **Validates: Requirements 3.2, 3.3**
  - [x] 3.3 Write property test: Default = all enabled (Property 4)
    - **Validates: Requirements 3.4, 6.2**
  - [x] 3.4 Write property test: Validation rejects invalid entries (Property 6)
    - **Validates: Requirements 3.7**
  - [x] 3.5 Write property test: Per-user isolation (Property 5)
    - **Validates: Requirements 3.5, 5.5**

- [x] 4. Checkpoint — Database + Service layer

- [x] 5. API Endpoints — ChatToolPermissionRoutes
  - [x] 5.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/routes/ChatToolPermissionRoutes.kt`: GET /tool-permissions, PUT /tool-permissions, PUT /tool-permissions/bulk. JWT auth (Reader+)
    - _Requirements: 3.2, 3.3, 3.6_
  - [x] 5.2 Đăng ký routes trong `ChatRoutes.kt`
    - _Requirements: 3.2_
  - [x] 5.3 Cập nhật `ServerModule.kt`: đăng ký `UserToolPermissionRepository` + `UserToolPermissionService` trong Koin
    - _Requirements: 3.1_

- [x] 6. Cập nhật McpAgenticLoop — Per-user permission check
  - [x] 6.1 Cập nhật `McpAgenticLoop.execute()`: thêm params `userId` + `permService`. Trong `executeToolWithLocalRouting()`, check `isEnabled()` trước khi execute. Disabled → skip với message "Tool disabled by user"
    - _Requirements: 1.2, 6.1, 6.3_
  - [x] 6.2 Xóa logic `isToolAutoApproved()` cũ (global autoApprove check), thay bằng per-user `isEnabled()`
    - _Requirements: 6.1_
  - [x] 6.3 Write property test: Disabled tools skipped in agentic loop (Property 2)
    - **Validates: Requirements 1.2, 6.1**

- [x] 7. Cập nhật ChatServiceImpl — System prompt filter + wiring
  - [x] 7.1 Cập nhật `ChatServiceImpl.buildMcpToolsContext()`: filter disabled tools khỏi system prompt injection. Chỉ inject tools có trạng thái `enabled` cho user hiện tại
    - _Requirements: 1.3, 6.4_
  - [x] 7.2 Inject `UserToolPermissionService` vào `ChatServiceImpl`, truyền `userId` + `permService` vào `McpAgenticLoop.execute()`
    - _Requirements: 6.3_
  - [x] 7.3 Cập nhật `ServerModule.kt`: thêm `userToolPermissionService` vào constructor `ChatServiceImpl`
    - _Requirements: 6.3_
  - [x] 7.4 Write property test: Disabled tools not in system prompt (Property 1)
    - **Validates: Requirements 1.3, 6.4**

- [x] 8. Checkpoint — Backend hoàn chỉnh

- [x] 9. Frontend — ChatToolPermissions.kt (AI Chat Sidebar)
  - [x] 9.1 Cập nhật `ai-chat-sidebar.html`: thêm section "� Tool Permissions" (collapsible)
    - _Requirements: 4.1, 4.7_
  - [x] 9.2 Tạo `frontend/src/jsMain/kotlin/com/assistant/frontend/components/chat/ChatToolPermissions.kt`: load permissions, render grouped by server, toggle switch per-tool, bulk enable/disable
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - [x] 9.3 Hiển thị counter "X / Y enabled" per server group + label "(default)"
    - _Requirements: 4.5, 4.6_

- [x] 10. Frontend — Cập nhật McpToolsSection.kt (Integrations Page)
  - [x] 10.1 Cập nhật `loadAutoApproveList()` → gọi `GET /api/chat/tool-permissions` per-user
    - _Requirements: 5.2_
  - [x] 10.2 Cập nhật `toggleAutoApprove()` → gọi `PUT /api/chat/tool-permissions` per-user
    - _Requirements: 5.1_
  - [x] 10.3 Cập nhật bulk actions → gọi `PUT /api/chat/tool-permissions/bulk` với `enable_all`/`disable_all`
    - _Requirements: 5.3_
  - [x] 10.4 Cập nhật tooltip: "Enabled — AI có thể sử dụng" / "Disabled — AI không thể sử dụng"
    - _Requirements: 5.4_
  - [x] 10.5 Write property test: Bulk update applies to all tools (Property 7)
    - **Validates: Requirements 3.6**

- [x] 11. Frontend — Cập nhật McpServerCards.kt badge
  - [x] 11.1 Cập nhật `createBuiltinBadge()`: đổi "Built-in" → "LOCAL", class → `local-kb-type-badge`
    - _Requirements: 2.1, 2.3_

- [x] 12. Checkpoint — Frontend hoàn chỉnh

- [x] 13. Final checkpoint — Per-User Tool Permissions
  - Verify: per-user permissions CRUD, enable/disable flow, system prompt filter, fallback (all enabled), badge LOCAL, UI đồng bộ.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Mỗi task tham chiếu requirements cụ thể để truy vết
- Code tuân thủ: max 200 dòng/file, max 20 dòng/hàm
- Mô hình đơn giản: enabled/disabled, không có confirmation flow
