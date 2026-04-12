# Bugfix Requirements Document

## Introduction

Sau khi scan hoàn tất thành công từ Dashboard, trang Knowledge Graph hiển thị "No graph data yet. Run a scan from the Dashboard." thay vì hiển thị dữ liệu đồ thị. Nguyên nhân gốc: hàm `buildAndSaveGraph(projectKey)` được định nghĩa trong `BatchScanTicketProcessor.kt` nhưng không bao giờ được gọi. Phương thức `completeScan()` trong `BatchScanEngine.kt` chỉ cập nhật trạng thái scan thành COMPLETED và xóa active job mà không xây dựng NetworkGraph.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN scan hoàn tất thành công (tất cả ticket đã được xử lý) THEN hệ thống chỉ cập nhật trạng thái scan thành COMPLETED mà không gọi `buildAndSaveGraph(projectKey)` để xây dựng dữ liệu đồ thị

1.2 WHEN user điều hướng đến trang Knowledge Graph sau khi scan hoàn tất THEN API `GET /api/graph/{projectKey}` trả về 404 vì `kbRepository.getGraphData(projectKey)` trả về null (không có dữ liệu graph được lưu)

1.3 WHEN API trả về 404 cho graph data THEN frontend hiển thị "No graph data yet. Run a scan from the Dashboard." mặc dù scan đã hoàn tất thành công

### Expected Behavior (Correct)

2.1 WHEN scan hoàn tất thành công (tất cả ticket đã được xử lý) THEN hệ thống SHALL gọi `buildAndSaveGraph(projectKey)` để fetch tất cả issues từ Jira, xây dựng NetworkGraph qua `featureNetworkMapper.map(issues)`, và lưu graph qua `kbRepository.saveGraphData(projectKey, graph)` trước khi đặt trạng thái thành COMPLETED

2.2 WHEN user điều hướng đến trang Knowledge Graph sau khi scan hoàn tất THEN API `GET /api/graph/{projectKey}` SHALL trả về dữ liệu graph hợp lệ với nodes, edges và clusters

2.3 WHEN `buildAndSaveGraph()` gặp lỗi (ví dụ: Jira không khả dụng, lỗi mapping) THEN hệ thống SHALL ghi log lỗi và vẫn hoàn tất scan với trạng thái COMPLETED (lỗi xây dựng graph không được chặn việc hoàn tất scan)

### Unchanged Behavior (Regression Prevention)

3.1 WHEN scan đang chạy và xử lý từng ticket THEN hệ thống SHALL CONTINUE TO phân tích ticket qua AI, lưu KB record, ghi log relationships và xử lý attachments như hiện tại

3.2 WHEN scan được pause, resume hoặc cancel THEN hệ thống SHALL CONTINUE TO cập nhật trạng thái scan và quản lý active jobs đúng cách

3.3 WHEN project không có ticket nào THEN hệ thống SHALL CONTINUE TO đặt trạng thái COMPLETED và ghi log "No tickets found" mà không gọi buildAndSaveGraph

3.4 WHEN scan hoàn tất và graph đã được xây dựng THEN hệ thống SHALL CONTINUE TO cho phép tìm kiếm, lọc nodes và hiển thị 3D visualization trên trang Knowledge Graph như hiện tại
