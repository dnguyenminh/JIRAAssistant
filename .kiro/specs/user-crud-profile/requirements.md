# Requirements Document

## Introduction

User CRUD & Profile Management (SCRUM-50) is the foundation story for the User Management Enhancement epic (SCRUM-49). This feature adds full lifecycle management for users: create, view detail, edit, disable/enable, and delete. Currently the User Management page only supports listing users, changing roles, and toggling 4 hardcoded permissions. Administrators cannot create new users, view detailed profiles, edit user info, soft-disable accounts, or permanently delete users.

The system is a Kotlin Multiplatform application with a Ktor backend, KotlinJS frontend, and a shared RBAC model. The existing `User` model needs extension with `status` (ACTIVE/DISABLED/PENDING) and `createdAt` fields. The `UserStore` interface needs new methods: `updateUser`, `deleteUser`, `updateStatus`. All operations require JWT authentication and `MANAGE_USERS` permission, and must be recorded in the audit log.

## Glossary

- **Admin_Panel**: The User Management page accessible only to users with the MANAGE_USERS permission
- **Administrator**: A user with the ADMINISTRATOR role who has full system access including user management
- **User_Directory**: The list of all users displayed on the Admin_Panel, showing avatar, name, email, role, and status
- **Detail_Panel**: A UI panel that slides in or expands to show comprehensive information about a selected user
- **User_Store**: The backend persistence interface (`UserStore`) responsible for storing and retrieving user data
- **RBAC_Engine**: The Role-Based Access Control engine that enforces permission checks on all operations
- **Audit_Log**: The persistent log that records all user management actions with actor, target, action, old/new values, and timestamp
- **User_Status**: An enum with values ACTIVE, DISABLED, and PENDING representing the lifecycle state of a user account
- **Blocking_Overlay**: A UI overlay that prevents duplicate interactions during async operations, showing a spinner and descriptive message
- **Confirmation_Dialog**: A modal dialog requiring explicit user confirmation before destructive actions
- **User_Model**: The shared Kotlin data class (`User`) representing a user entity across backend and frontend
- **User_DTO**: The serializable data transfer object (`UserDto`) used in API responses to represent user data

## Requirements

### Requirement 1: Create User

**User Story:** As an Administrator, I want to create a new user with name, email, role, and initial permissions, so that I can onboard new team members into the system.

#### Acceptance Criteria

1. WHEN the Administrator clicks the "Add User" button on the Admin_Panel, THE Admin_Panel SHALL display a creation form with fields for name, email, role selection, and initial status
2. THE Admin_Panel SHALL validate that the name field is non-empty before allowing form submission
3. THE Admin_Panel SHALL validate that the email field matches a standard email format before allowing form submission
4. THE Admin_Panel SHALL provide a role selection dropdown containing all available roles (ADMINISTRATOR, NEURAL_ARCHITECT, READER)
5. WHEN the Administrator submits a valid creation form, THE Admin_Panel SHALL send a POST request to `/api/users` with the user data
6. WHEN the backend receives a valid POST `/api/users` request, THE User_Store SHALL persist a new user with status ACTIVE and a server-generated createdAt timestamp
7. WHEN a user is successfully created, THE User_Directory SHALL display the new user immediately without requiring a page refresh
8. WHEN a user is successfully created, THE Audit_Log SHALL record the creation with the actor ID, new user ID, action "USER_CREATED", and the new user's role as the new value
9. IF the email address already exists in the User_Store, THEN THE backend SHALL return HTTP 409 Conflict with a descriptive error message
10. IF the creation request fails, THEN THE Admin_Panel SHALL display a specific error message describing the failure reason
11. WHILE the creation request is in progress, THE Admin_Panel SHALL display a Blocking_Overlay with the message "Creating user..." to prevent duplicate submissions

### Requirement 2: View User Detail

**User Story:** As an Administrator, I want to view detailed information about a user, so that I can review their profile, permissions, and account status.

#### Acceptance Criteria

1. WHEN the Administrator clicks on a user row in the User_Directory, THE Admin_Panel SHALL display the Detail_Panel for that user
2. WHEN the Detail_Panel is displayed, THE Admin_Panel SHALL send a GET request to `/api/users/{id}` to fetch the full user profile
3. THE Detail_Panel SHALL display the user's avatar (initials-based), name, email, role, status, and createdAt date. Custom permissions are accessible via the separate Permission_Panel displayed alongside the Detail_Panel.
4. WHILE the Detail_Panel data is loading, THE Detail_Panel SHALL display a loading skeleton placeholder
5. IF the GET request for user detail fails, THEN THE Detail_Panel SHALL display an error message with a retry option
6. WHEN the Administrator clicks on a different user row, THE Detail_Panel SHALL update to show the newly selected user's information
7. THE Detail_Panel SHALL visually distinguish between ACTIVE, DISABLED, and PENDING user statuses using color-coded badges

### Requirement 3: Edit User Info

**User Story:** As an Administrator, I want to edit a user's display name and email, so that I can keep user information accurate and up to date.

#### Acceptance Criteria

1. WHEN the Administrator clicks the "Edit" action in the Detail_Panel, THE Detail_Panel SHALL switch the name and email fields to editable mode
2. THE Detail_Panel SHALL validate that the edited name is non-empty before allowing save
3. THE Detail_Panel SHALL validate that the edited email matches a standard email format before allowing save
4. WHEN the Administrator saves valid edits, THE Admin_Panel SHALL send a PUT request to `/api/users/{id}` with the updated name and email
5. WHEN the backend receives a valid PUT `/api/users/{id}` request, THE User_Store SHALL update the user's name and email fields
6. WHEN the edit is successfully saved, THE User_Directory SHALL reflect the updated name and email immediately without requiring a page refresh
7. WHEN the edit is successfully saved, THE Audit_Log SHALL record the change with the actor ID, target user ID, action "USER_UPDATED", and the old and new values
8. IF the updated email already exists for a different user, THEN THE backend SHALL return HTTP 409 Conflict with a descriptive error message
9. WHEN the Administrator clicks "Cancel" during editing, THE Detail_Panel SHALL revert to the original values without sending any request
10. WHILE the save request is in progress, THE Admin_Panel SHALL display a Blocking_Overlay with the message "Saving changes..." to prevent duplicate submissions
11. IF the save request fails, THEN THE Detail_Panel SHALL display a specific error message and retain the edited values for retry

### Requirement 4: Disable and Enable User

**User Story:** As an Administrator, I want to disable a user account so that the user cannot log in while preserving their data, and re-enable the account when needed.

#### Acceptance Criteria

1. WHEN the Administrator clicks the "Disable" action for an ACTIVE user, THE Admin_Panel SHALL display a Confirmation_Dialog with the message "Are you sure you want to disable [username]?"
2. WHEN the Administrator confirms the disable action, THE Admin_Panel SHALL send a PUT request to `/api/users/{id}/status` with status DISABLED
3. WHEN the backend receives a valid disable request, THE User_Store SHALL update the user's status to DISABLED
4. WHEN a user is successfully disabled, THE User_Directory SHALL display the user with a visual indicator showing the disabled state (grayed-out row and "DISABLED" badge)
5. WHEN a user is successfully disabled, THE Audit_Log SHALL record the change with action "USER_DISABLED", old value "ACTIVE", and new value "DISABLED"
6. WHEN the Administrator clicks the "Enable" action for a DISABLED user, THE Admin_Panel SHALL send a PUT request to `/api/users/{id}/status` with status ACTIVE without requiring a confirmation dialog
7. WHEN a user is successfully re-enabled, THE User_Directory SHALL remove the disabled visual indicator and display the user as active
8. WHEN a user is successfully re-enabled, THE Audit_Log SHALL record the change with action "USER_ENABLED", old value "DISABLED", and new value "ACTIVE"
9. WHILE the status change request is in progress, THE Admin_Panel SHALL display a Blocking_Overlay with the message "Updating status..." to prevent duplicate submissions
10. IF the status change request fails, THEN THE Admin_Panel SHALL display a specific error message describing the failure reason
11. WHILE a user has status DISABLED, THE RBAC_Engine SHALL reject authentication attempts from that user with an appropriate error message

### Requirement 5: Delete User

**User Story:** As an Administrator, I want to permanently delete a user, so that I can remove accounts that are no longer needed.

#### Acceptance Criteria

1. WHEN the Administrator clicks the "Delete" action for a user, THE Admin_Panel SHALL display a Confirmation_Dialog with the message "Are you sure you want to delete [username]? This action cannot be undone."
2. THE Confirmation_Dialog SHALL require the Administrator to type the user's name to confirm deletion
3. WHEN the Administrator confirms the delete action, THE Admin_Panel SHALL send a DELETE request to `/api/users/{id}`
4. WHEN the backend receives a valid DELETE `/api/users/{id}` request, THE User_Store SHALL permanently remove the user from persistence
5. WHEN a user is successfully deleted, THE User_Directory SHALL remove the user row immediately without requiring a page refresh
6. WHEN a user is successfully deleted, THE Audit_Log SHALL record the deletion with action "USER_DELETED" and the deleted user's name and email as the old value
7. IF the Administrator attempts to delete their own account, THEN THE backend SHALL return HTTP 403 Forbidden with the message "Cannot delete your own account"
8. WHILE the delete request is in progress, THE Admin_Panel SHALL display a Blocking_Overlay with the message "Deleting user..." to prevent duplicate submissions
9. IF the delete request fails, THEN THE Admin_Panel SHALL display a specific error message describing the failure reason
10. WHEN the Detail_Panel is open for a deleted user, THE Detail_Panel SHALL close automatically after successful deletion

### Requirement 6: Extended User Model

**User Story:** As a developer, I want the User model to include status and createdAt fields, so that the system can track user lifecycle state and account creation time.

#### Acceptance Criteria

1. THE User_Model SHALL include a `status` field of type User_Status with a default value of ACTIVE
2. THE User_Model SHALL include a `createdAt` field of type String (ISO 8601 format) that is set at user creation time
3. THE User_DTO SHALL serialize the `status` and `createdAt` fields in all API responses that return user data
4. WHEN deserializing user data that lacks a `status` field, THE frontend SHALL default to ACTIVE for backward compatibility
5. WHEN deserializing user data that lacks a `createdAt` field, THE frontend SHALL display "N/A" for backward compatibility
6. FOR ALL valid User_Model instances, serializing then deserializing SHALL produce an equivalent User_Model object (round-trip property)

### Requirement 7: UserStore Interface Extension

**User Story:** As a developer, I want the UserStore interface to support update, delete, and status change operations, so that the backend can implement full user lifecycle management.

#### Acceptance Criteria

1. THE User_Store interface SHALL include an `updateUser(userId: String, name: String, email: String): Boolean` method that returns true on success
2. THE User_Store interface SHALL include a `deleteUser(userId: String): Boolean` method that returns true on success
3. THE User_Store interface SHALL include an `updateStatus(userId: String, status: UserStatus): Boolean` method that returns true on success
4. WHEN `updateUser` is called with a non-existent userId, THE User_Store SHALL return false
5. WHEN `deleteUser` is called with a non-existent userId, THE User_Store SHALL return false
6. WHEN `updateStatus` is called with a non-existent userId, THE User_Store SHALL return false
7. THE User_Store `addUser` method SHALL reject users with duplicate email addresses by throwing an appropriate exception

### Requirement 8: API Endpoints and Authorization

**User Story:** As a developer, I want all new CRUD endpoints to require JWT authentication and MANAGE_USERS permission, so that only authorized administrators can manage users.

#### Acceptance Criteria

1. THE backend SHALL expose a `POST /api/users` endpoint that creates a new user and returns the created User_DTO with HTTP 201
2. THE backend SHALL expose a `GET /api/users/{id}` endpoint that returns the full User_DTO for the specified user
3. THE backend SHALL expose a `PUT /api/users/{id}` endpoint that updates the user's name and email and returns the updated User_DTO
4. THE backend SHALL expose a `PUT /api/users/{id}/status` endpoint that updates the user's status and returns the updated User_DTO
5. THE backend SHALL expose a `DELETE /api/users/{id}` endpoint that permanently deletes the user and returns HTTP 204
6. WHEN any CRUD endpoint is called without a valid JWT token, THE backend SHALL return HTTP 401 Unauthorized
7. WHEN any CRUD endpoint is called by a user without MANAGE_USERS permission, THE backend SHALL return HTTP 403 Forbidden
8. IF a GET, PUT, or DELETE request references a non-existent user ID, THEN THE backend SHALL return HTTP 404 Not Found with a descriptive error message
9. THE backend SHALL validate all request bodies and return HTTP 400 Bad Request with specific validation error messages for invalid input
