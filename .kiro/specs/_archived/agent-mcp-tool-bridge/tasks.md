# Tasks — Agent MCP Tool Bridge

## Task 1: McpToolAdapter Core (Req 1) — ALREADY IMPLEMENTED

- [x] 1.1 McpToolWrapper implements AgentTool interface with name, description, execute()
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt` (McpToolWrapper class)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (BaMcpToolWrapper class)
- [x] 1.2 execute() converts params to JsonObject and delegates to McpProtocolClient.callTool()
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManagerHelpers.kt` (McpToolNameResolver.toJsonObject)
- [x] 1.3 Successful McpToolCallResponse converted to ToolResult(success=true)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManagerHelpers.kt` (McpToolNameResolver.toToolResult)
- [x] 1.4 Error McpToolCallResponse converted to ToolResult(success=false)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManagerHelpers.kt` (toToolResult checks isError)
- [x] 1.5 Exception in callTool() caught and returned as ToolResult(success=false)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt` (McpToolWrapper.callMcpTool catch block)

## Task 2: Extract parameterNames from inputSchema (Req 1.6) — NEW

- [x] 2.1 Create `InputSchemaParser` object in `server/src/jvmMain/kotlin/com/assistant/server/agent/tool/InputSchemaParser.kt`
  - Implement `extractParameterNames(inputSchema: JsonElement): List<String>`
  - Parse the "properties" keys from the JSON Schema object
  - Return `emptyList()` if schema is not a JsonObject or missing "properties"
  - Keep under 40 lines (object + function + error handling)
- [x] 2.2 Update `McpToolWrapper` in `AgentMcpManager.kt` to accept `inputSchema: JsonElement` and use `InputSchemaParser` for `parameterNames`
  - Change `override val parameterNames: List<String> = emptyList()` to use `InputSchemaParser.extractParameterNames(inputSchema)`
  - Update `registerTools()` in `AgentMcpManager` to pass `inputSchema` from MCP tool discovery
  - Note: `McpToolDiscovery.discoverTools()` currently returns `List<Pair<String, String>>` (name, description) — needs to also return inputSchema
- [x] 2.3 Update `BaMcpToolWrapper` in `BAAgentModule.kt` to use `InputSchemaParser` for `parameterNames`
  - Pass `inputSchema` from `McpAggregatedTool.inputSchema` to the wrapper
  - Replace `override val parameterNames: List<String> = emptyList()` with parsed values
- [x] 2.4 Write property-based test for `InputSchemaParser.extractParameterNames()`
  - Generate random JSON Schema objects with varying "properties" keys
  - Verify extracted names match the "properties" keys exactly
  - Test edge cases: empty properties, no properties key, non-object schema
  - Tag: `Feature: agent-mcp-tool-bridge, Property 3: inputSchema parameter extraction`

## Task 3: Duplicate Name Collision Detection (Req 2.7) — NEW

- [x] 3.1 Create `McpCollisionDetector` object in `server/src/jvmMain/kotlin/com/assistant/server/agent/home/McpCollisionDetector.kt`
  - Implement `resolve(tools: List<McpAggregatedTool>): List<ResolvedTool>`
  - `ResolvedTool` data class: `registeredName`, `originalName`, `serverName`, `wasRenamed`
  - Group tools by name, prefix with `{serverName}_` only when collision detected
  - Log warning for each collision via SLF4J
  - Keep under 60 lines total
- [x] 3.2 Integrate `McpCollisionDetector` into `registerMcpToolsFromDatabase()` in `BAAgentModule.kt`
  - Before registering tools, run collision detection on the active tools list
  - Use `resolvedTool.registeredName` instead of hardcoded `mcp_${tool.serverName}_${tool.name}`
- [x] 3.3 Integrate `McpCollisionDetector` into `AgentMcpManager.registerTools()` if multiple servers are configured
  - Apply collision detection across all servers' tools before registration
  - Only needed when agent home directory has multiple MCP server configs
- [x] 3.4 Write property-based test for `McpCollisionDetector.resolve()`
  - Generate random tool lists with varying name collision patterns
  - Verify: unique names keep original, duplicates get server prefix
  - Verify: no two resolved tools have the same registeredName
  - Tag: `Feature: agent-mcp-tool-bridge, Property 5: Collision detection prefixes only duplicates`

## Task 4: listToolsWithSource() Method (Req 3.4) — NEW

- [x] 4.1 Create `ToolDescriptorWithSource` data class in `shared/src/commonMain/kotlin/com/assistant/agent/models/ToolModels.kt`
  - Fields: `name`, `description`, `parameterNames`, `toolSource: String`, `serverId: String?`, `serverName: String?`
  - Use `@Serializable` annotation
  - `toolSource` as String ("LOCAL", "AGENT_MCP", "SHARED_MCP") for cross-platform compatibility
- [x] 4.2 Add `listToolsWithSource()` method to `ToolRegistryImpl`
  - Maintain a `toolSourceMap: MutableMap<String, ToolSourceInfo>` alongside existing `tools` map
  - `ToolSourceInfo` data class: `source: ToolSource`, `serverId: String?`, `serverName: String?`
  - On `register()`, detect if tool is `McpToolWrapper` or `BaMcpToolWrapper` → record MCP source; else → LOCAL
  - `listToolsWithSource()` returns `List<ToolDescriptorWithSource>` by joining `tools` with `toolSourceMap`
- [x] 4.3 Add `listToolsWithSource()` to `ToolRegistry` interface in shared module
  - Add method signature: `fun listToolsWithSource(): List<ToolDescriptorWithSource>`
  - Default implementation returns `listTools().map { ToolDescriptorWithSource(it.name, it.description, it.parameterNames) }`
- [x] 4.4 Write property-based test for `listToolsWithSource()`
  - Register random mixes of local and MCP tools
  - Verify: every MCP tool has `toolSource = "SHARED_MCP"` or `"AGENT_MCP"`, every local tool has `toolSource = "LOCAL"`
  - Verify: result count matches total registered tools
  - Tag: `Feature: agent-mcp-tool-bridge, Property 6: Tool source metadata correctness`

## Task 5: Parameter Type Conversion (Req 8.2-8.3) — NEW

- [x] 5.1 Create `ParamTypeConverter` object in `server/src/jvmMain/kotlin/com/assistant/server/agent/tool/ParamTypeConverter.kt`
  - Implement `convert(params: Map<String, String>, inputSchema: JsonElement): JsonObject`
  - Parse inputSchema to find "properties" → each property's "type" field
  - For "integer"/"number" type: try `value.toLongOrNull()` then `value.toDoubleOrNull()`, fall back to string
  - For "boolean" type: convert "true"/"false" (case-insensitive) to `JsonPrimitive(boolean)`, fall back to string
  - For all other types or missing schema: keep as `JsonPrimitive(string)`
  - Log conversions at DEBUG level
  - Keep under 60 lines total
- [x] 5.2 Integrate `ParamTypeConverter` into `McpToolWrapper.callMcpTool()` in `AgentMcpManager.kt`
  - Replace `McpToolNameResolver.toJsonObject(params)` with `ParamTypeConverter.convert(params, inputSchema)`
  - Pass `inputSchema` to `McpToolWrapper` constructor (same change as Task 2.2)
- [x] 5.3 Integrate `ParamTypeConverter` into `BaMcpToolWrapper.execute()` in `BAAgentModule.kt`
  - Replace inline `JsonObject(params.mapValues { JsonPrimitive(it.value) })` with `ParamTypeConverter.convert(params, inputSchema)`
  - Pass `inputSchema` from `McpAggregatedTool` to wrapper
- [x] 5.4 Write property-based test for `ParamTypeConverter.convert()`
  - Generate random params with schemas specifying "integer", "number", "boolean", "string" types
  - Verify: valid numeric strings → JsonPrimitive number; valid boolean strings → JsonPrimitive boolean
  - Verify: invalid conversions → JsonPrimitive string (fallback)
  - Verify: missing schema → all values remain as strings
  - Tag: `Feature: agent-mcp-tool-bridge, Property 9: Schema-aware parameter type conversion`

## Task 6: Tool Name Mapping Configuration (Req 6) — NEW

- [x] 6.1 Create `ToolNameMapper` class in `server/src/jvmMain/kotlin/com/assistant/server/agent/tool/ToolNameMapper.kt`
  - `ToolNameMapping` data class: `agentName`, `serverName`, `mcpToolName`
  - Constructor accepts `Map<String, ToolNameMapping>` (keyed by agentName)
  - `resolve(agentName): ToolNameMapping?` — lookup mapping
  - `hasMapping(agentName): Boolean`
  - `getMcpName(agentName): String?` — shortcut for the MCP tool name
  - `companion object { fun fromConfig(config: Map<String, Map<String, String>>): ToolNameMapper }`
  - Keep under 50 lines
- [x] 6.2 Integrate `ToolNameMapper` into `registerMcpToolsFromDatabase()` in `BAAgentModule.kt`
  - Accept optional `ToolNameMapper` parameter
  - When mapping exists for an MCP tool: register with `agentName`, store `mcpToolName` for callTool()
  - When no mapping: use existing `mcp_{serverName}_{toolName}` naming
- [x] 6.3 Update `BaMcpToolWrapper` to support mapped names
  - Constructor accepts both `name` (agent-facing) and `originalToolName` (MCP-facing) — already does this
  - Ensure `callTool()` always uses `originalToolName`, not `name`
  - This is already implemented correctly in `BaMcpToolWrapper` — verify only
- [x] 6.4 Write property-based test for `ToolNameMapper` + name mapping round-trip
  - Generate random mapping configs
  - Verify: mapped tools register with agentName, call MCP with mcpToolName
  - Verify: unmapped tools use original MCP name
  - Tag: `Feature: agent-mcp-tool-bridge, Property 8: Tool name mapping round-trip`

## Task 7: MCP Tool Bridge Service & Auto-Registration (Req 2, 4) — ALREADY IMPLEMENTED

- [x] 7.1 McpToolBridge discovers tools from McpProcessManager.getActiveTools()
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (registerMcpToolsFromDatabase)
- [x] 7.2 McpToolBridge queries getClient(serverId) for each server
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (processManager.getClient)
- [x] 7.3 One adapter per discovered MCP tool
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (BaMcpToolWrapper per tool)
- [x] 7.4 Null client handling — skip tools from unavailable servers
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (client null check in execute)
- [x] 7.5 AgentMcpManager registered as factory in AgentModule
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/di/AgentModule.kt`

## Task 8: Logging and Observability (Req 9) — ALREADY IMPLEMENTED

- [x] 8.1 Discovery summary logging
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt` (logger.info after registerTools)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (logger.info after registration)
- [x] 8.2 Tool execution logging (name, server, params)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt` (McpToolWrapper.execute logger.info)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt` (BaMcpToolWrapper.execute logger.info)
- [x] 8.3 Error logging for failed MCP calls
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/home/AgentMcpManager.kt` (McpToolWrapper.callMcpTool logger.error)

## Task 9: ToolSource Enum and Classification (Req 3.1-3.3) — ALREADY IMPLEMENTED

- [x] 9.1 ToolSource enum (LOCAL, AGENT_MCP, SHARED_MCP)
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessProxyHelpers.kt`
- [x] 9.2 resolveToolSource() prefix-based classification
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessProxyHelpers.kt`
