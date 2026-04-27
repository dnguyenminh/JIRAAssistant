# Streaming Generation Progress — Requirements

## Introduction

Hiện tại, quá trình sinh tài liệu BRD/FSD qua AI (Ollama) có trải nghiệm UX kém trong giai đoạn GENERATING_DOCUMENT: progress bar đứng yên ở 35% trong 60–90 giây chờ AI model hoàn tất, sau đó nhảy đột ngột lên 90%. Nguyên nhân là `JobExecutor.callAIWithRetry()` gọi `agent.analyze(prompt)` đồng bộ — chờ toàn bộ response trước khi cập nhật progress.

Ollama API hỗ trợ `stream: true` trả về NDJSON lines với partial responses theo từng token batch. Feature này tận dụng streaming để cập nhật progress mượt mà từ 35% → 85% trong suốt quá trình AI sinh document, thay vì đứng yên rồi nhảy.

Phạm vi thay đổi giới hạn ở backend — frontend hiện tại đã poll `GET /api/jobs/{jobId}` mỗi 3 giây và hiển thị progress percent, nên chỉ cần backend cập nhật progress thường xuyên hơn là frontend tự động phản ánh.

## Glossary

- **Streaming_Response**: Chuỗi NDJSON lines trả về từ Ollama API khi `stream: true`, mỗi line là một JSON object chứa `response` (partial text) và `done` (boolean kết thúc)
- **Progress_Callback**: Lambda function `(progressPercent: Int) -> Unit` được truyền vào quá trình AI generation, cho phép caller cập nhật progress khi nhận được token batches
- **Token_Batch**: Một nhóm tokens (1 hoặc nhiều) nhận được trong một NDJSON line từ Ollama streaming response
- **Streaming_Progress_Range**: Khoảng progress 35% → 85% được ánh xạ cho giai đoạn GENERATING_DOCUMENT — progress tăng dần khi nhận thêm Token_Batches
- **OllamaAgent**: Class trong shared module implement AIAgent interface, giao tiếp với Ollama REST API tại `/api/generate`
- **JobExecutor**: Class server-side thực thi generation job pipeline: aggregate → prompt → AI → parse → save, cập nhật progress qua JobRepository
- **NDJSON**: Newline-Delimited JSON — format mỗi dòng là một JSON object độc lập, dùng cho streaming responses

## Requirements

### Requirement 1: Streaming response từ Ollama API

**User Story:** Là một developer, tôi muốn OllamaAgent sử dụng Ollama streaming API (`stream: true`) khi được yêu cầu, để hệ thống nhận partial responses theo thời gian thực thay vì chờ toàn bộ response.

#### Acceptance Criteria

1.1 THE OllamaAgent SHALL cung cấp method `analyzeStreaming(prompt: String, onProgress: (Int) -> Unit): AIResult` gửi request tới Ollama `/api/generate` với `stream: true` và đọc response dưới dạng NDJSON lines.

1.2 WHEN OllamaAgent nhận mỗi NDJSON line từ Ollama streaming response, THE OllamaAgent SHALL parse trường `response` (partial text) và tích lũy vào buffer, sau đó gọi `onProgress` callback với progress percent tương ứng.

1.3 WHEN NDJSON line chứa `done: true`, THE OllamaAgent SHALL kết thúc streaming, trả về `AIResult.Success` với toàn bộ text đã tích lũy từ tất cả partial responses.

1.4 IF kết nối streaming bị ngắt giữa chừng (network error, timeout), THEN THE OllamaAgent SHALL trả về `AIResult.Failure` với message mô tả lỗi, không trả về partial response.

1.5 THE OllamaAgent SHALL tính progress percent dựa trên số NDJSON lines đã nhận so với ước lượng tổng số lines, ánh xạ vào khoảng 0–100 (caller sẽ map lại vào Streaming_Progress_Range). Ước lượng ban đầu dùng heuristic: mỗi 50 lines tương đương khoảng 10% progress, cap tại 95% cho đến khi nhận `done: true`.

1.6 THE AIAgent interface SHALL giữ nguyên method `analyze(prompt: String, context: AIContext?): AIResult` hiện tại — `analyzeStreaming` là method bổ sung trên OllamaAgent, không thay đổi interface chung.

### Requirement 2: Progress callback trong JobExecutor

**User Story:** Là một BA/PM, tôi muốn progress bar tăng dần mượt mà từ 35% đến 85% trong suốt quá trình AI sinh document, thay vì đứng yên rồi nhảy, để tôi biết hệ thống đang hoạt động và ước lượng thời gian còn lại.

#### Acceptance Criteria

2.1 WHEN JobExecutor gọi AI để sinh document, THE JobExecutor SHALL kiểm tra agent có phải OllamaAgent hay không. IF agent là OllamaAgent, THEN THE JobExecutor SHALL gọi `analyzeStreaming()` với progress callback thay vì `analyze()`.

2.2 THE JobExecutor SHALL truyền progress callback vào `analyzeStreaming()` để ánh xạ streaming progress (0–100) vào Streaming_Progress_Range (35–85), cập nhật job progress qua `jobRepository.updateStatus()`.

2.3 WHILE AI đang streaming response, THE JobExecutor SHALL cập nhật progress vào database tối đa mỗi 2 giây một lần (throttle), tránh ghi database quá thường xuyên khi Ollama trả về nhiều NDJSON lines liên tiếp.

2.4 WHEN streaming hoàn tất (`done: true`), THE JobExecutor SHALL cập nhật progress lên 85% trước khi chuyển sang phase SAVING (90%).

2.5 IF agent không phải OllamaAgent (ví dụ GeminiAgent, LMStudioAgent), THEN THE JobExecutor SHALL tiếp tục sử dụng `analyze()` như hiện tại — progress vẫn nhảy từ 35% lên 85% khi AI hoàn tất.

2.6 THE JobExecutor SHALL giữ nguyên retry logic hiện tại (tối đa 2 retries). Mỗi retry attempt SHALL reset streaming progress về 35% và bắt đầu streaming lại từ đầu.

### Requirement 3: Ước lượng progress dựa trên streaming tokens

**User Story:** Là một developer, tôi muốn progress percent phản ánh hợp lý tiến trình sinh document thực tế, để progress bar không tăng quá nhanh ở đầu rồi chậm lại, hoặc ngược lại.

#### Acceptance Criteria

3.1 THE OllamaAgent SHALL ước lượng tổng số NDJSON lines dựa trên heuristic: document generation thường tạo ra 500–2000 lines tùy độ dài document. Giá trị ước lượng mặc định là 1000 lines.

3.2 THE OllamaAgent SHALL tính progress percent theo công thức: `min(95, (linesReceived * 100) / estimatedTotalLines)`. Progress không vượt quá 95% cho đến khi nhận `done: true` (lúc đó set 100%).

3.3 IF số NDJSON lines thực tế vượt quá estimatedTotalLines, THEN THE OllamaAgent SHALL điều chỉnh estimatedTotalLines lên gấp đôi giá trị hiện tại, đảm bảo progress không bị stuck tại 95% quá lâu.

3.4 THE JobExecutor SHALL ánh xạ streaming progress (0–100) vào Streaming_Progress_Range (35–85) theo công thức: `jobProgress = 35 + (streamingProgress * 50) / 100`.

### Requirement 4: Backward compatibility và fallback

**User Story:** Là một quản trị viên hệ thống, tôi muốn streaming là enhancement tùy chọn — nếu streaming thất bại hoặc agent không hỗ trợ, hệ thống vẫn hoạt động bình thường với non-streaming mode, để đảm bảo tính ổn định.

#### Acceptance Criteria

4.1 IF `analyzeStreaming()` thất bại (exception hoặc connection error), THEN THE JobExecutor SHALL fallback sang gọi `analyze()` (non-streaming) và tiếp tục pipeline bình thường.

4.2 THE OllamaAgent method `analyze()` hiện tại SHALL giữ nguyên behavior — gửi request với `stream: false`, đọc full response, trả về `AIResult`. Không có thay đổi nào ảnh hưởng đến callers hiện tại của `analyze()`.

4.3 THE AIAgent interface SHALL không thay đổi — `analyzeStreaming()` chỉ tồn tại trên OllamaAgent class. Các agent khác (GeminiAgent, LMStudioAgent) không bị ảnh hưởng.

4.4 WHEN JobExecutor fallback sang non-streaming mode, THE JobExecutor SHALL log warning message "Streaming failed, falling back to non-streaming: {error}" và cập nhật progress theo pattern cũ (35% → đợi → 85%).

### Requirement 5: Throttle cập nhật progress vào database

**User Story:** Là một developer, tôi muốn hệ thống không ghi database quá thường xuyên khi streaming trả về hàng trăm NDJSON lines mỗi giây, để tránh tạo bottleneck I/O và ảnh hưởng performance.

#### Acceptance Criteria

5.1 THE JobExecutor SHALL throttle progress updates: chỉ ghi vào database khi progress percent thay đổi ít nhất 1% SO VỚI lần ghi trước đó, VÀ đã qua ít nhất 2 giây kể từ lần ghi trước.

5.2 WHEN streaming kết thúc (`done: true`), THE JobExecutor SHALL ghi progress update cuối cùng (85%) vào database ngay lập tức, bất kể throttle interval.

5.3 THE throttle logic SHALL được implement trong JobExecutor (không phải trong OllamaAgent), vì OllamaAgent là shared module không có dependency vào database.
