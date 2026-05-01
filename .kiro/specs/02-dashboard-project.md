# Dashboard & Project — Master Requirements

## Tổng quan

Domain Dashboard & Project bao gồm trang Dashboard (tổng quan dự án, scan control, metrics) và trang Project Analysis (sprint analytics, velocity trends, bottleneck radar). Dashboard là trang chính sau login, hiển thị health metrics, relationship network preview, AI estimation drift, và Neural Console. Batch Scan Engine cho phép quét toàn bộ tickets trong project với parallel processing, pause/resume/cancel, force re-analyze, và progressive loading.

Các bugfix đã giải quyết: navbar project badge không cập nhật sau chọn project, redirect loop khi Jira chưa cấu hình, page slow restore khi navigate giữa các trang, và race condition trong scan progress counter.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `dashboard` | Feature | ✅ Archived | Dashboard page, metrics, batch scan control, scan log UX |
| `project-analysis` | Feature | ✅ Archived | Project analysis, sprint analytics, velocity trends |
| `project-select-display-bug` | Bugfix | ✅ Archived | Fix navbar project badge không cập nhật sau chọn project |
| `no-jira-redirect-to-integrations` | Bugfix | ✅ Archived | Fix user bị stuck ở Project Select khi Jira chưa cấu hình |
| `page-slow-restore-fix` | Bugfix | ✅ Archived | Fix Analysis/Graph page chậm khi quay lại (sessionStorage restore) |
| `scan-progress-race-condition` | Bugfix | ✅ Archived | Fix race condition trong scan progress counter (99% thay vì 100%) |

## Requirements tổng hợp

### Dashboard — Metrics & Overview

- 3 thẻ chỉ số: PROJECT AI HEALTH (resolutionRate), ACTIVE KNOWLEDGE NODES (totalTickets), NEURAL VELOCITY (aiVelocity)
- Khi chưa có dữ liệu: hiển thị "—" thay vì giá trị hardcoded
- Relationship Network preview + nút "VIEW GRAPH"
- AI Estimation Drift chart + nút "ANALYSIS DRIFT"
- Neural Console: timestamp, tag (AI_SYNC, KB_WRITE, HEARTBEAT), nội dung
- Sidebar: Dashboard, Relationship Network, Project Analysis, Ticket Intelligence, Integrations
- Account dropdown: Account Settings, Security & Permissions, Sign Out

### Batch Scan Engine

- Nút "START SCAN" trên Dashboard, kiểm tra AI readiness trước khi scan
- Dropdown chọn concurrency (×1 đến ×10, default ×3), warning ⚠ khi ≥ 8
- Parallel processing: batch N tickets đồng thời, AI Semaphore giới hạn inference calls
- Pipeline per ticket: Phase 1 (parallel) Jira fetch → Phase 2 (semaphore) AI analysis → Phase 3 (parallel) relationships + attachments
- PAUSE/RESUME/CANCEL controls, progress bar (% + processed/total)
- Force re-analyze checkbox: bỏ qua KB cache, ghi đè kết quả cũ
- Auto-pause khi chuyển project, restore state khi quay lại

### Scan Log UX

- Append-only strategy: chỉ thêm entries mới, dedup theo id
- Scrollbar rõ ràng (max-height 280px), nút expand ⛶ → fullscreen dialog (90vw × 80vh)
- Live-update trong dialog, auto-scroll xuống cuối
- Reset log khi START SCAN mới
- Re-render toàn bộ khi navigate away và quay lại (detect DOM cleared)

### Project Analysis

- 4 thẻ chỉ số: Total Tickets, Resolution Rate, Cycle Time, AI Velocity
- Velocity Trend chart (≥ 7 cột), hover phóng to 1.05x
- Bottleneck Radar: ≥ 2 cảnh báo (rủi ro + gợi ý tối ưu)
- Progressive display: cập nhật metrics/chart/radar khi scan đang chạy
- Nút "DIVE INTO REPORTS" điều hướng chi tiết

### Multi-Project Scan Visibility

- `GET /api/scan/active`: danh sách tất cả projects đang SCANNING
- Stacked progress bars trong Dashboard scan panel, mỗi project 1 bar riêng
- Label: "[{PROJECT_KEY}] Scanning... {processed}/{total} — {percent}%"
- Khi click START SCAN và project đã có active scan (409) → hiển thị progress bar seamlessly
- Poll `GET /api/scan/active` mỗi 5 giây khi có scan active, auto-stop khi không còn

### Scan State Management

- Trạng thái per project: IDLE, SCANNING, PAUSED, CANCELLED, COMPLETED
- Lưu trong Knowledge Base: projectKey, totalTickets, processedCount, currentTicketId, status, timestamps
- Max 1 scan đồng thời per project (409 Conflict nếu đã có)
- Server restart → chuyển SCANNING sang PAUSED, user RESUME thủ công
- Error handling: skip failed tickets, ghi scan log, tiếp tục ticket tiếp theo

### Scan API Endpoints

- `POST /api/projects/{key}/scan/start` — khởi động scan
- `POST /api/projects/{key}/scan/pause` — tạm dừng
- `POST /api/projects/{key}/scan/resume` — tiếp tục từ vị trí đã dừng
- `POST /api/projects/{key}/scan/cancel` — hủy bỏ, giữ kết quả đã hoàn thành
- `GET /api/projects/{key}/scan/status` — trạng thái + 50 log entries gần nhất
- `GET /api/projects/{key}/scan/log` — log chi tiết với pagination
- `GET /api/projects/{key}/scan/ai-status` — kiểm tra AI provider readiness

### Page State Restore

- SessionStorage save/restore pattern: Phase 1 (immediate) hiển thị từ cache, Phase 2 (deferred) đồng bộ API
- Analysis page: lưu ProjectAnalysisResponse, restore metrics/velocity/bottleneck ngay lập tức
- Graph page: lưu GraphLayoutResponse, restore nodes/edges/clusters/filters ngay lập tức
- API response là source of truth, sessionStorage chỉ optimistic display
- Navigation context (ChatAction nodeKey) ưu tiên hơn state restore

### Navbar & Navigation

- Project badge `[PROJ]` trên navbar, cập nhật ngay khi chọn project
- Breadcrumb hiển thị đúng route đích sau navigation
- NavbarDropdown refresh khi project key thay đổi
- "Change Project" dropdown → auto-pause scan → navigate project_select
- Jira chưa cấu hình: Admin → redirect integrations, non-Admin → popup hướng dẫn

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `project-select-display-bug` | Fix navbar badge "Select Project" không cập nhật thành project key sau chọn project, breadcrumb hiển thị sai route |
| `no-jira-redirect-to-integrations` | Fix user stuck ở Project Select khi Jira chưa cấu hình — Admin redirect integrations, non-Admin hiển thị popup hướng dẫn |
| `page-slow-restore-fix` | Fix Analysis/Graph page load chậm khi quay lại — sessionStorage immediate restore + API background sync + progressive loading |
| `scan-progress-race-condition` | Fix race condition: cập nhật processedCount 1 lần sau batch (không per-coroutine), roundToInt thay vì toInt, COMPLETED luôn = 100% |
