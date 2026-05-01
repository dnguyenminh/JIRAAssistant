# Module Analysis — server-mcp

**Last Updated:** 2026-04-30T10:44:26.988Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-mcp/
├── com.assistant.server.ai/     # Application logic
├── com.assistant.server.di/     # Application logic
├── com.assistant.server.mcp/     # Application logic
├── com.assistant.server.mcp.internal/     # Application logic
├── com.assistant.server.mcp.internal.handlers/     # Application logic
└── com.assistant.server.routes/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| ProcessResult | com.assistant.server.ai | Application component | data |
| CopilotCliAgent | com.assistant.server.ai | Application component | public |
| ProcessResult | com.assistant.server.ai | Application component | data |
| GeminiCliAgent | com.assistant.server.ai | Application component | public |
| KiroCliAgent | com.assistant.server.ai | Application component | public |
| HttpMcpProtocolClient | com.assistant.server.ai | External service client | public |
| AnalysisToolDefs | com.assistant.server.ai | Application component | public |
| ArgumentValidator | com.assistant.server.ai | Input validation | public |
| ChatToolDefs | com.assistant.server.ai | Application component | public |
| DiagramToolDefs | com.assistant.server.ai | Application component | public |
| AnalysisHandlers | com.assistant.server.ai | Application component | public |
| ChatHandlers | com.assistant.server.ai | Application component | public |
| DashboardHandlers | com.assistant.server.ai | Application component | public |
| DiagramHandlers | com.assistant.server.ai | Application component | public |
| DiagramResult | com.assistant.server.ai | Application component | data |
| DiagramFileInfo | com.assistant.server.ai | Application component | data |
| IntegrationHandlers | com.assistant.server.ai | Application component | public |
| KnowledgeGraphHandlers | com.assistant.server.ai | Application component | public |
| NavigationHandlers | com.assistant.server.ai | Application component | public |
| PageInfo | com.assistant.server.ai | Application component | data |
| ScanHandlers | com.assistant.server.ai | Application component | public |
| SettingsHandlers | com.assistant.server.ai | Application component | public |
| UserManagementHandlers | com.assistant.server.ai | Application component | public |
| IntegrationToolDefs | com.assistant.server.ai | Application component | public |
| InternalMcpBridge | com.assistant.server.ai | Application component | open |
| InternalMcpToolExecutor | com.assistant.server.ai | Application component | public |
| InternalToolRegistry | com.assistant.server.ai | Application component | public |
| NavigationToolDefs | com.assistant.server.ai | Application component | public |
| ScanToolDefs | com.assistant.server.ai | Application component | public |
| with | com.assistant.server.ai | Application component | public |
| property | com.assistant.server.ai | Application component | public |
| SettingsToolDefs | com.assistant.server.ai | Application component | public |
| UserContext | com.assistant.server.ai | Application component | data |
| ManagedProcess | com.assistant.server.ai | Application component | data |
| McpHealthChecker | com.assistant.server.ai | Application component | public |
| McpLogEntry | com.assistant.server.ai | Application component | data |
| McpLogBuffer | com.assistant.server.ai | Application component | public |
| McpProcessManagerImpl | com.assistant.server.ai | Application component | public |
| to | com.assistant.server.ai | Application component | public |
| McpProtocolClientImpl | com.assistant.server.ai | Application component | public |
| ProcessSpawner | com.assistant.server.ai | Application component | public |
| ProviderConfigUpdateRequest | com.assistant.server.ai | Data transfer object | data |
| JiraConfigRequest | com.assistant.server.ai | Data transfer object | data |
| JiraConfigResponse | com.assistant.server.ai | Data transfer object | data |
| ProviderTestRequest | com.assistant.server.ai | Data transfer object | data |
| ProviderStatusUpdateRequest | com.assistant.server.ai | Data transfer object | data |
| OllamaModelsResponse | com.assistant.server.ai | Data transfer object | data |
| OllamaModelInfo | com.assistant.server.ai | Application component | data |
| ProviderTestResult | com.assistant.server.ai | Application component | data |
| JiraStatusResponse | com.assistant.server.ai | Data transfer object | data |
| McpTestResult | com.assistant.server.ai | Application component | data |
| InternalArgumentValidationPropertyTest | com.assistant.server.ai | Test class | public |
| InternalBusinessErrorPropertyTest | com.assistant.server.ai | Test class | public |
| InternalPageFilteringPropertyTest | com.assistant.server.ai | Test class | public |
| InternalRbacEnforcementPropertyTest | com.assistant.server.ai | Test class | public |
| InternalToolAggregationPropertyTest | com.assistant.server.ai | Test class | public |
| with | com.assistant.server.ai | Application component | public |
| InternalToolDefinitionsPropertyTest | com.assistant.server.ai | Test class | public |
| and | com.assistant.server.ai | Application component | public |
| McpAgenticLoopPropertyTest | com.assistant.server.ai | Test class | public |
| McpAutoApprovePropertyTest | com.assistant.server.ai | Test class | public |
| McpCriticalServerPropertyTest | com.assistant.server.ai | Test class | public |
| McpHealthCheckerPropertyTest | com.assistant.server.ai | Test class | public |
| InMemoryRepo | com.assistant.server.ai | Application component | private |
| ConfigurableProcessManager | com.assistant.server.ai | Application component | private |
| SuccessClient | com.assistant.server.ai | External service client | private |
| FailingClient | com.assistant.server.ai | External service client | private |
| RoleCase | com.assistant.server.ai | Application component | data |
| McpHealthCheckerTest | com.assistant.server.ai | Test class | public |
| InMemoryRepo | com.assistant.server.ai | Application component | private |
| ConfigurableProcessManager | com.assistant.server.ai | Application component | private |
| SuccessClient | com.assistant.server.ai | External service client | public |
| FailingClient | com.assistant.server.ai | External service client | private |
| McpPlaywrightIntegrationTest | com.assistant.server.ai | Test class | public |
| McpProcessManagerPropertyTest | com.assistant.server.ai | Test class | public |
| McpProtocolClientPropertyTest | com.assistant.server.ai | Test class | public |
| McpStatusMergeTest | com.assistant.server.ai | Test class | public |
| InMemoryMcpRepo | com.assistant.server.ai | Application component | public |
| StubProcessManager | com.assistant.server.ai | Application component | public |

## Public API Surface

- `buildAuthHeaders(env: Map<String, String>: Any): Map`
- `all(): List`
- `validate(toolDef: InternalToolDefinition, arguments: JsonObject): Unit`
- `all(): List`
- `all(): List`
- `textResponse(text: String): Unit`
- `errorResponse(msg: String): Unit`
- `missingField(field: String): Unit`
- `all(): List`
- `isInternalServer(serverId: String): Boolean`
- `getAggregatedTools(): List`
- `getStatus(): McpProcessStatus`
- `getTools(): List`
- `getAllTools(): List`
- `getTool(name: String): InternalToolDefinition`
- `all(): List`
- `all(): List`
- `all(): List`
- `add(entry: McpLogEntry): Unit`
- `getLogs(serverId: String, limit: Int = MAX_ENTRIES): List`
- `clear(serverId: String): Unit`
- `spawnProcess(config: McpServerConfig): Process`
- `createClient(process: Process, scope: CoroutineScope, serverId: String = "unknown"): McpProtocolClientImpl`
- `parseEnvPublic(raw: String): Map`
- `isCriticalForDocType(role: String, docType: String): Boolean`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | McpServerRepository |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: unknown
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
