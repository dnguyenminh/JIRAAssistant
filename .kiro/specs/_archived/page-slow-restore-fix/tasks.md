# Implementation Plan

- [x] 1. Viết exploration test cho Bug 1 & Bug 3 — Analysis slow restore + polling không lưu sessionStorage
  - **Property 1: Bug Condition** - AnalysisPage Slow Restore & Missing Polling Persistence
  - **CRITICAL**: Test này PHẢI FAIL trên code chưa fix — failure xác nhận lỗi tồn tại
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: Test này encode expected behavior — sẽ validate fix khi pass sau implementation
  - **GOAL**: Surface counterexamples chứng minh (1) quay lại trang Analysis không hiển thị ngay từ sessionStorage, (2) loadAnalysisData() không lưu vào sessionStorage
  - **Scoped PBT Approach**: Sinh random `ProjectAnalysisResponse` với metrics khác nhau (totalTickets, resolutionRate, cycleTimeDays, aiVelocity), lưu vào sessionStorage, gọi render flow
  - Tạo test file: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/analysis/AnalysisSlowRestoreBugTest.kt`
  - Dùng kotlin.test + kotlin.random consistent với `SlowRestoreBugConditionTest.kt` trong ticket package
  - **Test 1 — Immediate Restore**: Lưu `ProjectAnalysisResponse` vào sessionStorage key `analysis_page_state`, simulate render flow → kiểm tra metrics elements (`val-total-tickets`, `val-resolution-rate`, etc.) có hiển thị giá trị đã lưu ngay không
  - Bug condition: `isBugCondition_AnalysisSlowRestore(input)` — savedState != null AND isReturningToPage = true AND targetPage = "analysis"
  - Expected behavior: `result.metricsDisplayedImmediately = true AND result.noWaitForApiLoad = true`
  - Sẽ FAIL vì không có `immediateRestoreFromSession()` — metrics hiển thị "0" hoặc placeholder
  - **Test 2 — Polling Persistence**: Gọi `loadAnalysisData()` thành công → kiểm tra `sessionStorage.getItem("analysis_page_state")` != null
  - Bug condition: `isBugCondition_AnalysisNoProgressiveLoad(input)` — scanStatus = SCANNING AND partialAnalysisDataAvailable = true
  - Sẽ FAIL vì `loadAnalysisData()` không gọi `AnalysisStateManager.save()`
  - Chạy test trên code CHƯA fix
  - **EXPECTED OUTCOME**: Test FAILS — xác nhận cả 2 lỗi tồn tại
  - Document counterexamples: metrics = "0"/placeholder thay vì giá trị đã lưu; sessionStorage trống sau loadAnalysisData()
  - Mark task complete khi test được viết, chạy, và failure được document
  - _Requirements: 1.1, 1.3, 1.4_

- [x] 2. Viết exploration test cho Bug 2 — Graph slow restore
  - **Property 1: Bug Condition** - KnowledgeGraphPage Slow Restore
  - **CRITICAL**: Test này PHẢI FAIL trên code chưa fix — failure xác nhận lỗi tồn tại
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: Test này encode expected behavior — sẽ validate fix khi pass sau implementation
  - **GOAL**: Surface counterexamples chứng minh quay lại trang Graph không hiển thị đồ thị ngay từ sessionStorage, GraphState bị reset
  - **Scoped PBT Approach**: Sinh random `GraphLayoutResponse` với số lượng nodes/edges khác nhau, lưu vào sessionStorage, simulate render flow
  - Tạo test file: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/graph/GraphSlowRestoreBugTest.kt`
  - Dùng kotlin.test + kotlin.random consistent với project conventions
  - **Test — Immediate Graph Restore**: Lưu `GraphLayoutResponse` vào sessionStorage key `knowledge_graph_state`, simulate render flow → kiểm tra `GraphState.allNodes` có chứa nodes đã lưu không
  - Bug condition: `isBugCondition_GraphSlowRestore(input)` — savedState != null AND isReturningToPage = true AND targetPage = "knowledge_graph" AND NOT NavigationContext.hasContext("knowledge_graph")
  - Expected behavior: `result.graphRenderedImmediately = true AND result.nodesDisplayed = savedState.nodes`
  - Sẽ FAIL vì `GraphState.reset()` xóa toàn bộ allNodes/allEdges, không có `immediateRestoreFromSession()`
  - Chạy test trên code CHƯA fix
  - **EXPECTED OUTCOME**: Test FAILS — xác nhận lỗi tồn tại
  - Document counterexamples: `GraphState.allNodes` = emptyList() dù sessionStorage có data
  - Mark task complete khi test được viết, chạy, và failure được document
  - _Requirements: 1.2_

- [x] 3. Viết preservation property tests (TRƯỚC khi implement fix)
  - **Property 2: Preservation** - Hành vi Hiện tại Không Thay đổi
  - **IMPORTANT**: Tuân thủ observation-first methodology
  - Tạo test file: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/analysis/AnalysisPreservationTest.kt`
  - Tạo test file: `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/graph/GraphPreservationTest.kt`
  - Dùng kotlin.test + kotlin.random consistent với `PreservationPropertyTest.kt` trong ticket package
  - **Observe trên code CHƯA fix, sau đó viết properties:**
  - Observe: Trang Analysis load lần đầu (sessionStorage trống) → gọi API, hiển thị metrics bình thường
  - Observe: Trang Graph load lần đầu (sessionStorage trống) → gọi API, render Cytoscape, populate filter panel
  - Observe: API fail → hiển thị error message + nút retry trên cả hai trang
  - Observe: Navigate từ ChatAction với nodeKey context → auto-focus node trên Graph (ưu tiên hơn state restore)
  - Observe: `GraphScanStatus` incremental update → diff-based progressive loading hoạt động bình thường
  - **Property-based tests:**
  - Property: First load (sessionStorage trống) — `AnalysisStateManager.restore()` trả về null → flow gọi API bình thường, không crash
  - Property: First load (sessionStorage trống) — `GraphStateManager.restore()` trả về null → flow gọi API bình thường, không crash
  - Property: Navigation context ưu tiên — khi `NavigationContext.hasContext("knowledge_graph")` = true → skip state restore, gọi `applyNavigationContext()`
  - Property: Save/restore roundtrip — sinh random `ProjectAnalysisResponse`, save → restore → tất cả fields giữ nguyên
  - Property: Save/restore roundtrip — sinh random `GraphLayoutResponse`, save → restore → tất cả fields giữ nguyên
  - Property: Restore trả về null khi sessionStorage trống hoặc JSON invalid
  - Verify tất cả preservation tests PASS trên code CHƯA fix
  - **EXPECTED OUTCOME**: Tests PASS — xác nhận baseline behavior cần bảo toàn
  - Mark task complete khi tests được viết, chạy, và passing trên code chưa fix
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 4. Tạo AnalysisStateManager và cập nhật AnalysisPage

  - [x] 4.1 Tạo `AnalysisStateManager` object
    - Tạo file: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/analysis/AnalysisStateManager.kt`
    - Object `AnalysisStateManager` với `STORAGE_KEY = "analysis_page_state"`
    - `save(data: ProjectAnalysisResponse)`: Serialize → JSON → `sessionStorage.setItem()`
    - `restore(): ProjectAnalysisResponse?`: `sessionStorage.getItem()` → deserialize → return null nếu không có hoặc parse fail
    - `clear()`: `sessionStorage.removeItem()`
    - Shared `Json` instance với `ignoreUnknownKeys = true`, `encodeDefaults = true`, `isLenient = true`
    - Pattern tương tự `TicketStateManager.kt` — file ≤ 50 dòng
    - _Bug_Condition: isBugCondition_AnalysisSlowRestore(input) where savedState != null AND isReturningToPage = true_
    - _Expected_Behavior: save() persist ProjectAnalysisResponse, restore() trả về data đã lưu_
    - _Preservation: restore() trả về null khi sessionStorage trống hoặc JSON invalid_
    - _Requirements: 2.1, 2.4_

  - [x] 4.2 Thêm `immediateRestoreFromSession()` vào `AnalysisPage`
    - Đọc `ProjectAnalysisResponse` từ `AnalysisStateManager.restore()`
    - Nếu có dữ liệu: gọi `renderMetrics(data)`, `AnalysisVelocityChart.render(data.velocityTrend)`, `AnalysisBottleneckRadar.render(data.bottlenecks)` ngay lập tức
    - Hàm ≤ 20 dòng theo Kotlin code standards
    - _Bug_Condition: isBugCondition_AnalysisSlowRestore(input)_
    - _Expected_Behavior: Hiển thị metrics/chart/radar ngay từ sessionStorage TRƯỚC khi API trả về_
    - _Requirements: 2.1, 2.6_

  - [x] 4.3 Cập nhật `render()` flow trong `AnalysisPage`
    - Thêm `immediateRestoreFromSession()` SAU `bindEvents()` và TRƯỚC `loadAnalysisData()`
    - Flow mới: `bindEvents()` → `immediateRestoreFromSession()` → `loadAnalysisData()` → `AnalysisScanStatus.loadScanStatus()`
    - _Bug_Condition: render() hiện tại xóa innerHTML rồi chờ API — không có immediate restore_
    - _Expected_Behavior: Phase 1 (immediate) hiển thị từ sessionStorage, Phase 2 (deferred) đồng bộ API_
    - _Requirements: 2.1_

  - [x] 4.4 Cập nhật `loadAnalysisData()` để lưu sessionStorage
    - Sau khi decode `ProjectAnalysisResponse` thành công, gọi `AnalysisStateManager.save(data)`
    - Phục vụ cả immediate restore khi quay lại trang VÀ progressive loading persistence khi scan polling
    - _Bug_Condition: loadAnalysisData() hiện tại KHÔNG lưu response vào sessionStorage_
    - _Expected_Behavior: Mỗi API response thành công được persist vào sessionStorage_
    - _Preservation: Render metrics/chart/radar vẫn hoạt động như hiện tại_
    - _Requirements: 2.3, 2.4_

  - [x] 4.5 Verify exploration test Bug 1 & Bug 3 now passes
    - **Property 1: Expected Behavior** - AnalysisPage Immediate Restore & Polling Persistence
    - **IMPORTANT**: Re-run SAME test từ task 1 — KHÔNG viết test mới
    - Test từ task 1 encode expected behavior — khi pass, xác nhận fix hoạt động
    - Chạy exploration test từ step 1
    - **EXPECTED OUTCOME**: Test PASSES (xác nhận Bug 1 & Bug 3 đã fix)
    - _Requirements: 2.1, 2.3, 2.4_

  - [x] 4.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Analysis Behavior Unchanged
    - **IMPORTANT**: Re-run SAME tests từ task 3 — KHÔNG viết test mới
    - Chạy preservation tests từ step 3 (phần Analysis)
    - **EXPECTED OUTCOME**: Tests PASS (xác nhận không regression)
    - _Requirements: 3.1, 3.3, 3.7_

- [x] 5. Tạo GraphStateManager và cập nhật KnowledgeGraphPage

  - [x] 5.1 Tạo `GraphStateManager` object
    - Tạo file: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/graph/GraphStateManager.kt`
    - Object `GraphStateManager` với `STORAGE_KEY = "knowledge_graph_state"`
    - `save(data: GraphLayoutResponse)`: Serialize → JSON → `sessionStorage.setItem()`
    - `restore(): GraphLayoutResponse?`: `sessionStorage.getItem()` → deserialize → return null nếu không có hoặc parse fail
    - `clear()`: `sessionStorage.removeItem()`
    - Shared `Json` instance với `ignoreUnknownKeys = true`, `encodeDefaults = true`, `isLenient = true`
    - Lưu ý: `GraphLayoutResponse` có thể lớn (>100 nodes) — sessionStorage limit ~5MB, đủ cho hầu hết đồ thị
    - Pattern tương tự `TicketStateManager.kt` — file ≤ 50 dòng
    - _Bug_Condition: isBugCondition_GraphSlowRestore(input) where savedState != null AND isReturningToPage = true_
    - _Expected_Behavior: save() persist GraphLayoutResponse, restore() trả về data đã lưu_
    - _Preservation: restore() trả về null khi sessionStorage trống hoặc JSON invalid_
    - _Requirements: 2.2, 2.5_

  - [x] 5.2 Thêm `immediateRestoreFromSession()` vào `KnowledgeGraphPage`
    - Đọc `GraphLayoutResponse` từ `GraphStateManager.restore()`
    - Nếu `NavigationContext.hasContext("knowledge_graph")` = true → SKIP (navigation context ưu tiên)
    - Nếu có dữ liệu: populate `GraphState` (allNodes, allEdges, allClusters, allNodeTypes, typeColorMap, filteredNodeIds)
    - Gọi `CytoscapeRenderer.renderGraph()`, `GraphFilterPanel.populateNodeTypes()`, `GraphFilterPanel.populateClusters()`, `updateNodeCount()`
    - Hàm ≤ 20 dòng — có thể tách helper `populateGraphStateFromResponse(data)` nếu cần
    - _Bug_Condition: isBugCondition_GraphSlowRestore(input)_
    - _Expected_Behavior: Hiển thị đồ thị ngay từ sessionStorage TRƯỚC khi API trả về_
    - _Preservation: Navigation context từ ChatAction vẫn ưu tiên hơn state restore_
    - _Requirements: 2.2, 2.6, 3.6_

  - [x] 5.3 Cập nhật `render()` flow trong `KnowledgeGraphPage`
    - Thêm `immediateRestoreFromSession()` SAU `bindEvents()` và TRƯỚC `loadGraphData()`
    - Flow mới: `GraphFilterPanel.init()` → `GraphSearchCombobox.init()` → `bindEvents()` → `immediateRestoreFromSession()` → `loadGraphData()` → `GraphScanStatus.loadScanStatus()`
    - **Điều chỉnh `GraphState.reset()` timing**: KHÔNG gọi `GraphState.reset()` ở đầu `render()` nữa — `loadGraphData()` sẽ overwrite `GraphState` khi API trả về
    - `CytoscapeRenderer.destroy()` vẫn gọi trong `cleanup()` nhưng `immediateRestoreFromSession()` sẽ tạo instance mới ngay lập tức
    - _Bug_Condition: render() hiện tại gọi GraphState.reset() xóa toàn bộ state rồi chờ API_
    - _Expected_Behavior: Phase 1 (immediate) hiển thị đồ thị từ sessionStorage, Phase 2 (deferred) đồng bộ API_
    - _Requirements: 2.2_

  - [x] 5.4 Cập nhật `loadGraphData()` để lưu sessionStorage
    - Sau khi decode `GraphLayoutResponse` thành công và populate `GraphState`, gọi `GraphStateManager.save(graphData)`
    - _Bug_Condition: loadGraphData() hiện tại KHÔNG lưu response vào sessionStorage_
    - _Expected_Behavior: Mỗi API response thành công được persist vào sessionStorage_
    - _Preservation: Render đồ thị, filter panel, search vẫn hoạt động như hiện tại_
    - _Requirements: 2.5_

  - [x] 5.5 Verify exploration test Bug 2 now passes
    - **Property 1: Expected Behavior** - KnowledgeGraphPage Immediate Restore
    - **IMPORTANT**: Re-run SAME test từ task 2 — KHÔNG viết test mới
    - Test từ task 2 encode expected behavior — khi pass, xác nhận fix hoạt động
    - Chạy exploration test từ step 2
    - **EXPECTED OUTCOME**: Test PASSES (xác nhận Bug 2 đã fix)
    - _Requirements: 2.2, 2.5_

  - [x] 5.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Graph Behavior Unchanged
    - **IMPORTANT**: Re-run SAME tests từ task 3 — KHÔNG viết test mới
    - Chạy preservation tests từ step 3 (phần Graph)
    - **EXPECTED OUTCOME**: Tests PASS (xác nhận không regression)
    - _Requirements: 3.2, 3.4, 3.5, 3.6, 3.8_

- [x] 6. Cập nhật GraphScanStatus lưu graph data sau incremental update

  - [x] 6.1 Thêm `GraphStateManager.save()` vào `applyIncrementalUpdate()`
    - Trong `GraphScanStatus.applyIncrementalUpdate()`, sau khi `updateGraphState(graphData)`, gọi `GraphStateManager.save(graphData)`
    - Đảm bảo progressive loading data được persist — nếu chuyển trang rồi quay lại, hiển thị ngay dữ liệu partial
    - _Bug_Condition: applyIncrementalUpdate() hiện tại KHÔNG lưu graph data vào sessionStorage_
    - _Expected_Behavior: Mỗi incremental update được persist vào sessionStorage_
    - _Preservation: Diff-based progressive loading flow vẫn hoạt động như hiện tại_
    - _Requirements: 2.5, 3.5_

  - [x] 6.2 Verify tất cả exploration tests pass
    - **Property 1: Expected Behavior** - All Bugs Fixed
    - Re-run tất cả exploration tests từ task 1 và task 2
    - **EXPECTED OUTCOME**: Tất cả tests PASS
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 6.3 Verify tất cả preservation tests still pass
    - **Property 2: Preservation** - All Behavior Unchanged
    - Re-run tất cả preservation tests từ task 3
    - **EXPECTED OUTCOME**: Tất cả tests PASS (xác nhận không regression)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 7. Checkpoint — Đảm bảo tất cả tests pass
  - Chạy full test suite: `./gradlew :frontend:jsTest`
  - Verify exploration tests pass (Property 1 — Bug Condition cho cả 3 bugs)
  - Verify preservation property tests pass (Property 2 — Hành vi không thay đổi)
  - Verify existing tests trong `frontend/src/jsTest/` vẫn pass (không regression)
  - Đảm bảo tất cả tests pass, hỏi user nếu có vấn đề phát sinh
