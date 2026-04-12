# Ticket Intelligence — Requirements

# Yêu cầu 5: Ticket Intelligence — Phân tích AI Ticket (MH5)

**User Story:** Là một Scrum Master, tôi muốn phân tích ticket bằng AI để nhận được tóm tắt yêu cầu, lịch sử thay đổi và ước lượng Scrum point, giúp tôi hiểu sâu ticket trước khi lập kế hoạch sprint.

## Tiêu chí chấp nhận

1. THE Frontend_App SHALL cung cấp trường chọn ticket dạng combobox (searchable dropdown) trên trang Ticket Intelligence, cho phép người dùng tìm kiếm ticket theo ID hoặc summary, với danh sách ticket được lấy từ dự án hiện tại
2. WHEN người dùng nhấn "ANALYZE DATA", THE AI_Orchestrator SHALL thực hiện chiến lược KB-First: kiểm tra Knowledge_Base trước, chỉ gọi AI agent khi không tìm thấy kết quả đã lưu
3. WHILE AI_Orchestrator đang xử lý phân tích, THE Frontend_App SHALL hiển thị thanh tiến trình 3 giai đoạn: "Consolidating Ticket Metadata..." (0-40%), "AI RE-ANALYZING SCOPE..." (40-85%), "SYNCING TO KNOWLEDGE BASE..." (85-100%)
4. THE Frontend_App SHALL hiển thị kết quả trên 3 tab với hiệu ứng fadeIn (0.5s) khi chuyển tab:
   - Tab Context: Tóm tắt yêu cầu thống nhất với danh sách module bị ảnh hưởng (màu Primary, Accent, Secondary)
   - Tab Evolution: Timeline dạng neural console hiển thị lịch sử thay đổi yêu cầu từ Origin qua các phiên bản đến Current
   - Tab Complexity: Scrum Point (text gradient lớn), mô tả độ phức tạp, và badge tham chiếu KB với phần trăm tương đồng
5. WHEN người dùng nhấn "RE-ANALYZE", THE AI_Orchestrator SHALL thực hiện phân tích AI mới, ghi đè bản ghi KB cũ và cập nhật timestamp
6. THE ScrumEstimator SHALL chỉ trả về giá trị Scrum point nằm trong thang điểm: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40
7. WHILE người dùng có vai trò Reader, THE Frontend_App SHALL vô hiệu hóa nút "ANALYZE DATA" và "RE-ANALYZE" (opacity 0.5, cursor not-allowed)
8. THE AI_Orchestrator SHALL tổng hợp dữ liệu từ Summary, Description, Sub-tickets và Attachment metadata trước khi gửi đến AI agent
9. THE AI_Orchestrator SHALL parse kết quả AI thành 3 phần: Requirement Summary, Evolution History, và Complexity Assessment kèm Scrum point
10. IF AI agent trả về JSON không hợp lệ, THEN THE AI_Orchestrator SHALL thực hiện retry tối đa 2 lần với prompt được điều chỉnh, sau đó trả về thông báo lỗi cho người dùng

### Trạng thái Phân tích Ticket & Nút Hành động Động

11. WHEN người dùng chọn một ticket từ combobox, THE Frontend_App SHALL hiển thị trạng thái phân tích của ticket đó: "Chưa phân tích", "Đã phân tích", "Có cập nhật mới", hoặc "Đang phân tích"
12. WHEN ticket được chọn có trạng thái "Chưa phân tích", THE Frontend_App SHALL hiển thị nút hành động với nhãn "ANALYZE"
13. WHEN ticket được chọn có trạng thái "Có cập nhật mới" (ticket đã thay đổi sau lần phân tích cuối), THE Frontend_App SHALL hiển thị nút hành động với nhãn "RE-ANALYZE"
14. WHILE ticket được chọn đang trong quá trình phân tích (trạng thái "Đang phân tích"), THE Frontend_App SHALL vô hiệu hóa nút hành động (disabled) với nhãn "ANALYZING..." và hiển thị spinner
15. WHEN người dùng nhập ký tự vào combobox, THE Frontend_App SHALL lọc danh sách ticket theo ticket ID hoặc summary với độ trễ debounce tối đa 300ms
