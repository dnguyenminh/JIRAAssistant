# Backend Core — Requirements

# Yêu cầu 8: Backend Server & REST API

**User Story:** Là một Developer, tôi muốn có Backend Server cung cấp REST API hoàn chỉnh, để Frontend_App giao tiếp với các service AI, Jira và Knowledge_Base thông qua các endpoint chuẩn hóa.

## Tiêu chí chấp nhận

1. THE Backend_Server SHALL cung cấp các nhóm endpoint: `/api/auth` (xác thực), `/api/projects` (danh sách dự án), `/api/projects/{key}/issues` (danh sách ticket), `/api/analysis/{ticketId}` (phân tích AI), `/api/estimation` (ước lượng Scrum point), `/api/graph/{projectKey}` (dữ liệu đồ thị), `/api/users` (quản lý người dùng), `/api/integrations` (quản lý provider)
2. WHEN Frontend_App gửi HTTP request đến bất kỳ endpoint nào, THE Backend_Server SHALL phản hồi với đúng định dạng JSON và mã HTTP status phù hợp (200, 201, 400, 401, 403, 404, 500)
3. IF Backend_Server nhận request với dữ liệu đầu vào không hợp lệ, THEN THE Backend_Server SHALL trả về mã lỗi 400 kèm thông báo mô tả chi tiết trường dữ liệu bị lỗi
4. IF Backend_Server gặp lỗi nội bộ, THEN THE Backend_Server SHALL ghi log lỗi chi tiết và trả về mã lỗi 500 với thông báo chung, không tiết lộ thông tin hệ thống nội bộ
5. THE Backend_Server SHALL sử dụng Ktor framework với Kotlin Serialization cho JSON và Koin cho Dependency Injection, nhất quán với kiến trúc shared module hiện có. Frontend_App sử dụng kiến trúc Kotlin/JS + HTML Templates với Vite bundler, chia sẻ domain models với Backend qua shared module (Kotlin Multiplatform)
6. THE Backend_Server SHALL đọc cấu hình (Jira host, AI provider URL, database path, JWT secret) từ biến môi trường, không hardcode trong mã nguồn
7. THE Backend_Server SHALL cung cấp endpoint `/health` trả về trạng thái kết nối đến Jira API, AI provider, và Knowledge_Base

---

# Yêu cầu 9: Knowledge Base Persistence Layer

**User Story:** Là một Scrum Master, tôi muốn kết quả phân tích AI được lưu trữ bền vững trong Knowledge Base, để các lần truy vấn sau không cần gọi lại AI và dữ liệu lịch sử được bảo toàn.

## Tiêu chí chấp nhận

1. THE KB_Repository SHALL lưu trữ các bản ghi phân tích bao gồm: ticket_id, requirement_summary, evolution_history, scrum_points, confidence_score, rationale, similar_ticket_references, và timestamp
2. WHEN AI_Orchestrator hoàn thành phân tích ticket mới, THE KB_Repository SHALL lưu kết quả vào Knowledge_Base trong vòng 1 giây sau khi phân tích hoàn tất
3. WHEN người dùng yêu cầu phân tích ticket đã tồn tại trong Knowledge_Base, THE KB_Repository SHALL trả về kết quả đã lưu mà không gọi AI agent
4. WHEN người dùng nhấn "RE-ANALYZE", THE KB_Repository SHALL ghi đè bản ghi cũ bằng kết quả phân tích AI mới và cập nhật timestamp
5. THE KB_Repository SHALL sử dụng SQLDelight làm ORM, tương thích với Kotlin Multiplatform và hỗ trợ migration schema khi cấu trúc dữ liệu thay đổi
6. WHILE Knowledge_Base chứa hơn 10.000 bản ghi, THE KB_Repository SHALL duy trì thời gian truy vấn theo ticket_id dưới 200ms
7. IF quá trình ghi vào Knowledge_Base gặp lỗi, THEN THE KB_Repository SHALL thực hiện retry tối đa 3 lần và ghi log lỗi chi tiết
8. THE KB_Repository SHALL lưu trữ dữ liệu đồ thị mạng lưới (nodes và edges) cho mỗi dự án để phục vụ hiển thị Knowledge Graph

---

# Yêu cầu 12: Multi-Agent AI Orchestration & Failover

**User Story:** Là một Developer, tôi muốn hệ thống hỗ trợ nhiều AI provider đồng thời với cơ chế failover tự động, để đảm bảo tính sẵn sàng cao cho các tác vụ phân tích AI.

## Tiêu chí chấp nhận

1. THE AI_Orchestrator SHALL hỗ trợ kết nối đồng thời tới các external AI provider qua REST API: Ollama (local server), Gemini (Cloud API), LM Studio (OpenAI-compatible local server). Các provider chạy độc lập bên ngoài ứng dụng, Backend_Server chỉ gọi API tới chúng qua URL cấu hình
2. WHEN AI_Orchestrator gọi AI agent, THE AI_Orchestrator SHALL sử dụng provider có ưu tiên cao nhất đang ở trạng thái active
3. IF AI provider đang hoạt động không phản hồi trong vòng 30 giây, THEN THE AI_Orchestrator SHALL tự động chuyển sang provider dự phòng tiếp theo trong danh sách ưu tiên
4. WHEN AI_Orchestrator chuyển đổi provider, THE AI_Orchestrator SHALL ghi log sự kiện failover bao gồm: provider cũ, provider mới, lý do chuyển đổi, và timestamp
5. THE AI_Orchestrator SHALL cho phép Administrator cấu hình thứ tự ưu tiên failover của các provider thông qua trang Integrations
6. IF tất cả AI provider đều offline, THEN THE AI_Orchestrator SHALL trả về thông báo lỗi rõ ràng cho người dùng và ghi log cảnh báo

### Dynamic Provider Configuration

7. THE AI_Orchestrator SHALL đọc provider configs từ database mỗi lần gọi `analyzeTicket()` thông qua `providerConfigProvider` lambda, đảm bảo thay đổi config (thêm/xóa/sửa provider, thay đổi status) được áp dụng ngay lập tức mà không cần restart server
8. THE AI_Orchestrator SHALL tìm provider theo `ProviderType` (OLLAMA, GEMINI, LM_STUDIO) thay vì hardcode provider ID, đảm bảo hoạt động đúng khi user tạo provider với ID tùy chỉnh
9. THE AI_Orchestrator SHALL tạo AI agents dynamically từ DB config mỗi lần gọi, sử dụng `agentProvider` lambda. Nếu không có provider nào trong DB, fallback tạo default Ollama agent tại `http://localhost:11434`

### Enriched Ticket Analysis

10. WHEN AI_Orchestrator phân tích ticket, THE AI_Orchestrator SHALL nhận ticket content (summary, description, status, resolution) từ BatchScanEngine và đưa vào AI prompt dưới section `--- TICKET CONTENT ---`. Content được cap tối đa 3000 ký tự để tránh prompt overflow
11. THE BatchScanEngine SHALL fetch ticket details từ Jira API (`getIssueDetails`) trước khi gọi AI, trích xuất summary + descriptionText + status + resolution, và truyền vào `analyzeTicket(ticketId, ticketContent)`
