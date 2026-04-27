# Bugfix Requirements Document

## Introduction

Khi user hỏi về ticket cụ thể trong AI Chat (ví dụ: "Cho tôi hỏi ticket CRP-3203 đã phân tích chưa?"), AI Chat trả về reply trống. API response: `{"reply":"","actions":[],"references":[],"contextUsage":56}`. Frontend hiển thị chat bubble trống — typing indicator biến mất nhưng không có nội dung hiển thị.

Bug xảy ra do chuỗi 3 vấn đề liên tiếp:
1. **McpAgenticLoop** dùng hết 5 rounds gọi MCP tools mà AI không bao giờ trả lời text. Sau round cuối, AI response vẫn chứa tool call JSON thay vì text reply.
2. **ChatResponseParser** nhận tool call JSON (không có "reply" field) → parse thành `reply: ""`.
3. **AIChatSidebar** render `chatResp.reply` trực tiếp mà không kiểm tra trống → tạo bubble trống.

Bug ảnh hưởng đến mọi câu hỏi liên quan đến ticket cụ thể — một use case rất phổ biến của AI Chat.

---

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN user gửi message chứa ticket ID (ví dụ: "ticket CRP-3203 đã phân tích chưa?") VÀ McpAgenticLoop chạy hết MAX_ROUNDS (5 rounds) mà AI liên tục gọi MCP tools THEN hệ thống trả về AI response cuối cùng vẫn là tool call JSON thay vì text reply, vì không có instruction yêu cầu AI ngừng gọi tools và tổng hợp câu trả lời

1.2 WHEN AI response cuối cùng sau MAX_ROUNDS là tool call JSON (không chứa "reply" field hoặc text content) THEN ChatResponseParser.parse() không tìm thấy reply text trong bất kỳ format nào (standard, alternative keys, detector) và trả về `reply: ""` thay vì fallback message có ý nghĩa

1.3 WHEN API response trả về `ChatResponse` với `reply` là chuỗi trống (`""`) THEN AIChatSidebar.handleSuccess() render chat bubble trống — typing indicator biến mất nhưng bubble không có nội dung, user không nhận được thông tin gì

### Expected Behavior (Correct)

2.1 WHEN McpAgenticLoop đạt round cuối (round = MAX_ROUNDS) THEN hệ thống SHALL inject instruction vào prompt yêu cầu AI PHẢI trả lời bằng text tổng hợp từ các tool results đã thu thập, KHÔNG ĐƯỢC gọi thêm tool — đảm bảo AI response cuối cùng luôn chứa text reply

2.2 WHEN ChatResponseParser nhận AI response mà không tìm thấy reply text (reply rỗng hoặc chỉ chứa tool call JSON) THEN hệ thống SHALL trả về fallback message có ý nghĩa (ví dụ: "Tôi đã thu thập thông tin nhưng không thể tổng hợp câu trả lời. Vui lòng thử lại hoặc đặt câu hỏi cụ thể hơn.") thay vì chuỗi trống

2.3 WHEN ChatResponse.reply là chuỗi trống hoặc chỉ chứa whitespace THEN AIChatSidebar.handleSuccess() SHALL hiển thị thông báo lỗi thân thiện cho user (ví dụ: "AI không thể trả lời. Vui lòng thử lại.") thay vì render bubble trống

### Unchanged Behavior (Regression Prevention)

3.1 WHEN user gửi message đơn giản không liên quan đến ticket (ví dụ: "Hello", "Giới thiệu về dự án") THEN hệ thống SHALL CONTINUE TO trả về reply text bình thường mà không bị ảnh hưởng bởi logic mới

3.2 WHEN McpAgenticLoop phát hiện AI response không chứa tool call (text reply thuần) trước khi đạt MAX_ROUNDS THEN hệ thống SHALL CONTINUE TO trả về response ngay lập tức mà không chờ hết MAX_ROUNDS

3.3 WHEN AI response là JSON hợp lệ chứa "reply" field có nội dung THEN ChatResponseParser SHALL CONTINUE TO parse và trả về reply text đúng như hiện tại

3.4 WHEN AI response là plain text (không phải JSON) THEN ChatResponseParser SHALL CONTINUE TO sử dụng plain text fallback trả về raw text làm reply

3.5 WHEN ChatResponse.reply có nội dung hợp lệ (không trống) THEN AIChatSidebar.handleSuccess() SHALL CONTINUE TO render chat bubble với nội dung reply bình thường

3.6 WHEN McpAgenticLoop thực thi tool call thành công trong các round trước MAX_ROUNDS THEN hệ thống SHALL CONTINUE TO inject tool result vào prompt và tiếp tục vòng lặp agentic bình thường

---

## Bug Condition

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type ChatRequest
  OUTPUT: boolean

  // Bug xảy ra khi message khiến AI liên tục gọi MCP tools
  // qua hết MAX_ROUNDS mà không bao giờ trả lời text
  aiResponses ← runAgenticLoop(X.message, MAX_ROUNDS)
  RETURN ALL responses in aiResponses contain tool call JSON
         AND NONE of responses contain text reply
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property: Fix Checking — AI luôn trả về text reply sau MAX_ROUNDS
FOR ALL X WHERE isBugCondition(X) DO
  result ← McpAgenticLoop.execute'(X)
  ASSERT result.reply ≠ ""
  ASSERT result.reply IS meaningful text (not tool call JSON)
END FOR
```

### Property Specification — Preservation Checking

```pascal
// Property: Preservation Checking — Behavior không đổi cho non-buggy inputs
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT McpAgenticLoop.execute(X) = McpAgenticLoop.execute'(X)
END FOR
```
