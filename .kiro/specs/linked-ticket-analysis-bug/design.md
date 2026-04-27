# Linked Ticket Analysis Bugfix Design

## Overview

Khi `FeatureNetworkMapper` xây dựng Knowledge Graph, các linked tickets ngoài project hiện tại bị drop hoàn toàn — cả edges lẫn nodes. Root cause nằm ở `addSingleLink()` kiểm tra `if (targetId !in idSet) return` và `map()` chỉ tạo nodes từ input issues list.

Fix approach: Thay vì drop edges khi `targetId !in idSet`, thu thập metadata từ `JiraLinkedIssue` (key, summary, status) và tạo "external" nodes. Edges được giữ nguyên. Thay đổi tối thiểu, chỉ trong `FeatureNetworkMapper`.

## Glossary

- **Bug_Condition (C)**: Ticket T có linked ticket L mà `L.id !in idSet` — L thuộc project khác hoặc không nằm trong danh sách issues đầu vào
- **Property (P)**: Graph phải chứa node cho L (với basic metadata) và edge từ T tới L
- **Preservation**: Tất cả edges/nodes cho tickets trong cùng project (cả link, parent, keyword) phải giữ nguyên behavior
- **idSet**: HashSet chứa IDs của tất cả issues đầu vào cho `FeatureNetworkMapper.map()`
- **External Node**: `TicketNode` được tạo từ `JiraLinkedIssue` metadata (không phải từ full `JiraIssue`)
- **FeatureNetworkMapper**: Class trong `shared/.../domain/` xây dựng `NetworkGraph` từ danh sách `JiraIssue`
- **addSingleLink()**: Private function trong `FeatureNetworkMapper` xử lý từng `JiraIssueLink` — nơi chứa bug condition

## Bug Details

### Bug Condition

Bug xảy ra khi `FeatureNetworkMapper.addSingleLink()` gặp linked ticket có `targetId` không nằm trong `idSet`. Function return sớm, drop cả edge lẫn node cho linked ticket đó.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type { issue: JiraIssue, link: JiraIssueLink, idSet: Set<String> }
  OUTPUT: boolean
  
  targetId := link.outwardIssue?.id OR link.inwardIssue?.id
  IF targetId IS NULL THEN RETURN false
  
  RETURN targetId NOT IN idSet
END FUNCTION
```

### Examples

- Ticket `PROJ-1` (id="101") có link "blocks" tới `OTHER-5` (id="505") — `505 !in idSet` → edge bị drop, không có node cho OTHER-5
- Ticket `PROJ-2` (id="102") có link "relates to" tới `PROJ-99` (id="199") nhưng PROJ-99 không trong danh sách scan → edge bị drop
- Ticket `PROJ-3` (id="103") có link "duplicates" tới `PROJ-4` (id="104") và 104 ∈ idSet → edge được tạo bình thường (không phải bug condition)
- Ticket `PROJ-5` (id="105") có link nhưng `outwardIssue` và `inwardIssue` đều null → return sớm, không phải bug condition

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Edges giữa 2 tickets cùng thuộc `idSet` phải tiếp tục tạo bình thường với dedup logic (`pairKey`, `added` set)
- Parent/subtask edges trong cùng `idSet` phải giữ nguyên `parent_child` relationship type
- Keyword similarity edges phải sử dụng cùng thuật toán extraction và cluster connection
- Node creation cho tickets trong `idSet` phải giữ nguyên tất cả fields (id, key, summary, status, featureName, description)
- `NetworkGraph` serialization format không thay đổi

**Scope:**
Tất cả inputs mà mọi linked ticket đều nằm trong `idSet` sẽ không bị ảnh hưởng bởi fix. Bao gồm:
- Issues không có issuelinks
- Issues có links chỉ tới tickets trong cùng idSet
- Parent/subtask relationships (logic riêng, không bị ảnh hưởng)
- Keyword similarity edges (logic riêng, không bị ảnh hưởng)

## Hypothesized Root Cause

Based on code analysis, root cause đã được xác nhận:

1. **`addSingleLink()` line `if (targetId !in idSet) return`**: Drop edge hoàn toàn khi target không trong idSet. Đây là nguyên nhân chính — function không phân biệt "target không tồn tại" vs "target ở project khác".

2. **`map()` chỉ tạo nodes từ input `issues` list**: `issues.map { ... }` chỉ tạo `TicketNode` cho issues đầu vào. Không có logic tạo nodes cho linked tickets ngoài danh sách.

3. **`buildAndSaveGraph()` gọi `getIssues(projectKey)`**: Chỉ fetch tickets từ project hiện tại, nên `issues` list truyền vào `map()` không bao giờ chứa cross-project tickets. Tuy nhiên, fix không cần thay đổi ở đây — `FeatureNetworkMapper` sẽ tự tạo external nodes từ link metadata.

## Correctness Properties

Property 1: Bug Condition - External Linked Tickets Có Node Và Edge Trong Graph

_For any_ input issue có linked ticket với `targetId NOT IN idSet`, hàm `map()` đã fix SHALL tạo edge giữa issue và linked ticket, VÀ tạo external `TicketNode` cho linked ticket với metadata cơ bản (key, summary, status) từ `JiraLinkedIssue`.

**Validates: Requirements 2.2, 2.3, 2.4**

Property 2: Preservation - Internal Edges Và Nodes Không Thay Đổi

_For any_ input issues mà tất cả linked tickets đều nằm trong `idSet`, hàm `map()` đã fix SHALL produce cùng `NetworkGraph` (cùng nodes và edges) như hàm `map()` gốc, bảo toàn dedup logic, relationship types, và node metadata.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

## Fix Implementation

### Changes Required

**File**: `shared/src/commonMain/kotlin/com/assistant/domain/FeatureNetworkMapper.kt`

**Function**: `addSingleLink()` + `map()`

**Specific Changes**:

1. **Thêm `isExternal` field vào `TicketNode`**: Thêm optional boolean field `isExternal = false` vào `TicketNode` data class trong `NetworkModels.kt` để phân biệt external nodes.

2. **Sửa `addSingleLink()` — không drop khi targetId !in idSet**: Thay vì `return` khi `targetId !in idSet`, thu thập thông tin external node từ `JiraIssueLink` (outwardIssue/inwardIssue có key, summary, status). Vẫn tạo edge bình thường.

3. **Thu thập external nodes trong `map()`**: Thêm `externalNodes: MutableMap<String, TicketNode>` để collect external nodes từ `addSingleLink()`. Sau khi xử lý tất cả edges, merge external nodes vào danh sách nodes cuối cùng.

4. **Refactor `addSingleLink()` signature**: Thêm parameter `externalNodes: MutableMap<String, TicketNode>` để function có thể register external nodes khi gặp targetId ngoài idSet.

5. **Không thay đổi `buildAndSaveGraph()`**: Graph đã chứa external nodes từ FeatureNetworkMapper — không cần fetch thêm.

## Testing Strategy

### Validation Approach

Testing strategy gồm 2 phase: (1) surface counterexamples trên code chưa fix để confirm root cause, (2) verify fix hoạt động đúng và preserve behavior hiện tại.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples chứng minh bug trên code chưa fix. Confirm root cause analysis.

**Test Plan**: Tạo `JiraIssue` list với issuelinks tới tickets ngoài idSet, gọi `FeatureNetworkMapper.map()` trên code chưa fix, assert edges/nodes bị thiếu.

**Test Cases**:
1. **Cross-project link test**: Issue A có link tới issue B (B.id !in idSet) → verify edge bị drop (fail on unfixed code)
2. **Multiple external links test**: Issue có 3 links, 2 external → verify 2 edges bị drop (fail on unfixed code)
3. **Mixed internal/external test**: Issue có links cả internal và external → verify internal edges OK nhưng external bị drop (fail on unfixed code)

**Expected Counterexamples**:
- `graph.edges` không chứa edge tới external ticket
- `graph.nodes` không chứa node cho external ticket
- Root cause confirmed: `addSingleLink()` returns early khi `targetId !in idSet`

### Fix Checking

**Goal**: Verify rằng với mọi input có bug condition, hàm fix tạo đúng external nodes và edges.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  graph := map_fixed(input.issues)
  targetId := input.link.outwardIssue?.id OR input.link.inwardIssue?.id
  ASSERT existsEdge(graph, input.issue.id, targetId)
  ASSERT existsNode(graph, targetId)
  ASSERT node(graph, targetId).isExternal == true
  ASSERT node(graph, targetId).key == input.link.targetIssue.key
END FOR
```

### Preservation Checking

**Goal**: Verify rằng với mọi input KHÔNG có bug condition, hàm fix produce cùng kết quả như hàm gốc.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT map_original(input.issues) == map_fixed(input.issues)
END FOR
```

**Testing Approach**: Property-based testing recommended vì:
- Generate nhiều combinations of issues/links tự động
- Catch edge cases (empty links, null fields, duplicate links)
- Strong guarantee behavior unchanged cho non-buggy inputs

**Test Plan**: Observe behavior trên code chưa fix cho internal-only links, sau đó write PBT tests verify behavior preserved sau fix.

**Test Cases**:
1. **Internal-only links preservation**: Issues chỉ có links trong idSet → graph identical
2. **No links preservation**: Issues không có issuelinks → graph identical
3. **Parent/subtask preservation**: Parent/child edges không bị ảnh hưởng
4. **Keyword edges preservation**: Keyword similarity edges không bị ảnh hưởng

### Unit Tests

- Test `addSingleLink()` với external targetId → verify edge created + external node collected
- Test `addSingleLink()` với internal targetId → verify behavior unchanged
- Test `map()` với mix of internal/external links → verify complete graph
- Test external node metadata (key, summary, status) from JiraLinkedIssue

### Property-Based Tests

- Generate random JiraIssue lists với random links (internal/external) → verify all edges present
- Generate random JiraIssue lists với only internal links → verify graph identical to original
- Generate random link configurations → verify dedup logic still works (no duplicate edges)

### Integration Tests

- Test `buildAndSaveGraph()` end-to-end với mock JiraClient returning issues with external links
- Test graph serialization/deserialization với external nodes
- Test Knowledge Graph UI rendering với external nodes (manual verification)
