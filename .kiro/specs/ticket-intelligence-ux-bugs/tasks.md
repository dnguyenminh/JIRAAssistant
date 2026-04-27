# Kế hoạch Triển khai — Ticket Intelligence UX Bugs

- [x] 1. Viết exploration test cho Bug Condition — Khôi phục trạng thái chậm
  - **Property 1: Bug Condition** - Slow State Restore
  - **QUAN TRỌNG**: Viết property-based test này TRƯỚC KHI implement fix
  - **MỤC TIÊU**: Tái hiện lỗi — xác nhận combobox hiển thị trống khi quay lại trang có state đã lưu
  - **Scoped PBT Approach**: Scope property vào trường hợp cụ thể: có `TicketPageState` trong sessionStorage với `selectedTicketId` không rỗng, nhưng `ticketList` đang rỗng (chưa load xong API)
  - **Bug Condition từ design**: `isBugCondition_SlowRestore(input)` — `savedState != null AND savedState.selectedTicketId.isNotBlank() AND TicketCombobox.ticketList.isEmpty() AND NOT NavigationContext.hasContext("ticket_intelligence")`
  - Mô phỏng: lưu `TicketPageState(selectedTicketId="ICL2-1133")` vào sessionStorage → gọi `restoreState()` khi `ticketList` rỗng → kiểm tra combobox input value
  - **Expected Behavior**: `combobox.input.value == "ICL2-1133 — <summary>"` và hiển thị ngay lập tức (từ Expected Behavior Properties trong design)
  - Chạy test trên code CHƯA FIX — **KẾT QUẢ MONG ĐỢI: Test FAIL** (xác nhận lỗi tồn tại)
  - **KHÔNG sửa test hoặc code khi test fail** — failure là đúng, chứng minh bug tồn tại
  - Ghi nhận counterexample: `combobox.input.value == ""` khi có state đã lưu, vì `ticketList` rỗng tại thời điểm restore
  - Đánh dấu task hoàn thành khi test đã viết, chạy, và failure đã được ghi nhận
  - _Requirements: 1.1, 2.1_

- [x] 2. Viết exploration test cho Bug Condition — Combobox không auto-select khi blur
  - **Property 1: Bug Condition** - No Auto-Select on Blur
  - **QUAN TRỌNG**: Viết property-based test này TRƯỚC KHI implement fix
  - **MỤC TIÊU**: Tái hiện lỗi — xác nhận click-outside không auto-select ticket khi text khớp chính xác ticket ID
  - **Scoped PBT Approach**: Scope property vào trường hợp cụ thể: gõ ticket ID hợp lệ (ví dụ "ICL2-1133") vào input, trigger click-outside event
  - **Bug Condition từ design**: `isBugCondition_NoAutoSelect(input)` — `typedText.isNotBlank() AND ticketList.any { it.ticketId.equals(typedText, ignoreCase=true) } AND (selectedTicket == null OR selectedTicket.ticketId != typedText) AND clickIsOutsideComboboxAndDropdown(input)`
  - Mô phỏng: set `ticketList` với ticket "ICL2-1133" → set input value = "ICL2-1133" → dispatch click event ngoài combobox → kiểm tra `selectedTicket`
  - **Expected Behavior**: `selectedTicket.ticketId == "ICL2-1133"` và state đã lưu vào sessionStorage (từ Expected Behavior Properties trong design)
  - Chạy test trên code CHƯA FIX — **KẾT QUẢ MONG ĐỢI: Test FAIL** (xác nhận lỗi tồn tại)
  - **KHÔNG sửa test hoặc code khi test fail** — failure là đúng, chứng minh bug tồn tại
  - Ghi nhận counterexample: `selectedTicket` vẫn là ticket cũ (hoặc null) sau khi gõ đúng ticket ID mới và click ra ngoài
  - Thêm test case cho text không khớp: gõ "ICL2-999" (không tồn tại) → click ra ngoài → combobox phải restore về ticket cũ hoặc xóa text
  - Đánh dấu task hoàn thành khi test đã viết, chạy, và failure đã được ghi nhận
  - _Requirements: 1.2, 1.3, 2.2, 2.3, 2.4_

- [x] 3. Viết preservation property tests (TRƯỚC KHI implement fix)
  - **Property 2: Preservation** - Existing Ticket Selection & Interaction Behavior
  - **QUAN TRỌNG**: Tuân thủ observation-first methodology
  - **Bước 1 — Observe**: Chạy code CHƯA FIX với các non-buggy inputs và ghi nhận hành vi thực tế:
    - Observe: click chọn ticket từ dropdown → `selectTicket()` được gọi, `selectedTicket` cập nhật, badge + button cập nhật, state lưu vào sessionStorage
    - Observe: gõ text vào combobox → `filterTickets()` được gọi sau 250ms debounce, dropdown hiển thị tickets khớp
    - Observe: focus vào combobox khi `ticketList` không rỗng → dropdown hiển thị toàn bộ danh sách (hoặc filtered)
    - Observe: click-outside khi input rỗng → chỉ `hideDropdown()`, không thay đổi `selectedTicket`
    - Observe: trang load lần đầu (không có state) → combobox trống, `ticketList` load từ API
  - **Bước 2 — Write property-based tests**: Viết tests xác nhận hành vi đã observe:
    - Property: Với mọi ticket trong `ticketList`, gọi `selectTicket(ticket)` → `selectedTicket == ticket` AND input value == `"${ticket.ticketId} — ${ticket.ticketSummary}"` AND state saved
    - Property: Với mọi query string, `filterTickets(query)` → `filteredTickets` chỉ chứa tickets có ticketId hoặc ticketSummary chứa query (case-insensitive)
    - Property: Với mọi click event INSIDE combobox hoặc dropdown → `hideDropdown()` KHÔNG được gọi
    - Property: Khi `ticketList` rỗng và không có state → combobox input value rỗng
  - **Bước 3 — Verify**: Chạy tests trên code CHƯA FIX
  - **KẾT QUẢ MONG ĐỢI: Tests PASS** (xác nhận baseline behavior cần bảo toàn)
  - Đánh dấu task hoàn thành khi tests đã viết, chạy, và passing trên code chưa fix
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Fix Bug 1 — Khôi phục trạng thái tức thì từ sessionStorage

  - [x] 4.1 Thêm field `selectedTicketSummary` vào `TicketPageState`
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/DeepAnalysisDisplayModels.kt`
    - Thêm `val selectedTicketSummary: String = ""` vào data class `TicketPageState`
    - Default value `""` đảm bảo backward compatibility khi deserialize JSON cũ (không có field này)
    - `ignoreUnknownKeys = true` trong `TicketStateManager.json` đã handle trường hợp này
    - _Bug_Condition: isBugCondition_SlowRestore(input) — savedState != null AND selectedTicketId.isNotBlank() AND ticketList.isEmpty()_
    - _Expected_Behavior: combobox hiển thị ngay "ticketId — summary" từ sessionStorage_
    - _Preservation: Serialize/deserialize TicketPageState phải backward compatible_
    - _Requirements: 2.1_

  - [x] 4.2 Cập nhật `saveSelection()` trong `TicketCombobox.kt` để lưu summary
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`
    - Trong `saveSelection(ticketId)`, thêm `selectedTicketSummary = selectedTicket?.ticketSummary ?: ""`
    - Cũng cập nhật `saveState()` trong `TicketAutoLoader.kt` để lưu summary
    - _Bug_Condition: Cần lưu summary để phase immediate display có đủ thông tin_
    - _Expected_Behavior: sessionStorage chứa cả ticketId và ticketSummary_
    - _Preservation: Flow saveSelection hiện tại không thay đổi, chỉ thêm field mới_
    - _Requirements: 2.1_

  - [x] 4.3 Thêm immediate display từ sessionStorage trong `TicketIntelligencePage.kt`
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/TicketIntelligencePage.kt`
    - Thêm method `immediateRestoreFromSession()`: đọc `TicketPageState` từ sessionStorage, nếu có `selectedTicketId` không rỗng → set combobox input value = `"${savedState.selectedTicketId} — ${savedState.selectedTicketSummary}"` ngay lập tức
    - Sử dụng `TicketCombobox.setInputText()` (method mới từ task 5.3) để set text
    - Gọi `immediateRestoreFromSession()` TRƯỚC `loadTicketList()` trong `render()` flow
    - Cũng restore analysis result ngay lập tức nếu có trong saved state
    - _Bug_Condition: isBugCondition_SlowRestore — ticketList rỗng khi restore_
    - _Expected_Behavior: combobox hiển thị text ngay, không chờ API_
    - _Preservation: Navigation context vẫn ưu tiên hơn state restore_
    - _Requirements: 2.1_

  - [x] 4.4 Thêm deferred restore sau khi API load xong
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/TicketIntelligencePage.kt`
    - Cập nhật `render()` flow: sau `loadTicketList()` hoàn tất, gọi `restoreSelectedTicket()` để đồng bộ `selectedTicket` object đầy đủ từ `ticketList`
    - `restoreSelectedTicket()` tìm ticket trong `ticketList` theo `selectedTicketId`, gọi `selectTicket()` để đồng bộ internal state
    - Nếu không tìm thấy (ticket đã bị xóa), xóa text combobox và clear state
    - _Bug_Condition: Phase 2 — đồng bộ selectedTicket object sau khi API trả về_
    - _Expected_Behavior: selectedTicket object đầy đủ, badge + button cập nhật đúng_
    - _Preservation: restoreSelectedTicket() logic tìm kiếm giữ nguyên, chỉ thay đổi timing_
    - _Requirements: 2.1_

  - [x] 4.5 Verify exploration test Bug 1 now passes
    - **Property 1: Expected Behavior** - Slow State Restore Fixed
    - **QUAN TRỌNG**: Chạy lại CÙNG test từ task 1 — KHÔNG viết test mới
    - Test từ task 1 encode expected behavior: combobox hiển thị ngay text ticket đã lưu
    - Khi test pass → xác nhận expected behavior đã được thỏa mãn
    - **KẾT QUẢ MONG ĐỢI: Test PASS** (xác nhận bug đã fix)
    - _Requirements: 2.1_

  - [x] 4.6 Verify preservation tests still pass sau fix Bug 1
    - **Property 2: Preservation** - Existing Behavior After Bug 1 Fix
    - **QUAN TRỌNG**: Chạy lại CÙNG tests từ task 3 — KHÔNG viết tests mới
    - **KẾT QUẢ MONG ĐỢI: Tests PASS** (xác nhận không regression)

- [x] 5. Fix Bug 2 — Tự động chọn ticket khi blur với text khớp

  - [x] 5.1 Thêm auto-select logic vào click-outside handler trong `TicketCombobox.kt`
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`
    - Trong click-outside handler (block `if (!clickInCombobox && !clickInDropdown)`), TRƯỚC khi gọi `hideDropdown()`:
    - Đọc `val typedText = (document.getElementById("ticket-search") as? HTMLInputElement)?.value?.trim() ?: ""`
    - Tìm ticket khớp: `val match = ticketList.find { it.ticketId.equals(typedText, ignoreCase = true) }`
    - Nếu `match != null` và `match.ticketId != selectedTicket?.ticketId` → gọi `selectTicket(match)` (đã bao gồm `hideDropdown()`)
    - _Bug_Condition: isBugCondition_NoAutoSelect — typedText khớp chính xác ticketId_
    - _Expected_Behavior: selectTicket() được gọi, selectedTicket cập nhật, state saved_
    - _Preservation: Click-outside khi text rỗng hoặc không khớp → không trigger auto-select_
    - _Requirements: 2.2, 2.3_

  - [x] 5.2 Thêm fallback restore khi text không khớp ticket nào
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`
    - Trong click-outside handler, nếu `match == null` và `typedText.isNotBlank()`:
    - Nếu `selectedTicket != null` → restore input value = `"${selectedTicket!!.ticketId} — ${selectedTicket!!.ticketSummary}"`
    - Nếu `selectedTicket == null` → clear input value = `""`
    - Sau đó gọi `hideDropdown()`
    - _Bug_Condition: Text không khớp ticket nào → cần fallback_
    - _Expected_Behavior: Combobox restore về trạng thái ticket đã chọn trước đó_
    - _Preservation: Khi text rỗng, chỉ hideDropdown() như hiện tại_
    - _Requirements: 2.4_

  - [x] 5.3 Thêm public method `setInputText()` vào `TicketCombobox`
    - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/TicketCombobox.kt`
    - Thêm method: `fun setInputText(text: String)` — set `(document.getElementById("ticket-search") as? HTMLInputElement)?.value = text`
    - Method này cho phép `TicketIntelligencePage` set text combobox từ bên ngoài (dùng trong task 4.3)
    - _Requirements: 2.1_

  - [x] 5.4 Verify exploration test Bug 2 now passes
    - **Property 1: Expected Behavior** - No Auto-Select on Blur Fixed
    - **QUAN TRỌNG**: Chạy lại CÙNG test từ task 2 — KHÔNG viết test mới
    - Test từ task 2 encode expected behavior: click-outside auto-select ticket khớp
    - Khi test pass → xác nhận expected behavior đã được thỏa mãn
    - **KẾT QUẢ MONG ĐỢI: Test PASS** (xác nhận bug đã fix)
    - _Requirements: 2.2, 2.3, 2.4_

  - [x] 5.5 Verify preservation tests still pass sau fix Bug 2
    - **Property 2: Preservation** - Existing Behavior After Bug 2 Fix
    - **QUAN TRỌNG**: Chạy lại CÙNG tests từ task 3 — KHÔNG viết tests mới
    - **KẾT QUẢ MONG ĐỢI: Tests PASS** (xác nhận không regression)

- [x] 6. Checkpoint — Đảm bảo tất cả tests pass
  - Chạy toàn bộ test suite: exploration tests (task 1, 2), preservation tests (task 3)
  - Tất cả exploration tests phải PASS (bugs đã fix)
  - Tất cả preservation tests phải PASS (không regression)
  - Nếu có test fail, phân tích nguyên nhân và hỏi user nếu cần
