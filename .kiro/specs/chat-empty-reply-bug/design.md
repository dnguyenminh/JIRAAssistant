# Chat Empty Reply Bug — Bugfix Design

## Overview

AI Chat trả về reply trống khi user hỏi về ticket cụ thể. Root cause là chuỗi 3 vấn đề: (1) `McpAgenticLoop` chạy hết 5 rounds gọi MCP tools mà không inject instruction yêu cầu AI ngừng gọi tools ở round cuối, (2) `ChatResponseParser` không có fallback cho empty reply sau khi parse tool call JSON, (3) `AIChatSidebar` không guard cho empty reply trong `handleSuccess()`.

Fix approach: defense-in-depth — sửa cả 3 layers để đảm bảo user luôn nhận được reply có ý nghĩa.

## Glossary

- **Bug_Condition (C)**: AI liên tục gọi MCP tools qua hết MAX_ROUNDS (5 rounds) mà không bao giờ trả lời text → reply trống
- **Property (P)**: Sau MAX_ROUNDS, AI PHẢI trả về text reply có ý nghĩa (không trống, không phải tool call JSON)
- **Preservation**: Mọi behavior hiện tại cho non-buggy inputs (text reply trước MAX_ROUNDS, valid JSON parse, plain text fallback) phải giữ nguyên
- **McpAgenticLoop**: Object trong `server/.../chat/McpAgenticLoop.kt` điều khiển vòng lặp agentic gọi AI → parse tool call → execute tool → feed result back
- **ChatResponseParser**: Object trong `server/.../chat/ChatResponseParser.kt` parse AI response text thành `ChatResponse`
- **AIChatSidebar**: Object trong `frontend/.../components/AIChatSidebar.kt` render chat messages trong UI
- **MAX_ROUNDS**: Constant = 5, số rounds tối đa của agentic loop

## Bug Details

### Bug Condition

Bug xảy ra khi user gửi message khiến AI liên tục gọi MCP tools (ví dụ: hỏi về ticket cụ thể) qua hết MAX_ROUNDS mà AI không bao giờ trả lời text. Sau vòng lặp, `callAI(prompt)` cuối cùng vẫn trả về tool call JSON vì prompt không có instruction yêu cầu AI ngừng gọi tools.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ChatRequest (message chứa ticket ID hoặc trigger MCP tools)
  OUTPUT: boolean

  aiResponses ← runAgenticLoop(input.message, MAX_ROUNDS=5)
  finalResponse ← callAI(promptAfterAllRounds)
  
  RETURN ALL responses in aiResponses contain mcpToolCall JSON
         AND finalResponse does NOT contain text reply
         AND ChatResponseParser.parse(finalResponse).reply == ""
END FUNCTION
```

### Examples

- User gửi "ticket CRP-3203 đã phân tích chưa?" → AI gọi `get_ticket_info` 5 rounds → final response vẫn là tool call JSON → `reply: ""` → bubble trống
- User gửi "So sánh ticket ABC-1 và ABC-2" → AI gọi `get_ticket_info` cho mỗi ticket → hết 5 rounds → `reply: ""`
- User gửi "Phân tích tất cả tickets trong sprint" → AI gọi `search_knowledge` liên tục → hết rounds → `reply: ""`
- User gửi "Hello" (không trigger tools) → AI trả lời text ngay round 1 → reply bình thường (KHÔNG phải bug condition)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Messages đơn giản không trigger MCP tools (ví dụ: "Hello", "Giới thiệu dự án") phải tiếp tục trả về reply bình thường
- Khi AI trả lời text (không có tool call) trước MAX_ROUNDS, loop phải exit ngay lập tức
- `ChatResponseParser` phải tiếp tục parse valid JSON với "reply" field đúng như hiện tại
- `ChatResponseParser` phải tiếp tục sử dụng plain text fallback cho non-JSON responses
- `AIChatSidebar.handleSuccess()` phải tiếp tục render chat bubble bình thường khi reply có nội dung
- Tool execution trong các rounds trước MAX_ROUNDS phải tiếp tục hoạt động bình thường

**Scope:**
Tất cả inputs mà AI trả lời text reply (không trống) trước hoặc tại MAX_ROUNDS không bị ảnh hưởng bởi fix này.

## Hypothesized Root Cause

Based on code analysis, 3 root causes đã được xác nhận:

1. **McpAgenticLoop thiếu "stop tools" instruction**: Trong `execute()`, sau vòng lặp `for (round in 1..MAX_ROUNDS)`, code gọi `callAI(prompt)` nhưng `prompt` chỉ chứa tool results — KHÔNG có instruction yêu cầu AI ngừng gọi tools và tổng hợp câu trả lời. AI tiếp tục trả về tool call JSON.

2. **ChatResponseParser không handle empty reply sau parse**: Khi AI response là tool call JSON (ví dụ: `{"mcpToolCall":{...}}`), `tryStandardDecode` fail (không có "reply" key), `tryExtractJson` tìm thấy JSON object nhưng `extractReplyFromJson` trả về `null` (không có reply/text/content/message keys), cuối cùng fallback `ChatResponse(reply = raw)` trả về raw tool call JSON. Tuy nhiên nếu response chứa tool call JSON embedded trong text khác, `extractJsonObject` có thể extract JSON mà không có reply → `tryAlternativeJsonFormats` trả về `null` → plain text fallback trả về toàn bộ raw text bao gồm tool call JSON. Vấn đề thực sự: không có check cho trường hợp parsed reply là empty string.

3. **AIChatSidebar.handleSuccess() không guard empty reply**: `handleSuccess()` gọi `ChatMessageRenderer.renderMessage("assistant", chatResp.reply)` trực tiếp mà không kiểm tra `chatResp.reply` có trống hay không → render bubble trống.

## Correctness Properties

Property 1: Bug Condition — AI luôn trả về non-empty reply sau MAX_ROUNDS

_For any_ input where McpAgenticLoop chạy hết MAX_ROUNDS mà AI liên tục gọi MCP tools (isBugCondition returns true), the fixed system SHALL trả về `ChatResponse` với `reply` không trống và có ý nghĩa (không phải tool call JSON, không phải empty string).

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation — Non-buggy inputs behavior unchanged

_For any_ input where AI trả lời text reply (không trigger bug condition — isBugCondition returns false), the fixed system SHALL produce exactly the same `ChatResponse` as the original system, preserving reply content, actions, references, và contextUsage.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

**File**: `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`

**Function**: `execute()`

**Specific Changes**:
1. **Inject "stop tools" instruction trước final callAI**: Sau vòng lặp `for (round in 1..MAX_ROUNDS)`, trước `callAI(prompt)`, append instruction vào prompt yêu cầu AI PHẢI trả lời bằng text tổng hợp, KHÔNG ĐƯỢC gọi thêm tool.
   - Thêm `internal const val FINAL_ROUND_INSTRUCTION` (internal visibility để unit test có thể access)
   - Append instruction vào `prompt`: `prompt = "$prompt\n--- SYSTEM ---\n$FINAL_ROUND_INSTRUCTION"` trước `callAI(prompt)` cuối cùng

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatResponseParser.kt`

**Function**: `parse()`

**Specific Changes**:
2. **Thêm empty reply fallback**: Sau khi parse xong (bất kỳ path nào), kiểm tra nếu `reply` là empty string hoặc chỉ chứa whitespace → thay bằng fallback message.
   - Thêm `internal const val EMPTY_REPLY_FALLBACK` (internal visibility để unit test và PBT có thể access)
   - Thêm `private fun ensureNonEmptyReply(response: ChatResponse): ChatResponse`
   - Gọi `ensureNonEmptyReply()` trước khi return từ cả 2 path trong `parse()`: `tryStandardDecode` và `tryExtractJson`
   - Fallback message: "Tôi đã thu thập thông tin nhưng không thể tổng hợp câu trả lời. Vui lòng thử lại hoặc đặt câu hỏi cụ thể hơn."

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/components/AIChatSidebar.kt`

**Function**: `handleSuccess()`

**Specific Changes**:
3. **Guard empty reply trong handleSuccess()**: Kiểm tra `chatResp.reply.isBlank()` trước khi render.
   - Thêm `private const val EMPTY_REPLY_FALLBACK = "AI không thể trả lời lúc này. Vui lòng thử lại."`
   - Nếu reply trống → render fallback message dưới dạng assistant message bình thường (dùng `ChatMessageRenderer.renderMessage("assistant", displayReply)`) thay vì gọi `showError()`, để UX nhất quán với chat flow

## Testing Strategy

### Validation Approach

Testing strategy theo 2 phase: (1) surface counterexamples trên unfixed code để confirm root cause, (2) verify fix works và preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples demonstrate bug TRƯỚC khi implement fix. Confirm root cause analysis.

**Test Plan**: Mock `callAI` trả về tool call JSON liên tục qua 5 rounds. Verify rằng response cuối cùng có `reply: ""`.

**Test Cases**:
1. **All-rounds tool call test**: Mock callAI luôn trả về mcpToolCall JSON → verify reply trống (will fail on unfixed code — confirms bug)
2. **Tool call JSON parse test**: Pass tool call JSON vào ChatResponseParser.parse() → verify reply behavior (will show empty reply on unfixed code)
3. **Frontend empty reply test**: Simulate ChatResponse với reply="" → verify UI behavior

**Expected Counterexamples**:
- `McpAgenticLoop.execute()` trả về `ChatResponse(reply = "{\"mcpToolCall\":...}")` hoặc `reply = ""`
- `ChatResponseParser.parse(toolCallJson)` trả về reply chứa raw JSON thay vì meaningful text

### Fix Checking

**Goal**: Verify rằng cho tất cả inputs where bug condition holds, fixed system trả về non-empty meaningful reply.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := McpAgenticLoop.execute'(input)
  ASSERT result.reply ≠ ""
  ASSERT result.reply does NOT contain "mcpToolCall"
  ASSERT result.reply IS human-readable text
END FOR
```

### Preservation Checking

**Goal**: Verify rằng cho tất cả inputs where bug condition does NOT hold, fixed system produce same result as original.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT McpAgenticLoop.execute(input) = McpAgenticLoop.execute'(input)
END FOR
```

**Testing Approach**: Property-based testing recommended cho preservation checking vì:
- Generate nhiều test cases tự động across input domain
- Catch edge cases mà manual unit tests có thể miss
- Strong guarantees rằng behavior unchanged cho all non-buggy inputs

**Test Plan**: Observe behavior trên UNFIXED code cho non-tool-call responses, sau đó write PBT capturing behavior đó.

**Test Cases**:
1. **Plain text preservation**: Verify ChatResponseParser.parse(plainText) trả về same result trước và sau fix
2. **Valid JSON preservation**: Verify ChatResponseParser.parse(validJson) trả về same result
3. **Early exit preservation**: Verify McpAgenticLoop exit ngay khi AI trả lời text ở round < MAX_ROUNDS
4. **Blank input preservation**: Verify ChatResponseParser.parse("") vẫn trả về startup message

### Unit Tests

- `McpAgenticLoopEmptyReplyTest.kt` — Test McpAgenticLoop inject "stop tools" instruction khi đạt MAX_ROUNDS + early exit preservation khi AI trả lời text ở round 1
- `ChatResponseParserEmptyReplyTest.kt` — Test ChatResponseParser fallback cho empty reply, tool call JSON without reply field, blank input startup message, valid JSON preservation

### Property-Based Tests

- `ChatEmptyReplyPropertyTest.kt` (Kotest PBT, 25 iterations mỗi property):
  - Property 1: Generate random tool call JSON (varying serverId, toolName), verify ChatResponseParser luôn trả về non-empty reply không chứa "mcpToolCall"
  - Property 2a: Generate random valid ChatResponse JSON, verify parse result preserves exact reply text
  - Property 2b: Generate random plain text strings, verify parse result preserves raw text

### Integration Tests

- Test full flow: user message → McpAgenticLoop (5 rounds tool calls) → ChatResponseParser → verify non-empty reply
- Test full flow: user message → McpAgenticLoop (early exit) → verify reply unchanged
- E2E: verify UI không render bubble trống khi API trả về empty reply
