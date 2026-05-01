# Integrations — Tasks

Status: ✅ All completed

## From tasks-05-refactor-onboarding.md — Jira Config Tasks

### Task 47: Backend — Jira Credentials trong provider_configs
- [x] 47.1 Thêm endpoint `PUT /api/integrations/jira/config` — validate + save encrypted credentials
    - _Requirements: [auth-and-rbac] AC 1.1, [auth-and-rbac] AC 1.4, AC 6.6, AC 6.14, AC 6.17_
- [x] 47.2 Thêm endpoint `GET /api/integrations/jira/status` (public)
    - _Requirements: [auth-and-rbac] AC 1.1, [auth-and-rbac] AC 1.8_
- [x] 47.3 Tạo/cập nhật `JiraCredentialsService` — đọc từ provider_configs, decrypt apiToken
    - _Requirements: [auth-and-rbac] AC 1.6, [auth-and-rbac] AC 1.9_
- [x] 47.4 Cập nhật JiraClient Koin registration — singleton đọc credentials từ DB
    - _Requirements: [auth-and-rbac] AC 1.9, AC 6.17_
- [x] 47.5 Cập nhật `ProjectRoutes.kt` — dùng inject JiraClient từ Koin
    - _Requirements: [auth-and-rbac] AC 1.9_
- [x] 47.6 Cập nhật `GET /api/settings/status` — kiểm tra Jira configured từ provider_configs
    - _Requirements: [auth-and-rbac] AC 1.1_

### Task 48: Frontend — Integrations Page thêm Jira Config
- [x] 48.1 Cập nhật `integrations.html` — Jira config modal (domain, email, apiToken, SAVE & TEST)
    - _Requirements: AC 6.6, [design-system-ux] AC 17.1_
- [x] 48.2 Cập nhật `IntegrationsPage.kt` — Jira provider card logic
    - _Requirements: AC 6.6, AC 6.14, AC 6.15, AC 6.16_

---

## From tasks-06-post-refactor.md — Integrations UX

### Task 54: Integrations UX Improvements
- [x] 54.1 Config modal: TEST CONNECTION + SAVE riêng biệt
    - _Requirements: AC 6.10–6.14_
- [x] 54.2 Ollama Model Name: dropdown từ API
    - _Requirements: AC 6.10, AC 6.18_
- [x] 54.3 MAX TOKENS: slider (256–32768)
    - _Requirements: AC 6.10–6.12_
- [x] 54.4 Status persistence: ACTIVE/OFFLINE trong DB
    - _Requirements: AC 6.3, AC 6.4, AC 6.14_
- [x] 54.5 5 default providers merged với DB values
    - _Requirements: AC 6.1_
- [x] 54.6 Test connection với config chưa lưu
    - _Requirements: AC 6.14, AC 6.19_
