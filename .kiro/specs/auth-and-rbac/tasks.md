# Auth Flow & RBAC — Tasks

Status: ✅ All completed

---

## Task 2: Auth Service & JWT Authentication (from tasks-01-backend-core.md)

- [x] 2.1 Tạo `shared/.../auth/AuthService.kt` interface và `AuthServiceImpl.kt` với authenticate(), generateJwt(), validateJwt(), invalidateSession()
- [x] 2.2 Triển khai generateJwt() tạo JWT token chứa user_id, email, role, project_key với thời hạn 24 giờ sử dụng HMAC256
- [x] 2.3 Triển khai validateJwt() giải mã và xác thực JWT token, trả về AuthenticatedUser hoặc null
- [x] 2.4 Triển khai authenticate() gọi Jira REST API `/rest/api/3/project` để xác thực credentials và lấy danh sách projects
- [x] 2.5 Tạo `server/.../routes/AuthRoutes.kt` với POST /api/auth/login và POST /api/auth/logout
- [x] 2.6 Cấu hình JWT middleware trong Ktor Authentication plugin, đọc JWT_SECRET từ biến môi trường
- [x] 2.7 ✅ Viết property test cho Property 1: JWT Generation/Validation Round-Trip (Kotest)

## Task 3: RBAC Engine — Phân quyền dựa trên Vai trò (from tasks-01-backend-core.md)

- [x] 3.1 Tạo `shared/.../rbac/RBACEngine.kt` interface, `UserRole` enum, `Permission` enum, và `PermissionMatrix` object
- [x] 3.2 Triển khai `RBACEngineImpl.kt` với hasPermission(), getPermissions(), changeRole(), togglePermission()
- [x] 3.3 Tạo `server/.../middleware/RBACMiddleware.kt` — Ktor route interceptor kiểm tra quyền trước khi xử lý request
- [x] 3.4 Triển khai audit log: mỗi thay đổi role/permission ghi AuditLogEntry vào database
- [x] 3.5 Tạo `server/.../routes/UserRoutes.kt` với GET /api/users, PUT /api/users/{userId}/role, PUT /api/users/{userId}/permissions
- [x] 3.6 ✅ Viết property test cho Property 2: RBAC Permission Matrix Enforcement (Kotest)
- [x] 3.7 ✅ Viết property test cho Property 3: RBAC Audit Log Completeness (Kotest)

## Task 4: Knowledge Base — SQLDelight Persistence Layer (from tasks-01-backend-core.md, auth-related: users table)

- [x] 4.1 Tạo SQLDelight schema file với tables: kb_records, graph_data, users, audit_log, provider_configs

---

## Task 47: Backend — Jira Credentials trong provider_configs (from tasks-05-refactor-onboarding.md)

- [x] 47.1 Thêm endpoint `PUT /api/integrations/jira/config` — validate + save encrypted credentials
- [x] 47.2 Thêm endpoint `GET /api/integrations/jira/status` (public)
- [x] 47.3 Tạo/cập nhật `JiraCredentialsService` — đọc từ provider_configs, decrypt apiToken
- [x] 47.4 Cập nhật JiraClient Koin registration — singleton đọc credentials từ DB
- [x] 47.5 Cập nhật `ProjectRoutes.kt` — dùng inject JiraClient từ Koin
- [x] 47.6 Cập nhật `GET /api/settings/status` — kiểm tra Jira configured từ provider_configs

## Task 49: Frontend — Bỏ Onboarding, First-Launch Redirect (from tasks-05-refactor-onboarding.md)

- [x] 49.1 Cập nhật `App.kt` — bỏ onboarding, redirect Integrations khi Jira chưa configured
- [x] 49.2 Cập nhật `Router.kt` — bỏ "onboarding" khỏi standaloneRoutes
- [x] 49.3 Cập nhật `Sidebar.kt` — không cần thay đổi

## Task 50: Backend — Đơn giản hóa AuthRoutes (from tasks-05-refactor-onboarding.md)

- [x] 50.1 Đơn giản hóa `POST /api/auth/login` — không cần Jira credentials
- [x] 50.2 Cập nhật `AuthServiceImpl` — bỏ Jira API call trong authenticate()

## Task 51: Cleanup — Xóa code Onboarding cũ (from tasks-05-refactor-onboarding.md)

- [x] 51.1 Xóa `OnboardingPage.kt`
- [x] 51.2 Xóa `onboarding.html`
- [x] 51.3 Cập nhật `Navbar.kt` — Sign Out redirect login thay vì onboarding

---

## Task 53: Login & Project Selection (from tasks-06-post-refactor.md)

- [x] 53.1 Tạo `LoginPage.kt` — standalone, glass-card, 2 default users
    - _Requirements: AC 1.1, AC 1.2, AC 1.3, AC 10.1, AC 10.8, [design-system-ux] AC 17.1_
- [x] 53.2 Cập nhật `AuthServiceImpl.kt` — admin/admin123, user/user123, local auth
    - _Requirements: AC 1.2, AC 10.1, AC 10.7_
- [x] 53.3 Tạo `ProjectSelectPage.kt` — standalone, project grid, empty state
    - _Requirements: AC 1.4, AC 1.5, AC 1.6, [cross-cutting] AC 13.1, [cross-cutting] AC 13.8_
- [x] 53.4 Cập nhật `App.kt` — luồng Login → Project Select → Dashboard
    - _Requirements: AC 1.7, AC 1.8, [cross-cutting] AC 13.7_
- [x] 53.5 Cập nhật `Router.kt` — login, project_select là standalone routes
    - _Requirements: AC 1.1, [cross-cutting] AC 13.7_
- [x] 53.6 Cập nhật `ApiClient.kt` — saveProjectKey, getToken, postUnauthenticated
    - _Requirements: AC 1.4, AC 1.9, [cross-cutting] AC 13.1_
- [x] 53.7 Cập nhật `Navbar.kt` — project badge [PROJ], Sign Out → #login
    - _Requirements: AC 1.11, [dashboard] AC 2.7, [cross-cutting] AC 13.9_

## Task 56: Backend Fixes — RBAC (from tasks-06-post-refactor.md)

- [x] 56.1 RBAC Middleware: ApplicationCallPipeline.Call phase — _Requirements: AC 11.1, AC 10.3_
