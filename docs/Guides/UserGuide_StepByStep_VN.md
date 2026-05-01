# Hướng Dẫn Sử Dụng Jira Assistant — Từng Bước

**Dự án**: Jira Assistant (AI-Powered)  
**Phiên bản**: 2.0.0 (Obsidian Kinetic)  
**Ngôn ngữ**: Tiếng Việt  
**Đối tượng**: Người dùng mới (Developer, Scrum Master, Product Owner)

---

## Chương 1: Giới thiệu Tổng quan

Jira Assistant là công cụ quản lý dự án Agile tích hợp trí tuệ nhân tạo (AI). Hệ thống giúp bạn:

- **Phân tích DNA Ticket**: AI tự động tóm tắt yêu cầu, theo dõi lịch sử thay đổi, và ước lượng độ phức tạp
- **Vẽ đồ thị mạng lưới**: Hiển thị mối quan hệ giữa các ticket dưới dạng đồ thị trực quan
- **Ước lượng Scrum thông minh**: Đề xuất Story Points dựa trên so sánh với ticket lịch sử

### Đối tượng sử dụng

| Vai trò | Mô tả | Quyền truy cập |
|---------|-------|----------------|
| Administrator | Quản trị hệ thống, cấu hình AI, phân quyền | Toàn quyền |
| Neural Architect | Phân tích ticket, ước lượng, ghi Knowledge Base | Phân tích + Ghi KB |
| Reader | Xem dashboard, đồ thị, báo cáo | Chỉ xem |

### Yêu cầu hệ thống
- Trình duyệt: Google Chrome (phiên bản mới nhất)
- Kết nối: Jira Cloud (có API Token)
- Tùy chọn: Ollama cài đặt trên máy local (cho chế độ AI bảo mật)

---

## Chương 2: Cài đặt & Khởi tạo (Onboarding)

Đây là bước đầu tiên khi sử dụng hệ thống. Bạn cần kết nối với Jira để hệ thống có thể truy cập dữ liệu dự án.

### B01: Mở trang Onboarding
- Truy cập URL hệ thống trong trình duyệt Chrome
- Bạn sẽ thấy màn hình "Initiate Neural Link" với 3 bước: AUTHENTICATION → CONNECTION TEST → SELECT PROJECT

### B02: Nhập thông tin Jira
- **JIRA INSTANCE URL**: Nhập địa chỉ Jira của công ty bạn  
  Ví dụ: `https://company.atlassian.net`
- **API TOKEN**: Nhập API Token (không phải mật khẩu)

> **Cách lấy API Token từ Jira:**
> 1. Đăng nhập Jira → Click avatar góc trên phải → "Manage account"
> 2. Chọn tab "Security" → "Create and manage API tokens"
> 3. Click "Create API token" → Đặt tên → Copy token
> 
> ⚠️ **Lưu ý**: API Token khác với mật khẩu. Không chia sẻ token cho người khác.

### B03: Kết nối
- Nhấn nút **"ESTABLISH CONNECTION"**
- Quan sát thanh tiến trình:
  - 0-30%: "HANDSHAKING WITH JIRA..." — Hệ thống đang bắt tay với Jira
  - 30-70%: "VALIDATING API TOKENS..." — Đang xác minh token
  - 70-100%: "FETCHING PROJECT METADATA..." — Đang tải danh sách dự án

### B04: Chọn dự án
- Sau khi kết nối thành công, danh sách dự án hiển thị dạng lưới 2 cột
- Mỗi ô hiển thị: Mã dự án (ví dụ: JRA) + Tên dự án (ví dụ: Jira Assistant Dev)
- Click vào dự án bạn muốn làm việc → Hệ thống chuyển sang Dashboard

> **Nếu kết nối thất bại**: Kiểm tra lại URL (phải có `https://`) và API Token. Hệ thống sẽ hiển thị thông báo lỗi và cho phép nhập lại.

<!-- Screenshot: Màn hình Onboarding Step 1 (Auth), Step 2 (Progress), Step 3 (Project Select) -->

---

## Chương 3: Dashboard — Tổng quan Dự án

Dashboard là trang chính sau khi đăng nhập, hiển thị tổng quan sức khỏe dự án.

### B01: Đọc 3 chỉ số chính
Phía trên cùng có 3 thẻ thống kê:

| Thẻ | Ý nghĩa | Ví dụ |
|-----|---------|-------|
| PROJECT AI HEALTH | Độ chính xác của mô hình AI | 94.2% (+2.1%) |
| ACTIVE KNOWLEDGE NODES | Số ticket đã được AI phân tích và lưu vào KB | 1,024 / 1.5k |
| NEURAL VELOCITY | Tốc độ xử lý của hệ thống AI | 42.8 STABLE |

### B02: Xem Neural Console
- Cuộn xuống cuối trang để thấy bảng nhật ký hoạt động
- Mỗi dòng log có: thời gian, loại sự kiện, và mô tả
- Ví dụ:
  - `[10:14:02] AI_SYNC — Successfully indexed 42 new tickets from Jira`
  - `[10:14:05] KB_WRITE — Semantic cluster for 'Payment Gateway' updated`
  - `[10:14:10] HEARTBEAT — Ollama node responding at 12ms latency`

### B03: Điều hướng nhanh
- Nhấn **"VIEW GRAPH"** trên thẻ Relationship Network → Chuyển đến trang Đồ thị Mạng lưới
- Nhấn **"ANALYSIS DRIFT"** trên thẻ AI Estimation Drift → Chuyển đến trang Phân tích Dự án
- Hoặc sử dụng thanh sidebar bên trái để đi đến bất kỳ trang nào

### B04: Menu người dùng
- Click vào avatar (ảnh đại diện) góc trên bên phải
- Menu thả xuống hiển thị:
  - **Account Settings** → Trang quản lý người dùng
  - **Security & Permissions** → Trang phân quyền
  - **Sign Out** → Đăng xuất, quay về Onboarding

<!-- Screenshot: Dashboard full view, Neural Console close-up -->

---

## Chương 4: Mạng lưới Quan hệ (Knowledge Graph)

Trang này hiển thị mối quan hệ giữa các ticket dưới dạng đồ thị trực quan.

### B01: Truy cập
- Từ Dashboard: nhấn **"VIEW GRAPH"**
- Hoặc: click **"Relationship Network"** trên sidebar

### B02: Đọc đồ thị
- Mỗi hình lục giác (hexagon) là một Jira ticket
- Mã ticket hiển thị bên trong (ví dụ: NET-458, DB-123, UI-789)

### B03: Hiểu màu sắc node

| Màu | Loại | Ý nghĩa |
|-----|------|---------|
| Xanh cyan (sáng) | Feature | Tính năng chính của hệ thống |
| Xanh dương | Dependency | Phụ thuộc (database, API, thư viện) |
| Tím | UI Module | Thành phần giao diện người dùng |

### B04: Hiểu đường nối

| Kiểu đường | Ý nghĩa |
|------------|---------|
| Nét liền | Liên kết tường minh trong Jira (blocks, relates to) |
| Nét đứt | Quan hệ ngữ nghĩa do AI phát hiện dựa trên nội dung |

> **Semantic Relationship** là gì? Đây là mối quan hệ mà AI phát hiện bằng cách phân tích nội dung ticket — không phải link được tạo thủ công trong Jira. Ví dụ: 2 ticket cùng nói về "authentication" nhưng không link với nhau.

### B05: Xem chi tiết ticket
- Di chuột qua node → Node phóng to nhẹ (1.1x) với viền trắng
- Click vào node → Panel bên phải hiển thị:
  - Mã ticket (ví dụ: NET-458)
  - Tiêu đề (ví dụ: Service Discovery)
  - Mô tả ngắn
  - Nút **"OPEN IN JIRA"** → Mở ticket trong Jira

### B06: Tìm kiếm
- Sử dụng ô tìm kiếm phía trên để lọc ticket theo mã hoặc tên

<!-- Screenshot: Đồ thị với 3 nodes, panel chi tiết bên phải -->

---

## Chương 5: Phân tích Dự án (Project Analysis)

Trang này cung cấp cái nhìn tổng thể về hiệu suất sprint và các vấn đề cần chú ý.

### B01: Truy cập
- Từ Dashboard: nhấn **"ANALYSIS DRIFT"**
- Hoặc: click **"Project Analysis"** trên sidebar

### B02: Đọc 4 chỉ số

| Chỉ số | Ý nghĩa | Ví dụ |
|--------|---------|-------|
| Total Tickets | Tổng số ticket trong dự án | 142 |
| Resolution Rate | Tỷ lệ ticket đã hoàn thành | 88% |
| Cycle Time | Thời gian trung bình từ tạo đến hoàn thành | 12.4 ngày |
| AI Velocity | Tốc độ xử lý AI | 42 |

### B03: Biểu đồ Velocity Trend
- Biểu đồ cột hiển thị story points qua các sprint
- Mỗi cột là 1 sprint — cột cao hơn = sprint hoàn thành nhiều hơn
- Di chuột qua cột để xem chi tiết (cột phóng to nhẹ)

### B04: AI Bottleneck Radar
- Hệ thống AI tự động phát hiện vấn đề:
  - ⚠️ **Cảnh báo**: "Backend Blockers Detected — 4 tickets in 'In Review' for > 72 hours"
  - 🚀 **Cơ hội**: "Optimized Path Found — Sprint could finish 2 days earlier based on current pace"

<!-- Screenshot: Trang Analysis đầy đủ, Bottleneck alerts close-up -->

---

## Chương 6: Trí tuệ Ticket (Ticket Intelligence)

Đây là tính năng cốt lõi — AI phân tích chi tiết từng ticket Jira.

### B01: Truy cập
- Click **"Ticket Intelligence"** trên sidebar

### B02: Nhập Ticket ID
- Gõ mã ticket vào ô tìm kiếm (ví dụ: `NET-458`)
- Nhấn **"ANALYZE DATA"**

### B03: Quan sát quá trình phân tích
- Thanh tiến trình hiển thị 3 giai đoạn:
  1. **0-40%**: "Consolidating Ticket Metadata..." — Thu thập dữ liệu ticket
  2. **40-85%**: "AI RE-ANALYZING SCOPE..." — AI đang phân tích
  3. **85-100%**: "SYNCING TO KNOWLEDGE BASE..." — Lưu kết quả vào KB

> **KB-First**: Hệ thống kiểm tra Knowledge Base trước. Nếu ticket đã được phân tích trước đó, kết quả hiển thị ngay lập tức mà không cần gọi AI. Tiết kiệm thời gian và chi phí.

### B04: Tab "Requirement Context"
- Hiển thị tóm tắt yêu cầu tổng hợp từ Summary, Description, và Sub-tickets
- Danh sách modules bị ảnh hưởng với dấu chấm màu:
  - 🟢 (cyan): Module implementation chính
  - 🔵 (blue): Module phụ thuộc
  - 🟣 (violet): Module liên quan

### B05: Tab "Requirement Evolution"
- Click tab thứ 2 để xem lịch sử thay đổi yêu cầu
- Hiển thị dạng timeline:
  - `[Origin]` — Yêu cầu ban đầu
  - `[v1.2]` — Thay đổi phạm vi
  - `[Current]` — Trạng thái hiện tại

### B06: Tab "Predictive Complexity"
- Click tab thứ 3 để xem ước lượng Scrum Point
- Hiển thị:
  - **Scrum Point**: Số lớn (ví dụ: "8") — độ phức tạp ước tính
  - **Mô tả**: "High Complexity: Cross-Module Handshake"
  - **KB References**: Các ticket tương tự đã phân tích trước đó
    - Ví dụ: "NET-112 (Similarity 92%)", "DB-45 (Logic overlap 75%)"

> **Scrum Point** là gì? Điểm ước lượng độ phức tạp của công việc. Thang điểm: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40. Số càng cao = công việc càng phức tạp.

### B07: Phân tích lại (tùy chọn)
- Nhấn **"RE-ANALYZE"** để buộc AI phân tích lại từ đầu
- Kết quả mới sẽ ghi đè kết quả cũ trong Knowledge Base
- Thanh tiến trình chạy lại 3 giai đoạn

> ⚠️ **Lưu ý**: Nếu bạn có vai trò Reader, nút "ANALYZE DATA" và "RE-ANALYZE" sẽ bị vô hiệu hóa. Bạn chỉ có thể xem kết quả đã có trong KB.

<!-- Screenshot: Input + progress bar, 3 tabs content -->

---

## Chương 7: Tích hợp AI (Integrations)

Trang này quản lý các nhà cung cấp AI.

### B01: Truy cập
- Click **"Integrations"** trên sidebar

### B02: Xem danh sách providers
7 thẻ provider hiển thị trong lưới:

| Provider | Loại | Trạng thái mặc định |
|----------|------|---------------------|
| Ollama (Local) | AI cục bộ | 🟢 Active |
| Google Gemini API | AI đám mây | 🔵 Standby |
| LM Studio | AI cục bộ (OpenAI-compatible) | 🔴 Offline |
| Gemini CLI Interface | AI qua dòng lệnh | 🔵 Standby |
| Copilot CLI (GitHub) | AI qua dòng lệnh | 🔴 Offline |
| Kiro CLI (Amazon) | AI qua dòng lệnh | 🔴 Offline |
| Embedding Model | Mô hình nhúng | 🟢 Active |

### B03: Kiểm tra kết nối
- Nhấn **"TEST LINK"** trên thẻ Ollama
- Nút đổi thành "PROBING..." + thanh tiến trình chạy
- Sau khi hoàn tất, nút trở về bình thường

### B04: Cấu hình Gemini
- Nhập API Key vào ô (hiển thị dạng mật khẩu)
- Chọn Model Tier từ dropdown: Gemini 1.5 Pro / 1.0 Ultra / 1.5 Flash
- Nhấn **"RE-AUTHENTICATE"**

### Hiểu sự khác biệt giữa các AI provider

| | Ollama (Local) | Gemini (Cloud) |
|---|---|---|
| **Chạy ở đâu** | Trên máy tính của bạn | Trên Google Cloud |
| **Bảo mật** | Dữ liệu không rời khỏi mạng nội bộ | Dữ liệu gửi qua internet |
| **Tốc độ** | Phụ thuộc cấu hình máy | Nhanh và ổn định |
| **Chi phí** | Miễn phí | Có phí theo API usage |
| **Phù hợp** | Dự án bảo mật cao | Phân tích phức tạp, dự án lớn |

> ⚠️ **Lưu ý**: Chỉ Administrator mới có quyền thay đổi cấu hình Integrations.

<!-- Screenshot: Integration grid, TEST LINK progress -->

---

## Chương 8: Quản lý Người dùng & Phân quyền

### B01: Truy cập
- Click avatar góc trên phải → Chọn **"Account Settings"**
- Hoặc chọn **"Security & Permissions"**

> Trang này không hiển thị trên sidebar để giữ giao diện gọn gàng. Chỉ Administrator mới truy cập được.

### B02: Xem danh sách người dùng
- Mỗi người dùng hiển thị: ảnh đại diện, tên, email, vai trò hiện tại
- Ví dụ:
  - Sarah Jenkins (You) — Administrator
  - Alex Rivera — Neural Architect
  - Marcus Chen — Reader

### B03: Thay đổi vai trò
- Click dropdown bên cạnh tên người dùng
- Chọn vai trò mới: Administrator / Neural Architect / Reader

### B04: Bật/tắt quyền cụ thể
Panel **"GLOBAL PERMISSIONS"** bên phải có 4 công tắc:

| Quyền | Mặc định | Mô tả |
|-------|----------|-------|
| Trigger AI Scan | BẬT | Cho phép kích hoạt phân tích AI |
| Knowledge Base Write | BẬT | Cho phép ghi vào Knowledge Base |
| Update Integrations | TẮT | Cho phép thay đổi cấu hình AI providers |
| Export Neural Data | BẬT | Cho phép xuất dữ liệu |

- Click công tắc để bật/tắt
- Sidebar hiển thị "IAM SYNC: UPDATING..." + thanh tiến trình
- Sau khi hoàn tất: "SYNC COMPLETE"

### B05: Xem nhật ký thay đổi (Audit Log)
- Cuộn xuống cuối trang để thấy Neural Console
- Mỗi thay đổi quyền được ghi lại:
  - `[09:12:05] IAM_SYNC — Policy 'KB_WRITE' updated for group: ARCHITECTS`
  - `[08:45:12] USER_LOGIN — Sarah Jenkins authenticated via Neural Link`

### Hiểu 3 vai trò

| Vai trò | Xem Dashboard | Phân tích AI | Ghi KB | Cấu hình AI | Quản lý Users |
|---------|:---:|:---:|:---:|:---:|:---:|
| Administrator | ✅ | ✅ | ✅ | ✅ | ✅ |
| Neural Architect | ✅ | ✅ | ✅ | ❌ | ❌ |
| Reader | ✅ | ❌ | ❌ | ❌ | ❌ |

<!-- Screenshot: User list, permission toggles, audit log -->

---

## Chương 9: Xử lý Sự cố (Troubleshooting)

| Vấn đề | Nguyên nhân | Cách xử lý |
|--------|------------|-----------|
| Không kết nối được Jira | Sai Domain hoặc API Token | Kiểm tra lại Domain (phải có `https://`). Tạo API Token mới tại Jira Settings > API Tokens. |
| Ollama không phản hồi | Ollama chưa chạy hoặc sai port | Chạy `ollama serve` trong terminal. Kiểm tra port 11434. |
| AI phân tích quá chậm | Mô hình quá lớn hoặc máy yếu | Chuyển sang mô hình nhỏ hơn (llama3 thay vì llama3-70b). Hoặc dùng Gemini Cloud. |
| Đồ thị không hiển thị | Dự án chưa có ticket | Kiểm tra dự án có ticket trong Jira. Chạy lại phân tích. |
| Không đổi được quyền | Không phải Administrator | Chỉ Administrator mới đổi được. Liên hệ admin của team. |
| Progress bar bị kẹt | Mất kết nối mạng | Kiểm tra kết nối internet. Refresh trang (Ctrl+F5). |
| "RE-ANALYZE" không hoạt động | Role Reader không có quyền | Yêu cầu admin chuyển sang role Architect hoặc Admin. |
| Scrum Point không hợp lý | AI chưa đủ dữ liệu lịch sử | Thêm nhiều ticket vào dự án để AI có nhiều mẫu so sánh. |
| Tab không chuyển được | Lỗi JavaScript | Refresh trang (Ctrl+F5). Kiểm tra Console (F12) để xem lỗi. |
| Sign Out không hoạt động | Cookie/session lỗi | Xóa cache trình duyệt. Đăng nhập lại. |

---

## Chương 10: Bảng Thuật ngữ (Glossary)

| Thuật ngữ | Giải thích |
|-----------|-----------|
| **Knowledge Base (KB)** | Cơ sở dữ liệu lưu trữ kết quả phân tích AI. Hệ thống kiểm tra KB trước khi gọi AI để tiết kiệm thời gian. |
| **Scrum Point** | Điểm ước lượng độ phức tạp của một công việc. Thang điểm: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40. |
| **Feature DNA** | Cách AI phân tích và so sánh "gen" của một yêu cầu với các ticket lịch sử để tìm điểm tương đồng. |
| **Semantic Relationship** | Mối quan hệ giữa các ticket mà AI phát hiện dựa trên nội dung (không phải link tường minh trong Jira). |
| **RBAC** | Role-Based Access Control — phân quyền dựa trên vai trò (Admin, Architect, Reader). |
| **Neural Console** | Bảng nhật ký hoạt động real-time của hệ thống, hiển thị ở cuối mỗi trang. |
| **KB-First Strategy** | Chiến lược ưu tiên: kiểm tra KB trước, chỉ gọi AI khi chưa có kết quả. Tiết kiệm thời gian và chi phí. |
| **Failover** | Cơ chế tự động chuyển sang AI provider dự phòng khi provider chính không phản hồi. |
| **Glassmorphism** | Phong cách thiết kế với hiệu ứng kính mờ (glass effect) và nền mờ (blur). |
| **Ollama** | Phần mềm chạy mô hình AI trên máy tính cá nhân (local). Dữ liệu không rời khỏi máy. |
| **Gemini** | Dịch vụ AI của Google chạy trên cloud. Mạnh hơn nhưng cần kết nối internet. |
| **API Token** | Mã xác thực dùng thay mật khẩu khi kết nối với Jira. Tạo tại Jira Settings > API Tokens. |
