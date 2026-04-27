# Requirements Document — Document Generation UX Improvement

## Introduction

Hiện tại, UX của quá trình sinh tài liệu (BRD/FSD/Slides) trên trang Ticket Intelligence rất kém. Khi user click "Generate BRD", progress bar hiển thị "GENERATING_DOCUMENT 35%" và đứng yên trong 1-5 phút mà không có bất kỳ thông tin nào thêm. User không biết hệ thống đang làm gì, đã chờ bao lâu, còn bao lâu nữa, và không thể hủy job. Khi job timeout hoặc fail, user không thấy thông báo lỗi rõ ràng, nút Generate bị kẹt ở trạng thái disabled.

Feature này cải thiện toàn diện UX của document generation bằng cách:

1. **Granular progress tracking** — Backend cập nhật progress liên tục qua nhiều sub-phase thay vì đặt 35% một lần rồi chờ
2. **Elapsed time và estimated remaining** — Frontend hiển thị thời gian đã chờ và ước tính thời gian còn lại
3. **Phase detail** — Hiển thị chi tiết bước đang thực hiện (thu thập dữ liệu, gọi AI, phân tích response, lưu)
4. **Timeout warning** — Cảnh báo user khi job gần hết thời gian timeout (5 phút)
5. **Cancel button** — User có thể hủy job đang chạy từ inline progress UI
6. **Error detail** — Hiển thị thông báo lỗi cụ thể khi generation fail
7. **Animated progress** — Progress bar có animation mượt thay vì nhảy số cứng

## Glossary

- **Progress_Tracker**: Component backend chịu trách nhiệm cập nhật progress_percent và phase của Generation_Job theo từng sub-phase trong quá trình thực thi, thay thế cách cập nhật thủ công rời rạc hiện tại trong JobExecutor
- **Sub_Phase**: Một bước nhỏ trong quá trình generation, ví dụ: AGGREGATING_DATA (0-30%), GENERATING_DOCUMENT (30-85%), PARSING_RESPONSE (85-90%), SAVING (90-100%). Mỗi sub_phase có label mô tả hiển thị cho user
- **Phase_Label**: Chuỗi text mô tả thân thiện với user cho mỗi sub_phase, ví dụ: "Thu thập dữ liệu ticket...", "Đang gọi AI sinh tài liệu...", "Phân tích kết quả...", "Đang lưu tài liệu..."
- **Elapsed_Timer**: Component frontend hiển thị thời gian đã trôi qua kể từ khi job bắt đầu, cập nhật mỗi giây, format "Xm Ys"
- **Timeout_Warning**: Cảnh báo hiển thị trên frontend khi job đã chạy quá 80% thời gian timeout (4 phút / 5 phút), thông báo cho user rằng job có thể bị timeout
- **Inline_Progress_UI**: Component frontend hiển thị trong section DOCUMENT GENERATION cho mỗi job đang chạy, bao gồm: progress bar, phase label, elapsed timer, cancel button, và timeout warning
- **Heartbeat_Progress**: Cơ chế backend tăng progress_percent nhỏ (1-2%) mỗi 15 giây trong phase GENERATING_DOCUMENT khi chờ AI response, để frontend thấy progress bar vẫn đang di chuyển thay vì đứng yên
- **Generation_Job**: Đơn vị công việc sinh tài liệu, đã định nghĩa trong document-job-manager spec, chứa jobId, ticketId, documentType, status, progressPercent, phase, errorMessage
- **Job_Manager**: Component server-side quản lý lifecycle của Generation_Job, đã định nghĩa trong document-job-manager spec

## Requirements

### Requirement 1: Granular Progress Tracking trên Backend

**User Story:** Là một BA/PM, tôi muốn thấy progress bar di chuyển liên tục trong suốt quá trình sinh tài liệu, để tôi biết hệ thống đang hoạt động và không bị treo.

#### Acceptance Criteria

1.1 WHILE Generation_Job đang ở phase AGGREGATING_DATA, THE Progress_Tracker SHALL cập nhật progress_percent từ 0% đến 30% theo các mốc: 5% khi bắt đầu thu thập, 15% khi đã lấy main ticket data, 25% khi đã lấy linked ticket data, 30% khi hoàn tất aggregation.

1.2 WHILE Generation_Job đang ở phase GENERATING_DOCUMENT và chờ AI response, THE Progress_Tracker SHALL tăng progress_percent thêm 1% mỗi 15 giây (Heartbeat_Progress), bắt đầu từ 35% và không vượt quá 80%, để frontend thấy progress bar vẫn di chuyển.

1.3 WHEN AI response được nhận thành công, THE Progress_Tracker SHALL cập nhật progress_percent lên 85% và chuyển phase sang PARSING_RESPONSE.

1.4 WHILE Generation_Job đang ở phase PARSING_RESPONSE, THE Progress_Tracker SHALL cập nhật progress_percent từ 85% lên 90% khi parse hoàn tất.

1.5 WHILE Generation_Job đang ở phase SAVING, THE Progress_Tracker SHALL cập nhật progress_percent từ 90% lên 95% khi bắt đầu lưu và 100% khi lưu hoàn tất.

1.6 THE Progress_Tracker SHALL ghi nhận thời điểm bắt đầu job (startedAt) vào Generation_Job khi job chuyển từ QUEUED sang RUNNING, để frontend tính elapsed time chính xác.

### Requirement 2: Phase Label thân thiện với User

**User Story:** Là một BA/PM, tôi muốn thấy mô tả rõ ràng bằng tiếng Việt về bước đang thực hiện thay vì mã kỹ thuật như "GENERATING_DOCUMENT", để tôi hiểu hệ thống đang làm gì.

#### Acceptance Criteria

2.1 THE Backend_Server SHALL bổ sung trường phaseLabel vào response của endpoint `GET /api/jobs/{jobId}`, chứa Phase_Label tương ứng với phase hiện tại: "Thu thập dữ liệu ticket..." cho AGGREGATING_DATA, "Đang gọi AI sinh tài liệu..." cho GENERATING_DOCUMENT, "Phân tích kết quả AI..." cho PARSING_RESPONSE, "Đang lưu tài liệu..." cho SAVING, "Hoàn tất" cho COMPLETE.

2.2 THE Inline_Progress_UI SHALL hiển thị phaseLabel thay vì phase code kỹ thuật trong progress bar label.

2.3 WHEN phase là GENERATING_DOCUMENT và Heartbeat_Progress đang hoạt động, THE Inline_Progress_UI SHALL hiển thị phaseLabel kèm animated dots ("Đang gọi AI sinh tài liệu...") để user thấy hệ thống vẫn đang xử lý.

### Requirement 3: Elapsed Time và Timeout Warning trên Frontend

**User Story:** Là một BA/PM, tôi muốn biết tôi đã chờ bao lâu và được cảnh báo khi job sắp hết thời gian, để tôi quyết định có nên tiếp tục chờ hay hủy job.

#### Acceptance Criteria

3.1 THE Inline_Progress_UI SHALL hiển thị Elapsed_Timer bên cạnh progress bar, cập nhật mỗi giây, format "Xm Ys" (ví dụ: "1m 23s", "0m 05s"), tính từ thời điểm job bắt đầu (startedAt từ backend).

3.2 WHEN elapsed time vượt quá 4 phút (80% của timeout 5 phút), THE Inline_Progress_UI SHALL hiển thị Timeout_Warning với text "⚠️ Job sắp hết thời gian (5 phút). Cân nhắc hủy và thử lại." bằng màu vàng cảnh báo (color: var(--warning-color)).

3.3 WHEN Generation_Job chuyển sang status FAILED với errorMessage chứa "timed out", THE Inline_Progress_UI SHALL hiển thị thông báo "⏱️ Job đã hết thời gian sau 5 phút. Vui lòng thử lại." thay vì message lỗi kỹ thuật.

3.4 THE Elapsed_Timer SHALL dừng đếm khi job chuyển sang status COMPLETED, FAILED, hoặc CANCELLED.

### Requirement 4: Cancel Button trong Inline Progress UI

**User Story:** Là một BA/PM, tôi muốn có thể hủy job sinh tài liệu đang chạy trực tiếp từ section Document Generation, để tôi không phải chờ đợi khi thấy job chạy quá lâu.

#### Acceptance Criteria

4.1 THE Inline_Progress_UI SHALL hiển thị nút Cancel (icon ✕ hoặc "Hủy") bên cạnh progress bar cho mỗi job đang có status QUEUED hoặc RUNNING.

4.2 WHEN user click nút Cancel, THE Frontend_App SHALL gọi endpoint `POST /api/jobs/{jobId}/cancel` và hiển thị BlockingOverlay với message "Đang hủy..." trong khi chờ response.

4.3 WHEN cancel thành công, THE Inline_Progress_UI SHALL ẩn progress bar, enable lại nút Generate tương ứng, và hiển thị toast "Job đã được hủy".

4.4 IF cancel thất bại (HTTP 409 — job đã hoàn tất), THEN THE Frontend_App SHALL hiển thị toast thông báo "Job đã hoàn tất, không thể hủy" và refresh badges.

4.5 THE Cancel button SHALL bị disable trong 2 giây sau khi click để ngăn double-click.

### Requirement 5: Error Detail khi Generation Fail

**User Story:** Là một BA/PM, tôi muốn thấy thông báo lỗi cụ thể và hành động gợi ý khi sinh tài liệu thất bại, để tôi biết nguyên nhân và cách khắc phục.

#### Acceptance Criteria

5.1 WHEN Generation_Job chuyển sang status FAILED, THE Inline_Progress_UI SHALL hiển thị error panel với: icon lỗi (❌), errorMessage từ backend, và nút "Thử lại" để re-generate.

5.2 THE Backend_Server SHALL cung cấp errorMessage mô tả cụ thể cho từng loại lỗi: "AI provider không phản hồi — kiểm tra kết nối Integrations" cho connection error, "AI response không hợp lệ — thử lại" cho parse error, "Job đã hết thời gian sau 5 phút — thử lại" cho timeout, "Không tìm thấy BRD — cần sinh BRD trước" cho dependency error.

5.3 WHEN user click nút "Thử lại" trong error panel, THE Frontend_App SHALL dismiss error panel và bắt đầu generation mới cho cùng ticket và document type.

5.4 THE error panel SHALL tự động ẩn sau 30 giây hoặc khi user dismiss bằng nút close (✕), và nút Generate tương ứng SHALL được enable lại.

5.5 IF Generation_Job fail do AI provider error, THEN THE error panel SHALL hiển thị link "Kiểm tra Integrations" dẫn đến trang Integrations (#integrations) để user kiểm tra cấu hình provider.

### Requirement 6: Animated Progress Bar và Smooth Transitions

**User Story:** Là một BA/PM, tôi muốn progress bar có animation mượt mà thay vì nhảy số cứng, để trải nghiệm chờ đợi bớt khó chịu hơn.

#### Acceptance Criteria

6.1 THE Inline_Progress_UI SHALL áp dụng CSS transition cho progress bar fill width với duration 0.5s và easing ease-in-out, để progress bar di chuyển mượt khi percent thay đổi.

6.2 WHILE Generation_Job đang ở phase GENERATING_DOCUMENT, THE Inline_Progress_UI SHALL hiển thị shimmer animation (gradient di chuyển từ trái sang phải) trên progress bar fill để biểu thị hệ thống đang xử lý.

6.3 WHEN Generation_Job chuyển sang status COMPLETED, THE Inline_Progress_UI SHALL hiển thị progress bar 100% với màu xanh thành công (var(--accent-green)) trong 1.5 giây trước khi ẩn progress bar và hiển thị badge kết quả.

6.4 WHEN Generation_Job chuyển sang status FAILED, THE Inline_Progress_UI SHALL chuyển progress bar fill sang màu đỏ (var(--accent-red)) trước khi hiển thị error panel.

### Requirement 7: Cập nhật GenerationJobDto và Polling

**User Story:** Là một developer, tôi muốn DTO và polling mechanism được cập nhật để hỗ trợ các trường mới (phaseLabel, startedAt), để frontend có đủ dữ liệu render UX cải tiến.

#### Acceptance Criteria

7.1 THE Backend_Server SHALL bổ sung trường startedAt (nullable String, ISO timestamp) vào GenerationJobDto response, chứa thời điểm job chuyển từ QUEUED sang RUNNING.

7.2 THE Backend_Server SHALL bổ sung trường phaseLabel (String) vào GenerationJobDto response, chứa Phase_Label tương ứng với phase hiện tại.

7.3 THE Frontend_App SHALL cập nhật GenerationJobDto model để bao gồm startedAt và phaseLabel.

7.4 WHILE có job đang active cho ticket hiện tại, THE Inline_Progress_UI SHALL poll endpoint `GET /api/jobs/{jobId}` mỗi 2 giây để cập nhật progress, phase, phaseLabel, và tính elapsed time từ startedAt.

7.5 WHEN poll response cho thấy job đã COMPLETED hoặc FAILED, THE Frontend_App SHALL dừng polling, cập nhật UI tương ứng (success hoặc error), và refresh badges.

### Requirement 8: Button State Recovery sau Failure

**User Story:** Là một BA/PM, tôi muốn nút Generate luôn trở về trạng thái bình thường sau khi job kết thúc (dù thành công hay thất bại), để tôi có thể thử lại mà không cần refresh trang.

#### Acceptance Criteria

8.1 WHEN Generation_Job chuyển sang status COMPLETED, FAILED, hoặc CANCELLED, THE Frontend_App SHALL enable lại nút Generate tương ứng với text gốc ("GENERATE BRD" hoặc "RE-GENERATE BRD") trong vòng 2 giây.

8.2 WHEN user navigate đến trang Ticket Intelligence và có Generation_Job với status FAILED hoặc CANCELLED cho ticket hiện tại, THE Frontend_App SHALL hiển thị nút Generate ở trạng thái enabled (không bị kẹt ở "Đang sinh...").

8.3 WHEN polling phát hiện job không còn tồn tại (HTTP 404) hoặc server không phản hồi (network error), THE Frontend_App SHALL dừng polling, enable lại nút Generate, và hiển thị toast "Mất kết nối — vui lòng thử lại".

8.4 THE Frontend_App SHALL kiểm tra và recovery button state mỗi khi section DOCUMENT GENERATION được render (bao gồm khi navigate từ trang khác về).
