# Requirements Document — Knowledge Graph Optimization

## Giới thiệu

Trang Knowledge Graph hiện render 1308+ node bằng SVG thủ công với phép chiếu 3D (Projection3D.kt, Graph3DRenderer.kt). Mỗi tương tác (kéo, zoom) tạo lại toàn bộ DOM elements SVG, gây lag nghiêm trọng. Yêu cầu thay thế renderer SVG thủ công bằng thư viện graph visualization chuyên dụng, thêm navigation controls, và giữ nguyên giao diện Obsidian Kinetic dark theme.

## Glossary

- **Graph_Renderer**: Module frontend chịu trách nhiệm hiển thị graph (nodes, edges, clusters) lên canvas/DOM
- **Navigation_Controls**: Bộ nút điều khiển cho phép user zoom in, zoom out, fit-to-screen, reset view trong graph
- **Sigma_Graphology**: Thư viện JavaScript `sigma.js` (WebGL renderer) + `graphology` (data layer) — WebGL-based, tốc độ render nhanh nhất hiện nay, xử lý hàng chục ngàn nodes ở 60fps
- **Graph_Container**: Phần tử DOM chứa graph visualization, thay thế SVG element hiện tại
- **Detail_Panel**: Panel bên phải hiển thị thông tin chi tiết khi click vào node
- **Graph_State**: Object quản lý trạng thái graph (nodes, edges, clusters, filtered IDs, selected node)
- **Obsidian_Kinetic_Theme**: Design system dark theme của ứng dụng với màu primary #2dfecf, background tối, glass-morphism effects
- **Server_Layout**: Vị trí node (x, y, z) được tính toán bởi ForceDirectedGraphEngine trên server
- **Kotlin_JS_Interop**: Cơ chế gọi JavaScript library từ Kotlin/JS code thông qua external declarations hoặc js() function
- **Graphology**: Thư viện JavaScript quản lý cấu trúc dữ liệu graph (nodes, edges, attributes) — data layer cho Sigma.js
- **Sigma.js**: Thư viện JavaScript render graph bằng WebGL — rendering layer, nhận data từ Graphology

## Requirements

### Requirement 1: Thay thế SVG Renderer bằng Sigma.js + Graphology

**User Story:** Là developer, tôi muốn thay thế custom SVG renderer bằng Sigma.js (WebGL) + Graphology (data layer), để graph hiển thị mượt mà với 1000+ nodes ở 60fps.

#### Acceptance Criteria

1. WHEN trang Knowledge Graph được load, THE Graph_Renderer SHALL render graph sử dụng Sigma.js + Graphology thay vì custom SVG elements
2. WHEN graph chứa 1000+ nodes và 4000+ edges, THE Graph_Renderer SHALL render và cho phép tương tác (pan, zoom, drag) mà không gây lag đáng kể (frame rate duy trì trên 30fps)
3. THE Graph_Renderer SHALL sử dụng Canvas/WebGL rendering thay vì tạo DOM elements cho mỗi node và edge
4. WHEN dữ liệu graph được nhận từ API, THE Graph_Renderer SHALL sử dụng vị trí node (x, y) từ Server_Layout làm vị trí ban đầu, không tính toán lại layout phía client
5. THE Graph_Renderer SHALL hiển thị graph ở chế độ 2D (bỏ phép chiếu 3D) để đơn giản hóa rendering và tương tác

### Requirement 2: Navigation Controls

**User Story:** Là user, tôi muốn có các nút điều khiển navigation rõ ràng, để dễ dàng di chuyển và định hướng trong graph lớn.

#### Acceptance Criteria

1. THE Navigation_Controls SHALL cung cấp nút Zoom In để phóng to graph
2. THE Navigation_Controls SHALL cung cấp nút Zoom Out để thu nhỏ graph
3. THE Navigation_Controls SHALL cung cấp nút Fit-to-Screen để hiển thị toàn bộ graph vừa khung nhìn
4. THE Navigation_Controls SHALL cung cấp nút Reset View để đưa graph về trạng thái ban đầu (vị trí, zoom level mặc định)
5. WHEN user click vào một node trong graph, THE Navigation_Controls SHALL cung cấp khả năng center view vào node đó
6. THE Navigation_Controls SHALL được đặt ở vị trí cố định (bottom-right) trong Graph_Container, giống vị trí zoom controls hiện tại

### Requirement 3: Tương tác Node và Detail Panel

**User Story:** Là user, tôi muốn click vào node để xem chi tiết ticket, để nhanh chóng truy cập thông tin mà không rời khỏi graph.

#### Acceptance Criteria

1. WHEN user click vào một node, THE Graph_Renderer SHALL hiển thị Detail_Panel với thông tin ticket (key, summary, description, type, attachments)
2. WHEN user hover vào một node, THE Graph_Renderer SHALL highlight node đó và các edges kết nối với node
3. WHEN Detail_Panel đang hiển thị, THE Detail_Panel SHALL cho phép đóng bằng nút close (×)
4. THE Graph_Renderer SHALL hiển thị node label (ticket key) trên hoặc cạnh mỗi node

### Requirement 4: Tìm kiếm và Lọc Nodes

**User Story:** Là user, tôi muốn tìm kiếm node theo key hoặc summary, để nhanh chóng tìm ticket cần xem trong graph lớn.

#### Acceptance Criteria

1. WHEN user nhập text vào ô tìm kiếm, THE Graph_Renderer SHALL highlight các nodes có key hoặc summary khớp với query
2. WHEN user nhập text vào ô tìm kiếm, THE Graph_Renderer SHALL giảm opacity của các nodes không khớp với query
3. WHEN ô tìm kiếm trống, THE Graph_Renderer SHALL hiển thị tất cả nodes với opacity bình thường

### Requirement 5: Styling theo Obsidian Kinetic Theme

**User Story:** Là user, tôi muốn graph giữ nguyên giao diện dark theme Obsidian Kinetic, để trải nghiệm nhất quán với toàn bộ ứng dụng.

#### Acceptance Criteria

1. THE Graph_Renderer SHALL sử dụng background tối (rgba(0,0,0,0.2)) cho Graph_Container
2. THE Graph_Renderer SHALL sử dụng màu node theo type: Feature (#2dfecf), Dependency (#3386ff), UI_Module (#be9dff)
3. THE Graph_Renderer SHALL sử dụng màu edge nhạt với opacity thấp, phân biệt edge type SEMANTIC (tím, nét đứt) và edge thường (cyan)
4. THE Graph_Renderer SHALL hiển thị cluster backgrounds với màu nhạt tương ứng cho mỗi cluster
5. THE Graph_Renderer SHALL sử dụng font "Be Vietnam Pro" cho node labels, nhất quán với design system

### Requirement 6: Tích hợp Kotlin/JS với Sigma.js + Graphology

**User Story:** Là developer, tôi muốn tích hợp Sigma.js + Graphology vào Kotlin/JS project, để sử dụng thư viện JavaScript từ Kotlin code.

#### Acceptance Criteria

1. THE Kotlin_JS_Interop SHALL khai báo external declarations hoặc sử dụng js() interop để gọi Sigma.js và Graphology API từ Kotlin code
2. WHEN sigma và graphology packages được thêm vào frontend/package.json, THE build system (Vite) SHALL bundle thư viện cùng với Kotlin/JS output
3. THE Kotlin_JS_Interop SHALL cho phép tạo Graphology graph instance, thêm nodes/edges với attributes, và khởi tạo Sigma renderer từ Kotlin code
4. THE Kotlin_JS_Interop SHALL cho phép sử dụng Sigma.js camera API (zoom, pan, animate) từ Kotlin code

### Requirement 7: Scan Status và Progressive Loading

**User Story:** Là user, tôi muốn thấy graph cập nhật progressively khi scan đang chạy, để theo dõi tiến trình mà không cần refresh trang.

#### Acceptance Criteria

1. WHILE scan đang chạy, THE Graph_Renderer SHALL cập nhật graph khi có nodes mới từ polling API
2. WHEN nodes mới được thêm vào graph, THE Graph_Renderer SHALL thêm nodes mới mà không reset toàn bộ graph view (giữ nguyên vị trí pan/zoom hiện tại)
3. THE Graph_Renderer SHALL hiển thị scan status badge với thông tin tiến trình (processed/total)

### Requirement 8: Xử lý trạng thái Empty và Error

**User Story:** Là user, tôi muốn thấy thông báo rõ ràng khi graph trống hoặc có lỗi, để biết cần làm gì tiếp theo.

#### Acceptance Criteria

1. WHEN không có dữ liệu graph, THE Graph_Renderer SHALL hiển thị message "No graph data yet. Run a scan from the Dashboard."
2. IF API trả về lỗi, THEN THE Graph_Renderer SHALL hiển thị error banner với message lỗi cụ thể và nút Retry
3. IF API trả về lỗi, THEN THE Graph_Renderer SHALL hiển thị nút Switch Project để user chuyển project khác
