# Deployment Guide (DPG)

## Collex AI Assistant — SCRUM-50: User CRUD & Profile Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | SCRUM-50 |
| Title | User CRUD & Profile Management |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-01 |
| Status | Draft |
| Related TDD | documents/SCRUM-50/TDD.md |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-01 | DevOps Agent | Initiate document — auto-generated from TDD and project context |

---

## Sign-Off

| Name | Role | Signature and date |
|------|------|--------------------|
| | Dev Lead | ☐ Approved for deployment |
| | QA Lead | ☐ Testing completed |
| | Ops Lead | ☐ Infrastructure ready |

---

## 1. Overview

### 1.1 Feature Summary

User CRUD & Profile Management adds full user lifecycle management to the Admin Panel: create, view detail, edit, disable/enable, and delete users. All operations require JWT authentication and MANAGE_USERS permission, with full audit logging.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| Shared module | Modified | Extended User model (status, createdAt), UserStatus enum, UserStore interface |
| server-user-mgmt | Modified | 5 new API endpoints, UserCrudHandlers.kt, request DTOs, validation |
| Frontend | Modified | 4 new components, extended HTML templates, new CSS file |
| Configuration | None | No new configuration required |
| Database | None | InMemoryUserStore — no database migration needed |

### 1.3 Target Environments

| Environment | URL | Deploy Order | Approval Required |
|-------------|-----|-------------|-------------------|
| DEV | http://localhost:3000 | 1st | No |
| SIT | http://localhost:3000 | 2nd (same as DEV) | No |

---

## 2. Prerequisites

### 2.1 Software Dependencies

| Dependency | Version | Status |
|-----------|---------|--------|
| JDK | 17+ | Required |
| Gradle | 8.x (wrapper included) | Required |
| Node.js | 18+ (for KotlinJS compilation) | Required |
| kotest | 5.x | Already in build.gradle.kts |
| kotest-property | 5.x | Already in build.gradle.kts |
| kotlinx-serialization | Latest | Already in build.gradle.kts |

### 2.2 Access Requirements

| Access | Type | Who Needs It |
|--------|------|-------------|
| Source code repository | Git clone | Developer |
| Local machine | JDK + Gradle | Developer |

### 2.3 Backup Requirements

- No database backup needed (InMemoryUserStore — data is ephemeral)
- Git commit history serves as code backup

---

## 3. Pre-Deployment Checklist

| # | Item | Responsible | Status |
|---|------|-------------|--------|
| 1 | All 12 implementation tasks completed | Developer | ☐ |
| 2 | Shared module compiles: `./gradlew :shared:compileKotlinJvm` | Developer | ☐ |
| 3 | Backend compiles: `./gradlew :server:compileKotlinJvm` | Developer | ☐ |
| 4 | Frontend compiles: `./gradlew :frontend:compileKotlinJs` | Developer | ☐ |
| 5 | All property tests pass (14 properties, 100+ iterations each) | Developer | ☐ |
| 6 | All unit tests pass | Developer | ☐ |
| 7 | All integration tests pass | Developer | ☐ |
| 8 | Manual SIT completed — all scenarios pass | QA | ☐ |
| 9 | Code merged to release branch | Developer | ☐ |
| 10 | BRD, FSD, TDD, STP documents reviewed | Team | ☐ |

---

## 4. Database Migration

### 4.1 Migration Scripts

No database migration required. The system uses InMemoryUserStore (in-memory persistence).

### 4.2 Data Changes

| Change | Description | Impact |
|--------|-------------|--------|
| User model extension | New fields: status (default ACTIVE), createdAt (default "") | Backward compatible — existing data works with defaults |
| UserStatus enum | New enum: ACTIVE, DISABLED, PENDING | No existing data affected |

---

## 5. Application Deployment

### 5.1 Build Steps

```bash
# Step 1: Clean build all modules
./gradlew clean build

# Step 2: Verify shared module
./gradlew :shared:compileKotlinJvm
./gradlew :shared:jvmTest

# Step 3: Verify backend
./gradlew :server:compileKotlinJvm
./gradlew :server:user-mgmt:jvmTest

# Step 4: Verify frontend
./gradlew :frontend:compileKotlinJs

# Step 5: Run all tests
./gradlew test
```

### 5.2 Deployment Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Stop existing server | Ctrl+C or kill process | Server process terminated |
| 2 | Build all modules | `./gradlew clean build` | BUILD SUCCESSFUL |
| 3 | Start server | `./gradlew :server:jvmRun` | "Application started" in logs |
| 4 | Health check | Open http://localhost:3000 | Page loads successfully |
| 5 | Verify User Management | Navigate to User Management page | Page displays user list |
| 6 | Smoke test — Create user | Click "Add User", fill form, submit | User created successfully |

### 5.3 Quick Start (Development)

```bash
# One-command build and run
./gradlew :server:jvmRun

# Server starts at http://localhost:3000
# Login with admin credentials
# Navigate to User Management page
```

---

## 6. Configuration Changes

### 6.1 New Environment Variables

No new environment variables required.

### 6.2 Application Properties Changes

No application properties changes required.

### 6.3 Feature Flags

No feature flags. All CRUD functionality is enabled by default for users with MANAGE_USERS permission.

---

## 7. Post-Deployment Verification

### 7.1 Health Checks

| Check | Endpoint/Action | Expected Result | Timeout |
|-------|----------------|-----------------|---------|
| Application health | Open http://localhost:3000 | Page loads | 30s |
| API health | GET /api/users (with JWT) | 200 OK + user list | 5s |
| New endpoint | POST /api/users (with JWT) | 201 Created | 5s |

### 7.2 Smoke Tests

| # | Scenario | Steps | Expected Result |
|---|----------|-------|-----------------|
| 1 | Create user | Login → User Management → Add User → Fill form → Submit | User appears in list |
| 2 | View detail | Click user row | Detail panel shows user info |
| 3 | Edit user | Click Edit → Change name → Save | Name updated |
| 4 | Disable user | Click Disable → Confirm | User grayed out with DISABLED badge |
| 5 | Enable user | Click Enable | User restored to normal |
| 6 | Delete user | Click Delete → Type name → Confirm | User removed from list |
| 7 | Regression — existing features | Change role, toggle permission | Works as before |

### 7.3 Log Verification

| Log Entry | Level | Expected | Location |
|-----------|-------|----------|----------|
| Application started | INFO | Within 30s of start | Console output |
| User CRUD routes registered | INFO | After startup | Console output |

---

## 8. Rollback Plan

### 8.1 Rollback Decision Criteria

| Condition | Action |
|-----------|--------|
| CRUD operations fail completely | Rollback to previous version |
| Existing user list/role/permission features broken | Rollback to previous version |
| Minor UI issues only | Hotfix — no rollback |

### 8.2 Rollback Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Stop server | Ctrl+C | Server stopped |
| 2 | Checkout previous version | `git checkout {previous-commit}` | Code reverted |
| 3 | Rebuild | `./gradlew clean build` | BUILD SUCCESSFUL |
| 4 | Start server | `./gradlew :server:jvmRun` | Server running |
| 5 | Verify rollback | Open http://localhost:3000 | Previous version working |

### 8.3 Rollback Time Estimate

| Action | Estimated Time |
|--------|---------------|
| Stop server | 5 seconds |
| Git checkout | 5 seconds |
| Rebuild | 2-3 minutes |
| Start server | 30 seconds |
| Verification | 2 minutes |
| **Total** | **~5 minutes** |

### 8.4 Rollback Safety

All changes are **additive and backward compatible**:
- New fields (status, createdAt) have default values
- New API endpoints don't affect existing endpoints
- New frontend components don't modify existing components
- No database migration to rollback

---

## 9. Appendix

### Contacts

| Role | Name | Responsibility |
|------|------|---------------|
| Dev Lead | Dev Team | Technical issues, code review |
| QA Lead | QA Agent | Testing sign-off |

### Related Tickets

| Ticket | Summary | Relationship |
|--------|---------|-------------|
| SCRUM-50 | User CRUD & Profile Management | Main ticket |
| SCRUM-49 | User Management Enhancement | Parent epic |

### Files Changed

**New Files:**
- `shared/src/commonMain/kotlin/com/assistant/rbac/UserStatus.kt`
- `server/user-mgmt/.../routes/UserCrudHandlers.kt`
- `server/user-mgmt/.../routes/UserDtos.kt` (if separate)
- `frontend/.../pages/usermgmt/UserCreateForm.kt`
- `frontend/.../pages/usermgmt/UserDetailPanel.kt`
- `frontend/.../pages/usermgmt/UserDetailEditMode.kt`
- `frontend/.../pages/usermgmt/UserConfirmDialog.kt`
- `frontend/src/jsMain/resources/user-management-crud.css`
- Property test files (6 files in server + shared test directories)

**Modified Files:**
- `shared/.../rbac/RBACModels.kt` (User model + status, createdAt)
- `shared/.../rbac/UserStore.kt` (interface + 4 new methods)
- `shared/.../rbac/InMemoryUserStore.kt` (implementation + 4 new methods)
- `server/user-mgmt/.../routes/UserRoutes.kt` (5 new route registrations)
- `frontend/.../models/UserModels.kt` (UserInfo + status, createdAt, request DTOs)
- `frontend/.../pages/usermgmt/UserManagementPage.kt` (Add User button, detail panel wiring)
- `frontend/.../templates/user-management.html` (4 new templates)
- `frontend/src/jsMain/resources/user-management.css` (status badges, disabled rows)
