# Technical Design Document - Jira Assistant

**Project Name**: Jira Assistant (AI-Powered)  
**Version**: 1.0.0  
**Owner**: Architecture Team  
**Status**: Formalized  

## 1. System Overview
The Jira Assistant is built as a **Kotlin Multiplatform (KMP)** project to target the Web (Wasm/JS) while sharing all business logic, AI analysis, and data fetching between potential future platforms (iOS/Android/Desktop).

## 2. Component Architecture
The system follows a **Clean Architecture** approach with three primary modules:

### 2.1 shared Module (Core Business)
-   **AI Layer**: `AIAgent` interface with:
  - `OllamaAgent`: Local SLM (Llama3/Phi3).
  - `GeminiAgent`: Cloud API (1.5 Pro).
  - `LMStudioAgent`: OpenAI-Compatible local bridge.
  - `CliAgent`: External CLI wrapper for Gemini/Custom models.
  - **Orchestrator**: `AIAgentFactory` for dynamic provider switching and connection validation.
-   **Jira Layer**: `JiraClient` interface with `JiraRestClient` (Ktor) implementation.
-   **Domain Layer**: `FeatureNetworkMapper` and `ScrumEstimator` orchestrate AI and Jira services.
-   **DI Layer**: Koin modules (`aiModule`, `jiraModule`, `domainModule`).

### 2.2 composeApp Module (UI)
-   **Technology**: Compose Multiplatform (HTML/Wasm).
-   **Visual Components**: `TicketGraph` (Canvas-based), `EstimationView` (Material 3).
-   **Glassmorphism Design**: Use high-transparency (0.7-0.8) backgrounds with backdrop-blur (10px-20px) for all dashboard panels.
-   **State Management**: `AppViewState` (Sealed class) via `remember` and `LaunchedEffect`.

### 2.3 e2e-tests Module (QA)
-   **Technology**: Serenity BDD, Screenplay pattern.
-   **Target**: Automated user interaction testing against the Web app.

## 3. Data Flow & Sequence (Advanced Analysis)
1.  **Input**: User enters a `Ticket ID` in the Analysis View.
2.  **KB Lookup**: `KnowledgeBaseRepository` checks for existing analysis. If found, skip to step 6.
3.  **Jira Ingestion (Fallback)**: `JiraClient` fetches full ticket data (Summary, Description, Sub-tickets, Attachment metadata).
4.  **AI Orchestration**: `AnalysisOrchestrator` sends context to `AIAgent` (Local/Cloud).
5.  **Requirement Synthesis**: `AIAgent` produces:
    -   **Summary**: Consolidated view of all ticket information.
    -   **Evolution**: History of requirement changes across related tickets.
    -   **Complexity**: Scrum Points + references from the KB.
6.  **Persistence**: `AnalysisOrchestrator` saves/updates the record in the `KnowledgeBase`.
7.  **Visualization**: `AnalysisView` renders the 3-tab layout (Context, Evolution, Complexity).

## 4. Technology Stack
-   **Kotlin**: 2.0.0 (K2 compiler enabled).
-   **Compose Multiplatform**: 1.6.11.
-   **Ktor**: for HTTP/REST communication.
-   **Koin**: for Dependency Injection.
-   **Kotlinx Serialization**: for JSON parsing of AI and Jira responses.
-   **Serenity BDD**: for E2E testing with the Screenplay pattern.

## 5. Security & Identity Design

### 5.1 Identity & Access Management (IAM)
- **Authentication**: Secure handshake via OAuth2/Session tokens for both Jira and local neural clusters.
- **RBAC Engine**: `UserIdentityService` manages roles (Admin, Architect, Reader) and permissions.
- **Audit Logs**: All sensitive operations are logged via the `NeuralConsole`.

### 5.2 Secret Management
- **API Keys**: Sensitive keys (Gemini) are passed via environment variables during the build.
- **Local Privacy**: Support for on-premise Ollama instances to ensure zero data leakage.
