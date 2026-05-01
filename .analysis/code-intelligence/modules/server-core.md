# Module Analysis — server-core

**Last Updated:** 2026-04-30T10:44:26.950Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-core/
├── com.assistant.server.auth/     # Security/Authentication
├── com.assistant.server.config/     # Configuration
├── com.assistant.server.db/     # Application logic
├── com.assistant.server.db.pg/     # Application logic
├── com.assistant.server.di/     # Application logic
├── com.assistant.server.middleware/     # Application logic
├── com.assistant.server.routes/     # Application logic
├── com.assistant.server.kb/     # Application logic
├── com.assistant.server.rbac/     # Application logic
└── com.assistant.server.settings/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| AuthServiceImpl | com.assistant.server.auth | Business logic | public |
| UserCredentials | com.assistant.server.auth | Application component | data |
| ServerConfig | com.assistant.server.config | Configuration | data |
| DatabaseConfig | com.assistant.server.config | Configuration | data |
| DataMigrationService | com.assistant.server.auth | Business logic | public |
| DataSourceFactory | com.assistant.server.auth | Object creation | public |
| for | com.assistant.server.auth | Application component | public |
| DocumentRepository | com.assistant.server.auth | Data access | public |
| GeneratedDocumentMeta | com.assistant.server.auth | Application component | data |
| FlywayMigrator | com.assistant.server.auth | Application component | public |
| for | com.assistant.server.auth | Application component | public |
| JobRepository | com.assistant.server.auth | Data access | public |
| PgChatConversationRepository | com.assistant.server.auth | Data access | public |
| PgChatConversationSql | com.assistant.server.auth | Application component | internal |
| PgChatRepository | com.assistant.server.auth | Data access | public |
| PgChatSql | com.assistant.server.auth | Application component | internal |
| PgDocumentRepository | com.assistant.server.auth | Data access | public |
| PgDocumentSql | com.assistant.server.auth | Application component | internal |
| PgJobRepository | com.assistant.server.auth | Data access | public |
| PgJobSql | com.assistant.server.auth | Application component | internal |
| PgKBRepository | com.assistant.server.auth | Data access | public |
| PgKBSql | com.assistant.server.auth | Application component | internal |
| PgMcpServerRepository | com.assistant.server.auth | Data access | public |
| PgMcpServerSql | com.assistant.server.auth | Application component | internal |
| PgProviderConfigRepository | com.assistant.server.config | Data access | public |
| PgProviderConfigSql | com.assistant.server.config | Application component | internal |
| PgScanLogRepository | com.assistant.server.auth | Data access | public |
| PgScanLogSql | com.assistant.server.auth | Application component | internal |
| PgScanStateRepository | com.assistant.server.auth | Data access | public |
| PgScanStateSql | com.assistant.server.auth | Application component | internal |
| PgSettingsRepository | com.assistant.server.auth | Data access | public |
| PgSettingsSql | com.assistant.server.auth | Application component | internal |
| PgUserAIConfigRepository | com.assistant.server.config | Data access | public |
| PgUserAIConfigSql | com.assistant.server.config | Application component | internal |
| PgUserToolPermissionRepository | com.assistant.server.auth | Data access | public |
| PgUserToolPermissionSql | com.assistant.server.auth | Application component | internal |
| LoginRequest | com.assistant.server.auth | Data transfer object | data |
| LoginSuccessResponse | com.assistant.server.auth | Data transfer object | data |
| UserResponse | com.assistant.server.auth | Data transfer object | data |
| ErrorResponse | com.assistant.server.auth | Data transfer object | data |
| MessageResponse | com.assistant.server.auth | Data transfer object | data |
| HealthResponse | com.assistant.server.auth | Data transfer object | data |
| ComponentHealth | com.assistant.server.auth | Application component | data |
| ProjectsResponse | com.assistant.server.auth | Data transfer object | data |
| SettingsStatusResponse | com.assistant.server.auth | Data transfer object | data |
| FeatureToggleRequest | com.assistant.server.auth | Data transfer object | data |
| FeatureToggleResponse | com.assistant.server.auth | Data transfer object | data |
| kb_records | com.assistant.server.auth | Application component | public |
| graph_data | com.assistant.server.auth | Application component | public |
| users | com.assistant.server.auth | Application component | public |
| audit_log | com.assistant.server.auth | Application component | public |
| provider_configs | com.assistant.server.config | Application component | public |
| app_settings | com.assistant.server.auth | Application component | public |
| scan_states | com.assistant.server.auth | Application component | public |
| scan_log | com.assistant.server.auth | Application component | public |
| chat_messages | com.assistant.server.auth | Application component | public |
| chat_conversations | com.assistant.server.auth | Application component | public |
| user_ai_config | com.assistant.server.config | Application component | public |
| mcp_servers | com.assistant.server.auth | Application component | public |
| attachment_chunks | com.assistant.server.auth | Application component | public |
| user_tool_permissions | com.assistant.server.auth | Application component | public |
| generated_documents | com.assistant.server.auth | Application component | public |
| generation_jobs | com.assistant.server.auth | Application component | public |
| collection_jobs | com.assistant.server.auth | Application component | public |
| traversal_cache | com.assistant.server.auth | Application component | public |
| deep_collection_rate_limits | com.assistant.server.auth | Application component | public |
| AuthServicePropertyTest | com.assistant.server.auth | Test class | public |
| EmptyProviderConfigRepo | com.assistant.server.config | Application component | private |
| ServerConfigTest | com.assistant.server.config | Test class | public |
| FakeSettingsRepository | com.assistant.server.auth | Data access | private |
| DatabaseConfigPropertyTest | com.assistant.server.config | Test class | public |
| DataMigrationIdempotencyTest | com.assistant.server.auth | Test class | public |
| DataMigrationPropertyTest | com.assistant.server.auth | Test class | public |
| DataMigrationTestHelper | com.assistant.server.auth | Utility functions | internal |
| GraphDataPersistencePropertyTest | com.assistant.server.auth | Test class | public |
| KBRecordPersistencePropertyTest | com.assistant.server.auth | Test class | public |
| RBACAuditLogPropertyTest | com.assistant.server.auth | Test class | public |
| RBACPermissionMatrixPropertyTest | com.assistant.server.auth | Test class | public |
| ProjectsResponseTest | com.assistant.server.auth | Test class | public |
| SettingsRepositoryImplTest | com.assistant.server.auth | Test class | public |
| SettingsStatusTest | com.assistant.server.auth | Test class | public |

## Public API Surface

- `jwtAlgorithm(): Algorithm`
- `jwtVerifier(): JWTVerifier`
- `load(): ServerConfig`
- `fromEnvironment(): DatabaseConfig`
- `fromEnvMap(env: Map<String, String>: Any): DatabaseConfig`
- `create(config: DatabaseConfig): HikariDataSource`
- `migrate(dataSource: DataSource): Unit`
- `coreModule(config: ServerConfig): Module`
- `postgresModule(config: ServerConfig): Module`
- `createJiraClientFromDb(credentialsService: JiraCredentialsService, httpClient: HttpClient): JiraClient`
- `jwtRoundTripPreservesUserFields(): Unit`
- `jwtTokenHas24HourExpiration(): Unit`
- `jwtIssuerIsJiraAssistant(): Unit`
- `setup(): Unit`
- `cleanPg(): Unit`
- `setup(): Unit`
- `cleanPg(): Unit`
- `createSqliteSchema(conn: Connection): Unit`
- `insertKbRecord(conn: Connection, ticketId: String, summary: String = "sum"): Unit`
- `insertAppSetting(conn: Connection, key: String, value: String): Unit`
- `insertAttachmentChunk(conn: Connection, ticketId: String, attId: String, embedding: List<Float>): Unit`
- `countPgRows(pgConn: Connection, table: String): Int`
- `setup(): Unit`
- `saveAndGetGraphDataRoundTrip(): Unit`
- `getGraphDataReturnsNullForMissing(): Unit`
- `overwriteGraphDataReplacesExisting(): Unit`
- `setup(): Unit`
- `saveAndFindByTicketIdRoundTrip(): Unit`
- `overwriteUpdatesRecord(): Unit`
- `findByTicketIdReturnsNullForMissing(): Unit`
- `changeRoleCreatesCompleteAuditLog(): Unit`
- `togglePermissionCreatesCompleteAuditLog(): Unit`
- `hasPermissionMatchesDefinedMatrix(): Unit`
- `getPermissionsReturnsExactSetForRole(): Unit`
- `readerLacksRestrictedPermissions(): Unit`
- `neuralArchitectLacksAdminOnlyPermissions(): Unit`
- `administratorHasAllPermissions(): Unit`
- `setup(): Unit`
- `setup(): Unit`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
