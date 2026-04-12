# Test Scenarios - Jira Assistant

**Project Name**: Jira Assistant (AI-Powered)  
**Version**: 2.0.0  
**Owner**: QA Team  
**Status**: Updated — Full E2E Coverage per Screen  

## 1. Onboarding & Authentication (MH1)

- **TS-01: Successful Authentication Flow**
  - **Input**: Enter valid Jira Domain + API Token, click "ESTABLISH CONNECTION"
  - **Expectation**: Progress bar shows 3 stages → Project grid displays at least 2 projects

- **TS-02: Failed Authentication**
  - **Input**: Enter invalid credentials, click "ESTABLISH CONNECTION"
  - **Expectation**: Error message displayed, user remains on Authentication step

- **TS-03: Project Selection → Dashboard Redirect**
  - **Input**: Click on project "JRA" in the grid
  - **Expectation**: User redirected to Dashboard with project context loaded

- **TS-04: Progress Bar Stages**
  - **Input**: Submit valid credentials
  - **Expectation**: Status ticker changes through: "HANDSHAKING WITH JIRA..." → "VALIDATING API TOKENS..." → "FETCHING PROJECT METADATA..."

## 2. Dashboard (MH2)

- **TS-05: Hero Stats Display**
  - **Input**: Navigate to Dashboard
  - **Expectation**: 3 stat cards show: AI Health (94.2%), Knowledge Nodes (1,024), Neural Velocity (42.8)

- **TS-06: Neural Console Activity**
  - **Input**: View Dashboard
  - **Expectation**: Console shows at least 3 log entries with timestamp, tag, and message

- **TS-07: VIEW GRAPH Navigation**
  - **Input**: Click "VIEW GRAPH" button
  - **Expectation**: User redirected to Knowledge Graph page

- **TS-08: ANALYSIS DRIFT Navigation**
  - **Input**: Click "ANALYSIS DRIFT" button
  - **Expectation**: User redirected to Project Analysis page

- **TS-09: User Dropdown Open/Close**
  - **Input**: Click avatar → dropdown opens. Click outside → dropdown closes.
  - **Expectation**: Dropdown shows Account Settings, Security & Permissions, Sign Out with pop-in animation

- **TS-10: Sidebar Active State**
  - **Input**: Navigate to Dashboard
  - **Expectation**: "Dashboard" sidebar item highlighted with cyan, others inactive

## 3. Knowledge Graph (MH3)

- **TS-11: Hexagonal Nodes Display**
  - **Input**: Navigate to Knowledge Graph
  - **Expectation**: At least 3 hexagonal nodes visible with ticket keys (NET-458, DB-123, UI-789)

- **TS-12: Node Color Coding**
  - **Input**: View graph
  - **Expectation**: Feature nodes cyan, Dependency nodes blue, UI Module nodes violet

- **TS-13: Edge Type Differentiation**
  - **Input**: View graph edges
  - **Expectation**: Explicit edges solid, semantic edges dashed

- **TS-14: Node Hover Effect**
  - **Input**: Hover over node
  - **Expectation**: Node scales 1.1x with white stroke

- **TS-15: Node Click → Detail Panel**
  - **Input**: Click node "NET-458"
  - **Expectation**: Right panel shows: "NET-458", summary, description, "OPEN IN JIRA" button

- **TS-16: Graph Search**
  - **Input**: Type "DB" in search box
  - **Expectation**: Only DB-related nodes visible/highlighted

## 4. Project Analysis (MH4)

- **TS-17: Four Metrics Display**
  - **Input**: Navigate to Project Analysis
  - **Expectation**: Cards show: Total Tickets (142), Resolution Rate (88%), Cycle Time (12.4), AI Velocity (42)

- **TS-18: Velocity Chart Bars**
  - **Input**: View velocity chart
  - **Expectation**: 7 bars displayed, odd bars have gradient primary color

- **TS-19: Velocity Bar Hover**
  - **Input**: Hover over a bar
  - **Expectation**: Bar scales vertically (1.05x)

- **TS-20: Bottleneck Alerts**
  - **Input**: View AI Bottleneck Radar
  - **Expectation**: At least 1 warning alert and 1 positive suggestion displayed

## 5. Ticket Intelligence (MH5)

- **TS-21: Ticket Analysis Trigger**
  - **Input**: Enter "NET-458", click "ANALYZE DATA"
  - **Expectation**: Progress bar visible with 3 stages, results displayed after completion

- **TS-22: KB-First Cache Hit**
  - **Input**: Analyze a ticket that already exists in KB
  - **Expectation**: Result returned immediately without progress bar (or very short)

- **TS-23: Context Tab Content**
  - **Input**: View Context tab after analysis
  - **Expectation**: Summary text + module list with colored bullets (cyan, blue, violet)

- **TS-24: Evolution Tab Content**
  - **Input**: Click "Requirement Evolution" tab
  - **Expectation**: Timeline with entries: [Origin] REQ_START, [v1.2] SCOPE_PIVOT, [Current] NET-458

- **TS-25: Complexity Tab Content**
  - **Input**: Click "Predictive Complexity" tab
  - **Expectation**: Scrum Point "8" (large gradient text), complexity subtitle, 2 KB reference badges

- **TS-26: Tab Switch Animation**
  - **Input**: Switch between tabs
  - **Expectation**: Content panels fadeIn with 0.5s animation

- **TS-27: Re-Analyze**
  - **Input**: Click "RE-ANALYZE"
  - **Expectation**: Progress bar restarts, results updated

- **TS-28: Points Scale Validation**
  - **Input**: Any analysis result
  - **Expectation**: Scrum Points only from: {0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40}

- **TS-29: Reader Role Restriction**
  - **Input**: Reader user views Ticket Intelligence
  - **Expectation**: "ANALYZE DATA" and "RE-ANALYZE" buttons disabled (opacity 0.5, not clickable)

## 6. Integrations (MH6)

- **TS-30: Five Provider Cards**
  - **Input**: Navigate to Integrations
  - **Expectation**: 5 cards visible: Jira Cloud, Ollama, Gemini API, LM Studio, Gemini CLI

- **TS-31: Status Dot Colors**
  - **Input**: View provider cards
  - **Expectation**: Jira=Active(green), Ollama=Active(green), Gemini=Standby(blue), LMStudio=Offline(red), CLI=Standby(blue)

- **TS-32: TEST LINK Interaction**
  - **Input**: Click "TEST LINK" on Ollama card
  - **Expectation**: Button → "PROBING...", progress bar 0→100%, button resets

- **TS-33: Jira Reconnect**
  - **Input**: Click "RE-CONNECT / UPDATE" on Jira card
  - **Expectation**: Redirect to Onboarding page

- **TS-34: Gemini Model Dropdown**
  - **Input**: View Gemini card
  - **Expectation**: Dropdown contains: Gemini 1.5 Pro, Gemini 1.0 Ultra, Gemini 1.5 Flash

- **TS-35: Status Dot Tooltips**
  - **Input**: Hover over status dots
  - **Expectation**: Tooltips show details (latency, session time, error reason)

## 7. User Management & RBAC (MH7)

- **TS-36: User List Display**
  - **Input**: Navigate to User Management (Admin only)
  - **Expectation**: 3 users: Sarah Jenkins (Admin), Alex Rivera (Architect), Marcus Chen (Reader)

- **TS-37: User Card Hover**
  - **Input**: Hover over user card
  - **Expectation**: Card translates 8px to the right

- **TS-38: Permission Toggle**
  - **Input**: Toggle "Update Integrations" permission
  - **Expectation**: Toggle changes state, sidebar shows "IAM SYNC: UPDATING..." → "SYNC COMPLETE"

- **TS-39: Audit Log Entry**
  - **Input**: After toggling permission
  - **Expectation**: New entry in audit log with timestamp, IAM_SYNC tag, and change description

- **TS-40: Role Dropdown**
  - **Input**: View role selector for Alex Rivera
  - **Expectation**: Dropdown shows: Neural Architect (selected), Administrator, Reader

## 8. End-to-End Navigation

- **TS-41: Complete Demo Flow**
  - **Input**: Start at Onboarding → Authenticate → Select project → Navigate all 5 sidebar pages → Sign Out
  - **Expectation**: All pages load correctly, no broken links, sidebar active state updates

- **TS-42: Sidebar Active State Consistency**
  - **Input**: Navigate to each page via sidebar
  - **Expectation**: Only the current page's sidebar item is highlighted

- **TS-43: Sign Out Flow**
  - **Input**: Click avatar → Sign Out
  - **Expectation**: Redirect to Onboarding page

- **TS-44: Cross-Page Data Consistency**
  - **Input**: Analyze ticket on MH5, then view same ticket on MH3
  - **Expectation**: Ticket data consistent across both views

## 9. RBAC Cross-Screen Verification

- **TS-45: Reader Cannot Analyze**
  - **Input**: Reader navigates to Ticket Intelligence
  - **Expectation**: "ANALYZE DATA" and "RE-ANALYZE" disabled

- **TS-46: Reader Cannot Modify Integrations**
  - **Input**: Reader navigates to Integrations
  - **Expectation**: Config inputs and action buttons disabled

- **TS-47: Non-Admin Cannot Access User Management**
  - **Input**: Neural Architect or Reader tries to access User Management
  - **Expectation**: Redirected to Dashboard or access denied

- **TS-48: Admin Full Access**
  - **Input**: Administrator navigates all pages
  - **Expectation**: All features accessible and functional
