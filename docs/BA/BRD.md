# Business Requirements Document (BRD) - Jira Assistant

**Project Name**: Jira Assistant (AI-Powered)  
**Version**: 2.0.0  
**Owner**: Business Analysis Team  
**Status**: Updated — Full Screen-by-Screen Specification  

## 1. Executive Summary
The Jira Assistant is an AI-driven platform designed to optimize Agile delivery by providing deep insights into Jira ticket history, feature relationships, and predictive estimation. By leveraging both local and cloud-based Large Language Models (LLMs), the system bridges the gap between raw ticket data and actionable engineering intelligence.

## 2. Business Objectives
-   **Privacy-First AI**: Enable secure on-premise ticket analysis using local SLMs (Small Language Models) like Ollama to comply with strict corporate data policies.
-   **Enhanced Predictability**: Improve Scrum estimation accuracy by 25% through AI-driven historical comparison ("Feature DNA").
-   **Reducing Knowledge Silos**: Visualize the "invisible" network of functional dependencies through semantic relationship mapping.
-   **Agile Velocity**: Accelerate Sprint planning by automating the discovery of similar past solutions.

## 3. Target Audience & Personas

| Persona | Role | Primary Needs | Access Level |
|---------|------|--------------|-------------|
| **Sarah Jenkins** | Administrator | Setup system, manage AI providers, assign roles | Full access |
| **Alex Rivera** | Neural Architect (Scrum Master) | Analyze tickets, estimate points, review velocity | Analysis + KB Write |
| **Marcus Chen** | Reader (Product Owner) | View dashboards, review graphs, track dependencies | Read-only |

## 4. System Screens & Business Requirements

### 4.1 Onboarding — Authentication & Connection (MH1)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-AUTH-01 | Users authenticate via Jira Domain URL + API Token | High |
| BR-AUTH-02 | System validates credentials by calling Jira REST API | High |
| BR-AUTH-03 | Real-time connection progress with 3 stages (Handshake, Validation, Metadata Fetch) | High |
| BR-AUTH-04 | After authentication, display project list for selection | High |
| BR-AUTH-05 | Invalid credentials show error message and allow retry | High |
| BR-AUTH-06 | Selected project determines scope for all subsequent screens | High |

**User Flow:**
1. User opens Onboarding page
2. Enters Jira Domain + API Token
3. Clicks "ESTABLISH CONNECTION"
4. Watches progress: Handshaking (0-30%) → Validating (30-70%) → Fetching (70-100%)
5. Selects project from grid → Redirected to Dashboard

---

### 4.2 Dashboard — Project Overview (MH2)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-DASH-01 | Display 3 hero metrics: AI Health (%), Knowledge Nodes (count), Neural Velocity (score) | High |
| BR-DASH-02 | Relationship Network preview card with "VIEW GRAPH" navigation | High |
| BR-DASH-03 | AI Estimation Drift chart with "ANALYSIS DRIFT" navigation | High |
| BR-DASH-04 | Neural Console showing real-time system activity logs | High |
| BR-DASH-05 | User profile dropdown (avatar click) with Account Settings, Security, Sign Out | High |
| BR-DASH-06 | Sidebar navigation to all 5 core modules | High |
| BR-DASH-07 | Search box for quick task lookup | Medium |

**User Flow:**
1. Dashboard loads automatically after project selection
2. User reviews 3 key metrics at a glance
3. Neural Console shows latest AI sync, KB write, and heartbeat events
4. User navigates to deeper views via cards or sidebar

---

### 4.3 Knowledge Graph — Relationship Network (MH3)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-GRAPH-01 | Display ticket network as hexagonal nodes with color-coded types | High |
| BR-GRAPH-02 | Node colors: Cyan (Feature), Blue (Dependency), Violet (UI Module) | High |
| BR-GRAPH-03 | Edge types: Solid lines (explicit Jira links), Dashed lines (AI-discovered semantic) | High |
| BR-GRAPH-04 | Click node → Detail panel shows Ticket Key, Summary, Description, "OPEN IN JIRA" | High |
| BR-GRAPH-05 | Hover node → Scale 1.1x with white stroke highlight | Medium |
| BR-GRAPH-06 | Search box to filter tickets within graph | Medium |
| BR-GRAPH-07 | Sidebar status shows graph sync progress | Low |

**User Flow:**
1. User navigates from Dashboard ("VIEW GRAPH") or sidebar
2. Observes hexagonal nodes representing tickets
3. Identifies clusters by color grouping
4. Distinguishes explicit vs semantic relationships by line style
5. Clicks node to read details → Opens in Jira if needed

---

### 4.4 Project Analysis — Sprint Intelligence (MH4)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-ANALYSIS-01 | Display 4 metrics: Total Tickets, Resolution Rate, Cycle Time, AI Velocity | High |
| BR-ANALYSIS-02 | Velocity Trend bar chart showing story points across sprints | High |
| BR-ANALYSIS-03 | AI Bottleneck Radar with warning alerts and optimization suggestions | High |
| BR-ANALYSIS-04 | "DIVE INTO REPORTS" button for detailed analysis | Medium |
| BR-ANALYSIS-05 | Bar chart hover interaction (scale + opacity change) | Medium |

**User Flow:**
1. User navigates from Dashboard ("ANALYSIS DRIFT") or sidebar
2. Reviews 4 key project metrics
3. Analyzes velocity trend across sprints
4. Reads AI-generated bottleneck warnings and optimization opportunities

---

### 4.5 Ticket Intelligence — AI Analysis (MH5)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-TICKET-01 | Input field for Ticket ID + "ANALYZE DATA" button | High |
| BR-TICKET-02 | KB-First strategy: check Knowledge Base before calling AI | High |
| BR-TICKET-03 | 3-tab result display: Context, Evolution, Complexity | High |
| BR-TICKET-04 | Tab Context: Unified requirement summary with affected modules list | High |
| BR-TICKET-05 | Tab Evolution: Timeline of requirement changes (Origin → Current) | High |
| BR-TICKET-06 | Tab Complexity: Scrum Point score + confidence + KB reference badges | High |
| BR-TICKET-07 | "RE-ANALYZE" button to force fresh AI analysis and overwrite KB | High |
| BR-TICKET-08 | Progress bar with 3 stages during analysis | High |
| BR-TICKET-09 | Scrum Points restricted to scale: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40 | High |
| BR-TICKET-10 | Reader role cannot trigger Analyze or Re-Analyze (buttons disabled) | High |

**User Flow (Scrum Master):**
1. Enter Ticket ID (e.g., NET-458) → Click "ANALYZE DATA"
2. Watch progress: Metadata (0-40%) → AI Analysis (40-85%) → KB Sync (85-100%)
3. Read Context tab: summary + affected modules
4. Switch to Evolution tab: requirement change timeline
5. Switch to Complexity tab: Scrum Point "8" + KB references
6. Optionally click "RE-ANALYZE" to refresh

**User Flow (Reader — restricted):**
1. Can view cached results from KB
2. "ANALYZE DATA" and "RE-ANALYZE" buttons are disabled

---

### 4.6 Integrations — AI Provider Management (MH6)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-INTEG-01 | Display 5 provider cards: Jira Cloud, Ollama, Gemini API, LM Studio, Gemini CLI | High |
| BR-INTEG-02 | Each card shows: logo, status dot, config fields, action button | High |
| BR-INTEG-03 | Status indicators: Active (green), Standby (blue), Offline (red) with tooltips | High |
| BR-INTEG-04 | "TEST LINK" button with progress bar animation | High |
| BR-INTEG-05 | Jira card "RE-CONNECT" redirects to Onboarding | High |
| BR-INTEG-06 | Gemini card has model tier dropdown (1.5 Pro, 1.0 Ultra, 1.5 Flash) | High |
| BR-INTEG-07 | Only Administrator can modify provider configurations | High |

**User Flow (Administrator):**
1. Navigate to Integrations via sidebar
2. Review 5 provider cards and their connection status
3. Click "TEST LINK" on Ollama → Watch progress → Confirm active
4. Configure Gemini API Key + select model → Click "RE-AUTHENTICATE"
5. Note: LM Studio shows Offline — troubleshoot or skip

---

### 4.7 User Management — RBAC & Permissions (MH7)

| ID | Requirement | Priority |
|:---|:-----------|:---------|
| BR-USER-01 | Display user list with avatar, name, email, current role | High |
| BR-USER-02 | Administrator can change user roles via dropdown | High |
| BR-USER-03 | Granular permission toggles: AI Scan, KB Write, Update Integrations, Export Data | High |
| BR-USER-04 | Permission changes trigger "IAM SYNC" progress with audit log entry | High |
| BR-USER-05 | Audit log in Neural Console shows all permission/role changes | High |
| BR-USER-06 | Only Administrator can access User Management | High |
| BR-USER-07 | Access via User Profile Dropdown (not sidebar) to keep navigation clean | High |

**User Flow (Administrator):**
1. Click avatar → "Account Settings" → User Management page
2. View 3 users with their current roles
3. Change Alex's role or toggle specific permissions
4. Watch "IAM SYNC: UPDATING..." progress
5. Verify change in Audit Log at bottom

---

## 5. Cross-Screen Navigation Map

```
Onboarding (MH1)
    |-- [Select Project] --> Dashboard (MH2)
                                |-- [VIEW GRAPH] --> Knowledge Graph (MH3)
                                |-- [ANALYSIS DRIFT] --> Project Analysis (MH4)
                                |-- [Sidebar: Ticket Intelligence] --> Ticket Intelligence (MH5)
                                |-- [Sidebar: Integrations] --> Integrations (MH6)
                                |-- [Avatar > Account Settings] --> User Management (MH7)
                                |-- [Avatar > Sign Out] --> Onboarding (MH1)
```

## 6. RBAC Permission Matrix

| Feature | Administrator | Neural Architect | Reader |
|---------|:---:|:---:|:---:|
| View Dashboard | Yes | Yes | Yes |
| View Knowledge Graph | Yes | Yes | Yes |
| View Project Analysis | Yes | Yes | Yes |
| Analyze Ticket (AI) | Yes | Yes | No |
| View KB Cached Results | Yes | Yes | Yes |
| Re-Analyze (Force AI) | Yes | Yes | No |
| Configure Integrations | Yes | No | No |
| Test Provider Connection | Yes | Yes | No |
| Manage Users & Roles | Yes | No | No |
| Toggle Permissions | Yes | No | No |
| Sign Out | Yes | Yes | Yes |


## 7. User Journeys (End-to-End)

### Journey 1: Administrator — First-Time System Setup

**Persona:** Sarah Jenkins (Administrator)

1. **Onboarding (MH1):** Enter Jira Domain + API Token → Watch 3-stage connection → Select project "JRA"
2. **Dashboard (MH2):** Verify 3 metrics load correctly, Neural Console shows activity
3. **Integrations (MH6):** Test Ollama connection → Configure Gemini API Key → Verify status dots
4. **User Management (MH7):** Review 3 users → Assign roles → Toggle permissions → Check Audit Log
5. **Result:** System fully configured and ready for team use

### Journey 2: Scrum Master — Sprint Planning with AI

**Persona:** Alex Rivera (Neural Architect)

1. **Onboarding (MH1):** Authenticate → Select project
2. **Dashboard (MH2):** Review AI Health + Knowledge Nodes
3. **Project Analysis (MH4):** Check Velocity Trend + Bottleneck Radar
4. **Ticket Intelligence (MH5):** Analyze NET-458 → Read 3 tabs (Context, Evolution, Complexity) → Note Scrum Point "8"
5. **Ticket Intelligence (MH5):** Analyze DB-123 → KB-First returns cached result instantly
6. **Knowledge Graph (MH3):** View ticket relationships → Click nodes → Identify dependencies
7. **Result:** Sprint planned with AI-backed estimations and dependency awareness

### Journey 3: Product Owner — Roadmap Review (Read-Only)

**Persona:** Marcus Chen (Reader)

1. **Onboarding (MH1):** Authenticate → Select project
2. **Dashboard (MH2):** Review metrics overview
3. **Knowledge Graph (MH3):** Identify feature clusters and dependencies by color/edges
4. **Project Analysis (MH4):** Review Resolution Rate + Cycle Time + Bottleneck alerts
5. **Ticket Intelligence (MH5):** View cached analysis results (cannot trigger new analysis)
6. **Integrations (MH6):** View provider status (cannot modify)
7. **Result:** Roadmap priorities informed by data without modifying system state

## 8. Success Metrics
-   User adoption rate among Scrum teams (target: 80% within 3 months)
-   Reduction in estimation variance: Actual vs. Estimated (target: 25% improvement)
-   Time saved in "Discovery" phase of new features (target: 40% reduction)
-   KB cache hit rate (target: 60% of analysis requests served from KB)
-   System uptime with AI failover (target: 99.5%)
