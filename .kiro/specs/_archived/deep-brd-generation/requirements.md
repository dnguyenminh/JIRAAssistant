# Deep BRD Generation — Requirements

## Introduction

Hiện tại, hệ thống Jira Assistant sinh tài liệu BRD (Business Requirements Document) thông qua BA Agent sử dụng agentic loop (`AgenticLoopRunner` + `AgenticPromptBuilder` + `ToolExecutionBridge`). Tuy nhiên, chất lượng BRD bị hạn chế bởi 3 vấn đề chính:

1. **Linked ticket exploration không đệ quy**: `AgenticDataStrategy` hiện tại hướng dẫn AI gọi `get_issue` cho linked tickets ở Step 3, nhưng chỉ 1 level — không đệ quy vào linked tickets của linked tickets, không đọc attachments của linked tickets. Kết quả: BRD thiếu context từ các ticket liên quan sâu hơn.

2. **Không tận dụng Knowledge Base caching**: Mỗi lần sinh BRD, AI agent gọi Jira API trực tiếp cho mọi ticket — kể cả ticket đã được phân tích và lưu trong Knowledge Base. Điều này gây lãng phí thời gian và API calls, đặc biệt khi cùng ticket được reference bởi nhiều BRD khác nhau.

3. **Thiếu technical diagrams**: BRD sinh ra chỉ có text — không có Sequence diagram, Class diagram, Activity diagram, hay Deployment diagram. Các diagram này rất quan trọng để stakeholders hiểu rõ luồng xử lý, kiến trúc hệ thống, và mô hình dữ liệu.

Feature này cải thiện chất lượng BRD bằng cách: (a) hướng dẫn AI agent thực hiện recursive deep exploration cho linked tickets, (b) sử dụng Knowledge Base MCP tools làm cache layer trước khi gọi Jira API, và (c) yêu cầu AI sinh các technical diagrams ở định dạng draw.io XML trong BRD.

## Glossary

- **BA_Agent**: AI agent (chạy trên Ollama) thực hiện agentic loop để thu thập dữ liệu và sinh BRD. Sử dụng `AgenticLoopRunner` để lặp: gửi prompt → nhận tool call → thực thi tool → gửi kết quả → lặp lại cho đến khi sinh document cuối cùng
- **Agentic_Prompt**: Prompt được xây dựng bởi `AgenticPromptBuilder` và các section builders trong `AgenticPromptSections.kt`, chứa system instructions, tool definitions, tool protocol, BRD structure, và data collection strategy
- **Data_Collection_Strategy**: Phần prompt trong `AgenticDataStrategy.kt` hướng dẫn AI agent thu thập dữ liệu theo các bước tuần tự trước khi viết BRD. Hiện tại có 5 steps — feature này thay thế bằng strategy mới với recursive exploration và KB caching
- **Knowledge_Base_Tools**: Các MCP tools từ Knowledge Base server (external MCP) cho phép tìm kiếm, đọc, và lưu trữ dữ liệu ticket đã phân tích. Bao gồm: `kb_search`, `kb_search_smart`, `kb_read`, `kb_context`, `kb_ingest`, `kb_write`
- **Jira_Tools**: Các MCP tools (internal hoặc external) để tương tác với Jira API: `get_issue`, `search_jira`, `analyze_ticket`, `get_ticket_analysis`
- **Recursive_Exploration**: Quá trình đệ quy khám phá linked tickets: main ticket → linked tickets → mỗi linked ticket's attachments + linked tickets → tiếp tục cho đến khi đạt depth limit hoặc không còn linked tickets mới
- **Depth_Limit**: Giới hạn độ sâu đệ quy khi khám phá linked tickets, tránh vòng lặp vô hạn. Mặc định là 3 levels
- **Visited_Set**: Tập hợp ticket IDs đã được khám phá trong quá trình recursive exploration, dùng để phát hiện cycle và tránh xử lý trùng lặp
- **KB_Cache_Strategy**: Chiến lược tối ưu: kiểm tra Knowledge Base trước → nếu có dữ liệu thì dùng → nếu không có thì gọi Jira API → lưu kết quả vào KB → sử dụng dữ liệu từ KB cho BRD
- **DrawIO_Diagram**: Diagram kỹ thuật ở định dạng draw.io XML, có thể import trực tiếp vào draw.io/diagrams.net. Bao gồm 4 loại: Sequence, Class, Activity, Deployment
- **Tool_Descriptor**: Object mô tả một tool khả dụng cho AI agent, chứa `name` và `description`. Được lấy từ `InternalMcpBridge` (30 internal tools) và `McpProcessManager` (external MCP tools từ database)
- **Prompt_Section**: Một phần của Agentic_Prompt, được xây dựng bởi extension functions trên `StringBuilder` trong `AgenticPromptSections.kt`

## Requirements

### Requirement 1: Recursive Deep Exploration Strategy cho Linked Tickets

**User Story:** Là một BA/PM, tôi muốn AI agent tự động khám phá đệ quy tất cả linked tickets (bao gồm attachments và linked tickets của linked tickets) khi sinh BRD, để BRD có đầy đủ context từ toàn bộ hệ sinh thái ticket liên quan.

#### Acceptance Criteria

1.1 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent thực hiện Recursive_Exploration bắt đầu từ main ticket: đọc main ticket → xác định linked tickets → với mỗi linked ticket, đọc ticket details, attachments, và linked tickets của nó → tiếp tục đệ quy cho đến khi đạt Depth_Limit hoặc không còn linked tickets mới.

1.2 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent duy trì Visited_Set trong quá trình exploration. WHEN một ticket ID đã có trong Visited_Set, THE BA_Agent SHALL bỏ qua ticket đó để tránh cycle và xử lý trùng lặp.

1.3 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent đọc attachments của mỗi linked ticket (không chỉ main ticket). WHEN một linked ticket có attachments, THE BA_Agent SHALL sử dụng các tool khả dụng để lấy nội dung attachment trước khi tiếp tục khám phá linked tickets tiếp theo.

1.4 THE Data_Collection_Strategy SHALL cấu hình Depth_Limit mặc định là 3 levels (main ticket = level 0, linked tickets trực tiếp = level 1, linked tickets của linked tickets = level 2, level 3 = cuối cùng). Depth_Limit được định nghĩa là hằng số trong code, không hardcode trong prompt text.

1.5 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent ưu tiên thứ tự exploration: (1) parent/epic ticket, (2) blocking/blocked-by tickets, (3) relates-to tickets, (4) sub-tasks, (5) ticket IDs được nhắc đến trong text fields (description, comments).

1.6 IF BA_Agent không tìm thấy thêm linked tickets mới (tất cả đã có trong Visited_Set) trước khi đạt Depth_Limit, THEN THE BA_Agent SHALL dừng exploration sớm và chuyển sang bước sinh BRD.

1.7 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent giới hạn tổng số tickets khám phá tối đa 30 tickets (bao gồm main ticket) để tránh agentic loop chạy quá lâu. Giới hạn này được định nghĩa là hằng số trong code.

1.8 THE `AgenticDataStrategy.kt` SHALL được refactor để thay thế `appendDataCollectionStrategy()` hiện tại (5 steps không đệ quy) bằng strategy mới với recursive exploration, KB caching, và diagram generation instructions.

### Requirement 2: Knowledge Base Caching Strategy — KB First, Jira Fallback

**User Story:** Là một BA/PM, tôi muốn hệ thống kiểm tra Knowledge Base trước khi gọi Jira API cho mỗi ticket, để tối ưu thời gian sinh BRD và tránh gọi API trùng lặp cho ticket đã được phân tích.

#### Acceptance Criteria

2.1 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent thực hiện KB_Cache_Strategy cho mỗi ticket cần khám phá: (1) tìm kiếm ticket trong Knowledge Base bằng KB tools, (2) nếu tìm thấy dữ liệu đầy đủ thì sử dụng dữ liệu từ KB, (3) nếu không tìm thấy hoặc dữ liệu không đầy đủ thì gọi Jira tools để lấy dữ liệu mới.

2.2 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent phát hiện KB tools từ danh sách Tool_Descriptor khả dụng bằng cách tìm kiếm tools có tên chứa các pattern: "kb_search", "kb_search_smart", "kb_read", "kb_context", "kb_ingest", "kb_write". Không hardcode tên tool cụ thể — sử dụng pattern matching động.

2.3 WHEN KB tools khả dụng, THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent sử dụng `kb_search_smart` hoặc `kb_search` (tùy tool nào khả dụng) với ticket ID làm query để kiểm tra dữ liệu đã có trong KB.

2.4 WHEN KB trả về kết quả cho một ticket, THE BA_Agent SHALL đánh giá dữ liệu: nếu có đủ thông tin (summary, description, requirements, analysis) thì sử dụng trực tiếp; nếu chỉ có metadata cơ bản thì vẫn gọi Jira tools để bổ sung.

2.5 WHEN BA_Agent lấy dữ liệu mới từ Jira tools cho một ticket, THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent lưu dữ liệu vào Knowledge Base bằng `kb_ingest` hoặc `kb_write` (tùy tool khả dụng) để các lần sinh BRD sau có thể tái sử dụng.

2.6 IF KB tools không khả dụng (không có trong danh sách Tool_Descriptor), THEN THE Data_Collection_Strategy SHALL fallback về behavior hiện tại — gọi Jira tools trực tiếp mà không có caching layer. Strategy phải hoạt động đúng cả khi có và không có KB tools.

2.7 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent sử dụng `kb_context` (nếu khả dụng) để lấy briefing tổng quan về ticket trước khi quyết định cần đọc chi tiết hay không, giúp tiết kiệm token context.

### Requirement 3: Dynamic Tool Detection — Phát hiện tools từ danh sách khả dụng

**User Story:** Là một developer, tôi muốn Data_Collection_Strategy phát hiện và sử dụng tools một cách động dựa trên danh sách Tool_Descriptor thực tế, để strategy hoạt động đúng bất kể bộ tools nào được cấu hình.

#### Acceptance Criteria

3.1 THE `appendDataCollectionStrategy()` function SHALL nhận danh sách `List<ToolDescriptor>` làm tham số và phát hiện các nhóm tools khả dụng: Jira tools (chứa "get_issue", "search", "analyze_ticket"), KB tools (chứa "kb_search", "kb_read", "kb_ingest"), và analysis tools (chứa "get_ticket_analysis", "analyze_ticket").

3.2 THE Data_Collection_Strategy SHALL tạo prompt instructions khác nhau tùy thuộc vào bộ tools khả dụng: (a) có cả KB + Jira tools → full KB caching strategy, (b) chỉ có Jira tools → direct Jira strategy không có caching, (c) chỉ có KB tools → KB-only strategy.

3.3 THE Data_Collection_Strategy SHALL sử dụng tên tool thực tế từ Tool_Descriptor (ví dụ: `mcp_knowledge_base_kb_search_smart`) trong prompt instructions, không sử dụng tên tool giả định hoặc hardcoded.

3.4 THE tool detection logic SHALL sử dụng case-insensitive pattern matching trên `ToolDescriptor.name` để phát hiện tool categories, đảm bảo hoạt động với các naming conventions khác nhau của MCP servers. Matching chỉ dựa trên `name` — `description` không được sử dụng cho detection vì tool names đã đủ để phân loại chính xác.

3.5 FOR ALL danh sách Tool_Descriptor inputs, THE tool detection logic SHALL trả về kết quả nhất quán: cùng input luôn cho cùng output (deterministic property). Không phụ thuộc vào thứ tự tools trong danh sách.

### Requirement 4: Draw.io Diagrams trong BRD — Sequence, Class, Activity, Deployment

**User Story:** Là một BA/PM, tôi muốn BRD sinh ra bao gồm các technical diagrams (Sequence, Class, Activity, Deployment) ở định dạng draw.io XML, để stakeholders có thể hiểu rõ luồng xử lý, kiến trúc hệ thống, và mô hình dữ liệu mà không cần đọc toàn bộ text.

#### Acceptance Criteria

4.1 THE Agentic_Prompt SHALL hướng dẫn BA_Agent sinh 4 loại DrawIO_Diagram trong BRD: (1) Sequence diagram cho luồng tương tác giữa các actors/systems, (2) Class diagram cho mô hình dữ liệu và quan hệ giữa các entities, (3) Activity diagram cho quy trình nghiệp vụ (business process flow), (4) Deployment diagram cho kiến trúc hệ thống và các components.

4.2 THE Agentic_Prompt SHALL cung cấp draw.io XML template/example cho mỗi loại diagram, để BA_Agent biết cấu trúc XML chính xác cần sinh. Templates phải bao gồm: `<mxGraphModel>`, `<root>`, `<mxCell>` elements với đúng attributes (id, value, style, vertex, edge, source, target).

4.3 THE Agentic_Prompt SHALL hướng dẫn BA_Agent nhúng mỗi diagram trong BRD dưới dạng code block với language tag `xml` và heading mô tả loại diagram, ví dụ: `### Sequence Diagram: [Tên luồng]` theo sau bởi code block chứa draw.io XML.

4.4 THE DrawIO_Diagram XML sinh ra SHALL là valid draw.io XML có thể import trực tiếp vào draw.io/diagrams.net mà không cần chỉnh sửa. Cụ thể: phải có root element `<mxGraphModel>`, mỗi node/edge phải có unique `id`, edges phải reference đúng `source` và `target` IDs.

4.5 THE Agentic_Prompt SHALL hướng dẫn BA_Agent đặt diagrams **BÊN TRONG** các section chuẩn của BRD (không tạo heading ## mới cho diagrams). Cụ thể: Process Flow diagram trong "Existing Processes", Activity diagram trong "Project Requirements" > Process Overview, Class/Data diagram trong "Project Requirements" > Data Requirements, Deployment diagram trong "Appendix". Điều này đảm bảo `BrdResponseParser` (chỉ giữ 7 sections chuẩn) không bỏ mất diagrams.

4.6 THE diagram instructions SHALL được thêm vào prompt thông qua một Prompt_Section mới (extension function trên StringBuilder), tách biệt khỏi các sections hiện tại, tuân thủ SRP và giới hạn 200 dòng/file.

4.7 WHEN BA_Agent không có đủ dữ liệu để sinh một loại diagram cụ thể (ví dụ: không có thông tin về deployment architecture), THE Agentic_Prompt SHALL hướng dẫn BA_Agent bỏ qua diagram đó và ghi chú "[Diagram không khả dụng: thiếu dữ liệu về {topic}]" thay vì sinh diagram rỗng hoặc sai.

4.8 FOR ALL DrawIO_Diagram XML outputs, mỗi `<mxCell>` element với `edge="1"` SHALL có cả `source` và `target` attributes reference đến `id` của các `<mxCell>` elements khác trong cùng diagram (referential integrity property).

### Requirement 5: Cập nhật AgenticPromptSections — Tích hợp sections mới

**User Story:** Là một developer, tôi muốn các prompt sections mới (recursive exploration, KB caching, diagram instructions) được tích hợp vào `AgenticPromptBuilder` và `AgenticPromptSections.kt` theo đúng kiến trúc hiện tại, để code dễ bảo trì và mở rộng.

#### Acceptance Criteria

5.1 THE `AgenticPromptSections.kt` SHALL được mở rộng (hoặc tách thành file mới nếu vượt 200 dòng) với extension function mới `appendDiagramInstructions()` trên `StringBuilder`, chứa draw.io XML templates và hướng dẫn sinh diagrams.

5.2 THE `AgenticDataStrategy.kt` SHALL được refactor: function `appendDataCollectionStrategy()` hiện tại được thay thế bằng version mới nhận thêm tham số `tools: List<ToolDescriptor>` để thực hiện dynamic tool detection và sinh strategy phù hợp (KB caching + recursive exploration).

5.3 THE `buildToolCallingPrompt()` trong `AgenticPromptSections.kt` SHALL gọi `appendDiagramInstructions()` sau `appendBrdSections()` và trước `appendToolTask()`, đảm bảo diagram instructions nằm trong prompt gửi cho BA_Agent.

5.4 THE `appendToolTask()` function SHALL được cập nhật để gọi `appendDataCollectionStrategy()` mới với tham số `tools`, thay vì version cũ không có dynamic tool detection.

5.5 WHEN tổng số dòng của `AgenticPromptSections.kt` vượt 200 dòng sau khi thêm diagram instructions, THE developer SHALL tách diagram-related sections vào file mới `AgenticDiagramSections.kt` trong cùng package, tuân thủ giới hạn 200 dòng/file.

5.6 WHEN tổng số dòng của `AgenticDataStrategy.kt` vượt 200 dòng sau khi thêm KB caching và recursive exploration logic, THE developer SHALL tách thành nhiều file theo trách nhiệm: `AgenticDataStrategy.kt` (orchestration), `AgenticKbCacheStrategy.kt` (KB caching logic), `AgenticRecursiveExploration.kt` (recursive exploration logic).

5.7 THE `buildStatelessContinuation()` trong `AgenticPromptBuilder.kt` SHALL bao gồm diagram instructions trong continuation prompt, đảm bảo BA_Agent nhớ yêu cầu sinh diagrams ngay cả sau nhiều tool call iterations.

### Requirement 6: Prompt Size Management — Quản lý kích thước prompt với deep data

**User Story:** Là một developer, tôi muốn prompt builder quản lý kích thước prompt hiệu quả khi có nhiều dữ liệu từ recursive exploration, để prompt không vượt quá context window của AI model.

#### Acceptance Criteria

6.1 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent tóm tắt dữ liệu từ linked tickets ở depth >= 2 thay vì đưa toàn bộ raw data vào context, giảm kích thước prompt trong khi vẫn giữ thông tin quan trọng.

6.2 THE Data_Collection_Strategy SHALL hướng dẫn BA_Agent ưu tiên dữ liệu theo thứ tự: (1) main ticket full details, (2) linked tickets depth 1 — details + attachments, (3) linked tickets depth 2+ — chỉ summary và key requirements, (4) attachment content — ưu tiên specs và design docs trước screenshots.

6.3 THE `buildStatelessContinuation()` SHALL giới hạn kích thước collected data trong continuation prompt. WHEN tổng kích thước tool results vượt quá ngưỡng (định nghĩa là hằng số, mặc định 80,000 ký tự), THE prompt builder SHALL truncate tool results cũ nhất trước và thêm annotation "[TRUNCATED: {N} earlier tool results omitted due to prompt size limit]".

6.4 THE truncation logic SHALL giữ nguyên tool result gần nhất (latest) và main ticket data, chỉ truncate tool results từ linked tickets ở depth cao hơn.

6.5 FOR ALL prompt outputs, THE prompt builder SHALL đảm bảo prompt luôn chứa: system instructions, tool definitions, tool protocol, BRD structure, diagram instructions, và ít nhất main ticket data. Các phần này không bao giờ bị truncate.

### Requirement 7: Backward Compatibility — Không break flow hiện tại

**User Story:** Là một developer, tôi muốn các thay đổi trong Data_Collection_Strategy và prompt sections không break flow hiện tại của agentic loop, để hệ thống vẫn hoạt động đúng khi không có KB tools hoặc khi AI model không hỗ trợ recursive exploration.

#### Acceptance Criteria

7.1 WHEN KB tools không khả dụng trong danh sách Tool_Descriptor, THE Data_Collection_Strategy SHALL sinh prompt tương đương với behavior hiện tại (direct Jira calls, không caching), đảm bảo BRD vẫn được sinh thành công.

7.2 THE `AgenticLoopRunner` SHALL được mở rộng với **nudge retry mechanism**: khi AI model trả về BRD trực tiếp mà không gọi tool nào (`toolCallsExecuted == 0`), loop sẽ gửi một nudge message yêu cầu AI gọi tool trước, rồi retry một lần. Nếu AI vẫn không gọi tool sau nudge, loop chấp nhận response như final document. Thay đổi này không ảnh hưởng đến tool execution logic hay prompt building — chỉ thêm safety net cho trường hợp AI model bỏ qua tool-calling protocol.

7.3 THE `ToolExecutionBridge` SHALL được mở rộng với **MCP client retry mechanism**: khi `getClient()` trả về null (MCP server chưa ready), bridge sẽ đợi 3 giây rồi retry một lần. Điều này xử lý race condition khi BRD generation được trigger ngay sau khi server start, trước khi MCP servers kịp khởi động. KB tools và Jira tools vẫn được route qua cùng MCP bridge mechanism hiện tại.

7.4 THE `AiBackendPipelineStrategy` SHALL không cần thay đổi — tool filtering logic (`EXCLUDED_PATTERNS`) vẫn hoạt động đúng với KB tools (KB tools không match bất kỳ excluded pattern nào).

7.5 THE `filterExcludedTools()` function trong `AgenticPromptBuilder.kt` SHALL không exclude KB tools. KB tool names (chứa "kb_search", "kb_read", "kb_ingest") không match với `EXCLUDED_PATTERNS` hiện tại ("playwright", "browser", "convert_to_markdown").

7.6 WHEN AI model không follow recursive exploration instructions (ví dụ: chỉ gọi 1-2 tools rồi sinh BRD), THE agentic loop SHALL vẫn hoàn thành bình thường — recursive exploration là hướng dẫn trong prompt, không phải logic bắt buộc trong code.

7.7 FOR ALL existing test cases của `AgenticPromptBuilder` và `AgenticDataStrategy`, THE refactored code SHALL pass tất cả tests hiện tại (backward compatibility property).
