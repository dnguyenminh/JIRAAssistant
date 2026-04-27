# Kế hoạch Triển khai: Multi-Turn BA Orchestration

## Tổng quan

Tái cấu trúc `BASubprocessOrchestrator` từ mô hình single-shot prompt sang multi-turn orchestration sử dụng Strategy Pattern. Triển khai bottom-up: data models → components → integration → tests → verification.

Tất cả file mới nằm trong package `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/`.
Tất cả test files nằm trong `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/`.

## Tasks

- [x] 1. Tạo data models trong package `pipeline/models/`
  - [x] 1.1 Tạo `ToolCallOutcome.kt` — data class chứa kết quả của một MCP tool call (toolName, success, data, durationMs, errorMessage)
    - File: `pipeline/models/ToolCallOutcome.kt` (~15 dòng)
    - _Requirements: 1.5, 1.6_
  - [x] 1.2 Tạo `CollectedContext.kt` — data class chứa toàn bộ dữ liệu thu thập từ 4 MCP tools, bao gồm computed property `successCount`
    - File: `pipeline/models/CollectedContext.kt` (~25 dòng)
    - Phụ thuộc: `ToolCallOutcome`, `ToolCallLogEntry` (existing)
    - _Requirements: 1.6, 7.3, 7.4_
  - [x] 1.3 Tạo `StepResponse.kt` — data class chứa kết quả của một pipeline step (stepName, content, durationMs, timedOut, isEmpty)
    - File: `pipeline/models/StepResponse.kt` (~15 dòng)
    - _Requirements: 2.1, 4.2_
  - [x] 1.4 Tạo `StopDecision.kt` — enum `StopReason` (QUALITY_PASSED, LOOP_DETECTED, CONSECUTIVE_FAILURES, ERROR) và data class `StopDecision` (shouldStop, reason, message)
    - File: `pipeline/models/StopDecision.kt` (~20 dòng)
    - _Requirements: 2.3_
  - [x] 1.5 Tạo `PipelineStepConfig.kt` — data class chứa config cho mỗi step (name, timeoutSeconds, progressRange). Bao gồm default configs: data collection 30s, analysis 60s, writing 120s, review 120s
    - File: `pipeline/models/PipelineStepConfig.kt` (~15 dòng)
    - _Requirements: 2.3, 8.3_

- [x] 2. Tạo `PipelineStrategy` interface
  - [x] 2.1 Tạo `PipelineStrategy.kt` — interface với method `execute(config: BATaskConfig, progressReporter: ProgressReporter): BATaskResult`
    - File: `pipeline/PipelineStrategy.kt` (~15 dòng)
    - _Requirements: 10.2, 10.3_

- [x] 3. Implement `DataCollector` — thu thập dữ liệu từ MCP tools
  - [x] 3.1 Tạo `DataCollector.kt` — class sử dụng `SubprocessProxy.handleToolCallRequest()` để gọi 4 MCP tools: `mcp_jira_get_issue`, `mcp_jira_search`, `mcp_local_knowledge_base_get_ticket_info`, `mcp_local_knowledge_base_search_relationships`. Mỗi tool call wrap trong try-catch, ghi log lỗi, tiếp tục thu thập. Trả về `CollectedContext` với trạng thái từng tool call.
    - File: `pipeline/DataCollector.kt` (~120 dòng)
    - Phụ thuộc: `SubprocessProxy`, `ProgressReporter`, `CollectedContext`, `ToolCallOutcome`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 8.1, 8.2_
  - [x] 3.2 Viết property test cho DataCollector — **Property 1: Data collection completeness**
    - For any valid rootTicketId, CollectedContext trả về chứa đúng 4 ToolCallOutcome entries tương ứng 4 MCP tools
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6**
  - [x] 3.3 Viết property test cho DataCollector — **Property 2: Data collection resilience**
    - For any tổ hợp thành công/thất bại của 4 MCP tools (2⁴ = 16 trường hợp), collectData() luôn trả về CollectedContext, không throw exception
    - **Validates: Requirements 1.5, 1.6**

- [x] 4. Implement `StepPromptBuilder` — tạo prompt nhỏ cho từng step
  - [x] 4.1 Tạo `StepPromptBuilder.kt` — object với 4 methods: `buildAnalysisPrompt`, `buildRequirementsPrompt`, `buildWritingPrompt`, `buildReviewPrompt`. Mỗi prompt ≤200 dòng, không chứa tool descriptions/instructions. Truncate data nếu quá lớn.
    - File: `pipeline/StepPromptBuilder.kt` (~180 dòng)
    - Phụ thuộc: `CollectedContext`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.2_
  - [x] 4.2 Viết property test cho StepPromptBuilder — **Property 5: Step prompt structure invariant**
    - For any CollectedContext và step type, prompt KHÔNG chứa "toolCall", "tool_name", "TOOL USAGE INSTRUCTIONS", "Available tools:", hoặc JSON tool call format
    - **Validates: Requirements 3.1, 3.2, 5.2**
  - [x] 4.3 Viết property test cho StepPromptBuilder — **Property 6: Step prompt data inclusion**
    - For any CollectedContext chứa ticket data D, analysis prompt chứa D. For any analysis result A, requirements prompt chứa A. For any accumulated results R, writing prompt chứa R.
    - **Validates: Requirements 3.3, 3.4, 3.5**
  - [x] 4.4 Viết property test cho StepPromptBuilder — **Property 7: Step prompt size invariant**
    - For any input data có kích thước bất kỳ, prompt có số dòng ≤ 200. Builder truncate data nếu quá lớn.
    - **Validates: Requirements 3.6**

- [x] 5. Checkpoint — Đảm bảo data models và components cơ bản compile thành công
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement `PipelineStopCondition` — đánh giá điều kiện dừng pipeline
  - [x] 6.1 Tạo `PipelineStopCondition.kt` — class với method `evaluate()` kiểm tra 4 điều kiện dừng: quality passed, loop detected (similarity > 90%), consecutive failures ≥ 3, error. Sử dụng simple text similarity (Jaccard hoặc normalized edit distance) cho loop detection.
    - File: `pipeline/PipelineStopCondition.kt` (~100 dòng)
    - Phụ thuộc: `StepResponse`, `StopDecision`, `DocumentQualityChecker` (existing)
    - _Requirements: 2.2, 2.3, 2.4_
  - [x] 6.2 Viết property test cho PipelineStopCondition — **Property 3: Pipeline termination guarantee**
    - For any chuỗi StepResponse (rỗng, lặp lại, quality thấp), evaluate() trả về shouldStop=true trong tối đa N turns
    - **Validates: Requirements 2.2, 2.3**
  - [x] 6.3 Viết property test cho PipelineStopCondition — **Property 4: Loop detection accuracy**
    - For any hai chuỗi text a và b, nếu similarity > 90% thì phát hiện loop. Nếu similarity ≤ 90% thì không false positive.
    - **Validates: Requirements 2.3, 2.4**

- [x] 7. Implement `DocumentAssembler` — ghép kết quả thành BATaskResult
  - [x] 7.1 Tạo `DocumentAssembler.kt` — class với method `assemble()` lấy document từ StepResponse cuối cùng có content không rỗng, map metrics từ CollectedContext sang BATaskResult format (toolCallsExecuted, toolCallsFailed, toolCallLog). Ghi log kích thước tài liệu, số turns, thời gian.
    - File: `pipeline/DocumentAssembler.kt` (~80 dòng)
    - Phụ thuộc: `StepResponse`, `CollectedContext`, `BATaskResult`, `BATaskStatus`
    - _Requirements: 6.1, 6.3, 6.4, 7.2, 7.3, 7.4_
  - [x] 7.2 Viết property test cho DocumentAssembler — **Property 8: Document assembly correctness**
    - For any danh sách StepResponse không rỗng và CollectedContext, assemble() trả về BATaskResult với document = content của StepResponse cuối cùng có content không rỗng, status hợp lệ, totalDurationMs ≥ 0
    - **Validates: Requirements 6.1, 6.3**
  - [x] 7.3 Viết property test cho DocumentAssembler — **Property 9: Metrics mapping correctness**
    - For any CollectedContext với N tool calls (M thành công, F thất bại), BATaskResult có toolCallsExecuted == N, toolCallsFailed == F, toolCallLog có đúng N entries
    - **Validates: Requirements 7.3, 7.4**

- [x] 8. Implement `MultiTurnPipelineStrategy` — pipeline loop chính
  - [x] 8.1 Tạo `MultiTurnPipelineStrategy.kt` — class implement `PipelineStrategy`. Orchestrate 3 phases: (1) DataCollector thu thập dữ liệu, (2) Multi-turn loop gửi step prompts cho AI qua SubprocessManager, đọc response plain text với delimiter `---END---`, kiểm tra stop condition sau mỗi turn, (3) DocumentAssembler ghép kết quả. Sử dụng per-turn timeout. Xử lý subprocess crash bằng cách spawn mới và gửi lại context.
    - File: `pipeline/MultiTurnPipelineStrategy.kt` (~150 dòng)
    - Phụ thuộc: `DataCollector`, `StepPromptBuilder`, `PipelineStopCondition`, `DocumentAssembler`, `SubprocessManager`, `ProgressReporter`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.3, 5.4, 8.3, 8.4, 9.2, 9.3_

- [x] 9. Implement `LegacyToolCallStrategy` — backward compatibility wrapper
  - [x] 9.1 Tạo `LegacyToolCallStrategy.kt` — class implement `PipelineStrategy`, wrap logic hiện tại (ToolCallLoopEngine + ReviewLoopHelper + TaskMessageBuilder). Extract từ `BASubprocessOrchestrator.sendTaskWithReviewLoop()` hiện tại.
    - File: `pipeline/LegacyToolCallStrategy.kt` (~80 dòng)
    - Phụ thuộc: `SubprocessManager`, `SubprocessProxy`, `ProgressReporter`, `ToolCallLoopEngine`, `ReviewLoopHelper`, `TaskMessageBuilder`
    - _Requirements: 10.1, 10.3_

- [x] 10. Refactor `BASubprocessOrchestrator` — delegate sang Strategy
  - [x] 10.1 Refactor `BASubprocessOrchestrator.kt` — thêm `PipelineStrategy` dependency, delegate `executeTask()` sang strategy. Mặc định sử dụng `MultiTurnPipelineStrategy`. Giữ nguyên interface `executeTask(config: BATaskConfig): BATaskResult`. Giữ logic CliBackendResolver, registerSubprocessConfig, launchStderrCapture. Loại bỏ direct dependency vào ToolCallLoopEngine/ReviewLoopHelper (đã move sang LegacyToolCallStrategy).
    - File: `BASubprocessOrchestrator.kt` (~120 dòng)
    - _Requirements: 7.1, 7.2, 9.1, 10.2, 10.3_

- [x] 11. Checkpoint — Đảm bảo toàn bộ pipeline compile và integration hoạt động
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Viết unit tests cho MultiTurnPipelineStrategy và LegacyToolCallStrategy
  - [x] 12.1 Tạo `MultiTurnPipelineStrategyTest.kt` — unit tests với mock SubprocessManager, mock SubprocessProxy. Test cases: pipeline executes steps in order (Req 2.1), pipeline continues beyond fixed turns (Req 2.2), pipeline sends feedback on quality fail (Req 2.5), same subprocess used for all steps (Req 4.1), per-turn timeout applied (Req 4.4), subprocess crash recovery (Req 4.5), progress reporting checkpoints (Req 8.1-8.4), plain text communication (Req 5.3, 9.2)
    - File: `pipeline/MultiTurnPipelineStrategyTest.kt` (~200 dòng)
    - _Requirements: 2.1, 2.2, 2.5, 4.1, 4.4, 4.5, 5.3, 8.1-8.4, 9.2_
  - [x] 12.2 Tạo `LegacyToolCallStrategyTest.kt` — unit tests verify legacy strategy wrap đúng logic hiện tại, default strategy is multi-turn (Req 10.2), fallback to legacy strategy (Req 10.3)
    - File: `pipeline/LegacyToolCallStrategyTest.kt` (~100 dòng)
    - _Requirements: 10.1, 10.2, 10.3_

- [x] 13. Viết integration tests
  - [x] 13.1 Tạo `MultiTurnPipelineIntegrationTest.kt` — integration tests: end-to-end multi-turn pipeline với mock SubprocessManager + mock SubprocessProxy, end-to-end legacy fallback, CLI backend compatibility (gemini, copilot, kiro, ollama), delimiter-based reading
    - File: `pipeline/MultiTurnPipelineIntegrationTest.kt` (~200 dòng)
    - _Requirements: 2.1, 4.1, 9.1, 9.2, 9.3, 9.4, 10.2, 10.3_

- [x] 14. Final checkpoint — Đảm bảo toàn bộ tests pass và code compile thành công
  - Ensure all tests pass, ask the user if questions arise.

## Ghi chú

- Tasks đánh dấu `*` là optional và có thể bỏ qua để đẩy nhanh MVP
- Mỗi task tham chiếu requirements cụ thể để đảm bảo traceability
- Checkpoints đảm bảo validation tăng dần
- Property tests validate correctness properties từ design document
- Unit tests validate specific examples và edge cases
- Tất cả file mới tuân thủ giới hạn ≤200 dòng theo coding standards
- Sử dụng Kotest property testing library (`io.kotest:kotest-property`) cho PBT
