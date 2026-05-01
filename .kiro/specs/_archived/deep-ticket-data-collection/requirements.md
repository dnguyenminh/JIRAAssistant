# Deep Ticket Data Collection — Requirements

## Introduction

Hiện tại, hệ thống Jira Assistant thu thập dữ liệu ticket để sinh tài liệu BRD/FSD bị mất thông tin nghiêm trọng ở nhiều tầng:

1. **Comments bị cắt**: `JiraFieldMappers.mapComments()` chỉ lấy 20 comment cuối. Khi sinh BRD, nội dung raw comment KHÔNG được đưa vào prompt — chỉ có `businessSummary`/`extractedRequirements` từ AI summary. Nếu AI summary bỏ sót chi tiết, thông tin mất vĩnh viễn.

2. **Attachment chỉ có metadata**: `JiraFieldMappers.mapAttachments()` chỉ trích xuất filename, mimeType, size — KHÔNG có nội dung. Nội dung attachment chỉ khả dụng qua semantic search (top 10 chunks theo cosine similarity), dễ bỏ sót chunks quan trọng nếu search query không tốt.

3. **Linked tickets bị giới hạn nghiêm trọng**:
   - Trong analysis: `JiraContentExtractorImpl` fetch tối đa 5 linked tickets, chỉ 1 level, description bị cắt 500 ký tự
   - Trong BRD generation: `DocumentAggregatorImpl` fetch tối đa 20 linked tickets từ KB, nhưng CHỈ nếu đã được analyze. Không tự động analyze linked tickets chưa có trong KB
   - KHÔNG có transitive traversal — nếu Ticket A link đến B, B link đến C, thì C không bao giờ được thấy

4. **Ticket ID references bị bỏ qua hoàn toàn**: Ticket IDs được nhắc đến trong text fields (summary, description, comments) bị ignore. Ví dụ: comment nói "Xem ICL2-100 để biết API spec" — reference đó không bao giờ được follow.

Feature này xây dựng hệ thống **Deep Ticket Data Collection** để giải quyết tất cả các vấn đề trên, đảm bảo KHÔNG mất bất kỳ thông tin nào khi sinh tài liệu BRD/FSD.

## Glossary

- **Deep_Collector**: Component mới thay thế `DocumentAggregatorImpl` hiện tại, chịu trách nhiệm thu thập dữ liệu toàn diện từ tất cả ticket liên quan thông qua traversal đệ quy
- **Ticket_Graph**: Đồ thị (graph) các ticket liên quan được xây dựng bởi Deep_Collector, bắt đầu từ root ticket và mở rộng qua tất cả các mối quan hệ (issue links, sub-tasks, parent, text references). Mỗi node là một ticket, mỗi edge là một mối quan hệ
- **Traversal_Engine**: Component con của Deep_Collector thực hiện BFS/DFS trên Ticket_Graph, phát hiện và follow tất cả các mối quan hệ giữa tickets, với cycle detection và depth limiting
- **Ticket_ID_Extractor**: Component trích xuất ticket IDs (ví dụ: "ICL2-100", "PROJ-42") từ text fields (summary, description, comments, attachment content) bằng regex pattern matching
- **Traversal_Config**: Cấu hình cho Deep_Collector bao gồm: max_depth (độ sâu tối đa), max_tickets (số ticket tối đa), project_scope (giới hạn project keys được phép traverse)
- **Visited_Set**: Tập hợp ticket IDs đã được visit trong quá trình traversal, dùng để phát hiện cycle và tránh fetch trùng lặp
- **Root_Ticket**: Ticket gốc mà user yêu cầu sinh tài liệu, là điểm bắt đầu của traversal
- **Enriched_Context**: Phiên bản mở rộng của `GenerationContext` hiện tại, chứa toàn bộ dữ liệu thu thập được từ tất cả ticket trong Ticket_Graph — bao gồm raw comments, full attachment content, và metadata quan hệ
- **Comment_Collector**: Component thu thập TẤT CẢ comments của một ticket (không giới hạn 20), sử dụng Jira API pagination
- **Attachment_Content_Collector**: Component thu thập TẤT CẢ attachment chunks đã được xử lý cho một ticket từ VectorStore, không giới hạn top-K semantic search
- **Prompt_Assembler**: Component mới thay thế logic trong `BrdPromptSections.kt`, chịu trách nhiệm lắp ráp prompt từ Enriched_Context với quản lý kích thước prompt (truncation strategy khi vượt context window)
- **Relationship_Type**: Loại quan hệ giữa hai tickets: ISSUE_LINK (Jira issue links), SUB_TASK, PARENT, TEXT_REFERENCE (ticket ID được nhắc đến trong text)
- **Collection_Job**: Background job xử lý linked ticket analysis hoặc attachment processing cho một ticket cụ thể. Chạy bất đồng bộ sau khi Deep_Collector hoàn tất traversal nhanh, cho phép quá trình analyze/generation kết thúc sớm
- **Collection_Job_Manager**: Component quản lý lifecycle của Collection_Jobs — tạo, theo dõi progress, và cung cấp API để frontend monitor danh sách jobs đang chạy với % hoàn thành

## Requirements

### Requirement 1: Deep Ticket Traversal — Đệ quy khám phá tất cả ticket liên quan

**User Story:** Là một BA/PM, tôi muốn hệ thống tự động khám phá và thu thập dữ liệu từ TẤT CẢ ticket liên quan (ở mọi độ sâu) khi sinh tài liệu BRD/FSD, để không bỏ sót bất kỳ thông tin nào từ các ticket có liên quan.

#### Acceptance Criteria

1.1 WHEN người dùng yêu cầu sinh tài liệu cho một ticket, THE Deep_Collector SHALL bắt đầu từ Root_Ticket và thực hiện traversal đệ quy (BFS) để khám phá tất cả ticket liên quan thông qua 4 loại quan hệ: Jira issue links (outward + inward), sub-tasks, parent ticket, và ticket IDs được nhắc đến trong text fields.

1.2 THE Traversal_Engine SHALL duy trì Visited_Set chứa tất cả ticket IDs đã được visit. WHEN một ticket ID đã có trong Visited_Set, THE Traversal_Engine SHALL bỏ qua ticket đó để tránh cycle và fetch trùng lặp.

1.3 THE Traversal_Engine SHALL xây dựng Ticket_Graph trong quá trình traversal, lưu trữ mỗi ticket đã visit dưới dạng node với metadata: ticketId, depth (khoảng cách từ Root_Ticket), relationship_type (cách ticket này được phát hiện), parent_ticket_id (ticket nào dẫn đến ticket này).

1.4 WHEN Traversal_Engine visit một ticket mới, THE Traversal_Engine SHALL fetch full issue details từ Jira API (bao gồm fields, changelog, comments) và trích xuất tất cả ticket IDs liên quan từ: issue links (outward + inward), sub-tasks, parent key, và kết quả của Ticket_ID_Extractor trên summary, description, và comment bodies.

1.5 THE Traversal_Engine SHALL tuân thủ Traversal_Config: dừng mở rộng khi depth vượt max_depth, dừng thêm ticket mới khi tổng số ticket trong Visited_Set đạt max_tickets. Tickets đã được fetch nhưng chưa mở rộng (do đạt limit) vẫn được giữ trong Ticket_Graph với dữ liệu đã fetch.

1.6 IF Jira API trả về lỗi khi fetch một ticket (404 Not Found, 403 Forbidden, network error), THEN THE Traversal_Engine SHALL log warning với ticket ID và lỗi cụ thể, bỏ qua ticket đó, và tiếp tục traversal các ticket còn lại mà không dừng toàn bộ quá trình.

1.7 THE Traversal_Engine SHALL ưu tiên thứ tự traversal: (1) parent ticket, (2) blocking issue links, (3) other issue links, (4) sub-tasks, (5) text-referenced tickets. Tickets ở depth thấp hơn được xử lý trước tickets ở depth cao hơn (BFS).

1.8 WHEN traversal hoàn tất, THE Deep_Collector SHALL log tổng kết: tổng số tickets discovered, tổng số tickets fetched thành công, số tickets bị skip (do lỗi hoặc limit), max depth đạt được, và thời gian traversal.

1.9 THE Traversal_Engine SHALL gán relevance score cho mỗi ticket trong Ticket_Graph dựa trên: (a) depth — closer to root = higher score, (b) relationship type — blocking > relates > sub-task > text-ref, (c) recency — recently updated tickets score higher, (d) status — active/open tickets score higher than closed/resolved. Score được lưu trong TicketNode metadata và sử dụng bởi Prompt_Assembler để quyết định thứ tự truncation chi tiết hơn (xem Req 6.1).

1.10 WHEN Jira API trả về 403 Forbidden cho một ticket, THE Traversal_Engine SHALL phân loại riêng biệt: log "Skipped {ticketId}: insufficient permissions (403)" ở level WARN, và đưa ticket ID vào `traversalMetadata.permissionDeniedTickets` (danh sách riêng biệt, tách khỏi danh sách lỗi chung). Frontend SHALL hiển thị warning "⚠️ {N} tickets bị bỏ qua do không có quyền truy cập" nếu danh sách không rỗng. Ticket IDs bị 403 KHÔNG được expose ra frontend response — chỉ hiển thị count để tránh information disclosure.

### Requirement 2: Ticket ID Extraction từ Text Fields

**User Story:** Là một BA/PM, tôi muốn hệ thống tự động phát hiện và follow các ticket IDs được nhắc đến trong nội dung text (summary, description, comments, attachment content), để không bỏ sót ticket nào được tham chiếu gián tiếp.

#### Acceptance Criteria

2.1 THE Ticket_ID_Extractor SHALL sử dụng regex pattern để trích xuất ticket IDs từ text. Pattern phải match format chuẩn Jira: một hoặc nhiều ký tự chữ cái viết hoa, theo sau bởi dấu gạch ngang, theo sau bởi một hoặc nhiều chữ số (ví dụ: "ICL2-100", "PROJ-42", "ABC-1").

2.2 THE Ticket_ID_Extractor SHALL trích xuất ticket IDs từ các text fields sau của mỗi ticket trong Ticket_Graph: summary, description (plain text), và tất cả comment bodies.

2.3 THE Ticket_ID_Extractor SHALL trích xuất ticket IDs từ attachment text content (chunks đã được xử lý bởi AttachmentPipeline và lưu trong VectorStore).

2.4 THE Ticket_ID_Extractor SHALL loại bỏ ticket ID trùng với ticket đang được xử lý (self-reference) và các ticket IDs đã có trong Visited_Set.

2.5 IF Traversal_Config có project_scope được cấu hình (danh sách project keys cho phép), THEN THE Ticket_ID_Extractor SHALL chỉ giữ lại ticket IDs có project key nằm trong project_scope. IF project_scope rỗng hoặc không được cấu hình, THE Ticket_ID_Extractor SHALL chấp nhận tất cả project keys.

2.6 THE Ticket_ID_Extractor SHALL là pure function — nhận text input, trả về danh sách ticket IDs đã deduplicate và filter. Không thực hiện I/O hoặc side effects.

2.7 FOR ALL text inputs, parsing ticket IDs rồi format lại thành text chứa các IDs đó rồi parsing lại SHALL cho ra cùng tập ticket IDs (round-trip property cho parser).

### Requirement 3: Thu thập toàn bộ Comments — Không giới hạn

**User Story:** Là một BA/PM, tôi muốn TẤT CẢ comments của mỗi ticket liên quan được thu thập đầy đủ (không chỉ 20 comment cuối), để AI có đủ ngữ cảnh từ toàn bộ lịch sử thảo luận khi sinh tài liệu.

#### Acceptance Criteria

3.1 THE Comment_Collector SHALL thu thập TẤT CẢ comments của một ticket bằng cách sử dụng Jira REST API với pagination (parameter `startAt` và `maxResults`), lặp cho đến khi lấy hết toàn bộ comments.

3.2 WHEN Jira API trả về comment response với `total` lớn hơn số comments đã fetch, THE Comment_Collector SHALL tiếp tục gọi API với `startAt` tăng dần cho đến khi tổng số comments đã fetch bằng `total`.

3.3 THE Comment_Collector SHALL lưu trữ mỗi comment với đầy đủ thông tin: author (display name), created date, updated date (nếu có), và full body text (không truncate).

3.4 THE Comment_Collector SHALL sắp xếp comments theo thứ tự thời gian tăng dần (oldest first) để giữ nguyên flow thảo luận.

3.5 IF Jira API trả về lỗi trong quá trình pagination (ví dụ: timeout ở page 3 của 5), THEN THE Comment_Collector SHALL giữ lại các comments đã fetch thành công, log warning với số comments đã lấy được so với total, và tiếp tục xử lý mà không dừng toàn bộ quá trình.

3.6 THE Deep_Collector SHALL thu thập comments cho TẤT CẢ tickets trong Ticket_Graph (không chỉ Root_Ticket), đảm bảo thông tin thảo luận từ linked tickets cũng được đưa vào Enriched_Context.

3.7 THE Traversal_Config SHALL có tham số `max_comments_per_ticket` (integer, mặc định 200, tối thiểu 10, tối đa 1000). WHEN số comments của một ticket vượt quá `max_comments_per_ticket`, THE Comment_Collector SHALL chỉ lấy `max_comments_per_ticket` comments gần nhất và log warning "Ticket {ticketId} has {total} comments, capped at {max_comments_per_ticket}".

### Requirement 4: Thu thập toàn bộ Attachment Content — Không giới hạn top-K

**User Story:** Là một BA/PM, tôi muốn TẤT CẢ nội dung attachment đã được xử lý (không chỉ top-10 semantic search) được đưa vào prompt sinh tài liệu, để AI có đầy đủ thông tin từ tất cả tài liệu đính kèm.

#### Acceptance Criteria

4.1 THE Attachment_Content_Collector SHALL sử dụng method `VectorStore.findByTicketId(ticketId)` để lấy TẤT CẢ attachment chunks đã được xử lý cho một ticket, thay vì dùng semantic search `VectorStore.search()` với top-K limit.

4.2 THE Deep_Collector SHALL thu thập attachment chunks cho TẤT CẢ tickets trong Ticket_Graph (không chỉ Root_Ticket).

4.3 THE Attachment_Content_Collector SHALL nhóm các chunks theo filename và sắp xếp theo chunk_index tăng dần, để nội dung mỗi attachment được tái tạo theo đúng thứ tự.

4.4 WHEN một ticket trong Ticket_Graph không có attachment chunks nào trong VectorStore (chưa được xử lý bởi AttachmentPipeline), THE Attachment_Content_Collector SHALL bỏ qua ticket đó cho phần attachment và log info message.

4.5 THE Attachment_Content_Collector SHALL trả về danh sách attachment content đã deduplicate theo attachment ID — nếu cùng một attachment được reference từ nhiều tickets, nội dung chỉ xuất hiện một lần.

4.6 THE Attachment_Content_Collector SHALL trích xuất ticket IDs từ attachment text content và cung cấp cho Ticket_ID_Extractor để phát hiện thêm ticket references (cross-reference giữa Requirement 2 và Requirement 4).

### Requirement 5: Enriched Generation Context — Mở rộng GenerationContext

**User Story:** Là một developer, tôi muốn GenerationContext được mở rộng để chứa toàn bộ dữ liệu thu thập từ deep traversal (raw comments, full attachment content, ticket graph metadata), để prompt builder có đủ thông tin xây dựng prompt toàn diện.

#### Acceptance Criteria

5.1 THE Enriched_Context SHALL mở rộng `GenerationContext` hiện tại với các trường mới: `allTickets` (danh sách tất cả tickets trong Ticket_Graph với full data), `ticketRelationships` (danh sách edges trong Ticket_Graph: sourceId, targetId, relationship_type), `rawComments` (map từ ticketId đến danh sách comments đầy đủ), `allAttachmentChunks` (danh sách tất cả attachment chunks đã deduplicate), `traversalMetadata` (thống kê traversal: total_tickets, max_depth, tickets_skipped, traversal_time_ms).

5.2 THE Enriched_Context SHALL giữ backward compatibility với `GenerationContext` hiện tại — các trường `mainTicket`, `linkedTicketAnalyses`, `attachmentChunks`, `sprintMetadata` vẫn được populate như cũ để không break code hiện tại. Cụ thể: `GenerationContext` hiện tại là `data class` — để `EnrichedContext` kế thừa, `GenerationContext` SHALL được chuyển thành `open class` với manual `copy()` method và `equals()`/`hashCode()`/`toString()` implementations, đảm bảo code hiện tại sử dụng `copy()` không bị break.

5.3 WHEN Deep_Collector hoàn tất traversal, THE Deep_Collector SHALL tạo Enriched_Context với: `mainTicket` = KBRecord của Root_Ticket (nếu có trong KB), `allTickets` = danh sách StructuredTicketContent của tất cả tickets đã fetch từ Jira, `rawComments` = map comments đầy đủ cho mỗi ticket, `allAttachmentChunks` = tất cả attachment chunks.

5.4 THE Enriched_Context SHALL chứa thông tin raw từ Jira (summary, description, comments, status, priority, labels, components) cho mỗi ticket trong Ticket_Graph, KHÔNG chỉ dựa vào KBRecord (AI summary). Điều này đảm bảo prompt có cả raw data lẫn AI-analyzed data.

5.5 FOR ALL Enriched_Context objects, serializing rồi deserializing SHALL cho ra object tương đương (round-trip property cho serialization).

### Requirement 6: Prompt Assembly với quản lý kích thước

**User Story:** Là một developer, tôi muốn prompt builder mới có khả năng lắp ráp prompt từ Enriched_Context với chiến lược quản lý kích thước thông minh, để prompt không vượt quá context window của AI model mà vẫn giữ được thông tin quan trọng nhất.

#### Acceptance Criteria

6.1 THE Prompt_Assembler SHALL xây dựng prompt từ Enriched_Context theo thứ tự ưu tiên giảm dần: (1) Root_Ticket raw data (summary, description, full comments), (2) Root_Ticket KBRecord analysis (businessSummary, extractedRequirements, asIsState, toBeState), (3) Root_Ticket attachment content, (4) Directly-linked tickets (depth=1) raw data + analysis, (5) Depth-1 attachment content, (6) Deeper tickets (depth>=2) raw data + analysis, (7) Deeper attachment content.

6.2 THE Prompt_Assembler SHALL nhận tham số `maxPromptChars` (kích thước tối đa prompt tính bằng ký tự, mặc định 100,000 ký tự). WHILE tổng kích thước prompt chưa vượt `maxPromptChars`, THE Prompt_Assembler SHALL tiếp tục thêm nội dung theo thứ tự ưu tiên.

6.3 WHEN tổng kích thước nội dung vượt `maxPromptChars`, THE Prompt_Assembler SHALL áp dụng truncation strategy: cắt bớt nội dung từ mức ưu tiên thấp nhất trước (deeper tickets trước, attachment content trước raw ticket data). Prompt_Assembler SHALL thêm annotation "[TRUNCATED: {N} tickets và {M} attachment chunks bị cắt do giới hạn prompt size]" vào cuối phần context.

6.4 THE Prompt_Assembler SHALL đưa raw comment content vào prompt cho mỗi ticket (không chỉ AI summary), với format: "[Comment by {author} on {date}]: {full_body_text}". Comments được nhóm theo ticket ID.

6.5 THE Prompt_Assembler SHALL đưa attachment content vào prompt với format: "[Attachment: {filename} from {ticketId}]: {chunk_content}". Chunks của cùng một file được nối liền theo thứ tự chunk_index.

6.6 THE Prompt_Assembler SHALL đưa ticket relationship metadata vào prompt: "[Ticket Graph] Root: {rootId} → {N} related tickets across {maxDepth} levels. Relationships: {list of ticketA --relationship_type--> ticketB}".

6.7 THE Prompt_Assembler SHALL giữ nguyên các phần prompt hiện tại (role, template, instructions, output format từ `BrdPromptSections.kt`) và chỉ thay thế phần context data bằng Enriched_Context data.

6.8 FOR ALL Enriched_Context inputs có tổng content nhỏ hơn maxPromptChars, THE Prompt_Assembler SHALL include 100% nội dung mà không truncate bất kỳ phần nào (idempotence — không truncate khi không cần thiết).

6.9 THE Prompt_Assembler SHALL điều chỉnh priority order dựa trên document type: BRD ưu tiên business requirements, comments, acceptance criteria, stakeholder discussions; FSD ưu tiên technical details, API specs, DB changes, attachment content (design docs, wireframes). Priority order cụ thể cho mỗi document type được cấu hình trong Prompt_Assembler, không hardcode.

### Requirement 7: Traversal Configuration — Cấu hình giới hạn thực tế

**User Story:** Là một quản trị viên hệ thống, tôi muốn cấu hình được các giới hạn traversal (max depth, max tickets, project scope) để kiểm soát tài nguyên hệ thống và tránh traversal chạy quá lâu trên các project lớn.

#### Acceptance Criteria

7.1 THE Traversal_Config SHALL có các tham số cấu hình: `max_depth` (integer, mặc định 5, tối thiểu 1, tối đa 20), `max_tickets` (integer, mặc định 50, tối thiểu 1, tối đa 1000), `project_scope` (danh sách project keys cho phép traverse, mặc định rỗng = tất cả projects), `request_timeout_ms` (timeout cho mỗi Jira API call, mặc định 10000ms), `total_timeout_ms` (timeout tổng cho toàn bộ traversal, mặc định 120000ms).

7.2 THE Traversal_Config SHALL được lưu trữ trong application settings (cùng cơ chế với các settings hiện tại), cho phép admin thay đổi qua API hoặc settings page.

7.3 WHEN max_depth hoặc max_tickets được set giá trị ngoài phạm vi cho phép, THE Deep_Collector SHALL clamp giá trị về giới hạn gần nhất (ví dụ: max_depth = 25 → clamp thành 20) và log warning. _(Ranges mở rộng bởi map-reduce-analysis spec: maxDepth 1..20, maxTickets 1..1000)_

7.4 WHEN total_timeout_ms bị vượt trong quá trình traversal, THE Traversal_Engine SHALL dừng traversal ngay lập tức, giữ lại tất cả tickets đã fetch thành công, và log warning "Traversal timeout after {elapsed}ms, collected {N}/{total} tickets".

7.5 THE Deep_Collector SHALL log Traversal_Config đang được sử dụng ở đầu mỗi lần traversal để hỗ trợ debugging.

7.6 THE Traversal_Config SHALL có tham số `max_prompt_chars` (integer, mặc định 100,000). THE Deep_Collector SHALL implement early termination: WHEN tổng kích thước collected data (comments + attachments + ticket metadata) vượt 3× `max_prompt_chars`, THE Deep_Collector SHALL dừng thu thập thêm data cho tickets ở depth cao hơn và log "Early termination: collected data ({size} chars) exceeds 3× prompt capacity ({limit} chars)". Điều này tránh wasted API calls khi phần lớn data sẽ bị truncate.

### Requirement 8: Tích hợp Deep Collector vào Document Generation Pipeline

**User Story:** Là một developer, tôi muốn Deep_Collector được tích hợp vào pipeline sinh tài liệu hiện tại (JobExecutor) một cách seamless, thay thế DocumentAggregatorImpl mà không break các flow khác.

#### Acceptance Criteria

8.1 THE Deep_Collector SHALL implement interface `DocumentAggregator` hiện tại, đảm bảo `JobExecutor` có thể sử dụng Deep_Collector thay cho `DocumentAggregatorImpl` mà không cần thay đổi code trong `JobExecutor`.

8.2 WHEN Deep_Collector được inject vào JobExecutor, THE JobExecutor SHALL sử dụng Enriched_Context (là subtype của GenerationContext) cho toàn bộ pipeline: aggregate → prompt → AI → parse → save.

8.3 THE Deep_Collector SHALL cập nhật job progress trong quá trình traversal: 0-5% = initializing, 5-25% = traversing ticket graph (cập nhật theo số tickets đã fetch / max_tickets), 25-30% = collecting comments và attachments. Progress được cập nhật qua callback interface `(progressPercent: Int, phase: String) -> Unit` do JobExecutor truyền vào, đảm bảo Deep_Collector progress (0-30%) map vào phase AGGREGATING_DATA của Generation_Job trong document-job-manager.

8.4 WHEN Deep_Collector hoàn tất, THE JobExecutor SHALL lưu danh sách tất cả source ticket IDs (từ Ticket_Graph) vào trường `sourceTicketIds` của `GeneratedDocument`, thay vì chỉ lưu mainTicket + linkedTicketAnalyses như hiện tại.

8.5 THE Deep_Collector SHALL sử dụng cùng JiraClient instance đã được configure trong application (authentication, base URL), không tạo client riêng.

8.6 IF Deep_Collector gặp lỗi nghiêm trọng (không thể fetch Root_Ticket, Jira API hoàn toàn không khả dụng), THEN THE Deep_Collector SHALL throw exception với message mô tả rõ ràng để JobExecutor xử lý retry hoặc fail job.

### Requirement 9: Xử lý Jira API Pagination cho Comments

**User Story:** Là một developer, tôi muốn hệ thống xử lý đúng Jira API pagination khi fetch comments, để đảm bảo lấy được TẤT CẢ comments ngay cả khi ticket có hàng trăm comments.

#### Acceptance Criteria

9.1 THE JiraClient interface SHALL được mở rộng với method mới: `getIssueComments(issueKey: String, startAt: Int, maxResults: Int): JiraCommentResponse` trả về comments với pagination metadata (startAt, maxResults, total).

9.2 THE Comment_Collector SHALL gọi `getIssueComments()` lặp đi lặp lại với `startAt` tăng dần (0, 50, 100, ...) cho đến khi `startAt >= total`, sử dụng `maxResults = 50` cho mỗi page.

9.3 WHEN một page request thất bại (timeout, 5xx error), THE Comment_Collector SHALL retry tối đa 2 lần với exponential backoff (1s, 2s). Nếu vẫn thất bại sau 2 retries, bỏ qua page đó và tiếp tục page tiếp theo.

9.4 THE Comment_Collector SHALL xử lý trường hợp Jira API trả về `total` thay đổi giữa các page requests (do comments mới được thêm trong quá trình fetch) bằng cách dùng `startAt + maxResults` thay vì dựa vào `total` cố định.

9.5 FOR ALL comment collections, tổng số comments thu thập được SHALL nhỏ hơn hoặc bằng `total` được báo cáo bởi Jira API (metamorphic property — không thể thu thập nhiều hơn số tồn tại).

### Requirement 10: Concurrent Fetching — Tối ưu hiệu suất traversal

**User Story:** Là một BA/PM, tôi muốn quá trình thu thập dữ liệu deep traversal hoàn thành trong thời gian hợp lý (không quá 2 phút cho 50 tickets), để không phải chờ quá lâu khi sinh tài liệu.

#### Acceptance Criteria

10.1 THE Traversal_Engine SHALL fetch nhiều tickets đồng thời (concurrent) trong cùng một BFS level, sử dụng Kotlin coroutines với giới hạn concurrency tối đa 5 requests đồng thời để tránh overwhelm Jira API.

10.2 THE Traversal_Engine SHALL sử dụng semaphore hoặc channel để giới hạn số lượng concurrent Jira API calls, đảm bảo không vượt quá concurrency limit ngay cả khi một BFS level có nhiều tickets.

10.3 WHEN một concurrent request thất bại, THE Traversal_Engine SHALL xử lý lỗi độc lập cho request đó (log warning, skip ticket) mà không ảnh hưởng đến các requests đang chạy song song.

10.4 THE Deep_Collector SHALL log thời gian thực hiện cho mỗi phase: traversal time, comment collection time, attachment collection time, total time. Điều này hỗ trợ performance monitoring và tuning.

10.5 WHEN Jira API trả về HTTP 429 (Rate Limit), THE Traversal_Engine SHALL đọc header `Retry-After` (nếu có) và chờ đúng thời gian đó trước khi retry. Nếu không có header, chờ 5 giây. Tối đa 3 retries cho rate limit.

10.6 THE Deep_Collector SHALL sử dụng 2 semaphores riêng biệt: `jiraApiSemaphore` (cho Jira API calls — traversal + comment pagination) và `aiAnalysisSemaphore` (cho AI inference calls — shared với BatchScanEngine). Điều này đảm bảo Jira API calls và AI calls không block lẫn nhau.

10.7 THE Deep_Collector SHALL implement collection-level lock: WHEN 2 requests cùng trigger deep collection cho cùng root ticket đồng thời (ví dụ: 2 users cùng generate BRD), THE Deep_Collector SHALL cho phép request đầu tiên chạy và request thứ hai chờ kết quả từ request đầu tiên (coalesce pattern), tránh duplicate traversal. Lock dựa trên root ticket ID, timeout sau total_timeout_ms.

### Requirement 11: Logging và Observability cho Deep Collection

**User Story:** Là một developer/admin, tôi muốn quá trình deep collection có logging chi tiết và metrics, để tôi có thể debug khi có vấn đề và monitor hiệu suất hệ thống.

#### Acceptance Criteria

11.1 THE Deep_Collector SHALL log ở level INFO khi bắt đầu traversal: "Deep collection started for {rootTicketId} with config: max_depth={N}, max_tickets={M}".

11.2 THE Deep_Collector SHALL log ở level DEBUG cho mỗi ticket được fetch: "Fetched ticket {ticketId} at depth {D}, discovered {N} new related tickets".

11.3 THE Deep_Collector SHALL log ở level INFO khi traversal hoàn tất: "Deep collection completed for {rootTicketId}: {totalTickets} tickets, {totalComments} comments, {totalAttachmentChunks} attachment chunks, max_depth_reached={D}, elapsed={T}ms".

11.4 THE Deep_Collector SHALL log ở level WARN khi: ticket fetch thất bại, traversal bị timeout, max_tickets limit đạt được, comment pagination bị lỗi giữa chừng.

11.5 THE Deep_Collector SHALL cập nhật ScanLogRepository với các entries cho quá trình deep collection, cho phép user xem tiến trình trên frontend (tương tự scan log hiện tại).

11.6 WHEN traversal bị giới hạn bởi max_depth hoặc max_tickets, THE Deep_Collector SHALL log danh sách ticket IDs đã phát hiện nhưng không được fetch (do limit), để admin biết có thể cần tăng limit.

### Requirement 12: Backward Compatibility và Migration

**User Story:** Là một developer, tôi muốn Deep_Collector có thể được bật/tắt qua configuration, để có thể rollback về behavior cũ nếu phát hiện vấn đề, và đảm bảo không break các flow hiện tại.

#### Acceptance Criteria

12.1 THE Backend_Server SHALL cung cấp setting `deep_collection_enabled` (boolean, mặc định true). WHEN setting là false, THE Backend_Server SHALL sử dụng `DocumentAggregatorImpl` hiện tại thay vì Deep_Collector.

12.2 WHEN `deep_collection_enabled` là true, THE Deep_Collector SHALL được inject vào JobExecutor thay cho DocumentAggregatorImpl. Việc chuyển đổi không yêu cầu restart server — chỉ cần thay đổi setting.

12.3 THE Deep_Collector SHALL populate trường `linkedTicketAnalyses` trong Enriched_Context từ KBRecords của các tickets đã có trong KB (giống behavior hiện tại của DocumentAggregatorImpl), đảm bảo BrdPromptSections.kt hiện tại vẫn hoạt động đúng với Enriched_Context.

12.4 THE Prompt_Assembler SHALL chỉ được sử dụng khi `deep_collection_enabled` là true. Khi false, BrdPromptBuilder/FsdPromptBuilder hiện tại được sử dụng với GenerationContext từ DocumentAggregatorImpl.

12.5 THE Deep_Collector SHALL không yêu cầu tickets phải có KBRecord (đã được analyze) để thu thập dữ liệu. Deep_Collector fetch raw data trực tiếp từ Jira API, KBRecord chỉ là bonus data nếu có.

### Requirement 13: Background Job Processing cho Linked Tickets và Attachments

**User Story:** Là một BA/PM, tôi muốn quá trình analyze ticket kết thúc nhanh — việc xử lý linked tickets (deep analysis) và attachments (download + convert + embed) được tách thành background jobs riêng biệt chạy sau, để tôi không phải chờ đợi lâu và có thể monitor tiến trình xử lý từng item.

#### Acceptance Criteria

13.1 WHEN Deep_Collector hoàn tất BFS traversal (Phase 1) và thu thập raw data cho Root_Ticket, THE Deep_Collector SHALL trả về Enriched_Context ngay lập tức với dữ liệu đã có (root ticket + trực tiếp available data), ĐỒNG THỜI tạo Collection_Jobs cho các công việc nặng chạy ở background: (a) deep analysis cho linked tickets chưa có trong KB, (b) attachment processing cho tickets có attachments chưa được xử lý.

13.2 THE Collection_Job_Manager SHALL tạo và quản lý 2 loại Collection_Jobs:
- **LINKED_TICKET_ANALYSIS**: Phân tích AI cho mỗi linked ticket chưa có KBRecord trong KB. Mỗi ticket là một job item với progress riêng.
- **ATTACHMENT_PROCESSING**: Download, convert (markitdown), chunk, embed cho mỗi attachment chưa có trong VectorStore. Mỗi attachment là một job item với progress riêng.

13.3 THE Collection_Job_Manager SHALL lưu trữ Collection_Jobs trong database với các trường: job_id (UUID), parent_ticket_id (Root_Ticket ID), job_type (LINKED_TICKET_ANALYSIS hoặc ATTACHMENT_PROCESSING), total_items (tổng số tickets/attachments cần xử lý), completed_items, failed_items, status (QUEUED, RUNNING, COMPLETED, FAILED), created_at, updated_at.

13.4 THE Collection_Job_Manager SHALL cập nhật progress (completed_items, failed_items) sau mỗi item được xử lý xong, cho phép frontend tính % hoàn thành: `progress_percent = (completed_items + failed_items) * 100 / total_items`.

13.5 THE Backend_Server SHALL cung cấp endpoint `GET /api/collection-jobs?ticketId={ticketId}` trả về danh sách Collection_Jobs cho một ticket, bao gồm: job_id, job_type, status, total_items, completed_items, failed_items, progress_percent, và danh sách items với trạng thái từng item (PENDING, PROCESSING, COMPLETED, FAILED, SKIPPED).

13.6 THE Backend_Server SHALL cung cấp endpoint `GET /api/collection-jobs/active` trả về tất cả Collection_Jobs đang RUNNING hoặc QUEUED, cho phép frontend hiển thị global indicator tương tự document generation jobs.

13.7 THE Frontend_App SHALL hiển thị danh sách Collection_Jobs đang chạy cho ticket hiện tại trên trang Ticket Intelligence, bao gồm:
- Loại job (Linked Ticket Analysis / Attachment Processing)
- Progress bar với % hoàn thành
- Danh sách items: mỗi item hiển thị ticket ID hoặc filename, trạng thái (⏳ Pending, 🔄 Processing, ✅ Completed, ❌ Failed, ⏭️ Skipped)
- Tổng kết: "{completed}/{total} items, {failed} failed"

13.8 THE Frontend_App SHALL poll `GET /api/collection-jobs?ticketId={ticketId}` mỗi 5 giây khi có Collection_Jobs đang active, cập nhật progress bars và item statuses. Khi tất cả jobs hoàn tất, polling dừng.

13.9 WHEN tất cả Collection_Jobs cho một ticket hoàn tất (cả linked ticket analysis lẫn attachment processing), THE system SHALL tự động cập nhật Enriched_Context với dữ liệu mới (KBRecords mới từ linked ticket analysis, attachment chunks mới từ attachment processing). Nếu user yêu cầu re-generate BRD/FSD sau đó, prompt sẽ có đầy đủ dữ liệu.

13.10 THE Collection_Job_Manager SHALL xử lý từng item độc lập — nếu một linked ticket analysis thất bại, các tickets còn lại vẫn tiếp tục. Nếu một attachment processing thất bại, các attachments còn lại vẫn tiếp tục. Job chỉ chuyển sang FAILED khi TẤT CẢ items đều thất bại.

13.11 THE Collection_Job_Manager SHALL sử dụng cùng AI semaphore và concurrency limits với BatchScanEngine (Req 10.1, 10.2), đảm bảo không overwhelm Jira API hoặc AI provider khi có nhiều Collection_Jobs chạy đồng thời.

13.12 WHEN user navigate đến trang Ticket Intelligence và chọn ticket đang có Collection_Jobs active, THE Frontend_App SHALL hiển thị panel monitoring ngay lập tức, cho phép user theo dõi tiến trình mà không cần trigger lại.

13.13 THE Collection_Job_Manager SHALL tích hợp với ScanLogRepository — mỗi item được xử lý sẽ tạo scan log entry tương tự batch scan (ANALYZING → COMPLETED/FAILED), cho phép user xem chi tiết trong scan log.

### Requirement 14: Conflict Resolution — Collection Jobs vs Manual Ticket Intelligence Analysis

**User Story:** Là một BA/PM, tôi muốn hệ thống xử lý đúng khi tôi analyze một ticket trên Ticket Intelligence mà ticket đó đang được xử lý bởi Collection_Job background (hoặc ngược lại), để tránh duplicate work, race condition ghi KB, và trải nghiệm sử dụng nhất quán.

#### Acceptance Criteria

14.1 WHEN user trigger "ANALYZE" hoặc "RE-ANALYZE" trên Ticket Intelligence cho một ticket đang là item trong Collection_Job LINKED_TICKET_ANALYSIS (status PENDING hoặc PROCESSING), THE Backend_Server SHALL skip item đó trong Collection_Job (đánh dấu SKIPPED) và để manual analysis từ Ticket Intelligence xử lý. Manual analysis có priority cao hơn background job vì user chủ động yêu cầu.

14.2 WHEN Collection_Job LINKED_TICKET_ANALYSIS chuẩn bị xử lý một ticket item, THE Collection_Job_Manager SHALL kiểm tra KB trước (KB-First). IF ticket đã có KBRecord hợp lệ (không phải error record) trong KB — dù do manual analysis vừa tạo hay batch scan trước đó — THEN THE Collection_Job_Manager SHALL skip item đó (đánh dấu SKIPPED với reason "already in KB") và chuyển sang item tiếp theo.

14.3 WHEN user trigger "ANALYZE" trên Ticket Intelligence cho một ticket đang có Collection_Job ATTACHMENT_PROCESSING active, THE Backend_Server SHALL cho phép manual analysis chạy song song — attachment processing và ticket analysis là 2 công việc độc lập không conflict. Frontend SHALL hiển thị cả analysis progress lẫn attachment processing progress đồng thời.

14.4 WHEN Collection_Job LINKED_TICKET_ANALYSIS đang PROCESSING một ticket (AI đang analyze), VÀ user trigger manual "ANALYZE" cho cùng ticket đó trên Ticket Intelligence, THE Backend_Server SHALL trả về HTTP 409 Conflict với message "Ticket đang được phân tích bởi background job, vui lòng chờ hoàn tất" kèm job_id. Frontend SHALL hiển thị thông báo và link đến monitoring panel của Collection_Job.

14.5 WHEN Cascading Analysis (Req 26 trong spec ticket-intelligence) chạy cho một ticket và phát hiện linked tickets cần analyze, THE Cascading_Analysis_Engine SHALL áp dụng KB-First strategy — nếu Collection_Job đã analyze xong ticket → KB có record → Cascading skip tự nhiên. Không cần synchronization phức tạp giữa 2 async processes.

14.6 WHEN BatchScanEngine đang scan project và gặp ticket đang là item trong Collection_Job, THE BatchScanEngine SHALL áp dụng KB-First strategy bình thường — nếu ticket đã có KBRecord (do Collection_Job vừa analyze xong), skip; nếu chưa có, analyze bình thường. Không cần coordination đặc biệt vì KB-First đã xử lý dedup tự nhiên.

14.7 THE Collection_Job_Manager SHALL sử dụng database-level locking (optimistic locking via updated_at timestamp hoặc version field) khi cập nhật item status, đảm bảo không có race condition khi nhiều processes cùng cập nhật trạng thái của cùng một item.

14.8 THE Frontend_App trên trang Ticket Intelligence SHALL kiểm tra Collection_Jobs active cho ticket hiện tại khi load trang. IF có Collection_Job LINKED_TICKET_ANALYSIS đang chạy chứa ticket hiện tại, THE Frontend_App SHALL hiển thị banner thông báo "Ticket này đang được phân tích bởi background job từ {parent_ticket_id}" với link đến monitoring panel, VÀ disable nút "ANALYZE" (chỉ disable khi ticket đang ở trạng thái PROCESSING trong job, không disable khi PENDING — vì PENDING items có thể bị preempt bởi manual analysis theo 14.1).

14.9 WHEN Collection_Job hoàn tất xử lý một linked ticket (COMPLETED), VÀ user đang xem ticket đó trên Ticket Intelligence, THE Frontend_App SHALL tự động refresh kết quả analysis từ KB (qua polling đã có ở 13.8) để hiển thị dữ liệu mới nhất mà không cần user bấm refresh.

### Requirement 15: Traversal Cache — Tránh lặp lại traversal khi re-generate

**User Story:** Là một BA/PM, tôi muốn khi re-generate BRD/FSD cho cùng ticket trong thời gian ngắn, hệ thống reuse kết quả traversal trước đó thay vì chạy lại từ đầu, để tiết kiệm thời gian và API calls.

#### Acceptance Criteria

15.1 THE Deep_Collector SHALL cache TicketGraph vào database sau mỗi lần traversal thành công, kèm theo timestamp `cached_at` và root ticket ID.

15.2 THE Traversal_Config SHALL có tham số `cache_ttl_minutes` (integer, mặc định 60, tối thiểu 5, tối đa 1440). WHEN user trigger deep collection cho một root ticket đã có cached TicketGraph với `cached_at` trong vòng `cache_ttl_minutes`, THE Deep_Collector SHALL reuse cached graph và chỉ re-fetch comments và attachment chunks (vì comments/attachments có thể thay đổi thường xuyên hơn ticket relationships).

15.3 WHEN user trigger "RE-ANALYZE" (force re-analyze) trên Ticket Intelligence, THE Deep_Collector SHALL invalidate cache cho root ticket đó và chạy traversal mới từ đầu.

15.4 WHEN bất kỳ ticket nào trong cached TicketGraph có `updated_at` (từ Jira) mới hơn `cached_at`, THE Deep_Collector SHALL invalidate cache và chạy traversal mới. Kiểm tra `updated_at` chỉ cần gọi Jira API cho root ticket (lightweight check) — nếu root ticket chưa thay đổi, giả định graph vẫn valid.

15.5 THE Deep_Collector SHALL log khi sử dụng cache: "Reusing cached TicketGraph for {rootTicketId} (cached {minutes}m ago, {nodeCount} nodes)" ở level INFO.

### Requirement 16: Security và Rate Limiting cho Deep Collection

**User Story:** Là một quản trị viên hệ thống, tôi muốn deep collection có các biện pháp bảo mật và rate limiting, để tránh abuse Jira API và đảm bảo user chỉ truy cập dữ liệu họ có quyền xem.

#### Acceptance Criteria

16.1 THE Backend_Server SHALL giới hạn số lần deep collection mỗi user: tối đa 10 deep collections per user per hour. WHEN user vượt quá limit, THE Backend_Server SHALL trả về HTTP 429 với message "Đã vượt giới hạn deep collection ({limit}/hour). Vui lòng thử lại sau {remaining_seconds} giây."

16.2 THE Deep_Collector SHALL validate `project_scope` trong Traversal_Config against danh sách projects mà user hiện tại có quyền truy cập (từ Jira API hoặc cached project list). Tickets thuộc projects user không có quyền SHALL bị filter ra trước khi bắt đầu traversal, không chỉ dựa vào Jira API 403 response.

16.3 WHEN Traversal_Engine gặp 403 Forbidden cho một ticket, THE Traversal_Engine SHALL KHÔNG expose ticket ID ra frontend API response hoặc scan log visible cho user. Chỉ log ticket ID ở server-side (level WARN) và hiển thị count trên frontend: "⚠️ {N} tickets bị bỏ qua do không có quyền truy cập".
