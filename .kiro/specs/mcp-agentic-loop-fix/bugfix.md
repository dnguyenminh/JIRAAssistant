# Bugfix Requirements Document

## Introduction

Khi user tương tác với AI Chat Sidebar và AI cố gắng gọi internal MCP tools (serverId: `"jira-assistant-ui"`), tất cả 30 internal tools đều fail với lỗi `"Error: server 'jira-assistant-ui' not running"`. Bug xảy ra vì `McpAgenticLoop.executeToolWithLocalRouting()` nhận diện đúng internal server nhưng route tool call qua `McpProcessManager.getClient()` — vốn chỉ quản lý external processes và trả về `null` cho internal server. Trong khi đó, REST API endpoint (`/api/integrations/mcp/tools/call`) hoạt động đúng vì nó route qua `InternalMcpBridge.callTool()`.

Bug ảnh hưởng TẤT CẢ 30 internal MCP tools khi gọi qua agentic loop, bao gồm: `get_dashboard_metrics`, `list_projects`, `get_graph_data`, `navigate_to_page`, `start_scan`, `analyze_ticket`, v.v.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN AI chat sidebar gửi message và AI response chứa tool call với `serverId: "jira-assistant-ui"` THEN `McpAgenticLoop.executeToolWithLocalRouting()` gọi `executeTool(pm, req)` → `pm.getClient("jira-assistant-ui")` trả về `null` → trả về error string `"Error: server 'jira-assistant-ui' not running"` thay vì kết quả tool thực tế

1.2 WHEN `McpAgenticLoop.execute()` được gọi từ `ChatServiceImpl.processChat()` THEN `McpAgenticLoop` không có access tới `InternalMcpBridge` instance, do đó không thể route internal tool calls qua `bridge.callTool()` — dù `ChatServiceImpl` đã có `internalMcpBridge` property

1.3 WHEN AI response chứa tool call cho bất kỳ internal tool nào trong 30 tools (ví dụ: `get_dashboard_metrics`, `list_projects`, `analyze_ticket`, `get_graph_data`, `navigate_to_page`, `start_scan`) qua agentic loop THEN tool call luôn fail với error, khiến AI không thể trả lời câu hỏi cần data từ internal tools

1.4 WHEN `ChatActionRoutes.handleExecuteAction()` nhận request với `actionType` không phải `changeConfig`, `triggerAnalysis`, hoặc `navigate` THEN hệ thống trả về `"Unknown action: ${request.actionType}"` — không hỗ trợ mở rộng action types mới

1.5 WHEN `ChatPromptBuilder.buildToolCallBlock()` chứa instructions mâu thuẫn giữa "ALWAYS respond in JSON `{\"reply\":...}`" và "respond with ONLY `{\"mcpToolCall\":...}`" THEN AI ưu tiên format `reply+actions` và trả về `{"reply":"Tôi sẽ truy xuất...","actions":[{"type":"get_dashboard_metrics",...}]}` thay vì emit `{"mcpToolCall":{...}}` — agentic loop không detect được tool call → tương tác bị ngắt

### Expected Behavior (Correct)

2.1 WHEN AI chat sidebar gửi message và AI response chứa tool call với `serverId: "jira-assistant-ui"` THEN `McpAgenticLoop.executeToolWithLocalRouting()` SHALL route tool call qua `InternalMcpBridge.callTool()` và trả về kết quả tool thực tế (giống như REST API endpoint hoạt động)

2.2 WHEN `McpAgenticLoop.execute()` được gọi từ `ChatServiceImpl.processChat()` THEN `ChatServiceImpl` SHALL truyền `InternalMcpBridge` instance vào `McpAgenticLoop.execute()` để agentic loop có thể route internal tool calls đúng cách

2.3 WHEN AI response chứa tool call cho bất kỳ internal tool nào trong 30 tools qua agentic loop THEN tool call SHALL được execute thành công qua `InternalMcpBridge` và trả về kết quả data chính xác, cho phép AI tổng hợp và trả lời user

2.4 WHEN internal tool call qua agentic loop gặp exception THEN hệ thống SHALL trả về error message mô tả lỗi (graceful degradation) thay vì crash, tương tự cách xử lý error của external tool calls

2.5 WHEN user hỏi câu hỏi cần data (metrics, summaries, ticket info, project status) THEN `ChatPromptBuilder.buildToolCallBlock()` SHALL instruct AI to emit `{"mcpToolCall":{...}}` JSON only (highest priority), KHÔNG kèm `reply` text hoặc `actions` array — đảm bảo agentic loop detect và execute tool call

2.6 WHEN internal tool trả về data với tất cả giá trị bằng 0 (totalTickets:0, totalEdges:0) hoặc kết quả trống THEN AI SHALL trả lời "Dự án chưa được phân tích" và gợi ý user chạy Batch Scan, KHÔNG báo cáo số 0 như thật

2.7 WHEN `ChatPromptBuilder.buildActionsBlock()` liệt kê available actions THEN danh sách SHALL bao phủ TẤT CẢ screens của ứng dụng (`dashboard`, `knowledge_graph`, `analysis`, `ticket_intelligence`, `integrations`, `user_management`, `settings`, `project_select`), server actions (`changeConfig`, `triggerAnalysis`), graph actions, và external links — đảm bảo AI có thể navigate và tương tác với bất kỳ chức năng nào

2.8 WHEN user nói "mở ticket X", "open ticket X", "xem ticket X", "tra cứu ticket X", "tìm ticket X" THEN AI SHALL gọi `get_ticket_analysis` với ticketId để tra cứu KB, KHÔNG hiểu sai "mở" thành "tạo" (create). AI SHALL LUÔN kèm navigate action tới `ticket_intelligence` với `ticketId` trong response — kể cả khi KB chưa có data — để user có thể chuyển sang Ticket Intelligence xem/search. Nếu ticket prefix khác project hiện tại → cảnh báo có thể thuộc project khác

### Unchanged Behavior (Regression Prevention)

3.1 WHEN internal MCP tools được gọi qua REST API endpoint (`POST /api/integrations/mcp/tools/call`) THEN hệ thống SHALL CONTINUE TO route qua `InternalMcpBridge.callTool()` và trả về kết quả đúng — flow REST API hiện tại không bị ảnh hưởng

3.2 WHEN external MCP tools (serverId khác `"jira-assistant-ui"`) được gọi qua agentic loop THEN hệ thống SHALL CONTINUE TO route qua `McpProcessManager.getClient()` và execute bình thường

3.3 WHEN Local KB tools (serverId: `"local-kb"`) được gọi qua agentic loop THEN hệ thống SHALL CONTINUE TO route qua `LocalKBToolExecutor` và execute bình thường

3.4 WHEN AI chat sidebar gửi message không chứa tool call THEN hệ thống SHALL CONTINUE TO trả về AI text response trực tiếp mà không cần execute tool

3.5 WHEN `ChatActionRoutes.handleExecuteAction()` nhận request với `actionType` là `changeConfig`, `triggerAnalysis`, hoặc `navigate` THEN hệ thống SHALL CONTINUE TO xử lý đúng như hiện tại

3.6 WHEN internal tool call qua agentic loop có RBAC check THEN hệ thống SHALL CONTINUE TO enforce RBAC permissions thông qua `InternalMcpToolExecutor` — Reader không thể gọi write tools, Neural_Architect không thể manage users

---

## Bug Condition (Formal)

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type AgenticLoopToolCallRequest
  OUTPUT: boolean
  
  // Bug triggers when agentic loop receives a tool call targeting the internal MCP server
  RETURN X.serverId = "jira-assistant-ui" 
     AND X.callSource = "agentic-loop"
END FUNCTION
```

```pascal
// Property: Fix Checking — Internal tools execute successfully via agentic loop
FOR ALL X WHERE isBugCondition(X) DO
  result ← McpAgenticLoop'.executeToolWithLocalRouting(X)
  ASSERT result ≠ "Error: server 'jira-assistant-ui' not running"
  ASSERT result = InternalMcpBridge.callTool(X.toolName, X.arguments, X.userId, X.userRole).toText()
END FOR
```

```pascal
// Property: Preservation Checking — Non-internal tool calls unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT McpAgenticLoop(X) = McpAgenticLoop'(X)
END FOR
```
