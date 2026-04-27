# Bugfix Design — No Jira Redirect to Integrations

## Overview

Fix the post-login flow so users are not stuck on the Project Select page when Jira credentials are not configured. The fix has two layers: (1) remove standalone routing for `project_select` so the page always renders inside the Shell with sidebar, navbar, and account dropdown — giving users navigation and logout as an escape route; (2) detect unconfigured Jira when the project list is empty and branch by role — admin users get auto-redirected to Integrations, non-admin users see a disconnect footer + popup modal.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — user is authenticated, has no project key, and Jira is not configured, resulting in a stuck state on Project Select
- **Property (P)**: The desired behavior — admin users are redirected to `#integrations`; non-admin users see a disconnect indicator + popup requesting admin assistance
- **Preservation**: Existing flows that must remain unchanged — configured Jira with projects, search filtering, dashboard navigation, error fallback
- **Shell**: The app layout wrapper (`Shell.render()`) providing sidebar navigation, top navbar, and account dropdown
- **Standalone route**: A route registered via `Router.registerStandalone()` that renders WITHOUT the Shell (no sidebar/navbar)
- **`checkJiraStatus()`**: New function in `ProjectSelectPage.kt` that calls `/api/integrations/jira/status` and branches by Jira config + user role
- **`handleJiraConfigResult(configured)`**: Delegation function that routes admin to integrations or shows disconnect UI for non-admin
- **`showJiraNotConfigured()`**: Composes the non-admin disconnect UI by cloning footer + popup templates
- **`showEmptyWithRetry()`**: Fallback empty state with programmatically created "No projects available." message + RETRY button

## Bug Details

### Bug Condition

The bug manifests when a logged-in user has no project key in sessionStorage and Jira credentials are not configured. `AppStartup.checkAuthAndNavigate()` sends the user to `#project_select`, the backend returns an empty project list (via `NoOpJiraClient`), and the page displays a generic empty message with no way to navigate to Integrations or log out (because `project_select` was a standalone route without Shell).

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type AppState { token: String?, projectKey: String?, jiraConfigured: Boolean }
  OUTPUT: boolean

  RETURN input.token IS NOT NULL
         AND (input.projectKey IS NULL OR input.projectKey IS BLANK)
         AND input.jiraConfigured = false
END FUNCTION
```

### Examples

- Admin user, no project key, Jira not configured → was stuck on empty Project Select; now auto-redirects to `#integrations`
- Non-admin user, no project key, Jira not configured → was stuck on empty Project Select; now sees disconnect footer + popup with "contact your administrator" message
- Any user, no project key, Jira configured but 0 projects → shows "No projects available." + RETRY button (unchanged fallback)
- Any user, Jira status API fails → shows "No projects available." + RETRY button (graceful degradation)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Mouse clicks on project rows continue to call `selectProject(key)` → saves key → navigates to dashboard
- Search filtering continues to show "No projects match your search." when filter matches 0 projects from a non-empty list
- Pagination, sorting, and table rendering are completely unaffected
- Users with a valid project key navigate to dashboard or handle current route normally
- `AppStartup.checkAuthAndNavigate()` flow is unchanged — the fix is entirely in `App.kt` routing and `ProjectSelectPage` logic

**Scope:**
All inputs where Jira IS configured (projects available or not) and all inputs where the user already has a project key are completely unaffected. The fix only activates when `allProjects.isEmpty()` after a successful `/api/projects` response AND `/api/integrations/jira/status` returns `configured: false`.

## Hypothesized Root Cause

Based on the bug description, the root causes are:

1. **Standalone routing for Project Select**: `App.kt` registered `project_select` via `registerStandalone("project_select")`, causing the page to render WITHOUT the Shell. Users had no sidebar, no navbar, no account dropdown — no way to navigate away or log out.

2. **No Jira status detection in ProjectSelectPage**: When `loadProjects()` received an empty list, it showed a generic "No projects match your search." message without checking WHY the list was empty. It never called `/api/integrations/jira/status` to distinguish "Jira not configured" from "Jira configured but 0 projects".

3. **No role-based handling**: Even if the empty state was detected, there was no logic to differentiate admin users (who can configure Jira) from non-admin users (who need to contact an admin).

## Correctness Properties

Property 1: Bug Condition — Jira-Unconfigured Users Are Not Stuck

_For any_ input where the bug condition holds (user authenticated, no project key, Jira not configured), the fixed system SHALL either redirect admin users to `#integrations` or show non-admin users a disconnect footer + popup modal with guidance to contact an administrator. In all cases, the Shell (sidebar + navbar + account dropdown) SHALL be visible, providing navigation and logout as escape routes.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation — Existing Navigation Flows Unchanged

_For any_ input where the bug condition does NOT hold (Jira is configured, or user has a project key, or projects are available), the fixed system SHALL produce the same behavior as the original system — project list displays normally, search filtering works, dashboard navigation works, and error states show appropriate messages.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/App.kt`

**Change**: Remove `registerStandalone("project_select")` — the `project_select` route is now registered only via `Router.register("project_select") { ProjectSelectPage.render(it) }`, so it renders inside the Shell with sidebar + navbar + account dropdown.

**Before:**
```kotlin
Router.registerStandalone("project_select")
Router.register("project_select") { ProjectSelectPage.render(it) }
```

**After:**
```kotlin
Router.register("project_select") { ProjectSelectPage.render(it) }
```

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ProjectSelectPage.kt`

**Function**: `loadProjects()` — modified to call `checkJiraStatus()` when `allProjects.isEmpty()`

**Specific change in `loadProjects()`:**
```kotlin
if (allProjects.isEmpty()) {
    checkJiraStatus()
    return@launch
}
```

**New function `checkJiraStatus()`:**
- Launches a new coroutine via `scope.launch`
- Calls `ApiClient.get("/api/integrations/jira/status")`
- On HTTP 200: parses response body manually using `Json.parseToJsonElement(body).jsonObject`
- Extracts `configured` boolean via `obj["configured"]?.jsonPrimitive?.boolean ?: true` (defaults to `true` if missing)
- Delegates to `handleJiraConfigResult(configured)`
- On non-200 or exception: calls `showEmptyWithRetry()` (graceful fallback)
- Always removes `BlockingOverlay` in `finally` block
- Logs failures via `console.log("[ProjectSelect] Jira status check failed: ${e.message}")`

**New function `handleJiraConfigResult(configured: Boolean)`:**
- If `!configured`: checks `ApiClient.getUserRole()` against `UserRole.ADMINISTRATOR`
  - Admin → `Router.navigateTo("integrations")`
  - Non-admin → calls `showJiraNotConfigured()`
- If `configured`: calls `showEmptyWithRetry()`

**New function `showJiraNotConfigured()`:**
- Calls `cloneAndAppendFooter()` then `cloneAndShowPopup()`

**New function `cloneAndAppendFooter()`:**
- Gets `<template id="tmpl-jira-disconnect">` as `HTMLTemplateElement`
- Clones `tmpl.content.firstElementChild` via `cloneNode(true)`
- Appends cloned footer element to `document.body`

**New function `cloneAndShowPopup()`:**
- Gets `<template id="tmpl-jira-not-configured-popup">` as `HTMLTemplateElement`
- Clones `tmpl.content.firstElementChild` via `cloneNode(true)`
- Appends cloned overlay element to `document.body`
- Binds OK button: `overlay.querySelector("#jira-popup-ok-btn")?.addEventListener("click", { overlay.remove() })`
- Note: only the popup overlay is removed on OK click; the disconnect footer remains visible

**New function `showEmptyWithRetry()`:**
- Gets `#project-table-body` tbody element, clears `innerHTML`
- Creates DOM elements programmatically: `tr` > `td` (colspan=3, centered padding) > `span` ("No projects available.", opacity 0.5) + `br` + `button` ("RETRY", class `chat-action-btn`)
- RETRY button click handler calls `loadProjects()` to re-attempt

---

**File**: `frontend/src/jsMain/resources/templates/project-select.html`

**New templates added after the main page div:**

1. `<template id="tmpl-jira-disconnect">` — contains `.jira-disconnect-footer` div with ⚡ icon + "Jira Disconnected" label
2. `<template id="tmpl-jira-not-configured-popup">` — contains `.jira-popup-overlay` with `.glass-card.jira-popup-card` (⚠️ icon, "Jira Not Configured" title, message asking user to contact admin, OK dismiss button)

**New CSS styles (inline `<style>` block in template):**

- `.jira-disconnect-footer`: `position: fixed; bottom: 0; left: 0; right: 0;` — full-width fixed bottom bar with glass background `rgba(20, 24, 35, 0.85)`, `backdrop-filter: blur(16px)`, red neon border-top `rgba(255, 110, 132, 0.3)`, red neon box-shadow `rgba(255, 110, 132, 0.15)`, `color: var(--danger)`, `z-index: 900`, `letter-spacing: 1.5px`
- `.jira-disconnect-icon`: `font-size: 16px`
- `.jira-popup-overlay`: `position: fixed; inset: 0;` — full-screen centered flex overlay with dark background `rgba(0, 0, 0, 0.6)`, `backdrop-filter: blur(4px)`, `z-index: 1000`
- `.jira-popup-card`: `max-width: 420px; width: 90%; padding: 48px;` — danger-tinted border `rgba(255, 110, 132, 0.2)`, deep box-shadow with red neon glow `rgba(255, 110, 132, 0.1)`, entrance animation `popupFadeIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)`
- `.jira-popup-card:hover`: `transform: none` — disables the glass-card hover lift effect
- `.jira-popup-icon`: `font-size: 48px; margin-bottom: 16px`
- `.jira-popup-card h3`: `color: var(--danger); letter-spacing: 2px`
- `.jira-popup-card p`: `color: var(--text-sub); line-height: 1.6`
- `.jira-popup-card .chat-action-btn`: `padding: 12px 40px; letter-spacing: 1.5px`
- `@keyframes popupFadeIn`: scales from `0.9` + translates `10px` down → `1.0` + `0` with opacity fade-in

## Regression Prevention

- **Shell always visible**: Since `registerStandalone("project_select")` is removed, the Project Select page always renders inside the Shell. Users always have sidebar navigation, account dropdown (with logout), and navbar — even in edge cases where Jira status check fails or returns unexpected data.
- **Jira configured + 0 projects** → `showEmptyWithRetry()` displays "No projects available." + RETRY button (unchanged behavior path)
- **Search filter matches 0 projects** → existing `renderTable()` calls `renderEmpty("No projects match your search.")` (completely unaffected code path — this only triggers when `filtered.isEmpty()` from a non-empty `allProjects` list)
- **User has project key** → normal dashboard flow via `AppStartup` (unchanged)
- **Jira status API fails or returns non-200** → `showEmptyWithRetry()` fallback (graceful degradation, no stuck state)
- **Jira status response missing `configured` field** → defaults to `true` via `?: true`, falls through to `showEmptyWithRetry()` (safe default)

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis.

**Test Plan**: Simulate the post-login flow where no project key exists and Jira is not configured. Verify that on unfixed code, the user gets stuck on Project Select with no escape route.

**Test Cases**:
1. **Admin stuck test**: Login as admin, no project key, Jira not configured → verify user is stuck on `#project_select` with no navigation (will fail on unfixed code)
2. **Non-admin stuck test**: Login as non-admin, no project key, Jira not configured → verify user is stuck with generic empty message (will fail on unfixed code)
3. **No Shell test**: Verify that `project_select` renders as standalone (no sidebar/navbar) on unfixed code (will fail on unfixed code)

**Expected Counterexamples**:
- User sees "No projects match your search." with no way to navigate to Integrations
- No sidebar or navbar visible because `project_select` is a standalone route
- No logout option available

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := navigateAfterAuth_fixed(input)
  IF input.userRole = ADMINISTRATOR THEN
    ASSERT result.navigatedTo = "integrations"
  ELSE
    ASSERT result.showsDisconnectFooter = true
       AND result.showsPopupModal = true
       AND result.shellVisible = true
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT navigateAfterAuth_original(input) = navigateAfterAuth_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for configured-Jira flows and project-key-present flows, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Project list preservation**: Verify that when Jira is configured and projects exist, the table renders correctly after fix
2. **Search filter preservation**: Verify that search filtering continues to show "No projects match your search." for empty filter results
3. **Dashboard navigation preservation**: Verify that clicking a project row still calls `selectProject(key)` → saves key → navigates to dashboard
4. **Error state preservation**: Verify that `/api/projects` failure still shows "Connection error: ..." message

### Unit Tests

- Test `checkJiraStatus()` with mocked API returning `{ "configured": false }` → verify admin redirect vs non-admin UI
- Test `checkJiraStatus()` with mocked API returning `{ "configured": true }` → verify `showEmptyWithRetry()` called
- Test `checkJiraStatus()` with mocked API failure → verify `showEmptyWithRetry()` fallback
- Test `handleJiraConfigResult(false)` with admin role → verify `Router.navigateTo("integrations")`
- Test `handleJiraConfigResult(false)` with non-admin role → verify `showJiraNotConfigured()` called
- Test `handleJiraConfigResult(true)` → verify `showEmptyWithRetry()` called
- Test `showEmptyWithRetry()` → verify RETRY button calls `loadProjects()`

### Property-Based Tests

- Generate random `AppState` inputs and verify: if `isBugCondition(input)` then admin redirects to integrations or non-admin sees disconnect UI; if `!isBugCondition(input)` then behavior matches original
- Generate random project lists (empty/non-empty) with random Jira config states and verify correct branching
- Generate random API failure scenarios and verify graceful fallback to `showEmptyWithRetry()`

### Integration Tests

- Test full login → project select → Jira status check → redirect/popup flow for admin user
- Test full login → project select → Jira status check → disconnect UI flow for non-admin user
- Test that Shell (sidebar + navbar + account dropdown) is visible on Project Select page after fix
- Test that popup OK button dismisses overlay but footer remains visible
