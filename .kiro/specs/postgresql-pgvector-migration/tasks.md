# Implementation Plan: PostgreSQL + pgvector Migration

## Overview

Migrate the Jira Assistant persistence layer from SQLite (SQLDelight + JdbcSqliteDriver) to PostgreSQL 16 with pgvector. Tasks are ordered for incremental development: infrastructure first, then schema, then repositories (one at a time), then vector store, data migration, conditional wiring, and finally cleanup. Each task builds on the previous and can be verified independently.

## Tasks

- [x] 1. Add PostgreSQL dependencies to server/build.gradle.kts
  - Add `org.postgresql:postgresql:42.7.5` (JDBC driver)
  - Add `com.zaxxer:HikariCP:6.3.0` (connection pool)
  - Add `org.flywaydb:flyway-core:11.8.0` and `org.flywaydb:flyway-database-postgresql:11.8.0` (migrations)
  - Add `com.pgvector:pgvector:0.1.6` (vector type support)
  - Add `org.testcontainers:testcontainers:1.21.4`, `org.testcontainers:postgresql:1.21.4`, `org.testcontainers:junit-jupiter:1.21.4` to jvmTest dependencies
  - _Requirements: 2.1, 3.1, 4.1, 4.2_

- [x] 2. Create DatabaseConfig and DataSourceFactory
  - [x] 2.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/DatabaseConfig.kt`
    - Data class reading `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DATABASE_POOL_SIZE`, `DATABASE_BACKEND` from environment variables
    - Apply documented defaults: `DATABASE_USER` → `"postgres"`, `DATABASE_POOL_SIZE` → `10`, `DATABASE_BACKEND` → `"postgresql"`
    - Add `databaseBackend` field to `ServerConfig` reading `DATABASE_BACKEND` env var
    - _Requirements: 2.3, 8.1, 8.2, 8.3, 10.2_

  - [x] 2.2 Create `server/src/jvmMain/kotlin/com/assistant/server/db/DataSourceFactory.kt`
    - Object with `create(config: DatabaseConfig): HikariDataSource`
    - Configure `maximumPoolSize`, `connectionTimeout`, `connectionTestQuery = "SELECT 1"`, `poolName = "jira-assistant-pool"`
    - _Requirements: 2.2, 2.4, 2.5, 2.6_

  - [x] 2.3 Write property test for DatabaseConfig defaults (Property 1)
    - **Property 1: Configuration parsing with defaults**
    - **Validates: Requirements 2.3, 8.1, 8.2, 8.3, 10.2**

- [x] 3. Create Flyway migration infrastructure
  - [x] 3.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/FlywayMigrator.kt`
    - Object with `migrate(dataSource: DataSource)` method
    - Configure Flyway with `locations = "classpath:db/migration"`
    - Log migration info; throw on failure for application exit
    - _Requirements: 3.1, 3.3, 3.7_

  - [x] 3.2 Create `server/src/jvmMain/resources/db/migration/V1__initial_schema.sql`
    - `CREATE EXTENSION IF NOT EXISTS vector`
    - All 14 tables with PostgreSQL-compatible DDL (BIGSERIAL, BOOLEAN, vector(768))
    - All indexes including HNSW index on `attachment_chunks.embedding` with `m=16, ef_construction=64`
    - _Requirements: 3.2, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_

- [x] 4. Checkpoint — Verify infrastructure compiles
  - Ensure all new files compile, Flyway migration SQL is syntactically valid, ask the user if questions arise.

- [x] 5. Implement PgVectorStoreImpl
  - [x] 5.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgVectorStoreImpl.kt`
    - Implement `VectorStore` interface using `DataSource` and pgvector SQL operators
    - `search()`: Execute `SET LOCAL hnsw.ef_search = ?` then `SELECT ... ORDER BY embedding <=> ?::vector LIMIT ?`
    - `saveChunk()`: Insert with `?::vector` cast for embedding column
    - `existsByAttachmentId()`, `deleteByTicketId()`, `deleteByProjectKey()`, `findByTicketId()` using standard SQL
    - All queries use `PreparedStatement` with parameterized values
    - Default `efSearch = 40`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 11.1, 11.2, 11.3, 11.4_

  - [x] 5.2 Write property test for embedding round-trip (Property 2)
    - **Property 2: Embedding vector round-trip**
    - **Validates: Requirements 4.4, 5.4, 7.3**

  - [x] 5.3 Write property test for wrong dimension rejection (Property 3)
    - **Property 3: Wrong dimension rejection**
    - **Validates: Requirements 4.5**

  - [x] 5.4 Write property test for vector search correctness (Property 4)
    - **Property 4: Vector search correctness**
    - **Validates: Requirements 5.2, 5.3**

  - [x] 5.5 Write property test for VectorStore CRUD round-trip (Property 5)
    - **Property 5: VectorStore CRUD round-trip**
    - **Validates: Requirements 5.5, 6.2**

- [x] 6. Implement PostgreSQL-backed repositories (batch 1: core data)
  - [x] 6.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgKBRepository.kt`
    - Implement `KBRepository` interface: `findByTicketId`, `save`, `overwrite`, `saveGraphData`, `getGraphData`
    - Use `DataSource`, `PreparedStatement`, `INSERT ... ON CONFLICT` for upserts
    - _Requirements: 6.1, 6.2, 6.4_

  - [x] 6.2 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgSettingsRepository.kt`
    - Implement `SettingsRepository` interface: `getAll`, `get`, `put`, `putAll`
    - _Requirements: 6.1, 6.2_

  - [x] 6.3 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgProviderConfigRepository.kt`
    - Implement same contract as `ProviderConfigRepository`: `getAllProviders`, `findById`, `save`, `findByType`, `existsByType`, `updateStatus`
    - Accept `DataSource` and `encryptionKey` as constructor params
    - Preserve encryption-at-rest behavior for `api_key` field
    - _Requirements: 6.1, 6.2_

- [x] 7. Implement PostgreSQL-backed repositories (batch 2: scan + chat)
  - [x] 7.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgScanStateRepository.kt`
    - Implement `ScanStateRepository`: `findByProjectKey`, `save`, `delete`, `findAllScanning`
    - _Requirements: 6.1, 6.2_

  - [x] 7.2 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgScanLogRepository.kt`
    - Implement `ScanLogRepository`: `addEntry`, `getByProjectKey`, `getByProjectKeyPaged`, `countByProjectKey`, `deleteByProjectKey`
    - _Requirements: 6.1, 6.2_

  - [x] 7.3 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgChatRepository.kt`
    - Implement `ChatRepository`: `saveMessage`, `getHistory`, `getHistoryCount`, `deleteHistory`, `getUserMessageList`
    - Support `conversation_id` column for multi-conversation
    - _Requirements: 6.1, 6.2_

  - [x] 7.4 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgChatConversationRepository.kt`
    - Implement `ChatConversationRepository`: `create`, `getByUser`, `findById`, `updateTitle`, `updateTimestamp`, `delete`
    - _Requirements: 6.1, 6.2_

- [x] 8. Checkpoint — Verify repositories compile
  - Ensure all repository implementations compile, ask the user if questions arise.

- [x] 9. Implement PostgreSQL-backed repositories (batch 3: user config + MCP)
  - [x] 9.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgUserAIConfigRepository.kt`
    - Implement `UserAIConfigRepository`: `findByUserId`, `save`
    - JSON serialization for skills, workflow, instructions, rules columns
    - _Requirements: 6.1, 6.2_

  - [x] 9.2 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgMcpServerRepository.kt`
    - Implement `McpServerRepository`: `getAll`, `findById`, `findByName`, `isInternal`, `insert`, `update`, `updateStatus`, `delete`, `deleteAll`
    - Map `disabled`/`internal` boolean columns correctly
    - _Requirements: 6.1, 6.2_

  - [x] 9.3 Create `server/src/jvmMain/kotlin/com/assistant/server/db/pg/PgUserToolPermissionRepository.kt`
    - Implement `UserToolPermissionRepository`: `findByUserId`, `save`, `delete`
    - _Requirements: 6.1, 6.2_

- [x] 10. Implement DataMigrationService (SQLite → PostgreSQL)
  - [x] 10.1 Create `server/src/jvmMain/kotlin/com/assistant/server/db/DataMigrationService.kt`
    - Read from SQLite via `JdbcSqliteDriver` (read-only)
    - Write to PostgreSQL within a single transaction
    - Convert `attachment_chunks.embedding` from JSON text → `vector(768)` format
    - Use `INSERT ... ON CONFLICT DO NOTHING` for idempotency
    - Log table name + row count for each table
    - Skip migration if SQLite file does not exist (log info message)
    - Do NOT modify or delete the SQLite file
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 10.1_

  - [x] 10.2 Write property test for data migration row preservation (Property 6)
    - **Property 6: Data migration preserves row values**
    - **Validates: Requirements 7.2, 7.3**

  - [x] 10.3 Write property test for data migration idempotency (Property 7)
    - **Property 7: Data migration idempotency**
    - **Validates: Requirements 7.6**

  - [x] 10.4 Write property test for SQLite file immutability (Property 8)
    - **Property 8: SQLite file immutability during migration**
    - **Validates: Requirements 10.1**

- [x] 11. Checkpoint — Verify data migration compiles
  - Ensure all data migration code compiles, ask the user if questions arise.

- [x] 12. Wire conditional DI in ServerModule
  - [x] 12.1 Update `ServerModule.kt` with `DATABASE_BACKEND` conditional wiring
    - Read `config.databaseBackend` (`"postgresql"` or `"sqlite"`)
    - When `postgresql`: create `DatabaseConfig`, `HikariDataSource` via `DataSourceFactory`, run `FlywayMigrator.migrate()`, run `DataMigrationService`, wire all `Pg*` repository implementations
    - When `sqlite`: keep existing SQLDelight-based wiring unchanged
    - _Requirements: 5.7, 6.3, 10.2, 10.3, 10.4, 10.5_

  - [x] 12.2 Update `ServerConfig` to include `databaseBackend` field
    - Read from `DATABASE_BACKEND` env var, default `"postgresql"`
    - _Requirements: 10.2_

- [x] 13. Update Docker Compose and environment configuration
  - [x] 13.1 Update `docker-compose.yml` with PostgreSQL service
    - Add `postgres` service using `pgvector/pgvector:pg16` image
    - Configure environment variables, named volume `pg-data`, health check with `pg_isready`
    - Update `backend` service: add `DATABASE_BACKEND`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` env vars
    - Add `depends_on: postgres: condition: service_healthy`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 8.4_

  - [x] 13.2 Update `.env.example` with new PostgreSQL variables
    - Document `DATABASE_BACKEND`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DATABASE_POOL_SIZE`, `POSTGRES_DB`, `POSTGRES_PORT`
    - _Requirements: 8.6_

- [x] 14. Final checkpoint — Ensure all code compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1–8)
- All PG repository implementations follow the same pattern: `DataSource` constructor param, `connection.use { }`, `PreparedStatement` for all queries
- The Kotlin code standards (≤200 lines/file, ≤20 lines/function) apply to all new files
- `ProviderConfigRepository` is a concrete class (not interface) in the current codebase — the PG version must preserve the same public API
