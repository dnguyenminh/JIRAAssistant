# Bugfix Requirements Document

## Introduction

Bugfix này giải quyết 2 vấn đề liên quan trong quá trình batch scan trên Dashboard:

**Vấn đề 1 — Linked tickets bị bỏ qua:**
Khi BatchScanEngine quét tickets, các linked tickets (related to, blocked by, is blocked by, duplicates, v.v.) không được phân tích nếu chúng không thuộc project hiện tại hoặc không nằm trong danh sách scan. Knowledge Graph và relationship network bị thiếu data — các edge tới linked ticket ngoài project bị drop hoàn toàn.

**Vấn đề 2 — Attachment processing đồng bộ gây bottleneck:**
Hiện tại attachments được xử lý đồng bộ (download → markitdown → chunk → embed → store) ngay trong quá trình scan mỗi ticket. Pipeline này tốn thời gian (đặc biệt markitdown MCP conversion) và block AI analysis pipeline, làm tăng tổng thời gian scan đáng kể.

**Root Cause — Linked Tickets:**
- `BatchScanEngine.fetchTicketIds()` sử dụng JQL `project=$projectKey` → chỉ lấy tickets thuộc project hiện tại
- `FeatureNetworkMapper.addSingleLink()` kiểm tra `if (targetId !in idSet) return` → drop edges tới linked tickets ngoài project
- `buildAndSaveGraph()` gọi `getIssues(projectKey)` → cùng giới hạn project-scoped

**Root Cause — Attachment Bottleneck:**
- `processAttachmentsIfAvailable()` chạy trong Phase 3 của `processTicket()` — đồng bộ với scan loop
- `AttachmentPipeline.processAttachments()` thực hiện toàn bộ pipeline (download → convert → embed → store) ngay lập tức
- Markitdown MCP conversion có thể mất vài giây mỗi file, nhân với hàng trăm attachments → bottleneck nghiêm trọng

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN BatchScanEngine quét project P và ticket T có linked ticket L thuộc cùng project P nhưng L chưa nằm trong danh sách ticketIds ban đầu, THEN hệ thống bỏ qua L — không phân tích và graph edge từ T tới L bị drop

1.2 WHEN BatchScanEngine quét project P và ticket T có linked ticket L thuộc project khác Q (cross-project link), THEN hệ thống hoàn toàn bỏ qua L — FeatureNetworkMapper drop edge vì L.id không nằm trong idSet, graph không có node cho L

1.3 WHEN FeatureNetworkMapper.map() xây dựng graph từ danh sách issues, THEN hệ thống chỉ tạo nodes từ issues thuộc project hiện tại — linked tickets ngoài project không có node, relationship network thiếu data

1.4 WHEN buildAndSaveGraph() được gọi sau scan, THEN hệ thống gọi getIssues(projectKey) chỉ lấy tickets trong project → graph không chứa thông tin linked tickets ngoài project

1.5 WHEN BatchScanEngine xử lý ticket có attachments, THEN hệ thống chạy toàn bộ attachment pipeline (download → markitdown → chunk → embed → store) đồng bộ trong processTicket() Phase 3, block scan progress và tăng tổng thời gian scan

1.6 WHEN nhiều tickets có attachments lớn (PDF, DOCX, PPTX), THEN markitdown MCP conversion chạy tuần tự cho mỗi attachment, gây bottleneck nghiêm trọng — scan 1000+ tickets với attachments có thể mất thêm hàng giờ chỉ cho attachment processing

### Expected Behavior (Correct)

2.1 WHEN BatchScanEngine quét project P và ticket T có linked ticket L thuộc cùng project P, THEN hệ thống SHALL đảm bảo L nằm trong danh sách scan và được phân tích đầy đủ (nếu chưa có trong KB hoặc force re-analyze)

2.2 WHEN BatchScanEngine quét project P và ticket T có linked ticket L thuộc project khác Q (cross-project link), THEN hệ thống SHALL thu thập metadata cơ bản của L (key, summary, status, issue type) từ Jira API và tạo node trong graph với data tối thiểu, cho phép hiển thị relationship trên Knowledge Graph

2.3 WHEN FeatureNetworkMapper.map() xây dựng graph, THEN hệ thống SHALL tạo nodes cho cả linked tickets ngoài project (với metadata cơ bản) và tạo edges đầy đủ giữa tickets trong project và linked tickets, không drop edges vì thiếu node

2.4 WHEN buildAndSaveGraph() được gọi sau scan, THEN hệ thống SHALL bổ sung linked tickets ngoài project vào graph — tạo "external" nodes với metadata từ issuelinks data (key, summary, status) để graph có đầy đủ relationship data

2.5 WHEN BatchScanEngine xử lý ticket có attachments trong quá trình scan, THEN hệ thống SHALL chỉ lưu metadata của attachments (filename, size, mimeType, ticketId, status=PENDING) xuống database và tiếp tục scan ticket tiếp theo — KHÔNG chạy attachment pipeline đồng bộ

2.6 WHEN scan hoàn tất hoặc có attachments PENDING trong database, THEN hệ thống SHALL có background job (async) xử lý attachment pipeline (download → markitdown → chunk → embed → store) cho các attachments có status=PENDING, không block scan progress

### Unchanged Behavior (Regression Prevention)

3.1 WHEN BatchScanEngine quét project P và tất cả tickets không có linked tickets ngoài project, THEN hệ thống SHALL CONTINUE TO quét và phân tích bình thường — không thay đổi flow scan cơ bản

3.2 WHEN FeatureNetworkMapper tạo edges từ issuelinks giữa 2 tickets cùng thuộc project hiện tại, THEN hệ thống SHALL CONTINUE TO tạo edges bình thường với logic dedup và pairKey hiện tại

3.3 WHEN FeatureNetworkMapper tạo edges từ parent/subtask relationships trong cùng project, THEN hệ thống SHALL CONTINUE TO tạo parent_child edges bình thường

3.4 WHEN FeatureNetworkMapper tạo keyword_similarity edges, THEN hệ thống SHALL CONTINUE TO sử dụng cùng thuật toán keyword extraction và cluster connection hiện tại

3.5 WHEN BatchScanEngine xử lý ticket đã có kết quả trong KB (KB-First strategy), THEN hệ thống SHALL CONTINUE TO bỏ qua AI analysis cho ticket đó (trừ khi force re-analyze)

3.6 WHEN scan state management (START, PAUSE, RESUME, CANCEL) được sử dụng, THEN hệ thống SHALL CONTINUE TO hoạt động đúng với state machine hiện tại

3.7 WHEN attachment pipeline xử lý attachment qua background job, THEN hệ thống SHALL CONTINUE TO sử dụng cùng logic: KB-First check (skip nếu đã có trong vector store), eligibility check (size limit), markitdown conversion, chunking, embedding, và vector store save

3.8 WHEN attachment đã được xử lý thành công (status=COMPLETED trong DB), THEN hệ thống SHALL CONTINUE TO skip attachment đó trong các lần scan tiếp theo (KB-First strategy)

---

## Bug Condition (Structured Pseudocode)

### Bug Condition Function — Linked Tickets

```pascal
FUNCTION isBugCondition_LinkedTickets(X)
  INPUT: X of type ScanInput (projectKey, ticketId, linkedTickets[])
  OUTPUT: boolean
  
  FOR EACH linkedTicket IN X.linkedTickets DO
    IF linkedTicket.projectKey ≠ X.projectKey THEN
      RETURN true  // Cross-project linked ticket — bị drop hoàn toàn
    END IF
    IF linkedTicket.id NOT IN fetchTicketIds(X.projectKey) THEN
      RETURN true  // Same-project nhưng không trong scan list
    END IF
  END FOR
  
  RETURN false
END FUNCTION
```

### Bug Condition Function — Attachment Bottleneck

```pascal
FUNCTION isBugCondition_AttachmentSync(X)
  INPUT: X of type TicketScanInput (ticketId, attachments[])
  OUTPUT: boolean
  
  // Bug xảy ra khi ticket có attachments cần xử lý
  RETURN X.attachments.size > 0 
    AND EXISTS att IN X.attachments WHERE isEligible(att) 
    AND NOT existsInVectorStore(att.id)
END FUNCTION
```

### Property Specification — Fix Checking (Linked Tickets)

```pascal
// Property: Linked tickets có node và edge trong graph
FOR ALL X WHERE isBugCondition_LinkedTickets(X) DO
  graph ← buildAndSaveGraph'(X.projectKey)
  
  FOR EACH linkedTicket IN X.linkedTickets DO
    ASSERT existsNode(graph, linkedTicket.key)  // Node tồn tại (ít nhất basic metadata)
    ASSERT existsEdge(graph, X.ticketId, linkedTicket.id)  // Edge không bị drop
  END FOR
END FOR
```

### Property Specification — Fix Checking (Attachment Async)

```pascal
// Property: Attachments được lưu metadata và xử lý async
FOR ALL X WHERE isBugCondition_AttachmentSync(X) DO
  // Sau processTicket': metadata đã lưu, scan không bị block
  processTicket'(X.projectKey, X.ticketId)
  
  FOR EACH att IN X.attachments WHERE isEligible(att) DO
    ASSERT existsInAttachmentQueue(att.id, status=PENDING)  // Metadata đã lưu
  END FOR
  
  ASSERT scanDuration'(X) < scanDuration(X)  // Scan nhanh hơn vì không chờ attachment
  
  // Background job xử lý sau
  runAttachmentJob'()
  FOR EACH att IN X.attachments WHERE isEligible(att) DO
    ASSERT existsInVectorStore(att.id)  // Attachment đã được xử lý
  END FOR
END FOR
```

### Preservation Checking

```pascal
// Property: Behavior không thay đổi cho non-buggy inputs
FOR ALL X WHERE NOT isBugCondition_LinkedTickets(X) DO
  ASSERT buildAndSaveGraph(X.projectKey) = buildAndSaveGraph'(X.projectKey)
END FOR

FOR ALL X WHERE NOT isBugCondition_AttachmentSync(X) DO
  ASSERT processTicket(X.projectKey, X.ticketId) = processTicket'(X.projectKey, X.ticketId)
END FOR
```
