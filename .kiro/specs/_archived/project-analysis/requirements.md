# Project Analysis — Requirements

---

## Yêu cầu 4: Project Analysis — Phân tích Sprint (MH4)

**User Story:** Là một Scrum Master, tôi muốn xem phân tích chi tiết về velocity, bottleneck và các chỉ số sprint, để đưa ra quyết định lập kế hoạch sprint chính xác hơn.

### Tiêu chí chấp nhận

1. THE Frontend_App SHALL hiển thị 4 thẻ chỉ số: Total Tickets (tổng số), Resolution Rate (phần trăm), Cycle Time (ngày), AI Velocity (điểm)
2. THE Frontend_App SHALL hiển thị biểu đồ cột Velocity Trend với tối thiểu 7 cột thể hiện story points qua các sprint
3. WHEN người dùng hover lên một cột biểu đồ, THE Frontend_App SHALL phóng to cột theo chiều dọc 1.05x với hiệu ứng opacity
4. THE AI_Orchestrator SHALL phân tích dữ liệu sprint và tạo Bottleneck Radar với tối thiểu 2 cảnh báo: một cảnh báo rủi ro và một gợi ý tối ưu hóa
5. THE Frontend_App SHALL cung cấp nút "DIVE INTO REPORTS" để điều hướng đến phân tích chi tiết

### Hiển thị Dữ liệu Tăng dần (Progressive Display)

6. WHILE Batch_Scan_Engine đang quét dự án, THE Frontend_App SHALL cập nhật các thẻ chỉ số (Total Tickets, Resolution Rate, Cycle Time, AI Velocity) theo thời gian thực dựa trên dữ liệu ticket đã được phân tích, thay vì chờ toàn bộ quá trình quét hoàn tất
7. WHILE Batch_Scan_Engine đang quét dự án, THE Frontend_App SHALL cập nhật biểu đồ Velocity Trend và Bottleneck Radar tăng dần khi có thêm dữ liệu phân tích mới
8. THE Frontend_App SHALL hiển thị trạng thái quét hiện tại (đang quét / đã hoàn tất / chưa quét) và phần trăm hoàn thành trên trang Project Analysis
