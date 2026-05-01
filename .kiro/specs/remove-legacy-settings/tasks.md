# Tasks — Xoá Legacy Settings (JIRA_HOST, AI_PROVIDER_URL)

## Task 1: Xoá jiraHost và aiProviderUrl khỏi ServerConfig

- [x] 1.1 Xoá fields `jiraHost` và `aiProviderUrl` khỏi `ServerConfig` data class
  - File: `server/core/src/jvmMain/kotlin/com/assistant/server/config/ServerConfig.kt`
  - Xoá 2 fields khỏi constructor
  - Cập nhật `load()`: xoá đọc `JIRA_HOST` và `AI_PROVIDER_URL` từ env
  - Cập nhật `loadFromDb()`: xoá đọc `JIRA_HOST` và `AI_PROVIDER_URL` từ SettingsRepository và env

## Task 2: Xoá jiraHost và aiProviderUrl khỏi shared models

- [x] 2.1 Xoá fields `jiraHost` và `aiProviderUrl` khỏi `AppSettings` và `AppSettingsResponse`
  - File: `shared/src/commonMain/kotlin/com/assistant/settings/SettingsRepository.kt`
  - Xoá `jiraHost` và `aiProviderUrl` khỏi `AppSettings`
  - Xoá `jiraHost` và `aiProviderUrl` khỏi `AppSettingsResponse`
  - Cập nhật `AppSettingsResponse.fromSettings()` loại bỏ 2 fields

## Task 3: Cập nhật SettingsRoutes loại bỏ legacy fields

- [x] 3.1 Cập nhật `handleGetSettings` và `handlePutSettings` trong SettingsRoutes
  - File: `server/core/src/jvmMain/kotlin/com/assistant/server/routes/SettingsRoutes.kt`
  - `handleGetSettings`: xoá đọc `JIRA_HOST`/`AI_PROVIDER_URL` từ settingsRepo và serverConfig
  - `handlePutSettings`: xoá validate/persist `jiraHost`/`aiProviderUrl`
  - Xoá hàm `isValidUrl()` nếu không còn dùng ở nơi khác

## Task 4: Chuyển HealthRoutes sang đọc từ provider_configs

- [x] 4.1 Refactor HealthRoutes inject JiraCredentialsService và ProviderConfigRepository
  - File: `server/core/src/jvmMain/kotlin/com/assistant/server/routes/HealthRoutes.kt`
  - Thay `val config by inject<ServerConfig>()` bằng `val jiraCredentialsService by inject<JiraCredentialsService>()` và `val providerConfigRepo by inject<ProviderConfigRepository>()`
  - Cập nhật `checkJira()`: nhận `JiraCredentialsService`, gọi `getJiraCredentials()`, nếu null trả "Not configured", nếu có thì ping `credentials.domain`
  - Cập nhật `checkAiProvider()`: nhận `ProviderConfigRepository`, tìm first active AI provider (OLLAMA → GEMINI → LM_STUDIO), nếu không có trả "Not configured", nếu có thì ping endpoint

## Task 5: Chuyển AuthServiceImpl sang đọc Jira domain từ JiraCredentialsService

- [x] 5.1 Thêm JiraCredentialsService dependency vào AuthServiceImpl
  - File: `server/core/src/jvmMain/kotlin/com/assistant/server/auth/AuthServiceImpl.kt`
  - Thêm `private val jiraCredentialsService: JiraCredentialsService` vào constructor
  - Trong `authenticate()`: thay `config.jiraHost` bằng `jiraCredentialsService.getJiraCredentials()?.domain ?: ""`
  - Xoá mọi reference tới `config.jiraHost`
- [x] 5.2 Cập nhật DI module CoreModule
  - File: `server/core/src/jvmMain/kotlin/com/assistant/server/di/CoreModule.kt`
  - Cập nhật `single<AuthService> { AuthServiceImpl(get(), get(), get()) }` để inject thêm JiraCredentialsService

## Task 6: Xoá legacy fields khỏi Settings Page UI

- [x] 6.1 Xoá input fields JIRA_HOST và AI_PROVIDER_URL khỏi settings.html template
  - File: `frontend/src/jsMain/resources/templates/settings.html`
  - Xoá `<div class="settings-field">` chứa `input-jira-host`
  - Xoá `<div class="settings-field">` chứa `input-ai-provider-url`
- [x] 6.2 Cập nhật SettingsPage.kt loại bỏ binding legacy fields
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/SettingsPage.kt`
  - Xoá `setInput("input-jira-host", ...)` và `setInput("input-ai-provider-url", ...)` trong `bindSettingsToForm()`
- [x] 6.3 Cập nhật SettingsSaveHandler.kt loại bỏ legacy fields
  - File: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/settings/SettingsSaveHandler.kt`
  - Xoá `jiraHost` và `aiProviderUrl` khỏi `FormValues` data class
  - Cập nhật `gatherFormValues()`: xoá đọc `input-jira-host` và `input-ai-provider-url`
  - Cập nhật `validate()`: xoá validation URL cho 2 fields
  - Cập nhật `buildPayload()`: xoá `jiraHost` và `aiProviderUrl` khỏi `AppSettings` constructor

## Task 7: Dọn dẹp environment files

- [x] 7.1 Cập nhật `.env.example` và `.env`
  - `.env.example`: giữ `JIRA_HOST` trong section "Jira Integration" (dùng cho initial setup), xoá nếu có trong section "Server"
  - `.env.example`: xoá `AI_PROVIDER_URL` nếu tồn tại
  - `.env`: xoá `AI_PROVIDER_URL` nếu tồn tại (note: `.env` hiện không có `AI_PROVIDER_URL`)

## Task 8: Fix compile errors và verify

- [x] 8.1 Fix tất cả compile errors phát sinh từ việc xoá fields
  - Chạy `./gradlew :shared:compileKotlinJvm` và fix errors
  - Chạy `./gradlew :server:compileKotlinJvm` và fix errors
  - Chạy `./gradlew :frontend:compileKotlinJs` và fix errors
  - Kiểm tra và fix bất kỳ file nào reference `config.jiraHost`, `config.aiProviderUrl`, `settings.jiraHost`, `settings.aiProviderUrl`
