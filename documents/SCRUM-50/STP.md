# Software Test Plan (STP)

## Collex AI Assistant — SCRUM-50: User CRUD & Profile Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | SCRUM-50 |
| Title | User CRUD & Profile Management |
| Author | QA Agent |
| Version | 3.0 |
| Date | 2026-05-01 |
| Status | Draft |
| Related BRD | documents/SCRUM-50/BRD.md |
| Related FSD | documents/SCRUM-50/FSD.md |
| Related TDD | documents/SCRUM-50/TDD.md |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – QA Engineer | Create document |
| Peer Reviewer | BA Agent – Business Analyst | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-01 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |
| 2.0 | 2026-05-15 | QA Agent | Upgrade to 6 test levels — add E2E-API and E2E-UI; reclassify SIT to minimize manual testing |
| 3.0 | 2026-05-20 | QA Agent | Expand E2E-API from 1→6 cases; fix SIT-01 traceability |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy, scope, schedule, and resources for verifying the User CRUD & Profile Management feature (SCRUM-50). It covers all five CRUD operations across shared module, backend API, and frontend UI layers.

### 1.2 Test Objectives

- Verify all 8 user stories and their acceptance criteria from the BRD are implemented correctly
- Validate all 5 API endpoints return correct status codes and response bodies per FSD
- Ensure 14 correctness properties from TDD hold across random inputs (property-based testing)
- Confirm backward compatibility with existing user data and functionality
- Verify security controls (JWT auth, RBAC, self-deletion prevention)
- Validate audit logging completeness for all CRUD operations
- Ensure frontend UI components behave correctly (forms, panels, dialogs, overlays)

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/SCRUM-50/BRD.md |
| FSD | documents/SCRUM-50/FSD.md |
| TDD | documents/SCRUM-50/TDD.md |
| Kiro Spec — Requirements | .kiro/specs/user-crud-profile/requirements.md |
| Kiro Spec — Design | .kiro/specs/user-crud-profile/design.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties (random inputs) | Automated | kotest-property |
| UT | Unit/edge case tests | Automated | kotest |
| IT | API integration (Ktor testApplication) | Automated | Ktor test engine |
| E2E-API | REST endpoint E2E (real server) | Automated | Ktor client + JUnit 5 |
| E2E-UI | Browser UI E2E (Cucumber scenarios) | Automated | Cucumber + Serenity + WebDriver |
| SIT | Manual exploratory / edge cases only | Manual | Browser |

### 2.2 Test Cases Summary

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 14 | 14 | 0 |
| UT | 22 | 22 | 0 |
| IT | 7 | 7 | 0 |
| E2E-API | 6 | 6 | 0 |
| E2E-UI | 12 | 12 | 0 |
| SIT | 1 | 0 | 1 |
| **Total** | **62** | **61 (98%)** | **1 (2%)** |

### 2.3 E2E Automation Coverage

The following table shows how former SIT (manual) test cases have been reclassified to maximize automation coverage. Only visual/timing tests that require human judgment remain as manual SIT.

| Former SIT ID | Scenario | New Classification | Rationale |
|---------------|----------|-------------------|-----------|
| SIT-01 | Create user via form | **E2E-UI-01** | Deterministic CRUD, easy to automate |
| SIT-02 | Validation error | **E2E-UI-02** | Input/output clearly defined |
| SIT-03 | Duplicate email | **E2E-API-01** | API-level check sufficient, no browser needed |
| SIT-04 | View user detail | **E2E-UI-03** | Click + verify panel content |
| SIT-05 | Edit user | **E2E-UI-04** | Click + type + verify update |
| SIT-06 | Cancel edit | **E2E-UI-05** | Click + verify revert |
| SIT-07 | Disable user | **E2E-UI-06** | Click + verify badge change |
| SIT-08 | Enable user | **E2E-UI-07** | Click + verify badge change |
| SIT-09 | Delete user | **E2E-UI-08** | Click + type name + verify removal |
| SIT-10 | Delete confirmation safety | **E2E-UI-09** | Click + verify button state |
| SIT-11 | Blocking overlay | **SIT-01** (manual) | Visual timing hard to automate reliably |
| SIT-12 | Regression — user list | **E2E-UI-10** | Automate for fast regression |
| SIT-13 | Regression — role change | **E2E-UI-11** | Automate for fast regression |
| SIT-14 | Regression — permission toggle | **E2E-UI-12** | Automate for fast regression |

### 2.4 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify CRUD operations per FSD use cases | Yes |
| Property-Based Testing | Verify correctness properties with random inputs | Yes |
| Regression Testing | Ensure existing features not broken | Yes |
| Security Testing | Verify JWT auth, RBAC, self-deletion prevention | Yes |
| Usability Testing | Verify UI behavior (overlays, error messages, status badges) | Yes |
| Performance Testing | Not required (in-memory operations) | No |
| Compatibility Testing | Backward compat with existing data | Yes |

### 2.5 Test Approach

**Property-Based Testing First:** All 14 correctness properties are tested with kotest-property using minimum 100 random iterations each. This provides high confidence in universal correctness.

**Example-Based Unit Tests:** Cover specific edge cases, error scenarios, and UI interactions not easily captured by property tests.

**Integration Tests:** Verify full request/response cycle through Ktor test engine, including auth middleware.

**E2E-API Tests:** Verify REST endpoints against a real running server using Ktor HTTP client + JUnit 5. Covers full CRUD lifecycle, RBAC/auth verification (401/403), self-deletion prevention, status change API, duplicate email rejection, and audit log verification — all over real HTTP connections (6 test cases).

**E2E-UI Tests:** Verify browser-based user interactions using Cucumber + Serenity BDD + WebDriver. Covers all CRUD operations via the UI, form validation, status changes, confirmation dialogs, and regression scenarios.

**Manual SIT:** Reserved only for visual/UX tests that cannot be reliably automated — specifically blocking overlay timing and visual layout verification.

### 2.6 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| PBT / UT | Code compiles, shared module builds |
| IT | Backend compiles, all unit tests pass |
| E2E-API | Server deployed and running, all IT tests pass |
| E2E-UI | Server deployed and running, frontend built, all E2E-API tests pass |
| SIT | All automated E2E tests pass, server running on localhost:3000 |

### 2.7 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| PBT / UT | All 14 properties pass (100+ iterations each), all unit tests pass |
| IT | All API endpoints return correct responses, auth works |
| E2E-API | All API E2E scenarios pass on real server |
| E2E-UI | All Cucumber scenarios pass, Serenity report generated |
| SIT | All manual test scenarios pass, no Critical/Major defects |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Type |
|---|----------------|----------|---------------|-----------|
| 1 | Create User | High | UC-01 | PBT (P1,P2,P4,P6,P8) + UT + IT + E2E-API + E2E-UI |
| 2 | View User Detail | High | UC-02 | UT + IT + E2E-API + E2E-UI |
| 3 | Edit User Info | High | UC-03 | PBT (P1,P2,P4,P6) + UT + IT + E2E-API + E2E-UI |
| 4 | Disable/Enable User | High | UC-04a/b | PBT (P9,P11) + UT + IT + E2E-API + E2E-UI |
| 5 | Delete User | High | UC-05 | PBT (P10) + UT + IT + E2E-API + E2E-UI |
| 6 | Extended User Model | High | — | PBT (P3,P5) + UT |
| 7 | UserStore Interface | High | — | PBT (P4,P7,P9,P10) + UT |
| 8 | API Auth & Validation | High | — | PBT (P12,P13,P14) + IT + E2E-API |
| 9 | Backward Compatibility | Medium | — | UT + E2E-UI (regression) |
| 10 | Audit Logging | High | — | PBT (P6) + IT + E2E-API |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | User self-registration | Not part of SCRUM-50 |
| 2 | Password management | Not part of SCRUM-50 |
| 3 | Avatar upload | Not implemented (initials only) |
| 4 | PENDING status workflow | Reserved for future use |
| 5 | Performance/load testing | In-memory operations, not needed |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | URL | Database | Purpose |
|-------------|-----|----------|---------|
| DEV/SIT | http://localhost:3000 | InMemoryUserStore | Development and system testing |

### 4.2 Browser Requirements

| Browser | Version | Required |
|---------|---------|----------|
| Chrome | 90+ | Yes (primary) |
| Firefox | 90+ | No (optional) |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Admin user | User with ADMINISTRATOR role + MANAGE_USERS | Pre-seeded in InMemoryUserStore | Available on server start |
| Test users | Users with various roles and statuses | Created during tests | Via POST /api/users |

---

## 5. Test Cases

### 5.1 Property-Based Test Cases (Automated — kotest-property)

| ID | Property | Description | Min Iterations | File |
|----|----------|-------------|----------------|------|
| PBT-01 | P1: Name validation | Rejects empty/whitespace, accepts non-whitespace | 100 | UserValidationPropertyTest.kt |
| PBT-02 | P2: Email validation | Accepts valid emails, rejects invalid | 100 | UserValidationPropertyTest.kt |
| PBT-03 | P3: User round-trip | Serialize → deserialize preserves all fields | 100 | UserSerializationPropertyTest.kt |
| PBT-04 | P4: Email uniqueness | Rejects duplicate emails in UserStore | 100 | UserStorePropertyTest.kt |
| PBT-05 | P5: UserDto completeness | toDto() includes all required fields | 100 | UserDtoCompletenessPropertyTest.kt |
| PBT-06 | P6: Audit logging | All CRUD ops generate correct audit entries | 100 | UserAuditPropertyTest.kt |
| PBT-07 | P7: UserStore operations | CRUD returns true for existing, false for non-existent | 100 | UserStorePropertyTest.kt |
| PBT-08 | P8: Creation defaults | New users get ACTIVE status + createdAt | 100 | UserCreationDefaultsPropertyTest.kt |
| PBT-09 | P9: Status persistence | ACTIVE↔DISABLED round-trip works | 100 | UserStorePropertyTest.kt |
| PBT-10 | P10: Delete removes | Deleted user not found by findById/getAll | 100 | UserStorePropertyTest.kt |
| PBT-11 | P11: Disabled auth | DISABLED users rejected at auth | 100 | (auth integration) |
| PBT-12 | P12: Unauthorized rejection | No JWT → 401, no permission → 403 | 100 | UserCrudIntegrationTest.kt |
| PBT-13 | P13: Non-existent 404 | Random UUID → 404 on GET/PUT/DELETE | 100 | UserCrudRoutesTest.kt |
| PBT-14 | P14: Invalid body 400 | Malformed requests → 400 | 100 | UserCrudRoutesTest.kt |

### 5.2 Unit Test Cases (Automated — kotest)

| ID | Scenario | Input | Expected | Priority | Story |
|----|----------|-------|----------|----------|-------|
| UT-01 | Create user with valid data | Valid CreateUserRequest | 201 + UserDto | High | S1 |
| UT-02 | Create user with empty name | name="" | 400 "Name is required" | High | S1 |
| UT-03 | Create user with whitespace name | name="   " | 400 "Name is required" | High | S1 |
| UT-04 | Create user with invalid email | email="notanemail" | 400 "Invalid email format" | High | S1 |
| UT-05 | Create user with invalid role | role="SUPERADMIN" | 400 "Invalid role" | High | S1 |
| UT-06 | Create user with duplicate email | existing email | 409 "Email already exists" | High | S1 |
| UT-07 | Get existing user | valid userId | 200 + UserDto | High | S2 |
| UT-08 | Get non-existent user | random UUID | 404 "User not found" | High | S2 |
| UT-09 | Update user with valid data | Valid UpdateUserRequest | 200 + updated UserDto | High | S3 |
| UT-10 | Update user with duplicate email | existing email (other user) | 409 "Email already exists" | High | S3 |
| UT-11 | Update non-existent user | random UUID | 404 "User not found" | High | S3 |
| UT-12 | Disable active user | status=DISABLED | 200 + UserDto(DISABLED) | High | S4 |
| UT-13 | Enable disabled user | status=ACTIVE | 200 + UserDto(ACTIVE) | High | S4 |
| UT-14 | Invalid status value | status="INVALID" | 400 "Invalid status" | Medium | S4 |
| UT-15 | Delete existing user | valid userId | 204 No Content | High | S5 |
| UT-16 | Self-deletion attempt | own userId | 403 "Cannot delete own account" | High | S5 |
| UT-17 | Delete non-existent user | random UUID | 404 "User not found" | High | S5 |
| UT-18 | Backward compat — missing status | Old JSON without status | Defaults to ACTIVE | Medium | S6 |
| UT-19 | Backward compat — missing createdAt | Old JSON without createdAt | Defaults to "" | Medium | S6 |
| UT-20 | UserStore updateUser non-existent | random UUID | Returns false | Medium | S7 |
| UT-21 | UserStore deleteUser non-existent | random UUID | Returns false | Medium | S7 |
| UT-22 | UserStore updateStatus non-existent | random UUID | Returns false | Medium | S7 |

### 5.3 Integration Test Cases (Automated — Ktor testApplication)

| ID | Scenario | Steps | Expected | Priority |
|----|----------|-------|----------|----------|
| IT-01 | Full CRUD lifecycle | Create → Get → Update → Disable → Enable → Delete | All operations succeed with correct status codes | High |
| IT-02 | Unauthorized access (no JWT) | Call each endpoint without Authorization header | All return 401 | High |
| IT-03 | Forbidden access (no permission) | Call each endpoint with READER role JWT | All return 403 | High |
| IT-04 | Create + audit log | Create user, check audit log | AuditLogEntry with USER_CREATED | High |
| IT-05 | Update + audit log | Update user, check audit log | AuditLogEntry with USER_UPDATED + old/new values | High |
| IT-06 | Disable + audit log | Disable user, check audit log | AuditLogEntry with USER_DISABLED | High |
| IT-07 | Delete + audit log | Delete user, check audit log | AuditLogEntry with USER_DELETED | High |

### 5.4 E2E-API Test Cases (Automated — Ktor client + JUnit 5)

| ID | Scenario | Steps | Expected | Priority |
|----|----------|-------|----------|----------|
| E2E-API-01 | Duplicate email rejection (real server) | POST /api/users with existing email against running server | 409 Conflict, "Email already exists" | High |
| E2E-API-02 | Full CRUD lifecycle (real server) | Create → Get → Update → Disable → Enable → Delete on real server | All operations succeed with correct status codes over real HTTP | High |
| E2E-API-03 | RBAC/Auth checks (real server) | All 5 endpoints without JWT → 401; with READER JWT → 403 | All 10 requests rejected correctly | High |
| E2E-API-04 | Self-deletion prevention (real server) | DELETE /api/users/{own-id} with admin JWT | 403 "Cannot delete your own account" | High |
| E2E-API-05 | Status change API (real server) | Disable → verify → Enable → verify → invalid status | Status persists correctly; invalid returns 400 | High |
| E2E-API-06 | Audit log verification (real server) | Create + Update + Disable + Delete, verify audit entries | All 4 audit entries exist with correct action/values | Medium |

**File:** `e2e-tests/src/test/kotlin/com/assistant/e2e/api/UserCrudApiTest.kt`

### 5.5 E2E-UI Test Cases (Automated — Cucumber + Serenity + WebDriver)

| ID | Scenario | Gherkin Summary | Priority |
|----|----------|----------------|----------|
| E2E-UI-01 | Create user via form | Given admin on User Management → When fill form + submit → Then user in list | High |
| E2E-UI-02 | Create user validation error | Given form open → When submit empty name → Then error shown | High |
| E2E-UI-03 | View user detail | Given user list → When click user row → Then detail panel shows info | High |
| E2E-UI-04 | Edit user | Given detail panel → When edit name + save → Then name updated | High |
| E2E-UI-05 | Cancel edit | Given edit mode → When cancel → Then original values restored | High |
| E2E-UI-06 | Disable user | Given ACTIVE user → When disable + confirm → Then DISABLED badge | High |
| E2E-UI-07 | Enable user | Given DISABLED user → When enable → Then ACTIVE badge | High |
| E2E-UI-08 | Delete user | Given user → When delete + type name + confirm → Then user removed | High |
| E2E-UI-09 | Delete confirmation safety | Given delete dialog → When type wrong name → Then confirm disabled | High |
| E2E-UI-10 | Regression — user list | Given server running → When navigate to User Management → Then users displayed | High |
| E2E-UI-11 | Regression — role change | Given user selected → When change role → Then role updated | High |
| E2E-UI-12 | Regression — permission toggle | Given user selected → When toggle permission → Then permission toggled | High |

**Feature File:** `e2e-tests/src/test/resources/features/user-management/016-UserCrud.feature`
**Steps File:** `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/UserCrudSteps.kt`
**Runner:** `e2e-tests/src/test/kotlin/com/assistant/e2e/runners/UiUserCrudRunner.kt`

### 5.6 Manual SIT Test Cases (Browser-based — Visual/UX Only)

| ID | Scenario | Steps | Expected | Priority |
|----|----------|-------|----------|----------|
| SIT-01 | Blocking overlay timing | Perform any async operation | Overlay shown during request, clicks blocked, correct message displayed, overlay disappears on completion | Medium |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| QA Agent | QA Agent | Test planning, automated test design, SIT execution |
| Developer | Dev Team | Property-based tests, unit tests, integration tests, bug fixes |
| BA Agent | BA Agent | Acceptance criteria clarification, UAT support |
| SA Agent | SA Agent | Technical design review, test architecture guidance |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | InMemoryUserStore data loss between test runs | Medium | High | Re-seed test data before each test session |
| 2 | Property tests flaky due to random generation | Low | Low | Use deterministic seeds for reproducibility |
| 3 | Frontend template changes break existing UI | Medium | Medium | Regression tests for existing functionality |
| 4 | Concurrent access issues in UserStore | High | Low | Mutex-based locking + property tests for concurrency |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | CRUD operation fails completely, data loss | Delete doesn't remove user, create corrupts data |
| Major | Feature not working but workaround exists | Edit saves but doesn't update UI |
| Minor | UI issue, cosmetic defect | Status badge wrong color |
| Trivial | Typo, minor alignment | Button text typo |

### 8.2 Priority Levels

| Priority | Definition | SLA |
|----------|-----------|-----|
| P1 | Must fix immediately | Same session |
| P2 | Must fix before release | 1 day |
| P3 | Should fix if time permits | 3 days |
| P4 | Nice to fix, can defer | Next sprint |

---

## 9. Test Metrics & Reporting

### 9.1 Metrics

| Metric | Formula | Target |
|--------|---------|--------|
| Property Test Coverage | Properties tested / Total properties | 14/14 (100%) |
| Property Test Iterations | Min iterations per property | ≥ 100 |
| Unit Test Pass Rate | Passed / Total | ≥ 95% |
| Integration Test Pass Rate | Passed / Total | 100% |
| SIT Pass Rate | Passed / Total | ≥ 95% |
| Critical Defect Count | Count | 0 |

---

## 10. Appendix

### Glossary

| Term | Definition |
|------|------------|
| SIT | System Integration Testing (manual only — visual/UX tests) |
| STP | Software Test Plan |
| PBT | Property-Based Test |
| UT | Unit Test |
| IT | Integration Test |
| E2E-API | End-to-End API Test — REST endpoint tests against a real running server |
| E2E-UI | End-to-End UI Test — Browser-based tests using Cucumber + Serenity + WebDriver |

### Assumptions

- Server runs on localhost:3000 for SIT
- Admin user with MANAGE_USERS permission is pre-seeded
- InMemoryUserStore is reset on server restart
- All automated tests run via `./gradlew test`
