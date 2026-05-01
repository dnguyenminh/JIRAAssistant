# Requirements Document — Incremental Graph Rendering

## Giới thiệu

Hiện tại, trang Relationship Network (Knowledge Graph) chỉ hiển thị đồ thị mạng ticket sau khi **tất cả** ticket đã được analyze xong. Hàm `buildAndSaveGraph()` chỉ được gọi một lần duy nhất trong `completeScan()` — tức là graph chỉ được xây dựng khi scan hoàn tất 100%.

Feature này thay đổi hành vi để graph được xây dựng **tăng dần** (incremental): mỗi khi một ticket (hoặc một batch ticket) được analyze xong, graph sẽ được cập nhật ngay lập tức. Frontend sẽ hiển thị mạng ticket đang lớn dần theo thời gian thực, thay vì phải đợi toàn bộ scan hoàn tất.

## Thuật ngữ (Glossary)

- **Batch_Scan_Engine**: Module `BatchScanEngine` trong shared module — điều phối quá trình scan ticket theo batch, quản lý trạng thái scan (IDLE → SCANNING → PAUSED / COMPLETED / CANCELLED)
- **Graph_Builder**: Logic xây dựng `NetworkGraph` từ danh sách Jira issues thông qua `FeatureNetworkMapper.map(issues)` và lưu vào KB qua `KBRepository.saveGraphData()`
- **Graph_API**: Endpoint `GET /api/graph/{projectKey}` — trả về `GraphLayoutResponse` gồm nodes, edges, clusters, nodeTypes
- **Graph_Polling**: Cơ chế frontend (`GraphScanStatus.startGraphPolling()`) poll `Graph_API` mỗi 5 giây khi scan đang chạy để cập nhật đồ thị
- **Scan_Status_Polling**: Cơ chế frontend (`GraphScanStatus.startScanStatusPolling()`) poll `GET /api/projects/{key}/scan/status` mỗi 3 giây để cập nhật badge trạng thái scan
- **Cytoscape_Renderer**: Module `CytoscapeRenderer` — render đồ thị mạng ticket sử dụng thư viện Cytoscape.js
- **Incremental_Graph_Build**: Quá trình xây dựng lại graph sau mỗi batch ticket được analyze, thay vì chỉ xây dựng một lần khi scan hoàn tất
- **Feature_Network_Mapper**: Class `FeatureNetworkMapper` — chuyển đổi danh sách `JiraIssue` thành `NetworkGraph` (nodes + edges) dựa trên issue links, parent-subtask, và keyword similarity
- **KB_Repository**: Interface `KBRepository` — lưu trữ và truy xuất `NetworkGraph` theo `projectKey`
- **Scan_State**: Trạng thái scan hiện tại gồm `status`, `processedCount`, `totalTickets`, `ticketIds`

## Requirements

### Requirement 1: Xây dựng graph tăng dần trong quá trình scan

**User Story:** Là một Project Manager, tôi muốn đồ thị mạng ticket được xây dựng dần trong quá trình scan, để tôi có thể xem mối quan hệ giữa các ticket ngay khi chúng được analyze mà không phải đợi toàn bộ scan hoàn tất.

#### Acceptance Criteria

1. WHEN Batch_Scan_Engine hoàn tất xử lý một batch ticket, THE Graph_Builder SHALL xây dựng lại NetworkGraph từ tất cả issues hiện có trong project và lưu vào KB_Repository
2. WHILE scan đang ở trạng thái SCANNING, THE Graph_Builder SHALL được gọi sau mỗi batch hoàn tất, đảm bảo graph data luôn phản ánh trạng thái analyze mới nhất
3. WHEN Graph_Builder gặp lỗi trong quá trình xây dựng incremental graph, THE Batch_Scan_Engine SHALL ghi log lỗi và tiếp tục scan mà không dừng lại
4. WHEN scan hoàn tất (tất cả ticket đã xử lý), THE Graph_Builder SHALL xây dựng graph lần cuối cùng để đảm bảo dữ liệu đầy đủ và chính xác

### Requirement 2: Frontend hiển thị graph tăng dần theo thời gian thực

**User Story:** Là một Project Manager, tôi muốn trang Relationship Network tự động cập nhật đồ thị khi có ticket mới được analyze, để tôi thấy mạng ticket lớn dần mà không cần refresh trang.

#### Acceptance Criteria

1. WHILE scan đang ở trạng thái SCANNING, THE Graph_Polling SHALL poll Graph_API định kỳ để lấy dữ liệu graph mới nhất
2. WHEN Graph_Polling nhận được dữ liệu graph có thêm nodes hoặc edges mới so với lần poll trước, THE Cytoscape_Renderer SHALL cập nhật đồ thị bằng cách thêm các phần tử mới mà không render lại toàn bộ
3. WHEN nodes mới được thêm vào đồ thị, THE Cytoscape_Renderer SHALL áp dụng hiệu ứng fade-in để người dùng nhận biết được các phần tử mới xuất hiện
4. WHILE scan đang chạy và graph đang được cập nhật tăng dần, THE Graph_Polling SHALL cập nhật bộ đếm node count trên giao diện sau mỗi lần poll thành công
5. WHEN scan hoàn tất (trạng thái chuyển sang COMPLETED), THE Graph_Polling SHALL dừng polling và thực hiện một lần load cuối cùng để đảm bảo đồ thị hiển thị đầy đủ dữ liệu

### Requirement 3: Hiển thị trạng thái scan trên trang Knowledge Graph

**User Story:** Là một Project Manager, tôi muốn biết scan đang ở giai đoạn nào khi xem trang Relationship Network, để tôi hiểu đồ thị đang hiển thị dữ liệu chưa đầy đủ hay đã hoàn chỉnh.

#### Acceptance Criteria

1. WHILE scan đang ở trạng thái SCANNING, THE Scan_Status_Polling SHALL hiển thị badge trạng thái scan với thông tin tiến độ (ví dụ: "Scanning... 15/42 — 35%")
2. WHEN scan hoàn tất, THE Scan_Status_Polling SHALL cập nhật badge thành trạng thái "Completed" và hiển thị tổng số ticket đã xử lý
3. WHILE scan đang chạy và graph chưa có dữ liệu (chưa có ticket nào được analyze xong), THE Cytoscape_Renderer SHALL hiển thị trạng thái loading với message "Đang scan, đồ thị sẽ xuất hiện khi ticket đầu tiên được analyze..."
4. IF Graph_API trả về lỗi trong khi scan đang chạy, THEN THE Graph_Polling SHALL hiển thị thông báo lỗi tạm thời và tiếp tục retry ở lần poll tiếp theo

### Requirement 4: Đảm bảo hiệu năng khi xây dựng graph tăng dần

**User Story:** Là một Developer, tôi muốn quá trình xây dựng graph tăng dần không làm chậm quá trình scan, để scan vẫn hoàn tất trong thời gian hợp lý.

#### Acceptance Criteria

1. THE Graph_Builder SHALL thực hiện incremental graph build bất đồng bộ (async), không block luồng xử lý chính của Batch_Scan_Engine
2. WHEN hai lần incremental graph build chồng chéo (lần trước chưa xong, batch mới đã hoàn tất), THE Graph_Builder SHALL bỏ qua lần build trùng lặp và chỉ thực hiện lần build mới nhất
3. THE Graph_Builder SHALL giới hạn tần suất build tối đa một lần mỗi 5 giây (debounce) để tránh quá tải khi nhiều batch hoàn tất liên tiếp
4. WHILE scan đang chạy với project có hơn 200 ticket, THE Graph_Builder SHALL vẫn hoàn tất mỗi lần incremental build trong thời gian hợp lý mà không gây timeout cho Graph_API

### Requirement 5: Bảo toàn hành vi hiện tại (Preservation)

**User Story:** Là một Developer, tôi muốn đảm bảo feature mới không làm hỏng các chức năng hiện có của scan và graph.

#### Acceptance Criteria

1. THE Batch_Scan_Engine SHALL tiếp tục xử lý ticket qua pipeline hiện tại (AI analysis → KB save → relationship logging → attachment processing) mà không thay đổi
2. THE Batch_Scan_Engine SHALL tiếp tục hỗ trợ pause, resume, cancel scan với cùng hành vi quản lý trạng thái như hiện tại
3. WHEN scan hoàn tất, THE Graph_Builder SHALL vẫn xây dựng graph lần cuối cùng (giữ nguyên hành vi `buildAndSaveGraph()` trong `completeScan()`)
4. THE Graph_API SHALL tiếp tục trả về cùng cấu trúc `GraphLayoutResponse` (nodes, edges, clusters, nodeTypes) mà không thay đổi schema
5. WHEN project không có ticket nào, THE Batch_Scan_Engine SHALL tiếp tục xử lý như hiện tại (set COMPLETED, không gọi Graph_Builder)
6. THE Graph_Polling và Scan_Status_Polling hiện tại trên frontend SHALL tiếp tục hoạt động đúng khi scan đang chạy, chỉ bổ sung thêm logic incremental update
