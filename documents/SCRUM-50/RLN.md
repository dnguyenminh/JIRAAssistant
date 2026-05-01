# Release Notes (RLN)

## Collex AI Assistant — SCRUM-50: User CRUD & Profile Management

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.x.0 |
| Release Date | 2026-05-01 |
| Jira Ticket | SCRUM-50 |
| Epic | SCRUM-49 — User Management Enhancement |
| Environment | DEV |
| Author | DevOps Agent |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-01 | DevOps Agent | Initiate document |

---

## 1. What's New

### 1.1 Feature Summary

The User Management page now supports full user lifecycle management. Administrators can create new users, view detailed profiles, edit user information, disable/enable accounts, and permanently delete users — all from a single, intuitive interface.

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | Create User | New "Add User" button opens a form to create users with name, email, and role | High |
| 2 | View User Detail | Click any user row to see a detailed profile panel with avatar, status, and creation date | High |
| 3 | Edit User Info | Edit a user's name and email directly from the detail panel | High |
| 4 | Disable/Enable User | Soft-disable accounts (users can't log in) and re-enable them when needed | High |
| 5 | Delete User | Permanently remove users with a name-confirmation safety dialog | High |
| 6 | Status Badges | User rows now show color-coded status badges (ACTIVE/DISABLED) | Medium |
| 7 | Audit Trail | All user management actions are recorded in the audit log | Medium |

---

## 2. Technical Changes

### 2.1 API Changes

| Type | Endpoint | Method | Description |
|------|----------|--------|-------------|
| New | /api/users | POST | Create a new user (201 Created) |
| New | /api/users/{id} | GET | Get user detail (200 OK) |
| New | /api/users/{id} | PUT | Update user name and email (200 OK) |
| New | /api/users/{id}/status | PUT | Update user status — ACTIVE or DISABLED (200 OK) |
| New | /api/users/{id} | DELETE | Permanently delete a user (204 No Content) |
| Modified | /api/users | GET | Existing endpoint now returns `status` and `createdAt` fields in UserDto |

### 2.2 Model Changes

| Type | Object | Description |
|------|--------|-------------|
| New Enum | UserStatus | ACTIVE, DISABLED, PENDING — user lifecycle state |
| Modified | User (shared) | Added `status: UserStatus = ACTIVE` and `createdAt: String = ""` fields |
| Modified | UserDto | Added `status: String` and `createdAt: String` fields |
| New DTO | CreateUserRequest | name, email, role, status (optional) |
| New DTO | UpdateUserRequest | name, email |
| New DTO | UpdateStatusRequest | status |

### 2.3 Frontend Changes

| Type | Component | Description |
|------|-----------|-------------|
| New | UserCreateForm.kt | Create user form overlay with validation |
| New | UserDetailPanel.kt | User detail display with action buttons |
| New | UserDetailEditMode.kt | Inline edit mode for name/email |
| New | UserConfirmDialog.kt | Confirmation dialogs for disable/delete |
| New | user-management-crud.css | Styles for new CRUD components |
| Modified | UserManagementPage.kt | "Add User" button, detail panel wiring |
| Modified | user-management.html | 4 new HTML templates |
| Modified | user-management.css | Status badges, disabled row styles |
| Modified | UserModels.kt | Extended UserInfo + request DTOs |

### 2.4 Infrastructure Changes

No infrastructure changes required.

---

## 3. Bug Fixes

No bug fixes included in this release. This is a new feature release.

---

## 4. Known Issues & Limitations

| # | Issue | Impact | Workaround | Target Fix |
|---|-------|--------|------------|------------|
| 1 | InMemoryUserStore — data lost on restart | All created users are lost when server restarts | Re-create users after restart | Future: persistent database |
| 2 | PENDING status not exposed via API | Cannot set users to PENDING status | N/A — reserved for future use | Future release |
| 3 | No avatar upload | Users only have initials-based avatars | N/A | Future release |
| 4 | No user search/filter | Cannot search or filter the user list | Scroll through list | Future release |

---

## 5. Dependencies

### 5.1 Pre-requisite Releases

No pre-requisite releases. This feature builds on the existing User Management page.

### 5.2 External System Changes

No external system changes required.

---

## 6. Migration Notes

### 6.1 Data Migration

No data migration required. All changes are backward compatible:
- New `status` field defaults to `ACTIVE`
- New `createdAt` field defaults to `""`
- Existing serialized User data works without modification

### 6.2 Breaking Changes

No breaking changes in this release. Fully backward compatible.

### 6.3 Backward Compatibility

Fully backward compatible:
- Existing API responses now include `status` and `createdAt` fields (additive)
- Frontend handles missing fields gracefully (defaults)
- Existing role change and permission toggle features unchanged
- No database schema changes

---

## 7. Testing Summary

| Test Level | Total | Passed | Failed | Blocked | Pass Rate |
|-----------|-------|--------|--------|---------|-----------|
| Property-Based Tests | 14 | 14 | 0 | 0 | 100% |
| Unit Tests | 22 | 22 | 0 | 0 | 100% |
| Integration Tests | 7 | 7 | 0 | 0 | 100% |
| Manual SIT | 14 | 14 | 0 | 0 | 100% |

### Defect Summary

| Severity | Found | Fixed | Open | Deferred |
|----------|-------|-------|------|----------|
| Critical | 0 | 0 | 0 | 0 |
| Major | 0 | 0 | 0 | 0 |
| Minor | 0 | 0 | 0 | 0 |

---

## 8. Deployment Instructions

See: [Deployment Guide](documents/SCRUM-50/DPG.md)

### Quick Reference

| Step | Action | Estimated Time |
|------|--------|---------------|
| 1 | Stop existing server | 5 seconds |
| 2 | Build all modules (`./gradlew clean build`) | 2-3 minutes |
| 3 | Start server (`./gradlew :server:jvmRun`) | 30 seconds |
| 4 | Smoke test verification | 2 minutes |
| **Total** | | **~5 minutes** |

---

## 9. Rollback Plan

**Rollback Decision Criteria:**
- CRUD operations fail completely
- Existing user management features broken (regression)

**Estimated Rollback Time:** ~5 minutes

See [Deployment Guide — Rollback Section](documents/SCRUM-50/DPG.md#8-rollback-plan) for detailed steps.

---

## 10. Contacts

| Role | Name | Responsibility |
|------|------|---------------|
| Dev Lead | Dev Team | Technical issues |
| QA Lead | QA Agent | Testing sign-off |
| BA | BA Agent | Requirements clarification |
| SA | SA Agent | Technical design |

---

## 11. Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Dev Lead | | | ☐ Approved |
| QA Lead | | | ☐ Approved |
| Release Manager | | | ☐ Approved |
