# User Management — Master Requirements

## Tổng quan

Domain User Management bao gồm trang quản lý người dùng với hệ thống RBAC (Role-Based Access Control) phân quyền chi tiết. Administrator có thể quản lý vai trò (Administrator, Neural_Architect, Reader) và quyền hạn cụ thể (Trigger AI Scan, KB Write, Update Integrations, Export Neural Data) cho từng user. Hệ thống ghi nhật ký audit log cho mọi thay đổi quyền, hiển thị trong Neural Console.

Bugfix đã giải quyết các vấn đề nghiêm trọng: field name mismatch giữa backend/frontend khiến user directory không hiển thị, audit log chỉ lưu in-memory (mất khi restart), và thiếu GET endpoint để frontend fetch audit history từ server.

**Bug đã fix:** `InMemoryUserStore` trước đây được tạo trống trong `CoreModule.kt`. Đã fix bằng cách seed default users khi startup và register user on login. `GET /api/users` giờ trả về danh sách users đầy đủ.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `user-management` | Feature | ✅ Archived | Trang User Management — RBAC & phân quyền |
| `user-management-audit-fix` | Bugfix | ✅ Archived | Fix user directory display + audit log persistence |
| `user-management-empty-page` | Bugfix | ✅ Completed | Fix UserStore trống — seed default users + register on login |

## Requirements tổng hợp

### RBAC & Vai trò

- Hỗ trợ 3 vai trò: **Administrator** (toàn quyền), **Neural_Architect** (phân tích AI + ghi KB + xem dashboard), **Reader** (chỉ xem dashboard và đồ thị)
- Administrator thay đổi vai trò user qua dropdown, áp dụng ngay lập tức
- Non-Administrator bị từ chối truy cập trang User Management (HTTP 403)
- Truy cập trang qua User Profile Dropdown (Account Settings), không qua sidebar

### User Directory

- Hiển thị danh sách người dùng: avatar (48px), tên, email, vai trò (dropdown selector)
- Backend `UserDto` fields (`id`, `name`) mapping chính xác sang frontend `UserInfo` (`userId`, `displayName`)
- Loading state khi fetch users, error với retry button khi API fail
- **[FIXED]** `InMemoryUserStore` phải được seed với default users khi server khởi động
- **[FIXED]** User đăng nhập thành công phải được đăng ký vào `UserStore` nếu chưa tồn tại

### Permission Toggles

- Panel quyền hạn chi tiết với 4 toggle: Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data
- Toggle hiển thị "IAM SYNC: UPDATING..." trên sidebar ticker, animate progress bar, "SYNC COMPLETE" khi hoàn tất
- Permission toggles panel hiển thị khi chọn user từ directory

### Audit Log & Neural Console

- Ghi nhật ký audit log: người thực hiện, người bị thay đổi, quyền cũ/mới, timestamp
- Neural Console hiển thị audit entries: timestamp, tag (IAM_SYNC, USER_LOGIN), mô tả
- Backend expose `GET /api/users/audit-log` endpoint để frontend fetch audit history
- Audit log lưu persistent (file-based hoặc database) thay vì in-memory
- Frontend model `AuditLogEntry` đồng bộ fields với backend (actorId, targetUserId, tag)
- Khi page load, fetch recent audit entries từ backend và hiển thị trong Neural Console

### API Endpoints

- `GET /api/users` — danh sách users (id, name, email, role, permissions)
- `PUT /api/users/{userId}/role` — thay đổi vai trò user (Administrator only)
- `PUT /api/users/{userId}/permissions` — toggle permission cụ thể (Administrator only)
- `GET /api/users/audit-log` — fetch audit log entries từ persistent storage
- Tất cả endpoints yêu cầu JWT authentication
- Non-Administrator truy cập → HTTP 403

### Data Model

- `UserDto` (backend): id, name, email, role, permissions
- `UserInfo` (frontend): userId, displayName, email, role, permissions — mapping chính xác với backend
- `AuditLogEntry`: actorId, targetUserId, action, oldValue, newValue, tag, timestamp — đồng bộ backend/frontend
- Persistent audit storage (file-based hoặc database) thay vì InMemoryAuditLogStore

### UI Components

- User Directory: danh sách users với avatar, tên, email, role dropdown
- Permission Panel: 4 toggles với IAM SYNC animation
- Neural Console: audit log entries với timestamp, tag, mô tả
- Access Denied view (403) cho non-Administrator
- Loading state + error with retry button

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `user-management-audit-fix` | Fix field name mismatch UserDto↔UserInfo, thêm GET audit-log endpoint, chuyển audit storage sang persistent, đồng bộ AuditLogEntry model giữa backend/frontend |
| `user-management-empty-page` | Fix InMemoryUserStore trống — seed default users khi startup, register user on login, để GET /api/users trả về danh sách users |

## Open Issues

Không có bug đang mở.

---

## Planned Enhancements (SCRUM-49)

> Gap Analysis: `documents/USER-MANAGEMENT-GAP-ANALYSIS.md`
> Epic: SCRUM-49 — User Management Enhancement — Full RBAC & UX Overhaul

Trang hiện tại chỉ đáp ứng ~30% chức năng cần thiết. Các nhóm chức năng cần bổ sung:

### Phase 1: MVP (Must-have) — Sprint 1-2

#### User CRUD & Profile (SCRUM-50) ⭐⭐⭐
- Administrator có thể tạo user mới (name, email, role, initial permissions) — `POST /api/users`
- Click user hiển thị detail panel: avatar, info, role, all permissions, activity summary — `GET /api/users/{id}`
- Administrator có thể sửa display name, email — `PUT /api/users/{id}`
- Administrator có thể disable/enable user (soft delete) — `PUT /api/users/{id}/status`
- Administrator có thể xóa user với confirmation dialog — `DELETE /api/users/{id}`
- User model mở rộng: thêm `status` (ACTIVE/DISABLED/PENDING), `createdAt`
- Tất cả thao tác CRUD ghi audit log

#### Search, Filter & Pagination (SCRUM-51) ⭐⭐⭐
- Search bar tìm theo name, email (client-side cho <100 users)
- Filter by role: All / Administrator / Neural Architect / Reader
- Filter by status: Active / Disabled / All
- Sort by name, email, role, last activity
- User count badge: "Showing 5 of 12 users"

#### Full Permission Management (SCRUM-52) ⭐⭐⭐
- Dynamic permission list: load đầy đủ 12 permissions từ backend thay vì hardcode 4 toggles
- Permission groups: View (4), Action (5), Admin (3)
- Role-based defaults indicator: hiển thị inherited (từ role) vs custom override
- Permission conflict warning: cảnh báo khi toggle mâu thuẫn với role
- Bulk permission update: set nhiều permissions cùng lúc
- API mới: `GET /api/permissions` (metadata), `GET /api/roles` (roles + default permissions)

### Phase 2: Enhanced (Should-have) — Sprint 3-4

#### Enhanced Audit Log (SCRUM-53) ⭐⭐
- Filter by action type (ROLE_CHANGE, IAM_SYNC, USER_LOGIN, USER_CREATED)
- Filter by user, date range
- Full-text search trong audit entries
- Pagination cho audit log lớn
- Export CSV/JSON cho compliance — `GET /api/users/audit-log/export`
- Detail view: click entry xem old value → new value
- Real-time updates via WebSocket hoặc polling

#### User Activity & Session Management (SCRUM-54) ⭐⭐
- Online status indicator (green/gray dot)
- Last login time hiển thị trong user list
- Active sessions list — `GET /api/users/{id}/sessions`
- Force logout — `POST /api/users/{id}/logout`
- Login history (IP, browser, time) — `GET /api/users/{id}/login-history`

#### Bulk Operations (SCRUM-55) ⭐⭐
- Multi-select users (checkbox)
- Bulk role change, bulk enable/disable
- Select all / Deselect all

#### UI/UX Improvements (SCRUM-56) ⭐⭐
- User stats summary cards: Total Users, Active, By Role
- Confirmation dialogs trước destructive actions
- Toast notifications cho success/error
- Loading skeletons thay vì text "Loading..."

### Phase 3: Advanced (Nice-to-have) — Sprint 5+

#### Custom Role Management (SCRUM-57) ⭐
- View role details, role comparison
- Custom role creation — `POST /api/roles`
- Role × Permission matrix

#### User Invitation & Onboarding (SCRUM-58) ⭐
- Invite user via email — `POST /api/users/invite`
- Pending invitations list, resend, revoke
