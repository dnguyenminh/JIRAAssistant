# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Graph không được xây dựng khi scan hoàn tất
  - **CRITICAL**: Test này PHẢI FAIL trên code chưa fix — failure xác nhận bug tồn tại
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: Test này encode expected behavior — nó sẽ validate fix khi pass sau implementation
  - **GOAL**: Surface counterexamples chứng minh `buildAndSaveGraph()` không được gọi khi scan hoàn tất
  - **Scoped PBT Approach**: Scope property vào concrete failing case: scan hoàn tất với ≥1 ticket đã xử lý, assert `kbRepository.getGraphData(projectKey)` trả về non-null
  - Tạo file test: `shared/src/jvmTest/kotlin/com/assistant/scan/CompleteScanBugConditionPropertyTest.kt`
  - Sử dụng kotest property-based testing (Arb generators) theo pattern hiện có trong `GraphEnginePropertyTest.kt`
  - Mock `BatchScanEngine` dependencies: `kbRepository`, `jiraClientProvider`, `featureNetworkMapper`, `scanStateRepository`, `scanLogRepository`, `aiOrchestrator`
  - Generate random projectKey (alphanumeric 2-10 chars) và random ticket counts (1-20)
  - Gọi `completeScan()` (thông qua `scanLoop()` hoặc test trực tiếp) và assert:
    - `kbRepository.getGraphData(projectKey) != null` (từ Bug Condition trong design: `isBugCondition` returns true khi `getGraphData` returns null sau scan completion)
    - `scanState.status == ScanStatus.COMPLETED`
  - Run test trên code CHƯA FIX
  - **EXPECTED OUTCOME**: Test FAILS (xác nhận bug tồn tại — `getGraphData()` returns null vì `buildAndSaveGraph()` không được gọi)
  - Document counterexamples: ví dụ `completeScan("PROJ")` → `getGraphData("PROJ")` returns null
  - Mark task complete khi test đã viết, chạy, và failure đã document
  - _Requirements: 1.1, 2.1, 2.2_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Scan state management và empty project behavior không thay đổi
  - **IMPORTANT**: Follow observation-first methodology
  - Tạo file test: `shared/src/jvmTest/kotlin/com/assistant/scan/CompleteScanPreservationPropertyTest.kt`
  - Sử dụng kotest property-based testing theo pattern hiện có
  - **Observe behavior trên code CHƯA FIX cho non-buggy inputs:**
    - Observe: `pauseScan(projectKey)` → status chuyển sang PAUSED, active job bị cancel
    - Observe: `resumeScan(projectKey)` → status chuyển sang SCANNING, new job launched
    - Observe: `cancelScan(projectKey)` → status chuyển sang CANCELLED, active job bị cancel
    - Observe: `handleEmptyProject(projectKey)` → status = COMPLETED, totalTickets = 0, `buildAndSaveGraph()` KHÔNG được gọi
    - Observe: `processTicket(projectKey, ticketId)` → KB record saved, relationships logged
  - **Write property-based tests capturing observed behavior:**
    - Property: For all random projectKey, `pauseScan()` trên SCANNING state → status == PAUSED và job removed
    - Property: For all random projectKey, `cancelScan()` trên SCANNING state → status == CANCELLED và job removed
    - Property: For all random projectKey với 0 tickets, `handleEmptyProject()` → status == COMPLETED, totalTickets == 0, `buildAndSaveGraph()` không được gọi
    - Property: For all random (projectKey, ticketId), `processTicket()` → KB record saved với đúng ticketId
  - Run tests trên code CHƯA FIX
  - **EXPECTED OUTCOME**: Tests PASS (xác nhận baseline behavior cần preserve)
  - Mark task complete khi tests đã viết, chạy, và passing trên code chưa fix
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. Fix completeScan() để gọi buildAndSaveGraph()

  - [x] 3.1 Implement the fix trong BatchScanEngine.kt
    - File: `shared/src/commonMain/kotlin/com/assistant/scan/BatchScanEngine.kt`
    - Function: `completeScan(projectKey: String)` (dòng 201-205)
    - Thêm lời gọi `buildAndSaveGraph(projectKey)` TRƯỚC khi set status COMPLETED
    - Wrap trong try-catch: catch Exception → `logToBoth(projectKey, "-", ScanLogStatus.FAILED, "Graph build failed: ${e.message ?: \"Unknown error\"}")` → tiếp tục set COMPLETED
    - Giữ nguyên `private suspend fun` visibility
    - Không thay đổi bất kỳ hàm nào khác (`processTicket`, `pauseScan`, `resumeScan`, `cancelScan`, `handleEmptyProject`)
    - _Bug_Condition: isBugCondition(input) where processedCount == totalTickets AND totalTickets > 0 AND getGraphData(projectKey) == null_
    - _Expected_Behavior: completeScan() gọi buildAndSaveGraph(projectKey) trước khi set COMPLETED; getGraphData(projectKey) != null sau scan completion_
    - _Preservation: pauseScan/resumeScan/cancelScan state transitions không thay đổi; handleEmptyProject không gọi buildAndSaveGraph; processTicket pipeline không thay đổi_
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Graph được xây dựng khi scan hoàn tất
    - **IMPORTANT**: Re-run SAME test từ task 1 — KHÔNG viết test mới
    - Test từ task 1 encode expected behavior: `getGraphData(projectKey) != null` sau scan completion
    - Khi test PASS, nó xác nhận expected behavior đã được satisfy
    - Run `CompleteScanBugConditionPropertyTest` từ step 1
    - **EXPECTED OUTCOME**: Test PASSES (xác nhận bug đã fix — `buildAndSaveGraph()` được gọi, `getGraphData()` returns non-null)
    - _Requirements: 2.1, 2.2_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Scan state management và empty project behavior không thay đổi
    - **IMPORTANT**: Re-run SAME tests từ task 2 — KHÔNG viết tests mới
    - Run `CompleteScanPreservationPropertyTest` từ step 2
    - **EXPECTED OUTCOME**: Tests PASS (xác nhận không có regression)
    - Confirm tất cả preservation tests vẫn pass sau fix
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 4. Checkpoint - Ensure all tests pass
  - Run toàn bộ test suite: `./gradlew :shared:jvmTest`
  - Verify `CompleteScanBugConditionPropertyTest` PASSES (bug fixed)
  - Verify `CompleteScanPreservationPropertyTest` PASSES (no regression)
  - Verify existing tests không bị break (đặc biệt `GraphEnginePropertyTest`, `Graph3DPropertyTest`)
  - Ensure all tests pass, ask the user if questions arise.
