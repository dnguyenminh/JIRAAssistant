# Implementation Plan: Incremental Graph Rendering

## Overview

Chuyển đổi Knowledge Graph từ mô hình "build once on scan complete" sang "incremental build after each batch". Backend thêm `IncrementalGraphBuilder` với debounce/async, frontend nâng cấp polling với diff-based update và fade-in animation. Kotlin Multiplatform: shared module (Kotlin JVM), frontend module (Kotlin/JS + Cytoscape.js), tests (kotlin.test + Kotest).

## Tasks

- [x] 1. Implement IncrementalGraphBuilder in shared module
  - [x] 1.1 Create `IncrementalGraphBuilder` class
    - Create file `shared/src/commonMain/kotlin/com/assistant/scan/IncrementalGraphBuilder.kt`
    - Implement `triggerBuild(projectKey: String)` with debounce (5s) using coroutine `delay`
    - Implement `cancel()` to cancel pending debounce job
    - Use `AtomicBoolean` for `building` flag to skip overlapping builds
    - Use `CoroutineScope` for async execution — `triggerBuild` returns immediately without suspending
    - Delegate actual build to `BatchScanEngine.buildAndSaveGraph()`
    - Wrap build call in try-catch: log errors via `logToBoth()`, never propagate exceptions
    - _Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 4.3_

  - [x] 1.2 Write property test: Incremental build triggered after batch completion (Property 1)
    - **Property 1: Incremental build triggered after batch completion**
    - Generate random batch sizes and ticket counts, verify `triggerBuild()` is called after each batch and `saveGraphData()` is called after debounce
    - Create `IncrementalGraphBuildPropertyTest` in shared jvmTest
    - Tag: `Feature: incremental-graph-rendering, Property 1: Incremental build triggered`
    - **Validates: Requirements 1.1, 1.2**

  - [x] 1.3 Write property test: Non-blocking build (Property 5)
    - **Property 5: Incremental build is non-blocking**
    - Verify `triggerBuild()` returns immediately without suspending, actual build runs in separate coroutine
    - Add to `IncrementalGraphBuildPropertyTest` in shared jvmTest
    - Tag: `Feature: incremental-graph-rendering, Property 5: Non-blocking build`
    - **Validates: Requirements 4.1**

  - [x] 1.4 Write property test: Debounce coalescing (Property 6)
    - **Property 6: Debounce coalesces rapid triggers**
    - Generate sequences of N rapid trigger calls within 5s window, verify at most 1 build executes per window
    - Create `IncrementalGraphDebouncePropertyTest` in shared jvmTest
    - Tag: `Feature: incremental-graph-rendering, Property 6: Debounce coalescing`
    - **Validates: Requirements 4.2, 4.3**

- [x] 2. Integrate IncrementalGraphBuilder into BatchScanEngine
  - [x] 2.1 Modify `BatchScanEngine` to wire `IncrementalGraphBuilder`
    - Add `internal var incrementalGraphBuilder: IncrementalGraphBuilder? = null` field
    - In `scanLoop`, after each batch completes: call `incrementalGraphBuilder?.triggerBuild(projectKey)`
    - In `completeScan()`: call `incrementalGraphBuilder?.cancel()` before final `buildAndSaveGraph()`
    - In pause/cancel handlers: call `incrementalGraphBuilder?.cancel()` to prevent orphan builds
    - _Requirements: 1.1, 1.2, 1.4, 5.1, 5.2_

  - [x] 2.2 Write property test: Scan continues despite build failure (Property 2)
    - **Property 2: Scan continues despite incremental build failure**
    - Inject `buildAndSaveGraph()` failures, verify scan reaches COMPLETED with `processedCount == totalTickets`
    - Create `IncrementalBuildErrorResiliencePropertyTest` in shared jvmTest
    - Tag: `Feature: incremental-graph-rendering, Property 2: Scan continues on failure`
    - **Validates: Requirements 1.3**

  - [x] 2.3 Write property test: Final graph build on scan completion (Property 3)
    - **Property 3: Final graph build on scan completion**
    - For random ticket counts, verify `getGraphData(projectKey)` returns non-null after scan completes
    - Create `CompleteScanPreservationPropertyTest` or add to existing test class in shared jvmTest
    - Tag: `Feature: incremental-graph-rendering, Property 3: Final graph build`
    - **Validates: Requirements 1.4, 5.3**

- [x] 3. Checkpoint — Backend incremental build complete
  - Ensure all shared module tests pass, including existing `CompleteScanPreservationPropertyTest`, `GraphDataPersistencePropertyTest`, `GraphEnginePropertyTest`, `FeatureNetworkMapperPropertyTest`
  - Ask the user if questions arise.

- [x] 4. Implement frontend diff-based graph polling
  - [x] 4.1 Upgrade `GraphScanStatus.startGraphPolling()` with diff logic
    - Compare node IDs between new `GraphLayoutResponse` and current `GraphState`
    - Identify new nodes (IDs in response but not in state) and new edges
    - If Cytoscape not yet initialized (first poll with data): call existing `renderGraph()` for initial render
    - If Cytoscape already initialized: call `CytoscapeRenderer.addElementsWithFadeIn()` for incremental update
    - Update `GraphState.allNodes`, `allEdges`, `allClusters` after each successful poll
    - Update node count display after each poll
    - On scan COMPLETED: stop polling, perform final load via `renderGraph()` to ensure complete data
    - _Requirements: 2.1, 2.2, 2.4, 2.5_

  - [x] 4.2 Write property test: Graph diff correctness (Property 4)
    - **Property 4: Graph diff correctly identifies new elements**
    - Generate random graph pairs where newGraph ⊇ oldGraph, verify diff produces exactly the new node IDs and edge keys
    - Create `GraphDiffPropertyTest` in shared jvmTest (or frontend jsTest)
    - Tag: `Feature: incremental-graph-rendering, Property 4: Graph diff correctness`
    - **Validates: Requirements 2.2**

  - [x] 4.3 Write unit tests for graph polling behavior
    - Test: Graph polling starts when scan status is SCANNING (Req 2.1)
    - Test: Node count updated after successful poll (Req 2.4)
    - Test: Polling stops and final load on COMPLETED (Req 2.5)
    - Test: API error during poll → retry on next interval (Req 3.4)
    - _Requirements: 2.1, 2.4, 2.5, 3.4_

- [x] 5. Implement CytoscapeRenderer fade-in animation
  - [x] 5.1 Add `addElementsWithFadeIn()` method to `CytoscapeRenderer`
    - Add new method: `fun addElementsWithFadeIn(newNodes: List<GraphNode>, newEdges: List<GraphEdge>, newNodeIds: Set<String>)`
    - Use `cy.add()` to add new elements to existing Cytoscape instance without destroying/rebuilding
    - Apply CSS class with fade-in animation (opacity 0→1 transition) to new elements
    - Preserve positions of existing nodes — do not re-layout existing elements
    - Run incremental layout only on new nodes (e.g., `cy.layout().run()` scoped to new elements)
    - _Requirements: 2.2, 2.3_

  - [x] 5.2 Write unit tests for fade-in rendering
    - Test: Fade-in CSS class applied to new Cytoscape elements (Req 2.3)
    - Test: Loading message shown when graph empty during scan (Req 3.3)
    - _Requirements: 2.3, 3.3_

- [x] 6. Implement scan status badge updates on Knowledge Graph page
  - Update `GraphScanStatus` scan status polling to display badge with progress info (e.g., "Scanning... 15/42 — 35%")
  - Update badge to "Completed" with total count when scan finishes
  - Show loading message "Đang scan, đồ thị sẽ xuất hiện khi ticket đầu tiên được analyze..." when graph is empty during scan
  - _Requirements: 3.1, 3.2, 3.3_

  - [x] 6.1 Write unit tests for scan status badge
    - Test: Badge shows progress during SCANNING (Req 3.1)
    - Test: Badge shows "Completed" with total count (Req 3.2)
    - _Requirements: 3.1, 3.2_

- [x] 7. Checkpoint — Frontend incremental rendering complete
  - Ensure all frontend tests pass
  - Ensure existing graph rendering behavior is preserved (no regressions)
  - Ask the user if questions arise.

- [x] 8. Wire everything together and verify preservation
  - [x] 8.1 Initialize `IncrementalGraphBuilder` in application startup
    - Wire `IncrementalGraphBuilder` creation and assignment to `BatchScanEngine.incrementalGraphBuilder` in the DI/startup code
    - Ensure proper `CoroutineScope` is provided (tied to scan lifecycle)
    - _Requirements: 1.1, 1.2_

  - [x] 8.2 Verify existing test suites pass (preservation)
    - Run `CompleteScanPreservationPropertyTest` — verify scan pipeline unchanged (Req 5.1, 5.3, 5.5)
    - Run `GraphDataPersistencePropertyTest` — verify graph data persistence unchanged (Req 5.4)
    - Run `GraphEnginePropertyTest` — verify layout computation unchanged
    - Run `FeatureNetworkMapperPropertyTest` — verify mapper logic unchanged
    - _Requirements: 5.1, 5.3, 5.4, 5.5_

- [x] 9. Final checkpoint — All tests pass
  - Ensure all new and existing tests pass across shared and frontend modules
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at backend and frontend boundaries
- Property tests validate universal correctness properties using Kotest
- Unit tests validate specific examples and edge cases using kotlin.test
- Existing test suites (preservation) must continue passing — no regressions allowed
