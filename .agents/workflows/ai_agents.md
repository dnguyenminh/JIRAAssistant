---
description: How to add a new AI Provider to the Jira Assistant
---

1.  **Define the Interface**:
    -   In `shared/src/commonMain/kotlin/.../domain/AIAgent.kt`, add any new capabilities required.
2.  **Implementation Implementation**:
    -   Create a new implementation class in `shared/src/commonMain/kotlin/.../data/ai/`.
    -   Implement fetching logic for REST APIs (Ktor) or CLI (via `Process`).
3.  **Dependency Injection**:
    -   Update `AIModule.kt` to include the new provider.
    -   Ensure it can be selected by the `AIAgentFactory`.
4.  **Verification**:
    -   Verify connection status and availability.
    -   Test a basic prompt through the `AI Assistant` console.
