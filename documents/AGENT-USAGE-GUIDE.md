    # Hướng Dẫn Sử Dụng Hệ Thống Multi-Agent

> **Cập nhật:** 2026-04-30
> **Dự án:** Skip Tracing Workflow

---

## Tổng Quan

Hệ thống multi-agent được thiết kế theo quy trình SDLC (Software Development Life Cycle), mỗi agent đảm nhận một vai trò chuyên biệt. Tất cả agent giao tiếp bằng **tiếng Việt** với user, nhưng tạo tài liệu bằng **tiếng Anh**.

### Sơ Đồ Pipeline

```
Jira Ticket
    │
    ▼
┌─────────────────┐
│  Scrum Master    │ ◄── Entry point duy nhất (điều phối tất cả)
│  @scrum-master   │
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
Phase 1    Phase 2    Phase 3     Phase 3.5    Phase 4     Phase 5      Phase 6-7
  BA ──►    BA ──►    SA ──►     BA ↔ SA ──►   QA ──►     DEV ──►    DevOps
 BRD       FSD       TDD      Feedback Loop  STP/STC     Code       DPG/RLN
```

---

## Danh Sách Agent

| # | Agent | Vai Trò | Cách Gọi | Output |
|---|-------|---------|-----------|--------|
| 1 | **Scrum Master** | Điều phối toàn bộ pipeline | `@scrum-master-agent` | STATUS.json |
| 2 | **BA Agent** | Phân tích nghiệp vụ | `@ba-agent` | BRD.md, FSD.md |
| 3 | **SA Agent** | Thiết kế kỹ thuật | `@sa-agent` | TDD.md |
| 4 | **QA Agent** | Lập kế hoạch test | `@qa-agent` | STP.md, STC.md, CSV |
| 5 | **DEV Agent** | Implement code | `@dev-agent` | Source code |
| 6 | **DevOps Agent** | Deployment & Release | `@devops-agent` | DPG.md, RLN.md |
| 7 | **Technical Doc Expert** | Tạo FSD chuyên sâu | `@technical-document-expert` | FSD (11 sections) |

---

## 1. Scrum Master Agent

> **Vai trò:** Điều phối viên — entry point duy nhất cho toàn bộ pipeline. Không tự viết tài liệu, chỉ gọi các agent khác.

### Khi nào dùng
- Khi muốn chạy **toàn bộ pipeline** từ đầu đến cuối
- Khi muốn xem **trạng thái** hiện tại của ticket
- Khi muốn **tiếp tục** từ phase đang dở

### Cách sử dụng

```
# Chạy pipeline đầy đủ (SM tự detect phase hiện tại)
@scrum-master-agent COLLEX-64

# Xem trạng thái
@scrum-master-agent COLLEX-64 status

# Tạo document cụ thể
@scrum-master-agent COLLEX-64 tạo BRD
@scrum-master-agent COLLEX-64 tạo FSD
@scrum-master-agent COLLEX-64 tạo TDD

# Tạo lại document (force redo)
@scrum-master-agent COLLEX-64 tạo lại FSD

# Tạo tài liệu đầy đủ (BRD → FSD → TDD)
@scrum-master-agent COLLEX-64 tạo tài liệu đầy đủ

# Dùng template tùy chỉnh
@scrum-master-agent COLLEX-64 tạo BRD template:documents/templates/BRD-CUSTOM.md
```

### Đặc điểm
- Tự **resume** từ phase đang dở (đọc STATUS.json)
- Tự chạy **feedback loop** BA ↔ SA (tối đa 5 vòng)
- **Hỏi user** trước khi chuyển phase lớn
- Kiểm tra **quality gate** — không skip prerequisite

---

## 2. BA Agent (Business Analyst)

> **Vai trò:** Thu thập requirements từ Jira, tạo BRD và FSD.

### Khi nào dùng
- Khi cần tạo **BRD** (Business Requirements Document)
- Khi cần tạo **FSD** (Functional Specification Document)
- Khi cần **cập nhật FSD** sau discrepancy report từ SA

### Cách sử dụng

```
# Tạo BRD (mặc định)
@ba-agent COLLEX-64

# Tạo FSD
@ba-agent Tạo FSD cho COLLEX-64

# Tạo cả BRD + FSD
@ba-agent Tạo BRD và FSD cho COLLEX-64

# Dùng template tùy chỉnh
@ba-agent COLLEX-64 template:documents/templates/MY-TEMPLATE.md
```

### Quy trình tự động
1. Đọc template → Fetch Jira ticket + tất cả linked tickets (đệ quy)
2. Lưu vào Knowledge Base
3. Phân tích & tổng hợp requirements
4. Tạo BRD/FSD với đầy đủ sections
5. Tạo diagrams (Use Case, Business Flow, Sequence, Wireframe)
6. Export PNG từ draw.io
7. Export DOCX

### Output
| File | Đường dẫn |
|------|-----------|
| BRD (Markdown) | `documents/{TICKET}/BRD.md` |
| BRD (Word) | `documents/{TICKET}/BRD-{TICKET}.docx` |
| FSD (Markdown) | `documents/{TICKET}/FSD.md` |
| FSD (Word) | `documents/{TICKET}/FSD-{TICKET}.docx` |
| Diagrams | `documents/{TICKET}/diagrams/*.drawio` + `*.png` |

---

## 3. SA Agent (Solution Architect)

> **Vai trò:** Thiết kế kỹ thuật — tạo TDD từ BRD + FSD + phân tích code thực tế.

### Khi nào dùng
- Khi cần tạo **TDD** (Technical Design Document)
- Khi cần **review FSD** và tạo discrepancy report

### Cách sử dụng

```
# Tạo TDD
@sa-agent COLLEX-64

# Hoặc
@sa-agent Tạo TDD cho COLLEX-64
```

### Điểm đặc biệt
- **Bắt buộc** đọc Code Intelligence data trước khi thiết kế
- **Bắt buộc** query database thực tế để verify data model
- **Bắt buộc** phân tích source code để match patterns hiện có
- Tự động tạo **Discrepancy Report** nếu FSD không khớp codebase

### Prerequisite
- `documents/{TICKET}/BRD.md` — REQUIRED
- `documents/{TICKET}/FSD.md` — REQUIRED

### Output
| File | Đường dẫn |
|------|-----------|
| TDD (Markdown) | `documents/{TICKET}/TDD.md` |
| TDD (Word) | `documents/{TICKET}/TDD-{TICKET}.docx` |
| Discrepancy Report | `documents/{TICKET}/DISCREPANCY.md` (nếu có) |
| Diagrams | `documents/{TICKET}/diagrams/architecture.drawio`, `component.drawio`, `db-schema.drawio`, ... |

---

## 4. QA Agent (Quality Assurance)

> **Vai trò:** Lập kế hoạch test — tạo Test Plan và Test Cases từ BRD + FSD + TDD.

### Khi nào dùng
- Khi cần tạo **STP** (Software Test Plan)
- Khi cần tạo **STC** (Software Test Cases)
- Khi cần **Test Execution Report** template (CSV cho Excel)

### Cách sử dụng

```
# Tạo cả STP + STC (mặc định)
@qa-agent COLLEX-64

# Chỉ tạo Test Plan
@qa-agent Tạo Test Plan cho COLLEX-64

# Chỉ tạo Test Cases
@qa-agent Tạo Test Cases cho COLLEX-64
```

### Prerequisite
- `documents/{TICKET}/BRD.md` — REQUIRED
- `documents/{TICKET}/FSD.md` — REQUIRED
- `documents/{TICKET}/TDD.md` — OPTIONAL (bổ sung API/DB testing)

### Output
| File | Đường dẫn |
|------|-----------|
| Test Plan (Markdown) | `documents/{TICKET}/STP.md` |
| Test Plan (Word) | `documents/{TICKET}/STP-{TICKET}.docx` |
| Test Cases (Markdown) | `documents/{TICKET}/STC.md` |
| Test Cases (Word) | `documents/{TICKET}/STC-{TICKET}.docx` |
| Test Report (CSV) | `documents/{TICKET}/TEST-REPORT-{TICKET}.csv` |
| Evidence folder | `documents/{TICKET}/evidence/` |

### Quy ước đánh số Test Case
| Range | Loại |
|-------|------|
| TC-001 → TC-099 | Functional — Happy Path |
| TC-100 → TC-199 | Functional — Alternative Flows |
| TC-200 → TC-299 | Functional — Exception/Error Flows |
| TC-300 → TC-399 | Business Rule Validation |
| TC-400 → TC-499 | Boundary & Negative Testing |
| TC-500 → TC-599 | UI/UX Testing |
| TC-600 → TC-699 | Non-Functional (Performance, Security) |
| TC-700 → TC-799 | Integration Testing |
| TC-800 → TC-899 | Regression Testing |

---

## 5. DEV Agent (Developer)

> **Vai trò:** Implement code từ TDD — tạo API, database migrations, services, unit tests.

### Khi nào dùng
- Khi cần **implement toàn bộ** feature theo TDD
- Khi cần implement **một phần** cụ thể (API, DB, service)

### Cách sử dụng

```
# Implement toàn bộ
@dev-agent COLLEX-64

# Implement phần cụ thể
@dev-agent Implement API cho COLLEX-64
@dev-agent Tạo database migration cho COLLEX-64
@dev-agent Implement service cho COLLEX-64
```

### Prerequisite
- `documents/{TICKET}/TDD.md` — REQUIRED
- `documents/{TICKET}/FSD.md` — REQUIRED
- `documents/{TICKET}/BRD.md` — OPTIONAL

### Thứ tự implement
1. Database migrations → Entity/Model classes
2. Repository/DAO interfaces
3. Service interfaces + implementations
4. DTOs (Request/Response)
5. Controller/Handler classes
6. Unit tests (target ≥ 80% coverage)

### Đặc điểm
- **Bắt buộc** đọc Code Intelligence data trước khi code
- Match **patterns hiện có** — không introduce patterns mới
- Dùng **libraries hiện có** — không thêm dependency mới
- Viết **semantic annotations** sau khi implement xong

---

## 6. DevOps Agent

> **Vai trò:** Tạo Deployment Guide, Release Notes, CI/CD config.

### Khi nào dùng
- Khi cần tạo **DPG** (Deployment Guide)
- Khi cần tạo **RLN** (Release Notes)
- Khi cần cập nhật **CI/CD pipeline** hoặc **Docker config**

### Cách sử dụng

```
# Tạo cả DPG + RLN (mặc định)
@devops-agent COLLEX-64

# Chỉ tạo Deployment Guide
@devops-agent Tạo deployment guide cho COLLEX-64

# Chỉ tạo Release Notes
@devops-agent Tạo release notes cho COLLEX-64
```

### Prerequisite
- `documents/{TICKET}/TDD.md` — REQUIRED
- `documents/{TICKET}/FSD.md` — OPTIONAL
- `documents/{TICKET}/BRD.md` — OPTIONAL

### Output
| File | Đường dẫn |
|------|-----------|
| Deployment Guide (Markdown) | `documents/{TICKET}/DPG.md` |
| Deployment Guide (Word) | `documents/{TICKET}/DPG-{TICKET}.docx` |
| Release Notes (Markdown) | `documents/{TICKET}/RLN.md` |
| Release Notes (Word) | `documents/{TICKET}/RLN-{TICKET}.docx` |

---

## 7. Technical Document Expert

> **Vai trò:** Tạo FSD chuyên sâu theo template FECredit (11 sections) — dùng khi cần FSD chi tiết hơn BA agent.

### Khi nào dùng
- Khi cần FSD **cực kỳ chi tiết** với đầy đủ 11 sections
- Khi cần FSD cho **enterprise-grade** systems
- Khi BA agent tạo FSD chưa đủ chi tiết cho developer implement

### Cách sử dụng

```
@technical-document-expert Tạo FSD cho COLLEX-64 từ BRD đã có
```

### 11 Sections của FSD
1. Introduction
2. System/Solution Overview
3. Functional Specifications (Use Cases, Business Rules, Field Specs)
4. System Configurations
5. Non-Functional Requirements
6. Reporting Requirements
7. Integration Requirements (API Contracts)
8. Data Migration/Conversion
9. References
10. Open Issues
11. Appendix

---

## Cấu Trúc Thư Mục Output

```
documents/
├── templates/                    ← Templates cho các loại tài liệu
│   ├── BRD-TEMPLATE.md
│   ├── FSD-TEMPLATE.md
│   ├── TDD-TEMPLATE.md
│   ├── STP-TEMPLATE.md
│   ├── STC-TEMPLATE.md
│   ├── DPG-TEMPLATE.md
│   └── RLN-TEMPLATE.md
│
└── {TICKET-KEY}/                 ← Thư mục cho mỗi ticket
    ├── STATUS.json               ← Trạng thái pipeline (SM quản lý)
    ├── BRD.md                    ← Business Requirements Document
    ├── BRD-{TICKET}.docx         ← BRD dạng Word
    ├── FSD.md                    ← Functional Specification Document
    ├── FSD-{TICKET}.docx         ← FSD dạng Word
    ├── TDD.md                    ← Technical Design Document
    ├── TDD-{TICKET}.docx         ← TDD dạng Word
    ├── DISCREPANCY.md            ← Báo cáo sai lệch FSD vs Code (SA tạo)
    ├── STP.md                    ← Software Test Plan
    ├── STP-{TICKET}.docx
    ├── STC.md                    ← Software Test Cases
    ├── STC-{TICKET}.docx
    ├── TEST-REPORT-{TICKET}.csv  ← Template báo cáo test (mở bằng Excel)
    ├── DPG.md                    ← Deployment Guide
    ├── DPG-{TICKET}.docx
    ├── RLN.md                    ← Release Notes
    ├── RLN-{TICKET}.docx
    ├── diagrams/                 ← Tất cả diagrams
    │   ├── use-case.drawio
    │   ├── use-case.png
    │   ├── business-flow.drawio
    │   ├── business-flow.png
    │   ├── architecture.drawio
    │   ├── architecture.png
    │   └── ...
    └── evidence/                 ← Screenshots khi test (QA điền)
```

---

## Quy Trình Đề Xuất

### Cách 1: Dùng Scrum Master (Khuyến nghị)

Chỉ cần gọi Scrum Master với ticket key — SM tự điều phối tất cả:

```
@scrum-master-agent COLLEX-64
```

SM sẽ:
1. Kiểm tra trạng thái hiện tại
2. Đề xuất bước tiếp theo
3. Hỏi bạn xác nhận trước khi chạy
4. Gọi agent phù hợp
5. Báo cáo kết quả

### Cách 2: Gọi Từng Agent (Khi cần kiểm soát chi tiết)

```
# Bước 1: Tạo BRD
@ba-agent COLLEX-64

# Bước 2: Tạo FSD
@ba-agent Tạo FSD cho COLLEX-64

# Bước 3: Tạo TDD (SA tự đọc code intelligence + query DB)
@sa-agent COLLEX-64

# Bước 4: Tạo Test Plan + Test Cases
@qa-agent COLLEX-64

# Bước 5: Implement code
@dev-agent COLLEX-64

# Bước 6: Tạo Deployment Guide + Release Notes
@devops-agent COLLEX-64
```

---

## Code Intelligence System

Tất cả agent (SA, DEV, QA) đều sử dụng **Code Intelligence System** để hiểu codebase trước khi làm việc.

### Files quan trọng
| File | Mô tả |
|------|--------|
| `.analysis/code-intelligence/project-structure.md` | Tổng quan modules, languages, frameworks |
| `.analysis/code-intelligence/modules/{name}.md` | Chi tiết từng module: classes, APIs, patterns |
| `.analysis/code-intelligence/database-schema.md` | Schema database hiện tại |
| `.analysis/code-intelligence/index-metadata.json` | Metadata indexing (kiểm tra freshness) |

### Cách cập nhật Code Intelligence
- **Tự động:** Hooks tự chạy khi bạn save/create/delete file
- **Thủ công:** Click hook "Code Index — Full Re-Index" trong Agent Hooks panel

### Chi tiết
Xem `.kiro/steering/code-intelligence.md` để biết cách agent sử dụng code intelligence.

---

## Mẹo Sử Dụng

1. **Luôn bắt đầu từ Scrum Master** nếu chưa quen — SM sẽ hướng dẫn từng bước
2. **Kiểm tra STATUS.json** để biết ticket đang ở phase nào
3. **Dùng "tạo lại"** khi muốn redo một document (SM sẽ cảnh báo nếu ảnh hưởng downstream)
4. **SA agent tự tạo Discrepancy Report** — nếu có, SM sẽ tự chạy feedback loop BA ↔ SA
5. **Test Report CSV** mở bằng Excel — QA điền kết quả test vào các cột Status, Actual Result, Evidence
6. **Diagrams** luôn có 2 file: `.drawio` (editable) + `.png` (embed trong document)
7. **Code Intelligence** tự cập nhật khi bạn save file — không cần chạy thủ công
