# Jira Modal Consistency Bugfix Design

## Overview

The Jira Cloud Services config modal deviates from the established AI provider modal pattern in three ways: (1) it uses a single combined "SAVE & TEST" button instead of separate "TEST CONNECTION" + "SAVE" buttons, (2) Email/API Token input fields render with light background due to browser native styling overriding the dark theme, and (3) it allows saving without first verifying the connection. The fix aligns the Jira modal with the AI provider modal pattern established in `IntegrationsConfigModal.kt`, ensuring UI consistency across all integration modals.

## Glossary

- **Bug_Condition (C)**: The condition that triggered the bug — when the user opens the Jira Cloud Services config modal (providerType = "JIRA"), the modal rendered with inconsistent button layout and input styling compared to AI provider modals (now fixed)
- **Property (P)**: The desired behavior — Jira modal displays separate "TEST CONNECTION" + "SAVE" buttons (SAVE disabled until test succeeds), and all input fields use dark background matching Obsidian Kinetic design system
- **Preservation**: AI provider modal behavior (Ollama, Gemini, LM Studio, Gemini CLI) must remain unchanged — separate buttons, SAVE disabled until test, dark-themed inputs
- **`testJiraConnection()`**: The function in `IntegrationsJiraModal.kt` that tests the Jira connection by calling `PUT /api/integrations/jira/config` and enables/disables the SAVE button based on the result (replaced the old `saveAndTestJiraConfig()`)
- **`saveJiraConfig()`**: The function in `IntegrationsJiraModal.kt` that acts as a visual confirmation step — shows success toast, updates card status, and closes the modal after delay (config already saved during test)
- **`testConnectionInModal()`**: The function in `IntegrationsConfigModal.kt` that tests connection via `POST /api/integrations/{providerId}/test` without saving — the AI provider pattern
- **`saveConfigOnly()`**: The function in `IntegrationsConfigModal.kt` that saves config via `PUT /api/integrations/{providerId}/config` — the AI provider pattern

## Bug Details

### Bug Condition

The bug manifests when a user opens the Jira Cloud Services config modal. The modal renders with a single "SAVE & TEST" button (`btn-jira-save-test`) instead of separate "TEST CONNECTION" + "SAVE" buttons, the Email and API Token input fields display with light/white background due to browser native styling on `<input type="email">` and `<input type="password">`, and the save action is always available without requiring a successful connection test first.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ConfigModalInteraction
  OUTPUT: boolean
  
  RETURN input.providerType = "JIRA"
         AND input.action = "OPEN_CONFIG_MODAL"
END FUNCTION
```

### Examples

- User opens Jira modal → sees single "SAVE & TEST" button; expected: separate "TEST CONNECTION" + "SAVE" buttons
- User opens Jira modal → Email input has white background on dark theme; expected: dark background (`rgba(12,14,22,0.95)`) matching AI provider inputs
- User opens Jira modal → can immediately click "SAVE & TEST" to save untested credentials; expected: SAVE disabled until TEST CONNECTION succeeds
- User opens Ollama modal → sees correct "TEST CONNECTION" + "SAVE" layout (not affected by bug)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- AI provider modals (Ollama, Gemini, LM Studio, Gemini CLI) must continue to display separate "TEST CONNECTION" + "SAVE" buttons with SAVE disabled until test succeeds
- Jira credentials must continue to be saved with AES-256-GCM encryption via `PUT /api/integrations/jira/config`
- Successful Jira connection test must continue to update provider card status to ACTIVE with green glow
- Failed Jira connection test must continue to display error message and update status to OFFLINE
- Non-Administrator users must continue to see read-only fields with actions disabled
- Close button and outside-click must continue to close the Jira modal and reset progress/status indicators
- The `POST /api/integrations/jira/test` backend endpoint behavior remains unchanged

**Scope:**
All inputs that do NOT involve opening the Jira config modal should be completely unaffected by this fix. This includes:
- Opening any AI provider config modal (Ollama, Gemini, LM Studio, Gemini CLI)
- MCP server configuration modals
- Atlassian Rovo MCP modal
- Mouse clicks, keyboard inputs, and other interactions outside the Jira modal

## Hypothesized Root Cause

The root causes were confirmed by the bug condition exploration test (Property 1) and resolved in the fix:

1. **Single Button in HTML Template**: `integrations.html` defined a single `<button id="btn-jira-save-test">SAVE & TEST</button>` instead of two separate buttons matching the AI provider pattern. **Fixed**: Replaced with a flex container containing `btn-jira-test` (TEST CONNECTION) + `btn-jira-save` (SAVE, disabled).

2. **Combined Save+Test Function**: `IntegrationsJiraModal.kt` had `saveAndTestJiraConfig()` which combined save + test into one call. **Fixed**: Split into `testJiraConnection()` (calls `PUT /api/integrations/jira/config`, enables SAVE on success) and `saveJiraConfig()` (visual confirmation + toast + close modal).

3. **Missing SAVE Disable Logic**: The Jira modal had no logic to disable the save action until a test succeeds. **Fixed**: Added `enableSaveButton()`, `disableSaveButton()`, and `resetSaveButton()` helpers. SAVE starts disabled (`opacity:0.4; pointer-events:none; disabled`) and is enabled only after successful test.

4. **Browser Native Styling Override**: The `<input type="email">` and `<input type="password">` elements had browser native styling overriding the dark background. **Fixed**: Added explicit inline `style="background:rgba(12,14,22,0.95);color:#e0e0e0;"` to both inputs.

## Correctness Properties

Property 1: Bug Condition - Jira Modal Button Layout Consistency

_For any_ interaction where the user opens the Jira Cloud Services config modal (isBugCondition returns true), the fixed modal SHALL display two separate buttons labeled "TEST CONNECTION" and "SAVE", with the SAVE button initially disabled (opacity 0.4, pointer-events none), and all input fields SHALL render with dark background matching the Obsidian Kinetic design system.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - AI Provider Modal Behavior Unchanged

_For any_ interaction where the user opens a non-Jira provider config modal (isBugCondition returns false), the fixed code SHALL produce exactly the same modal layout and behavior as the original code, preserving the separate "TEST CONNECTION" + "SAVE" button pattern, SAVE-disabled-until-test logic, and dark-themed input styling.

**Validates: Requirements 3.1, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Implemented

**File**: `frontend/src/jsMain/resources/templates/integrations.html`

**Section**: Jira Config Modal

**Changes**:
1. **Replaced single button with two buttons**: Removed `<button id="btn-jira-save-test">SAVE & TEST</button>` and added a flex container with two buttons:
   - `<button id="btn-jira-test">TEST CONNECTION</button>` — styled like `modal-btn-test` in AI provider modal (transparent bg, border, white text)
   - `<button id="btn-jira-save" class="btn-vibrant" disabled>SAVE</button>` — starts disabled with `opacity:0.4; pointer-events:none;`
2. **Fixed input field dark theme**: Added explicit `style="background:rgba(12,14,22,0.95);color:#e0e0e0;"` to the Email (`<input type="email">`) and API Token (`<input type="password">`) fields

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/IntegrationsJiraModal.kt`

**Changes**:
1. **Added `testJiraConnection(provider)` function**: Validates form fields are non-blank, calls `PUT /api/integrations/jira/config` with `JiraConfigRequest`. On success: enables SAVE button and shows "✓ Connected to Jira" status. On failure: keeps SAVE disabled and shows error. Uses `BlockingOverlay.show("jira-modal-content", "Testing connection...")`.
2. **Renamed `saveAndTestJiraConfig()` to `saveJiraConfig(provider)`**: Visual confirmation step only — shows "Configuration saved ✓" status, fires success toast via `ToastService`, closes modal after 1.5s delay, and re-renders provider cards. Does NOT re-call the API (config was already saved during test).
3. **Updated `bindJiraModalEvents()`**: Binds `btn-jira-test` → `testJiraConnection()` and `btn-jira-save` → `saveJiraConfig()`. Both gated by `ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)`.
4. **Extracted helper functions** (to meet 20-line function limit):
   - `readFormValues()` — reads domain, email, apiToken from form inputs
   - `handleTestResult()` — processes `JiraConfigResponse`, updates card status, enables/disables SAVE
   - `updateProviderState()` — updates in-memory provider list with new status/endpoint
   - `enableSaveButton()` / `disableSaveButton()` / `resetSaveButton()` — SAVE button state management
   - `showJiraStatus()` — displays status message with success/error styling

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Open the Jira config modal on unfixed code and inspect the DOM for button layout, input styling, and SAVE button state. Compare with AI provider modals.

**Test Cases**:
1. **Button Layout Test**: Open Jira modal → verify it has single "SAVE & TEST" button instead of two separate buttons (will fail consistency check on unfixed code)
2. **Input Styling Test**: Open Jira modal → inspect Email/API Token input computed background color (will show light background on unfixed code)
3. **SAVE Without Test**: Open Jira modal → verify SAVE & TEST button is immediately clickable without prior test (will demonstrate missing guard on unfixed code)
4. **AI Provider Comparison**: Open Ollama modal → verify it has separate "TEST CONNECTION" + "SAVE" with SAVE disabled (baseline for expected pattern)

**Expected Counterexamples**:
- Jira modal DOM contains `btn-jira-save-test` element with text "SAVE & TEST" instead of separate `btn-jira-test` + `btn-jira-save`
- Possible causes: HTML template defines single button, Kotlin binds single button handler

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  modal := openJiraConfigModal(input)
  ASSERT modal.hasButton("TEST CONNECTION")
    AND modal.hasButton("SAVE")
    AND NOT modal.hasButton("SAVE & TEST")
    AND modal.saveButton.isDisabled = true
    AND modal.inputFields.ALL(field => field.background = darkThemeBackground)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT openConfigModal_original(input) = openConfigModal_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain (different provider types)
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-Jira providers

**Test Plan**: Observe behavior on UNFIXED code first for AI provider modals, then write property-based tests capturing that behavior.

**Test Cases**:
1. **AI Provider Modal Preservation**: Verify opening Ollama/Gemini/LM Studio/Gemini CLI modals continues to show separate TEST + SAVE buttons with SAVE disabled
2. **RBAC Preservation**: Verify non-Administrator users continue to see disabled actions in Jira modal
3. **Close/Reset Preservation**: Verify closing Jira modal continues to reset progress and status indicators
4. **Card Status Preservation**: Verify successful/failed Jira test continues to update provider card status correctly

### Unit Tests

- Test that Jira modal HTML contains `btn-jira-test` and `btn-jira-save` elements after fix
- Test that `btn-jira-save` starts with `disabled` attribute and `opacity:0.4`
- Test that `testJiraConnection()` enables SAVE button on success response
- Test that `testJiraConnection()` keeps SAVE disabled on failure response
- Test that Email/API Token inputs have explicit dark background styling

### Property-Based Tests

- Generate random provider types and verify correct modal pattern is rendered (Jira → new pattern, others → existing pattern)
- Generate random test connection outcomes (success/failure) and verify SAVE button state matches expected (enabled on success, disabled on failure)
- Generate random sequences of open/test/save/close actions and verify modal state consistency

### Integration Tests

- Test full Jira config flow: open modal → fill fields → TEST CONNECTION → verify SAVE enabled → SAVE → verify card status updates
- Test Jira config flow with failed test: open modal → fill invalid fields → TEST CONNECTION → verify SAVE stays disabled → verify error message
- Test switching between Jira modal and AI provider modal to verify no cross-contamination of state
