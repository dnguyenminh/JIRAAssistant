# Knowledge Graph — Master Requirements

## Tổng quan

Domain Knowledge Graph cung cấp trang trực quan hóa mạng lưới quan hệ giữa các Jira tickets. Hệ thống đã trải qua nhiều giai đoạn phát triển: từ SVG 3D perspective rendering ban đầu, chuyển sang Sigma.js + Graphology (WebGL), rồi cuối cùng sang Cytoscape.js. Trang hỗ trợ lọc theo node type, cluster, Focus Mode với depth slider, search combobox, incremental rendering khi scan đang chạy, và tích hợp AI Chat cho conversational navigation. Vector Store được mở rộng để index ticket descriptions, relationships, và analysis results cho semantic search.

Các tối ưu hiệu năng bao gồm: WebGL rendering cho 1000+ nodes, native hide/show thay vì DOM manipulation, debounce filtering, BFS traversal giới hạn 500 nodes, và concentric layout cho Focus Mode.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `knowledge-graph-3d` | Feature | ✅ Archived | 3D visualization, force-directed layout, data-driven network |
| `knowledge-graph-optimization` | Feature | ✅ Archived | Thay SVG renderer bằng Sigma.js + Graphology (WebGL) |
| `graph-filter-focus-mode` | Feature | ✅ Archived | Filter panel, Focus Mode, depth slider, search combobox, AI Chat integration |
| `incremental-graph-rendering` | Feature | ✅ Archived | Graph tăng dần khi scan, fade-in nodes mới |
| `graph-build-on-scan-complete` | Bugfix | ✅ Archived | Fix graph không build khi scan hoàn tất |

## Requirements tổng hợp

### Visualization & Rendering

- Graph render bằng Cytoscape.js (thay thế SVG thủ công và Sigma.js trước đó)
- Nodes hiển thị dạng hexagonal với ticket key làm nhãn, phân biệt màu theo type
- Edges phân biệt: nét liền (explicit Jira links), nét đứt (semantic AI-detected)
- Cluster backgrounds với màu riêng biệt, đường viền bao quanh
- Navigation controls: Zoom In/Out, Fit-to-Screen, Reset View (bottom-right)
- Obsidian Kinetic dark theme: background tối, font "Be Vietnam Pro", neon glow effects
- Render 1000+ nodes ở 30fps+ với pan/zoom/drag mượt mà

### Data-Driven Network

- `FeatureNetworkMapper` trích xuất edges từ: Jira issue links (blocks, relates to, duplicates), parent-subtask hierarchy, keyword similarity
- `JiraRestClient` fetch fields: description, parent, subtasks, issuelinks, attachment
- Chỉ tạo edges giữa nodes cùng project
- Node type lấy từ Jira issue type (Story, Bug, Task, Sub-task, Epic)

### Filter & Focus Mode

- **Node Type Filter**: Checkboxes động từ backend, ẩn/hiện nodes theo type
- **Cluster Filter**: Dropdown chọn cluster, option "All Clusters" mặc định
- **Focus Mode**: Click node → chỉ hiển thị node + neighbors theo depth, highlight vàng gold (#f9d423), concentric layout
- **Depth Slider**: Range 1-5 hops, BFS traversal, giới hạn 500 nodes khi depth > 3
- **Simplify Toggle**: Giảm edges rối rắm trong Focus Mode (max 2 edges/neighbor ngoài focused)
- **Show All**: Reset tất cả filters về mặc định
- **Combined Filter**: AND logic giữa tất cả bộ lọc, cập nhật trong 200ms

### Search Combobox

- Tìm kiếm trên 3 fields: ticket key, summary, description
- Dropdown grouped theo loại match (🔑 Key, 📝 Summary, 📄 Description)
- Filter bar với checkboxes cho từng loại kết quả
- Click suggestion → activate Focus Mode trên node tương ứng
- Keyboard navigation, debounce 120ms, tối đa 100 kết quả

### Ticket Information Panel

- Panel bên phải hiển thị khi click node: type badge, key, summary, description đầy đủ
- Section "LINKED TICKETS": ticket key (clickable), summary, relationship type
- Section "SUB-TASKS": ticket key (clickable), summary, status
- Ẩn sections khi không có data, click ticket key → focus node trong graph

### Incremental Graph Rendering

- Graph build tăng dần sau mỗi batch ticket analyze (async, không block scan)
- Frontend poll Graph API mỗi 5 giây khi scan đang chạy
- Nodes mới thêm với fade-in effect, không reset toàn bộ graph view
- Scan status badge: "Scanning... 15/42 — 35%"
- Debounce graph build tối đa 1 lần/5 giây, skip build trùng lặp

### Vector Store Indexing

- **Ticket Embeddings**: Index `"[key] summary. description"` với chunkType="TICKET"
- **Relationship Embeddings**: Index `"sourceKey edgeType targetKey"` với chunkType="RELATIONSHIP"
- **Analysis Embeddings**: Index KBRecord (estimate, rationale) với chunkType="ANALYSIS"
- **Cluster Summaries**: Index cluster info với chunkType="CLUSTER"
- Indexing Pipeline chạy async sau scan, batch 20 texts/request, skip duplicates

### AI Chat Integration

- Graph Chat Context: focused node, active filters, visible count, search query
- Conversational Navigation: "focus on ICL2-24", "show only features", "reset filters"
- Chat Actions: focusNode, filterByType, filterByCluster, resetFilters, searchNodes, navigateToGraph
- Cross-page navigation: AI tự chuyển trang khi cần
- Enhanced Knowledge Context: semantic search trên tất cả chunkTypes, top-10 results grouped

### Trạng thái Empty & Error

- Không có data: "No graph data yet. Run a scan from the Dashboard."
- API error: error banner + nút Retry + nút Switch Project

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `graph-build-on-scan-complete` | Fix `buildAndSaveGraph()` không được gọi khi scan hoàn tất — graph data trống sau scan thành công |
