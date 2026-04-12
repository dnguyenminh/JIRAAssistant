# Ticket Intelligence — Tasks

Status: ✅ All completed

## From tasks-03-frontend-kotlinjs.md

### Task 32.5: TicketIntelligencePage.kt
- [x] 32.5 `TicketIntelligencePage.kt` — _Requirements: AC 5.1–5.4, AC 5.7, [cross-cutting] AC 13.2, [cross-cutting] AC 13.3_

### Task 12a.6: UI E2E Tests — Ticket Intelligence
- [x] 12a.6 Tạo `007-TicketIntelligence.feature` + `TicketIntelligenceSteps.kt` + `UiTicketIntelligenceRunner.kt`
    - _Requirements: AC 5.1–5.7_

---

## From tasks-07-batch-scan-engine.md

### Task 64: Backend — Ticket Analysis Status Endpoint
- [x] 64.1 Tạo `TicketAnalysisStatus.kt` và `TicketAnalysisState` enum
    - _Requirements: AC 5.11_
- [x] 64.2 Thêm endpoint `GET /api/projects/{key}/tickets/status`
    - _Requirements: AC 5.1, AC 5.11–5.14_

### Task 69: Frontend — Ticket Intelligence Combobox & Dynamic Actions
- [x] 69.1 Cập nhật `ticket-intelligence.html` — combobox, status badge, dynamic action button
    - _Requirements: AC 5.1, AC 5.11, [design-system-ux] AC 17.1_
- [x] 69.2 Cập nhật `components.css` — combobox styles
    - _Requirements: [design-system-ux] AC 17.1, [design-system-ux] AC 17.3_
- [x] 69.3 Cập nhật `TicketIntelligencePage.kt` — loadTicketList, renderCombobox, filterTickets, selectTicket, RBAC
    - _Requirements: AC 5.1, AC 5.11–5.15, AC 5.7_

### Task 71: E2E Tests — Scan API Endpoints (Ticket Status)
- [x] 71.2 `TicketStatusApiTest.kt` — ticket analysis status
    - _Requirements: AC 5.1, AC 5.11_

### Task 71a: UI E2E Tests — Batch Scan Engine (Combobox scenarios)
- [x] 71a.1 Tạo `014-BatchScan.feature` (includes Ticket Intelligence combobox scenarios):
    - Ticket Intelligence combobox: searchable dropdown, status badges, dynamic action button
    - _Requirements: AC 5.11–5.15_
- [x] 71a.2 Tạo `BatchScanSteps.kt` — Step definitions (includes combobox steps)
    - CSS selectors: `.ticket-combobox`, `.status-badge`
    - _Requirements: AC 5.11–5.15_
- [x] 71a.3 Tạo `UiBatchScanRunner.kt` — Serenity Cucumber runner
