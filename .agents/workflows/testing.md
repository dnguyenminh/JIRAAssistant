---
description: How to perform E2E testing with Serenity BDD in the Jira Assistant
---

1.  **Define the Feature**:
    -   In `e2e-tests/src/test/resources/features/`, create a new `.feature` file.
    -   Use the `[Requirement ID] - [Description]` format.
2.  **Implementation Scenario**:
    -   Implement the step definitions in `e2e-tests/src/test/kotlin/.../steps/`.
    -   Use the **Screenplay Pattern** by creating `Tasks`, `Interactions`, and `Questions`.
3.  **Run Tests**:
    -   Use `./gradlew e2e-tests:test` to execute the tests.
    -   Use `./gradlew e2e-tests:aggregate` to generate the HTML report.
4.  **Review Results**:
    -   Open `e2e-tests/target/site/serenity/index.html` to see the detailed report.
    -   Verify that all scenarios passed for the given feature.
