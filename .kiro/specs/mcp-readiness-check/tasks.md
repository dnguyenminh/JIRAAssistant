# Implementation Plan: MCP Readiness Check

## Overview

Thêm bước kiểm tra sẵn sàng MCP server trước khi sinh tài liệu BRD/FSD/Slides. Backend: `McpHealthChecker` service + `GET /api/mcp/health` endpoint. Frontend: readiness interceptor trong `DocumentGenerationFlow` + `ReadinessDialog`. Shared: `McpHealthResponse` / `McpServerHealth` models.

Backend files in: `server/src/jvmMain/kotlin/com/assistant/server/`
Frontend files in: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/`
Shared files in: `shared/src/commonMain/kotlin/com/assistant/mcp/models/`
Test files in: `server/src/jvmTest/kotlin/com/assistant/server/mcp/`

## Tasks

- [x] 1. Create shared McpHealthResponse models
  - [x] 1.1 Create `McpHealthResponse.kt` in shared module
    - Create `shared/src/commonMain/kotlin/com/assistant/mcp/models/McpHealthResponse.kt`
    - Define `McpHealthResponse` data class with `allReady: Boolean` and `servers: List<McpServerHealth>`
    - Define `McpServerHealth` data class with `configId: String`, `serverName: String`, `ready: Boolean`, `toolCount: Int = 0`, `error: String? = null`, `role: String = "other"`
    - Both classes annotated with `@Serializable`
    - _Requirements: 1.2, 1.6, 5.1_

- [x] 2. Create McpHealthChecker backend service
  - [x] 2.1 Create `McpHealthChecker.kt` service
    - Create `server/src/jvmMain/kotlin/com/assistant/server/mcp/McpHealthChecker.kt`
    - Constructor: `McpHealthChecker(processManager: McpProcessManager, internalMcpBridge: InternalMcpBridge, mcpRepo: McpServerRepository)`
    - Implement `suspend fun checkAll(): McpHealthResponse`
    - Load all server configs from `mcpRepo.getAll()`, filter `disabled == false` and `type != "marker"`
    - For each external server: launch async ping via `coroutineScope { servers.map { async { pingServer(it) } } }`
    - For internal server: always return `McpServerHealth(ready = true, role = "jira_internal", toolCount = internalBridge.getAggregatedTools().size)`
    - Compute `allReady = servers.all { it.ready }`
    - Keep file ≤ 120 lines, each function ≤ 20 lines
    - _Requirements: 1.1, 1.5, 1.6, 4.2_

  - [x] 2.2 Implement `pingServer()` with 3-second timeout
    - In `McpHealthChecker.kt`, implement `private suspend fun pingServer(config: McpServerConfig): McpServerHealth`
    - Get client via `processManager.getClient(config.id)`
    - If client is null → return `ready: false, error: "Server not running"`
    - If client exists → `withTimeout(3000) { client.listTools() }` → return `ready: true, toolCount: tools.size`
    - Catch `TimeoutCancellationException` → `ready: false, error: "Connection timeout (3s)"`
    - Catch general `Exception` → `ready: false, error: e.message`
    - _Requirements: 1.3, 1.4, 4.3_

  - [x] 2.3 Implement `classifyRole()` for server role detection
    - In `McpHealthChecker.kt`, implement `internal fun classifyRole(config: McpServerConfig): String`
    - Pattern matching (case-insensitive) on `config.name` and `config.id`:
      - `config.id == InternalMcpBridge.INTERNAL_SERVER_ID` → `"jira_internal"`
      - name contains "knowledge" or "kb" → `"knowledge_base"`
      - name contains "database" or "db" → `"database"`
      - name contains "markitdown" → `"markitdown"`
      - else → `"other"`
    - _Requirements: 5.1_

  - [x] 2.4 Write property test: Health response completeness (Property 1)
    - **Feature: mcp-readiness-check, Property 1: Health response completeness**
    - *For any* set of active server configs and ping results, checkAll() returns exactly one McpServerHealth per active server with valid fields
    - Create `server/src/jvmTest/kotlin/com/assistant/server/mcp/McpHealthCheckerPropertyTest.kt`
    - Use Kotest `checkAll` with `PropTestConfig(iterations = 100)`
    - Generate random `McpServerConfig` lists (0-10 servers, mix of disabled/enabled/marker)
    - Mock `McpProcessManager` and `McpServerRepository`
    - Verify: response.servers.size == active server count, all configIds present, toolCount >= 0, error non-null only when ready == false
    - **Validates: Requirements 1.1, 1.2**

  - [x] 2.5 Write property test: Ping result determines ready status (Property 2)
    - **Feature: mcp-readiness-check, Property 2: Ping result determines ready status**
    - *For any* external server, ready status matches ping success/failure
    - In `McpHealthCheckerPropertyTest.kt`
    - Generate random scenarios: client exists + ping succeeds → ready: true; client null / ping throws / ping timeout → ready: false with error
    - Use `PropTestConfig(iterations = 100)`
    - **Validates: Requirements 1.3, 1.4, 4.3**

  - [x] 2.6 Write property test: allReady consistency (Property 4)
    - **Feature: mcp-readiness-check, Property 4: allReady consistency**
    - *For any* McpHealthResponse, allReady == servers.all { it.ready }
    - In `McpHealthCheckerPropertyTest.kt`
    - Generate random lists of McpServerHealth with varying ready states
    - Verify allReady invariant holds
    - Use `PropTestConfig(iterations = 100)`
    - **Validates: Requirements 1.6**

  - [x] 2.7 Write property test: Server role classification (Property 5)
    - **Feature: mcp-readiness-check, Property 5: Server role classification correctness**
    - *For any* server config, classifyRole() returns correct role based on name/ID patterns
    - In `McpHealthCheckerPropertyTest.kt`
    - Generate random server names with/without role keywords (knowledge, kb, database, db, markitdown)
    - Verify classification is case-insensitive and deterministic
    - Use `PropTestConfig(iterations = 100)`
    - **Validates: Requirements 5.1**

  - [x] 2.8 Write property test: Internal server invariant (Property 3)
    - **Feature: mcp-readiness-check, Property 3: Internal server invariant**
    - *For any* health check with internal server present, internal server is always ready: true with role jira_internal
    - In `McpHealthCheckerPropertyTest.kt`
    - Generate random external server sets, always include internal server
    - Verify internal server entry has ready: true, role: "jira_internal", toolCount matches internal tools
    - Use `PropTestConfig(iterations = 100)`
    - **Validates: Requirements 1.5**

- [x] 3. Create McpHealthRoutes backend endpoint
  - [x] 3.1 Create `McpHealthRoutes.kt` with GET /api/mcp/health
    - Create `server/src/jvmMain/kotlin/com/assistant/server/routes/McpHealthRoutes.kt`
    - Define `fun Routing.mcpHealthRoutes()` following existing route patterns (see `McpRoutes.kt`)
    - Route: `GET /api/mcp/health` inside `authenticate("auth-jwt")` block
    - Require `ANALYZE_AI` permission using `withPermission(Permission.ANALYZE_AI)` (same as AnalysisRoutes)
    - Inject `McpHealthChecker` via Koin
    - Call `checker.checkAll()` → respond with `McpHealthResponse`
    - Wrap in try/catch → HTTP 500 with `ErrorResponse` on unexpected error
    - Keep file ≤ 60 lines
    - _Requirements: 1.1, 1.7, 1.8_

  - [x] 3.2 Register McpHealthChecker in Koin module and route in Application
    - Add `McpHealthChecker` as singleton in Koin DI module (inject McpProcessManager, InternalMcpBridge, McpServerRepository)
    - Register `mcpHealthRoutes()` in Application routing setup (same file where `mcpRoutes()` is registered)
    - _Requirements: 1.1_

  - [x] 3.3 Write unit tests for McpHealthRoutes
    - Create `server/src/jvmTest/kotlin/com/assistant/server/mcp/McpHealthCheckerTest.kt`
    - Test: all servers ready → allReady: true
    - Test: one server down → allReady: false with correct error
    - Test: no external servers → only internal server in response
    - Test: role classification edge cases (mixed case, empty name)
    - _Requirements: 1.1–1.8_

- [x] 4. Create frontend McpReadinessChecker
  - [x] 4.1 Create `McpReadinessChecker.kt`
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/McpReadinessChecker.kt`
    - Implement `suspend fun check(): McpHealthResponse` — call `GET /api/mcp/health` via `window.fetch` with JWT header
    - Implement `suspend fun checkAndProceed(docType: String): Boolean` — call check(), if allReady return true, else show ReadinessDialog and return user choice
    - Handle errors: network failure, timeout, non-200 status → throw with descriptive message
    - Parse response using `kotlinx.serialization.json.Json`
    - Keep file ≤ 80 lines
    - _Requirements: 2.1, 2.3, 2.4, 2.5_

- [x] 5. Create frontend ReadinessDialog
  - [x] 5.1 Create `ReadinessDialog.kt`
    - Create `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/ReadinessDialog.kt`
    - Implement `suspend fun show(response: McpHealthResponse, docType: String): Boolean`
    - Use `CompletableDeferred<Boolean>` to suspend until user clicks button
    - Create modal overlay using `document.createElement()` + CSS classes (no innerHTML with HTML strings)
    - Render server list: ✅ icon for ready, ⚠️ icon for not ready
    - Show `serverName` and `error` for unavailable servers
    - Show warning message about incomplete output
    - Show "Tiếp tục" and "Hủy" buttons
    - Dismiss on click outside or Escape key → same as "Hủy"
    - Keep file ≤ 150 lines
    - _Requirements: 3.1–3.7_

  - [x] 5.2 Implement critical server warning logic
    - In `ReadinessDialog.kt`, define `CRITICAL_SERVERS` map: BRD → {knowledge_base, database}, FSD → {knowledge_base, database}, REQUIREMENT_SLIDES → {knowledge_base}
    - Highlight critical unavailable servers with distinct styling
    - When ALL critical servers for docType are unavailable → show strong warning: "Các tool quan trọng cho [docType] đều không khả dụng — tài liệu sinh ra có thể thiếu dữ liệu nghiêm trọng."
    - _Requirements: 5.2, 5.3_

  - [x] 5.3 Write property test: Critical server identification (Property 6)
    - **Feature: mcp-readiness-check, Property 6: Critical server identification and warning**
    - *For any* doc type and server health list, critical servers are correctly identified and strong warning appears when all critical servers are unavailable
    - Create `server/src/jvmTest/kotlin/com/assistant/server/mcp/McpCriticalServerPropertyTest.kt`
    - Test the critical server logic as a pure function (extract from ReadinessDialog for testability)
    - Generate random doc types (BRD/FSD/REQUIREMENT_SLIDES) and random McpServerHealth lists with varying roles and ready states
    - Verify: critical servers correctly identified, strong warning present iff all critical servers unavailable
    - Use `PropTestConfig(iterations = 100)`
    - **Validates: Requirements 5.2, 5.3**

- [x] 6. Integrate readiness check into DocumentGenerationFlow
  - [x] 6.1 Modify `DocumentGenerationFlow.startGeneration()` to add readiness check
    - In `DocumentGenerationFlow.kt`, modify `startGeneration(ticketId, docType)`:
    - Before existing logic: show `BlockingOverlay("Checking MCP tools...")`
    - Call `McpReadinessChecker.checkAndProceed(docType)`
    - If returns false (cancelled) → remove overlay, re-enable button, return
    - If returns true → remove overlay, proceed with existing generation logic
    - If throws → remove overlay, show error toast "Không thể kiểm tra MCP tools — vui lòng thử lại", re-enable button
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 6.2 Modify `DocumentGenerationFlow.startGenerateAll()` to add readiness check
    - In `DocumentGenerationFlow.kt`, modify `startGenerateAll(ticketId)`:
    - Before existing logic: show `BlockingOverlay("Checking MCP tools...")`
    - Call `McpReadinessChecker.checkAndProceed("BRD")` (BRD is the most critical for generate-all)
    - Same proceed/cancel/error handling as 6.1
    - _Requirements: 2.1, 2.2_

- [x] 7. Add CSS styles for ReadinessDialog
  - [x] 7.1 Add ReadinessDialog styles to ticket-intelligence.css
    - Add styles in `frontend/src/jsMain/resources/ticket-intelligence.css`
    - Modal overlay: dark semi-transparent background, centered glass-card dialog
    - Server list: flex layout with icon + name + error
    - Ready icon: green color, Not-ready icon: red/amber color
    - Warning text: amber color, strong warning: red background
    - Buttons: "Tiếp tục" = primary style, "Hủy" = secondary/ghost style
    - Follow Obsidian Kinetic design system (dark theme, glass-card, neon accents)
    - _Requirements: 3.1–3.4_
