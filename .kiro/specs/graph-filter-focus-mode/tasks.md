# Implementation Plan: Graph Filter & Focus Mode

## Tổng quan

Triển khai 4 nhóm chức năng: (1) Graph Filters & Focus Mode trên frontend, (2) AI Chat — Graph Integration, (3) RAG Expansion — Knowledge Vector Store trên backend, (4) Jira MCP Integration. Mỗi task xây dựng trên task trước, kết thúc bằng wiring tất cả components lại với nhau.

## Tasks

- [x] 1. Tạo data models và shared types
  - [x] 1.1 Tạo `GraphFilters` data class trong `frontend/.../models/GraphFilterModels.kt`
    - Định nghĩa `GraphFilters(enabledTypes, selectedClusterId, focusNodeId, focusDepth, searchQuery)` với defaults
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 6.1_

  - [x] 1.2 Mở rộng `ChatContext` với `graphContext` field trong `shared/.../chat/ChatDtos.kt`
    - Thêm `GraphChatContext` data class (focusedNodeKey, activeTypeFilters, selectedClusterId, depthValue, visibleNodeCount, searchQuery)
    - Thêm optional field `graphContext: GraphChatContext? = null` vào `ChatContext`
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 1.3 Thêm `chunkType` field vào `AttachmentChunk` trong `server/.../attachment/models/AttachmentChunk.kt`
    - Thêm `val chunkType: String = "ATTACHMENT"` vào data class
    - Tạo `ChunkType` object constants trong `server/.../attachment/models/ChunkType.kt`
    - _Requirements: 12.2, 13.2, 14.2_

  - [x] 1.4 Mở rộng `VectorStore` interface với chunkType filter
    - Thêm overload `search(queryEmbedding, topK, chunkType)` vào `VectorStore` interface
    - Thêm `deleteByProjectKey(projectKey, chunkType)` method
    - Cập nhật implementation(s) tương ứng
    - _Requirements: 12.3, 13.3, 15.1, 16.5_

- [x] 2. Implement GraphFilterEngine (pure logic)
  - [x] 2.1 Tạo `GraphFilterEngine.kt` trong `frontend/.../pages/graph/`
    - Implement `computeVisibleNodes(filters, allNodes, allEdges, graph): Set<String>` — AND logic giữa tất cả filters
    - Implement `computeVisibleEdges(visibleNodeIds, allEdges): Set<String>` — edge visible khi cả 2 nodes visible
    - Implement `isAnyFilterActive(filters): Boolean`
    - Sử dụng GraphState.allNodes để lọc, tránh duyệt graph không cần thiết (trừ BFS)
    - _Requirements: 1.2, 1.3, 1.4, 2.3, 2.5, 5.7, 6.1, 6.2, 6.3, 6.4, 7.2_

  - [x] 2.2 Implement `bfsTraversal(startNodeId, depth, graph): Set<String>` trong `GraphFilterEngine.kt`
    - BFS dùng Graphology `neighbors()` recursive
    - Starting node luôn thuộc kết quả
    - Cap kết quả tối đa 500 nodes khi depth > 3
    - _Requirements: 3.1, 3.3, 4.2, 7.4_

  - [x] 2.3 Property test — Property 1: Combined AND Filter Node Visibility
    - **Property 1: Combined AND Filter — Node Visibility**
    - Tạo generator `genGraphNode()`, `genGraphFilters()`
    - Verify: node ∈ visibleSet ⟺ node.type ∈ enabledTypes ∧ (clusterId match) ∧ (focus match) ∧ (search match)
    - 100+ iterations với random inputs
    - **Validates: Requirements 1.2, 1.3, 1.6, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5**

  - [x] 2.4 Property test — Property 2: Edge Visibility Derived from Node Visibility
    - **Property 2: Edge Visibility Derived from Node Visibility**
    - Verify: edge visible ⟺ source ∈ visibleSet ∧ target ∈ visibleSet
    - 100+ iterations
    - **Validates: Requirements 1.4, 2.5, 3.4**

  - [x] 2.5 Property test — Property 3: BFS Traversal Correctness
    - **Property 3: BFS Traversal Correctness**
    - Tạo generator `genGraph(nodeCount, edgeCount)`
    - Verify: (1) startNode ∈ result, (2) mọi node trong result có path ≤ D, (3) không node ngoài result có path ≤ D
    - 100+ iterations
    - **Validates: Requirements 3.1, 3.3, 4.2**

  - [x] 2.6 Property test — Property 4: BFS Result Cap
    - **Property 4: BFS Result Cap**
    - Verify: khi graph > 500 nodes và depth > 3, |BFS result| ≤ 500
    - 100+ iterations
    - **Validates: Requirements 7.4**

  - [x] 2.7 Property test — Property 5: isAnyFilterActive Correctness
    - **Property 5: isAnyFilterActive Correctness**
    - Verify: trả về true ⟺ ít nhất 1 filter khác default
    - 100+ iterations
    - **Validates: Requirements 5.7**

- [x] 3. Implement GraphFilterPanel (UI controller)
  - [x] 3.1 Cập nhật HTML template `knowledge-graph.html` — thêm filter panel UI
    - Thêm 3 checkboxes cho node types (FEATURE, DEPENDENCY, UI_MODULE) với color indicators
    - Thêm cluster dropdown với option "All Clusters"
    - Thêm Focus Mode indicator (hidden by default)
    - Thêm Depth Slider (range 1–5, default 1, disabled by default)
    - Thêm nút "Show All"
    - Thêm node count display "{visible} / {total} nodes"
    - Tuân thủ dark theme rules cho native form elements
    - _Requirements: 1.1, 1.5, 2.1, 2.6, 3.2, 4.1, 4.3, 5.1, 6.5_

  - [x] 3.2 Tạo `GraphFilterPanel.kt` trong `frontend/.../pages/graph/`
    - Implement `init()` — bind events cho checkboxes, dropdown, slider, Show All button
    - Implement `onFilterChange()` — đọc filter state từ DOM, gọi `GraphFilterEngine.computeVisibleNodes()`, cập nhật `GraphState.filteredNodeIds`, gọi `SigmaHighlight.applyFilter()`
    - Implement `activateFocusMode(nodeId)` — enable focus indicator, enable depth slider, trigger filter
    - Implement `deactivateFocusMode()` — hide indicator, disable slider, reset depth
    - Implement `resetAll()` — reset tất cả filters về defaults, gọi `onFilterChange()`
    - Implement `updateNodeCount(visible, total)` — cập nhật DOM display
    - Implement `getFilters(): GraphFilters` — đọc current state từ DOM elements
    - Implement `populateClusters(clusters)` — populate dropdown từ GraphState.allClusters
    - Debounce slider input 150ms
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.6, 3.2, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.5, 7.1_

  - [x] 3.3 Cập nhật `SigmaHighlight.kt` — mở rộng reducers để ẩn nodes/edges (hidden thay vì dim)
    - Cập nhật `nodeReducer` để set `hidden = true` cho nodes không thuộc `GraphState.filteredNodeIds` khi filter active
    - Cập nhật `edgeReducer` để set `hidden = true` cho edges có source hoặc target bị hidden
    - Giữ nguyên hover highlight logic hiện có
    - _Requirements: 6.4, 7.3_

  - [x] 3.4 Tích hợp GraphFilterPanel vào `KnowledgeGraphPage.kt`
    - Gọi `GraphFilterPanel.init()` sau khi load template
    - Gọi `GraphFilterPanel.populateClusters()` sau khi load graph data
    - Cập nhật `applySearchFilter()` để đi qua `GraphFilterPanel.onFilterChange()` thay vì xử lý riêng
    - Cập nhật node click handler để gọi `GraphFilterPanel.activateFocusMode(nodeId)`
    - Cập nhật `updateNodeCount()` để hiển thị visible/total format
    - _Requirements: 3.1, 3.5, 3.6, 6.2, 6.3_

  - [x] 3.5 Cập nhật `knowledge-graph.css` — styles cho filter panel
    - Styles cho filter panel container, checkboxes, dropdown, slider, focus indicator, Show All button
    - Dark theme compliance cho tất cả native form elements
    - _Requirements: 1.5, 2.6, 4.3_

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement AI Chat — Graph Integration
  - [x] 5.1 Cập nhật `AIChatSidebar.kt` — mở rộng `buildContext()` với `GraphChatContext`
    - Khi `currentScreen == "knowledge_graph"`, populate `graphContext` từ `GraphFilterPanel.getFilters()` và `GraphState`
    - Khi ở trang khác, `graphContext = null`
    - Cập nhật `graphContext` mỗi khi filter thay đổi (lưu latest state)
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 5.2 Tạo `GraphActionHandler.kt` trong `frontend/.../components/chat/`
    - Implement `canHandle(action): Boolean` — check action.type in {focusNode, filterByType, filterByCluster, resetFilters, searchNodes, navigateToGraph, openUrl}
    - Implement `execute(action)`:
      - `focusNode` → `GraphFilterPanel.activateFocusMode(nodeKey)` + `SigmaRenderer.centerOnNode()`
      - `filterByType` → cập nhật checkboxes + trigger `onFilterChange()`
      - `filterByCluster` → cập nhật dropdown + trigger `onFilterChange()`
      - `resetFilters` → `GraphFilterPanel.resetAll()`
      - `searchNodes` → cập nhật search input + trigger filter
      - `navigateToGraph` → navigate to knowledge_graph, queue pending action
      - `openUrl` → `window.open(url, "_blank")`
    - Handle edge cases: node not found, cluster not found, navigate then execute
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 10.1, 10.2, 19.3_

  - [x] 5.3 Tích hợp `GraphActionHandler` vào `ChatActionHandler.kt`
    - Trong `execute()`, check `GraphActionHandler.canHandle(action)` trước khi fallback về server action
    - Dispatch graph actions locally (không gọi server)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [x] 5.4 Unit tests cho GraphActionHandler
    - Test mỗi action type dispatch đúng
    - Test edge cases: node not found, navigate then execute
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 6. Implement IndexingPipeline (backend)
  - [x] 6.1 Tạo `IndexingPipeline.kt` trong `server/.../indexing/`
    - Implement `indexTickets(projectKey, tickets)` — embed `"[{key}] {summary}. {description}"`, save với chunkType=TICKET
    - Implement `indexRelationships(projectKey, edges, nodeMap)` — embed `"{sourceKey} {edgeType} {targetKey}: {sourceSummary} → {targetSummary}"`, save với chunkType=RELATIONSHIP
    - Implement `indexClusterSummaries(projectKey, clusters, nodeMap)` — embed `"Cluster {label}: contains {nodeCount} tickets — {top5Keys}"`, save với chunkType=CLUSTER
    - Skip tickets đã index (check `existsByAttachmentId("ticket:{ticketId}")`)
    - Handle null/blank description → dùng summary only
    - _Requirements: 12.1, 12.2, 12.4, 13.1, 13.2, 13.4_

  - [x] 6.2 Implement `indexAnalysisResults(projectKey, records)` trong `IndexingPipeline.kt`
    - Embed `"[{ticketId}] Estimate: {scrumPoints}pts (confidence: {confidenceScore}). {requirementSummary}. Rationale: {rationale}"`
    - Index evolution entries riêng: `"[{ticketId}] v{version} ({date}): {description} [{changeType}]"` với chunkType=EVOLUTION
    - _Requirements: 14.1, 14.2, 14.4_

  - [x] 6.3 Implement batch embedding và error handling trong `IndexingPipeline.kt`
    - Batch tối đa 20 texts per request
    - Khi EmbeddingService null/unavailable → skip + log warning, KHÔNG throw
    - Khi embed() trả về null → skip chunk, continue
    - Retry failed items 1 lần
    - Log progress: "Indexing {type}: {processed}/{total}"
    - _Requirements: 16.1, 16.2, 16.3, 16.4_

  - [x] 6.4 Implement `reindex(projectKey, graph, records)` trong `IndexingPipeline.kt`
    - Xóa tất cả chunks cũ của project (theo projectKey + chunkType) trước khi index mới
    - Gọi indexTickets + indexRelationships + indexClusterSummaries + indexAnalysisResults
    - Chạy async, không block caller
    - _Requirements: 12.5, 16.5_

  - [x] 6.5 Property test — Property 6: Embedding Text Format Correctness
    - **Property 6: Embedding Text Format Correctness**
    - Tạo generators `genTicketNode()`, `genKBRecord()`, edge data
    - Verify format strings match spec cho mỗi chunkType
    - 100+ iterations
    - **Validates: Requirements 12.1, 12.2, 13.1, 13.2, 13.4, 14.1, 14.2, 14.4**

  - [x] 6.6 Property test — Property 9: Embedding Batch Size Constraint
    - **Property 9: Embedding Batch Size Constraint**
    - Verify: N texts → ⌈N/20⌉ batches, mỗi batch ≤ 20, tổng = N
    - 100+ iterations
    - **Validates: Requirements 16.2**

  - [x] 6.7 Property test — Property 7: Indexing Idempotency và Reindex Correctness
    - **Property 7: Indexing Idempotency và Reindex Correctness**
    - Verify: (1) index 2 lần không duplicate, (2) reindex chỉ chứa data mới
    - Mock VectorStore in-memory
    - 100+ iterations
    - **Validates: Requirements 12.4, 12.5, 16.5**

- [x] 7. Mở rộng buildKnowledgeContext (backend)
  - [x] 7.1 Rename `buildAttachmentContext` → `buildKnowledgeContext` trong `ChatServiceImpl.kt`
    - Semantic search trên TẤT CẢ chunkTypes (TICKET, RELATIONSHIP, ANALYSIS, EVOLUTION, ATTACHMENT)
    - Top-10 chunks (tăng từ top-5)
    - Group output theo sections: `--- RELEVANT TICKETS ---`, `--- RELATIONSHIPS ---`, `--- ANALYSIS ---`, `--- ATTACHMENTS ---`
    - Fallback "No attachment data." khi trống
    - Cập nhật `buildFullPrompt()` để gọi `buildKnowledgeContext()` thay vì `buildAttachmentContext()`
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

  - [x] 7.2 Property test — Property 8: Knowledge Context Output Grouping
    - **Property 8: Knowledge Context Output Grouping**
    - Tạo generator `genAttachmentChunk()` với random chunkTypes
    - Verify: output chứa đúng section headers cho mỗi chunkType có mặt, chunks cùng type nằm cùng section
    - 100+ iterations
    - **Validates: Requirements 15.2, 15.3**

- [x] 8. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Tích hợp Jira MCP và bi-directional sync
  - [x] 9.1 Cập nhật Integrations page — thêm Atlassian Rovo MCP Server config
    - Thêm preset config cho `https://mcp.atlassian.com/v1/mcp` trong Integrations page
    - Hỗ trợ OAuth 2.1 hoặc API token authentication
    - _Requirements: 17.1_

  - [x] 9.2 Cập nhật `ChatServiceImpl` — xử lý Jira MCP tool responses cho graph sync
    - Khi MCP tool call tạo ticket mới → thêm node vào GraphState + cập nhật graph view
    - Khi MCP tool call link tickets → thêm edge mới
    - Khi MCP tool call update ticket → cập nhật node attributes
    - Index ticket mới/updated vào VectorStore
    - Handle sync failure → warning message trong chat
    - _Requirements: 17.2, 17.3, 17.4, 17.5, 17.6, 18.1, 18.2, 18.3, 18.4, 18.5_

  - [x] 9.3 Implement Confluence search integration
    - Khi user hỏi về documentation → sử dụng Confluence MCP tools
    - Trả về page summaries + ChatAction "openUrl" cho mỗi page
    - Index Confluence summaries vào VectorStore (chunkType=CONFLUENCE)
    - _Requirements: 19.1, 19.2, 19.3, 19.4_

  - [x] 9.4 Unit tests cho Jira MCP integration
    - Mock Atlassian MCP → verify tool call format
    - Test graph sync sau create/link/update
    - Test fallback khi MCP unavailable
    - _Requirements: 17.2, 17.6, 18.1, 18.5_

- [x] 10. Wiring và integration cuối cùng
  - [x] 10.1 Tích hợp IndexingPipeline vào scan flow
    - Hook IndexingPipeline.reindex() sau khi scan hoàn tất (async, không block scan)
    - Hook indexAnalysisResults() sau khi KBRecord được save
    - _Requirements: 12.1, 14.1, 16.1_

  - [x] 10.2 Đảm bảo conversation history persist khi chuyển trang
    - Verify AIChatSidebar duy trì messages khi Router.navigateTo() được gọi
    - Verify ChatContext.currentScreen cập nhật đúng khi chuyển trang
    - _Requirements: 10.3, 10.4_

  - [x] 10.3 Cross-page navigation từ chat
    - Implement ChatAction "navigate" với payload context (ticket key, etc.)
    - Trang đích nhận context và hiển thị thông tin liên quan
    - _Requirements: 10.1, 10.2_

  - [x] 10.4 Integration tests
    - Test chat → graph action flow end-to-end
    - Test indexing pipeline → search → verify grouped output
    - Test cross-page navigation với context passing
    - _Requirements: 9.1, 10.1, 15.1_

- [x] 11. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Migration Sigma.js → Cytoscape.js
  - [x] 12.1 Tạo `CytoscapeInterop.kt` — external declarations cho Cytoscape.js
    - Khai báo `external fun cytoscape(options: dynamic): dynamic` via `require('cytoscape')`
    - _Requirements: 1.1, 7.3_

  - [x] 12.2 Tạo `CytoscapeRenderer.kt` — thay thế SigmaRenderer + SigmaHighlight + SigmaNodeDrag + GraphLayoutHelper + GraphCameraHelper
    - `renderGraph()` — init Cytoscape instance, populate nodes/edges, apply dark theme style, enable node drag (native)
    - `applyFilter(filteredIds)` — `cy.nodes().show()` rồi hide nodes không trong set, chạy layout trên visible nodes, `cy.fit(visible, padding)`
    - `centerOnNode(nodeId)` — `cy.animate({fit:{eles:node}})`
    - `resetCamera()` — `cy.fit()`
    - `destroy()` — `cy.destroy()`
    - Node click → `GraphDetailPanel.show(node)` (KHÔNG trigger activateFocusMode)
    - Dark theme: `CytoscapeStyles.kt` — dark background, neon node colors, white labels with text-outline
    - _Requirements: 1.2, 1.3, 1.4, 2.3, 2.5, 3.1, 3.5, 6.4, 7.1, 7.2, 7.3_

  - [x] 12.3 Cập nhật `GraphNavControls.kt` — dùng Cytoscape API thay Sigma camera
    - _Requirements: 2.1, 2.2_

  - [x] 12.4 Cập nhật `GraphFilterPanel.kt` — gọi `CytoscapeRenderer.applyFilter()` thay vì `SigmaRenderer.applySearchFilter()`
    - _Requirements: 1.1, 3.2, 6.2_

  - [x] 12.5 Cập nhật `GraphActionHandler.kt` — dùng `CytoscapeRenderer` thay `SigmaRenderer`
    - _Requirements: 9.1_

  - [x] 12.6 Xóa files Sigma.js cũ
    - Đã xóa: `SigmaRenderer.kt`, `SigmaHighlight.kt`, `SigmaNodeDrag.kt`, `SigmaInterop.kt`, `SigmaAttributes.kt`, `GraphLayoutHelper.kt`, `GraphCameraHelper.kt`
    - Giữ `GraphFilterEngine.kt` (pure logic, không phụ thuộc renderer)
    - _Requirements: 7.2_

  - [x] 12.7 Cập nhật `package.json` — removed sigma + graphology dependencies
    - Chỉ còn `cytoscape` trong dependencies
    - _Cleanup_

- [x] 13. Checkpoint — Verify Cytoscape.js migration
  - Build thành công: `./gradlew :frontend:jsBrowserDevelopmentWebpack`
  - E2E verified: chọn cluster → 3 nodes tam giác + auto-layout circle + camera fit
  - E2E verified: drag node → giữ vị trí, không collapse
  - E2E verified: Show All → full 1308 nodes hiện lại

- [x] 14. Thêm SUB_TASK node type support
  - [x] 14.1 Cập nhật `GraphFilters` default `enabledTypes` thêm "SUB_TASK"
    - Trong `GraphFilterModels.kt`, đảm bảo default set là `setOf("FEATURE", "DEPENDENCY", "UI_MODULE", "SUB_TASK")`
    - _Requirements: 1.1, 5.3_

  - [x] 14.2 Cập nhật HTML template `knowledge-graph.html` — thêm checkbox thứ 4 cho SUB_TASK
    - Thêm checkbox với label "Sub Task" và color indicator `#ff9d5c`
    - Đặt sau checkbox UI_MODULE, cùng format với 3 checkboxes hiện có
    - _Requirements: 1.1, 1.5_

  - [x] 14.3 Cập nhật `GraphFilterPanel.kt` — handle 4 checkboxes
    - `init()` bind event cho checkbox SUB_TASK mới
    - `getFilters()` đọc state từ 4 checkboxes thay vì 3
    - `resetAll()` check cả 4 checkboxes khi reset
    - _Requirements: 1.1, 1.2, 1.3, 5.3_

  - [x] 14.4 Cập nhật `isAnyFilterActive` logic trong `GraphFilterEngine.kt`
    - Default set giờ có 4 types: `setOf("FEATURE", "DEPENDENCY", "UI_MODULE", "SUB_TASK")`
    - `isAnyFilterActive` trả về true khi enabledTypes ≠ set 4 types
    - _Requirements: 5.7_

  - [x] 14.5 Cập nhật `CytoscapeStyles.kt` — thêm node color cho SUB_TASK
    - Thêm style rule: nodes có type "SUB_TASK" → background-color `#ff9d5c`
    - _Requirements: 1.5_

  - [x] 14.6 Cập nhật property tests P1 và P5 cho 4 types
    - **Property 1**: Generator `genGraphNode()` include type "SUB_TASK", `genGraphFilters()` enabledTypes subset of 4 types
    - **Property 5**: Default set trong assertion là 4 types
    - **Validates: Requirements 1.2, 1.3, 5.7**

- [x] 15. Checkpoint — Verify SUB_TASK filter hoạt động
  - Build thành công
  - Verify: 4 checkboxes hiển thị đúng với colors
  - Verify: bỏ chọn SUB_TASK → ẩn SUB_TASK nodes, chọn lại → hiện lại
  - Verify: Show All reset cả 4 checkboxes
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Tạo DTOs và API endpoints cho Ticket Information Panel
  - [x] 16.1 Tạo `LinkedTicketDTO` và `SubTaskDTO` trong shared models
    - Tạo file `shared/.../models/TicketDetailDtos.kt`
    - `LinkedTicketDTO(key, summary, relationship)` — serializable
    - `SubTaskDTO(key, summary, status)` — serializable
    - _Requirements: 20.3, 20.4, 20.5, 20.6_

  - [x] 16.2 Tạo backend API endpoint `GET /api/projects/{key}/tickets/{ticketKey}/links`
    - Sử dụng `JiraClient.getIssueDetails(ticketKey)` để lấy `issuelinks`
    - Map sang `List<LinkedTicketDTO>` — extract relationship type (blocks, is blocked by, relates to, etc.)
    - Trả về empty list nếu Jira unavailable hoặc không có links
    - _Requirements: 20.3, 20.4_

  - [x] 16.3 Tạo backend API endpoint `GET /api/projects/{key}/tickets/{ticketKey}/subtasks`
    - Sử dụng `JiraClient.getIssueDetails(ticketKey)` để lấy `subtasks`
    - Map sang `List<SubTaskDTO>` — extract key, summary, status
    - Trả về empty list nếu Jira unavailable hoặc không có sub-tasks
    - _Requirements: 20.5, 20.6_

  - [x] 16.4 Unit tests cho API endpoints links và subtasks
    - Mock JiraClient → verify mapping sang DTOs đúng
    - Test empty list khi Jira unavailable
    - Test edge cases: ticket không tồn tại, issuelinks null
    - _Requirements: 20.3, 20.5_

- [x] 17. Mở rộng GraphDetailPanel — Ticket Information Panel
  - [x] 17.1 Cập nhật HTML template `knowledge-graph.html` — panel layout mới
    - Thêm section DESCRIPTION (hiển thị full description text)
    - Thêm section LINKED TICKETS (container cho danh sách linked tickets, hidden by default)
    - Thêm section SUB-TASKS (container cho danh sách sub-tasks, hidden by default)
    - Giữ nguyên sections hiện có (type badge, key, summary, attachments, Open in Jira button)
    - _Requirements: 20.1, 20.2, 20.3, 20.5, 20.7, 20.8_

  - [x] 17.2 Cập nhật `GraphDetailPanel.kt` — hiển thị description đầy đủ
    - Trong `show(node)`, render `node.description` vào section DESCRIPTION
    - Nếu description null hoặc blank → hiển thị "No description available."
    - _Requirements: 20.1, 20.2_

  - [x] 17.3 Implement `loadLinkedTickets(ticketKey)` trong `GraphDetailPanel.kt`
    - Gọi `GET /api/projects/{projectKey}/tickets/{ticketKey}/links`
    - Nếu response trống → ẩn section LINKED TICKETS hoàn toàn
    - Nếu có data → gọi `renderLinkedTickets(links)`
    - Handle API failure → ẩn section, log warning
    - _Requirements: 20.3, 20.4, 20.7_

  - [x] 17.4 Implement `loadSubTasks(ticketKey)` trong `GraphDetailPanel.kt`
    - Gọi `GET /api/projects/{projectKey}/tickets/{ticketKey}/subtasks`
    - Nếu response trống → ẩn section SUB-TASKS hoàn toàn
    - Nếu có data → gọi `renderSubTasks(subtasks)`
    - Handle API failure → ẩn section, log warning
    - _Requirements: 20.5, 20.6, 20.8_

  - [x] 17.5 Implement click-to-focus: click ticket key trong linked tickets/sub-tasks → focus node trong graph
    - Mỗi ticket key trong danh sách linked tickets và sub-tasks là clickable
    - Click → gọi `GraphFilterPanel.activateFocusMode(nodeId)` + `CytoscapeRenderer.centerOnNode(nodeId)`
    - Nếu node không tồn tại trong graph → log warning, không trigger focus
    - _Requirements: 20.9_

  - [x] 17.6 Cập nhật `knowledge-graph.css` — styles cho panel sections mới
    - Styles cho DESCRIPTION section (text wrapping, max-height with scroll)
    - Styles cho LINKED TICKETS section (list items với relationship badge)
    - Styles cho SUB-TASKS section (list items với status indicator)
    - Clickable ticket keys: primary color, hover underline, cursor pointer
    - Dark theme compliance
    - _Requirements: 20.1, 20.4, 20.6_

  - [x] 17.7 Unit tests cho GraphDetailPanel rendering
    - Test description rendering: with description, without description (fallback)
    - Test linked tickets section: with data (visible), without data (hidden)
    - Test sub-tasks section: with data (visible), without data (hidden)
    - Test click-to-focus: click ticket key → verify activateFocusMode called
    - _Requirements: 20.1, 20.2, 20.3, 20.5, 20.9_

- [x] 18. Final checkpoint — Verify Ticket Information Panel
  - Build thành công
  - Verify: click node → panel hiển thị description đầy đủ
  - Verify: linked tickets section hiển thị đúng hoặc ẩn khi trống
  - Verify: sub-tasks section hiển thị đúng hoặc ẩn khi trống
  - Verify: click ticket key trong linked tickets/sub-tasks → focus node trong graph
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks đánh dấu `*` là optional, có thể skip cho MVP nhanh hơn
- Mỗi task reference requirements cụ thể để traceability
- Checkpoints đảm bảo validation incremental
- Property tests kiểm chứng 9 correctness properties từ design document
- Tuân thủ Kotlin code standards: file ≤ 200 dòng, hàm ≤ 20 dòng, models ở package riêng
- Frontend tuân thủ HTML template pattern — không tạo HTML trong Kotlin code
- Tất cả async operations sử dụng BlockingOverlay
