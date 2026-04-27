---
inclusion: always
---

# Post-Implementation Verification Rule

## Quy tắc bắt buộc

Mỗi khi một requirement mới được implement hoặc bug được fix, agent @jira-master-agent PHẢI:

1. **Kiểm tra trên app đang chạy** (localhost:3000) bằng browser — verify chức năng hoạt động đúng theo spec
2. **Cung cấp báo cáo** cho từng requirement/bug đã implement:
   - ✅ PASS — chức năng hoạt động đúng
   - ❌ FAIL — chức năng không hoạt động, mô tả chi tiết
   - ⚠️ SKIP — không thể kiểm tra (ghi lý do)
3. **Chụp screenshot** khi cần thiết để minh họa kết quả
4. **Báo cáo regression** — kiểm tra các chức năng liên quan không bị ảnh hưởng

## Khi nào áp dụng

- Sau khi hoàn thành implement một hoặc nhiều tasks trong spec
- Sau khi fix bug (bugfix spec hoặc ad-hoc fix)
- Sau khi cập nhật code ảnh hưởng đến UI hoặc API endpoints
- Khi user yêu cầu verify chức năng

## Quy trình

1. Đọc requirements/bugfix spec liên quan
2. Mở browser tại localhost:3000
3. Login và navigate đến trang liên quan
4. Kiểm tra từng acceptance criteria
5. Test API endpoints nếu cần (qua browser network hoặc direct calls)
6. Tổng hợp báo cáo với bảng PASS/FAIL/SKIP
7. Liệt kê bugs mới phát hiện (nếu có)
