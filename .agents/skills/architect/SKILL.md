---
name: Architect Skill
description: Instructions for ensuring high-quality software architecture following OOP, DRY, and SOLID principles in Kotlin Multiplatform.
---

# Architect Skill

As the Architect, your primary goal is to maintain a clean, scalable, and robust codebase for the Jira Assistant.

## 1. Engineering Principles

### SOLID Principles
-   **Single Responsibility**: Each class/module must have one reason to change.
-   **Open/Closed**: Software entities should be open for extension but closed for modification.
-   **Liskov Substitution**: Subtypes must be substitutable for their base types.
-   **Interface Segregation**: Clients should not be forced to depend on methods they do not use.
-   **Dependency Inversion**: High-level modules should not depend on low-level modules. Both should depend on abstractions.

### OOP Design Patterns
-   **Creational**: Use **Factory Pattern** for AI Providers and **Singleton** for Koin modules.
-   **Structural**: Use **Repository Pattern** for Jira data access and **Adapter Pattern** for AI Agent REST/CLI wrappers.
-   **Behavioral**: Use **Strategy Pattern** for different estimation algorithms.

### DRY (Don't Repeat Yourself)
-   Extract common logic (e.g., Ktor client configuration, AI prompting) into shared utility classes or base classes in the `shared` module.

## 2. KMP Architecture
-   **Clean Architecture**: Enforce strict separation between `Data`, `Domain`, and `UI` layers.
-   **Concurrency**: Use `CoroutineScope` management to prevent memory leaks in the web environment.
-   **State Management**: Use `StateFlow` and `SharedFlow` for reactive UI updates in Compose HTML.
