# Tài liệu Yêu cầu — Hoàn thiện Requirements & Hướng dẫn Sử dụng Jira Assistant

## Giới thiệu

Tài liệu này hệ thống hóa các yêu cầu còn thiếu và cần hoàn thiện cho dự án **Jira Assistant** — một công cụ quản lý dự án Agile tích hợp AI, xây dựng trên nền tảng Kotlin Multiplatform (KMP) + Compose for Web/Wasm. Dự án hiện đã hoàn thành khoảng 70-80% phần core (AI agents, Jira client, domain models, Compose UI, frontend HTML). Tài liệu tập trung vào 7 khoảng trống chính: Backend Server/API, Knowledge Base persistence, Xác thực & Phân quyền (RBAC), Tích hợp Frontend-Backend, Triển khai Production, Thuật toán đồ thị nâng cao, và Hướng dẫn sử dụng toàn diện.

## Thuật ngữ (Glossary)

- **Backend_Server**: Máy chủ Ktor phía server cung cấp REST API cho toàn bộ ứng dụng Jira Assistant
- **API_Gateway**: Lớp điều phối các request HTTP từ Frontend đến các service nội bộ của Backend_Server
- **Knowledge_Base**: Cơ sở dữ liệu lưu trữ kết quả phân tích AI, lịch sử ticket, và mối quan hệ ngữ nghĩa
- **KB_Repository**: Lớp truy cập dữ liệu (repository) quản lý đọc/ghi vào Knowledge_Base thông qua SQLDelight
- **Auth_Service**: Dịch vụ xác thực người dùng và quản lý phiên đăng nhập (session/token)
- **RBAC_Engine**: Hệ thống phân quyền dựa trên vai trò (Administrator, Neural_Architect, Reader)
- **Graph_Engine**: Module xử lý thuật toán bố trí đồ thị (layout) và phân cụm (clustering) cho mạng lưới ticket
- **Frontend_App**: Ứng dụng web HTML/CSS/JS hiện có tại thư mục `frontend/`
- **Compose_App**: Ứng dụng Compose Multiplatform (Wasm) tại thư mục `composeApp/`
- **AI_Orchestrator**: Thành phần điều phối phân tích AI, quản lý chiến lược KB-First và failover giữa các provider
- **User_Guide**: Tài liệu hướng dẫn sử dụng toàn diện dành cho người dùng cuối
- **Deployment_Pipeline**: Quy trình CI/CD tự động hóa build, test và triển khai ứng dụng lên môi trường production

---

## Yêu cầu

### Yêu cầu 1: Backend Server & REST API

**User Story:** Là một Developer, tôi muốn có một Backend Server cung cấp REST API hoàn chỉnh, để Frontend có thể giao tiếp với các service AI, Jira và Knowledge Base thông qua các endpoint chuẩn hóa.

#### Tiêu chí chấp nhận

1. WHEN Frontend_App gửi một HTTP request đến bất kỳ endpoint nào, THE Backend_Server SHALL phản hồi với đúng định dạng JSON và mã HTTP status phù hợp (200, 400, 401, 404, 500)
2. THE Backend_Server SHALL cung cấp các nhóm endpoint sau: `/api/projects` (danh sách dự án), `/api/issues` (danh sách ticket), `/api/analysis` (phân tích AI), `/api/estimation` (ước lượng Scrum point), `/api/graph` (dữ liệu đồ thị mạng lưới), `/api/auth` (xác thực), `/api/users` (quản lý người dùng)
3. WHEN Backend_Server nhận request phân tích ticket, THE AI_Orchestrator SHALL kiểm tra Knowledge_Base trước, chỉ gọi AI agent khi không tìm thấy kết quả đã lưu
4. IF Backend_Server nhận request với dữ liệu đầu vào không hợp lệ, THEN THE Backend_Server SHALL trả về mã lỗi 400 kèm thông báo mô tả chi tiết trường dữ liệu bị lỗi
5. IF Backend_Server gặp lỗi nội bộ trong quá trình xử lý, THEN THE Backend_Server SHALL ghi log lỗi chi tiết và trả về mã lỗi 500 với thông báo chung, không tiết lộ thông tin hệ thống nội bộ
6. THE Backend_Server SHALL sử dụng Ktor framework với Kotlin Serialization cho JSON và Koin cho Dependency Injection, nhất quán với kiến trúc shared module hiện có


### Yêu cầu 2: Knowledge Base Persistence Layer

**User Story:** Là một Scrum Master, tôi muốn kết quả phân tích AI được lưu trữ bền vững trong Knowledge Base, để các lần truy vấn sau không cần gọi lại AI và dữ liệu lịch sử được bảo toàn.

#### Tiêu chí chấp nhận

1. THE KB_Repository SHALL lưu trữ các bản ghi phân tích bao gồm: ticket_id, requirement_summary, evolution_history, scrum_points, confidence_score, rationale, similar_ticket_references, và timestamp
2. WHEN AI_Orchestrator hoàn thành phân tích một ticket mới, THE KB_Repository SHALL lưu kết quả vào Knowledge_Base trong vòng 1 giây sau khi phân tích hoàn tất
3. WHEN người dùng yêu cầu phân tích một ticket đã tồn tại trong Knowledge_Base, THE KB_Repository SHALL trả về kết quả đã lưu mà không gọi AI agent
4. WHEN người dùng nhấn nút "RE-ANALYZE", THE KB_Repository SHALL ghi đè bản ghi cũ bằng kết quả phân tích AI mới và cập nhật timestamp
5. THE KB_Repository SHALL sử dụng SQLDelight làm ORM, tương thích với Kotlin Multiplatform và hỗ trợ migration schema khi cấu trúc dữ liệu thay đổi
6. WHILE Knowledge_Base chứa hơn 10.000 bản ghi, THE KB_Repository SHALL duy trì thời gian truy vấn theo ticket_id dưới 200ms
7. IF quá trình ghi vào Knowledge_Base gặp lỗi, THEN THE KB_Repository SHALL thực hiện retry tối đa 3 lần và ghi log lỗi chi tiết

### Yêu cầu 3: Xác thực Người dùng & Phân quyền (RBAC)

**User Story:** Là một Administrator, tôi muốn hệ thống có cơ chế xác thực và phân quyền chặt chẽ, để chỉ những người dùng được ủy quyền mới truy cập được các chức năng tương ứng với vai trò của họ.

#### Tiêu chí chấp nhận

1. WHEN người dùng truy cập trang Onboarding, THE Auth_Service SHALL yêu cầu nhập Jira Domain, email và API Token để xác thực
2. WHEN Auth_Service nhận thông tin xác thực hợp lệ, THE Auth_Service SHALL tạo một session token (JWT) có thời hạn 24 giờ và trả về cho Frontend_App
3. IF Auth_Service nhận thông tin xác thực không hợp lệ, THEN THE Auth_Service SHALL trả về mã lỗi 401 kèm thông báo "Thông tin xác thực không chính xác"
4. THE RBAC_Engine SHALL hỗ trợ 3 vai trò: Administrator (toàn quyền), Neural_Architect (phân tích AI, ghi KB, xem dashboard), Reader (chỉ xem dashboard và đồ thị)
5. WHEN một người dùng có vai trò Reader cố gắng thực hiện thao tác ghi KB hoặc kích hoạt phân tích AI, THE RBAC_Engine SHALL từ chối request và trả về mã lỗi 403
6. WHEN Administrator thay đổi quyền của một người dùng, THE RBAC_Engine SHALL áp dụng thay đổi ngay lập tức và ghi nhật ký audit log bao gồm: người thực hiện, người bị thay đổi, quyền cũ, quyền mới, và timestamp
7. IF session token hết hạn hoặc không hợp lệ, THEN THE Auth_Service SHALL chuyển hướng người dùng về trang Onboarding với thông báo "Phiên đăng nhập đã hết hạn"


### Yêu cầu 4: Tích hợp Frontend-Backend

**User Story:** Là một Developer, tôi muốn Frontend_App giao tiếp liền mạch với Backend_Server thông qua REST API, để dữ liệu hiển thị trên giao diện luôn đồng bộ với trạng thái thực tế của hệ thống.

#### Tiêu chí chấp nhận

1. WHEN người dùng chọn một dự án trên Dashboard, THE Frontend_App SHALL gọi endpoint `/api/projects/{key}/issues` và hiển thị danh sách ticket trong vòng 3 giây
2. WHEN người dùng nhập Ticket ID và nhấn "ANALYZE" trên trang Ticket Intelligence, THE Frontend_App SHALL gọi endpoint `/api/analysis/{ticketId}` và hiển thị kết quả trên 3 tab (Context, Evolution, Complexity)
3. WHILE Backend_Server đang xử lý một request phân tích AI, THE Frontend_App SHALL hiển thị thanh tiến trình (progress bar) với trạng thái cập nhật theo thời gian thực thông qua Server-Sent Events hoặc polling
4. WHEN người dùng nhấn "TEST LINK" trên trang Integrations, THE Frontend_App SHALL gọi endpoint `/api/integrations/test` và hiển thị kết quả kết nối (active/offline) kèm độ trễ (latency) trong vòng 5 giây
5. IF Frontend_App không thể kết nối đến Backend_Server, THEN THE Frontend_App SHALL hiển thị thông báo lỗi "Không thể kết nối đến máy chủ" và cung cấp nút "Thử lại"
6. THE Frontend_App SHALL gửi session token trong header `Authorization: Bearer {token}` cho mọi request đến Backend_Server (trừ endpoint `/api/auth/login`)

### Yêu cầu 5: Thuật toán Đồ thị Nâng cao

**User Story:** Là một Product Owner, tôi muốn đồ thị mạng lưới ticket hiển thị rõ ràng các cụm tính năng và mối quan hệ, để tôi có thể nhanh chóng nhận diện các phụ thuộc và ưu tiên roadmap.

#### Tiêu chí chấp nhận

1. THE Graph_Engine SHALL triển khai thuật toán force-directed layout để tự động sắp xếp vị trí các node trên đồ thị, đảm bảo các node liên quan nằm gần nhau
2. WHEN đồ thị chứa từ 2 cụm tính năng trở lên, THE Graph_Engine SHALL phân biệt các cụm bằng màu sắc riêng biệt và đường viền bao quanh (bounding box)
3. WHEN người dùng nhấn vào một node trên đồ thị, THE Frontend_App SHALL hiển thị panel chi tiết ticket bên phải bao gồm: ticket key, summary, status, và nút "OPEN IN JIRA"
4. WHILE đồ thị đang hiển thị, THE Graph_Engine SHALL hỗ trợ thao tác zoom (phóng to/thu nhỏ) và pan (kéo di chuyển) bằng chuột
5. THE Graph_Engine SHALL render đồ thị với tối đa 100 node trong thời gian dưới 3 giây trên trình duyệt Chrome phiên bản hiện hành
6. WHEN AI_Orchestrator phát hiện mối quan hệ ngữ nghĩa mới giữa các ticket, THE Graph_Engine SHALL phân biệt đường nối ngữ nghĩa (semantic edge) với đường nối tường minh (explicit edge) bằng kiểu đường nét khác nhau (nét đứt vs nét liền)


### Yêu cầu 6: Triển khai Production

**User Story:** Là một DevOps Engineer, tôi muốn có quy trình triển khai tự động và cấu hình production rõ ràng, để ứng dụng có thể vận hành ổn định trên môi trường thực tế.

#### Tiêu chí chấp nhận

1. THE Deployment_Pipeline SHALL hỗ trợ build ứng dụng thành Docker container bao gồm Backend_Server và Frontend_App tĩnh
2. THE Deployment_Pipeline SHALL chạy toàn bộ unit test và E2E test trước khi cho phép deploy lên môi trường production
3. THE Backend_Server SHALL đọc cấu hình (Jira host, AI provider URL, database path, JWT secret) từ biến môi trường (environment variables), không hardcode trong mã nguồn
4. WHILE ứng dụng đang chạy trên production, THE Backend_Server SHALL cung cấp endpoint `/health` trả về trạng thái kết nối đến Jira API, AI provider, và Knowledge_Base
5. IF Backend_Server phát hiện AI provider chính (Ollama) không phản hồi, THEN THE Backend_Server SHALL tự động chuyển sang AI provider dự phòng (Gemini) và ghi log cảnh báo
6. THE Deployment_Pipeline SHALL hỗ trợ chế độ "Air-gapped" cho phép triển khai hoàn toàn nội bộ (on-premise) chỉ sử dụng Ollama, không yêu cầu kết nối internet cho AI

### Yêu cầu 7: Hướng dẫn Sử dụng Toàn diện

**User Story:** Là một người dùng mới, tôi muốn có tài liệu hướng dẫn sử dụng chi tiết và dễ hiểu, để tôi có thể tự học cách sử dụng tất cả các tính năng của Jira Assistant mà không cần hỗ trợ trực tiếp.

#### Tiêu chí chấp nhận

1. THE User_Guide SHALL bao gồm các chương sau: Giới thiệu tổng quan, Cài đặt & Khởi tạo, Dashboard, Mạng lưới Quan hệ (Knowledge Graph), Phân tích Dự án, Trí tuệ Ticket (Ticket Intelligence), Tích hợp AI (Integrations), Quản lý Người dùng & Phân quyền, và Xử lý Sự cố
2. WHEN User_Guide mô tả một thao tác trên giao diện, THE User_Guide SHALL kèm theo ảnh chụp màn hình (screenshot) minh họa bước thực hiện tương ứng
3. THE User_Guide SHALL được viết bằng tiếng Việt, sử dụng ngôn ngữ phổ thông, tránh thuật ngữ kỹ thuật chuyên sâu trừ khi có giải thích kèm theo
4. THE User_Guide SHALL bao gồm bảng thuật ngữ (glossary) giải thích các khái niệm chính: Knowledge Base, Scrum Point, Feature DNA, Semantic Relationship, RBAC
5. WHEN User_Guide mô tả tính năng phân tích AI, THE User_Guide SHALL giải thích rõ sự khác biệt giữa chế độ Local AI (Ollama) và Cloud AI (Gemini), bao gồm ưu nhược điểm và trường hợp sử dụng phù hợp
6. THE User_Guide SHALL bao gồm mục "Câu hỏi Thường gặp" (FAQ) với tối thiểu 10 câu hỏi phổ biến về cách sử dụng và xử lý lỗi
7. THE User_Guide SHALL bao gồm hướng dẫn cấu hình ban đầu cho 3 vai trò: Administrator (thiết lập hệ thống), Neural_Architect (sử dụng phân tích AI), và Reader (xem báo cáo)

### Yêu cầu 8: Phân tích AI & Chiến lược KB-First

**User Story:** Là một Scrum Master, tôi muốn hệ thống phân tích ticket thông minh với chiến lược ưu tiên Knowledge Base, để tiết kiệm thời gian và chi phí gọi AI cho các ticket đã được phân tích trước đó.

#### Tiêu chí chấp nhận

1. WHEN người dùng yêu cầu phân tích ticket, THE AI_Orchestrator SHALL thực hiện theo thứ tự: (a) kiểm tra KB_Repository, (b) nếu không có kết quả thì gọi Jira API lấy dữ liệu, (c) gửi đến AI agent phân tích, (d) lưu kết quả vào KB_Repository
2. THE AI_Orchestrator SHALL tổng hợp dữ liệu từ Summary, Description, Sub-tickets và Attachment metadata của ticket trước khi gửi đến AI agent
3. WHEN AI agent trả về kết quả phân tích, THE AI_Orchestrator SHALL parse kết quả thành 3 phần: Requirement Summary (tóm tắt yêu cầu), Evolution History (lịch sử thay đổi), và Complexity Assessment (đánh giá độ phức tạp kèm Scrum point)
4. IF AI agent trả về JSON không hợp lệ, THEN THE AI_Orchestrator SHALL thực hiện retry tối đa 2 lần với prompt được điều chỉnh, sau đó trả về thông báo lỗi cho người dùng
5. WHEN AI_Orchestrator thực hiện ước lượng Scrum point, THE AI_Orchestrator SHALL chỉ trả về giá trị nằm trong thang điểm cho phép: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40
6. FOR ALL kết quả phân tích hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng tương đương với đối tượng ban đầu (round-trip property)


### Yêu cầu 9: Multi-Agent AI Orchestration & Failover

**User Story:** Là một Developer, tôi muốn hệ thống hỗ trợ nhiều AI provider đồng thời với cơ chế failover tự động, để đảm bảo tính sẵn sàng cao cho các tác vụ phân tích AI.

#### Tiêu chí chấp nhận

1. THE AI_Orchestrator SHALL hỗ trợ cấu hình đồng thời các provider: Ollama (REST local), Gemini (Cloud API), LM Studio (OpenAI-compatible local)
2. WHEN người dùng nhấn "TEST LINK" cho một AI provider, THE AI_Orchestrator SHALL gửi request kiểm tra kết nối và trả về kết quả (active/offline) kèm độ trễ (latency) trong vòng 5 giây
3. IF AI provider đang hoạt động (active provider) không phản hồi trong vòng 30 giây, THEN THE AI_Orchestrator SHALL tự động chuyển sang provider dự phòng tiếp theo trong danh sách ưu tiên
4. WHEN AI_Orchestrator chuyển đổi provider, THE AI_Orchestrator SHALL ghi log sự kiện failover bao gồm: provider cũ, provider mới, lý do chuyển đổi, và timestamp
5. THE AI_Orchestrator SHALL cho phép Administrator cấu hình thứ tự ưu tiên failover của các provider thông qua trang Integrations

### Yêu cầu 10: Serialization Round-Trip cho Dữ liệu Phân tích

**User Story:** Là một Developer, tôi muốn đảm bảo dữ liệu phân tích AI được serialize/deserialize chính xác, để không mất thông tin khi lưu trữ và truyền tải giữa các thành phần hệ thống.

#### Tiêu chí chấp nhận

1. FOR ALL đối tượng ScrumEstimation hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
2. FOR ALL đối tượng NetworkGraph hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
3. FOR ALL đối tượng AIResult hợp lệ (cả Success và Failure), việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
4. FOR ALL đối tượng JiraIssue hợp lệ, việc serialize thành JSON rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu
5. IF dữ liệu JSON đầu vào thiếu trường bắt buộc, THEN THE JSON parser SHALL trả về lỗi mô tả rõ trường bị thiếu thay vì tạo đối tượng với giá trị mặc định sai

