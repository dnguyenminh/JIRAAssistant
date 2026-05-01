# MCP Agentic Loop Internal Tool Routing Bugfix Design

## Overview

Khi AI Chat Sidebar gọi internal MCP tools (serverId: `"jira-assistant-ui"`) qua agentic loop, tất cả 30 internal tools đều fail với lỗi `"Error: server 'jira-assistant-ui' not running"`. Root cause: `McpAgenticLoop.executeToolWithLocalRouting()` route internal tool calls qua `McpProcessManager.getClient()` — vốn chỉ quản lý external processes và trả về `null` cho internal server. Fix approach: thêm `InternalMcpBridge` parameter vào `McpAgenticLoop.execute()` và route internal calls qua `bridge.callTool()`.

## Glossary

- **Bug_Condition (C)**: Tool call có `serverId = "jira-assistant-ui"` được gọi qua agentic loop (từ `McpAgenticLoop.executeToolWithLocalRouting()`)
- **Property (P)**: Internal tool calls qua agentic loop phải execute thành công qua `InternalMcpBridge.callTool()` và trả về kết quả thực tế
- **Preservation**: External tool calls, Local KB calls, REST API calls, non-tool-call responses, và RBAC enforcement phải hoạt động y hệt trước fix
- **McpAgenticLoop**: Object singleton trong `McpAgenticLoop.kt` xử lý agentic loop — detect tool calls từ AI response, execute, feed result back (max 5 rounds)
- **InternalMcpBridge**: Bridge class trong `InternalMcpBridge.kt` route internal tool calls tới `InternalMcpToolExecutor` với RBAC check
- **McpProcessManager**: Manager cho external MCP processes — `getClient(serverId)` trả về `null` cho internal server vì không quản lý internal processes
- **executeToolWithLocalRouting()**: Private method trong `McpAgenticLoop` quyết định route tool call tới Local KB, internal, hoặc external handler

## Bug Details

### Bug Condition

Bug xảy ra khi agentic loop nhận tool call targeting internal MCP server (`"jira-assistant-ui"`). `executeToolWithLocalRouting()` nhận diện đúng `InternalMcpBridge.INTERNAL_SERVER_ID` nhưng vẫn gọi `executeTool(pm, req)` → `pm.getClient("jira-assistant-ui")` trả về `null` → error string.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type McpToolCallRequest with callSource context
  OUTPUT: boolean
  
  RETURN input.serverId = "jira-assistant-ui"
         AND input.callSource = "agentic-loop"
         AND correspondingInternalToolExists(input.toolName)
END FUNCTION
```

### Examples

- User hỏi "Cho tôi xem dashboard metrics" → AI gọi `get_dashboard_metrics` với `serverId: "jira-assistant-ui"` → **Actual**: `"Error: server 'jira-assistant-ui' not running"` | **Expected**: JSON data với metrics thực tế
- User hỏi "List all projects" → AI gọi `list_projects` với `serverId: "jira-assistant-ui"` → **Actual**: Error string | **Expected**: Danh sách projects từ KBRepository
- User hỏi "Analyze ticket ABC-123" → AI gọi `analyze_ticket` với `serverId: "jira-assistant-ui"` → **Actual**: Error string | **Expected**: Kết quả analysis từ AIOrchestrator
- User hỏi "Navigate to settings page" → AI gọi `navigate_to_page` với `serverId: "jira-assistant-ui"` → **Actual**: Error string | **Expected**: Navigation response thành công

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- REST API endpoint `POST /api/integrations/mcp/tools/call` tiếp tục route internal tools qua `InternalMcpBridge.callTool()` trong `McpToolsHandlers.handleToolCall()`
- External MCP tools (serverId khác `"jira-assistant-ui"`) tiếp tục route qua `McpProcessManager.getClient()` trong agentic loop
- Local KB tools (serverId: `"local-kb"`) tiếp tục route qua `LocalKBToolExecutor` trong agentic loop
- AI responses không chứa tool call tiếp tục trả về text response trực tiếp
- RBAC enforcement qua `InternalMcpToolExecutor.execute()` không bị ảnh hưởng
- Per-user tool permission check (`isToolDisabledByUser`) tiếp tục hoạt động cho internal tools

**Scope:**
Tất cả inputs KHÔNG có `serverId = "jira-assistant-ui"` qua agentic loop hoàn toàn không bị ảnh hưởng. Bao gồm:
- External MCP tool calls (Jira, Confluence, custom servers)
- Local KB tool calls (`local-kb`)
- Non-tool-call AI text responses
- REST API direct tool calls (không qua agentic loop)

## Root Cause (Confirmed — 2 Layers)

Root cause đã được xác nhận và fix đã được implement + verify bằng property-based tests và browser testing.

### Layer 1: Missing InternalMcpBridge routing trong McpAgenticLoop

1. **Missing InternalMcpBridge in McpAgenticLoop**: `executeToolWithLocalRouting()` check `req.serverId == InternalMcpBridge.INTERNAL_SERVER_ID` nhưng không có `InternalMcpBridge` instance → fallthrough tới `executeTool(pm, req)` → `pm.getClient()` returns `null`

2. **ChatServiceImpl không truyền bridge**: `ChatServiceImpl.processChat()` gọi `McpAgenticLoop.execute()` với `mcpProcessManager` nhưng KHÔNG truyền `internalMcpBridge` — dù `ChatServiceImpl` đã có property `internalMcpBridge: InternalMcpBridge?`

3. **McpAgenticLoop.execute() thiếu parameter**: Cả hai overloads của `execute()` đều không có parameter cho `InternalMcpBridge`, nên không có cách nào truyền bridge vào

4. **executeToolWithLocalRouting() routing logic sai**: Khi `serverId == INTERNAL_SERVER_ID`, code check `if (pm == null) return error` rồi gọi `executeTool(pm, req)` — đúng ra phải route qua `InternalMcpBridge.callTool()`

### Layer 2: Conflicting prompt instructions trong ChatPromptBuilder

5. **ChatPromptBuilder.buildToolCallBlock() có instructions mâu thuẫn**: Prompt gốc có 2 instructions conflict — "ALWAYS respond in JSON format: `{\"reply\":...}`" (ưu tiên cao) vs "When you need data, respond with ONLY `{\"mcpToolCall\":...}`" (bị AI bỏ qua). Kết quả: AI trả về `{"reply":"Tôi sẽ truy xuất...","actions":[{"type":"get_dashboard_metrics",...}]}` thay vì `{"mcpToolCall":{...}}`. Agentic loop chỉ detect `mcpToolCall` format → không execute tool → tương tác bị ngắt dù Layer 1 đã fix

## Correctness Properties

Property 1: Bug Condition - Internal Tool Calls Execute Successfully via Agentic Loop

_For any_ tool call request where `serverId = "jira-assistant-ui"` and the call originates from the agentic loop, the fixed `executeToolWithLocalRouting()` SHALL route the call through `InternalMcpBridge.callTool(toolName, arguments, userId, userRole)` and return the actual tool result (not the error string `"Error: server 'jira-assistant-ui' not running"`).

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Non-Internal Tool Call Behavior Unchanged

_For any_ tool call request where `serverId ≠ "jira-assistant-ui"` (external MCP tools, Local KB tools) or where the AI response contains no tool call, the fixed code SHALL produce exactly the same behavior as the original code, preserving external tool routing via `McpProcessManager.getClient()`, Local KB routing via `LocalKBToolExecutor`, and direct text response passthrough.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Implemented

**File**: `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`

**Function**: `execute()` (both overloads) + `executeToolWithLocalRouting()`

**Specific Changes**:
1. **Added `InternalMcpBridge?` parameter to both `execute()` overloads**: `internalMcpBridge: InternalMcpBridge? = null` — default `null` giữ backward compatibility
   - Overload 1 (backward-compatible): forward parameter tới overload 2
   - Overload 2 (full): pass bridge tới `executeToolWithLocalRouting()`

2. **Added `InternalMcpBridge?` parameter to `executeToolWithLocalRouting()`**: `bridge: InternalMcpBridge? = null` parameter

3. **Fixed `canExecute` check trong `execute()` overload 2**: Thêm `|| isInternalCall(toolCall, internalMcpBridge)` để cho phép internal tool execution khi `mcpProcessManager` là null. Thêm helper `isInternalCall(req, bridge)` check `serverId == INTERNAL_SERVER_ID && bridge != null`

4. **Fixed routing logic trong `executeToolWithLocalRouting()`**: Khi `req.serverId == InternalMcpBridge.INTERNAL_SERVER_ID` → gọi `executeInternalTool(bridge, req, userId)` thay vì `executeTool(pm, req)`

5. **Extracted `executeInternalTool()` private method** (11 dòng):
   - Nếu `bridge == null` → return `"Error: no internal MCP bridge available"` (graceful degradation)
   - Gọi `bridge.callTool(req.toolName, req.arguments, userId ?: "system", "READER")` — `userRole` hardcoded `"READER"` vì agentic loop context không có role info
   - Convert `McpToolCallResponse` → String: `resp.content.mapNotNull { it.text }.joinToString("\n")`
   - Handle `isError` flag: return `"Tool error: ${resp.content.firstOrNull()?.text ?: "unknown"}"`
   - Wrap trong try-catch cho graceful degradation matching external tool error handling pattern

**File**: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatServiceImpl.kt`

**Function**: `processChat()`

**Specific Changes**:
1. **Pass `internalMcpBridge` to `McpAgenticLoop.execute()`**: Thêm `internalMcpBridge = internalMcpBridge` vào call site trong `processChat()`

**File**: `server/src/jvmMain/kotlin/com/assistant/server/mcp/internal/InternalMcpBridge.kt`

**Specific Changes**:
1. **Made class `open`**: `open class InternalMcpBridge` — cho phép subclassing trong tests (StubBridge)
2. **Made constructor params nullable**: `executor: InternalMcpToolExecutor?`, `mcpRepo: McpServerRepository?` — cho phép tạo instance với null deps trong tests
3. **Made `callTool()` method `open`**: Cho phép override trong test stubs

**File**: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatPromptBuilder.kt`

**Function**: `buildToolCallBlock()`

**Specific Changes** (Layer 2 fix):
1. **Tách biệt rõ ràng 2 response formats**: Tool calling block (highest priority) đặt trước response format block, với explicit anti-patterns
2. **Thêm explicit DO NOT instructions**: `DO NOT respond with {"reply":"I will fetch data...","actions":[{"type":"get_dashboard_metrics",...}]}` — ngăn AI trộn lẫn 2 formats
3. **Thêm NEVER rules**: `NEVER say "I will fetch data" without actually calling the tool`, `NEVER put tool names in the "actions" array — actions are ONLY for UI navigation/filtering`
4. **Response format block ghi rõ**: `RESPONSE FORMAT (only when NOT calling a tool)` — chỉ dùng reply+actions format SAU KHI đã có data từ tool
5. **Thêm EMPTY DATA HANDLING instruction**: Khi tool trả về data toàn zero (totalTickets:0, totalEdges:0), AI phải nói "Dự án chưa được phân tích" thay vì báo cáo số 0 như thật, và gợi ý user chạy Batch Scan
6. **Mở rộng `buildActionsBlock()` bao phủ tất cả screens và action types**: Từ 3 screens thành đầy đủ 8 navigation targets (`dashboard`, `knowledge_graph`, `analysis` với `ticketId` context, `ticket_intelligence`, `integrations`, `user_management`, `settings`, `project_select`). Thêm server actions (`changeConfig` — Administrator only, `triggerAnalysis` — Neural_Architect+). Tổ chức actions thành 4 nhóm: Graph actions, Navigation actions, Server actions, External links. Thêm note phân biệt: actions = UI buttons, mcpToolCall = data operations
7. **Thêm TICKET LOOKUP instruction**: Khi user nói "mở/open/xem/tra cứu/tìm ticket X", AI phải gọi `get_ticket_analysis` thay vì hiểu sai thành "tạo ticket". LUÔN kèm navigate action tới `ticket_intelligence` với `ticketId` — kể cả khi KB chưa có data (để user có thể xem/search trên trang Ticket Intelligence). Nếu KB có data → trả summary + navigate action + references. Nếu không → thông báo chưa phân tích + vẫn kèm navigate action. Nếu ticket prefix khác project hiện tại → cảnh báo có thể thuộc project khác

## Testing Strategy

### Validation Approach

Testing strategy theo hai phase: (1) surface counterexamples trên unfixed code để confirm root cause, (2) verify fix works và preserve existing behavior.

### Exploratory Bug Condition Checking (Implemented)

**Test File**: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopInternalToolBugTest.kt`

**Approach**: Property-based test using Kotest `checkAll` with `Arb.element(INTERNAL_TOOLS)` (9 iterations across 9 internal tools). Mocks `McpProcessManager` returning null for `getClient()`, creates `StubBridge` extending `open class InternalMcpBridge` returning successful response. Captures round 2 prompt to verify error marker absence.

**Result**: Test FAILED on unfixed code (confirmed bug), PASSES after fix.

### Fix Checking (Implemented)

**Verified by**: Re-running `McpAgenticLoopInternalToolBugTest` after fix — test PASSES, confirming all internal tool calls route via `bridge.callTool()` and return actual results instead of error strings.

### Preservation Checking (Implemented)

**Test File**: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopPreservationTest.kt`
**Fakes File**: `server/src/jvmTest/kotlin/com/assistant/server/chat/PreservationTestFakes.kt`

**Approach**: 4 property-based tests using Kotest `checkAll` (15 iterations each):
1. **Property 2a**: External tool calls route via `McpProcessManager.getClient()` — uses `TrackingClientManager` to verify
2. **Property 2b**: Local KB calls route via `LocalKBToolExecutor.execute()` — verifies `getClient` NOT called
3. **Property 2c**: Non-tool-call AI responses return text directly — verifies single AI call, text passthrough
4. **Property 2d**: Per-user disabled tools return "disabled by user" message — uses `InMemoryPermissionRepo`

**Result**: All 4 tests PASS on both unfixed and fixed code (preservation confirmed).

### Unit Tests (Covered by existing tests)

Existing test classes verified passing after fix (27 total tests, 0 failures):
- `McpAgenticLoopTest` — 14 tests (parsing, sync warnings, Confluence actions)
- `McpAgenticLoopLocalKBTest` — 3 tests (local KB routing)
- `McpAgenticLoopEmptyReplyTest` — 2 tests (empty reply handling)
- `McpAgenticLoopPropertyTest` — 3 tests (parseMcpToolCall properties)

### Property-Based Tests (Implemented)

- `McpAgenticLoopInternalToolBugTest` — 1 PBT (9 iterations across internal tools, Property 1)
- `McpAgenticLoopPreservationTest` — 4 PBTs (15 iterations each, Property 2a-2d)
