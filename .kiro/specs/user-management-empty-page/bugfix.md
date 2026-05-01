# Bugfix Requirements Document

## Introduction

The User Management page at `localhost:3000/#user_management` previously appeared completely empty. The "USER DIRECTORY" section showed no users, the "AUDIT LOG — NEURAL CONSOLE" was stuck on "Awaiting audit events...", and the Permission Toggles panel never appeared because no user could be selected.

The root cause was that the `InMemoryUserStore` was created empty in the DI module (`CoreModule.kt`) and was never seeded with users. The `AuthServiceImpl` maintained its own hardcoded `defaultUsers` map for authentication but never registered those users into the `UserStore`. When the frontend called `GET /api/users`, it received an empty list `[]`. Additionally, when a user successfully authenticated via JWT, they were not added to the `UserStore`, so the store remained permanently empty.

**Status: Fixed.** The `InMemoryUserStore` is now seeded with default users (`admin`/ADMINISTRATOR, `user`/READER) at DI initialization, and `AuthServiceImpl` registers users into the store on successful authentication.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the server starts THEN the `InMemoryUserStore` is created with an empty map and no users are seeded into it

1.2 WHEN a user successfully authenticates via the login page THEN the authenticated user is NOT registered into the `UserStore`, leaving it empty

1.3 WHEN the User Management page loads and calls `GET /api/users` THEN the API returns an empty list `[]` and the User Directory section shows no users

1.4 WHEN the User Management page loads and calls `GET /api/users/audit-log` THEN the Audit Log section remains stuck on "Awaiting audit events..." because no user management actions have ever been possible

1.5 WHEN an administrator navigates to the User Management page THEN the Permission Toggles panel never appears because there are no users to select

### Expected Behavior (Correct)

2.1 WHEN the server starts THEN the system SHALL seed the `InMemoryUserStore` with the default users defined in `AuthServiceImpl` (admin and user accounts) so that `GET /api/users` returns a populated list

2.2 WHEN a user successfully authenticates via the login page THEN the system SHALL ensure the authenticated user exists in the `UserStore`, registering them if not already present

2.3 WHEN the User Management page loads and calls `GET /api/users` THEN the API SHALL return the list of all registered users with their id, name, email, role, avatarUrl, and customPermissions, and the User Directory SHALL display them with avatar, name, email, and role dropdown

2.4 WHEN the User Management page loads and calls `GET /api/users/audit-log` THEN the Audit Log section SHALL display any existing audit entries, or show "Awaiting audit events..." only if genuinely no audit events have been recorded yet

2.5 WHEN an administrator navigates to the User Management page and clicks on a user in the User Directory THEN the Permission Toggles panel SHALL appear for the selected user

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a user provides invalid credentials (wrong username or password) THEN the system SHALL CONTINUE TO return a 401 authentication failure without adding any user to the `UserStore`

3.2 WHEN a non-Administrator user accesses the User Management page THEN the system SHALL CONTINUE TO show the "Access Denied" view with HTTP 403

3.3 WHEN an administrator changes a user's role via `PUT /api/users/{userId}/role` THEN the system SHALL CONTINUE TO update the role correctly and log an audit entry

3.4 WHEN an administrator toggles a user's permission via `PUT /api/users/{userId}/permissions` THEN the system SHALL CONTINUE TO update the permission correctly and log an audit entry

3.5 WHEN the `FileBasedAuditLogStore` has existing audit entries from previous sessions THEN the system SHALL CONTINUE TO load and display them correctly on server restart

3.6 WHEN the JWT token is validated for API requests THEN the system SHALL CONTINUE TO authenticate and authorize requests correctly using the existing JWT verification logic

---

## Bug Condition (Formal)

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type ServerStartupState
  OUTPUT: boolean

  // The bug triggers whenever the server starts or a user logs in,
  // because the UserStore is never populated in either case.
  RETURN X.userStoreContents = EMPTY
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — UserStore is populated after server startup
FOR ALL X WHERE isBugCondition(X) DO
  result ← startServer'(X)
  ASSERT result.userStore.getAll().size > 0
  ASSERT result.userStore.getAll() CONTAINS defaultUsers
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Existing auth, RBAC, and audit behavior unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // Authentication with invalid credentials still fails
  // Role changes still work and produce audit entries
  // Permission toggles still work and produce audit entries
  // JWT validation still works correctly
  // Non-admin access still returns 403
END FOR
```
