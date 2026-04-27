# AI Chat Sidebar — Requirements

# Yêu cầu 19: AI Chat Sidebar — Trợ lý AI Tương tác

## Giới thiệu

AI Chat Sidebar là panel docked bên phải trong Shell layout, có thể resize bằng drag handle. Cung cấp giao diện chat AI cho phép người dùng đặt câu hỏi, nhận hướng dẫn, và tương tác với các thành phần khác của ứng dụng thông qua ngôn ngữ tự nhiên. AI sử dụng các provider đã cấu hình trong hệ thống (Ollama, Gemini, LM Studio) và tham khảo Knowledge Base (kết quả phân tích, relationship network) để trả lời chính xác theo ngữ cảnh dự án.

---

## User Stories

**User Story 1:** Là một người dùng, tôi muốn mở thanh AI Chat Sidebar để đặt câu hỏi về dự án, để tôi có thể nhận được câu trả lời nhanh chóng dựa trên dữ liệu phân tích hiện có.

**User Story 2:** Là một người dùng, tôi muốn AI tham khảo Knowledge Base và Relationship Network khi trả lời, để câu trả lời có ngữ cảnh và chính xác hơn.

**User Story 3:** Là một người dùng, tôi muốn AI có thể điều hướng tôi đến các màn hình khác trong ứng dụng, để tôi có thể nhanh chóng truy cập thông tin liên quan.

**User Story 4:** Là một Administrator, tôi muốn yêu cầu AI thay đổi cấu hình hệ thống, để tôi có thể điều chỉnh tham số mà không cần rời khỏi cuộc hội thoại.

**User Story 5:** Là một người dùng, tôi muốn nhập câu hỏi bằng giọng nói, để tôi có thể tương tác với AI nhanh hơn mà không cần gõ phím.

**User Story 6:** Là một người dùng, tôi muốn đính kèm file (Word, Excel, PDF, hình ảnh) vào cuộc hội thoại, để AI có thể phân tích nội dung file và trả lời dựa trên đó.

**User Story 7:** Là một người dùng, tôi muốn paste hình ảnh từ clipboard vào chat, để tôi có thể chia sẻ screenshot nhanh chóng với AI.

---

## Acceptance Criteria

### UI & Layout

**19.1** WHEN người dùng nhấn nút toggle AI Chat (💬 icon trên Navbar header), THE Frontend_App SHALL hiển thị AI_Chat_Sidebar dưới dạng panel docked bên phải trong Shell layout, theo Obsidian_Kinetic design system.

**19.2** WHEN AI_Chat_Sidebar đang mở, THE Frontend_App SHALL hiển thị khung chat bao gồm: tiêu đề panel, vùng hiển thị lịch sử hội thoại (cuộn được), ô nhập tin nhắn dạng textarea (hỗ trợ nhiều dòng, Shift+Enter xuống dòng, Enter gửi), và nút gửi.

**19.3** WHEN người dùng nhấn nút toggle AI Chat lần nữa hoặc nhấn nút đóng trên panel, THE Frontend_App SHALL ẩn AI_Chat_Sidebar và giữ nguyên lịch sử hội thoại.

**19.4** THE Frontend_App SHALL hiển thị nút toggle AI Chat (💬 icon) trên Navbar header, bên cạnh project badge và user avatar, luôn nhìn thấy được trên mọi màn hình có Shell (không hiển thị trên Login và Project Select).

**19.4a** THE AI_Chat_Sidebar SHALL có resize handle ở cạnh trái, cho phép người dùng kéo để thay đổi chiều rộng (min 280px, max 600px, default 380px).

### Gửi & Nhận Tin nhắn

**19.5** WHEN người dùng gửi tin nhắn trong AI_Chat_Sidebar, THE Backend_Server SHALL chuyển tin nhắn đến AI_Orchestrator kèm theo ngữ cảnh hiện tại (project key, màn hình đang xem, vai trò người dùng).

**19.6** WHEN AI_Orchestrator nhận được tin nhắn chat, THE AI_Orchestrator SHALL sử dụng AI provider đang hoạt động (theo thứ tự failover đã cấu hình) để tạo phản hồi.

**19.7** WHILE AI_Orchestrator đang xử lý tin nhắn, THE Frontend_App SHALL hiển thị chỉ báo "đang nhập" (typing indicator) trong vùng chat.

**19.8** WHEN AI_Orchestrator trả về phản hồi, THE Frontend_App SHALL hiển thị phản hồi trong vùng chat với định dạng markdown cơ bản (bold, italic, code block, danh sách).

### Tích hợp Knowledge Base

**19.9** WHEN AI_Orchestrator xử lý tin nhắn chat, THE AI_Orchestrator SHALL truy vấn KB_Repository để lấy dữ liệu phân tích ticket liên quan (RequirementSummary, ComplexityAssessment, EvolutionHistory) làm ngữ cảnh bổ sung cho prompt.

**19.10** WHEN AI_Orchestrator xử lý tin nhắn chat, THE AI_Orchestrator SHALL truy vấn FeatureNetworkMapper và Graph_Engine để lấy thông tin mối quan hệ giữa các ticket (clusters, dependencies, semantic links) làm ngữ cảnh bổ sung.

**19.11** WHEN người dùng hỏi về một ticket cụ thể, THE AI_Orchestrator SHALL tìm kiếm ticket đó trong Knowledge_Base và trả về thông tin phân tích đã lưu trước khi gọi AI provider (chiến lược KB-First).

> **Mở rộng từ spec `ticket-intelligence` (Req 24)**: WHEN ticket đã được phân tích sâu (Deep Analysis), THE ChatService SHALL đưa kết quả Deep Analysis vào ngữ cảnh prompt bao gồm extracted_requirements, technical_details (API specs, DB changes, integrations), dependencies, và acceptance_criteria — thay vì chỉ RequirementSummary đơn giản. Khi user hỏi về requirements, API endpoints, hoặc DB changes, ChatService trả lời dựa trên dữ liệu Deep Analysis. Nếu ticket chưa được phân tích sâu, ChatService gợi ý action button "Analyze Ticket".

### Tương tác với Ứng dụng

**19.12** WHEN AI phản hồi chứa tham chiếu đến một màn hình hoặc chức năng cụ thể, THE Frontend_App SHALL hiển thị phản hồi kèm nút hành động (action button) cho phép người dùng điều hướng đến màn hình đó (Dashboard, Knowledge Graph, Analysis, Ticket Intelligence, Integrations, Settings).

**19.13** WHEN người dùng yêu cầu AI thay đổi cấu hình (ví dụ: đổi AI provider, thay đổi endpoint, cập nhật temperature), THE Backend_Server SHALL kiểm tra quyền RBAC của người dùng trước khi thực hiện thay đổi qua SettingsRepository hoặc ProviderConfigRepository.

**19.14** IF người dùng không có quyền Administrator và yêu cầu thay đổi cấu hình, THEN THE Backend_Server SHALL từ chối yêu cầu và AI_Chat_Sidebar SHALL hiển thị thông báo "Bạn không có quyền thực hiện thao tác này. Vui lòng liên hệ Administrator."

**19.15** WHEN AI thực hiện thay đổi cấu hình thành công, THE AI_Chat_Sidebar SHALL hiển thị xác nhận thay đổi kèm chi tiết (tham số đã thay đổi, giá trị cũ → giá trị mới).

### Lịch sử Hội thoại (Per-User Persistence)

**19.16** THE Backend_Server SHALL lưu trữ toàn bộ lịch sử hội thoại AI Chat theo từng tài khoản người dùng (per-user) trong Knowledge_Base, đảm bảo lịch sử được giữ lại vĩnh viễn và có thể truy cập bất cứ lúc nào sau khi đăng nhập.

**19.17** WHEN người dùng mở AI_Chat_Sidebar, THE Frontend_App SHALL tải lịch sử hội thoại từ Backend_Server (qua `GET /api/chat/history`) và hiển thị toàn bộ tin nhắn trước đó trong vùng chat, cho phép cuộn xem lại.

**19.17a** WHEN người dùng nhấn phím mũi tên lên (↑) trong ô nhập tin nhắn, THE Frontend_App SHALL điền lại câu hỏi đã gửi trước đó (command history navigation), và phím mũi tên xuống (↓) SHALL di chuyển đến câu hỏi gần hơn trong lịch sử. Chỉ các tin nhắn do người dùng gửi mới nằm trong danh sách điều hướng.

### Xử lý Lỗi

**19.18** IF không có AI provider nào được cấu hình hoặc tất cả provider đều OFFLINE, THEN THE AI_Chat_Sidebar SHALL hiển thị thông báo "Không có AI provider khả dụng. Vui lòng cấu hình provider trong trang Integrations." kèm nút điều hướng đến Integrations.

**19.19** IF AI provider trả về lỗi hoặc timeout (30 giây), THEN THE AI_Orchestrator SHALL thử failover sang provider tiếp theo theo thứ tự ưu tiên, và AI_Chat_Sidebar SHALL hiển thị thông báo lỗi nếu tất cả provider đều thất bại.

### API & Backend

**19.20** THE Backend_Server SHALL cung cấp endpoint `POST /api/chat/send` nhận `ChatRequest` (message, conversationHistory, context) và trả về `ChatResponse` (reply, actions, references).

**19.21** THE Backend_Server SHALL cung cấp endpoint `POST /api/chat/execute-action` nhận `ChatActionRequest` (actionType, parameters) để thực hiện các hành động AI đề xuất (navigate, changeConfig, triggerAnalysis), yêu cầu JWT authentication và RBAC check.

**19.22** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/history` trả về danh sách tin nhắn hội thoại của người dùng hiện tại (xác thực qua JWT), sắp xếp theo thời gian tăng dần, hỗ trợ phân trang (query params: `page`, `size`).

**19.23** THE Backend_Server SHALL cung cấp endpoint `DELETE /api/chat/history` cho phép người dùng xóa toàn bộ lịch sử hội thoại của chính mình, yêu cầu JWT authentication.

### Context Window Indicator

**19.24** THE Frontend_App SHALL hiển thị một hình tròn (circular progress indicator) bên dưới nút Send, thể hiện phần trăm (%) context window đã sử dụng. Tính toán dựa trên tổng ký tự conversation history + KB context + system prompt so với giới hạn max tokens của AI provider.

**19.25** WHEN context window usage vượt 80%, THE indicator SHALL chuyển sang màu warning (vàng). WHEN vượt 95%, SHALL chuyển sang màu danger (đỏ) và hiển thị tooltip gợi ý "Context gần đầy. Hãy xóa lịch sử hoặc bắt đầu cuộc hội thoại mới."

### Voice Input (Speech-to-Text)

**19.26** THE Frontend_App SHALL hiển thị nút microphone (🎤) bên cạnh nút Send trong input area của AI_Chat_Sidebar.

**19.27** WHEN người dùng nhấn nút microphone, THE Frontend_App SHALL sử dụng Web Speech API (`SpeechRecognition`) để bắt đầu ghi âm giọng nói và chuyển đổi thành văn bản (speech-to-text), điền vào textarea.

**19.28** WHILE đang ghi âm, THE Frontend_App SHALL hiển thị chỉ báo trực quan (nút microphone đổi màu đỏ + animation pulse) để user biết đang recording.

**19.29** WHEN ghi âm kết thúc (user nhấn nút lần nữa hoặc im lặng >3 giây), THE Frontend_App SHALL dừng recording và điền text đã nhận dạng vào textarea. User có thể chỉnh sửa trước khi gửi.

### File & Image Upload

**19.30** THE Frontend_App SHALL hiển thị nút đính kèm file (📎) bên cạnh nút microphone trong input area.

**19.31** WHEN người dùng nhấn nút đính kèm, THE Frontend_App SHALL mở file picker cho phép chọn file với các định dạng: Word (.doc, .docx), Excel (.xls, .xlsx), PDF (.pdf), hình ảnh (.png, .jpg, .jpeg, .gif, .webp).

**19.32** WHEN người dùng chọn file, THE Frontend_App SHALL upload file lên Backend_Server qua `POST /api/chat/upload` (multipart/form-data), hiển thị preview (thumbnail cho hình ảnh, icon + tên file cho documents) trong input area trước khi gửi.

**19.33** THE Backend_Server SHALL cung cấp endpoint `POST /api/chat/upload` nhận file upload (max 10MB), lưu file vào storage, trả về `fileId` và `fileUrl`. Hỗ trợ MIME types: `image/*`, `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.*`, `application/vnd.ms-excel`.

**19.34** WHEN người dùng gửi tin nhắn kèm file đính kèm, THE ChatRequest SHALL bao gồm `attachments: List<ChatAttachment>` (fileId, fileName, fileType, fileUrl). THE ChatService SHALL extract text content từ file (PDF text, Word text, Excel data) và inject vào prompt context.

### Clipboard Paste (Image)

**19.35** WHEN người dùng paste (Ctrl+V) hình ảnh từ clipboard vào textarea hoặc vùng chat, THE Frontend_App SHALL tự động detect clipboard image data, upload lên server qua `POST /api/chat/upload`, và hiển thị preview thumbnail trong input area.

**19.36** THE Frontend_App SHALL hỗ trợ drag-and-drop file vào vùng chat — kéo file từ desktop/explorer vào AI_Chat_Sidebar SHALL trigger upload flow giống như nút đính kèm.

---
### AI Personalization — Per-User Configuration

**19.37** THE Frontend_App SHALL hiển thị nút cấu hình (⚙️) trên header của AI_Chat_Sidebar, mở panel "AI Personalization" cho phép user tùy chỉnh hành vi AI.

**19.38** THE AI_Personalization panel SHALL hiển thị 4 bảng tóm tắt (summary table, read-only) cho phép user xem tổng quan dữ liệu cá nhân hóa. Mỗi bảng hiển thị danh sách dòng đã tạo với thông tin rút gọn, kèm nút "➕ Thêm" (mở popup form tạo mới) và nút xóa (🗑️) trên mỗi dòng. Khi click vào một dòng trong bảng, popup form chỉnh sửa sẽ mở ra. Bốn loại bảng:
- **Skills Table**: Bảng kỹ năng/chuyên môn — hiển thị tóm tắt `Tên kỹ năng`, `Mức độ`. AI sẽ điều chỉnh ngôn ngữ và mức độ chi tiết phù hợp với kỹ năng user.
- **Workflow Table**: Bảng quy trình làm việc — hiển thị tóm tắt `Bước`, `Tên quy trình`. AI sẽ đề xuất hành động phù hợp workflow.
- **Instructions Table**: Bảng hướng dẫn cho AI — hiển thị tóm tắt `Hướng dẫn` (cắt ngắn), `Độ ưu tiên`. Dữ liệu inject vào system prompt.
- **Rules Table**: Bảng quy tắc bắt buộc — hiển thị tóm tắt `Quy tắc` (cắt ngắn), `Loại`. Dữ liệu inject vào system prompt như constraints.

**19.38a** WHEN người dùng nhấn nút "➕ Thêm" trên mỗi bảng, THE Frontend_App SHALL mở popup form (modal dialog) tương ứng với loại bảng đó, cho phép nhập dữ liệu mới trên form đủ lớn, thoải mái. Popup form có nút "Save" (lưu và đóng) và "Cancel" (hủy và đóng).

**19.38b** WHEN người dùng click vào một dòng trong bảng tóm tắt, THE Frontend_App SHALL mở popup form (modal dialog) chứa dữ liệu hiện tại của dòng đó, cho phép chỉnh sửa trên form đủ lớn. Nhấn "Save" SHALL lưu thay đổi và đóng popup, nhấn "Cancel" SHALL hủy thay đổi và đóng popup.

**19.38c** WHEN người dùng nhấn nút xóa (🗑️) trên một dòng trong bảng, THE Frontend_App SHALL hiển thị confirm dialog và xóa dòng đó khỏi bảng sau khi user xác nhận.

**19.38d** THE Skill_Popup_Form SHALL hiển thị dưới dạng modal dialog (overlay toàn màn hình với backdrop mờ), chứa các trường nhập liệu: `Tên kỹ năng` (TEXT input, bắt buộc), `Mức độ` (SELECT dropdown: Beginner/Intermediate/Expert, bắt buộc), `Mô tả` (TEXTAREA, optional — cho phép nhập mô tả chi tiết nhiều dòng, ví dụ: "5 năm kinh nghiệm Spring Boot, microservices"). Form có chiều rộng tối thiểu 500px (hoặc 90% viewport trên mobile) để đảm bảo đủ không gian làm việc.

**19.38e** THE Workflow_Popup_Form SHALL hiển thị dưới dạng modal dialog, chứa các trường nhập liệu: `Bước` (NUMBER input, auto-increment khi tạo mới, cho phép sửa khi edit), `Tên quy trình` (TEXT input, bắt buộc), `Mô tả chi tiết` (TEXTAREA, bắt buộc — cho phép nhập mô tả nhiều dòng, ví dụ: "Tôi review PR trước khi merge vào main, kiểm tra code style và logic"). Form có chiều rộng tối thiểu 500px.

**19.38f** THE Instruction_Popup_Form SHALL hiển thị dưới dạng modal dialog, chứa các trường nhập liệu: `Hướng dẫn` (TEXTAREA, bắt buộc — cho phép nhập hướng dẫn chi tiết nhiều dòng, ví dụ: "Luôn trả lời bằng tiếng Việt. Khi giải thích code, thêm comment inline."), `Độ ưu tiên` (SELECT dropdown: Cao/Trung bình/Thấp, bắt buộc). Form có chiều rộng tối thiểu 500px.

**19.38g** THE Rule_Popup_Form SHALL hiển thị dưới dạng modal dialog, chứa các trường nhập liệu: `Quy tắc` (TEXTAREA, bắt buộc — cho phép nhập quy tắc chi tiết nhiều dòng, ví dụ: "Không bao giờ xóa dữ liệu production. Luôn backup trước khi thực hiện migration."), `Loại` (SELECT dropdown: Cấm/Bắt buộc/Khuyến nghị, bắt buộc). Form có chiều rộng tối thiểu 500px.

**19.38h** WHEN người dùng nhấn "Save" trên popup form mà các trường bắt buộc chưa được điền, THE Frontend_App SHALL hiển thị validation error trên trường tương ứng (viền đỏ + thông báo "Trường này là bắt buộc") và KHÔNG đóng popup cho đến khi dữ liệu hợp lệ.

**19.38i** WHEN popup form đang mở, THE Frontend_App SHALL hiển thị backdrop overlay (nền mờ) phía sau modal, ngăn tương tác với các thành phần khác. Nhấn phím Escape hoặc click vào backdrop SHALL đóng popup (tương đương Cancel).

**19.39** THE Backend_Server SHALL lưu trữ AI personalization config per-user trong bảng `user_ai_config` (SQLDelight), với cấu trúc: `user_id TEXT PK, skills_json TEXT, workflow_json TEXT, instructions_json TEXT, rules_json TEXT, updated_at TEXT`. Mỗi trường `*_json` lưu dữ liệu bảng dưới dạng JSON array of objects (ví dụ: `[{"name":"Java","level":"Expert","description":"5 năm"}]`).

**19.40** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/config` trả về AI personalization config của user hiện tại (dữ liệu JSON cho 4 bảng), và `PUT /api/chat/config` để cập nhật toàn bộ config (nhận JSON body chứa 4 mảng tương ứng 4 bảng).

**19.41** WHEN ChatService xây dựng system prompt, THE ChatService SHALL đọc dữ liệu từ 4 bảng personalization, format thành text có cấu trúc (liệt kê skills kèm mức độ, workflow theo thứ tự bước, instructions theo độ ưu tiên, rules theo loại), và inject vào prompt context sau system prompt chung và trước KB/graph context.

**19.42** WHEN user chưa cấu hình personalization (tất cả bảng trống), THE ChatService SHALL sử dụng default system prompt không có personalization — không ảnh hưởng đến chức năng chat cơ bản.

**19.43** THE Frontend_App SHALL hiển thị preview ngắn gọn của personalization config trên header AI_Chat_Sidebar (ví dụ: "⚙️ 3 skills, 2 rules") dựa trên số dòng trong mỗi bảng, để user biết config đang active.

---

### Multi-Conversation Management

**19.44** THE Frontend_App SHALL hiển thị danh sách conversations ở phần trên của AI_Chat_Sidebar (collapsible list), mỗi conversation hiển thị tiêu đề (auto-generated từ message đầu tiên) và thời gian tạo.

**19.45** THE Frontend_App SHALL có nút "➕ New Chat" ở đầu danh sách conversations để tạo cuộc hội thoại mới. Conversation mới bắt đầu trống, conversation cũ được giữ nguyên.

**19.46** WHEN người dùng click vào một conversation trong danh sách, THE Frontend_App SHALL load và hiển thị messages của conversation đó trong vùng chat, thay thế messages hiện tại.

**19.47** THE Backend_Server SHALL lưu trữ conversations per-user trong bảng `chat_conversations` (id, user_id, title, created_at, updated_at). Mỗi message trong `chat_messages` SHALL có `conversation_id` foreign key.

**19.48** THE Backend_Server SHALL cung cấp endpoints:
- `GET /api/chat/conversations` — danh sách conversations của user (mới nhất trước)
- `POST /api/chat/conversations` — tạo conversation mới, trả về conversation_id
- `DELETE /api/chat/conversations/{id}` — xóa conversation và tất cả messages
- `PUT /api/chat/conversations/{id}` — đổi tên conversation

**19.49** WHEN người dùng gửi message đầu tiên trong conversation mới, THE Backend_Server SHALL auto-generate tiêu đề conversation từ nội dung message (cắt 50 ký tự đầu).

**19.50** THE Frontend_App SHALL cho phép người dùng đổi tên conversation bằng cách double-click vào tiêu đề trong danh sách.

**19.51** THE Frontend_App SHALL cho phép người dùng xóa conversation bằng nút 🗑️ (hiện khi hover) trên mỗi item trong danh sách. Hiển thị confirm dialog trước khi xóa.

**19.52** THE Frontend_App SHALL highlight conversation đang active trong danh sách với màu primary.

---

**Tổng (trước Local KB MCP Tool): 66 tiêu chí chấp nhận**


### Model-Aware Chat UI

**19.53** THE Frontend_App SHALL hiển thị tên model AI đang active (ví dụ: "gemma4:e2b", "gemini-1.5-pro") trên header hoặc footer của AI_Chat_Sidebar, lấy từ `GET /api/chat/model-info`.

**19.54** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/model-info` trả về thông tin model đang active: `{ modelName, provider, supportsVision, supportsTools, maxTokens }`.

**19.55** IF model đang active hỗ trợ vision (`supportsVision = true`), THE Frontend_App SHALL enable nút đính kèm hình ảnh (📎), cho phép paste hình từ clipboard (Ctrl+V) và upload hình từ đĩa. IF `supportsVision = false`, THE Frontend_App SHALL disable/ẩn các tính năng hình ảnh và hiển thị tooltip "Model hiện tại không hỗ trợ xử lý hình ảnh".

**19.56** THE Frontend_App SHALL hiển thị section "MCP Tools Available" trong AI_Chat_Sidebar (collapsible panel phía trên input area hoặc trong sidebar header area), liệt kê tất cả tools từ các MCP servers đang ACTIVE. Mỗi tool hiển thị: tên tool, tên server nguồn, và mô tả ngắn.

**19.56a** WHEN AI_Chat_Sidebar được mở, THE Frontend_App SHALL gọi `GET /api/chat/tools` để tải danh sách MCP tools available và hiển thị trong section "MCP Tools Available". Danh sách SHALL được nhóm theo server name.

**19.56b** IF không có MCP server nào ACTIVE hoặc không có tools available, THE Frontend_App SHALL hiển thị thông báo "Không có MCP tools khả dụng" trong section "MCP Tools Available".

**19.56c** IF model đang active hỗ trợ tool use (`supportsTools = true`), THE Frontend_App SHALL hiển thị icon 🔧 (tools) trong input area. Click vào icon SHALL mở dropdown danh sách tools available (từ MCP servers + built-in tools) cho phép chọn nhanh.

**19.57** WHEN người dùng chọn một tool từ dropdown hoặc click vào tool trong section "MCP Tools Available", THE Frontend_App SHALL chèn `@tên_tool` vào vị trí cursor trong textarea. Ví dụ: chọn "aws-docs" → chèn `@aws-docs` vào ô chat.

**19.58** THE Frontend_App SHALL hỗ trợ autocomplete khi user gõ `@` trong textarea — hiển thị dropdown filtered danh sách tools matching text sau `@`. Ví dụ: gõ `@aws` → hiển thị `@aws-docs`, `@aws-cli`.

**19.59** WHEN tin nhắn chứa `@tên_tool`, THE ChatService SHALL route phần liên quan đến tool đó qua MCP server tương ứng, kết hợp kết quả tool với AI response.

**19.60** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/tools` trả về danh sách tools available: `[{ toolName, serverName, description, inputSchema }]`, tổng hợp từ tất cả MCP servers đang ACTIVE.

---

### Local Knowledge Base MCP Tool

**User Story 8:** Là một người dùng, tôi muốn AI có thể chủ động truy vấn Knowledge Base cục bộ (vectorized data) thông qua tool call, để AI lấy được dữ liệu chính xác hơn mà không cần gọi external Jira MCP tool (chậm), giảm thời gian phản hồi.

#### Glossary bổ sung

- **Local_KB_Tool**: Bộ công cụ MCP-style chạy in-process (không qua stdio/network), cho phép AI chủ động truy vấn VectorStore và KBRepository để lấy dữ liệu đã vectorize (tickets, attachments, confluence, analysis, relationships, clusters).
- **McpAgenticLoop**: Vòng lặp agentic xử lý tool call trong AI response — phát hiện JSON tool call, thực thi tool, inject kết quả trở lại prompt, lặp tối đa 5 vòng.

#### Đăng ký Tool (Tool Registration)

**19.61** THE ChatService SHALL đăng ký bộ công cụ "knowledge_base" (gồm 3 operations: `search_knowledge`, `get_ticket_info`, `search_relationships`) vào danh sách MCP tools context, để AI biết các tool này khả dụng khi xây dựng prompt.

**19.62** WHEN Frontend_App gọi `GET /api/chat/tools`, THE Backend_Server SHALL bao gồm các Local_KB_Tool trong danh sách trả về, với `serverName` là "local-knowledge-base" và mô tả cho từng tool. Local_KB_Tool SHALL hiển thị trong section "MCP Tools Available" trên AI_Chat_Sidebar cùng với các external MCP tools.

**19.63** THE Local_KB_Tool SHALL có mô tả tổng quát: "Tìm kiếm Knowledge Base cục bộ chứa dữ liệu đã vectorize từ Jira tickets, attachments (PDF, Word, Excel), Confluence pages, kết quả phân tích AI, và mối quan hệ giữa các ticket."

#### Tool Operations

**19.64** THE Local_KB_Tool SHALL cung cấp operation `search_knowledge` nhận các tham số: `query` (STRING, bắt buộc — câu truy vấn semantic search), `chunkType` (STRING, tùy chọn — lọc theo loại: TICKET, ATTACHMENT, CONFLUENCE, ANALYSIS, EVOLUTION, RELATIONSHIP, CLUSTER), `topK` (INTEGER, tùy chọn, mặc định 10 — số kết quả trả về). Operation thực hiện semantic search qua EmbeddingService và VectorStore, trả về danh sách chunks phù hợp nhất.

**19.65** THE Local_KB_Tool SHALL cung cấp operation `get_ticket_info` nhận tham số: `ticketId` (STRING, bắt buộc — mã ticket, ví dụ "ITCM-129"). Operation truy vấn KBRepository.findByTicketId() trả về thông tin phân tích đã lưu: requirementSummary, complexityAssessment, scrumPoints, confidenceScore, evolutionHistory. IF ticket không tồn tại trong KB, THEN operation SHALL trả về thông báo "Ticket không tìm thấy trong Knowledge Base."

**19.66** THE Local_KB_Tool SHALL cung cấp operation `search_relationships` nhận tham số: `query` (STRING, bắt buộc — câu truy vấn về mối quan hệ/dependency). Operation thực hiện semantic search với `chunkType = RELATIONSHIP` qua VectorStore, trả về danh sách relationship/dependency chunks liên quan.

#### Thực thi Tool (Tool Execution)

**19.67** WHEN AI response chứa tool call JSON với `serverId` là "local-knowledge-base", THE McpAgenticLoop SHALL phát hiện đây là Local_KB_Tool và thực thi trực tiếp in-process qua VectorStore và KBRepository, KHÔNG gửi qua external MCP protocol (stdio/network). Thời gian thực thi mục tiêu dưới 100ms cho mỗi tool call.

**19.68** WHEN McpAgenticLoop thực thi Local_KB_Tool thành công, THE McpAgenticLoop SHALL inject kết quả tool vào prompt context theo cùng format với external MCP tool results (`--- TOOL RESULT [tool_name] ---`), cho phép AI tiếp tục vòng lặp agentic bình thường.

**19.69** IF Local_KB_Tool thực thi thất bại (VectorStore lỗi, EmbeddingService không khả dụng, hoặc không có dữ liệu), THEN THE McpAgenticLoop SHALL trả về thông báo lỗi mô tả ("Tool error: EmbeddingService unavailable" hoặc "No results found") và AI SHALL tiếp tục xử lý mà không bị gián đoạn.

**19.70** THE McpAgenticLoop SHALL xử lý Local_KB_Tool calls trong cùng vòng lặp agentic với external MCP tool calls, tuân thủ giới hạn tối đa 5 vòng (MAX_ROUNDS). AI có thể kết hợp Local_KB_Tool call và external MCP tool call trong cùng một conversation.

#### System Prompt Guidance

**19.71** WHEN ChatService xây dựng system prompt VÀ Local_KB_Tool đang enabled, THE ChatService SHALL inject hướng dẫn ưu tiên TUYỆT ĐỐI sử dụng Local_KB_Tool: "BẮT BUỘC sử dụng local knowledge_base tools (search_knowledge, get_ticket_info, search_relationships) TRƯỚC KHI gọi bất kỳ external Jira/Atlassian MCP tools nào. Local tools nhanh hơn (< 100ms) và chứa dữ liệu đã được phân tích sẵn (pre-analyzed, vectorized). CHỈ fall back sang external Jira MCP tools khi: (1) kết quả local không đủ hoặc không tìm thấy, (2) user yêu cầu rõ ràng dữ liệu real-time từ Jira, hoặc (3) cần thao tác write (tạo/cập nhật ticket) mà local KB không hỗ trợ." WHEN Local_KB_Tool đang disabled, THE ChatService SHALL KHÔNG inject hướng dẫn ưu tiên này vào system prompt.

**19.72** THE system prompt SHALL liệt kê rõ ràng 3 operations của Local_KB_Tool kèm mô tả tham số và use case, để AI biết khi nào nên dùng tool nào: `search_knowledge` cho tìm kiếm tổng quát, `get_ticket_info` cho tra cứu ticket cụ thể, `search_relationships` cho truy vấn mối quan hệ/dependency.

#### Tương tác với Knowledge Context tự động

**19.73** THE ChatService SHALL duy trì cơ chế `buildKnowledgeContext()` hiện tại (auto-inject top 10 vector search results vào mỗi prompt) song song với Local_KB_Tool. Local_KB_Tool cho phép AI chủ động truy vấn thêm khi cần dữ liệu cụ thể hơn, trong khi `buildKnowledgeContext()` cung cấp ngữ cảnh nền tảng tự động.

**19.74** WHEN AI sử dụng Local_KB_Tool `search_knowledge`, THE kết quả trả về SHALL được format theo cùng cấu trúc section với `buildKnowledgeContext()` (nhóm theo chunkType: RELEVANT TICKETS, RELATIONSHIPS, ANALYSIS, CONFLUENCE DOCS, ATTACHMENTS), đảm bảo tính nhất quán trong prompt context.

#### Integrations Page — Local KB Tool Card

**19.75** THE Frontend_App SHALL hiển thị Local_KB_Tool dưới dạng một card trong trang Integrations (section MCP Servers), với các thông tin: server name "Local Knowledge Base", type hiển thị là "local" (phân biệt với các loại "stdio" và "http" của external MCP servers), trạng thái ACTIVE hoặc DISABLED tùy theo cấu hình enable/disable. Card SHALL hiển thị mô tả: "Knowledge Base cục bộ — tìm kiếm dữ liệu đã vectorize từ Jira tickets, attachments, Confluence pages". KHÁC với external MCP servers, card Local_KB_Tool SHALL KHÔNG hiển thị các trường cấu hình command, args, và env (vì tool chạy in-process, không cần cấu hình external process).

#### Enable/Disable Toggle

**19.76** THE Local_KB_Tool card trong trang Integrations SHALL có toggle bật/tắt (enable/disable). WHEN toggle ở trạng thái disabled: Local_KB_Tool SHALL KHÔNG được bao gồm trong MCP tools context (AI không thể gọi tool này), KHÔNG hiển thị trong section "MCP Tools Available" trên AI_Chat_Sidebar, và cơ chế `buildKnowledgeContext()` auto-inject SHALL tiếp tục hoạt động bình thường (không bị ảnh hưởng). WHEN toggle ở trạng thái enabled (mặc định): Local_KB_Tool SHALL khả dụng cho AI sử dụng qua tool call. THE Backend_Server SHALL lưu trạng thái bật/tắt trong bảng `app_settings` với key `local_kb_tool_enabled` và value `"true"` hoặc `"false"` (mặc định `"true"`).

---

---

### Focus Ticket Parsing — Sửa lỗi AI trích xuất sai Ticket ID từ Focused Node

**User Story 9:** Là một người dùng, khi tôi focus vào một ticket trên Knowledge Graph và hỏi AI về ticket đó, tôi muốn AI sử dụng đúng ticket ID đầy đủ (ví dụ: ICL2-400) thay vì chỉ lấy phần project key (ICL2), để AI tra cứu đúng ticket tôi đang quan tâm.

#### Hành vi Hiện tại (Lỗi)

**19.77** WHEN user focus vào ticket ICL2-400 trên Knowledge Graph và hỏi AI về ticket đang focus THEN hệ thống inject prompt "Focused on node: ICL2-400" mà không có chỉ dẫn rằng đây là Jira ticket ID đầy đủ, khiến AI trích xuất sai thành "ICL2" (chỉ project key)

**19.78** WHEN user focus vào bất kỳ ticket nào có format `PROJECT-NUMBER` (ví dụ: ITCM-129, ABC-42) và yêu cầu AI tra cứu ticket đang focus THEN AI có thể tách sai ticket ID theo dấu gạch ngang và chỉ sử dụng phần project key khi gọi Jira tool

**19.79** WHEN prompt chứa "Focused on node: X" mà không có hướng dẫn cụ thể về cách sử dụng giá trị X THEN AI không có đủ ngữ cảnh để phân biệt giữa project key và ticket ID đầy đủ

#### Hành vi Mong đợi (Sửa lỗi)

**19.80** WHEN user focus vào ticket ICL2-400 trên Knowledge Graph và hỏi AI về ticket đang focus THEN hệ thống SHALL inject prompt rõ ràng chỉ định "ICL2-400" là Jira ticket ID đầy đủ (full issue key), và AI SHALL sử dụng chính xác "ICL2-400" khi gọi Jira tool

**19.81** WHEN user focus vào bất kỳ ticket nào có format `PROJECT-NUMBER` và yêu cầu AI tra cứu ticket đang focus THEN hệ thống SHALL cung cấp hướng dẫn trong prompt rằng focused node key là Jira issue key đầy đủ, KHÔNG ĐƯỢC tách hoặc cắt ngắn

**19.82** WHEN prompt chứa thông tin về focused node THEN hệ thống SHALL bao gồm chỉ dẫn rõ ràng cho AI rằng giá trị focused node là ticket ID đầy đủ cần sử dụng nguyên vẹn (ví dụ: "The focused Jira ticket ID is ICL2-400. Use this EXACT value as the issue key when calling Jira tools. Do NOT split or truncate it.")

#### Hành vi Không thay đổi (Ngăn ngừa Hồi quy)

**19.83** WHEN user KHÔNG ở trang Knowledge Graph THEN hệ thống SHALL CONTINUE TO trả về "User is NOT on the Knowledge Graph page." như hiện tại

**19.84** WHEN user ở trang Knowledge Graph nhưng KHÔNG focus vào node nào THEN hệ thống SHALL CONTINUE TO không thêm thông tin focused node vào prompt

**19.85** WHEN user ở trang Knowledge Graph và có active type filters, search query, hoặc selected cluster THEN hệ thống SHALL CONTINUE TO inject các thông tin graph context khác (filters, cluster, search query, visible nodes, depth) vào prompt như hiện tại

**19.86** WHEN user gửi tin nhắn chat bình thường không liên quan đến focused ticket THEN hệ thống SHALL CONTINUE TO xử lý chat bình thường với đầy đủ KB context, graph context, và MCP tools context

---

### Auto-Detect AI Response Format — Tự động nhận diện & cache cấu trúc phản hồi AI

**User Story 10:** Là một người dùng, tôi muốn hệ thống tự động nhận diện cấu trúc JSON phản hồi của từng AI model (ví dụ: model A dùng `"reply"`, model B dùng `"response.text"`, model C dùng `"content"`), lưu mapping vào DB, và tái sử dụng cho các lần gọi sau — để hệ thống linh động hỗ trợ mọi AI provider mà không cần hardcode.

#### Glossary bổ sung

- **Response_Format_Detector**: Module phát hiện cấu trúc JSON phản hồi AI bằng cách phân tích JSON object, tìm field chứa reply text (string dài nhất hoặc nested object chứa text), và trả về JSON path (ví dụ: `"reply"`, `"response.text"`, `"data.content"`).
- **Response_Format_Cache**: Bảng `app_settings` lưu mapping `ai_response_format:{provider}:{model}` → JSON path đã phát hiện, cho phép tái sử dụng mà không cần re-detect.
- **Tool_Call_Format_Detector**: Module phát hiện cấu trúc tool call trong JSON phản hồi AI (ví dụ: `"tool_calls[].function.name"` vs `"tool_name"` vs `"actions[].tool"`), lưu mapping tương tự Response_Format_Detector.

#### Auto-Detect Reply Field

**19.87** WHEN ChatResponseParser nhận JSON response từ AI provider VÀ không thể decode trực tiếp thành ChatResponse chuẩn (`"reply"` key), THE Response_Format_Detector SHALL phân tích JSON object để tìm field chứa reply text bằng heuristic: (1) ưu tiên key đã cache trong DB cho provider+model hiện tại, (2) thử các known keys (`"reply"`, `"text"`, `"content"`, `"message"`, `"response"`, `"answer"`, `"output"`), (3) nếu không match, tìm field kiểu String có độ dài lớn nhất trong JSON object (bao gồm nested 1 cấp).

**19.88** WHEN Response_Format_Detector tìm thấy reply field thành công qua heuristic (bước 2 hoặc 3 trong 19.87), THE Response_Format_Detector SHALL lưu JSON path của field đó vào `app_settings` với key format `ai_response_format:{providerType}:{modelName}` và value là JSON path (ví dụ: `"response.text"`, `"content"`, `"data.message"`). ProviderType là loại provider (OLLAMA, GEMINI, LMSTUDIO), modelName là tên model cụ thể (ví dụ: `"gemma3:4b"`, `"gemini-2.0-flash"`).

**19.89** WHEN ChatResponseParser xử lý JSON response VÀ đã có cached format mapping trong `app_settings` cho provider+model hiện tại, THE ChatResponseParser SHALL thử cached JSON path TRƯỚC KHI chạy heuristic detection. Cached path giúp bỏ qua bước detection, giảm thời gian parse.

**19.90** IF cached JSON path không còn hợp lệ (field không tồn tại hoặc trả về null trong response mới), THEN THE Response_Format_Detector SHALL xóa cached entry cũ, chạy lại heuristic detection từ đầu, và lưu path mới nếu tìm thấy.

**19.91** WHEN Response_Format_Detector không tìm được reply field nào (JSON không chứa string field phù hợp), THE ChatResponseParser SHALL fallback về raw text response như hiện tại — trả về toàn bộ raw AI response dưới dạng plain text trong `ChatResponse.reply`.

#### Auto-Detect Tool Call Format

**19.92** WHEN McpAgenticLoop phân tích AI response để tìm tool call VÀ không match format chuẩn (`"mcpToolCall"` key), THE Tool_Call_Format_Detector SHALL phân tích JSON object để tìm tool call structure bằng heuristic: (1) ưu tiên format đã cache trong DB cho provider+model hiện tại, (2) thử các known patterns (`"mcpToolCall"`, `"tool_calls"`, `"tool_name"` + `"tool_input"`, `"function_call"`, `"actions[].tool"`), (3) nếu match, extract tool name và arguments.

**19.93** WHEN Tool_Call_Format_Detector tìm thấy tool call format thành công, THE Tool_Call_Format_Detector SHALL lưu format pattern vào `app_settings` với key format `ai_tool_call_format:{providerType}:{modelName}` và value là pattern identifier (ví dụ: `"mcpToolCall"`, `"tool_name_input"`, `"tool_calls_array"`, `"function_call"`).

**19.94** WHEN McpAgenticLoop xử lý AI response VÀ đã có cached tool call format trong `app_settings` cho provider+model hiện tại, THE McpAgenticLoop SHALL thử cached format pattern TRƯỚC KHI chạy tất cả fallback parsers. Cached pattern giúp giảm số lần thử parse thất bại.

**19.95** IF cached tool call format không còn hợp lệ (parse thất bại với format đã cache), THEN THE Tool_Call_Format_Detector SHALL xóa cached entry cũ, chạy lại tất cả known patterns từ đầu, và lưu pattern mới nếu tìm thấy.

#### Provider + Model Context

**19.96** THE ChatServiceImpl SHALL truyền thông tin provider type và model name hiện tại vào ChatResponseParser và McpAgenticLoop, để các module detector có thể đọc/ghi cache theo đúng provider+model key.

**19.97** WHEN AI provider hoặc model thay đổi (user đổi provider trong Settings, hoặc đổi model), THE hệ thống SHALL tự động sử dụng cache entry tương ứng với provider+model mới (nếu có), hoặc chạy detection lại cho provider+model mới (nếu chưa có cache).

#### Quản lý Cache

**19.98** THE Backend_Server SHALL cung cấp khả năng xem và xóa response format cache entries thông qua `app_settings` API hiện có (`GET /api/settings`, `DELETE /api/settings/{key}`). Không cần UI riêng — admin có thể quản lý qua API hoặc Settings page hiện tại.

**19.99** THE Response_Format_Cache entries trong `app_settings` SHALL có prefix rõ ràng (`ai_response_format:` và `ai_tool_call_format:`) để dễ dàng lọc và quản lý, phân biệt với các settings khác.

#### Hành vi Không thay đổi (Ngăn ngừa Hồi quy)

**19.100** WHEN AI response là plain text (không phải JSON), THE ChatResponseParser SHALL CONTINUE TO trả về raw text trong `ChatResponse.reply` như hiện tại — auto-detect chỉ áp dụng cho JSON responses.

**19.101** WHEN AI response match format chuẩn ChatResponse (`"reply"` key decode thành công), THE ChatResponseParser SHALL CONTINUE TO sử dụng kết quả decode trực tiếp — KHÔNG chạy detection hoặc cache lookup.

**19.102** THE hệ thống SHALL CONTINUE TO hỗ trợ tất cả hardcoded fallback patterns hiện tại trong ChatResponseParser và McpToolCallFallback như một phần của heuristic detection — auto-detect bổ sung thêm khả năng cache và dynamic discovery, KHÔNG thay thế logic fallback hiện có.

---

**Tổng: 112 tiêu chí chấp nhận**


---

## Liên kết Spec — Internal MCP Server

> **Internal MCP Server (spec `mcp-servers`, requirements 6.70–6.112)**: Mở rộng MCP với Internal MCP Server (`jira-assistant-ui`) chạy in-process, expose toàn bộ tương tác UI của ứng dụng dưới dạng MCP tools. Ảnh hưởng đến AI Chat Sidebar:
> - **System prompt injection (6.109)**: Internal MCP tools hiển thị với prefix `[Internal]` thay vì `[MCP:{serverName}]` (thay đổi hành vi req 6.52), và được ưu tiên hiển thị trước external tools
> - **Chat tools (6.86–6.88)**: AI agent có thể gọi `send_chat_message`, `get_chat_history`, `list_conversations` qua MCP protocol — bổ sung cho endpoints 19.20–19.23
> - **Tool discovery (6.107)**: Internal tools xuất hiện trong `GET /api/integrations/mcp/tools` và section "MCP Tools Available" (19.56) với `serverName: "Jira Assistant UI"`
> - **RBAC (6.104–6.106)**: Mỗi Internal MCP tool enforce cùng RBAC permissions như UI, tương thích với 19.13–19.14

---

## Liên kết Spec — Per-User Tool Permissions

> **Per-User Tool Permissions (spec `per-user-tool-permissions`)**: Thêm section "🔧 Tool Permissions" vào AI_Chat_Sidebar (bên dưới "MCP Tools Available" 19.56) cho phép user enable/disable tools cá nhân. Ảnh hưởng đến AI Chat Sidebar:
> - **Tool Permissions UI**: Section mới trong sidebar với toggle switches per-tool (ON = enabled, OFF = disabled), grouped theo server, nút Enable All / Disable All, counter "X / Y enabled"
> - **System prompt filter**: Tools disabled bởi user sẽ không xuất hiện trong system prompt injection — AI không biết tool đó tồn tại
> - **Per-user storage**: Tool permissions lưu per-user trong bảng `user_tool_permissions`
> - **Đồng bộ**: Thay đổi permissions ở Integrations page hoặc Chat sidebar đều đọc/ghi cùng bảng `user_tool_permissions`
