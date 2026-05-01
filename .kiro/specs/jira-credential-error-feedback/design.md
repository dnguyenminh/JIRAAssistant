# Jira Credential Error Feedback — Bugfix Design

## Overview

When Jira credentials exist in the database but fail to decrypt (ENCRYPTION_KEY mismatch), `ProviderConfigRepository.decryptApiKey()` returns an empty string `""`. `JiraCredentialsService.getJiraCredentials()` then returns `null` (because `decryptedApiKey.isNullOrBlank()` is true), and `createJiraClientFromDb()` returns `NoOpJiraClient`. The `/api/projects` endpoint responds with `200 []` — indistinguishable from "Jira not configured" or "no projects exist". The frontend shows a generic "No projects available" message with no actionable guidance.

The fix introduces a `JiraCredentialState` enum to distinguish between NOT_CONFIGURED, CREDENTIALS_INVALID, and OK states. The `/api/projects` endpoint will return a wrapper response object containing both the project list and the credential state. The frontend will check this state and display an appropriate error message with a link to Integrations when credentials are invalid.

## Glossary

- **Bug_Condition (C)**: Jira credentials exist in DB (`provider_configs` has a `jira` row) but `decryptApiKey()` returns empty string due to ENCRYPTION_KEY mismatch
- **Property (P)**: The API response includes a `jiraStatus` field indicating `CREDENTIALS_INVALID`, and the frontend displays an error message with a link to Integrations
- **Preservation**: Valid credentials returning projects, valid credentials with empty Jira, and unauthenticated requests must behave identically to current behavior
- **`JiraCredentialsService`**: Service in `shared/.../jira/` that reads and decrypts Jira credentials from `provider_configs` table
- **`createJiraClientFromDb()`**: Helper in `ProjectRoutes.kt` that creates a `JiraClient` from DB credentials, returning `NoOpJiraClient` when credentials are unavailable
- **`ProjectSelectPage`**: Frontend page controller that loads projects and handles empty/error states

## Bug Details

### Bug Condition

The bug manifests when a Jira provider config row exists in the database but the stored `api_key` cannot be decrypted (typically because `ENCRYPTION_KEY` environment variable changed). `ProviderConfigRepository.decryptApiKey()` catches the exception and returns `""`. `JiraCredentialsService` sees blank apiKey and returns `null`. The system then treats this identically to "Jira not configured" — returning `200 []` with no error context.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type JiraCredentialState
  OUTPUT: boolean
  
  RETURN input.providerConfigRowExists = true
         AND input.apiKeyColumnIsNotNull = true
         AND input.decryptedApiKeyValue IS BLANK
         // i.e., credentials exist but decrypt failed
END FUNCTION
```

### Examples

- User changes `ENCRYPTION_KEY` in `.env` and restarts server → `/api/projects` returns `200 []` → frontend shows "No projects available" with RETRY button → user has no idea the issue is encryption
- User migrates database to new machine without matching encryption key → same silent failure
- User accidentally corrupts the `api_key` column → same silent failure
- Valid case: User has never configured Jira → `findById("jira")` returns `null` → correctly shows "not configured" flow (this is NOT the bug)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- When credentials are valid and Jira API returns projects, the endpoint returns `200` with the project list and frontend renders the project table
- When credentials are valid but Jira has no projects, the endpoint returns `200` with empty list and frontend shows "No projects available" + RETRY
- When user is unauthenticated, the endpoint returns `401` and frontend redirects to login
- When Jira is not configured (no row in DB) AND user is Administrator, frontend redirects to Integrations page
- Mouse clicks, sorting, searching, pagination on the project table continue to work

**Scope:**
All inputs where `isBugCondition` is false should be completely unaffected. This includes:
- Valid credentials with projects
- Valid credentials with empty Jira
- No Jira configuration at all (no DB row)
- Unauthenticated requests

## Hypothesized Root Cause

Based on the code analysis, the root cause chain is:

1. **`ProviderConfigRepository.decryptApiKey()` swallows exceptions**: When `CryptoUtils.decryptAES256GCM()` throws (Tag mismatch, wrong key), the catch block returns `""` and only prints to stderr. No error state is propagated.

2. **`JiraCredentialsService.getJiraCredentials()` conflates two failure modes**: It returns `null` for both "no config exists" AND "config exists but decrypt failed". The caller cannot distinguish these cases.

3. **`createJiraClientFromDb()` returns `NoOpJiraClient` for both cases**: Since it only checks `credentials == null`, it treats decrypt failure the same as "not configured".

4. **`/api/projects` endpoint has no error signaling**: It always returns `200` with the project list (empty or not), with no metadata about credential state.

5. **Frontend `checkJiraStatus()` is insufficient**: It calls `/api/integrations/jira/status` which reports `configured: true` (because the row exists), so the frontend falls into `showEmptyWithRetry()` — a dead end for the user.

## Correctness Properties

Property 1: Bug Condition - Credential Error Feedback

_For any_ request to `/api/projects` where Jira credentials exist in the database but fail to decrypt (isBugCondition returns true), the fixed endpoint SHALL return a response containing a `jiraStatus` field with value `"CREDENTIALS_INVALID"`, and the frontend SHALL display an error message indicating credential failure with a navigable link to the Integrations page.

**Validates: Requirements 2.1, 2.3**

Property 2: Preservation - Valid Credential Behavior

_For any_ request to `/api/projects` where the bug condition does NOT hold (credentials are valid, or not configured, or user is unauthenticated), the fixed code SHALL produce the same observable behavior as the original code, preserving project listing, empty state handling, and authentication redirects.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**File**: `shared/src/jvmMain/kotlin/com/assistant/jira/JiraCredentialsService.kt`

**Changes**:
1. **Add `JiraCredentialState` enum**: `NOT_CONFIGURED`, `CREDENTIALS_INVALID`, `OK`
2. **Add `getCredentialState()` method**: Returns the enum value by checking if config exists and if decrypt succeeds, without conflating the two failure modes

**File**: `server/core/src/jvmMain/kotlin/com/assistant/server/routes/ProjectRoutes.kt`

**Changes**:
3. **Add `ProjectsResponse` data class**: Wrapper with `projects: List<JiraProject>` and `jiraStatus: String` fields
4. **Modify GET `/api/projects`**: Before calling `createJiraClientFromDb()`, check `credentialsService.getCredentialState()`. If `CREDENTIALS_INVALID`, return `ProjectsResponse(projects = emptyList(), jiraStatus = "CREDENTIALS_INVALID")`. If `NOT_CONFIGURED`, return `ProjectsResponse(projects = emptyList(), jiraStatus = "NOT_CONFIGURED")`. If `OK`, proceed normally and return `ProjectsResponse(projects = ..., jiraStatus = "OK")`.

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/ProjectModels.kt`

**Changes**:
5. **Add `ProjectsResponse` data class**: Frontend-side `@Serializable` wrapper with `projects: List<ProjectInfo>` and `jiraStatus: String` fields (defaults: `emptyList()`, `"OK"`).
6. **Add `ProjectInfo` data class**: Frontend-side project model with `id`, `key`, `name`, `projectTypeKey` fields.

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ProjectSelectPage.kt`

**Changes**:
7. **Modify `loadProjects()`**: Parse response as `ProjectsResponse` object instead of `List<JiraProject>`. Delegate to `handleProjectsResponse()` which dispatches based on `jiraStatus`.
8. **Add `handleProjectsResponse()` method**: Routes `"CREDENTIALS_INVALID"` → `showCredentialError()`, `"NOT_CONFIGURED"` → `handleJiraConfigResult(false)`, `"OK"` with empty → `showEmptyWithRetry()`, `"OK"` with projects → render table. Removed old `checkJiraStatus()` method (no longer needed).

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ProjectSelectStates.kt`

**Changes**:
9. **Extract `ProjectSelectStates` object**: UI state helpers extracted from `ProjectSelectPage` to keep file under 200 lines (SRP). Contains `showCredentialError()`, `showJiraNotConfigured()`, and `showEmptyWithRetry()`.
10. **`showCredentialError()`**: Clones `tmpl-credential-error` template into table body, wires "Go to Integrations" button click to navigate to `#integrations` via `Router.navigateTo()`.

**File**: `frontend/src/jsMain/resources/templates/project-select.html`

**Changes**:
11. **Add `<template id="tmpl-credential-error">`**: HTML template with 🔐 icon, "Jira Connection Failed" title, "Credentials could not be decrypted. Please reconfigure in Integrations settings." message, and "Go to Integrations" button (`id="btn-go-integrations"`).

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis.

**Test Plan**: Write tests that call `JiraCredentialsService.getJiraCredentials()` with a mismatched encryption key and verify the current behavior returns `null` with no way to distinguish from "not configured". Then call the `/api/projects` endpoint and verify it returns `200 []`.

**Test Cases**:
1. **Decrypt Failure Test**: Configure a `jira` provider config with encrypted apiKey, then change encryption key → `getJiraCredentials()` returns null (will demonstrate the conflation on unfixed code)
2. **API Response Test**: With decrypt failure active, call GET `/api/projects` → returns `200 []` with no error indicator (demonstrates silent failure)
3. **Frontend Behavior Test**: With empty response, frontend calls `checkJiraStatus()` which returns `configured: true` → shows "No projects available" (demonstrates dead-end UX)

**Expected Counterexamples**:
- `getJiraCredentials()` returns `null` for both "not configured" and "decrypt failure"
- API response is identical `[]` for both cases
- Frontend has no way to show different messages for the two cases

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  response := GET /api/projects (with decrypt-failing credentials)
  ASSERT response.jiraStatus = "CREDENTIALS_INVALID"
  ASSERT response.projects = []
  ASSERT frontend.showsCredentialError(response) = true
  ASSERT frontend.showsIntegrationsLink(response) = true
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT getProjects_original(input).projects = getProjects_fixed(input).projects
  ASSERT frontend_behavior_original(input) = frontend_behavior_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for valid credentials and not-configured scenarios, then write tests capturing that behavior.

**Test Cases**:
1. **Valid Credentials Preservation**: With valid credentials and Jira returning projects, verify response contains projects and `jiraStatus = "OK"`
2. **Not Configured Preservation**: With no `jira` row in DB, verify response contains empty projects and `jiraStatus = "NOT_CONFIGURED"` — frontend still redirects admin to Integrations
3. **Empty Jira Preservation**: With valid credentials but Jira API returning empty list, verify response contains empty projects and `jiraStatus = "OK"` — frontend shows "No projects available" + RETRY
4. **Auth Preservation**: Unauthenticated request still returns 401

### Unit Tests — `JiraCredentialStateTest.kt`

**File**: `shared/src/jvmTest/kotlin/com/assistant/jira/JiraCredentialStateTest.kt`

8 test cases covering `getCredentialState()`:
- `NOT_CONFIGURED`: no jira config row exists
- `CREDENTIALS_INVALID`: blank apiKey, no colon separator, blank email part, blank token part, decrypt failure with wrong encryption key
- `OK`: valid credentials, apiToken containing colons

### Integration Tests — `ProjectsResponseTest.kt`

**File**: `server/core/src/jvmTest/kotlin/com/assistant/server/routes/ProjectsResponseTest.kt`

6 test cases verifying `ProjectsResponse` wrapper:
- `jiraStatus = "NOT_CONFIGURED"` when no config exists
- `jiraStatus = "CREDENTIALS_INVALID"` when decrypt fails or apiKey is blank
- `jiraStatus = "OK"` when credentials are valid
- Serialization round-trip preserving all fields
