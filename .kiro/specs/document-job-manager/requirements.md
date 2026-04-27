# Document Job Manager — Requirements

## Introduction

Hiện tại, hệ thống sinh tài liệu BRD/FSD/Slides (spec `ai-generated-brd-fsd`) hoạt động đồng bộ trên từng trang Ticket Intelligence — user phải ở lại trang chờ generation hoàn tất, không có cơ chế quản lý job nền, không có dependency chain giữa các loại tài liệu, và tài liệu bị overwrite khi re-generate (mất version cũ). Feature này bổ sung:

1. **Dependency Chain** (BRD → FSD → Slides) với khả năng "Generate All" chạy tuần tự
2. **Background Job Manager** — job chạy server-side, user có thể navigate đi nơi khác và theo dõi tiến trình qua global indicator, pause/cancel job chưa hoàn tất, job tiếp tục chạy khi user logout
3. **Generation Lock** — ngăn user tạo duplicate generation cho cùng ticket + document type khi quay lại trang
4. **Review/Approve Workflow** — tài liệu sinh ra ở trạng thái DRAFT, user review và Approve/Reject trước khi lưu chính thức
5. **Document Versioning** — mỗi lần tài liệu được approve sẽ tạo version mới trong knowledge base, user xem được lịch sử version và so sánh

Feature này mở rộng trên nền tảng `ai-generated-brd-fsd` đã có (DocumentStatusTracker in-memory, DocumentRepository với UNIQUE constraint overwrite, DocumentGenerationFlow polling trên frontend).

## Glossary

- **Job_Manager**: Component server-side quản lý lifecycle của document generation jobs — tạo, theo dõi, pause, cancel, resume. Thay thế DocumentStatusTracker in-memory hiện tại bằng persistent storage (PostgreSQL)
- **Generation_Job**: Một đơn vị công việc sinh tài liệu, chứa thông tin: jobId, ticketId, documentType, status, progress, createdBy, createdAt, updatedAt. Được lưu trong bảng `generation_jobs`
- **Job_Chain**: Một chuỗi Generation_Jobs có dependency tuần tự (BRD → FSD → Slides). Khi job trước hoàn tất, job tiếp theo tự động bắt đầu
- **Job_Status**: Trạng thái của Generation_Job: QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
- **Global_Job_Indicator**: Component frontend hiển thị trên navbar, cho phép user theo dõi trạng thái các jobs đang chạy từ bất kỳ trang nào
- **Generation_Lock**: Cơ chế server-side ngăn tạo duplicate generation job cho cùng ticket + document type khi đã có job đang QUEUED hoặc RUNNING
- **Document_Review**: Quy trình review tài liệu sau khi sinh — user xem nội dung DRAFT và quyết định Approve hoặc Reject
- **Approval_Status**: Trạng thái review của tài liệu: DRAFT (mới sinh, chưa review), APPROVED (đã duyệt), REJECTED (bị từ chối)
- **Document_Version**: Một phiên bản cụ thể của tài liệu đã được approve, lưu với version number tăng dần trong knowledge base
- **Active_Version**: Phiên bản approved mới nhất của một tài liệu — là version được hiển thị mặc định

## Requirements

### Requirement 1: Dependency Chain cho Document Generation (BRD → FSD → Slides)

**User Story:** Là một BA/PM, tôi muốn hệ thống tự động sinh tài liệu theo đúng thứ tự phụ thuộc (BRD trước, FSD dựa trên BRD, Slides dựa trên BRD), để đảm bảo tính nhất quán giữa các tài liệu và tiết kiệm thời gian khi cần sinh tất cả.

#### Acceptance Criteria

1.1 THE Job_Manager SHALL enforce dependency chain: FSD generation chỉ được bắt đầu khi ticket đã có BRD với Approval_Status là APPROVED hoặc DRAFT, và Slides generation chỉ được bắt đầu khi ticket đã có BRD với Approval_Status là APPROVED hoặc DRAFT.

1.2 WHEN người dùng yêu cầu generate FSD mà ticket chưa có BRD (không có document nào hoặc chỉ có BRD với Approval_Status là REJECTED), THE Job_Manager SHALL trả lỗi HTTP 400 với message "BRD phải được sinh trước khi tạo FSD".

1.3 WHEN người dùng yêu cầu generate Slides mà ticket chưa có BRD (không có document nào hoặc chỉ có BRD với Approval_Status là REJECTED), THE Job_Manager SHALL trả lỗi HTTP 400 với message "BRD phải được sinh trước khi tạo Slides".

1.4 WHEN người dùng click nút "Generate All", THE Job_Manager SHALL tạo một Job_Chain gồm 3 Generation_Jobs theo thứ tự: BRD → FSD → Slides, với mỗi job ở trạng thái QUEUED. Job BRD bắt đầu chạy ngay, FSD chỉ chạy khi BRD COMPLETED, Slides chỉ chạy khi FSD COMPLETED.

1.5 IF một job trong Job_Chain thất bại (status FAILED), THEN THE Job_Manager SHALL đánh dấu các jobs còn lại trong chain là CANCELLED và thông báo cho user qua Global_Job_Indicator.

1.6 THE Frontend_App SHALL hiển thị nút "Generate All" bên cạnh các nút generate riêng lẻ trong section DOCUMENT GENERATION, chỉ enabled khi ticket đã có deep analysis.

1.7 WHILE một Job_Chain đang chạy cho một ticket, THE Frontend_App SHALL hiển thị trạng thái của từng job trong chain (BRD: RUNNING, FSD: QUEUED, Slides: QUEUED) trong section DOCUMENT GENERATION.

### Requirement 2: Background Job Manager — Server-Side Persistent Jobs

**User Story:** Là một BA/PM, tôi muốn quá trình sinh tài liệu chạy ở background trên server và tiếp tục ngay cả khi tôi đóng trình duyệt hoặc logout, để tôi không phải chờ đợi và có thể làm việc khác.

#### Acceptance Criteria

2.1 THE Job_Manager SHALL lưu trữ Generation_Job trong bảng `generation_jobs` (PostgreSQL) với các trường: job_id (UUID), ticket_id, document_type, status (QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED), progress_percent (0-100), phase (AGGREGATING_DATA, GENERATING_DOCUMENT, SAVING, COMPLETE), chain_id (nullable UUID — liên kết các jobs trong cùng Job_Chain), created_by (user ID), created_at, updated_at, error_message (nullable).

2.2 WHEN người dùng yêu cầu generate document, THE Job_Manager SHALL tạo Generation_Job mới với status QUEUED, lưu vào database, và trả về job_id cho frontend. Server-side coroutine xử lý job bất đồng bộ — không phụ thuộc vào HTTP connection của user.

2.3 WHILE Generation_Job đang RUNNING, THE Job_Manager SHALL cập nhật progress_percent và phase vào database mỗi khi có thay đổi trạng thái (thay thế DocumentStatusTracker in-memory hiện tại).

2.4 WHEN người dùng logout hoặc đóng trình duyệt, THE Job_Manager SHALL tiếp tục xử lý các Generation_Jobs đang RUNNING hoặc QUEUED trên server mà không bị gián đoạn.

2.5 THE Backend_Server SHALL cung cấp endpoint `GET /api/jobs` trả về danh sách Generation_Jobs của user hiện tại, hỗ trợ filter theo status (active, completed, all). Mặc định trả về jobs có status QUEUED, RUNNING, hoặc PAUSED.

2.6 THE Backend_Server SHALL cung cấp endpoint `GET /api/jobs/{jobId}` trả về chi tiết một Generation_Job bao gồm status, progress_percent, phase, error_message.

2.7 WHEN server khởi động lại, THE Job_Manager SHALL khôi phục các Generation_Jobs có status RUNNING thành QUEUED và đưa vào hàng đợi xử lý lại, đảm bảo không mất job khi server restart.

### Requirement 3: Pause và Cancel Jobs

**User Story:** Là một BA/PM, tôi muốn có thể tạm dừng hoặc hủy các job sinh tài liệu chưa hoàn tất, để tôi kiểm soát được tài nguyên hệ thống và ưu tiên công việc quan trọng hơn.

#### Acceptance Criteria

3.1 THE Backend_Server SHALL cung cấp endpoint `POST /api/jobs/{jobId}/pause` để tạm dừng Generation_Job. Chỉ job có status QUEUED được phép pause (chuyển sang PAUSED). Job đang RUNNING không thể pause vì AI generation đang xử lý.

3.2 THE Backend_Server SHALL cung cấp endpoint `POST /api/jobs/{jobId}/resume` để tiếp tục Generation_Job đã PAUSED. Job chuyển từ PAUSED sang QUEUED và được đưa vào hàng đợi xử lý.

3.3 THE Backend_Server SHALL cung cấp endpoint `POST /api/jobs/{jobId}/cancel` để hủy Generation_Job. Job có status QUEUED, PAUSED, hoặc RUNNING được phép cancel (chuyển sang CANCELLED).

3.4 WHEN một job trong Job_Chain bị cancel, THE Job_Manager SHALL cancel tất cả jobs QUEUED và PAUSED còn lại trong cùng chain. Jobs đã COMPLETED trong chain không bị ảnh hưởng.

3.5 IF người dùng cố pause hoặc cancel job có status COMPLETED, FAILED, hoặc CANCELLED, THEN THE Backend_Server SHALL trả về HTTP 409 Conflict với message mô tả trạng thái hiện tại không cho phép thao tác.

3.6 THE Global_Job_Indicator SHALL hiển thị nút Pause và Cancel cho mỗi job có status QUEUED hoặc PAUSED, cho phép user thao tác trực tiếp từ bất kỳ trang nào.

### Requirement 4: Global Job Indicator trên Frontend

**User Story:** Là một BA/PM, tôi muốn thấy trạng thái các job sinh tài liệu đang chạy từ bất kỳ trang nào trong ứng dụng, để tôi biết tiến trình mà không cần quay lại trang Ticket Intelligence.

#### Acceptance Criteria

4.1 THE Frontend_App SHALL hiển thị Global_Job_Indicator trên navbar (thanh điều hướng trên cùng), hiển thị số lượng jobs đang active (QUEUED + RUNNING) dưới dạng badge số.

4.2 WHEN không có job nào đang active, THE Global_Job_Indicator SHALL ẩn đi (không hiển thị badge hoặc icon).

4.3 WHEN người dùng click vào Global_Job_Indicator, THE Frontend_App SHALL hiển thị dropdown panel liệt kê tất cả jobs đang active với thông tin: ticket ID, document type, status, progress percent. Mỗi job có nút Pause/Resume và Cancel.

4.4 WHILE có ít nhất một job đang RUNNING, THE Global_Job_Indicator SHALL poll endpoint `GET /api/jobs?status=active` mỗi 3 giây để cập nhật trạng thái. Khi không còn job active, polling dừng lại.

4.5 WHEN một job chuyển sang COMPLETED, THE Global_Job_Indicator SHALL hiển thị toast notification "Tài liệu {documentType} cho {ticketId} đã sinh xong" với link để mở preview.

4.6 WHEN một job chuyển sang FAILED, THE Global_Job_Indicator SHALL hiển thị toast notification lỗi "Sinh tài liệu {documentType} cho {ticketId} thất bại: {error_message}" và giữ toast cho đến khi user dismiss.

### Requirement 5: Generation Lock — Ngăn Duplicate Generation

**User Story:** Là một quản trị viên hệ thống, tôi muốn ngăn user tạo duplicate generation job cho cùng một tài liệu đang được sinh, để tránh lãng phí tài nguyên AI và tránh xung đột dữ liệu.

#### Acceptance Criteria

5.1 WHEN người dùng yêu cầu generate document cho ticket + document type đã có Generation_Job với status QUEUED hoặc RUNNING, THE Job_Manager SHALL kiểm tra thời gian cập nhật cuối cùng (updatedAt) của job. IF job đã RUNNING hoặc QUEUED quá 5 phút (stale/zombie job), THEN THE Job_Manager SHALL tự động đánh dấu job cũ là FAILED với message "Stale job auto-recovered after 5m" và cho phép tạo job mới. IF job vẫn còn trong thời hạn, THEN THE Job_Manager SHALL trả về HTTP 409 Conflict với message "Tài liệu đang được sinh, vui lòng chờ hoàn tất" kèm job_id của job hiện tại.

5.2 WHEN người dùng navigate đến trang Ticket Intelligence và chọn ticket đang có Generation_Job active (QUEUED hoặc RUNNING), THE Frontend_App SHALL disable nút Generate tương ứng (opacity 0.5, cursor not-allowed) và hiển thị label "Đang sinh..." kèm progress percent.

5.3 WHEN Generation_Job hoàn tất (COMPLETED hoặc FAILED), THE Frontend_App SHALL tự động enable lại nút Generate tương ứng và cập nhật badge.

5.4 THE Generation_Lock SHALL kiểm tra dựa trên cặp (ticket_id, document_type) — cho phép sinh đồng thời BRD cho ticket A và FSD cho ticket B, nhưng không cho phép sinh 2 BRD cho cùng ticket A.

5.5 WHEN người dùng quay lại trang Ticket Intelligence từ trang khác, THE Frontend_App SHALL gọi endpoint kiểm tra active jobs cho ticket hiện tại và áp dụng Generation_Lock tương ứng trước khi hiển thị section DOCUMENT GENERATION.

### Requirement 6: Review và Approve Workflow

**User Story:** Là một BA/PM, tôi muốn review tài liệu được AI sinh ra trước khi chính thức lưu vào knowledge base, để đảm bảo chất lượng tài liệu và có cơ hội chỉnh sửa trước khi chia sẻ với team.

#### Acceptance Criteria

6.1 WHEN Generation_Job hoàn tất thành công (status COMPLETED), THE Job_Manager SHALL lưu tài liệu với Approval_Status là DRAFT.

6.2 THE Frontend_App SHALL hiển thị badge "DRAFT" (màu vàng) bên cạnh tài liệu chưa được review, badge "APPROVED" (màu xanh) cho tài liệu đã duyệt, và badge "REJECTED" (màu đỏ) cho tài liệu bị từ chối.

6.3 WHEN người dùng mở preview tài liệu có Approval_Status là DRAFT, THE Document_Preview SHALL hiển thị hai nút "Approve" và "Reject" trong metadata bar, bên cạnh nút Export.

6.4 WHEN người dùng click "Approve", THE Backend_Server SHALL cập nhật Approval_Status thành APPROVED, tạo Document_Version mới trong knowledge base (xem Requirement 7), và hiển thị toast "Tài liệu đã được duyệt và lưu vào Knowledge Base".

6.5 WHEN người dùng click "Reject", THE Frontend_App SHALL hiển thị dialog yêu cầu nhập lý do reject (tối thiểu 10 ký tự). THE Backend_Server SHALL cập nhật Approval_Status thành REJECTED và lưu reject_reason.

6.6 THE Backend_Server SHALL cung cấp endpoint `POST /api/documents/{documentId}/approve` và `POST /api/documents/{documentId}/reject` (body: `{"reason": "..."}`) để cập nhật Approval_Status.

6.7 WHEN tài liệu có Approval_Status là REJECTED, THE Frontend_App SHALL cho phép user re-generate tài liệu đó (nút Generate vẫn enabled) để tạo bản DRAFT mới.

6.8 WHILE người dùng có vai trò Reader, THE Frontend_App SHALL ẩn nút Approve và Reject — Reader chỉ được xem preview, không được review.

### Requirement 7: Document Versioning trong Knowledge Base

**User Story:** Là một BA/PM, tôi muốn mỗi lần tài liệu được approve sẽ tạo version mới thay vì overwrite bản cũ, để tôi có thể xem lại lịch sử thay đổi và so sánh giữa các phiên bản.

#### Acceptance Criteria

7.1 THE Backend_Server SHALL mở rộng schema bảng `generated_documents` bỏ UNIQUE constraint (ticket_id, document_type), thêm các trường: approval_status (DRAFT, APPROVED, REJECTED), version_number (integer, tăng dần cho mỗi cặp ticket_id + document_type khi approve), reject_reason (nullable text), reviewed_by (nullable user ID), reviewed_at (nullable timestamp).

7.2 WHEN tài liệu được approve, THE Backend_Server SHALL gán version_number = max(version_number cho cùng ticket_id + document_type) + 1. Version đầu tiên có version_number = 1.

7.3 THE Backend_Server SHALL cung cấp endpoint `GET /api/analysis/{ticketId}/documents/{type}/versions` trả về danh sách tất cả versions đã APPROVED của tài liệu, sắp xếp theo version_number giảm dần, bao gồm metadata (version_number, generatedAt, reviewed_by, reviewed_at, aiProviderUsed).

7.4 THE Backend_Server SHALL cung cấp endpoint `GET /api/analysis/{ticketId}/documents/{type}/versions/{versionNumber}` trả về full content của một version cụ thể.

7.5 WHEN người dùng mở preview tài liệu, THE Document_Preview SHALL hiển thị Active_Version (version approved mới nhất) mặc định. Nếu chưa có version approved nào, hiển thị bản DRAFT mới nhất.

7.6 THE Document_Preview SHALL hiển thị dropdown "Version History" trong metadata bar, cho phép user chọn và xem các version cũ hơn.

7.7 WHEN người dùng chọn một version cũ từ dropdown, THE Document_Preview SHALL hiển thị nội dung version đó với label "Version {N}" và badge "HISTORICAL" để phân biệt với Active_Version.

7.8 THE Backend_Server SHALL cung cấp endpoint `GET /api/analysis/{ticketId}/documents/{type}/diff?v1={version1}&v2={version2}` trả về diff giữa hai versions dưới dạng unified diff format, cho phép user so sánh nội dung thay đổi giữa các phiên bản.

7.9 THE Document_Preview SHALL hiển thị nút "Compare Versions" khi có ít nhất 2 versions approved, mở side-by-side diff view giữa 2 versions được chọn.

### Requirement 8: Cập nhật API Endpoints hiện tại

**User Story:** Là một developer, tôi muốn các API endpoints hiện tại được cập nhật để tương thích với Job Manager mới và versioning, để frontend có thể tích hợp mượt mà mà không break backward compatibility.

#### Acceptance Criteria

8.1 THE Backend_Server SHALL thay thế endpoint `POST /api/analysis/{ticketId}/generate-brd` để tạo Generation_Job thay vì chạy generation trực tiếp. Response trả về `{"jobId": "uuid", "status": "QUEUED"}` thay vì `{"status": "started"}`.

8.2 THE Backend_Server SHALL thay thế endpoint `POST /api/analysis/{ticketId}/generate-fsd` tương tự generate-brd — tạo Generation_Job, trả về jobId.

8.3 THE Backend_Server SHALL thay thế endpoint `POST /api/analysis/{ticketId}/generate-slides` tương tự — tạo Generation_Job, trả về jobId.

8.4 THE Backend_Server SHALL thêm endpoint `POST /api/analysis/{ticketId}/generate-all` để tạo Job_Chain (BRD → FSD → Slides), trả về `{"chainId": "uuid", "jobs": [{"jobId": "...", "documentType": "BRD", "status": "RUNNING"}, ...]}`.

8.5 THE Backend_Server SHALL cập nhật endpoint `GET /api/analysis/{ticketId}/documents/{type}` để trả về Active_Version (bản approved mới nhất) hoặc bản DRAFT mới nhất nếu chưa có version approved. Response bổ sung trường approval_status và version_number. Endpoint hỗ trợ query param `?status=DRAFT` để trả về bản DRAFT mới nhất cụ thể (dùng sau re-generate để mở preview DRAFT thay vì APPROVED).

8.6 THE Backend_Server SHALL cập nhật endpoint `GET /api/analysis/{ticketId}/documents` để trả về metadata bao gồm approval_status, version_number (của Active_Version), và has_draft (boolean — có bản DRAFT chưa review hay không).

8.7 THE Backend_Server SHALL thêm endpoint `GET /api/analysis/{ticketId}/active-jobs` trả về danh sách Generation_Jobs đang active (QUEUED, RUNNING) cho ticket cụ thể, dùng cho Generation_Lock check trên frontend.

### Requirement 9: Cập nhật Frontend — Document Generation Section

**User Story:** Là một BA/PM, tôi muốn giao diện Document Generation được cập nhật để phản ánh trạng thái job, dependency chain, và review workflow, để tôi có trải nghiệm sử dụng mượt mà và rõ ràng.

#### Acceptance Criteria

9.1 WHEN trang Ticket Intelligence load với ticket đã chọn, THE Frontend_App SHALL gọi `GET /api/analysis/{ticketId}/active-jobs` và `GET /api/analysis/{ticketId}/documents` để xác định trạng thái Generation_Lock và hiển thị badges phù hợp (DRAFT/APPROVED/REJECTED).

9.2 THE Frontend_App SHALL hiển thị nút "Generate All" với icon chain (🔗) bên cạnh các nút generate riêng lẻ. Nút "Generate All" chỉ enabled khi ticket đã có deep analysis và không có active jobs.

9.3 WHEN nút Generate FSD bị disable do dependency (chưa có BRD), THE Frontend_App SHALL hiển thị tooltip "Cần sinh BRD trước" khi user hover.

9.4 WHEN có active job cho ticket hiện tại, THE Frontend_App SHALL hiển thị inline progress bar trong section DOCUMENT GENERATION cho mỗi job đang chạy, bao gồm phase label và progress percent.

9.5 WHEN tài liệu có Approval_Status là DRAFT, THE Frontend_App SHALL hiển thị badge "DRAFT" (màu vàng, border rgba(255,180,50,0.3)) bên cạnh nút Generate tương ứng, kèm link để mở preview và review.

9.6 WHEN tài liệu có Approval_Status là APPROVED, THE Frontend_App SHALL hiển thị badge "APPROVED v{N}" (màu xanh, border rgba(45,254,207,0.3)) với version number, kèm link để mở preview. WHEN tài liệu APPROVED có has_draft = true (bản DRAFT mới chưa review), THE Frontend_App SHALL hiển thị thêm badge "DRAFT" (màu vàng) bên cạnh badge APPROVED, kèm link để mở preview DRAFT (dùng `?status=DRAFT`) và review/approve.

9.7 WHEN tài liệu có Approval_Status là REJECTED, THE Frontend_App SHALL hiển thị badge "REJECTED" (màu đỏ, border rgba(255,80,80,0.3)) kèm reject reason truncated (tối đa 50 ký tự), và nút Generate vẫn enabled để re-generate.
