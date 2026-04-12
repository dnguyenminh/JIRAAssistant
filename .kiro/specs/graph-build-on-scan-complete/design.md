# Graph Build on Scan Complete — Bugfix Design

## Overview

Sau khi scan hoàn tất, hàm `completeScan()` trong `BatchScanEngine.kt` chỉ cập nhật trạng thái thành COMPLETED mà không gọi `buildAndSaveGraph(projectKey)` — hàm đã tồn tại trong `BatchScanTicketProcessor.kt`. Kết quả: trang Knowledge Graph hiển thị "No graph data yet" dù scan đã thành công. Fix: thêm lời gọi `buildAndSaveGraph(projectKey)` vào `completeScan()` trước khi set status COMPLETED, với error handling đảm bảo lỗi graph build không chặn scan completion.

## Glossary

- **Bug_Condition (C)**: Scan hoàn tất thành công (tất cả ticket đã xử lý) nhưng `buildAndSaveGraph()` không được gọi → `kbRepository.getGraphData(projectKey)` trả về null
- **Property (P)**: Khi scan hoàn tất, `buildAndSaveGraph()` phải được gọi trước khi status chuyển sang COMPLETED, và graph data phải được lưu vào KB
- **Preservation**: Toàn bộ hành vi hiện tại của ticket processing, scan state management (pause/resume/cancel), và empty project handling phải không thay đổi
- **`completeScan()`**: Hàm private trong `BatchScanEngine.kt` (dòng 201-205) được gọi khi `scanLoop` xử lý xong tất cả ticket — hiện chỉ set status COMPLETED và remove active job
- **`buildAndSaveGraph()`**: Extension function trong `BatchScanTicketProcessor.kt` — fetch issues từ Jira, build `NetworkGraph` qua `FeatureNetworkMapper.map()`, lưu qua `kbRepository.saveGraphData()`
- **`scanLoop()`**: Hàm internal trong `BatchScanEngine.kt` — vòng lặp xử lý batch ticket, gọi `completeScan()` khi hoàn tất

## Bug Details

### Bug Condition

Bug xảy ra khi scan hoàn tất thành công: `scanLoop()` xử lý hết tất cả ticket và gọi `completeScan()`. Hàm `completeScan()` chỉ cập nhật `ScanState.status = COMPLETED` và xóa active job, nhưng không gọi `buildAndSaveGraph(projectKey)` để xây dựng và lưu `NetworkGraph`.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ScanCompletionEvent { projectKey: String, processedCount: Int, totalTickets: Int }
  OUTPUT: boolean
  
  RETURN input.processedCount == input.totalTickets
         AND input.totalTickets > 0
         AND kbRepository.getGraphData(input.projectKey) == null
END FUNCTION
```

### Examples

- User scan project "ITCM" với 42 ticket → scan hoàn tất 42/42 → status = COMPLETED → user mở Knowledge Graph → "No graph data yet" (Expected: hiển thị graph với nodes và edges)
- User scan project "PROJ" với 5 ticket → scan hoàn tất 5/5 → API `GET /api/graph/PROJ` trả về 404 (Expected: trả về 200 với NetworkGraph data)
- User scan project "DEMO" với 1 ticket → scan hoàn tất 1/1 → `kbRepository.getGraphData("DEMO")` trả về null (Expected: trả về NetworkGraph với ít nhất 1 node)
- Edge case: Project "EMPTY" với 0 ticket → `handleEmptyProject()` xử lý → KHÔNG nên gọi `buildAndSaveGraph()` (Expected: giữ nguyên behavior hiện tại)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Ticket processing pipeline: AI analysis → KB save → relationship logging → attachment processing phải tiếp tục hoạt động chính xác như hiện tại
- Scan state management: pause, resume, cancel phải tiếp tục cập nhật trạng thái và quản lý active jobs đúng cách
- Empty project handling: project không có ticket phải tiếp tục set COMPLETED và log "No tickets found" mà không gọi `buildAndSaveGraph()`
- Scan conflict detection: không cho phép 2 scan chạy đồng thời trên cùng project

**Scope:**
Tất cả input không liên quan đến thời điểm scan completion (ticket processing, state transitions, empty projects) phải hoàn toàn không bị ảnh hưởng bởi fix này. Cụ thể:
- `processTicket()` — không thay đổi
- `processBatchParallel()` — không thay đổi
- `pauseScan()`, `resumeScan()`, `cancelScan()` — không thay đổi
- `handleEmptyProject()` — không thay đổi
- `startScan()`, `launchScan()` — không thay đổi

## Hypothesized Root Cause

Based on code analysis, root cause đã được xác nhận rõ ràng:

1. **Missing function call**: `buildAndSaveGraph(projectKey)` đã được implement đầy đủ trong `BatchScanTicketProcessor.kt` (extension function trên `BatchScanEngine`) nhưng không bao giờ được gọi từ bất kỳ đâu trong codebase. Hàm `completeScan()` trong `BatchScanEngine.kt` (dòng 201-205) chỉ chứa:
   ```kotlin
   private suspend fun completeScan(projectKey: String) {
       val current = scanStateRepository.findByProjectKey(projectKey) ?: return
       scanStateRepository.save(current.copy(status = ScanStatus.COMPLETED, currentTicketId = null, updatedAt = Clock.System.now().toString()))
       activeJobs.remove(projectKey)
   }
   ```

2. **Thiếu error handling cho graph build**: Ngay cả khi thêm lời gọi `buildAndSaveGraph()`, cần đảm bảo exception từ graph building (Jira unavailable, mapping error) không làm crash `completeScan()` và ngăn scan chuyển sang COMPLETED.

## Correctness Properties

Property 1: Bug Condition - Graph được xây dựng khi scan hoàn tất

_For any_ scan completion event nơi tất cả ticket đã được xử lý thành công (processedCount == totalTickets AND totalTickets > 0), hàm `completeScan()` đã fix SHALL gọi `buildAndSaveGraph(projectKey)` và sau đó `kbRepository.getGraphData(projectKey)` SHALL trả về non-null NetworkGraph.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition - Graph build failure không chặn scan completion

_For any_ scan completion event nơi `buildAndSaveGraph()` throw exception (Jira unavailable, mapping error, DB write failure), hàm `completeScan()` đã fix SHALL vẫn set `ScanState.status = COMPLETED` và log lỗi, đảm bảo scan không bị stuck ở trạng thái SCANNING.

**Validates: Requirements 2.3**

Property 3: Preservation - Ticket processing behavior không thay đổi

_For any_ ticket processing input (projectKey, ticketId), hàm `processTicket()` SHALL produce cùng kết quả (KB record saved, relationships logged, attachments processed) trước và sau fix, vì fix chỉ thay đổi `completeScan()` và không ảnh hưởng đến ticket processing pipeline.

**Validates: Requirements 3.1**

Property 4: Preservation - Scan state management không thay đổi

_For any_ scan state transition (pause, resume, cancel), các hàm tương ứng SHALL produce cùng state transitions và job management behavior trước và sau fix, vì fix chỉ thêm logic vào `completeScan()`.

**Validates: Requirements 3.2, 3.3**

## Fix Implementation

### Changes Required

Vì root cause đã xác nhận rõ ràng (missing function call), fix rất targeted:

**File**: `shared/src/commonMain/kotlin/com/assistant/scan/BatchScanEngine.kt`

**Function**: `completeScan(projectKey: String)` (dòng 201-205)

**Specific Changes**:
1. **Thêm lời gọi `buildAndSaveGraph(projectKey)`**: Gọi trước khi set status COMPLETED, wrapped trong try-catch để đảm bảo error handling
2. **Error handling**: Catch exception từ `buildAndSaveGraph()`, log lỗi qua `logToBoth()`, và tiếp tục set status COMPLETED bình thường
3. **Không thay đổi visibility**: `completeScan()` vẫn là `private suspend fun`

**Code change dự kiến:**
```kotlin
private suspend fun completeScan(projectKey: String) {
    val current = scanStateRepository.findByProjectKey(projectKey) ?: return
    // Build graph before marking as completed
    try {
        buildAndSaveGraph(projectKey)
    } catch (e: Exception) {
        logToBoth(projectKey, "-", ScanLogStatus.FAILED,
            "Graph build failed: ${e.message ?: "Unknown error"}")
    }
    scanStateRepository.save(current.copy(
        status = ScanStatus.COMPLETED,
        currentTicketId = null,
        updatedAt = Clock.System.now().toString()
    ))
    activeJobs.remove(projectKey)
}
```

**Lưu ý**: `buildAndSaveGraph()` đã có try-catch nội bộ (catch và println), nhưng thêm try-catch ở `completeScan()` là defense-in-depth để đảm bảo không có exception nào escape.

## Testing Strategy

### Validation Approach

Testing strategy theo hai giai đoạn: (1) viết exploratory tests chạy trên code chưa fix để xác nhận bug, (2) viết fix + preservation tests để verify fix hoạt động đúng và không gây regression.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples chứng minh bug tồn tại TRƯỚC khi implement fix. Xác nhận root cause analysis.

**Test Plan**: Viết integration test mock `BatchScanEngine` với đầy đủ dependencies, chạy `scanLoop()` đến completion, và assert rằng `kbRepository.getGraphData(projectKey)` trả về non-null. Test này sẽ FAIL trên code chưa fix.

**Test Cases**:
1. **Scan completion without graph**: Chạy scan với 3 ticket mock → scan hoàn tất → assert `getGraphData()` returns non-null (will fail on unfixed code)
2. **completeScan does not call buildAndSaveGraph**: Mock `buildAndSaveGraph` và verify nó không được gọi khi `completeScan()` chạy (will fail on unfixed code — actually confirms bug)
3. **API returns 404 after scan**: Simulate full flow: start scan → complete → GET /api/graph/{projectKey} → expect 200 (will fail on unfixed code)

**Expected Counterexamples**:
- `kbRepository.getGraphData(projectKey)` returns null sau khi scan hoàn tất
- `buildAndSaveGraph()` never invoked trong scan lifecycle
- Root cause confirmed: missing call in `completeScan()`

### Fix Checking

**Goal**: Verify rằng với mọi input nơi bug condition holds, hàm đã fix produce expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := completeScan_fixed(input.projectKey)
  ASSERT kbRepository.getGraphData(input.projectKey) != null
  ASSERT scanState.status == COMPLETED
END FOR
```

### Preservation Checking

**Goal**: Verify rằng với mọi input nơi bug condition KHÔNG hold, hàm đã fix produce cùng kết quả như hàm gốc.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT completeScan_original(input) == completeScan_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing được khuyến nghị cho preservation checking vì:
- Tự động generate nhiều test cases across input domain (various projectKeys, scan states)
- Catch edge cases mà manual unit tests có thể miss
- Đảm bảo mạnh mẽ rằng behavior không thay đổi cho tất cả non-buggy inputs

**Test Plan**: Observe behavior trên code chưa fix cho ticket processing và state management, sau đó viết property-based tests capturing behavior đó.

**Test Cases**:
1. **Ticket processing preservation**: Verify `processTicket()` produce cùng KB records trước và sau fix
2. **State management preservation**: Verify pause/resume/cancel produce cùng state transitions
3. **Empty project preservation**: Verify project với 0 ticket vẫn handled đúng cách (COMPLETED, no graph build)
4. **Scan conflict preservation**: Verify concurrent scan detection vẫn hoạt động

### Unit Tests

- Test `completeScan()` gọi `buildAndSaveGraph()` trước khi set COMPLETED
- Test `completeScan()` handles `buildAndSaveGraph()` exception gracefully
- Test `completeScan()` vẫn set COMPLETED khi graph build fails
- Test `completeScan()` log error khi graph build fails
- Test empty project (0 tickets) không gọi `buildAndSaveGraph()`

### Property-Based Tests

- Generate random project configurations (varying ticket counts, edge types) và verify graph được build khi scan hoàn tất
- Generate random exception types từ graph building và verify scan vẫn completes
- Generate random scan state sequences và verify preservation of pause/resume/cancel behavior
- Extend existing `GraphDataPersistencePropertyTest` pattern cho integration testing

### Integration Tests

- Test full scan flow: startScan → processTickets → completeScan → verify graph data available
- Test scan flow với Jira failure during graph build → verify scan still completes
- Test scan flow → verify API `GET /api/graph/{projectKey}` returns 200 với valid data
