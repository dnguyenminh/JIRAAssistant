# User Management — Gap Analysis & Feature Proposals

**Ngày phân tích:** 2026-05-01
**Ứng dụng:** Jira Assistant — Ticket Intelligence Platform
**Trang:** User Management (`localhost:3000/#user_management`)

---

## 1. Phân tích hiện trạng (AS-IS)

### 1.1 Chức năng hiện có

| # | Chức năng | Frontend | Backend API | Trạng thái |
|---|-----------|----------|-------------|------------|
| 1 | Hiển thị danh sách users | ✅ `UserManagementPage.kt` | ✅ `GET /api/users` | Hoạt động |
| 2 | Thay đổi role user (dropdown) | ✅ `UserRoleChanger.kt` | ✅ `PUT /api/users/{id}/role` | Hoạt động |
| 3 | Toggle permissions (4 toggles) | ✅ `UserPermissionPanel.kt` | ✅ `PUT /api/users/{id}/permissions` | Hoạt động |
| 4 | Audit log console | ✅ `UserAuditLog.kt` | ✅ `GET /api/users/audit-log` | Hoạt động |
| 5 | Access denied cho non-admin | ✅ HTML template | ✅ Permission check | Hoạt động |
| 6 | Retry khi load users thất bại | ✅ Error state + retry button | — | Hoạt động |

### 1.2 Kiến trúc hiện tại

**Frontend:**
- `UserManagementPage.kt` — Main page controller (render, load users, create user rows)
- `UserRoleChanger.kt` — Role change via API
- `UserPermissionPanel.kt` — Permission toggle panel (4 hardcoded toggles)
- `UserAuditLog.kt` — Audit log console display
- `user-management.html` — HTML template
- `user-management.css` — Page-specific CSS

**Backend:**
- `UserRoutes.kt` — 4 endpoints: GET users, GET audit-log, PUT role, PUT permissions
- `UserMgmtModule.kt` — Koin DI module (empty, depends on aggregator)
- `RBACEngine` — Role/permission management
- `UserStore` — In-memory user persistence (interface ready for DB)
- `AuditLogStore` — In-memory audit log (interface ready for DB)
- `PermissionMatrix` — Hardcoded role-permission mapping

**RBAC Model:**
- 3 roles: `ADMINISTRATOR`, `NEURAL_ARCHITECT`, `READER`
- 12 permissions: VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS, ANALYZE_AI, VIEW_KB, RE_ANALYZE, CONFIG_INTEGRATIONS, TEST_PROVIDER, MANAGE_USERS, TOGGLE_PERMISSIONS, SIGN_OUT, MANAGE_SETTINGS

### 1.3 Hạn chế chính

| # | Hạn chế | Mức độ | Mô tả |
|---|---------|--------|-------|
| 1 | Không có CRUD user đầy đủ | Critical | Không thể tạo, xóa, enable/disable user |
| 2 | Không có search/filter users | High | Với nhiều users sẽ không tìm được |
| 3 | Không có user profile/detail view | High | Chỉ thấy tên + email + role trên 1 dòng |
| 4 | Permission toggles hardcoded | High | Chỉ 4 toggles cố định, không phản ánh 12 permissions thực tế |
| 5 | Audit log không có filter/search | Medium | Chỉ hiển thị raw console, không filter theo user/action/time |
| 6 | Không có bulk operations | Medium | Phải thay đổi role/permission từng user một |
| 7 | Không có session management | Medium | Không thấy active sessions, không force logout |
| 8 | Không có password/auth management | Medium | Không reset password, không manage auth methods |
| 9 | Không có user activity tracking | Low | Không biết user nào đang online, last login |
| 10 | Không có export audit log | Low | Không export CSV/PDF cho compliance |
| 11 | Không có role management UI | Low | Roles hardcoded, không tạo custom roles |
| 12 | Không có invitation system | Low | Không invite user mới qua email |

---

## 2. Đề xuất chức năng cần bổ sung (TO-BE)

### 2.1 Nhóm 1: User CRUD & Profile (Must-have) ⭐⭐⭐

**Mục tiêu:** Quản lý vòng đời user đầy đủ — tạo, xem, sửa, vô hiệu hóa.

| ID | Chức năng | Mô tả | API cần thêm |
|----|-----------|-------|--------------|
| F1.1 | **Create User** | Form tạo user mới (name, email, role, initial permissions) | `POST /api/users` |
| F1.2 | **User Detail Panel** | Panel chi tiết khi click user: avatar, info, role, all permissions, activity summary | Enhance `GET /api/users/{id}` |
| F1.3 | **Edit User Info** | Sửa display name, email | `PUT /api/users/{id}` |
| F1.4 | **Disable/Enable User** | Vô hiệu hóa user (soft delete) thay vì xóa | `PUT /api/users/{id}/status` |
| F1.5 | **Delete User** | Xóa user (với confirmation dialog) | `DELETE /api/users/{id}` |

**UI Concept:**
```
┌─────────────────────────────────────────────────────────┐
│ USER DIRECTORY                          [+ Add User]    │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 🔍 Search users...              [Filter ▾] [Sort ▾]│ │
│ ├─────────────────────────────────────────────────────┤ │
│ │ 👤 Admin User    admin@co.com    ADMINISTRATOR  ●  │ │
│ │ 👤 John Doe      john@co.com     NEURAL_ARCH    ●  │ │
│ │ 👤 Jane Smith    jane@co.com     READER         ○  │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

### 2.2 Nhóm 2: Search, Filter & Pagination (Must-have) ⭐⭐⭐

**Mục tiêu:** Tìm kiếm và lọc users hiệu quả khi số lượng tăng.

| ID | Chức năng | Mô tả |
|----|-----------|-------|
| F2.1 | **Search bar** | Tìm theo name, email (client-side filter cho <100 users, server-side cho >100) |
| F2.2 | **Filter by role** | Dropdown filter: All / Administrator / Neural Architect / Reader |
| F2.3 | **Filter by status** | Active / Disabled / All |
| F2.4 | **Sort options** | Sort by name, email, role, last activity |
| F2.5 | **User count badge** | Hiển thị "Showing 5 of 12 users" |

---

### 2.3 Nhóm 3: Full Permission Management (Must-have) ⭐⭐⭐

**Mục tiêu:** Hiển thị và quản lý đầy đủ 12 permissions thay vì 4 hardcoded toggles.

| ID | Chức năng | Mô tả |
|----|-----------|-------|
| F3.1 | **Dynamic permission list** | Load permissions từ backend thay vì hardcode 4 toggles |
| F3.2 | **Permission groups** | Nhóm permissions theo category: View, Action, Admin |
| F3.3 | **Role-based defaults indicator** | Hiển thị permissions nào là mặc định của role (inherited) vs custom |
| F3.4 | **Permission conflict warning** | Cảnh báo khi toggle permission mâu thuẫn với role |
| F3.5 | **Bulk permission update** | Set nhiều permissions cùng lúc |

**UI Concept — Permission Panel cải tiến:**
```
┌─────────────────────────────────────────────────────────┐
│ PERMISSIONS — John Doe (NEURAL_ARCHITECT)               │
│                                                         │
│ 📊 View Permissions                                     │
│   ☑ View Dashboard        (inherited from role)         │
│   ☑ View Graph            (inherited from role)         │
│   ☑ View Analysis         (inherited from role)         │
│   ☑ View Knowledge Base   (inherited from role)         │
│                                                         │
│ ⚡ Action Permissions                                    │
│   ☑ Trigger AI Scan       (inherited from role)         │
│   ☑ Re-Analyze            (inherited from role)         │
│   ☑ Test Provider         (inherited from role)         │
│   ☐ Config Integrations   (custom override)      [🔓]  │
│   ☐ Export Data            (custom override)      [🔓]  │
│                                                         │
│ 🔐 Admin Permissions                                    │
│   ☐ Manage Users          (not in role)                 │
│   ☐ Manage Settings       (not in role)                 │
│   ☐ Toggle Permissions    (not in role)                 │
└─────────────────────────────────────────────────────────┘
```

---

### 2.4 Nhóm 4: Enhanced Audit Log (Should-have) ⭐⭐

**Mục tiêu:** Audit log chuyên nghiệp với filter, search, export cho compliance.

| ID | Chức năng | Mô tả | API cần thêm |
|----|-----------|-------|--------------|
| F4.1 | **Filter by action type** | Filter: ROLE_CHANGE, IAM_SYNC, USER_LOGIN, USER_CREATED, etc. | Query param `?action=` |
| F4.2 | **Filter by user** | Xem audit log của 1 user cụ thể | Query param `?userId=` |
| F4.3 | **Filter by date range** | Chọn khoảng thời gian | Query params `?from=&to=` |
| F4.4 | **Search audit log** | Full-text search trong audit entries | Query param `?q=` |
| F4.5 | **Pagination** | Phân trang cho audit log lớn | Query params `?page=&size=` |
| F4.6 | **Export audit log** | Export CSV/JSON cho compliance reporting | `GET /api/users/audit-log/export` |
| F4.7 | **Audit log detail view** | Click entry để xem chi tiết (old value → new value) | — |
| F4.8 | **Real-time updates** | WebSocket hoặc polling cho live audit feed | WebSocket `/ws/audit` |

**UI Concept — Enhanced Audit Log:**
```
┌─────────────────────────────────────────────────────────┐
│ AUDIT LOG                                    [Export ▾] │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 🔍 Search...  [Action ▾] [User ▾] [Date range 📅] │ │
│ ├─────────────────────────────────────────────────────┤ │
│ │ [10:23:45] ROLE_CHANGE  admin changed John → ADMIN  │ │
│ │ [10:20:12] IAM_SYNC     KB_WRITE enabled for Jane   │ │
│ │ [09:55:00] USER_LOGIN   admin@co.com logged in      │ │
│ │ [09:30:15] USER_CREATED john@co.com created          │ │
│ ├─────────────────────────────────────────────────────┤ │
│ │                    Page 1 of 5  [< 1 2 3 4 5 >]    │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

### 2.5 Nhóm 5: User Activity & Session Management (Should-have) ⭐⭐

**Mục tiêu:** Theo dõi hoạt động user và quản lý sessions.

| ID | Chức năng | Mô tả | API cần thêm |
|----|-----------|-------|--------------|
| F5.1 | **Online status indicator** | Hiển thị user đang online/offline (green/gray dot) | `GET /api/users/online` |
| F5.2 | **Last login time** | Hiển thị thời gian login gần nhất | Enhance User model |
| F5.3 | **Active sessions list** | Xem danh sách sessions đang active của 1 user | `GET /api/users/{id}/sessions` |
| F5.4 | **Force logout** | Admin force logout 1 user (invalidate JWT) | `POST /api/users/{id}/logout` |
| F5.5 | **Login history** | Lịch sử login (IP, browser, time) | `GET /api/users/{id}/login-history` |

---

### 2.6 Nhóm 6: Bulk Operations (Should-have) ⭐⭐

**Mục tiêu:** Thao tác hàng loạt cho admin quản lý nhiều users.

| ID | Chức năng | Mô tả |
|----|-----------|-------|
| F6.1 | **Multi-select users** | Checkbox để chọn nhiều users |
| F6.2 | **Bulk role change** | Đổi role cho nhiều users cùng lúc |
| F6.3 | **Bulk enable/disable** | Enable/disable nhiều users |
| F6.4 | **Bulk permission update** | Set permissions cho nhiều users |
| F6.5 | **Select all / Deselect all** | Quick select controls |

---

### 2.7 Nhóm 7: Role Management (Nice-to-have) ⭐

**Mục tiêu:** Quản lý roles linh hoạt thay vì hardcoded.

| ID | Chức năng | Mô tả | API cần thêm |
|----|-----------|-------|--------------|
| F7.1 | **View role details** | Xem permissions mặc định của mỗi role | `GET /api/roles` |
| F7.2 | **Role comparison** | So sánh permissions giữa 2 roles | — (client-side) |
| F7.3 | **Custom role creation** | Tạo role mới với custom permissions | `POST /api/roles` |
| F7.4 | **Role permission matrix** | Bảng matrix hiển thị role × permission | — (client-side) |

---

### 2.8 Nhóm 8: Invitation & Onboarding (Nice-to-have) ⭐

**Mục tiêu:** Quy trình invite và onboard user mới.

| ID | Chức năng | Mô tả | API cần thêm |
|----|-----------|-------|--------------|
| F8.1 | **Invite user via email** | Gửi invitation link qua email | `POST /api/users/invite` |
| F8.2 | **Pending invitations list** | Danh sách invitations chưa accept | `GET /api/users/invitations` |
| F8.3 | **Resend invitation** | Gửi lại invitation | `POST /api/users/invite/{id}/resend` |
| F8.4 | **Revoke invitation** | Hủy invitation | `DELETE /api/users/invite/{id}` |

---

### 2.9 Nhóm 9: UI/UX Improvements (Should-have) ⭐⭐

**Mục tiêu:** Cải thiện trải nghiệm sử dụng tổng thể.

| ID | Chức năng | Mô tả |
|----|-----------|-------|
| F9.1 | **User stats summary** | Cards tổng quan: Total Users, Active, By Role |
| F9.2 | **Confirmation dialogs** | Confirm trước khi thay đổi role/permission/delete |
| F9.3 | **Toast notifications** | Success/error toast cho mọi thao tác |
| F9.4 | **Responsive layout** | Layout responsive cho tablet/mobile |
| F9.5 | **Keyboard shortcuts** | Ctrl+N (new user), Ctrl+F (search), etc. |
| F9.6 | **Empty state improvement** | Better empty states với illustrations |
| F9.7 | **Loading skeletons** | Skeleton loading thay vì text "Loading..." |

---

## 3. Tổng hợp ưu tiên

### Must-have (MVP — Cần triển khai ngay) ⭐⭐⭐

| Priority | Feature Group | Effort | Impact |
|----------|--------------|--------|--------|
| P1 | **F1: User CRUD & Profile** | Medium | Rất cao — không có thì không quản lý được users |
| P2 | **F3: Full Permission Management** | Medium | Rất cao — hiện chỉ 4/12 permissions |
| P3 | **F2: Search, Filter & Pagination** | Low | Cao — cần thiết khi >10 users |

### Should-have (Phase 2 — Triển khai sau MVP) ⭐⭐

| Priority | Feature Group | Effort | Impact |
|----------|--------------|--------|--------|
| P4 | **F4: Enhanced Audit Log** | Medium | Cao — cần cho compliance |
| P5 | **F9: UI/UX Improvements** | Low-Medium | Cao — cải thiện UX tổng thể |
| P6 | **F5: User Activity & Sessions** | Medium | Trung bình — security monitoring |
| P7 | **F6: Bulk Operations** | Low | Trung bình — tiết kiệm thời gian admin |

### Nice-to-have (Phase 3 — Khi có thời gian) ⭐

| Priority | Feature Group | Effort | Impact |
|----------|--------------|--------|--------|
| P8 | **F7: Role Management** | High | Thấp — 3 roles hiện tại đủ dùng |
| P9 | **F8: Invitation & Onboarding** | High | Thấp — có thể tạo user trực tiếp |

---

## 4. Đề xuất Implementation Roadmap

### Phase 1: MVP (Sprint 1-2)
1. **F1.1-F1.5** — User CRUD (Create, Read detail, Edit, Disable, Delete)
2. **F3.1-F3.3** — Dynamic permissions từ backend, grouped by category
3. **F2.1-F2.2** — Search bar + role filter
4. **F9.1-F9.3** — Stats summary, confirmation dialogs, toast notifications

### Phase 2: Enhanced (Sprint 3-4)
5. **F4.1-F4.6** — Audit log filters, search, pagination, export
6. **F2.3-F2.5** — Status filter, sort, user count
7. **F3.4-F3.5** — Permission conflict warning, bulk permission update
8. **F5.1-F5.2** — Online status, last login
9. **F9.4-F9.7** — Responsive, keyboard shortcuts, skeletons

### Phase 3: Advanced (Sprint 5+)
10. **F5.3-F5.5** — Session management, force logout, login history
11. **F6.1-F6.5** — Bulk operations
12. **F7.1-F7.4** — Role management
13. **F8.1-F8.4** — Invitation system

---

## 5. Backend API Changes Required

### New Endpoints Needed

| Method | Path | Description | Phase |
|--------|------|-------------|-------|
| `POST` | `/api/users` | Create new user | Phase 1 |
| `GET` | `/api/users/{id}` | Get user detail | Phase 1 |
| `PUT` | `/api/users/{id}` | Update user info | Phase 1 |
| `PUT` | `/api/users/{id}/status` | Enable/disable user | Phase 1 |
| `DELETE` | `/api/users/{id}` | Delete user | Phase 1 |
| `GET` | `/api/permissions` | List all permissions with metadata | Phase 1 |
| `GET` | `/api/roles` | List roles with default permissions | Phase 1 |
| `GET` | `/api/users/audit-log/export` | Export audit log (CSV/JSON) | Phase 2 |
| `GET` | `/api/users/online` | Get online users | Phase 2 |
| `GET` | `/api/users/{id}/sessions` | Get user sessions | Phase 3 |
| `POST` | `/api/users/{id}/logout` | Force logout user | Phase 3 |
| `GET` | `/api/users/{id}/login-history` | Get login history | Phase 3 |
| `POST` | `/api/users/invite` | Send invitation | Phase 3 |

### Model Changes Needed

```kotlin
// Enhanced User model
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val avatarUrl: String? = null,
    val customPermissions: Set<Permission> = emptySet(),
    // New fields:
    val status: UserStatus = UserStatus.ACTIVE,      // Phase 1
    val createdAt: String? = null,                    // Phase 1
    val lastLoginAt: String? = null,                  // Phase 2
    val isOnline: Boolean = false                     // Phase 2
)

enum class UserStatus { ACTIVE, DISABLED, PENDING }

// Permission metadata for dynamic UI
data class PermissionInfo(
    val key: String,           // "ANALYZE_AI"
    val label: String,         // "Trigger AI Scan"
    val group: String,         // "Action"
    val description: String    // "Allows user to trigger AI analysis on tickets"
)
```

---

## 6. Kết luận

Trang User Management hiện tại chỉ đáp ứng **~30%** chức năng cần thiết cho một hệ thống RBAC hoàn chỉnh. Các chức năng cốt lõi đang thiếu:

1. **Không thể tạo/xóa user** — chỉ có thể thay đổi role/permission cho users đã tồn tại
2. **Permission management không đầy đủ** — chỉ 4/12 permissions được hiển thị
3. **Không có search/filter** — không scalable khi số users tăng
4. **Audit log quá đơn giản** — không filter, không search, không export

Đề xuất ưu tiên triển khai **Phase 1 (MVP)** trước với 3 nhóm chức năng Must-have, ước tính **2 sprints** để hoàn thành.
