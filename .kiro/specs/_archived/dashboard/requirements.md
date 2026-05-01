# Dashboard — Requirements

---

## Yêu cầu 2: Dashboard — Tổng quan Dự án (MH2)

**User Story:** Là một thành viên dự án, tôi muốn xem tổng quan nhanh về sức khỏe dự án, các chỉ số AI và hoạt động hệ thống, để nắm bắt tình trạng dự án ngay khi đăng nhập.

### Mô tả

Dashboard hiển thị dữ liệu thực từ API (`GET /api/projects/{key}/analysis`). Khi chưa có dữ liệu (Jira chưa cấu hình hoặc project mới), các chỉ số hiển thị "—" thay vì giá trị hardcoded. Project key được lấy từ `ApiClient.getProjectKey()` (sessionStorage).

### Tiêu chí chấp nhận

1. THE Frontend_App SHALL hiển thị 3 thẻ chỉ số chính với dữ liệu thực từ API: PROJECT AI HEALTH (resolutionRate phần trăm + delta), ACTIVE KNOWLEDGE NODES (totalTickets), NEURAL VELOCITY (aiVelocity điểm + trạng thái). Khi API chưa trả về dữ liệu, hiển thị "—" cho mỗi giá trị
2. THE Frontend_App SHALL hiển thị thẻ xem trước Relationship Network với nút "VIEW GRAPH" điều hướng đến trang Knowledge Graph
3. THE Frontend_App SHALL hiển thị biểu đồ AI Estimation Drift với nút "ANALYSIS DRIFT" điều hướng đến trang Project Analysis
4. THE Frontend_App SHALL hiển thị Neural_Console với tối thiểu 3 mục log bao gồm timestamp, tag (AI_SYNC, KB_WRITE, HEARTBEAT) và nội dung thông báo
5. WHEN người dùng nhấn avatar trên thanh điều hướng, THE Frontend_App SHALL hiển thị dropdown với các mục: Account Settings, Security & Permissions, Sign Out
6. THE Frontend_App SHALL hiển thị sidebar điều hướng gồm 5 mục: Dashboard, Relationship Network, Project Analysis, Ticket Intelligence, Integrations với mục đang active được highlight
7. WHEN người dùng nhấn "Sign Out" trong dropdown, THE Auth_Service SHALL xóa JWT token và project key khỏi sessionStorage, sau đó redirect tới `#login` (trang đăng nhập)
8. THE Frontend_App SHALL sử dụng `ApiClient.getProjectKey()` để lấy project key cho mọi API call liên quan đến dự án, thay vì hardcode project key

### Batch Project Scan — Quét toàn bộ Ticket trong Dự án

9. THE Frontend_App SHALL hiển thị nút "START SCAN" trên Dashboard khi project đã được chọn, cho phép người dùng khởi động quá trình quét và phân tích toàn bộ ticket trong dự án hiện tại
9a. BEFORE starting scan, THE Frontend_App SHALL kiểm tra AI readiness qua `GET /api/projects/{key}/scan/ai-status`. Nếu không có AI provider ACTIVE, hiển thị confirm dialog: "No AI provider is active. Scan will proceed without AI analysis. Continue anyway?" cho user chọn tiếp tục hoặc hủy
9b. THE Frontend_App SHALL hiển thị dropdown chọn số luồng xử lý song song (×1, ×2, ×3, ×5, ×8, ×10) bên cạnh nút START SCAN. Giá trị mặc định là ×3. Giá trị được truyền qua query parameter `?concurrency=N` khi gọi `POST /api/projects/{key}/scan/start`
9c. THE BatchScanEngine SHALL xử lý tickets song song theo batch size do user chọn (1-10). Mỗi batch gồm N tickets được xử lý đồng thời bằng coroutines, batch tiếp theo bắt đầu khi batch trước hoàn tất
9d. THE BatchScanEngine SHALL sử dụng AI Semaphore để giới hạn số lượng AI inference calls đồng thời (mặc định 1 cho local Ollama). Jira fetch, relationship logging, và attachment processing chạy song song không bị giới hạn bởi semaphore. Cấu trúc xử lý mỗi ticket: Phase 1 (parallel) Jira fetch → Phase 2 (semaphore) AI analysis → Phase 3 (parallel) relationships + attachments
9e. WHEN user chọn concurrency ≥ 8, THE Frontend_App SHALL hiển thị warning icon (⚠) trên dropdown option để cảnh báo có thể chậm hơn do AI bottleneck

### Force Re-analyze

9f. THE Frontend_App SHALL hiển thị checkbox "FORCE" bên cạnh nút START SCAN. Khi checked, scan sẽ gọi AI phân tích lại TẤT CẢ tickets (bỏ qua KB cache), ghi đè kết quả cũ trong Knowledge Base
9g. WHEN "FORCE" checkbox được checked, THE BatchScanEngine SHALL gọi `analyzeTicket(ticketId, content, forceReanalyze = true)` thay vì `false`. AI Orchestrator sẽ bỏ qua KB-First check và gọi AI provider cho mọi ticket
9h. THE scan log SHALL hiển thị "FORCE RE-ANALYZE" trong config message khi force mode được bật, để user biết scan đang chạy ở chế độ force
10. WHILE Batch_Scan_Engine đang quét dự án, THE Frontend_App SHALL hiển thị nút "PAUSE" cho phép tạm dừng quá trình quét, và nút "CANCEL" cho phép hủy bỏ hoàn toàn quá trình quét
11. WHEN quá trình quét đã được tạm dừng (PAUSED), THE Frontend_App SHALL chuyển nút thành "RESUME" cho phép người dùng tiếp tục quét từ vị trí đã dừng
12. WHILE Batch_Scan_Engine đang quét, THE Frontend_App SHALL hiển thị thanh tiến trình (progress bar) với phần trăm hoàn thành và số ticket đã xử lý trên tổng số ticket
13. WHILE Batch_Scan_Engine đang quét, THE Frontend_App SHALL hiển thị log chi tiết (scan log) gồm timestamp, ticket ID đang xử lý, trạng thái (ANALYZING, COMPLETED, FAILED) và thông báo kết quả cho mỗi ticket
14. WHEN người dùng chuyển sang dự án khác qua Navbar Project Badge, THE Frontend_App SHALL tự động tạm dừng (PAUSE) quá trình quét của dự án hiện tại và lưu trạng thái quét vào Batch_Scan_Engine
15. WHEN người dùng quay lại dự án đã có quá trình quét bị tạm dừng, THE Frontend_App SHALL khôi phục trạng thái quét trước đó và hiển thị nút "RESUME" để tiếp tục quét

### Scan Log UX Improvements

16. THE Frontend_App SHALL hiển thị scan log với append-only strategy: mỗi lần poll API, chỉ thêm entries mới (chưa render) vào cuối danh sách, giữ lại toàn bộ entries cũ. Entries được dedup theo `id` field. WHEN user navigate away và quay lại Dashboard, THE Frontend_App SHALL detect DOM đã bị clear và re-render toàn bộ recentLog entries từ API response (bỏ qua sessionStorage dedup cho lần render đầu tiên) để đảm bảo scan log hiển thị đầy đủ
17. THE Frontend_App SHALL hiển thị scrollbar rõ ràng trên scan log section khi số entries vượt quá chiều cao container (max-height 280px), cho phép người dùng cuộn xem log cũ
18. THE Frontend_App SHALL hiển thị nút expand (⛶) ở góc trên phải scan log section, cho phép phóng to scan log thành dialog fullscreen (90vw × 80vh) để xem nhiều log hơn
19. WHEN scan log dialog đang mở, THE Frontend_App SHALL live-update dialog body mỗi khi có entries mới từ polling, auto-scroll xuống cuối
20. WHEN người dùng nhấn START SCAN mới, THE Frontend_App SHALL reset toàn bộ scan log entries đã render (xóa `renderedIds` cache) để bắt đầu log mới

---

## Liên kết Spec

> **Deep Analysis Enhancement (spec `ticket-intelligence`, phần Deep Analysis)**: Scan trên Dashboard sử dụng cùng pipeline `AIOrchestrator.analyzeTicket()` với Ticket Intelligence. Deep Analysis nâng cấp pipeline này (Jira extraction + prompt + data model) tại tầng AIOrchestrator — khi scan chạy, mỗi ticket cũng được phân tích sâu tự động. BatchScanEngine.processTicket() không cần thay đổi.
