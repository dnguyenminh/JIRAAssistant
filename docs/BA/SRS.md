# Software Requirements Specification (SRS) - Jira Assistant

**Project Name**: Jira Assistant (AI-Powered)  
**Version**: 2.0.0  
**Owner**: Business Analysis & QA Team  
**Status**: Updated — Full Functional Specification per Screen  

## 1. Introduction
The Jira Assistant is an AI-driven tool built with **Kotlin Multiplatform (KMP)** for Web-based agile project management. It enhances team performance by automating ticket relationship mapping and point estimation. This SRS specifies functional requirements organized by screen, with acceptance criteria for each.

## 2. Functional Requirements by Screen

### 2.1 Onboarding (MH1)

- **FR-01: Jira Authentication**: The system SHALL accept Jira Instance URL and API Token as authentication credentials.
- **FR-02: Credential Validation**: The system SHALL validate credentials by calling the Jira REST API (`/rest/api/3/project`).
- **FR-03: Connection Progress**: The system SHALL display a 3-stage progress bar: "HANDSHAKING WITH JIRA..." (0-30%), "VALIDATING API TOKENS..." (30-70%), "FETCHING PROJECT METADATA..." (70-100%).
- **FR-04: Project Selection**: After successful authentication, the system SHALL display available projects in a 2-column grid showing Project Key and Project Name.
- **FR-05: Authentication Error**: IF credentials are invalid, THEN the system SHALL display an error message and allow the user to re-enter credentials without page reload.
- **FR-06: Project Redirect**: WHEN a user selects a project, THEN the system SHALL redirect to the Dashboard with the selected project context.

### 2.2 Dashboard (MH2)

- **FR-07: Hero Metrics**: The Dashboard SHALL display 3 stat cards: PROJECT AI HEALTH (percentage + delta), ACTIVE KNOWLEDGE NODES (count / total), NEURAL VELOCITY (score + status).
- **FR-08: Graph Preview**: The Dashboard SHALL display a Relationship Network preview card with a "VIEW GRAPH" button that navigates to the Knowledge Graph page.
- **FR-09: Estimation Drift**: The Dashboard SHALL display an AI Estimation Drift bar chart with a "ANALYSIS DRIFT" button that navigates to the Project Analysis page.
- **FR-10: Neural Console**: The Dashboard SHALL display a real-time activity log (Neural Console) with at least 3 entries showing timestamp, tag (AI_SYNC, KB_WRITE, HEARTBEAT), and message.
- **FR-11: User Dropdown**: WHEN the user clicks the avatar in the navbar, THEN a dropdown SHALL appear with: Account Settings, Security & Permissions, Sign Out. Clicking outside SHALL close the dropdown.
- **FR-12: Sidebar Navigation**: The sidebar SHALL contain 5 navigation items: Dashboard, Relationship Network, Project Analysis, Ticket Intelligence, Integrations. The active page SHALL be highlighted.
- **FR-13: Search Box**: The navbar SHALL contain a search box for quick task lookup.

### 2.3 Knowledge Graph (MH3)

- **FR-14: Hexagonal Nodes**: The graph SHALL display Jira tickets as hexagonal (6-point polygon) nodes with the ticket key as label.
- **FR-15: Node Color Coding**: Nodes SHALL be color-coded by type: Cyan/Primary for Features, Blue/Accent for Dependencies, Violet/Secondary for UI Modules.
- **FR-16: Edge Differentiation**: Explicit Jira link edges SHALL be solid lines. AI-discovered semantic edges SHALL be dashed lines.
- **FR-17: Node Interaction**: WHEN a user hovers over a node, THEN the node SHALL scale to 1.1x with a white stroke. WHEN a user clicks a node, THEN the detail panel SHALL display: Ticket Key, Summary, Description, and "OPEN IN JIRA" button.
- **FR-18: Detail Panel**: A 300px panel on the right side SHALL display selected ticket details with an "OPEN IN JIRA" button.
- **FR-19: Graph Search**: The search box SHALL filter visible nodes by ticket key or summary text.

### 2.4 Project Analysis (MH4)

- **FR-20: Project Metrics**: The page SHALL display 4 stat cards: Total Tickets, Resolution Rate (%), Cycle Time (days), AI Velocity (score).
- **FR-21: Velocity Chart**: A bar chart SHALL display story points across sprints (minimum 7 bars). Odd bars SHALL have a gradient primary color. Hovering a bar SHALL scale it vertically (1.05x).
- **FR-22: Bottleneck Radar**: The AI Bottleneck Radar SHALL display at least 2 alerts: one warning (e.g., blocked tickets) and one positive suggestion (e.g., optimization opportunity).
- **FR-23: Reports Button**: A "DIVE INTO REPORTS" button SHALL be available for detailed analysis navigation.

### 2.5 Ticket Intelligence (MH5)

- **FR-24: Ticket Input**: The page SHALL provide a Ticket ID input field and an "ANALYZE DATA" button.
- **FR-25: KB-First Strategy**: WHEN a user requests analysis, the system SHALL first check the Knowledge Base. IF a cached result exists, THEN return it immediately without calling AI. IF not found, THEN perform AI analysis and save to KB.
- **FR-26: Analysis Progress**: During AI analysis, the system SHALL display a progress bar with 3 stages: "Consolidating Ticket Metadata..." (0-40%), "AI RE-ANALYZING SCOPE..." (40-85%), "SYNCING TO KNOWLEDGE BASE..." (85-100%).
- **FR-27: Three-Tab Display**: Results SHALL be displayed across 3 tabs with fadeIn animation (0.5s) on tab switch:
  - **Context Tab**: Unified requirement summary with colored bullet list of affected modules (Primary, Accent, Secondary colors).
  - **Evolution Tab**: Timeline in neural console format showing requirement changes from Origin through versions to Current state.
  - **Complexity Tab**: Scrum Point score (large gradient text), complexity subtitle, and KB reference badges with similarity percentages.
- **FR-28: Re-Analysis**: A "RE-ANALYZE" button SHALL force a fresh AI analysis, overwriting the existing KB entry. The progress bar SHALL restart.
- **FR-29: Points Scale**: Estimated Scrum Points SHALL only use values from: {0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40}.
- **FR-30: RBAC Restriction**: Users with Reader role SHALL NOT be able to trigger "ANALYZE DATA" or "RE-ANALYZE". These buttons SHALL be visually disabled (opacity 0.5, cursor not-allowed).

### 2.6 Integrations (MH6)

- **FR-31: Provider Cards**: The page SHALL display 7 provider cards in a responsive grid (min 380px columns): Ollama (Local), Google Gemini API, LM Studio, Gemini CLI Interface, Copilot CLI (GitHub), Kiro CLI (Amazon), Embedding Model.
- **FR-32: Card Content**: Each card SHALL display: provider logo, status dot with tooltip, configuration fields, and an action button.
- **FR-33: Status Indicators**: Status dots SHALL use 3 states: Active (green with glow), Standby (blue with glow), Offline (red with glow). Each SHALL have a tooltip with details (latency, error reason, session time).
- **FR-34: Connection Test**: WHEN a user clicks "TEST LINK", THEN the button text SHALL change to "PROBING...", a progress bar SHALL animate from 0% to 100%, and the button SHALL reset after completion.
- **FR-35: Gemini Model Selection**: The Gemini card SHALL include a dropdown with options: Gemini 1.5 Pro, Gemini 1.0 Ultra, Gemini 1.5 Flash.
- **FR-36: Integration RBAC**: Only Administrator role SHALL be able to modify provider configurations. Other roles can view but not edit.

### 2.7 User Management (MH7)

- **FR-38: User List**: The page SHALL display a list of users with: avatar (48px), name, email, and current role in a dropdown selector.
- **FR-39: Role Assignment**: Administrator SHALL be able to change user roles via dropdown. Available roles: Administrator, Neural Architect, Reader.
- **FR-40: Permission Toggles**: A permissions panel SHALL display granular toggles: Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data.
- **FR-41: IAM Sync Feedback**: WHEN a permission is toggled, THEN the sidebar ticker SHALL display "IAM SYNC: UPDATING...", a progress bar SHALL animate, and upon completion display "SYNC COMPLETE".
- **FR-42: Audit Log**: A Neural Console at the bottom SHALL display audit log entries with timestamp, tag (IAM_SYNC, USER_LOGIN), and description of changes.
- **FR-43: Access Restriction**: Only Administrator role SHALL access the User Management page. Access is via the User Profile Dropdown, not the sidebar.

## 3. Non-Functional Requirements (NFR)

### 3.1 Security & Privacy
- **NFR-01: Data Sovereignty**: The system MUST support "Air-gapped" mode using only local AI (Ollama) for sensitive projects.
- **NFR-02: Access Control**: Jira interaction MUST use authenticated REST API with user-specific credentials.
- **NFR-03: Token Security**: API tokens and JWT secrets MUST NOT be stored in client-side code or local storage in plain text.

### 3.2 Performance
- **NFR-04: Graph Rendering**: Graph rendering for up to 100 nodes SHOULD complete under 3 seconds on Chrome.
- **NFR-05: AI Latency**: Local AI analysis SHOULD not exceed 10 seconds per batch of 5 tickets.
- **NFR-06: KB Query Speed**: Knowledge Base lookups by ticket_id SHOULD complete under 200ms.

### 3.3 UI/UX (Obsidian Kinetic)
- **NFR-07: Design System**: All screens MUST use the Obsidian Kinetic design system (Luminous V3) with glassmorphism, deep nebula backgrounds, and neon accents.
- **NFR-08: Typography**: Primary font: Be Vietnam Pro (100/300/400/600/700). Monospace: JetBrains Mono (console elements).
- **NFR-09: Animations**: All glass cards MUST have hover transitions (translateY -5px, scale 1.02). Tab switching MUST use fadeIn animation (0.5s).
- **NFR-10: Tooltips**: All metrics and status indicators MUST have glass-styled tooltips via `[data-tooltip]` attribute.
- **NFR-11: Progress Feedback**: All logic-heavy operations MUST display real-time progress bars and status tickers.

## 4. Navigation Model

### 4.1 Primary Sidebar (5 items)
| Item | Icon | Target |
|------|------|--------|
| Dashboard | House | `frontend/dashboard/` |
| Relationship Network | Magnifier | `frontend/knowledge_graph/` |
| Project Analysis | Chart | `frontend/analysis/` |
| Ticket Intelligence | Sparkle | `frontend/ticket_analysis/` |
| Integrations | Plug | `frontend/integrations/` |

### 4.2 User Profile Dropdown (navbar, top-right)
| Item | Target |
|------|--------|
| Account Settings | `frontend/users/` |
| Security & Permissions | `frontend/users/` |
| Sign Out | `frontend/onboarding/` |

## 5. System Models
- **Node**: A Jira Ticket (Key, Summary, Status, Type).
- **Edge**: Relationship (Type: Explicit/Linked, Semantic/AI-Discovered).
- **Context**: Sliding window of historical tickets passed to AI for analysis.
- **KBRecord**: Cached analysis result (ticket_id, summary, evolution, points, confidence, rationale, references, timestamp).
