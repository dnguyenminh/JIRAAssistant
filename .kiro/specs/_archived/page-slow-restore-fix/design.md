# Page Slow Restore Fix — Thiết kế Sửa lỗi

## Overview

Ba lỗi UX trên trang **Project Analysis** (`AnalysisPage`) và **Relationship Network** (`KnowledgeGraphPage`) gây trải nghiệm chậm khi người dùng quay lại trang:

1. **Project Analysis slow restore** — Quay lại trang → `container.innerHTML = ""` xóa toàn bộ, gọi API `/api/projects/{key}/analysis`, hiển thị trang trống cho đến khi API trả về.
2. **Relationship Network slow restore** — Quay lại trang → `GraphState.reset()` xóa nodes/edges, `CytoscapeRenderer.destroy()` hủy đồ thị, gọi API `/api/graph/{key}` và render lại từ đầu.
3. **Project Analysis thiếu progressive loading** — Khi scan đang chạy, `AnalysisScanStatus.startAnalysisPolling()` gọi `loadAnalysisData()` mỗi 5 giây nhưng KHÔNG lưu kết quả vào sessionStorage — nếu chuyển trang rồi quay lại, phải chờ API load lại.

**Pattern fix** (đã chứng minh hiệu quả trên Ticket Intelligence):
- **Phase 1 (immediate)**: Đọc state từ sessionStorage → hiển thị ngay lập tức
- **Phase 2 (deferred)**: Đồng bộ với API response khi có dữ liệu mới

## Glossary

- **Bug_Condition (C)**: Điều kiện kích hoạt lỗi — quay lại trang khi có state đã lưu trong sessionStorage
- **Property (P)**: Hành vi mong đợi — hiển thị ngay lập tức dữ liệu đã lưu, sau đó đồng bộ với API
- **Preservation**: Hành vi không thay đổi — first load, error handling, navigation context, scan polling
- **`AnalysisPage`**: Object trong `AnalysisPage.kt` — controller trang Project Analysis
- **`KnowledgeGraphPage`**: Object trong `KnowledgeGraphPage.kt` — controller trang Relationship Network
- **`AnalysisStateManager`**: Object trong `AnalysisStateManager.kt` — lưu/khôi phục `ProjectAnalysisResponse` từ sessionStorage
- **`GraphStateManager`**: Object trong `GraphStateManager.kt` — lưu/khôi phục `GraphLayoutResponse` từ sessionStorage
- **`populateGraphStateFromResponse()`**: Helper method trong `KnowledgeGraphPage` — populate `GraphState` fields từ `GraphLayoutResponse` (DRY cho cả immediate restore và API load)
- **`GraphState`**: Object trong `GraphState.kt` — shared mutable state cho nodes, edges, clusters, filters
- **`CytoscapeRenderer`**: Object trong `CytoscapeRenderer.kt` — render đồ thị Cytoscape.js
- **`AnalysisScanStatus`**: Object trong `AnalysisScanStatus.kt` — polling scan status và analysis data
- **`GraphScanStatus`**: Object trong `GraphScanStatus.kt` — polling scan status và diff-based progressive graph loading
- **`NavigationContext`**: Service quản lý navigation params từ ChatAction (ưu tiên hơn state restore)

## Bug Details

### Bug Condition

Ba lỗi có điều kiện kích hoạt khác nhau nhưng cùng root cause pattern:

**Bug 1 — Project Analysis: Khôi phục trạng thái chậm:**
Lỗi xảy ra khi quay lại trang Project Analysis và có dữ liệu đã xem trước đó. Hàm `render()` gọi `container.innerHTML = ""` rồi `loadAnalysisData()` — API call async mất 500ms-3s. Trong thời gian chờ, trang hiển thị template HTML trống (metrics = "0", chart trống, radar trống).

**Formal Specification:**
```
FUNCTION isBugCondition_AnalysisSlowRestore(input)
  INPUT: input of type PageNavigation
  OUTPUT: boolean

  savedState := sessionStorage.getItem("analysis_page_state")
  RETURN savedState != null
         AND input.isReturningToPage = true
         AND input.targetPage = "analysis"
END FUNCTION
```

**Bug 2 — Relationship Network: Khôi phục trạng thái chậm:**
Lỗi xảy ra khi quay lại trang Relationship Network và có đồ thị đã xem trước đó. Hàm `render()` gọi `GraphState.reset()` xóa toàn bộ nodes/edges, `CytoscapeRenderer.destroy()` hủy Cytoscape instance, rồi `loadGraphData()` gọi API — mất 1-5s tùy kích thước đồ thị. Đặc biệt chậm với đồ thị lớn (>100 nodes) vì phải re-render toàn bộ Cytoscape.

**Formal Specification:**
```
FUNCTION isBugCondition_GraphSlowRestore(input)
  INPUT: input of type PageNavigation
  OUTPUT: boolean

  savedState := sessionStorage.getItem("knowledge_graph_state")
  RETURN savedState != null
         AND input.isReturningToPage = true
         AND input.targetPage = "knowledge_graph"
         AND NOT NavigationContext.hasContext("knowledge_graph")
END FUNCTION
```

**Bug 3 — Project Analysis: Không lưu kết quả polling vào sessionStorage:**
Khi scan đang chạy, `AnalysisScanStatus.startAnalysisPolling()` gọi `AnalysisPage.loadAnalysisData()` mỗi 5 giây. Hàm `loadAnalysisData()` decode `ProjectAnalysisResponse` và render metrics/chart/radar — nhưng KHÔNG lưu response vào sessionStorage. Nếu người dùng chuyển trang rồi quay lại, phải chờ API load lại từ đầu.

**Formal Specification:**
```
FUNCTION isBugCondition_AnalysisNoProgressiveLoad(input)
  INPUT: input of type ScanEvent
  OUTPUT: boolean

  RETURN input.scanStatus = SCANNING
         AND input.targetPage = "analysis"
         AND input.partialAnalysisDataAvailable = true
         AND sessionStorage.getItem("analysis_page_state") = null OR stale
END FUNCTION
```

### Examples

- **Bug 1**: Người dùng xem Project Analysis (metrics: 42 tickets, 85% resolution, velocity chart 5 sprints), chuyển sang Dashboard, quay lại → trang trống 1-3s chờ API → metrics hiện lại
- **Bug 1 — Mong đợi**: Quay lại → metrics hiển thị ngay "42 tickets, 85%, velocity chart" từ sessionStorage → API trả về → cập nhật nếu có thay đổi
- **Bug 2**: Người dùng xem đồ thị 150 nodes, chuyển sang Ticket Intelligence, quay lại → đồ thị trống 2-5s chờ API + Cytoscape re-render
- **Bug 2 — Mong đợi**: Quay lại → đồ thị hiển thị ngay 150 nodes từ sessionStorage → API trả về → cập nhật diff nếu có
- **Bug 3**: Scan đang chạy, Analysis hiển thị 20 tickets analyzed, người dùng chuyển trang → quay lại → trang trống chờ API → hiện lại 20 tickets (hoặc nhiều hơn nếu scan tiếp tục)
- **Bug 3 — Mong đợi**: Chuyển trang → quay lại → hiển thị ngay 20 tickets từ sessionStorage → polling tiếp tục cập nhật

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Trang Project Analysis load lần đầu (không có state) phải gọi API và hiển thị như hiện tại
- Trang Relationship Network load lần đầu phải gọi API, render Cytoscape, populate filter panel như hiện tại
- API error handling (hiển thị lỗi + nút retry) phải giữ nguyên trên cả hai trang
- Navigation context từ ChatAction trên Relationship Network phải ưu tiên hơn state restore (auto-focus node)
- Incremental graph rendering qua `GraphScanStatus` (diff-based progressive loading) phải giữ nguyên
- Nút "Dive into Reports", retry, integrations trên Project Analysis phải hoạt động như hiện tại
- Filter panel, search, detail panel trên Relationship Network phải hoạt động như hiện tại
- Scan status badge polling trên cả hai trang phải giữ nguyên

**Scope:**
Tất cả tương tác KHÔNG liên quan đến quay lại trang với state đã lưu phải hoàn toàn không bị ảnh hưởng bởi fix.

## Hypothesized Root Cause

### Bug 1 — AnalysisPage thiếu cơ chế lưu/khôi phục state (ĐÃ FIX)

Trong `AnalysisPage.render()`, flow sau khi fix:
```kotlin
fun render(container: Element) {
    container.innerHTML = ""
    cleanup()
    scope.launch {
        val html = ApiClient.loadTemplate("analysis")
        container.innerHTML = html
        bindEvents()
        immediateRestoreFromSession()     // ← Phase 1: hiển thị ngay từ sessionStorage
        loadAnalysisData()                // ← Phase 2: đồng bộ với API
        AnalysisScanStatus.loadScanStatus()
    }
}
```

Fix đã thêm `AnalysisStateManager` và `immediateRestoreFromSession()`. `loadAnalysisData()` giờ gọi `AnalysisStateManager.save(data)` sau mỗi API response thành công.

**Root cause đã xác nhận**: Không có `AnalysisStateManager` tương đương `TicketStateManager`. Không có `immediateRestoreFromSession()`. `loadAnalysisData()` không lưu response vào sessionStorage.

### Bug 2 — KnowledgeGraphPage reset toàn bộ state khi re-render (ĐÃ FIX)

Trong `KnowledgeGraphPage.render()`, flow sau khi fix:
```kotlin
fun render(container: Element) {
    container.innerHTML = ""
    cleanup()                         // ← CytoscapeRenderer.destroy() + cancel jobs
    // GraphState.reset() đã được loại bỏ khỏi render()
    scope.launch {
        // ... load template ...
        GraphFilterPanel.init()
        GraphSearchCombobox.init()
        bindEvents()
        immediateRestoreFromSession()  // ← Phase 1: hiển thị đồ thị ngay từ sessionStorage
        loadGraphData()                // ← Phase 2: đồng bộ với API
        GraphScanStatus.loadScanStatus()
    }
}
```

Fix đã thêm `GraphStateManager`, `immediateRestoreFromSession()` (skip khi có NavigationContext), và `populateGraphStateFromResponse()` helper. `loadGraphData()` giờ gọi `GraphStateManager.save(graphData)` sau mỗi API response thành công. `GraphState.reset()` đã được loại bỏ khỏi `render()` — `loadGraphData()` overwrite `GraphState` khi API trả về.

**Root cause đã xác nhận**: `GraphState.reset()` xóa toàn bộ in-memory state. `CytoscapeRenderer.destroy()` hủy Cytoscape instance. Không có `GraphStateManager` hay `immediateRestoreFromSession()`.

### Bug 3 — AnalysisScanStatus polling không lưu kết quả (ĐÃ FIX)

`loadAnalysisData()` giờ gọi `AnalysisStateManager.save(data)` sau mỗi API response thành công — bao gồm cả khi được gọi từ polling. Tương tự, `GraphScanStatus.applyIncrementalUpdate()` giờ gọi `GraphStateManager.save(graphData)` sau mỗi incremental update.

**Root cause đã xác nhận**: `loadAnalysisData()` không lưu `ProjectAnalysisResponse` vào sessionStorage. Mỗi lần polling nhận dữ liệu mới nhưng không persist.

## Correctness Properties

Property 1: Bug Condition - Project Analysis hiển thị ngay từ sessionStorage

_For any_ page navigation event quay lại trang Project Analysis mà có `ProjectAnalysisResponse` đã lưu trong sessionStorage (isBugCondition_AnalysisSlowRestore returns true), hệ thống SHALL hiển thị ngay lập tức metrics (totalTickets, resolutionRate, cycleTimeDays, aiVelocity), velocity chart, và bottleneck radar từ dữ liệu đã lưu — TRƯỚC khi gọi API. Khi API trả về, hệ thống SHALL cập nhật UI với dữ liệu mới.

**Validates: Requirements 2.1, 2.4, 2.6**

Property 2: Bug Condition - Relationship Network hiển thị đồ thị ngay từ sessionStorage

_For any_ page navigation event quay lại trang Relationship Network mà có `GraphLayoutResponse` đã lưu trong sessionStorage VÀ không có NavigationContext (isBugCondition_GraphSlowRestore returns true), hệ thống SHALL hiển thị ngay lập tức đồ thị Cytoscape với nodes, edges, clusters, filter panel từ dữ liệu đã lưu — TRƯỚC khi gọi API. Khi API trả về, hệ thống SHALL cập nhật đồ thị với dữ liệu mới (diff-based).

**Validates: Requirements 2.2, 2.5, 2.6**

Property 3: Bug Condition - Analysis polling lưu kết quả vào sessionStorage

_For any_ API response từ `loadAnalysisData()` (dù từ initial load hay polling khi scan), hệ thống SHALL lưu `ProjectAnalysisResponse` vào sessionStorage qua `AnalysisStateManager.save()` — đảm bảo dữ liệu progressive loading được persist cho immediate restore.

**Validates: Requirements 2.3, 2.4**

Property 4: Preservation - Hành vi hiện tại không thay đổi

_For any_ tương tác mà KHÔNG thuộc bug condition (first load, API error, navigation context, scan polling flow, filter/search, retry), hệ thống SHALL hoạt động giống hệt code hiện tại, bảo toàn toàn bộ flow load dữ liệu, error handling, và UI interactions.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8**

## Fix Implementation (ĐÃ HOÀN THÀNH)

### Changes Implemented

Fix theo pattern đã implement trên Ticket Intelligence:

---

**File MỚI**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/analysis/AnalysisStateManager.kt`

**Mục đích**: Lưu/khôi phục `ProjectAnalysisResponse` từ sessionStorage (tương tự `TicketStateManager`)

**Specific Changes:**
1. **Object `AnalysisStateManager`** với `STORAGE_KEY = "analysis_page_state"`
2. **`save(data: ProjectAnalysisResponse)`**: Serialize `ProjectAnalysisResponse` → JSON → `sessionStorage.setItem()`
3. **`restore(): ProjectAnalysisResponse?`**: `sessionStorage.getItem()` → deserialize → return null nếu không có hoặc parse fail
4. **`clear()`**: `sessionStorage.removeItem()`
5. **Shared `Json` instance** với `ignoreUnknownKeys = true`, `encodeDefaults = true`, `isLenient = true`

---

**File MỚI**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/graph/GraphStateManager.kt`

**Mục đích**: Lưu/khôi phục `GraphLayoutResponse` từ sessionStorage (tương tự `TicketStateManager`)

**Specific Changes:**
1. **Object `GraphStateManager`** với `STORAGE_KEY = "knowledge_graph_state"`
2. **`save(data: GraphLayoutResponse)`**: Serialize `GraphLayoutResponse` → JSON → `sessionStorage.setItem()`
3. **`restore(): GraphLayoutResponse?`**: `sessionStorage.getItem()` → deserialize → return null nếu không có hoặc parse fail
4. **`clear()`**: `sessionStorage.removeItem()`
5. **Shared `Json` instance** với `ignoreUnknownKeys = true`, `encodeDefaults = true`, `isLenient = true`
6. **Lưu ý**: `GraphLayoutResponse` có thể lớn (>100 nodes) — sessionStorage limit ~5MB, đủ cho hầu hết đồ thị

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/AnalysisPage.kt`

**Function**: `render()`, `loadAnalysisData()`, thêm `immediateRestoreFromSession()`

**Specific Changes:**

1. **Thêm `immediateRestoreFromSession()`**: Đọc `ProjectAnalysisResponse` từ `AnalysisStateManager.restore()`. Nếu có dữ liệu, gọi `renderMetrics(data)`, `AnalysisVelocityChart.render(data.velocityTrend)`, `AnalysisBottleneckRadar.render(data.bottlenecks)` ngay lập tức.

2. **Cập nhật `render()` flow**: Thêm `immediateRestoreFromSession()` SAU `bindEvents()` và TRƯỚC `loadAnalysisData()`:
   ```
   bindEvents()
   immediateRestoreFromSession()    // ← Phase 1: hiển thị ngay từ sessionStorage
   loadAnalysisData()               // ← Phase 2: đồng bộ với API
   AnalysisScanStatus.loadScanStatus()
   ```

3. **Cập nhật `loadAnalysisData()`**: Sau khi decode `ProjectAnalysisResponse` thành công, gọi `AnalysisStateManager.save(data)` để lưu vào sessionStorage — phục vụ cả immediate restore và progressive loading persistence.

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/KnowledgeGraphPage.kt`

**Function**: `render()`, `loadGraphData()`, thêm `immediateRestoreFromSession()`

**Specific Changes:**

1. **Thêm `immediateRestoreFromSession()`**: Đọc `GraphLayoutResponse` từ `GraphStateManager.restore()`. Nếu có dữ liệu, populate `GraphState` (allNodes, allEdges, allClusters, allNodeTypes, typeColorMap, filteredNodeIds), gọi `CytoscapeRenderer.renderGraph()`, `GraphFilterPanel.populateNodeTypes()`, `GraphFilterPanel.populateClusters()`, `updateNodeCount()`.

2. **Cập nhật `render()` flow**: Thêm `immediateRestoreFromSession()` SAU `bindEvents()` và TRƯỚC `loadGraphData()`:
   ```
   GraphFilterPanel.init()
   GraphSearchCombobox.init()
   bindEvents()
   immediateRestoreFromSession()    // ← Phase 1: hiển thị đồ thị ngay từ sessionStorage
   loadGraphData()                  // ← Phase 2: đồng bộ với API
   GraphScanStatus.loadScanStatus()
   ```

3. **Cập nhật `loadGraphData()`**: Sau khi decode `GraphLayoutResponse` thành công và populate `GraphState`, gọi `GraphStateManager.save(graphData)` để lưu vào sessionStorage.

4. **Điều chỉnh `GraphState.reset()` timing**: KHÔNG gọi `GraphState.reset()` ở đầu `render()` nữa — thay vào đó, `loadGraphData()` sẽ overwrite `GraphState` khi API trả về. `immediateRestoreFromSession()` cần `GraphState` chưa bị reset để populate từ sessionStorage. `CytoscapeRenderer.destroy()` vẫn gọi trong `cleanup()` nhưng `immediateRestoreFromSession()` sẽ tạo instance mới ngay lập tức.

5. **Navigation context ưu tiên**: Nếu `NavigationContext.hasContext("knowledge_graph")` trả về true, SKIP `immediateRestoreFromSession()` — để `applyNavigationContext()` trong `loadGraphData()` xử lý auto-focus node.

---

**File**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/graph/GraphScanStatus.kt`

**Function**: `applyIncrementalUpdate()`

**Specific Changes:**

1. **Lưu graph data sau mỗi incremental update**: Sau khi `updateGraphState(graphData)`, gọi `GraphStateManager.save(graphData)` — đảm bảo progressive loading data được persist.

## Testing Strategy (ĐÃ HOÀN THÀNH)

### Validation Approach

Chiến lược testing theo 2 phase đã thực hiện thành công: (1) xác nhận lỗi tồn tại trên code chưa fix bằng exploratory tests, (2) xác nhận fix hoạt động đúng và không gây regression.

### Test Files

- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/analysis/AnalysisSlowRestoreBugTest.kt` — Bug 1 & Bug 3 exploration (3 tests)
- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/graph/GraphSlowRestoreBugTest.kt` — Bug 2 exploration (2 tests)
- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/analysis/AnalysisPreservationTest.kt` — Analysis preservation (4 tests)
- `frontend/src/jsTest/kotlin/com/assistant/frontend/pages/graph/GraphPreservationTest.kt` — Graph preservation (6 tests)

### Kết quả: 153 tests, 0 failures, 0 errors — tất cả exploration tests PASS (bugs đã fix), tất cả preservation tests PASS (không regression).

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples chứng minh lỗi tồn tại TRƯỚC khi implement fix. Xác nhận hoặc bác bỏ root cause analysis.

**Test Plan**: Viết tests mô phỏng quay lại trang khi có state trong sessionStorage, kiểm tra xem dữ liệu có hiển thị ngay lập tức không. Chạy trên code CHƯA fix để quan sát failures.

**Test Cases**:
1. **Analysis Slow Restore Test**: Lưu `ProjectAnalysisResponse` vào sessionStorage, gọi `AnalysisPage.render()` → kiểm tra metrics có hiển thị ngay không (sẽ fail: metrics trống chờ API)
2. **Graph Slow Restore Test**: Lưu `GraphLayoutResponse` vào sessionStorage, gọi `KnowledgeGraphPage.render()` → kiểm tra đồ thị có render ngay không (sẽ fail: `GraphState.reset()` xóa data, đồ thị trống)
3. **Analysis Polling Persistence Test**: Gọi `loadAnalysisData()` thành công → kiểm tra sessionStorage có chứa data không (sẽ fail: không có `AnalysisStateManager.save()`)

**Expected Counterexamples**:
- Bug 1: Sau `render()`, metrics elements có `textContent` = "0" hoặc placeholder, không phải giá trị đã lưu
- Bug 2: Sau `render()`, `GraphState.allNodes` = `emptyList()` dù sessionStorage có data
- Bug 3: Sau `loadAnalysisData()`, `sessionStorage.getItem("analysis_page_state")` = null

### Fix Checking

**Goal**: Xác nhận fix hoạt động đúng cho tất cả inputs thuộc bug condition.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition_AnalysisSlowRestore(input) DO
  result := renderAnalysisPage_fixed(input)
  ASSERT result.metricsDisplayedImmediately = true
  ASSERT result.velocityChartRendered = true
  ASSERT result.bottleneckRadarRendered = true
END FOR

FOR ALL input WHERE isBugCondition_GraphSlowRestore(input) DO
  result := renderGraphPage_fixed(input)
  ASSERT result.graphRenderedImmediately = true
  ASSERT result.filterPanelPopulated = true
  ASSERT result.nodeCountUpdated = true
END FOR

FOR ALL input WHERE isBugCondition_AnalysisNoProgressiveLoad(input) DO
  result := loadAnalysisData_fixed(input)
  ASSERT sessionStorage.getItem("analysis_page_state") != null
END FOR
```

### Preservation Checking

**Goal**: Xác nhận fix không ảnh hưởng hành vi hiện tại cho inputs KHÔNG thuộc bug condition.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition_AnalysisSlowRestore(input)
                AND NOT isBugCondition_GraphSlowRestore(input)
                AND NOT isBugCondition_AnalysisNoProgressiveLoad(input) DO
  ASSERT originalBehavior(input) = fixedBehavior(input)
END FOR
```

**Testing Approach**: Property-based testing được khuyến nghị cho preservation checking vì:
- Tự động sinh nhiều test cases trên toàn bộ input domain
- Phát hiện edge cases mà unit tests thủ công có thể bỏ sót
- Đảm bảo mạnh mẽ rằng hành vi không thay đổi cho tất cả non-buggy inputs

**Test Plan**: Quan sát hành vi trên code chưa fix cho first load, error handling, navigation context, sau đó viết property-based tests xác nhận hành vi giữ nguyên sau fix.

**Test Cases**:
1. **First Load Preservation**: Xác nhận trang load lần đầu (sessionStorage trống) vẫn gọi API và hiển thị dữ liệu bình thường
2. **API Error Preservation**: Xác nhận khi API fail, hiển thị lỗi + nút retry trên cả hai trang
3. **Navigation Context Preservation**: Xác nhận navigate từ ChatAction vẫn auto-focus node trên Relationship Network
4. **Graph Scan Status Preservation**: Xác nhận incremental graph rendering qua `GraphScanStatus` vẫn hoạt động (diff-based progressive loading)
5. **Filter/Search Preservation**: Xác nhận filter panel, search, detail panel trên Relationship Network vẫn hoạt động

### Unit Tests

- Test `AnalysisStateManager.save()` serialize `ProjectAnalysisResponse` đúng vào sessionStorage
- Test `AnalysisStateManager.restore()` deserialize đúng từ sessionStorage
- Test `AnalysisStateManager.restore()` trả về null khi sessionStorage trống hoặc JSON invalid
- Test `GraphStateManager.save()` serialize `GraphLayoutResponse` đúng vào sessionStorage
- Test `GraphStateManager.restore()` deserialize đúng từ sessionStorage
- Test `GraphStateManager.restore()` trả về null khi sessionStorage trống hoặc JSON invalid
- Test `immediateRestoreFromSession()` trên AnalysisPage render metrics đúng từ saved data
- Test `immediateRestoreFromSession()` trên KnowledgeGraphPage populate GraphState đúng từ saved data
- Test `loadAnalysisData()` lưu response vào sessionStorage sau khi decode thành công
- Test `loadGraphData()` lưu response vào sessionStorage sau khi decode thành công

### Property-Based Tests

- Sinh random `ProjectAnalysisResponse` với các giá trị metrics khác nhau, xác nhận save/restore roundtrip giữ nguyên tất cả fields
- Sinh random `GraphLayoutResponse` với số lượng nodes/edges khác nhau, xác nhận save/restore roundtrip giữ nguyên tất cả fields
- Sinh random page navigation events (first load vs returning), xác nhận immediate restore chỉ kích hoạt khi có saved state
- Sinh random navigation contexts, xác nhận navigation context ưu tiên hơn state restore trên Relationship Network

### Integration Tests

- Test full flow: xem Analysis → chuyển trang → quay lại → metrics hiển thị ngay → API load → cập nhật
- Test full flow: xem Graph → chuyển trang → quay lại → đồ thị hiển thị ngay → API load → cập nhật diff
- Test full flow: scan đang chạy → Analysis polling → lưu sessionStorage → chuyển trang → quay lại → hiển thị ngay dữ liệu partial
- Test full flow: navigate từ ChatAction → auto-focus node (skip state restore)
- Test full flow: API fail → hiển thị lỗi → retry → load thành công
