# Requirements Document — Graph Filter & Focus Mode

## Giới thiệu

Trang Knowledge Graph hiện hiển thị ~1308 nodes và ~4000+ edges trong một mạng lưới dày đặc, khó đọc. Bộ lọc hiện tại (search input) chỉ giảm opacity của nodes không khớp mà không thực sự loại bỏ chúng khỏi view. Yêu cầu bổ sung các công cụ lọc và điều hướng nâng cao: lọc theo node type, lọc theo cluster, chế độ Focus Mode (click node → chỉ hiển thị node đó + neighbors), depth slider điều chỉnh số hops, nút Show All để reset, và tích hợp AND logic giữa tất cả bộ lọc với search input hiện có. Ngoài ra, tích hợp AI Chat Sidebar với trang Knowledge Graph và các trang khác để user có thể truy xuất thông tin bằng ngôn ngữ tự nhiên — ví dụ hỏi "show me ticket ICL2-24 and its dependencies" và chat tự động điều hướng graph, lọc nodes, hoặc chuyển trang.

## Glossary

- **Filter_Panel**: Khu vực UI chứa các bộ lọc (node type, cluster, focus mode, depth slider, Show All) — đặt trong header hoặc sidebar của trang Knowledge Graph
- **Node_Type_Filter**: Bộ lọc cho phép hiển thị/ẩn nodes theo type — danh sách types được trả về động từ backend dựa trên Jira issue types thực tế trong project (ví dụ: Story, Bug, Task, Sub-task, Epic)
- **Cluster_Filter**: Bộ lọc cho phép chọn một cluster cụ thể để chỉ hiển thị nodes thuộc cluster đó
- **Focus_Mode**: Chế độ hiển thị khi user click vào một node — chỉ hiển thị node được chọn và các nodes kết nối trực tiếp (neighbors), ẩn tất cả nodes còn lại. Focused node được highlight bằng màu vàng gold (#f9d423) nổi bật, kích thước lớn hơn
- **Depth_Slider**: Thanh trượt điều chỉnh số hops từ focused node: 1 hop = direct neighbors, 2 hops = neighbors of neighbors, v.v.
- **Simplify_Toggle**: Checkbox "Simplify" chỉ active khi Focus Mode bật — khi enabled, giảm số edges hiển thị cho neighbor nodes (giữ tối đa 2 edges ngoài edge đến focused node) để giảm rối rắm
- **Graph_Renderer**: Module frontend (CytoscapeRenderer.kt) chịu trách nhiệm hiển thị graph bằng Cytoscape.js — hỗ trợ native node drag, hide/show, auto-layout, camera fit
- **Graph_State**: Object (GraphState.kt) quản lý trạng thái graph: allNodes, allEdges, allClusters, filteredNodeIds, typeColorMap
- **Sigma_Highlight**: (Đã loại bỏ — thay bằng Cytoscape.js native hide/show + CSS classes)
- **Combined_Filter**: Logic kết hợp tất cả bộ lọc đang active (node type + cluster + focus mode + search) bằng phép AND — node phải thỏa mãn TẤT CẢ bộ lọc để được hiển thị
- **Visible_Node_Set**: Tập hợp node IDs còn hiển thị sau khi áp dụng Combined_Filter
- **BFS_Traversal**: Thuật toán duyệt đồ thị theo chiều rộng (Breadth-First Search) để tìm tất cả nodes trong phạm vi N hops từ focused node
- **AI_Chat_Sidebar**: Component chat AI hiện có (AIChatSidebar.kt) cho phép user gửi tin nhắn và nhận phản hồi từ AI — đã có sẵn với ChatContext (projectKey, currentScreen, userRole)
- **Chat_Action**: Hành động mà AI trả về trong response (ChatAction) — user click để thực thi, ví dụ: navigate to page, focus on node, apply filter
- **Chat_Context**: Object chứa thông tin ngữ cảnh gửi kèm mỗi tin nhắn chat: projectKey, currentScreen, userRole, userId — cần mở rộng thêm graph context
- **Graph_Chat_Context**: Phần mở rộng của ChatContext chứa thông tin graph hiện tại: focused node, active filters, visible node count — giúp AI hiểu trạng thái graph khi trả lời
- **Conversational_Navigation**: Khả năng user dùng ngôn ngữ tự nhiên trong chat để điều khiển UI: lọc graph, focus node, chuyển trang, xem chi tiết ticket
- **Knowledge_Vector_Store**: Mở rộng VectorStore hiện có để lưu không chỉ attachment chunks mà còn ticket descriptions, graph relationships, và analysis results — cho phép semantic search trên toàn bộ knowledge base
- **Ticket_Embedding**: Vector embedding của ticket description + summary, lưu trong VectorStore để AI có thể tìm tickets liên quan bằng semantic similarity
- **Relationship_Embedding**: Vector embedding mô tả mối quan hệ giữa các tickets (edges trong graph), cho phép AI hiểu và trả lời về dependencies, blockers, related tickets
- **Analysis_Embedding**: Vector embedding của KBRecord (requirementSummary, rationale, evolutionHistory), cho phép AI trả lời câu hỏi về estimation, analysis results
- **Indexing_Pipeline**: Quy trình tự động index dữ liệu mới vào VectorStore sau khi scan hoàn tất hoặc khi analysis results được tạo
- **Ticket_Information_Panel**: Panel bên phải hiển thị khi user click vào một node trong graph — hiển thị chi tiết ticket bao gồm type badge, ticket key, summary, description đầy đủ, danh sách linked tickets, danh sách sub-tasks, và attachments
- **Search_Combobox**: Ô tìm kiếm dạng combobox với dropdown suggestions — tìm kiếm trên key, summary, description — kết quả chia thành groups theo loại match, mỗi kết quả có icon chỉ loại match, dropdown có checkboxes để filter loại kết quả
- **Streamable_HTTP_MCP**: Loại MCP server giao tiếp qua HTTP POST (JSON-RPC) thay vì stdio process — dùng cho remote MCP servers như Atlassian Rovo. Client gửi JSON-RPC messages qua HTTP với Authorization header, server trả về response qua HTTP response body

## Requirements

### Requirement 1: Lọc theo Node Type

**User Story:** Là user, tôi muốn lọc graph theo loại node (dựa trên Jira issue type thực tế), để chỉ xem các nodes thuộc loại tôi quan tâm trong mạng lưới dày đặc.

#### Acceptance Criteria

1. THE backend graph API SHALL trả về danh sách `nodeTypes` chứa các loại node thực tế có trong graph data, mỗi loại bao gồm: type name (từ Jira issue type), count (số nodes), và color (tự động gán từ palette)
2. THE Filter_Panel SHALL tạo checkboxes động từ danh sách `nodeTypes` trả về từ backend — tất cả được chọn mặc định, mỗi checkbox hiển thị tên type, color indicator, và được gán màu tương ứng
3. WHEN user bỏ chọn một node type checkbox, THE Graph_Renderer SHALL ẩn tất cả nodes có type tương ứng khỏi graph view
4. WHEN user chọn lại một node type checkbox, THE Graph_Renderer SHALL hiển thị lại tất cả nodes có type tương ứng trong graph view
5. WHEN một node bị ẩn bởi Node_Type_Filter, THE Graph_Renderer SHALL ẩn tất cả edges kết nối với node đó
6. WHEN user thay đổi Node_Type_Filter, THE Filter_Panel SHALL cập nhật số lượng nodes đang hiển thị (Visible_Node_Set size)
7. THE backend SHALL lấy node type từ Jira issue type (`issuetype.name`) khi build graph — ví dụ: Story, Bug, Task, Sub-task, Epic. Nếu issue type không có, fallback về "Story"

### Requirement 2: Lọc theo Cluster

**User Story:** Là user, tôi muốn chọn một cluster cụ thể để chỉ xem nodes trong cluster đó, để tập trung vào một nhóm chức năng liên quan.

#### Acceptance Criteria

1. THE Filter_Panel SHALL hiển thị một dropdown chọn cluster với option mặc định "All Clusters"
2. WHEN dữ liệu graph được load, THE Filter_Panel SHALL populate dropdown với danh sách clusters từ GraphState.allClusters (id, label, color)
3. WHEN user chọn một cluster từ dropdown, THE Graph_Renderer SHALL chỉ hiển thị nodes có clusterId khớp với cluster được chọn
4. WHEN user chọn "All Clusters", THE Graph_Renderer SHALL hiển thị tất cả nodes (không lọc theo cluster)
5. WHEN một node bị ẩn bởi Cluster_Filter, THE Graph_Renderer SHALL ẩn tất cả edges kết nối với node đó
6. THE Filter_Panel SHALL hiển thị màu cluster bên cạnh mỗi option trong dropdown

### Requirement 3: Focus Mode

**User Story:** Là user, tôi muốn click vào một node để chỉ xem node đó và các nodes kết nối trực tiếp, để hiểu mối quan hệ cục bộ mà không bị nhiễu bởi phần còn lại của graph.

#### Acceptance Criteria

1. WHEN user click vào một node trong graph, THE Graph_Renderer SHALL kích hoạt Focus_Mode: chỉ hiển thị node được click và các nodes trong phạm vi depth hops (mặc định 1 hop = direct neighbors), đồng thời mở Ticket_Information_Panel cho node đó
2. WHEN Focus_Mode đang active, THE Filter_Panel SHALL hiển thị indicator rõ ràng cho biết Focus_Mode đang bật, kèm tên/key của focused node
3. WHEN Focus_Mode đang active, THE Graph_Renderer SHALL ẩn tất cả nodes không nằm trong phạm vi depth hops từ focused node
4. WHEN Focus_Mode đang active, THE Graph_Renderer SHALL ẩn tất cả edges không kết nối hai nodes đều thuộc Visible_Node_Set
5. WHEN Focus_Mode đang active, THE Graph_Renderer SHALL animate camera để center vào focused node
6. WHEN user click vào một node khác trong khi Focus_Mode đang active, THE Graph_Renderer SHALL chuyển focus sang node mới
7. THE focused node SHALL được highlight bằng màu vàng gold (#f9d423), viền 4px, kích thước lớn hơn (28px vs 16px), label lớn hơn (15px, bold), z-index cao nhất — để dễ dàng nhận biết trên biểu đồ
8. WHEN Focus_Mode đang active VÀ visible nodes ≤ 200, THE Graph_Renderer SHALL sử dụng concentric layout: focused node ở trung tâm, depth 1 neighbors vòng trong, depth 2+ neighbors vòng ngoài

### Requirement 4: Depth Slider

**User Story:** Là user, tôi muốn điều chỉnh số hops từ focused node để mở rộng hoặc thu hẹp vùng hiển thị, để khám phá mối quan hệ ở các mức độ khác nhau.

#### Acceptance Criteria

1. THE Filter_Panel SHALL hiển thị một range slider với giá trị từ 1 đến 5, mặc định là 1
2. WHILE Focus_Mode đang active, WHEN user thay đổi giá trị Depth_Slider, THE Graph_Renderer SHALL cập nhật Visible_Node_Set bằng BFS_Traversal từ focused node với depth bằng giá trị slider
3. THE Filter_Panel SHALL hiển thị giá trị hiện tại của Depth_Slider bên cạnh slider (ví dụ: "Depth: 2 hops")
4. WHILE Focus_Mode không active, THE Depth_Slider SHALL ở trạng thái disabled (không tương tác được)
5. WHEN giá trị Depth_Slider thay đổi, THE Filter_Panel SHALL cập nhật số lượng nodes đang hiển thị

### Requirement 5: Nút Show All (Reset Filters)

**User Story:** Là user, tôi muốn có nút reset nhanh để quay lại xem toàn bộ graph sau khi đã lọc, để không phải tắt từng bộ lọc một.

#### Acceptance Criteria

1. THE Filter_Panel SHALL hiển thị nút "Show All" luôn visible trong khu vực bộ lọc
2. WHEN user click nút "Show All", THE Graph_Renderer SHALL tắt Focus_Mode (nếu đang active)
3. WHEN user click nút "Show All", THE Node_Type_Filter SHALL reset tất cả checkboxes (tất cả types từ backend) về trạng thái checked
4. WHEN user click nút "Show All", THE Cluster_Filter SHALL reset về "All Clusters"
5. WHEN user click nút "Show All", THE Depth_Slider SHALL reset về giá trị mặc định (1) và trạng thái disabled
6. WHEN user click nút "Show All", THE Simplify_Toggle SHALL reset về unchecked và trạng thái disabled
7. WHEN user click nút "Show All", THE Graph_Renderer SHALL hiển thị toàn bộ graph với tất cả nodes và edges
8. WHEN không có bộ lọc nào đang active (tất cả types checked, cluster = All, focus mode off, search trống), THE nút "Show All" SHALL ở trạng thái disabled (visual cue rằng đã hiển thị tất cả)

### Requirement 6: Search Combobox với Grouped Results

**User Story:** Là user, tôi muốn ô tìm kiếm hoạt động như combobox với dropdown suggestions, tìm kiếm trên nhiều fields (key, summary, description), kết quả chia groups với icons để dễ phân biệt, và có checkboxes để filter loại kết quả.

#### Acceptance Criteria

1. THE Search_Combobox SHALL hiển thị dropdown suggestions khi user gõ ≥ 2 ký tự, tối đa 100 kết quả
2. THE Search_Combobox SHALL tìm kiếm trên 3 fields: ticket key, summary, và description — mỗi kết quả hiển thị icon chỉ loại match (🔑 Key, 📝 Summary, 📄 Description)
3. THE Search_Combobox dropdown SHALL chia kết quả thành groups theo loại match (Key, Summary, Description), mỗi group có header hiển thị tên group + số lượng kết quả
4. THE Search_Combobox dropdown SHALL có filter bar ở đầu với checkboxes cho mỗi loại kết quả (Key, Summary, Description) — cho phép user bật/tắt từng loại, tất cả checked mặc định
5. WHEN user click vào một suggestion từ Search_Combobox, THE Search_Combobox SHALL clear search input text, tìm node tương ứng trong GraphState, và activate Focus Mode trên node đó (gọi `GraphFilterPanel.activateFocusMode(nodeId)`) — hiển thị node + neighbors theo BFS depth. KHÔNG set search text = ticket key (tránh conflict giữa search filter và focus mode)
6. THE Search_Combobox SHALL hỗ trợ keyboard navigation: Arrow Up/Down để di chuyển, Enter để chọn, Escape để đóng dropdown
7. WHEN user nhập text vào Search_Combobox VÀ có bộ lọc khác đang active, THE Combined_Filter SHALL áp dụng phép AND: node phải khớp search query VÀ thỏa mãn Node_Type_Filter VÀ thỏa mãn Cluster_Filter VÀ thỏa mãn Focus_Mode (nếu active)
8. WHEN search query thay đổi, THE Graph_Renderer SHALL tính lại Visible_Node_Set dựa trên Combined_Filter và cập nhật graph view
9. THE Graph_Renderer SHALL thực sự ẩn (remove khỏi rendering) các nodes không thuộc Visible_Node_Set, thay vì chỉ giảm opacity
10. WHEN Visible_Node_Set thay đổi, THE Filter_Panel SHALL cập nhật hiển thị số lượng nodes: "{visible count} / {total count} nodes"
11. WHEN description match, THE Search_Combobox SHALL hiển thị đoạn text xung quanh vị trí match (context snippet) thay vì full description
12. THE Search_Combobox SHALL debounce input 120ms để tránh lag khi gõ nhanh

### Requirement 7: Hiệu suất Filtering trên Graph lớn

**User Story:** Là developer, tôi muốn các thao tác lọc hoạt động mượt mà trên graph 1300+ nodes, để user không bị lag khi thay đổi bộ lọc.

#### Acceptance Criteria

1. WHEN user thay đổi bất kỳ bộ lọc nào, THE Graph_Renderer SHALL cập nhật graph view trong vòng 200ms
2. THE Combined_Filter SHALL tính toán Visible_Node_Set bằng cách lọc trên danh sách allNodes trong GraphState, tránh duyệt đồ thị không cần thiết (trừ BFS cho Focus_Mode)
3. THE Graph_Renderer SHALL sử dụng Cytoscape.js native `hide()/show()` để ẩn/hiện nodes — KHÔNG chạy layout lại cho tập lớn (>30 nodes) để tránh giật, chỉ `cy.fit()` camera vào visible nodes
4. WHEN Focus_Mode active với depth > 3, THE BFS_Traversal SHALL giới hạn kết quả tối đa 500 nodes để tránh hiển thị quá nhiều nodes
5. WHEN visible node count ≤ 30 (không Focus Mode), THE Graph_Renderer CÓ THỂ chạy auto-layout (circle/cose) để sắp xếp nodes cho dễ đọc
6. WHEN Focus_Mode active VÀ visible nodes ≤ 200, THE Graph_Renderer SHALL sử dụng concentric layout (focused node center, BFS depth xác định vòng) — tránh nodes chồng chéo ở giữa khi depth ≥ 2

### Requirement 8: AI Chat — Graph Context Awareness

**User Story:** Là user, tôi muốn AI Chat hiểu trạng thái hiện tại của graph (filters, focused node, visible nodes), để AI có thể trả lời chính xác dựa trên ngữ cảnh tôi đang xem.

#### Acceptance Criteria

1. WHEN user gửi tin nhắn từ trang Knowledge Graph, THE AI_Chat_Sidebar SHALL gửi kèm Graph_Chat_Context bao gồm: focused node key (nếu có), active node type filters, selected cluster, depth value, visible node count, và search query hiện tại
2. WHEN user chuyển sang trang khác (Dashboard, Analysis, v.v.), THE AI_Chat_Sidebar SHALL cập nhật ChatContext.currentScreen tương ứng và KHÔNG gửi Graph_Chat_Context
3. THE Graph_Chat_Context SHALL được cập nhật mỗi khi user thay đổi bất kỳ filter nào trên graph, để tin nhắn tiếp theo luôn có context mới nhất

### Requirement 9: AI Chat — Conversational Graph Navigation

**User Story:** Là user, tôi muốn dùng ngôn ngữ tự nhiên trong chat để điều khiển graph — ví dụ "focus on ICL2-24" hoặc "show only features" — để truy xuất thông tin nhanh hơn so với click UI.

#### Acceptance Criteria

1. WHEN AI response chứa Chat_Action với type "focusNode", THE AI_Chat_Sidebar SHALL kích hoạt Focus_Mode trên node có key/id tương ứng trong graph
2. WHEN AI response chứa Chat_Action với type "filterByType", THE AI_Chat_Sidebar SHALL cập nhật Node_Type_Filter theo danh sách types trong action payload
3. WHEN AI response chứa Chat_Action với type "filterByCluster", THE AI_Chat_Sidebar SHALL cập nhật Cluster_Filter theo cluster ID trong action payload
4. WHEN AI response chứa Chat_Action với type "resetFilters", THE AI_Chat_Sidebar SHALL trigger nút Show All để reset tất cả filters
5. WHEN AI response chứa Chat_Action với type "searchNodes", THE AI_Chat_Sidebar SHALL cập nhật search input với query từ action payload và trigger search filter
6. WHEN user đang ở trang khác và AI response chứa Chat_Action với type "navigateToGraph", THE AI_Chat_Sidebar SHALL chuyển đến trang Knowledge Graph trước khi thực thi graph actions

### Requirement 10: AI Chat — Cross-Page Navigation

**User Story:** Là user, tôi muốn hỏi AI về thông tin từ bất kỳ trang nào — ví dụ "show me the analysis for ICL2-24" hoặc "go to integrations" — và AI tự động chuyển trang cho tôi.

#### Acceptance Criteria

1. WHEN AI response chứa Chat_Action với type "navigate", THE AI_Chat_Sidebar SHALL gọi Router.navigateTo() để chuyển đến trang tương ứng (dashboard, knowledge_graph, analysis, ticket_intelligence, integrations, user_management)
2. WHEN AI response chứa Chat_Action với type "navigate" kèm payload chứa thông tin bổ sung (ví dụ: ticket key), THE AI_Chat_Sidebar SHALL chuyển trang VÀ truyền context để trang đích có thể hiển thị thông tin liên quan
3. THE AI_Chat_Sidebar SHALL duy trì conversation history khi user chuyển trang — không mất tin nhắn cũ
4. WHEN user hỏi về thông tin cần dữ liệu từ trang khác, THE AI SHALL trả lời dựa trên dữ liệu có sẵn từ API, KHÔNG yêu cầu user phải chuyển trang trước

### Requirement 11: AI Chat — Node Detail từ Chat

**User Story:** Là user, tôi muốn hỏi AI về chi tiết một ticket cụ thể — ví dụ "tell me about ICL2-24" — và nhận thông tin ngay trong chat mà không cần click vào node.

#### Acceptance Criteria

1. WHEN user hỏi về một ticket cụ thể, THE AI SHALL trả lời với thông tin ticket (key, summary, description, type, cluster) trong chat message
2. WHEN AI trả lời về một ticket, THE AI response SHALL bao gồm Chat_Action "focusNode" để user có thể click để focus vào node đó trong graph
3. WHEN AI trả lời về một ticket có attachments, THE AI response SHALL bao gồm số lượng attachments và trạng thái (converted/pending/failed)
4. WHEN user hỏi "what is connected to ICL2-24", THE AI SHALL liệt kê các nodes kết nối trực tiếp (neighbors) với ticket đó, bao gồm key, summary, và loại relationship (edge type)


### Requirement 12: Index Ticket Descriptions vào Vector Store

**User Story:** Là user, tôi muốn AI có thể tìm tickets liên quan bằng semantic search — ví dụ hỏi "tickets about payment processing" — thay vì chỉ tìm theo keyword exact match.

#### Acceptance Criteria

1. WHEN scan hoàn tất cho một project, THE Indexing_Pipeline SHALL tạo Ticket_Embedding cho mỗi ticket bằng cách embed `"[{key}] {summary}. {description}"` qua EmbeddingService
2. THE Indexing_Pipeline SHALL lưu mỗi Ticket_Embedding vào VectorStore với metadata: ticketId, chunkType="TICKET", filename=ticket.key
3. WHEN user hỏi AI về một chủ đề (ví dụ "payment processing"), THE ChatService SHALL semantic search VectorStore với chunkType="TICKET" để tìm top-K tickets liên quan nhất
4. THE Indexing_Pipeline SHALL skip tickets đã được index (kiểm tra existsByAttachmentId với attachmentId = "ticket:{ticketId}") để tránh duplicate
5. WHEN ticket data thay đổi (re-scan), THE Indexing_Pipeline SHALL xóa embeddings cũ và tạo mới

### Requirement 13: Index Graph Relationships vào Vector Store

**User Story:** Là user, tôi muốn AI hiểu mối quan hệ giữa các tickets — ví dụ hỏi "what depends on the authentication module" — dựa trên graph edges.

#### Acceptance Criteria

1. WHEN graph data được lưu (sau scan hoặc graph build), THE Indexing_Pipeline SHALL tạo Relationship_Embedding cho mỗi edge bằng cách embed `"{sourceKey} {edgeType} {targetKey}: {sourceSummary} → {targetSummary}"`
2. THE Indexing_Pipeline SHALL lưu mỗi Relationship_Embedding vào VectorStore với metadata: ticketId=sourceId, chunkType="RELATIONSHIP", filename="{sourceKey}-{targetKey}"
3. WHEN user hỏi AI về dependencies hoặc relationships, THE ChatService SHALL semantic search VectorStore với chunkType="RELATIONSHIP" để tìm edges liên quan
4. THE Indexing_Pipeline SHALL tạo thêm cluster summary embeddings: `"Cluster {label}: contains {nodeCount} tickets — {top5Keys}"` với chunkType="CLUSTER"

### Requirement 14: Index Analysis Results vào Vector Store

**User Story:** Là user, tôi muốn AI có thể trả lời câu hỏi về estimation và analysis — ví dụ "which tickets have high complexity" hoặc "what's the rationale for ICL2-24's estimate" — dựa trên KBRecord data.

#### Acceptance Criteria

1. WHEN KBRecord được save (sau analysis), THE Indexing_Pipeline SHALL tạo Analysis_Embedding bằng cách embed `"[{ticketId}] Estimate: {scrumPoints}pts (confidence: {confidenceScore}). {requirementSummary}. Rationale: {rationale}"`
2. THE Indexing_Pipeline SHALL lưu Analysis_Embedding vào VectorStore với metadata: ticketId, chunkType="ANALYSIS", filename=ticketId
3. WHEN user hỏi AI về estimation hoặc complexity, THE ChatService SHALL semantic search VectorStore với chunkType="ANALYSIS" để tìm records liên quan
4. THE Indexing_Pipeline SHALL index evolution history entries riêng biệt: `"[{ticketId}] v{version} ({date}): {description} [{changeType}]"` với chunkType="EVOLUTION"

### Requirement 15: Enhanced Chat Context với Semantic Search

**User Story:** Là developer, tôi muốn ChatService sử dụng semantic search trên toàn bộ Knowledge_Vector_Store (tickets + relationships + analysis + attachments) để xây dựng context phong phú hơn cho AI prompt.

#### Acceptance Criteria

1. WHEN ChatService xây dựng prompt, THE buildAttachmentContext SHALL được mở rộng thành buildKnowledgeContext — semantic search trên TẤT CẢ chunkTypes (TICKET, RELATIONSHIP, ANALYSIS, EVOLUTION, attachment chunks) thay vì chỉ attachment chunks
2. THE buildKnowledgeContext SHALL trả về top-10 chunks liên quan nhất (tăng từ top-5), grouped theo chunkType để AI dễ phân biệt nguồn thông tin
3. THE buildKnowledgeContext SHALL format output theo sections: `--- RELEVANT TICKETS ---`, `--- RELATIONSHIPS ---`, `--- ANALYSIS ---`, `--- ATTACHMENTS ---`
4. WHEN VectorStore trống hoặc không có kết quả, THE buildKnowledgeContext SHALL fallback về hành vi hiện tại (trả về "No attachment data.")
5. THE buildKnowledgeContext SHALL hoàn thành trong vòng 500ms cho queries trên VectorStore có 10,000+ chunks

### Requirement 16: Indexing Pipeline Performance và Reliability

**User Story:** Là developer, tôi muốn Indexing_Pipeline chạy hiệu quả và không ảnh hưởng đến scan performance, để user không bị chậm khi scan project lớn.

#### Acceptance Criteria

1. THE Indexing_Pipeline SHALL chạy asynchronously sau khi scan hoàn tất, KHÔNG block scan process
2. THE Indexing_Pipeline SHALL batch embed requests (tối đa 20 texts per batch) để giảm số lần gọi EmbeddingService
3. WHEN EmbeddingService không available (null hoặc lỗi), THE Indexing_Pipeline SHALL skip indexing và log warning, KHÔNG throw exception
4. THE Indexing_Pipeline SHALL report progress qua log: "Indexing {type}: {processed}/{total}"
5. WHEN re-indexing (sau re-scan), THE Indexing_Pipeline SHALL xóa tất cả chunks cũ của project trước khi index mới, tránh stale data

### Requirement 17: Tích hợp Atlassian Jira MCP Server

**User Story:** Là user, tôi muốn AI Chat có thể tương tác trực tiếp với Jira — tạo ticket, update status, search issues, link tickets — thông qua ngôn ngữ tự nhiên, để không cần chuyển sang Jira UI.

#### Acceptance Criteria

1. THE hệ thống SHALL hỗ trợ cấu hình Atlassian Rovo MCP Server (`https://mcp.atlassian.com/v1/mcp`) như một MCP server trong Integrations page, với authentication qua OAuth 2.1 hoặc API token
2. THE hệ thống SHALL hỗ trợ MCP server type `streamable-http` — giao tiếp qua HTTP POST (JSON-RPC) thay vì stdio process, với Authorization header (Basic hoặc Bearer)
3. WHEN MCP server có type `streamable-http`, THE backend SHALL sử dụng HTTP-based MCP client gửi JSON-RPC messages qua HTTP POST đến server URL, kèm Authorization header từ env config
4. WHEN user cấu hình API token authentication, THE hệ thống SHALL tạo Authorization header dạng `Basic <base64(email:api_token)>` và gửi kèm mỗi HTTP request đến MCP server
5. WHEN user cấu hình OAuth 2.1, THE hệ thống SHALL thực hiện OAuth token exchange với client_id + client_secret để lấy access token, sau đó gửi `Bearer <access_token>` trong Authorization header
6. WHEN test connection cho `streamable-http` server, THE backend SHALL gửi MCP `initialize` request qua HTTP POST và verify response, KHÔNG spawn local process
7. WHEN Atlassian MCP Server được cấu hình và connected, THE AI Chat SHALL có khả năng gọi Jira MCP tools: search issues (JQL), create issue, update issue, get issue details, link issues
8. WHEN user hỏi "create a bug ticket for login page crash", THE AI SHALL sử dụng Jira MCP tool để tạo ticket trong Jira project hiện tại và trả về ticket key + link
9. WHEN user hỏi "update ICL2-24 status to In Progress", THE AI SHALL sử dụng Jira MCP tool để update issue status và confirm kết quả
10. WHEN user hỏi "find all open bugs in this project", THE AI SHALL sử dụng Jira MCP tool với JQL query để search và trả về danh sách issues
11. WHEN Atlassian MCP Server không available hoặc chưa cấu hình, THE AI Chat SHALL fallback về hành vi hiện tại (chỉ đọc data từ local database), KHÔNG throw error
12. THE McpProcessManager SHALL phân biệt giữa `stdio` servers (spawn process) và `streamable-http` servers (HTTP client) — mỗi loại có lifecycle riêng

### Requirement 18: Jira MCP — Bi-directional Sync với Knowledge Graph

**User Story:** Là user, tôi muốn khi AI tạo hoặc update ticket qua Jira MCP, Knowledge Graph tự động cập nhật để phản ánh thay đổi mới nhất.

#### Acceptance Criteria

1. WHEN AI tạo ticket mới qua Jira MCP, THE hệ thống SHALL thêm node mới vào GraphState và cập nhật graph view (nếu user đang ở trang Knowledge Graph)
2. WHEN AI link hai tickets qua Jira MCP, THE hệ thống SHALL thêm edge mới vào graph giữa hai nodes tương ứng
3. WHEN AI update ticket qua Jira MCP, THE hệ thống SHALL cập nhật node attributes (summary, type, status) trong graph
4. THE hệ thống SHALL index ticket mới/updated vào VectorStore (Ticket_Embedding) để semantic search luôn up-to-date
5. WHEN sync thất bại, THE hệ thống SHALL hiển thị warning trong chat: "Ticket updated in Jira but graph sync pending. Refresh to see changes."

### Requirement 19: Jira MCP — Confluence Integration

**User Story:** Là user, tôi muốn AI có thể tìm kiếm và tóm tắt Confluence pages liên quan đến tickets, để có thêm context khi phân tích requirements.

#### Acceptance Criteria

1. WHEN Atlassian MCP Server connected VÀ user hỏi về documentation (ví dụ "find docs about authentication"), THE AI SHALL sử dụng Confluence MCP tools để search pages và trả về summaries
2. WHEN user hỏi "what documentation exists for ICL2-24", THE AI SHALL search Confluence pages linked to ticket ICL2-24 và trả về danh sách pages với summaries
3. WHEN AI tìm thấy Confluence pages liên quan, THE AI response SHALL bao gồm Chat_Action "openUrl" để user có thể click mở page trong browser mới
4. THE AI SHALL index Confluence page summaries vào VectorStore (chunkType="CONFLUENCE") để enrichment context cho các câu hỏi sau

### Requirement 20: Ticket Information Panel — Hiển thị chi tiết đầy đủ

**User Story:** Là user, tôi muốn khi click vào một node trên graph, panel bên phải hiển thị đầy đủ thông tin ticket bao gồm description thực tế, danh sách linked tickets, và danh sách sub-tasks, để tôi có thể nắm bắt toàn bộ context của ticket mà không cần mở Jira.

#### Acceptance Criteria

1. WHEN user click vào một node trong graph, THE Ticket_Information_Panel SHALL hiển thị description đầy đủ từ ticket data thay vì text mặc định "No description available" khi ticket có description
2. IF ticket không có description (null hoặc blank), THEN THE Ticket_Information_Panel SHALL hiển thị text "No description available." làm fallback
3. WHEN user click vào một node, THE Ticket_Information_Panel SHALL gọi API để lấy danh sách linked tickets của ticket đang xem và hiển thị trong section "LINKED TICKETS"
4. THE Ticket_Information_Panel SHALL hiển thị mỗi linked ticket với: ticket key (clickable để focus node tương ứng trong graph), summary, và loại relationship (ví dụ: blocks, is blocked by, relates to)
5. WHEN user click vào một node, THE Ticket_Information_Panel SHALL gọi API để lấy danh sách sub-tasks của ticket đang xem và hiển thị trong section "SUB-TASKS"
6. THE Ticket_Information_Panel SHALL hiển thị mỗi sub-task với: ticket key (clickable để focus node tương ứng trong graph), summary, và status
7. IF ticket không có linked tickets, THEN THE Ticket_Information_Panel SHALL ẩn section "LINKED TICKETS" thay vì hiển thị danh sách trống
8. IF ticket không có sub-tasks, THEN THE Ticket_Information_Panel SHALL ẩn section "SUB-TASKS" thay vì hiển thị danh sách trống
9. WHEN user click vào ticket key trong danh sách linked tickets hoặc sub-tasks, THE Graph_Renderer SHALL focus vào node tương ứng trong graph (nếu node tồn tại trong graph hiện tại)


### Requirement 21: Simplify Toggle — Giảm rối rắm edges trong Focus Mode

**User Story:** Là user, tôi muốn có option để giảm số lượng edges hiển thị khi Focus Mode active, vì với depth 2+ các neighbor nodes có quá nhiều kết nối chéo gây rối rắm, khó đọc.

#### Acceptance Criteria

1. THE Filter_Panel SHALL hiển thị checkbox "Simplify" bên cạnh Depth_Slider
2. WHILE Focus_Mode không active, THE Simplify_Toggle SHALL ở trạng thái disabled (không tương tác được, opacity thấp)
3. WHEN Focus_Mode được kích hoạt, THE Simplify_Toggle SHALL chuyển sang trạng thái enabled
4. WHEN Simplify_Toggle được checked VÀ Focus_Mode active, THE Graph_Renderer SHALL giữ TẤT CẢ edges kết nối đến focused node, nhưng giới hạn mỗi neighbor node tối đa 2 edges khác (không tính edge đến focused node)
5. WHEN Simplify_Toggle được unchecked, THE Graph_Renderer SHALL hiển thị tất cả edges giữa visible nodes như bình thường
6. WHEN user thoát Focus_Mode hoặc click "Show All", THE Simplify_Toggle SHALL reset về unchecked và disabled
7. THE Simplify_Toggle SHALL trigger filter recalculation khi thay đổi trạng thái checked/unchecked
