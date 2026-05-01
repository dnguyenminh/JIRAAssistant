# Ticket Intelligence UX Bugs — Thiết kế Sửa lỗi

## Overview

Trang Ticket Intelligence có 2 lỗi UX liên quan đến combobox chọn ticket:

1. **Khôi phục trạng thái chậm** — Khi quay lại trang, combobox hiển thị trống trong khi chờ API load danh sách ticket. Nguyên nhân: `restoreState()` phụ thuộc vào `ticketList` đã được load xong, nhưng `loadTicketList()` là async. Fix: hiển thị ngay text ticket đã lưu từ sessionStorage vào combobox input mà không cần chờ API.

2. **Không tự động chọn khi blur** — Gõ đúng ticket ID rồi click ra ngoài, dropdown đóng nhưng không auto-select ticket khớp. Nguyên nhân: click-outside handler chỉ gọi `hideDropdown()`. Fix: trước khi ẩn dropdown, kiểm tra text đã gõ có khớp chính xác ticket ID nào không, nếu có thì auto-select.

## Glossary

- **Bug_Condition (C)**: Điều kiện kích hoạt lỗi — (1) quay lại trang khi có state đã lưu, hoặc (2) gõ đúng ticket ID và click ra ngoài
- **Property (P)**: Hành vi mong đợi — (1) combobox hiển thị ticket ngay lập tức, hoặc (2) tự động chọn ticket khớp
- **Preservation**: Hành vi không thay đổi — click chọn từ dropdown, filter debounce, analyze, navigation context
- **`TicketCombobox`**: Object trong `TicketCombobox.kt` quản lý combobox search/filter/select ticket
- **`TicketStateManager`**: Object trong `TicketStateManager.kt` lưu/khôi phục `TicketPageState` từ sessionStorage
- **`TicketPageState`**: Data class chứa `selectedTicketId`, `selectedTicketSummary`, `activeTab`, `analysisResult` — lưu trong sessionStorage
- **`TicketAnalysisStatus`**: Data class chứa `ticketId`, `ticketSummary`, `analysisState` — trả về từ API
- **`restoreState()`**: Hàm trong `TicketIntelligencePage` khôi phục state đã lưu khi quay lại trang (Phase 2 — deferred restore sau khi API load)
- **`restoreSelectedTicket()`**: Hàm tìm ticket trong `ticketList` theo `selectedTicketId` rồi gọi `selectTicket()`. Nếu không tìm thấy, xóa combobox text và clear state
- **`immediateRestoreFromSession()`**: Hàm Phase 1 — đọc `TicketPageState` từ sessionStorage và set combobox text ngay lập tức TRƯỚC khi API load
- **`handleClickOutside()`**: Hàm trong `TicketCombobox` xử lý click-outside: auto-select ticket khớp, restore input khi không khớp, hoặc chỉ hide dropdown
- **`setInputText()`**: Public method trong `TicketCombobox` cho phép set text combobox từ bên ngoài

## Bug Details

### Bug Condition

Hai lỗi có điều kiện kích hoạt khác nhau:

**Bug 1 — Khôi phục trạng thái chậm:**
Lỗi xảy ra khi người dùng quay lại trang Ticket Intelligence và có state đã lưu trong sessionStorage. Hàm `restoreSelectedTicket()` gọi `TicketCombobox.ticketList.find { it.ticketId == saved.selectedTicketId }` — nhưng `ticketList` vẫn là `emptyList()` vì `loadTicketList()` (async API call) chưa trả về. Kết quả: `find` trả về `null`, combobox hiển thị trống.

**Formal Specification:**
```
FUNCTION isBugCondition_SlowRestore(input)
  INPUT: input of type PageNavigation
  OUTPUT: boolean

  savedState := TicketStateManager.restore()
  RETURN savedState != null
         AND savedState.selectedTicketId.isNotBlank()
         AND TicketCombobox.ticketList.isEmpty()
         AND NOT NavigationContext.hasContext("ticket_intelligence")
END FUNCTION
```

**Bug 2 — Không tự động chọn khi blur:**
Lỗi xảy ra khi người dùng gõ chính xác ticket ID vào combobox rồi click ra ngoài (click-outside). Handler hiện tại chỉ gọi `hideDropdown()` mà không kiểm tra text đã gõ có khớp ticket nào không.

**Formal Specification:**
```
FUNCTION isBugCondition_NoAutoSelect(input)
  INPUT: input of type ClickOutsideEvent
  OUTPUT: boolean

  typedText := getInputValue("ticket-search").trim()
  RETURN typedText.isNotBlank()
         AND ticketList.any { it.ticketId.equals(typedText, ignoreCase=true) }
         AND (selectedTicket == null OR selectedTicket.ticketId != typedText)
         AND clickIsOutsideComboboxAndDropdown(input)
END FUNCTION
```

### Examples

- **Bug 1 — Quay lại trang**: Người dùng chọn ticket "ICL2-1133", chuyển sang Dashboard, quay lại Ticket Intelligence → combobox trống 1-3 giây chờ API → sau khi API trả về, `restoreState()` đã chạy xong (với `ticketList` rỗng) nên không khôi phục được
- **Bug 1 — Mong đợi**: Combobox hiển thị ngay "ICL2-1133 — Fix login timeout" từ sessionStorage, không cần chờ API
- **Bug 2 — Gõ đúng ticket ID**: Người dùng gõ "ICL2-1133" vào combobox, dropdown hiện ticket khớp, nhưng thay vì click chọn, người dùng click ra ngoài → dropdown đóng, `selectedTicket` vẫn là ticket cũ (hoặc null)
- **Bug 2 — Mong đợi**: Khi click ra ngoài, hệ thống tự động chọn "ICL2-1133" vì text khớp chính xác
- **Bug 2 — Text không khớp**: Người dùng gõ "ICL2-999" (không tồn tại) rồi click ra ngoài → combobox khôi phục về ticket đã chọn trước đó hoặc xóa text

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Click chọn ticket từ dropdown phải tiếp tục hoạt động như hiện tại (`selectTicket()` flow)
- Filter debounce 250ms khi gõ text vào combobox phải giữ nguyên
- Nút ANALYZE/RE-ANALYZE phải tiếp tục hoạt động bình thường
- Navigation context từ ChatAction phải tiếp tục ưu tiên hơn state restore
- Trang load lần đầu (không có state) phải hiển thị combobox trống và load ticket list từ API
- API error handling (hiển thị lỗi + nút retry) phải giữ nguyên
- Auto-load cached analysis results khi chọn ticket đã analyzed phải giữ nguyên
- Status badge và action button update khi chọn ticket phải giữ nguyên

**Scope:**
Tất cả tương tác KHÔNG liên quan đến (1) quay lại trang với state đã lưu, hoặc (2) click-outside khi đã gõ text khớp ticket ID — phải hoàn toàn không bị ảnh hưởng bởi fix.

## Hypothesized Root Cause

### Bug 1 — Race condition giữa API load và state restore (ĐÃ FIX)

Trong `TicketIntelligencePage.render()`, flow sau khi fix:
```kotlin
scope.launch {
    val html = ApiClient.loadTemplate("ticket-intelligence")
    container.innerHTML = html
    bindEvents()
    applyRBAC()
    immediateRestoreFromSession()          // ← Phase 1: hiển thị ngay từ sessionStorage
    TicketCombobox.loadTicketList()         // ← async, chờ API trả về
    if (!applyNavigationContext()) restoreState()  // ← Phase 2: đồng bộ selectedTicket
}
```

Fix đã thêm `immediateRestoreFromSession()` — đọc `TicketPageState` từ sessionStorage và gọi `TicketCombobox.setInputText("ticketId — summary")` ngay lập tức TRƯỚC khi `loadTicketList()`. Sau khi API trả về, `restoreSelectedTicket()` đồng bộ `selectedTicket` object đầy đủ từ `ticketList`. Nếu ticket không tìm thấy (đã bị xóa), xóa combobox text và clear state.

**Root cause đã xác nhận**: Không có cơ chế hiển thị tạm thời (optimistic display) từ sessionStorage trong khi chờ API.

### Bug 2 — Click-outside handler thiếu logic auto-select (ĐÃ FIX)

Trong `TicketCombobox.bindInputEvents()`, click-outside handler sau khi fix:
```kotlin
document.addEventListener("click", { e ->
    val target = e.target as? HTMLElement ?: return@addEventListener
    val cb = document.getElementById("ticket-combobox")
    val dd = getDropdown()
    val clickInCombobox = cb != null && cb.contains(target)
    val clickInDropdown = dd != null && dd.contains(target)
    if (!clickInCombobox && !clickInDropdown) handleClickOutside()
})
```

`handleClickOutside()` xử lý 3 trường hợp:
```kotlin
private fun handleClickOutside() {
    val typedText = (document.getElementById("ticket-search") as? HTMLInputElement)
        ?.value?.trim() ?: ""
    val match = ticketList.find { it.ticketId.equals(typedText, ignoreCase = true) }
    when {
        match != null && match.ticketId != selectedTicket?.ticketId -> selectTicket(match)
        match == null && typedText.isNotBlank() -> { restoreInputOnBlur(); hideDropdown() }
        else -> hideDropdown()
    }
}
```

`restoreInputOnBlur()` khôi phục input về ticket đã chọn trước đó hoặc xóa text:
```kotlin
private fun restoreInputOnBlur() {
    val input = document.getElementById("ticket-search") as? HTMLInputElement ?: return
    val current = selectedTicket
    input.value = if (current != null) "${current.ticketId} — ${current.ticketSummary}" else ""
}
```

**Root cause đã xác nhận**: Handler cũ chỉ gọi `hideDropdown()` — thiếu logic kiểm tra text khớp ticket ID.

## Correctness Properties

Property 1: Bug Condition - Khôi phục trạng thái tức thì từ sessionStorage

_For any_ page navigation event mà có state đã lưu trong sessionStorage (isBugCondition_SlowRestore returns true), hệ thống SHALL hiển thị ngay lập tức text ticket đã lưu (ticketId + summary) trong combobox input mà không cần chờ API load danh sách ticket. Khi API trả về sau đó, hệ thống SHALL đồng bộ internal state (`selectedTicket`) với ticket object đầy đủ từ API response.

**Validates: Requirements 2.1**

Property 2: Bug Condition - Tự động chọn ticket khi blur với text khớp

_For any_ click-outside event mà text trong combobox khớp chính xác với một ticket ID trong danh sách (isBugCondition_NoAutoSelect returns true), hệ thống SHALL tự động gọi `selectTicket()` cho ticket khớp, cập nhật `selectedTicket`, hiển thị nội dung phân tích, và lưu state vào sessionStorage — giống hệt khi click chọn từ dropdown.

**Validates: Requirements 2.2, 2.3**

Property 3: Bug Condition - Khôi phục combobox khi text không khớp

_For any_ click-outside event mà text trong combobox KHÔNG khớp chính xác với bất kỳ ticket ID nào, hệ thống SHALL khôi phục combobox về trạng thái ticket đã chọn trước đó (nếu có) hoặc xóa text nếu chưa chọn ticket nào.

**Validates: Requirements 2.4**

Property 4: Preservation - Hành vi hiện tại không thay đổi

_For any_ tương tác mà KHÔNG thuộc bug condition (click chọn từ dropdown, filter text, analyze, navigation context, first load), hệ thống SHALL hoạt động giống hệt code hiện tại, bảo toàn toàn bộ flow chọn ticket, filter, analyze, và state management.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation (ĐÃ HOÀN THÀNH)

### Changes Implemented

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/TicketIntelligencePage.kt`

**Function**: `immediateRestoreFromSession()`, `restoreState()`, `restoreSelectedTicket()`

**Specific Changes:**

1. **Thêm `immediateRestoreFromSession()`**: Method mới đọc `TicketPageState` từ sessionStorage và set combobox input text ngay lập tức via `TicketCombobox.setInputText()`. Cũng restore analysis result nếu có. Được gọi TRƯỚC `loadTicketList()` trong `render()` flow.

2. **Cập nhật `restoreSelectedTicket()`**: Thêm xử lý khi ticket không tìm thấy trong `ticketList` (ticket đã bị xóa) — xóa combobox text via `setInputText("")`, null out `selectedTicket`, và gọi `TicketStateManager.clear()`.

3. **Cập nhật flow trong `render()`**: `bindEvents() → applyRBAC() → immediateRestoreFromSession() → loadTicketList() → applyNavigationContext()/restoreState()`

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`

**Function**: `handleClickOutside()`, `restoreInputOnBlur()`, `setInputText()`

**Specific Changes:**

4. **Thêm `handleClickOutside()`**: Thay thế `hideDropdown()` trực tiếp trong click-outside handler. Đọc giá trị input, tìm ticket khớp chính xác theo ticketId (case-insensitive), nếu tìm thấy thì gọi `selectTicket()`.

5. **Thêm `restoreInputOnBlur()`**: Khi text không khớp ticket nào, khôi phục input value về ticket đã chọn trước đó hoặc xóa text nếu chưa chọn.

6. **Thêm `setInputText()`**: Public method cho phép `TicketIntelligencePage` set text combobox từ bên ngoài mà không cần truy cập DOM trực tiếp.

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/DeepAnalysisDisplayModels.kt`

**Specific Changes:**

7. **Thêm field `selectedTicketSummary`** vào `TicketPageState` (đã implement):
   ```kotlin
   data class TicketPageState(
       val selectedTicketId: String = "",
       val selectedTicketSummary: String = "",  // NEW
       val activeTab: String = "context",
       val analysisResult: AnalysisResponse? = null
   )
   ```

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`

**Function**: `saveSelection()` — đã cập nhật để lưu summary

**Specific Changes:**

8. **Cập nhật `saveSelection()`** để lưu `selectedTicketSummary` (đã implement):
   ```kotlin
   private fun saveSelection(ticketId: String) {
       TicketStateManager.save(TicketPageState(
           selectedTicketId = ticketId,
           selectedTicketSummary = selectedTicket?.ticketSummary ?: "",  // NEW
           activeTab = TicketResultTabs.activeTab,
           analysisResult = TicketResultTabs.currentAnalysis
       ))
   }
   ```

## Testing Strategy (ĐÃ HOÀN THÀNH)

### Validation Approach

Chiến lược testing theo 2 phase đã thực hiện thành công: (1) xác nhận lỗi tồn tại trên code chưa fix bằng exploratory tests, (2) xác nhận fix hoạt động đúng và không gây regression.

### Exploratory Bug Condition Checking (ĐÃ PASS)

**Test Files**:
- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/ticket/SlowRestoreBugConditionTest.kt` — Bug 1
- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/ticket/NoAutoSelectBugConditionTest.kt` — Bug 2

**Kết quả**: Tất cả exploration tests FAIL trên code chưa fix (xác nhận bug tồn tại), sau đó PASS trên code đã fix (xác nhận bug đã sửa).

**Test Cases**:
1. **Slow Restore Test**: Mô phỏng quay lại trang khi có state trong sessionStorage nhưng `ticketList` rỗng — kiểm tra combobox input value (sẽ fail: input trống)
2. **Auto-Select Blur Test**: Mô phỏng gõ ticket ID hợp lệ vào input rồi trigger click-outside event — kiểm tra `selectedTicket` (sẽ fail: selectedTicket không thay đổi)
3. **Non-Match Blur Test**: Mô phỏng gõ text không khớp rồi trigger click-outside — kiểm tra input value (sẽ fail: input giữ text không khớp thay vì restore)

**Expected Counterexamples**:
- Bug 1: `combobox.input.value == ""` khi có state đã lưu, vì `ticketList` rỗng tại thời điểm restore
- Bug 2: `selectedTicket` vẫn là ticket cũ sau khi gõ đúng ticket ID mới và click ra ngoài

### Fix Checking (ĐÃ PASS)

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition_SlowRestore(input) DO
  result := renderPage_fixed(input)
  ASSERT result.comboboxInputValue == savedTicketId + " — " + savedTicketSummary
  ASSERT result.displayedImmediately == true
END FOR

FOR ALL input WHERE isBugCondition_NoAutoSelect(input) DO
  result := handleClickOutside_fixed(input)
  ASSERT result.selectedTicket.ticketId == input.matchedTicketId
  ASSERT result.stateSaved == true
END FOR
```

### Preservation Checking (ĐÃ PASS)

**Test File**: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/ticket/PreservationPropertyTest.kt`

**Kết quả**: 5/5 preservation tests PASS trước và sau fix — không regression.

**Goal**: Xác nhận fix không ảnh hưởng hành vi hiện tại cho inputs KHÔNG thuộc bug condition.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition_SlowRestore(input)
                AND NOT isBugCondition_NoAutoSelect(input) DO
  ASSERT originalBehavior(input) == fixedBehavior(input)
END FOR
```

**Testing Approach**: Property-based testing được khuyến nghị cho preservation checking vì:
- Tự động sinh nhiều test cases trên toàn bộ input domain
- Phát hiện edge cases mà unit tests thủ công có thể bỏ sót
- Đảm bảo mạnh mẽ rằng hành vi không thay đổi cho tất cả non-buggy inputs

**Test Plan**: Quan sát hành vi trên code chưa fix cho các tương tác bình thường (click chọn, filter, analyze), sau đó viết property-based tests xác nhận hành vi giữ nguyên sau fix.

**Test Cases**:
1. **Dropdown Select Preservation**: Xác nhận click chọn ticket từ dropdown vẫn gọi `selectTicket()` đúng, cập nhật badge, button, và lưu state
2. **Filter Preservation**: Xác nhận gõ text vào combobox vẫn filter danh sách sau 250ms debounce
3. **Navigation Context Preservation**: Xác nhận navigate từ ChatAction vẫn auto-select ticket từ context
4. **First Load Preservation**: Xác nhận trang load lần đầu (không có state) hiển thị combobox trống
5. **API Error Preservation**: Xác nhận khi API fail, hiển thị lỗi + nút retry

### Unit Tests

- Test `restoreState()` hiển thị text ngay lập tức từ sessionStorage khi `ticketList` rỗng
- Test `restoreState()` đồng bộ `selectedTicket` sau khi `loadTicketList()` hoàn tất
- Test click-outside handler auto-select khi text khớp chính xác ticket ID
- Test click-outside handler restore input khi text không khớp
- Test click-outside handler không thay đổi khi text rỗng
- Test `TicketPageState` serialize/deserialize với field `selectedTicketSummary` mới
- Test backward compatibility: `TicketPageState` deserialize từ JSON cũ (không có `selectedTicketSummary`)

### Property-Based Tests

- Sinh random `TicketPageState` với các giá trị `selectedTicketId` và `selectedTicketSummary` khác nhau, xác nhận immediate display luôn hiển thị đúng format
- Sinh random danh sách ticket IDs và random typed text, xác nhận auto-select chỉ kích hoạt khi text khớp chính xác (case-insensitive)
- Sinh random click events (inside/outside combobox), xác nhận preservation: click inside không trigger auto-select logic

### Integration Tests

- Test full flow: chọn ticket → chuyển trang → quay lại → combobox hiển thị ngay → API load → state đồng bộ
- Test full flow: gõ ticket ID → click ra ngoài → ticket được chọn → analysis results hiển thị
- Test full flow: gõ text không khớp → click ra ngoài → combobox restore về ticket cũ
- Test full flow: navigate từ ChatAction → auto-select ưu tiên hơn state restore
