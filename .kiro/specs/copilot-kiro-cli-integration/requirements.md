# Requirements Document — Copilot CLI & Kiro CLI Integration

## Introduction

Ứng dụng Jira Assistant hiện hỗ trợ các AI provider sau cho việc sinh tài liệu BRD/FSD:
- **Ollama** (HTTP API, LLM local)
- **LM Studio** (HTTP API, LLM local)
- **Gemini CLI** (CLI binary, spawn process mỗi request, gửi prompt qua stdin, đọc response từ stdout)

Spec này định nghĩa việc tích hợp thêm hai AI provider dạng CLI mới:
1. **Copilot CLI** — Công cụ CLI của GitHub Copilot (`gh copilot`) có khả năng sinh text từ prompt
2. **Kiro CLI** — Công cụ CLI của Amazon Kiro có khả năng sinh text từ prompt

Cả hai provider mới đều tuân theo pattern giống `GeminiCliAgent`: spawn CLI process mỗi request, gửi prompt qua stdin, đọc response từ stdout, hỗ trợ timeout cấu hình được (240s), model parameter, test connection, provider card trên trang Integrations, và lưu config trong bảng `provider_configs`.

### Ràng buộc chính

- **Tuân theo pattern GeminiCliAgent** — Tái sử dụng cơ chế spawn process, đọc stdout/stderr bằng CompletableFuture, xử lý timeout
- **Implement AIAgent interface** — Từ shared module: `analyze(prompt, context): AIResult`
- **Tích hợp vào hệ thống hiện có** — ServerModule agent resolution, JobExecutor provider selection, Integrations page UI
- **Kotlin Multiplatform** — Enum values trong shared module, agent implementations trong server module, UI trong frontend module

## Glossary

- **Copilot_CLI_Agent**: Agent implementation cho GitHub Copilot CLI — spawn `gh copilot` process, gửi prompt qua stdin, đọc response từ stdout
- **Kiro_CLI_Agent**: Agent implementation cho Amazon Kiro CLI — spawn Kiro CLI binary, gửi prompt qua stdin, đọc response từ stdout
- **CLI_Path**: Đường dẫn tuyệt đối đến CLI binary trên hệ thống (ví dụ: `/usr/local/bin/gh`, `/usr/local/bin/kiro`) — lưu trong trường `endpoint` của ProviderConfig
- **Provider_Type**: Enum trong shared module định nghĩa loại AI provider — cần thêm `COPILOT_CLI` và `KIRO_CLI`
- **Provider_Config**: Data class chứa cấu hình provider: providerId, name, type, endpoint (CLI_Path), model, priority, status
- **Agent_Map**: Map trong ServerModule ánh xạ providerId → AIAgent instance — dùng để resolve agent cho document generation
- **Job_Executor**: Component chịu trách nhiệm chọn AI agent phù hợp dựa trên provider type và priority để thực hiện document generation
- **Provider_Card**: UI card trên trang Integrations hiển thị thông tin provider: tên, type, status, nút Configure và Test Link
- **Config_Modal**: Modal dialog cho phép user cấu hình CLI path và model name cho provider
- **Test_Connection**: Chức năng kiểm tra kết nối bằng cách chạy CLI binary với flag `--version` hoặc tương đương

## Requirements

### Requirement 1: Mở rộng ProviderType Enum

**User Story:** As a developer, I want new ProviderType enum values for Copilot CLI and Kiro CLI, so that the system can identify and route requests to the correct agent implementation.

#### Acceptance Criteria

1. THE Provider_Type enum SHALL include the value `COPILOT_CLI` representing the GitHub Copilot CLI provider
2. THE Provider_Type enum SHALL include the value `KIRO_CLI` representing the Amazon Kiro CLI provider
3. WHEN the Provider_Type enum is serialized to JSON, THE Serializer SHALL produce the string `"COPILOT_CLI"` for Copilot CLI and `"KIRO_CLI"` for Kiro CLI
4. FOR ALL valid Provider_Type enum values, serializing to JSON then deserializing back SHALL produce the same enum value (round-trip property)

### Requirement 2: CopilotCliAgent Implementation

**User Story:** As a developer, I want a CopilotCliAgent class that delegates to the GitHub Copilot CLI binary, so that the system can use Copilot for AI-powered document generation.

#### Acceptance Criteria

1. THE Copilot_CLI_Agent SHALL implement the `AIAgent` interface with method `analyze(prompt, context): AIResult`
2. WHEN `analyze` is called, THE Copilot_CLI_Agent SHALL spawn a new OS process using `ProcessBuilder` with the configured CLI_Path and model parameter
3. WHEN `analyze` is called, THE Copilot_CLI_Agent SHALL send the full prompt (including context) to the process via stdin and close the output stream
4. WHEN the CLI process completes with exit code 0 and non-blank stdout, THE Copilot_CLI_Agent SHALL return `AIResult.Success` with the trimmed stdout content
5. IF the CLI process completes with non-zero exit code or blank stdout, THEN THE Copilot_CLI_Agent SHALL return `AIResult.Failure` with the stderr content or "Empty response"
6. IF the CLI process exceeds the 240-second timeout, THEN THE Copilot_CLI_Agent SHALL destroy the process forcibly and return `AIResult.Failure` with a timeout error message
7. THE Copilot_CLI_Agent SHALL read stdout and stderr in separate threads using `CompletableFuture.supplyAsync` to avoid pipe buffer deadlock
8. WHEN running on Windows, THE Copilot_CLI_Agent SHALL prepend `cmd /c` to the command list
9. THE Copilot_CLI_Agent SHALL return agent name in format `"Copilot CLI - {model}"` from `getAgentName()`

### Requirement 3: KiroCliAgent Implementation

**User Story:** As a developer, I want a KiroCliAgent class that delegates to the Amazon Kiro CLI binary, so that the system can use Kiro for AI-powered document generation.

#### Acceptance Criteria

1. THE Kiro_CLI_Agent SHALL implement the `AIAgent` interface with method `analyze(prompt, context): AIResult`
2. WHEN `analyze` is called, THE Kiro_CLI_Agent SHALL spawn a new OS process using `ProcessBuilder` with the configured CLI_Path and model parameter
3. WHEN `analyze` is called, THE Kiro_CLI_Agent SHALL send the full prompt (including context) to the process via stdin and close the output stream
4. WHEN the CLI process completes with exit code 0 and non-blank stdout, THE Kiro_CLI_Agent SHALL return `AIResult.Success` with the trimmed stdout content
5. IF the CLI process completes with non-zero exit code or blank stdout, THEN THE Kiro_CLI_Agent SHALL return `AIResult.Failure` with the stderr content or "Empty response"
6. IF the CLI process exceeds the 240-second timeout, THEN THE Kiro_CLI_Agent SHALL destroy the process forcibly and return `AIResult.Failure` with a timeout error message
7. THE Kiro_CLI_Agent SHALL read stdout and stderr in separate threads using `CompletableFuture.supplyAsync` to avoid pipe buffer deadlock
8. WHEN running on Windows, THE Kiro_CLI_Agent SHALL prepend `cmd /c` to the command list
9. THE Kiro_CLI_Agent SHALL return agent name in format `"Kiro CLI - {model}"` from `getAgentName()`

### Requirement 4: Test Connection cho CLI Providers

**User Story:** As a user, I want to test the connection to Copilot CLI and Kiro CLI from the Integrations page, so that I can verify the CLI binary is installed and accessible before using it for document generation.

#### Acceptance Criteria

1. WHEN test connection is requested for Copilot_CLI_Agent, THE Copilot_CLI_Agent SHALL execute the CLI binary with `--version` flag and a 15-second timeout
2. WHEN the `--version` command returns exit code 0 with non-blank stdout, THE Copilot_CLI_Agent SHALL return a success message containing the version string
3. IF the `--version` command fails or times out, THEN THE Copilot_CLI_Agent SHALL return null indicating connection failure
4. WHEN test connection is requested for Kiro_CLI_Agent, THE Kiro_CLI_Agent SHALL execute the CLI binary with `--version` flag and a 15-second timeout
5. WHEN the `--version` command returns exit code 0 with non-blank stdout, THE Kiro_CLI_Agent SHALL return a success message containing the version string
6. IF the `--version` command fails or times out, THEN THE Kiro_CLI_Agent SHALL return null indicating connection failure

### Requirement 5: ServerModule Agent Resolution

**User Story:** As a developer, I want ServerModule to recognize COPILOT_CLI and KIRO_CLI provider types, so that the system can instantiate the correct agent when building the agent map.

#### Acceptance Criteria

1. WHEN `buildAgentMap` encounters a ProviderConfig with type `COPILOT_CLI`, THE Agent_Map SHALL create a `CopilotCliAgent` instance with the config's endpoint as CLI_Path and config's model (default: `"copilot"`)
2. WHEN `buildAgentMap` encounters a ProviderConfig with type `KIRO_CLI`, THE Agent_Map SHALL create a `KiroCliAgent` instance with the config's endpoint as CLI_Path and config's model (default: `"kiro"`)
3. THE Agent_Map SHALL handle `COPILOT_CLI` and `KIRO_CLI` types in the same `when` block as existing provider types without modifying behavior for other types

### Requirement 6: JobExecutor Agent Selection

**User Story:** As a developer, I want JobExecutor to include COPILOT_CLI and KIRO_CLI in its provider resolution logic, so that these providers can be selected for document generation based on priority.

#### Acceptance Criteria

1. THE Job_Executor `resolveAgent` method SHALL include `ProviderType.COPILOT_CLI` and `ProviderType.KIRO_CLI` in the list of eligible provider types for filtering
2. WHEN the highest-priority active provider has type `COPILOT_CLI`, THE Job_Executor SHALL instantiate a `CopilotCliAgent` with the provider's endpoint and model
3. WHEN the highest-priority active provider has type `KIRO_CLI`, THE Job_Executor SHALL instantiate a `KiroCliAgent` with the provider's endpoint and model
4. THE Job_Executor SHALL maintain existing fallback behavior: if no active provider is found, return default OllamaAgent

### Requirement 7: Provider Cards trên Integrations Page

**User Story:** As a user, I want to see Copilot CLI and Kiro CLI provider cards on the Integrations page, so that I can manage, configure, and monitor these providers alongside existing ones.

#### Acceptance Criteria

1. THE Integrations_Page SHALL display a Provider_Card for Copilot CLI with provider name "Copilot CLI (GitHub)", type "COPILOT_CLI", and a distinctive logo icon
2. THE Integrations_Page SHALL display a Provider_Card for Kiro CLI with provider name "Kiro CLI (Amazon)", type "KIRO_CLI", and a distinctive logo icon
3. WHEN no providers are loaded from the API, THE Integrations_Page SHALL include Copilot CLI and Kiro CLI in the default provider list with status "OFFLINE"
4. THE Provider_Cards for Copilot CLI and Kiro CLI SHALL support the same interactions as existing providers: TEST LINK button, CONFIGURE button, priority drag-and-drop, and priority arrow buttons

### Requirement 8: Configure Modal cho CLI Providers

**User Story:** As a user, I want a configuration modal for Copilot CLI and Kiro CLI, so that I can set the CLI binary path and model name for each provider.

#### Acceptance Criteria

1. WHEN the CONFIGURE button is clicked for a COPILOT_CLI provider, THE Config_Modal SHALL display fields for: CLI Path (text input, placeholder `/usr/local/bin/gh`) and Model Name (text input)
2. WHEN the CONFIGURE button is clicked for a KIRO_CLI provider, THE Config_Modal SHALL display fields for: CLI Path (text input, placeholder `/usr/local/bin/kiro`) and Model Name (text input)
3. WHEN the user saves the configuration, THE Config_Modal SHALL send the CLI Path as `endpoint` and Model Name as `model` to the `/api/integrations/{providerId}/config` API endpoint
4. IF the save request fails, THEN THE Config_Modal SHALL display an error message to the user

### Requirement 9: Provider Config Persistence

**User Story:** As a user, I want my Copilot CLI and Kiro CLI configurations to be persisted in the database, so that they survive application restarts.

#### Acceptance Criteria

1. THE Provider_Config for COPILOT_CLI SHALL be stored in the `provider_configs` table with type column value `"COPILOT_CLI"`
2. THE Provider_Config for KIRO_CLI SHALL be stored in the `provider_configs` table with type column value `"KIRO_CLI"`
3. WHEN the application starts, THE ServerModule SHALL load COPILOT_CLI and KIRO_CLI configs from the `provider_configs` table and create corresponding agent instances
4. WHEN a user updates the config for COPILOT_CLI or KIRO_CLI, THE system SHALL persist the changes to the `provider_configs` table immediately
