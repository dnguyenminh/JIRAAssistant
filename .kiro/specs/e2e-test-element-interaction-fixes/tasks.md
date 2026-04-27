# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - ElementNotInteractableException on SVG selectors and broad CSS selectors
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the 12 failing tests exist due to wrong selectors and missing visibility checks
  - **Scoped PBT Approach**: Scope the property to the concrete failing methods across the 5 step files
  - **Test file**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/ElementInteractionBugConditionTest.kt`
  - **Pattern A — SVG selectors on Cytoscape canvas (KnowledgeGraphSteps)**:
    - Test that `userHoversOverNode()` fails because `By.cssSelector("svg circle, svg [class*='node']")` finds zero elements on a Cytoscape canvas page
    - Test that `userClicksNode("PROJ-10")` fails because `By.xpath("//*[contains(text(),'PROJ-10')]")` and `By.cssSelector("svg circle")` find zero interactable elements
    - Test that `userScrollsUp()` / `userScrollsDown()` fails because `By.tagName("svg")` targets a zero-size element, throwing `ElementNotInteractableException`
    - Test that `userClicksAndDrags()` fails because `By.tagName("svg")` targets a zero-size element
    - Test that `userClicksCloseOnDetailPanel()` fails because the detail panel was never opened (cascading from `userClicksNode` failure)
  - **Pattern B — Broad selector matches hidden element first**:
    - Test that `userTypesInChatInput(message)` in `AIChatSidebarSteps` fails because `By.id("chat-input")` element is not visible/scrolled into view when `.clear()` is called
    - Test that `userClicksEyeToggle()` in `IntegrationsSteps` fails because `By.cssSelector("[class*='toggle'], [class*='eye'], button[class*='visibility']")` matches a hidden element first
    - Test that `userClicksCloseButton()` in `IntegrationsSteps` fails because `By.cssSelector("[class*='close'], button[aria-label='close']")` matches a hidden element first
    - Test that `adminTogglesPermission(permissionName)` in `UserManagementSteps` fails because `By.cssSelector("input[type='checkbox'], [role='switch'], [class*='toggle']")` matches a hidden element first
  - The test assertions should match the Expected Behavior Properties from design: each step SHALL interact without throwing `ElementNotInteractableException`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct — it proves the bug exists)
  - Document counterexamples found (e.g., "SVG selectors return 0 elements", "first matched element isDisplayed=false")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.9, 1.10, 1.12_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Passing E2E steps remain unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **Test file**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/ElementInteractionPreservationTest.kt`
  - **Observe on UNFIXED code** that the following currently-passing methods work correctly:
    - `KnowledgeGraphSteps`: `graphSvgVisible()`, `graphDisplaysCircleNodes()`, `nodesLabeledWithTicketKey()`, `userTypesInSearch()`, `graphDisplaysEmptyState()` — these use `pageSource` checks, not SVG interaction
    - `AIChatSidebarSteps`: `toggleButtonVisible()`, `userClicksToggleButton()`, `sidebarShouldBeOpen()`, `userClicksCloseButton()`, `sidebarDisplaysChatInput()`, `sidebarDisplaysSendButton()` — these use specific selectors or `TestHelper.tryClick()` pattern
    - `IntegrationsSteps`: `userNavigatesToIntegrations()`, `pageDisplaysProviderCards()`, `cardDisplaysStatusDot()`, `userClicksConfigureOnJira()`, `modalAppearsWithTitle()`, `modalContainsCloseButton()` — these use specific selectors
    - `UserManagementSteps`: `userNavigatesToUserManagement()`, `userManagementPageDisplayed()`, `pageDisplaysUserList()`, `permissionPanelDisplaysToggles()` — these use `pageSource` checks
    - `TestHelper`: `wait()`, `js()`, `navigateTo()`, `pageRendered()`, `waitForOverlayGone()`, `waitForClickable()`, `tryClick()` — utility methods must behave identically
  - Write property-based test: for all step invocations where `isBugCondition` returns false (non-buggy steps), the step code produces the same result before and after the fix
  - Verify test passes on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 3. Fix ElementNotInteractableException across 4 step files

  - [x] 3.1 Fix KnowledgeGraphSteps.kt — Replace SVG selectors with Cytoscape container + JS API
    - Fix `userHoversOverNode()`: Replace `By.cssSelector("svg circle, svg [class*='node']")` with targeting `#graphCyContainer canvas` or `[class*='graph'] canvas`. Use JS to access `container._cyreg.cy.nodes().first().emit('mouseover')` or dispatch mousemove event on canvas center. Fallback: dispatch native mouseover event on canvas element
    - Fix `userClicksNode(ticketKey)`: Replace SVG/XPath selectors with JS execution that accesses `container._cyreg.cy` and emits `tap` event on node whose `label` data matches `ticketKey`. Fallback: emit `tap` on first node
    - Fix `userClicksCloseOnDetailPanel()`: Add `.filter { it.isDisplayed }` before `.first().click()`. Add scroll-into-view via `arguments[0].scrollIntoView({block:'center'})` and JS click fallback
    - Fix `userScrollsUp()` / `userScrollsDown()`: Replace `By.tagName("svg")` with targeting `#graphCyContainer canvas`. Use JS to call `cy.zoom()` via `container._cyreg.cy` or dispatch wheel event on canvas
    - Fix `userClicksAndDrags()`: Replace `By.tagName("svg")` with targeting `#graphCyContainer canvas`. Use JS to call `cy.pan()` via `container._cyreg.cy` or dispatch mousedown/mousemove/mouseup on canvas
    - _Bug_Condition: isBugCondition(step) where step.selectorTargetsSvgElements AND page.usesCytoscapeCanvas_
    - _Expected_Behavior: Each step interacts with Cytoscape container + JS API without ElementNotInteractableException_
    - _Preservation: KnowledgeGraphSteps methods that currently pass (graph rendering, search, colors, edges, empty state, clusters, performance, API error) must continue to work unchanged_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.2 Fix AIChatSidebarSteps.kt — Add wait for visibility + scroll into view + JS fallback
    - Fix `userTypesInChatInput(message)`: After finding element by `By.id("chat-input")`, add `TestHelper.waitForVisible(driver, By.id("chat-input"))` to wait for element to be displayed. Add scroll into view via `js(driver).executeScript("arguments[0].scrollIntoView({block:'center'})", input)`. Wrap `.clear()` + `.sendKeys()` in try/catch, falling back to JS: `arguments[0].value = ''; arguments[0].value = message; arguments[0].dispatchEvent(new Event('input', {bubbles:true}))`
    - This single fix resolves 3 scenarios: direct chat input (1.6), typing indicator (1.7), clicking navigate action (1.8), and also the McpServers scenario (1.11) via shared step
    - _Bug_Condition: isBugCondition(step) where step.element is chat-input AND NOT element.isDisplayed OR NOT element.isInViewport_
    - _Expected_Behavior: userTypesInChatInput waits for visibility, scrolls into view, and uses JS fallback without ElementNotInteractableException_
    - _Preservation: AIChatSidebarSteps methods that currently pass (toggle, open/close, send button, error handling, RBAC, page exclusions) must continue to work unchanged_
    - _Requirements: 2.6, 2.7, 2.8, 2.11_

  - [x] 3.3 Fix IntegrationsSteps.kt — Add isDisplayed filter + JS click fallback
    - Fix `userClicksEyeToggle()`: Add `.filter { it.isDisplayed }` after `driver.findElements(By.cssSelector("[class*='toggle'], [class*='eye'], button[class*='visibility']"))`. Add scroll-into-view and JS click fallback via try/catch pattern: `try { el.click() } catch (_: Exception) { TestHelper.js(driver).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click()", el) }`
    - Fix `userClicksCloseButton()`: Add `.filter { it.isDisplayed }` after `driver.findElements(By.cssSelector("[class*='close'], button[aria-label='close']"))`. Add scroll-into-view and JS click fallback
    - _Bug_Condition: isBugCondition(step) where broadSelector.matches(hiddenElements) AND elements.first().isDisplayed == false_
    - _Expected_Behavior: Each step filters for visible elements and clicks with JS fallback without ElementNotInteractableException_
    - _Preservation: IntegrationsSteps methods that currently pass (provider cards, Jira config modal, validation, save/test, priority reorder, RBAC) must continue to work unchanged_
    - _Requirements: 2.9, 2.10_

  - [x] 3.4 Fix UserManagementSteps.kt — Add isDisplayed filter + JS click fallback
    - Fix `adminTogglesPermission(permissionName)`: Add `.filter { it.isDisplayed }` after `driver.findElements(By.cssSelector("input[type='checkbox'], [role='switch'], [class*='toggle']"))`. Add scroll-into-view and JS click fallback via try/catch pattern
    - _Bug_Condition: isBugCondition(step) where broadSelector.matches(hiddenElements) AND elements.first().isDisplayed == false_
    - _Expected_Behavior: adminTogglesPermission filters for visible toggle and clicks with JS fallback without ElementNotInteractableException_
    - _Preservation: UserManagementSteps methods that currently pass (navigation, user list, role change, audit log, RBAC denial, permission matrix) must continue to work unchanged_
    - _Requirements: 2.12_

  - [x] 3.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - ElementNotInteractableException Eliminated
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior (each step interacts without exception)
    - When this test passes, it confirms the expected behavior is satisfied for all 12 previously failing scenarios
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.9, 2.10, 2.12_

  - [x] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Passing E2E steps remain unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint — Ensure all tests pass
  - Run the full E2E UI test suite (`./gradlew uiTest`) to verify all 178 tests pass (166 previously passing + 12 now fixed)
  - Verify no new failures introduced in any runner class
  - Ensure all tests pass, ask the user if questions arise
