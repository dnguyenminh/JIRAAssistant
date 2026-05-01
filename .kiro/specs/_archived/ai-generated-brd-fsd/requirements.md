# AI-Generated BRD/FSD from Ticket Analysis — Requirements

## Introduction

Ticket Intelligence hiện cung cấp deep analysis cho từng ticket riêng lẻ (Business Summary, As-Is/To-Be, Technical Details, Dependencies, Diagrams). Tuy nhiên, với ticket phức tạp có nhiều linked tickets, sub-tasks và attachments, thông tin phân tích vẫn chưa đủ để tạo tài liệu nghiệp vụ hoàn chỉnh. Feature này cho phép hệ thống tổng hợp dữ liệu từ ticket chính + linked tickets + sub-tasks + attachment content (VectorStore) để sinh ra BRD (Business Requirements Document) và FSD (Functional Specification Document) hoàn chỉnh, có thể preview và export.

## Glossary

- **BRD_Generator**: Component backend chịu trách nhiệm tổng hợp dữ liệu và sinh BRD document từ kết quả phân tích ticket
- **FSD_Generator**: Component backend chịu trách nhiệm tổng hợp dữ liệu và sinh FSD document từ kết quả phân tích ticket
- **Document_Aggregator**: Component thu thập và tổng hợp dữ liệu từ nhiều nguồn (ticket chính, linked tickets, sub-tasks, VectorStore) thành unified context cho AI prompt
- **Document_Preview**: Frontend component hiển thị preview BRD/FSD dưới dạng rendered Markdown
- **Document_Exporter**: Component chuyển đổi generated document sang định dạng export (Markdown file, PDF)
- **BRD_Template**: Template chuẩn cho BRD theo Carleton University ITS gồm 7 top-level sections (với sub-sections và deep sub-sections): Revision History, Project Overview (Sponsor(s), Contributors, In Scope, Out of Scope), Common Project Acronyms/Names/Descriptions, Existing Processes (Summary Process Narrative, Timing, Volume, Screenshots, Problems), Project Requirements (Process Overview [Summary Process Narrative, Flow Diagram, Triggering Event and Pre-Conditions, Timing, Volume, Outcome(s) and/or Post-Conditions], Functional Requirements, Non-Functional Requirements [Availability, Compatibility, Extensibility, Maintainability, Scalability, Security, Usability, Performance], Data Requirements [Known Issues/Assumptions/Risks/Dependencies]), Sign Off, Appendix (Mock-ups, Glossary, Business Rules and Procedures, Document References)
- **FSD_Template**: Template chuẩn cho FSD theo FECredit gồm 11 top-level sections (với sub-sections): Introduction (Purpose, Project Scope, Document Scope, Related Documents, Terms/Acronyms, Risks & Assumptions), System/Solution Overview (Context/Interface/Data Flow Diagrams, System Actors, Dependencies & Change Impacts), Functional Specifications (Purpose/Description, Use Cases, Mock-ups, Functional Requirements, Field Level Specifications), System Configurations, Non-Functional Requirements, Reporting Requirements, Integration Requirements (Exception Handling/Error Reporting), Data Migration/Conversion Requirements (Strategy, Preparation, Specifications), References, Open Issues, Appendix
- **Generation_Context**: Đối tượng chứa toàn bộ dữ liệu tổng hợp (ticket analysis + linked ticket analyses + sub-task analyses + attachment chunks) dùng làm input cho AI prompt
- **KBRecord**: Model Knowledge Base hiện tại chứa kết quả deep analysis của một ticket (businessSummary, technicalDetails, dependencies, acceptanceCriteria, diagrams)
- **Vector_Store**: Bảng lưu trữ attachment content dưới dạng text chunks với embeddings cho semantic search

## Requirements

### Requirement 1: Tổng hợp dữ liệu đa nguồn cho document generation

**User Story:** Là một BA/Scrum Master, tôi muốn hệ thống tự động thu thập và tổng hợp dữ liệu từ ticket chính, linked tickets, sub-tasks và nội dung attachments, để AI có đủ ngữ cảnh tạo BRD/FSD chính xác.

#### Acceptance Criteria

1.1 WHEN người dùng yêu cầu generate BRD hoặc FSD cho một ticket, THE Document_Aggregator SHALL thu thập KBRecord của ticket chính từ Knowledge Base. IF ticket chưa có deep analysis, THEN THE Document_Aggregator SHALL trả lỗi yêu cầu phân tích ticket trước.

1.2 WHEN ticket chính có linked tickets (blocking, related) hoặc sub-tasks, THE Document_Aggregator SHALL thu thập KBRecord của TẤT CẢ linked tickets và sub-tasks đã có trong Knowledge Base, không giới hạn số lượng.

1.3 WHEN ticket chính có attachments đã được indexed vào Vector_Store, THE Document_Aggregator SHALL thực hiện semantic search trong Vector_Store với query dựa trên businessSummary và extractedRequirements của ticket, lấy TẤT CẢ attachment chunks có similarity cao (topK=100), không giới hạn số lượng nhỏ.

1.4 THE Document_Aggregator SHALL tổng hợp tất cả dữ liệu thu thập thành đối tượng Generation_Context bao gồm: (a) deep analysis của ticket chính, (b) danh sách deep analyses của linked tickets và sub-tasks, (c) attachment content chunks liên quan, (d) sprint metadata (nếu có từ Jira).

1.5 IF một số linked tickets chưa có trong Knowledge Base, THEN THE Document_Aggregator SHALL bỏ qua tickets đó và ghi log warning, không block quá trình generation.

### Requirement 2: Sinh BRD document từ kết quả phân tích

**User Story:** Là một BA/PM, tôi muốn hệ thống sinh ra BRD hoàn chỉnh theo template chuẩn từ kết quả phân tích ticket, để tôi có tài liệu nghiệp vụ sẵn sàng review mà không cần viết thủ công.

#### Acceptance Criteria

2.1 THE BRD_Generator SHALL xây dựng AI prompt chứa Generation_Context và BRD_Template, yêu cầu AI sinh document theo đúng 7 top-level sections (với sub-sections và deep sub-sections) của BRD_Template (Carleton University ITS Business Requirements Template).

2.2 THE BRD_Generator SHALL chỉ định AI sử dụng dữ liệu thực tế từ Generation_Context, không được bịa đặt nội dung. Mỗi section phải trích dẫn nguồn dữ liệu (ticket ID, attachment filename).

2.3 WHEN Generation_Context chứa businessSummary và extractedRequirements, THE BRD_Generator SHALL yêu cầu AI tổng hợp chúng vào sections Executive Summary và Functional Requirements.

2.4 WHEN Generation_Context chứa dependencies từ ticket chính và linked tickets, THE BRD_Generator SHALL yêu cầu AI tổng hợp vào section Dependencies với risk level từ dependency analysis.

2.5 WHEN Generation_Context chứa acceptanceCriteria, THE BRD_Generator SHALL yêu cầu AI tổng hợp vào section Functional Requirements với testability assessment.

2.6 WHEN Generation_Context chứa attachment content liên quan đến business requirements, THE BRD_Generator SHALL yêu cầu AI tích hợp thông tin từ attachments vào các sections phù hợp, kèm tham chiếu filename.

2.7 THE BRD_Generator SHALL trả về kết quả dưới dạng Markdown string có cấu trúc rõ ràng theo 7 top-level sections (với sub-sections và deep sub-sections).

2.8 IF AI response không chứa đủ 7 top-level sections, THEN THE BRD_Generator SHALL bổ sung sections thiếu với nội dung mặc định "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none" thay vì trả về document không hoàn chỉnh.

### Requirement 3: Sinh FSD document từ kết quả phân tích

**User Story:** Là một Technical Lead/Architect, tôi muốn hệ thống sinh ra FSD hoàn chỉnh theo template chuẩn từ kết quả phân tích ticket, để team dev có tài liệu kỹ thuật chi tiết cho implementation.

#### Acceptance Criteria

3.1 THE FSD_Generator SHALL xây dựng AI prompt chứa Generation_Context và FSD_Template, yêu cầu AI sinh document theo đúng 11 top-level sections (với sub-sections) của FSD_Template (FECredit Functional Specification template).

3.2 THE FSD_Generator SHALL chỉ định AI sử dụng dữ liệu thực tế từ Generation_Context, không được bịa đặt nội dung.

3.3 WHEN Generation_Context chứa technicalDetails.apiSpecifications, THE FSD_Generator SHALL yêu cầu AI mở rộng thành section API Specifications chi tiết bao gồm method, path, request/response schema, error codes.

3.4 WHEN Generation_Context chứa technicalDetails.databaseChanges, THE FSD_Generator SHALL yêu cầu AI mở rộng thành section Database Design chi tiết bao gồm table schemas, relationships, migration notes.

3.5 WHEN Generation_Context chứa technicalDetails.externalIntegrations, THE FSD_Generator SHALL yêu cầu AI mở rộng thành section External Integrations chi tiết bao gồm protocols, endpoints, authentication, data flow.

3.6 WHEN Generation_Context chứa diagrams từ deep analysis, THE FSD_Generator SHALL yêu cầu AI tham chiếu và mở rộng diagrams vào section System Architecture.

3.7 WHEN Generation_Context chứa acceptanceCriteria với testabilityAssessment, THE FSD_Generator SHALL yêu cầu AI tổng hợp thành section Testing Strategy với test scenarios cụ thể.

3.8 THE FSD_Generator SHALL trả về kết quả dưới dạng Markdown string có cấu trúc rõ ràng theo 11 top-level sections (với sub-sections).

3.9 IF AI response không chứa đủ 11 top-level sections, THEN THE FSD_Generator SHALL bổ sung sections thiếu với nội dung mặc định "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none" thay vì trả về document không hoàn chỉnh.

### Requirement 4: API endpoint cho BRD/FSD generation

**User Story:** Là một developer, tôi muốn có API endpoint rõ ràng để trigger BRD/FSD generation và nhận kết quả, để frontend có thể gọi và hiển thị document.

#### Acceptance Criteria

4.1 THE Backend_Server SHALL cung cấp endpoint `POST /api/analysis/{ticketId}/generate-brd` nhận ticketId, khởi chạy quá trình generation bất đồng bộ (async), và trả về HTTP 202 Accepted với `{"status": "started"}`. Frontend theo dõi tiến trình qua endpoint document-status (Req 4.3) và fetch kết quả qua endpoint documents/{type} (Req 5.5) khi hoàn tất.

4.2 THE Backend_Server SHALL cung cấp endpoint `POST /api/analysis/{ticketId}/generate-fsd` nhận ticketId, khởi chạy quá trình generation bất đồng bộ (async) tương tự endpoint generate-brd, trả về HTTP 202 Accepted.

4.3 WHILE BRD hoặc FSD đang được generate, THE Backend_Server SHALL cập nhật trạng thái qua endpoint `GET /api/analysis/{ticketId}/document-status` trả về: phase ("AGGREGATING_DATA", "GENERATING_DOCUMENT", "COMPLETE", "FAILED"), progressPercent (0-100), documentType.

4.4 IF ticket chưa có deep analysis trong Knowledge Base, THEN THE Backend_Server SHALL trả về HTTP 400 với message mô tả yêu cầu phân tích ticket trước khi generate document.

4.5 IF AI generation thất bại, THEN THE Backend_Server SHALL retry tối đa 2 lần. Sau đó trả về HTTP 500 với message lỗi cụ thể.

4.6 WHILE người dùng có vai trò Reader, THE Backend_Server SHALL từ chối request generate BRD/FSD với HTTP 403.

### Requirement 5: Lưu trữ generated documents

**User Story:** Là một BA/PM, tôi muốn documents đã generate được lưu lại trong hệ thống, để tôi có thể xem lại mà không cần generate lại.

#### Acceptance Criteria

5.1 THE Knowledge_Base SHALL mở rộng schema để lưu trữ generated documents với các trường: id, ticketId, documentType ("BRD" hoặc "FSD"), markdownContent, generatedAt, sourceTicketIds (JSON array), attachmentSources (JSON array), aiProviderUsed.

5.2 WHEN BRD hoặc FSD được generate thành công, THE Backend_Server SHALL lưu document vào Knowledge Base.

5.3 WHEN người dùng yêu cầu generate BRD/FSD cho ticket đã có document cùng loại, THE Backend_Server SHALL tạo document mới (overwrite) và cập nhật timestamp.

5.4 THE Backend_Server SHALL cung cấp endpoint `GET /api/analysis/{ticketId}/documents` trả về danh sách documents đã generate cho ticket đó (BRD và/hoặc FSD), bao gồm metadata (documentType, generatedAt, aiProviderUsed) nhưng không bao gồm markdownContent.

5.5 THE Backend_Server SHALL cung cấp endpoint `GET /api/analysis/{ticketId}/documents/{documentType}` trả về full document content (markdownContent) cho document type chỉ định ("brd" hoặc "fsd").

### Requirement 6: Frontend — Nút Generate BRD/FSD trên Ticket Intelligence

**User Story:** Là một BA/Scrum Master, tôi muốn có nút Generate BRD và Generate FSD trên trang Ticket Intelligence sau khi deep analysis hoàn tất, để tôi có thể tạo tài liệu với một click.

#### Acceptance Criteria

6.1 WHEN ticket đã có deep analysis (trạng thái ANALYZED), THE Frontend_App SHALL hiển thị hai nút "Generate BRD" và "Generate FSD" bên dưới kết quả phân tích, trong một section riêng có tiêu đề "DOCUMENT GENERATION".

6.2 WHEN ticket chưa có deep analysis (trạng thái NOT_ANALYZED hoặc SCANNED), THE Frontend_App SHALL ẩn section Document Generation.

6.3 WHEN ticket đã có BRD hoặc FSD đã generate trước đó, THE Frontend_App SHALL hiển thị badge "Generated" kèm timestamp bên cạnh nút tương ứng, và đổi label thành "Re-generate BRD" hoặc "Re-generate FSD".

6.4 WHILE người dùng có vai trò Reader, THE Frontend_App SHALL vô hiệu hóa các nút Generate (opacity 0.5, cursor not-allowed).

6.5 WHEN người dùng click nút Generate, THE Frontend_App SHALL hiển thị BlockingOverlay với message "Generating BRD..." hoặc "Generating FSD..." và progress bar 3 giai đoạn: "Aggregating data..." (0-30%), "AI generating document..." (30-90%), "Saving..." (90-100%).

6.6 WHILE cascade analysis đang chạy cho ticket (status = RUNNING), THE Frontend_App SHALL vô hiệu hóa tất cả nút Generate (BRD, FSD, Slides, Generate All) với tooltip "Đang phân tích linked tickets — vui lòng đợi cascade analysis hoàn tất". Khi cascade hoàn tất (COMPLETED hoặc FAILED), buttons tự động enable lại qua self-healing polling. *(Thêm bởi feature spec `brd-insufficient-data-fix` — đảm bảo linked tickets có KBRecord trước khi generate BRD)*

6.7 WHEN người dùng cố gắng generate BRD/FSD qua API trong khi cascade đang chạy, THE Backend_Server SHALL trả về HTTP 409 Conflict với message "Cascade analysis đang chạy — vui lòng đợi hoàn tất trước khi sinh document". *(Thêm bởi feature spec `brd-insufficient-data-fix`)*

### Requirement 7: Frontend — Document Preview

**User Story:** Là một BA/PM, tôi muốn xem preview BRD/FSD ngay trên giao diện Ticket Intelligence sau khi generate, để tôi review nội dung trước khi export.

#### Acceptance Criteria

7.1 WHEN BRD hoặc FSD được generate thành công, THE Document_Preview SHALL hiển thị document trong một panel mới (tab hoặc modal full-width) với rendered Markdown bao gồm headings, lists, tables, code blocks.

7.2 THE Document_Preview SHALL hiển thị metadata bar ở đầu document: document type (BRD/FSD), ticket ID, generated timestamp, AI provider, và danh sách source tickets.

7.3 THE Document_Preview SHALL cung cấp table of contents sidebar liệt kê tất cả sections (H2 headings) cho phép click để scroll đến section tương ứng.

7.4 WHEN người dùng click nút "Close" hoặc nhấn Escape, THE Document_Preview SHALL đóng panel và quay về kết quả phân tích.

7.5 WHEN ticket đã có document generated trước đó, THE Frontend_App SHALL cho phép mở preview document cũ bằng cách click vào badge "Generated" mà không cần generate lại.

### Requirement 8: Frontend — Export Document

**User Story:** Là một BA/PM, tôi muốn export BRD/FSD ra file Markdown hoặc PDF, để tôi có thể chia sẻ với stakeholders ngoài hệ thống.

#### Acceptance Criteria

8.1 THE Document_Preview SHALL hiển thị nút "Export" trong metadata bar với dropdown chọn format: Markdown (.md) hoặc PDF (.pdf).

8.2 WHEN người dùng chọn export Markdown, THE Document_Exporter SHALL tải xuống file với tên `{ticketId}-{documentType}.md` (ví dụ: `NET-458-BRD.md`).

8.3 WHEN người dùng chọn export PDF, THE Document_Exporter SHALL chuyển đổi Markdown thành PDF sử dụng browser print API (window.print) với CSS tối ưu cho print, và trigger download.

8.4 THE Document_Exporter SHALL bao gồm header trong exported file: document type, ticket ID, generated timestamp, và source tickets.

### Requirement 9: Document Generation Prompt — Chống bịa đặt và đảm bảo truy xuất nguồn gốc

**User Story:** Là một BA/PM, tôi muốn BRD/FSD được tạo ra chỉ dựa trên dữ liệu thực tế từ ticket analysis, không chứa thông tin bịa đặt, để tôi tin tưởng và sử dụng document cho quyết định nghiệp vụ.

#### Acceptance Criteria

9.1 THE BRD_Generator và FSD_Generator SHALL bao gồm instruction rõ ràng trong prompt yêu cầu AI: (a) chỉ sử dụng dữ liệu từ Generation_Context, (b) không bịa đặt requirements, stakeholders, timelines, hoặc technical details, (c) đánh dấu rõ sections không có đủ dữ liệu.

9.2 THE BRD_Generator và FSD_Generator SHALL yêu cầu AI trích dẫn nguồn dữ liệu cho mỗi requirement hoặc technical detail, dưới dạng "[Source: {ticketId}]" hoặc "[Source: {filename}]".

9.3 WHEN Generation_Context không có đủ dữ liệu cho một section, THE generated document SHALL hiển thị rõ: "⚠️ Insufficient data — This section requires manual input. Available data from analysis: [list available data points]".

9.4 FOR ALL generated BRD documents hợp lệ, serialize thành Markdown rồi parse lại SHALL giữ nguyên cấu trúc 7 top-level sections với tất cả headings (round-trip property).

9.5 FOR ALL generated FSD documents hợp lệ, serialize thành Markdown rồi parse lại SHALL giữ nguyên cấu trúc 11 top-level sections với tất cả headings (round-trip property).

### Requirement 10: Draw.io Diagrams trong BRD/FSD

**User Story:** Là một BA/Architect, tôi muốn BRD/FSD chứa các diagrams dưới dạng draw.io format (business process flow, system architecture, data flow), để tôi có thể chỉnh sửa và mở rộng diagrams trong draw.io editor.

#### Acceptance Criteria

10.1 THE BRD_Generator SHALL yêu cầu AI sinh các diagrams sau ở draw.io XML format: (a) Process Flow diagram cho section Existing Processes > Summary Process Narrative, (b) Requirements Traceability diagram cho section Project Requirements > Process Overview, (c) Stakeholder Map diagram cho section Project Overview > Project Contributors.

10.2 THE FSD_Generator SHALL yêu cầu AI sinh các diagrams sau ở draw.io XML format: (a) Context/Interface Diagram cho section System/Solution Overview, (b) Data Flow Diagram cho section System/Solution Overview > Data Flow, (c) Integration Architecture diagram cho section Integration Requirements, (d) Data Migration Flow diagram cho section Data Migration/Conversion Requirements.

10.3 WHEN AI sinh draw.io diagram, THE generated XML SHALL tuân thủ draw.io mxGraph format với: `<mxGraphModel>` root element, `<mxCell>` nodes với style attributes, và proper parent-child hierarchy.

10.4 THE Document_Preview SHALL render draw.io diagrams inline trong document sử dụng draw.io viewer (viewer-static.min.js) — tương tự cách DiagramRenderer hiện tại render trong Ticket Intelligence.

10.5 THE Document_Exporter SHALL embed draw.io XML dưới dạng `<!-- drawio: base64_encoded_xml -->` comment trong Markdown export, và render thành image trong PDF export.

10.6 WHEN AI không có đủ dữ liệu để sinh diagram có ý nghĩa, THE generated document SHALL bỏ qua diagram đó thay vì sinh diagram trống hoặc generic.

10.7 THE BRD_Generator và FSD_Generator SHALL yêu cầu AI sử dụng dữ liệu thực tế từ Generation_Context (ticket IDs, service names, table names, endpoints) làm labels trong diagrams — không dùng placeholder text.

### Requirement 11: Sinh Slide tóm tắt Requirements

**User Story:** Là một BA/PM, tôi muốn hệ thống tự động sinh file slide (presentation) tóm tắt requirements từ BRD, để tôi có thể trình bày nhanh cho stakeholders mà không cần tạo slide thủ công.

#### Acceptance Criteria

11.1 WHEN người dùng yêu cầu generate slides (qua endpoint `POST /api/analysis/{ticketId}/generate-slides` hoặc nút "Generate Slides" trên frontend), THE Backend_Server SHALL sinh Requirements Summary Slide document dạng Markdown (slide format) từ BRD đã có, bao gồm: (a) Vision/Overview slide, (b) Requirements overview table, (c) Data flow diagram, (d) Scope (In/Out) slide, (e) Key stakeholders slide, (f) Risk summary slide, (g) Timeline/Milestones slide.

11.2 THE Requirements Summary Slide SHALL sử dụng Markdown heading separators (`---`) để phân chia các slides, mỗi slide tối đa 5-7 bullet points cho readability.

11.3 THE Requirements Summary Slide SHALL extract và tóm tắt thông tin từ BRD sections — KHÔNG sinh nội dung mới mà chỉ condensed từ BRD đã generate.

11.4 THE Backend_Server SHALL lưu slide document với documentType = "REQUIREMENT_SLIDES" trong cùng schema với BRD/FSD.

11.5 THE Frontend_App SHALL hiển thị nút "Generate Slides" bên cạnh nút "Generate BRD", chỉ enabled khi BRD đã được generate.

11.6 THE Document_Preview SHALL hỗ trợ hiển thị slide document với navigation slide-by-slide (Previous/Next buttons) ngoài chế độ scroll thông thường.

11.7 THE Document_Exporter SHALL hỗ trợ export slides ra Markdown (.md) với slide separators, cho phép import vào các presentation tools (Marp, Slidev, reveal.js).
