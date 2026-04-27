# Tài liệu Yêu cầu Sửa lỗi — Ticket Intelligence UX Bugs

## Giới thiệu

Trang Ticket Intelligence (`/Ticket_Intelligence`) có 2 lỗi UX ảnh hưởng đến trải nghiệm người dùng khi tương tác với combobox chọn ticket:

1. **Khôi phục trạng thái chậm khi quay lại trang** — Khi người dùng đã chọn ticket, chuyển sang trang khác rồi quay lại, combobox hiển thị trống trong khi chờ API load danh sách ticket. Người dùng phải đợi API trả về mới thấy ticket đã chọn trước đó, gây trải nghiệm kém.

2. **Combobox không tự động chọn khi gõ đúng ticket ID** — Khi người dùng gõ chính xác ticket ID (ví dụ "ICL2-1133") vào combobox nhưng không click chọn từ dropdown, mà click ra ngoài, dropdown đóng lại mà không tự động chọn ticket khớp. Nội dung hiển thị vẫn là của ticket đã chọn trước đó, không phải ticket vừa gõ.

## Phân tích Lỗi

### Hành vi Hiện tại (Lỗi)

1.1 WHEN người dùng đã chọn ticket trên trang Ticket Intelligence, chuyển sang trang khác rồi quay lại THEN hệ thống hiển thị combobox trống trong khi chờ API `/api/projects/{key}/tickets/status` trả về danh sách ticket, sau đó mới khôi phục ticket đã chọn — gây delay đáng kể và trải nghiệm UX kém

1.2 WHEN người dùng gõ chính xác một ticket ID hợp lệ (ví dụ "ICL2-1133") vào combobox và click ra ngoài (blur) mà không chọn từ dropdown THEN hệ thống chỉ đóng dropdown mà không tự động chọn ticket khớp, combobox giữ nguyên text đã gõ nhưng `selectedTicket` vẫn là ticket cũ

1.3 WHEN người dùng gõ ticket ID hợp lệ vào combobox, không chọn từ dropdown, và click ra ngoài THEN nội dung hiển thị (CONTEXT, EVOLUTION, COMPLEXITY tabs) vẫn thuộc về ticket đã chọn trước đó, không phải ticket vừa gõ — gây nhầm lẫn giữa text trong combobox và nội dung hiển thị

### Hành vi Mong đợi (Đúng)

2.1 WHEN người dùng đã chọn ticket trên trang Ticket Intelligence, chuyển sang trang khác rồi quay lại THEN hệ thống SHALL hiển thị ngay lập tức ticket đã chọn trước đó trong combobox (từ sessionStorage) mà không cần chờ API load danh sách ticket — combobox hiển thị text ticket ID và summary ngay khi trang render

2.2 WHEN người dùng gõ chính xác một ticket ID hợp lệ vào combobox và click ra ngoài (blur/click outside) THEN hệ thống SHALL tự động tìm và chọn ticket khớp chính xác với ticket ID đã gõ trong danh sách ticket

2.3 WHEN hệ thống tự động chọn ticket từ text đã gõ (theo 2.2) THEN hệ thống SHALL cập nhật `selectedTicket`, hiển thị nội dung phân tích tương ứng, và lưu trạng thái vào sessionStorage — giống hệt như khi người dùng click chọn từ dropdown

2.4 WHEN người dùng gõ text không khớp chính xác với bất kỳ ticket ID nào và click ra ngoài THEN hệ thống SHALL khôi phục combobox về trạng thái ticket đã chọn trước đó (nếu có) hoặc xóa text nếu chưa chọn ticket nào

### Hành vi Không thay đổi (Ngăn Regression)

3.1 WHEN người dùng click chọn ticket từ dropdown THEN hệ thống SHALL CONTINUE TO chọn ticket, hiển thị nội dung phân tích, và cập nhật combobox text như hiện tại

3.2 WHEN người dùng gõ text vào combobox để lọc danh sách ticket THEN hệ thống SHALL CONTINUE TO lọc và hiển thị dropdown với các ticket khớp sau 250ms debounce

3.3 WHEN người dùng chọn ticket và click nút ANALYZE/RE-ANALYZE THEN hệ thống SHALL CONTINUE TO thực hiện phân tích ticket như hiện tại

3.4 WHEN người dùng navigate đến trang Ticket Intelligence qua ChatAction với ticketKey context THEN hệ thống SHALL CONTINUE TO tự động chọn ticket từ navigation context (ưu tiên hơn state restore)

3.5 WHEN trang Ticket Intelligence load lần đầu (không có state đã lưu) THEN hệ thống SHALL CONTINUE TO hiển thị combobox trống và load danh sách ticket từ API

3.6 WHEN API load danh sách ticket thất bại THEN hệ thống SHALL CONTINUE TO hiển thị thông báo lỗi với nút retry

---

## Điều kiện Lỗi (Bug Condition)

### Bug 1 — Khôi phục trạng thái chậm

```pascal
FUNCTION isBugCondition_SlowRestore(X)
  INPUT: X of type PageNavigation
  OUTPUT: boolean
  
  // Lỗi xảy ra khi quay lại trang và có state đã lưu trong sessionStorage
  RETURN X.hasSessionState = true AND X.isReturningToPage = true
END FUNCTION
```

```pascal
// Property: Fix Checking — Khôi phục trạng thái tức thì
FOR ALL X WHERE isBugCondition_SlowRestore(X) DO
  result ← renderPage'(X)
  ASSERT result.comboboxText = X.savedTicketId + " — " + X.savedTicketSummary
    AND result.comboboxDisplayedImmediately = true
    AND result.noWaitForApiLoad = true
END FOR
```

```pascal
// Property: Preservation Checking — Trang load lần đầu không bị ảnh hưởng
FOR ALL X WHERE NOT isBugCondition_SlowRestore(X) DO
  ASSERT renderPage(X) = renderPage'(X)
END FOR
```

### Bug 2 — Combobox không tự động chọn khi blur

```pascal
FUNCTION isBugCondition_NoAutoSelect(X)
  INPUT: X of type ComboboxInteraction
  OUTPUT: boolean
  
  // Lỗi xảy ra khi gõ ticket ID hợp lệ và click ra ngoài mà không chọn từ dropdown
  RETURN X.typedText matches exactTicketId IN ticketList
    AND X.dismissedWithoutSelection = true
END FUNCTION
```

```pascal
// Property: Fix Checking — Tự động chọn ticket khớp khi blur
FOR ALL X WHERE isBugCondition_NoAutoSelect(X) DO
  result ← handleBlur'(X)
  ASSERT result.selectedTicket.ticketId = X.matchedTicketId
    AND result.contentDisplayed = analysisOf(X.matchedTicketId)
    AND result.stateSaved = true
END FOR
```

```pascal
// Property: Preservation Checking — Click chọn từ dropdown vẫn hoạt động
FOR ALL X WHERE NOT isBugCondition_NoAutoSelect(X) DO
  ASSERT handleBlur(X) = handleBlur'(X)
END FOR
```
