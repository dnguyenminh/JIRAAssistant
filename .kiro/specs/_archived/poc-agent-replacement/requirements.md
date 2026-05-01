# Requirements Document

## Introduction

This document specifies the requirements for replacing the current BA agent's `CliInteractiveStrategy` with the proven POC architecture from `poc/AgentCLI-Interaction/`. The current implementation suffers from reliability issues due to its single-process interactive stdin/stdout loop approach. The POC introduces a transport-agnostic `AiBackend` interface hierarchy supporting both CLI-based backends (Gemini, Copilot, Kiro) and API-based backends (Ollama), with stateless and persistent process modes. The replacement must be a drop-in substitution implementing the existing `PipelineStrategy` interface while preserving all integration points (`SubprocessProxy`, `ProgressReporter`, `BATaskResult`, `BATaskConfig`).

## Glossary

- **AiBackend**: Transport-agnostic interface for all AI backends, supporting stateless and session modes
- **AiCliClient**: CLI-specific extension of AiBackend with process mode (STATELESS/PERSISTENT) and CLI type
- **AiApiClient**: API-specific extension of AiBackend with baseUrl and model properties
- **BaseNodeCliClient**: Abstract base class handling Node.js CLI process spawning, stdin/stdout I/O, and path resolution
- **NodeCliPathResolver**: Utility that finds Node.js and CLI JS entry point paths across Windows/Unix
- **PipelineStrategy**: Existing strategy interface (`execute(config, progressReporter): BATaskResult`) that the new implementation must satisfy
- **SubprocessProxy**: Existing proxy that routes tool calls to the ToolRegistry for execution
- **ProgressReporter**: Existing interface for reporting phase transitions, progress percentages, and tool call status
- **BATaskConfig**: Existing configuration containing rootTicketId, docType, maxToolCalls, taskTimeoutSeconds, cliBackend
- **BATaskResult**: Existing result containing document, toolCallsExecuted, toolCallsFailed, totalDurationMs, status, toolCallLog
- **CliBackendResolver**: Existing resolver that maps backend names to SubprocessConfig via SettingsRepository and ProviderConfigRepository
- **AgenticLoop**: The iterative cycle where the AI requests tool calls, receives results, and eventually produces a final document
- **STATELESS_Mode**: Process mode where each AI interaction spawns a new process; full context resent each iteration
- **PERSISTENT_Mode**: Process mode where the CLI uses `--resume latest` or session history to maintain conversation context across spawns
- **ToolCallProtocol_POC**: The POC's JSON tool protocol using `{"type":"tool_call","tool":"...","params":{...}}` format
- **OllamaNativeToolCalling**: Ollama's built-in tool_calls mechanism using JSON Schema tool definitions in the request body
- **BASubprocessOrchestrator**: Existing orchestrator that delegates to a PipelineStrategy for document generation

## Requirements

### Requirement 1: AiBackend Interface Hierarchy

**User Story:** As a developer, I want a transport-agnostic AI backend interface hierarchy ported from the POC, so that the BA agent can interact with CLI and API backends through a unified contract.

#### Acceptance Criteria

1. THE AiBackend interface SHALL define stateless mode via `sendPrompt(prompt: String): AiCliResponse`
2. THE AiBackend interface SHALL define session mode via `startSession()`, `sendMessage(message: String): AiCliResponse`, `endSession()`, and `isSessionActive(): Boolean`
3. THE AiBackend interface SHALL define tool handling via `isToolCall(response: String): Boolean` and `parseToolCall(response: String): ToolRequest?`
4. THE AiBackend interface SHALL define availability checking via `isInstalled(): Boolean` and `getInstallInstructions(): String`
5. THE AiCliClient interface SHALL extend AiBackend with `type: AiCliType` and `processMode: ProcessMode` properties
6. THE AiApiClient interface SHALL extend AiBackend with `baseUrl: String` and `model: String` properties
7. THE AiCliResponse data class SHALL contain `response: String`, `sessionId: String?`, `rawJson: String?`, and `metadata: Map<String, String>`

### Requirement 2: NodeCliPathResolver Cross-Platform Path Detection

**User Story:** As a developer, I want automatic CLI path detection across Windows and Unix, so that the system finds Node.js and CLI JS entry points without manual configuration.

#### Acceptance Criteria

1. THE NodeCliPathResolver SHALL find the Node.js executable path using OS-specific commands (`where` on Windows, `which` on Unix)
2. THE NodeCliPathResolver SHALL find CLI JS entry points by reading script wrappers and extracting JS paths via configurable regex patterns
3. THE NodeCliPathResolver SHALL infer JS paths from script locations by checking relative `node_modules` paths
4. THE NodeCliPathResolver SHALL fall back to common global npm installation paths when script-based resolution fails
5. WHEN the Node.js executable is not found via OS commands, THE NodeCliPathResolver SHALL fall back to `"node"` from PATH
6. THE NodeCliPathResolver SHALL support Windows-specific paths including Scoop, nvm-windows, and `%APPDATA%/npm`
7. THE NodeCliPathResolver SHALL support Unix-specific paths including nvm, npm-global, and `/usr/local/lib`

### Requirement 3: BaseNodeCliClient Process Management

**User Story:** As a developer, I want a base class that handles Node.js CLI process spawning with both stateless and persistent modes, so that concrete CLI clients only define backend-specific command arguments.

#### Acceptance Criteria

1. WHEN operating in STATELESS mode, THE BaseNodeCliClient SHALL spawn a new process per `sendPrompt()` call, write the prompt to stdin, close stdin, read all stdout, and return the response after process exit
2. WHEN operating in PERSISTENT mode, THE BaseNodeCliClient SHALL spawn a process with stream-json output format, read NDJSON events line-by-line, and detect completion via `"type":"result"` events
3. WHEN a subsequent message is sent in PERSISTENT mode, THE BaseNodeCliClient SHALL include resume flags (e.g., `--resume latest`) so the CLI loads prior conversation context
4. THE BaseNodeCliClient SHALL read stderr in a background thread to prevent process blocking
5. IF a process exceeds the configured timeout, THEN THE BaseNodeCliClient SHALL force-kill the process and report a timeout error
6. THE BaseNodeCliClient SHALL use NodeCliPathResolver to lazily resolve `nodePath` and `cliJsPath` at first use
7. THE BaseNodeCliClient SHALL parse tool calls from responses using the POC tool protocol format `{"type":"tool_call","tool":"...","params":{...}}`

### Requirement 4: Gemini CLI Backend

**User Story:** As a developer, I want a Gemini CLI backend implementation, so that the BA agent can use `@google/gemini-cli` for document generation.

#### Acceptance Criteria

1. THE GeminiCliClient SHALL extend BaseNodeCliClient with `type = GEMINI` and `displayName = "Gemini CLI"`
2. THE GeminiCliClient SHALL configure NodeCliConfig with `commandName = "gemini"`, `npmPackage = "@google/gemini-cli"`, and `jsEntryPath = "bundle/gemini.js"`
3. WHEN in STATELESS mode, THE GeminiCliClient SHALL pass arguments `-p "" --output-format json --sandbox` and append `-m <model>` when a model is configured. The `--sandbox` flag disables Gemini CLI's built-in tools so the AI uses the POC JSON tool protocol instead.
4. WHEN in PERSISTENT mode, THE GeminiCliClient SHALL pass arguments `-p "" --output-format json --sandbox` (NOT stream-json), append `-m <model>` when configured, and add `--resume latest` for subsequent messages. The GeminiCliClient SHALL override `sendMessage()` to use stateless execution with persistent args, because stream-json mode causes Gemini CLI to process tool calls internally.
5. THE GeminiCliClient SHALL detect installation by checking if the CLI JS entry point exists on disk

### Requirement 5: Copilot CLI Backend

**User Story:** As a developer, I want a GitHub Copilot CLI backend implementation, so that the BA agent can use `@github/copilot` for document generation.

#### Acceptance Criteria

1. THE CopilotCliClient SHALL extend BaseNodeCliClient with `type = COPILOT` and `displayName = "GitHub Copilot CLI"`
2. THE CopilotCliClient SHALL configure NodeCliConfig with `commandName = "copilot"`, `npmPackage = "@github/copilot"`, and `jsEntryPath = "index.js"`
3. WHEN in STATELESS mode, THE CopilotCliClient SHALL pass arguments `-p "" -s --allow-all-tools`
4. WHEN in PERSISTENT mode, THE CopilotCliClient SHALL add `--continue` flag for subsequent messages to resume prior sessions
5. THE CopilotCliClient SHALL treat all responses as plain text since Copilot CLI does not support `--output-format json`

### Requirement 6: Kiro CLI Backend

**User Story:** As a developer, I want a Kiro CLI backend placeholder implementation, so that the system is prepared for Kiro CLI availability.

#### Acceptance Criteria

1. THE KiroCliClient SHALL extend BaseNodeCliClient with `type = KIRO` and `displayName = "Kiro CLI"`
2. THE KiroCliClient SHALL configure NodeCliConfig with `commandName = "kiro-cli"` and `npmPackage = "@amazon/kiro-cli"`
3. THE KiroCliClient SHALL return `false` from `isInstalled()` when the CLI JS entry point does not exist on disk

### Requirement 7: Ollama API Backend with Native Tool Calling

**User Story:** As a developer, I want an Ollama REST API backend with native tool calling support, so that the BA agent can use local LLMs for document generation without the JSON text protocol.

#### Acceptance Criteria

1. THE OllamaApiClient SHALL implement AiApiClient with configurable `baseUrl` and `model` properties
2. THE OllamaApiClient SHALL send tool definitions in Ollama's native JSON Schema format via the `tools` field in chat requests
3. WHEN the Ollama response contains `tool_calls` in the message, THE OllamaApiClient SHALL parse them into ToolRequest objects
4. THE OllamaApiClient SHALL support streaming responses by reading NDJSON chunks and accumulating content until `done: true`
5. THE OllamaApiClient SHALL maintain conversation history in session mode by appending user, assistant, and tool messages
6. WHEN sending a tool result back, THE OllamaApiClient SHALL append a message with `role = "tool"` containing the result to conversation history
7. THE OllamaApiClient SHALL check server availability by sending a GET request to the base URL
8. IF the Ollama server is unreachable, THEN THE OllamaApiClient SHALL throw a descriptive error with connection instructions

### Requirement 8: Ollama Tool Definition Converter

**User Story:** As a developer, I want automatic conversion from application tool descriptors to Ollama's native tool format, so that tools are correctly registered with the Ollama API.

#### Acceptance Criteria

1. THE OllamaToolConverter SHALL convert application ToolDescriptor objects to OllamaTool objects with JSON Schema parameter definitions
2. THE OllamaToolConverter SHALL map parameter type strings to JSON Schema types: `"string"` → `"string"`, `"integer"/"int"` → `"integer"`, `"number"/"double"/"float"` → `"number"`, `"boolean"/"bool"` → `"boolean"`
3. WHEN a parameter type is unrecognized, THE OllamaToolConverter SHALL default to `"string"`

### Requirement 9: AiBackend-Based PipelineStrategy Implementation

**User Story:** As a developer, I want a new PipelineStrategy implementation that uses the POC's AiBackend-based agentic loop, so that it replaces CliInteractiveStrategy as a drop-in substitution.

#### Acceptance Criteria

1. THE AiBackendPipelineStrategy SHALL implement the PipelineStrategy interface with `execute(config: BATaskConfig, progressReporter: ProgressReporter): BATaskResult`
2. THE AiBackendPipelineStrategy SHALL select the appropriate AiBackend based on `BATaskConfig.cliBackend` (gemini, copilot, kiro → CLI clients; ollama → API client)
3. THE AiBackendPipelineStrategy SHALL auto-select stateless vs persistent mode: API backends always use session mode; CLI backends default to STATELESS mode, which allows the agentic loop to intercept tool calls from the AI response text between process invocations
4. THE AiBackendPipelineStrategy SHALL run an agentic loop that sends prompts, detects tool calls, executes tools via SubprocessProxy, and sends results back until the AI produces a final document
5. THE AiBackendPipelineStrategy SHALL enforce `BATaskConfig.maxToolCalls` by stopping tool execution and requesting final document output when the limit is reached
6. THE AiBackendPipelineStrategy SHALL enforce `BATaskConfig.taskTimeoutSeconds` by terminating the loop and returning a TIMEOUT status when exceeded
7. IF the AI backend is not installed, THEN THE AiBackendPipelineStrategy SHALL return a FAILED BATaskResult with installation instructions in the document field

### Requirement 10: Tool Execution Bridge to SubprocessProxy, McpProcessManager, and InternalMcpBridge

**User Story:** As a developer, I want tool calls from the agentic loop routed through the existing SubprocessProxy, McpProcessManager, or InternalMcpBridge, so that all registered tools (local, MCP bridge, agent MCP, internal Jira/KB tools) remain available.

#### Acceptance Criteria

1. WHEN the agentic loop detects a tool call, THE Tool_Execution_Bridge SHALL convert the POC ToolRequest format to a SubprocessProxy ToolCallRequest with a generated correlation UUID
2. WHEN the tool name starts with `mcp_`, THE Tool_Execution_Bridge SHALL first check internal tools via `InternalMcpBridge.getAggregatedTools()`, matching by full prefixed name `mcp_{serverName}_{toolName}`. If matched, it SHALL route the call through `InternalMcpBridge.callTool()` with `userId = "ba-agent"` and `userRole = "ADMINISTRATOR"` (matching the `UserRole` enum exactly).
3. WHEN the tool name starts with `mcp_` and no internal match is found, THE Tool_Execution_Bridge SHALL check external tools via `McpProcessManager.getActiveTools()` and route to `McpProcessManager.getClient(serverId).callTool()`
4. WHEN no MCP match is found, THE Tool_Execution_Bridge SHALL fall back to `SubprocessProxy.handleToolCallRequest()`
5. FOR CLI backends, THE Tool_Execution_Bridge SHALL format tool results as POC protocol JSON: `{"type":"tool_result","tool":"...","success":true/false,"data":{...},"error":"..."}`
6. THE Tool_Execution_Bridge SHALL report each tool call to ProgressReporter via `reportToolCall(toolName, status)`
7. THE Tool_Execution_Bridge SHALL record each tool call as a ToolCallLogEntry with name, duration, success, and response size

### Requirement 11: Progress Reporting Integration

**User Story:** As a developer, I want the new strategy to report progress through the existing ProgressReporter, so that the frontend progress UI remains functional.

#### Acceptance Criteria

1. WHEN the agentic loop starts, THE AiBackendPipelineStrategy SHALL report progress at 5% with message "AI backend starting"
2. WHEN the initial prompt is sent, THE AiBackendPipelineStrategy SHALL report progress at 10% with message "Prompt sent"
3. WHILE tool calls are being executed, THE AiBackendPipelineStrategy SHALL report incremental progress between 10% and 90% proportional to tool calls executed vs maxToolCalls
4. WHEN the final document is received, THE AiBackendPipelineStrategy SHALL report progress at 90% with message "Document received"
5. WHEN execution completes, THE AiBackendPipelineStrategy SHALL report progress at 100% with message "Complete"

### Requirement 12: BATaskResult Construction

**User Story:** As a developer, I want the new strategy to produce BATaskResult objects compatible with the existing system, so that downstream consumers (API endpoints, frontend) work without changes.

#### Acceptance Criteria

1. THE AiBackendPipelineStrategy SHALL return BATaskResult with `status = SUCCESS` when the document is generated and all tool calls succeeded
2. THE AiBackendPipelineStrategy SHALL return BATaskResult with `status = PARTIAL` when the document is generated but some tool calls failed
3. THE AiBackendPipelineStrategy SHALL return BATaskResult with `status = TIMEOUT` when the task exceeded taskTimeoutSeconds
4. THE AiBackendPipelineStrategy SHALL return BATaskResult with `status = FAILED` when no document is produced or the backend cannot start
5. THE AiBackendPipelineStrategy SHALL populate `toolCallLog` with ordered ToolCallLogEntry records for every tool call executed
6. THE AiBackendPipelineStrategy SHALL measure and report `totalDurationMs` from strategy entry to exit

### Requirement 13: Orchestrator Wiring

**User Story:** As a developer, I want the BASubprocessOrchestrator to use the new AiBackendPipelineStrategy as default, so that the POC approach replaces CliInteractiveStrategy.

#### Acceptance Criteria

1. THE BASubprocessOrchestrator SHALL use AiBackendPipelineStrategy as the default PipelineStrategy in `createDefaultStrategy()`
2. THE BASubprocessOrchestrator SHALL retain MultiTurnPipelineStrategy as a fallback via the `createMultiTurnStrategy()` companion method
3. THE BASubprocessOrchestrator SHALL continue to support strategy injection via constructor parameter for testing

### Requirement 14: AiBackend Factory and Backend Resolution

**User Story:** As a developer, I want a factory that creates the correct AiBackend based on the configured backend name, so that backend selection is centralized and extensible.

#### Acceptance Criteria

1. THE AiBackendFactory SHALL create GeminiCliClient when `cliBackend = "gemini"`
2. THE AiBackendFactory SHALL create CopilotCliClient when `cliBackend = "copilot"`
3. THE AiBackendFactory SHALL create KiroCliClient when `cliBackend = "kiro"`
4. THE AiBackendFactory SHALL create OllamaApiClient when `cliBackend = "ollama"`, using model from CliBackendResolver.resolveModel()
5. THE AiBackendFactory SHALL resolve model names via `CliBackendResolver.resolveModel()`, which checks ProviderConfigRepository first, then SettingsRepository as fallback
6. WHEN a backend name is not in the supported set, THE AiBackendFactory SHALL return a failure result with a descriptive error

### Requirement 15: Prompt Building for Agentic Loop

**User Story:** As a developer, I want prompt building that works with the agentic loop's stateless and persistent modes, so that the AI receives proper context and tool definitions.

#### Acceptance Criteria

1. THE PromptBuilder SHALL build an initial prompt containing role instructions, available tool definitions, tool protocol instructions, document template structure, and the target ticket ID
2. THE PromptBuilder SHALL obtain tools from `InternalMcpBridge.getAggregatedTools()` + `McpProcessManager.getActiveTools()`, falling back to `SubprocessProxy.getAvailableToolDescriptors()` only when no MCP tools are available
3. THE PromptBuilder SHALL NOT use a hardcoded whitelist to filter tools. Instead, it SHALL pass all tools to the AI (minus a small blacklist of clearly irrelevant tools: playwright, browser, convert_to_markdown), letting the AI model choose which tools to call.
4. FOR stateless mode iterations after the first, THE PromptBuilder SHALL rebuild the full prompt including all previously collected tool results, with tools filtered only by the exclusion blacklist
5. FOR persistent mode iterations after the first, THE PromptBuilder SHALL build a minimal message containing only the latest tool result, since the AI retains prior context
6. THE PromptBuilder SHALL include tool definitions formatted with the POC tool protocol for CLI backends
