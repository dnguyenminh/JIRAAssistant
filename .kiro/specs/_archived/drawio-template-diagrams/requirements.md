# Draw.io Template-Based Diagrams — Requirements

## Giới thiệu

Mở rộng tính năng sinh sơ đồ trực quan (Req 28 — Mermaid diagrams) trong Deep Analysis bằng cách thêm hỗ trợ định dạng draw.io sử dụng phương pháp template-based. Mermaid có hạn chế: ít shapes, không có icons (server, database, cloud, user, mobile), không hỗ trợ rich styling, chỉ auto-layout. Với deployment diagrams và business process flows, draw.io cho chất lượng trực quan tốt hơn đáng kể.

Phương pháp hybrid: giữ Mermaid cho flow/component/dependency diagrams đơn giản, thêm draw.io cho deployment, infrastructure, và business process diagrams cần shapes và icons phong phú hơn.

## Thuật ngữ

- **Draw.io_Template_Engine**: Module frontend xử lý merge JSON metadata vào XML template và render draw.io diagram
- **Draw.io_Viewer**: Thư viện viewer.min.js (~1.5MB) từ draw.io, load via CDN on-demand
- **Drawio_Metadata**: Cấu trúc JSON mô tả nodes, connections, và template type — AI sinh ra thay vì raw XML
- **Template_Registry**: Registry quản lý các built-in draw.io XML templates (flow, deployment, component, dependency, bpmn)
- **DiagramData_Model**: Model dữ liệu diagram trong shared module, mở rộng thêm format và drawio metadata
- **Mermaid_Renderer**: DiagramRenderer hiện tại, render Mermaid diagrams (Req 28.3-28.5)
- **Drawio_Renderer**: Component mới render draw.io diagrams từ merged XML
- **Deep_Analysis_Prompt_Builder**: Component xây dựng prompt AI (đã có), mở rộng thêm instructions cho draw.io metadata

## Yêu cầu

### Yêu cầu 1: Mở rộng DiagramData model hỗ trợ dual format

**User Story:** Là một developer, tôi muốn DiagramData model hỗ trợ cả Mermaid và draw.io format, để frontend có thể phát hiện format và route đến renderer phù hợp.

#### Tiêu chí chấp nhận

1.1 THE DiagramData_Model SHALL bao gồm trường `format` với giá trị `"mermaid"` hoặc `"drawio"`, mặc định là `"mermaid"` để đảm bảo backward compatibility với diagrams hiện tại (Req 28.2)

1.2 THE DiagramData_Model SHALL bao gồm trường `drawioMetadata` kiểu `DrawioMetadata?` (nullable), chứa cấu trúc JSON metadata khi format là `"drawio"`

1.3 THE DrawioMetadata SHALL bao gồm các trường: `template` (tên template), `nodes` (danh sách DrawioNode), và `connections` (danh sách DrawioConnection)

1.4 THE DrawioNode SHALL bao gồm các trường: `id` (unique identifier), `label` (display text), `type` (node type mapping đến shape trong template, ví dụ: "webapp", "database", "external_api", "server", "mobile", "cloud", "user", "service", "queue", "cache")

1.5 THE DrawioConnection SHALL bao gồm các trường: `from` (source node id), `to` (target node id), `label` (connection label, optional)

1.6 FOR ALL DiagramData hợp lệ có format `"drawio"`, serialize rồi deserialize SHALL tạo ra đối tượng tương đương (round-trip property)

1.7 FOR ALL DiagramData hợp lệ có format `"mermaid"`, trường `drawioMetadata` SHALL là null và trường `mermaidCode` SHALL chứa Mermaid syntax hợp lệ — đảm bảo Mermaid rendering hiện tại không bị ảnh hưởng

### Yêu cầu 2: Built-in draw.io XML templates

**User Story:** Là một BA/PM, tôi muốn hệ thống cung cấp các template draw.io chuyên biệt cho từng loại sơ đồ, để kết quả trực quan có shapes, icons, và layout phù hợp với ngữ cảnh.

#### Tiêu chí chấp nhận

2.1 THE Template_Registry SHALL cung cấp tối thiểu 5 built-in draw.io XML templates: flow (swimlane layout), deployment (3-tier: client → server → database), component (boxes + arrows), dependency (left-to-right graph), và bpmn (business process BPMN-style)

2.2 THE Template_Registry SHALL lưu trữ templates dưới dạng XML resource files trong frontend module (`src/jsMain/resources/templates/drawio/`)

2.3 WHEN Draw.io_Template_Engine yêu cầu template theo tên, THE Template_Registry SHALL trả về nội dung XML tương ứng

2.4 IF template name không tồn tại trong registry, THEN THE Template_Registry SHALL fallback sang template `component` (generic nhất)

2.5 THE templates SHALL sử dụng draw.io XML format chuẩn với placeholder markers cho nodes và connections, cho phép merge động với JSON metadata

2.6 THE templates SHALL bao gồm shape definitions cho các node types phổ biến: webapp, database, external_api, server, mobile, cloud, user, service, queue, cache — mỗi type mapping đến draw.io shape/icon tương ứng

### Yêu cầu 3: AI sinh draw.io metadata JSON

**User Story:** Là một BA/PM, tôi muốn AI sinh structured JSON metadata cho draw.io diagrams thay vì raw XML, để đảm bảo output luôn hợp lệ và dễ merge vào template.

#### Tiêu chí chấp nhận

3.1 THE Deep_Analysis_Prompt_Builder SHALL mở rộng diagram instructions (hiện tại chỉ Mermaid — Req 28.1) để thêm hướng dẫn AI sinh draw.io metadata JSON cho deployment diagrams và infrastructure diagrams

3.2 THE Deep_Analysis_Prompt_Builder SHALL chỉ định JSON schema cụ thể cho drawio metadata trong prompt, bao gồm: template name, nodes array (id, label, type), connections array (from, to, label)

3.3 THE Deep_Analysis_Prompt_Builder SHALL hướng dẫn AI chọn format phù hợp: sử dụng `"drawio"` cho deployment, infrastructure, và business process diagrams; sử dụng `"mermaid"` cho flow, component, và dependency diagrams đơn giản

3.4 THE AI prompt SHALL liệt kê danh sách node types hợp lệ (webapp, database, external_api, server, mobile, cloud, user, service, queue, cache) để AI chọn type phù hợp cho mỗi node

3.5 THE AI prompt SHALL liệt kê danh sách template names hợp lệ (flow, deployment, component, dependency, bpmn) để AI chọn template phù hợp

3.6 THE PromptJsonSchema SHALL mở rộng `diagrams` array schema để bao gồm trường `format`, `drawioMetadata` bên cạnh `mermaidCode` hiện tại

### Yêu cầu 4: Frontend merge JSON metadata vào XML template

**User Story:** Là một developer, tôi muốn frontend tự động merge AI-generated JSON metadata vào draw.io XML template, để tạo ra draw.io XML hoàn chỉnh sẵn sàng render.

#### Tiêu chí chấp nhận

4.1 WHEN DiagramData có format `"drawio"` và drawioMetadata hợp lệ, THE Draw.io_Template_Engine SHALL load XML template tương ứng từ Template_Registry, merge nodes và connections từ metadata vào template, tạo ra draw.io XML hoàn chỉnh

4.2 THE Draw.io_Template_Engine SHALL tạo draw.io XML cell cho mỗi node trong metadata, sử dụng shape definition tương ứng với node type từ template

4.3 THE Draw.io_Template_Engine SHALL tạo draw.io XML edge cho mỗi connection trong metadata, kết nối source và target cells theo node id

4.4 THE Draw.io_Template_Engine SHALL tính toán vị trí (x, y) cho mỗi node dựa trên layout strategy của template (ví dụ: deployment template sắp xếp 3 tiers từ trên xuống, dependency template sắp xếp left-to-right)

4.5 IF drawioMetadata chứa node type không có trong template shape definitions, THEN THE Draw.io_Template_Engine SHALL sử dụng shape mặc định (rectangle) cho node đó

4.6 FOR ALL drawioMetadata hợp lệ, merge vào template SHALL tạo ra draw.io XML hợp lệ mà draw.io viewer có thể render (metamorphic property: số nodes trong output XML bằng số nodes trong metadata input)

### Yêu cầu 5: Render draw.io diagrams trên frontend

**User Story:** Là một BA/PM, tôi muốn draw.io diagrams được render trực tiếp trên browser trong tab Context, để tôi xem sơ đồ deployment và business process ngay trong ứng dụng.

#### Tiêu chí chấp nhận

5.1 WHEN DiagramData có format `"drawio"`, THE Drawio_Renderer SHALL render diagram trong tab CONTEXT bên cạnh Mermaid diagrams hiện tại, mỗi diagram trong card riêng với title

5.2 THE Drawio_Renderer SHALL load draw.io viewer library (viewer.min.js) via CDN on-demand, tương tự cách Mermaid.js được load hiện tại (lazy loading khi có diagram cần render)

5.3 THE Drawio_Renderer SHALL truyền merged XML vào draw.io viewer để render SVG trực tiếp trên browser

5.4 WHILE draw.io viewer đang load, THE Drawio_Renderer SHALL hiển thị placeholder "Loading diagram..." (tương tự Mermaid loading state)

5.5 IF draw.io viewer load thất bại (CDN không khả dụng), THEN THE Drawio_Renderer SHALL hiển thị link download file `.drawio` để người dùng mở trong draw.io desktop hoặc web app

5.6 IF draw.io viewer render thất bại (XML không hợp lệ), THEN THE Drawio_Renderer SHALL hiển thị raw XML trong code block kèm link download file `.drawio` (tương tự Mermaid fallback — Req 28.5)

5.7 THE Drawio_Renderer SHALL áp dụng dark theme styling phù hợp với Obsidian Kinetic design system (background transparent, border glass-border)

### Yêu cầu 6: Diagram format routing

**User Story:** Là một developer, tôi muốn frontend tự động phát hiện format của mỗi diagram và route đến renderer phù hợp, để cả Mermaid và draw.io diagrams hiển thị đúng trong cùng một view.

#### Tiêu chí chấp nhận

6.1 WHEN ContextTabRenderer render danh sách diagrams, THE Frontend_App SHALL kiểm tra trường `format` của mỗi DiagramData và route đến Mermaid_Renderer (format `"mermaid"` hoặc format rỗng) hoặc Drawio_Renderer (format `"drawio"`)

6.2 THE Frontend_App SHALL render Mermaid diagrams và draw.io diagrams xen kẽ theo thứ tự trong danh sách diagrams, không nhóm theo format

6.3 WHEN tất cả diagrams trong danh sách có format `"mermaid"`, THE Frontend_App SHALL KHÔNG load draw.io viewer library (tiết kiệm bandwidth)

6.4 WHEN tất cả diagrams trong danh sách có format `"drawio"`, THE Frontend_App SHALL KHÔNG load Mermaid.js library

### Yêu cầu 7: Response parser hỗ trợ draw.io metadata

**User Story:** Là một developer, tôi muốn DeepAnalysisResponseParser parse được draw.io metadata từ AI response, để kết quả phân tích bao gồm cả draw.io diagrams.

#### Tiêu chí chấp nhận

7.1 THE DeepAnalysisResponseParser SHALL parse trường `format` và `drawioMetadata` từ mỗi diagram trong AI response JSON

7.2 IF diagram trong AI response có format `"drawio"` nhưng thiếu `drawioMetadata`, THEN THE parser SHALL bỏ qua diagram đó và ghi log warning

7.3 IF diagram trong AI response có format `"drawio"` và drawioMetadata chứa node id trùng lặp, THEN THE parser SHALL deduplicate nodes theo id (giữ node đầu tiên)

7.4 IF diagram trong AI response có format `"drawio"` và drawioMetadata chứa connection tham chiếu node id không tồn tại, THEN THE parser SHALL bỏ qua connection đó và ghi log warning

7.5 IF diagram trong AI response thiếu trường `format`, THEN THE parser SHALL mặc định format là `"mermaid"` để đảm bảo backward compatibility

### Yêu cầu 8: KB storage cho draw.io diagrams

**User Story:** Là một developer, tôi muốn draw.io diagram metadata được lưu trữ trong Knowledge Base cùng với kết quả phân tích, để khi load từ cache, diagrams hiển thị đầy đủ.

#### Tiêu chí chấp nhận

8.1 THE KBDeepAnalysisData SHALL serialize và deserialize DiagramData bao gồm trường `format` và `drawioMetadata` vào `deep_analysis_json` column

8.2 IF KBRecord cũ chứa DiagramData không có trường `format`, THEN THE deserializer SHALL mặc định format là `"mermaid"` — đảm bảo backward compatibility với dữ liệu hiện tại

8.3 WHEN người dùng load kết quả phân tích từ KB cache (auto-load hoặc RE-ANALYZE), THE Frontend_App SHALL render draw.io diagrams đầy đủ từ cached metadata

### Yêu cầu 9: Fallback và download cho draw.io diagrams

**User Story:** Là một BA/PM, tôi muốn khi draw.io viewer không hoạt động, tôi vẫn có thể tải file .drawio về mở trong ứng dụng draw.io, để không mất thông tin sơ đồ.

#### Tiêu chí chấp nhận

9.1 WHEN draw.io viewer không khả dụng (CDN fail hoặc render error), THE Drawio_Renderer SHALL tạo Blob từ merged XML và hiển thị link download với tên file `{diagram_title}.drawio`

9.2 THE download link SHALL sử dụng `URL.createObjectURL()` với MIME type `application/xml` để browser tải file đúng định dạng

9.3 THE Drawio_Renderer SHALL hiển thị message hướng dẫn: "Không thể render diagram. Tải file .drawio để mở trong draw.io desktop hoặc app.diagrams.net"
