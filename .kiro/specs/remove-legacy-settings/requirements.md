# Requirements Document — Xoá Legacy Settings (JIRA_HOST, AI_PROVIDER_URL)

## Giới thiệu

Trang Settings (`/#/settings`) hiện hiển thị hai trường cấu hình legacy: **JIRA_HOST** và **AI_PROVIDER_URL**. Cả hai đều thừa vì trang Integrations đã quản lý cấu hình kết nối thực tế cho Jira (qua `JiraCredentialsService` đọc từ bảng `provider_configs`) và AI providers (qua `ProviderConfigRepository`).

Hai trường này chỉ còn được sử dụng ở:
- **Health check** (`GET /health`) — dùng `config.jiraHost` và `config.aiProviderUrl` để ping
- **JWT token** — `AuthServiceImpl` nhúng `config.jiraHost` vào claim `jira_domain`
- **Settings API** (`GET/PUT /api/settings`) — đọc/ghi hai trường này
- **Frontend Settings page** — hiển thị input fields cho hai trường

Feature này sẽ loại bỏ hoàn toàn hai trường legacy, chuyển health check và JWT sang đọc từ `provider_configs` (nguồn dữ liệu chính thức), và dọn dẹp tất cả code liên quan.

## Glossary

- **Settings_Page**: Trang cấu hình ứng dụng tại `/#/settings`, cho phép admin quản lý các thiết lập hệ thống
- **ServerConfig**: Data class `ServerConfig.kt` chứa cấu hình server, load từ DB hoặc environment variables
- **HealthRoutes**: Module route `GET /health` trả về trạng thái kết nối của Jira, AI Provider, và Knowledge Base
- **AuthService**: Service xác thực người dùng, tạo và validate JWT tokens
- **JiraCredentialsService**: Service đọc thông tin kết nối Jira (domain, email, apiToken) từ bảng `provider_configs`
- **ProviderConfigRepository**: Repository đọc/ghi cấu hình provider (Jira, Ollama, Gemini, LM Studio) từ bảng `provider_configs`
- **AppSettings**: Data class shared dùng cho request body khi PUT settings
- **AppSettingsResponse**: Data class shared dùng cho response body khi GET settings
- **SettingsRoutes**: Module route `GET/PUT /api/settings` xử lý đọc/ghi cấu hình
- **SettingsSaveHandler**: Frontend handler xử lý logic lưu settings từ form

## Requirements

### Requirement 1: Xoá trường JIRA_HOST và AI_PROVIDER_URL khỏi ServerConfig

**User Story:** Là developer, tôi muốn ServerConfig không còn chứa hai trường legacy `jiraHost` và `aiProviderUrl`, để cấu hình server chỉ phản ánh các thiết lập thực sự cần thiết.

#### Acceptance Criteria

1. THE ServerConfig SHALL NOT contain the `jiraHost` field in its data class definition
2. THE ServerConfig SHALL NOT contain the `aiProviderUrl` field in its data class definition
3. WHEN the `load()` function is called, THE ServerConfig SHALL NOT read `JIRA_HOST` from environment variables
4. WHEN the `load()` function is called, THE ServerConfig SHALL NOT read `AI_PROVIDER_URL` from environment variables
5. WHEN the `loadFromDb()` function is called, THE ServerConfig SHALL NOT read `JIRA_HOST` from SettingsRepository or environment variables
6. WHEN the `loadFromDb()` function is called, THE ServerConfig SHALL NOT read `AI_PROVIDER_URL` from SettingsRepository or environment variables

### Requirement 2: Xoá trường JIRA_HOST và AI_PROVIDER_URL khỏi shared models

**User Story:** Là developer, tôi muốn các data class shared (`AppSettings`, `AppSettingsResponse`) không còn chứa hai trường legacy, để API contract giữa frontend và backend nhất quán.

#### Acceptance Criteria

1. THE AppSettings SHALL NOT contain the `jiraHost` field
2. THE AppSettings SHALL NOT contain the `aiProviderUrl` field
3. THE AppSettingsResponse SHALL NOT contain the `jiraHost` field
4. THE AppSettingsResponse SHALL NOT contain the `aiProviderUrl` field
5. WHEN `AppSettingsResponse.fromSettings()` is called, THE AppSettingsResponse SHALL NOT include `jiraHost` or `aiProviderUrl` in the output

### Requirement 3: Cập nhật Settings API loại bỏ hai trường legacy

**User Story:** Là admin, tôi muốn API settings (`GET/PUT /api/settings`) không còn trả về hoặc chấp nhận `jiraHost` và `aiProviderUrl`, để tránh nhầm lẫn với cấu hình thực tế trên trang Integrations.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/settings`, THE SettingsRoutes SHALL return a response that does not contain `jiraHost` or `aiProviderUrl` fields
2. WHEN a PUT request is sent to `/api/settings` with `jiraHost` or `aiProviderUrl` in the body, THE SettingsRoutes SHALL ignore those fields and not persist them
3. WHEN a PUT request is sent to `/api/settings` with valid `jwtSecret` or `encryptionKey`, THE SettingsRoutes SHALL persist those fields successfully

### Requirement 4: Chuyển Health Check sang đọc từ provider_configs

**User Story:** Là admin, tôi muốn endpoint `/health` kiểm tra kết nối Jira và AI Provider dựa trên cấu hình thực tế từ trang Integrations (bảng `provider_configs`), để kết quả health check phản ánh đúng trạng thái hệ thống.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/health`, THE HealthRoutes SHALL read the Jira domain from JiraCredentialsService instead of ServerConfig.jiraHost
2. WHEN a GET request is sent to `/health` and Jira is not configured in provider_configs, THE HealthRoutes SHALL return `jira.status` as `"down"` with message `"Not configured"` instead of attempting a connection
3. WHEN a GET request is sent to `/health`, THE HealthRoutes SHALL read the AI provider URL from ProviderConfigRepository (first active AI provider: OLLAMA, GEMINI, or LM_STUDIO) instead of ServerConfig.aiProviderUrl
4. WHEN a GET request is sent to `/health` and no AI provider is configured in provider_configs, THE HealthRoutes SHALL return `aiProvider.status` as `"down"` with message `"Not configured"` instead of attempting a connection
5. WHEN Jira credentials are configured and the Jira server responds successfully, THE HealthRoutes SHALL return `jira.status` as `"up"`
6. WHEN an AI provider is configured and the provider endpoint responds successfully, THE HealthRoutes SHALL return `aiProvider.status` as `"up"`

### Requirement 5: Chuyển JWT token sang đọc Jira domain từ JiraCredentialsService

**User Story:** Là developer, tôi muốn JWT token chứa `jira_domain` claim được lấy từ `JiraCredentialsService` (nguồn dữ liệu chính thức), thay vì từ `ServerConfig.jiraHost` (legacy).

#### Acceptance Criteria

1. WHEN AuthService authenticates a user, THE AuthService SHALL read the Jira domain from JiraCredentialsService.getJiraCredentials()?.domain for the `jira_domain` claim
2. WHEN Jira is not configured in provider_configs, THE AuthService SHALL set the `jira_domain` claim to an empty string
3. WHEN a JWT token is generated, THE AuthService SHALL NOT reference ServerConfig.jiraHost

### Requirement 6: Xoá trường JIRA_HOST và AI_PROVIDER_URL khỏi Settings Page UI

**User Story:** Là admin, tôi muốn trang Settings không còn hiển thị hai trường input `JIRA_HOST` và `AI_PROVIDER_URL`, để tránh nhầm lẫn với cấu hình thực tế trên trang Integrations.

#### Acceptance Criteria

1. THE Settings_Page HTML template SHALL NOT contain an input field with id `input-jira-host`
2. THE Settings_Page HTML template SHALL NOT contain an input field with id `input-ai-provider-url`
3. THE Settings_Page SHALL NOT bind or read values from `input-jira-host` or `input-ai-provider-url` DOM elements
4. WHEN settings are loaded from the API, THE Settings_Page SHALL NOT attempt to populate `jiraHost` or `aiProviderUrl` fields in the form
5. THE SettingsSaveHandler SHALL NOT gather, validate, or include `jiraHost` or `aiProviderUrl` in the save payload

### Requirement 7: Dọn dẹp environment files

**User Story:** Là developer, tôi muốn các file `.env` và `.env.example` không còn chứa biến `JIRA_HOST` (trong phần Server/Settings), để tránh nhầm lẫn rằng biến này vẫn được sử dụng bởi Settings page.

#### Acceptance Criteria

1. THE `.env.example` file SHALL NOT contain a `JIRA_HOST` entry in the Server configuration section
2. THE `.env.example` file SHALL retain `JIRA_HOST` in the Jira Integration section only if it is used by other components outside of Settings (note: `.env.example` currently has `JIRA_HOST` in the Jira Integration section which may be used for initial setup — this should be reviewed)
3. IF `AI_PROVIDER_URL` exists in `.env.example`, THEN THE `.env.example` file SHALL NOT contain that entry
4. THE `.env` file SHALL NOT contain `AI_PROVIDER_URL` if it was previously present as a Settings-related variable
