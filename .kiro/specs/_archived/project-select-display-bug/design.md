# Project Select Display Bug — Bugfix Design

## Overview

Sau khi user chọn project từ trang ProjectSelectPage, navbar badge vẫn hiển thị "Select Project" thay vì project key đã chọn, và breadcrumb hiển thị sai "PROJECT_SELECT / PROJECT_SELECT" thay vì route đích. Nguyên nhân gốc: `NavbarDropdown.renderProjectSelector()` đọc project key một lần lúc render và không bao giờ cập nhật lại. `Navbar.updateBreadcrumb()` tồn tại nhưng không được gọi sau khi Router navigate. Fix sẽ thêm method `refreshProjectSelector()` vào `NavbarDropdown` và đảm bảo cả badge lẫn breadcrumb được cập nhật khi navigate sau project selection.

## Glossary

- **Bug_Condition (C)**: Điều kiện kích hoạt bug — khi hệ thống navigate từ `project_select` sang route khác sau khi user chọn project, badge và breadcrumb không được cập nhật
- **Property (P)**: Hành vi mong muốn — badge hiển thị `[PROJECT_KEY]` và breadcrumb hiển thị đúng route đích ngay khi trang đích được render
- **Preservation**: Hành vi hiện tại phải giữ nguyên — empty project key hiển thị "Select Project", user dropdown hoạt động bình thường, standalone pages render không có Shell, normal navigation breadcrumb cập nhật đúng
- **`renderProjectSelector()`**: Function trong `NavbarDropdown.kt` tạo project badge DOM element — hiện tại đọc project key một lần và không cập nhật
- **`refreshProjectSelector()`**: Method mới cần thêm vào `NavbarDropdown` — cập nhật badge text và rebuild dropdown từ sessionStorage
- **`updateBreadcrumb(route)`**: Function trong `Navbar.kt` cập nhật breadcrumb DOM — tồn tại nhưng không được gọi sau navigation từ project_select
- **`selectProject(key)`**: Function trong `ProjectSelectPage.kt` lưu project key vào sessionStorage và gọi `Router.navigateTo("dashboard")`

## Bug Details

### Bug Condition

Bug xảy ra khi user chọn project từ ProjectSelectPage. `selectProject()` gọi `ApiClient.saveProjectKey(key)` rồi `Router.navigateTo("dashboard")`. Router render Shell + DashboardPage, nhưng Navbar đã được render trước đó với project key cũ (null/"Select Project") và không được refresh. `Navbar.updateBreadcrumb()` cũng không được gọi nên breadcrumb giữ route cũ "PROJECT_SELECT".

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type NavigationEvent
  OUTPUT: boolean
  
  RETURN input.previousRoute = "project_select"
         AND input.targetRoute ≠ "project_select"
         AND sessionStorage.getItem("jira_assistant_project") ≠ null
         AND (navbarBadge.textContent ≠ "[" + projectKey + "]"
              OR breadcrumb does NOT contain routeSection(input.targetRoute))
END FUNCTION
```

### Examples

- User chọn project "PROJ" từ ProjectSelectPage → navigate sang dashboard → badge vẫn hiển thị "Select Project"; expected: "[PROJ]"
- User chọn project "PROJ" → navigate sang dashboard → breadcrumb hiển thị "PROJECT_SELECT / PROJECT_SELECT"; expected: "DASHBOARD / OVERVIEW"
- User đã ở dashboard với project "PROJ", navigate sang analysis rồi quay lại → badge vẫn hiển thị giá trị từ lần render đầu tiên
- User đổi project qua dropdown "Change Project" → chọn project mới "NEW" → badge không cập nhật sang "[NEW]"

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Khi chưa có project nào được chọn (project key null/blank), badge phải hiển thị "Select Project" và click navigate đến project_select
- "Change Project" dropdown phải auto-pause scan nếu đang scanning, sau đó navigate đến project_select
- User widget dropdown (Account Settings, App Settings, Sign Out) phải hoạt động bình thường
- Standalone pages (login, project_select) phải render không có Shell
- Navigation giữa các trang bình thường (không liên quan project selection) phải cập nhật breadcrumb đúng

**Scope:**
Tất cả inputs KHÔNG liên quan đến navigation sau project selection phải hoàn toàn không bị ảnh hưởng. Bao gồm:
- Navigation giữa các trang khi đã có project (dashboard → analysis → knowledge_graph)
- Mouse clicks trên sidebar, navbar items
- User dropdown interactions
- Standalone page rendering (login, project_select)

## Hypothesized Root Cause

Dựa trên phân tích code, các nguyên nhân gốc:

1. **`NavbarDropdown.renderProjectSelector()` đọc project key một lần**: Function đọc `ApiClient.getProjectKey()` lúc render và tạo badge DOM element. Khi project key thay đổi trong sessionStorage, badge DOM không được cập nhật vì không có mechanism để refresh.

2. **Thiếu `refreshProjectSelector()` method**: `NavbarDropdown` không có public method để cập nhật badge text sau khi project key thay đổi. Cần thêm method này để tìm badge element trong DOM và cập nhật `textContent` + rebuild dropdown.

3. **`Navbar.updateBreadcrumb()` không được gọi sau navigation**: `Navbar.updateBreadcrumb(route)` tồn tại và hoạt động đúng, nhưng không ai gọi nó khi Router navigate từ project_select sang dashboard. Router chỉ gọi `updateActiveNav()` cho sidebar.

4. **`ProjectSelectPage.selectProject()` không trigger navbar refresh**: Function chỉ gọi `ApiClient.saveProjectKey(key)` + `Router.navigateTo("dashboard")` mà không thông báo cho Navbar/NavbarDropdown cập nhật.

## Correctness Properties

Property 1: Bug Condition - Project Badge và Breadcrumb Cập Nhật Sau Selection

_For any_ navigation event từ project_select sang route khác khi project key đã được lưu trong sessionStorage (isBugCondition returns true), hệ thống đã fix SHALL cập nhật project badge hiển thị `[PROJECT_KEY]` và breadcrumb hiển thị đúng section/page name của route đích.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation - Non-Selection Navigation và UI Behavior Unchanged

_For any_ interaction KHÔNG liên quan đến navigation sau project selection (isBugCondition returns false), hệ thống đã fix SHALL giữ nguyên hành vi: empty project key hiển thị "Select Project", user dropdown hoạt động bình thường, standalone pages render không có Shell, và breadcrumb cập nhật đúng cho normal navigation.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming root cause analysis đúng:

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/components/NavbarDropdown.kt`

**Function**: Thêm `refreshProjectSelector()`

**Specific Changes**:
1. **Thêm `refreshProjectSelector()` public method**: Tìm element `#navbar-project-selector` trong DOM, lấy parent container, xóa selector cũ khỏi DOM (`remove()`), rồi gọi lại `renderProjectSelector(container)` để render mới với project key hiện tại từ sessionStorage. Cách tiếp cận remove-and-re-render đảm bảo toàn bộ DOM subtree (badge + dropdown + event listeners) được tạo lại chính xác, tránh edge cases khi chỉ cập nhật textContent mà không rebuild dropdown.

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/components/Navbar.kt`

**Function**: Không cần sửa — `updateBreadcrumb(route)` đã hoạt động đúng

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ProjectSelectPage.kt`

**Function**: `selectProject(key)` — Không cần sửa. ProjectSelectPage là standalone page (không có Shell/Navbar trong DOM), nên gọi `refreshProjectSelector()` tại đây sẽ là no-op. Khi `Router.navigateTo("dashboard")` được gọi, Shell render fresh (vì chuyển từ standalone → shell), Navbar đọc đúng project key đã lưu.

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/router/Router.kt`

**Function**: `handleRoute()`

**Specific Changes**:
2. **Gọi `Navbar.updateBreadcrumb(hash)` và `NavbarDropdown.refreshProjectSelector()` sau khi render route**: Trong `handleRoute()`, sau `updateActiveNav(hash)`, thêm block kiểm tra `!isStandalone` rồi gọi cả hai methods. Điều này đảm bảo breadcrumb và badge được cập nhật mỗi lần navigate giữa các non-standalone routes — bao gồm cả trường hợp chuyển từ project_select (standalone) sang dashboard (Shell re-render fresh + Router refresh).

## Testing Strategy

### Validation Approach

Testing strategy theo hai giai đoạn: (1) surface counterexamples trên unfixed code để xác nhận bug, (2) verify fix hoạt động đúng và preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples chứng minh bug tồn tại TRƯỚC khi implement fix. Xác nhận hoặc bác bỏ root cause analysis.

**Test Plan**: Simulate navigation flow từ project_select sang dashboard, kiểm tra DOM state của badge và breadcrumb. Chạy trên UNFIXED code để observe failures.

**Test Cases**:
1. **Badge After Selection Test**: Simulate `selectProject("PROJ")` → kiểm tra badge textContent (will show "Select Project" on unfixed code)
2. **Breadcrumb After Navigation Test**: Simulate navigate từ project_select sang dashboard → kiểm tra breadcrumb content (will show "PROJECT_SELECT" on unfixed code)
3. **Badge After Page Switch Test**: Navigate dashboard → analysis → kiểm tra badge vẫn hiển thị project key (may show stale value on unfixed code)
4. **Badge After Project Change Test**: Simulate đổi project key trong sessionStorage → kiểm tra badge (will show old value on unfixed code)

**Expected Counterexamples**:
- Badge textContent = "Select Project" thay vì "[PROJ]" sau khi selectProject()
- Breadcrumb innerHTML chứa "PROJECT_SELECT" thay vì "DASHBOARD / OVERVIEW"
- Nguyên nhân: `renderProjectSelector()` không có refresh mechanism, `updateBreadcrumb()` không được gọi

### Fix Checking

**Goal**: Verify rằng cho tất cả inputs thỏa bug condition, hệ thống đã fix tạo ra hành vi đúng.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := navigateAfterProjectSelection_fixed(input)
  badge := document.querySelector(".project-badge")
  breadcrumb := document.querySelector(".breadcrumb")
  projectKey := sessionStorage.getItem("jira_assistant_project")
  
  ASSERT badge.textContent = "[" + projectKey + "]"
    AND breadcrumb contains routeSection(input.targetRoute)
    AND breadcrumb contains routeName(input.targetRoute)
END FOR
```

### Preservation Checking

**Goal**: Verify rằng cho tất cả inputs KHÔNG thỏa bug condition, hệ thống đã fix tạo ra kết quả giống hệ thống gốc.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT navigateOriginal(input) = navigateFixed(input)
END FOR
```

**Testing Approach**: Property-based testing recommended cho preservation checking vì:
- Tự động generate nhiều test cases across input domain (different routes, different project states)
- Bắt edge cases mà manual unit tests có thể miss
- Đảm bảo strong guarantees rằng behavior unchanged cho tất cả non-buggy inputs

**Test Plan**: Observe behavior trên UNFIXED code cho normal navigation, sau đó viết property-based tests capturing behavior đó.

**Test Cases**:
1. **Empty Project Key Preservation**: Verify khi project key null/blank, badge hiển thị "Select Project" và click navigate đến project_select
2. **User Dropdown Preservation**: Verify user widget dropdown (Account Settings, App Settings, Sign Out) hoạt động bình thường sau fix
3. **Normal Navigation Breadcrumb Preservation**: Verify navigation giữa dashboard → analysis → knowledge_graph cập nhật breadcrumb đúng
4. **Standalone Page Preservation**: Verify login và project_select pages render không có Shell

### Unit Tests

- Test `NavbarDropdown.refreshProjectSelector()` cập nhật badge text khi project key thay đổi
- Test `Router.handleRoute()` gọi `Navbar.updateBreadcrumb()` cho non-standalone routes
- Test `ProjectSelectPage.selectProject()` trigger navbar refresh
- Test badge hiển thị "Select Project" khi project key null (preservation)

### Property-Based Tests

- Generate random project keys và verify badge luôn hiển thị `[key]` sau refreshProjectSelector()
- Generate random route names và verify breadcrumb luôn hiển thị đúng section/page name sau updateBreadcrumb()
- Generate random navigation sequences và verify badge + breadcrumb consistent với sessionStorage state

### Integration Tests

- Test full flow: login → project_select → chọn project → dashboard → verify badge + breadcrumb
- Test flow: dashboard → change project → project_select → chọn project mới → dashboard → verify badge cập nhật
- Test flow: dashboard → analysis → knowledge_graph → verify breadcrumb cập nhật mỗi lần navigate
