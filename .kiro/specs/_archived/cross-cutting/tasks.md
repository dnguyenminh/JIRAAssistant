# Cross-cutting Concerns — Tasks

Status: ✅ All completed

---

# App Settings — Tasks 37–46

Cấu hình Server qua UI (thay thế Environment Variables).

## Task 37: SQLDelight Schema — Bảng `app_settings`
- [x] 37.1 Cập nhật `KnowledgeBase.sq`: thêm bảng `app_settings` (key-value), queries: getAllSettings, findSettingByKey, upsertSetting, deleteSetting
    - _Requirements: [backend-core] AC 9.1, [backend-core] AC 8.1_

## Task 38: SettingsRepository — Shared Module
- [x] 38.1 Tạo `SettingsRepository.kt` interface, `AppSettings`, `AppSettingsResponse` data classes
    - _Requirements: [backend-core] AC 8.1, AC 16.1_
- [x] 38.2 Tạo `SettingsRepositoryImpl.kt` — JVM triển khai SQLDelight
    - _Requirements: [backend-core] AC 9.1, [backend-core] AC 8.1_

## Task 39: Cập nhật ServerConfig — Load từ DB, fallback env vars
- [x] 39.1 Cập nhật `ServerConfig.kt`: `loadFromDb()` đọc settings từ DB, fallback env vars
    - _Requirements: [backend-core] AC 8.1, [backend-core] AC 8.3_
- [x] 39.2 Cập nhật `ServerModule.kt`: đăng ký SettingsRepository singleton
    - _Requirements: [backend-core] AC 8.1_

## Task 40: Settings API Routes — Backend
- [x] 40.1 Tạo `SettingsRoutes.kt`: GET /api/settings (masked), PUT /api/settings (validate + save)
    - _Requirements: [backend-core] AC 8.1, [backend-core] AC 8.2, [auth-and-rbac] AC 11.1, AC 16.1_
- [x] 40.2 Thêm `MANAGE_SETTINGS` vào Permission enum
    - _Requirements: [auth-and-rbac] AC 11.1_
- [x] 40.3 Thêm `MANAGE_SETTINGS` vào PermissionMatrix cho ADMINISTRATOR
    - _Requirements: [auth-and-rbac] AC 11.1_
- [x] 40.4 Cập nhật `Routing.kt`: thêm settingsRoutes()
    - _Requirements: [backend-core] AC 8.1_

## Task 41: Checkpoint — Backend Settings API
- [x] 41. Schema, GET/PUT endpoints, RBAC

## Task 42: Frontend Settings Page — HTML Template & CSS
- [x] 42.1 Tạo `settings.html` — 6 fields, SAVE button, progress bar
    - _Requirements: [design-system-ux] AC 17.1, [design-system-ux] AC 17.3_
- [x] 42.2 Cập nhật `components.css` — settings-form, field-badge, readonly, masked
    - _Requirements: [design-system-ux] AC 17.1, [design-system-ux] AC 17.3_

## Task 43: Frontend Settings Page — Kotlin/JS Controller
- [x] 43.1 Tạo `SettingsPage.kt` — RBAC check, loadSettings, saveSettings, sensitive field toggle
    - _Requirements: [backend-core] AC 8.1, [backend-core] AC 8.2, [auth-and-rbac] AC 11.1, AC 16.1, [design-system-ux] AC 17.1_

## Task 44: Cập nhật Router & Navbar — Thêm Settings
- [x] 44.1 Cập nhật `App.kt`: đăng ký route "settings"
    - _Requirements: AC 13.1_
- [x] 44.2 Cập nhật `Navbar.kt`: thêm "⚙️ App Settings" (Administrator only)
    - _Requirements: AC 13.1, [auth-and-rbac] AC 11.1_
- [x] 44.3 Cập nhật `Router.kt`: thêm "settings" vào Shell routes
    - _Requirements: AC 13.1_

## Task 45: First-Launch Redirect Logic
- [x] 45.1 Thêm endpoint `GET /api/settings/status` (public)
    - _Requirements: [backend-core] AC 8.1_
- [x] 45.2 Cập nhật `App.kt`: first-launch check → redirect settings nếu chưa configured
    - _Requirements: [backend-core] AC 8.1, AC 13.1_

## Task 46: Checkpoint — App Settings Feature hoàn chỉnh
- [x] 46. Settings page, masked fields, readonly, RBAC, first-launch redirect, DB override

## Task 46a: UI E2E Tests — App Settings (Cucumber + Serenity)
- [x] 46a.1 Tạo `011-AppSettings.feature` + `AppSettingsSteps.kt` + `UiAppSettingsRunner.kt`
    - Settings page hiển thị 6 fields, SAVE button, RBAC (Administrator only)
    - Masked fields cho sensitive data, readonly cho ENV-only fields
    - First-launch redirect khi chưa configured
    - _Requirements: [backend-core] AC 8.1, [backend-core] AC 8.2, [auth-and-rbac] AC 11.1, AC 16.1_
- [x] 46a.2 Tạo `004-FirstLaunchRedirect.feature` + `FirstLaunchRedirectSteps.kt` + `UiFirstLaunchRunner.kt`
    - First-launch redirect logic, Jira not configured → redirect Integrations
    - _Requirements: [auth-and-rbac] AC 1.1, [backend-core] AC 8.1_

---

# Post-Refactor — Task 57 (Dependency Upgrade) & Task 58 (E2E Test Suite)

## Task 57: Dependency Upgrade
- [x] 57.1 Kotlin → 2.3.20 — _Requirements: AC 15.1_
- [x] 57.2 Ktor → 3.4.0 — _Requirements: [backend-core] AC 8.1, AC 15.1_
- [x] 57.3 Serenity → 5.3.10 — _Requirements: AC 15.1_
- [x] 57.4 Compose 1.10.3, Koin 4.1.1 — _Requirements: AC 15.1_

## Task 58: E2E Test Suite
- [x] 58.1 126 API tests trong 12 test files — _Requirements: AC 15.4_
- [x] 58.2 Base test class với server auto-start — _Requirements: AC 15.4_
- [x] 58.3 Regression tests — _Requirements: AC 15.4_
- [x] 58.4 Real Jira integration tests (conditional) — _Requirements: AC 15.4_

## Task 58a: UI E2E Tests — Post-Refactor Features (Cucumber + Serenity)
- [x] 58a.1 Tạo `010-FrontendBackendIntegration.feature` + `FrontendBackendIntegrationSteps.kt` + `UiFrontendBackendRunner.kt`
    - Frontend ↔ Backend integration: API calls, JWT auth, error handling, CORS
    - _Requirements: AC 13.1–13.9_

---

# Frontend Superseded — Tasks 10, 18–26 (Historical Reference)

> ⛔ Tất cả tasks trong section này đã được SUPERSEDED. Compose for Web approach đã thay thế bằng Kotlin/JS + HTML Templates (Tasks 27–36).

## ~~Task 10: Frontend Module Setup (Kotlin/JS + Vite)~~ — ⛔ SUPERSEDED bởi Task 27
- [x] ~~10.1–10.7~~ → Superseded

## ~~Task 18: Obsidian Kinetic Design System — CSS Implementation~~ — ⛔ SUPERSEDED bởi Task 28
- [x] ~~18.1–18.5~~ → Superseded

## ~~Task 19: Frontend Shell Components (Kotlin/JS)~~ — ⛔ SUPERSEDED bởi Task 29
- [x] ~~19.1 Tạo `Shell.kt`~~
- [x] ~~19.2 Tạo `Sidebar.kt`~~
- [x] ~~19.3 Tạo `Navbar.kt`~~

## ~~Task 20: Frontend Pages (Kotlin/JS)~~ — ⛔ SUPERSEDED bởi Task 32
- [x] ~~20.1–20.7~~ → Superseded

## ~~Task 21: Frontend Obsidian Kinetic CSS (Kotlin/JS)~~ — ⛔ SUPERSEDED bởi Task 28
- [x] ~~21.1–21.2~~ → Superseded

## ~~Task 22: Compose for Web Module Setup~~ — ⛔ SUPERSEDED bởi Task 27
- [x] ~~22.1–22.8~~ → Superseded

## ~~Task 23: Compose for Web Shell Components~~ — ⛔ SUPERSEDED bởi Task 29
- [x] ~~23.1–23.3~~ → Superseded

## ~~Task 24: Compose for Web Screens~~ — ⛔ SUPERSEDED bởi Task 32
- [x] ~~24.1–24.7~~ → Superseded

## ~~Task 25: Compose for Web Obsidian Kinetic Design System~~ — ⛔ SUPERSEDED bởi Task 28
- [x] ~~25.1–25.6~~ → Superseded

## ~~Task 26: Compose for Web SVG Charts~~ — ⛔ SUPERSEDED bởi Task 34
- [x] ~~26.1–26.2~~ → Superseded
