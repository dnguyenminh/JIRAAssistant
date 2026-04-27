# Tài liệu Yêu cầu Sửa lỗi — Page Slow Restore Fix

## Giới thiệu

Hai trang **Project Analysis** (`AnalysisPage`) và **Relationship Network** (`KnowledgeGraphPage`) bị chậm khi người dùng chuyển sang trang khác rồi quay lại — giống hệt lỗi đã fix trên trang Ticket Intelligence (xem spec `ticket-intelligence-ux-bugs`). Hiện tại cả hai trang đều gọi API mỗi lần render, không có cơ chế lưu/khôi phục state từ sessionStorage, khiến người dùng phải chờ API trả về mới thấy dữ liệu.

Ngoài ra, trang Project Analysis cần hiển thị kết quả phân tích ngay khi scan đang chạy (progressive loading), tương tự cách trang Relationship Network đã implement incremental graph rendering qua `GraphScanStatus`.

**Pattern fix đã chứng minh hiệu quả trên Ticket Intelligence:**
- **Phase 1 (immediate)**: Đọc state từ sessionStorage và hiển thị ngay lập tức
- **Phase 2 (deferred)**: Đồng bộ với API response khi có dữ liệu mới

## Phân tích Lỗi

### Hành vi Hiện tại (Lỗi)

1.1 WHEN người dùng đang ở trang Project Analysis (đã có dữ liệu metrics, velocity chart, bottleneck radar), chuyển sang trang khác rồi quay lại THEN hệ thống xóa toàn bộ nội dung (`container.innerHTML = ""`), gọi API `/api/projects/{key}/analysis` và hiển thị trang trống cho đến khi API trả về — gây delay đáng kể và trải nghiệm UX kém

1.2 WHEN người dùng đang ở trang Relationship Network (đã có đồ thị ticket network), chuyển sang trang khác rồi quay lại THEN hệ thống gọi `GraphState.reset()` xóa toàn bộ nodes/edges, gọi `CytoscapeRenderer.destroy()`, rồi gọi API `/api/graph/{key}` và render lại đồ thị từ đầu — gây delay đáng kể, đặc biệt với đồ thị lớn (>100 nodes)

1.3 WHEN scan đang chạy và người dùng ở trang Project Analysis THEN hệ thống chỉ hiển thị dữ liệu phân tích sau khi scan hoàn tất — không có progressive loading như trang Relationship Network (nơi đồ thị được vẽ ngay khi ticket đầu tiên được analyze)

1.4 WHEN scan đang chạy và `AnalysisScanStatus` poll dữ liệu mới THEN hệ thống gọi `loadAnalysisData()` mỗi 5 giây nhưng KHÔNG lưu kết quả vào sessionStorage — nếu người dùng chuyển trang rồi quay lại, phải chờ API load lại từ đầu

### Hành vi Mong đợi (Đúng)

2.1 WHEN người dùng đã xem trang Project Analysis (có dữ liệu), chuyển sang trang khác rồi quay lại THEN hệ thống SHALL hiển thị ngay lập tức dữ liệu đã lưu từ sessionStorage (metrics, velocity chart, bottleneck radar) mà không cần chờ API — sau đó đồng bộ với API response ở background

2.2 WHEN người dùng đã xem trang Relationship Network (có đồ thị), chuyển sang trang khác rồi quay lại THEN hệ thống SHALL hiển thị ngay lập tức đồ thị đã lưu từ sessionStorage (nodes, edges, clusters, filter state) mà không cần chờ API — sau đó đồng bộ với API response ở background

2.3 WHEN scan đang chạy và người dùng ở trang Project Analysis THEN hệ thống SHALL hiển thị kết quả phân tích ngay khi có dữ liệu từ API (progressive loading) — tương tự cách `GraphScanStatus` hiển thị đồ thị ngay khi ticket đầu tiên được analyze, không cần chờ scan hoàn tất

2.4 WHEN hệ thống nhận được dữ liệu phân tích mới từ API (dù từ load ban đầu hay polling) THEN hệ thống SHALL lưu `ProjectAnalysisResponse` vào sessionStorage để phục vụ immediate restore khi quay lại trang

2.5 WHEN hệ thống nhận được dữ liệu đồ thị mới từ API THEN hệ thống SHALL lưu `GraphLayoutResponse` vào sessionStorage để phục vụ immediate restore khi quay lại trang

2.6 WHEN hệ thống restore từ sessionStorage và sau đó API trả về dữ liệu mới hơn THEN hệ thống SHALL cập nhật UI với dữ liệu mới từ API — sessionStorage chỉ là optimistic display, API response là source of truth

### Hành vi Không thay đổi (Ngăn Regression)

3.1 WHEN trang Project Analysis load lần đầu (không có state đã lưu trong sessionStorage) THEN hệ thống SHALL CONTINUE TO gọi API và hiển thị dữ liệu như hiện tại

3.2 WHEN trang Relationship Network load lần đầu (không có state đã lưu) THEN hệ thống SHALL CONTINUE TO gọi API, render đồ thị Cytoscape, populate filter panel và node types như hiện tại

3.3 WHEN API load dữ liệu phân tích thất bại THEN hệ thống SHALL CONTINUE TO hiển thị thông báo lỗi với nút retry trên trang Project Analysis

3.4 WHEN API load dữ liệu đồ thị thất bại THEN hệ thống SHALL CONTINUE TO hiển thị thông báo lỗi với nút retry trên trang Relationship Network

3.5 WHEN scan đang chạy trên trang Relationship Network THEN hệ thống SHALL CONTINUE TO thực hiện incremental graph rendering qua `GraphScanStatus` (diff-based progressive loading) như hiện tại

3.6 WHEN người dùng navigate đến trang Relationship Network qua ChatAction với nodeKey context THEN hệ thống SHALL CONTINUE TO auto-focus vào node tương ứng (navigation context ưu tiên hơn state restore)

3.7 WHEN người dùng click nút retry, nút "Dive into Reports", hoặc các nút điều hướng trên trang Project Analysis THEN hệ thống SHALL CONTINUE TO hoạt động như hiện tại

3.8 WHEN người dùng tương tác với filter panel, search, hoặc detail panel trên trang Relationship Network THEN hệ thống SHALL CONTINUE TO hoạt động như hiện tại

---

## Điều kiện Lỗi (Bug Condition)

### Bug 1 — Project Analysis: Khôi phục trạng thái chậm

```pascal
FUNCTION isBugCondition_AnalysisSlowRestore(X)
  INPUT: X of type PageNavigation
  OUTPUT: boolean
  
  // Lỗi xảy ra khi quay lại trang Project Analysis và có dữ liệu đã lưu
  savedState := sessionStorage.getItem("analysis_page_state")
  RETURN savedState != null
         AND X.isReturningToPage = true
         AND X.targetPage = "analysis"
END FUNCTION
```

```pascal
// Property: Fix Checking — Hiển thị ngay lập tức từ sessionStorage
FOR ALL X WHERE isBugCondition_AnalysisSlowRestore(X) DO
  result ← renderAnalysisPage'(X)
  ASSERT result.metricsDisplayedImmediately = true
    AND result.velocityChartDisplayedImmediately = true
    AND result.bottleneckRadarDisplayedImmediately = true
    AND result.noWaitForApiLoad = true
END FOR
```

```pascal
// Property: Preservation Checking — Trang load lần đầu không bị ảnh hưởng
FOR ALL X WHERE NOT isBugCondition_AnalysisSlowRestore(X) DO
  ASSERT renderAnalysisPage(X) = renderAnalysisPage'(X)
END FOR
```

### Bug 2 — Relationship Network: Khôi phục trạng thái chậm

```pascal
FUNCTION isBugCondition_GraphSlowRestore(X)
  INPUT: X of type PageNavigation
  OUTPUT: boolean
  
  // Lỗi xảy ra khi quay lại trang Relationship Network và có dữ liệu đã lưu
  savedState := sessionStorage.getItem("knowledge_graph_state")
  RETURN savedState != null
         AND X.isReturningToPage = true
         AND X.targetPage = "knowledge_graph"
         AND NOT NavigationContext.hasContext("knowledge_graph")
END FUNCTION
```

```pascal
// Property: Fix Checking — Hiển thị đồ thị ngay lập tức từ sessionStorage
FOR ALL X WHERE isBugCondition_GraphSlowRestore(X) DO
  result ← renderGraphPage'(X)
  ASSERT result.graphRenderedImmediately = true
    AND result.nodesDisplayed = savedState.nodes
    AND result.edgesDisplayed = savedState.edges
    AND result.noWaitForApiLoad = true
END FOR
```

```pascal
// Property: Preservation Checking — Navigation context vẫn ưu tiên
FOR ALL X WHERE NOT isBugCondition_GraphSlowRestore(X) DO
  ASSERT renderGraphPage(X) = renderGraphPage'(X)
END FOR
```

### Bug 3 — Project Analysis: Không có progressive loading khi scan đang chạy

```pascal
FUNCTION isBugCondition_AnalysisNoProgressiveLoad(X)
  INPUT: X of type ScanEvent
  OUTPUT: boolean
  
  // Lỗi xảy ra khi scan đang chạy nhưng trang Analysis không hiển thị kết quả partial
  RETURN X.scanStatus = SCANNING
         AND X.targetPage = "analysis"
         AND X.partialAnalysisDataAvailable = true
END FUNCTION
```

```pascal
// Property: Fix Checking — Hiển thị kết quả ngay khi có dữ liệu
FOR ALL X WHERE isBugCondition_AnalysisNoProgressiveLoad(X) DO
  result ← pollAnalysis'(X)
  ASSERT result.metricsUpdated = true
    AND result.savedToSessionStorage = true
END FOR
```

```pascal
// Property: Preservation Checking — Scan polling vẫn hoạt động
FOR ALL X WHERE NOT isBugCondition_AnalysisNoProgressiveLoad(X) DO
  ASSERT pollAnalysis(X) = pollAnalysis'(X)
END FOR
```
