# Batch Scan Engine — Requirements

# Yêu cầu 18: Batch Scan Engine — Quét và Phân tích Hàng loạt Ticket

**User Story:** Là một Scrum Master, tôi muốn quét và phân tích toàn bộ ticket trong dự án một cách tự động, để xây dựng Knowledge Base đầy đủ và có cái nhìn tổng quan về mạng lưới quan hệ giữa các ticket.

## Mô tả

Batch Scan Engine là thành phần backend chịu trách nhiệm điều phối quá trình quét hàng loạt ticket trong một dự án Jira. Engine hỗ trợ các thao tác START, PAUSE, RESUME, CANCEL và lưu trữ trạng thái quét per-project trong Knowledge_Base để đảm bảo tính liên tục khi người dùng chuyển đổi giữa các dự án.

## Thuật ngữ bổ sung

- **Batch_Scan_Engine**: Thành phần backend điều phối quá trình quét hàng loạt ticket, quản lý trạng thái quét (IDLE, SCANNING, PAUSED, CANCELLED, COMPLETED) cho mỗi dự án
- **Scan_State**: Trạng thái quét của một dự án, bao gồm: project key, danh sách ticket đã quét, ticket hiện tại, phần trăm hoàn thành, trạng thái (IDLE/SCANNING/PAUSED/CANCELLED/COMPLETED), timestamp bắt đầu và cập nhật cuối

## Tiêu chí chấp nhận

### Quản lý Trạng thái Quét

1. THE Batch_Scan_Engine SHALL duy trì trạng thái quét riêng biệt cho mỗi project key, với các trạng thái: IDLE, SCANNING, PAUSED, CANCELLED, COMPLETED
2. WHEN nhận yêu cầu START scan cho một dự án, THE Batch_Scan_Engine SHALL lấy danh sách toàn bộ ticket từ JiraClient, tạo Scan_State mới với trạng thái SCANNING, và bắt đầu phân tích tuần tự từng ticket qua AI_Orchestrator
3. WHEN nhận yêu cầu PAUSE scan, THE Batch_Scan_Engine SHALL chuyển trạng thái sang PAUSED, dừng xử lý ticket tiếp theo, và lưu vị trí hiện tại (index của ticket cuối cùng đã xử lý) vào Knowledge_Base
4. WHEN nhận yêu cầu RESUME scan cho dự án có trạng thái PAUSED, THE Batch_Scan_Engine SHALL khôi phục Scan_State từ Knowledge_Base và tiếp tục phân tích từ ticket tiếp theo sau vị trí đã lưu
5. WHEN nhận yêu cầu CANCEL scan, THE Batch_Scan_Engine SHALL chuyển trạng thái sang CANCELLED, dừng xử lý, và giữ lại kết quả phân tích của các ticket đã hoàn thành trong Knowledge_Base
6. THE Batch_Scan_Engine SHALL lưu trữ Scan_State vào Knowledge_Base (SQLDelight) bao gồm: projectKey, totalTickets, processedCount, currentTicketId, status, startedAt, updatedAt

### API Endpoints

7. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/start` để khởi động quá trình quét hàng loạt cho dự án, trả về Scan_State hiện tại
8. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/pause` để tạm dừng quá trình quét, trả về Scan_State đã cập nhật
9. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/resume` để tiếp tục quá trình quét đã tạm dừng, trả về Scan_State hiện tại
10. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/cancel` để hủy bỏ quá trình quét, trả về Scan_State đã cập nhật
11. THE Backend_Server SHALL cung cấp endpoint `GET /api/projects/{key}/scan/status` để truy vấn trạng thái quét hiện tại bao gồm: status, processedCount, totalTickets, phần trăm hoàn thành, và log các ticket đã xử lý gần nhất
12. THE Backend_Server SHALL cung cấp endpoint `GET /api/projects/{key}/scan/log` để truy vấn log chi tiết quá trình quét, bao gồm danh sách ticket đã xử lý với timestamp, trạng thái (COMPLETED/FAILED), và thông báo kết quả

### Xử lý Lỗi & Tính Bền vững

13. IF một ticket gặp lỗi trong quá trình phân tích, THEN THE Batch_Scan_Engine SHALL ghi nhận lỗi vào scan log, bỏ qua ticket đó, và tiếp tục phân tích ticket tiếp theo
14. IF Backend_Server khởi động lại trong khi có scan đang ở trạng thái SCANNING, THEN THE Batch_Scan_Engine SHALL chuyển trạng thái sang PAUSED để người dùng có thể RESUME thủ công
15. THE Batch_Scan_Engine SHALL giới hạn tối đa 1 quá trình quét đồng thời cho mỗi project key, trả về lỗi 409 Conflict nếu đã có scan đang chạy

### Tích hợp với Các Thành phần Khác

16. WHEN một ticket được phân tích xong trong quá trình quét, THE Batch_Scan_Engine SHALL lưu kết quả vào Knowledge_Base và cập nhật FeatureNetworkMapper để Graph_Engine có thể hiển thị dữ liệu mới
17. THE Batch_Scan_Engine SHALL sử dụng cùng chiến lược KB-First của AI_Orchestrator: bỏ qua ticket đã có kết quả trong Knowledge_Base (trừ khi ticket có cập nhật mới từ Jira)

### Multi-Project Scan Visibility

18. THE Backend_Server SHALL cung cấp endpoint `GET /api/scan/active` trả về danh sách TẤT CẢ projects đang có trạng thái SCANNING, mỗi entry gồm: projectKey, status, totalTickets, processedCount, progressPercent. Endpoint yêu cầu JWT (Reader+).

19. THE Frontend_App SHALL hiển thị scan progress với project key label: "[{PROJECT_KEY}] Scanning... {processed}/{total} — {percent}%". Khi user chuyển project, progress bar SHALL cập nhật để hiển thị scan status của project hiện tại.

20. WHEN nhiều projects có active scans đồng thời, THE Frontend_App SHALL hiển thị danh sách stacked progress bars trong Dashboard scan panel, mỗi project 1 progress bar riêng với label project key, cho phép user phân biệt giữa các scans.

21. WHEN user click START SCAN và project đã có active scan (backend trả 409), THE Frontend_App SHALL ngay lập tức hiển thị progress bar của scan đang chạy (như thể scan vừa được chấp nhận) thay vì hiển thị error message. User không cần biết scan đã chạy từ trước — UX phải seamless.

22. THE Frontend_App SHALL poll `GET /api/scan/active` mỗi 5 giây khi có ít nhất 1 scan đang chạy, cập nhật tất cả progress bars đồng thời. Khi không còn scan nào active, polling tự động dừng.

### Chi tiết Logging cho Quá trình Phân tích (Detailed Scan Logging)

23. WHEN Batch_Scan_Engine bắt đầu phân tích một ticket, THE scan log SHALL ghi entry "Analyzing ticket {ticketKey}" với status ANALYZING.

24. WHEN AI analysis hoàn tất cho một ticket, THE scan log SHALL ghi entry "AI analysis completed (source: {KB_CACHE|FRESH_AI})" với status COMPLETED, hoặc "AI analysis failed: {error}" với status FAILED.

25. WHEN FeatureNetworkMapper phát hiện issue links cho một ticket, THE scan log SHALL ghi entry "Found {count} issue links for {ticketKey}: {linkTypes}" với status COMPLETED. Ví dụ: "Found 3 issue links for ICL-42: Blocks(1), Relates(2)".

26. WHEN FeatureNetworkMapper phát hiện parent/subtask relationship, THE scan log SHALL ghi entry "Parent: {parentKey}" hoặc "Subtasks: {count} ({subtaskKeys})" với status COMPLETED.

27. WHEN Attachment_Pipeline bắt đầu xử lý attachment, THE scan log SHALL ghi entry "Processing attachment {filename} ({size}) for {ticketKey}" với status ANALYZING.

28. WHEN Attachment_Pipeline hoàn tất chuyển đổi attachment, THE scan log SHALL ghi entry "Attachment converted: {filename} → {chunkCount} chunks, {embeddingDim}d embeddings" với status COMPLETED.

29. WHEN Attachment_Pipeline bỏ qua attachment (unsupported type, quá lớn, đã tồn tại), THE scan log SHALL ghi entry "Attachment skipped: {filename} — {reason}" với status COMPLETED.

30. WHEN Attachment_Pipeline gặp lỗi xử lý attachment, THE scan log SHALL ghi entry "Attachment failed: {filename} — {error}" với status FAILED.

31. WHEN graph building hoàn tất sau scan, THE scan log SHALL ghi entry "Graph built: {nodeCount} nodes, {edgeCount} edges (links: {linkEdges}, parent: {parentEdges}, keyword: {keywordEdges})" với status COMPLETED.

32. THE Frontend_App SHALL hiển thị TẤT CẢ scan log entries (bao gồm AI analysis, issue links, subtasks, attachments, graph building) trong scan log container trên Dashboard, với color coding: ANALYZING (accent), COMPLETED (primary), FAILED (danger).

33. THE Backend_Server SHALL ghi log entries vào cả scan_log database (cho frontend polling) VÀ server console (println/logger) để hỗ trợ debugging.

---

**Tổng: 33 tiêu chí chấp nhận**
