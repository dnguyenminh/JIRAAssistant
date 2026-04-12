# Knowledge Graph 3D — Tasks

Status: ✅ All completed

---

## Task 133: Backend — Position thêm z, ForceDirectedGraphEngine 3D

**Req:** AC 3.14

- [x] 133.1 Mở rộng `Position` trong `GraphEngine.kt`: thêm `val z: Double = 0.0`
- [x] 133.2 Mở rộng `ForceDirectedGraphEngine.computeLayout()` sang 3D:
  - Thêm `posZ` array, `dispZ` array
  - Repulsion/Attraction tính `dz`, `dist = sqrt(dx²+dy²+dz²)`
  - Clamp `posZ` trong `[0, depth]`, depth = 600.0
  - Output `Position(posX[i], posY[i], posZ[i])`
- [x] 133.3 Unit test: layout 1 node → center position có z
- [x] 133.4 Unit test: layout 10 nodes → tất cả positions có z trong bounds

**Checkpoint:** `./gradlew :shared:test` pass

---

## Task 134: Backend — GraphRoutes thêm z field

**Req:** AC 3.14

- [x] 134.1 `GraphNodeDto` thêm `val z: Double = 0.0`
- [x] 134.2 Mapping: `z = pos?.z ?: 0.0` trong route handler
- [x] 134.3 Verify API response chứa z field (E2E test hoặc manual)

**Checkpoint:** `GET /api/graph/{key}` trả JSON có field `z`

---

## Task 135: Frontend — GraphModels.kt thêm z, GraphState mở rộng

**Req:** AC 3.14, AC 3.17, AC 3.18

- [x] 135.1 `GraphNode` thêm `val z: Double = 0.0`
- [x] 135.2 `GraphState` thêm 3D state: `rotationX`, `rotationY`, `focalLength`, `isDragging`, `dragStartX`, `dragStartY`, `isAnimating`
- [x] 135.3 `GraphState.reset()` reset 3D state về defaults

**Checkpoint:** Compile thành công, existing graph vẫn hoạt động (z=0.0 default)

---

## Task 136: Frontend — Projection3D.kt (phép chiếu + xoay)

**Req:** AC 3.15, AC 3.16, AC 3.17, AC 3.23

- [x] 136.1 Tạo file `Projection3D.kt` trong `pages/graph/`
- [x] 136.2 Implement `rotate(x, y, z, rotX, rotY): Triple<Double, Double, Double>` — Y-axis rồi X-axis rotation
- [x] 136.3 Implement `project(x, y, z, focalLength): Pair<Double, Double>` — perspective projection
- [x] 136.4 Implement `depthScale(z, focalLength): Double` — `f / (z + f)`, guard division-by-zero
- [x] 136.5 Implement `depthOpacity(z, maxZ): Double` — `clamp(1.0 - z/maxZ * 0.7, 0.15, 1.0)`
- [x] 136.6 Implement `glowIntensity(z, maxZ): Double` — tỷ lệ nghịch với z

**Checkpoint:** Tất cả functions là pure, không phụ thuộc DOM

---

## Task 137: Frontend — Graph3DRenderer.kt (render 3D)

**Req:** AC 3.15, AC 3.16, AC 3.19, AC 3.20, AC 3.22, AC 3.23

- [x] 137.1 Tạo file `Graph3DRenderer.kt` trong `pages/graph/`
- [x] 137.2 Implement `renderGraph()`:
  - Project tất cả nodes qua `Projection3D.rotate()` + `Projection3D.project()`
  - Sort theo `projectedZ` descending (painter's algorithm)
  - Render edges với gradient opacity theo avg z-depth
  - Render nodes với scale + opacity theo depth
  - LOD: `shouldShowLabel()` dựa trên scale > threshold
- [x] 137.3 Implement neon glow: SVG `<feGaussianBlur>` filter với `stdDeviation` tỷ lệ nghịch z
- [x] 137.4 Implement `renderEmptyState()` (reuse logic cũ)
- [x] 137.5 Tích hợp vào `KnowledgeGraphPage.loadGraphData()` — gọi `Graph3DRenderer.renderGraph()` thay vì `GraphRenderer.renderGraph()`

**Checkpoint:** Graph hiển thị 3D với depth cues, node xa nhỏ + mờ

---

## Task 138: Frontend — Graph3DInteraction.kt (xoay + zoom)

**Req:** AC 3.17, AC 3.18

- [x] 138.1 Tạo file `Graph3DInteraction.kt` trong `pages/graph/`
- [x] 138.2 Mouse drag → cập nhật `GraphState.rotationX/Y`, gọi `requestAnimationFrame` → re-render
- [x] 138.3 Mouse wheel → cập nhật `GraphState.focalLength` (clamp [200, 2000]), re-render
- [x] 138.4 Smooth animation: dùng `requestAnimationFrame` loop, chỉ re-render khi state thay đổi
- [x] 138.5 Cursor feedback: `grab` → `grabbing` khi drag

**Checkpoint:** Kéo chuột xoay graph mượt, scroll zoom in/out

---

## Task 139: Frontend — Graph3DGrid.kt (lưới tham chiếu z=0)

**Req:** AC 3.21

- [x] 139.1 Tạo file `Graph3DGrid.kt` trong `pages/graph/`
- [x] 139.2 Render grid lines tại z=0, project qua `Projection3D`
- [x] 139.3 Grid opacity thấp (0.06), màu `rgba(255,255,255,0.06)`
- [x] 139.4 Grid xoay cùng graph (áp dụng rotation hiện tại)

**Checkpoint:** Lưới hiển thị tại z=0, xoay cùng graph

---

## Task 140: Frontend — CSS updates cho 3D effects

**Req:** AC 3.23

- [x] 140.1 Thêm SVG filter definitions cho neon glow (`<defs><filter>`) vào template hoặc render code
- [x] 140.2 Cập nhật `knowledge-graph.css`: thêm styles cho 3D mode (cursor, transitions)
- [x] 140.3 Đảm bảo glassmorphism depth panel vẫn hoạt động trên 3D graph

**Checkpoint:** Neon glow hiển thị, CSS không conflict

---

## Task 141: Frontend — Node click + Detail Panel trong 3D

**Req:** AC 3.25

- [x] 141.1 Bind click event trên 3D rendered nodes → `GraphDetailPanel.show(node)`
- [x] 141.2 Hit detection: tính khoảng cách click position đến projected node center, chọn node gần nhất trong radius
- [x] 141.3 Verify detail panel hiển thị đúng data (key, summary, description, OPEN IN JIRA)

**Checkpoint:** Click node 3D → panel chi tiết mở đúng

---

## Task 142: Property-Based Tests

**Req:** AC 3.14–3.23, Design Properties 1–6

- [x] 142.1 Property 1: 3D Layout bounded coordinates — random graphs, verify bounds
- [x] 142.2 Property 2: Perspective projection formula — random (x,y,z,f), verify formula
- [x] 142.3 Property 3: Depth cue monotonicity — random z pairs, verify ordering
- [x] 142.4 Property 4: Rotation isometry — random points + angles, verify distance preservation
- [x] 142.5 Property 5: Painter's algorithm sort — random lists, verify descending z
- [x] 142.6 Property 6: LOD threshold consistency — random nodes, verify label visibility

**Checkpoint:** `./gradlew test` — tất cả property tests pass (100+ iterations mỗi test)

---

## Task 143: Performance Optimization cho 1800+ nodes

**Req:** AC 3.24

- [x] 143.1 Profile render time với 1800 nodes
- [x] 143.2 Optimize: batch SVG element creation, minimize DOM reads
- [x] 143.3 Optimize: skip re-render nếu rotation delta < epsilon
- [ ] 143.4 *Optional:* Canvas fallback cho > 3000 nodes nếu SVG quá chậm

**Checkpoint:** Rotation animation ≥ 30fps với 1800 nodes trên Chrome

---

## Task 144: Jira Data-Driven Network — Models & API Fields

**Req:** AC 3.26–3.30

- [x] 144.1 Thêm models `JiraParent`, `JiraSubtask`, `JiraIssueLink`, `JiraIssueLinkType`, `JiraLinkedIssue`, `JiraAttachment` vào `JiraClient.kt`
- [x] 144.2 Cập nhật `JiraIssueFields` thêm `parent`, `subtasks`, `issuelinks`, `attachment` fields (nullable)
- [x] 144.3 Cập nhật `JiraRestClient.getIssues()` request fields: `summary,status,resolution,created,updated,description,parent,subtasks,issuelinks,attachment`

**Checkpoint:** API fetch trả về đầy đủ fields mới

---

## Task 145: Jira Data-Driven Network — FeatureNetworkMapper Rewrite

**Req:** AC 3.26–3.28, AC 3.30

- [x] 145.1 Rewrite `FeatureNetworkMapper.map()` — 3 edge sources: issuelinks, parent/subtask, keyword similarity
- [x] 145.2 `addIssueLinkEdges()` — extract edges từ `issuelinks` field, type `link:{linkTypeName}`
- [x] 145.3 `addParentSubtaskEdges()` — extract edges từ `parent` và `subtasks`, type `parent_child`
- [x] 145.4 `addKeywordEdges()` — heuristic edges từ shared summary keywords, type `keyword_similarity`
- [x] 145.5 Deduplication: `pairKey()` normalize, `added` set track pairs
- [x] 145.6 Scope filtering: chỉ tạo edge giữa nodes trong `idSet` (cùng project)

**Checkpoint:** Graph có edges thực từ Jira data

---

## Task 146: Zoom Controls UI

**Req:** AC 3.31, AC 3.32

- [x] 146.1 Thêm 3 nút (Zoom In, Zoom Out, Reset View) vào `knowledge-graph.html` template
- [x] 146.2 `Graph3DInteraction.setupZoomButtons()` — event handlers cho 3 nút
- [x] 146.3 Reset View trả về `rotationX=0.3, rotationY=0, focalLength=800`

**Checkpoint:** 3 nút hoạt động đúng, Reset View trả về trạng thái mặc định
