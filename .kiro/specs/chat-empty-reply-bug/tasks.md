# Tasks — Chat Empty Reply Bug Fix

## Task 1: McpAgenticLoop — Inject "stop tools" instruction ở round cuối

- [x] 1.1 Thêm constant `FINAL_ROUND_INSTRUCTION` trong `McpAgenticLoop` chứa instruction yêu cầu AI ngừng gọi tools và tổng hợp câu trả lời bằng text
  - File: `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`
  - Instruction text: "IMPORTANT: You have used all available tool rounds. You MUST now provide a final text answer summarizing the information gathered. Do NOT call any more tools. Respond with a helpful text reply in the same language as the user's question."
- [x] 1.2 Trong `execute()`, append `FINAL_ROUND_INSTRUCTION` vào `prompt` trước `callAI(prompt)` cuối cùng (sau vòng lặp `for (round in 1..MAX_ROUNDS)`)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/chat/McpAgenticLoop.kt`
  - Thêm dòng: `prompt = "$prompt\n--- SYSTEM ---\n$FINAL_ROUND_INSTRUCTION"` trước `val finalResult = callAI(prompt)`
- [x] 1.3 Unit test: verify prompt cuối chứa FINAL_ROUND_INSTRUCTION khi AI gọi tools hết MAX_ROUNDS
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopEmptyReplyTest.kt`
  - Mock callAI capture prompt, verify instruction present

## Task 2: ChatResponseParser — Fallback cho empty reply

- [x] 2.1 Thêm private constant `EMPTY_REPLY_FALLBACK` chứa fallback message
  - File: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatResponseParser.kt`
  - Message: "Tôi đã thu thập thông tin nhưng không thể tổng hợp câu trả lời. Vui lòng thử lại hoặc đặt câu hỏi cụ thể hơn."
- [x] 2.2 Thêm private function `ensureNonEmptyReply(response: ChatResponse): ChatResponse` — nếu `reply.isBlank()` thì thay bằng fallback message
  - File: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatResponseParser.kt`
- [x] 2.3 Gọi `ensureNonEmptyReply()` trong `parse()` 4-param trước khi return từ mỗi path (tryStandardDecode, tryExtractJson)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/chat/ChatResponseParser.kt`
  - Wrap return value: thay `return result` bằng `return ensureNonEmptyReply(result)`
- [x] 2.4 Unit test: verify parse() trả về fallback khi input là tool call JSON không có reply field
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatResponseParserEmptyReplyTest.kt`
- [x] 2.5 Unit test: verify parse() trả về fallback khi input là JSON với `"reply":""`
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatResponseParserEmptyReplyTest.kt`

## Task 3: AIChatSidebar — Guard empty reply trong handleSuccess()

- [x] 3.1 Trong `handleSuccess()`, thêm guard check `chatResp.reply.isBlank()` — nếu trống thì hiển thị fallback message thay vì render bubble trống
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/components/AIChatSidebar.kt`
  - Fallback: render message "AI không thể trả lời lúc này. Vui lòng thử lại." hoặc gọi `showError()`

## Task 4: Property-Based Tests — Fix Checking & Preservation

- [x] 4.1 PBT Fix Checking: generate random tool call JSON (varying serverId, toolName), pass vào ChatResponseParser.parse(), verify reply luôn non-empty và không chứa "mcpToolCall"
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatEmptyReplyPropertyTest.kt`
  - Validates: Property 1 (Bug Condition)
- [x] 4.2 PBT Preservation: generate random valid ChatResponse JSON (với reply non-empty), verify parse() trả về đúng reply text — behavior unchanged
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatEmptyReplyPropertyTest.kt`
  - Validates: Property 2 (Preservation)
- [x] 4.3 PBT Preservation: generate random plain text strings (non-JSON), verify parse() trả về raw text — behavior unchanged
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatEmptyReplyPropertyTest.kt`
  - Validates: Property 2 (Preservation)

## Task 5: Regression Tests — Verify existing behavior unchanged

- [x] 5.1 Verify McpAgenticLoop early exit khi AI trả lời text ở round 1 (không chờ MAX_ROUNDS)
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/McpAgenticLoopEmptyReplyTest.kt`
- [x] 5.2 Verify ChatResponseParser.parse() cho blank input vẫn trả về "AI đang khởi động" message
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatResponseParserEmptyReplyTest.kt`
- [x] 5.3 Verify ChatResponseParser.parse() cho valid JSON với "reply" field vẫn trả về đúng reply
  - File: `server/src/jvmTest/kotlin/com/assistant/server/chat/ChatResponseParserEmptyReplyTest.kt`
