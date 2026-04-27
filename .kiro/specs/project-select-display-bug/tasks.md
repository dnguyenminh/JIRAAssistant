# Implementation Plan — Project Select Display Bug

## Tasks

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Project Badge và Breadcrumb Không Cập Nhật Sau Selection
  - **IMPORTANT**: Write this property-based test BEFORE implementing the fix
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples chứng minh badge và breadcrumb không cập nhật sau project selection
  - **Scoped PBT Approach**: Scope the property to the concrete failing case: navigate from `project_select` to `dashboard` after `selectProject(key)`
  - Test that after `selectProject(key)` and navigation to dashboard:
    - Project badge (`document.querySelector(".project-badge")`) textContent = `[PROJECT_KEY]` (not "Select Project")
    - Breadcrumb (`document.querySelector(".breadcrumb")`) contains "DASHBOARD" section name (not "PROJECT_SELECT")
    - `NavbarDropdown.refreshProjectSelector()` method exists and is callable
    - `Navbar.updateBreadcrumb("dashboard")` produces correct breadcrumb content
  - Bug Condition from design: `isBugCondition(input) WHERE input.previousRoute = "project_select" AND input.targetRoute ≠ "project_select" AND sessionStorage has project key`
  - Expected Behavior from design: badge SHALL display `[PROJECT_KEY]` and breadcrumb SHALL display correct route section/page name
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct — proves the bug exists: badge shows "Select Project", breadcrumb shows "PROJECT_SELECT")
  - Document counterexamples found
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Selection Navigation và UI Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **IMPORTANT**: Write and run these tests BEFORE implementing the fix
  - Observe on UNFIXED code: khi project key null/blank, badge hiển thị "Select Project"
  - Observe on UNFIXED code: user widget dropdown (Account Settings, App Settings, Sign Out) hoạt động bình thường
  - Observe on UNFIXED code: standalone pages (login, project_select) render không có Shell
  - Observe on UNFIXED code: `Navbar.updateBreadcrumb(route)` cập nhật breadcrumb đúng cho các route đã biết
  - Write property-based test: for all non-project-selection navigation events:
    - Empty project key → badge shows "Select Project"
    - `Navbar.updateBreadcrumb("dashboard")` → breadcrumb contains "DASHBOARD / OVERVIEW"
    - `Navbar.updateBreadcrumb("analysis")` → breadcrumb contains "ANALYSIS / PROJECT ANALYSIS"
    - User dropdown rendering unaffected by NavbarDropdown changes
  - Preservation from design: `FOR ALL X WHERE NOT isBugCondition(X) DO ASSERT F(X) = F'(X)`
  - Verify tests pass on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Implement the fix

  - [x] 3.1 Add `refreshProjectSelector()` method to `NavbarDropdown.kt`
    - Add public `refreshProjectSelector()` method that:
      - Finds `#navbar-project-selector` element in DOM
      - Reads current project key from `ApiClient.getProjectKey()`
      - Updates badge `textContent` to `[projectKey]` or "Select Project" if null/blank
      - Rebuilds dropdown if project key changed from null to non-null (or vice versa)
    - Method must be ≤ 20 lines per function rule
    - Extract helper functions if needed (e.g., `rebuildProjectDropdown()`)
    - _Bug_Condition: badge không cập nhật khi project key thay đổi_
    - _Expected_Behavior: badge phản ánh project key hiện tại từ sessionStorage_
    - _Preservation: renderProjectSelector() ban đầu không bị sửa, chỉ thêm method mới_
    - _Requirements: 2.1, 2.3, 2.4, 9b_

  - [x] 3.2 Update `Router.handleRoute()` to call breadcrumb update and badge refresh
    - In `handleRoute()`, after rendering non-standalone route content:
      - Call `Navbar.updateBreadcrumb(hash)` to update breadcrumb for new route
      - Call `NavbarDropdown.refreshProjectSelector()` to ensure badge reflects current sessionStorage
    - Only call for non-standalone routes (standalone pages don't have navbar)
    - Keep function ≤ 20 lines — extract helper if needed
    - _Bug_Condition: breadcrumb không cập nhật sau navigation từ project_select_
    - _Expected_Behavior: breadcrumb hiển thị đúng route đích_
    - _Preservation: standalone route rendering unchanged, updateActiveNav() still called_
    - _Requirements: 2.2, 9a, 9c_

  - [x] 3.3 Update `ProjectSelectPage.selectProject()` to trigger navbar refresh
    - After `ApiClient.saveProjectKey(key)` and before `Router.navigateTo("dashboard")`:
      - Call `NavbarDropdown.refreshProjectSelector()` to update badge immediately
    - This ensures badge is updated even before Router re-renders the shell
    - _Bug_Condition: selectProject() không thông báo cho navbar cập nhật_
    - _Expected_Behavior: badge cập nhật ngay sau khi project key được lưu_
    - _Preservation: saveProjectKey() và navigateTo() calls unchanged_
    - _Requirements: 2.1, 2.4_

  - [x] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Project Badge và Breadcrumb Cập Nhật Sau Selection
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms:
      - Badge shows `[PROJECT_KEY]` after selection
      - Breadcrumb shows correct route section/page name
      - `refreshProjectSelector()` works correctly
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Selection Navigation và UI Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run full test suite to verify no regressions
  - Verify bug condition test (Property 1) passes — badge + breadcrumb update correctly
  - Verify preservation test (Property 2) passes — existing behavior unchanged
  - Ensure changes are minimal and scoped to 4 files: NavbarDropdown.kt, Router.kt, ProjectSelectPage.kt (+ test files)
  - Verify all modified files ≤ 200 lines, all functions ≤ 20 lines
  - Ask the user if questions arise
