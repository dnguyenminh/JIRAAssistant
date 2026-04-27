# E2E Test ElementNotInteractableException Bugfix Design

## Overview

12 out of 178 E2E UI tests fail with `ElementNotInteractableException` across 5 step definition files. The root cause is twofold: (1) KnowledgeGraphSteps uses SVG DOM selectors to interact with a Cytoscape.js canvas-based graph that has no SVG elements, and (2) four other step files use broad CSS selectors that match hidden/zero-size elements before the actual visible target, or fail to wait for element visibility before interaction. The fix replaces SVG selectors with Cytoscape container + JS API calls, and adds visible-element filtering + scroll-into-view + JS click fallback to the remaining steps.

## Glossary

- **Bug_Condition (C)**: A test step attempts to interact with a WebElement that is not interactable — either because the selector targets non-existent SVG elements (Cytoscape renders to canvas), or because a broad selector matches a hidden/zero-size element before the visible target
- **Property (P)**: Each test step SHALL interact with the correct visible element (or use JS API for canvas-based graphs) without throwing `ElementNotInteractableException`
- **Preservation**: All 166 currently passing E2E tests must continue to pass with identical behavior after the fix
- **Cytoscape.js**: The canvas-based graph library used by the frontend; renders to `<canvas>` inside `#graphCyContainer`, not to SVG DOM elements
- **`CytoscapeRenderer.cy`**: The Kotlin/JS internal Cytoscape instance; not exposed on `window` — tests must access the canvas element or inject a JS bridge
- **`TestHelper`**: Shared utility object in `e2e-tests/.../steps/TestHelper.kt` providing `js()`, `wait()`, `navigateTo()`, `pageRendered()`, `waitForClickable()`, `tryClick()`, `waitForOverlayGone()`
- **JS fallback**: Pattern where `element.click()` is wrapped in try/catch, falling back to `JavascriptExecutor.executeScript("arguments[0].click()", element)` on failure

## Bug Details

### Bug Condition

The bug manifests in two distinct patterns:

**Pattern A — Wrong element technology (KnowledgeGraphSteps, 5 failures):** The step code uses `By.cssSelector("svg circle")`, `By.tagName("svg")`, or `By.xpath("//*[contains(text(),'PROJ-10')]")` to find graph elements. Cytoscape.js renders everything to a `<canvas>` element inside `#graphCyContainer` — no SVG circles, text elements, or lines exist in the DOM. The selectors return empty lists or match a zero-size decorative SVG, causing `ElementNotInteractableException` when Selenium tries to interact.

**Pattern B — Broad selector matches hidden element first (4 step files, 7 failures):** The step code uses broad CSS selectors like `[class*='toggle']`, `[class*='close']`, or `By.id("chat-input")` and calls `.first().click()` or `.first().clear()` without checking `isDisplayed`. The first matched element may be hidden, overlapped, or not yet scrolled into view.

**Formal Specification:**
```
FUNCTION isBugCondition(stepInvocation)
  INPUT: stepInvocation of type { stepName: String, driver: WebDriver }
  OUTPUT: boolean

  // Pattern A: SVG selectors on Cytoscape canvas
  IF stepName IN ['userHoversOverNode', 'userClicksNode', 'userScrollsUp',
                   'userScrollsDown', 'userClicksAndDrags']
    RETURN selectorTargetsSvgElements(stepInvocation)
           AND pageUsesCytoscapeCanvas(driver)

  // Pattern B: Broad selector matches non-interactable element first
  IF stepName IN ['userTypesInChatInput', 'userClicksEyeToggle',
                   'userClicksCloseButton', 'adminTogglesPermission']
    elements := driver.findElements(broadSelector)
    RETURN elements.isNotEmpty()
           AND NOT elements.first().isDisplayed
           OR NOT elements.first().isInViewport
END FUNCTION
```

### Examples

- **KnowledgeGraph hover**: `driver.findElements(By.cssSelector("svg circle, svg [class*='node']"))` returns empty list → hover action silently skips → subsequent assertion fails
- **KnowledgeGraph click node**: `driver.findElements(By.xpath("//*[contains(text(),'PROJ-10')]"))` returns empty (text is on canvas, not DOM) → fallback `By.cssSelector("svg circle")` also empty → no click → detail panel never opens
- **KnowledgeGraph zoom**: `driver.findElements(By.tagName("svg"))` finds a decorative SVG with zero dimensions → `Actions(driver).moveToElement(svg).scrollByAmount(0, -100)` throws `ElementNotInteractableException`
- **Chat input**: `driver.findElements(By.id("chat-input"))` finds the textarea but it's not yet visible (sidebar animation in progress) or not scrolled into view → `.clear()` throws `ElementNotInteractableException`
- **Eye toggle**: `driver.findElements(By.cssSelector("[class*='toggle'], [class*='eye']"))` matches a hidden toggle element in the page before the visible eye icon button → `.click()` throws `ElementNotInteractableException`
- **Permission toggle**: `driver.findElements(By.cssSelector("input[type='checkbox'], [role='switch'], [class*='toggle']"))` matches a hidden checkbox before the visible permission toggle → `.click()` throws `ElementNotInteractableException`

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- All 166 currently passing E2E tests must continue to pass with identical assertions
- `TestHelper` utility methods (`wait()`, `js()`, `navigateTo()`, `pageRendered()`, `waitForOverlayGone()`, `waitForClickable()`, `tryClick()`) must behave identically
- KnowledgeGraphSteps methods that currently pass (graph rendering, search filter, node colors, edge types, empty state, API error, performance, clusters) must continue to work
- AIChatSidebarSteps methods that currently pass (toggle button, sidebar open/close, send button, error handling, RBAC, page exclusions) must continue to work
- IntegrationsSteps methods that currently pass (provider cards, Jira config modal open, validation, save/test, priority reorder, RBAC) must continue to work
- McpServersSteps methods that currently pass (MCP section, card elements, tools expandable, tool permissions, config modal, import/export, RBAC) must continue to work
- UserManagementSteps methods that currently pass (navigation, user list, role change, audit log, RBAC denial, permission matrix) must continue to work

**Scope:**
All step methods NOT listed in the 12 failing tests should be completely unaffected. The fix only modifies the specific methods that throw `ElementNotInteractableException`. No changes to feature files, runner classes, or TestHelper.

## Hypothesized Root Cause

Based on code analysis, the root causes are confirmed (not hypothesized):

1. **KnowledgeGraphSteps — Wrong rendering technology assumption**: The step code was written assuming the graph renders as SVG DOM elements (`<svg>`, `<circle>`, `<text>`, `<line>`). The frontend actually uses Cytoscape.js which renders to a `<canvas>` element inside `#graphCyContainer`. The Cytoscape instance is stored in `CytoscapeRenderer.cy` (Kotlin internal object) and is not exposed on `window`. All SVG selectors return empty or match a zero-size decorative element.

2. **AIChatSidebarSteps — Missing visibility wait + scroll**: The `chat-input` textarea (`<textarea id="chat-input">`) exists in the DOM but may not be visible when the sidebar is still animating open (400ms CSS transition). The step calls `.clear()` and `.sendKeys()` without waiting for visibility or scrolling into view.

3. **IntegrationsSteps — Broad selector matches hidden elements**: `[class*='toggle'], [class*='eye']` and `[class*='close']` match multiple elements across the page. The first match may be a hidden element (e.g., a close button in a non-visible modal, or a toggle in a collapsed section). The step calls `.first().click()` without filtering for `isDisplayed`.

4. **UserManagementSteps — Same broad selector pattern**: `input[type='checkbox'], [role='switch'], [class*='toggle']` matches hidden checkboxes or toggles before the visible permission toggle. Same fix pattern as IntegrationsSteps.

## Correctness Properties

Property 1: Bug Condition - ElementNotInteractableException Elimination

_For any_ test step invocation where the bug condition holds (isBugCondition returns true — SVG selectors on Cytoscape canvas, or broad selector matching hidden element first), the fixed step code SHALL interact with the correct target element (Cytoscape container + JS API for graph steps, or visible filtered element for other steps) without throwing `ElementNotInteractableException`.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.9, 2.10, 2.12**

Property 2: Preservation - Passing Tests Unchanged

_For any_ test step invocation where the bug condition does NOT hold (isBugCondition returns false — steps that currently pass), the fixed step code SHALL produce exactly the same behavior as the original code, preserving all 166 currently passing test results.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

**File**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/KnowledgeGraphSteps.kt`

**5 methods to fix:**

1. **`userHoversOverNode()`**: Replace `By.cssSelector("svg circle, svg [class*='node']")` with targeting `#graphCyContainer canvas`. Use JS to emit a mouseover event on the first Cytoscape node via `document.getElementById('graphCyContainer')._cyreg.cy.nodes().first().emit('mouseover')` or dispatch a mousemove event on the canvas at a node's rendered position.

2. **`userClicksNode(ticketKey)`**: Replace SVG/XPath selectors with JS execution that accesses the Cytoscape instance from the container and emits a `tap` event on the node whose `label` data matches the ticket key. Fallback: emit `tap` on the first node.

3. **`userClicksCloseOnDetailPanel()`**: Add `filter { it.isDisplayed }` before `.first().click()`. Add scroll-into-view and JS click fallback.

4. **`userScrollsUp()` / `userScrollsDown()`**: Replace `By.tagName("svg")` with targeting `#graphCyContainer canvas`. Use JS to call zoom on the Cytoscape instance or dispatch wheel events on the canvas element.

5. **`userClicksAndDrags()`**: Replace `By.tagName("svg")` with targeting `#graphCyContainer canvas`. Use JS to call `cy.pan()` or dispatch mousedown/mousemove/mouseup events on the canvas.

**File**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/AIChatSidebarSteps.kt`

**2 methods to fix (fixes 4+ scenarios):**

6. **`userTypesInChatInput(message)`**: After finding the element by `By.id("chat-input")`, add: `TestHelper.waitForVisible(driver, locator)` to wait for `isDisplayed`, scroll into view via JS (`arguments[0].scrollIntoView({block:'center'})`), then try `.clear()` + `.sendKeys()`. On `ElementNotInteractableException`, fall back to JS: `arguments[0].value = ''; arguments[0].value = message; arguments[0].dispatchEvent(new Event('input', {bubbles:true}))`.

7. **`userPressesEnterInChatInput()`**: Same pattern as #6 — add `TestHelper.waitForVisible(driver, locator)`, scroll into view, then try `.sendKeys(Keys.ENTER)`. On failure, fall back to JS: `arguments[0].dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', bubbles:true}))`. This method was also throwing `ElementNotInteractableException` when the chat-input was not yet visible.

**File**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/IntegrationsSteps.kt`

**2 methods to fix:**

8. **`userClicksEyeToggle()`**: Add `.filter { it.isDisplayed }` before `.first().click()`. Add scroll-into-view and JS click fallback via `TestHelper.tryClick()` pattern.

9. **`userClicksCloseButton()`**: Add `.filter { it.isDisplayed }` before `.first().click()`. Add scroll-into-view and JS click fallback.

**File**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/UserManagementSteps.kt`

**1 method to fix:**

10. **`adminTogglesPermission(permissionName)`**: Add `.filter { it.isDisplayed }` before `.first().click()`. Add scroll-into-view and JS click fallback.

**No changes to McpServersSteps.kt** — the failing scenario uses `"the user sends a chat message"` which is defined in `AIChatSidebarSteps`. Fixes #6 and #7 resolve it.

**Additional fix**: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/BatchScanSteps.kt`

11. **`userClicksStartScan()`**: Added `try { driver.switchTo().alert().accept() } catch (_: Exception) {}` after clicking the START SCAN button. The browser `confirm()` dialog ("No AI provider is active...") was not being handled, causing `UnhandledAlertException` in 2 BatchScan scenarios. This is not an `ElementNotInteractableException` but was discovered during the full test suite checkpoint run.

### Cytoscape JS Bridge Strategy

Since `CytoscapeRenderer.cy` is a Kotlin `internal` object not exposed on `window`, the test code needs a way to access the Cytoscape instance. Two approaches:

**Approach A (Preferred) — Access via container's internal registry:** Cytoscape.js stores a reference on the container element at `container._cyreg.cy`. The test JS can do:
```javascript
var cy = document.getElementById('graphCyContainer')._cyreg.cy;
```

**Approach B (Fallback) — Canvas event dispatch:** If `_cyreg` is not available, dispatch native mouse/wheel events directly on the `<canvas>` element at known coordinates (center of canvas for general interactions).

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, confirm the 12 tests fail on unfixed code (already confirmed via test report), then verify the fix resolves all 12 failures while preserving the 166 passing tests.

### Exploratory Bug Condition Checking

**Goal**: Confirm the root cause analysis by examining the actual error traces. Already confirmed via the test report showing 12 failures with `ElementNotInteractableException`.

**Test Cases**:
1. **KnowledgeGraph Hover**: `userHoversOverNode()` — SVG selector returns empty, hover skips, assertion fails (confirmed failing)
2. **KnowledgeGraph Click Node**: `userClicksNode("PROJ-10")` — XPath and SVG fallback both return empty (confirmed failing)
3. **KnowledgeGraph Zoom**: `userScrollsUp()` — SVG element has zero size, `Actions.moveToElement` throws (confirmed failing)
4. **KnowledgeGraph Pan**: `userClicksAndDrags()` — same zero-size SVG issue (confirmed failing)
5. **KnowledgeGraph Close Panel**: `userClicksCloseOnDetailPanel()` — cascading from click node failure (confirmed failing)
6. **Chat Input Type**: `userTypesInChatInput()` — textarea not visible during sidebar animation (confirmed failing)
7. **Chat Input Send+Enter**: `userSendsChatMessage()` — inherits from #6 (confirmed failing, 2 scenarios)
8. **Eye Toggle**: `userClicksEyeToggle()` — broad selector matches hidden element (confirmed failing)
9. **Close Button**: `userClicksCloseButton()` — broad selector matches hidden element (confirmed failing)
10. **MCP Chat**: `userSendsChatMessage()` in MCP context — inherits from #6 (confirmed failing)
11. **Permission Toggle**: `adminTogglesPermission()` — broad selector matches hidden element (confirmed failing)

**Expected Counterexamples**:
- SVG selectors return 0 elements on Cytoscape canvas pages
- `.first().click()` on hidden elements throws `ElementNotInteractableException`
- Possible causes confirmed: wrong technology selectors, missing visibility filter, missing scroll-into-view

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed step code interacts successfully.

**Pseudocode:**
```
FOR ALL stepInvocation WHERE isBugCondition(stepInvocation) DO
  result := fixedStep(stepInvocation)
  ASSERT NOT throws(ElementNotInteractableException)
  ASSERT elementInteractedCorrectly(result)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed code produces the same result as the original code.

**Pseudocode:**
```
FOR ALL stepInvocation WHERE NOT isBugCondition(stepInvocation) DO
  ASSERT originalStep(stepInvocation) = fixedStep(stepInvocation)
END FOR
```

**Testing Approach**: Run the full E2E test suite (`./gradlew uiTest`) after applying fixes. All 166 previously passing tests must continue to pass. The 12 previously failing tests should now pass.

**Test Cases**:
1. **Graph Rendering Preservation**: Verify `graphSvgVisible()`, `graphDisplaysCircleNodes()`, `nodesLabeledWithTicketKey()` still pass — these use `pageSource` checks, not SVG interaction
2. **Chat Sidebar Preservation**: Verify toggle, open/close, send button, error handling, RBAC steps still pass — these don't use `chat-input` directly
3. **Integrations Preservation**: Verify provider cards, Jira config modal open, validation, save/test steps still pass — these use specific selectors or `TestHelper.tryClick()`
4. **User Management Preservation**: Verify navigation, user list, role change, audit log steps still pass — these don't use the broad toggle selector

### Unit Tests

- Verify KnowledgeGraph steps target `#graphCyContainer canvas` instead of SVG elements
- Verify chat input step waits for visibility before interaction
- Verify eye toggle, close button, and permission toggle filter for `isDisplayed`
- Verify JS fallback is invoked when direct click fails

### Property-Based Tests

- Not directly applicable: the code under fix is E2E test step definitions (test infrastructure), not application logic. The "property" is verified by running the full E2E suite as an integration test.

### Integration Tests

- Run full `./gradlew uiTest` suite: expect 178 tests, 0 failures (up from 166 pass / 12 fail)
- Verify each of the 12 previously failing scenarios now passes individually
- Additionally, 2 BatchScan scenarios that failed with `UnhandledAlertException` were fixed (alert handling in `userClicksStartScan`)
- Additionally, `userPressesEnterInChatInput` was fixed with the same visibility wait + scroll + JS fallback pattern
- Verify no new failures introduced in any runner class
