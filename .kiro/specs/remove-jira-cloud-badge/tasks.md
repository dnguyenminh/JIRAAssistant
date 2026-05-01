# Tasks — Loại bỏ Jira Cloud Services Badge khỏi Integrations Page

## Task 1: Xoá Jira entry khỏi Frontend Provider Defaults

- [x] 1.1 Xoá Jira entry khỏi `defaultProviders()` trong `IntegrationsPage.kt`
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/IntegrationsPage.kt`
  - Xoá dòng `ProviderInfo("jira", "Jira Cloud Services", "JIRA", "STANDBY", priority = 0)`
  - Cập nhật priority indices: ollama=0, gemini=1, lm_studio=2, gemini_cli=3, copilot_cli=4, kiro_cli=5, embedding=10

## Task 2: Xoá Jira entry khỏi Backend Provider Defaults và thêm filter

- [x] 2.1 Xoá Jira entry khỏi `defaults` list trong `IntegrationRoutes.kt`
  - File: `server/mcp/src/jvmMain/kotlin/com/assistant/server/routes/IntegrationRoutes.kt`
  - Xoá dòng `ProviderConfig(providerId = "jira", name = "Jira Cloud Services", type = ProviderType.JIRA, ...)`
  - Cập nhật priority indices cho remaining providers: ollama=0, gemini=1, lm_studio=2, gemini_cli=3, copilot_cli=4, kiro_cli=5, embedding=10
- [x] 2.2 Thêm filter loại bỏ ProviderType.JIRA khỏi GET /api/integrations response
  - File: `server/mcp/src/jvmMain/kotlin/com/assistant/server/routes/IntegrationRoutes.kt`
  - Thêm `.filter { it.type != ProviderType.JIRA }` vào `result` trước `call.respond`
  - Đảm bảo Jira entries từ DB cũng bị filter out

## Task 3: Xoá Jira Config Modal khỏi HTML template

- [x] 3.1 Xoá toàn bộ Jira Config Modal section khỏi `integrations.html`
  - File: `frontend/src/jsMain/resources/templates/integrations.html`
  - Xoá từ `<!-- ── Jira Config Modal ──` đến closing `</div>` của `jira-config-modal`

## Task 4: Xoá JIRA special-case logic khỏi IntegrationsCardBuilder

- [x] 4.1 Xoá JIRA special-case trong `buildCardHtml()` và `providerLogo()` và `bindCardEvents()`
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/IntegrationsCardBuilder.kt`
  - `buildCardHtml()`: thay `val configBtnClass = if (provider.type == "JIRA") "integ-btn-jira-configure" else "integ-btn-configure"` bằng `val configBtnClass = "integ-btn-configure"`
  - `providerLogo()`: xoá entry `"JIRA" -> "🔷"` khỏi when block
  - `bindCardEvents()`: xoá toàn bộ block `card.querySelector(".integ-btn-jira-configure")?.addEventListener(...)`

## Task 5: Xoá file IntegrationsJiraModal.kt

- [x] 5.1 Xoá file `IntegrationsJiraModal.kt`
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/IntegrationsJiraModal.kt`
  - Xoá toàn bộ file vì Jira Config Modal không còn cần thiết

## Task 6: Xoá file JiraModalBugConditionTest.kt

- [x] 6.1 Xoá file `JiraModalBugConditionTest.kt`
  - File: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/integrations/JiraModalBugConditionTest.kt`
  - Xoá toàn bộ file vì test cho modal đã bị xoá

## Task 7: Cập nhật E2E Tests

- [x] 7.1 Cập nhật `008-Integrations.feature` loại bỏ Jira-related scenarios
  - File: `e2e-tests/src/test/resources/features/integrations/008-Integrations.feature`
  - Cập nhật scenario "Provider cards are displayed in a responsive grid": đổi card count từ 5 thành 7, xoá "Jira Cloud Services" khỏi danh sách cards
  - Xoá tất cả Jira-specific scenarios: "Jira CONFIGURE button opens dedicated config modal", "Jira API token field has visibility toggle", "Jira SAVE & TEST validates required fields", "Jira SAVE & TEST with valid credentials succeeds", "Jira SAVE & TEST with invalid credentials shows error", "Jira credentials persist across page navigation", "Jira config modal closes on overlay click", "Jira config modal closes on close button click"
  - Cập nhật error handling scenarios: đổi "5 default provider cards" thành "7 default provider cards"

## Task 8: Cập nhật Documentation

- [x] 8.1 Cập nhật documentation files phản ánh việc loại bỏ Jira Cloud Services card
  - Tìm và cập nhật `TestScenarios.md`: xoá Jira card references, cập nhật card count
  - Tìm và cập nhật `SRS.md`: xoá Jira Cloud Services khỏi provider card list
  - Tìm và cập nhật `BRD.md`: cập nhật provider card count
  - Tìm và cập nhật `UserGuide_StepByStep_VN.md`: xoá references tới Jira Cloud Services card trên Integrations page

## Task 9: Compile và verify

- [x] 9.1 Compile tất cả modules và verify không có errors
  - Chạy `./gradlew :frontend:compileKotlinJs` — verify frontend compiles
  - Chạy `./gradlew :server:compileKotlinJvm` — verify backend compiles
  - Kiểm tra không còn import references tới `IntegrationsJiraModal` trong bất kỳ file nào
