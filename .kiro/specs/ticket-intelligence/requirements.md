# Ticket Intelligence — Requirements

# Yêu cầu 5: Ticket Intelligence — Phân tích AI Ticket (MH5)

**User Story:** Là một Scrum Master, tôi muốn phân tích ticket bằng AI để nhận được tóm tắt yêu cầu, lịch sử thay đổi và ước lượng Scrum point, giúp tôi hiểu sâu ticket trước khi lập kế hoạch sprint.

## Tiêu chí chấp nhận

1. THE Frontend_App SHALL cung cấp trường chọn ticket dạng combobox (searchable dropdown) trên trang Ticket Intelligence, cho phép người dùng tìm kiếm ticket theo ID hoặc summary, với danh sách gợi ý được lấy từ dự án hiện tại. Ngoài ra, combobox SHALL cho phép người dùng nhập bất kỳ ticket ID hợp lệ (format PROJECT-NUMBER) từ bất kỳ project nào — khi ticket ID không thuộc project hiện tại, hệ thống tạo synthetic TicketAnalysisStatus với state NOT_ANALYZED và cho phép ANALYZE
2. WHEN người dùng nhấn "ANALYZE DATA", THE AI_Orchestrator SHALL thực hiện chiến lược KB-First: kiểm tra Knowledge_Base trước, chỉ gọi AI agent khi không tìm thấy kết quả đã lưu
3. WHILE AI_Orchestrator đang xử lý phân tích, THE Frontend_App SHALL hiển thị thanh tiến trình 4 giai đoạn: "Fetching Jira Data..." (0-20%), "Extracting Content..." (20-35%), "AI Analyzing Scope..." (35-85%), "Syncing to Knowledge Base..." (85-100%). Frontend sử dụng fire-and-forget pattern: POST /reanalyze trả về 202 Accepted ngay, frontend polling status mỗi 3s qua window.fetch (không dùng ktor-client-js). *(Cập nhật: chuyển từ long-running request + polling fallback sang fire-and-forget + polling-only để tránh ktor-client-js coroutine timeout)*
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

11. WHEN người dùng chọn một ticket từ combobox, THE Frontend_App SHALL hiển thị trạng thái phân tích của ticket đó: "Chưa phân tích" (NOT_ANALYZED), "Đã quét" (SCANNED — batch scan đã chạy nhưng chưa deep analyze), "Đã phân tích" (ANALYZED — deep analysis đầy đủ), "Có cập nhật mới" (HAS_UPDATES), hoặc "Đang phân tích" (ANALYZING)
12. WHEN ticket được chọn có trạng thái "Chưa phân tích" hoặc "Đã quét", THE Frontend_App SHALL hiển thị nút hành động với nhãn "ANALYZE"
13. WHEN ticket được chọn có trạng thái "Có cập nhật mới" (ticket đã thay đổi sau lần phân tích cuối), THE Frontend_App SHALL hiển thị nút hành động với nhãn "RE-ANALYZE"
14. WHILE ticket được chọn đang trong quá trình phân tích (trạng thái "Đang phân tích"), THE Frontend_App SHALL vô hiệu hóa nút hành động (disabled) với nhãn "ANALYZING..." và hiển thị spinner
15. WHEN người dùng nhập ký tự vào combobox, THE Frontend_App SHALL lọc danh sách ticket theo ticket ID hoặc summary với độ trễ debounce tối đa 300ms
15.1 WHEN người dùng gõ chính xác một ticket ID hợp lệ vào combobox và click ra ngoài (blur/click outside) mà không chọn từ dropdown, THE Frontend_App SHALL: (a) nếu ticket ID khớp với ticket trong danh sách project hiện tại → tự động chọn ticket đó, cập nhật selectedTicket, hiển thị nội dung phân tích; (b) nếu ticket ID hợp lệ (format PROJECT-NUMBER) nhưng không thuộc project hiện tại → accept as cross-project ticket, tạo synthetic TicketAnalysisStatus với state NOT_ANALYZED, enable ANALYZE button
15.2 WHEN người dùng gõ text không khớp với bất kỳ ticket ID hợp lệ nào (không match regex ^[A-Z][A-Z0-9]+-\d+$) và click ra ngoài, THE Frontend_App SHALL khôi phục combobox về trạng thái ticket đã chọn trước đó (nếu có) hoặc xóa text nếu chưa chọn ticket nào

---

## Liên kết Spec

> **Nâng cấp phân tích sâu**: Spec `ticket-intelligence-deep-analysis` đã được merge vào spec này (xem phần Deep Analysis bên dưới).

---

# Deep Analysis — Nâng cấp Pipeline Phân tích Sâu

## Giới thiệu

Module Ticket Intelligence hiện tại cho kết quả phân tích nông và không chính xác. Prompt AI quá chung chung, backend không truyền nội dung ticket thực tế khi gọi AI, và data model chỉ lưu một chuỗi tóm tắt đơn giản. Deep Analysis nâng cấp toàn bộ pipeline phân tích: trích xuất dữ liệu Jira có cấu trúc, xây dựng prompt AI chi tiết, mở rộng data model, và hiển thị kết quả phân tích sâu trên giao diện.

### Ràng buộc kiến trúc: Chia sẻ pipeline với Dashboard Scan

Deep Analysis PHẢI nâng cấp pipeline phân tích chung (`AIOrchestrator.analyzeTicket()`) mà cả Ticket Intelligence lẫn Dashboard Batch Scan đều sử dụng. `BatchScanEngine.processTicket()` KHÔNG cần thay đổi — bên trong `analyzeTicket()` đã được nâng cấp. KB-First strategy, failover, retry, semaphore — tất cả giữ nguyên.

## Thuật ngữ bổ sung

- **Deep_Analysis_Engine**: Module xử lý phân tích sâu ticket
- **Jira_Content_Extractor**: Component trích xuất nội dung có cấu trúc từ Jira ticket description
- **Deep_Analysis_Prompt_Builder**: Component xây dựng prompt AI chi tiết
- **Analysis_Result_Model**: Cấu trúc dữ liệu mở rộng lưu trữ kết quả phân tích sâu

### Yêu cầu 16: Trích xuất nội dung Jira có cấu trúc

**User Story:** Là một Scrum Master, tôi muốn hệ thống trích xuất đầy đủ và có cấu trúc nội dung từ Jira ticket, để AI có đủ ngữ cảnh thực tế khi phân tích thay vì tạo nội dung bịa đặt.

#### Tiêu chí chấp nhận

16.1 WHEN người dùng yêu cầu phân tích một ticket (qua Ticket Intelligence hoặc Dashboard Batch Scan), THE Jira_Content_Extractor SHALL thay thế hàm `fetchTicketContent()` hiện tại trong pipeline, gọi Jira REST API để lấy đầy đủ dữ liệu ticket bao gồm summary, description, status, priority, story points, issue type, assignee, reporter, created date, updated date, labels, và components
16.2 WHEN ticket description chứa nội dung Atlassian Document Format (ADF), THE Jira_Content_Extractor SHALL parse ADF thành các section có cấu trúc, nhận diện headings, bullet lists, tables, code blocks, và panels thay vì chỉ trích xuất plain text
16.3 WHEN ticket có sub-tasks, THE Jira_Content_Extractor SHALL lấy key, summary và status của từng sub-task và đưa vào ngữ cảnh phân tích. Trường `key` cũng được sử dụng bởi CascadingAnalysisEngine để phát hiện ticket liên quan (Req 26.1)
16.4 WHEN ticket có issue links (blocks, is blocked by, relates to, duplicates), THE Jira_Content_Extractor SHALL lấy key, summary, và relationship type của từng linked ticket. Keys từ issue links, sub-tasks, và parent ticket PHẢI được validate theo Jira ticket key format (`[A-Z][A-Z0-9]+-\d+`) trước khi thực hiện API call — keys không hợp lệ SHALL bị filter ra và log warning (xem bugfix spec `invalid-jira-key-fetch`)
16.5 WHEN ticket có attachments, THE Jira_Content_Extractor SHALL lấy danh sách attachment metadata bao gồm filename, mime type, và size
16.6 WHEN ticket có comments, THE Jira_Content_Extractor SHALL lấy tối đa 20 comments gần nhất bao gồm author, created date, và nội dung comment
16.7 WHEN ticket có changelog (history), THE Jira_Content_Extractor SHALL lấy lịch sử thay đổi các field quan trọng: status, priority, story points, assignee, và summary
16.8 THE Jira_Content_Extractor SHALL tổng hợp tất cả dữ liệu trích xuất thành một đối tượng StructuredTicketContent, bao gồm cả trường `parentKey` (key của parent ticket) để hỗ trợ cascading analysis (Req 26.1)

### Yêu cầu 17: Nhận diện và phân loại section trong ticket description

**User Story:** Là một Scrum Master, tôi muốn hệ thống nhận diện các section kỹ thuật trong ticket description (As-Is/To-Be, Technical Changes, API endpoints, DB schema), để kết quả phân tích phản ánh đúng nội dung thực tế của ticket.

#### Tiêu chí chấp nhận

17.1 WHEN ticket description chứa các heading hoặc pattern nhận diện được (ví dụ: "As-Is", "To-Be", "Current State", "Expected State"), THE Jira_Content_Extractor SHALL phân loại nội dung dưới heading đó vào section tương ứng
17.2 WHEN ticket description chứa thông tin API (pattern nhận diện: HTTP methods, URL paths dạng /api/..., request/response body), THE Jira_Content_Extractor SHALL trích xuất thành danh sách API specifications với method, path, và mô tả
17.3 WHEN ticket description chứa thông tin database (pattern nhận diện: tên bảng, column definitions, SQL statements, migration scripts), THE Jira_Content_Extractor SHALL trích xuất thành danh sách database changes với table name, operation type (CREATE, ALTER, DROP), và chi tiết columns
17.4 WHEN ticket description chứa thông tin tích hợp với hệ thống bên ngoài (tên service, protocol, endpoint), THE Jira_Content_Extractor SHALL trích xuất thành danh sách external dependencies
17.5 WHEN ticket description chứa acceptance criteria hoặc definition of done, THE Jira_Content_Extractor SHALL trích xuất thành danh sách acceptance criteria riêng biệt
17.6 IF ticket description không chứa section có cấu trúc rõ ràng, THEN THE Jira_Content_Extractor SHALL giữ nguyên toàn bộ description text trong trường raw_description và đánh dấu extraction_confidence là LOW

### Yêu cầu 18: Xây dựng prompt AI chi tiết cho phân tích sâu

**User Story:** Là một Scrum Master, tôi muốn AI nhận được prompt chi tiết với đầy đủ ngữ cảnh ticket thực tế, để kết quả phân tích chính xác và có chiều sâu.

#### Tiêu chí chấp nhận

18.1 THE Deep_Analysis_Prompt_Builder SHALL thay thế hàm `buildAnalysisPrompt()` hiện tại trong `AIOrchestratorImpl`, xây dựng prompt bao gồm tất cả dữ liệu từ StructuredTicketContent. Pipeline chung đảm bảo cả Ticket Intelligence lẫn Batch Scan đều sử dụng prompt mới
18.2 THE Deep_Analysis_Prompt_Builder SHALL yêu cầu AI phân tích theo 6 khía cạnh riêng biệt: (a) Tóm tắt yêu cầu nghiệp vụ, (b) Acceptance criteria, (c) Chi tiết kỹ thuật (API, DB, integrations), (d) Dependencies và risks, (e) Lịch sử thay đổi có phân tích impact, (f) Đánh giá complexity với rationale chi tiết
18.3 WHEN StructuredTicketContent chứa API specifications, THE Deep_Analysis_Prompt_Builder SHALL đưa danh sách API vào prompt và yêu cầu AI đánh giá tính đầy đủ
18.4 WHEN StructuredTicketContent chứa database changes, THE Deep_Analysis_Prompt_Builder SHALL đưa DB schema vào prompt và yêu cầu AI đánh giá data model
18.5 THE Deep_Analysis_Prompt_Builder SHALL chỉ định rõ output format JSON với schema cụ thể
18.6 THE Deep_Analysis_Prompt_Builder SHALL bao gồm instruction yêu cầu AI chỉ phân tích dựa trên dữ liệu ticket thực tế, không được bịa đặt

### Yêu cầu 19: Mở rộng data model kết quả phân tích

**User Story:** Là một Scrum Master, tôi muốn kết quả phân tích được lưu trữ với cấu trúc chi tiết bao gồm requirements, technical specs, và dependencies.

#### Tiêu chí chấp nhận

19.1 THE Analysis_Result_Model SHALL mở rộng RequirementSummary để bao gồm: business_summary, as_is_state, to_be_state, và extracted_requirements
19.2 THE Analysis_Result_Model SHALL bao gồm trường technical_details chứa: api_specifications, database_changes, và external_integrations
19.3 THE Analysis_Result_Model SHALL bao gồm trường acceptance_criteria chứa danh sách tiêu chí chấp nhận với id, description, và testability_assessment
19.4 THE Analysis_Result_Model SHALL bao gồm trường dependencies chứa: blocking_issues, related_issues, và external_dependencies
19.5 THE Analysis_Result_Model SHALL bao gồm trường analysis_metadata chứa: extraction_confidence, analyzed_at, ai_provider_used, và prompt_version
19.6 THE Analysis_Result_Model SHALL duy trì backward compatibility — các trường mới là optional với giá trị mặc định

### Yêu cầu 20: Mở rộng Knowledge Base để lưu trữ phân tích sâu

#### Tiêu chí chấp nhận

20.1 THE Knowledge_Base SHALL mở rộng KBRecord để lưu trữ tất cả trường mới từ Analysis_Result_Model
20.2 WHEN AI_Orchestrator lưu kết quả phân tích sâu vào Knowledge_Base, THE Knowledge_Base SHALL serialize toàn bộ cấu trúc dữ liệu mở rộng
20.3 WHEN AI_Orchestrator đọc kết quả từ Knowledge_Base cache, THE Knowledge_Base SHALL deserialize đầy đủ cấu trúc dữ liệu mở rộng
20.4 IF KBRecord cũ thiếu các trường mới, THEN THE Knowledge_Base SHALL trả về giá trị mặc định thay vì gây lỗi deserialization

### Yêu cầu 21: Nâng cấp AIOrchestrator.analyzeTicket() — Pipeline chung

#### Tiêu chí chấp nhận

21.1 THE AIOrchestratorImpl.analyzeTicket() SHALL được nâng cấp để gọi Jira_Content_Extractor lấy StructuredTicketContent, sau đó dùng Deep_Analysis_Prompt_Builder xây dựng prompt. Deep analysis components (JiraContentExtractor, DeepAnalysisPromptBuilder, DeepAnalysisResponseParser) là optional constructor dependencies — khi không được inject, AIOrchestratorImpl fallback sang legacy prompt/parse pipeline để đảm bảo backward compatibility
21.2 THE BatchScanEngine.processTicket() SHALL KHÔNG cần thay đổi — bên trong `analyzeTicket()` đã được nâng cấp
21.3 THE BatchScanEngine.fetchTicketContentForBatch() SHALL gọi `jiraContentExtractor.extract(ticketId)` khi extractor available, chuyển đổi `StructuredTicketContent` thành plain-text string (summary + description + sub-tasks + comments, capped 3000 chars) qua helper `formatStructuredContent()`. Khi extractor throw exception, fallback sang `fetchLegacyBatchContent()` (summary + description từ Jira API) với log message. Khi extractor không được inject (null), sử dụng `fetchLegacyBatchContent()`. BatchScanEngine nhận `jiraContentExtractor: JiraContentExtractor?` là optional constructor parameter. *(Cập nhật bởi bugfix spec `batch-scan-placeholder-analysis` — trước đó trả về empty string khi extractor available, gây ra placeholder results trong batch mode)*
21.4 WHILE phân tích đang xử lý qua AnalysisRoutes, THE AnalysisRoutes SHALL cập nhật AnalysisStatusTracker với 4 giai đoạn: FETCHING_JIRA (0-20%), EXTRACTING_CONTENT (20-35%), AI_ANALYZING (35-85%), KB_SYNCING (85-100%). Phase COMPLETE chỉ được set SAU KHI tất cả processing hoàn tất — bao gồm AI analysis, KB save, root ticket attachment processing, và linked ticket attachment processing. *(Cập nhật bởi feature spec `linked-ticket-attachments` — COMPLETE phải đợi attachment processing xong để frontend blocking layer không tắt sớm)*
21.5 IF Jira API không khả dụng, THEN THE analyzeTicket() SHALL fallback sang KB cache hoặc trả lỗi rõ ràng
21.6 THE KB-First strategy, provider failover, retry logic, và AI semaphore SHALL được giữ nguyên. KB-First SHALL skip cache khi record là error (requirementSummary bắt đầu bằng "Error:" hoặc blank) — đảm bảo tickets có error records từ lần analyze fail trước sẽ được re-analyze tự động khi scan lại
21.7 WHEN batch analysis (batch_prompt_size > 1) trả về kết quả có `requirementSummary` là placeholder pattern (`"..."`, chứa `"Placeholder"` case-insensitive, hoặc length < 10 ký tự), THE BatchScanEngine SHALL skip lưu placeholder results vào KB và fallback sang single-ticket analysis (`processTicket()`) cho các ticket bị ảnh hưởng. Helper `isPlaceholderResult()` trong `BatchContentFormatter.kt` thực hiện detection. *(Thêm bởi bugfix spec `batch-scan-placeholder-analysis`)*

### Yêu cầu 22: Hiển thị kết quả phân tích sâu trên Frontend

#### Tiêu chí chấp nhận

22.1 THE Frontend_App SHALL mở rộng tab Context để hiển thị: business summary, As-Is/To-Be, extracted requirements, affected modules, Technical Details (API specs, DB changes, integrations), Diagrams, Dependencies Overview (blocking issues + risk badges + related count), Acceptance Criteria preview (top 3 + link tới Complexity tab), và Analysis Info (timestamp, AI provider, extraction confidence badge)
22.2 THE Frontend_App SHALL mở rộng tab Evolution để hiển thị changelog chi tiết: field changed, old value, new value, changed by, impact assessment
22.3 THE Frontend_App SHALL mở rộng tab Complexity để hiển thị: Scrum Points + rationale, dependencies + risk level, acceptance criteria + testability, KB references
22.4 WHEN extraction_confidence là LOW, THE Frontend_App SHALL hiển thị cảnh báo
22.5 THE Frontend_App SHALL hiển thị badge analysis_metadata (thời gian, AI provider, confidence)

### Yêu cầu 23: Giữ trạng thái phân tích khi chuyển màn hình và tự động load kết quả

#### Tiêu chí chấp nhận

23.1 WHEN người dùng chuyển sang màn hình khác rồi quay lại, THE Frontend_App SHALL khôi phục trạng thái trước đó (ticket đã chọn, kết quả phân tích, tab đang active). Combobox SHALL hiển thị ngay lập tức ticket đã chọn trước đó (ticketId + summary) từ sessionStorage mà không cần chờ API load danh sách ticket (immediate restore). Sau khi API trả về, hệ thống đồng bộ internal state (`selectedTicket` object) với ticket đầy đủ từ API response (deferred restore)
23.2 THE Frontend_App SHALL lưu trạng thái vào sessionStorage, bao gồm `selectedTicketId`, `selectedTicketSummary`, `activeTab`, và `analysisResult`
23.3 WHEN chọn ticket "Đã phân tích" (ANALYZED) hoặc "Đã quét" (SCANNED), THE Frontend_App SHALL tự động gọi `GET /api/analysis/{ticketId}` hiển thị kết quả từ KB cache ngay. Với SCANNED tickets (chỉ có batch scan cơ bản), tab Context hiển thị unified summary fallback thay vì deep analysis fields
23.4 WHILE đang tải từ KB cache, THE Frontend_App SHALL hiển thị skeleton loading (không progress bar đầy đủ)
23.5 WHEN chọn ticket "Chưa phân tích", THE Frontend_App SHALL chỉ hiển thị nút ANALYZE
23.6 WHEN chọn ticket "Có cập nhật mới", THE Frontend_App SHALL load kết quả cũ + badge cảnh báo + nút RE-ANALYZE

### Yêu cầu 24: Nâng cấp AI Chat Sidebar để sử dụng dữ liệu phân tích sâu

#### Tiêu chí chấp nhận

24.1 WHEN người dùng hỏi về ticket đã phân tích sâu, THE ChatService SHALL đưa kết quả Deep Analysis vào ngữ cảnh prompt
24.2 WHEN hỏi về requirements, THE ChatService SHALL trả lời dựa trên extracted_requirements
24.3 WHEN hỏi về API endpoints hoặc DB changes, THE ChatService SHALL trả lời dựa trên technical_details
24.4 IF ticket chưa phân tích sâu, THEN THE ChatService SHALL gợi ý action button "Analyze Ticket"

### Yêu cầu 25: Parse kết quả AI thành cấu trúc Deep Analysis

#### Tiêu chí chấp nhận

25.1 THE Deep_Analysis_Engine SHALL parse response JSON từ AI thành DeepAnalysisResult
25.2 IF AI response thiếu trường optional, THEN gán default values thay vì gây lỗi parse
25.3 IF AI response chứa JSON không hợp lệ, THEN retry tối đa 2 lần với prompt strict JSON
25.4 THE Deep_Analysis_Engine SHALL validate Scrum Points trong thang hợp lệ (0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40)
25.5 FOR ALL DeepAnalysisResult hợp lệ, serialize rồi deserialize SHALL tạo ra đối tượng tương đương (round-trip property)
25.6 THE Deep_Analysis_Engine SHALL ghi log extraction_confidence: HIGH (≥4 sections), MEDIUM (2-3), LOW (0-1)

### Yêu cầu 26: Phân tích lan truyền — Tự động phân tích các ticket liên quan chưa có trong KB

**User Story:** Là một Scrum Master, tôi muốn khi phân tích một ticket, hệ thống tự động phát hiện và phân tích tất cả ticket liên quan chưa được phân tích, để tôi có bức tranh đầy đủ về dependencies và context.

#### Tiêu chí chấp nhận

26.1 WHEN phân tích một ticket hoàn tất, THE Deep_Analysis_Engine SHALL thu thập tất cả ticket liên quan từ Jira_Content_Extractor: issue links, sub-tasks, parent ticket, và ticket được đề cập trong comments
26.2 FOR EACH ticket liên quan, THE Deep_Analysis_Engine SHALL kiểm tra KB — nếu chưa có, tự động đưa vào hàng đợi phân tích
26.3 THE Deep_Analysis_Engine SHALL sử dụng cùng pipeline `AIOrchestrator.analyzeTicket()` (giống logic scan Dashboard)
26.4 THE Deep_Analysis_Engine SHALL xử lý tuần tự, dùng cùng AI semaphore với BatchScanEngine
26.5 THE Deep_Analysis_Engine SHALL thực hiện cascading đệ quy: chỉ dừng khi không còn ticket liên quan nào chưa phân tích
26.6 THE Deep_Analysis_Engine SHALL duy trì Set visited tickets tránh vòng lặp và trùng lặp
26.7 THE Deep_Analysis_Engine SHALL giới hạn tối đa 50 ticket (configurable). Khi đạt giới hạn, ghi log cảnh báo và dừng

#### Log chi tiết trên màn hình (Cascading Analysis Log)

26.8 THE Frontend_App SHALL hiển thị panel log chi tiết bên dưới kết quả phân tích chính
26.9 Log entries: [DISCOVERED], [ANALYZING], [COMPLETED], [SKIPPED], [FAILED], [CASCADE], [DONE] summary
26.10 THE Frontend_App SHALL hiển thị progress bar phụ: `{completed}/{total}` cập nhật real-time qua polling
26.11 WHEN cascading hoàn tất, THE Frontend_App SHALL cập nhật tab Complexity với dependencies đầy đủ

### Yêu cầu 27: Tổng hợp thông tin từ linked tickets vào phân tích chính

**User Story:** Là một BA/Scrum Master, tôi muốn khi phân tích sâu một ticket, hệ thống tự động fetch và tổng hợp nội dung từ các ticket liên quan (blocking, linked, sub-tasks) vào prompt AI — để kết quả phân tích phản ánh đầy đủ context, không chỉ dựa trên nội dung ticket chính.

#### Tiêu chí chấp nhận

27.1 WHEN `JiraContentExtractorImpl.extract()` được gọi, THE extractor SHALL fetch content chi tiết (summary, description, status) của tất cả linked tickets (issue links + sub-tasks + parent) — không giới hạn số lượng.
27.2 THE `StructuredTicketContent` SHALL bao gồm trường `linkedTicketContents: List<LinkedTicketContent>` chứa: ticketId, summary, description, status, linkType (blocks, is blocked by, relates to, sub-task, parent), comments (List<CommentInfo>, default emptyList()), attachments (List<AttachmentInfo>, default emptyList()). *(Cập nhật bởi feature spec `linked-ticket-attachments` — thêm comments và attachments fields)*
27.3 THE `DeepAnalysisPromptBuilder` SHALL inject nội dung linked tickets vào prompt AI dưới section riêng "RELATED TICKETS CONTEXT", bao gồm summary, description, status, comments (author + date + content), và attachment filenames (filename + mimeType + size) cho mỗi linked ticket. Khi sử dụng `DeepJiraContentExtractor` (deep extraction enabled), không giới hạn số linked tickets. Khi sử dụng `JiraContentExtractorImpl` (legacy), giới hạn tối đa 5 linked tickets, mỗi ticket tối đa 500 ký tự description. *(Cập nhật bởi feature spec `linked-ticket-attachments` — thêm comments và attachments vào prompt)*
27.4 THE AI prompt SHALL yêu cầu AI tổng hợp thông tin từ linked tickets vào businessSummary, technicalDetails, và dependencies — không chỉ liệt kê mà phải phân tích mối quan hệ và ảnh hưởng.
27.5 IF linked ticket không tồn tại hoặc fetch fail, THEN skip ticket đó và tiếp tục — không block phân tích chính.
27.6 BEFORE fetching any linked ticket, THE `JiraContentExtractorImpl.collectLinkedKeys()` SHALL validate mỗi key theo Jira ticket key format regex `[A-Z][A-Z0-9]+-\d+`. Keys không hợp lệ (ví dụ: "active-jobs", "documents") SHALL bị loại bỏ khỏi danh sách fetch và log warning. Điều này tránh HTTP 404 errors và `IllegalStateException` không cần thiết (xem bugfix spec `invalid-jira-key-fetch`)

### Yêu cầu 28: Sinh sơ đồ trực quan cho kết quả phân tích sâu

**User Story:** Là một BA/PM, tôi muốn khi phân tích sâu hoàn tất, hệ thống sinh các sơ đồ trực quan (flow diagram, component diagram, dependency graph) — để tôi hiểu nhanh cách CR hoạt động, các đối tượng tham gia, và mối quan hệ giữa chúng.

#### Tiêu chí chấp nhận

28.1 THE AI prompt SHALL yêu cầu AI sinh Mermaid diagram code cho mỗi ticket phân tích sâu, bao gồm:
  - Flow diagram: luồng xử lý chính của CR/ticket (actors, steps, decisions)
  - Component diagram: các hệ thống/module tham gia và kết nối giữa chúng
  - Dependency graph: mối quan hệ giữa ticket chính và linked tickets
28.2 THE `DeepAnalysisResult` SHALL bao gồm trường `diagrams: List<DiagramData>` với mỗi diagram chứa: type (flow/component/dependency), title, mermaidCode (string).
28.3 THE Frontend_App SHALL render Mermaid diagrams trong tab CONTEXT, bên dưới section TECHNICAL DETAILS. Mỗi diagram hiển thị trong card riêng với title và rendered SVG.
28.4 THE Frontend_App SHALL sử dụng Mermaid.js library (CDN hoặc bundled) để render mermaidCode thành SVG trực tiếp trên browser.
28.5 IF AI không sinh được diagram hợp lệ (Mermaid syntax error), THEN Frontend SHALL hiển thị raw mermaid code trong code block thay vì render lỗi.
28.6 THE diagrams SHALL phản ánh thông tin từ cả ticket chính lẫn linked tickets (Req 27) — không chỉ ticket đơn lẻ.

---

## Liên kết Spec — Draw.io Template-Based Diagrams

> **Draw.io Template-Based Diagrams (spec `drawio-template-diagrams`)**: Mở rộng Req 28 bằng cách thêm hỗ trợ draw.io format sử dụng phương pháp template-based. Hybrid approach: Mermaid cho flow/component/dependency đơn giản, draw.io cho deployment/infrastructure/bpmn cần shapes/icons phong phú. Các thay đổi:
> - **DiagramData model**: Thêm `format` ("mermaid" | "drawio") và `drawioMetadata` (nullable) — backward compatible
> - **AI prompt**: Hướng dẫn AI chọn format phù hợp, sinh JSON metadata cho draw.io thay vì raw XML
> - **Frontend**: `DiagramRenderer` refactored thành format router → `MermaidDiagramRenderer` + `DrawioDiagramRenderer`. Draw.io viewer CDN lazy-loaded on-demand
> - **5 built-in XML templates**: flow, deployment, component, dependency, bpmn — AI chỉ sinh metadata, frontend merge vào template
