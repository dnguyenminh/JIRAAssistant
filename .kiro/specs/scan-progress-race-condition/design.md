# Scan Progress Race Condition — Bugfix Design

## Overview

Thanh tiến trình scan hiển thị sai (ví dụ: "1307 / 1308 — 99%") khi scan hoàn tất do hai nguyên nhân: (1) race condition trong `processBatchParallel()` — mỗi coroutine gọi `updateProcessedCount()` độc lập, coroutine kết thúc sau có thể ghi đè giá trị cao hơn bằng giá trị thấp hơn; (2) `progressPercent` dùng `.toInt()` truncate thay vì `roundToInt()`, và không force 100% khi status COMPLETED. Fix: di chuyển `updateProcessedCount` ra sau `forEach { it.join() }`, đổi `.toInt()` thành `roundToInt()`, và force `progressPercent = 100` khi COMPLETED.

## Glossary

- **Bug_Condition (C)**: Batch có >1 coroutine chạy song song VÀ coroutine cuối cùng hoàn thành không phải coroutine có index cao nhất → `processedCount` bị ghi đè sai
- **Property (P)**: `processedCount` phải được cập nhật đúng một lần sau khi toàn bộ batch hoàn tất, và `progressPercent` phải dùng `roundToInt()` + force 100% khi COMPLETED
- **Preservation**: Hành vi scan đang chạy (SCANNING), tạm dừng (PAUSED), hủy (CANCELLED), project rỗng, và resume phải không thay đổi
- **`processBatchParallel()`**: Hàm private trong `BatchScanEngine.kt` — xử lý batch ticket song song bằng coroutines, hiện gọi `updateProcessedCount()` trong mỗi coroutine
- **`progressPercent`**: Computed property trong `ScanState.kt` — tính phần trăm tiến trình từ `processedCount/totalTickets`
- **`updateProcessedCount()`**: Hàm private trong `BatchScanEngine.kt` — đọc state hiện tại từ repository và save lại với `processedCount` mới

## Bug Details

### Bug Condition

Bug xảy ra khi `processBatchParallel()` xử lý batch có nhiều hơn 1 ticket. Mỗi coroutine gọi `updateProcessedCount(projectKey, startIdx + idx + 1)` độc lập khi hoàn thành. Vì thứ tự hoàn thành không xác định, coroutine có index thấp có thể kết thúc SAU coroutine có index cao, ghi đè `processedCount` bằng giá trị thấp hơn. Ngoài ra, `progressPercent` dùng `.toInt()` truncate nên 99.923% hiển thị là 99% thay vì 100%.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type BatchProcessInput { projectKey, batch, startIdx, coroutineCompletionOrder }
  OUTPUT: boolean

  // Race condition: batch > 1 ticket VÀ coroutine cuối cùng hoàn thành
  // không phải coroutine có index cao nhất
  raceCondition := batch.size > 1
                   AND lastFinishedCoroutineIndex ≠ batch.size - 1

  // Truncation: processedCount/totalTickets gần 100% nhưng bị truncate
  truncationIssue := (processedCount / totalTickets * 100) ≥ 99.5
                     AND (processedCount / totalTickets * 100) < 100

  // COMPLETED nhưng processedCount < totalTickets
  completedMismatch := status == COMPLETED AND processedCount < totalTickets

  RETURN raceCondition OR truncationIssue OR completedMismatch
END FUNCTION
```

### Examples

- Batch 3 ticket [A, B, C] với startIdx=1305: coroutine C (idx=2) hoàn thành trước → ghi 1308, sau đó coroutine A (idx=0) hoàn thành → ghi đè thành 1306. Kết quả: processedCount=1306 thay vì 1308
- Scan 1308 ticket hoàn tất, processedCount=1307 do race condition → progressPercent = (1307/1308*100).toInt() = 99 thay vì 100
- Scan 200 ticket hoàn tất đúng processedCount=199 (batch cuối 1 ticket) → progressPercent = (199/200*100).toInt() = 99 do truncation (99.5% → 99)
- Scan status=COMPLETED nhưng processedCount=1307 → UI hiển thị "1307 / 1308 — 99%" thay vì "1308 / 1308 — 100%"

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Scan đang chạy (SCANNING) với `processedCount < totalTickets` phải tiếp tục hiển thị phần trăm chính xác dựa trên tỷ lệ thực tế
- Scan tạm dừng (PAUSED) phải tiếp tục hiển thị phần trăm tại thời điểm tạm dừng
- Scan bị hủy (CANCELLED) phải tiếp tục hoạt động như hiện tại
- `totalTickets = 0` phải tiếp tục trả về `progressPercent = 0`
- Resume sau pause phải tiếp tục xử lý từ `processedCount` hiện tại
- Tất cả ticket trong batch phải được xử lý (không bỏ sót)

**Scope:**
Tất cả input không liên quan đến race condition trong batch processing hoặc tính toán `progressPercent` phải hoàn toàn không bị ảnh hưởng. Cụ thể:
- `processTicket()` — không thay đổi
- `pauseScan()`, `resumeScan()`, `cancelScan()` — không thay đổi
- `handleEmptyProject()` — không thay đổi
- `startScan()`, `launchScan()` — không thay đổi
- `completeScan()` — không thay đổi
- `scanLoop()` — không thay đổi

## Hypothesized Root Cause

Based on code analysis, có 2 root causes đã xác nhận:

1. **Race condition trong `processBatchParallel()`**: Mỗi coroutine trong batch gọi `updateProcessedCount(projectKey, startIdx + idx + 1)` khi hoàn thành. Hàm `updateProcessedCount()` đọc state hiện tại từ repository rồi save lại với count mới. Khi nhiều coroutine chạy song song, thứ tự hoàn thành không xác định → coroutine có index thấp có thể ghi đè giá trị cao hơn đã được ghi bởi coroutine có index cao hoàn thành trước.

   ```kotlin
   // HIỆN TẠI — mỗi coroutine gọi riêng lẻ (race condition)
   launch {
       processTicket(projectKey, ticketId)
       updateProcessedCount(projectKey, startIdx + idx + 1)  // ← BUG
   }
   ```

2. **Truncation trong `progressPercent`**: Property `progressPercent` dùng `.toInt()` (truncate toward zero) thay vì `roundToInt()` (làm tròn toán học). Với 1307/1308 = 99.923%, `.toInt()` trả về 99 thay vì 100. Thêm vào đó, khi status COMPLETED, `progressPercent` vẫn tính từ `processedCount/totalTickets` thay vì force trả về 100.

## Correctness Properties

Property 1: Bug Condition - processedCount cập nhật đúng sau batch

_For any_ batch processing input nơi batch có nhiều hơn 1 ticket, hàm `processBatchParallel()` đã fix SHALL cập nhật `processedCount` đúng một lần sau khi tất cả coroutine hoàn thành, với giá trị `startIdx + batch.size`, bất kể thứ tự hoàn thành của các coroutine.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition - progressPercent dùng roundToInt và force 100% khi COMPLETED

_For any_ ScanState nơi `status == COMPLETED`, `progressPercent` SHALL trả về 100. _For any_ ScanState nơi `totalTickets > 0` và `status != COMPLETED`, `progressPercent` SHALL bằng `((processedCount.toDouble() / totalTickets) * 100).roundToInt()`.

**Validates: Requirements 2.3, 2.4**

Property 3: Preservation - Tiến trình SCANNING/PAUSED không thay đổi

_For any_ ScanState nơi `status` là SCANNING hoặc PAUSED và `processedCount < totalTickets`, `progressPercent` SHALL trả về giá trị làm tròn chính xác của `processedCount/totalTickets * 100`, giữ nguyên hành vi hiển thị tiến trình đang chạy.

**Validates: Requirements 3.1, 3.2**

Property 4: Preservation - Batch processing xử lý đầy đủ ticket

_For any_ batch processing input, hàm `processBatchParallel()` đã fix SHALL gọi `processTicket()` cho mọi ticket trong batch, đảm bảo không bỏ sót ticket nào so với hành vi gốc.

**Validates: Requirements 3.5, 3.6**

## Fix Implementation

### Changes Required

**File 1**: `shared/src/commonMain/kotlin/com/assistant/scan/BatchScanEngine.kt`

**Function**: `processBatchParallel(projectKey, batch, startIdx)`

**Specific Changes**:
1. **Xóa `updateProcessedCount()` khỏi mỗi coroutine**: Loại bỏ lời gọi `updateProcessedCount(projectKey, startIdx + idx + 1)` bên trong `launch { }` block
2. **Thêm `updateProcessedCount()` sau `forEach { it.join() }`**: Gọi một lần duy nhất với `startIdx + batch.size` sau khi tất cả coroutine đã hoàn thành

**Code change dự kiến:**
```kotlin
private suspend fun processBatchParallel(projectKey: String, batch: List<String>, startIdx: Int) {
    kotlinx.coroutines.coroutineScope {
        batch.mapIndexed { idx, ticketId ->
            launch {
                ensureActive()
                updateCurrentTicket(projectKey, ticketId)
                processTicket(projectKey, ticketId)
            }
        }.forEach { it.join() }
        updateProcessedCount(projectKey, startIdx + batch.size)
    }
}
```

---

**File 2**: `shared/src/commonMain/kotlin/com/assistant/scan/ScanState.kt`

**Property**: `progressPercent`

**Specific Changes**:
1. **Thêm import `kotlin.math.roundToInt`**
2. **Force 100% khi COMPLETED**: Thêm check `if (status == ScanStatus.COMPLETED) return 100`
3. **Đổi `.toInt()` thành `.roundToInt()`**: Đảm bảo làm tròn toán học thay vì truncate

**Code change dự kiến:**
```kotlin
val progressPercent: Int
    get() = when {
        status == ScanStatus.COMPLETED -> 100
        totalTickets > 0 -> ((processedCount.toDouble() / totalTickets) * 100).roundToInt()
        else -> 0
    }
```

## Testing Strategy

### Validation Approach

Testing strategy theo hai giai đoạn: (1) viết exploratory tests chạy trên code chưa fix để xác nhận race condition và truncation bug, (2) viết fix + preservation tests để verify fix hoạt động đúng và không gây regression.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples chứng minh race condition và truncation bug tồn tại TRƯỚC khi implement fix. Xác nhận root cause analysis.

**Test Plan**: Viết unit tests mock `ScanStateRepository` và chạy `processBatchParallel()` với batch nhiều ticket. Capture thứ tự gọi `updateProcessedCount()` để chứng minh race condition. Test `progressPercent` với các tỷ lệ gần 100%.

**Test Cases**:
1. **Race condition test**: Chạy `processBatchParallel()` với batch 3 ticket, mock `processTicket()` với delay khác nhau → verify `processedCount` cuối cùng có thể sai (will fail on unfixed code)
2. **Multiple updateProcessedCount calls**: Verify `updateProcessedCount()` được gọi nhiều lần trong 1 batch thay vì 1 lần (will fail on unfixed code — confirms race condition pattern)
3. **Truncation test**: Tạo ScanState với processedCount=1307, totalTickets=1308 → assert progressPercent == 100 (will fail on unfixed code, returns 99)
4. **COMPLETED with wrong count**: Tạo ScanState với status=COMPLETED, processedCount=1307, totalTickets=1308 → assert progressPercent == 100 (will fail on unfixed code)

**Expected Counterexamples**:
- `processedCount` cuối batch < `startIdx + batch.size` do coroutine có index thấp ghi đè sau
- `progressPercent` trả về 99 thay vì 100 cho tỷ lệ 99.923%
- Possible causes: concurrent writes to repository, truncation instead of rounding

### Fix Checking

**Goal**: Verify rằng với mọi input nơi bug condition holds, hàm đã fix produce expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := processBatchParallel_fixed(input)
  ASSERT result.processedCount == input.startIdx + input.batch.size
  ASSERT updateProcessedCount called exactly once per batch
END FOR

FOR ALL state WHERE state.status == COMPLETED DO
  ASSERT state.progressPercent == 100
END FOR

FOR ALL state WHERE state.processedCount/state.totalTickets >= 0.995 DO
  ASSERT state.progressPercent == 100  // roundToInt rounds up from 99.5
END FOR
```

### Preservation Checking

**Goal**: Verify rằng với mọi input nơi bug condition KHÔNG hold, hàm đã fix produce cùng kết quả như hàm gốc.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT processBatchParallel_original(input).ticketsProcessed
      == processBatchParallel_fixed(input).ticketsProcessed
END FOR

FOR ALL state WHERE state.status IN [SCANNING, PAUSED, IDLE, CANCELLED]
                 AND state.processedCount/state.totalTickets < 0.995 DO
  ASSERT progressPercent_original(state) == progressPercent_fixed(state)
END FOR
```

**Testing Approach**: Property-based testing được khuyến nghị cho preservation checking vì:
- Tự động generate nhiều ScanState combinations (status, processedCount, totalTickets)
- Catch edge cases ở boundary values (0%, 50%, 99%, 99.4%, 99.5%)
- Đảm bảo mạnh mẽ rằng behavior không thay đổi cho tất cả non-buggy inputs

**Test Plan**: Observe behavior trên code chưa fix cho các trạng thái SCANNING/PAUSED/CANCELLED, sau đó viết property-based tests capturing behavior đó.

**Test Cases**:
1. **SCANNING progress preservation**: Verify progressPercent cho SCANNING states với processedCount < 99.5% totalTickets trả về cùng giá trị trước và sau fix
2. **PAUSED progress preservation**: Verify progressPercent cho PAUSED states giữ nguyên giá trị tại thời điểm pause
3. **Batch ticket completeness**: Verify tất cả ticket trong batch đều được gọi `processTicket()` trước và sau fix
4. **Zero tickets preservation**: Verify totalTickets=0 vẫn trả về progressPercent=0

### Unit Tests

- Test `processBatchParallel()` gọi `updateProcessedCount()` đúng 1 lần sau khi tất cả coroutine hoàn thành
- Test `processBatchParallel()` gọi `processTicket()` cho mọi ticket trong batch
- Test `progressPercent` trả về 100 khi status=COMPLETED
- Test `progressPercent` dùng roundToInt (99.5% → 100, 99.4% → 99)
- Test `progressPercent` trả về 0 khi totalTickets=0
- Test `progressPercent` cho các giá trị boundary (0%, 50%, 99%, 100%)

### Property-Based Tests

- Generate random batch sizes (1-10) và verify `processedCount` luôn bằng `startIdx + batch.size` sau batch
- Generate random ScanState (status, processedCount, totalTickets) và verify `progressPercent` tuân thủ rules: COMPLETED→100, totalTickets=0→0, otherwise→roundToInt
- Generate random coroutine completion orders và verify `processedCount` không bị ảnh hưởng bởi thứ tự hoàn thành

### Integration Tests

- Test full scan flow: startScan → processBatches → completeScan → verify processedCount == totalTickets và progressPercent == 100
- Test scan flow với batch sizes khác nhau (1, 3, 5, 10) → verify tiến trình tăng monotonically
- Test pause/resume flow → verify processedCount đúng sau resume và tiếp tục tăng
