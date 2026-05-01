# Requirements Document — Generic Agent Framework

## Introduction

The Jira Assistant application acts as an **Orchestrator** that manages AI agents as **subprocesses**. Rather than agents being in-process Kotlin objects with direct method calls, each agent runs as a long-lived CLI subprocess (e.g., Gemini CLI, Ollama CLI) that communicates with the Orchestrator via **stdin/stdout streaming**. The Orchestrator sends commands to agents, receives streaming responses, and manages agent lifecycle, memory, and tool access.

Each agent has a well-defined **Agent Home Directory** containing its skills (markdown-based capability definitions), workflows (step-by-step processes), rules (classification and decision logic), memory storage, and MCP server configurations. This structure enables agents to be configured entirely through files — no code changes needed to add new capabilities.

The framework follows the formula: **Agent = LLM + Planning + Memory + Tools**, implemented through a ReAct (Reason + Act + Observe) thinking loop. Agents plan by decomposing tasks, act by invoking tools via MCP and registered tool functions, remember through structured memory with named semantic slots, and self-correct by reviewing error history in their memory.

The framework provides: a subprocess management layer with stdin/stdout communication, an agent home directory system, markdown-based skills and rules, structured typed memory with working and semantic slots, a dynamic tool registry with MCP integration, a configurable ReAct thinking loop engine, parallel tool execution, streaming output, multi-command session management, serializable agent state, progress reporting, and configurable error handling with self-correction.

The first agents built on this framework are the BA Agent, Architect Agent, QA Agent, and PM Agent — but the framework itself contains no domain-specific logic. New agents are created by defining an agent home directory with skills, rules, and configuration — without modifying framework code.

### Key Constraints

- **Kotlin Multiplatform compatible** — Core interfaces in `shared` module, runtime implementations in `server` module
- **Coroutines-first** — All async operations use Kotlin Coroutines and structured concurrency
- **Subprocess architecture** — Agents run as long-lived CLI processes managed by the Orchestrator
- **File-based configuration** — Agent capabilities defined via markdown files in the agent home directory
- **Incremental delivery** — Build what the first agents need, with clean abstractions for future agents
- **Reuse existing infrastructure** — `AIAgent`, `AIOrchestrator`, `CoroutineScope`, `kotlinx.serialization`, `Koin`, `SettingsRepository`

## Glossary

- **Orchestrator**: The Jira Assistant application acting as the central coordinator that manages Agent_Subprocesses, routes commands, and aggregates results
- **Agent_Subprocess**: A long-lived CLI process (e.g., Gemini CLI, Ollama CLI) managed by the Orchestrator — communicates via stdin/stdout and maintains context across multiple commands within a session
- **Agent_Home_Directory**: A structured folder on disk that contains all configuration, skills, workflows, rules, memory, and MCP configs for a single agent — the agent's complete identity and capabilities
- **Skill**: A markdown file within the Agent_Home_Directory that defines a specific capability of an agent — describes purpose, available tools, step-by-step procedures, input/output format, and constraints
- **Rule**: A markdown file within the Agent_Home_Directory that defines classification logic, decision criteria, keyword recognition patterns, conflict resolution, and priority ordering for an agent's decision-making
- **Workflow**: A markdown file within the Agent_Home_Directory that defines a multi-step process an agent follows to complete a complex task — references skills and rules used at each step
- **Working_Memory**: Short-term, in-session memory that exists within the agent subprocess's context window — automatically managed by the LLM, cleared between sessions or on reset
- **Semantic_Memory_Slot**: A named, typed section within Structured_Memory used for context staging — organizes data by semantic category (e.g., `Business_Goal`, `Technical_Constraints`, `User_Flow`) with metadata
- **Agent_Framework**: The collection of core interfaces, abstract classes, and runtime components that provide reusable infrastructure for building specialized AI agents — lives in `shared` and `server` modules
- **Specialized_Agent**: A concrete agent implementation built on the Agent_Framework that defines domain-specific tools, phases, memory schema, and behavior (e.g., BA Agent, Architect Agent, QA Agent)
- **Agent_Interface**: The common contract (`GenericAgent`) that all Specialized_Agents implement — defines `execute(input) → output`, lifecycle hooks, and state access
- **Agent_Input**: A typed, serializable input envelope that carries the request payload, agent configuration, and execution context to a Specialized_Agent
- **Agent_Output**: A typed, serializable output envelope that carries the agent's result, execution metadata, reasoning log, and any errors back to the caller
- **Structured_Memory**: A generic, typed memory container with configurable named Semantic_Memory_Slots, completeness tracking, size limits, and JSON serialization — supports both Working_Memory (transient) and persistent semantic slots
- **Memory_Slot**: A named, typed section within Structured_Memory that holds a specific category of data with metadata (source, timestamp, fill level)
- **Tool_Registry**: The component that manages registration, discovery, validation, and invocation of Agent_Tools — provides rate limiting, timeouts, and execution logging
- **Agent_Tool**: A composable, reusable function with a declared name, description, input schema, and output type that an agent can invoke during its thinking loop
- **MCP_Server**: A Model Context Protocol server that provides external tool access to agents — configured in the Agent_Home_Directory and automatically discovered at agent startup
- **Thinking_Loop_Engine**: The configurable runtime that drives an agent through the ReAct (Reason + Act + Observe) loop, evaluating phase transitions, invoking tools, and checking completion conditions
- **Phase_Definition**: A declarative specification of one phase in an agent's thinking loop — includes phase name, entry conditions, available tools, exit conditions, and maximum duration
- **Phase_Config**: The complete ordered list of Phase_Definitions that defines a Specialized_Agent's thinking loop behavior
- **Parallel_Tool_Executor**: The coroutine-based component that executes multiple independent tool calls concurrently with configurable concurrency limits and per-call timeouts
- **Agent_State**: A serializable snapshot of an agent's execution progress — includes current phase, iteration count, memory contents, tool call history, reasoning log, and timing data
- **Orchestrator_Backend**: A pluggable abstraction over the LLM interaction layer — concrete implementations include `SubprocessOrchestrator` (CLI subprocess management) and `CustomKotlinOrchestrator` (direct API calls)
- **Error_Strategy**: A configurable policy that determines how the framework handles tool failures and phase errors — options include RETRY, SKIP, ABORT, and FALLBACK
- **Progress_Reporter**: The integration point between the Agent_Framework and the existing `DocGenProgressTracker` / frontend progress UI
- **Agent_Config**: A declarative configuration (DSL or data class) that defines a Specialized_Agent's behavior: home directory path, phase definitions, tool registrations, memory schema, error strategies, and limits — without requiring code changes to the framework
- **Command_Mutex**: A concurrency control mechanism that ensures only one command is sent to an Agent_Subprocess at a time, preventing command overlap in the stdin/stdout communication channel
- **Orchestrator_MCP_Proxy**: The mechanism by which the Orchestrator proxies tool calls between an Agent_Subprocess and the application's shared MCP servers (Tầng 1/2) — the agent subprocess emits tool call requests via stdout, the Orchestrator executes them via ToolRegistry/McpToolBridge, and returns results via stdin
- **Tool_Call_Request**: A structured JSON message emitted by an Agent_Subprocess on stdout requesting the Orchestrator to execute a tool — contains tool name, arguments, and a correlation ID for matching the response
- **Tool_Call_Response**: A structured JSON message sent by the Orchestrator to an Agent_Subprocess on stdin containing the result of a proxied tool call — contains the correlation ID, success/failure status, and result data

## Requirements

### Requirement 1: Generic Agent Interface

**User Story:** As a developer, I want a common agent contract that all specialized agents implement, so that the framework can manage agent lifecycle, execution, and state uniformly regardless of agent type.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define a `GenericAgent` interface in the `shared` module with the following methods: `suspend fun execute(input: Agent_Input): Agent_Output`, `fun getAgentId(): String`, `fun getAgentType(): String`, and `fun getState(): Agent_State`
2. THE `GenericAgent` interface SHALL define lifecycle hooks: `suspend fun onStart(input: Agent_Input)` called before the thinking loop begins, and `suspend fun onComplete(output: Agent_Output)` called after the thinking loop finishes
3. WHEN a Specialized_Agent is instantiated, THE Agent_Framework SHALL inject dependencies (Tool_Registry, Structured_Memory, Thinking_Loop_Engine, Progress_Reporter) via constructor injection using Koin
4. THE `Agent_Input` SHALL be a serializable data class containing: `requestId` (String), `agentType` (String), `payload` (Map of String to String), and `config` (Agent_Config) — enabling any Specialized_Agent to receive typed input without framework changes
5. THE `Agent_Output` SHALL be a serializable data class containing: `requestId` (String), `agentType` (String), `result` (String), `metadata` (Map of String to String), `reasoningLog` (List of String), `toolCallCount` (Int), `totalDurationMs` (Long), and `status` (enum: SUCCESS, PARTIAL, FAILED)
6. FOR ALL valid Agent_Input objects, serializing to JSON then deserializing back SHALL produce an equivalent Agent_Input object (round-trip property)
7. FOR ALL valid Agent_Output objects, serializing to JSON then deserializing back SHALL produce an equivalent Agent_Output object (round-trip property)

### Requirement 2: Structured Typed Memory

**User Story:** As a developer building a specialized agent, I want a generic typed memory system with named semantic slots and a distinction between working memory and structured memory, so that my agent can organize data by semantic category, track completeness, and support self-correction through memory review.

#### Acceptance Criteria

1. THE Structured_Memory SHALL be a generic container that accepts a memory schema definition (list of Semantic_Memory_Slot declarations with name, type, semantic category, and max size) at construction time
2. WHEN a Specialized_Agent stores data into a Semantic_Memory_Slot, THE Structured_Memory SHALL record the source identifier, the tool name that produced the data, and a timestamp for each entry
3. THE Structured_Memory SHALL provide a `getCompleteness()` method that returns a map of Semantic_Memory_Slot names to fill percentages (0.0 to 1.0), calculated as current entries divided by declared max entries for list slots, or current character count divided by max characters for string slots
4. THE Structured_Memory SHALL be serializable to JSON using kotlinx.serialization with `encodeDefaults = true`, enabling persistence of Agent_State for pause/resume
5. WHEN a Semantic_Memory_Slot reaches its declared maximum size, THE Structured_Memory SHALL reject additional entries for that slot and return a `SlotFullResult` containing the slot name and current size
6. THE Structured_Memory SHALL provide a `clear()` method that resets all slots to empty and a `getSlot(name: String)` method that returns the current contents of a named slot
7. THE Structured_Memory SHALL provide a `getTotalSize()` method that returns the total character count across all slots, enabling the agent to monitor memory consumption
8. FOR ALL Structured_Memory instances with data, serializing to JSON then deserializing back SHALL produce a Structured_Memory with equivalent slot contents and metadata (round-trip property)
9. THE Structured_Memory SHALL support named semantic slots with predefined categories (e.g., `Business_Goal`, `Technical_Constraints`, `User_Flow`, `Error_History`) that organize data by meaning rather than just type
10. THE Structured_Memory SHALL distinguish between Working_Memory slots (transient, cleared on session reset) and Persistent_Memory slots (retained across sessions) — EACH slot declaration SHALL specify its persistence mode
11. WHEN a tool call fails, THE Structured_Memory SHALL store the error details (tool name, error type, error message, attempted parameters) in a designated `Error_History` slot, enabling the Thinking_Loop_Engine to review past failures and adjust strategy in subsequent iterations

### Requirement 3: Tool Registry and Agent Tools

**User Story:** As a developer building a specialized agent, I want to register, discover, and invoke tools dynamically with built-in rate limiting and timeouts, so that my agent can use composable tools without managing execution concerns.

#### Acceptance Criteria

1. THE Tool_Registry SHALL provide methods to register Agent_Tools: `fun register(tool: Agent_Tool)` and `fun registerAll(tools: List<Agent_Tool>)` — each Agent_Tool declares a unique name, description, input parameter names, and a `suspend` execution function
2. WHEN a Specialized_Agent requests tool invocation via `suspend fun invoke(toolName: String, params: Map<String, String>): ToolResult`, THE Tool_Registry SHALL validate that the tool exists, validate input parameters, execute the tool, and return a `ToolResult` containing the data, execution time (ms), data size (chars), and success/failure status
3. IF an Agent_Tool call fails (exception, timeout, invalid parameters), THEN THE Tool_Registry SHALL return a `ToolResult` with `success = false`, the error type, and the error message — THE Tool_Registry SHALL NOT throw exceptions to the caller
4. THE Tool_Registry SHALL enforce a configurable rate limit (default: 50 tool calls per agent execution session) — WHEN the limit is reached, THE Tool_Registry SHALL return a `ToolResult` with error type `RATE_LIMIT_EXCEEDED`
5. EACH Agent_Tool invocation SHALL have a configurable timeout (default: 30 seconds) — IF the tool does not complete within the timeout, THEN THE Tool_Registry SHALL cancel the coroutine and return a `ToolResult` with error type `TIMEOUT`
6. THE Tool_Registry SHALL log each tool invocation with: tool name, input parameters (truncated to 200 chars), execution time (ms), result size (chars), and success/failure status
7. THE Tool_Registry SHALL provide `fun listTools(): List<ToolDescriptor>` that returns the name and description of all registered tools, enabling the Thinking_Loop_Engine to present available tools to the LLM
8. WHEN a tool is registered with a name that already exists, THE Tool_Registry SHALL replace the existing registration and log a warning

### Requirement 4: Thinking Loop Engine (ReAct Pattern)

**User Story:** As a developer building a specialized agent, I want a configurable ReAct (Reason + Act + Observe) thinking loop engine, so that I can define my agent's reasoning process as a sequence of phases where the agent reasons about the current state, acts by invoking tools, and observes the results to decide next steps.

#### Acceptance Criteria

1. THE Thinking_Loop_Engine SHALL accept a Phase_Config (ordered list of Phase_Definitions) and execute phases following the ReAct pattern: Reason (evaluate current memory state and plan), Act (invoke tools via Tool_Registry), Observe (check results and update memory)
2. EACH Phase_Definition SHALL declare: `name` (String), `entryCondition` (a function that evaluates Structured_Memory and returns Boolean), `phaseAction` (a suspend function that receives Structured_Memory and Tool_Registry and performs the phase work), `exitCondition` (a function that evaluates Structured_Memory and returns Boolean), and `maxDurationSeconds` (Int, default 30)
3. WHEN a phase's `exitCondition` returns false after the `phaseAction` completes, THE Thinking_Loop_Engine SHALL allow the Phase_Config to define a retry target phase (loopback) — THE engine SHALL execute the loopback at most `maxIterations` times (configurable per Phase_Config, default: 3)
4. WHEN a phase exceeds its `maxDurationSeconds`, THE Thinking_Loop_Engine SHALL cancel the phase, log a timeout warning, and proceed to the next phase with whatever data is available in Structured_Memory
5. THE Thinking_Loop_Engine SHALL report phase transitions to the Progress_Reporter with: phase name, phase index (e.g., 2 of 4), and elapsed time — enabling frontend progress updates
6. THE Thinking_Loop_Engine SHALL maintain a reasoning log (list of strings) recording each phase entry, phase exit, tool calls made, loopback decisions, and timeout events
7. THE Thinking_Loop_Engine SHALL enforce a total execution timeout (configurable, default: 120 seconds) — WHEN the total timeout is reached, THE engine SHALL force-complete by skipping remaining phases and proceeding to the final phase
8. WHEN the Thinking_Loop_Engine enters the Observe step and detects failed tool calls in the `Error_History` memory slot, THE engine SHALL include the error context in the next Reason step, enabling the agent to self-correct by adjusting its strategy without explicit error handling code

### Requirement 5: Parallel Tool Executor

**User Story:** As a developer building a specialized agent, I want to execute multiple independent tool calls concurrently with throttling, so that data collection completes faster when multiple sources need to be fetched.

#### Acceptance Criteria

1. WHEN a Specialized_Agent submits a batch of independent tool calls via `suspend fun executeBatch(calls: List<ToolCall>): List<ToolResult>`, THE Parallel_Tool_Executor SHALL execute them concurrently using `coroutineScope` and `async`
2. THE Parallel_Tool_Executor SHALL limit concurrent tool executions to a configurable maximum (default: 5 simultaneous calls) using a `Semaphore`
3. WHEN one tool call in a batch fails, THE Parallel_Tool_Executor SHALL NOT cancel other in-flight calls — each call SHALL complete independently and the batch result SHALL contain individual success/failure results for every call
4. THE Parallel_Tool_Executor SHALL log the total wall-clock time for each batch, the number of calls in the batch, and the individual execution times
5. WHEN the batch contains more calls than the concurrency limit, THE Parallel_Tool_Executor SHALL queue excess calls and execute them as slots become available, maintaining FIFO ordering
6. FOR ALL batches of N independent tool calls, THE Parallel_Tool_Executor SHALL return exactly N ToolResult objects in the same order as the input calls (order preservation property)

### Requirement 6: Agent State Management

**User Story:** As a system operator, I want to inspect, serialize, and restore an agent's execution state, so that I can debug issues, support pause/resume, and recover from server restarts.

#### Acceptance Criteria

1. THE Agent_State SHALL contain: `agentId` (String), `agentType` (String), `currentPhase` (String), `phaseIndex` (Int), `iterationCount` (Int), `memorySnapshot` (serialized Structured_Memory), `toolCallHistory` (List of ToolCallRecord), `reasoningLog` (List of String, max 100 entries), `elapsedTimeMs` (Long), and `status` (enum: RUNNING, PAUSED, COMPLETED, FAILED)
2. THE Agent_State SHALL be serializable to JSON using kotlinx.serialization with `encodeDefaults = true`, enabling persistence to the database
3. WHEN the Agent_Framework receives a pause signal for a running agent, THE framework SHALL serialize the current Agent_State, store it via a provided persistence callback, and stop execution gracefully by completing the current tool call before stopping
4. WHEN the Agent_Framework receives a resume signal with a stored Agent_State, THE framework SHALL deserialize the state, restore the Structured_Memory and phase position, and continue the thinking loop from where it left off without re-executing completed tool calls
5. THE Agent_State reasoning log SHALL retain only the most recent 100 entries — WHEN the log exceeds 100 entries, THE Agent_Framework SHALL discard the oldest entries
6. FOR ALL Agent_State objects, serializing to JSON then deserializing back SHALL produce an equivalent Agent_State with the same phase, memory, and tool history (round-trip property)

### Requirement 7: Orchestrator Backend Abstraction

**User Story:** As a developer, I want pluggable orchestrator backends that abstract the LLM interaction layer, so that the framework can support different execution modes (subprocess CLI, direct API, LangChain4j) without changing agent logic.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define an `OrchestratorBackend` interface with methods: `suspend fun runThinkingLoop(memory: Structured_Memory, tools: Tool_Registry, phases: List<PhaseDefinition>): Agent_Output` and `fun getBackendName(): String`
2. THE Agent_Framework SHALL provide a `CustomKotlinOrchestrator` implementation that manages the thinking loop using Kotlin Coroutines and direct AI provider API calls (via existing `AIAgent` interface), without requiring external AI framework dependencies
3. THE Agent_Framework SHALL provide a `LangChain4jOrchestrator` implementation that delegates tool calling and memory management to the LangChain4j library — this implementation SHALL be optional and loaded only when LangChain4j dependencies are present on the classpath
4. THE system SHALL select the OrchestratorBackend implementation based on a configuration setting `agent_orchestrator_type` stored in SettingsRepository with values `custom` (default), `subprocess`, or `langchain4j`
5. IF the configured OrchestratorBackend fails to initialize (e.g., missing dependencies), THEN THE Agent_Framework SHALL fall back to the `CustomKotlinOrchestrator` and log a warning with the initialization error
6. THE `OrchestratorBackend` interface SHALL NOT expose LLM-specific types (no Gemini or Ollama types in the interface) — all communication SHALL use framework-defined types (Structured_Memory, Tool_Registry, Phase_Config, Agent_Output)

### Requirement 8: Progress Reporting Integration

**User Story:** As a frontend developer, I want the agent framework to report execution progress through the existing progress tracking system, so that users see real-time updates during agent execution without building new UI components.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define a `ProgressReporter` interface with methods: `suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int)`, `suspend fun reportProgress(percent: Int, message: String)`, and `suspend fun reportToolCall(toolName: String, status: String)`
2. THE Agent_Framework SHALL provide a `DocGenProgressAdapter` that implements `ProgressReporter` by delegating to the existing `DocGenProgressTracker`, mapping agent phases to the existing phase labels (AGGREGATING_DATA, GENERATING_DOCUMENT, PARSING_RESPONSE, SAVING)
3. WHEN the Thinking_Loop_Engine transitions between phases, THE engine SHALL call `ProgressReporter.reportPhase()` with the current phase information
4. WHEN a Specialized_Agent does not provide a ProgressReporter, THE Agent_Framework SHALL use a `NoOpProgressReporter` that silently discards all progress events
5. THE ProgressReporter SHALL be injectable via Koin, enabling Specialized_Agents to provide custom progress reporting implementations for different UI contexts

### Requirement 9: Error Handling and Self-Correction

**User Story:** As a developer building a specialized agent, I want configurable error handling strategies with self-correction via memory, so that my agent can degrade gracefully and learn from failures within a session without hardcoding error recovery logic.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define an `ErrorStrategy` enum with values: `RETRY` (retry the failed operation with configurable max retries and delay), `SKIP` (skip the failed operation and continue), `ABORT` (stop the agent and return a FAILED Agent_Output), and `FALLBACK` (execute a provided fallback function)
2. EACH Phase_Definition SHALL declare a default ErrorStrategy (default: SKIP) that applies to all tool failures within that phase
3. EACH Agent_Tool registration SHALL optionally declare an ErrorStrategy override that takes precedence over the phase-level default
4. WHEN the ErrorStrategy is RETRY, THE Agent_Framework SHALL retry the operation up to `maxRetries` times (configurable, default: 2) with a configurable delay between retries (default: 2 seconds) — IF all retries fail, THE framework SHALL escalate to the phase-level ErrorStrategy
5. WHEN the ErrorStrategy is FALLBACK, THE Agent_Framework SHALL execute the provided fallback function and use its result as the tool result — IF the fallback also fails, THE framework SHALL escalate to SKIP
6. THE Agent_Framework SHALL log every error occurrence with: error type, error message, tool name (if applicable), phase name, chosen ErrorStrategy, and outcome (recovered/escalated/aborted)
7. THE Agent_Framework SHALL classify errors into `recoverable` (tool timeout, network error, rate limit — eligible for RETRY/SKIP) and `unrecoverable` (authentication failure, invalid agent config — forced ABORT) categories
8. WHEN a tool call fails, THE Agent_Framework SHALL store the error details in the Structured_Memory `Error_History` slot — in the next thinking loop iteration, THE Thinking_Loop_Engine SHALL include this error context so the agent can reason about the failure and adjust its approach (self-correction via memory)

### Requirement 10: Agent Configuration DSL

**User Story:** As a developer building a specialized agent, I want to define my agent's behavior (phases, tools, memory schema, error strategies, home directory) declaratively via a configuration DSL, so that I can create new agents without modifying framework code.

#### Acceptance Criteria

1. THE Agent_Framework SHALL provide a Kotlin DSL for defining Agent_Config with builders for: memory schema (slot declarations with semantic categories and persistence mode), phase definitions (ordered phases with conditions and actions), tool registrations, error strategies, execution limits, and home directory path
2. WHEN a Specialized_Agent is created using the DSL, THE Agent_Framework SHALL validate the configuration at construction time: phase names must be unique and memory slot names must be unique. Tool name and loopback target validation is deferred to runtime since tools and phases may not be registered at config construction time
3. IF the Agent_Config validation fails, THEN THE Agent_Framework SHALL throw an `InvalidAgentConfigException` with a descriptive message listing all validation errors
4. THE Agent_Config SHALL be serializable to JSON using kotlinx.serialization, enabling storage and inspection of agent configurations
5. THE DSL SHALL support defining execution limits: `maxTotalDurationSeconds` (default: 120), `maxToolCalls` (default: 50), `maxIterations` (default: 3), and `maxConcurrentTools` (default: 5)
6. FOR ALL valid Agent_Config objects built via the DSL, serializing to JSON then deserializing back SHALL produce an equivalent Agent_Config (round-trip property)
7. THE DSL SHALL support specifying the Agent_Home_Directory path, enabling the framework to load skills, rules, workflows, and MCP configurations from the file system at agent startup

### Requirement 11: Observability and Metrics

**User Story:** As a system operator, I want comprehensive logging and metrics for all agent executions, so that I can monitor performance, debug issues, and compare agent effectiveness.

#### Acceptance Criteria

1. THE Agent_Framework SHALL log the following metrics for every agent execution: total execution time (ms), number of phases executed, number of tool calls made, number of parallel batches, total data collected in memory (chars), and final output size (chars)
2. THE Agent_Framework SHALL log each phase transition with: phase name, duration (ms), memory completeness before and after, number of tool calls in that phase, and ErrorStrategy invocations
3. WHEN an agent execution exceeds 60 seconds, THE Agent_Framework SHALL log a performance warning with a breakdown of time spent per phase and per tool call
4. THE Agent_Framework SHALL provide an `AgentMetrics` data class containing execution statistics that is included in every Agent_Output, enabling callers to inspect performance without parsing logs
5. THE `AgentMetrics` SHALL contain: `totalDurationMs` (Long), `phaseCount` (Int), `toolCallCount` (Int), `parallelBatchCount` (Int), `memoryTotalChars` (Int), `outputSizeChars` (Int), `retryCount` (Int), and `errorCount` (Int)

### Requirement 12: Agent Registry and Discovery

**User Story:** As a developer, I want a central registry where specialized agents are registered and discoverable, so that the system can instantiate and execute agents by type name without hardcoded references.

#### Acceptance Criteria

1. THE Agent_Framework SHALL provide an `AgentRegistry` singleton that maps agent type names (String) to agent factory functions (`(Agent_Config) -> GenericAgent`)
2. WHEN a caller requests an agent via `fun getAgent(agentType: String, config: AgentConfig): GenericAgent`, THE AgentRegistry SHALL instantiate the agent using the registered factory function, passing the provided config
3. IF a caller requests an agent type that is not registered, THEN THE AgentRegistry SHALL throw an `AgentNotFoundException` with the requested type name and a list of available agent types
4. THE AgentRegistry SHALL provide `fun listAgentTypes(): List<String>` that returns all registered agent type names, enabling discovery by the system or API endpoints
5. WHEN a Specialized_Agent is registered with a type name that already exists, THE AgentRegistry SHALL replace the existing registration and log a warning
6. THE AgentRegistry SHALL be initialized during application startup via Koin module configuration, with each Specialized_Agent module registering its factory function


### Requirement 13: Agent Subprocess Management

**User Story:** As a developer, I want the Orchestrator to manage AI agent CLI processes as long-lived subprocesses with stdin/stdout communication, so that agents maintain context across multiple commands and the system avoids the overhead of process creation per request.

#### Acceptance Criteria

1. THE Orchestrator SHALL manage each Agent_Subprocess as a long-lived process using the singleton pattern — WHEN a command targets an agent type that already has a running subprocess, THE Orchestrator SHALL reuse the existing process instead of spawning a new one
2. THE Orchestrator SHALL communicate with Agent_Subprocesses via stdin (sending commands) and stdout (receiving responses), using a well-defined message protocol with delimiters to separate command boundaries
3. THE Orchestrator SHALL use a Command_Mutex per Agent_Subprocess to ensure sequential command execution — WHEN a new command arrives while a previous command is still executing, THE Orchestrator SHALL queue the new command and execute it after the current command completes
4. WHEN an Agent_Subprocess crashes or becomes unresponsive (no output within a configurable timeout, default: 60 seconds), THE Orchestrator SHALL terminate the process, log the failure, and spawn a new subprocess on the next command
5. THE Orchestrator SHALL provide a `suspend fun sendCommand(agentType: String, command: String): Flow<String>` method that sends a command to the agent's stdin and returns a Kotlin Flow of streaming response chunks from stdout
6. THE Orchestrator SHALL capture stderr output from Agent_Subprocesses and log it as warnings, enabling debugging without mixing error output with response data
7. WHEN the application shuts down, THE Orchestrator SHALL gracefully terminate all running Agent_Subprocesses by sending a termination signal and waiting up to 5 seconds before force-killing

### Requirement 14: Agent Home Directory

**User Story:** As a developer, I want each agent to have a structured home directory containing its configuration, skills, rules, workflows, memory, and MCP configs, so that agent capabilities are defined entirely through files and can be versioned, shared, and modified without code changes.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define a standard Agent_Home_Directory structure with the following subdirectories: `.agent/skills/` (markdown skill files), `.agent/workflows/` (markdown workflow files), `.agent/rules/` (markdown rule files), `.agent/memory/` (persistent memory storage), `.agent/mcp/` (MCP server configurations), `config.json` (agent configuration), and `workspace/` (temporary file I/O)
2. WHEN an agent is initialized, THE Agent_Framework SHALL scan the Agent_Home_Directory and load all skill files from `.agent/skills/`, all rule files from `.agent/rules/`, and all workflow files from `.agent/workflows/` — making their contents available to the agent's system prompt
3. THE Agent_Framework SHALL validate the Agent_Home_Directory structure at agent startup — IF required directories or the `config.json` file are missing, THEN THE framework SHALL create them with default values and log a warning
4. THE `config.json` file SHALL contain: agent type name, LLM model identifier, token limits, API endpoint configuration, and references to active skills and rules
5. FOR ALL valid `config.json` files, serializing to JSON then deserializing back SHALL produce an equivalent configuration object (round-trip property)
6. THE Agent_Framework SHALL watch the Agent_Home_Directory for file changes during runtime — WHEN a skill, rule, or workflow file is added, modified, or deleted, THE framework SHALL reload the affected files without requiring an agent restart

### Requirement 15: Skills System (Markdown-Based Capabilities)

**User Story:** As a developer, I want to define agent capabilities as markdown files with a structured format, so that I can add new skills to an agent by dropping a file into its skills directory without writing code.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define a standard skill file format in markdown with the following sections: `## Purpose` (what the skill does), `## Available Tools` (tools the skill can use), `## Procedure` (step-by-step instructions), `## Output Format` (expected output structure), and `## Constraints` (limitations and rules)
2. WHEN an agent is initialized, THE Agent_Framework SHALL parse all `.md` files in the `.agent/skills/` directory and construct a combined system prompt that includes all active skill definitions
3. THE Agent_Framework SHALL support skill activation and deactivation — WHEN the `config.json` lists specific skill file names in an `activeSkills` array, THE framework SHALL load only those skills; WHEN `activeSkills` is empty or absent, THE framework SHALL load all skill files
4. THE Agent_Framework SHALL validate skill files at load time — IF a skill file is missing required sections (`## Purpose`, `## Procedure`), THEN THE framework SHALL log a warning and skip the invalid skill
5. EACH skill file SHALL be loadable independently — THE Agent_Framework SHALL NOT require skills to reference or depend on other skills, enabling modular composition

### Requirement 16: Rules System (Markdown-Based Classification Logic)

**User Story:** As a developer, I want to define classification and decision-making rules as markdown files, so that agents can categorize data, resolve conflicts, and make decisions based on configurable criteria without hardcoded logic.

#### Acceptance Criteria

1. THE Agent_Framework SHALL define a standard rule file format in markdown with the following sections: `## Purpose` (what the rule classifies or decides), `## Keywords` (recognition patterns and trigger words), `## Categories` (possible classification outcomes), `## Priority` (ordering when multiple rules match), and `## Conflict Resolution` (how to handle ambiguous cases)
2. WHEN an agent is initialized, THE Agent_Framework SHALL parse all `.md` files in the `.agent/rules/` directory and make rule definitions available to the agent's reasoning context
3. THE Agent_Framework SHALL support rule priority ordering — WHEN multiple rules match the same input, THE framework SHALL apply rules in priority order (lowest number first) and use the first matching rule's classification
4. THE Agent_Framework SHALL validate rule files at load time — IF a rule file is missing required sections (`## Purpose`, `## Categories`), THEN THE framework SHALL log a warning and skip the invalid rule
5. EACH rule file SHALL be loadable independently — THE Agent_Framework SHALL NOT require rules to reference or depend on other rules, enabling modular composition

### Requirement 17: Streaming Output

**User Story:** As a frontend developer, I want agents to stream their responses in real-time as they generate output, so that users see progressive results without waiting for the entire response to complete.

#### Acceptance Criteria

1. THE Agent_Framework SHALL support streaming output from Agent_Subprocesses via a Kotlin `Flow<String>` that emits response chunks as they arrive on stdout
2. THE Agent_Framework SHALL provide an `onUpdate` callback mechanism that Specialized_Agents can use to emit intermediate results during phase execution — EACH callback invocation SHALL include the current chunk text and a progress indicator
3. WHEN an Agent_Subprocess produces output on stdout, THE Orchestrator SHALL forward each line or chunk to the registered `onUpdate` callback within 100 milliseconds, enabling near-real-time UI updates
4. THE streaming output SHALL be compatible with the existing Progress_Reporter — WHEN streaming is active, THE Progress_Reporter SHALL receive both phase-level progress updates and chunk-level content updates
5. IF the streaming connection is interrupted (e.g., client disconnects), THEN THE Agent_Framework SHALL continue the agent execution to completion and discard undelivered chunks, ensuring the agent's work is not lost

### Requirement 18: Multi-Command Session Management

**User Story:** As a developer, I want agents to maintain context across multiple sequential commands within a session, so that follow-up commands can reference previous results without re-sending all context.

#### Acceptance Criteria

1. THE Agent_Framework SHALL maintain a session context per Agent_Subprocess that persists across multiple commands — EACH command within a session SHALL have access to the Structured_Memory populated by previous commands
2. THE Agent_Framework SHALL provide a `resetSession(agentType: String)` method that clears the agent's Working_Memory slots, resets the command history, and optionally sends a reset signal to the Agent_Subprocess (equivalent to `/reset` or `/new-thread`)
3. WHEN a new command is sent to an Agent_Subprocess within an existing session, THE Orchestrator SHALL include a session context summary (previous command results, current memory state) in the command payload, enabling the agent to maintain continuity
4. THE Agent_Framework SHALL track command history within a session — EACH session SHALL maintain an ordered list of commands sent and responses received, with configurable maximum history size (default: 50 entries)
5. WHEN a session exceeds the maximum history size, THE Agent_Framework SHALL summarize older entries and retain only the most recent entries plus the summary, preventing unbounded memory growth

### Requirement 19: MCP Integration

**User Story:** As a developer, I want agents to automatically discover and use MCP (Model Context Protocol) servers for external tool access, so that agents can interact with external systems without custom tool implementation code.

#### Acceptance Criteria

1. THE Agent_Framework SHALL read MCP server configurations from the `.agent/mcp/` directory in the Agent_Home_Directory — EACH configuration file SHALL specify the MCP server command, arguments, environment variables, and available tool descriptions
2. WHEN an agent is initialized, THE Agent_Framework SHALL start configured MCP servers and register their tools in the Tool_Registry automatically — MCP tools SHALL be invocable through the same `Tool_Registry.invoke()` interface as native Agent_Tools
3. THE Agent_Framework SHALL prefix MCP tool names with the server name to avoid naming conflicts with native tools (e.g., `mcp_jira_search` for a tool named `search` from the `jira` MCP server)
4. IF an MCP server fails to start or becomes unresponsive, THEN THE Agent_Framework SHALL log the error, mark the server's tools as unavailable in the Tool_Registry, and continue agent execution with remaining tools
5. THE Agent_Framework SHALL support MCP server lifecycle management — WHEN an agent session ends, THE framework SHALL gracefully shut down all MCP servers associated with that agent
6. FOR ALL MCP tool invocations, THE Tool_Registry SHALL apply the same rate limiting, timeout, and logging policies as native Agent_Tools

### Requirement 20: Orchestrator MCP Proxy

**User Story:** As a developer, I want the Orchestrator to proxy tool calls between agent subprocesses and the application's shared MCP servers, so that agent subprocesses can access all MCP tools (Jira, markitdown, Internal MCP Server, etc.) without starting duplicate MCP server processes or implementing MCP protocol themselves.

#### Acceptance Criteria

1. WHEN an Agent_Subprocess emits a Tool_Call_Request on stdout (JSON message with `toolCall` field containing tool name, arguments, and correlation ID), THE Orchestrator SHALL parse the request, execute the tool via `ToolRegistry.invoke()`, and send a Tool_Call_Response back to the agent's stdin with the result
2. THE Orchestrator SHALL inject the list of available tools (from ToolRegistry, including all shared MCP tools from McpToolBridge and Agent Home Directory MCP tools) into the agent subprocess's context at session start — THE tool list SHALL include each tool's name, description, and parameter schema, enabling the agent's LLM to know which tools are available
3. WHEN the Orchestrator receives a Tool_Call_Request for a tool registered via McpToolBridge (Tầng 2), THE Orchestrator SHALL route the call through `McpToolAdapter → McpProtocolClient → MCP Server` transparently — the agent subprocess SHALL NOT need to know whether a tool is local, shared MCP, or agent-specific MCP
4. THE Tool_Call_Request message format SHALL be: `{"toolCall": {"id": "<correlationId>", "name": "<toolName>", "arguments": {<key-value params>}}}` — THE Tool_Call_Response format SHALL be: `{"toolResult": {"id": "<correlationId>", "success": <boolean>, "data": "<result>", "error": "<errorMessage if failed>"}}`
5. THE Orchestrator SHALL support multiple concurrent tool call requests from a single agent subprocess — WHEN the agent emits multiple Tool_Call_Requests before receiving responses, THE Orchestrator SHALL execute them in parallel (via ParallelToolExecutor) and return results as they complete, matched by correlation ID
6. WHEN a proxied tool call fails (tool not found, MCP server unavailable, timeout), THE Orchestrator SHALL return a Tool_Call_Response with `success = false` and a descriptive error message — THE Orchestrator SHALL NOT terminate the agent subprocess on tool failure
7. THE Orchestrator SHALL log each proxied tool call with: agent type, tool name, tool source (LOCAL/MCP/AGENT_MCP), execution time (ms), result size (chars), and success/failure status — enabling end-to-end observability across the subprocess boundary
8. WHEN the application's shared MCP servers change at runtime (new server started, existing server stopped via Integrations UI), THE Orchestrator SHALL update the tool list available to agent subprocesses — for active sessions, THE Orchestrator SHALL send an updated tool list to the agent subprocess via stdin using a `toolsUpdated` message type
9. THE tool registration priority for agent subprocesses SHALL be: Local tools (registered in agent code) > Agent Home Directory MCP tools (`.agent/mcp/`) > Shared MCP Bridge tools (from McpProcessManager) — WHEN tools from different sources have the same name, THE higher-priority source SHALL take precedence
