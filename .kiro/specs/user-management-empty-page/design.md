# User Management Empty Page Bugfix Design

## Overview

The User Management page (`localhost:3000/#user_management`) previously displayed an empty User Directory because the `InMemoryUserStore` was never populated with users. The `AuthServiceImpl` maintained a hardcoded `defaultUsers` map for credential validation but never registered authenticated users into the `UserStore`. The fix seeds default users into the store at DI initialization time and ensures newly authenticated users are registered on login, so `GET /api/users` now returns a populated list. **Status: Fixed and verified.**

## Glossary

- **Bug_Condition (C)**: The condition where `InMemoryUserStore` contains zero users — triggered by server startup without seeding and by successful authentication without registration
- **Property (P)**: After server startup, `UserStore.getAll()` returns the default users; after successful login, the authenticated user exists in the store
- **Preservation**: Existing authentication failure handling, RBAC role/permission changes, audit logging, JWT validation, and access control must remain unchanged
- **InMemoryUserStore**: The `UserStore` implementation in `shared/.../rbac/InMemoryUserStore.kt` that holds users in a `MutableMap<String, User>` protected by a `Mutex`
- **AuthServiceImpl**: The `AuthService` implementation in `server/core/.../auth/AuthServiceImpl.kt` that validates credentials against a hardcoded `defaultUsers` map
- **CoreModule**: The Koin DI module in `server/core/.../di/CoreModule.kt` that wires all core services including `UserStore` and `AuthService`
- **defaultUsers**: The hardcoded map in `AuthServiceImpl` containing `"admin"` (ADMINISTRATOR) and `"user"` (READER) credentials

## Bug Details

### Bug Condition

The bug manifested whenever the server started and a user navigated to the User Management page. Previously, `InMemoryUserStore` was instantiated empty in `CoreModule.kt` via `single<UserStore> { InMemoryUserStore() }` and no code path called `addUser()`. When `AuthServiceImpl.authenticate()` succeeded, it created an `AuthenticatedUser` for JWT generation but did not register a `User` into the `UserStore`. Consequently, `GET /api/users` always returned `[]`.

**This bug condition is now resolved.** The store is seeded at startup and users are registered on authentication.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SystemState
  OUTPUT: boolean

  RETURN input.userStore.getAll().isEmpty()
         AND (input.event = SERVER_STARTUP OR input.event = SUCCESSFUL_AUTHENTICATION)
END FUNCTION
```

### Examples

- **Server startup**: Admin starts the server → `InMemoryUserStore` is created empty → `GET /api/users` returns `[]` → User Directory shows no users
- **Admin login**: Admin logs in with `admin/admin123` → JWT is issued → `UserStore` still empty → navigating to User Management shows empty list
- **User login**: User logs in with `user/user123` → JWT is issued → `UserStore` still empty → User Management page (if accessible) shows empty list
- **Role change attempt**: Admin tries to change a user's role → `RBACEngineImpl.changeRole()` calls `userStore.findById()` → returns `null` → returns 404 "User not found"

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Invalid credential authentication (`wrong_user/wrong_pass`) must continue to return 401 failure without adding any user to the store
- Non-Administrator users accessing User Management must continue to see "Access Denied" (403)
- `RBACEngineImpl.changeRole()` and `togglePermission()` must continue to work correctly for users that exist in the store
- Audit log entries must continue to be recorded for role/permission changes
- JWT generation, validation, and session invalidation must remain unchanged
- `FileBasedAuditLogStore` must continue to persist and load audit entries across restarts

**Scope:**
All inputs that do NOT involve user store population should be completely unaffected by this fix. This includes:
- JWT token validation logic
- Permission matrix checks (`PermissionMatrix.check()`)
- Frontend rendering logic (HTML templates, CSS, DOM manipulation)
- API route definitions and middleware (`withPermission`)

## Root Cause (Confirmed & Fixed)

The root causes were identified and confirmed during implementation:

1. **No seeding at startup**: `CoreModule.kt` created `InMemoryUserStore()` with an empty constructor. **Fixed**: The Koin `single<UserStore>` block now uses `runBlocking` to seed `admin` (ADMINISTRATOR) and `user` (READER) during DI initialization.

2. **No registration on authentication**: `AuthServiceImpl.authenticate()` created an `AuthenticatedUser` (an auth-layer DTO for JWT claims) but never converted it to a `User` (the RBAC-layer model). **Fixed**: `AuthServiceImpl` now accepts `UserStore` as a constructor dependency and calls `userStore.addUser()` after successful authentication.

3. **Disconnected user models**: `AuthenticatedUser` (auth layer) and `User` (RBAC layer) are separate data classes with no bridge between them. **Fixed**: The `authenticate()` method now explicitly converts to a `User` and registers it in the store.

4. **`addUser` is suspend**: `InMemoryUserStore.addUser()` is a `suspend` function, which prevented simple seeding in a Koin `single { }` block. **Fixed**: `runBlocking` is used in the module initialization to call the suspend function synchronously during startup.

## Correctness Properties

Property 1: Bug Condition - UserStore Populated After Startup

_For any_ server startup where the `InMemoryUserStore` is initialized, the store SHALL contain the default users (`admin` with ADMINISTRATOR role, `user` with READER role) so that `GET /api/users` returns a non-empty list with correct user data (id, name, email, role).

**Validates: Requirements 2.1, 2.3**

Property 2: Bug Condition - User Registered on Authentication

_For any_ successful authentication where the authenticated user does not yet exist in the `UserStore`, the system SHALL register the user into the store via `addUser()`, ensuring the user appears in subsequent `GET /api/users` responses.

**Validates: Requirements 2.2, 2.3**

Property 3: Preservation - Authentication Failure Unchanged

_For any_ authentication attempt with invalid credentials (wrong username or wrong password), the system SHALL produce the same 401 failure result as the original code, and SHALL NOT add any user to the `UserStore`.

**Validates: Requirements 3.1**

Property 4: Preservation - RBAC Operations Unchanged

_For any_ role change or permission toggle operation on a user that exists in the store, the system SHALL produce the same result as the original code, correctly updating the user and logging an audit entry.

**Validates: Requirements 3.3, 3.4**

## Fix Implementation (Completed)

### Changes Made

**File**: `shared/src/commonMain/kotlin/com/assistant/rbac/UserStore.kt`

- `addUser(user: User)` is declared in the `UserStore` interface, allowing `AuthServiceImpl` to depend on the interface rather than the concrete `InMemoryUserStore` class.

**File**: `shared/src/commonMain/kotlin/com/assistant/rbac/InMemoryUserStore.kt`

- `addUser` is implemented as `override suspend fun addUser(user: User)` using `mutex.withLock { users[user.id] = user }`.

**File**: `server/core/src/jvmMain/kotlin/com/assistant/server/di/CoreModule.kt`

- The `UserStore` singleton seeds default users at DI initialization using `runBlocking`:

```kotlin
single<UserStore> {
    InMemoryUserStore().also { store ->
        runBlocking {
            store.addUser(User(id = "admin", name = "admin", email = "admin@assistant.local", role = UserRole.ADMINISTRATOR))
            store.addUser(User(id = "user", name = "user", email = "user@assistant.local", role = UserRole.READER))
        }
    }
}
```

- The `AuthService` binding passes `UserStore` as the 4th dependency: `AuthServiceImpl(get(), get(), get(), get())`.
- `UserStore` is declared before `AuthService` in the module to satisfy the dependency order.

**File**: `server/core/src/jvmMain/kotlin/com/assistant/server/auth/AuthServiceImpl.kt`

- `UserStore` added as a constructor parameter: `class AuthServiceImpl(config, httpClient, jiraCredentialsService, userStore: UserStore)`.
- After creating `AuthenticatedUser` and before returning `AuthResult.Success`, the user is registered into the RBAC store:

```kotlin
userStore.addUser(User(id = username, name = username, email = creds.email, role = creds.role))
```

- This is idempotent — `users[user.id] = user` (map put) overwrites existing entries with the same data.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that create an `InMemoryUserStore` via the same Koin module wiring as production, then call `getAll()` to verify it is empty. Also test that `AuthServiceImpl.authenticate()` succeeds but does not populate the store.

**Test Cases**:
1. **Empty Store After Init**: Create `InMemoryUserStore()` as done in `CoreModule` → call `getAll()` → assert returns empty list (will pass on unfixed code, confirming bug)
2. **Auth Success Without Registration**: Call `authenticate("admin", "admin123")` → assert success → call `userStore.getAll()` → assert still empty (will pass on unfixed code, confirming bug)
3. **Role Change Fails on Empty Store**: Call `rbacEngine.changeRole("admin", "user", READER)` → assert returns 404 "User not found" (will pass on unfixed code, confirming bug)
4. **GET /api/users Returns Empty**: Start server, authenticate, call `GET /api/users` → assert returns `[]` (will pass on unfixed code, confirming bug)

**Expected Counterexamples**:
- `UserStore.getAll()` returns empty list after server startup
- `AuthServiceImpl.authenticate()` returns success but `UserStore` remains empty
- Possible causes: no seeding logic, no `UserStore` dependency in `AuthServiceImpl`

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := startServer'(input)
  ASSERT result.userStore.getAll().size >= 2
  ASSERT result.userStore.findById("admin") != null
  ASSERT result.userStore.findById("admin").role == ADMINISTRATOR
  ASSERT result.userStore.findById("user") != null
  ASSERT result.userStore.findById("user").role == READER
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT F(input) = F'(input)
  // Invalid credentials still return 401
  // JWT validation still works
  // Role changes on existing users still succeed
  // Permission toggles on existing users still succeed
  // Audit entries still recorded
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for authentication failures, JWT validation, and RBAC operations, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Auth Failure Preservation**: Verify that invalid credentials (`wrong/wrong`, `admin/wrong`, `nobody/admin123`) continue to return 401 after fix
2. **JWT Validation Preservation**: Verify that valid JWTs continue to decode correctly and expired/invalid JWTs continue to return null
3. **RBAC Role Change Preservation**: Verify that changing a role for an existing user updates the store and logs an audit entry
4. **RBAC Permission Toggle Preservation**: Verify that toggling a permission for an existing user updates the store and logs an audit entry
5. **Non-Admin Access Denial Preservation**: Verify that non-ADMINISTRATOR users still cannot access User Management endpoints

### Unit Tests

- Test `InMemoryUserStore` seeding: after DI initialization, `getAll()` returns 2 users with correct data
- Test `AuthServiceImpl.authenticate()` registers user into store on success
- Test `AuthServiceImpl.authenticate()` does NOT register user on failure
- Test `addUser` idempotency: calling `addUser` twice with same ID does not create duplicates
- Test `UserStore.findById()` returns seeded users correctly

### Property-Based Tests

- Generate random valid/invalid credential pairs and verify authentication results match expected behavior (success for known users, failure for unknown)
- Generate random user IDs and verify `findById` returns correct user for seeded IDs and null for unknown IDs
- Generate random role change sequences on seeded users and verify store state is consistent

### Integration Tests

- Test full flow: start server → login as admin → `GET /api/users` returns 2 users → change role → verify audit log entry
- Test full flow: start server → login as user → verify User Management returns 403
- Test full flow: start server → `GET /api/users` without auth → verify 401
