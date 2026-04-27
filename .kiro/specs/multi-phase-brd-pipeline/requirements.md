# Multi-Phase BRD Pipeline — Requirements

## Introduction

Hiện tại, hệ thống Jira Assistant sinh BRD thông qua một single-phase agentic loop (`AiBackendPipelineStrategy` → `AgenticLoopRunner` → `AgenticPromptBuilder`). Toàn bộ quá trình — data collection, BRD writing, diagram generation — diễn ra trong **một AI session duy nhất** với prompt ban đầu ~84K ký tự chứa tất cả: 57 tool definitions, BRD template, diagram templates, data strategy, KB cache instructions.

Cách tiếp cận này gây ra 3 vấn đề nghiêm trọng:

1. **Lost in the middle problem**: Ollama model bỏ qua instructions ở cuối prompt khi prompt quá dài. AI thường fail ở data collection → không bao giờ đến bước sinh diagram.

2. **Context accumulation**: Context tích lũy qua mỗi tool call turn → tổng context 100K+ ký tự. AI phải vừa nhớ data collection strategy, vừa nhớ BRD template, vừa nhớ diagram XML format — quá nhiều cho một session.

3. **Diagram quality kém**: AI phải vừa viết BRD dài (7 sections) vừa sinh valid draw.io XML trong cùng một response. Kết quả: diagrams thường bị bỏ qua hoặc XML không hợp lệ.

Feature này tách single-phase pipeline thành **multi-phase pipeline** với Knowledge Base (KB) MCP server làm external memory. Mỗi phase là một AI session riêng biệt với prompt nhỏ (~15-20K ký tự), chỉ chứa tools và instructions cần thiết cho phase đó. KB đóng vai trò shared memory giữa các phases.

## Glossary

- **Pipeline_Orchestrator**: Component mới điều phối thứ tự thực thi các phases, quản lý state giữa phases, và xử lý fallback. Thay thế single `AgenticLoopRunner.runLoop()` call hiện tại bằng multi-phase orchestration
- **Phase**: Một AI session độc lập trong pipeline, có prompt riêng, tool set riêng, và output riêng. Mỗi phase sử dụng `AgenticLoopRunner` hiện tại để chạy agentic loop
- **Phase_Config**: Cấu hình cho một phase, bao gồm: phase ID, ticket ID, doc type, max tool calls, timeout. Mở rộng từ `AgenticLoopConfig` hiện tại. Không chứa function fields — prompt building và tool filtering được xử lý bởi `PipelineOrchestrator` thông qua `PhasePromptBuilder` và `PhaseToolFilter`
- **Phase_Result**: Kết quả của một phase, bao gồm: output text, tool call metrics, success/failure status. Tương thích với `AgenticLoopResult` hiện tại
- **KB_Memory**: Knowledge Base MCP server đóng vai trò external memory giữa các phases. Phase 1 ghi data vào KB, Phase 2 và 3 đọc data từ KB
- **Tool_Filter**: Function lọc `List<ToolDescriptor>` để chỉ include tools cần thiết cho một phase cụ thể. Ví dụ: Phase 1 chỉ include Jira + KB tools, Phase 3 chỉ include diagram + KB tools
- **Phase_Prompt_Builder**: Function xây dựng prompt cho một phase cụ thể. Mỗi phase có prompt builder riêng, tập trung vào task của phase đó
- **Assembly_Step**: Bước cuối cùng (code only, không AI) merge outputs từ Phase 2 (BRD markdown) và Phase 3 (diagram XMLs) thành BRD hoàn chỉnh
- **Single_Phase_Mode**: Mode fallback khi KB không available — chạy pipeline hiện tại (single AgenticLoopRunner call) không thay đổi
- **Multi_Phase_Mode**: Mode mới khi KB available — chạy 3 AI phases + 1 assembly step
- **Agentic_Loop_Runner**: Component hiện tại (`AgenticLoopRunner`) chạy agentic loop: gửi prompt → parse tool call → execute → send continuation → lặp lại. Được reuse cho single-phase fallback. Trong multi-phase mode, `PipelineOrchestrator` chạy mini agentic loop riêng cho mỗi phase sử dụng `ToolExecutionBridge` trực tiếp (vì `AgenticLoopRunner` có prompt builder baked-in không thể swap per phase)
- **Prompt_Builder**: Component hiện tại (`AgenticPromptBuilder`) xây dựng prompts. Được mở rộng với phase-specific prompt builders
- **Tool_Execution_Bridge**: Component hiện tại (`ToolExecutionBridge`) route tool calls đến MCP servers. Được reuse không thay đổi cho mỗi phase

## Requirements

### Requirement 1: Pipeline Orchestrator — Điều phối Multi-Phase Execution

**User Story:** Là một BA/PM, tôi muốn BRD generation pipeline tự động chia thành nhiều phases nhỏ khi KB available, để mỗi phase tập trung vào một task cụ thể và AI model không bị overload bởi prompt quá lớn.

#### Acceptance Criteria

1.1 THE Pipeline_Orchestrator SHALL phát hiện KB_Memory availability bằng cách kiểm tra danh sách Tool_Descriptor có chứa ít nhất một KB tool (tên chứa "kb_search" hoặc "kb_ingest" hoặc "kb_write"). WHEN KB tools khả dụng, THE Pipeline_Orchestrator SHALL chạy Multi_Phase_Mode. WHEN KB tools không khả dụng, THE Pipeline_Orchestrator SHALL chạy Single_Phase_Mode.

1.2 THE Pipeline_Orchestrator SHALL thực thi các phases theo thứ tự: Phase 1 (Data Collection) → Phase 2 (BRD Writing) song song với Phase 3 (Diagram Generation) → Assembly_Step. Phase 2 và Phase 3 chỉ bắt đầu sau khi Phase 1 hoàn thành thành công.

1.3 THE Pipeline_Orchestrator SHALL tạo một AI session riêng biệt cho mỗi phase. Mỗi phase có prompt riêng, tool set riêng, và conversation history riêng — không chia sẻ context giữa các phases. Trong multi-phase mode, mỗi phase chạy mini agentic loop riêng (sử dụng `AiBackend` session + `ToolExecutionBridge` trực tiếp). Trong single-phase fallback, sử dụng `AgenticLoopRunner.runLoop()` như hiện tại.

1.4 WHEN một phase fail (timeout hoặc error), THE Pipeline_Orchestrator SHALL cho phép retry phase đó tối đa 1 lần trước khi đánh dấu pipeline là failed. Retry sử dụng cùng Phase_Config nhưng AI session mới.

1.5 THE Pipeline_Orchestrator SHALL thu thập Phase_Result từ mỗi phase và tổng hợp thành một `AgenticLoopResult` cuối cùng, bao gồm: tổng tool calls từ tất cả phases, tổng duration, combined document từ Assembly_Step.

1.6 THE Pipeline_Orchestrator SHALL reuse các components hiện tại: `AgenticLoopRunner` cho single-phase fallback, `ToolExecutionBridge` cho tool execution trong cả hai modes, `OllamaApiClient` cho AI backend. Không tạo mới các components này. `PipelineOrchestrator` nhận thêm `ToolExecutionBridge` làm constructor dependency để thực thi tool calls trực tiếp trong multi-phase mode.

1.7 THE Pipeline_Orchestrator SHALL báo cáo progress qua `ProgressReporter` hiện tại: Phase 1 (DataPreFetcher) = 5-30%, Phase 2 = 30-70%, Phase 3 = sau Phase 2 (sequential), Assembly = 70-100%.

### Requirement 2: Phase 1 — Code-Driven Data Collection

**User Story:** Là một BA/PM, tôi muốn phase đầu tiên thu thập dữ liệu nhanh chóng từ KB, Jira, và VectorStore bằng code trực tiếp (không qua AI), để giảm thời gian từ 60-70s xuống 1-7s và tăng độ tin cậy.

#### Acceptance Criteria

2.1 THE Phase 1 SHALL sử dụng `DataPreFetcher` (code-driven, không AI) để thu thập dữ liệu. `DataPreFetcher` thực hiện 6 bước: (1) KB semantic search, (2) main ticket từ KBRepository, (3) parse linked IDs từ KB deps + Jira raw links, (4) fetch mỗi linked ticket từ KB, (5) search relationships, (6) fetch attachments từ VectorStore.

2.2 THE `DataPreFetcher` SHALL nhận dependencies qua constructor: `KBRepository` (bắt buộc), `LocalKBToolExecutor` (optional), `JiraContentExtractor` (optional), `VectorStore` (optional). Khi optional dependency là null, bước tương ứng bị skip.

2.3 THE `DataPreFetcher` SHALL parse linked ticket IDs từ nhiều nguồn: KB record dependencies (blockingIssues, relatedIssues), similarTicketRefs, text fields scan (regex `[A-Z][A-Z0-9]+-\d+`), và Jira raw links (issueLinks, subTasks, parentKey). Tất cả IDs được deduplicate và giới hạn MAX_LINKED=500.

2.4 THE `DataPreFetcher` SHALL fetch attachments từ `VectorStore.findByTicketId()` cho main ticket VÀ tối đa 10 linked tickets (MAX_ATTACHMENT_TICKETS=10). Chunks được filter theo `chunkType` là `ATTACHMENT` hoặc `CONFLUENCE`, group by filename, version-deduplicated (files cùng base name nhưng khác version như `v1.7`, `v2.3` → chỉ giữ version mới nhất), và truncate mỗi chunk text tối đa 500 ký tự. Output format bao gồm source ticket ID cho mỗi file.

2.5 THE Phase 1 output SHALL là một string chứa tất cả data thu thập được, format theo sections: `=== KB SEARCH ===`, `=== MAIN TICKET ===`, `=== LINKED: {id} ===`, `=== RELATIONSHIPS ===`, `=== ATTACHMENTS ===`. Output này được inject vào Phase 2 và Phase 3 prompts làm data context.

2.6 THE Phase 1 SHALL có timeout riêng (mặc định 180 giây) nhưng thường hoàn thành trong 1-7 giây vì không cần AI.

### Requirement 3: Phase 2 — BRD Writing Phase

**User Story:** Là một BA/PM, tôi muốn phase viết BRD chỉ tập trung vào việc đọc data từ KB và viết document, không phải thu thập data hay sinh diagrams, để BRD content có chất lượng cao hơn.

#### Acceptance Criteria

3.1 THE Phase 2 prompt SHALL chứa: BRD template (7 sections chuẩn từ `BrdPromptBuilder.BRD_SECTIONS`), KB tool definitions, và instructions đọc data từ KB_Memory. Prompt KHÔNG chứa Jira tool definitions, diagram instructions, hay recursive exploration strategy.

3.2 THE Phase 2 Tool_Filter SHALL chỉ include KB tools (tên chứa "kb_search", "kb_search_smart", "kb_read", "kb_context"). Jira tools, diagram tools, và document conversion tools bị exclude.

3.3 THE Phase 2 prompt SHALL hướng dẫn AI agent: (1) gọi `kb_context` hoặc `kb_search` để lấy collection summary từ Phase 1, (2) gọi `kb_read` cho từng ticket cần chi tiết, (3) viết BRD markdown với 7 sections chuẩn sử dụng data từ KB.

3.4 THE Phase 2 output SHALL là BRD markdown text hoàn chỉnh (không có diagrams). Output này được truyền cho Assembly_Step.

3.5 THE Phase 2 prompt SHALL include MANDATORY instructions cho AI chèn diagram placeholder markers: `<!-- DIAGRAM:PROCESS_FLOW -->` (cuối section 4), `<!-- DIAGRAM:ACTIVITY -->` (sau Process Overview trong section 5), `<!-- DIAGRAM:DATA_MODEL -->` (sau Data Requirements trong section 5), `<!-- DIAGRAM:DEPLOYMENT -->` (cuối section 7). Prompt SHALL chứa `⚠️ CRITICAL` warning rằng nếu không chèn placeholders thì diagrams sẽ bị mất. Nếu AI vẫn không chèn placeholders, Assembly_Step sẽ fallback sang section injection.

3.8 THE Phase 2 prompt SHALL hướng dẫn AI reference attachment data (nếu có trong collected data) trong Appendix > Document References, liệt kê filename và tóm tắt relevance.

3.6 THE Phase 2 SHALL có max tool calls riêng (mặc định 15) và timeout riêng (mặc định 120 giây).

3.7 THE Phase 2 prompt size SHALL không vượt quá 20,000 ký tự.

### Requirement 4: Phase 3 — Diagram Generation Phase

**User Story:** Là một BA/PM, tôi muốn phase sinh diagrams chỉ tập trung vào việc tạo draw.io XML từ data trong KB, để diagram quality cao hơn so với việc sinh cùng lúc với BRD text.

#### Acceptance Criteria

4.1 THE Phase 3 prompt SHALL chứa: diagram instructions (draw.io XML templates từ `AgenticDiagramSections`), KB tool definitions, và instructions đọc data từ KB_Memory. Prompt KHÔNG chứa BRD template, Jira tools, hay recursive exploration strategy.

4.2 THE Phase 3 Tool_Filter SHALL chỉ include: KB tools (tên chứa "kb_search", "kb_read", "kb_context"), và diagram tools (tên chứa "drawio", "draw.io", "diagram") nếu có. Jira tools và document conversion tools bị exclude.

4.3 THE Phase 3 prompt SHALL hướng dẫn AI agent: (1) gọi `kb_context` để lấy tổng quan data, (2) sinh draw.io XML cho mỗi loại diagram cần thiết, (3) output mỗi diagram dưới dạng labeled XML block.

4.4 THE Phase 3 output SHALL là text chứa các draw.io XML blocks, mỗi block có label identifier: `<!-- DIAGRAM:PROCESS_FLOW -->`, `<!-- DIAGRAM:ACTIVITY -->`, `<!-- DIAGRAM:DATA_MODEL -->`, `<!-- DIAGRAM:DEPLOYMENT -->` theo sau bởi XML code block.

4.5 THE Phase 3 SHALL có max tool calls riêng (mặc định 10) và timeout riêng (mặc định 90 giây).

4.6 THE Phase 3 prompt size SHALL không vượt quá 15,000 ký tự.

4.7 THE Phase 3 CÓ THỂ chạy song song với Phase 2 vì cả hai chỉ đọc từ KB_Memory (không ghi). Pipeline_Orchestrator quyết định chạy song song hay tuần tự dựa trên cấu hình.

### Requirement 5: Assembly Step — Merge BRD + Diagrams

**User Story:** Là một developer, tôi muốn có một bước assembly thuần code (không AI) merge outputs từ Phase 2 và Phase 3 thành BRD hoàn chỉnh, để kết quả cuối cùng nhất quán và không phụ thuộc vào AI cho bước merge.

#### Acceptance Criteria

5.1 THE Assembly_Step SHALL nhận 2 inputs: BRD markdown từ Phase 2 và diagram XML blocks từ Phase 3. Assembly_Step là pure Kotlin code, không gọi AI.

5.2 THE Assembly_Step SHALL parse diagram output từ Phase 3 bằng cách tìm các labeled blocks (`<!-- DIAGRAM:PROCESS_FLOW -->`, etc.) và extract XML code blocks tương ứng.

5.3 THE Assembly_Step SHALL sử dụng 3-branch logic:
  - WHEN BRD markdown chứa placeholder markers (`<!-- DIAGRAM:LABEL -->`) → replace placeholders bằng diagram XML tương ứng. Placeholders không có diagram → thay bằng `[Diagram không khả dụng]`.
  - WHEN BRD markdown KHÔNG chứa placeholder markers nhưng diagrams được parse thành công → inject diagram XML vào cuối section tương ứng trong BRD (sử dụng `SECTION_TARGETS` map: PROCESS_FLOW → "Existing Processes", ACTIVITY → "Project Requirements", DATA_MODEL → "Data Requirements", DEPLOYMENT → "Appendix"). Insert point là trước heading `## ` tiếp theo.
  - WHEN diagram output không parse được thành labeled blocks → append raw diagram output trước section Appendix.

5.4 THE Assembly_Step SHALL trả về BRD markdown hoàn chỉnh tương thích với `BrdResponseParser` hiện tại — output phải có 7 sections chuẩn với diagrams embedded bên trong sections.

5.5 IF Phase 3 fail hoặc không có output, THE Assembly_Step SHALL trả về BRD từ Phase 2 không có diagrams (graceful degradation). BRD vẫn hợp lệ và có giá trị mà không cần diagrams.

### Requirement 6: Phase-Specific Prompt Builders

**User Story:** Là một developer, tôi muốn mỗi phase có prompt builder riêng, tách biệt và tập trung, để prompt size nhỏ và AI model nhận instructions rõ ràng cho từng task.

#### Acceptance Criteria

6.1 THE Phase_Prompt_Builder cho Phase 1 SHALL reuse `appendSystemInstructions()`, `appendToolDefinitions()`, `appendToolProtocol()`, và `appendDataCollectionStrategy()` từ codebase hiện tại. Prompt builder KHÔNG include `appendBrdSections()` hay `appendDiagramInstructions()`.

6.2 THE Phase_Prompt_Builder cho Phase 2 SHALL reuse `appendSystemInstructions()`, `appendToolDefinitions()`, `appendToolProtocol()`, và `appendBrdSections()` từ codebase hiện tại. Prompt builder KHÔNG include `appendDataCollectionStrategy()` hay `appendDiagramInstructions()`.

6.3 THE Phase_Prompt_Builder cho Phase 3 SHALL reuse `appendSystemInstructions()`, `appendToolDefinitions()`, `appendToolProtocol()`, và `appendDiagramInstructions()` từ codebase hiện tại. Prompt builder KHÔNG include `appendBrdSections()` hay `appendDataCollectionStrategy()`.

6.4 FOR ALL phase prompt builders, THE prompt output size SHALL được đo và log. WHEN prompt size vượt ngưỡng cho phase đó (Phase 1: 20K, Phase 2: 20K, Phase 3: 15K), THE prompt builder SHALL log warning nhưng vẫn tiếp tục (soft limit, không hard fail).

6.5 THE phase prompt builders SHALL được tổ chức trong file(s) riêng, tách biệt khỏi `AgenticPromptBuilder` hiện tại, tuân thủ SRP và giới hạn 200 dòng/file.

### Requirement 7: Tool Filtering per Phase

**User Story:** Là một developer, tôi muốn mỗi phase chỉ nhận tools cần thiết cho task của nó, để prompt size nhỏ hơn và AI model không bị confused bởi tools không liên quan.

#### Acceptance Criteria

7.1 THE Tool_Filter SHALL nhận `List<ToolDescriptor>` đầy đủ và trả về `List<ToolDescriptor>` đã lọc cho một phase cụ thể. Filter logic sử dụng case-insensitive pattern matching trên `ToolDescriptor.name`, reuse approach từ `AgenticToolDetector` hiện tại.

7.2 THE Tool_Filter cho Phase 1 SHALL include tools matching patterns: "get_issue", "search" + "jira", "analyze_ticket", "get_ticket_analysis", "kb_search", "kb_read", "kb_context", "kb_ingest", "kb_write", "convert_to_markdown", "markitdown". Tất cả tools khác bị exclude.

7.3 THE Tool_Filter cho Phase 2 SHALL include tools matching patterns: "kb_search", "kb_search_smart", "kb_read", "kb_context". Tất cả tools khác bị exclude.

7.4 THE Tool_Filter cho Phase 3 SHALL include tools matching patterns: "kb_search", "kb_read", "kb_context", "drawio", "draw.io", "diagram". Tất cả tools khác bị exclude.

7.5 THE Tool_Filter SHALL luôn exclude tools matching `EXCLUDED_PATTERNS` hiện tại ("playwright", "browser") bất kể phase nào.

7.6 FOR ALL phase tool filters, THE filter output SHALL là deterministic: cùng input luôn cho cùng output, không phụ thuộc thứ tự tools trong danh sách.

### Requirement 8: Backward Compatibility — Single-Phase Fallback

**User Story:** Là một developer, tôi muốn pipeline mới backward compatible với hệ thống hiện tại, để BRD generation vẫn hoạt động khi KB không available hoặc khi cần fallback.

#### Acceptance Criteria

8.1 WHEN KB tools không khả dụng trong danh sách Tool_Descriptor, THE Pipeline_Orchestrator SHALL fallback về Single_Phase_Mode — chạy `AgenticLoopRunner.runLoop()` một lần duy nhất với prompt hiện tại (từ `AgenticPromptBuilder.buildInitialPrompt()`), behavior giống hệt pipeline hiện tại.

8.2 THE `AiBackendPipelineStrategy` SHALL được cập nhật để sử dụng Pipeline_Orchestrator thay vì gọi trực tiếp `AgenticLoopRunner.runLoop()`. Pipeline_Orchestrator tự quyết định Single_Phase_Mode hay Multi_Phase_Mode.

8.3 THE Pipeline_Orchestrator output (`AgenticLoopResult`) SHALL tương thích với `AgenticLoopRunner.determineStatus()` và `buildTaskResult()` hiện tại trong `AiBackendPipelineStrategy`. Không cần thay đổi status determination logic.

8.4 FOR ALL existing test cases của `AiBackendPipelineStrategy`, `AgenticLoopRunner`, và `AgenticPromptBuilder`, THE refactored code SHALL pass tất cả tests hiện tại.

8.5 THE `AgenticLoopRunner`, `AgenticPromptBuilder`, và `OllamaApiClient` SHALL không bị modified — chỉ được reuse bởi Pipeline_Orchestrator. `ToolExecutionBridge` được mở rộng thêm Local KB routing (xem bugfix spec `brd-pipeline-local-kb-integration`) nhưng routing logic cho internal MCP và external MCP tools không thay đổi. Thay đổi chỉ ở orchestration layer và Local KB integration layer.

### Requirement 9: Performance — Tổng thời gian pipeline

**User Story:** Là một BA/PM, tôi muốn multi-phase pipeline không chậm hơn đáng kể so với single-phase, để trải nghiệm người dùng không bị ảnh hưởng tiêu cực.

#### Acceptance Criteria

9.1 THE Pipeline_Orchestrator hiện tại chạy Phase 2 và Phase 3 **tuần tự** (không song song) vì `OllamaApiClient` có shared `conversationHistory` — chạy parallel gây conflict session. Parallel execution code vẫn tồn tại nhưng bị disable. Khi chuyển sang AI backend hỗ trợ multiple sessions, có thể enable lại.

9.2 ~~THE Pipeline_Orchestrator SHALL sử dụng Kotlin coroutines (`async`/`await`) để chạy Phase 2 và Phase 3 song song~~ — DISABLED do shared session conflict. Phase 2 và Phase 3 chạy tuần tự.

9.3 THE Phase_Config cho mỗi phase SHALL có timeout riêng biệt. Tổng timeout của pipeline = Phase 1 timeout + max(Phase 2 timeout, Phase 3 timeout) + Assembly timeout (cố định 5 giây).

9.4 THE Pipeline_Orchestrator SHALL log duration của mỗi phase và tổng pipeline duration để monitoring và tuning. Ngoài ra, `PipelineInteractionLogger` SHALL ghi chi tiết từng turn tương tác BA Agent ↔ AI qua SLF4J logger `"PipelineInteraction"`, output vào `data/logs/pipeline-interaction.log` (daily rolling, 14 ngày) và console. Mỗi turn bao gồm: prompt gửi đi (→), AI response (←), tool calls (→), tool results (←), với timestamp và content preview.

### Requirement 10: Data Collection Protocol — DataPreFetcher Output Format

**User Story:** Là một developer, tôi muốn có protocol rõ ràng cho cách data được thu thập và format bởi DataPreFetcher, để Phase 2 và Phase 3 nhận được context đầy đủ và có cấu trúc.

#### Acceptance Criteria

10.1 THE `DataPreFetcher.fetchAll()` SHALL output data theo format sections: `=== KB SEARCH ===` (semantic search results), `=== MAIN TICKET: {ticketId} ===` (main ticket details), `=== LINKED: {linkedId} ===` (mỗi linked ticket), `=== RELATIONSHIPS ===` (ticket relationships), `=== ATTACHMENTS ===` (attachment chunks grouped by filename).

10.2 THE `DataPreFetcher` SHALL format mỗi KB record bao gồm: ticketId, summary (requirementSummary), business summary, AS-IS state, TO-BE state, scrum points, confidence score, rationale, extracted requirements, blocking issues, related issues, acceptance criteria, và similar ticket refs.

10.3 THE Phase 2 và Phase 3 prompts SHALL nhận toàn bộ DataPreFetcher output text làm data context, inject trực tiếp vào prompt. AI agent sử dụng data này để viết BRD/sinh diagrams mà không cần gọi thêm KB tools (mặc dù KB tools vẫn available nếu cần bổ sung).

10.4 THE `DataPreFetcher` SHALL log chi tiết mỗi bước thu thập qua `PipelineInteractionLogger`: tool call name, params, success/failure, result size. Điều này cho phép debug và audit quá trình data collection.

10.5 IF một bước trong DataPreFetcher fail (ví dụ: KBRepository trả null, VectorStore throw exception), THE DataPreFetcher SHALL skip bước đó và tiếp tục các bước còn lại. Không fail toàn bộ collection vì một bước lỗi.
