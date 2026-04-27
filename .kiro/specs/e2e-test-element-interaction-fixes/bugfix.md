# Bugfix Requirements Document

## Introduction

12 out of 178 E2E UI tests fail with `org.openqa.selenium.ElementNotInteractableException`. All failures are in the test step code — the frontend application is correct. The tests attempt to interact with elements that are either not visible/interactable, use wrong selectors (e.g., SVG selectors for a Cytoscape.js canvas-based graph), or don't wait for elements to become interactable before acting. This bugfix targets the 5 affected step definition files across 5 test runner groups.

## Bug Analysis

### Current Behavior (Defect)

**Group 1 — KnowledgeGraphSteps (5 failures): Wrong element selectors for Cytoscape.js canvas**

1.1 WHEN `userHoversOverNode()` is called THEN the system tries `By.cssSelector("svg circle, svg [class*='node']")` which finds zero elements because Cytoscape.js renders to `<canvas>`, not SVG, causing the hover action to silently skip and the test to fail on subsequent assertions

1.2 WHEN `userClicksNode(ticketKey)` is called THEN the system tries `By.xpath("//*[contains(text(),'PROJ-10')]")` and falls back to `By.cssSelector("svg circle")` which finds zero interactable elements because Cytoscape.js renders nodes on a canvas, not as DOM elements

1.3 WHEN `userClicksCloseOnDetailPanel()` is called (which depends on 1.2 opening the panel first) THEN the system tries `By.cssSelector("[class*='close'], button[aria-label='close']")` which may match non-interactable elements or fail because the detail panel was never opened

1.4 WHEN `userScrollsUp()` is called THEN the system tries `By.tagName("svg")` and calls `Actions(driver).moveToElement(svg.first()).scrollByAmount(0, -100)` which throws `ElementNotInteractableException` because the SVG element has zero size and no location in the Cytoscape.js layout

1.5 WHEN `userClicksAndDrags()` is called THEN the system tries `By.tagName("svg")` and calls `Actions(driver).moveToElement(svg.first()).clickAndHold().moveByOffset(50, 50).release()` which throws `ElementNotInteractableException` because the SVG element has zero size and no location

**Group 2 — AIChatSidebarSteps (3 failures + 1 related): Element not interactable on chat input**

1.6 WHEN `userTypesInChatInput(message)` is called THEN the system calls `inputs.first().clear()` then `inputs.first().sendKeys(message)` on the `chat-input` element which throws `ElementNotInteractableException` because the element is not yet visible, not scrolled into view, or is overlapped by another element

1.6a WHEN `userPressesEnterInChatInput()` is called THEN the system calls `inputs.first().sendKeys(Keys.ENTER)` on the `chat-input` element which throws `ElementNotInteractableException` for the same visibility/scroll reasons as 1.6. This was discovered during the full test suite checkpoint run.

1.7 WHEN `userSendsChatMessage(message)` is called (which calls `userTypesInChatInput`) for the "Typing indicator" scenario THEN the same `ElementNotInteractableException` occurs on the `chat-input` element

1.8 WHEN `userSendsChatMessage(message)` is called for the "Clicking navigate action" scenario THEN the same `ElementNotInteractableException` occurs on the `chat-input` element

**Group 3 — IntegrationsSteps (2 failures): CSS selector matches non-interactable elements**

1.9 WHEN `userClicksEyeToggle()` is called THEN the system tries `By.cssSelector("[class*='toggle'], [class*='eye'], button[class*='visibility']")` and calls `.click()` on the first match which throws `ElementNotInteractableException` because the broad selector matches hidden or zero-size elements before the actual visible eye toggle button

1.10 WHEN `userClicksCloseButton()` is called THEN the system tries `By.cssSelector("[class*='close'], button[aria-label='close']")` and calls `.click()` on the first match which throws `ElementNotInteractableException` because the broad selector matches hidden or non-interactable close buttons before the visible modal close button

**Group 4 — McpServersSteps (1 failure): Element not interactable in chat context**

1.11 WHEN the "Internal tools use home icon in chat" scenario runs THEN `userSendsChatMessage("Navigate to integrations")` is called which delegates to `AIChatSidebarSteps.userTypesInChatInput()` and throws `ElementNotInteractableException` on the `chat-input` element for the same reason as 1.6

**Group 5 — UserManagementSteps (1 failure): Toggle element not interactable**

1.12 WHEN `adminTogglesPermission(permissionName)` is called THEN the system tries `By.cssSelector("input[type='checkbox'], [role='switch'], [class*='toggle']")` and calls `.click()` on the first match which throws `ElementNotInteractableException` because the broad selector matches hidden or non-interactable toggle elements before the actual visible permission toggle

**Group 6 — BatchScanSteps (2 failures, discovered during checkpoint): Unhandled browser alert**

1.13 WHEN `userClicksStartScan()` is called and no AI provider is active THEN the browser shows a `confirm()` dialog ("No AI provider is active...") which is not handled, causing `UnhandledAlertException` in 2 BatchScan scenarios. This is not an `ElementNotInteractableException` but was discovered during the full test suite checkpoint run.

### Expected Behavior (Correct)

**Group 1 — KnowledgeGraphSteps: Use Cytoscape container + JS API**

2.1 WHEN `userHoversOverNode()` is called THEN the system SHALL target the Cytoscape container (`#graphCyContainer` or `canvas`) and use JavaScript execution via the Cytoscape API (`cy.nodes()`) to trigger a mouseover event on a node, without throwing any exception

2.2 WHEN `userClicksNode(ticketKey)` is called THEN the system SHALL target the Cytoscape container and use JavaScript execution via the Cytoscape API to emit a `tap` event on the node matching the ticket key, or on the first available node, without throwing any exception

2.3 WHEN `userClicksCloseOnDetailPanel()` is called THEN the system SHALL wait for the close button to be visible and interactable (using `TestHelper.waitForClickable` or filtering for `isDisplayed`), scroll it into view if needed, and click it using JS fallback if direct click fails

2.4 WHEN `userScrollsUp()` is called THEN the system SHALL target the Cytoscape container element (not SVG) and use JavaScript execution to call `cy.zoom()` via the Cytoscape API or dispatch a wheel event on the canvas element, without throwing any exception

2.5 WHEN `userClicksAndDrags()` is called THEN the system SHALL target the Cytoscape container element (not SVG) and use JavaScript execution to call `cy.pan()` via the Cytoscape API or dispatch mouse events on the canvas element, without throwing any exception

**Group 2 — AIChatSidebarSteps: Wait for visibility + scroll into view + JS fallback**

2.6 WHEN `userTypesInChatInput(message)` is called THEN the system SHALL wait for the `chat-input` element to be visible and interactable, scroll it into view, and use JavaScript execution to set its value and dispatch input events if direct `clear()`/`sendKeys()` throws an exception

2.6a WHEN `userPressesEnterInChatInput()` is called THEN the system SHALL wait for the `chat-input` element to be visible and interactable, scroll it into view, and use JavaScript execution to dispatch a KeyboardEvent('keydown', {key:'Enter'}) if direct `sendKeys(Keys.ENTER)` throws an exception

2.7 WHEN `userSendsChatMessage(message)` is called for the "Typing indicator" scenario THEN the system SHALL successfully type and send the message without `ElementNotInteractableException` (inherits fix from 2.6 and 2.6a)

2.8 WHEN `userSendsChatMessage(message)` is called for the "Clicking navigate action" scenario THEN the system SHALL successfully type and send the message without `ElementNotInteractableException` (inherits fix from 2.6 and 2.6a)

**Group 3 — IntegrationsSteps: Filter for visible elements or use specific selectors**

2.9 WHEN `userClicksEyeToggle()` is called THEN the system SHALL filter the matched elements to only those that are displayed (`isDisplayed == true`), scroll the visible element into view, and click it using JS fallback if direct click fails, without throwing any exception

2.10 WHEN `userClicksCloseButton()` is called THEN the system SHALL filter the matched elements to only those that are displayed (`isDisplayed == true`), scroll the visible element into view, and click it using JS fallback if direct click fails, without throwing any exception

**Group 4 — McpServersSteps: Inherits chat input fix**

2.11 WHEN the "Internal tools use home icon in chat" scenario runs THEN `userSendsChatMessage()` SHALL successfully interact with the `chat-input` element without `ElementNotInteractableException` (inherits fix from 2.6 via shared step)

**Group 5 — UserManagementSteps: Filter for visible toggle + JS fallback**

2.12 WHEN `adminTogglesPermission(permissionName)` is called THEN the system SHALL filter the matched toggle elements to only those that are displayed (`isDisplayed == true`), scroll the visible element into view, and click it using JS fallback if direct click fails, without throwing any exception

**Group 6 — BatchScanSteps: Handle browser confirm dialog**

2.13 WHEN `userClicksStartScan()` is called and no AI provider is active THEN the system SHALL accept the browser `confirm()` dialog via `driver.switchTo().alert().accept()` to prevent `UnhandledAlertException`

### Unchanged Behavior (Regression Prevention)

3.1 WHEN any of the 166 currently passing E2E UI tests run THEN the system SHALL CONTINUE TO pass with the same assertions and behavior as before the fix

3.2 WHEN KnowledgeGraphSteps methods that currently pass (graph rendering, search filter, empty state, API error) are called THEN the system SHALL CONTINUE TO find elements and verify page content correctly

3.3 WHEN AIChatSidebarSteps methods that currently pass (toggle button, sidebar open/close, send button, error handling, RBAC) are called THEN the system SHALL CONTINUE TO interact with elements correctly

3.4 WHEN IntegrationsSteps methods that currently pass (provider cards, Jira config modal open, validation, save/test, priority reorder, RBAC) are called THEN the system SHALL CONTINUE TO interact with elements correctly

3.5 WHEN McpServersSteps methods that currently pass (MCP section visibility, card elements, tools expandable, tool permissions, config modal, import/export, RBAC) are called THEN the system SHALL CONTINUE TO interact with elements correctly

3.6 WHEN UserManagementSteps methods that currently pass (navigation, user list, role change, audit log, RBAC denial, permission matrix) are called THEN the system SHALL CONTINUE TO interact with elements correctly

3.7 WHEN `TestHelper` utility methods (`wait()`, `js()`, `navigateTo()`, `pageRendered()`, `waitForOverlayGone()`, `waitForClickable()`, `tryClick()`) are called from any step THEN the system SHALL CONTINUE TO behave identically with the same timeouts and fallback logic
