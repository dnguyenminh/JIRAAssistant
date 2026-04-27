# Cross-cutting Concerns — Requirements

# Yêu cầu 13: Tích hợp Frontend-Backend

**User Story:** Là một Developer, tôi muốn Frontend_App giao tiếp liền mạch với Backend_Server qua REST API, để dữ liệu hiển thị trên giao diện luôn đồng bộ với trạng thái thực tế của hệ thống.

## Mô tả

### Thay đổi so với thiết kế ban đầu:
- **Login flow**: Frontend gửi POST `/api/auth/login` với `{email: username, password}`, nhận JWT + user info, lưu vào sessionStorage
- **Project selection flow**: Sau login, nếu chưa có project key → hiển thị trang Project Selection. Gọi `GET /api/projects` để lấy danh sách projects. User chọn project → lưu key vào sessionStorage qua `ApiClient.saveProjectKey()`
- **ApiClient.getProjectKey()**: Tất cả pages sử dụng `ApiClient.getProjectKey()` để lấy project key từ sessionStorage cho API calls, thay vì hardcode
- **Navbar project badge**: Hiển thị `[PROJ]` badge với project key hiện tại, có dropdown để đổi project

## Tiêu chí chấp nhận

1. WHEN người dùng chọn dự án trên trang Project Selection, THE Frontend_App SHALL lưu project key vào sessionStorage qua `ApiClient.saveProjectKey()` và tất cả API calls sau đó SHALL sử dụng `ApiClient.getProjectKey()` để lấy project key
2. WHEN người dùng nhập Ticket ID và nhấn "ANALYZE" trên trang Ticket Intelligence, THE Frontend_App SHALL gọi endpoint `/api/analysis/{ticketId}` qua Kotlin/JS API client và bind kết quả vào 3 tab trong HTML template qua DOM manipulation
3. WHILE Backend_Server đang xử lý request phân tích AI, THE Frontend_App SHALL cập nhật thanh tiến trình trong HTML template với trạng thái theo thời gian thực thông qua Kotlin/JS polling logic (kiểm tra `/api/analysis/{ticketId}/status` mỗi 3 giây khi request kéo dài hơn 15 giây)
4. WHEN người dùng mở trang Knowledge Graph, THE Frontend_App SHALL gọi endpoint `/api/graph/{projectKey}` qua Kotlin/JS API client (projectKey từ `ApiClient.getProjectKey()`) và render đồ thị mạng lưới bằng cách tạo SVG elements qua DOM APIs (document.createElementNS)
5. WHEN người dùng mở trang Project Analysis, THE Frontend_App SHALL gọi endpoint `/api/projects/{key}/analysis` qua Kotlin/JS API client (key từ `ApiClient.getProjectKey()`) và bind dữ liệu vào 4 thẻ chỉ số cùng biểu đồ velocity trong HTML template
6. THE Frontend_App SHALL xử lý tất cả HTTP error response (400, 401, 403, 404, 500) trong Kotlin/JS API client với thông báo lỗi thân thiện người dùng, hiển thị qua toast notification. 401 → redirect `#login`
7. THE Frontend_App SHALL hiển thị trang Login standalone (không Shell) với form đăng nhập. POST `/api/auth/login` với `{email: username, password}` → nhận JWT + user info → lưu sessionStorage → redirect `#project_select` hoặc `#dashboard`
8. THE Frontend_App SHALL hiển thị trang Project Selection standalone (không Shell) với grid projects từ `GET /api/projects`. Click project → `ApiClient.saveProjectKey()` → redirect `#dashboard`
9. THE Navbar SHALL hiển thị project badge `[PROJ]` với project key hiện tại từ `ApiClient.getProjectKey()`, có dropdown cho phép đổi project (redirect `#project_select`)
9a. WHEN người dùng chọn project từ trang Project Selection và hệ thống navigate sang trang đích, THE Navbar SHALL cập nhật project badge hiển thị project key mới ngay khi trang đích được render — badge KHÔNG ĐƯỢC giữ giá trị "Select Project" từ lần render trước
9b. WHEN project key thay đổi (chọn project mới hoặc đổi project qua dropdown), THE NavbarDropdown SHALL refresh project selector badge để phản ánh project key hiện tại từ sessionStorage
9c. WHEN hệ thống navigate từ trang project_select sang trang khác, THE Navbar SHALL cập nhật breadcrumb hiển thị đúng route đích (ví dụ: "DASHBOARD / OVERVIEW") thay vì route cũ "PROJECT_SELECT / PROJECT_SELECT"

---

# Yêu cầu 14: Serialization Round-Trip cho Dữ liệu Hệ thống

**User Story:** Là một Developer, tôi muốn đảm bảo dữ liệu được serialize/deserialize chính xác giữa các thành phần hệ thống, để không mất thông tin khi lưu trữ và truyền tải.

## Tiêu chí chấp nhận

1. FOR ALL đối tượng ScrumEstimation hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
2. FOR ALL đối tượng NetworkGraph hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
3. FOR ALL đối tượng AIResult hợp lệ (cả Success và Failure), việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
4. FOR ALL đối tượng JiraIssue hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
5. IF dữ liệu JSON đầu vào thiếu trường bắt buộc, THEN THE JSON parser SHALL trả về lỗi mô tả rõ trường bị thiếu thay vì tạo đối tượng với giá trị mặc định sai
6. FOR ALL đối tượng KBRecord hợp lệ (bản ghi Knowledge Base), việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu

---

# Yêu cầu 15: Triển khai Production & DevOps

**User Story:** Là một DevOps Engineer, tôi muốn có quy trình triển khai tự động và cấu hình production rõ ràng, để ứng dụng vận hành ổn định trên môi trường thực tế.

## Tiêu chí chấp nhận

1. THE Deployment_Pipeline SHALL hỗ trợ build ứng dụng thành Docker container bao gồm Backend_Server và Frontend_App tĩnh
2. THE Deployment_Pipeline SHALL chạy toàn bộ unit test và E2E test trước khi cho phép deploy lên môi trường production
3. THE Deployment_Pipeline SHALL tạo Dockerfile multi-stage: stage build (Gradle + JDK) và stage runtime (JRE minimal)
4. WHILE ứng dụng đang chạy trên production, THE Backend_Server SHALL cung cấp endpoint `/health` trả về trạng thái kết nối đến Jira API, AI provider, và Knowledge_Base
5. IF Backend_Server phát hiện AI provider chính (Ollama) không phản hồi, THEN THE Backend_Server SHALL tự động chuyển sang AI provider dự phòng (Gemini) và ghi log cảnh báo
6. THE Deployment_Pipeline SHALL hỗ trợ chế độ "Air-gapped" cho phép triển khai hoàn toàn nội bộ (on-premise) bằng cách trỏ `AI_PROVIDER_URL` tới Ollama server trong mạng nội bộ, không yêu cầu kết nối internet cho AI
7. THE Deployment_Pipeline SHALL cung cấp file `docker-compose.yml` để khởi chạy Backend_Server và Frontend_App bằng một lệnh duy nhất. Các AI provider (Ollama, Gemini, LM Studio) là external services chạy độc lập, Backend_Server kết nối tới chúng qua biến môi trường `AI_PROVIDER_URL`. Administrator có thể cấu hình lại kết nối provider qua trang Integrations trong ứng dụng

---

# Yêu cầu 16: Bảo mật & Quyền riêng tư Dữ liệu

**User Story:** Là một Administrator, tôi muốn hệ thống đảm bảo bảo mật dữ liệu và quyền riêng tư, để tuân thủ chính sách bảo mật doanh nghiệp khi xử lý dữ liệu Jira nhạy cảm.

## Tiêu chí chấp nhận

1. THE Backend_Server SHALL hỗ trợ chế độ "Air-gapped" bằng cách kết nối tới Ollama server chạy trong mạng nội bộ (on-premise), đảm bảo không có dữ liệu nào rời khỏi mạng nội bộ. Administrator cấu hình endpoint Ollama qua biến môi trường `AI_PROVIDER_URL` hoặc qua trang Integrations
2. THE Auth_Service SHALL sử dụng Jira REST API với credentials riêng của từng người dùng, không dùng chung một service account
3. THE Backend_Server SHALL lưu trữ API tokens và JWT secrets dưới dạng mã hóa, không lưu plaintext trong client-side code hoặc local storage
4. THE Backend_Server SHALL ghi audit log cho tất cả thao tác nhạy cảm: đăng nhập, thay đổi quyền, cấu hình provider, và truy cập dữ liệu phân tích
5. IF người dùng Sign Out, THEN THE Auth_Service SHALL hủy JWT token phía server và xóa token khỏi client-side storage

---

# Yêu cầu 21: Jira API v3 Compatibility

## Giới thiệu

Atlassian đã deprecate một số Jira REST API endpoints. Ứng dụng phải sử dụng các endpoints mới và xử lý đúng format response của Jira API v3.

---

## Tiêu chí chấp nhận

**21.1** THE JiraRestClient SHALL sử dụng endpoint `/rest/api/3/search/jql` cho search queries thay vì `/rest/api/3/search` (đã deprecated, trả 410 Gone).

**21.2** THE JiraRestClient SHALL hỗ trợ cursor-based pagination qua `nextPageToken` parameter, loop cho đến khi `isLast=true`, không giới hạn số tickets.

**21.3** THE JiraRestClient SHALL xử lý `description` field dưới dạng `JsonElement` (Atlassian Document Format — ADF object) thay vì `String`, vì Jira API v3 trả description dạng structured JSON.

**21.4** THE JiraRestClient SHALL request các fields cần thiết (`summary,status,resolution,created,updated,description,parent,subtasks,issuelinks,attachment`) qua `fields` parameter để cung cấp đầy đủ dữ liệu cho analysis và network graph.

**21.5** THE JiraRestClient SHALL log chi tiết khi API call thất bại: HTTP status, response body (200 chars đầu), và exception message — KHÔNG catch silently.

**21.6** THE JiraIssueFields model SHALL hỗ trợ các fields bổ sung từ Jira API v3: `parent` (JiraParent), `subtasks` (List<JiraSubtask>), `issuelinks` (List<JiraIssueLink>), `attachment` (List<JiraAttachment>) — tất cả nullable với default empty.

**21.7** THE JiraIssueLink model SHALL chứa `type` (name, inward, outward), `inwardIssue` (id, key), và `outwardIssue` (id, key) để trích xuất quan hệ tường minh giữa tickets.

---

**Tổng: 7 tiêu chí chấp nhận**
