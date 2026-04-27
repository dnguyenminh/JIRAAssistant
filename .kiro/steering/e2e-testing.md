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
│           └── {capability}/           # Thư mục con theo domain (Serenity Capability)
│               └── {NNN}-{Feature}.feature  # Feature file (đánh số thứ tự)
```

## Serenity Requirements Hierarchy — Tổ chức Feature Files

### Quy tắc BẮT BUỘC

Feature files **PHẢI** được đặt trong thư mục con (capability) theo domain, **KHÔNG** đặt trực tiếp ở `features/` gốc. Serenity BDD sử dụng cấu trúc thư mục để tạo Requirements Hierarchy trong report:

- **Cấp 1 (Capability)**: Thư mục con trực tiếp dưới `features/` → hiển thị như "Capability" trong report
- **Cấp 2 (Feature)**: File `.feature` trong thư mục con → hiển thị như "Feature" trong report
- **Cấp 3 (Scenario)**: Scenario trong file → hiển thị như test case

### Bảng ánh xạ Capability → Feature Files

| Capability (thư mục) | Feature Files | Mô tả Domain |
|---|---|---|
| `core/` | Initialization, SecurityAndErrorHandling | Khởi tạo hệ thống, bảo mật |
| `dashboard/` | Dashboard | Trang chủ dashboard |
| `analysis/` | AIAnalysis, Estimation, TicketIntelligence | Phân tích AI, ước lượng |
| `knowledge-graph/` | KnowledgeGraph | Đồ thị tri thức |
| `integrations/` | Integrations, McpServers | Tích hợp bên ngoài |
| `user-management/` | FirstLaunchRedirect, UserManagement | Quản lý người dùng |
| `settings/` | AppSettings | Cài đặt ứng dụng |
| `ai-chat/` | AIChatSidebar | Chat AI sidebar |
| `scanning/` | FrontendBackendIntegration, BatchScan | Quét và tích hợp FE-BE |

### Khi tạo feature file mới

1. Xác định domain/capability phù hợp từ bảng trên
2. Nếu không khớp capability nào → tạo thư mục con mới (kebab-case)
3. Đặt file vào: `features/{capability}/{NNN}-{Feature}.feature`
4. Cập nhật runner path: `features/{capability}/{NNN}-{Feature}.feature`
5. Cập nhật bảng coverage ở cuối file steering này

### Cấu hình Serenity Properties

File `serenity.properties` PHẢI chứa:
```properties
serenity.features.directory=src/test/resources/features
serenity.requirement.types=capability,feature
```

## Quy tắc tạo API E2E Tests

- File: `e2e-tests/src/test/kotlin/com/assistant/e2e/api/{Feature}ApiTest.kt`
- Extend `ApiTestBase()`
- Sử dụng `@Tag("api")` (inherited), `@TestMethodOrder(OrderAnnotation::class)`
- Mỗi test dùng `runBlocking { ... }`, Ktor client, raw JSON `setBody("""...""")`
- Kiểm tra: HTTP status codes, response body fields, RBAC (admin/reader/neural_architect), no-JWT → 401

## Quy tắc tạo UI E2E Tests

### 1. Feature file (`.feature`)

- Đặt tại: `e2e-tests/src/test/resources/features/{capability}/{NNN}-{Feature}.feature`
- Chọn `{capability}` từ bảng ánh xạ Capability ở trên (hoặc tạo mới nếu domain mới)
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
    features = ["src/test/resources/features/{capability}/{NNN}-{Feature}.feature"],
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
  - [ ] `{capability}/{NNN}-{Feature}.feature` — Cucumber scenarios (đặt trong thư mục capability phù hợp)
  - [ ] `{Feature}Steps.kt` — Step definitions
  - [ ] `Ui{Feature}Runner.kt` — Serenity runner (path trỏ đến `features/{capability}/...`)

## CSS Selectors Convention

Khi viết UI E2E tests, sử dụng CSS selectors theo thứ tự ưu tiên:
1. `By.id("element-id")` — ưu tiên nhất
2. `By.cssSelector(".class-name")` — khi không có id
3. `By.xpath("//...")` — fallback cuối cùng

## Existing Features & UI E2E Test Coverage

| Capability | Feature | Feature File | Steps | Runner | Status |
|---|---------|-------------|-------|--------|--------|
| core | Initialization | core/001-Initialization.feature | InitializationSteps.kt | UiInitializationRunner.kt | ✅ |
| analysis | AI Analysis | analysis/002-AIAnalysis.feature | AIAnalysisSteps.kt | — | ✅ |
| analysis | Estimation | analysis/003-Estimation.feature | EstimationSteps.kt | — | ✅ |
| user-management | First Launch | user-management/004-FirstLaunchRedirect.feature | FirstLaunchRedirectSteps.kt | UiFirstLaunchRunner.kt | ✅ |
| dashboard | Dashboard | dashboard/005-Dashboard.feature | DashboardSteps.kt | UiDashboardRunner.kt | ✅ |
| knowledge-graph | Knowledge Graph | knowledge-graph/006-KnowledgeGraph.feature | KnowledgeGraphSteps.kt | UiKnowledgeGraphRunner.kt | ✅ |
| analysis | Ticket Intelligence | analysis/007-TicketIntelligence.feature | TicketIntelligenceSteps.kt | UiTicketIntelligenceRunner.kt | ✅ |
| integrations | Integrations | integrations/008-Integrations.feature | IntegrationsSteps.kt | UiIntegrationsRunner.kt | ✅ |
| user-management | User Management | user-management/009-UserManagement.feature | UserManagementSteps.kt | UiUserManagementRunner.kt | ✅ |
| scanning | Frontend-Backend | scanning/010-FrontendBackendIntegration.feature | FrontendBackendIntegrationSteps.kt | UiFrontendBackendRunner.kt | ✅ |
| settings | App Settings | settings/011-AppSettings.feature | AppSettingsSteps.kt | UiAppSettingsRunner.kt | ✅ |
| core | Security | core/012-SecurityAndErrorHandling.feature | SecuritySteps.kt | UiSecurityRunner.kt | ✅ |
| ai-chat | AI Chat Sidebar | ai-chat/013-AIChatSidebar.feature | AIChatSidebarSteps.kt | UiAIChatSidebarRunner.kt | ✅ |
| scanning | Batch Scan | scanning/014-BatchScan.feature | BatchScanSteps.kt | UiBatchScanRunner.kt | ✅ |
| integrations | MCP Servers | integrations/015-McpServers.feature | McpServersSteps.kt | UiMcpServersRunner.kt | ✅ |
