---
description: How to implement a new feature in the Jira Assistant
---

1.  **Analysis**:
    -   Define the feature's "Requirement DNA" based on existing Jira Assistant logic.
    -   Identify necessary changes in `shared` (Domain/Data) and `composeApp` (UI).
2.  **Shared Logic**:
    -   Implement the `UseCase` in `shared/src/commonMain/kotlin/.../domain`.
    -   Implement the `Repository` and data sources (Jira API, Local DB).
3.  **UI Implementation**:
    -   Create a new `@Composable` component in `composeApp/src/commonMain/kotlin/.../ui`.
    -   Connect the UI to the Shared UseCase via a `ViewModel` (using Koin).
4.  **Verification**:
    -   Write a Serenity BDD test for the feature.
    -   Run `gradle e2e-tests:test` to verify compliance.
