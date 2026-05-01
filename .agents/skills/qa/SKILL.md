---
name: QA Skill
description: Instructions for test planning, E2E automation, Serenity BDD, and manual testing for Jira Assistant.
---

# QA Skill (Quality Assurance)

As the QA, your goal is to ensure the Jira Assistant is reliable, bug-free, and delivers a premium user experience.

## 1. Test Planning — Test Level Classification

### ⛔ BẮT BUỘC: 6 Test Levels

Khi tạo STP (Software Test Plan) và STC (Software Test Cases), PHẢI phân loại test cases vào **6 levels** (không phải 4):

| Level | Prefix | Scope | Automation | Tools |
|-------|--------|-------|------------|-------|
| **PBT** | PBT-XX | Correctness properties (random inputs) | ✅ Automated | kotest-property |
| **UT** | UT-XX | Unit/edge case tests | ✅ Automated | kotest |
| **IT** | IT-XX | API integration (Ktor testApplication) | ✅ Automated | Ktor test engine |
| **E2E-API** | E2E-API-XX | REST endpoint E2E (real server) | ✅ Automated | Ktor client + JUnit 5 |
| **E2E-UI** | E2E-UI-XX | Browser UI E2E (Cucumber scenarios) | ✅ Automated | Cucumber + Serenity + WebDriver |
| **SIT** | SIT-XX | Manual exploratory / edge cases only | ❌ Manual | Browser (Playwright/manual) |

### Phân loại SIT → E2E Automation

Khi thiết kế test cases, ƯU TIÊN tự động hóa. Chỉ giữ lại SIT manual cho:
- Visual/UX verification (layout, animation, responsive)
- Complex multi-step exploratory scenarios
- Edge cases khó tự động hóa (drag-drop, file upload, etc.)

**Quy tắc chuyển đổi:**

| SIT Scenario | Chuyển thành | Lý do |
|-------------|-------------|-------|
| CRUD operations (create, edit, delete) | E2E-UI | Deterministic, dễ automate |
| Form validation (empty, invalid input) | E2E-UI | Input/output rõ ràng |
| API response verification | E2E-API | Không cần browser |
| RBAC/auth checks | E2E-API | API-level check đủ |
| Status changes (disable/enable) | E2E-UI | Click + verify badge |
| Confirmation dialogs | E2E-UI | Click + verify dialog |
| Regression — existing features | E2E-UI | Automate để chạy lại nhanh |
| Blocking overlay timing | SIT (manual) | Visual timing khó automate |
| Complex UX flows | SIT (manual) | Cần human judgment |

### STP Structure với 6 Levels

```markdown
## Test Strategy

### Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties | Automated | kotest-property |
| UT | Unit/edge cases | Automated | kotest |
| IT | API integration | Automated | Ktor testApplication |
| E2E-API | REST endpoint E2E | Automated | Ktor client + JUnit 5 |
| E2E-UI | Browser UI E2E | Automated | Cucumber + Serenity + WebDriver |
| SIT | Manual exploratory | Manual | Browser |

### Test Cases Summary

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | {N} | {N} | 0 |
| UT | {N} | {N} | 0 |
| IT | {N} | {N} | 0 |
| E2E-API | {N} | {N} | 0 |
| E2E-UI | {N} | {N} | 0 |
| SIT | {N} | 0 | {N} |
| **Total** | **{N}** | **{N}** | **{N}** |
```

### STC Structure cho E2E Tests

#### E2E-API Test Cases

```markdown
### E2E-API-01: {Title}

| Attribute | Value |
|-----------|-------|
| **ID** | E2E-API-01 |
| **Priority** | {High/Medium/Low} |
| **Type** | Automated (Ktor client + JUnit 5) |
| **File** | e2e-tests/src/test/kotlin/com/assistant/e2e/api/{Feature}ApiTest.kt |
| **Traces To** | BRD Req {N} (AC {N}) |

**Test Steps:**
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | {action} | {expected} |
```

#### E2E-UI Test Cases

```markdown
### E2E-UI-01: {Title}

| Attribute | Value |
|-----------|-------|
| **ID** | E2E-UI-01 |
| **Priority** | {High/Medium/Low} |
| **Type** | Automated (Cucumber + Serenity) |
| **Feature File** | e2e-tests/src/test/resources/features/{capability}/{NNN}-{Feature}.feature |
| **Steps File** | e2e-tests/src/test/kotlin/com/assistant/e2e/steps/{Feature}Steps.kt |
| **Scenario** | {Gherkin scenario name} |
| **Traces To** | BRD Req {N} (AC {N}) |

**Gherkin:**
```gherkin
Scenario: {title}
  Given {precondition}
  When {action}
  Then {expected result}
```
```

## 2. E2E Test Implementation

### E2E-API Tests
- Xem chi tiết trong `.kiro/steering/e2e-testing.md`
- File: `e2e-tests/src/test/kotlin/com/assistant/e2e/api/{Feature}ApiTest.kt`
- Extend `ApiTestBase()`, sử dụng `@Tag("api")`
- Kiểm tra: HTTP status codes, response body, RBAC, no-JWT → 401

### E2E-UI Tests
- Xem chi tiết trong `.kiro/steering/e2e-testing.md`
- 3 files: `.feature` + `Steps.kt` + `Runner.kt`
- Sử dụng Cucumber + Serenity + WebDriver
- Feature files đặt trong thư mục capability phù hợp

## 3. Serenity BDD Implementation

### Screenplay Pattern
- Organize all tests using **Actors**, **Tasks**, **Interactions**, and **Questions**
- Ensure each task is reusable and atomic
- Avoid "Spaghetti Testing" by following SOLID for Page Objects and Task objects

### Feature Definition
- Requirements must be translated into **Acceptance Criteria**
- Scenario names should be clear and follow the `Gherkin` format (`Given-When-Then`)

## 4. Testing Coverage

### UI Testing
- Test all major UI components
- Verify responsive layouts and interactions

### AI Integration Testing
- Verify AI response consistency and error handling for both Local (Ollama) and Cloud (Gemini) agents
- Test the "No AI" state for offline support

## 5. Reporting

### Test Report Template
- Sử dụng template tại `documents/templates/TEST-REPORT-TEMPLATE.md`
- Sections 1-7 chỉ hiển thị kết quả CUỐI CÙNG
- Re-test history gộp vào Appendix A (collapsible `<details>`)
- KHÔNG hiển thị intermediate FAIL results trong sections chính

### Serenity Reports
- Automatically generate Serenity reports after each build
- Ensure screenshot capturing is enabled for failed scenarios

## 6. Test Execution Checklist

Khi thực hiện test execution cho một ticket:

- [ ] Chạy automated tests: `./gradlew :shared:jvmTest :server:jvmTest`
- [ ] Chạy E2E-API tests: `./gradlew :e2e-tests:test --tests "*ApiTest*"`
- [ ] Chạy E2E-UI tests: `./gradlew :e2e-tests:test --tests "*Runner*"`
- [ ] Thực hiện manual SIT tests trên browser (localhost:3000)
- [ ] Tạo TEST-REPORT.md theo template
- [ ] Chụp screenshots cho evidence
- [ ] Báo cáo bugs nếu có
