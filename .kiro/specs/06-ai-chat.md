# AI Chat — Master Requirements

## Tổng quan

Domain AI Chat cung cấp AI Chat Sidebar — panel docked bên phải trong Shell layout, cho phép người dùng tương tác với trợ lý AI bằng ngôn ngữ tự nhiên. AI sử dụng các provider đã cấu hình (Ollama, Gemini, LM Studio, Gemini CLI) và tham khảo Knowledge Base để trả lời theo ngữ cảnh dự án. Hệ thống hỗ trợ MCP tool integration qua text-based agentic loop (tối đa 5 vòng), multi-conversation management, AI personalization per-user, voice input, file/image upload, Local KB MCP tools, và per-user tool permissions. Native tool calling infrastructure (`OllamaChatAgent`, `NativeToolCallHandler`) đã được tạo nhưng chưa enable — cần thêm testing trước khi kích hoạt.

Các bugfix đã giải quyết: AI trả reply trống khi hết MAX_ROUNDS agentic loop, và internal MCP tools fail qua agentic loop do routing sai qua McpProcessManager thay vì InternalMcpBridge.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `ai-chat-sidebar` | Feature | ✅ Archived | AI Chat Sidebar — chat UI, KB integration, MCP tools, personalization, Local KB tools |
| `per-user-tool-permissions` | Feature | ✅ Archived | Per-user enable/disable MCP tools, thay thế global autoApprove |
| `chat-empty-reply-bug` | Bugfix | ✅ Archived | Fix AI trả reply trống khi agentic loop hết MAX_ROUNDS |
| `mcp-agentic-loop-fix` | Bugfix | ✅ Archived | Fix internal MCP tools fail qua agentic loop |

## Requirements tổng hợp

### UI & Layout

- Panel docked bên phải, resize handle (min 280px, max 600px, default 380px)
- Toggle button 💬 trên Navbar header, hiển thị trên mọi trang có Shell
- Chat area: tiêu đề, lịch sử hội thoại (cuộn), textarea (Shift+Enter xuống dòng, Enter gửi), nút gửi
- Giữ nguyên lịch sử khi đóng/mở sidebar

### Gửi & Nhận Tin nhắn

- `POST /api/chat/send` nhận ChatRequest (message, conversationHistory, context)
- AI provider failover theo thứ tự ưu tiên, timeout 30 giây
- Typing indicator khi đang xử lý, markdown rendering (bold, italic, code block, lists)
- Context window indicator (circular progress) — warning ở 80%, danger ở 95%

### Knowledge Base Integration

- KB-First strategy: tìm ticket trong KB trước khi gọi AI provider
- Deep Analysis context: extracted_requirements, technical_details, dependencies, acceptance_criteria
- Graph context: clusters, dependencies, semantic links
- buildKnowledgeContext: semantic search trên tất cả chunkTypes, top-10 grouped results

### MCP Tool Integration & Agentic Loop

**Text-Based Parsing (Active):**
- System prompt inject danh sách MCP tools available (chỉ ACTIVE servers)
- Agentic loop: AI response chứa `{"mcpToolCall": {...}}` → parse text → execute → inject result → tiếp tục (max 5 rounds)
- Tool call format detection (priority order): cached format → `{"mcpToolCall":{...}}` JSON → `{"tool_name":"...","tool_input":{...}}` JSON → `Tool Call: name(args)` text → natural language pattern (Vietnamese/English)
- Natural language fallback: nhận diện text dạng "Dùng tool search_knowledge để tìm 'X'" — extract tool name + arguments
- Tool execution indicator: "🔧 Đang gọi {toolName}...", kết quả trong collapsible block
- Graceful degradation khi tool fail — AI sinh response thay thế
- Round cuối inject instruction yêu cầu AI tổng hợp text reply (fix empty reply)
- Fallback message khi reply trống: "Tôi đã thu thập thông tin nhưng không thể tổng hợp..."

**Native Tool Calling (Code exists, NOT active — cần testing):**
- `OllamaChatAgent` sử dụng Ollama `/api/chat` endpoint với `tools` parameter — model trả về structured `tool_calls`
- `OllamaToolConverter` convert MCP tools sang Ollama tool format, convention `serverId__toolName`
- `NativeToolCallHandler` agentic loop với structured tool_calls
- `NativeToolCallExecutor` route tool calls về đúng server
- `ChatServiceImpl.processChat()` detect `OllamaChatAgent` → native path; fallback về legacy
- Hiện tại `ChatModule` tạo `OllamaAgent` (text-based) — chuyển sang `OllamaChatAgent` khi đã test đủ

### Internal MCP Tools Routing

- Internal tools (serverId: `jira-assistant-ui`) route qua `InternalMcpBridge.callTool()` thay vì McpProcessManager
- ChatServiceImpl truyền InternalMcpBridge vào McpAgenticLoop.execute()
- Phân biệt "mở ticket" (tra cứu) vs "tạo ticket" (create)
- Native path (khi enable): `NativeToolCallExecutor` parse `jira-assistant-ui__toolName` → route qua InternalMcpBridge

### Per-User Tool Permissions

- Mỗi user enable/disable bất kỳ MCP tool — disabled tools không inject vào system prompt
- Bảng `user_tool_permissions`: `{serverId::toolName → enabled|disabled}`
- `GET/PUT /api/chat/tool-permissions`, `PUT /api/chat/tool-permissions/bulk`
- Default: tất cả tools enabled cho user mới
- Section "🔧 Tool Permissions" trong Chat Sidebar (collapsible, grouped theo server)
- Đồng bộ giữa Integrations Page và Chat Sidebar

### Local Knowledge Base MCP Tools

- 3 operations: `search_knowledge`, `get_ticket_info`, `search_relationships`
- Chạy in-process (không qua stdio/network), target < 100ms
- System prompt ưu tiên Local KB tools trước external Jira MCP tools
- `search_knowledge` sử dụng cosine similarity với relevance threshold (MIN_RELEVANCE_SCORE = 0.3) — kết quả có score thấp hơn bị lọc bỏ, trả về "No relevant results found" thay vì data không liên quan
- `VectorStore.searchWithScores()` trả về `List<Pair<AttachmentChunk, Float>>` — cho phép caller filter theo similarity score
- Enable/disable toggle trên Integrations page, card hiển thị type "local"

### Multi-Conversation Management

- Danh sách conversations (collapsible), nút "➕ New Chat"
- Auto-generate tiêu đề từ message đầu tiên (50 ký tự)
- CRUD endpoints: `GET/POST/DELETE/PUT /api/chat/conversations`
- Double-click đổi tên, hover hiện nút xóa (🗑️ + confirm)

### AI Personalization

- Panel "AI Personalization" (⚙️ button): 4 bảng tóm tắt + popup forms
- Skills Table, Workflow Table, Instructions Table, Rules Table
- Popup modal forms với validation, backdrop overlay, Escape to close
- Bảng `user_ai_config` lưu per-user, inject vào system prompt
- Preview trên header: "⚙️ 3 skills, 2 rules"

### Voice Input & File Upload

- Voice input: Web Speech API, nút 🎤, visual indicator khi recording
- File upload: 📎 button, hỗ trợ Word/Excel/PDF/images (max 10MB)
- Clipboard paste: Ctrl+V auto-detect image, drag-and-drop support
- `POST /api/chat/upload` endpoint, extract text content cho prompt context

### Model-Aware UI

- Hiển thị tên model AI active, `GET /api/chat/model-info`
- Vision support: enable/disable image features theo `supportsVision`
- "MCP Tools Available" section: grouped theo server, autocomplete `@tool_name`

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `chat-empty-reply-bug` | Fix AI trả reply trống khi agentic loop hết 5 rounds — inject instruction round cuối + fallback message |
| `mcp-agentic-loop-fix` | Fix 30 internal MCP tools fail qua agentic loop — route qua InternalMcpBridge thay vì McpProcessManager, fix prompt format conflicts |
