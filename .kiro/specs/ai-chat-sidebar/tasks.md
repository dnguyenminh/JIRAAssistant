# AI Chat Sidebar — Tasks

Status: ✅ All completed

# AI Chat Sidebar — Tasks 73–85

Panel chat AI docked bên phải trong Shell layout, resizable (280–600px), toggle từ Navbar header 💬. Per-user persistent history, KB+Graph context, OllamaAgent direct injection, action system. Textarea multiline (Shift+Enter xuống dòng).

Tất cả tasks đã hoàn thành. Thay đổi chính so với thiết kế ban đầu:
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
- [x]* 83.1 Tạo `ChatApiTest.kt`:
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
