# Requirements Document

## Introduction

Dự án đã tích hợp Jira MCP (Model Context Protocol) thông qua Atlassian Rovo MCP Server, cung cấp khả năng truy cập Jira đầy đủ qua MCP tools. Provider card "Jira Cloud Services" trên trang Integrations hiện tại sử dụng REST API riêng (domain + email + API token) để kết nối Jira — đây là cơ chế cũ (legacy) và gây nhầm lẫn cho người dùng khi cả hai cách kết nối Jira cùng tồn tại.

Yêu cầu này loại bỏ provider card "Jira Cloud Services" khỏi UI trang Integrations, đồng thời giữ nguyên toàn bộ backend Jira infrastructure (JiraCredentialsService, JiraRestClient, IntegrationRoutes cho Jira) vì chúng vẫn được sử dụng bởi các module analysis, batch scan, docgen, và project routes.

## Glossary

- **Integrations_Page**: Trang quản lý AI providers, MCP servers, và pipeline settings tại route `#integrations`
- **Provider_Card**: Thẻ UI hiển thị thông tin một provider (logo, tên, status badge, nút TEST LINK và CONFIGURE) trong grid trên Integrations_Page
- **Jira_Cloud_Services_Card**: Provider_Card có providerId "jira", tên "Jira Cloud Services", type "JIRA" — hiển thị trên Integrations_Page
- **Jira_Config_Modal**: Modal popup chứa form cấu hình Jira (domain URL, email, API token) với nút TEST CONNECTION và SAVE
- **MCP_Server**: Kết nối Jira thông qua Model Context Protocol (Atlassian Rovo), thay thế chức năng của Jira_Cloud_Services_Card
- **Provider_Defaults**: Danh sách provider mặc định được hardcode trong frontend (IntegrationsPage.kt) và backend (IntegrationRoutes.kt)
- **JiraCredentialsService**: Service backend đọc Jira credentials từ database — vẫn được sử dụng bởi analysis, batch scan, docgen, project routes
- **IntegrationRoutes**: Backend routes quản lý provider configs tại `/api/integrations`

## Requirements

### Requirement 1: Loại bỏ Jira Cloud Services Card khỏi Integrations Page

**User Story:** As a user, I want the Jira Cloud Services card removed from the Integrations page, so that I am not confused by having two Jira connection methods (legacy REST API card vs MCP Server).

#### Acceptance Criteria

1. WHEN the Integrations_Page renders, THE Integrations_Page SHALL NOT display a Provider_Card with providerId "jira" or type "JIRA"
2. WHEN the Integrations_Page loads provider data from the backend, THE Integrations_Page SHALL filter out any provider with providerId "jira" before rendering Provider_Cards
3. THE Provider_Defaults list in IntegrationsPage.kt SHALL NOT contain an entry with providerId "jira"

### Requirement 2: Loại bỏ Jira Config Modal khỏi UI

**User Story:** As a user, I want the Jira configuration modal removed, so that there is no orphaned UI element for the legacy Jira connection.

#### Acceptance Criteria

1. THE Integrations_Page SHALL NOT contain the Jira_Config_Modal HTML template (element id "jira-config-modal")
2. THE Integrations_Page SHALL NOT bind any click events to open the Jira_Config_Modal
3. WHEN a Provider_Card CONFIGURE button is clicked, THE IntegrationsCardBuilder SHALL NOT reference the "integ-btn-jira-configure" CSS class

### Requirement 3: Cập nhật Backend Provider Defaults

**User Story:** As a developer, I want the backend provider defaults updated to exclude Jira, so that the API response does not include the legacy Jira provider card data.

#### Acceptance Criteria

1. THE Provider_Defaults list in IntegrationRoutes.kt SHALL NOT contain an entry with providerId "jira" and type ProviderType.JIRA
2. WHEN the GET `/api/integrations` endpoint returns provider data, THE IntegrationRoutes SHALL filter out any provider with type ProviderType.JIRA from the response
3. THE IntegrationRoutes SHALL retain the Jira-specific routes (`/jira/status`, `/jira/config`, `/{providerId}/test` for jira) to support existing backend consumers (analysis, batch scan, docgen, project routes)

### Requirement 4: Giữ nguyên Backend Jira Infrastructure

**User Story:** As a developer, I want the backend Jira services preserved, so that analysis, batch scan, docgen, and project features continue to function correctly.

#### Acceptance Criteria

1. THE JiraCredentialsService SHALL remain fully functional and accessible via dependency injection
2. THE JiraRestClient SHALL remain fully functional for all existing consumers (AnalysisRoutes, TicketDetailRoutes, ProjectRoutes, BatchScanEngineFactory, DocgenModule)
3. THE IntegrationRoutes SHALL retain the PUT `/api/integrations/jira/config` endpoint for programmatic Jira credential management
4. THE IntegrationRoutes SHALL retain the POST `/api/integrations/jira/test` endpoint for connection testing by backend consumers
5. THE IntegrationRoutes SHALL retain the GET `/api/integrations/jira/status` endpoint for health checks and first-launch detection

### Requirement 5: Cập nhật Provider Card Count và Grid Layout

**User Story:** As a user, I want the Integrations page grid to display correctly after removing the Jira card, so that the remaining provider cards are properly laid out.

#### Acceptance Criteria

1. WHEN the Integrations_Page renders, THE Provider_Card grid SHALL display the remaining providers (Ollama, Gemini API, LM Studio, Gemini CLI, Copilot CLI, Kiro CLI, Embedding) without visual gaps
2. THE Provider_Card priority indices SHALL be recalculated to start from 0 after removing the Jira entry from Provider_Defaults

### Requirement 6: Cập nhật E2E Tests và Documentation

**User Story:** As a QA engineer, I want the E2E tests and documentation updated to reflect the removal of the Jira Cloud Services card, so that tests pass and docs are accurate.

#### Acceptance Criteria

1. THE E2E test scenarios in `008-Integrations.feature` SHALL be updated to expect the correct number of provider cards (excluding Jira Cloud Services)
2. THE E2E test scenario for "Jira CONFIGURE button opens dedicated config modal" SHALL be removed or updated
3. THE test documentation in `TestScenarios.md` SHALL be updated to reflect the new provider card list
4. THE SRS document (SRS.md) SHALL be updated to list the correct provider cards (excluding Jira Cloud Services)
5. THE BRD document (BRD.md) SHALL be updated to reflect the new provider card count
6. THE User Guide (UserGuide_StepByStep_VN.md) SHALL be updated to remove references to Jira Cloud Services as a provider card on the Integrations page

### Requirement 7: Cleanup Frontend Jira-Specific Code

**User Story:** As a developer, I want the frontend Jira-specific code cleaned up, so that there is no dead code related to the removed Jira card.

#### Acceptance Criteria

1. THE IntegrationsJiraModal.kt file SHALL be removed since the Jira_Config_Modal is no longer needed
2. THE IntegrationsCardBuilder SHALL NOT contain special-case logic for JIRA provider type (e.g., "integ-btn-jira-configure" class assignment)
3. THE JiraModalBugConditionTest.kt test file SHALL be removed or updated to reflect the removal of the Jira modal
4. IF the IntegrationsTestLink contains Jira-specific test logic, THEN THE IntegrationsTestLink SHALL remove that Jira-specific code
