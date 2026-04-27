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
2. WHEN nhận yêu cầu START scan cho một dự án, THE Batch_Scan_Engine SHALL lấy danh sách toàn bộ ticket từ JiraClient, tạo Scan_State mới với trạng thái SCANNING, và bắt đầu phân tích tuần tự từng ticket qua AI_Orchestrator. Tham số `concurrency` (parallel batch size) chỉ giới hạn tối thiểu >= 1, không có giới hạn tối đa (default=3). Tham số `aiConcurrency` (số AI inference đồng thời) chỉ giới hạn tối thiểu >= 1, không có giới hạn tối đa (default=1). Frontend gửi 2 tham số qua query string: `concurrency` và `batchPromptSize` (lưu vào app_settings). `aiConcurrency` hiện KHÔNG có input trên UI — luôn dùng default=1. User nhập bao nhiêu thì hệ thống chạy bấy nhiêu
3. WHEN nhận yêu cầu PAUSE scan, THE Batch_Scan_Engine SHALL chuyển trạng thái sang PAUSED, dừng xử lý ticket tiếp theo, và lưu vị trí hiện tại (index của ticket cuối cùng đã xử lý) vào Knowledge_Base
4. WHEN nhận yêu cầu RESUME scan cho dự án có trạng thái PAUSED, THE Batch_Scan_Engine SHALL khôi phục Scan_State từ Knowledge_Base và tiếp tục phân tích từ ticket tiếp theo sau vị trí đã lưu
5. WHEN nhận yêu cầu CANCEL scan, THE Batch_Scan_Engine SHALL chuyển trạng thái sang CANCELLED, dừng xử lý, và giữ lại kết quả phân tích của các ticket đã hoàn thành trong Knowledge_Base
6. THE Batch_Scan_Engine SHALL lưu trữ Scan_State vào Knowledge_Base (SQLDelight) bao gồm: projectKey, totalTickets, processedCount, currentTicketId, status, startedAt, updatedAt

### API Endpoints

7. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/start` để khởi động quá trình quét hàng loạt cho dự án, trả về Scan_State hiện tại
8. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/pause` để tạm dừng quá trình quét, trả về Scan_State đã cập nhật
9. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/resume` để tiếp tục quá trình quét đã tạm dừng, trả về Scan_State hiện tại
10. THE Backend_Server SHALL cung cấp endpoint `POST /api/projects/{key}/scan/cancel` để hủy bỏ quá trình quét, trả về Scan_State đã cập nhật
11. THE Backend_Server SHALL cung cấp endpoint `GET /api/projects/{key}/scan/status` để truy vấn trạng thái quét hiện tại bao gồm: status, processedCount, totalTickets, phần trăm hoàn thành, và 50 log entries gần nhất (recentLog). Số lượng 50 đảm bảo frontend có đủ entries để hiển thị khi user navigate away và quay lại Dashboard
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

# Yêu cầu 19: Batch Prompt Optimization — Gộp Nhiều Ticket vào 1 AI Prompt

**User Story:** Là một Scrum Master, tôi muốn hệ thống gộp nhiều ticket vào một prompt AI duy nhất thay vì gửi từng ticket riêng lẻ, để giảm tổng số lượng AI request và rút ngắn thời gian quét hàng loạt.

## Mô tả

Hiện tại BatchScanEngine gửi 1 ticket/request tới AI provider (OllamaAgent.analyze()). Với 1308 tickets, hệ thống thực hiện 1308 sequential AI calls → ~2.5-3 giờ scan time. Batch Prompt Optimization gộp 3-5 tickets vào 1 prompt duy nhất, AI trả về kết quả phân tích riêng biệt cho từng ticket trong cùng 1 response. Batch size có thể cấu hình qua app_settings.

## Thuật ngữ bổ sung

- **Batch_Prompt_Size**: Số lượng ticket được gộp vào 1 AI prompt duy nhất. Giá trị mặc định: 3, phạm vi hợp lệ: 1–10. Lưu trong app_settings với key `batch_prompt_size`.
- **Batch_Prompt_Template**: Template prompt chứa nhiều ticket với separator rõ ràng giữa các ticket, yêu cầu AI trả về JSON array với kết quả phân tích riêng biệt cho từng ticket.
- **Batch_Analysis_Result**: JSON array chứa AnalysisResult cho từng ticket trong batch, mỗi entry được map về ticketId tương ứng.

## Tiêu chí chấp nhận

### Cấu hình Batch Prompt Size

34. THE Settings_Repository SHALL lưu trữ cấu hình `batch_prompt_size` trong bảng app_settings với key `batch_prompt_size`, giá trị mặc định là `3`. Giá trị phải là số nguyên >= 1. IF giá trị < 1, THEN hệ thống SHALL sử dụng giá trị mặc định 3.

35. WHEN giá trị `batch_prompt_size` được cập nhật qua Settings API hoặc Settings Page, THE Batch_Scan_Engine SHALL sử dụng giá trị mới cho các batch tiếp theo trong scan đang chạy (không cần restart scan).

36. WHEN giá trị `batch_prompt_size` bằng 1, THE Batch_Scan_Engine SHALL fallback về chế độ single-ticket analysis hiện tại (1 ticket/prompt), đảm bảo backward compatibility.

### Batch Prompt Template

37. THE AI_Orchestrator SHALL cung cấp method `analyzeTicketBatch(tickets: List<Pair<String, String>>)` nhận danh sách cặp (ticketId, ticketContent) và gửi 1 prompt duy nhất tới AI provider chứa tất cả ticket với separator `--- TICKET {index} ---` giữa các ticket.

38. THE Batch_Prompt_Template SHALL yêu cầu AI trả về JSON array, mỗi phần tử chứa `ticketId` và cấu trúc AnalysisResult tương ứng, theo format:
```json
[
  { "ticketId": "PROJ-1", "requirementSummary": {...}, "evolution": [...], "complexity": {...} },
  { "ticketId": "PROJ-2", "requirementSummary": {...}, "evolution": [...], "complexity": {...} }
]
```

39. THE Batch_Prompt_Template SHALL giới hạn tổng nội dung prompt (tất cả ticket content cộng lại) tối đa 12000 ký tự để tránh vượt context window của AI model. WHEN tổng content vượt giới hạn, THE AI_Orchestrator SHALL tự động giảm batch size cho batch đó.

### Phân tích Response từ Batch Prompt

40. WHEN AI provider trả về response cho batch prompt, THE AI_Orchestrator SHALL parse JSON array và map từng phần tử về AnalysisResult với ticketId tương ứng. Mỗi ticket trong batch phải có đúng 1 kết quả trong response.

41. IF AI response cho batch prompt không parse được thành JSON array hợp lệ, THEN THE AI_Orchestrator SHALL retry 1 lần với cùng batch. IF retry vẫn thất bại, THEN THE AI_Orchestrator SHALL fallback sang phân tích từng ticket riêng lẻ (single-ticket mode) cho toàn bộ batch đó.

42. IF AI response trả về số lượng kết quả khác với số ticket trong batch (thiếu hoặc thừa), THEN THE AI_Orchestrator SHALL fallback sang single-ticket mode cho các ticket bị thiếu kết quả.

### Tích hợp với BatchScanEngine

43. WHEN Batch_Scan_Engine xử lý một nhóm ticket, THE Batch_Scan_Engine SHALL đọc giá trị `batch_prompt_size` từ Settings_Repository, gộp ticket theo batch size, và gọi `analyzeTicketBatch()` thay vì `analyzeTicket()` cho từng ticket.

44. THE Batch_Scan_Engine SHALL cập nhật progress (processedCount) sau mỗi batch hoàn tất, không phải sau mỗi ticket riêng lẻ trong batch.

45. WHEN một batch được phân tích xong, THE scan log SHALL ghi entry "Batch analyzed: {ticketIds} ({count} tickets in 1 prompt, source: {FRESH_AI|MIXED})" với status COMPLETED.

### Tương thích với Tất cả AI Providers

46. THE Batch_Prompt_Template SHALL hoạt động với tất cả AI providers đã hỗ trợ (Ollama, Gemini, LM Studio) vì prompt được gửi qua AIAgent.analyze() interface chung, không phụ thuộc vào provider cụ thể.

47. WHEN một AI provider không hỗ trợ batch prompt (response không đúng format), THE AI_Orchestrator SHALL tự động fallback sang single-ticket mode và ghi log cảnh báo "Provider {name} does not support batch prompt, falling back to single-ticket mode".

### Cấu hình qua Settings Page và API

48. THE Settings_Page SHALL hiển thị trường "Batch Prompt Size" dưới dạng combobox (dropdown) trong phần Scan Settings, với các giá trị gợi ý (1, 2, 3, 5, 8, 10, 15, 20) và cho phép nhập giá trị tùy chỉnh. Validate: giá trị phải là số nguyên >= 1. IF giá trị < 1, THEN hiển thị validation error.

49. THE Settings_API (`PUT /api/settings/feature`) SHALL chấp nhận key `batch_prompt_size` với giá trị là số nguyên >= 1. IF giá trị < 1, THEN THE Settings_API SHALL trả về lỗi 400 Bad Request với message "batch_prompt_size must be >= 1".

50. THE Settings_Page SHALL validate trường "Concurrent Scan Count" (số lượng scan đồng thời) với cùng quy tắc: giá trị phải là số nguyên >= 1. IF giá trị < 1, THEN hiển thị validation error.

---

**Tổng: 50 tiêu chí chấp nhận (33 cũ + 17 mới)**

---

## Liên kết Spec

> **Deep Analysis Enhancement (spec `ticket-intelligence`, phần Deep Analysis)**: Nâng cấp pipeline `AIOrchestrator.analyzeTicket()` mà BatchScanEngine.processTicket() đang sử dụng:
> - `fetchTicketContentForBatch()` gọi `jiraContentExtractor.extract()` trực tiếp cho batch mode, chuyển đổi `StructuredTicketContent` → plain text qua `formatStructuredContent()` (capped 3000 chars). Fallback sang `fetchLegacyBatchContent()` khi extractor null hoặc throw. *(Cập nhật bởi bugfix `batch-scan-placeholder-analysis`)*
> - `saveBatchResults()` detect placeholder results và fallback sang single-ticket mode *(Thêm bởi bugfix `batch-scan-placeholder-analysis`)*
> - `buildAnalysisPrompt()` (generic prompt) → thay bằng `Deep_Analysis_Prompt_Builder` (6 khía cạnh phân tích chi tiết)
> - `AnalysisResult`/`KBRecord` mở rộng với technical_details, acceptance_criteria, dependencies, analysis_metadata
> - BatchScanEngine.processTicket() KHÔNG cần thay đổi — vẫn gọi `analyzeTicket()` như cũ, nhưng kết quả phân tích sẽ sâu hơn
> - KB-First strategy, failover, retry, semaphore, batch prompt — tất cả giữ nguyên
