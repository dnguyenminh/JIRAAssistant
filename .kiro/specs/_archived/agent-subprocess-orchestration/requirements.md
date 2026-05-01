# Requirements Document — Agent Subprocess Orchestration

> **Updated 2025:** This document has been updated to reflect the [legacy-pipeline-removal spec](./../legacy-pipeline-removal/requirements.md). The legacy 4-phase pipeline, feature flags, and all fallback chains have been completely removed. Subprocess orchestration is now the single execution path.

## Introduction

The BA agent currently uses a fundamentally flawed architecture: it self-collects data through hardcoded phases (CollectPhase, ExpandPhase, VisualizePhase, SynthesizePhase), builds a giant prompt via MasterPromptBuilder, then fires-and-forgets to GeminiCliAgent which spawns a new process per call. The AI service gets one shot with whatever data the agent pre-collected — it has no ability to request additional data on demand.

This spec redesigns the BA agent to use the **Agent-as-Subprocess** architecture already defined in Generic Agent Framework Requirements 13–20. The correct architecture: the BA agent manages a long-lived AI subprocess (Gemini CLI, Copilot CLI, Kiro CLI, etc.), gives it a task with initial context and available tools, then serves tool call requests from the AI service on demand. The AI service decides what data it needs, requests tools through stdout, and the agent proxies those calls through ToolRegistry (which includes all MCP tools from the Integrations page).

This eliminates the rigid 4-phase data collection pipeline, replaces it with an AI-driven tool call loop, and dramatically simplifies the BA agent codebase while making it more capable.

## Glossary

- **BA_Subprocess_Orchestrator**: The new component that manages the long-lived AI subprocess for the BA agent — starts/reuses the subprocess, sends tasks, listens for tool call requests, proxies tool calls through ToolRegistry, and collects the final response
- **AI_Subprocess**: A long-lived CLI process (Gemini CLI, Copilot CLI, Kiro CLI, Ollama CLI) that receives tasks via stdin, reasons about what data it needs, emits ToolCallRequest messages on stdout, receives ToolCallResponse messages on stdin, and produces a final document response
- **Tool_Call_Loop**: The event-driven loop where the BA_Subprocess_Orchestrator reads stdout from the AI_Subprocess, detects ToolCallRequest JSON messages, proxies them through ToolRegistry, and sends ToolCallResponse messages back via stdin — repeating until the AI_Subprocess emits its final response
- **Task_Message**: The initial message sent to the AI_Subprocess containing the task description, role instructions, document template, output format, and the list of available tools — replaces the giant pre-collected MasterPrompt
- **Legacy_Pipeline**: ~~The former 4-phase architecture (CollectPhase → ExpandPhase → VisualizePhase → SynthesizePhase → MasterPromptBuilder → GeminiCliAgent.generate).~~ **REMOVED** — deleted per the legacy-pipeline-removal spec. No longer exists in the codebase. Subprocess orchestration is the only execution path.
- **SubprocessManager**: The existing interface from Generic Agent Framework (Requirement 13) that manages subprocess lifecycle — singleton per agent type, stdin/stdout communication, Command_Mutex, crash detection
- **SubprocessProxy**: The existing interface from Generic Agent Framework (Requirement 20) that proxies tool calls between subprocesses and ToolRegistry — handles ToolCallRequest parsing, ToolRegistry invocation, ToolCallResponse formatting
- **MessageProtocol**: The existing wire-format protocol from Generic Agent Framework that frames stdin/stdout messages with JSON and `---END---` delimiters
- **ToolRegistry**: The existing tool registry that manages all registered tools — MCP tools from the Integrations page (Jira MCP, markitdown, Playwright, etc.). Native BA tools have been removed per the [native-tool-removal spec](./../native-tool-removal/requirements.md)

## Requirements

### Requirement 1: BA Subprocess Orchestrator Component

**User Story:** As a developer, I want a single orchestrator component that manages the AI subprocess lifecycle for BA document generation, so that the BA agent delegates data collection decisions to the AI service instead of hardcoding collection phases.

#### Acceptance Criteria

1. THE BA_Subprocess_Orchestrator SHALL provide a `suspend fun executeTask(taskConfig: BATaskConfig): BATaskResult` method that starts or reuses an AI_Subprocess, sends a Task_Message, runs the Tool_Call_Loop until the AI_Subprocess emits a final response, and returns the generated document
2. THE BA_Subprocess_Orchestrator SHALL delegate subprocess lifecycle management to the existing SubprocessManager interface — calling `sendCommand()` to communicate with the AI_Subprocess and relying on SubprocessManager for spawn, reuse, crash detection, and graceful shutdown
3. THE BA_Subprocess_Orchestrator SHALL delegate tool call proxying to the existing SubprocessProxy interface — calling `handleToolCallRequest()` for each ToolCallRequest received from the AI_Subprocess and sending the ToolCallResponse back via stdin using MessageProtocol
4. WHEN the BA_Subprocess_Orchestrator starts a new session, THE orchestrator SHALL inject the full list of available tools into the AI_Subprocess context by calling `SubprocessProxy.buildToolListMessage()` and writing the result to the subprocess stdin before sending the task
5. THE BA_Subprocess_Orchestrator SHALL be injectable via Koin as a singleton, receiving SubprocessManager, SubprocessProxy, ProgressReporter, SettingsRepository, and ProviderConfigRepository as constructor dependencies
6. THE BA_Subprocess_Orchestrator SHALL report progress to the existing ProgressReporter at key milestones: subprocess started (5%), task sent (10%), tool call loop active (15–80%), final response received (85%), parsing complete (95%)
7. BEFORE spawning a subprocess, THE BA_Subprocess_Orchestrator SHALL resolve the CLI configuration from the Integrations page (ProviderConfigRepository) and register the resulting SubprocessConfig with SubprocessManagerImpl at runtime — this ensures users can change AI provider settings on the Integrations page without restarting the server

### Requirement 2: Task Message Builder

**User Story:** As a developer, I want a task message builder that constructs the initial prompt sent to the AI subprocess, so that the AI service receives clear instructions, role context, document template, and a list of available tools without pre-collected data.

#### Acceptance Criteria

1. THE Task_Message builder SHALL construct a message containing: role instruction (from existing MasterPromptSections.buildRoleInstruction), document template structure (from existing MasterPromptSections.buildTemplateStructure), output format instructions (from existing MasterPromptSections.buildOutputFormat), diagram instructions (from existing MasterPromptSections.buildDiagramInstructions), the root ticket ID, and the document type (BRD, FSD, SLIDES)
2. THE Task_Message builder SHALL NOT include pre-collected ticket data, comments, attachments, or linked ticket details — the AI_Subprocess SHALL fetch this data on demand via tool calls
3. THE Task_Message builder SHALL include a tool usage instruction section that explains to the AI_Subprocess how to request tool calls using the ToolCallRequest JSON format, with examples based on the dynamically available MCP tools (e.g., `mcp_jira_get_issue`, `mcp_jira_get_comments`, etc. — whatever tools are registered from the Integrations page)
4. THE Task_Message builder SHALL include a strategy hint section based on the document type — for BRD: prioritize business goals, stakeholder needs, and acceptance criteria; for FSD: prioritize technical architecture, data models, and API specifications; for SLIDES: prioritize executive summary, key metrics, and visual elements
5. THE Task_Message builder SHALL format the message using MessageProtocol.formatCommand() to ensure proper framing with JSON and delimiters for stdin transmission
6. FOR ALL valid BATaskConfig inputs, THE Task_Message builder SHALL produce a non-empty Task_Message that is parseable by MessageProtocol (round-trip property: formatCommand then parseStdoutLine on each line SHALL produce valid SubprocessMessage objects or plain text)

### Requirement 3: Tool Call Loop Engine

**User Story:** As a developer, I want an event-driven loop that reads AI subprocess stdout, detects tool call requests, proxies them through ToolRegistry, and sends responses back, so that the AI service can fetch data on demand during document generation.

#### Acceptance Criteria

1. WHEN the AI_Subprocess emits a line on stdout containing a valid ToolCallRequest JSON (detected via MessageProtocol.parseStdoutLine returning a SubprocessMessage with type "toolCall"), THE Tool_Call_Loop SHALL extract the ToolCallRequest, invoke SubprocessProxy.handleToolCallRequest(), and write the ToolCallResponse back to the subprocess stdin using MessageProtocol.formatToolResponse()
2. WHEN the AI_Subprocess emits plain text lines on stdout (not JSON tool call requests), THE Tool_Call_Loop SHALL accumulate these lines as part of the final document response
3. WHEN the AI_Subprocess emits the MessageProtocol.DELIMITER (`---END---`) on stdout, THE Tool_Call_Loop SHALL treat this as the end of the current response and return the accumulated document text
4. THE Tool_Call_Loop SHALL support multiple concurrent ToolCallRequests from the AI_Subprocess — WHEN the subprocess emits multiple ToolCallRequest messages before receiving responses, THE loop SHALL execute them in parallel via SubprocessProxy and return results as they complete, matched by correlation ID
5. THE Tool_Call_Loop SHALL enforce a configurable maximum number of tool calls per task (default: 30) — WHEN the limit is reached, THE loop SHALL send a ToolCallResponse with `success = false` and error "Tool call limit exceeded" for subsequent requests, and include a message instructing the AI to produce its final response with available data
6. THE Tool_Call_Loop SHALL enforce a configurable total timeout per task (default: 180 seconds) — WHEN the timeout is reached, THE loop SHALL send a termination hint to the subprocess and return whatever document content has been accumulated so far
7. THE Tool_Call_Loop SHALL report each tool call to ProgressReporter with the tool name and status, and update the progress percentage proportionally within the 15–80% range based on elapsed time relative to the timeout
8. IF a ToolCallResponse has `success = false`, THEN THE Tool_Call_Loop SHALL still send the error response to the AI_Subprocess — the AI_Subprocess SHALL decide whether to retry with different parameters, try an alternative tool, or proceed without the data

### Requirement 4: BA Agent Simplification

**User Story:** As a developer, I want the BA agent to use the subprocess orchestrator instead of the 4-phase pipeline, so that the agent codebase is dramatically simplified and the AI service drives data collection decisions.

#### Acceptance Criteria

1. THE BADocumentAgent.execute() method SHALL delegate to BA_Subprocess_Orchestrator.executeTask() for all document generation
2. THE BAAgentConfig SHALL provide a new `buildSubprocessConfig()` method that returns a SubprocessConfig for the AI CLI backend — reading the CLI path, model, and arguments from SettingsRepository (keys: `ai_cli_path`, `ai_cli_model`, `ai_cli_args`)
3. THE BAAgentModule SHALL register the BA_Subprocess_Orchestrator in Koin, wiring SubprocessManager, SubprocessProxy (with ToolRegistry containing MCP tools from the Integrations page), and ProgressReporter
4. THE BADocumentAgent SHALL always delegate to BA_Subprocess_Orchestrator. WHEN the orchestrator is null or returns FAILED, THE agent SHALL return FAILED status — no fallback to any legacy pipeline
5. THE BADocumentAgent SHALL retain the existing onStart() and onComplete() lifecycle hooks unchanged — only the execute() method changes to delegate to the subprocess orchestrator
6. THE BADocumentAgent SHALL pass the root ticket ID and document type to BA_Subprocess_Orchestrator via BATaskConfig, which the orchestrator uses to build the Task_Message

### Requirement 5: Native BA Tool Registration for Subprocess

> **SUPERSEDED by [native-tool-removal spec](./../native-tool-removal/requirements.md).** All 6 native BA tools (`FetchJiraDetailsTool`, `GetLinkedIssuesTool`, `FetchCommentsTool`, `LookupKBRecordTool`, `SearchKBTool`, `ProcessAttachmentTool`) have been deleted from the codebase. The `wireNativeBATools()` function has been removed from `BAAgentModule`. The system now relies entirely on dynamically configured MCP tools from the Integrations page. This requirement is retained for historical context only — all acceptance criteria below reflect the state before removal.

**User Story:** ~~As a developer, I want all native BA tools to be registered in ToolRegistry so the AI subprocess can invoke them on demand, so that the AI service has access to the same data sources as the legacy pipeline.~~ **OBSOLETE** — Native BA tools no longer exist. MCP tools from the Integrations page are the sole source of tools for the BA subprocess.

#### Acceptance Criteria (Historical — Superseded)

1. ~~THE BAAgentModule SHALL register all six native BA tools in ToolRegistry at agent creation time~~ → **Native BA tools have been deleted.** `BAAgentModule` registers only MCP tools via `registerMcpTools()`.
2. THE ToolRegistry SHALL contain all MCP tools from the Integrations page (registered via AgentMcpManager) — the AI_Subprocess SHALL have access to Jira MCP tools (45+ tools), markitdown, Playwright, database MCP, knowledge base MCP, and any other configured MCP servers. *(Still valid — MCP tool registration is unchanged.)*
3. THE SubprocessProxy.getAvailableToolDescriptors() SHALL return the list of MCP tools — each descriptor SHALL include the tool name, description, and parameter schema so the AI_Subprocess LLM can discover and invoke tools correctly. *(Updated: returns MCP tools only, no native BA tools.)*
4. WHEN a new MCP server is started or stopped via the Integrations page during an active BA subprocess session, THE BA_Subprocess_Orchestrator SHALL call SubprocessProxy.buildToolsUpdatedMessage() and write the updated tool list to the subprocess stdin. *(Still valid — unchanged.)*
5. ~~THE tool registration priority SHALL follow the existing framework convention: native BA tools (Local) take precedence over Agent Home MCP tools~~ → **No native BA tools exist.** Tool priority is now: Agent Home MCP tools take precedence over Shared MCP Bridge tools.

### Requirement 6: BATaskConfig and BATaskResult Models

**User Story:** As a developer, I want typed configuration and result models for subprocess task execution, so that the orchestrator has a clear contract for input and output without relying on untyped maps.

#### Acceptance Criteria

1. THE BATaskConfig SHALL be a serializable data class containing: `rootTicketId` (String), `docType` (String, values: "BRD", "FSD", "SLIDES"), `maxToolCalls` (Int, default: 30), `taskTimeoutSeconds` (Int, default: 180), and `cliBackend` (String, default: "gemini", values: "gemini", "copilot", "kiro", "ollama")
2. THE BATaskResult SHALL be a serializable data class containing: `document` (String — the generated markdown document), `toolCallsExecuted` (Int), `toolCallsFailed` (Int), `totalDurationMs` (Long), `status` (enum: SUCCESS, PARTIAL, TIMEOUT, FAILED), and `toolCallLog` (List of ToolCallLogEntry)
3. THE ToolCallLogEntry SHALL be a serializable data class containing: `toolName` (String), `durationMs` (Long), `success` (Boolean), and `resultSizeChars` (Int)
4. FOR ALL valid BATaskConfig objects, serializing to JSON then deserializing back SHALL produce an equivalent BATaskConfig object (round-trip property)
5. FOR ALL valid BATaskResult objects, serializing to JSON then deserializing back SHALL produce an equivalent BATaskResult object (round-trip property)
6. THE BATaskConfig and BATaskResult SHALL reside in the `shared` module under `com.assistant.agent.ba.models` package for KMP compatibility

### Requirement 7: Backward Compatibility and Migration

> **SUPERSEDED by [legacy-pipeline-removal spec](./../legacy-pipeline-removal/requirements.md).** The legacy pipeline has been completely removed. This requirement is retained for historical context only — all acceptance criteria below reflect the current state after removal.

**User Story:** ~~As a system operator, I want the subprocess orchestration to be opt-in with a feature flag, so that the existing legacy pipeline continues to work as a fallback during migration and I can switch between architectures without code changes.~~ **OBSOLETE** — Subprocess orchestration is now the only execution path. No feature flag, no fallback, no dual-path logic.

#### Acceptance Criteria (Current State)

1. ~~THE system SHALL select the BA execution mode based on the `ba_subprocess_enabled` setting~~ → **No feature flag exists.** Subprocess orchestration is the only path — always active, no setting to check.
2. ~~THE system SHALL automatically fall back to the Legacy_Pipeline~~ → **No fallback.** WHEN the BA_Subprocess_Orchestrator fails (CLI not found, process crash, configuration error), THE system SHALL return FAILED status with a descriptive error — no fallback to any other pipeline.
3. ~~THE Legacy_Pipeline code SHALL NOT be deleted~~ → **Legacy pipeline code HAS been deleted.** The phases (`CollectPhase`, `ExpandPhase`, `VisualizePhase`, `SynthesizePhase`), `MasterPromptBuilder`, orchestrator backends (`CustomKotlinOrchestrator`, `LangChain4jOrchestrator`), `AgentJobExecutorBridge`, `JobExecutorCallHelper`, `BAAgentSettings`, and `CollectionStrategy` implementations have all been removed from the codebase.
4. ~~THE JobExecutor.resolvePrompt() method SHALL support the subprocess orchestration mode as a new pipeline tier~~ → **`JobExecutor.resolvePrompt()` no longer exists.** `JobExecutor` calls `BASubprocessOrchestrator.executeTask()` directly — no multi-tier fallback chain.
5. ~~THE system SHALL NOT require a server restart — the feature flag SHALL be checked at each task execution time~~ → **No feature flag to check.** Subprocess mode is always active.
6. ~~THE system SHALL log which execution mode was used~~ → **Only subprocess mode exists.** No comparison between execution modes is needed.

### Requirement 8: Multi-Backend CLI Support

**User Story:** As a developer, I want the subprocess orchestrator to support multiple AI CLI backends (Gemini CLI, Copilot CLI, Kiro CLI, Ollama CLI), so that the system is not locked to a single AI provider and operators can switch backends via configuration.

#### Acceptance Criteria

1. THE BA_Subprocess_Orchestrator SHALL resolve the CLI backend from BATaskConfig.cliBackend and map it to a SubprocessConfig — each backend SHALL have a configurable CLI path, default model, and CLI arguments. The resolver SHALL check ProviderConfigRepository (Integrations page) first, then fall back to SettingsRepository if the Integrations config is not found
2. WHEN the cliBackend is "gemini", THE orchestrator SHALL configure SubprocessConfig with the Gemini CLI path from `ai_cli_path` setting, model from `ai_cli_model` setting, and pass `-m <model>` as CLI arguments
3. WHEN the cliBackend is "copilot", THE orchestrator SHALL configure SubprocessConfig with the Copilot CLI path from `copilot_cli_path` setting and appropriate CLI arguments for the Copilot CLI protocol
4. WHEN the cliBackend is "ollama", THE orchestrator SHALL configure SubprocessConfig with the Ollama CLI path from `ollama_cli_path` setting, model from `ollama_cli_model` setting, and pass `run <model>` as CLI arguments
5. IF the configured CLI backend is not installed or the CLI path is invalid, THEN THE BA_Subprocess_Orchestrator SHALL return a BATaskResult with status FAILED and a descriptive error message — no fallback to any other pipeline
6. THE SubprocessConfig for each backend SHALL use the existing MessageProtocol for stdin/stdout framing — all backends SHALL communicate using the same ToolCallRequest/ToolCallResponse JSON format

### Requirement 9: Observability and Diagnostics

**User Story:** As a system operator, I want comprehensive logging and metrics for subprocess-based document generation, so that I can monitor performance, debug tool call failures, and compare effectiveness against the legacy pipeline.

#### Acceptance Criteria

1. THE BA_Subprocess_Orchestrator SHALL log the following metrics for every task execution: total duration (ms), number of tool calls executed, number of tool calls failed, final document size (chars), and CLI backend used
2. THE BA_Subprocess_Orchestrator SHALL log each tool call proxied during the Tool_Call_Loop with: tool name, tool source (LOCAL/MCP), duration (ms), result size (chars), success/failure status, and correlation ID — enabling end-to-end tracing across the subprocess boundary
3. WHEN a task execution exceeds 120 seconds, THE BA_Subprocess_Orchestrator SHALL log a performance warning with a breakdown of time spent: subprocess startup time, tool call total time, AI thinking time (total minus tool call time), and response parsing time
4. THE BATaskResult.toolCallLog SHALL contain a complete ordered list of all tool calls made during the task, enabling post-execution analysis of the AI service's data collection strategy
5. THE BA_Subprocess_Orchestrator SHALL capture and log stderr output from the AI_Subprocess as warnings, enabling debugging of CLI-level errors without mixing them with the document response on stdout
6. WHEN the AI_Subprocess crashes during a task, THE BA_Subprocess_Orchestrator SHALL log the crash details (exit code, last stderr output, tool calls completed before crash) and include them in the BATaskResult with status FAILED
