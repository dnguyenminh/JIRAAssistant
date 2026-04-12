# Tài liệu Yêu cầu Sửa lỗi

## Giới thiệu

Thanh tiến trình scan hiển thị "1307 / 1308 — 99%" thay vì "1308 / 1308 — 100%" khi scan hoàn tất. Nguyên nhân gốc là race condition trong `processBatchParallel()` tại `BatchScanEngine.kt`: các coroutine chạy song song ghi đè `processedCount` theo thứ tự không xác định, khiến coroutine kết thúc sau cùng có thể ghi giá trị thấp hơn giá trị đã được ghi trước đó. Ngoài ra, `progressPercent` dùng `.toInt()` (truncate) thay vì làm tròn, gây mất chính xác ở các tỷ lệ gần 100%.

## Phân tích Lỗi

### Hành vi Hiện tại (Lỗi)

1.1 KHI xử lý batch ticket song song, mỗi coroutine gọi `updateProcessedCount(projectKey, startIdx + idx + 1)` độc lập THÌ hệ thống ghi đè `processedCount` theo thứ tự hoàn thành không xác định, dẫn đến coroutine kết thúc sau có thể ghi giá trị thấp hơn giá trị đã ghi trước đó (ví dụ: coroutine index 1 ghi 1307 sau khi coroutine index 2 đã ghi 1308)

1.2 KHI `processedCount` bị ghi đè thành giá trị thấp hơn `totalTickets` và scan hoàn tất THÌ hệ thống hiển thị thanh tiến trình sai (ví dụ: "1307 / 1308 — 99%" thay vì "1308 / 1308 — 100%")

1.3 KHI tính `progressPercent` với tỷ lệ `processedCount/totalTickets` gần nhưng chưa đạt 100% (ví dụ: 1307/1308 = 99.923%) THÌ hệ thống dùng `.toInt()` truncate xuống 99 thay vì làm tròn lên 100

1.4 KHI scan có status COMPLETED nhưng `processedCount < totalTickets` do race condition THÌ hệ thống vẫn hiển thị phần trăm dựa trên `processedCount` sai thay vì luôn trả về 100%

### Hành vi Mong đợi (Đúng)

2.1 KHI xử lý batch ticket song song THÌ hệ thống PHẢI cập nhật `processedCount` MỘT LẦN DUY NHẤT sau khi toàn bộ batch hoàn tất (sau `.forEach { it.join() }`), sử dụng `startIdx + batch.size` thay vì cập nhật từng coroutine riêng lẻ

2.2 KHI batch hoàn tất và `processedCount` được cập nhật đúng THÌ hệ thống PHẢI hiển thị thanh tiến trình chính xác phản ánh số ticket đã xử lý thực tế (ví dụ: "1308 / 1308 — 100%")

2.3 KHI tính `progressPercent` THÌ hệ thống PHẢI dùng `kotlin.math.roundToInt()` để làm tròn thay vì `.toInt()` truncate, đảm bảo tỷ lệ 99.5%+ hiển thị là 100%

2.4 KHI scan có status COMPLETED THÌ hệ thống PHẢI luôn trả về `progressPercent = 100` bất kể giá trị `processedCount`

### Hành vi Không thay đổi (Ngăn Regression)

3.1 KHI scan đang chạy (status SCANNING) với `processedCount < totalTickets` THÌ hệ thống PHẢI TIẾP TỤC hiển thị phần trăm tiến trình chính xác dựa trên tỷ lệ `processedCount/totalTickets`

3.2 KHI scan bị tạm dừng (status PAUSED) THÌ hệ thống PHẢI TIẾP TỤC hiển thị phần trăm tiến trình tại thời điểm tạm dừng

3.3 KHI scan bị hủy (status CANCELLED) THÌ hệ thống PHẢI TIẾP TỤC ẩn thanh tiến trình như hiện tại

3.4 KHI `totalTickets = 0` THÌ hệ thống PHẢI TIẾP TỤC trả về `progressPercent = 0`

3.5 KHI scan tiếp tục sau pause (resume) THÌ hệ thống PHẢI TIẾP TỤC xử lý từ `processedCount` hiện tại và cập nhật tiến trình đúng

3.6 KHI xử lý batch song song THÌ hệ thống PHẢI TIẾP TỤC xử lý tất cả ticket trong batch (không bỏ sót ticket nào)

---

## Bug Condition (Điều kiện Lỗi)

### Hàm Bug Condition — Xác định input gây lỗi:

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type BatchProcessInput (projectKey, batch, startIdx, coroutineCompletionOrder)
  OUTPUT: boolean
  
  // Lỗi xảy ra khi batch có nhiều hơn 1 ticket VÀ coroutine không hoàn thành
  // theo thứ tự index tăng dần (coroutine cuối cùng hoàn thành không phải là
  // coroutine có index cao nhất trong batch)
  RETURN batch.size > 1 AND lastFinishedCoroutineIndex ≠ batch.size - 1
END FUNCTION
```

### Property Specification — Hành vi đúng cho input lỗi:

```pascal
// Property: Fix Checking — Cập nhật processedCount sau batch
FOR ALL X WHERE isBugCondition(X) DO
  result ← processBatchParallel'(X)
  ASSERT result.processedCount = X.startIdx + X.batch.size
  ASSERT result.processedCount is updated exactly once after all coroutines complete
END FOR
```

### Preservation Goal — Hành vi không đổi cho input không lỗi:

```pascal
// Property: Preservation Checking
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X).processedCount = F'(X).processedCount
  ASSERT F(X).progressPercent = F'(X).progressPercent
END FOR
```
