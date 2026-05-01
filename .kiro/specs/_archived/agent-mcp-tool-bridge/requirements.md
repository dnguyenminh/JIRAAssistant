# Requirements Document — Agent MCP Tool Bridge

## Introduction

Feature này chuyển tất cả agents trong Generic Agent Framework sang sử dụng MCP tools thay vì direct API clients. Hiện tại, BA Agent sử dụng các `AgentTool` implementations (FetchJiraDetailsTool, GetLinkedIssuesTool, v.v.) gọi trực tiếp vào `JiraClient`, `KBRepository`, `VectorStore` — gây ra các vấn đề:

1. **Credential timing**: `JiraClient` được inject qua Koin factory — tại thời điểm startup có thể resolve thành `NoOpJiraClient` nếu credentials chưa sẵn sàng
2. **Tight coupling**: Mỗi tool gắn chặt với một API client cụ thể, không tái sử dụng được
3. **MCP server đã có sẵn**: MCP Jira server đang chạy với 45 tools và auth đã cấu hình đúng, nhưng agents không thể sử dụng
4. **Manual wiring**: Mỗi agent mới cần wiring thủ công trong Koin module riêng

Giải pháp: Xây dựng một MCP Tool Bridge layer tại tầng Generic Agent Framework, cho phép `ToolRegistry` route tool calls qua MCP servers đang chạy. Agents vẫn gọi `tools.invoke("fetchJiraDetails", params)` nhưng execution đi qua MCP protocol — transparent, không cần thay đổi agent code.

### Mối quan hệ với Generic Agent Framework — MCP Integration (Requirement 19)

Generic Agent Framework (spec `generic-agent-framework`) đã được cập nhật với Requirement 19 (MCP Integration) mô tả việc auto-discovery MCP servers từ **Agent Home Directory** (`.agent/mcp/`). Hai cơ chế MCP integration bổ sung cho nhau:

- **Agent Home Directory MCP** (Framework Req 19): Đọc MCP server configs từ file system trong Agent Home Directory, khởi động MCP servers khi agent được khởi tạo, và tự động register tools. Phù hợp cho **agent-specific MCP servers** được cấu hình per-agent.
- **MCP Tool Bridge** (spec này): Discover MCP tools từ **McpProcessManager** — các MCP servers đã được đăng ký và quản lý qua Integrations UI (spec `mcp-servers`). Phù hợp cho **shared MCP servers** (Jira, markitdown, playwright) mà tất cả agents đều sử dụng.

Khi cả hai cơ chế hoạt động đồng thời, tool registration priority là: Local tools > Agent Home Directory MCP tools > Shared MCP Bridge tools. Điều này đảm bảo agent-specific tools luôn override shared tools.

## Glossary

- **Generic_Agent_Framework**: Framework domain-agnostic cung cấp nền tảng cho tất cả specialized agents — bao gồm `ToolRegistry`, `ThinkingLoopEngine`, `OrchestratorBackend`, `StructuredMemory`, và `AgentRegistry`. Code nằm trong `shared/.../agent/` (interfaces) và `server/.../server/agent/` (implementations).
- **ToolRegistry**: Interface quản lý registration, discovery, và invocation của agent tools. Implementation hiện tại là `ToolRegistryImpl`.
- **AgentTool**: Interface cho một tool mà agent có thể invoke — khai báo `name`, `description`, `parameterNames`, và `execute()` function.
- **MCP_Server**: Một external process giao tiếp qua JSON-RPC 2.0 (stdio transport), cung cấp tools qua MCP protocol. Ví dụ: Jira MCP server (45 tools), markitdown, playwright.
- **McpProcessManager**: Singleton quản lý lifecycle của MCP server processes — start, stop, health check, auto-restart. Cung cấp `getActiveTools()` và `getClient(configId)`.
- **McpProtocolClient**: JSON-RPC 2.0 client giao tiếp với một MCP server cụ thể — hỗ trợ `listTools()`, `callTool(name, arguments)`.
- **McpAggregatedTool**: Data class chứa tool info kèm server metadata (`serverId`, `serverName`, `name`, `description`, `inputSchema`).
- **MCP_Tool_Bridge**: Component mới sẽ được xây dựng — adapter layer chuyển đổi MCP tools thành `AgentTool` instances để register vào `ToolRegistry`.
- **McpToolAdapter**: Implementation của `AgentTool` interface, delegate execution sang `McpProtocolClient.callTool()`.
- **ToolSource**: Enum phân biệt nguồn gốc tool — `LOCAL` (direct API client) hoặc `MCP` (qua MCP server).
- **BA_Agent**: BA Document Agent — agent chuyên biệt đầu tiên, hiện dùng 6 direct API tools (fetchJiraDetails, getLinkedIssues, fetchComments, lookupKBRecord, searchKB, processAttachment).
- **Direct_API_Tool**: AgentTool implementation gọi trực tiếp vào API client (JiraClient, KBRepository, VectorStore) — pattern hiện tại cần được thay thế.

## Requirements

### Requirement 1: MCP Tool Adapter

**User Story:** As a framework developer, I want a generic adapter that wraps any MCP tool as an AgentTool, so that agents can invoke MCP tools through the existing ToolRegistry interface without knowing the underlying transport.

#### Acceptance Criteria

1. THE McpToolAdapter SHALL implement the AgentTool interface with `name`, `description`, `parameterNames`, and `execute()` method
2. WHEN McpToolAdapter.execute() is called with a params map, THE McpToolAdapter SHALL convert the params to a JsonObject and delegate to McpProtocolClient.callTool(name, arguments)
3. WHEN McpProtocolClient.callTool() returns a successful McpToolCallResponse, THE McpToolAdapter SHALL convert the response content into a ToolResult with `success = true` and the text content as `data`
4. WHEN McpProtocolClient.callTool() returns an error McpToolCallResponse (isError = true), THE McpToolAdapter SHALL return a ToolResult with `success = false`, `errorType = "MCP_ERROR"`, and the error content as `errorMessage`
5. IF McpProtocolClient.callTool() throws an exception (network error, timeout, process crash), THEN THE McpToolAdapter SHALL catch the exception and return a ToolResult with `success = false`, `errorType = "MCP_TRANSPORT_ERROR"`, and the exception message as `errorMessage`
6. THE McpToolAdapter SHALL extract `parameterNames` from the MCP tool's `inputSchema` JSON (parsing the "properties" keys from the JSON Schema object)

### Requirement 2: MCP Tool Bridge Service

**User Story:** As a framework developer, I want a bridge service that discovers running MCP servers and registers their tools into a ToolRegistry, so that any agent can access MCP tools without manual wiring.

#### Acceptance Criteria

1. THE McpToolBridge SHALL accept a McpProcessManager and provide a method to discover all active MCP tools from all running MCP servers
2. WHEN McpToolBridge.discoverTools() is called, THE McpToolBridge SHALL query McpProcessManager.getActiveTools() to get the list of McpAggregatedTool from all running servers
3. WHEN McpToolBridge.discoverTools() is called, THE McpToolBridge SHALL query McpProcessManager.getClient(serverId) for each unique serverId to obtain the McpProtocolClient needed for tool execution
4. THE McpToolBridge SHALL create one McpToolAdapter instance per discovered MCP tool, binding each adapter to the correct McpProtocolClient for its server
5. WHEN an MCP server has no running client (getClient returns null), THE McpToolBridge SHALL skip all tools from that server and log a warning with the serverId
6. THE McpToolBridge SHALL provide a method `registerInto(toolRegistry: ToolRegistry)` that registers all discovered McpToolAdapter instances into the given ToolRegistry
7. WHEN multiple MCP servers expose tools with the same name, THE McpToolBridge SHALL prefix the tool name with the server name (format: `{serverName}_{toolName}`) to avoid collisions, and log a warning about the duplicate

### Requirement 3: ToolRegistry Enhancement for Tool Source Tracking

**User Story:** As a framework developer, I want the ToolRegistry to track whether each tool comes from a local implementation or an MCP server, so that debugging and monitoring can distinguish tool sources.

#### Acceptance Criteria

1. THE ToolRegistryImpl SHALL maintain metadata about each registered tool's source — either LOCAL (direct AgentTool implementation) or MCP (via McpToolAdapter)
2. WHEN a McpToolAdapter is registered, THE ToolRegistryImpl SHALL record the tool source as MCP along with the serverId and serverName
3. WHEN a non-McpToolAdapter AgentTool is registered, THE ToolRegistryImpl SHALL record the tool source as LOCAL
4. THE ToolRegistryImpl SHALL provide a method `listToolsWithSource()` that returns ToolDescriptor objects enriched with source metadata (toolSource, serverId, serverName)
5. WHEN ToolRegistryImpl.invoke() executes a tool, THE ToolRegistryImpl SHALL include the tool source in the log message (e.g., "Tool [fetchJiraDetails] source=MCP server=jira params=... time=...ms")

### Requirement 4: Automatic MCP Tool Registration in AgentModule

**User Story:** As a framework developer, I want MCP tools to be automatically registered into every agent's ToolRegistry at creation time, so that all agents benefit from MCP tools without per-agent wiring code.

#### Acceptance Criteria

1. THE AgentModule SHALL register McpToolBridge as a singleton in Koin, injecting McpProcessManager as its dependency
2. WHEN a new ToolRegistry is created via the Koin factory, THE AgentModule SHALL automatically invoke McpToolBridge.registerInto(toolRegistry) to populate the registry with all available MCP tools
3. WHEN McpToolBridge.registerInto() is called during ToolRegistry creation, THE ToolRegistry SHALL contain all MCP tools from all running servers in addition to any locally registered tools
4. WHEN a locally registered tool has the same name as an MCP tool, THE locally registered tool SHALL take precedence (local override), and the MCP tool SHALL be skipped with a logged info message
5. IF McpToolBridge.discoverTools() fails (McpProcessManager unavailable, no servers running), THEN THE AgentModule SHALL create the ToolRegistry without MCP tools and log a warning, ensuring agents can still function with locally registered tools only

### Requirement 5: BA Agent Migration to MCP Tools

**User Story:** As a developer, I want the BA Agent to use MCP Jira tools instead of direct JiraClient calls, so that it benefits from the MCP server's proper auth handling and avoids the NoOpJiraClient timing issue.

#### Acceptance Criteria

1. WHEN the BA Agent's ToolRegistry is created, THE ToolRegistry SHALL contain MCP Jira tools (from the running Jira MCP server) automatically via the McpToolBridge
2. WHEN the BA Agent needs to fetch Jira ticket details, THE BA Agent SHALL invoke the tool through ToolRegistry.invoke() which routes to the MCP Jira server, instead of calling JiraClient directly
3. THE BA Agent's BAAgentModule SHALL remove the wireBATools() function that manually creates FetchJiraDetailsTool, GetLinkedIssuesTool, and FetchCommentsTool with direct JiraClient injection
4. THE BA Agent SHALL retain locally registered tools for non-Jira operations (LookupKBRecordTool, SearchKBTool, ProcessAttachmentTool) until those services also have MCP server equivalents
5. WHEN the MCP Jira server is not running, THE BA Agent SHALL fall back to locally registered Jira tools (if available) or report tool-not-found errors via ToolResult, triggering the existing error handling strategy (RETRY or SKIP)

### Requirement 6: Tool Name Mapping Configuration

**User Story:** As a framework developer, I want a configurable mapping between agent tool names and MCP tool names, so that agents can use semantic tool names (e.g., "fetchJiraDetails") while the bridge routes to the correct MCP tool (e.g., "get_issue").

#### Acceptance Criteria

1. THE McpToolBridge SHALL support a tool name mapping configuration that maps agent-facing tool names to MCP server tool names (e.g., "fetchJiraDetails" → server "jira", tool "get_issue")
2. WHEN a tool name mapping is configured, THE McpToolBridge SHALL register the McpToolAdapter with the agent-facing name (not the MCP tool name), so agents continue using their existing tool names
3. WHEN a tool name mapping is configured, THE McpToolAdapter SHALL use the MCP tool name when calling McpProtocolClient.callTool(), translating transparently between the two naming conventions
4. THE tool name mapping SHALL be configurable per agent type, allowing different agents to map different subsets of MCP tools with different names
5. WHEN no mapping is configured for a tool, THE McpToolBridge SHALL register the MCP tool with its original MCP name (no transformation)

### Requirement 7: MCP Tool Health and Availability

**User Story:** As a framework developer, I want the bridge to handle MCP server unavailability gracefully, so that agents degrade gracefully when MCP servers go down during execution.

#### Acceptance Criteria

1. WHEN an MCP server becomes unavailable during agent execution (process crash, restart), THE McpToolAdapter SHALL detect the failure on the next callTool() invocation and return a ToolResult with `success = false` and `errorType = "MCP_SERVER_UNAVAILABLE"`
2. WHEN McpToolAdapter detects an MCP server is unavailable, THE McpToolAdapter SHALL log a warning with the serverId, tool name, and error details
3. THE McpToolBridge SHALL provide a method `refreshTools()` that re-discovers tools from McpProcessManager, updating the internal tool list to reflect servers that have started or stopped since the last discovery
4. WHEN McpToolBridge.refreshTools() detects new tools from a newly started server, THE McpToolBridge SHALL make those tools available for registration into new ToolRegistry instances (existing registries are not modified)
5. IF all MCP servers are unavailable, THEN THE McpToolBridge SHALL return an empty tool list from discoverTools() and log a warning, allowing agents to operate with locally registered tools only

### Requirement 8: Parameter Format Conversion

**User Story:** As a framework developer, I want the bridge to handle parameter format differences between AgentTool (Map<String, String>) and MCP tools (JsonObject), so that the conversion is transparent and lossless.

#### Acceptance Criteria

1. WHEN McpToolAdapter.execute() receives a params Map<String, String>, THE McpToolAdapter SHALL convert each entry to a JsonObject where each value is a JsonPrimitive string
2. WHEN the MCP tool's inputSchema specifies a parameter type as "integer" or "number", THE McpToolAdapter SHALL attempt to convert the string value to the appropriate JSON number type before calling callTool()
3. WHEN the MCP tool's inputSchema specifies a parameter type as "boolean", THE McpToolAdapter SHALL convert string values "true"/"false" to JSON boolean primitives
4. IF a parameter value cannot be converted to the schema-specified type (e.g., "abc" for an integer field), THEN THE McpToolAdapter SHALL pass the value as a string and let the MCP server handle validation
5. WHEN McpToolCallResponse contains multiple content items, THE McpToolAdapter SHALL concatenate all text content items with newline separators into a single ToolResult.data string

### Requirement 9: Logging and Observability

**User Story:** As an operator, I want comprehensive logging for MCP tool bridge operations, so that I can diagnose issues with tool discovery, registration, and execution.

#### Acceptance Criteria

1. WHEN McpToolBridge.discoverTools() completes, THE McpToolBridge SHALL log a summary: total tools discovered, tools per server, and any skipped servers (with reasons)
2. WHEN McpToolAdapter.execute() completes (success or failure), THE McpToolAdapter SHALL log: tool name, MCP server name, execution time in milliseconds, response size in chars, and success/failure status
3. WHEN a tool name collision is detected during registration, THE McpToolBridge SHALL log a warning with both the existing tool source and the new tool source
4. WHEN parameter type conversion is performed (string to number/boolean), THE McpToolAdapter SHALL log at DEBUG level: parameter name, original string value, converted type
5. THE McpToolBridge SHALL log at INFO level when MCP tool registration is skipped due to local tool override (Requirement 4, AC 4)
