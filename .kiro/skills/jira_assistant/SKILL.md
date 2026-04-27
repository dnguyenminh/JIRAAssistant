---
name: Jira Assistant Skill
description: Specialized instructions for the Jira Assistant project, covering AI-driven ticket analysis, knowledge base management, and Serenity BDD testing.
---

# Jira Assistant Skill

This skill provides the instructions and context needed to build and maintain the Jira Assistant application.

## 1. Domain Knowledge: Jira Ticket Analysis

### Ticket Clustering & DNA Mapping
-   When analyzing tickets, look for:
    -   **Feature DNA**: Recurring terms, shared components, and overlapping logic.
    -   **Historical Solutions**: How similar problems were solved in previous sprints (check resolution descriptions).
    -   **Dependencies**: Explicit links (`is blocked by`, `relates to`) and implicit semantic links.

### Solution Lineage
-   Track how a requirement evolves. If a ticket is a follow-up or a bug fix for a previous feature, link them in the Knowledge Graph.

## 2. Technical Instructions: Kotlin Multiplatform (KMP)

### Workspace Structure
-   `composeApp`: Web-specific UI components.
-   `shared`: Business logic, Jira API clients, and AI Agent abstractions.
-   `server` (Optional): Backend for handling complex RAG indexing or securing API keys.

### Design System
-   Follow the **Antigravity Premium Design** principles:
    -   Vibrant colors (not browser defaults).
    -   Glassmorphism and smooth animations for the "Feature Network" visualization.
    -   Modern typography (Inter/Roboto).

## 3. Workflow Implementation

### Adding a New Feature
1.  Check the `features.md` workflow for step-by-step instructions.
2.  Ensure both the `shared` logic and `composeApp` UI are updated.

### Testing with Serenity BDD
1.  Follow the **Screenplay Pattern**.
2.  Use `gradle e2e-tests:aggregate` to verify the "wow" factor of the documentation/reports.

## 4. AI Agent Orchestration

### Connectivity Rules
-   **Local**: Use `localhost:11434/api/generate` for Ollama.
-   **Cloud**: Use Gemini via the standard API (ensure API keys are handled securely via `.env` or properties).
-   **Context Management**: Summarize long ticket histories before sending them to the LLM to fit within context windows and reduce latency.

## 5. Estimation Logic
-   Always provide estimations in the **Scrum scale**: `0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40`.
## 6. Investor-Ready A-Z Master Hook (Mandatory)
This project is currently optimized for high-fidelity investor demonstrations. Every update must adhere to the following "A-Z Demo Sync" rules:
- **Collaboration Sync**: BA, QA, Frontend-Designer, Architect, and Technical Writer MUST work in unison. Update requirements, UI, and documentation simultaneously.
- **Backend Transparency**: Any task that would normally "take time" in a real system (AI analysis, KB indexing, Jira fetching) MUST have a **Progress Bar** (Neural Loader) and a **Real-time Status Ticker** (Neural Console) to show the work being performed.
- **Fidelity Standards**:
  - **Premium Aesthetics**: Use the 'Obsidian Kinetic' system (Luminous V3). Never use browser defaults.
  - **End-to-End Connectivity**: Ensure the user can navigate from the Onboarding screen through to the detailed Ticket Intelligence view without broken links.
  - **Documentation Fidelity**: All guides (User Guide, SRS) must exactly match the visual and logical state of the UI screenshots/mockups.
