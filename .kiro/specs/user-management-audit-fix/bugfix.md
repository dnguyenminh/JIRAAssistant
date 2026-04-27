# Bugfix Requirements Document

## Introduction

The User Management page (`/#user_management`) has multiple interconnected bugs that render it non-functional. The user directory may fail to display users due to a field name mismatch between backend `UserDto` (`id`, `name`) and frontend `UserInfo` (`userId`, `displayName`). The Audit Log — Neural Console permanently shows "Awaiting audit events..." because: (a) no GET endpoint exists to fetch audit history from the backend, (b) the `AuditLogStore` is `InMemoryAuditLogStore` which loses all data on server restart, (c) the frontend `UserAuditLog` only adds entries from local UI actions and never fetches persisted server-side audit history, and (d) the backend `RBACEngineImpl` does log role/permission changes to `AuditLogStore` but `UserRoutes.kt` never exposes a GET endpoint to retrieve them.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the frontend deserializes the GET `/api/users` response THEN the system maps backend `UserDto` fields (`id`, `name`) to frontend `UserInfo` fields (`userId`, `displayName`) incorrectly due to field name mismatch, resulting in empty strings for user ID and display name

1.2 WHEN the User Management page loads THEN the system shows "Awaiting audit events..." in the Neural Console because no API call is made to fetch existing audit log entries from the backend

1.3 WHEN a role change or permission toggle is performed via the backend `RBACEngineImpl` THEN the system writes the audit entry to `InMemoryAuditLogStore` but provides no GET endpoint in `UserRoutes.kt` to retrieve these entries, making server-side audit data inaccessible to the frontend

1.4 WHEN the server restarts THEN the system loses all audit log entries because `InMemoryAuditLogStore` stores data only in a mutable list in JVM heap memory with no persistence

1.5 WHEN the frontend `UserAuditLog.addEntry()` is called from `UserRoleChanger` or `UserPermissionPanel` THEN the system only creates a local-only audit entry with client-side timestamp and actor, which is not persisted to the backend and disappears on page navigation

1.6 WHEN the frontend `AuditLogEntry` model fields (`actor`, `target`) differ from the backend `AuditLogEntry` model fields (`actorId`, `targetUserId`, `tag`) THEN the system cannot correctly deserialize backend audit entries even if a GET endpoint existed

### Expected Behavior (Correct)

2.1 WHEN the frontend deserializes the GET `/api/users` response THEN the system SHALL correctly map all user fields (ID, display name, email, role, permissions) so that the User Directory displays each user's avatar, name, email, and role dropdown

2.2 WHEN the User Management page loads THEN the system SHALL fetch recent audit log entries from a backend GET `/api/users/audit-log` endpoint and display them in the Neural Console

2.3 WHEN a role change or permission toggle is performed via the backend THEN the system SHALL expose a GET endpoint in `UserRoutes.kt` (e.g., `GET /api/users/audit-log`) that returns audit entries from the `AuditLogStore`, making server-side audit data accessible to the frontend

2.4 WHEN the server restarts THEN the system SHALL retain audit log entries by using a persistent storage mechanism (file-based or database-backed `AuditLogStore` implementation) instead of in-memory-only storage

2.5 WHEN a role change or permission toggle succeeds on the backend THEN the system SHALL record the audit entry server-side (already done by `RBACEngineImpl`) AND the frontend SHALL refresh the audit log from the backend to show the latest entries, rather than creating local-only entries

2.6 WHEN the frontend fetches audit log entries from the backend THEN the system SHALL use a consistent data model between backend and frontend `AuditLogEntry` so that serialization/deserialization works correctly

### Unchanged Behavior (Regression Prevention)

3.1 WHEN an Administrator changes a user's role via the dropdown THEN the system SHALL CONTINUE TO call PUT `/api/users/{userId}/role` and apply the role change immediately via `RBACEngineImpl`

3.2 WHEN an Administrator toggles a permission THEN the system SHALL CONTINUE TO call PUT `/api/users/{userId}/permissions` and update the permission via `RBACEngineImpl` with IAM SYNC sidebar animation

3.3 WHEN a non-Administrator user accesses the User Management page THEN the system SHALL CONTINUE TO show the Access Denied (403) view and hide the main content

3.4 WHEN the `RBACEngineImpl.changeRole()` or `togglePermission()` is called THEN the system SHALL CONTINUE TO append audit entries to the `AuditLogStore` with timestamp, actorId, targetUserId, action, oldValue, newValue, and tag

3.5 WHEN the User Management page loads THEN the system SHALL CONTINUE TO show a loading state while fetching users and display an error with retry button if the API call fails

3.6 WHEN a user is selected from the User Directory THEN the system SHALL CONTINUE TO display the Permission Toggles panel with the 4 toggles (Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data) reflecting the user's current permissions
