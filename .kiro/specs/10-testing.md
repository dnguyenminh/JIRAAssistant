# Testing — Master Requirements

## Tổng quan

Domain Testing bao gồm cấu trúc E2E test framework sử dụng Serenity BDD với Cucumber, tổ chức theo phân cấp domain để tạo Requirements Hierarchy trong Serenity report. Feature files được phân vào 9 thư mục con theo domain (core, dashboard, analysis, knowledge-graph, integrations, user-management, settings, ai-chat, scanning). Runner classes đã migrate sang JUnit 5 Platform Suite.

Bugfix đã sửa 12+ E2E test failures do `ElementNotInteractableException` trên 5 step definition files, bao gồm sửa selectors cho Cytoscape.js canvas (thay SVG selectors), thêm wait-for-visibility + JS fallback cho chat input, filter visible elements cho toggle/close buttons, và handle browser confirm dialog.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `serenity-report-feature-hierarchy` | Feature | ✅ Archived | Tổ chức feature files phân cấp, migrate JUnit 5, Serenity report hierarchy |
| `e2e-test-element-interaction-fixes` | Bugfix | ✅ Archived | Fix 12+ E2E test ElementNotInteractableException |

## Requirements tổng hợp

### Cấu trúc Feature Files

- 9 thư mục con theo domain trong `e2e-tests/src/test/resources/features/`: core, dashboard, analysis, knowledge-graph, integrations, user-management, settings, ai-chat, scanning
- 15 feature files di chuyển vào đúng thư mục con (move, không copy)
- Nội dung feature files (scenarios, steps, tags) không thay đổi sau di chuyển
- Không còn file `.feature` nào ở cấp `features/` gốc

### Runner Classes — JUnit 5 Migration

- 13 UI runner classes migrate sang JUnit 5: `@Suite` + `@IncludeEngines("cucumber")` + `@SelectClasspathResource`
- `CucumberTestRunner` dùng `@SelectClasspathResource("/features")` + filter `not @api and not @ui`
- `ApiTestRunner` dùng `@SelectClasspathResource("/features")` + filter `@api`
- Cucumber scan đệ quy tìm tất cả 15 feature files
- Compile thành công, không deprecation warnings từ CucumberWithSerenity/CucumberOptions

### Serenity Report Configuration

- `serenity.properties` chứa `serenity.requirement.types=capability,feature`
- Serenity HTML report hiển thị Requirements Hierarchy: Capability → Feature → Scenario
- 9 Capabilities tương ứng 9 thư mục con
- Tab "Features" hiển thị 15 features với test counts, scenarios, % pass
- Tổng số scenarios không thay đổi sau tổ chức lại

### Build & Compatibility

- `./gradlew :e2e-tests:compileTestKotlin` thành công
- `./gradlew :e2e-tests:aggregate` tạo report trong `target/site/serenity/`

### E2E Test Step Definitions

- **KnowledgeGraphSteps**: Sử dụng Cytoscape container + JS API thay vì SVG selectors cho canvas-based graph (hover, click, scroll, drag)
- **AIChatSidebarSteps**: Wait for visibility + scroll into view + JS fallback cho chat-input element
- **IntegrationsSteps**: Filter `isDisplayed == true` cho eye toggle và close button selectors
- **UserManagementSteps**: Filter visible toggle elements + JS fallback cho permission toggles
- **BatchScanSteps**: Handle browser `confirm()` dialog via `driver.switchTo().alert().accept()`
- 166 currently passing tests không bị regression

### Test Infrastructure

- Serenity BDD framework với Cucumber Platform Engine
- Selenium WebDriver cho UI tests
- TestHelper utilities: wait(), js(), navigateTo(), pageRendered(), waitForOverlayGone(), waitForClickable(), tryClick()
- 178 E2E UI tests tổng cộng, 166 passing trước bugfix
- API tests với `@api` tag, UI tests với `@ui` tag

### Server Unit Test Infrastructure

- **Test grouping**: Server tests chia thành 2 group qua JUnit 5 `@Tag("sequential")`:
  - `jvmTest` (parallel): Tất cả unit tests và property tests, chạy với `maxParallelForks`, `forkEvery = 50`, exclude `@Tag("sequential")`. Timeout 120s per test.
  - `jvmTestSequential`: Tests cần chạy tuần tự (Testcontainers, subprocess pipe tests, external CLI tests), `maxParallelForks = 1`. Timeout 180s per test.
  - `jvmTestAll`: Chạy parallel trước, sequential sau.
- **Sequential tests** (`@Tag("sequential")`):
  - `DataMigrationPropertyTest`, `DataMigrationIdempotencyTest`, `PgVectorStorePropertyTest` — Testcontainers PostgreSQL, cần Docker
  - `SubprocessTimeoutIntegrationTest`, `EmitStdoutInterruptibleTest` — dùng PipedOutputStream, dễ deadlock khi chạy parallel
  - `GeminiCliInteractiveTest` — spawn real Gemini CLI process, cần external binary
- **Property-based testing**: Kotest property với `PropTestConfig(iterations = 25)` là chuẩn cho server module
- **Test logging**: `testLogging { events("started", "passed", "skipped", "failed") }` để dễ identify test stuck
- **Skipped tests**: Tests phụ thuộc external services (MCP servers, Gemini CLI, Ollama) tự skip qua `assumeTrue()` khi service không available

### Domain → Feature File Mapping

| Domain | Feature Files |
|--------|--------------|
| core | login, shell |
| dashboard | dashboard |
| analysis | project-analysis |
| knowledge-graph | knowledge-graph |
| integrations | integrations, mcp-servers |
| user-management | user-management |
| settings | settings |
| ai-chat | ai-chat-sidebar |
| scanning | batch-scan, ticket-intelligence |

### Test Patterns & Best Practices

- Wait for visibility + scroll into view trước khi interact
- JS fallback khi direct Selenium interaction fails
- Filter `isDisplayed == true` cho broad CSS selectors
- Handle browser dialogs (confirm, alert) trước khi tiếp tục
- Cytoscape.js: dùng JS API (`cy.nodes()`, `cy.zoom()`, `cy.pan()`) thay vì DOM selectors
- Canvas-based graphs: không dùng SVG selectors

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `e2e-test-element-interaction-fixes` | Fix 12+ E2E failures: KnowledgeGraph SVG→Cytoscape JS API, AIChatSidebar wait+JS fallback, Integrations filter visible elements, UserManagement toggle fix, BatchScan alert handling |
