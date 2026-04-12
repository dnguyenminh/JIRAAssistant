# User Management — Requirements

# Yêu cầu 7: User Management — RBAC & Phân quyền (MH7)

**User Story:** Là một Administrator, tôi muốn quản lý vai trò và quyền hạn chi tiết cho từng người dùng, để kiểm soát chặt chẽ ai được phép thực hiện thao tác nào trong hệ thống.

## Tiêu chí chấp nhận

1. THE Frontend_App SHALL hiển thị danh sách người dùng với: avatar (48px), tên, email, và vai trò hiện tại trong dropdown selector
2. THE RBAC_Engine SHALL hỗ trợ 3 vai trò: Administrator (toàn quyền), Neural_Architect (phân tích AI + ghi KB + xem dashboard), Reader (chỉ xem dashboard và đồ thị)
3. WHEN Administrator thay đổi vai trò người dùng qua dropdown, THE RBAC_Engine SHALL áp dụng thay đổi ngay lập tức
4. THE Frontend_App SHALL hiển thị panel quyền hạn chi tiết với 4 toggle: Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data
5. WHEN một quyền hạn được toggle, THE Frontend_App SHALL hiển thị "IAM SYNC: UPDATING..." trên sidebar ticker, animate progress bar, và hiển thị "SYNC COMPLETE" khi hoàn tất
6. WHEN Administrator thay đổi quyền của người dùng, THE RBAC_Engine SHALL ghi nhật ký audit log bao gồm: người thực hiện, người bị thay đổi, quyền cũ, quyền mới, và timestamp
7. THE Frontend_App SHALL hiển thị Neural_Console ở cuối trang với các mục audit log gồm timestamp, tag (IAM_SYNC, USER_LOGIN), và mô tả thay đổi
8. WHILE người dùng không có vai trò Administrator, THE RBAC_Engine SHALL từ chối truy cập trang User Management và trả về mã lỗi 403
9. THE Frontend_App SHALL truy cập trang User Management qua User Profile Dropdown (Account Settings), không qua sidebar
