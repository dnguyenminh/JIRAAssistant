# Tasks — Jira Credential Error Feedback Bugfix

## Task 1: Backend — Add credential state detection

- [x] 1.1 Add `JiraCredentialState` enum (`NOT_CONFIGURED`, `CREDENTIALS_INVALID`, `OK`) to `JiraCredentialsService.kt`
- [x] 1.2 Add `getCredentialState(): JiraCredentialState` method to `JiraCredentialsService` that checks if config exists and if decrypt succeeds, returning the appropriate enum value
- [x] 1.3 Add `ProjectsResponse` data class with `projects: List<JiraProject>` and `jiraStatus: String` fields (in a shared or server model file)
- [x] 1.4 Modify GET `/api/projects` in `ProjectRoutes.kt` to use `getCredentialState()` and return `ProjectsResponse` wrapper instead of raw `List<JiraProject>`

## Task 2: Frontend — Handle credential error state

- [x] 2.1 Add `<template id="tmpl-credential-error">` to `project-select.html` with error icon, message text, and "Go to Integrations" button
- [x] 2.2 Modify `loadProjects()` in `ProjectSelectPage.kt` to parse response as JSON object with `projects` and `jiraStatus` fields
- [x] 2.3 Add `showCredentialError()` method that clones the credential error template and displays it, with click handler navigating to `#integrations`
- [x] 2.4 Wire the `jiraStatus` check: if `"CREDENTIALS_INVALID"` → call `showCredentialError()`, if `"NOT_CONFIGURED"` → existing `handleJiraConfigResult(false)` flow, if `"OK"` with empty projects → existing `showEmptyWithRetry()` flow

## Task 3: Testing & Verification

- [x] 3.1 Write unit test for `JiraCredentialsService.getCredentialState()` covering all three states
- [x] 3.2 Write integration test for GET `/api/projects` verifying `ProjectsResponse` structure with `jiraStatus` field for each credential state
- [x] 3.3 Manual verification: verified on localhost:3000 — API returns ProjectsResponse wrapper with jiraStatus field, frontend parses correctly and renders project table for OK state. Credential error template exists with 🔐 icon, title, message, and Go to Integrations button. Unit + integration tests all PASS.
