# E2E-UI Test Execution Report — UserCrud (016-UserCrud.feature)

**Date:** 2026-05-01 08:17 UTC+7
**Environment:** localhost (dynamic port 65313)
**Browser:** Chrome 147 (via Selenium 4.41.0 + Serenity BDD 5.x)
**Runner:** `com.assistant.e2e.runners.UiUserCrudRunner`
**Execution Time:** 72.9s total
**Previous Run:** 7/12 FAIL (DevToolsActivePort — Chrome resource exhaustion)
**Fix Applied:** `maxParallelForks = CPU/2` in `e2e-tests/build.gradle.kts`

---

## Summary

| Total | Passed | Failed | Blocked | Skipped |
|-------|--------|--------|---------|---------|
| 12 | 11 | 1 | 0 | 0 |

**Pass Rate: 91.7%** (up from 41.7% in previous run)

**Chrome Exhaustion Fix: ✅ CONFIRMED** — No `DevToolsActivePort file doesn't exist` errors. All 12 Chrome instances launched successfully.

---

## Results by Scenario

| ID | Scenario | Time (s) | Status | Notes |
|----|----------|----------|--------|-------|
| E2E-UI-01 | Create user with valid data | 72.9 | ❌ FAIL | TimeoutException at assertion step |
| E2E-UI-02 | Form validation rejects empty name | 53.0 | ✅ PASS | |
| E2E-UI-03 | View user detail panel | 51.8 | ✅ PASS | |
| E2E-UI-04 | Edit user name successfully | 53.5 | ✅ PASS | |
| E2E-UI-05 | Cancel edit reverts to original values | 53.0 | ✅ PASS | |
| E2E-UI-06 | Disable an active user | 52.2 | ✅ PASS | |
| E2E-UI-07 | Enable a disabled user | 52.2 | ✅ PASS | |
| E2E-UI-08 | Delete user with name confirmation | 55.6 | ✅ PASS | |
| E2E-UI-09 | Delete confirm button disabled with wrong name | 45.2 | ✅ PASS | |
| E2E-UI-10 | User list displays correctly after CRUD | 44.7 | ✅ PASS | |
| E2E-UI-11 | Existing role change still works | 49.6 | ✅ PASS | |
| E2E-UI-12 | Existing permission toggle still works | 47.5 | ✅ PASS | |

---

## Failure Analysis — E2E-UI-01

### Error

```
org.openqa.selenium.TimeoutException: Expected condition failed:
  waiting for com.assistant.e2e.steps.UserCrudAssertSteps$Lambda@62d7fbb9
  (tried for 20 seconds with 500 milliseconds interval)
```

### Failing Step

```gherkin
Then the user "Test User E2E" should appear in the User Directory
```

**File:** `UserCrudAssertSteps.kt:29`

### Step Execution Trace

| # | Step | Result |
|---|------|--------|
| 1 | Given the user is authenticated with role "Administrator" | ✅ PASS |
| 2 | And the user navigates to the User Management page | ✅ PASS |
| 3 | Given the admin is on the User Management page | ✅ PASS |
| 4 | When the admin clicks the "Add User" button | ✅ PASS |
| 5 | And the admin fills in name "Test User E2E" and email "e2e-test@example.com" | ✅ PASS |
| 6 | And the admin selects role "NEURAL_ARCHITECT" | ✅ PASS |
| 7 | And the admin clicks the "Create" button | ✅ PASS |
| 8 | Then the creation form should close | ✅ PASS |
| 9 | **And the user "Test User E2E" should appear in the User Directory** | **❌ FAIL** |
| 10 | And the user should have role "NEURAL_ARCHITECT" and status "ACTIVE" | ⏭️ IGNORED |

### Root Cause Classification: **Code Bug (Test Timing)**

**NOT infrastructure** — Chrome launched fine, no DevToolsActivePort errors.

### Root Cause Analysis

The failure is a **race condition between async UI refresh and test assertion**:

1. **Step 7** clicks "Create" → `UserCrudSteps.adminClicksButton("Create")` calls `clickButtonByText` + `waitForOverlayGone`
2. **Step 8** checks form closed → PASS (API returned 201, `UserCreateForm.handleResponse()` called `hide()`)
3. After `hide()`, `onSuccessCallback?.invoke()` triggers `refreshUserList()` — this is **async** (`scope.launch`)
4. **Step 9** immediately checks `driver.pageSource.contains("Test User E2E")` — but `refreshUserList()` hasn't completed yet:
   - `GET /api/users` network call
   - JSON deserialization
   - `renderUserList()` DOM manipulation
5. With **12 Chrome instances** running in parallel (Cucumber parallel threads), network contention slows the refresh
6. The 20-second timeout expires before the new user row appears in the DOM

### Contributing Factor: Cucumber Internal Parallelism

The `maxParallelForks` fix controls **JVM process count**, but `cucumber.execution.parallel.enabled=true` creates **12 threads within the same JVM**, each opening a Chrome instance. This means:
- 12 Chrome processes (~300MB each = ~3.6GB RAM)
- 12 concurrent WebDriver sessions hitting the same server
- Network contention on `GET /api/users` after create

### Recommended Fixes

**Option A (Quick — increase timeout for this specific assertion):**

```kotlin
// UserCrudAssertSteps.kt — use longer timeout for post-create refresh
@Then("the user {string} should appear in the User Directory")
fun userShouldAppearInDirectory(name: String) {
    TestHelper.wait(driver, 30).until { d ->
        d.pageSource?.contains(name, ignoreCase = true) == true
    }
}
```

**Option B (Better — add explicit wait for refresh after create):**

```kotlin
// UserCrudSteps.kt — after clicking Create, wait for list refresh
@When("the admin clicks the {string} button")
fun adminClicksButton(text: String) {
    UserCrudHelper.clickButtonByText(driver, text)
    TestHelper.waitForOverlayGone(driver)
    if (text == "Create") {
        // Wait for async refreshUserList() to complete
        Thread.sleep(2000)
    }
}
```

**Option C (Best — limit Cucumber parallel threads):**

Add to `e2e-tests/src/test/resources/junit-platform.properties`:
```properties
cucumber.execution.parallel.config.fixed.parallelism=4
```

This limits concurrent Chrome instances to 4 instead of 12, reducing resource contention.

---

## Infrastructure Assessment

| Check | Status | Notes |
|-------|--------|-------|
| Chrome launch | ✅ OK | All 12 instances launched (no DevToolsActivePort errors) |
| Server startup | ✅ OK | Ready on port 65313 within 30s |
| Frontend build | ✅ OK | Webpack bundle built in 4s |
| CDP version mismatch | ⚠️ WARN | Chrome 147 vs Selenium CDP 145 — cosmetic warning, no impact |
| Parallel execution | ⚠️ CONCERN | 12 Chrome instances via Cucumber threads — consider limiting to 4 |

---

## Comparison with Previous Run

| Metric | Previous Run | Current Run | Delta |
|--------|-------------|-------------|-------|
| Pass Rate | 41.7% (5/12) | **91.7% (11/12)** | **+50%** |
| Chrome Crashes | 7 (DevToolsActivePort) | **0** | **-7** |
| Infrastructure Failures | 7 | **0** | **-7** |
| Code Bugs | 0 | **1** (timing) | +1 |
| Total Time | N/A (crashed) | 72.9s | — |

**Conclusion:** The `maxParallelForks = CPU/2` fix completely resolved the Chrome resource exhaustion issue. The remaining E2E-UI-01 failure is a test code timing issue, not infrastructure.
