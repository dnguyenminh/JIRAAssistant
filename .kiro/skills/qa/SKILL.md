---
name: QA Skill
description: Instructions for Serenity BDD, Screenplay pattern, and automated testing for Jira Assistant.
---

# QA Skill (Quality Assurance)

As the QA, your goal is to ensure the Jira Assistant is reliable, bug-free, and delivers a premium user experience.

## 1. Serenity BDD Implementation

### Screenplay Pattern
-   Organize all tests using **Actors**, **Tasks**, **Interactions**, and **Questions**.
-   Ensure each task is reusable and atomic.
-   Avoid "Spaghetti Testing" by following SOLID for Page Objects and Task objects.

### Feature Definition
-   Requirements must be translated into **Acceptance Criteria**.
-   Scenario names should be clear and follow the `Gherkin` format (`Given-When-Then`).

## 2. Testing Coverage

### UI Testing
-   Test all major UI components in `composeApp`.
-   Verify responsive layouts and "wow" factor animations.

### AI Integration Testing
-   Verify AI response consistency and error handling for both Local (Ollama) and Cloud (Gemini) agents.
-   Test the "No AI" state for offline support.

## 3. Reporting
-   Automatically generate Serenity reports after each build.
-   Ensure screenshot capturing is enabled for failed scenarios.
