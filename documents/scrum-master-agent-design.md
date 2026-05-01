# Scrum Master Agent — Design Document

## 1. Mục đích

Scrum Master Agent là **entry point duy nhất** để user tương tác với pipeline multi-agent. Thay vì user phải biết gọi agent nào cho việc gì, user chỉ cần nói với Scrum Master và SM sẽ tự điều phối.

## 2. Nguyên tắc thiết kế

1. **Một entry point** — User chỉ cần gọi `scrum-master` cho mọi việc
2. **Context-aware** — SM biết ticket đang ở phase nào, documents nào đã có
3. **Quality gates** — SM không chuyển phase nếu chưa pass quality check
4. **Feedback loops** — SM tự chạy BA↔SA loop, DEV↔QA loop
5. **Transparent** — SM báo user đang làm gì, ở phase nào, kết quả ra sao
6. **User vẫn là người quyết định** — SM đề xuất, user approve trước khi chuyển phase lớn

## 3. SDLC Phases & Agent Mapping

```
Phase 1: Requirements    → ba-agent      → BRD.md
Phase 2: Specification   → ba-agent      → FSD.md (đọc code intelligence)
Phase 3: Design          → sa-agent      → TDD.md (đọc code intelligence + FSD)
Phase 3.5: Feedback Loop → ba↔sa         → FSD fix ↔ discrepancy report (max 5 vòng)
Phase 4: Test Planning   → qa-agent      → STP.md + STC.md
Phase 5: Implementation  → dev-agent     → Source code
Phase 6: Testing         → qa-agent      → Test execution
Phase 7: Deployment      → devops-agent  → DPG.md + RLN.md
```

## 4. Status Tracking

SM track trạng thái mỗi ticket qua file `documents/{TICKET}/STATUS.json`:

```json
{
  "ticket": "COLLEX-64",
  "currentPhase": "design",
  "phases": {
    "requirements": { "status": "done", "file": "BRD.md", "version": 1 },
    "specification": { "status": "done", "file": "FSD.md", "version": 2 },
    "design": { "status": "in_progress", "file": "TDD.md", "version": null },
    "feedback_loop": { "status": "not_started", "iterations": 0 },
    "test_planning": { "status": "not_started" },
    "implementation": { "status": "not_started" },
    "testing": { "status": "not_started" },
    "deployment": { "status": "not_started" }
  },
  "lastUpdated": "2026-04-30T10:00:00Z"
}
```

## 5. User Interaction Patterns

### Pattern 1: Full pipeline
```
User: "COLLEX-64"
SM: Kiểm tra status → chưa có gì → bắt đầu từ Phase 1
SM: "📋 COLLEX-64 — Bắt đầu tạo tài liệu. Phase 1: Requirements (BA Agent)..."
SM: Gọi BA → BRD done
SM: "✅ BRD done. Chuyển sang Phase 2: Specification?"
User: "OK"
SM: Gọi BA → FSD done (với code intelligence)
SM: "✅ FSD done. Chuyển sang Phase 3: Design?"
User: "OK"
SM: Gọi SA → TDD done + discrepancy report
SM: "⚠️ SA phát hiện 3 discrepancies. Chạy feedback loop..."
SM: Tự chạy BA fix → SA review → lặp cho đến hết
SM: "✅ FSD v2 + TDD v2 consistent. Chuyển sang Phase 4: Test Planning?"
...
```

### Pattern 2: Resume từ giữa
```
User: "COLLEX-64"
SM: Kiểm tra status → BRD done, FSD done, TDD chưa có
SM: "📋 COLLEX-64 — BRD ✅, FSD ✅, TDD chưa có. Tiếp tục Phase 3: Design?"
User: "OK"
SM: Gọi SA → ...
```

### Pattern 3: Chỉ một phase
```
User: "Tạo TDD cho COLLEX-64"
SM: Kiểm tra prerequisites → FSD phải có trước
SM: FSD exists → gọi SA trực tiếp
```

### Pattern 4: Redo một phase
```
User: "Tạo lại FSD cho COLLEX-64"
SM: Gọi BA tạo lại FSD → sau đó tự trigger SA review (vì TDD đã có, cần update)
```

## 6. Quality Gates

| Gate | Condition | Action nếu fail |
|------|-----------|-----------------|
| FSD → TDD | FSD phải tồn tại | SM gọi BA tạo FSD trước |
| TDD → Code | TDD phải tồn tại, không còn Critical discrepancy | SM chạy feedback loop |
| Code → Test | Code phải compile, unit tests pass | SM báo DEV fix |
| Test → Deploy | All test cases pass | SM báo QA/DEV fix |

## 7. Feedback Loops

### Loop 1: BA ↔ SA (Document consistency)
- **Trigger**: SA tạo DISCREPANCY.md
- **Process**: BA fix FSD → SA review → lặp
- **Exit**: Không còn discrepancy HOẶC max 5 vòng
- **SM role**: Tự động chạy loop, báo user progress

### Loop 2: DEV ↔ QA (Code quality) — Future
- **Trigger**: QA phát hiện bugs
- **Process**: DEV fix → QA retest → lặp
- **Exit**: All tests pass
- **SM role**: Tự động chạy loop, báo user progress

## 8. Agents được SM điều phối

| Agent | Khi nào SM gọi | Input | Output |
|-------|----------------|-------|--------|
| ba-agent | Phase 1, 2, feedback loop | Jira ticket, code intelligence, discrepancy report | BRD.md, FSD.md |
| sa-agent | Phase 3, feedback loop | BRD, FSD, code intelligence | TDD.md, DISCREPANCY.md |
| qa-agent | Phase 4, 6 | BRD, FSD, TDD | STP.md, STC.md, test results |
| dev-agent | Phase 5 | TDD, code intelligence | Source code |
| devops-agent | Phase 7 | TDD, code intelligence | DPG.md, RLN.md |

## 9. Điều SM KHÔNG làm

- SM **không tự viết documents** — chỉ gọi agents
- SM **không tự viết code** — chỉ gọi dev-agent
- SM **không quyết định thay user** — luôn hỏi trước khi chuyển phase lớn
- SM **không skip quality gates** — nếu prerequisite thiếu, phải tạo trước

## 10. Implementation Notes

- SM agent file: `.kiro/agents/scrum-master-agent.md`
- Status file: `documents/{TICKET}/STATUS.json`
- SM dùng `invokeSubAgent` để gọi các agents khác
- SM dùng `readFile` để check status và documents
- SM dùng `fsWrite` để update STATUS.json
- `doc-orchestrator-agent.md` sẽ bị xóa (replaced by SM)
