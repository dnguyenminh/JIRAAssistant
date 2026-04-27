# AI Chat Sidebar — Tasks

Status: 🔄 In Progress (Tasks 73–124 completed, Tasks 125–128 remaining)

# AI Chat Sidebar — Implementation Plan

Panel chat AI docked bên phải trong Shell layout, resizable (280–600px), toggle từ Navbar header 💬. Per-user persistent history, KB+Graph context, OllamaAgent direct injection, action system. Textarea multiline (Shift+Enter xuống dòng). Multi-conversation management, AI personalization (table-based), model-aware chat UI, MCP tools section, Local KB MCP Tool.

Thay đổi chính so với thiết kế ban đầu:
- Panel docked bên phải (không phải fixed overlay bên trái) — tránh che Navigation Sidebar
- Toggle button trên Navbar header (không phải fixed bottom-left)
- Resize handle ở cạnh trái panel (drag to resize)
- ChatServiceImpl inject OllamaAgent trực tiếp từ DB config (không qua Koin `get<AIAgent>()`)
- DB cũ cần xóa khi thêm bảng `chat_messages` (chưa có migration tự động)

## Task 73: Backend — SQLDelight Schema cho AI Chat
- [x] 73.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `chat_messages`
    - `CREATE TABLE chat_messages (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, role TEXT NOT NULL, message TEXT NOT NULL, context TEXT, timestamp TEXT NOT NULL);`
    - `CREATE INDEX idx_chat_messages_user_timestamp ON chat_messages(user_id, timestamp ASC);`
    - Queries: insertChatMessage, getChatHistory (LIMIT/OFFSET), getChatHistoryCount, deleteChatHistory, getUserMessages (role='user'), lastInsertRowId
    - _Requirements: AC 19.16, [backend-core] AC 9.1_

- [x] 73.2 Tạo `shared/.../chat/ChatMessage.kt` — `@Serializable data class ChatMessage(id, userId, role, message, context, timestamp)`
    - _Requirements: AC 19.16_

- [x] 73.3 Tạo shared DTOs: `ChatRequest`, `ChatResponse`, `ChatContext`, `ChatAction`, `ChatReference`, `ChatActionRequest`, `ChatActionResponse`, `ChatHistoryResponse`
    - `ChatRequest(message, context?)` — tin nhắn gửi từ frontend
    - `ChatResponse(reply, actions, references)` — phản hồi AI
    - `ChatContext(projectKey, currentScreen, userRole, userId)` — ngữ cảnh hiện tại
    - `ChatAction(type, label, params)` — hành động đề xuất (navigate/changeConfig/triggerAnalysis)
    - `ChatReference(type, id, label)` — tham chiếu ticket/màn hình
    - `ChatActionRequest(actionType, parameters)` — yêu cầu thực hiện action
    - `ChatActionResponse(success, details)` — kết quả thực hiện action
    - `ChatHistoryResponse(messages, total, page, size)` — phân trang lịch sử
    - _Requirements: AC 19.5, AC 19.8, AC 19.12, AC 19.20, AC 19.21, AC 19.22_

## Task 74: Backend — ChatRepository Interface & Implementation
- [x] 74.1 Tạo `ChatRepository.kt` interface:
    - `saveMessage(userId, role, message, context?): Long`
    - `getHistory(userId, page, size): List<ChatMessage>`
    - `getHistoryCount(userId): Long`
    - `deleteHistory(userId): Boolean`
    - `getUserMessageList(userId): List<String>` — cho command history navigation
    - _Requirements: AC 19.16, AC 19.17, AC 19.23_

- [x] 74.2 Tạo `ChatRepositoryImpl.kt` — JVM SQLDelight triển khai
    - _Requirements: AC 19.16, AC 19.22, AC 19.23_

## Task 75: Backend — ChatService Interface & Implementation
- [x] 75.1 Tạo `ChatService.kt` interface:
    - `processChat(message, context, conversationHistory): ChatResponse`
    - `buildSystemPrompt(context): String`
    - _Requirements: AC 19.5, AC 19.6, AC 19.9, AC 19.10_

- [x] 75.2 Tạo `ChatServiceImpl.kt` — dependencies: AIOrchestrator, KBRepository, FeatureNetworkMapper, GraphEngine
    - `processChat()`: systemPrompt + kbContext + graphContext + history (last 20) + userMessage → AI → parseResponse
    - `buildSystemPrompt()`: project key, màn hình, vai trò, quy tắc JSON format
    - `buildKBContext()`: extract ticket IDs (regex), query KB → format context
    - `buildGraphContext()`: query graph → summary (nodes, edges, clusters)
    - `parseAIResponse()`: try JSON decode → ChatResponse, fallback plain text
    - _Requirements: AC 19.5, AC 19.6, AC 19.9, AC 19.10, AC 19.11_

## Task 76: Backend — ChatRoutes & Koin Registration
- [x] 76.1 Tạo `ChatRoutes.kt` — 4 endpoints:
    - `POST /api/chat/send` — JWT (Reader+). Save user message, load history, processChat, save reply, respond
    - `POST /api/chat/execute-action` — JWT. RBAC per actionType: changeConfig→Administrator, triggerAnalysis→Neural_Architect+, navigate→Reader+
    - `GET /api/chat/history` — JWT (Reader+). Phân trang (page, size)
    - `DELETE /api/chat/history` — JWT (Reader+). Xóa lịch sử user hiện tại
    - _Requirements: AC 19.20, AC 19.21, AC 19.22, AC 19.23, AC 19.13, AC 19.14_

- [x] 76.2 Cập nhật `ServerModule.kt`: đăng ký ChatRepository, ChatService
    - _Requirements: [backend-core] AC 8.1_

- [x] 76.3 Cập nhật `Routing.kt`: thêm chatRoutes()
    - _Requirements: AC 19.20_

## Task 77: Checkpoint — Backend AI Chat Sidebar
- [x] 77. Schema, ChatRepository CRUD, ChatService prompt, 4 endpoints, RBAC, phân trang

## Task 78: Frontend — HTML Template cho AI Chat Sidebar
- [x] 78.1 Tạo `ai-chat-sidebar.html`:
    - Panel container (`.ai-chat-sidebar#ai-chat-sidebar`, mặc định hidden)
    - Header: "AI Assistant" + nút đóng
    - Message area (`.chat-messages#chat-messages`, cuộn được)
    - Typing indicator (`.typing-indicator#typing-indicator`, hidden)
    - Input area: textarea `#chat-input` + nút gửi `#btn-send-chat`
    - Error banner (`#chat-error-banner`, hidden)
    - _Requirements: AC 19.1, AC 19.2, AC 19.3, AC 19.7, AC 19.18_

## Task 79: Frontend — CSS Styles cho AI Chat Sidebar
- [x] 79.1 Cập nhật `components.css`:
    - `.ai-chat-sidebar` — panel docked bên phải, glassmorphism, width 380px (min 280, max 600)
    - `.ai-chat-sidebar.open` — display: flex (thay vì transform)
    - `.chat-resize-handle` — drag handle ở cạnh trái, cursor col-resize, highlight khi hover/drag
    - `.chat-messages` — flex column, overflow-y auto
    - `.chat-message.user` / `.chat-message.assistant` — message bubbles
    - `.chat-input` — textarea multiline (rows=3), resize vertical, min-height 60px, max-height 160px
    - `.typing-indicator` — animated 3 dots
    - `.chat-action-btn` — nút hành động, neon accent
    - `.chat-error-banner` — error styling
    - `.nav-chat-btn` — toggle button inline trên Navbar header (36x36px)
    - `.shell-right-area` — flexbox row cho MainContent + ChatPanel
    - _Requirements: AC 19.1, AC 19.3, AC 19.4, AC 19.4a, [design-system-ux] AC 17.1_

## Task 80: Frontend — AIChatSidebar.kt Controller
- [x] 80.1 Tạo `AIChatSidebar.kt`:
    - `init()`: load template, attach event listeners
    - `toggle()`: add/remove `.open`, loadHistory() lần đầu
    - `loadHistory()`: GET /api/chat/history → render messages, lưu commandHistory cho ↑/↓
    - `sendMessage()`: validate, show user bubble, typing indicator, POST /api/chat/send → render response + actions
    - `renderMessage(role, content)`: DOM element, markdown parsing (bold, italic, code block, lists)
    - `renderActions(actions)`: action buttons, click → executeAction()
    - `executeAction(action)`: POST /api/chat/execute-action, navigate/confirm/403 handling
    - `handleKeyDown(event)`: Enter → send, ↑/↓ → command history navigation
    - `showError(message)`: error banner, nút "Đi đến Integrations" nếu no provider
    - _Requirements: AC 19.1–AC 19.3, AC 19.5, AC 19.7, AC 19.8, AC 19.12–AC 19.15, AC 19.17, AC 19.17a, AC 19.18, AC 19.19_

## Task 81: Frontend — Tích hợp AI Chat Sidebar vào Shell
- [x] 81.1 Cập nhật Shell layout: `shell-right-area` flexbox row chứa MainContent + ChatPanel container. Navbar chứa toggle button 💬
    - _Requirements: AC 19.4_
- [x] 81.2 Cập nhật `Shell.kt`: load template vào container, inject resize handle, setup drag resize (280–600px), init AIChatSidebar controller
    - _Requirements: AC 19.4, AC 19.4a, AC 19.1_
- [x] 81.3 Cập nhật `Navbar.kt`: thêm chat toggle button `.nav-chat-btn` vào `navActions`, gọi `AIChatSidebar.toggle()` on click
    - _Requirements: AC 19.4_

## Task 82: Checkpoint — Frontend AI Chat Sidebar
- [x] 82. Toggle button, slide-in/out, send/receive, markdown, action buttons, command history ↑/↓, load history, error handling, RBAC

## Task 83: E2E Tests — Chat API Endpoints
- [x] 83.1 Tạo `ChatApiTest.kt`:
    - POST /api/chat/send → 200 + ChatResponse
    - POST /api/chat/send lưu vào history
    - POST /api/chat/execute-action navigate → 200
    - POST /api/chat/execute-action changeConfig Reader → 403
    - POST /api/chat/execute-action changeConfig Administrator → 200
    - POST /api/chat/execute-action triggerAnalysis Reader → 403
    - GET /api/chat/history → 200 + phân trang
    - GET /api/chat/history empty → messages=[], total=0
    - DELETE /api/chat/history → 200, sau đó GET → empty
    - No JWT → 401
    - _Requirements: AC 19.20, AC 19.21, AC 19.22, AC 19.23, AC 19.13, AC 19.14_

## Task 84: Final Checkpoint — AI Chat Sidebar Feature
- [x] 84. End-to-end, ChatService prompt, per-user history, action system, frontend toggle/send/receive/markdown/actions/command history, error handling, RBAC

## Task 85: UI E2E Tests — AI Chat Sidebar (Cucumber + Serenity)
- [x] 85.1 Tạo `013-AIChatSidebar.feature`:
    - Toggle button hiển thị trên Shell pages, ẩn trên Login/Project Select
    - Click toggle → sidebar mở (slide-in), click close → sidebar đóng (slide-out)
    - Sidebar hiển thị header "AI Assistant", input area, send button
    - Gửi tin nhắn → user bubble + typing indicator + assistant bubble
    - Action buttons render khi AI đề xuất, click navigate → hash thay đổi
    - Error banner hiển thị khi provider lỗi, có link "Đi đến Integrations"
    - Command history ↑/↓ navigation
    - Chat history load khi mở sidebar lần đầu
    - RBAC: Reader bị từ chối changeConfig (403 message)
    - _Requirements: AC 19.1–AC 19.4, AC 19.7, AC 19.8, AC 19.12–AC 19.15, AC 19.17, AC 19.17a, AC 19.18_

- [x] 85.2 Tạo `AIChatSidebarSteps.kt` — Step definitions cho feature file
    - Sử dụng Serenity WebDriver, TestHelper patterns
    - CSS selectors: `#btn-toggle-chat`, `.ai-chat-sidebar.open`, `#chat-input`, `.chat-message.user/.assistant`, `.chat-action-btn`, `#chat-error-banner`
    - Không redefine steps đã có trong CommonSteps.kt
    - _Requirements: AC 19.1–AC 19.4, AC 19.7, AC 19.8, AC 19.12–AC 19.15, AC 19.17, AC 19.17a, AC 19.18_

- [x] 85.3 Tạo `UiAIChatSidebarRunner.kt` — Serenity Cucumber runner
    - _Requirements: AC 19.1_


---

# ═══════════════════════════════════════════════════════════════
# CHAT ENHANCEMENTS: Context Indicator, Voice Input, File Upload
# ═══════════════════════════════════════════════════════════════

## Task 86: Context Window Indicator
- [x] 86.1 Cập nhật `ai-chat-sidebar.html`: thêm SVG circular progress indicator bên dưới nút Send
    - _Requirements: AC 19.24_
- [x] 86.2 Cập nhật `components.css`: styles cho `.context-indicator` (SVG circle, color transitions)
    - _Requirements: AC 19.24, AC 19.25_
- [x] 86.3 Cập nhật `ChatResponse` DTO: thêm `contextUsage: Int` (phần trăm)
    - _Requirements: AC 19.24_
- [x] 86.4 Cập nhật `ChatServiceImpl`: tính toán context usage và trả trong response
    - _Requirements: AC 19.24_
- [x] 86.5 Cập nhật `AIChatSidebar.kt`: render + update circular indicator sau mỗi message
    - _Requirements: AC 19.24, AC 19.25_

## Task 87: Voice Input (Speech-to-Text)
- [x] 87.1 Cập nhật `ai-chat-sidebar.html`: thêm nút 🎤 microphone trong input area
    - _Requirements: AC 19.26_
- [x] 87.2 Cập nhật `components.css`: styles cho `.voice-btn`, `.voice-btn.recording` (pulse animation)
    - _Requirements: AC 19.28_
- [x] 87.3 Tạo `frontend/.../chat/VoiceInput.kt`: Web Speech API integration
    - `startRecording()`: SpeechRecognition init, onresult → append to textarea
    - `stopRecording()`: stop recognition
    - `isSupported()`: check browser support, ẩn nút nếu không hỗ trợ
    - _Requirements: AC 19.27, AC 19.28, AC 19.29_

## Task 88: File Upload — Backend
- [x] 88.1 Tạo `server/.../routes/ChatUploadRoutes.kt`: `POST /api/chat/upload` (multipart, max 10MB)
    - Lưu file vào `data/chat-uploads/{userId}/{timestamp}-{filename}`
    - Validate MIME type (image/*, pdf, word, excel)
    - Extract text: PDF (PDFBox), Word (POI), Excel (POI)
    - Response: `{ fileId, fileName, fileType, fileUrl, textContent }`
    - _Requirements: AC 19.33_
- [x] 88.2 Thêm dependencies: Apache PDFBox, Apache POI vào `server/build.gradle.kts`
    - _Requirements: AC 19.33, AC 19.34_
- [x] 88.3 Cập nhật `ChatRequest` DTO: thêm `attachments: List<ChatAttachment>`
    - _Requirements: AC 19.34_
- [x] 88.4 Cập nhật `ChatServiceImpl`: inject extracted text từ attachments vào prompt context
    - _Requirements: AC 19.34_

## Task 89: File Upload — Frontend
- [x] 89.1 Cập nhật `ai-chat-sidebar.html`: thêm nút 📎 attach + file preview area
    - _Requirements: AC 19.30_
- [x] 89.2 Cập nhật `components.css`: styles cho `.attach-btn`, `.file-preview`, `.file-thumbnail`
    - _Requirements: AC 19.30, AC 19.32_
- [x] 89.3 Tạo `frontend/.../chat/FileUploader.kt`: file picker, upload, preview
    - `openFilePicker()`: input[type=file] với accept filter
    - `uploadFile(file)`: POST /api/chat/upload (FormData)
    - `renderPreview(attachment)`: thumbnail cho image, icon cho documents
    - _Requirements: AC 19.31, AC 19.32_

## Task 90: Clipboard Paste & Drag-Drop
- [x] 90.1 Tạo `frontend/.../chat/ClipboardHandler.kt`: paste event listener
    - Detect image từ clipboard → upload → preview
    - _Requirements: AC 19.35_
- [x] 90.2 Tạo `frontend/.../chat/DragDropHandler.kt`: drag-drop event listeners
    - Validate MIME type → upload → preview
    - Visual feedback: border highlight khi dragging
    - _Requirements: AC 19.36_

## Task 91: Checkpoint — Chat Enhancements
- [x] 91. Context indicator, voice input, file upload, clipboard paste, drag-drop


## Task 92: Backend — User AI Config Schema & Repository
- [x] 92.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `user_ai_config`
    - _Requirements: AC 19.39_
- [x] 92.2 Tạo `UserAIConfig` data class + `UserAIConfigRepository` interface + impl
    - _Requirements: AC 19.39, AC 19.40_

## Task 93: Backend — AI Config API Routes
- [x] 93.1 Tạo `GET /api/chat/config` và `PUT /api/chat/config` endpoints
    - _Requirements: AC 19.40_
- [x] 93.2 Cập nhật `ChatServiceImpl.buildSystemPrompt()`: inject skills, workflow, instructions, rules
    - _Requirements: AC 19.41, AC 19.42_

## Task 94: Frontend — AI Personalization Panel
- [x] 94.1 Tạo `templates/ai-config-panel.html`: 4 textarea fields + SAVE button
    - _Requirements: AC 19.38_
- [x] 94.2 Tạo `chat/AIConfigPanel.kt`: load/save config, open/close panel
    - _Requirements: AC 19.37, AC 19.38, AC 19.40_
- [x] 94.3 Cập nhật `AIChatSidebar.kt`: thêm ⚙️ button trên header, config preview badge
    - _Requirements: AC 19.37, AC 19.43_

## Task 95: Checkpoint — AI Personalization
- [x] 95. Config panel, per-user persistence, system prompt injection, preview badge


## Task 96: Backend — Multi-Conversation Schema & Repository
- [x] 96.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `chat_conversations`, thêm `conversation_id` vào `chat_messages`
    - _Requirements: AC 19.47_
- [x] 96.2 Tạo `ChatConversation` data class + `ChatConversationRepository` interface + impl
    - _Requirements: AC 19.47, AC 19.48_

## Task 97: Backend — Conversation API Routes
- [x] 97.1 Thêm endpoints: GET/POST/PUT/DELETE `/api/chat/conversations`
    - _Requirements: AC 19.48_
- [x] 97.2 Cập nhật `POST /api/chat/send`: nhận `conversationId`, auto-generate title cho conversation mới
    - _Requirements: AC 19.49_
- [x] 97.3 Cập nhật `GET /api/chat/history`: filter by `conversationId`
    - _Requirements: AC 19.48_

## Task 98: Frontend — Conversation List UI
- [x] 98.1 Cập nhật `ai-chat-sidebar.html`: thêm conversation list area + "New Chat" button
    - _Requirements: AC 19.44, AC 19.45_
- [x] 98.2 Tạo `chat/ConversationList.kt`: load conversations, render list, switch active, rename, delete
    - _Requirements: AC 19.44, AC 19.46, AC 19.50, AC 19.51, AC 19.52_
- [x] 98.3 Cập nhật `AIChatSidebar.kt`: integrate conversation list, track active conversationId
    - _Requirements: AC 19.44, AC 19.46_

## Task 99: Checkpoint — Multi-Conversation
- [x] 99. Create/switch/rename/delete conversations, messages per conversation, auto-title


---

# ═══════════════════════════════════════════════════════════════
# MODEL-AWARE CHAT UI
# ═══════════════════════════════════════════════════════════════

## Task 100a: Backend — Model Info & Tools Endpoints
- [x] 100a.1 Tạo `GET /api/chat/model-info`: trả về `{ modelName, provider, supportsVision, supportsTools, maxTokens }`
    - Đọc active provider config từ DB, detect capabilities từ model name
    - _Requirements: AC 19.54_
- [x] 100a.2 Tạo `GET /api/chat/tools`: trả về danh sách tools từ active MCP servers
    - _Requirements: AC 19.60_

## Task 100b: Frontend — Model Name Display & Capability Detection
- [x] 100b.1 Cập nhật `AIChatSidebar.kt`: load model info on init, hiển thị model name trên header/footer
    - _Requirements: AC 19.53_
- [x] 100b.2 Conditional UI: enable/disable image features dựa trên `supportsVision`
    - _Requirements: AC 19.55_
- [x] 100b.3 Conditional UI: show/hide tools icon 🔧 dựa trên `supportsTools`
    - _Requirements: AC 19.56_

## Task 100c: Frontend — Tool Picker & @mention Autocomplete
- [x] 100c.1 Tạo `chat/ToolPicker.kt`: dropdown danh sách tools, click → insert `@tool_name`
    - _Requirements: AC 19.56, AC 19.57_
- [x] 100c.2 Tạo `chat/ToolAutocomplete.kt`: listen `@` keypress → filtered dropdown
    - _Requirements: AC 19.58_

## Task 100d: Backend — Tool Routing in ChatService
- [x] 100d.1 Cập nhật `ChatServiceImpl`: parse `@tool_name` từ message, route qua MCP server
    - _Requirements: AC 19.59_

## Task 100e: Checkpoint — Model-Aware Chat
- [x] 100e. Model name display, vision toggle, tools picker, @mention autocomplete, tool routing


---

# ═══════════════════════════════════════════════════════════════
# AI PERSONALIZATION TABLE-BASED + MCP TOOLS AVAILABLE SECTION
# ═══════════════════════════════════════════════════════════════

## Task 101: Shared — Chuyển đổi UserAIConfig data model sang structured entries
- [x] 101.1 Cập nhật `shared/src/commonMain/kotlin/com/assistant/chat/UserAIConfig.kt`:
    - Thêm 4 entry data classes: `SkillEntry(name, level, description)`, `WorkflowEntry(step, name, description)`, `InstructionEntry(instruction, priority)`, `RuleEntry(rule, type)`
    - Đổi `UserAIConfig` fields từ `String` sang `List<SkillEntry>`, `List<WorkflowEntry>`, `List<InstructionEntry>`, `List<RuleEntry>`
    - Tất cả `@Serializable`, default `emptyList()`
    - _Requirements: AC 19.38, AC 19.39_

## Task 102: Backend — Cập nhật SQLDelight schema & Repository cho JSON arrays
- [x] 102.1 Cập nhật `shared/src/commonMain/sqldelight/com/assistant/db/KnowledgeBase.sq`:
    - Đổi tên cột: `skills` → `skills_json`, `workflow` → `workflow_json`, `instructions` → `instructions_json`, `rules` → `rules_json`
    - Default values: `'[]'` thay vì `''`
    - Cập nhật queries `findUserAIConfig`, `upsertUserAIConfig` với tên cột mới
    - _Requirements: AC 19.39_

- [x] 102.2 Cập nhật `shared/src/jvmMain/kotlin/com/assistant/chat/UserAIConfigRepositoryImpl.kt`:
    - `findByUserId()`: đọc `skills_json`, `workflow_json`, `instructions_json`, `rules_json` → `Json.decodeFromString<List<SkillEntry>>()` etc.
    - `save()`: serialize `List<SkillEntry>` → JSON string, lưu vào `skills_json` column
    - _Requirements: AC 19.39, AC 19.40_

## Task 103: Backend — Cập nhật ChatServiceImpl.buildPersonalization() cho structured data
- [x] 103.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatServiceImpl.kt`:
    - `buildPersonalization()`: format structured table data thay vì plain strings
    - Skills: `"Skills: Java (Expert), Python (Intermediate)"` — liệt kê kèm level
    - Workflow: `"Workflow: 1. Review PR → 2. Merge → 3. Deploy"` — theo thứ tự step
    - Instructions: `"Instructions: [Cao] Luôn trả lời tiếng Việt; [Trung bình] Dùng code examples"` — theo priority
    - Rules: `"RULES: [Cấm] Không xóa dữ liệu; [Bắt buộc] Validate input"` — theo type
    - _Requirements: AC 19.41, AC 19.42_

## Task 104: Backend — Cập nhật ChatConfigRoutes cho new format
- [x] 104.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/routes/ChatConfigRoutes.kt`:
    - `GET /api/chat/config`: trả về `UserAIConfig` với 4 arrays (SkillEntry, WorkflowEntry, InstructionEntry, RuleEntry)
    - `PUT /api/chat/config`: nhận `UserAIConfig` body với 4 arrays, validate, lưu
    - _Requirements: AC 19.40_

## Task 105: Checkpoint — Backend AI Personalization Table Migration
- [x] 105. Schema đổi sang `*_json` columns, Repository serialize/deserialize JSON arrays, ChatService format structured data, API routes handle new format

## Task 106: Frontend — Cập nhật ai-config-panel.html thành editable tables
- [x] 106.1 Cập nhật `frontend/src/jsMain/resources/templates/ai-config-panel.html` (hoặc inline HTML trong AIConfigPanel):
    - Thay 4 textareas bằng 4 editable tables:
      - Skills Table: cột `Tên kỹ năng` (text), `Mức độ` (select: Beginner/Intermediate/Expert), `Mô tả` (text), `🗑️` (delete)
      - Workflow Table: cột `Bước` (number, auto), `Tên quy trình` (text), `Mô tả` (text), `🗑️`
      - Instructions Table: cột `Hướng dẫn` (text), `Độ ưu tiên` (select: Cao/Trung bình/Thấp), `🗑️`
      - Rules Table: cột `Quy tắc` (text), `Loại` (select: Cấm/Bắt buộc/Khuyến nghị), `🗑️`
    - Mỗi bảng có nút `➕ Thêm dòng`
    - Nút SAVE ở cuối
    - _Requirements: AC 19.38, AC 19.38a_

## Task 107: Frontend — Cập nhật AIConfigPanel.kt cho editable tables
- [x] 107.1 Cập nhật `frontend/src/jsMain/kotlin/com/assistant/frontend/components/chat/AIConfigPanel.kt`:
    - `buildPanelHtml()`: render 4 tables thay vì 4 textareas
    - `loadConfig()`: decode `UserAIConfig` với List fields, populate table rows
    - `saveConfig()`: collect data từ table rows → build `UserAIConfig` với List fields → PUT
    - `addRow(tableId)`: thêm dòng trống mới vào bảng (inline editable inputs)
    - `deleteRow(row)`: confirm dialog → xóa dòng khỏi DOM
    - Inline edit: click ô → chuyển sang input/select, blur/Enter → lưu
    - _Requirements: AC 19.38, AC 19.38a, AC 19.38b, AC 19.38c, AC 19.40_

- [x] 107.2 Cập nhật `AIChatSidebar.kt` (`frontend/src/jsMain/kotlin/com/assistant/frontend/components/AIChatSidebar.kt`):
    - `updateBadge()` / `AIConfigPanel.updateBadge()`: đếm số dòng từ mỗi bảng (skills.size, rules.size, etc.)
    - Badge format: `"⚙️ 3 skills, 2 rules"` thay vì đếm fields non-blank
    - _Requirements: AC 19.43_

## Task 108: Frontend — CSS styles cho editable tables trong config panel
- [x] 108.1 Cập nhật `frontend/src/jsMain/resources/styles/components.css`:
    - `.config-table`: border-collapse, dark theme, glass border
    - `.config-table th`: header row, uppercase, small font, opacity
    - `.config-table td`: padding, border-bottom, editable cells
    - `.config-table input`, `.config-table select`: dark background override, inline edit styling
    - `.config-table .btn-delete-row`: 🗑️ button, danger color on hover
    - `.config-table .btn-add-row`: ➕ button, primary accent
    - Confirm dialog styles cho delete row
    - _Requirements: AC 19.38, AC 19.38c_

## Task 109: Checkpoint — Frontend AI Personalization Tables
- [x] 109. 4 editable tables render đúng, add row, inline edit, delete row với confirm, save/load JSON arrays, badge hiển thị counts

## Task 110: Frontend — MCP Tools Available collapsible section
- [x] 110.1 Cập nhật `frontend/src/jsMain/resources/templates/ai-chat-sidebar.html`:
    - Thêm collapsible section `#mcp-tools-section` phía trên input area (sau typing indicator, trước file preview)
    - Header: `▸ MCP Tools Available (N)` — click toggle collapse/expand
    - Body: `#mcp-tools-list` — danh sách tools grouped by server
    - Empty state: `"Không có MCP tools khả dụng"`
    - _Requirements: AC 19.56, AC 19.56a, AC 19.56b_

- [x] 110.2 Tạo `frontend/src/jsMain/kotlin/com/assistant/frontend/components/chat/McpToolsSection.kt`:
    - `init(inputEl)`: cache elements, bind toggle collapse
    - `load()`: gọi `GET /api/chat/tools` → render danh sách grouped by serverName
    - `renderTools(tools)`: group by serverName, mỗi group có header server name, mỗi tool hiển thị tên + mô tả
    - `renderEmptyState()`: hiển thị "Không có MCP tools khả dụng"
    - Click tool → chèn `@tool_name` vào textarea tại vị trí cursor
    - Cập nhật count trên header `(N)`
    - _Requirements: AC 19.56, AC 19.56a, AC 19.56b, AC 19.57_

- [x] 110.3 Cập nhật `frontend/src/jsMain/kotlin/com/assistant/frontend/components/AIChatSidebar.kt`:
    - `initSubComponents()`: thêm `McpToolsSection.init(inputEl)`
    - `loadInitialData()`: thêm `McpToolsSection.load()`
    - _Requirements: AC 19.56a_

## Task 111: Frontend — CSS styles cho MCP Tools Available section
- [x] 111.1 Cập nhật `frontend/src/jsMain/resources/styles/components.css`:
    - `.mcp-tools-section`: collapsible container, border-top, flex-shrink 0
    - `.mcp-tools-header`: click toggle, cursor pointer, flex row, gap, small font, uppercase
    - `.mcp-tools-header .toggle-arrow`: rotate animation khi expand/collapse
    - `.mcp-tools-body`: max-height transition, overflow hidden khi collapsed
    - `.mcp-tools-body.expanded`: max-height auto, overflow-y auto
    - `.mcp-tool-group-header`: server name, opacity 0.5, small font
    - `.mcp-tool-item`: click cursor, hover highlight primary, padding, font-size 12px
    - `.mcp-tool-item .tool-name`: primary color, monospace
    - `.mcp-tool-item .tool-desc`: opacity 0.6
    - `.mcp-tools-empty`: empty state text, opacity 0.4, text-align center
    - _Requirements: AC 19.56, AC 19.56b_

## Task 112: Final Checkpoint — AI Personalization Tables + MCP Tools Section
- [x] 112. Editable tables (add/edit/delete rows), structured JSON save/load, buildPersonalization format structured data, MCP Tools collapsible section (auto-load, grouped by server, click insert @tool, empty state), badge counts


---

# ═══════════════════════════════════════════════════════════════
# LOCAL KNOWLEDGE BASE MCP TOOL
# ═══════════════════════════════════════════════════════════════

## Task 113: Backend — Tạo LocalKBToolExecutor
- [x] 113.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/chat/LocalKBToolExecutor.kt`:
    - Class nhận dependencies: `EmbeddingService`, `VectorStore`, `KBRepository`
    - Companion object: `SERVER_ID = "local-knowledge-base"`, `DEFAULT_TOP_K = 10`
    - `execute(toolName, arguments): String` — route đến operation tương ứng
    - `searchKnowledge(args)`: đọc `query` (bắt buộc), `chunkType` (tùy chọn), `topK` (tùy chọn, default 10). Gọi `embeddingService.embed(query)` → `vectorStore.search(embedding, topK, chunkType)` → format kết quả theo section groups (RELEVANT TICKETS, RELATIONSHIPS, ANALYSIS, CONFLUENCE DOCS, ATTACHMENTS)
    - `getTicketInfo(args)`: đọc `ticketId` (bắt buộc). Gọi `kbRepository.findByTicketId(ticketId)` → format KBRecord (ticketId, requirementSummary, scrumPoints, confidenceScore, rationale, evolutionHistory, similarTicketRefs). Nếu null → "Ticket không tìm thấy trong Knowledge Base."
    - `searchRelationships(args)`: đọc `query` (bắt buộc). Gọi `embeddingService.embed(query)` → `vectorStore.search(embedding, 10, "RELATIONSHIP")` → format kết quả
    - Error handling: missing params → "Tool error: missing 'X'", EmbeddingService null → "Tool error: EmbeddingService unavailable", unknown tool → "Tool error: Unknown tool 'X'"
    - `formatKnowledgeChunks()` dùng cùng logic nhóm theo chunkType section với `ChatServiceImpl.formatKnowledgeChunks()`
    - _Requirements: AC 19.64, AC 19.65, AC 19.66, AC 19.67, AC 19.69, AC 19.74_

- [x] 113.2 Viết unit tests cho `LocalKBToolExecutor`:
    - `searchKnowledge` trả về kết quả grouped by section
    - `getTicketInfo` trả về formatted KBRecord
    - `getTicketInfo` ticket không tồn tại → "Ticket không tìm thấy trong Knowledge Base."
    - `searchRelationships` gọi VectorStore với chunkType = "RELATIONSHIP"
    - Missing required params → error message
    - EmbeddingService unavailable → error message
    - Unknown toolName → error message
    - _Requirements: AC 19.64, AC 19.65, AC 19.66, AC 19.69_

## Task 114: Backend — Cập nhật McpAgenticLoop routing logic
- [x] 114.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`:
    - Thêm parameter `localKBExecutor: LocalKBToolExecutor?` vào cả 2 overload `execute()`
    - Cập nhật `executeTool()`: kiểm tra `req.serverId == LocalKBToolExecutor.SERVER_ID` → nếu đúng VÀ `localKBExecutor != null`, gọi `localKBExecutor.execute(req.toolName, req.arguments)` trực tiếp in-process. Nếu không, tiếp tục external MCP flow hiện tại
    - Wrap local execution trong try-catch → "Tool error: {message}" nếu exception
    - Kết quả tool inject vào prompt theo cùng format `--- TOOL RESULT [tool_name] ---`
    - Tuân thủ MAX_ROUNDS = 5 cho cả local và external tool calls
    - _Requirements: AC 19.67, AC 19.68, AC 19.69, AC 19.70_

- [x] 114.2 Viết unit tests cho McpAgenticLoop routing:
    - serverId == "local-knowledge-base" → route đến LocalKBToolExecutor, KHÔNG gọi McpProcessManager.getClient()
    - serverId khác → route đến McpProcessManager như cũ
    - Local tool exception → graceful error message, loop tiếp tục
    - _Requirements: AC 19.67, AC 19.70_

## Task 115: Backend — Cập nhật ChatServiceImpl cho Local KB Tool
- [x] 115.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatServiceImpl.kt`:
    - Thêm constructor parameter: `settingsRepository: SettingsRepository? = null`
    - Thêm `isLocalKBToolEnabled()`: đọc `settingsRepository.get("local_kb_tool_enabled")`, return `value != "false"` (default enabled)
    - Cập nhật `buildMcpToolsContext()`: gọi `buildLocalKBToolsContext()` để thêm 3 tool descriptions khi enabled, gọi `buildLocalKBPriorityHint()` để thêm hướng dẫn ưu tiên
    - `buildLocalKBToolsContext()`: nếu enabled, trả về 3 dòng mô tả tools (`search_knowledge`, `get_ticket_info`, `search_relationships`) với params và use case. Nếu disabled, trả về emptyList()
    - `buildLocalKBPriorityHint()`: nếu enabled, trả về text hướng dẫn ưu tiên "BẮT BUỘC sử dụng local knowledge_base tools... TRƯỚC KHI gọi external Jira/Atlassian MCP tools...". Nếu disabled, trả về empty string
    - Cập nhật `processChat()`: truyền `localKBToolExecutor` vào `McpAgenticLoop.execute()`
    - _Requirements: AC 19.61, AC 19.71, AC 19.72, AC 19.73, AC 19.76_

- [x] 115.2 Viết property tests cho ChatServiceImpl Local KB:
    - **Property 1: Tool registration reflects enabled state** — khi enabled, `buildMcpToolsContext()` chứa 3 tool descriptions + priority guidance. Khi disabled, không chứa
    - **Validates: Requirements 19.61, 19.71, 19.76**
    - **Property 6: buildKnowledgeContext independence** — `buildKnowledgeContext()` output không thay đổi bất kể `local_kb_tool_enabled` state
    - **Validates: Requirements 19.73**

## Task 116: Backend — Cập nhật ChatConfigRoutes handleGetTools()
- [x] 116.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/routes/ChatConfigRoutes.kt`:
    - Cập nhật `handleGetTools()`: inject `SettingsRepository` từ Koin
    - Đọc `settingsRepo.get("local_kb_tool_enabled")` — nếu `!= "false"`, thêm 3 `ToolInfo` entries vào response:
      - `ToolInfo("search_knowledge", "local-knowledge-base", "Tìm kiếm semantic trong Knowledge Base cục bộ")`
      - `ToolInfo("get_ticket_info", "local-knowledge-base", "Tra cứu thông tin phân tích ticket từ KB")`
      - `ToolInfo("search_relationships", "local-knowledge-base", "Tìm kiếm mối quan hệ/dependency giữa tickets")`
    - _Requirements: AC 19.62, AC 19.63_

- [x] 116.2 Viết unit tests cho handleGetTools():
    - Khi enabled → response chứa 3 local KB ToolInfo entries với serverName "local-knowledge-base"
    - Khi disabled → response KHÔNG chứa local KB tools
    - Local KB tools + external MCP tools cùng trả về trong 1 response
    - _Requirements: AC 19.62_

## Task 117: Backend — Cập nhật ServerModule DI registration
- [x] 117.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/di/ServerModule.kt`:
    - Đăng ký `LocalKBToolExecutor` singleton: `single { LocalKBToolExecutor(embeddingService = get(), vectorStore = get(), kbRepository = get()) }`
    - Cập nhật `ChatServiceImpl` constructor: thêm `settingsRepository = get<SettingsRepository>()`
    - _Requirements: AC 19.67_

## Task 118: Checkpoint — Backend Local KB MCP Tool
- [x] 118. LocalKBToolExecutor 3 operations (search_knowledge, get_ticket_info, search_relationships), McpAgenticLoop routing by serverId, ChatServiceImpl tool registration + priority guidance + enabled/disabled toggle, handleGetTools() include local KB tools, ServerModule DI wiring. Ensure all tests pass, ask the user if questions arise.

## Task 119: Frontend — Local KB Tool Card trên trang Integrations
- [x] 119.1 Tạo `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/LocalKBCard.kt`:
    - `render(container)`: tạo card HTML cho Local Knowledge Base tool
    - Card hiển thị: server name "Local Knowledge Base", type badge "local", mô tả "Knowledge Base cục bộ — tìm kiếm dữ liệu đã vectorize từ Jira tickets, attachments, Confluence pages"
    - KHÔNG hiển thị command/args/env fields (tool chạy in-process)
    - KHÔNG hiển thị nút TEST/CONFIGURE/REMOVE
    - Toggle ON/OFF: switch element, gọi `PUT /api/settings` body `{ "key": "local_kb_tool_enabled", "value": "true"|"false" }`
    - Status dot: xanh khi enabled, xám khi disabled
    - `loadState()`: gọi `GET /api/settings` → đọc key `local_kb_tool_enabled` → set toggle state
    - _Requirements: AC 19.75, AC 19.76_

- [x] 119.2 Cập nhật `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/McpServerCards.kt`:
    - Trong `render()`: gọi `LocalKBCard.render(grid)` để chèn Local KB card vào đầu MCP grid (trước các external MCP server cards)
    - _Requirements: AC 19.75_

- [x] 119.3 Cập nhật `frontend/src/jsMain/resources/styles/components.css`:
    - `.local-kb-card`: styling cho card Local KB (phân biệt với external MCP cards)
    - `.local-kb-toggle`: switch toggle ON/OFF styling
    - `.local-kb-type-badge`: badge "local" với màu khác biệt (so với "stdio"/"http")
    - _Requirements: AC 19.75_

## Task 120: Final Checkpoint — Local Knowledge Base MCP Tool
- [x] 120. End-to-end: LocalKBToolExecutor 3 operations hoạt động đúng, McpAgenticLoop routing local vs external, ChatServiceImpl tool registration + priority guidance + enabled/disabled, handleGetTools() trả về local KB tools, ServerModule DI, Integrations page Local KB card với toggle ON/OFF persist vào app_settings. Ensure all tests pass, ask the user if questions arise.


---

# ═══════════════════════════════════════════════════════════════
# FOCUS TICKET PARSING FIX (AC 19.77–19.86)
# ═══════════════════════════════════════════════════════════════

## Task 121: Backend — Sửa buildGraphStateContext() prompt cho focused node
- [x] 121.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatServiceImpl.kt`:
    - Trong `buildGraphStateContext()`, thay dòng `gc.focusedNodeKey?.let { parts.add("Focused on node: $it") }` bằng prompt mới rõ ràng hơn:
      ```kotlin
      gc.focusedNodeKey?.let { key ->
          parts.add(
              "The user is currently focused on Jira ticket \"$key\". " +
              "\"$key\" is the COMPLETE Jira issue key (project prefix + number). " +
              "Use this EXACT value \"$key\" as the issueId/issue key when calling any Jira tools. " +
              "Do NOT split, truncate, or extract only the project prefix from this key."
          )
      }
      ```
    - KHÔNG thay đổi bất kỳ phần nào khác của method (null check, filters, cluster, search, visible nodes, depth)
    - KHÔNG thay đổi bất kỳ method nào khác trong ChatServiceImpl
    - _Requirements: AC 19.80, AC 19.81, AC 19.82_

## Task 122: Backend — Unit tests cho buildGraphStateContext() fix
- [x] 122.1 Tạo hoặc cập nhật unit test file cho `ChatServiceImpl.buildGraphStateContext()`:
    - Test: `buildGraphStateContext(null)` → trả về "User is NOT on the Knowledge Graph page." (regression, Req: 19.83)
    - Test: `buildGraphStateContext(GraphChatContext(focusedNodeKey = "ICL2-400"))` → output chứa "ICL2-400" và "EXACT" và "Do NOT split" (Req: 19.80)
    - Test: `buildGraphStateContext(GraphChatContext(focusedNodeKey = "ITCM-129"))` → output chứa "ITCM-129" nguyên vẹn (Req: 19.81)
    - Test: `buildGraphStateContext(GraphChatContext(focusedNodeKey = null, activeTypeFilters = listOf("Bug", "Story")))` → output chứa "Bug", "Story", KHÔNG chứa "EXACT" hay focused instruction (Req: 19.84, 19.85)
    - Test: `buildGraphStateContext(GraphChatContext(focusedNodeKey = null, searchQuery = "test query"))` → output chứa "test query" (Req: 19.85)
    - _Requirements: AC 19.80, AC 19.81, AC 19.82, AC 19.83, AC 19.84, AC 19.85_

## Task 123: Backend — Property tests cho buildGraphStateContext() fix
- [x] 123.1 Tạo property test file `server/src/jvmTest/kotlin/com/assistant/server/chat/BuildGraphStateContextPropertyTest.kt`:
    - **Property 7: Focused node prompt contains exact key with explicit instruction**
      - Generator: random Jira ticket IDs matching `[A-Z]{2,5}-[0-9]{1,5}` (ví dụ: AB-1, ABCDE-99999)
      - Tạo `GraphChatContext(focusedNodeKey = generatedKey)`
      - Assert: output chứa `generatedKey` ít nhất 1 lần, chứa "EXACT", chứa "Do NOT split"
      - 100+ iterations
      - Tag: `Feature: ai-chat-sidebar, Property 7: Focused node prompt contains exact key with explicit instruction`
      - _Requirements: AC 19.80, AC 19.81, AC 19.82_
    - **Property 8: No-focus context preserves other graph state without focused node instruction**
      - Generator: random `GraphChatContext` với `focusedNodeKey = null`, random `activeTypeFilters` (0–5 items), random `selectedClusterId` (nullable), random `searchQuery`, random `visibleNodeCount`, random `depthValue`
      - Assert: output KHÔNG chứa "EXACT", KHÔNG chứa "Do NOT split", KHÔNG chứa "focused on Jira ticket"
      - Assert: nếu `activeTypeFilters` non-empty → output chứa mỗi filter value; nếu `searchQuery` non-blank → output chứa search query; nếu `selectedClusterId` non-null → output chứa cluster id
      - 100+ iterations
      - Tag: `Feature: ai-chat-sidebar, Property 8: No-focus context preserves other graph state`
      - _Requirements: AC 19.84, AC 19.85_

## Task 124: Checkpoint — Focus Ticket Parsing Fix
- [x] 124. buildGraphStateContext() prompt cải thiện, unit tests pass, property tests pass (100+ iterations), regression tests pass (null context, no focus, filters/search/cluster vẫn hoạt động). Ensure all existing tests still pass.


---

# ═══════════════════════════════════════════════════════════════
# AI PERSONALIZATION POPUP FORMS (AC 19.38a–19.38i)
# ═══════════════════════════════════════════════════════════════
# Hiện tại Tasks 106-107 triển khai inline table editing.
# Requirements 19.38a-19.38i yêu cầu popup modal forms (modal dialog)
# cho thêm mới và chỉnh sửa entries. Bảng tóm tắt (summary table)
# hiển thị read-only, click dòng → mở popup edit, nút ➕ → mở popup add.

## Task 125: Frontend — Popup Modal Form Infrastructure & Skill/Workflow Forms
- [ ] 125.1 Tạo `frontend/src/jsMain/kotlin/com/assistant/frontend/components/chat/AIConfigPopupForm.kt`:
    - `showModal(title, formHtml, onSave, onCancel)`: render modal dialog overlay (backdrop mờ, centered form, min-width 500px / 90vw mobile)
    - `closeModal()`: remove modal từ DOM
    - Backdrop click hoặc Escape → đóng modal (tương đương Cancel)
    - Validation: khi Save, kiểm tra required fields → viền đỏ + "Trường này là bắt buộc" nếu trống, KHÔNG đóng modal cho đến khi hợp lệ
    - _Requirements: AC 19.38a, AC 19.38b, AC 19.38h, AC 19.38i_

- [ ] 125.2 Tạo Skill Popup Form:
    - `showSkillForm(existingEntry?)`: modal chứa fields: `Tên kỹ năng` (TEXT, bắt buộc), `Mức độ` (SELECT: Beginner/Intermediate/Expert, bắt buộc), `Mô tả` (TEXTAREA, optional — nhiều dòng)
    - Nếu `existingEntry` != null → pre-fill fields (edit mode). Nếu null → form trống (add mode)
    - Save → trả về `SkillEntry`, đóng modal. Cancel → đóng modal, không thay đổi
    - _Requirements: AC 19.38d_

- [ ] 125.3 Tạo Workflow Popup Form:
    - `showWorkflowForm(existingEntry?, nextStep?)`: modal chứa fields: `Bước` (NUMBER, auto-increment khi add, editable khi edit), `Tên quy trình` (TEXT, bắt buộc), `Mô tả chi tiết` (TEXTAREA, bắt buộc — nhiều dòng)
    - _Requirements: AC 19.38e_

## Task 126: Frontend — Instruction/Rule Popup Forms & Summary Table Refactor
- [ ] 126.1 Tạo Instruction Popup Form:
    - `showInstructionForm(existingEntry?)`: modal chứa fields: `Hướng dẫn` (TEXTAREA, bắt buộc — nhiều dòng), `Độ ưu tiên` (SELECT: Cao/Trung bình/Thấp, bắt buộc)
    - _Requirements: AC 19.38f_

- [ ] 126.2 Tạo Rule Popup Form:
    - `showRuleForm(existingEntry?)`: modal chứa fields: `Quy tắc` (TEXTAREA, bắt buộc — nhiều dòng), `Loại` (SELECT: Cấm/Bắt buộc/Khuyến nghị, bắt buộc)
    - _Requirements: AC 19.38g_

- [ ] 126.3 Cập nhật `AIConfigPanel.kt` — chuyển 4 bảng từ inline editable sang summary tables (read-only):
    - Skills Table: hiển thị cột `Tên kỹ năng`, `Mức độ` (read-only text), nút 🗑️
    - Workflow Table: hiển thị cột `Bước`, `Tên quy trình` (read-only text), nút 🗑️
    - Instructions Table: hiển thị cột `Hướng dẫn` (cắt ngắn), `Độ ưu tiên` (read-only text), nút 🗑️
    - Rules Table: hiển thị cột `Quy tắc` (cắt ngắn), `Loại` (read-only text), nút 🗑️
    - Click dòng → mở popup form edit (pre-fill data hiện tại). Nút ➕ → mở popup form add (trống)
    - Nút 🗑️ → confirm dialog → xóa dòng
    - Nút SAVE ở cuối panel → collect data từ in-memory list → PUT /api/chat/config
    - _Requirements: AC 19.38, AC 19.38a, AC 19.38b, AC 19.38c_

## Task 127: Frontend — CSS styles cho Popup Modal Forms
- [ ] 127.1 Cập nhật `frontend/src/jsMain/resources/styles/components.css`:
    - `.config-modal-overlay`: fixed inset 0, backdrop mờ (rgba(0,0,0,0.6)), z-index cao, flex center
    - `.config-modal`: min-width 500px, max-width 90vw, dark theme, glass border, padding, border-radius
    - `.config-modal-header`: title + close button
    - `.config-modal-body`: form fields layout, gap
    - `.config-modal-body label`: field labels
    - `.config-modal-body input`, `.config-modal-body select`, `.config-modal-body textarea`: dark input styling, full width
    - `.config-modal-body textarea`: min-height 100px, resize vertical
    - `.config-modal-body .field-error`: viền đỏ + error message styling
    - `.config-modal-footer`: Save + Cancel buttons, flex row, gap
    - Summary table rows: cursor pointer, hover highlight (để indicate clickable)
    - _Requirements: AC 19.38d, AC 19.38e, AC 19.38f, AC 19.38g, AC 19.38h, AC 19.38i_

## Task 128: Checkpoint — AI Personalization Popup Forms
- [ ] 128. 4 popup modal forms (Skill, Workflow, Instruction, Rule) mở đúng khi click ➕ hoặc click dòng. Validation required fields (viền đỏ, không đóng modal). Backdrop click / Escape đóng modal. Summary tables read-only hiển thị dữ liệu rút gọn. Save/Cancel hoạt động đúng. Ensure all tests pass, ask the user if questions arise.


---

# ═══════════════════════════════════════════════════════════════
# AUTO-DETECT AI RESPONSE FORMAT (AC 19.87–19.102)
# ═══════════════════════════════════════════════════════════════

## Task 129: Backend — AIModelContext data class
- [ ] 129.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/chat/models/AIModelContext.kt`:
    - `data class AIModelContext(val providerType: String, val modelName: String)`
    - Computed property `cacheKeySuffix: String` = `"$providerType:$modelName"`
    - _Requirements: AC 19.96, AC 19.97_

## Task 130: Backend — ResponseFormatDetector
- [ ] 130.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/chat/ResponseFormatDetector.kt`:
    - `internal object ResponseFormatDetector`
    - `CACHE_PREFIX = "ai_response_format:"`
    - `data class DetectedReply(val path: String, val text: String)`
    - `suspend fun detect(jsonObj: JsonObject, modelCtx: AIModelContext?, settingsRepo: SettingsRepository?): DetectedReply?`
    - Priority: (1) tryCache → (2) tryKnownKeys → (3) findLongestString
    - `tryCache()`: read `app_settings` by key, extract by path, invalidate if stale (Req: 19.89, 19.90)
    - `tryKnownKeys()`: try "reply", "text", "content", "message", "answer", "output", nested "response.*" (Req: 19.87)
    - `findLongestString()`: scan top-level + 1-level nested for longest string (Req: 19.87)
    - `extractByPath(obj, path)`: dot-separated path extraction (e.g. "response.text")
    - `saveCache()`: write to app_settings with correct prefix (Req: 19.88, 19.99)
    - _Requirements: AC 19.87, AC 19.88, AC 19.89, AC 19.90, AC 19.91, AC 19.99_

## Task 131: Backend — ToolCallFormatDetector
- [ ] 131.1 Tạo `server/src/jvmMain/kotlin/com/assistant/server/chat/ToolCallFormatDetector.kt`:
    - `internal object ToolCallFormatDetector`
    - `CACHE_PREFIX = "ai_tool_call_format:"`
    - Format identifiers: `MCP_TOOL_CALL`, `TOOL_NAME_INPUT`, `TEXT_PATTERN`
    - `suspend fun detect(response: String, modelCtx: AIModelContext?, settingsRepo: SettingsRepository?): McpToolCallRequest?`
    - Priority: (1) tryCachedFormat → (2) tryAllPatterns
    - `tryCachedFormat()`: read cached format, parse by format, invalidate if stale (Req: 19.94, 19.95)
    - `tryAllPatterns()`: mcpToolCall JSON → tool_name_input JSON → text pattern (Req: 19.92)
    - `saveCache()`: write format identifier to app_settings (Req: 19.93, 19.99)
    - Reuse `McpToolCallFallback.parseJsonToolName()` and `McpToolCallFallback.parseTextPattern()` (Req: 19.102)
    - _Requirements: AC 19.92, AC 19.93, AC 19.94, AC 19.95, AC 19.99, AC 19.102_

## Task 132: Backend — Cập nhật ChatResponseParser với cache-first detection
- [ ] 132.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatResponseParser.kt`:
    - Thêm overload `parse(raw, usage, modelCtx: AIModelContext?, settingsRepo: SettingsRepository?): ChatResponse`
    - Giữ nguyên `parse(raw, usage)` gọi overload mới với null params (backward-compatible, Req: 19.102)
    - Flow: blank check → tryStandardDecode (Req: 19.101) → extract JSON → ResponseFormatDetector.detect() → raw text fallback (Req: 19.100)
    - Extract actions + references từ JSON object khi detector tìm thấy reply
    - _Requirements: AC 19.89, AC 19.100, AC 19.101, AC 19.102_

## Task 133: Backend — Cập nhật McpAgenticLoop với cache-first tool call detection
- [ ] 133.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`:
    - Thêm params `modelCtx: AIModelContext? = null`, `settingsRepo: SettingsRepository? = null` vào cả 2 overload `execute()`
    - Cập nhật `parseMcpToolCall()`: nếu modelCtx + settingsRepo available → delegate to `ToolCallFormatDetector.detect()`, else → legacy logic (Req: 19.94, 19.102)
    - Giữ nguyên backward-compatible overload không có modelCtx/settingsRepo
    - _Requirements: AC 19.94, AC 19.102_

## Task 134: Backend — Cập nhật ChatServiceImpl resolve + pass AIModelContext
- [ ] 134.1 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatServiceImpl.kt`:
    - Thêm constructor param: `providerConfigRepository: ProviderConfigRepository? = null`
    - Thêm `resolveModelContext(): AIModelContext?` — đọc active provider config, trả về AIModelContext(type.name, model)
    - Cập nhật `parseAIResponse()`: gọi `ChatResponseParser.parse(raw, usage, resolveModelContext(), settingsRepository)` (Req: 19.96)
    - Cập nhật `processChat()`: truyền `resolveModelContext()` và `settingsRepository` vào `McpAgenticLoop.execute()` (Req: 19.96)
    - _Requirements: AC 19.96, AC 19.97_

- [ ] 134.2 Cập nhật `server/src/jvmMain/kotlin/com/assistant/server/di/ServerModule.kt`:
    - Thêm `providerConfigRepository = get()` vào ChatServiceImpl constructor
    - _Requirements: AC 19.96_

## Task 135: Backend — Unit tests cho ResponseFormatDetector
- [ ] 135.1 Tạo `server/src/jvmTest/kotlin/com/assistant/server/chat/ResponseFormatDetectorTest.kt`:
    - Test: detect known key "text" → returns DetectedReply("text", value)
    - Test: detect known key "content" → returns DetectedReply("content", value)
    - Test: detect nested "response.text" → returns DetectedReply("response.text", value)
    - Test: detect longest string when no known key → returns correct path
    - Test: cache hit → returns cached path value without running heuristic
    - Test: stale cache → invalidated, re-detected, new path saved
    - Test: no string field → returns null
    - Test: extractByPath valid path → correct value
    - Test: extractByPath invalid path → null
    - Test: cache key uses correct prefix "ai_response_format:"
    - _Requirements: AC 19.87, AC 19.88, AC 19.89, AC 19.90, AC 19.91, AC 19.99_

## Task 136: Backend — Unit tests cho ToolCallFormatDetector
- [ ] 136.1 Tạo `server/src/jvmTest/kotlin/com/assistant/server/chat/ToolCallFormatDetectorTest.kt`:
    - Test: detect mcpToolCall JSON format → returns McpToolCallRequest
    - Test: detect tool_name+tool_input JSON format → returns McpToolCallRequest
    - Test: detect "Tool Call: name(args)" text pattern → returns McpToolCallRequest
    - Test: cache hit → returns cached format result
    - Test: stale cache → invalidated, retries all patterns
    - Test: no tool call in response → returns null
    - Test: cache key uses correct prefix "ai_tool_call_format:"
    - _Requirements: AC 19.92, AC 19.93, AC 19.94, AC 19.95, AC 19.99_

## Task 137: Backend — Property tests cho Auto-Detect
- [ ] 137.1 Tạo `server/src/jvmTest/kotlin/com/assistant/server/chat/ResponseFormatDetectorPropertyTest.kt`:
    - **Property 9: Reply format detection with cache lifecycle** — 100+ iterations
      - Generator: random JSON objects with string fields at various paths, random AIModelContext
      - Tag: `Feature: ai-chat-sidebar, Property 9: Reply format detection with cache lifecycle`
    - **Property 13: extractByPath round-trip correctness** — 100+ iterations
      - Generator: random JSON objects with known paths
      - Tag: `Feature: ai-chat-sidebar, Property 13: extractByPath round-trip correctness`
    - _Requirements: AC 19.87, AC 19.88, AC 19.89, AC 19.90_

- [ ] 137.2 Tạo `server/src/jvmTest/kotlin/com/assistant/server/chat/AutoDetectPropertyTest.kt`:
    - **Property 11: Cache key isolation per provider+model** — 100+ iterations
      - Generator: pairs of distinct AIModelContext values
      - Tag: `Feature: ai-chat-sidebar, Property 11: Cache key isolation per provider+model`
    - **Property 12: Standard format and plain text bypass detection** — 100+ iterations
      - Generator: valid ChatResponse JSON + random non-JSON strings
      - Tag: `Feature: ai-chat-sidebar, Property 12: Standard format and plain text bypass detection`
    - _Requirements: AC 19.97, AC 19.100, AC 19.101_

## Task 138: Backend — Regression tests cho backward compatibility
- [ ] 138.1 Tạo hoặc cập nhật `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatResponseParserRegressionTest.kt`:
    - Test: parse(raw, usage) 2-param overload still works (Req: 19.102)
    - Test: standard "reply" key decoded directly without detection (Req: 19.101)
    - Test: plain text returned as-is (Req: 19.100)
    - Test: alternative keys ("text", "content", "message", "response.text") still work (Req: 19.102)
    - Test: McpToolCallFallback patterns still work after ToolCallFormatDetector integration (Req: 19.102)
    - _Requirements: AC 19.100, AC 19.101, AC 19.102_

## Task 139: Checkpoint — Auto-Detect AI Response Format
- [ ] 139. ResponseFormatDetector (cache-first → known keys → longest string), ToolCallFormatDetector (cache-first → all patterns), ChatResponseParser updated with cache-first detection, McpAgenticLoop updated with cache-first tool call detection, ChatServiceImpl resolves + passes AIModelContext, cache entries in app_settings with correct prefixes, all unit tests pass, property tests pass (100+ iterations), regression tests pass. Ensure all existing tests still pass.
