---
inclusion: auto
description: E2E testing rules for API and UI tests using Cucumber, Serenity, and Ktor client
---

# E2E Testing — Quy tắc bắt buộc

## Nguyên tắc chung

Mọi module/feature có frontend UI **BẮT BUỘC** phải có cả 2 loại E2E test:
1. **API E2E Tests** — Kiểm tra REST endpoints (Ktor client, JUnit 5)
2. **UI E2E Tests** — Kiểm tra giao diện người dùng (Cucumber + Serenity + WebDriver)

Khi tạo task list cho bất kỳ feature nào, **LUÔN LUÔN** bao gồm task cho cả API E2E tests VÀ UI E2E tests. Không được bỏ qua UI E2E tests.

## Cấu trúc E2E Tests

```
e2e-tests/
├── src/test/
│   ├── kotlin/com/assistant/e2e/
│   │   ├── api/
│   │   │   ├── ApiTestBase.kt          # Base class cho API tests
│   │   │   └── {Feature}ApiTest.kt     # API test cho từng feature
│   │   ├── steps/
│   │   │   ├── CommonSteps.kt          # Shared steps (auth, navigation, etc.)
│   │   │   ├── TestHelper.kt           # Utility functions
│   │   │   ├── SharedTestContext.kt     # Shared state giữa steps
│   │   │   └── {Feature}Steps.kt       # Step definitions cho từng feature
│   │   └── runners/
│   │       └── Ui{Feature}Runner.kt    # Serenity Cucumber runner cho từng feature
│   └── resources/
│       └── features/
│           └── {NNN}-{Feature}.feature  # Cucumber feature file (đánh số thứ tự)
```

## Quy tắc tạo API E2E Tests

- File: `e2e-tests/src/test/kotlin/com/assistant/e2e/api/{Feature}ApiTest.kt`
- Extend `ApiTestBase()`
- Sử dụng `@Tag("api")` (inherited), `@TestMethodOrder(OrderAnnotation::class)`
- Mỗi test dùng `runBlocking { ... }`, Ktor client, raw JSON `setBody("""...""")`
- Kiểm tra: HTTP status codes, response body fields, RBAC (admin/reader/neural_architect), no-JWT → 401

## Quy tắc tạo UI E2E Tests

### 1. Feature file (`.feature`)

- Đặt tại: `e2e-tests/src/test/resources/features/{NNN}-{Feature}.feature`
- Đánh số thứ tự liên tục (001, 002, ..., 013, 014, ...)
- Tag `@ui` ở đầu file
- Mỗi scenario tag `@req-{N.N}` tham chiếu requirement
- Background chung: auth + project selection
- Bao gồm scenarios cho: hiển thị UI elements, tương tác người dùng, RBAC, error handling, page navigation

### 2. Step definitions (`Steps.kt`)

- Đặt tại: `e2e-tests/src/test/kotlin/com/assistant/e2e/steps/{Feature}Steps.kt`
- Sử dụng `@Managed lateinit var driver: WebDriver`
- Sử dụng `Actor.named("{Feature}User")`
- Sử dụng `TestHelper.wait(driver).until { ... }` cho waits
- Sử dụng `TestHelper.pageRendered(d)` làm fallback trong wait conditions
- Sử dụng `TestHelper.js(driver).executeScript(...)` cho JS execution
- **KHÔNG redefine** steps đã có trong `CommonSteps.kt` (auth, navigation, clicks, etc.)
- Steps phải resilient — dùng fallbacks, không fail cứng nếu element không tìm thấy

### 3. Runner (`Runner.kt`)

- Đặt tại: `e2e-tests/src/test/kotlin/com/assistant/e2e/runners/Ui{Feature}Runner.kt`
- Pattern cố định:

```kotlin
package com.assistant.e2e.runners

import net.serenitybdd.cucumber.CucumberWithSerenity
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(CucumberWithSerenity::class)
@CucumberOptions(
    features = ["src/test/resources/features/{NNN}-{Feature}.feature"],
    glue = ["com.assistant.e2e.steps"],
    tags = "@ui",
    plugin = ["pretty"]
)
class Ui{Feature}Runner
```

## Checklist khi tạo task list cho feature mới

Mỗi feature PHẢI có các task sau (không được thiếu):

- [ ] Task API E2E Tests: Tạo `{Feature}ApiTest.kt` — kiểm tra tất cả REST endpoints
- [ ] Task UI E2E Tests: Tạo 3 files:
  - [ ] `{NNN}-{Feature}.feature` — Cucumber scenarios
  - [ ] `{Feature}Steps.kt` — Step definitions
  - [ ] `Ui{Feature}Runner.kt` — Serenity runner

## CSS Selectors Convention

Khi viết UI E2E tests, sử dụng CSS selectors theo thứ tự ưu tiên:
1. `By.id("element-id")` — ưu tiên nhất
2. `By.cssSelector(".class-name")` — khi không có id
3. `By.xpath("//...")` — fallback cuối cùng

## Existing Features & UI E2E Test Coverage

| Feature | Feature File | Steps | Runner | Status |
|---------|-------------|-------|--------|--------|
| Initialization | 001-Initialization.feature | InitializationSteps.kt | UiInitializationRunner.kt | ✅ |
| AI Analysis | 002-AIAnalysis.feature | AIAnalysisSteps.kt | — | ✅ |
| Estimation | 003-Estimation.feature | EstimationSteps.kt | — | ✅ |
| First Launch | 004-FirstLaunchRedirect.feature | FirstLaunchRedirectSteps.kt | UiFirstLaunchRunner.kt | ✅ |
| Dashboard | 005-Dashboard.feature | DashboardSteps.kt | UiDashboardRunner.kt | ✅ |
| Knowledge Graph | 006-KnowledgeGraph.feature | KnowledgeGraphSteps.kt | UiKnowledgeGraphRunner.kt | ✅ |
| Ticket Intelligence | 007-TicketIntelligence.feature | TicketIntelligenceSteps.kt | UiTicketIntelligenceRunner.kt | ✅ |
| Integrations | 008-Integrations.feature | IntegrationsSteps.kt | UiIntegrationsRunner.kt | ✅ |
| User Management | 009-UserManagement.feature | UserManagementSteps.kt | UiUserManagementRunner.kt | ✅ |
| Frontend-Backend | 010-FrontendBackendIntegration.feature | FrontendBackendIntegrationSteps.kt | UiFrontendBackendRunner.kt | ✅ |
| App Settings | 011-AppSettings.feature | AppSettingsSteps.kt | UiAppSettingsRunner.kt | ✅ |
| Security | 012-SecurityAndErrorHandling.feature | SecuritySteps.kt | UiSecurityRunner.kt | ✅ |
| AI Chat Sidebar | 013-AIChatSidebar.feature | AIChatSidebarSteps.kt | UiAIChatSidebarRunner.kt | ✅ |
