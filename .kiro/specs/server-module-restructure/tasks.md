# Tasks — Server Module Restructure

## Phase 1: Foundation — Create `server/core` Sub-Module

- [x] 1.1 Update `settings.gradle.kts` to include all 10 sub-modules: `include(":server:core")`, `include(":server:dashboard")`, `include(":server:analysis")`, `include(":server:docgen")`, `include(":server:agent")`, `include(":server:chat")`, `include(":server:mcp")`, `include(":server:knowledge-graph")`, `include(":server:user-mgmt")`, `include(":server:testing-support")`
- [x] 1.2 Create `server/core/build.gradle.kts` with KMP configuration, depending on `:shared` only. Include dependencies: Ktor server core, auth, JWT, status-pages, CORS, call-logging, content-negotiation, serialization, Koin, coroutines, logback, PostgreSQL driver, HikariCP, Flyway, pgvector
- [x] 1.3 Create `server/core/` source set directories: `src/jvmMain/kotlin/com/assistant/server/` and `src/jvmTest/kotlin/com/assistant/server/`
- [x] 1.4 Move `config/ServerConfig.kt` to `server/core`
- [x] 1.5 Move `auth/AuthServiceImpl.kt` to `server/core`
- [x] 1.6 Move `db/` package (DatabaseConfig, DataSourceFactory, FlywayMigrator, DataMigrationService, DocumentRepository, JobRepository, `pg/` sub-package) to `server/core`
- [x] 1.7 Move `middleware/RBACMiddleware.kt` to `server/core`
- [x] 1.8 Move `di/PostgresModule.kt` to `server/core`. Create `server/core` Koin module (`coreModule`) containing config, auth, RBAC, database, and middleware bindings
- [x] 1.9 Move `routes/AuthRoutes.kt`, `routes/HealthRoutes.kt`, `routes/SettingsRoutes.kt`, `routes/ProjectRoutes.kt` to `server/core`. Create `Routing.configureCoreRoutes()` extension function
- [x] 1.10 Move corresponding tests from `server/src/jvmTest/` to `server/core/src/jvmTest/` for config, auth, db, middleware, and core routes
- [x] 1.11 Update aggregator `server/build.gradle.kts` to add `implementation(project(":server:core"))` dependency
- [x] 1.12 Verify: `./gradlew :server:core:compileKotlinJvm` compiles successfully
- [x] 1.13 Verify: `./gradlew build` compiles the full project successfully

## Phase 2: Feature Sub-Modules — Create Remaining Modules

### 2A: `server/testing-support`

- [x] 2.1 Create `server/testing-support/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.2 Move shared test utilities, test doubles, and test fixtures from `server/src/jvmTest/` to `server/testing-support/src/jvmMain/` (note: test-support code goes in `jvmMain` so other modules can depend on it as `implementation`)
- [x] 2.3 Verify: `./gradlew :server:testing-support:compileKotlinJvm` compiles successfully

### 2B: `server/analysis`

- [x] 2.4 Create `server/analysis/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`. Include Ktor client CIO dependency for HTTP calls
- [x] 2.5 Move `analysis/` package (MapReduceOrchestrator, BatchStrategy, models/, etc.) to `server/analysis`
- [x] 2.6 Move `attachment/` package (AttachmentPipeline, EmbeddingService, VectorStore, etc.) to `server/analysis`
- [x] 2.7 Move `indexing/` package (IndexingPipeline, BatchEmbedder) to `server/analysis`
- [x] 2.8 Move `routes/AnalysisRoutes.kt`, `routes/AttachmentRoutes.kt`, `routes/TicketDetailRoutes.kt`, `routes/CascadeRoutes.kt`, `routes/CascadeStatusTracker.kt` to `server/analysis`. Create `Routing.configureAnalysisRoutes()` extension function
- [x] 2.9 Create `server/analysis` Koin module (`analysisModule`) with analysis, attachment, embedding, indexing bindings
- [x] 2.10 Move corresponding tests to `server/analysis/src/jvmTest/`
- [x] 2.11 Verify: `./gradlew :server:analysis:compileKotlinJvm` compiles successfully

### 2C: `server/mcp`

- [x] 2.12 Create `server/mcp/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.13 Move `mcp/` package (McpProcessManagerImpl, internal/, protocol client, health checker, etc.) to `server/mcp`
- [x] 2.14 Move `routes/IntegrationRoutes.kt`, `routes/McpRoutes.kt`, `routes/McpHealthRoutes.kt`, `routes/McpRuntimeRoutes.kt`, `routes/McpRuntimeHandlers.kt`, `routes/McpToolsHandlers.kt` to `server/mcp`. Create `Routing.configureMcpRoutes()` extension function
- [x] 2.15 Create `server/mcp` Koin module (`mcpKoinModule`) with MCP process manager, health checker, internal tool registry, internal MCP bridge bindings
- [x] 2.16 Move corresponding tests to `server/mcp/src/jvmTest/`
- [x] 2.17 Verify: `./gradlew :server:mcp:compileKotlinJvm` compiles successfully

### 2D: `server/agent`

- [x] 2.18 Create `server/agent/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.19 Move `agent/` package (all sub-packages: ba/, engine/, orchestrator/, subprocess/, tool/, registry/, di/, error/, home/, progress/, session/, state/, streaming/) to `server/agent`
- [x] 2.20 Move `ai/` package (CliAgentUtils, CopilotCliAgent, GeminiCliAgent, KiroCliAgent) to `server/agent`
- [x] 2.21 Create `server/agent` Koin module (update existing `agentModule` and `baAgentModule` to be self-contained)
- [x] 2.22 Move corresponding tests to `server/agent/src/jvmTest/`
- [x] 2.23 Verify: `./gradlew :server:agent:compileKotlinJvm` compiles successfully

### 2E: `server/chat`

- [x] 2.24 Create `server/chat/build.gradle.kts` with KMP configuration, depending on `:shared`, `:server:core`, and `:server:mcp`
- [x] 2.25 Move `chat/` package (ChatServiceImpl, McpAgenticLoop, models/, etc.) to `server/chat`
- [x] 2.26 Move `routes/ChatRoutes.kt`, `routes/ChatActionRoutes.kt`, `routes/ChatConfigRoutes.kt`, `routes/ChatHistoryRoutes.kt`, `routes/ChatToolPermissionRoutes.kt`, `routes/ChatUploadRoutes.kt` to `server/chat`. Create `Routing.configureChatRoutes()` extension function
- [x] 2.27 Create `server/chat` Koin module (`chatKoinModule`) with chat service, tool permission service, local KB tool executor bindings
- [x] 2.28 Move corresponding tests to `server/chat/src/jvmTest/`
- [x] 2.29 Verify: `./gradlew :server:chat:compileKotlinJvm` compiles successfully

### 2F: `server/docgen`

- [x] 2.30 Create `server/docgen/build.gradle.kts` with KMP configuration, depending on `:shared`, `:server:core`, and `:server:analysis`
- [x] 2.31 Move `document/` package (DeepCollector, DocumentAggregator, all sub-packages: cache/, collection/, curation/, extraction/, jobs/, models/, prompt/, security/, traversal/) to `server/docgen`
- [x] 2.32 Move `jobs/` package (JobManager, JobExecutor, JobChainOrchestrator, etc.) to `server/docgen`
- [x] 2.33 Move `routes/DocumentRoutes.kt`, `routes/DocumentRouteHandlers.kt`, `routes/JobRoutes.kt`, `routes/CollectionJobRoutes.kt` to `server/docgen`. Create `Routing.configureDocgenRoutes()` extension function
- [x] 2.34 Create `server/docgen` Koin module (`docgenModule`) with document aggregator, job manager, deep collection bindings (absorb `deepCollectionModule` and `curationModule`)
- [x] 2.35 Move corresponding tests to `server/docgen/src/jvmTest/`
- [x] 2.36 Verify: `./gradlew :server:docgen:compileKotlinJvm` compiles successfully

### 2G: `server/dashboard`

- [x] 2.37 Create `server/dashboard/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.38 Move `routes/ScanRoutes.kt`, `routes/EstimationRoutes.kt`, `routes/GraphRoutes.kt` to `server/dashboard`. Create `Routing.configureDashboardRoutes()` extension function
- [x] 2.39 Create `server/dashboard` Koin module (`dashboardModule`) with scan engine and graph-related bindings
- [x] 2.40 Move corresponding tests to `server/dashboard/src/jvmTest/`
- [x] 2.41 Verify: `./gradlew :server:dashboard:compileKotlinJvm` compiles successfully

### 2H: `server/knowledge-graph`

- [x] 2.42 Create `server/knowledge-graph/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.43 Extract graph-specific route handlers into `server/knowledge-graph` (if any exist beyond what's in `server/dashboard`). Create `Routing.configureKnowledgeGraphRoutes()` extension function
- [x] 2.44 Create `server/knowledge-graph` Koin module (`knowledgeGraphModule`)
- [x] 2.45 Move corresponding tests to `server/knowledge-graph/src/jvmTest/`
- [x] 2.46 Verify: `./gradlew :server:knowledge-graph:compileKotlinJvm` compiles successfully

### 2I: `server/user-mgmt`

- [x] 2.47 Create `server/user-mgmt/build.gradle.kts` with KMP configuration, depending on `:shared` and `:server:core`
- [x] 2.48 Move `routes/UserRoutes.kt` to `server/user-mgmt`. Create `Routing.configureUserMgmtRoutes()` extension function
- [x] 2.49 Create `server/user-mgmt` Koin module (`userMgmtModule`) with user store and audit log bindings
- [x] 2.50 Move corresponding tests to `server/user-mgmt/src/jvmTest/`
- [x] 2.51 Verify: `./gradlew :server:user-mgmt:compileKotlinJvm` compiles successfully

## Phase 3: Slim Down Aggregator

- [x] 3.1 Update aggregator `server/build.gradle.kts` to depend on all sub-modules via `implementation(project(":server:*"))` for each sub-module
- [x] 3.2 Refactor `server/di/ServerModule.kt` to compose all sub-module Koin modules via `includes()` — remove all domain-specific bindings that have been moved to sub-modules
- [x] 3.3 Refactor `server/routes/Routing.kt` `configureRouting()` to call each sub-module's `configure*Routes()` extension function — remove inline route registrations
- [x] 3.4 Ensure `Application.kt` remains in the aggregator with entry point, Ktor plugin installation, and startup hooks
- [x] 3.5 Remove all source files from aggregator `src/jvmMain/` that have been moved to sub-modules (only `Application.kt`, `Routing.kt`, `ServerModule.kt` should remain)
- [x] 3.6 Remove all test files from aggregator `src/jvmTest/` that have been moved to sub-modules (only aggregator-level integration tests should remain)
- [x] 3.7 Verify: `./gradlew build` compiles the full project successfully

## Phase 4: Verification and Backward Compatibility

- [x] 4.1 Verify: `./gradlew :server:fatJar` produces `server/build/libs/jira-assistant-server-all.jar` with classes from all sub-modules
- [x] 4.2 Verify: `java -jar server/build/libs/jira-assistant-server-all.jar` starts the application and serves API endpoints
- [x] 4.3 Verify: `./gradlew :server:jvmRun` starts the application with all features functional
- [x] 4.4 Verify: `./gradlew :server:jvmTest` runs all parallel tests across all sub-modules
- [x] 4.5 Verify: `./gradlew :server:jvmTestSequential` runs all sequential tests
- [x] 4.6 Verify: `./gradlew :server:jvmTestAll` runs parallel then sequential tests
- [x] 4.7 Verify: Individual sub-module test commands work (e.g., `./gradlew :server:analysis:jvmTest`)
- [x] 4.8 Verify: `./gradlew :server:compileKotlinJvm` compiles all sub-modules
- [x] 4.9 Verify: Dockerfile builds and runs without modifications
- [x] 4.10 Write Koin `checkModules()` integration test in aggregator to verify all DI bindings resolve
- [x] 4.11 Write route registration smoke test to verify all API endpoints return non-404 status codes
- [x] 4.12 Compare test counts before and after restructuring — ensure no tests were lost or broken
