# Implementation Plan — Jira Modal Consistency Bugfix

## Tasks

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Jira Modal Button Layout & Input Styling Inconsistency
  - **IMPORTANT**: Write this property-based test BEFORE implementing the fix
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the Jira modal deviates from the AI provider modal pattern
  - **Scoped PBT Approach**: Scope the property to the concrete failing case: `providerType = "JIRA"` with `action = "OPEN_CONFIG_MODAL"`
  - Test that opening the Jira config modal produces a DOM with:
    - Two separate buttons: "TEST CONNECTION" (`btn-jira-test`) and "SAVE" (`btn-jira-save`)
    - No "SAVE & TEST" button (`btn-jira-save-test` should NOT exist)
    - SAVE button initially disabled (`opacity:0.4`, `pointer-events:none`, `disabled=true`)
    - Email input (`jira-email`) has dark background (`rgba(12,14,22,0.95)`)
    - API Token input (`jira-api-token`) has dark background (`rgba(12,14,22,0.95)`)
  - Bug Condition from design: `isBugCondition(input) WHERE input.providerType = "JIRA" AND input.action = "OPEN_CONFIG_MODAL"`
  - Expected Behavior from design: modal SHALL display separate "TEST CONNECTION" + "SAVE" buttons, SAVE disabled, all inputs dark-themed
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct — proves the bug exists: single "SAVE & TEST" button, light input backgrounds, no SAVE disable logic)
  - Document counterexamples found (e.g., "Jira modal DOM contains `btn-jira-save-test` instead of `btn-jira-test` + `btn-jira-save`; Email input has white background")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - AI Provider Modal Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **IMPORTANT**: Write and run these tests BEFORE implementing the fix
  - Observe on UNFIXED code: AI provider modals (Ollama, Gemini, LM Studio, Gemini CLI) display separate "TEST CONNECTION" (`modal-btn-test`) + "SAVE" (`modal-btn-save`) buttons
  - Observe on UNFIXED code: AI provider modal SAVE button starts disabled (`opacity:0.4`, `pointer-events:none`)
  - Observe on UNFIXED code: AI provider modal input fields use dark background via `.field-input` class
  - Observe on UNFIXED code: `IntegrationsConfigModal.openConfigModal()` routes JIRA providers to `IntegrationsJiraModal` and non-JIRA providers to the standard config modal
  - Write property-based test: for all non-JIRA provider types (Ollama, Gemini, LM Studio, Gemini CLI), opening the config modal produces:
    - "TEST CONNECTION" button (`modal-btn-test`) present
    - "SAVE" button (`modal-btn-save`) present and initially disabled
    - No "SAVE & TEST" button in the modal
    - Modal HTML built by `buildModalHtml()` contains correct button structure
  - Preservation from design: `FOR ALL X WHERE NOT isBugCondition(X) DO ASSERT F(X) = F'(X)` — AI provider modals behave identically before and after fix
  - Verify tests pass on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline AI provider modal behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix Jira modal to match AI provider modal pattern

  - [x] 3.1 Update `integrations.html` — Replace single button with two-button layout
    - Replace `<button id="btn-jira-save-test">SAVE & TEST</button>` with a flex container containing:
      - `<button id="btn-jira-test">TEST CONNECTION</button>` — styled like `modal-btn-test` in AI provider modal (transparent bg, border, white text)
      - `<button id="btn-jira-save" class="btn-vibrant" disabled>SAVE</button>` — starts disabled with `opacity:0.4; pointer-events:none;`
    - Add explicit dark theme override to Email input: `style="background:rgba(12,14,22,0.95);color:#e0e0e0;"` on `<input type="email" id="jira-email">`
    - Add explicit dark theme override to API Token input: `style="background:rgba(12,14,22,0.95);color:#e0e0e0;"` on `<input type="password" id="jira-api-token">`
    - _Bug_Condition: isBugCondition(input) WHERE input.providerType = "JIRA"_
    - _Expected_Behavior: modal displays separate "TEST CONNECTION" + "SAVE" buttons, SAVE disabled, dark-themed inputs_
    - _Preservation: AI provider modal HTML template unchanged (uses separate `integ-modal-overlay`)_
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3_

  - [x] 3.2 Update `IntegrationsJiraModal.kt` — Add `testJiraConnection()` and rename `saveAndTestJiraConfig()` to `saveJiraConfig()`
    - Add `testJiraConnection(provider: ProviderInfo)` function that:
      - Reads form values (domain, email, apiToken)
      - Validates all fields are non-blank
      - Calls `PUT /api/integrations/jira/config` with `JiraConfigRequest`
      - On success: enables SAVE button (`opacity:1`, `pointer-events:auto`, `disabled=false`), shows success status
      - On failure: keeps SAVE disabled, shows error status
      - Uses `BlockingOverlay.show("jira-modal-content", "Testing connection...")`
    - Rename `saveAndTestJiraConfig()` to `saveJiraConfig()` — visual confirmation step (config already saved by test)
      - Shows success toast, updates card status, closes modal after delay
    - Update `bindJiraModalEvents()`:
      - Bind `btn-jira-test` click → `testJiraConnection(provider)`
      - Bind `btn-jira-save` click → `saveJiraConfig(provider)`
      - Remove `btn-jira-save-test` binding
    - _Bug_Condition: isBugCondition(input) WHERE input.providerType = "JIRA"_
    - _Expected_Behavior: separate test + save flow matching IntegrationsConfigModal pattern_
    - _Preservation: IntegrationsConfigModal.kt unchanged, AI provider flow unaffected_
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 3.1, 3.2_

  - [x] 3.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Jira Modal Button Layout & Input Styling Consistency
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied:
      - Jira modal has separate "TEST CONNECTION" + "SAVE" buttons
      - SAVE button starts disabled
      - Email and API Token inputs have dark background
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.4 Verify preservation tests still pass
    - **Property 2: Preservation** - AI Provider Modal Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in AI provider modals)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run full test suite to verify no regressions
  - Verify bug condition test (Property 1) passes — Jira modal has correct layout
  - Verify preservation test (Property 2) passes — AI provider modals unchanged
  - Ensure no backend changes were needed (existing endpoints reused)
  - Ask the user if questions arise
