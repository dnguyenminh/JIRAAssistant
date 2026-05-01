# Map-Reduce Analysis — Requirements

## Introduction

Hiện tại, hệ thống Jira Assistant phân tích ticket qua pipeline: `JiraContentExtractor.extract()` → `DeepAnalysisPromptBuilder.buildPrompt()` → AI Agent → `DeepAnalysisResponseParser.parse()`. Pipeline này gửi **một prompt duy nhất** chứa toàn bộ dữ liệu ticket + linked tickets đến AI.

Vấn đề:

1. **Giới hạn nhân tạo trên data collection**: `DeepJiraContentExtractor` hiện tại dùng `maxDepth=10, maxTickets=200` — vẫn là giới hạn cứng. Với ticket graph lớn (>200 tickets), hệ thống bỏ sót dữ liệu quan trọng.

2. **Context window AI bị giới hạn**: Dù thu thập được nhiều ticket, `DeepAnalysisPromptBuilder` nhồi tất cả vào một prompt duy nhất. Khi data vượt context window, phải truncate → mất thông tin.

3. **Không có cơ chế tổng hợp thông minh**: Hiện tại chỉ có truncation (cắt bớt data ưu tiên thấp). Không có cơ chế để AI phân tích từng phần rồi tổng hợp kết quả — dẫn đến AI chỉ "thấy" một phần của ticket graph.

Feature này xây dựng **Map-Reduce Analysis Pipeline** để giải quyết tất cả vấn đề trên:
- **Bỏ giới hạn thu thập**: BFS traversal không bị cap bởi maxTickets/maxDepth cứng — chỉ dừng khi hết ticket hoặc timeout
- **Map phase**: Chia tickets thành batches, gửi từng batch đến AI để tạo batch summary
- **Reduce phase**: Tổng hợp tất cả batch summaries thành kết quả phân tích cuối cùng
- Đảm bảo AI có cái nhìn **toàn diện** về toàn bộ ticket graph, không bỏ sót thông tin

## Glossary

- **Map_Reduce_Orchestrator**: Component mới điều phối toàn bộ Map-Reduce analysis pipeline — chia tickets thành batches, gọi AI cho từng batch (Map), tổng hợp kết quả (Reduce). Thay thế single-prompt flow trong `AIOrchestratorImpl` khi ticket graph lớn
- **Batch**: Một nhóm tickets được gom lại để gửi đến AI trong một lần gọi. Mỗi batch chứa tickets có liên quan (cùng depth level hoặc cùng relationship cluster) để AI có ngữ cảnh tốt nhất
- **Batch_Summary**: Kết quả phân tích AI cho một batch — chứa tóm tắt requirements, technical details, dependencies, và key insights từ các tickets trong batch đó
- **Map_Phase**: Giai đoạn gửi từng batch đến AI để tạo Batch_Summary. Các batches có thể được xử lý song song (concurrent) để tối ưu thời gian
- **Reduce_Phase**: Giai đoạn tổng hợp tất cả Batch_Summaries thành một AnalysisResult cuối cùng. AI nhận tất cả summaries + root ticket data và tạo kết quả phân tích toàn diện
- **Batch_Prompt_Builder**: Component xây dựng prompt cho Map phase — format batch tickets thành prompt yêu cầu AI tạo Batch_Summary
- **Reduce_Prompt_Builder**: Component xây dựng prompt cho Reduce phase — format tất cả Batch_Summaries thành prompt yêu cầu AI tạo AnalysisResult cuối cùng
- **Batch_Strategy**: Chiến lược chia tickets thành batches — dựa trên depth level, relationship type, hoặc kích thước data. Có thể cấu hình qua MapReduceConfig
- **MapReduceConfig**: Cấu hình cho Map-Reduce pipeline: max_batch_size (số tickets tối đa mỗi batch), max_concurrent_batches (số batches xử lý song song), batch_strategy (cách chia batch)
- **Unlimited_Traversal_Config**: Cấu hình traversal mới với giới hạn rất cao (maxDepth=20, maxTickets=1000, timeout=600s) — cho phép BFS khám phá toàn bộ ticket graph mà không bị cắt sớm
- **Progress_Tracker**: Component theo dõi tiến trình Map-Reduce — báo cáo batch nào đang được xử lý, bao nhiêu batch đã hoàn thành, tổng thời gian ước tính
- **Analysis_Pipeline**: Toàn bộ flow phân tích ticket: extract → batch → map → reduce → parse. Bao gồm cả single-prompt (legacy) và map-reduce (mới)

## Requirements

### Requirement 1: Unlimited Traversal — Bỏ giới hạn thu thập dữ liệu

**User Story:** Là một BA/PM, tôi muốn hệ thống thu thập TẤT CẢ tickets liên quan mà không bị giới hạn bởi maxTickets hay maxDepth cứng, để AI có đầy đủ dữ liệu phân tích toàn bộ ticket graph.

#### Acceptance Criteria

1.1 THE DeepJiraContentExtractor SHALL sử dụng Unlimited_Traversal_Config với maxDepth=20 và maxTickets=1000 thay cho giá trị hiện tại (maxDepth=10, maxTickets=200), cho phép BFS traversal khám phá toàn bộ ticket graph trong phạm vi thực tế.

1.2 THE TraversalConfig SHALL mở rộng phạm vi clamp cho maxDepth từ 1..10 thành 1..20, và maxTickets từ 1..200 thành 1..1000, để hỗ trợ Unlimited_Traversal_Config.

1.3 THE DeepJiraContentExtractor SHALL tăng totalTimeoutMs từ 300,000ms (5 phút) lên 600,000ms (10 phút) để cho phép traversal hoàn tất trên ticket graph lớn.

1.4 WHEN traversal thu thập được nhiều hơn 200 tickets, THE Map_Reduce_Orchestrator SHALL tự động kích hoạt Map-Reduce pipeline thay vì single-prompt flow. WHEN số tickets nhỏ hơn hoặc bằng 200, THE Analysis_Pipeline SHALL sử dụng single-prompt flow hiện tại (backward compatible).

1.5 THE TraversalEngine SHALL bỏ early termination dựa trên 3× maxPromptChars khi chạy trong chế độ map-reduce (vì data sẽ được chia batch, không cần fit vào một prompt). Early termination chỉ áp dụng khi chạy single-prompt flow.

1.6 IF traversal timeout (600s) trước khi hoàn tất toàn bộ graph, THEN THE DeepJiraContentExtractor SHALL giữ lại tất cả tickets đã thu thập và tiếp tục với Map-Reduce pipeline trên dữ liệu đã có, log warning "Traversal timeout: collected {N} of estimated {M} tickets".

### Requirement 2: Batch Grouping — Chia tickets thành batches thông minh

**User Story:** Là một developer, tôi muốn tickets được chia thành các batches có ngữ cảnh liên quan (cùng depth, cùng cluster), để AI phân tích mỗi batch có đủ context mà không bị quá tải.

#### Acceptance Criteria

2.1 THE Batch_Strategy SHALL chia tickets trong TicketGraph thành các batches, mỗi batch chứa tối đa `max_batch_size` tickets (mặc định 30, cấu hình qua MapReduceConfig).

2.2 THE Batch_Strategy SHALL ưu tiên nhóm tickets theo depth level: tickets cùng depth được gom vào cùng batch trước. WHEN một depth level có nhiều tickets hơn max_batch_size, THE Batch_Strategy SHALL chia depth level đó thành nhiều batches.

2.3 THE Batch_Strategy SHALL luôn đặt root ticket (depth=0) vào batch đầu tiên cùng với tickets depth=1 có relevance score cao nhất, để batch đầu tiên chứa ngữ cảnh quan trọng nhất.

2.4 WHEN chia batch, THE Batch_Strategy SHALL giữ tickets có quan hệ trực tiếp (parent-child, blocking) trong cùng batch khi có thể, để AI hiểu được mối quan hệ giữa các tickets trong batch.

2.5 THE Batch_Strategy SHALL đảm bảo mỗi batch chứa ít nhất 1 ticket. Batches rỗng không được tạo.

2.6 FOR ALL ticket graphs, tổng số tickets trong tất cả batches SHALL bằng tổng số tickets trong TicketGraph (không mất ticket, không trùng ticket giữa các batches).

2.7 THE MapReduceConfig SHALL có các tham số: `max_batch_size` (integer, mặc định 30, tối thiểu 5, tối đa 100), `max_concurrent_batches` (integer, mặc định 3, tối thiểu 1, tối đa 5), `map_reduce_threshold` (integer, mặc định 200, tối thiểu 50 — ngưỡng kích hoạt map-reduce).

### Requirement 3: Map Phase — Tạo Batch Summary qua AI

**User Story:** Là một BA/PM, tôi muốn mỗi batch tickets được AI phân tích và tóm tắt thành một batch summary, để thông tin từ mọi ticket đều được AI xử lý mà không bị truncate.

#### Acceptance Criteria

3.1 THE Batch_Prompt_Builder SHALL xây dựng prompt cho mỗi batch chứa: (a) danh sách tickets trong batch với full data (summary, description, comments, status, priority, labels), (b) thông tin relationship giữa các tickets trong batch, (c) instruction yêu cầu AI tạo Batch_Summary dạng JSON.

3.2 THE Batch_Prompt_Builder SHALL include root ticket summary và description trong mỗi batch prompt (dù root ticket chỉ nằm trong batch đầu tiên), để AI có ngữ cảnh gốc khi phân tích bất kỳ batch nào.

3.3 THE Map_Reduce_Orchestrator SHALL gửi batch prompts đến AI Agent song song (concurrent), giới hạn bởi `max_concurrent_batches` (mặc định 3) để tránh overwhelm AI provider.

3.4 WHEN một batch AI call thất bại (timeout, parse error, provider error), THE Map_Reduce_Orchestrator SHALL retry tối đa 2 lần với exponential backoff (2s, 4s). IF vẫn thất bại sau retries, THE Map_Reduce_Orchestrator SHALL bỏ qua batch đó, log warning "Batch {batchIndex} failed after retries: {error}", và tiếp tục với các batches còn lại.

3.5 THE Batch_Summary SHALL chứa các trường: `batchIndex` (thứ tự batch), `ticketIds` (danh sách ticket IDs trong batch), `requirementsSummary` (tóm tắt requirements phát hiện được), `technicalInsights` (technical details, API specs, DB changes), `dependencySummary` (dependencies giữa tickets), `keyFindings` (những phát hiện quan trọng), `openQuestions` (câu hỏi chưa được giải đáp).

3.6 THE Batch_Prompt_Builder SHALL giới hạn prompt size cho mỗi batch không vượt quá `maxPromptChars` (mặc định 100,000 ký tự). WHEN data trong batch vượt giới hạn, THE Batch_Prompt_Builder SHALL truncate comments và attachment content (ưu tiên thấp) trước khi truncate ticket descriptions.

3.7 FOR ALL batch prompts, THE Batch_Prompt_Builder SHALL include batch metadata: "Batch {X} of {Y}, containing {N} tickets at depth levels {D1, D2, ...}" để AI biết vị trí batch trong tổng thể.

### Requirement 4: Reduce Phase — Tổng hợp Batch Summaries thành kết quả cuối cùng

**User Story:** Là một BA/PM, tôi muốn tất cả batch summaries được tổng hợp thành một kết quả phân tích cuối cùng toàn diện, để tôi có cái nhìn đầy đủ về toàn bộ ticket graph.

#### Acceptance Criteria

4.1 THE Reduce_Prompt_Builder SHALL xây dựng reduce prompt chứa: (a) root ticket full data (summary, description, status, priority), (b) tất cả Batch_Summaries đã thu thập thành công, (c) ticket graph metadata (tổng tickets, max depth, relationship overview), (d) instruction yêu cầu AI tổng hợp thành AnalysisResult JSON format.

4.2 THE Map_Reduce_Orchestrator SHALL gọi AI Agent với reduce prompt để tạo AnalysisResult cuối cùng. Reduce call sử dụng cùng AI Agent và provider failover logic như single-prompt flow hiện tại.

4.3 WHEN reduce AI call thất bại, THE Map_Reduce_Orchestrator SHALL retry tối đa 2 lần. IF vẫn thất bại, THE Map_Reduce_Orchestrator SHALL tạo AnalysisResult từ batch summary đầu tiên (batch chứa root ticket) làm fallback, log error "Reduce phase failed, using batch-0 summary as fallback".

4.4 THE AnalysisResult từ reduce phase SHALL có cùng cấu trúc và format với AnalysisResult từ single-prompt flow hiện tại (RequirementSummary, TechnicalDetails, ComplexityAssessment, AcceptanceCriteria, Dependencies, Diagrams), đảm bảo downstream consumers (KB save, frontend display) không cần thay đổi.

4.5 THE AnalysisResult từ reduce phase SHALL chứa `analysisMetadata.mapReduceInfo` với: `totalBatches` (tổng số batches), `successfulBatches` (số batches thành công), `failedBatches` (số batches thất bại), `totalTicketsAnalyzed` (tổng tickets đã phân tích), `mapPhaseTimeMs`, `reducePhaseTimeMs`.

4.6 THE Reduce_Prompt_Builder SHALL sắp xếp Batch_Summaries theo batchIndex tăng dần (batch chứa root ticket luôn đầu tiên) để AI xử lý thông tin theo thứ tự ưu tiên.

4.7 WHEN chỉ có 1 batch (tất cả tickets fit trong 1 batch), THE Map_Reduce_Orchestrator SHALL bỏ qua reduce phase và sử dụng trực tiếp kết quả từ map phase duy nhất, tránh gọi AI thừa.

### Requirement 5: Progress Tracking — Hiển thị tiến trình Map-Reduce

**User Story:** Là một BA/PM, tôi muốn thấy tiến trình phân tích Map-Reduce (batch nào đang xử lý, bao nhiêu batch đã xong), để tôi biết hệ thống đang hoạt động và ước tính thời gian chờ.

#### Acceptance Criteria

5.1 THE Progress_Tracker SHALL cập nhật tiến trình qua callback interface `(phase: String, detail: String, progressPercent: Int) -> Unit` với các phase: "TRAVERSAL" (0-20%), "MAP" (20-80%), "REDUCE" (80-95%), "PARSING" (95-100%).

5.2 WHILE Map phase đang chạy, THE Progress_Tracker SHALL cập nhật progress cho mỗi batch hoàn thành: `progressPercent = 20 + (completedBatches * 60 / totalBatches)`, `detail = "Processing batch {X}/{Y}: {N} tickets"`.

5.3 THE Map_Reduce_Orchestrator SHALL log ở level INFO khi bắt đầu mỗi phase: "Map-Reduce analysis started for {ticketId}: {totalTickets} tickets, {totalBatches} batches", "Map phase: processing batch {X}/{Y}", "Reduce phase: combining {N} batch summaries".

5.4 THE Map_Reduce_Orchestrator SHALL log ở level INFO khi hoàn tất: "Map-Reduce analysis completed for {ticketId}: {totalBatches} batches ({successfulBatches} success, {failedBatches} failed), map={mapTimeMs}ms, reduce={reduceTimeMs}ms, total={totalTimeMs}ms".

5.5 IF Map_Reduce_Orchestrator được gọi từ AIOrchestratorImpl (ticket analysis flow), THE Progress_Tracker SHALL tích hợp với ScanLogRepository để frontend hiển thị tiến trình tương tự scan log hiện tại.

### Requirement 6: Backward Compatibility — Single-prompt flow vẫn hoạt động

**User Story:** Là một developer, tôi muốn single-prompt analysis flow hiện tại vẫn hoạt động bình thường cho ticket graphs nhỏ, và Map-Reduce chỉ kích hoạt khi cần thiết, để không break behavior hiện tại.

#### Acceptance Criteria

6.1 WHEN số tickets trong TicketGraph nhỏ hơn hoặc bằng `map_reduce_threshold` (mặc định 200), THE Analysis_Pipeline SHALL sử dụng single-prompt flow hiện tại: `DeepAnalysisPromptBuilder.buildPrompt()` → AI → `DeepAnalysisResponseParser.parse()`. Không có thay đổi nào trong flow này.

6.2 WHEN số tickets vượt `map_reduce_threshold`, THE Analysis_Pipeline SHALL tự động chuyển sang Map-Reduce pipeline: batch → map → reduce → parse.

6.3 THE MapReduceConfig SHALL có setting `map_reduce_enabled` (boolean, mặc định true). WHEN setting là false, THE Analysis_Pipeline SHALL luôn sử dụng single-prompt flow bất kể số tickets, với truncation như hiện tại.

6.4 THE Map_Reduce_Orchestrator SHALL sử dụng cùng AI Agent instances và provider failover logic từ AIOrchestratorImpl, không tạo AI connections riêng.

6.5 THE AnalysisResult từ Map-Reduce pipeline SHALL được lưu vào KBRepository qua cùng `saveToKB()` logic hiện tại, đảm bảo KB cache hoạt động đúng cho cả hai flows.

6.6 WHEN `deep_collection_enabled` là false (feature flag từ deep-ticket-data-collection spec), THE Analysis_Pipeline SHALL sử dụng legacy `JiraContentExtractorImpl` với single-prompt flow. Map-Reduce chỉ khả dụng khi `deep_collection_enabled` là true.

### Requirement 7: Error Handling — Partial failures không block pipeline

**User Story:** Là một BA/PM, tôi muốn quá trình phân tích Map-Reduce tiếp tục hoạt động ngay cả khi một số batches thất bại, để tôi vẫn nhận được kết quả phân tích từ dữ liệu đã xử lý thành công.

#### Acceptance Criteria

7.1 WHEN một hoặc nhiều batches thất bại trong Map phase, THE Map_Reduce_Orchestrator SHALL tiếp tục Reduce phase với các Batch_Summaries đã thu thập thành công, miễn là có ít nhất 1 batch thành công (batch chứa root ticket).

7.2 IF batch chứa root ticket (batch 0) thất bại, THEN THE Map_Reduce_Orchestrator SHALL retry batch 0 thêm 2 lần (tổng 4 retries cho batch 0). IF vẫn thất bại, THE Map_Reduce_Orchestrator SHALL fallback về single-prompt flow với chỉ root ticket data.

7.3 THE Map_Reduce_Orchestrator SHALL log danh sách batches thất bại với lý do cụ thể: "Failed batches: [batch-2: timeout after 120s, batch-5: parse error: invalid JSON]".

7.4 WHEN reduce phase nhận được Batch_Summaries từ ít hơn 50% tổng batches, THE Reduce_Prompt_Builder SHALL thêm annotation trong prompt: "WARNING: Only {N}/{M} batches were successfully analyzed. The following ticket groups were not analyzed: {list of failed batch ticket ranges}. Analysis may be incomplete."

7.5 IF tất cả batches thất bại (0 Batch_Summaries), THEN THE Map_Reduce_Orchestrator SHALL fallback về single-prompt flow với root ticket + depth-1 tickets (truncated nếu cần), log error "All map batches failed, falling back to single-prompt with root context".

7.6 THE Map_Reduce_Orchestrator SHALL set timeout cho mỗi batch AI call: `batch_timeout_ms` (mặc định 120,000ms). WHEN timeout xảy ra, batch đó được đánh dấu failed và pipeline tiếp tục.

### Requirement 8: Batch Summary Parsing — Parse và validate Batch_Summary từ AI

**User Story:** Là một developer, tôi muốn Batch_Summary từ AI được parse và validate đúng format, để Reduce phase nhận được dữ liệu cấu trúc chính xác.

#### Acceptance Criteria

8.1 THE Map_Reduce_Orchestrator SHALL parse AI response cho mỗi batch thành Batch_Summary object. WHEN AI response không phải valid JSON, THE Map_Reduce_Orchestrator SHALL strip markdown fences (```json ... ```) và retry parsing.

8.2 THE Batch_Summary parser SHALL sử dụng `ignoreUnknownKeys = true` để chấp nhận AI responses có thêm fields ngoài schema.

8.3 WHEN Batch_Summary thiếu required fields (requirementsSummary, keyFindings), THE parser SHALL sử dụng default values (empty string, empty list) thay vì throw exception, đảm bảo partial data vẫn được sử dụng.

8.4 FOR ALL valid Batch_Summary objects, serializing rồi deserializing SHALL cho ra object tương đương (round-trip property cho serialization).

8.5 THE Batch_Summary parser SHALL validate rằng `ticketIds` trong response khớp với tickets đã gửi trong batch prompt. WHEN có mismatch, THE parser SHALL log warning nhưng vẫn chấp nhận response (AI có thể reference ticket IDs khác nhau).

### Requirement 9: MapReduceConfig — Cấu hình Map-Reduce pipeline

**User Story:** Là một quản trị viên hệ thống, tôi muốn cấu hình được các tham số Map-Reduce (batch size, concurrency, threshold), để tối ưu hiệu suất và chi phí AI cho từng môi trường.

#### Acceptance Criteria

9.1 THE MapReduceConfig SHALL có các tham số: `map_reduce_enabled` (boolean, mặc định true), `max_batch_size` (integer, mặc định 30, clamp 5..100), `max_concurrent_batches` (integer, mặc định 3, clamp 1..5), `map_reduce_threshold` (integer, mặc định 200, clamp 50..1000), `batch_timeout_ms` (long, mặc định 120,000), `reduce_timeout_ms` (long, mặc định 180,000).

9.2 THE MapReduceConfig SHALL được lưu trữ trong application settings (cùng cơ chế với TraversalConfig hiện tại), cho phép admin thay đổi qua API hoặc settings page.

9.3 WHEN max_batch_size hoặc max_concurrent_batches được set giá trị ngoài phạm vi cho phép, THE MapReduceConfig SHALL clamp giá trị về giới hạn gần nhất và log warning.

9.4 THE Map_Reduce_Orchestrator SHALL log MapReduceConfig đang được sử dụng ở đầu mỗi lần phân tích: "Map-Reduce config: batch_size={N}, concurrency={M}, threshold={T}".

### Requirement 10: Integration với AIOrchestratorImpl — Tích hợp vào analysis flow hiện tại

**User Story:** Là một developer, tôi muốn Map-Reduce pipeline được tích hợp vào `AIOrchestratorImpl.analyzeTicket()` một cách seamless, sử dụng cùng provider failover và KB caching logic hiện tại.

#### Acceptance Criteria

10.1 THE AIOrchestratorImpl SHALL nhận thêm dependency `Map_Reduce_Orchestrator` (optional, nullable) để hỗ trợ Map-Reduce flow. WHEN Map_Reduce_Orchestrator là null, behavior giữ nguyên như hiện tại (single-prompt only).

10.2 WHEN Map_Reduce_Orchestrator được inject và `map_reduce_enabled` là true, THE AIOrchestratorImpl SHALL kiểm tra số tickets từ `JiraContentExtractor.extract()`. WHEN số linked tickets vượt `map_reduce_threshold`, THE AIOrchestratorImpl SHALL delegate phân tích cho Map_Reduce_Orchestrator thay vì single-prompt flow.

10.3 THE Map_Reduce_Orchestrator SHALL sử dụng `currentAgents()` và `getActiveProvidersByPriority()` từ AIOrchestratorImpl để gọi AI, đảm bảo provider failover hoạt động cho cả map và reduce calls.

10.4 THE AIOrchestratorImpl SHALL vẫn thực hiện KB-First check trước khi gọi Map-Reduce: WHEN ticket đã có KBRecord hợp lệ và `forceReanalyze` là false, THE AIOrchestratorImpl SHALL trả về cached result mà không kích hoạt Map-Reduce.

10.5 THE Map_Reduce_Orchestrator SHALL sử dụng `aiAnalysisSemaphore` (từ deep-ticket-data-collection spec) để giới hạn concurrent AI calls, đảm bảo map batch calls và reduce call đều tuân thủ AI concurrency limit.

