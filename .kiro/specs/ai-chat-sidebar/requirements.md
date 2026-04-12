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

**19.38** THE AI Personalization panel SHALL cho phép user cấu hình các mục sau, mỗi mục là textarea có thể chỉnh sửa:
- **Skills**: Danh sách kỹ năng/chuyên môn của user (ví dụ: "Backend Java developer", "Scrum Master", "QA Engineer") — AI sẽ điều chỉnh ngôn ngữ và mức độ chi tiết phù hợp
- **Workflow**: Quy trình làm việc ưa thích (ví dụ: "Tôi review PR trước khi merge", "Sprint 2 tuần") — AI sẽ đề xuất hành động phù hợp workflow
- **Instructions**: Hướng dẫn cụ thể cho AI (ví dụ: "Luôn trả lời bằng tiếng Việt", "Ưu tiên giải pháp đơn giản", "Không đề xuất thay đổi config") — inject vào system prompt
- **Rules**: Quy tắc bắt buộc (ví dụ: "Không bao giờ xóa dữ liệu", "Luôn hỏi xác nhận trước khi thay đổi config") — inject vào system prompt như constraints

**19.39** THE Backend_Server SHALL lưu trữ AI personalization config per-user trong bảng `user_ai_config` (SQLDelight), với cấu trúc: `user_id TEXT PK, skills TEXT, workflow TEXT, instructions TEXT, rules TEXT, updated_at TEXT`.

**19.40** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/config` trả về AI personalization config của user hiện tại, và `PUT /api/chat/config` để cập nhật.

**19.41** WHEN ChatService xây dựng system prompt, THE ChatService SHALL inject user's skills, workflow, instructions, và rules vào prompt context, sau system prompt chung và trước KB/graph context.

**19.42** WHEN user chưa cấu hình personalization (config trống), THE ChatService SHALL sử dụng default system prompt không có personalization — không ảnh hưởng đến chức năng chat cơ bản.

**19.43** THE Frontend_App SHALL hiển thị preview ngắn gọn của personalization config trên header AI_Chat_Sidebar (ví dụ: "⚙️ 3 skills, 2 rules") để user biết config đang active.

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

**Tổng: 60 tiêu chí chấp nhận**


### Model-Aware Chat UI

**19.53** THE Frontend_App SHALL hiển thị tên model AI đang active (ví dụ: "gemma4:e2b", "gemini-1.5-pro") trên header hoặc footer của AI_Chat_Sidebar, lấy từ `GET /api/chat/model-info`.

**19.54** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/model-info` trả về thông tin model đang active: `{ modelName, provider, supportsVision, supportsTools, maxTokens }`.

**19.55** IF model đang active hỗ trợ vision (`supportsVision = true`), THE Frontend_App SHALL enable nút đính kèm hình ảnh (📎), cho phép paste hình từ clipboard (Ctrl+V) và upload hình từ đĩa. IF `supportsVision = false`, THE Frontend_App SHALL disable/ẩn các tính năng hình ảnh và hiển thị tooltip "Model hiện tại không hỗ trợ xử lý hình ảnh".

**19.56** IF model đang active hỗ trợ tool use (`supportsTools = true`), THE Frontend_App SHALL hiển thị icon 🔧 (tools) trong input area. Click vào icon SHALL mở dropdown danh sách tools available (từ MCP servers + built-in tools).

**19.57** WHEN người dùng chọn một tool từ dropdown, THE Frontend_App SHALL chèn `@tên_tool` vào vị trí cursor trong textarea. Ví dụ: chọn "aws-docs" → chèn `@aws-docs` vào ô chat.

**19.58** THE Frontend_App SHALL hỗ trợ autocomplete khi user gõ `@` trong textarea — hiển thị dropdown filtered danh sách tools matching text sau `@`. Ví dụ: gõ `@aws` → hiển thị `@aws-docs`, `@aws-cli`.

**19.59** WHEN tin nhắn chứa `@tên_tool`, THE ChatService SHALL route phần liên quan đến tool đó qua MCP server tương ứng, kết hợp kết quả tool với AI response.

**19.60** THE Backend_Server SHALL cung cấp endpoint `GET /api/chat/tools` trả về danh sách tools available: `[{ toolName, serverName, description, inputSchema }]`, tổng hợp từ tất cả MCP servers đang ACTIVE.
