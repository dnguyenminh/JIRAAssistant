# Batch Scan Engine — Tasks

Status: ✅ All completed

# Batch Scan Engine — Tasks 59–72, 126–132

Quét hàng loạt ticket per-project với progressive display + multi-project scan visibility.

## Task 59: Backend — SQLDelight Schema cho Batch Scan
- [x] 59.1 Thêm bảng `scan_states` và `scan_log`, indexes, queries
    - _Requirements: AC 18.6, [backend-core] AC 9.1_
- [x] 59.2 Tạo `ScanStatus.kt` enum (IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED)
    - _Requirements: AC 18.1_
- [x] 59.3 Tạo `ScanState.kt` data class với computed `progressPercent`
    - _Requirements: AC 18.6_
- [x] 59.4 Tạo `ScanLogEntry.kt` data class và `ScanLogStatus` enum
    - _Requirements: AC 18.12, AC 18.13_

## Task 60: Backend — ScanStateRepository & ScanLogRepository
- [x] 60.1 Tạo `ScanStateRepository.kt` interface
    - _Requirements: AC 18.3, AC 18.4, AC 18.6_
- [x] 60.2 Tạo `ScanStateRepositoryImpl.kt` — JVM SQLDelight
    - _Requirements: AC 18.6, [backend-core] AC 9.1_
- [x] 60.3 Tạo `ScanLogRepository.kt` interface
    - _Requirements: AC 18.12, AC 18.13_
- [x] 60.4 Tạo `ScanLogRepositoryImpl.kt` — JVM SQLDelight
    - _Requirements: AC 18.12_

## Task 61: Backend — BatchScanEngine (State Machine & Coroutine Scan Loop)
- [x] 61.1 Tạo `BatchScanEngine.kt` — startScan, pauseScan, resumeScan, cancelScan, getStatus, getLog
    - _Requirements: AC 18.1–18.5, AC 18.15_
- [x] 61.2 Triển khai `scanLoop()` — lặp ticketIds, kiểm tra isActive, cập nhật DB
    - _Requirements: AC 18.2, AC 18.16_
- [x] 61.3 Triển khai `processTicket()` — KB-First, log ANALYZING/COMPLETED/FAILED
    - _Requirements: AC 18.13, AC 18.16, AC 18.17_
- [x] 61.4 Triển khai `recoverOnStartup()` — SCANNING → PAUSED
    - _Requirements: AC 18.14_

## Task 62: Backend — Scan API Routes (6 endpoints)
- [x] 62.1 Tạo `ScanRoutes.kt` — start/pause/resume/cancel/status/log
    - _Requirements: AC 18.7–18.12_
- [x] 62.2 Tạo DTOs: ScanStatusResponse, ScanLogEntryResponse, ScanLogResponse
    - _Requirements: AC 18.11, AC 18.12_
- [x] 62.3 Tạo `ConflictException` cho HTTP 409
    - _Requirements: AC 18.15_

## Task 63: Backend — Koin Registration & Server Startup Recovery
- [x] 63.1 Cập nhật `ServerModule.kt` — đăng ký ScanStateRepository, ScanLogRepository, BatchScanEngine
    - _Requirements: AC 18.1, [backend-core] AC 8.1_
- [x] 63.2 Cập nhật `Routing.kt` — thêm scanRoutes()
    - _Requirements: AC 18.7_
- [x] 63.3 Cập nhật `Application.kt` — gọi recoverOnStartup()
    - _Requirements: AC 18.14_

## Task 64: Backend — Ticket Analysis Status Endpoint
- [x] 64.1 Tạo `TicketAnalysisStatus.kt` và `TicketAnalysisState` enum
    - _Requirements: [ticket-intelligence] AC 5.11_
- [x] 64.2 Thêm endpoint `GET /api/projects/{key}/tickets/status`
    - _Requirements: [ticket-intelligence] AC 5.1, [ticket-intelligence] AC 5.11–5.14_

## Task 65: Checkpoint — Backend Batch Scan Engine
- [x] 65. Schema, state machine, 6 endpoints, recovery, conflict

## Task 66: Frontend — Dashboard Scan Control Panel
- [x] 66.1 Cập nhật `dashboard.html` — scan control card, progress bar, log container
    - _Requirements: [dashboard] AC 2.9–2.13, [design-system-ux] AC 17.1_
- [x] 66.2 Cập nhật `DashboardPage.kt` — START/PAUSE/RESUME/CANCEL buttons, RBAC
    - _Requirements: [dashboard] AC 2.9–2.11, [user-management] AC 7.8_
- [x] 66.3 Polling scan status mỗi 3s, cập nhật progress + log
    - _Requirements: [dashboard] AC 2.12, [dashboard] AC 2.13_
- [x] 66.4 Auto-pause khi chuyển project
    - _Requirements: [dashboard] AC 2.14, [dashboard] AC 2.15_

## Task 67: Frontend — Knowledge Graph Progressive Display
- [x] 67.1 Scan status indicator + node count
    - _Requirements: [knowledge-graph-3d] AC 3.13_
- [x] 67.2 Progressive graph loading: poll mỗi 5s, fadeIn new nodes
    - _Requirements: [knowledge-graph-3d] AC 3.11, [knowledge-graph-3d] AC 3.12_

## Task 68: Frontend — Project Analysis Progressive Display
- [x] 68.1 Scan status indicator
    - _Requirements: [project-analysis] AC 4.8_
- [x] 68.2 Progressive metrics loading: poll mỗi 5s
    - _Requirements: [project-analysis] AC 4.6, [project-analysis] AC 4.7_

## Task 69: Frontend — Ticket Intelligence Combobox & Dynamic Actions
- [x] 69.1 Cập nhật `ticket-intelligence.html` — combobox, status badge, dynamic action button
    - _Requirements: [ticket-intelligence] AC 5.1, [ticket-intelligence] AC 5.11, [design-system-ux] AC 17.1_
- [x] 69.2 Cập nhật `components.css` — combobox styles
    - _Requirements: [design-system-ux] AC 17.1, [design-system-ux] AC 17.3_
- [x] 69.3 Cập nhật `TicketIntelligencePage.kt` — loadTicketList, renderCombobox, filterTickets, selectTicket, RBAC
    - _Requirements: [ticket-intelligence] AC 5.1, [ticket-intelligence] AC 5.11–5.15, [ticket-intelligence] AC 5.7_

## Task 70: Checkpoint — Frontend Batch Scan UI
- [x] 70. Dashboard scan control, progressive display, combobox, auto-pause

## Task 71: E2E Tests — Scan API Endpoints
- [x] 71.1 `ScanApiTest.kt` — 6 scan endpoints + RBAC
    - _Requirements: AC 18.7–18.12, AC 18.15_
- [x] 71.2 `TicketStatusApiTest.kt` — ticket analysis status
    - _Requirements: [ticket-intelligence] AC 5.1, [ticket-intelligence] AC 5.11_

## Task 71a: UI E2E Tests — Batch Scan Engine (Cucumber + Serenity)
- [x] 71a.1 Tạo `014-BatchScan.feature`:
    - Dashboard scan control panel: START/PAUSE/RESUME/CANCEL buttons
    - Progress bar cập nhật khi scan đang chạy
    - Scan log hiển thị entries mới
    - RBAC: Reader không thể START scan
    - Knowledge Graph progressive display: nodes xuất hiện khi scan
    - Project Analysis progressive display: metrics cập nhật khi scan
    - Ticket Intelligence combobox: searchable dropdown, status badges, dynamic action button
    - Auto-pause khi chuyển project
    - _Requirements: [dashboard] AC 2.9–2.15, [knowledge-graph-3d] AC 3.11–3.13, [project-analysis] AC 4.6–4.8, [ticket-intelligence] AC 5.11–5.15, AC 18.7–18.12_
- [x] 71a.2 Tạo `BatchScanSteps.kt` — Step definitions cho feature file
    - CSS selectors: `#btn-start-scan`, `#btn-pause-scan`, `#scan-progress`, `.scan-log-entry`, `.ticket-combobox`, `.status-badge`
    - _Requirements: [dashboard] AC 2.9–2.15, [knowledge-graph-3d] AC 3.11–3.13, [project-analysis] AC 4.6–4.8, [ticket-intelligence] AC 5.11–5.15_
- [x] 71a.3 Tạo `UiBatchScanRunner.kt` — Serenity Cucumber runner
    - _Requirements: AC 18.7_

## Task 72: Final Checkpoint — Batch Scan Engine Feature
- [x] 72. End-to-end, state machine, recovery, progressive display, combobox, RBAC

---

## Multi-Project Scan Visibility (Tasks 126–132)

Hiển thị tiến trình quét đồng thời nhiều project, xử lý 409 Conflict graceful, polling active scans.

## Task 126: Backend — GET /api/scan/active Endpoint
- [x] 126.1 Thêm method `getActiveScans()` vào `BatchScanEngine.kt` — gọi `scanStateRepository.findAllScanning()`
    - _Requirements: AC 18.18_
- [x] 126.2 Thêm route `GET /api/scan/active` vào `ScanRoutes.kt` — `withPermission(VIEW_ANALYSIS)`, trả về `List<ScanStatusResponse>`
    - _Requirements: AC 18.18_
- [x] 126.3 Verify `POST /api/projects/{key}/scan/start` trả 409 khi project đã có SCANNING status (đã có — chỉ verify)
    - _Requirements: AC 18.18_

## Task 127: Frontend — DashboardMultiScanProgress Component
- [x] 127.1 Tạo `DashboardMultiScanProgress.kt` — `renderActiveScans()`, `formatProgressLabel()`, `startActivePolling()`, `stopActivePolling()`
    - _Requirements: AC 18.19, AC 18.20, AC 18.22_
- [x] 127.2 `formatProgressLabel()` — format "[{PROJECT_KEY}] Scanning... {processed}/{total} — {percent}%"
    - _Requirements: AC 18.19_
- [x] 127.3 `renderActiveScans()` — tạo stacked progress bars trong `#multi-scan-progress` container, mỗi scan 1 bar với project key label
    - _Requirements: AC 18.20_
- [x] 127.4 `startActivePolling()` — poll `GET /api/scan/active` mỗi 5s, cập nhật tất cả progress bars, stop khi empty list
    - _Requirements: AC 18.22_

## Task 128: Frontend — Dashboard HTML Template Update
- [x] 128.1 Cập nhật `dashboard.html` — thêm `#multi-scan-progress` container trong scan control panel
    - _Requirements: AC 18.20_
- [x] 128.2 Cập nhật `components.css` — styles cho `.scan-progress-item`, `.scan-progress-item-label`
    - _Requirements: AC 18.20_

## Task 129: Frontend — 409 Conflict Graceful Handling
- [x] 129.1 Cập nhật `DashboardScanControl.scanAction("start")` — khi nhận 409, gọi `GET /api/scan/active` và hiển thị progress bar thay vì error
    - _Requirements: AC 18.21_
- [x] 129.2 Cập nhật `DashboardPage.cleanup()` — gọi `DashboardMultiScanProgress.stopActivePolling()`
    - _Requirements: AC 18.22_

## Task 130: Checkpoint — Multi-Project Scan Visibility
- [x] 130. GET /api/scan/active hoạt động, stacked progress bars hiển thị, 409 handled gracefully, polling start/stop đúng

## Task 131: E2E Tests — Multi-Project Scan Visibility
- [x] 131.1 API test: `GET /api/scan/active` — JWT Reader+ → 200, no JWT → 401, verify response format
    - _Requirements: AC 18.18_
- [x] 131.2 API test: `POST start` khi đã có scan → 409, verify response body
    - _Requirements: AC 18.18_
- [x] 131.3 UI test: Stacked progress bars hiển thị khi nhiều scans active, 409 shows progress bar
    - _Requirements: AC 18.19, AC 18.20, AC 18.21_

## Task 132: Final Checkpoint — Multi-Project Scan Visibility Feature
- [x] 132. Active scans endpoint, multi-project progress bars, 409 graceful handling, polling, E2E tests


---

## Batch Prompt Optimization (Tasks 133–142)

Gộp nhiều ticket vào 1 AI prompt để giảm số lượng AI calls và rút ngắn thời gian scan.

## Task 133: Backend — BatchPromptBuilder & BatchResponseParser
- [x] 133.1 Tạo `BatchPromptBuilder.kt` — `buildBatchPrompt()`, `splitByContentLimit()`, constants
    - _Requirements: AC 37, AC 39_
- [x] 133.2 Tạo `BatchResponseParser.kt` — `parseBatchResponse()` parse JSON array → Map<ticketId, AnalysisResult>
    - _Requirements: AC 40, AC 42_

## Task 134: Backend — AIOrchestrator Batch Method
- [x] 134.1 Thêm `analyzeTicketBatch()` vào `AIOrchestrator` interface
    - _Requirements: AC 37_
- [x] 134.2 Implement `analyzeTicketBatch()` trong `AIOrchestratorImpl` — KB filter, batch prompt, parse, retry, fallback
    - _Requirements: AC 37, AC 40, AC 41, AC 42, AC 46, AC 47_

## Task 135: Backend — BatchScanEngine Integration
- [x] 135.1 Thêm `getBatchPromptSize()` helper đọc từ SettingsRepository với default 3, validate >= 1
    - _Requirements: AC 34_
- [x] 135.2 Tạo `BatchScanTicketBatchProcessor.kt` — `processBatchPrompt()` gộp tickets, gọi `analyzeTicketBatch()`, save KB, log batch result
    - _Requirements: AC 43, AC 44, AC 45_
- [x] 135.3 Cập nhật `scanLoop()` trong `BatchScanEngine.kt` — đọc batch_prompt_size, gọi `processBatchPrompt()` thay vì `processTicket()` khi size > 1
    - _Requirements: AC 35, AC 36, AC 43_

## Task 136: Backend — Settings API Validation
- [x] 136.1 Cập nhật `SettingsRoutes.kt` — validate `batch_prompt_size` >= 1 trong PUT /api/settings/feature, trả 400 nếu < 1
    - _Requirements: AC 49_

## Task 137: Frontend — Batch Prompt Size Combobox
- [x] 137.1 Cập nhật `dashboard.html` — thêm combobox `scan-batch-prompt-size` với datalist suggested values
    - _Requirements: AC 48_
- [x] 137.2 Cập nhật `DashboardScanControl.kt` — đọc batch_prompt_size, gửi qua query param khi START scan, validate >= 1
    - _Requirements: AC 48, AC 50_

## Task 138: Backend — ScanRoutes Accept batch_prompt_size Param
- [x] 138.1 Cập nhật `ScanRoutes.kt` — đọc `batchPromptSize` query param từ start endpoint, lưu vào SettingsRepository
    - _Requirements: AC 35, AC 43_

## Task 139: Checkpoint — Batch Prompt Optimization
- [x] 139. BatchPromptBuilder, BatchResponseParser, AIOrchestrator batch method, BatchScanEngine integration, Settings validation, Frontend combobox
