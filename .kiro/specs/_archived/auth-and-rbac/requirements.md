# Auth Flow & RBAC — Requirements

---

## Yêu cầu 1: Luồng Xác thực & Chọn Dự án (Login → Project Select → Dashboard)

**User Story:** Là một người dùng, tôi muốn đăng nhập bằng username/password, chọn dự án Jira, và được chuyển đến Dashboard — để truy cập hệ thống một cách an toàn và nhanh chóng.

### Mô tả

Hệ thống sử dụng luồng xác thực 3 bước thay vì Onboarding:

1. **Login Page** — Trang đăng nhập standalone (không có Shell/sidebar/navbar). Người dùng nhập username + password. Hệ thống có 2 tài khoản mặc định: `admin/admin123` (ADMINISTRATOR) và `user/user123` (READER). Đăng nhập thành công → tạo JWT token.
2. **Project Selection Page** — Trang chọn dự án render trong Shell (có sidebar/navbar/account dropdown). Hiển thị grid các Jira projects từ `GET /api/projects`. Người dùng chọn 1 project → lưu project key vào sessionStorage. Khi chưa có project key, navbar hiện badge "Select Project" để navigate đến trang này.
3. **Dashboard** — Sau khi có JWT + project key → hiển thị Dashboard trong Shell.

**First-Launch Flow:**
- Nếu chưa có JWT → redirect `#login`
- Nếu có JWT nhưng chưa chọn project → redirect `#project_select`
- Nếu có JWT + project nhưng Jira chưa cấu hình + user là Admin → redirect `#integrations`
- Nếu có JWT + project nhưng Jira chưa cấu hình + user không phải Admin → toast + Dashboard (dữ liệu trống)
- Nếu có JWT + project + Jira đã cấu hình → Dashboard bình thường

**Onboarding đã bị loại bỏ** — không còn trang Onboarding riêng biệt.

### Tiêu chí chấp nhận

1. THE Frontend_App SHALL hiển thị trang Login standalone (không Shell) với 2 trường: USERNAME (text input) và PASSWORD (password input), nút "SIGN IN", và khu vực hiển thị lỗi. Thiết kế Obsidian Kinetic: glass-card trên nền living-void
2. WHEN người dùng nhập username/password hợp lệ và nhấn "SIGN IN", THE Backend_Server SHALL xác thực credentials từ danh sách user mặc định (admin/admin123 → ADMINISTRATOR, user/user123 → READER), tạo JWT token (24 giờ), và trả về JWT + user info (role, email)
3. IF credentials không hợp lệ hoặc trống, THEN THE Backend_Server SHALL trả về HTTP 401 với message "Invalid username or password"
4. WHEN đăng nhập thành công VÀ chưa có project key trong sessionStorage, THE Frontend_App SHALL redirect tới trang Project Selection (`#project_select`)
5. THE Frontend_App SHALL hiển thị trang Project Selection trong Shell (có sidebar/navbar) với bảng (table) các Jira projects từ `GET /api/projects`. Bảng hiển thị các cột: Project Key, Project Name, Type. Bảng hỗ trợ: search/filter theo tên hoặc key, sort theo từng cột (click header), phân trang (20 projects/trang) với nút Previous/Next và hiển thị "Page X of Y". Chiếm toàn bộ chiều rộng màn hình (max-width 1200px). Click vào row → lưu project key vào sessionStorage + redirect `#dashboard`. WHEN chưa có project key trong sessionStorage, navbar project selector SHALL hiển thị badge "Select Project" — click vào navigate đến `#project_select`
6. IF `GET /api/projects` trả về danh sách rỗng, THEN THE Frontend_App SHALL gọi `GET /api/integrations/jira/status` để xác định nguyên nhân. Nếu Jira chưa cấu hình → hiển thị message hướng dẫn cấu hình Jira kèm nút "Go to Integrations" (cho Admin) hoặc message "Please contact an administrator to configure Jira" (cho non-Admin). Nếu Jira đã cấu hình nhưng không có projects → hiển thị empty state với message và nút "RETRY"
7. WHEN ứng dụng khởi động, THE Frontend_App SHALL kiểm tra JWT token trong sessionStorage: nếu null → redirect `#login`, nếu có token nhưng không có project key → redirect `#project_select`, nếu có cả hai → kiểm tra Jira status rồi navigate
8. WHEN ứng dụng khởi động với JWT + project key, THE Frontend_App SHALL gọi `GET /api/integrations/jira/status` để kiểm tra Jira đã cấu hình chưa. Nếu chưa cấu hình + Admin → redirect `#integrations`. Nếu chưa cấu hình + non-Admin → toast "Please ask an administrator to configure Jira" + Dashboard
9. THE Frontend_App SHALL tạo JWT token (24 giờ) cho user session — JWT chỉ chứa user identity (userId, email, role), không chứa Jira credentials
10. THE Backend_Server SHALL sử dụng Jira credentials đã lưu trong database (bảng `provider_configs`) cho TẤT CẢ API calls tới Jira — không yêu cầu per-user Jira auth
11. WHEN người dùng nhấn "Sign Out", THE Frontend_App SHALL xóa JWT token và project key khỏi sessionStorage, sau đó redirect tới `#login` (không phải onboarding)

---

## Yêu cầu 10: Xác thực JWT & Quản lý Session

**User Story:** Là một Developer, tôi muốn hệ thống xác thực dựa trên JWT token an toàn, để bảo vệ các API endpoint và quản lý phiên đăng nhập người dùng.

### Mô tả

Hệ thống sử dụng xác thực local với 2 tài khoản mặc định thay vì xác thực qua Jira credentials. Người dùng đăng nhập qua trang Login (`#login`) với username/password. JWT token được tạo sau khi đăng nhập thành công và lưu trong sessionStorage.

### Thay đổi so với thiết kế ban đầu:
- **2 default users**: `admin/admin123` (ADMINISTRATOR, admin@assistant.local) và `user/user123` (READER, user@assistant.local) — hardcoded trong `AuthServiceImpl`
- **Không xác thực qua Jira**: `authenticate()` kiểm tra credentials từ danh sách user mặc định, không gọi Jira API
- **Empty credentials → 401**: Nếu username hoặc password trống, trả về HTTP 401
- **Login required**: Tất cả trang đều yêu cầu JWT token. Nếu không có token → redirect `#login`

### Tiêu chí chấp nhận

1. WHEN người dùng gửi POST `/api/auth/login` với username/password hợp lệ, THE Auth_Service SHALL xác thực từ danh sách user mặc định (admin/admin123 → ADMINISTRATOR, user/user123 → READER) và tạo JWT token chứa: user_id, email, role, project_key, với thời hạn 24 giờ
2. THE Frontend_App SHALL gửi JWT token trong header `Authorization: Bearer {token}` cho mọi request đến Backend_Server (trừ endpoint `/api/auth/login`)
3. WHEN Backend_Server nhận request với JWT token hợp lệ, THE Auth_Service SHALL giải mã token và gắn thông tin người dùng vào request context
4. IF JWT token hết hạn hoặc không hợp lệ, THEN THE Auth_Service SHALL trả về mã lỗi 401 và Frontend_App SHALL chuyển hướng về trang Login (`#login`) với thông báo "Phiên đăng nhập đã hết hạn"
5. THE Auth_Service SHALL lưu trữ JWT secret trong biến môi trường, không hardcode trong mã nguồn
6. IF Frontend_App không thể kết nối đến Backend_Server, THEN THE Frontend_App SHALL hiển thị thông báo lỗi "Không thể kết nối đến máy chủ" và cung cấp nút "Thử lại"
7. IF username hoặc password trống trong request POST `/api/auth/login`, THEN THE Auth_Service SHALL trả về HTTP 401 với message "Invalid username or password"
8. THE Frontend_App SHALL yêu cầu đăng nhập trước khi truy cập bất kỳ trang nào. Nếu không có JWT token trong sessionStorage → redirect `#login`

---

## Yêu cầu 11: RBAC Engine — Phân quyền dựa trên Vai trò

**User Story:** Là một Administrator, tôi muốn hệ thống phân quyền chặt chẽ dựa trên vai trò, để chỉ người dùng được ủy quyền mới truy cập được các chức năng tương ứng.

### Tiêu chí chấp nhận

1. THE RBAC_Engine SHALL thực thi ma trận phân quyền sau:
   - Administrator: Xem Dashboard, Xem Graph, Xem Analysis, Phân tích AI, Xem KB, Re-Analyze, Cấu hình Integrations, Test Provider, Quản lý Users, Toggle Permissions, Sign Out
   - Neural_Architect: Xem Dashboard, Xem Graph, Xem Analysis, Phân tích AI, Xem KB, Re-Analyze, Test Provider, Sign Out
   - Reader: Xem Dashboard, Xem Graph, Xem Analysis, Xem KB, Sign Out
2. WHEN người dùng có vai trò Reader cố gắng gọi endpoint phân tích AI hoặc ghi KB, THE RBAC_Engine SHALL từ chối request và trả về mã lỗi 403
3. WHEN người dùng có vai trò Neural_Architect cố gắng truy cập endpoint quản lý users hoặc cấu hình integrations, THE RBAC_Engine SHALL từ chối request và trả về mã lỗi 403
4. THE RBAC_Engine SHALL kiểm tra quyền hạn ở cả Backend_Server (server-side enforcement) và Frontend_App (UI-level disable)
5. WHEN Administrator thay đổi vai trò hoặc quyền hạn, THE RBAC_Engine SHALL áp dụng thay đổi ngay lập tức cho các request tiếp theo của người dùng bị thay đổi
