# Implementation Plan: Knowledge Graph Optimization

## Overview

Thay thế custom SVG renderer (Graph3DRenderer + Projection3D) bằng Sigma.js (WebGL) + Graphology (data layer). Tạo Kotlin/JS interop layer, cập nhật HTML template và CSS, simplify GraphState, cập nhật page controller và progressive loading, viết property-based tests, và xóa deprecated files.

## Tasks

- [x] 1. Thêm npm dependencies và cấu hình build
  - [x] 1.1 Thêm sigma, graphology, @types/graphology vào frontend/package.json
    - Thêm `"sigma": "^3.0.0"`, `"graphology": "^0.25.4"`, `"graphology-types": "^0.24.7"` vào dependencies
    - Chạy `npm install` trong thư mục frontend/
    - _Requirements: 6.2_

- [x] 2. Tạo SigmaInterop.kt — Kotlin/JS external declarations
  - [x] 2.1 Tạo file SigmaInterop.kt với external interfaces cho Graphology và Sigma.js
    - Khai báo `external interface GraphologyGraph` với methods: addNode, addEdge, dropNode, hasNode, clear, forEachNode, updateNodeAttribute
    - Khai báo `external interface SigmaCamera` với methods: animatedZoom, animatedUnzoom, animatedReset, animate, properties x/y/ratio
    - Khai báo `external interface SigmaInstance` với methods: getCamera, getGraph, on, off, setSetting, refresh, kill, getNodeDisplayData
    - Tạo factory functions `createGraphologyGraph()` và `createSigma()` sử dụng `js()` interop
    - File path: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/graph/SigmaInterop.kt`
    - File ≤ 200 dòng, functions ≤ 20 dòng
    - _Requirements: 6.1, 6.3, 6.4_

- [x] 3. Tạo SigmaRenderer.kt — Graph rendering logic
  - [x] 3.1 Tạo file SigmaRenderer.kt với core rendering methods
    - Implement `renderGraph()`: tạo Graphology graph từ GraphState, populate nodes/edges, khởi tạo Sigma với Obsidian Kinetic settings
    - Implement `renderEmptyState()`: hiển thị message "No graph data yet. Run a scan from the Dashboard."
    - Implement `destroy()`: gọi sigma.kill() để cleanup
    - Sigma settings: labelFont "Be Vietnam Pro", defaultNodeColor "#2dfecf", background tối, minCameraRatio 0.1, maxCameraRatio 10
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 5.1, 5.2, 5.5, 8.1_

  - [x] 3.2 Implement interaction methods trong SigmaRenderer.kt
    - Implement `setupEventHandlers()`: click node → GraphDetailPanel.show(), hover → highlightNode(), leave → resetHighlight()
    - Implement `highlightNode(nodeId)`: highlight node + connected edges, dim others
    - Implement `resetHighlight()`: reset tất cả nodes/edges về trạng thái bình thường
    - Implement `applySearchFilter(filteredIds)`: set highlighted/dimmed state cho nodes theo search results
    - Implement `centerOnNode(nodeId)`: animate camera đến node position
    - _Requirements: 3.1, 3.2, 3.4, 4.1, 4.2, 4.3, 2.5_

  - [x] 3.3 Implement progressive loading method
    - Implement `updateGraph(addedNodes, addedEdges)`: thêm nodes/edges mới vào Graphology graph mà không reset camera position
    - Giữ nguyên pan/zoom state hiện tại khi thêm nodes mới
    - _Requirements: 7.1, 7.2_

  - [x] 3.4 Write property test: Node attribute mapping preserves server data
    - **Property 1: Node attribute mapping preserves server data**
    - Generate random GraphNode lists, convert to Graphology attributes, verify x/y match GraphNode.x/y, label == GraphNode.key, color == typeColorMap[GraphNode.type]
    - Minimum 100 iterations
    - **Validates: Requirements 1.4, 3.4, 5.2**

  - [x] 3.5 Write property test: Edge attribute mapping by type
    - **Property 2: Edge attribute mapping by type**
    - Generate random GraphEdge lists with random types, convert to attributes, verify SEMANTIC → purple + dashed, others → cyan + line
    - Minimum 100 iterations
    - **Validates: Requirements 5.3**

- [x] 4. Tạo GraphNavControls.kt — Navigation buttons
  - [x] 4.1 Tạo file GraphNavControls.kt
    - Implement `bind(sigma: SigmaInstance)`: bind click handlers cho zoom in, zoom out, fit-to-screen, reset view buttons
    - Zoom In: `camera.animatedZoom({ duration: 300 })`
    - Zoom Out: `camera.animatedUnzoom({ duration: 300 })`
    - Fit-to-Screen: `camera.animatedReset({ duration: 500 })`
    - Reset View: `camera.animatedReset({ duration: 500 })`
    - File path: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/graph/GraphNavControls.kt`
    - File ≤ 200 dòng, functions ≤ 20 dòng
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_

- [x] 5. Cập nhật HTML template và CSS
  - [x] 5.1 Cập nhật knowledge-graph.html
    - Thay thế `<svg id="graphSvg">` bằng `<div id="sigmaContainer" style="width:100%;height:100%;"></div>`
    - Thêm nút Fit-to-Screen (`id="btnGraphFit"`) vào zoom controls
    - Giữ nguyên error banner, detail panel, search input, legend
    - _Requirements: 1.1, 2.3, 2.6_

  - [x] 5.2 Cập nhật knowledge-graph.css
    - Xóa SVG-specific styles (.link, .node-id, .node-title, #graphSvg cursor)
    - Thêm styles cho `#sigmaContainer` (width: 100%, height: 100%)
    - Thêm styles cho navigation control buttons (hover effects, active states)
    - Giữ nguyên .legend-box styles
    - _Requirements: 5.1, 5.4_

- [x] 6. Checkpoint — Verify new files compile
  - Ensure all new files (SigmaInterop.kt, SigmaRenderer.kt, GraphNavControls.kt) và updated templates compile thành công. Ask the user if questions arise.

- [x] 7. Simplify GraphState.kt
  - [x] 7.1 Xóa 3D state và SVG viewBox state khỏi GraphState.kt
    - Xóa: rotationX, rotationY, focalLength, isDragging, dragStartX, dragStartY
    - Xóa: viewBoxX, viewBoxY, viewBoxW, viewBoxH, isPanning, panStartX, panStartY
    - Thêm: `highlightedNodeId: String?` cho hover state
    - Cập nhật `reset()` function tương ứng
    - Giữ nguyên: allNodes, allEdges, allClusters, filteredNodeIds, selectedNode, typeColorMap, defaultClusterColors
    - _Requirements: 1.5_

- [x] 8. Cập nhật KnowledgeGraphPage.kt
  - [x] 8.1 Thay thế Graph3DRenderer bằng SigmaRenderer
    - Thay `Graph3DRenderer.renderGraph()` → `SigmaRenderer.renderGraph()`
    - Thay `Graph3DRenderer.renderEmptyState()` → `SigmaRenderer.renderEmptyState()`
    - Thay `applySearchFilter()` re-render toàn bộ → `SigmaRenderer.applySearchFilter(filteredIds)`
    - Thêm `SigmaRenderer.destroy()` vào `cleanup()`
    - _Requirements: 1.1, 4.1, 4.2, 4.3, 8.1, 8.2, 8.3_

  - [x] 8.2 Write property test: Search filter correctness
    - **Property 3: Search filter correctness**
    - Generate random query strings + random GraphNode lists, apply filter logic, verify filtered set contains exactly nodes where key.lowercase().contains(query) OR summary.lowercase().contains(query). Blank query → all node IDs
    - Minimum 100 iterations
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 9. Cập nhật GraphScanStatus.kt — Progressive loading
  - [x] 9.1 Thay thế Graph3DRenderer bằng SigmaRenderer trong progressive loading
    - Thay `Graph3DRenderer.renderGraph()` → `SigmaRenderer.updateGraph(addedNodes, addedEdges)` trong `renderGraphWithFadeIn()`
    - Thay `Graph3DRenderer.renderEmptyState()` → `SigmaRenderer.renderEmptyState()`
    - Xóa reference đến `graphSvg` element, sử dụng SigmaRenderer API
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 9.2 Write property test: Hover highlights exactly connected edges
    - **Property 4: Hover highlights exactly connected edges**
    - Generate random graph (nodes + edges), pick random node, compute highlighted edges, verify set == edges where sourceId == nodeId OR targetId == nodeId
    - Minimum 100 iterations
    - **Validates: Requirements 3.2**

- [x] 10. Checkpoint — Verify integration
  - Ensure tất cả files compile, SigmaRenderer tích hợp đúng với KnowledgeGraphPage và GraphScanStatus. Ensure all tests pass, ask the user if questions arise.

- [x] 11. Xóa deprecated files
  - [x] 11.1 Xóa các file 3D renderer không còn sử dụng
    - Xóa: `Graph3DRenderer.kt`, `Graph3DGrid.kt`, `Graph3DInteraction.kt`, `Projection3D.kt`, `ProjectedNode.kt`, `GraphPanZoom.kt`, `GraphRenderer.kt`
    - Verify không còn import references đến các file đã xóa trong toàn bộ codebase
    - _Requirements: 1.5_

- [x] 12. Final checkpoint — Verify build và tests
  - Ensure toàn bộ project build thành công, không có compile errors. Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from design document
- File size ≤ 200 dòng, function size ≤ 20 dòng theo Kotlin code standards
- HTML templates trong resources/templates/, không trong Kotlin code
- Sử dụng BlockingOverlay cho async operations theo frontend-structure rules
