# Implementation Plan: User CRUD & Profile

## Overview

This plan implements full user lifecycle management (create, view detail, edit, disable/enable, delete) across the Kotlin Multiplatform stack. Tasks are ordered to build incrementally: shared models first, then backend persistence, then route handlers, then frontend components, with property tests and unit tests woven in close to each implementation step.

All code uses Kotlin. Backend uses Ktor + kotest. Frontend uses KotlinJS + HTML templates + DOM APIs. Property-based tests use kotest-property.

## Tasks

- [x] 1. Extend shared module models and interfaces
  - [x] 1.1 Create `UserStatus` enum and extend `User` model
    - Create `shared/src/commonMain/kotlin/com/assistant/rbac/UserStatus.kt` with `@Serializable enum class UserStatus { ACTIVE, DISABLED, PENDING }`
    - Add `status: UserStatus = UserStatus.ACTIVE` and `createdAt: String = ""` fields to `User` data class in `RBACModels.kt`
    - Default values ensure backward compatibility with existing serialized data
    - _Requirements: 6.1, 6.2, 6.4, 6.5_

  - [x] 1.2 Extend `UserStore` interface with new methods
    - Add `updateUser(userId: String, name: String, email: String): Boolean` to `UserStore`
    - Add `deleteUser(userId: String): Boolean` to `UserStore`
    - Add `updateStatus(userId: String, status: UserStatus): Boolean` to `UserStore`
    - Add `findByEmail(email: String): User?` to `UserStore`
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 1.3 Implement new methods in `InMemoryUserStore`
    - Implement `updateUser` — find user by ID, update name and email, return true/false
    - Implement `deleteUser` — remove user by ID, return true/false
    - Implement `updateStatus` — find user by ID, update status field, return true/false
    - Implement `findByEmail` — iterate users, return first matching email or null
    - Add email uniqueness check to `addUser` — throw `IllegalArgumentException` if email already exists
    - All operations use existing `mutex.withLock` pattern for thread safety
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 1.4 Write property tests for User serialization round-trip
    - **Property 3: User model serialization round-trip**
    - Generate random `User` instances with all combinations of status, role, permissions, createdAt
    - Verify `Json.encodeToString` then `Json.decodeFromString` produces equivalent object
    - **Validates: Requirements 6.6**

  - [x] 1.5 Write property tests for UserStore operations
    - **Property 7: UserStore operations succeed for existing users**
    - Generate random users, add to store, verify `updateUser`/`deleteUser`/`updateStatus` return true
    - Verify same operations with non-existent IDs return false
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6**

  - [x] 1.6 Write property test for email uniqueness enforcement
    - **Property 4: Email uniqueness enforcement**
    - Generate two users with same email, verify `addUser` rejects the second
    - Generate user update that changes email to existing email, verify rejection
    - **Validates: Requirements 1.9, 3.8, 7.7**

  - [x] 1.7 Write property tests for status change persistence
    - **Property 9: Status change persistence**
    - Generate users with ACTIVE status, call `updateStatus(DISABLED)`, verify stored status is DISABLED
    - Verify reverse: DISABLED → ACTIVE
    - Verify round-trip: ACTIVE → DISABLED → ACTIVE restores original
    - **Validates: Requirements 4.3, 4.6**

  - [x] 1.8 Write property test for delete removes user permanently
    - **Property 10: Delete removes user permanently**
    - Generate users, add to store, delete, verify `findById` returns null and `getAll` excludes them
    - **Validates: Requirements 5.4**

- [x] 2. Checkpoint — Shared module complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Implement backend request DTOs and validation
  - [x] 3.1 Create request DTOs and validation service
    - Create `CreateUserRequest`, `UpdateUserRequest`, `UpdateStatusRequest` data classes in `server/user-mgmt` routes package (or a dedicated DTOs file if needed to stay under 200 lines)
    - Extend existing `UserDto` with `status: String = "ACTIVE"` and `createdAt: String = ""` fields
    - Update `User.toDto()` extension to include status and createdAt
    - Create validation functions: `isValidName(name: String): Boolean`, `isValidEmail(email: String): Boolean`
    - Name validation: reject empty and whitespace-only strings
    - Email validation: regex-based standard email format check
    - _Requirements: 1.2, 1.3, 3.2, 3.3, 8.9_

  - [x] 3.2 Write property tests for name and email validation
    - **Property 1: Name validation rejects empty and whitespace-only strings**
    - Generate whitespace-only strings → verify rejection; generate strings with non-whitespace → verify acceptance
    - **Validates: Requirements 1.2, 3.2**
    - **Property 2: Email validation accepts valid emails and rejects invalid ones**
    - Generate valid email patterns → verify acceptance; generate random strings → verify rejection
    - **Validates: Requirements 1.3, 3.3**

  - [x] 3.3 Write property test for UserDto completeness
    - **Property 5: UserDto contains all required fields**
    - Generate random `User` objects, convert to `UserDto`, verify id, name, email, role, status, createdAt are all non-null and present in serialized JSON
    - **Validates: Requirements 2.3, 6.3**

- [x] 4. Implement backend CRUD route handlers
  - [x] 4.1 Implement POST `/api/users` (create user) handler
    - Add `post` route inside existing `withPermission(Permission.MANAGE_USERS)` block in `UserRoutes.kt`
    - Receive and validate `CreateUserRequest` (name, email, role)
    - Generate UUID for new user, set status ACTIVE, set createdAt to current ISO 8601 timestamp
    - Call `userStore.addUser()`, handle duplicate email (catch exception → 409)
    - Append `AuditLogEntry` with action `USER_CREATED`
    - Return 201 + `UserDto`
    - Extract handler to separate function (≤20 lines) — if `UserRoutes.kt` exceeds 200 lines, create `UserCrudHandlers.kt`
    - _Requirements: 1.5, 1.6, 1.8, 1.9, 8.1_

  - [x] 4.2 Implement GET `/api/users/{id}` (get user detail) handler
    - Add `get("/{userId}")` route
    - Look up user by ID from `userStore.findById()`
    - Return 404 if not found, 200 + `UserDto` if found
    - _Requirements: 2.2, 8.2, 8.8_

  - [x] 4.3 Implement PUT `/api/users/{id}` (update user) handler
    - Add `put("/{userId}")` route
    - Receive and validate `UpdateUserRequest` (name, email)
    - Check email uniqueness (exclude current user) via `userStore.findByEmail()`
    - Call `userStore.updateUser()`, return 404 if user not found
    - Append `AuditLogEntry` with action `USER_UPDATED`, old and new values
    - Return 200 + updated `UserDto`
    - _Requirements: 3.4, 3.5, 3.7, 3.8, 8.3, 8.8_

  - [x] 4.4 Implement PUT `/api/users/{id}/status` (update status) handler
    - Add `put("/{userId}/status")` route
    - Receive and validate `UpdateStatusRequest` (status must be ACTIVE or DISABLED)
    - Call `userStore.updateStatus()`, return 404 if user not found
    - Append `AuditLogEntry` with action `USER_DISABLED` or `USER_ENABLED` based on new status
    - Return 200 + updated `UserDto`
    - _Requirements: 4.2, 4.3, 4.5, 4.6, 4.7, 4.8, 8.4_

  - [x] 4.5 Implement DELETE `/api/users/{id}` (delete user) handler
    - Add `delete("/{userId}")` route
    - Extract actor ID from JWT principal, compare with target userId — return 403 if self-deletion
    - Fetch user before deletion (for audit log old values)
    - Call `userStore.deleteUser()`, return 404 if user not found
    - Append `AuditLogEntry` with action `USER_DELETED`
    - Return 204 No Content
    - _Requirements: 5.3, 5.4, 5.6, 5.7, 8.5, 8.8_

  - [x] 4.6 Write property test for audit logging completeness
    - **Property 6: CRUD audit logging completeness**
    - For each CRUD operation (create, update, disable, enable, delete), verify an `AuditLogEntry` is appended with correct actor ID, target user ID, action tag, and old/new values
    - **Validates: Requirements 1.8, 3.7, 4.5, 4.8, 5.6**

  - [x] 4.7 Write property test for creation defaults
    - **Property 8: User creation sets ACTIVE status and createdAt**
    - Generate valid `CreateUserRequest` instances, process through create handler logic, verify persisted user has status ACTIVE and non-empty ISO 8601 createdAt
    - **Validates: Requirements 1.6, 6.1**

  - [x] 4.8 Write unit tests for CRUD route error cases
    - Test 404 for non-existent user on GET, PUT, DELETE
    - Test 409 for duplicate email on POST and PUT
    - Test 403 for self-deletion attempt
    - Test 400 for invalid request bodies (missing name, invalid email, invalid role, invalid status)
    - **Property 13: Non-existent user returns 404**
    - **Property 14: Invalid request body returns 400**
    - **Validates: Requirements 8.6, 8.7, 8.8, 8.9**

- [x] 5. Checkpoint — Backend CRUD complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Extend frontend models and HTML templates
  - [x] 6.1 Extend frontend `UserInfo` model and add request DTOs
    - Add `status: String = "ACTIVE"` and `createdAt: String = ""` to `UserInfo` in `models/UserModels.kt`
    - Add `CreateUserRequest(name, email, role)` data class
    - Add `UpdateUserRequest(name, email)` data class
    - Add `UpdateStatusRequest(status)` data class
    - _Requirements: 6.1, 6.2, 6.4, 6.5_

  - [x] 6.2 Extend `user-management.html` template with new UI sections
    - Add "Add User" button in the User Directory header
    - Add `<template id="tmpl-create-form">` for user creation form (name, email, role dropdown, submit/cancel buttons)
    - Add `<template id="tmpl-user-detail">` for detail panel (avatar, name, email, role, status badge, createdAt, edit/disable/delete action buttons)
    - Add `<template id="tmpl-confirm-dialog">` for confirmation dialog (message, name input for delete confirmation, confirm/cancel buttons)
    - Add `<template id="tmpl-user-row">` for user row with status indicator (refactor existing `createUserRow` to use template clone pattern)
    - All templates use CSS classes, no inline styles for layout
    - _Requirements: 1.1, 2.1, 2.3, 2.7, 5.1, 5.2_

  - [x] 6.3 Add CSS styles for new components
    - Add styles for create form (`.um-create-form`, `.um-form-field`, `.um-form-actions`)
    - Add styles for detail panel (`.um-detail-panel`, `.um-detail-header`, `.um-detail-info`, `.um-detail-actions`)
    - Add styles for status badges (`.um-status-badge`, `.um-status-active`, `.um-status-disabled`, `.um-status-pending`)
    - Add styles for confirmation dialog (`.um-confirm-dialog`, `.um-confirm-overlay`)
    - Add styles for disabled user row (`.um-user-row.disabled` — grayed-out appearance)
    - _Requirements: 2.7, 4.4, 4.7_

- [x] 7. Implement frontend create user flow
  - [x] 7.1 Create `UserCreateForm.kt` component
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/usermgmt/UserCreateForm.kt`
    - `show()` — clone create form template, append to DOM, bind events
    - `hide()` — remove form from DOM
    - Validate name (non-empty) and email (format) before submission
    - On submit: show BlockingOverlay("Creating user..."), POST `/api/users`, handle success (hide form, refresh list) and error (show error below form, retain values)
    - On cancel: hide form without API call
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 1.10, 1.11_

  - [x] 7.2 Wire "Add User" button in `UserManagementPage.kt`
    - Bind click handler on "Add User" button to call `UserCreateForm.show()`
    - Add `refreshUserList()` method that reloads users from API and re-renders the list
    - _Requirements: 1.1, 1.7_

- [x] 8. Implement frontend view detail and edit flows
  - [x] 8.1 Create `UserDetailPanel.kt` component
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/usermgmt/UserDetailPanel.kt`
    - `selectUser(userId)` — show BlockingOverlay, GET `/api/users/{id}`, render detail panel with user info
    - Display avatar (initials), name, email, role, permissions, status badge (color-coded), createdAt
    - Show loading skeleton while fetching, error + retry on failure
    - `enterEditMode()` — switch name and email to editable fields
    - `saveEdit()` — validate, show BlockingOverlay("Saving changes..."), PUT `/api/users/{id}`, handle success/error
    - `cancelEdit()` — revert to original values without API call
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 3.1, 3.2, 3.3, 3.4, 3.6, 3.9, 3.10, 3.11_

  - [x] 8.2 Wire detail panel selection in `UserManagementPage.kt`
    - Update user row click handler to call `UserDetailPanel.selectUser(userId)` instead of `UserPermissionPanel.selectUser(userId)`
    - Keep `UserPermissionPanel` accessible from within the detail panel
    - _Requirements: 2.1, 2.6_

- [x] 9. Implement frontend disable/enable and delete flows
  - [x] 9.1 Create `UserConfirmDialog.kt` component
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/usermgmt/UserConfirmDialog.kt`
    - `showDisableConfirm(userId, userName)` — clone confirm dialog template, show message "Are you sure you want to disable [username]?", confirm/cancel buttons
    - `showDeleteConfirm(userId, userName)` — clone confirm dialog template, show message "Are you sure you want to delete [username]? This action cannot be undone.", require typing user name to confirm
    - On confirm disable: show BlockingOverlay("Updating status..."), PUT `/api/users/{id}/status` with DISABLED
    - On confirm delete: show BlockingOverlay("Deleting user..."), DELETE `/api/users/{id}`
    - Handle success (refresh list, close dialog, close detail panel for delete) and error (show toast)
    - _Requirements: 4.1, 4.9, 4.10, 5.1, 5.2, 5.5, 5.8, 5.9, 5.10_

  - [x] 9.2 Implement enable action (no confirmation needed)
    - In `UserDetailPanel`, add "Enable" button for DISABLED users
    - On click: show BlockingOverlay("Updating status..."), PUT `/api/users/{id}/status` with ACTIVE
    - Update user row and detail panel on success
    - _Requirements: 4.6, 4.7, 4.8_

  - [x] 9.3 Update user row rendering with status indicators
    - Refactor `UserManagementPage.createUserRow()` to use `<template id="tmpl-user-row">` clone pattern
    - Add status badge to each user row
    - Apply `.disabled` CSS class to rows for DISABLED users (grayed-out)
    - _Requirements: 4.4, 4.7_

- [x] 10. Checkpoint — Frontend complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Integration wiring and final verification
  - [x] 11.1 Ensure backward compatibility for existing user list
    - Verify existing GET `/api/users` endpoint returns `status` and `createdAt` in each `UserDto`
    - Verify frontend `UserInfo` deserialization handles missing `status`/`createdAt` gracefully (defaults)
    - Verify existing role change and permission toggle flows still work with extended `User` model
    - _Requirements: 6.3, 6.4, 6.5_

  - [x] 11.2 Write integration tests for CRUD endpoints
    - Test full create → get → update → disable → enable → delete flow via Ktor test engine
    - Test authorization: requests without JWT → 401, requests without MANAGE_USERS → 403
    - Test all 5 endpoints return correct status codes and response bodies
    - **Property 12: Unauthorized access rejection**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**

- [x] 12. Final checkpoint — All tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each layer (shared → backend → frontend → integration)
- Property tests validate universal correctness properties from the design document (P1–P14)
- Unit tests validate specific examples, edge cases, and error conditions
- Frontend components follow the HTML template + DOM API pattern (no HTML in Kotlin code)
- All new Kotlin files must stay under 200 lines; functions under 20 lines
- BlockingOverlay is required for all async operations
- All mutations must log to audit log
