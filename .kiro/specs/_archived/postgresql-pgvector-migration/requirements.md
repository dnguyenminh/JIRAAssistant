# Requirements Document

## Introduction

This feature migrates the Jira Assistant's entire persistence layer from SQLite (via SQLDelight + JdbcSqliteDriver) to PostgreSQL with the pgvector extension. The current system stores all data — knowledge base records, graph data, scan state, chat history, MCP configs, user configs, and attachment chunk embeddings — in a single SQLite file. Vector search is performed via brute-force cosine similarity in Kotlin, loading all chunks into memory. At the expected scale of 200K–500K+ chunks across multiple projects, this approach is untenable: SQLite's single-writer lock causes write contention during parallel scans, and brute-force search degrades linearly with dataset size. PostgreSQL provides MVCC-based write concurrency, and pgvector provides native approximate nearest neighbor (ANN) search via HNSW indexing, solving both problems.

## Glossary

- **Database_Layer**: The server-side persistence subsystem responsible for all CRUD operations, backed by PostgreSQL via raw JDBC with HikariCP connection pooling.
- **PostgreSQL_Driver**: A JDBC-based database driver (`org.postgresql:postgresql`) that connects the Ktor server to a PostgreSQL instance.
- **pgvector**: A PostgreSQL extension that adds a `vector` column type and operators for cosine distance (`<=>`) and inner product (`<#>`), with support for HNSW and IVFFlat index types.
- **HNSW_Index**: Hierarchical Navigable Small World index — an approximate nearest neighbor index provided by pgvector that enables sub-linear vector search.
- **VectorStore**: The Kotlin interface (`com.assistant.server.attachment.VectorStore`) defining the contract for saving, searching, and deleting embedding chunks.
- **Embedding**: A 768-dimensional float vector produced by the Ollama nomic-embed-text model, representing the semantic content of a text chunk.
- **Connection_Pool**: A managed pool of reusable PostgreSQL connections (HikariCP) that avoids per-request connection overhead and limits concurrent connections.
- **Schema_Migration_Tool**: A versioned migration framework (Flyway) that applies DDL changes to PostgreSQL in a repeatable, ordered manner.
- **ServerModule**: The Koin DI module (`com.assistant.server.di.serverModule`) that wires all repositories, services, and the database driver.
- **Docker_Compose**: The `docker-compose.yml` file that defines the application's container services.

## Requirements

### Requirement 1: PostgreSQL Service in Docker Compose

**User Story:** As a developer, I want PostgreSQL with pgvector available as a Docker Compose service, so that the application has a production-ready database without manual setup.

#### Acceptance Criteria

1. THE Docker_Compose SHALL define a PostgreSQL service using the `pgvector/pgvector:pg16` image (or equivalent image with pgvector pre-installed).
2. THE Docker_Compose SHALL expose PostgreSQL on a configurable host port (default 5432) and map it to the container's port 5432.
3. THE Docker_Compose SHALL configure the PostgreSQL service with environment variables for `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, reading values from `.env` with sensible defaults.
4. THE Docker_Compose SHALL define a named volume for PostgreSQL data persistence across container restarts.
5. THE Docker_Compose SHALL configure the backend service to depend on the PostgreSQL service with a health check condition (`service_healthy`).
6. THE Docker_Compose SHALL define a health check for the PostgreSQL service using `pg_isready`.

### Requirement 2: PostgreSQL JDBC Driver and Connection Pooling

**User Story:** As a developer, I want the server to connect to PostgreSQL via a connection pool, so that the application handles concurrent database access efficiently.

#### Acceptance Criteria

1. THE Database_Layer SHALL use the PostgreSQL JDBC driver (`org.postgresql:postgresql`) to connect to the PostgreSQL instance.
2. THE Database_Layer SHALL use HikariCP as the Connection_Pool implementation.
3. THE Connection_Pool SHALL read its configuration (JDBC URL, username, password, max pool size) from environment variables with sensible defaults.
4. WHEN the application starts, THE Connection_Pool SHALL validate connectivity to PostgreSQL before accepting HTTP requests.
5. WHEN the Connection_Pool fails to establish a connection within a configured timeout, THE Database_Layer SHALL log the error and terminate the application with a non-zero exit code.
6. THE Connection_Pool SHALL configure a maximum pool size appropriate for the expected concurrent load (default: 10 connections).

### Requirement 3: Schema Migration with Flyway

**User Story:** As a developer, I want database schema changes managed by a versioned migration tool, so that schema evolution is repeatable and auditable.

#### Acceptance Criteria

1. THE Schema_Migration_Tool SHALL use Flyway to manage all PostgreSQL DDL changes as versioned SQL migration files.
2. THE Schema_Migration_Tool SHALL store migration files under a dedicated directory in the server module (e.g., `server/src/jvmMain/resources/db/migration/`).
3. WHEN the application starts, THE Schema_Migration_Tool SHALL automatically run any pending migrations before the application accepts HTTP requests.
4. THE Schema_Migration_Tool SHALL create all tables currently defined in the SQLDelight_Schema as PostgreSQL-compatible DDL, including: `kb_records`, `graph_data`, `users`, `audit_log`, `provider_configs`, `app_settings`, `scan_states`, `scan_log`, `chat_messages`, `chat_conversations`, `user_ai_config`, `mcp_servers`, `attachment_chunks`, and `user_tool_permissions`.
5. THE Schema_Migration_Tool SHALL replace SQLite-specific syntax (`INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT`) with PostgreSQL equivalents (`BIGSERIAL PRIMARY KEY`).
6. THE Schema_Migration_Tool SHALL create the pgvector extension (`CREATE EXTENSION IF NOT EXISTS vector`) in the initial migration.
7. IF a migration fails, THEN THE Schema_Migration_Tool SHALL log the failing migration version and SQL statement, and terminate the application with a non-zero exit code.

### Requirement 4: pgvector Column and HNSW Index for Embeddings

**User Story:** As a developer, I want attachment chunk embeddings stored as native pgvector columns with an HNSW index, so that vector search is performed in the database with sub-linear time complexity.

#### Acceptance Criteria

1. THE Schema_Migration_Tool SHALL define the `embedding` column in the `attachment_chunks` table as `vector(768)` type instead of `TEXT`.
2. THE Schema_Migration_Tool SHALL create an HNSW index on the `embedding` column of `attachment_chunks` using cosine distance operator (`vector_cosine_ops`).
3. THE HNSW_Index SHALL use configurable `m` and `ef_construction` parameters with sensible defaults (e.g., `m=16`, `ef_construction=64`).
4. WHEN a chunk is inserted with a 768-dimensional Embedding, THE Database_Layer SHALL store the embedding directly in the `vector(768)` column without JSON serialization.
5. IF an embedding with a dimension other than 768 is provided, THEN THE Database_Layer SHALL reject the insert and return a descriptive error.

### Requirement 5: PostgreSQL-Backed VectorStore Implementation

**User Story:** As a developer, I want a new VectorStore implementation that delegates search to pgvector, so that semantic search scales to 500K+ chunks without loading data into memory.

#### Acceptance Criteria

1. THE Database_Layer SHALL provide a new `PgVectorStoreImpl` class that implements the existing `VectorStore` interface.
2. WHEN `search(queryEmbedding, topK)` is called, THE PgVectorStoreImpl SHALL execute a SQL query using pgvector's `<=>` (cosine distance) operator with `ORDER BY` and `LIMIT` to retrieve the top-K nearest chunks.
3. WHEN `search(queryEmbedding, topK, chunkType)` is called with a non-null chunkType, THE PgVectorStoreImpl SHALL include a `WHERE chunk_type = ?` filter in the vector search query.
4. WHEN `saveChunk(chunk)` is called, THE PgVectorStoreImpl SHALL insert the chunk with the embedding as a native `vector` value (not JSON text).
5. THE PgVectorStoreImpl SHALL implement all other VectorStore methods (`existsByAttachmentId`, `deleteByTicketId`, `deleteByProjectKey`, `findByTicketId`) using standard SQL queries against PostgreSQL.
6. THE PgVectorStoreImpl SHALL use parameterized queries for all database operations to prevent SQL injection.
7. THE ServerModule SHALL wire `PgVectorStoreImpl` as the `VectorStore` implementation in the Koin dependency injection module, replacing `VectorStoreImpl`.

### Requirement 6: PostgreSQL-Backed Repository Implementations

**User Story:** As a developer, I want all repositories to use PostgreSQL instead of SQLDelight/SQLite, so that the entire persistence layer benefits from PostgreSQL's concurrency and scalability.

#### Acceptance Criteria

1. THE Database_Layer SHALL provide PostgreSQL-backed implementations for all repositories currently using SQLDelight: `KBRepository`, `ScanStateRepository`, `ScanLogRepository`, `ChatRepository`, `ChatConversationRepository`, `UserAIConfigRepository`, `McpServerRepository`, `SettingsRepository`, `ProviderConfigRepository`, and `UserToolPermissionRepository`.
2. EACH PostgreSQL-backed repository SHALL preserve the exact same interface contract as the current SQLDelight-backed implementation.
3. THE ServerModule SHALL wire all PostgreSQL-backed repository implementations in the Koin DI module, replacing the SQLDelight-backed implementations.
4. WHEN multiple concurrent write operations target the same table, THE Database_Layer SHALL handle them without write contention errors (leveraging PostgreSQL MVCC).
5. THE server module's `build.gradle.kts` SHALL NOT include the SQLDelight JDBC SQLite driver (`app.cash.sqldelight:sqlite-driver`) as a runtime dependency.

### Requirement 7: Data Migration from SQLite to PostgreSQL

**Status:** Completed and removed. The one-time `DataMigrationService` and all supporting files (`DataMigrationTables.kt`, `DataMigrationTables2.kt`, `DataMigrationSql.kt`) have been deleted. Data migration was a transitional capability; all data now lives exclusively in PostgreSQL.

### Requirement 8: Environment Configuration

**User Story:** As a developer, I want all PostgreSQL connection parameters configurable via environment variables, so that the application works across local development, Docker, and production environments.

#### Acceptance Criteria

1. THE Database_Layer SHALL read the PostgreSQL JDBC URL from the `DATABASE_URL` environment variable.
2. THE Database_Layer SHALL read the PostgreSQL username from the `DATABASE_USER` environment variable with a default of `postgres`.
3. THE Database_Layer SHALL read the PostgreSQL password from the `DATABASE_PASSWORD` environment variable.
4. THE Docker_Compose SHALL pass `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` to the backend service environment, referencing `.env` values.
5. THE `.env.example` file SHALL document all PostgreSQL-related environment variables with example values.
6. THE server module's `build.gradle.kts` SHALL parse the root `.env` file at configuration time and inject all key-value pairs as environment variables into the Gradle `jvmRun` task, so that `System.getenv()` calls in application code resolve `.env` values during local development without requiring OS-level environment variable setup.

### Requirement 9: Removal of Brute-Force Search and SQLite Dependencies

**Status:** Completed. The following items have been removed from the codebase:

- `SqliteModule.kt` — SQLite DI wiring module
- `DatabaseMigrations.kt` — SQLite incremental migration logic (`runIncrementalMigrations`)
- `DataMigrationService.kt` and all supporting files — one-time SQLite → PG migration
- `app.cash.sqldelight:sqlite-driver` runtime dependency from `server/build.gradle.kts`
- `DB_PATH` environment variable from `ServerConfig`, `.env`, `.env.example`, `Dockerfile`, `docker-compose.yml`
- `DATABASE_BACKEND` environment variable and conditional backend wiring from `ServerConfig`, `DatabaseConfig`, `ServerModule`
- `dbPath` and `dbPathReadOnly` fields from `AppSettings` / `AppSettingsResponse` DTOs
- DB_PATH field from the frontend Settings page UI and e2e tests

The `ServerModule` now unconditionally wires PostgreSQL-backed implementations via `postgresModule()`.

### Requirement 10: Backward Compatibility and Rollback

**Status:** Removed. The SQLite rollback path (`DATABASE_BACKEND=sqlite`) has been fully removed. PostgreSQL is the sole persistence backend. The `SqliteModule`, conditional DI wiring, and `DATABASE_BACKEND` environment variable no longer exist.

### Requirement 11: Vector Search Performance

**User Story:** As a developer, I want vector search to perform efficiently at scale, so that semantic search remains responsive as the dataset grows to 500K+ chunks.

#### Acceptance Criteria

1. WHEN searching 500K+ chunks, THE PgVectorStoreImpl SHALL return top-K results using the HNSW_Index without loading all chunks into application memory.
2. THE HNSW_Index SHALL support configurable `ef_search` parameter at query time to trade off between recall and latency.
3. WHEN `ef_search` is not explicitly configured, THE PgVectorStoreImpl SHALL use a default value of 40.
4. THE PgVectorStoreImpl SHALL set the `ef_search` parameter via `SET LOCAL hnsw.ef_search = ?` before executing the search query within the same transaction.
