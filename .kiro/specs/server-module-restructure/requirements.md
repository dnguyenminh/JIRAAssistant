# Requirements Document — Server Module Restructure

## Introduction

The Jira Assistant server currently consolidates all backend code into a single `server` Gradle module containing 15+ packages (routes, db, agent, mcp, chat, document, analysis, attachment, indexing, jobs, auth, config, di, middleware, ai). As the codebase has grown to support 10 distinct feature domains, this monolithic structure hinders independent development, slows incremental builds, and makes dependency boundaries implicit rather than enforced.

This restructuring splits the `server` module into multiple Gradle sub-modules under `server/`, each aligned with one of the 10 feature groups defined in `.kiro/specs/`. The goal is to enforce dependency boundaries at the build level, enable faster incremental compilation, and improve code navigability — while preserving the existing fat JAR deployment, test infrastructure, and build commands.

## Glossary

- **Server_Module**: The current single Gradle module at `server/` containing all backend source code
- **Sub_Module**: A new Gradle sub-module under `server/` (e.g., `server/core`, `server/chat`) with its own `build.gradle.kts`
- **Feature_Group**: One of the 10 domain areas defined in `.kiro/specs/01-*.md` through `.kiro/specs/10-*.md`
- **Fat_JAR**: A single JAR file containing all compiled classes and dependencies, used for Docker deployment
- **KMP_Configuration**: Kotlin Multiplatform `kotlin { jvm { } }` build configuration used by the project
- **Shared_Module**: The existing `:shared` module providing cross-platform domain models and interfaces
- **Koin_Module**: A Koin DI module definition (`org.koin.core.module.Module`) that wires service implementations
- **Build_Command**: Existing Gradle commands such as `./gradlew :server:jvmRun`, `./gradlew :server:jvmTest`, `./gradlew :server:fatJar`
- **Dependency_Rule**: A constraint specifying which Sub_Modules may depend on which other Sub_Modules
- **Aggregator_Module**: The top-level `server/` module that depends on all Sub_Modules and assembles the Fat_JAR
- **Package_Mapping**: The assignment of existing server packages to their target Sub_Modules

## Requirements

### Requirement 1: Sub-Module Definitions

**User Story:** As a developer, I want the server code organized into sub-modules aligned with feature groups, so that I can work on one domain without navigating unrelated code and benefit from faster incremental builds.

#### Acceptance Criteria

1. THE Build_System SHALL define the following 10 Sub_Modules under `server/`:
   - `server/core` — Core platform: config, auth, RBAC middleware, database infrastructure (db, db/pg), DI bootstrap, Application entry point, health routes
   - `server/dashboard` — Dashboard & Project: scan engine integration, project routes, scan routes, estimation routes, graph routes
   - `server/analysis` — Ticket Intelligence: analysis package (map-reduce), deep analysis, attachment processing, indexing pipeline, ticket detail routes
   - `server/docgen` — Document Generation: document package, jobs package, document routes, job routes, collection job routes
   - `server/agent` — AI Agent Framework: agent package (ba, engine, orchestrator, subprocess, tool, registry, etc.), ai package (CLI agents)
   - `server/chat` — AI Chat: chat package, chat routes (send, history, config, actions, upload, tool permissions)
   - `server/mcp` — MCP & Integrations: mcp package (process manager, protocol client, internal tools), integration routes, MCP routes
   - `server/knowledge-graph` — Knowledge Graph: graph routes, graph-specific handlers from MCP internal
   - `server/user-mgmt` — User Management: user routes, audit log store
   - `server/testing-support` — Testing: shared test utilities, test doubles, test fixtures used across Sub_Modules

2. THE Build_System SHALL retain the top-level `server/` module as the Aggregator_Module that depends on all Sub_Modules

3. WHEN a Sub_Module is compiled independently, THE Build_System SHALL compile only that Sub_Module and its transitive dependencies

4. THE Aggregator_Module SHALL contain the `Application.kt` entry point, the `configureRouting()` function, the root Koin module assembly (`ServerModule.kt`), and cross-cutting aggregator bindings (`AggregatorBindings.kt`, `AggregatorHelpers.kt`, `BatchScanEngineFactory.kt`) that wire together dependencies spanning multiple Sub_Modules (e.g., Jira client, AI orchestrator, BatchScanEngine, GraphEngine)

### Requirement 2: KMP Build Configuration

**User Story:** As a developer, I want each sub-module to use the same Kotlin Multiplatform JVM configuration as the current server module, so that the build system remains consistent.

#### Acceptance Criteria

1. WHEN a Sub_Module `build.gradle.kts` is created, THE Build_System SHALL use the `kotlin { jvm { } }` KMP_Configuration with `jvmMain` and `jvmTest` source sets

2. THE Build_System SHALL apply `alias(libs.plugins.kotlinMultiplatform)` and `alias(libs.plugins.kotlinSerialization)` plugins to each Sub_Module

3. WHEN a Sub_Module declares dependencies, THE Build_System SHALL use the version catalog (`libs.*`) for all third-party dependencies

4. THE Build_System SHALL NOT introduce any non-KMP Gradle plugins (such as `java-library` or `application`) to Sub_Modules

### Requirement 3: Dependency Rules Between Sub-Modules

**User Story:** As an architect, I want enforced dependency rules between sub-modules, so that feature domains do not develop unintended coupling.

#### Acceptance Criteria

1. THE Build_System SHALL enforce the following Dependency_Rules:
   - `server/core` depends on `:shared` only (no other Sub_Modules)
   - `server/dashboard` depends on `server/core` and `:shared`
   - `server/analysis` depends on `server/core` and `:shared`
   - `server/docgen` depends on `server/core`, `server/analysis`, `server/agent`, and `:shared`
   - `server/agent` depends on `server/core` and `:shared`
   - `server/chat` depends on `server/core`, `server/mcp`, and `:shared`
   - `server/mcp` depends on `server/core` and `:shared`
   - `server/knowledge-graph` depends on `server/core` and `:shared`
   - `server/user-mgmt` depends on `server/core` and `:shared`
   - `server/testing-support` depends on `server/core` and `:shared`

2. IF a Sub_Module attempts to depend on a Sub_Module not listed in its Dependency_Rules, THEN THE Build_System SHALL fail compilation with a dependency violation error

3. THE Aggregator_Module SHALL depend on all Sub_Modules to assemble the complete application

4. THE Build_System SHALL NOT allow circular dependencies between Sub_Modules

### Requirement 4: Package Mapping

**User Story:** As a developer, I want a clear mapping from existing packages to sub-modules, so that I know where each class belongs after restructuring.

#### Acceptance Criteria

1. THE Package_Mapping SHALL assign existing server packages as follows:
   - `server/core`: `config`, `auth`, `db`, `db/pg`, `di` (ServerModule, PostgresModule), `middleware`, `routes/AuthRoutes`, `routes/HealthRoutes`, `routes/Routing`, `routes/SettingsRoutes`, `routes/ProjectRoutes`
   - `server/dashboard`: `routes/ScanRoutes`, `routes/EstimationRoutes`, `routes/GraphRoutes`
   - `server/analysis`: `analysis`, `attachment`, `indexing`, `routes/AnalysisRoutes`, `routes/AttachmentRoutes`, `routes/TicketDetailRoutes`, `routes/CascadeRoutes`, `routes/CascadeStatusTracker`
   - `server/docgen`: `document`, `jobs`, `routes/DocumentRoutes`, `routes/DocumentRouteHandlers`, `routes/JobRoutes`, `routes/CollectionJobRoutes`
   - `server/agent`: `agent` (all sub-packages), `ai` (CLI agents)
   - `server/chat`: `chat`, `routes/ChatRoutes`, `routes/ChatActionRoutes`, `routes/ChatConfigRoutes`, `routes/ChatHistoryRoutes`, `routes/ChatToolPermissionRoutes`, `routes/ChatUploadRoutes`
   - `server/mcp`: `mcp` (all sub-packages including `internal`), `routes/IntegrationRoutes`, `routes/McpRoutes`, `routes/McpHealthRoutes`, `routes/McpRuntimeRoutes`, `routes/McpRuntimeHandlers`, `routes/McpToolsHandlers`
   - `server/knowledge-graph`: graph-specific route handlers (extracted from `routes/GraphRoutes` if needed)
   - `server/user-mgmt`: `routes/UserRoutes`

2. WHEN a class is moved to a Sub_Module, THE Build_System SHALL preserve the original package name (e.g., `com.assistant.server.chat.ChatServiceImpl` remains unchanged)

3. IF a class is referenced by multiple Sub_Modules, THEN THE class SHALL be placed in the lowest common dependency (preferring `server/core` or `:shared`)

### Requirement 5: Koin DI Module Organization

**User Story:** As a developer, I want each sub-module to define its own Koin module, so that DI wiring is co-located with the code it configures.

#### Acceptance Criteria

1. WHEN a Sub_Module is created, THE Sub_Module SHALL define its own Koin_Module in a `di/` package within that Sub_Module

2. THE Aggregator_Module SHALL compose all Sub_Module Koin_Modules into the root `serverModule()` using `includes()`

3. WHEN a Koin_Module in a Sub_Module needs a dependency from another Sub_Module, THE Koin_Module SHALL declare it via `get()` (Koin resolution) rather than direct class reference across module boundaries

4. THE existing `serverModule(config)` function in the Aggregator_Module SHALL delegate to Sub_Module Koin_Modules for domain-specific bindings and include an `aggregatorBindingsModule` for cross-cutting bindings (Jira client, AI orchestrator, BatchScanEngine, GraphEngine) that span multiple Sub_Modules

### Requirement 6: Fat JAR Deployment Preservation

**User Story:** As a DevOps engineer, I want the fat JAR build to continue working after restructuring, so that Docker deployment is unaffected.

#### Acceptance Criteria

1. WHEN `./gradlew :server:fatJar` is executed, THE Build_System SHALL produce a single JAR file at `server/build/libs/jira-assistant-server-all.jar` containing classes from all Sub_Modules

2. THE Fat_JAR SHALL include the `Main-Class` manifest attribute pointing to `com.assistant.server.ApplicationKt`

3. WHEN the Fat_JAR is executed with `java -jar app.jar`, THE Application SHALL start and serve all API endpoints identically to the pre-restructure behavior

4. THE Dockerfile SHALL require no modifications to build and run the application after restructuring

### Requirement 7: Test Organization

**User Story:** As a developer, I want tests to live alongside their sub-module source code, so that I can run tests for a specific domain independently.

#### Acceptance Criteria

1. WHEN a Sub_Module is created, THE Sub_Module SHALL contain its own `jvmTest` source set with tests relevant to that domain

2. WHEN `./gradlew :server:analysis:jvmTest` is executed, THE Build_System SHALL run only tests belonging to the `server/analysis` Sub_Module

3. THE Aggregator_Module SHALL define a `jvmTestAll` task that runs tests across all Sub_Modules

4. WHEN a test requires test doubles from another Sub_Module, THE test SHALL depend on the `server/testing-support` Sub_Module

5. THE existing test grouping strategy SHALL be preserved: parallel tests (default `jvmTest` excluding `@Tag("sequential")`) and sequential tests (`jvmTestSequential` including `@Tag("sequential")`)

6. WHEN `./gradlew :server:jvmTest` is executed on the Aggregator_Module, THE Build_System SHALL run all parallel tests across all Sub_Modules (backward compatibility)

### Requirement 8: Incremental Migration Strategy

**User Story:** As a team lead, I want the migration to happen incrementally, so that the codebase remains functional at every step and we can merge partial progress.

#### Acceptance Criteria

1. THE Migration SHALL follow this sequence:
   - Phase 1: Create `server/core` Sub_Module, move foundational code (config, auth, db, middleware)
   - Phase 2: Create remaining Sub_Modules one at a time, moving packages per the Package_Mapping
   - Phase 3: Slim down the Aggregator_Module to contain only Application.kt, routing assembly, and root DI
   - Phase 4: Verify Fat_JAR, all tests, and Docker deployment

2. WHEN a Phase is completed, THE Build_System SHALL compile successfully with `./gradlew build`

3. WHEN a Phase is completed, THE existing test suite SHALL pass with the same pass/fail results as before the Phase

4. IF a migration Phase introduces a compilation error, THEN THE developer SHALL resolve the error before proceeding to the next Phase

### Requirement 9: Settings.gradle.kts Updates

**User Story:** As a developer, I want all sub-modules registered in settings.gradle.kts, so that Gradle recognizes them as part of the build.

#### Acceptance Criteria

1. WHEN a Sub_Module is created, THE `settings.gradle.kts` SHALL include the Sub_Module using the pattern `include(":server:core")`, `include(":server:chat")`, etc.

2. THE existing module includes (`:shared`, `:server`, `:frontend`, `:e2e-tests`) SHALL remain unchanged in `settings.gradle.kts`

3. WHEN all Sub_Modules are registered, THE `settings.gradle.kts` SHALL contain includes for all 10 Sub_Modules plus the existing 4 modules

### Requirement 10: Shared Interface Extraction

**User Story:** As an architect, I want cross-cutting interfaces used by multiple sub-modules to live in `server/core` or `:shared`, so that sub-modules depend on abstractions rather than concrete implementations from other sub-modules.

#### Acceptance Criteria

1. WHEN a service interface (e.g., `AIOrchestrator`, `KBRepository`, `McpProcessManager`) is used by multiple Sub_Modules, THE interface SHALL reside in `:shared` or `server/core`

2. WHEN a Sub_Module needs a concrete implementation from another Sub_Module, THE Sub_Module SHALL depend on the interface from `server/core` or `:shared` and receive the implementation via Koin DI

3. THE existing interfaces in `:shared` (e.g., `com.assistant.ai.AIOrchestrator`, `com.assistant.kb.KBRepository`, `com.assistant.mcp.McpProcessManager`, `com.assistant.chat.ChatService`) SHALL remain in `:shared` unchanged

4. IF a new cross-cutting interface is needed during restructuring, THEN THE interface SHALL be placed in `server/core` with implementations in the appropriate Sub_Module

### Requirement 11: Build Command Backward Compatibility

**User Story:** As a developer, I want existing build commands to continue working, so that CI/CD pipelines and developer workflows are not disrupted.

#### Acceptance Criteria

1. WHEN `./gradlew :server:jvmRun` is executed, THE Application SHALL start with all features functional

2. WHEN `./gradlew :server:fatJar` is executed, THE Build_System SHALL produce the deployable Fat_JAR

3. WHEN `./gradlew :server:jvmTest` is executed, THE Build_System SHALL run all parallel server tests

4. WHEN `./gradlew :server:jvmTestSequential` is executed, THE Build_System SHALL run all sequential server tests

5. WHEN `./gradlew :server:jvmTestAll` is executed, THE Build_System SHALL run parallel tests first, then sequential tests

6. WHEN `./gradlew :server:compileKotlinJvm` is executed, THE Build_System SHALL compile all Sub_Modules

7. WHEN `./gradlew build` is executed, THE Build_System SHALL compile and test all modules including Sub_Modules

### Requirement 12: Route Registration

**User Story:** As a developer, I want each sub-module to expose its routes via an extension function, so that the Aggregator_Module can compose all routes cleanly.

#### Acceptance Criteria

1. WHEN a Sub_Module contains route definitions, THE Sub_Module SHALL expose a Ktor `Application.configure*Routes()` extension function (e.g., `Application.configureChatRoutes()`)

2. THE Aggregator_Module `configureRouting()` function SHALL call each Sub_Module route registration function

3. WHEN a new Sub_Module with routes is added, THE developer SHALL add one line to `configureRouting()` to register the Sub_Module routes

4. THE route paths (e.g., `/api/chat/send`, `/api/analysis/{ticketId}`) SHALL remain identical after restructuring
