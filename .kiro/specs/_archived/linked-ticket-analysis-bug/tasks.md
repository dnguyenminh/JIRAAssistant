# Tasks — Linked Ticket Analysis Bugfix

## Task 1: Thêm `isExternal` field vào `TicketNode`

- [x] 1.1 Thêm field `val isExternal: Boolean = false` vào `TicketNode` data class trong `NetworkModels.kt`

## Task 2: Sửa `FeatureNetworkMapper` — không drop external edges, tạo external nodes

- [x] 2.1 Trong `map()`: thêm `externalNodes: MutableMap<String, TicketNode>` để collect external nodes
- [x] 2.2 Sửa `addSingleLink()`: khi `targetId !in idSet`, thay vì `return` → tạo external `TicketNode` từ `JiraLinkedIssue` metadata (key, summary, status) và add vào `externalNodes` map, vẫn tạo edge bình thường
- [x] 2.3 Cập nhật `addIssueLinkEdges()` để truyền `externalNodes` map xuống `addSingleLink()`
- [x] 2.4 Trong `map()`: merge `externalNodes.values` vào danh sách nodes cuối cùng trước khi return `NetworkGraph`
- [x] 2.5 Verify file `FeatureNetworkMapper.kt` ≤ 200 dòng, mỗi function ≤ 20 dòng

## Task 3: Unit tests cho external node creation

- [x] 3.1 Test `map()` với issue có link tới external ticket → verify edge tồn tại và external node có đúng metadata (key, summary, status, isExternal=true)
- [x] 3.2 Test `map()` với issue có multiple external links → verify tất cả edges và external nodes được tạo
- [x] 3.3 Test `map()` với mix internal/external links → verify internal edges unchanged, external edges + nodes added
- [x] 3.4 Test dedup: 2 issues cùng link tới 1 external ticket → verify chỉ 1 external node, 2 edges (hoặc 1 nếu dedup)

## Task 4: Property-based tests

- [x] 4.1 ⚙️ PBT: Bug condition — cho random issues với external links, verify tất cả external links có edge và node trong graph
- [x] 4.2 ⚙️ PBT: Preservation — cho random issues với chỉ internal links, verify `map()` fixed produce cùng graph như `map()` original

## Task 5: Verify build và existing tests pass

- [x] 5.1 Chạy `./gradlew shared:jvmTest` verify existing tests không bị break
- [x] 5.2 Chạy `./gradlew build` verify project compile thành công
