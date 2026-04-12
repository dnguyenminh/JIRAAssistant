---
description: Workflow xay dung BRD, E2E Tests va User Guide cho A-Z Investor Demo. Giai doan pre-DEV — tap trung vao tai lieu va kich ban, chua can implementation.
---

# Investor Demo Workflow: BRD, E2E Tests & User Guide

## Muc dich

Workflow nay huong dan xay dung 3 san pham chinh truoc khi chuyen sang giai doan DEV:
1. **BRD (Business Requirements Document)** — Dac ta day du tung chuc nang cua he thong
2. **E2E Test Scenarios** — Kich ban kiem thu end-to-end cho toan bo demo flow
3. **User Guide (Tieng Viet)** — Huong dan su dung tung buoc cho nguoi dung cuoi

Tat ca 3 san pham phai dong bo voi nhau va phu kin 7 man hinh cua he thong.

## Pham vi: 7 Man hinh Demo

| # | Man hinh | Path | Chuc nang chinh |
|---|----------|------|----------------|
| 1 | Onboarding | `frontend/onboarding/` | Xac thuc Jira (Domain + API Token), test ket noi, chon du an |
| 2 | Dashboard | `frontend/dashboard/` | Tong quan AI Health, Knowledge Nodes, Neural Velocity, Neural Console |
| 3 | Knowledge Graph | `frontend/knowledge_graph/` | Do thi mang luoi ticket, node hexagonal, semantic/explicit edges, detail panel |
| 4 | Project Analysis | `frontend/analysis/` | Sprint velocity, resolution rate, cycle time, AI bottleneck radar |
| 5 | Ticket Intelligence | `frontend/ticket_analysis/` | Phan tich ticket 3 tab (Context, Evolution, Complexity), KB-First, re-analyze |
| 6 | Integrations | `frontend/integrations/` | Cau hinh multi-provider (Jira, Ollama, Gemini, LM Studio, CLI), test connection |
| 7 | User Management | `frontend/users/` | RBAC (Admin, Architect, Reader), permission toggles, audit log |

## Vai tro tham gia (Pre-DEV)

| Role | Trach nhiem |
|------|------------|
| **PM** | Dieu phoi, review, dam bao dong bo giua 3 san pham |
| **BA** | Viet va cap nhat BRD, xac dinh acceptance criteria |
| **QA** | Viet E2E test scenarios (Gherkin), dinh nghia test data |
| **Technical Writer** | Viet User Guide tieng Viet, nhung mockup, viet FAQ |
| **Frontend Designer** | Cung cap mockup/screenshot cho User Guide va test scenarios |

---

## PHAN 1: BRD (Business Requirements Document)

### Files can cap nhat
- `docs/BA/BRD.md` — Yeu cau nghiep vu cap cao
- `docs/BA/SRS.md` — Dac ta yeu cau phan mem chi tiet

### Cau truc BRD theo tung man hinh

#### MH1: Onboarding — Xac thuc & Ket noi

**Business Requirement:**
- BR-AUTH-01: He thong cho phep nguoi dung nhap Jira Domain va API Token de xac thuc
- BR-AUTH-02: He thong xac minh thong tin xac thuc bang cach goi Jira REST API
- BR-AUTH-03: He thong hien thi tien trinh ket noi real-time (progress bar + status ticker)
- BR-AUTH-04: Sau khi xac thuc thanh cong, he thong hien thi danh sach du an de nguoi dung chon

**Acceptance Criteria:**
- AC-AUTH-01: WHEN nguoi dung nhap Jira Domain va API Token roi nhan "ESTABLISH CONNECTION", THEN he thong hien thi progress bar voi 3 giai doan: "HANDSHAKING WITH JIRA..." (0-30%), "VALIDATING API TOKENS..." (30-70%), "FETCHING PROJECT METADATA..." (70-100%)
- AC-AUTH-02: WHEN xac thuc thanh cong, THEN he thong hien thi grid 2 cot chua danh sach du an (Project Key + Project Name)
- AC-AUTH-03: WHEN nguoi dung chon mot du an, THEN he thong chuyen huong den trang Dashboard
- AC-AUTH-04: IF thong tin xac thuc khong hop le, THEN he thong hien thi thong bao loi va cho phep nhap lai

**Personas:** Developer, Scrum Master, Product Owner (tat ca can xac thuc)

---

#### MH2: Dashboard — Tong quan Du an

**Business Requirement:**
- BR-DASH-01: Dashboard hien thi 3 chi so chinh: AI Health (%), Knowledge Nodes (so luong), Neural Velocity (diem)
- BR-DASH-02: Dashboard hien thi preview do thi mang luoi voi nut "VIEW GRAPH"
- BR-DASH-03: Dashboard hien thi bieu do AI Estimation Drift voi nut "ANALYSIS DRIFT"
- BR-DASH-04: Neural Console hien thi nhat ky hoat dong real-time (AI sync, KB write, heartbeat)
- BR-DASH-05: User dropdown cho phep truy cap Account Settings, Security, va Sign Out

**Acceptance Criteria:**
- AC-DASH-01: WHEN trang Dashboard load, THEN 3 stat cards hien thi voi so lieu: AI Health (94.2%, +2.1%), Knowledge Nodes (1,024/1.5k), Neural Velocity (42.8 STABLE)
- AC-DASH-02: WHEN nguoi dung nhan "VIEW GRAPH", THEN he thong chuyen den trang Knowledge Graph
- AC-DASH-03: WHEN nguoi dung nhan "ANALYSIS DRIFT", THEN he thong chuyen den trang Project Analysis
- AC-DASH-04: Neural Console hien thi toi thieu 3 dong log voi timestamp, tag (AI_SYNC, KB_WRITE, HEARTBEAT), va noi dung
- AC-DASH-05: WHEN nguoi dung click avatar, THEN dropdown hien thi voi animation pop-in. Click ngoai dropdown thi dong lai

**Personas:** Scrum Master (xem tong quan), Product Owner (theo doi health)

---

#### MH3: Knowledge Graph — Mang luoi Quan he

**Business Requirement:**
- BR-GRAPH-01: He thong hien thi do thi mang luoi ticket dang hexagonal nodes
- BR-GRAPH-02: Moi node the hien mot Jira ticket voi mau sac phan biet theo loai (Feature, Dependency, UI Module)
- BR-GRAPH-03: Duong noi phan biet: net lien (explicit Jira link) vs net dut (semantic AI-discovered)
- BR-GRAPH-04: Panel chi tiet ben phai hien thi thong tin ticket khi click node
- BR-GRAPH-05: Ho tro tim kiem ticket trong do thi

**Acceptance Criteria:**
- AC-GRAPH-01: Do thi hien thi toi thieu 3 node voi mau: Feature (cyan), Dependency (blue), UI Module (violet)
- AC-GRAPH-02: WHEN nguoi dung hover node, THEN node scale 1.1 voi stroke trang
- AC-GRAPH-03: WHEN nguoi dung click node, THEN panel ben phai hien thi: Ticket Key, Summary, Description, va nut "OPEN IN JIRA"
- AC-GRAPH-04: Duong noi semantic (AI) hien thi net dut, duong noi explicit hien thi net lien
- AC-GRAPH-05: Sidebar hien thi "GRAPH SYNC: 75%" voi progress bar tuong ung

**Personas:** Product Owner (nhan dien phu thuoc), Developer (hieu quan he ticket)

---

#### MH4: Project Analysis — Phan tich Du an

**Business Requirement:**
- BR-ANALYSIS-01: Hien thi 4 chi so: Total Tickets, Resolution Rate, Cycle Time, AI Velocity
- BR-ANALYSIS-02: Bieu do Velocity Trend hien thi story points theo sprint (bar chart)
- BR-ANALYSIS-03: AI Bottleneck Radar phat hien va canh bao cac van de (tickets bi ket, co hoi toi uu)
- BR-ANALYSIS-04: Nut "DIVE INTO REPORTS" cho phep xem bao cao chi tiet

**Acceptance Criteria:**
- AC-ANALYSIS-01: 4 stat cards hien thi: Total Tickets (142), Resolution Rate (88%), Cycle Time (12.4 days), AI Velocity (42)
- AC-ANALYSIS-02: Velocity chart hien thi 7 bars voi heights khac nhau, bars le co gradient primary
- AC-ANALYSIS-03: Bottleneck Radar hien thi toi thieu 2 alerts: 1 warning (blockers) va 1 positive (optimization)
- AC-ANALYSIS-04: WHEN hover bar trong velocity chart, THEN bar scale 1.05 voi opacity tang

**Personas:** Scrum Master (velocity tracking), Product Owner (bottleneck detection)

---

#### MH5: Ticket Intelligence — Phan tich Ticket AI

**Business Requirement:**
- BR-TICKET-01: Nguoi dung nhap Ticket ID va nhan "ANALYZE" de kich hoat phan tich
- BR-TICKET-02: He thong ap dung chien luoc KB-First: kiem tra Knowledge Base truoc, chi goi AI khi chua co ket qua
- BR-TICKET-03: Ket qua hien thi tren 3 tab: Requirement Context, Requirement Evolution, Predictive Complexity
- BR-TICKET-04: Tab Context hien thi tom tat yeu cau tong hop tu Summary, Description, Sub-tickets
- BR-TICKET-05: Tab Evolution hien thi lich su thay doi yeu cau theo timeline
- BR-TICKET-06: Tab Complexity hien thi Scrum Point estimation voi confidence score va KB references
- BR-TICKET-07: Nguoi dung co the nhan "RE-ANALYZE" de buoc AI phan tich lai va ghi de KB

**Acceptance Criteria:**
- AC-TICKET-01: WHEN nguoi dung nhap "NET-458" va nhan "ANALYZE", THEN progress bar hien thi 3 giai doan: "Consolidating Ticket Metadata..." (0-40%), "AI RE-ANALYZING SCOPE..." (40-85%), "SYNCING TO KNOWLEDGE BASE..." (85-100%)
- AC-TICKET-02: Tab Context hien thi summary text va danh sach modules anh huong voi colored bullets (primary, accent, secondary)
- AC-TICKET-03: Tab Evolution hien thi neural console voi timeline: [Origin] REQ_START, [v1.2] SCOPE_PIVOT, [Current] NET-458
- AC-TICKET-04: Tab Complexity hien thi Scrum Point "8" (font 12rem, gradient text), subtitle "High Complexity", va 2 KB reference badges
- AC-TICKET-05: WHEN chuyen tab, THEN content panel co fadeIn animation 0.5s
- AC-TICKET-06: WHEN nhan "RE-ANALYZE", THEN progress bar chay lai va ket qua cap nhat
- AC-TICKET-07: Scrum Points chi nam trong thang: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40

**Personas:** Scrum Master (estimation), Developer (hieu yeu cau), Product Owner (theo doi evolution)

---

#### MH6: Integrations — Cau hinh AI Providers

**Business Requirement:**
- BR-INTEG-01: Hien thi danh sach 5 providers: Jira Cloud, Ollama, Gemini API, LM Studio, Gemini CLI
- BR-INTEG-02: Moi provider hien thi: logo, trang thai (Active/Standby/Offline), cac truong cau hinh, va nut hanh dong
- BR-INTEG-03: Nut "TEST LINK" kiem tra ket noi voi progress bar real-time
- BR-INTEG-04: Trang thai ket noi hien thi bang status dot voi tooltip chi tiet

**Acceptance Criteria:**
- AC-INTEG-01: 5 provider cards hien thi trong grid (min 380px columns)
- AC-INTEG-02: Jira card co border primary, status Active (green dot), fields: Domain + Auth Method
- AC-INTEG-03: Ollama card hien thi: Endpoint URL (localhost:11434), Active Model (llama3-8b-instruct), status Active
- AC-INTEG-04: Gemini card hien thi: API Key (masked), Model Tier dropdown (3 options), status Standby
- AC-INTEG-05: LM Studio card hien thi: Port (1234), status Offline (red dot, tooltip "Connection Timed Out")
- AC-INTEG-06: WHEN nhan "TEST LINK", THEN button text = "PROBING...", progress bar animate 0-100%, sau do reset
- AC-INTEG-07: Status dots: Active (green + glow), Standby (blue + glow), Offline (red + glow)

**Personas:** Administrator (cau hinh providers), Developer (test connections)

---

#### MH7: User Management — Phan quyen RBAC

**Business Requirement:**
- BR-USER-01: Hien thi danh sach nguoi dung voi avatar, ten, email, va role hien tai
- BR-USER-02: Administrator co the thay doi role cua nguoi dung khac qua dropdown
- BR-USER-03: Panel permissions cho phep bat/tat quyen cu the (Trigger AI Scan, KB Write, Update Integrations, Export Data)
- BR-USER-04: Moi thay doi quyen duoc ghi vao audit log hien thi trong Neural Console

**Acceptance Criteria:**
- AC-USER-01: Danh sach hien thi 3 users: Sarah Jenkins (Admin), Alex Rivera (Architect), Marcus Chen (Reader)
- AC-USER-02: Moi user card co hover effect translateX(8px)
- AC-USER-03: Role dropdown hien thi options phu hop voi role hien tai
- AC-USER-04: Permission toggles: AI Scan (ON), KB Write (ON), Update Integrations (OFF), Export Data (ON)
- AC-USER-05: WHEN toggle permission, THEN sidebar ticker hien thi "IAM SYNC: UPDATING...", progress bar animate, sau do "SYNC COMPLETE"
- AC-USER-06: Audit log hien thi toi thieu 2 entries voi timestamp, tag (IAM_SYNC, USER_LOGIN), va noi dung

**Personas:** Administrator (quan ly users va permissions)

---


## PHAN 2: E2E TEST SCENARIOS (Serenity BDD / Gherkin)

### Files can cap nhat
- `docs/QA/TestScenarios.md` — Tong hop kich ban kiem thu
- `e2e-tests/src/test/resources/features/` — Gherkin feature files

### Cau truc Feature Files

#### Feature 1: Onboarding & Authentication
File: `e2e-tests/.../features/004-Onboarding.feature`

```gherkin
Feature: Onboarding & Authentication
  As a user
  I want to authenticate with my Jira credentials
  So that I can access the AI-powered analysis tools

  Scenario: Successful authentication flow
    Given the user is on the Onboarding page
    When the user enters Jira Domain "https://company.atlassian.net"
    And the user enters API Token "valid-token"
    And the user clicks "ESTABLISH CONNECTION"
    Then the progress bar should display "HANDSHAKING WITH JIRA..."
    And the progress bar should advance to "VALIDATING API TOKENS..."
    And the progress bar should advance to "FETCHING PROJECT METADATA..."
    And the project selection grid should be visible
    And the grid should display at least 2 projects

  Scenario: Failed authentication
    Given the user is on the Onboarding page
    When the user enters invalid credentials
    And the user clicks "ESTABLISH CONNECTION"
    Then an error message should be displayed
    And the user should remain on the Authentication step

  Scenario: Project selection navigates to Dashboard
    Given the user has completed authentication
    And the project selection grid is visible
    When the user clicks on project "JRA"
    Then the user should be redirected to the Dashboard page
```

#### Feature 2: Dashboard Overview
File: `e2e-tests/.../features/005-Dashboard.feature`

```gherkin
Feature: Dashboard Overview
  As a Scrum Master
  I want to see project health metrics at a glance
  So that I can monitor AI synchronization status

  Scenario: Dashboard displays hero stats
    Given the user is on the Dashboard page
    Then the "PROJECT AI HEALTH" card should display "94.2%"
    And the "ACTIVE KNOWLEDGE NODES" card should display "1,024"
    And the "NEURAL VELOCITY" card should display "42.8"

  Scenario: Neural Console shows activity logs
    Given the user is on the Dashboard page
    Then the Neural Console should display at least 3 log entries
    And each log entry should have a timestamp, tag, and message

  Scenario: Navigate to Knowledge Graph
    Given the user is on the Dashboard page
    When the user clicks "VIEW GRAPH"
    Then the user should be redirected to the Knowledge Graph page

  Scenario: Navigate to Analysis
    Given the user is on the Dashboard page
    When the user clicks "ANALYSIS DRIFT"
    Then the user should be redirected to the Project Analysis page

  Scenario: User dropdown interaction
    Given the user is on the Dashboard page
    When the user clicks on the avatar
    Then the user dropdown should appear with animation
    And the dropdown should display "Account Settings", "Security & Permissions", "Sign Out"
    When the user clicks outside the dropdown
    Then the dropdown should close
```

#### Feature 3: Knowledge Graph
File: `e2e-tests/.../features/006-KnowledgeGraph.feature`

```gherkin
Feature: Knowledge Graph Visualization
  As a Product Owner
  I want to see ticket relationships in a visual graph
  So that I can identify feature dependencies

  Scenario: Graph displays hexagonal nodes
    Given the user is on the Knowledge Graph page
    Then the graph should display at least 3 hexagonal nodes
    And nodes should have distinct colors: cyan, blue, violet

  Scenario: Node hover effect
    Given the user is on the Knowledge Graph page
    When the user hovers over a node
    Then the node should scale to 1.1 with white stroke

  Scenario: Node click shows detail panel
    Given the user is on the Knowledge Graph page
    When the user clicks on node "NET-458"
    Then the detail panel should display ticket key "NET-458"
    And the detail panel should display a summary
    And the "OPEN IN JIRA" button should be visible

  Scenario: Edge types are visually distinct
    Given the user is on the Knowledge Graph page
    Then semantic edges should be displayed as dashed lines
    And explicit edges should be displayed as solid lines
```

#### Feature 4: Project Analysis
File: `e2e-tests/.../features/007-ProjectAnalysis.feature`

```gherkin
Feature: Project Analysis
  As a Scrum Master
  I want to see sprint velocity and bottleneck insights
  So that I can optimize team performance

  Scenario: Hero metrics display
    Given the user is on the Project Analysis page
    Then the "Total Tickets" card should display "142"
    And the "Resolution Rate" card should display "88%"
    And the "Cycle Time" card should display "12.4"
    And the "AI Velocity" card should display "42"

  Scenario: Velocity chart interaction
    Given the user is on the Project Analysis page
    Then the velocity chart should display 7 bars
    When the user hovers over a bar
    Then the bar should scale vertically

  Scenario: Bottleneck alerts
    Given the user is on the Project Analysis page
    Then the AI Bottleneck Radar should display at least 2 alerts
    And one alert should be a warning about blockers
    And one alert should be a positive optimization suggestion
```

#### Feature 5: Ticket Intelligence
File: `e2e-tests/.../features/008-TicketIntelligence.feature`

```gherkin
Feature: Ticket Intelligence Analysis
  As a Scrum Master
  I want to analyze tickets with AI
  So that I can get requirement summaries and point estimations

  Scenario: Analyze a ticket
    Given the user is on the Ticket Intelligence page
    When the user enters ticket ID "NET-458"
    And the user clicks "ANALYZE DATA"
    Then the progress bar should be visible
    And the progress bar should show analysis stages
    And the Requirement Context tab should display a summary

  Scenario: Tab switching
    Given the user has analyzed ticket "NET-458"
    When the user clicks "Requirement Evolution" tab
    Then the Evolution panel should display a timeline
    When the user clicks "Predictive Complexity" tab
    Then the Complexity panel should display Scrum Point "8"
    And KB reference badges should be visible

  Scenario: Requirement Context tab content
    Given the user has analyzed ticket "NET-458"
    Then the Context tab should display a unified summary
    And the summary should list affected modules with colored bullets

  Scenario: Requirement Evolution tab content
    Given the user has analyzed ticket "NET-458"
    And the user is on the "Requirement Evolution" tab
    Then the neural console should display timeline entries
    And entries should include: Origin, version changes, and Current state

  Scenario: Predictive Complexity tab content
    Given the user has analyzed ticket "NET-458"
    And the user is on the "Predictive Complexity" tab
    Then the Scrum Point should be displayed as "8"
    And the subtitle should indicate complexity level
    And at least 2 KB reference badges should be visible

  Scenario: Re-analyze ticket
    Given the user has analyzed ticket "NET-458"
    When the user clicks "RE-ANALYZE"
    Then the progress bar should restart
    And the results should be updated
```

#### Feature 6: Integrations
File: `e2e-tests/.../features/009-Integrations.feature`

```gherkin
Feature: AI Provider Integrations
  As an Administrator
  I want to manage AI provider connections
  So that I can configure the analysis pipeline

  Scenario: Provider cards display
    Given the user is on the Integrations page
    Then 5 provider cards should be visible
    And "Jira Cloud Services" should have status "Active"
    And "Ollama (Local)" should have status "Active"
    And "Google Gemini API" should have status "Standby"
    And "LM Studio" should have status "Offline"

  Scenario: Test connection for Ollama
    Given the user is on the Integrations page
    When the user clicks "TEST LINK" on the Ollama card
    Then the button text should change to "PROBING..."
    And a progress bar should animate from 0% to 100%
    And the button should reset to "TEST LINK" after completion

  Scenario: Jira reconnect navigates to Onboarding
    Given the user is on the Integrations page
    When the user clicks "RE-CONNECT / UPDATE" on the Jira card
    Then the user should be redirected to the Onboarding page

  Scenario: Gemini model selection
    Given the user is on the Integrations page
    Then the Gemini card should have a model dropdown
    And the dropdown should contain: "Gemini 1.5 Pro", "Gemini 1.0 Ultra", "Gemini 1.5 Flash"
```

#### Feature 7: User Management & RBAC
File: `e2e-tests/.../features/010-UserManagement.feature`

```gherkin
Feature: User Management & RBAC
  As an Administrator
  I want to manage user roles and permissions
  So that I can control access to system features

  Scenario: User list display
    Given the user is on the User Management page
    Then 3 user cards should be visible
    And "Sarah Jenkins" should have role "Administrator"
    And "Alex Rivera" should have role "Neural Architect"
    And "Marcus Chen" should have role "Reader"

  Scenario: Permission toggle interaction
    Given the user is on the User Management page
    When the user toggles "Update Integrations" permission
    Then the toggle should change state
    And the sidebar ticker should display "IAM SYNC: UPDATING..."
    And the progress bar should animate
    And the ticker should display "SYNC COMPLETE"

  Scenario: Audit log display
    Given the user is on the User Management page
    Then the audit log should display at least 2 entries
    And entries should have timestamp, tag, and description

  Scenario: User card hover effect
    Given the user is on the User Management page
    When the user hovers over a user card
    Then the card should translate 8px to the right
```

#### Feature 8: End-to-End Navigation
File: `e2e-tests/.../features/011-Navigation.feature`

```gherkin
Feature: End-to-End Navigation
  As a user
  I want to navigate between all screens seamlessly
  So that I can access all features without broken links

  Scenario: Complete demo flow navigation
    Given the user starts at the Onboarding page
    When the user completes authentication
    And the user selects a project
    Then the user should be on the Dashboard page
    When the user clicks "Relationship Network" in the sidebar
    Then the user should be on the Knowledge Graph page
    When the user clicks "Project Analysis" in the sidebar
    Then the user should be on the Project Analysis page
    When the user clicks "Ticket Intelligence" in the sidebar
    Then the user should be on the Ticket Intelligence page
    When the user clicks "Integrations" in the sidebar
    Then the user should be on the Integrations page

  Scenario: Sidebar active state
    Given the user is on any page
    Then the corresponding sidebar item should have the active class
    And only one sidebar item should be active at a time

  Scenario: Sign out returns to Onboarding
    Given the user is on the Dashboard page
    When the user opens the user dropdown
    And the user clicks "Sign Out"
    Then the user should be redirected to the Onboarding page
```

### Test Data (Mock)

```json
{
  "users": [
    {"name": "Sarah Jenkins", "email": "sarah.j@company.com", "role": "Administrator"},
    {"name": "Alex Rivera", "email": "alex.riv@company.com", "role": "Neural Architect"},
    {"name": "Marcus Chen", "email": "m.chen@company.com", "role": "Reader"}
  ],
  "projects": [
    {"key": "JRA", "name": "Jira Assistant Dev"},
    {"key": "OBA", "name": "Obsidian Core Architecture"},
    {"key": "PLAT", "name": "Neural Platform v3"},
    {"key": "DX", "name": "Developer Experience"}
  ],
  "tickets": [
    {"key": "NET-458", "summary": "Service Discovery Core", "status": "In Progress", "points": 8},
    {"key": "DB-123", "summary": "Database Storage Layer", "status": "Done", "points": 5},
    {"key": "UI-789", "summary": "UI Component Module", "status": "To Do", "points": 3}
  ]
}
```

---


## PHAN 3: USER GUIDE (Tieng Viet)

### File can cap nhat
- `docs/Guides/UserGuide_StepByStep_VN.md`

### Cau truc User Guide

#### Chuong 1: Gioi thieu tong quan
- Jira Assistant la gi? (1 doan ngan)
- 3 gia tri chinh: Phan tich DNA ticket, Do thi mang luoi, Uoc luong Scrum thong minh
- Doi tuong su dung: Developer, Scrum Master, Product Owner
- Yeu cau he thong: Trinh duyet Chrome, ket noi Jira Cloud, (tuy chon) Ollama local

#### Chuong 2: Cai dat & Khoi tao (Onboarding)
- **B01**: Mo trang Onboarding
- **B02**: Nhap Jira Domain (vi du: `https://company.atlassian.net`)
- **B03**: Nhap API Token (huong dan lay token tu Jira: Settings > API Tokens > Create)
- **B04**: Nhan "ESTABLISH CONNECTION" — giai thich 3 giai doan ket noi
- **B05**: Chon du an tu danh sach
- **Luu y**: API Token khac voi mat khau. Khong chia se token.
- **Screenshot**: Man hinh Step 1 (Auth), Step 2 (Progress), Step 3 (Project Select)

#### Chuong 3: Dashboard — Tong quan Du an
- **B01**: Sau khi chon du an, Dashboard tu dong hien thi
- **B02**: Doc 3 chi so chinh: AI Health, Knowledge Nodes, Neural Velocity
- **B03**: Xem Neural Console de theo doi hoat dong he thong
- **B04**: Nhan "VIEW GRAPH" de xem do thi mang luoi
- **B05**: Nhan "ANALYSIS DRIFT" de xem phan tich sprint
- **Giai thich**: AI Health = do chinh xac cua mo hinh AI. Knowledge Nodes = so ticket da duoc AI phan tich va luu vao KB.
- **Screenshot**: Dashboard full view, Neural Console close-up

#### Chuong 4: Mang luoi Quan he (Knowledge Graph)
- **B01**: Truy cap tu sidebar hoac nut "VIEW GRAPH" tren Dashboard
- **B02**: Quan sat do thi: moi hinh luc giac la mot ticket
- **B03**: Mau sac node: Cyan = Feature chinh, Blue = Dependency, Violet = UI Module
- **B04**: Duong noi: Net lien = lien ket Jira tuong minh, Net dut = quan he AI phat hien
- **B05**: Click vao node de xem chi tiet ticket ben phai
- **B06**: Nhan "OPEN IN JIRA" de mo ticket trong Jira
- **Giai thich**: Semantic Relationship = moi quan he ma AI phat hien dua tren noi dung, khong phai link trong Jira.
- **Screenshot**: Do thi voi 3 nodes, detail panel

#### Chuong 5: Phan tich Du an (Project Analysis)
- **B01**: Truy cap tu sidebar "Project Analysis"
- **B02**: Doc 4 chi so: Total Tickets, Resolution Rate, Cycle Time, AI Velocity
- **B03**: Xem bieu do Velocity Trend — moi cot la 1 sprint
- **B04**: Xem AI Bottleneck Radar — canh bao van de va co hoi toi uu
- **Giai thich**: Cycle Time = thoi gian trung binh tu tao ticket den hoan thanh. AI Velocity = toc do xu ly cua AI.
- **Screenshot**: Full analysis page, bottleneck alerts close-up

#### Chuong 6: Tri tue Ticket (Ticket Intelligence)
- **B01**: Truy cap tu sidebar "Ticket Intelligence"
- **B02**: Nhap Ticket ID (vi du: NET-458) vao o tim kiem
- **B03**: Nhan "ANALYZE DATA" — quan sat progress bar
- **B04**: Doc tab "Requirement Context" — tom tat yeu cau tong hop
- **B05**: Chuyen sang tab "Requirement Evolution" — lich su thay doi
- **B06**: Chuyen sang tab "Predictive Complexity" — diem Scrum va ly do
- **B07**: (Tuy chon) Nhan "RE-ANALYZE" de AI phan tich lai
- **Giai thich**:
  - KB-First: He thong kiem tra Knowledge Base truoc. Neu da co ket qua thi hien thi ngay, khong goi AI. Tiet kiem thoi gian va chi phi.
  - Scrum Point: Diem uoc luong do phuc tap, theo thang 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40.
  - Feature DNA: Cach AI so sanh yeu cau moi voi cac ticket lich su de tim diem tuong dong.
- **Screenshot**: Input + progress bar, 3 tabs content

#### Chuong 7: Tich hop AI (Integrations)
- **B01**: Truy cap tu sidebar "Integrations"
- **B02**: Xem danh sach 5 providers va trang thai ket noi
- **B03**: Nhan "TEST LINK" tren Ollama card de kiem tra ket noi local
- **B04**: Cau hinh Gemini: nhap API Key, chon Model Tier
- **B05**: Nhan "RE-AUTHENTICATE" tren Gemini card
- **Giai thich**:
  - Ollama (Local): Chay tren may tinh cua ban, du lieu khong roi khoi mang noi bo. Phu hop du an bao mat cao.
  - Gemini (Cloud): Chay tren Google Cloud, manh hon nhung du lieu gui qua internet. Phu hop phan tich phuc tap.
  - LM Studio: Tuong tu Ollama nhung tuong thich OpenAI API format.
- **Luu y**: Chi Administrator moi co quyen thay doi cau hinh Integrations.
- **Screenshot**: Integration grid, TEST LINK progress

#### Chuong 8: Quan ly Nguoi dung & Phan quyen
- **B01**: Click avatar goc tren phai > "Account Settings" hoac truy cap tu sidebar (chi Admin)
- **B02**: Xem danh sach nguoi dung va vai tro hien tai
- **B03**: Thay doi vai tro: click dropdown ben canh ten nguoi dung
- **B04**: Bat/tat quyen cu the trong panel "GLOBAL PERMISSIONS"
- **B05**: Xem nhat ky thay doi trong Audit Log (Neural Console phia duoi)
- **Giai thich**:
  - Administrator: Toan quyen — cau hinh he thong, quan ly users, phan tich AI, ghi KB.
  - Neural Architect: Phan tich AI, ghi KB, xem dashboard. Khong quan ly users.
  - Reader: Chi xem dashboard va do thi. Khong phan tich AI hay ghi KB.
- **Screenshot**: User list, permission toggles, audit log

#### Chuong 9: Xu ly Su co (Troubleshooting)

| Van de | Nguyen nhan | Cach xu ly |
|--------|------------|-----------|
| Khong ket noi duoc Jira | Sai Domain hoac API Token | Kiem tra lai Domain (phai co `https://`). Tao API Token moi tai Jira Settings. |
| Ollama khong phan hoi | Ollama chua chay hoac sai port | Chay `ollama serve` trong terminal. Kiem tra port 11434. |
| AI phan tich qua cham | Mo hinh qua lon hoac may yeu | Chuyen sang mo hinh nho hon (llama3 thay vi llama3-70b). Hoac dung Gemini Cloud. |
| Do thi khong hien thi | Du an chua co ticket | Kiem tra du an co ticket trong Jira. Chay lai phan tich. |
| Khong doi duoc quyen | Khong phai Administrator | Chi Administrator moi doi duoc. Lien he admin cua team. |
| Progress bar bi ket | Mat ket noi mang | Kiem tra ket noi internet. Refresh trang. |
| "RE-ANALYZE" khong hoat dong | Role Reader khong co quyen | Chuyen sang role Architect hoac Admin. |
| Scrum Point khong hop ly | AI chua du du lieu lich su | Them nhieu ticket vao du an de AI co nhieu mau so sanh. |
| Tab khong chuyen duoc | Loi JavaScript | Refresh trang (Ctrl+F5). Kiem tra Console (F12) de xem loi. |
| Sign Out khong hoat dong | Cookie/session loi | Xoa cache trinh duyet. Dang nhap lai. |

#### Chuong 10: Bang Thuat ngu (Glossary)

| Thuat ngu | Giai thich |
|-----------|-----------|
| Knowledge Base (KB) | Co so du lieu luu tru ket qua phan tich AI. He thong kiem tra KB truoc khi goi AI de tiet kiem thoi gian. |
| Scrum Point | Diem uoc luong do phuc tap cua mot cong viec. Thang diem: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40. |
| Feature DNA | Cach AI phan tich va so sanh "gen" cua mot yeu cau voi cac ticket lich su de tim diem tuong dong. |
| Semantic Relationship | Moi quan he giua cac ticket ma AI phat hien dua tren noi dung (khong phai link tuong minh trong Jira). |
| RBAC | Role-Based Access Control — phan quyen dua tren vai tro (Admin, Architect, Reader). |
| Neural Console | Bang nhat ky hoat dong real-time cua he thong, hien thi o cuoi moi trang. |
| KB-First Strategy | Chien luoc uu tien: kiem tra KB truoc, chi goi AI khi chua co ket qua. |
| Failover | Co che tu dong chuyen sang AI provider du phong khi provider chinh khong phan hoi. |
| Glassmorphism | Phong cach thiet ke voi hieu ung kinh mo (glass effect) va nen mo (blur). |
| Ollama | Phan mem chay mo hinh AI tren may tinh ca nhan (local). Du lieu khong roi khoi may. |
| Gemini | Dich vu AI cua Google chay tren cloud. Manh hon nhung can ket noi internet. |

---

## PHAN 4: EXECUTION WORKFLOW

### Phase 1: BRD & SRS Update (BA)
- [ ] Review va cap nhat `docs/BA/BRD.md` voi tat ca BR-* requirements tren
- [ ] Review va cap nhat `docs/BA/SRS.md` voi tat ca AC-* acceptance criteria tren
- [ ] Xac nhan mock data cho tung man hinh
- [ ] PM review va approve

### Phase 2: E2E Test Scenarios (QA)
- [ ] Tao 8 feature files moi trong `e2e-tests/.../features/` (004 den 011)
- [ ] Cap nhat `docs/QA/TestScenarios.md` voi tong hop kich ban
- [ ] Dinh nghia test data (mock) trong test resources
- [ ] PM review va approve

### Phase 3: User Guide (Technical Writer)
- [ ] Viet/cap nhat `docs/Guides/UserGuide_StepByStep_VN.md` theo 10 chuong tren
- [ ] Yeu cau Frontend Designer cung cap screenshots cho tung man hinh
- [ ] Nhung screenshots vao guide
- [ ] Viet FAQ (toi thieu 10 cau — da co trong Troubleshooting)
- [ ] Viet Glossary (da co trong Bang Thuat ngu)
- [ ] PM review va approve

### Phase 4: Mockup & Screenshot (Frontend Designer)
- [ ] Chup screenshot hoac tao mockup cho tat ca 7 man hinh
- [ ] Dam bao screenshots phan anh dung trang thai demo (voi mock data)
- [ ] Cung cap cho Technical Writer de nhung vao guide
- [ ] Luu screenshots vao `docs/Guides/assets/`

### Phase 5: Cross-Review (PM)
- [ ] Kiem tra tinh nhat quan giua BRD <-> E2E Tests <-> User Guide
- [ ] Moi BR-* requirement phai co it nhat 1 AC-* acceptance criteria
- [ ] Moi AC-* phai co it nhat 1 Gherkin scenario tuong ung
- [ ] Moi chuong User Guide phai khop voi man hinh va BRD
- [ ] Tat ca roles sign-off

---

## Definition of Done (Pre-DEV)

- [ ] BRD cap nhat day du cho 7 man hinh (tat ca BR-* va AC-*)
- [ ] SRS dong bo voi BRD
- [ ] 8 Gherkin feature files da tao (004-011)
- [ ] TestScenarios.md cap nhat
- [ ] User Guide VN 10 chuong hoan chinh
- [ ] Screenshots/mockups cho tat ca man hinh
- [ ] FAQ toi thieu 10 cau
- [ ] Glossary day du
- [ ] Cross-review: BRD <-> Tests <-> Guide nhat quan
- [ ] PM sign-off


---

## PHAN 5: USER JOURNEYS — LUONG SU DUNG TU DAU DEN CUOI

### Journey 1: Administrator — Thiet lap He thong Lan dau

Persona: Sarah Jenkins (Administrator)
Muc tieu: Ket noi Jira, cau hinh AI providers, thiet lap phan quyen cho team

```
BUOC 1: Onboarding (MH1)
  |-- Mo trinh duyet Chrome, truy cap URL he thong
  |-- Thay man hinh "Initiate Neural Link" voi 3 buoc: Authentication > Connection Test > Select Project
  |-- Nhap JIRA INSTANCE URL: https://company.atlassian.net
  |-- Nhap API TOKEN: (paste token tu Jira Settings > API Tokens)
  |-- Nhan "ESTABLISH CONNECTION"
  |-- Quan sat progress bar:
  |     [0-30%]  "HANDSHAKING WITH JIRA..."
  |     [30-70%] "VALIDATING API TOKENS..."
  |     [70-100%] "FETCHING PROJECT METADATA..."
  |-- Thay danh sach 4 du an trong grid 2 cot
  |-- Click chon "JRA — Jira Assistant Dev"
  |-- He thong chuyen sang Dashboard
  |
BUOC 2: Dashboard — Kiem tra tong quan (MH2)
  |-- Thay 3 stat cards: AI Health 94.2%, Knowledge Nodes 1,024, Neural Velocity 42.8
  |-- Kiem tra Neural Console phia duoi: 3 dong log (AI_SYNC, KB_WRITE, HEARTBEAT)
  |-- Xac nhan sidebar hien thi 5 menu items, "Dashboard" dang active (highlight cyan)
  |
BUOC 3: Integrations — Cau hinh AI Providers (MH6)
  |-- Click "Integrations" tren sidebar
  |-- Thay 5 provider cards:
  |     Jira Cloud: Active (green dot) — Domain da dien tu Onboarding
  |     Ollama: Active (green dot) — Endpoint localhost:11434, Model llama3-8b-instruct
  |     Gemini API: Standby (blue dot) — API Key masked, Model dropdown
  |     LM Studio: Offline (red dot) — Port 1234
  |     Gemini CLI: Standby (blue dot) — CLI path
  |-- Nhan "TEST LINK" tren Ollama card:
  |     Button text doi thanh "PROBING..."
  |     Progress bar animate 0% -> 100% (khoang 2.5s)
  |     Button reset ve "TEST LINK"
  |-- Nhap API Key cho Gemini, chon "Gemini 1.5 Pro" tu dropdown
  |-- Nhan "RE-AUTHENTICATE" tren Gemini card
  |     Tuong tu: progress bar animate, xac nhan ket noi
  |
BUOC 4: User Management — Thiet lap phan quyen (MH7)
  |-- Click avatar goc tren phai > "Account Settings"
  |     HOAC: truy cap truc tiep URL frontend/users/
  |-- Thay danh sach 3 users:
  |     Sarah Jenkins (You) — Administrator
  |     Alex Rivera — Neural Architect
  |     Marcus Chen — Reader
  |-- Kiem tra panel "GLOBAL PERMISSIONS" ben phai:
  |     Trigger AI Scan: ON (toggle xanh)
  |     Knowledge Base Write: ON
  |     Update Integrations: OFF (toggle xam)
  |     Export Neural Data: ON
  |-- Bat "Update Integrations" cho Alex Rivera:
  |     Click toggle -> toggle chuyen sang xanh
  |     Sidebar ticker: "IAM SYNC: UPDATING..."
  |     Progress bar animate -> "SYNC COMPLETE"
  |-- Kiem tra Audit Log phia duoi:
  |     [09:12:05] IAM_SYNC  Policy 'INTEG_UPDATE' updated for user: Alex Rivera
  |
BUOC 5: Xac nhan he thong san sang
  |-- Quay lai Dashboard (click sidebar)
  |-- Neural Console hien thi log moi: "IAM policy updated"
  |-- He thong san sang cho team su dung
```

---

### Journey 2: Scrum Master — Sprint Planning voi AI

Persona: Alex Rivera (Neural Architect)
Muc tieu: Phan tich ticket, uoc luong Scrum points, xem velocity trend

```
BUOC 1: Dang nhap (MH1)
  |-- Mo Onboarding, nhap credentials
  |-- Chon du an "JRA — Jira Assistant Dev"
  |-- Vao Dashboard
  |
BUOC 2: Kiem tra tong quan Sprint (MH2 + MH4)
  |-- Dashboard: doc AI Health (94.2%), Knowledge Nodes (1,024)
  |-- Nhan "ANALYSIS DRIFT" -> chuyen sang Project Analysis (MH4)
  |-- Doc 4 chi so:
  |     Total Tickets: 142
  |     Resolution Rate: 88%
  |     Cycle Time: 12.4 days
  |     AI Velocity: 42
  |-- Xem Velocity Trend chart: 7 bars the hien story points qua 7 sprints
  |     Hover tung bar de xem chi tiet (bar scale 1.05)
  |-- Xem AI Bottleneck Radar:
  |     Warning: "Backend Blockers Detected — 4 tickets in 'In Review' for > 72 hours"
  |     Positive: "Optimized Path Found — Sprint could finish 2 days earlier"
  |
BUOC 3: Phan tich Ticket cu the (MH5)
  |-- Click "Ticket Intelligence" tren sidebar
  |-- Nhap "NET-458" vao o Ticket ID
  |-- Nhan "ANALYZE DATA"
  |-- Quan sat progress bar:
  |     [0-40%]  "Consolidating Ticket Metadata..."
  |     [40-85%] "AI RE-ANALYZING SCOPE..."
  |     [85-100%] "SYNCING TO KNOWLEDGE BASE..."
  |-- Progress bar an di, ket qua hien thi
  |
  |-- TAB 1: Requirement Context (mac dinh active)
  |     Doc summary: "NET-458 represents a core architectural pivot..."
  |     Xem danh sach modules anh huong:
  |       (cyan)   core-discovery-v2 (Implementation)
  |       (blue)   cloud-provider-aws (Dependency)
  |       (violet) security-iam-bridge (Auth Handshake)
  |
  |-- Click TAB 2: Requirement Evolution
  |     Content fadeIn 0.5s
  |     Neural console hien thi timeline:
  |       [Origin]  REQ_START   Initial scope defined in BT-001
  |       [v1.2]    SCOPE_PIVOT Analysis of BT-124 suggested decoupling
  |       [Current] NET-458     Finalizing sub-ticket structure
  |
  |-- Click TAB 3: Predictive Complexity
  |     Content fadeIn 0.5s
  |     Scrum Point: "8" (font cuc lon, gradient trang->cyan)
  |     Subtitle: "High Complexity: Cross-Module Handshake"
  |     KB References:
  |       Badge: "NET-112 (Similarity 92%)"
  |       Badge: "DB-45 (Logic overlap 75%)"
  |
BUOC 4: Phan tich them ticket khac
  |-- Nhap "DB-123" vao o Ticket ID
  |-- Nhan "ANALYZE DATA"
  |-- Lan nay KB-First: neu DB-123 da duoc phan tich truoc do
  |     -> He thong tra ve ket qua tu KB ngay lap tuc (khong co progress bar dai)
  |     -> Hien thi ket qua cached
  |-- Neu muon AI phan tich lai: nhan "RE-ANALYZE"
  |     -> Progress bar chay lai 3 giai doan
  |     -> Ket qua moi ghi de KB
  |
BUOC 5: Xem mang luoi quan he (MH3)
  |-- Click "Relationship Network" tren sidebar
  |-- Do thi hien thi 3 nodes hexagonal:
  |     NET-458 (cyan) — Service Discovery Core
  |     DB-123 (blue) — Database Storage Layer
  |     UI-789 (violet) — UI Component Module
  |-- Duong noi:
  |     NET-458 <-> DB-123: net lien (explicit dependency)
  |     NET-458 <-> UI-789: net dut (semantic — AI phat hien)
  |-- Click node NET-458:
  |     Panel ben phai hien thi:
  |       "TICKET DETAILS"
  |       "NET-458: Service"
  |       "Implement discovery logic in the core module..."
  |       Button "OPEN IN JIRA"
  |-- Tim kiem: nhap "DB" vao search box -> filter nodes
```

---

### Journey 3: Product Owner — Roadmap & Dependency Review

Persona: Marcus Chen (Reader)
Muc tieu: Xem tong quan du an, nhan dien phu thuoc, khong can phan tich AI

```
BUOC 1: Dang nhap (MH1)
  |-- Onboarding -> chon du an -> Dashboard
  |
BUOC 2: Dashboard (MH2)
  |-- Doc 3 chi so tong quan
  |-- Xem Neural Console de biet hoat dong gan nhat
  |
BUOC 3: Knowledge Graph (MH3)
  |-- Click "Relationship Network" tren sidebar
  |-- Quan sat do thi de nhan dien:
  |     Cac cum tinh nang (nodes cung mau nam gan nhau)
  |     Phu thuoc chinh (duong noi day)
  |     Quan he an (duong net dut — AI phat hien)
  |-- Click tung node de doc chi tiet
  |-- Nhan "OPEN IN JIRA" de xem ticket goc trong Jira
  |
BUOC 4: Project Analysis (MH4)
  |-- Click "Project Analysis" tren sidebar
  |-- Doc Resolution Rate (88%) va Cycle Time (12.4 days)
  |-- Xem Velocity Trend de danh gia xu huong sprint
  |-- Doc Bottleneck Radar de biet van de can xu ly
  |
BUOC 5: Ticket Intelligence — Chi xem (MH5)
  |-- Click "Ticket Intelligence" tren sidebar
  |-- Xem ket qua phan tich da co (tu KB cache)
  |-- LUU Y: Voi role Reader, Marcus KHONG THE:
  |     - Nhan "ANALYZE DATA" cho ticket moi (button disabled hoac bi chan)
  |     - Nhan "RE-ANALYZE" (button disabled hoac bi chan)
  |     - Chi xem ket qua da co trong KB
  |
BUOC 6: Khong truy cap duoc Integrations va User Management
  |-- Integrations: Marcus co the xem nhung KHONG the thay doi cau hinh
  |-- User Management: Marcus KHONG the doi role hay toggle permissions
  |     (cac controls disabled hoac an di)
```

---

## PHAN 6: CHI TIET TUNG ELEMENT TREN MOI MAN HINH

### MH1: Onboarding — Chi tiet Elements

#### Layout tong the
- Khong co sidebar (man hinh full-screen centered)
- Container: max-width 900px, padding 80px 0
- Background: living-void (gradient nebula)

#### Element: Logo Header
- Logo icon: 48x48px, background cyan, border-radius 8px, margin auto
- Title: "Initiate Neural Link" — font-size 40px, font-weight 100
- Subtitle: "Connect your Jira ecosystem..." — opacity 0.5, margin-top 16px

#### Element: Step Indicator
- 3 steps ngang: AUTHENTICATION, CONNECTION TEST, SELECT PROJECT
- Moi step: step-num (24x24 circle voi border) + label (11px, letter-spacing 2px)
- Step active: opacity 1, color cyan
- Step inactive: opacity 0.3

#### Element: Step 1 Card (Authentication)
- Glass card: padding 48px, min-height 400px
- Input stack (flex column, gap 24px):
  - Label: "JIRA INSTANCE URL" (11px, opacity 0.5, font-weight 700, letter-spacing 2px)
  - Input: glass-input (background rgba white 5%, border glass-border, padding 18px, border-radius 12px)
  - Focus state: border-color cyan, background rgba cyan 5%
  - Label: "API TOKEN"
  - Input: type password
- Button: "ESTABLISH CONNECTION" (btn-vibrant, padding 20px, font-size 16px, full width)

#### Element: Step 2 Card (Connection Test)
- Glass card, text-align center
- Status ticker: "HANDSHAKING WITH JIRA..." (14px)
- Neural loader: height 4px, max-width 400px, margin auto
- Sub-text: "VALIDATING PERMISSIONS & SCOPES" (12px, opacity 0.4)

#### Element: Step 3 Card (Project Selection)
- Title: "Choose Project Workspace" (24px, font-weight 300)
- Grid 2 columns, gap 20px
- Project item: padding 24px, border glass-border, border-radius 16px
  - Project key: 11px, opacity 0.5, font-weight 700
  - Project name: font-weight 600
  - Hover: border-color cyan, background rgba cyan 5%
  - Click: redirect to Dashboard

---

### MH2: Dashboard — Chi tiet Elements

#### Layout tong the
- Sidebar (280px) | Main content (flex 1)
- Navbar (80px) | Workspace (padding 48px, overflow-y auto)

#### Element: Sidebar
- Logo section: icon 32x32 cyan + "JIRA ASSISTANT" (14px, letter-spacing 2px)
- Nav items (5): icon + label, padding 14px 20px, border-radius 12px
  - Active: background rgba cyan 10%, color cyan, font-weight 600
  - Hover: color white, background rgba white 3%, translateX 5px
- Status panel: glass border, padding 20px
  - "Core Backend: ACTIVE" (9px, JetBrains Mono)
  - Neural loader: 2px, width 100%
  - "OLLAMA v3.1 SYNC" (9px, opacity 0.4)

#### Element: Navbar
- Left: Breadcrumb "DASHBOARD / OVERVIEW" (11px, letter-spacing 2.5px)
- Right: Search box (300px) + User widget
  - Search: glass background, border glass-border, padding 10px 20px, border-radius 10px
  - Avatar: 36px circle, border 2px accent
  - Name: "Sarah K."

#### Element: User Dropdown (click avatar)
- Position: absolute, top calc(100% + 20px), right 0, width 260px
- Background: rgba(15,23,42,0.95), backdrop-filter blur 40px
- Border-radius 20px, z-index 9999
- Animation: dropdownPop 0.4s cubic-bezier
- Header: Name (14px, bold) + Role (11px, opacity 0.5)
- Items: Account Settings, Security & Permissions, (divider), Sign Out (danger color)
- Item hover: background rgba white 5%, translateX 5px

#### Element: Stat Cards (3)
- Grid span-4 each
- Border-left 4px (primary / accent / secondary)
- Label: 11px, font-weight 700, opacity 0.4, letter-spacing 1px
- Value: 32px, font-weight 300
- Delta: 14px, color primary

#### Element: Graph Preview Card (span-6)
- Background: rgba cyan 5%
- Title: "Relationship Network" (font-weight 300)
- Placeholder: 120px, dashed border, centered text "[Live Visualization Placeholder]"
- Button: "VIEW GRAPH" (background accent)

#### Element: Estimation Drift Card (span-6)
- Background: rgba violet 5%
- Title: "AI Estimation Drift"
- 4 bars: flex, gap 8px, align-items flex-end, height 120px
  - Heights: 30%, 45%, 80%, 60%
  - Color: secondary
  - Tallest bar: box-shadow 0 0 20px secondary
- Button: "ANALYSIS DRIFT" (background secondary, color black)

#### Element: Neural Console
- Height 200px, background rgba black 30%, border glass-border, border-radius 12px
- Font: JetBrains Mono 11px, color primary
- Console line: flex, gap 12px
  - Time: opacity 0.3
  - Tag: font-weight 700, color secondary, min-width 80px
  - Message: default color

---

### MH3: Knowledge Graph — Chi tiet Elements

#### Element: Graph Container
- Width/height 100%, position relative
- SVG: width/height 100%, overflow visible

#### Element: Hexagonal Nodes
- Polygon 6 diem (40,0 80,20 80,60 40,80 0,60 0,20)
- Fill: rgba voi mau tuong ung (opacity 0.2)
- Stroke: mau tuong ung, stroke-width 2
- Text: ticket key, 12px, font-weight 700, fill white, text-anchor middle
- Cursor: pointer
- data-tooltip: "Feature: [description]. Confidence: [%]"

#### Element: Edge Lines
- SVG line elements
- Explicit: stroke solid, color primary, opacity 0.3
- Semantic: stroke dashed (dasharray), color secondary, opacity 0.3

#### Element: Detail Panel (right side)
- Position absolute, right 0, top 0, width 300px, height 100%
- Glass card, border-radius 0 0 24px 24px
- Header: "TICKET DETAILS" (11px, opacity 0.5) + Ticket title (20px, font-weight 300)
- Description: 13px, line-height 1.6, opacity 0.8
- Button: "OPEN IN JIRA" (btn-vibrant, full width)

#### Element: Search Box (navbar)
- Placeholder: "Search feature tickets..."
- Filter nodes khi nhap text

---

### MH5: Ticket Intelligence — Chi tiet Elements

#### Element: Input Bar
- Flex row, justify-content space-between
- Left: Title section (h1 + subtitle)
- Right: Ticket ID input (180px) + "ANALYZE DATA" button

#### Element: Analysis Tabs
- Flex row, gap 40px, border-bottom 1px glass-border
- Tab item: padding 16px 0, font-size 14px, cursor pointer
- Active tab: color primary, font-weight 700
- Active indicator: pseudo-element ::after, bottom -1px, height 2px, background primary, box-shadow glow

#### Element: Progress Bar (analysis)
- Container: margin 24px 0, display none (show khi analyzing)
- Label row: flex space-between, 11px, font-weight 700, color primary
  - Left: step text (thay doi theo giai doan)
  - Right: percentage
- Neural loader: animate width

#### Element: Tab Content — Context
- Glass card
- Title: "Unified Requirement Summary" (h3)
- Summary text: 15px, line-height 1.8, opacity 0.8
- Module list: ul, margin 24px, line-height 2
  - Colored bullet (span): primary / accent / secondary
  - Module name + role in parentheses

#### Element: Tab Content — Evolution
- Glass card, border-left 4px accent
- Title: "Lineage & Change History"
- Neural console: height 300px
- Timeline entries: console-line format
  - Time: [Origin], [v1.2], [Current]
  - Tag: REQ_START, SCOPE_PIVOT, NET-458
  - Current entry: color white (highlight)

#### Element: Tab Content — Complexity
- Centered layout (calibration-grid)
- Score display: glass card, width 400px, text-align center
  - Label: "Scrum Point Estimation" (m-label)
  - Score: "8" (font-size 12rem, font-weight 100, gradient text white->primary)
  - Subtitle: "High Complexity: Cross-Module Handshake" (13px, opacity 0.5)
  - KB References section:
    - Label: "KB REFERENCES" (9px)
    - Badges: role-badge style, 9px
      - "NET-112 (Similarity 92%)" — admin badge
      - "DB-45 (Logic overlap 75%)" — architect badge

---

### MH6: Integrations — Chi tiet Elements

#### Element: Provider Card (chung)
- Glass card, position relative, overflow hidden, flex column
- Provider logo: 40x40, border-radius 8px, background rgba white 5%, centered emoji 20px
- Status dot: 8x8, border-radius 50%, inline-block, box-shadow 0 0 10px currentColor
- Title: h3
- Description: 13px, opacity 0.5
- Config fields: label (config-label 10px) + input (config-input, glass style)
- Action area: margin-top auto, flex row, gap 12px

#### Element: Config Input
- Width 100%, background rgba white 3%, border glass-border
- Padding 12px, border-radius 8px, color white, font-size 13px

#### Element: TEST LINK Flow
- Click button:
  1. button.style.opacity = 0.3
  2. button.innerText = "PROBING..."
  3. progress container display block
  4. Interval 50ms: bar width += 5%
  5. At 100%: clearInterval, reset button, hide progress (500ms delay)

---

### MH7: User Management — Chi tiet Elements

#### Element: IAM Container
- Grid: 1fr (user list) + 350px (permissions panel)
- Gap 32px, padding 40px 0

#### Element: User Card
- Glass card, flex row, align-items center, justify-content space-between
- Padding 20px
- Hover: translateX 8px, background rgba white 5%
- Left: avatar (48px circle) + name (font-weight 600) + email (12px, opacity 0.5)
- Right: role-selector dropdown
  - Background rgba black 30%, border glass-border, color white
  - Padding 6px 12px, border-radius 20px, font-size 11px

#### Element: Permission Toggle
- Container: perm-row, flex space-between, padding 12px 0, border-bottom glass-border
- Label: 13px
- Toggle: 40x20px, border-radius 10px, cursor pointer
  - OFF: background rgba white 10%
  - ON (.active): background primary, box-shadow 0 0 10px primary
  - Circle (::after): 16px, white, border-radius 50%
    - OFF: left 2px
    - ON: left 22px
  - Transition 0.3s

#### Element: Audit Log
- Neural console, margin-top 40px
- Same format as Dashboard neural console
- Entries: IAM_SYNC, USER_LOGIN events

---

## PHAN 7: GLOBAL INTERACTIONS & MICRO-ANIMATIONS

### Hover Effects
| Element | Effect | Duration |
|---------|--------|----------|
| Glass card | translateY(-5px) scale(1.02), border primary, box-shadow cyan glow | 0.4s cubic-bezier |
| Nav item | color white, background rgba white 3%, translateX 5px | 0.3s cubic-bezier |
| Nav item icon | rotate(15deg) scale(1.2) | 0.3s |
| Button (btn-vibrant) | active: scale(0.95) | instant |
| User card | translateX(8px), background rgba white 5% | 0.3s |
| Velocity bar | scaleY(1.05), opacity 1 | 0.5s cubic-bezier |
| Hexagonal node | stroke white, strokeWidth 2, scale 1.1 | 0.3s cubic-bezier |
| Tooltip | opacity 0->1, bottom 125%->110% | 0.3s |

### Progress Bar Patterns
| Context | Stages | Total Duration |
|---------|--------|---------------|
| Onboarding connection | 3 stages (30/70/100%) | ~2.5s |
| Ticket analysis | 3 stages (40/85/100%) | ~2.9s |
| Integration test | Linear 0->100% | ~2.5s |
| Permission toggle | Linear 0->100% | ~0.5s |

### Dropdown Behavior
- Open: click avatar -> toggle .active class -> animation dropdownPop 0.4s
- Close: click outside document -> remove .active
- Internal click: stopPropagation (khong dong)

### Tab Switching
- Remove .active tu tat ca tabs va panels
- Add .active cho tab va panel duoc chon
- Panel animation: fadeIn 0.5s (opacity 0->1, translateY 10px->0)

### Parallax (Dashboard only)
- Mouse move -> living-void translate (x*20px, y*20px)
- Based on clientX/innerWidth va clientY/innerHeight

### Number Animation (Dashboard only)
- On page load: stats count up tu 0 den target
- Interval 30ms per increment

---

## PHAN 8: RBAC PERMISSION MATRIX — CHI TIET

| Chuc nang | Man hinh | Administrator | Neural Architect | Reader |
|-----------|----------|:---:|:---:|:---:|
| Xem Dashboard stats | MH2 | YES | YES | YES |
| Xem Neural Console | MH2 | YES | YES | YES |
| Xem Knowledge Graph | MH3 | YES | YES | YES |
| Click node xem detail | MH3 | YES | YES | YES |
| Open in Jira | MH3 | YES | YES | YES |
| Xem Project Analysis | MH4 | YES | YES | YES |
| Xem Velocity/Bottleneck | MH4 | YES | YES | YES |
| Nhap Ticket ID + Analyze | MH5 | YES | YES | NO |
| Xem ket qua tu KB cache | MH5 | YES | YES | YES |
| RE-ANALYZE (force AI) | MH5 | YES | YES | NO |
| Xem Integrations | MH6 | YES | YES | YES |
| Thay doi cau hinh provider | MH6 | YES | NO | NO |
| TEST LINK | MH6 | YES | YES | NO |
| Xem User list | MH7 | YES | NO | NO |
| Doi role nguoi dung | MH7 | YES | NO | NO |
| Toggle permissions | MH7 | YES | NO | NO |
| Xem Audit Log | MH7 | YES | NO | NO |
| Sign Out | All | YES | YES | YES |

### UI Behavior theo Role

**Reader (Marcus Chen):**
- MH5: Button "ANALYZE DATA" va "RE-ANALYZE" hien thi nhung disabled (opacity 0.5, cursor not-allowed)
- MH6: Cac input fields va buttons disabled, chi xem trang thai
- MH7: Khong hien thi trong sidebar hoac redirect ve Dashboard neu truy cap truc tiep

**Neural Architect (Alex Rivera):**
- MH6: Xem duoc tat ca, TEST LINK duoc, nhung khong doi duoc cau hinh (inputs disabled)
- MH7: Khong truy cap duoc

**Administrator (Sarah Jenkins):**
- Toan quyen tren tat ca man hinh
