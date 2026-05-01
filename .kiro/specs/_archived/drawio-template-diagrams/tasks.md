# Kế hoạch triển khai: Draw.io Template-Based Diagrams

## Tổng quan

Mở rộng hệ thống sinh sơ đồ trực quan trong Deep Analysis — thêm hỗ trợ draw.io diagrams sử dụng phương pháp template-based. Triển khai theo thứ tự: shared models → prompt/parser → frontend templates & engine → renderer & routing → fallback & download. Mỗi bước xây dựng trên bước trước, đảm bảo không có code orphan.

## Tasks

- [x] 1. Tạo Draw.io data models trong shared module
  - [x] 1.1 Mở rộng `DiagramData.kt` — thêm trường `format` (default `"mermaid"`) và `drawioMetadata: DrawioMetadata?` (nullable)
    - Giữ backward compatibility: default values đảm bảo diagrams hiện tại không bị ảnh hưởng
    - _Requirements: 1.1, 1.2_
  - [x] 1.2 Tạo `DrawioModels.kt` trong `shared/.../deepanalysis/models/` — `DrawioMetadata`, `DrawioNode`, `DrawioConnection`
    - `DrawioMetadata`: template (String), nodes (List<DrawioNode>), connections (List<DrawioConnection>)
    - `DrawioNode`: id, label, type — tất cả có default values
    - `DrawioConnection`: from, to, label — tất cả có default values
    - _Requirements: 1.3, 1.4, 1.5_
  - [x] 1.3 Viết property test: DiagramData serialization round-trip (Property 1)
    - **Property 1: DiagramData serialization round-trip**
    - Generator: random DiagramData với format `"drawio"`, random DrawioMetadata (random template, 1-20 nodes, 0-30 connections)
    - Serialize sang JSON rồi deserialize SHALL tạo ra đối tượng tương đương
    - **Validates: Requirements 1.6**
  - [x] 1.4 Viết property test: Mermaid format invariant (Property 2)
    - **Property 2: Mermaid format invariant**
    - Generator: random DiagramData với format `"mermaid"`, random mermaidCode
    - `drawioMetadata` SHALL là `null` và `mermaidCode` SHALL không rỗng
    - **Validates: Requirements 1.7**

- [x] 2. Mở rộng AI prompt và JSON schema cho draw.io metadata
  - [x] 2.1 Mở rộng `PromptJsonSchema.kt` — thêm `format`, `drawioMetadata` vào `diagrams` array schema
    - Thêm `format: "mermaid | drawio"`, `drawioMetadata` object với template, nodes[], connections[]
    - Mở rộng `type` enum: thêm `"deployment"`, `"bpmn"`
    - _Requirements: 3.6_
  - [x] 2.2 Mở rộng `PromptSectionTicketData.kt` — cập nhật `appendDiagramInstructions()` thêm draw.io metadata instructions
    - Hướng dẫn AI chọn format phù hợp: `"drawio"` cho deployment/infrastructure/bpmn, `"mermaid"` cho flow/component/dependency đơn giản
    - Liệt kê node types hợp lệ: webapp, database, external_api, server, mobile, cloud, user, service, queue, cache
    - Liệt kê template names hợp lệ: flow, deployment, component, dependency, bpmn
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Mở rộng response parser hỗ trợ draw.io metadata
  - [x] 3.1 Mở rộng `ResponseJsonModels.kt` — thêm `AIDiagram.format`, `AIDiagram.drawioMetadata`, và các model `AIDrawioMetadata`, `AIDrawioNode`, `AIDrawioConnection`
    - Tất cả fields có default values cho backward compatibility
    - _Requirements: 7.1_
  - [x] 3.2 Mở rộng `ResponseToResultMapper.kt` — cập nhật `mapDiagram()` xử lý draw.io format
    - Validate drawioMetadata presence (skip nếu thiếu, log warning)
    - Dedup node IDs (giữ node đầu tiên)
    - Filter connections tham chiếu node ID không tồn tại
    - Default format `"mermaid"` khi thiếu trường format
    - _Requirements: 7.2, 7.3, 7.4, 7.5_
  - [x] 3.3 Viết property test: Parser produces valid metadata — dedup + connection filter (Property 9)
    - **Property 9: Parser produces valid metadata (dedup + connection filter)**
    - Generator: random AI response JSON với drawioMetadata có node IDs trùng lặp và connections tham chiếu node IDs không tồn tại
    - Sau khi parse: (a) tất cả node IDs SHALL là unique, (b) tất cả connections SHALL chỉ tham chiếu node IDs tồn tại
    - **Validates: Requirements 7.3, 7.4**

- [x] 4. Checkpoint — Đảm bảo shared module compile và tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Tạo draw.io XML template files
  - [x] 5.1 Tạo thư mục `frontend/src/jsMain/resources/templates/drawio/` và 5 XML template files
    - `flow.xml` — swimlane layout template
    - `deployment.xml` — 3-tier: client → server → database
    - `component.xml` — boxes + arrows (generic, fallback)
    - `dependency.xml` — left-to-right graph
    - `bpmn.xml` — business process BPMN-style
    - Mỗi template: `<mxGraphModel><root>` với cell 0 (root) và cell 1 (default parent), không chứa user cells
    - _Requirements: 2.1, 2.2, 2.5_

- [x] 6. Implement DrawioShapeMapper — node type → draw.io style
  - [x] 6.1 Tạo `DrawioShapeMapper.kt` trong `frontend/.../pages/ticket/drawio/`
    - Map 10 node types (webapp, database, external_api, server, mobile, cloud, user, service, queue, cache) → draw.io shape/style XML
    - Unknown type → default rectangle shape
    - Function `toCell(node, position, cellId)` → mxCell XML string với vertex="1"
    - _Requirements: 2.6, 4.2, 4.5_
  - [x] 6.2 Viết property test: Unknown node type fallback to default shape (Property 6)
    - **Property 6: Unknown node type fallback to default shape**
    - Generator: random node type string không thuộc tập hợp types hợp lệ
    - `DrawioShapeMapper.toCell()` SHALL sử dụng style mặc định (rectangle) thay vì throw error
    - **Validates: Requirements 4.5**

- [x] 7. Implement DrawioLayoutEngine — node positioning
  - [x] 7.1 Tạo `DrawioLayoutEngine.kt` trong `frontend/.../pages/ticket/drawio/`
    - Function `calculate(template, nodes)` → List<Pair<Int, Int>> (x, y positions)
    - Layout strategies: deployment (tiered vertical), flow (horizontal), component (grid), dependency (left-to-right DAG), bpmn (horizontal flow)
    - Node dimensions mặc định: width=120, height=80
    - _Requirements: 4.4_
  - [x] 7.2 Viết property test: Layout positions non-overlapping (Property 5)
    - **Property 5: Layout positions non-overlapping**
    - Generator: 1-20 random nodes, bất kỳ template nào
    - `DrawioLayoutEngine.calculate()` SHALL trả về positions mà không có hai nodes nào có cùng tọa độ (x, y)
    - **Validates: Requirements 4.4**

- [x] 8. Implement DrawioTemplateRegistry — template loading/caching
  - [x] 8.1 Tạo `DrawioTemplateRegistry.kt` trong `frontend/.../pages/ticket/drawio/`
    - Function `load(templateName)` → XML string (fetch từ `/templates/drawio/{name}.xml`)
    - Cache templates sau lần load đầu tiên
    - Fallback sang template `component` khi template name không tồn tại
    - _Requirements: 2.3, 2.4_
  - [x] 8.2 Viết property test: Template fallback for unknown names (Property 3)
    - **Property 3: Template fallback for unknown names**
    - Generator: random string không thuộc tập hợp template names hợp lệ (flow, deployment, component, dependency, bpmn)
    - `DrawioTemplateRegistry.load()` SHALL trả về nội dung XML của template `component`
    - **Validates: Requirements 2.4**

- [x] 9. Implement DrawioTemplateEngine — JSON metadata → XML merge
  - [x] 9.1 Tạo `DrawioTemplateEngine.kt` trong `frontend/.../pages/ticket/drawio/`
    - Function `merge(metadata: DrawioMetadata)` → XML string hoàn chỉnh
    - Pipeline: load template → calculate positions → generate node cells (via ShapeMapper) → generate edge cells → inject vào template XML
    - _Requirements: 4.1, 4.2, 4.3, 4.6_
  - [x] 9.2 Viết property test: Merge output cell counts match metadata (Property 4)
    - **Property 4: Merge output cell counts match metadata**
    - Generator: random DrawioMetadata (random template, 1-20 nodes, 0-30 connections)
    - Output XML SHALL chứa đúng N mxCell elements có `vertex="1"` (= số nodes) và đúng M mxCell elements có `edge="1"` (= số connections)
    - **Validates: Requirements 4.2, 4.3, 4.6**

- [x] 10. Checkpoint — Đảm bảo template engine pipeline hoạt động
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement DrawioDownloadHelper — fallback download
  - [x] 11.1 Tạo `DrawioDownloadHelper.kt` trong `frontend/.../pages/ticket/drawio/`
    - Function `showFallback(container, xml, title)` — tạo Blob từ XML, hiển thị download link `.drawio`
    - Sử dụng `URL.createObjectURL()` với MIME type `application/xml`
    - Hiển thị message hướng dẫn: "Không thể render diagram. Tải file .drawio để mở trong draw.io desktop hoặc app.diagrams.net"
    - _Requirements: 9.1, 9.2, 9.3_

- [x] 12. Implement DrawioDiagramRenderer — render draw.io diagrams
  - [x] 12.1 Tạo `DrawioDiagramRenderer.kt` trong `frontend/.../pages/ticket/`
    - Function `renderInCard(card, diagram)` — merge metadata → XML → load viewer → render
    - Lazy load draw.io viewer CDN (`viewer-static.min.js`) khi có draw.io diagram đầu tiên
    - Placeholder "Loading diagram..." trong khi viewer đang load
    - Fallback: CDN fail → `DrawioDownloadHelper.showFallback()`, render fail → raw XML code block + download link
    - Dark theme styling phù hợp Obsidian Kinetic design system
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

- [x] 13. Refactor DiagramRenderer thành format router
  - [x] 13.1 Extract Mermaid logic từ `DiagramRenderer.kt` sang `MermaidDiagramRenderer.kt`
    - Di chuyển toàn bộ Mermaid rendering logic (sanitize, ensureMermaidLoaded, initMermaid, showCodeFallback) sang file mới
    - `MermaidDiagramRenderer` expose function `renderInCard(card, diagram)`
    - _Requirements: 28.3, 28.4, 28.5 (giữ nguyên behavior)_
  - [x] 13.2 Refactor `DiagramRenderer.kt` thành format router
    - Kiểm tra `diagram.format`: route sang `MermaidDiagramRenderer` (format `"mermaid"` hoặc rỗng) hoặc `DrawioDiagramRenderer` (format `"drawio"`)
    - Giữ nguyên thứ tự render trong DOM (không nhóm theo format)
    - Lazy loading: chỉ load Mermaid.js khi có mermaid diagrams, chỉ load draw.io viewer khi có drawio diagrams
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 13.3 Viết property test: Format routing correctness (Property 7)
    - **Property 7: Format routing correctness**
    - Generator: random danh sách DiagramData với format ngẫu nhiên (`"mermaid"` hoặc `"drawio"`)
    - DiagramRenderer SHALL route mỗi diagram đến đúng renderer
    - **Validates: Requirements 6.1**
  - [x] 13.4 Viết property test: Diagram rendering order preservation (Property 8)
    - **Property 8: Diagram rendering order preservation**
    - Generator: random danh sách mixed-format DiagramData
    - Thứ tự render trong DOM SHALL giữ nguyên thứ tự input
    - **Validates: Requirements 6.2**

- [x] 14. Checkpoint — Đảm bảo frontend rendering pipeline hoạt động end-to-end
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. KB storage backward compatibility
  - [x] 15.1 Verify `KBDeepAnalysisData` serialize/deserialize DiagramData với `format` và `drawioMetadata`
    - kotlinx.serialization tự động handle nhờ default values — chỉ cần verify, không cần sửa code
    - Verify KBRecord cũ (không có trường `format`) deserialize đúng với default `"mermaid"`
    - _Requirements: 8.1, 8.2, 8.3_
  - [x] 15.2 Viết property test: KB storage round-trip for draw.io diagrams (Property 10)
    - **Property 10: KB storage round-trip for draw.io diagrams**
    - Generator: random KBDeepAnalysisData chứa danh sách DiagramData với mix format (mermaid + drawio)
    - Serialize sang JSON rồi deserialize SHALL tạo ra đối tượng tương đương
    - **Validates: Requirements 8.1**

- [x] 16. Final checkpoint — Đảm bảo toàn bộ tests pass và tích hợp hoàn chỉnh
  - Ensure all tests pass, ask the user if questions arise.

## Ghi chú

- Tasks đánh dấu `*` là optional, có thể bỏ qua để MVP nhanh hơn
- Mỗi task tham chiếu requirements cụ thể để truy vết
- Checkpoints đảm bảo validation tăng dần
- Property tests validate correctness properties từ design document
- Unit tests validate edge cases và error conditions cụ thể
