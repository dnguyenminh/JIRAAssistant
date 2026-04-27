# BRD Insufficient Data Fix — Requirements

## Introduction

Hiện tại, khi sinh BRD cho ticket phức tạp (ví dụ: ICL2-15 với 36 linked tickets, nhiều comments và attachments), hệ thống tạo ra BRD với 4/7 sections hiển thị "⚠️ Insufficient data" mặc dù `EnrichedContext` chứa đầy đủ dữ liệu. Nguyên nhân gốc rễ nằm ở nhiều tầng:

1. **Data gap trong linkedTicketAnalyses (BUG)**: Khi RE-ANALYZE ticket gốc, hệ thống chỉ analyze root ticket qua AI. Linked tickets được discover qua BFS traversal nhưng KHÔNG được analyze — chúng chỉ có raw Jira data (summary, description). Cascade analysis chạy **async** sau khi root analysis xong, nên khi user click RE-GENERATE BRD ngay sau RE-ANALYZE, linked tickets chưa có KBRecord → `linkedTicketAnalyses` rỗng → AI không có ngữ cảnh linked ticket. Đây là bug vì tất cả linked tickets phải được analyze giống ticket gốc.

2. **Prompt budget truncation quá mạnh**: `PromptAssembler` có budget 100K ký tự. Với 36 tickets + comments + attachments, nội dung có thể vượt budget → truncation cắt bỏ sections quan trọng → AI thiếu dữ liệu cho nhiều BRD sections.

3. **Data utilization không đầy đủ**: `PromptSectionBuilder.buildRootRaw()` chỉ include summary + description + comments cho root ticket. Không include: `technicalDetails` (API specs, DB changes), `diagrams`, `evolution/changelog`, `affectedModules` từ KBRecord.

4. **Comments không có trong basic prompt**: `BrdPromptSections.appendLinkedTicketsData()` chỉ include summary + requirements + dependencies từ KBRecords. Comments từ linked tickets KHÔNG được include.

5. **Data mapping instructions không đủ chi tiết**: `BrdPromptMappingInstructions` chỉ map 4 sections (Project Overview, Existing Processes, Project Requirements, Appendix). Thiếu mapping cho Revision History, Acronyms, Sign Off → AI không biết dùng dữ liệu nào cho các sections này.

Feature này sửa tất cả các vấn đề trên để đảm bảo BRD có đầy đủ 7 sections với nội dung thực tế từ dữ liệu đã thu thập.

## Glossary

- **BRD_Prompt_Builder**: Component `BrdPromptBuilder` trong shared module, xây dựng AI prompt từ `GenerationContext` cho BRD generation
- **BRD_Prompt_Sections**: Component `BrdPromptSections.kt` chứa các StringBuilder extension functions xây dựng từng phần của BRD prompt (role, context data, template, instructions)
- **Prompt_Assembler**: Component `PromptAssembler` trong server module, lắp ráp prompt từ `EnrichedContext` với priority-based truncation khi deep collection enabled
- **Prompt_Section_Builder**: Component `PromptSectionBuilder` trong server module, xây dựng các text sections từ `EnrichedContext` data (root raw, root KB, tickets raw, attachments, graph metadata)
- **Enriched_Context**: Phiên bản mở rộng của `GenerationContext`, chứa `allTickets`, `rawComments`, `allAttachmentChunks`, `ticketRelationships`, `ticketDepthMap` từ deep traversal
- **Generation_Context**: Data class cơ bản chứa `mainTicket` (KBRecord), `linkedTicketAnalyses` (List<KBRecord>), `attachmentChunks`, `sprintMetadata`
- **KBRecord**: Model Knowledge Base chứa kết quả deep analysis của một ticket (businessSummary, technicalDetails, dependencies, acceptanceCriteria, diagrams, asIsState, toBeState)
- **Basic_Prompt_Path**: Luồng prompt khi deep collection KHÔNG enabled — `BrdPromptBuilder.buildPrompt()` sử dụng `GenerationContext` fields trực tiếp
- **Enriched_Prompt_Path**: Luồng prompt khi deep collection enabled — `PromptAssembler.buildPrompt()` sử dụng `EnrichedContext` với priority-based truncation
- **Data_Mapping_Instructions**: Phần prompt chỉ dẫn AI map dữ liệu context vào từng BRD section cụ thể
- **Prompt_Budget**: Giới hạn kích thước prompt tính bằng ký tự (hiện tại 100,000 chars cho PromptAssembler)

## Requirements

### Requirement 1: Mở rộng dữ liệu KBRecord trong Basic Prompt Path

**User Story:** Là một BA/PM, tôi muốn BRD prompt sử dụng TẤT CẢ dữ liệu có sẵn trong KBRecord của ticket chính (không chỉ businessSummary, asIsState, toBeState), để AI có đủ ngữ cảnh sinh nội dung cho mọi BRD section.

#### Acceptance Criteria

1.1 WHEN BRD prompt được xây dựng qua Basic_Prompt_Path, THE BRD_Prompt_Sections SHALL include các trường sau từ KBRecord của ticket chính vào phần context data: businessSummary, asIsState, toBeState, extractedRequirements, acceptanceCriteria, dependencies, technicalDetails (bao gồm apiSpecifications, databaseChanges, externalIntegrations), diagrams, requirementSummary, và affectedModules.

1.2 WHEN KBRecord của ticket chính chứa technicalDetails với apiSpecifications không rỗng, THE BRD_Prompt_Sections SHALL serialize apiSpecifications vào context data với format "API Specifications: {danh sách endpoints với method, path, description}".

1.3 WHEN KBRecord của ticket chính chứa technicalDetails với databaseChanges không rỗng, THE BRD_Prompt_Sections SHALL serialize databaseChanges vào context data với format "Database Changes: {danh sách changes}".

1.4 WHEN KBRecord của ticket chính chứa technicalDetails với externalIntegrations không rỗng, THE BRD_Prompt_Sections SHALL serialize externalIntegrations vào context data với format "External Integrations: {danh sách integrations}".

1.5 WHEN KBRecord của ticket chính chứa diagrams không rỗng, THE BRD_Prompt_Sections SHALL serialize diagrams vào context data với format "Diagrams: {danh sách diagram types và descriptions}".

### Requirement 2: Mở rộng dữ liệu Linked Tickets trong Basic Prompt Path

**User Story:** Là một BA/PM, tôi muốn BRD prompt include đầy đủ thông tin từ linked tickets (bao gồm comments, technicalDetails, acceptanceCriteria), để AI có ngữ cảnh toàn diện từ tất cả tickets liên quan khi sinh BRD.

#### Acceptance Criteria

2.1 WHEN BRD prompt được xây dựng qua Basic_Prompt_Path và linkedTicketAnalyses không rỗng, THE BRD_Prompt_Sections SHALL include các trường sau cho mỗi linked ticket KBRecord: businessSummary, extractedRequirements, dependencies, acceptanceCriteria, technicalDetails (apiSpecifications, databaseChanges, externalIntegrations), asIsState, và toBeState.

2.2 WHEN linkedTicketAnalyses rỗng nhưng Generation_Context là Enriched_Context với allTickets không rỗng, THE BRD_Prompt_Sections SHALL fallback sang sử dụng raw ticket data từ allTickets (summary, description) để cung cấp ngữ cảnh linked ticket cho AI.

2.3 WHEN Enriched_Context chứa rawComments cho linked tickets, THE BRD_Prompt_Sections SHALL include comments từ linked tickets vào context data với format "[Comment by {author} on {date} for {ticketId}]: {body}".

### Requirement 3: Cải thiện Data Mapping Instructions cho tất cả 7 BRD Sections

**User Story:** Là một BA/PM, tôi muốn AI prompt có hướng dẫn mapping dữ liệu rõ ràng cho TẤT CẢ 7 BRD sections (không chỉ 4 sections hiện tại), để AI biết chính xác dùng dữ liệu nào cho từng section và không để section nào trống.

#### Acceptance Criteria

3.1 THE Data_Mapping_Instructions SHALL cung cấp hướng dẫn mapping cho section "Revision History": sử dụng ticketId, generated timestamp, danh sách source ticket IDs, và sprint metadata (nếu có) để tạo bảng revision history.

3.2 THE Data_Mapping_Instructions SHALL cung cấp hướng dẫn mapping cho section "Common Project Acronyms, Names, and Descriptions": trích xuất thuật ngữ kỹ thuật, viết tắt, và tên hệ thống từ businessSummary, technicalDetails, extractedRequirements, và attachment content để tạo bảng glossary.

3.3 THE Data_Mapping_Instructions SHALL cung cấp hướng dẫn mapping cho section "Sign Off": sử dụng dependencies (stakeholders), linked ticket assignees, và sprint metadata để tạo bảng sign-off với roles và responsibilities.

3.4 THE Data_Mapping_Instructions SHALL mở rộng hướng dẫn mapping cho section "Project Overview": thêm mapping từ toBeState cho "In Scope (Deliverables)", từ dependencies.externalDependencies và technicalDetails cho "Out of Scope", từ linked ticket assignees cho "Project Contributors".

3.5 THE Data_Mapping_Instructions SHALL mở rộng hướng dẫn mapping cho section "Existing Processes": thêm mapping từ rawComments (thảo luận về current pain points) và attachment content (screenshots, process docs) cho sub-sections Timing, Volume, Screenshots, Problems.

3.6 THE Data_Mapping_Instructions SHALL mở rộng hướng dẫn mapping cho section "Project Requirements": thêm mapping từ technicalDetails.apiSpecifications cho Functional Requirements, từ technicalDetails.databaseChanges cho Data Requirements, từ linked ticket acceptanceCriteria cho cross-ticket requirements.

3.7 THE Data_Mapping_Instructions SHALL mở rộng hướng dẫn mapping cho section "Appendix": thêm mapping từ diagrams cho Mock-ups, từ technicalDetails cho Business Rules and Procedures, từ tất cả source ticket IDs cho Document References.

### Requirement 4: Cải thiện PromptSectionBuilder để include đầy đủ KBRecord fields

**User Story:** Là một developer, tôi muốn PromptSectionBuilder (Enriched_Prompt_Path) include tất cả KBRecord fields quan trọng khi xây dựng root KB section, để PromptAssembler có đầy đủ dữ liệu cho AI.

#### Acceptance Criteria

4.1 WHEN PromptSectionBuilder xây dựng root KB analysis section, THE Prompt_Section_Builder SHALL include các trường sau từ KBRecord: businessSummary, asIsState, toBeState, extractedRequirements (danh sách đầy đủ), acceptanceCriteria (với testabilityAssessment), dependencies (blocking, related, external), technicalDetails (apiSpecifications, databaseChanges, externalIntegrations), diagrams, và affectedModules.

4.2 WHEN PromptSectionBuilder xây dựng root raw data section, THE Prompt_Section_Builder SHALL include ngoài summary và description: status, priority, labels, components, và fix versions từ StructuredTicketContent (nếu có trong allTickets).

4.3 WHEN PromptSectionBuilder xây dựng depth-1 tickets raw data, THE Prompt_Section_Builder SHALL include cho mỗi ticket: summary, description, status, priority, comments (từ rawComments), và relationship type với root ticket.

### Requirement 5: Tối ưu Prompt Budget và Truncation Strategy

**User Story:** Là một developer, tôi muốn PromptAssembler quản lý prompt budget hiệu quả hơn, đảm bảo dữ liệu quan trọng nhất luôn được giữ lại và truncation không cắt bỏ thông tin cần thiết cho BRD sections.

#### Acceptance Criteria

5.1 THE Prompt_Assembler SHALL tăng default prompt budget từ 100,000 lên 200,000 ký tự khi AI model hỗ trợ context window lớn (Gemini, GPT-4), giữ 100,000 cho models có context window nhỏ (Ollama local models).

5.2 WHEN truncation cần áp dụng, THE Prompt_Assembler SHALL ưu tiên giữ lại: (1) root ticket raw data + KB analysis 100%, (2) root ticket comments 100%, (3) root ticket attachments 100%, (4) depth-1 tickets raw data (truncate descriptions nếu cần, giữ summaries), (5) depth-1 comments (giữ 5 comments gần nhất mỗi ticket), (6) deeper tickets (chỉ giữ summaries).

5.3 WHEN truncation xảy ra, THE Prompt_Assembler SHALL thêm annotation chi tiết: "[TRUNCATED: Giữ lại {N1} tickets đầy đủ, {N2} tickets chỉ summary, cắt {N3} tickets và {M} attachment chunks. Tổng data gốc: {originalSize} chars, budget: {budget} chars]".

5.4 THE Prompt_Assembler SHALL KHÔNG truncate phần Data_Mapping_Instructions, anti-hallucination rules, và BRD template — các phần này luôn được giữ nguyên bất kể budget.

### Requirement 6: Cải thiện Section Completion Instructions trong Prompt

**User Story:** Là một BA/PM, tôi muốn AI prompt có hướng dẫn mạnh mẽ hơn để AI tận dụng tối đa dữ liệu có sẵn cho mỗi section, giảm thiểu "Insufficient data" khi dữ liệu thực sự tồn tại trong context.

#### Acceptance Criteria

6.1 THE BRD_Prompt_Sections SHALL thêm instruction yêu cầu AI: "Trước khi đánh dấu một section là 'Insufficient data', hãy kiểm tra TẤT CẢ nguồn dữ liệu trong CONTEXT: main ticket analysis, linked ticket data, comments, attachment content, và technical details. Chỉ đánh dấu 'Insufficient data' khi KHÔNG CÓ bất kỳ dữ liệu nào liên quan trong toàn bộ CONTEXT."

6.2 THE BRD_Prompt_Sections SHALL thêm instruction cho mỗi section có khả năng thiếu dữ liệu: "Nếu dữ liệu trực tiếp không có, hãy suy luận từ dữ liệu gián tiếp. Ví dụ: Revision History có thể được tạo từ ticket metadata; Acronyms có thể được trích xuất từ technical terms trong requirements; Sign Off có thể được suy luận từ stakeholders trong dependencies."

6.3 THE BRD_Prompt_Sections SHALL thêm instruction: "Mỗi section PHẢI có ít nhất 3 dòng nội dung thực tế. Nếu dữ liệu hạn chế, hãy phân tích và mở rộng từ dữ liệu có sẵn, đánh dấu phần suy luận bằng [INFERRED] tag."

6.4 THE BRD_Prompt_Sections SHALL thêm instruction: "Sử dụng comments từ linked tickets như nguồn dữ liệu bổ sung — comments thường chứa thảo luận về requirements, pain points, technical decisions, và stakeholder feedback mà AI summary có thể bỏ sót."

### Requirement 7: Fallback Data Strategy khi linkedTicketAnalyses rỗng

**User Story:** Là một developer, tôi muốn hệ thống có chiến lược fallback khi linked tickets chưa được analyze (không có KBRecord), sử dụng raw Jira data từ EnrichedContext thay vì để linkedTicketAnalyses rỗng.

#### Acceptance Criteria

7.1 WHEN JobExecutor xây dựng prompt và context là EnrichedContext với linkedTicketAnalyses rỗng nhưng allTickets có nhiều hơn 1 ticket, THE BRD_Prompt_Builder SHALL sử dụng raw ticket data từ allTickets (summary, description, status, priority) làm nguồn dữ liệu thay thế cho linked ticket context.

7.2 WHEN Enriched_Context có rawComments cho các tickets trong allTickets, THE Prompt_Section_Builder SHALL include comments từ tất cả tickets (không chỉ root ticket) vào prompt, bất kể tickets đó có KBRecord hay không.

7.3 WHEN Enriched_Context có allAttachmentChunks từ linked tickets, THE Prompt_Section_Builder SHALL include attachment content từ tất cả tickets vào prompt, bất kể tickets đó có KBRecord hay không.

7.4 THE Prompt_Section_Builder SHALL thêm annotation khi sử dụng fallback data: "[Note: Linked ticket {ticketId} chưa có deep analysis — sử dụng raw Jira data. Recommend chạy deep analysis cho ticket này để có dữ liệu đầy đủ hơn.]"

### Requirement 8: Đảm bảo tất cả 7 BRD Sections có nội dung thực tế

**User Story:** Là một BA/PM, tôi muốn BRD output có TẤT CẢ 7 sections được populate với nội dung thực tế từ dữ liệu phân tích, không có section nào hiển thị "Insufficient data" khi dữ liệu tồn tại trong hệ thống.

#### Acceptance Criteria

8.1 WHEN EnrichedContext chứa ít nhất 1 ticket với businessSummary không rỗng, THE generated BRD SHALL có section "Project Overview" với nội dung thực tế (không phải "Insufficient data").

8.2 WHEN EnrichedContext chứa ít nhất 1 ticket với asIsState không rỗng HOẶC rawComments chứa thảo luận về current process, THE generated BRD SHALL có section "Existing Processes" với nội dung thực tế.

8.3 WHEN EnrichedContext chứa ít nhất 1 ticket với extractedRequirements không rỗng HOẶC acceptanceCriteria không rỗng, THE generated BRD SHALL có section "Project Requirements" với nội dung thực tế.

8.4 WHEN EnrichedContext chứa ticket metadata (ticketId, timestamps, source ticket IDs), THE generated BRD SHALL có section "Revision History" với nội dung thực tế được tạo từ metadata.

8.5 WHEN EnrichedContext chứa technicalDetails hoặc extractedRequirements với thuật ngữ kỹ thuật, THE generated BRD SHALL có section "Common Project Acronyms" với nội dung thực tế được trích xuất từ context.

8.6 WHEN EnrichedContext chứa dependencies với stakeholder information hoặc linked ticket assignees, THE generated BRD SHALL có section "Sign Off" với nội dung thực tế.

8.7 WHEN EnrichedContext chứa attachmentChunks hoặc diagrams, THE generated BRD SHALL có section "Appendix" với nội dung thực tế tham chiếu đến attachments và diagrams.

8.8 FOR ALL BRD generations với EnrichedContext chứa ít nhất 5 tickets và rawComments không rỗng, THE generated BRD SHALL có tối đa 1 section với "Insufficient data" (giảm từ 4 sections hiện tại).
