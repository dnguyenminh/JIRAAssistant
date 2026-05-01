# User Management Audit Fix — Bugfix Design

## Overview

The User Management page (`/#user_management`) has 6 interconnected defects that prevent the user directory from displaying correctly and render the audit log non-functional. The bugs span field name mismatches between backend DTOs and frontend models, a missing GET endpoint for audit retrieval, volatile in-memory audit storage, frontend-only audit entries that are never persisted, model mismatches between frontend/backend `AuditLogEntry`, and no audit fetch on page load. The fix strategy is to align data models, add the missing API endpoint, replace in-memory storage with file-based persistence, and wire the frontend to fetch audit data from the backend.

## Glossary

- **Bug_Condition (C)**: Any interaction with the User Management page that involves (a) deserializing user data from the backend, or (b) viewing/creating audit log entries — these paths are broken due to field mismatches and missing endpoints
- **Property (P)**: Users display correctly with all fields populated; audit entries are persisted server-side, retrievable via API, and displayed in the Neural Console
- **Preservation**: Role change API, permission toggle API, access control (403 for non-admins), RBACEngineImpl audit appending, loading/error states, and permission panel behavior must remain unchanged
- **UserDto**: Backend DTO in `UserRoutes.kt` with fields `id`, `name`, `email`, `role`, `avatarUrl`, `customPermissions`
- **UserInfo**: Frontend model in `UserModels.kt` with fields `userId`, `displayName`, `email`, `role`, `permissions`
- **AuditLogEntry (backend)**: Shared model in `RBACModels.kt` with fields `actorId`, `targetUserId`, `action`, `oldValue`, `newValue`, `tag`
- **AuditLogEntry (frontend)**: Frontend model in `UserModels.kt` with fields `actor` (`@SerialName("actorId")`), `target` (`@SerialName("targetUserId")`), `action`, `oldValue`, `newValue`, `tag`
- **InMemoryAuditLogStore**: Current volatile implementation of `AuditLogStore` that loses data on JVM restart

## Bug Details

### Bug Condition

The bugs manifest across two domains: (1) user data deserialization fails silently due to field name mismatches between `UserDto` and `UserInfo`, and (2) the audit log system is non-functional because there is no GET endpoint, no persistent storage, no model alignment, and no frontend fetch logic.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type UserManagementInteraction
  OUTPUT: boolean

  // Domain 1: User data deserialization
  IF input.type == "DESERIALIZE_USER_LIST" THEN
    RETURN backendFieldNames(UserDto) != frontendFieldNames(UserInfo)
           // UserDto.id vs UserInfo.userId, UserDto.name vs UserInfo.displayName,
           // UserDto.customPermissions vs UserInfo.permissions
  END IF

  // Domain 2: Audit log retrieval
  IF input.type == "FETCH_AUDIT_LOG" THEN
    RETURN NOT endpointExists("GET /api/users/audit-log")
  END IF

  // Domain 3: Audit log persistence
  IF input.type == "SERVER_RESTART" THEN
    RETURN auditStore IS InMemoryAuditLogStore
  END IF

  // Domain 4: Audit entry creation
  IF input.type == "FRONTEND_AUDIT_ENTRY" THEN
    RETURN entry.persistedToBackend == false
  END IF

  // Domain 5: Audit model deserialization
  IF input.type == "DESERIALIZE_AUDIT_ENTRY" THEN
    RETURN backendFieldNames(AuditLogEntry_backend) != frontendFieldNames(AuditLogEntry_frontend)
           // actorId vs actor, targetUserId vs target, tag has no frontend equivalent
  END IF

  // Domain 6: Page load audit fetch
  IF input.type == "PAGE_LOAD" THEN
    RETURN NOT auditFetchCalledOnLoad()
  END IF

  RETURN false
END FUNCTION
```

### Examples

- **Defect 1 — Field mismatch**: Backend returns `{"id":"u1","name":"Alice","customPermissions":["ANALYZE_AI"]}`, frontend deserializes to `UserInfo(userId="", displayName="", permissions=[])` — all fields empty because JSON keys don't match
- **Defect 2 — No GET endpoint**: Frontend attempts `GET /api/users/audit-log` → 404 Not Found because `UserRoutes.kt` only defines `GET /api/users`, `PUT .../role`, `PUT .../permissions`
- **Defect 3 — Volatile storage**: Admin changes user role → `RBACEngineImpl` appends audit entry to `InMemoryAuditLogStore` → server restarts → all audit entries lost
- **Defect 4 — Local-only entries**: `UserAuditLog.addEntry("ROLE_CHANGE", "...")` creates a client-side entry with `ApiClient.getUserEmail()` as actor → entry exists only in browser memory, disappears on page navigation
- **Defect 5 — Model mismatch**: Backend `AuditLogEntry(actorId="admin1", targetUserId="u2", tag="IAM_SYNC")` serialized to JSON → frontend `AuditLogEntry(actor="", target="")` because field names differ
- **Defect 6 — No fetch on load**: `UserManagementPage.render()` calls `loadUsers()` but never calls any audit log fetch → Neural Console permanently shows "Awaiting audit events..."

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- PUT `/api/users/{userId}/role` must continue to change roles via `RBACEngineImpl` and return success/failure responses
- PUT `/api/users/{userId}/permissions` must continue to toggle permissions via `RBACEngineImpl` with IAM SYNC sidebar animation
- Non-Administrator users must continue to see the Access Denied (403) view
- `RBACEngineImpl.changeRole()` and `togglePermission()` must continue to append audit entries to `AuditLogStore` with all 7 fields (timestamp, actorId, targetUserId, action, oldValue, newValue, tag)
- Page loading state ("Loading users...") and error state with retry button must continue to work
- Permission Toggles panel must continue to show 4 toggles reflecting current permissions when a user is selected

**Scope:**
All inputs that do NOT involve user data deserialization field mapping or audit log retrieval/persistence should be completely unaffected by this fix. This includes:
- Role change and permission toggle API request/response handling (only the audit refresh after success is new)
- Access control checks (permission-based routing)
- UI rendering of user rows, role dropdowns, permission toggle switches
- Sidebar status animations during IAM SYNC operations

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Field Name Mismatch (UserDto ↔ UserInfo)**: `UserRoutes.kt` defines `UserDto(id, name, customPermissions)` but `UserModels.kt` defines `UserInfo(userId, displayName, permissions)`. Since `kotlinx.serialization` uses `ignoreUnknownKeys = true`, mismatched fields silently default to empty strings/lists instead of throwing errors. The `User.toDto()` extension maps `User.id → UserDto.id` and `User.name → UserDto.name`, but the frontend expects `userId` and `displayName`.

2. **Missing GET Endpoint**: `UserRoutes.kt` injects `auditLogStore` via Koin but never defines a `get("/audit-log")` route. The `AuditLogStore` interface already has `getRecent(limit)` — the endpoint simply was never wired up.

3. **Volatile In-Memory Storage**: `ServerModule.kt` registers `single<AuditLogStore> { InMemoryAuditLogStore() }` which stores entries in a `mutableListOf<AuditLogEntry>()` on JVM heap. No file or database persistence exists.

4. **Frontend-Only Audit Entries**: `UserAuditLog.addEntry()` creates entries locally using `ApiClient.getUserEmail()` as actor and client-side timestamp. These are never sent to the backend and are lost on page navigation because `auditLog` is a `mutableListOf` in the `UserAuditLog` object.

5. **AuditLogEntry Model Mismatch**: Backend model (in `RBACModels.kt`) uses `actorId`/`targetUserId`/`tag`, while frontend model (in `UserModels.kt`) uses `actor`/`target` and has no `tag` field. Even if a GET endpoint existed, deserialization would produce empty values.

6. **No Audit Fetch on Page Load**: `UserManagementPage.render()` calls `loadUsers()` but has no corresponding `loadAuditLog()` call. `UserAuditLog` has no method to fetch from backend.

## Correctness Properties

Property 1: Bug Condition — User Data Field Mapping

_For any_ valid `User` object in the backend store, when serialized as `UserDto` via the GET `/api/users` endpoint and deserialized as `UserInfo` on the frontend, the fixed system SHALL produce a `UserInfo` where `userId == User.id`, `displayName == User.name`, `email == User.email`, `role == User.role.name`, and `permissions == User.customPermissions.map { it.name }`.

**Validates: Requirements 2.1**

Property 2: Bug Condition — Audit Log Retrieval

_For any_ set of audit entries appended to the `AuditLogStore` by `RBACEngineImpl`, the fixed system SHALL return those entries via GET `/api/users/audit-log` with correct field mapping, ordered by timestamp descending, limited to the requested count.

**Validates: Requirements 2.2, 2.3**

Property 3: Bug Condition — Audit Log Persistence

_For any_ set of audit entries appended to the `AuditLogStore`, the fixed system SHALL retain those entries across JVM restarts by using a file-based persistence mechanism, such that a new `AuditLogStore` instance reading the same backing file returns the same entries.

**Validates: Requirements 2.4**

Property 4: Bug Condition — Audit Model Consistency

_For any_ backend `AuditLogEntry` (with fields `actorId`, `targetUserId`, `tag`, `action`, `oldValue`, `newValue`, `timestamp`), when serialized to JSON and deserialized by the frontend, the fixed system SHALL correctly map all fields so that the Neural Console displays accurate actor, action, and detail information.

**Validates: Requirements 2.5, 2.6**

Property 5: Preservation — Role Change and Permission Toggle APIs

_For any_ valid role change or permission toggle request, the fixed system SHALL produce the same API response and state mutation as the original system, preserving `RBACEngineImpl` behavior, audit entry appending, and HTTP status codes.

**Validates: Requirements 3.1, 3.2, 3.4**

Property 6: Preservation — Access Control and UI State

_For any_ non-Administrator user accessing the User Management page, the fixed system SHALL continue to show the Access Denied view. For any Administrator, the loading state, error handling with retry, and permission panel with 4 toggles SHALL remain unchanged.

**Validates: Requirements 3.3, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/models/UserModels.kt`

**Change 1 — Align UserInfo field names with UserDto** (implemented via `@SerialName` annotations):
- Add `@SerialName("id")` to `userId` field to match `UserDto.id`
- Add `@SerialName("name")` to `displayName` field to match `UserDto.name`
- Add `@SerialName("customPermissions")` to `permissions` field to match `UserDto.customPermissions`
- Kotlin field names (`userId`, `displayName`, `permissions`) preserved — no changes needed in `UserManagementPage.kt`, `UserPermissionPanel.kt`, `UserRoleChanger.kt`

**Change 2 — Align frontend AuditLogEntry with backend model**:
- Add `@SerialName("actorId")` to `actor` field, or rename to `actorId`
- Add `@SerialName("targetUserId")` to `target` field, or rename to `targetUserId`
- Add `tag` field (String) to match backend model
- Ensure `timestamp`, `action`, `oldValue`, `newValue` remain aligned (these already match)

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/routes/UserRoutes.kt`

**Change 3 — Add GET audit-log endpoint**:
- Add `get("/audit-log")` route inside the `/api/users` route block
- Call `auditLogStore.getRecent(limit)` with a query parameter for limit (default 50)
- Respond with `HttpStatusCode.OK` and the list of `AuditLogEntry`
- Protect with `withPermission(Permission.MANAGE_USERS)` (same as other user routes)

---

**New file**: `shared/src/jvmMain/kotlin/com/assistant/rbac/FileBasedAuditLogStore.kt`

**Change 4 — Create FileBasedAuditLogStore** (JVM-only due to `java.io.File` usage):
- Create `FileBasedAuditLogStore` implementing `AuditLogStore`
- Use a JSON file (e.g., `data/audit-log.json`) for persistence
- On `append()`: add entry to in-memory list + write to file
- On `getRecent()`/`getAll()`: read from in-memory list (loaded from file on init)
- Use `Mutex` for thread safety (same pattern as `InMemoryAuditLogStore`)
- Keep `InMemoryAuditLogStore` for test usage

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/di/ServerModule.kt`

**Change 5 — Wire FileBasedAuditLogStore in DI**:
- Change `single<AuditLogStore> { InMemoryAuditLogStore() }` to `single<AuditLogStore> { FileBasedAuditLogStore(dataDir) }`

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/usermgmt/UserAuditLog.kt`

**Change 6 — Add backend audit fetch capability**:
- Add `suspend fun loadFromBackend()` method that calls `GET /api/users/audit-log`
- Deserialize response as `List<AuditLogEntry>` (using aligned model with `@SerialName` annotations)
- Keep existing `addEntry()` for optimistic local display; `loadFromBackend()` becomes the source of truth called after mutations
- Handle errors gracefully: log to console, show "Failed to load audit log" in console element via `showFetchError()`
- Render fetched entries in the Neural Console

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/UserManagementPage.kt`

**Change 7 — Fetch audit log on page load**:
- After `loadUsers()` completes successfully, call `UserAuditLog.loadFromBackend()` inside the same coroutine scope
- No field reference updates needed since `@SerialName` annotations preserved Kotlin field names

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/usermgmt/UserRoleChanger.kt` and `UserPermissionPanel.kt`

**Change 8 — Refresh audit from backend after mutations**:
- After successful role change or permission toggle, call `UserAuditLog.loadFromBackend()` after the optimistic `UserAuditLog.addEntry()` call (both kept — optimistic for immediate UI feedback, then backend refresh for authoritative data)
- No field reference updates needed since `@SerialName` annotations preserved Kotlin field names

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that serialize `UserDto` and attempt deserialization as `UserInfo`, call the missing GET endpoint, and verify audit model round-trips. Run these tests on the UNFIXED code to observe failures.

**Test Cases**:
1. **UserDto→UserInfo Deserialization Test**: Serialize a `UserDto(id="u1", name="Alice", customPermissions=["ANALYZE_AI"])` to JSON, deserialize as `UserInfo` → expect `userId==""`, `displayName==""`, `permissions==[]` (will fail on unfixed code, confirming field mismatch)
2. **GET Audit Endpoint Test**: Call `GET /api/users/audit-log` → expect 404 (will fail on unfixed code, confirming missing endpoint)
3. **AuditLogEntry Model Round-Trip Test**: Serialize backend `AuditLogEntry(actorId="admin", targetUserId="u2", tag="IAM_SYNC")` to JSON, deserialize as frontend `AuditLogEntry` → expect `actor==""`, `target==""` (will fail on unfixed code, confirming model mismatch)
4. **Persistence Test**: Append entries to `InMemoryAuditLogStore`, create new instance, call `getAll()` → expect empty list (will fail on unfixed code, confirming volatile storage)

**Expected Counterexamples**:
- `UserInfo` fields are empty strings after deserialization from `UserDto` JSON
- GET `/api/users/audit-log` returns 404
- Frontend `AuditLogEntry.actor` is empty after deserializing backend JSON with `actorId` field
- New `InMemoryAuditLogStore` instance returns 0 entries

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  IF input.type == "DESERIALIZE_USER_LIST" THEN
    result := deserialize(serialize(UserDto_fixed), UserInfo_fixed)
    ASSERT result.userId == original.id
    ASSERT result.displayName == original.name
    ASSERT result.permissions == original.customPermissions
  END IF

  IF input.type == "FETCH_AUDIT_LOG" THEN
    result := GET /api/users/audit-log
    ASSERT result.status == 200
    ASSERT result.body.size <= limit
    ASSERT result.body IS sorted BY timestamp DESC
  END IF

  IF input.type == "SERVER_RESTART" THEN
    store1.append(entries)
    store2 := new FileBasedAuditLogStore(sameFile)
    ASSERT store2.getAll() == entries
  END IF

  IF input.type == "DESERIALIZE_AUDIT_ENTRY" THEN
    result := deserialize(serialize(backendEntry), frontendModel)
    ASSERT result.actor == backendEntry.actorId  // or mapped correctly
    ASSERT result.target == backendEntry.targetUserId
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many random role change and permission toggle requests to verify API behavior is unchanged
- It catches edge cases in RBAC logic that manual unit tests might miss
- It provides strong guarantees that non-audit, non-deserialization behavior is preserved

**Test Plan**: Observe behavior on UNFIXED code first for role changes, permission toggles, and access control, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Role Change API Preservation**: For any valid (adminId, targetUserId, newRole) triple, verify PUT `/api/users/{userId}/role` returns the same response and mutates state identically before and after fix
2. **Permission Toggle API Preservation**: For any valid (userId, permission, enabled) triple, verify PUT `/api/users/{userId}/permissions` returns the same response before and after fix
3. **Access Control Preservation**: For any non-admin JWT, verify all user management endpoints return 403
4. **RBACEngineImpl Audit Appending Preservation**: For any successful role change or permission toggle, verify an `AuditLogEntry` is appended to the store with all 7 fields populated

### Unit Tests

- Test `UserInfo` deserialization from `UserDto`-shaped JSON with aligned field names
- Test `AuditLogEntry` deserialization from backend-shaped JSON with aligned field names
- Test `FileBasedAuditLogStore.append()` writes to file and `getRecent()` reads back correctly
- Test `FileBasedAuditLogStore` survives simulated restart (new instance, same file)
- Test GET `/api/users/audit-log` returns entries from store with correct limit and ordering
- Test GET `/api/users/audit-log` requires `MANAGE_USERS` permission

### Property-Based Tests

- Generate random `User` objects → serialize as `UserDto` → deserialize as `UserInfo` → verify all fields match (Property 1)
- Generate random sequences of audit entries → append to `FileBasedAuditLogStore` → verify `getRecent(n)` returns correct subset ordered by timestamp descending (Property 2, 3)
- Generate random backend `AuditLogEntry` objects → JSON round-trip through frontend model → verify field preservation (Property 4)
- Generate random role change / permission toggle requests → verify API responses and state mutations are identical to original behavior (Property 5)

### Integration Tests

- Full flow: load User Management page → verify user list displays with correct names/emails/roles → verify audit log fetches and displays entries
- Full flow: change a user's role → verify audit log refreshes from backend showing the role change entry
- Full flow: toggle a permission → verify audit log refreshes from backend showing the permission change entry
- Restart simulation: append audit entries → recreate `FileBasedAuditLogStore` → verify entries persist
- Access control: login as non-admin → navigate to User Management → verify 403 Access Denied view
