# Requirements Document

## Introduction

This document specifies requirements for integrating a CLI-based interactive agent approach into the BA Document Agent system. The feature combines a proven POC (GeminiCliInteractiveTest.kt) that uses direct CLI spawning with interactive tool call loops, with the existing BA agent infrastructure (BASubprocessOrchestrator, ToolCallLoopEngine, PipelineStrategy pattern).

The goal is to leverage the simplicity and effectiveness of the POC's direct CLI interaction while retaining the robust abstractions, error handling, and extensibility of the current architecture. This integration will improve BRD generation quality by enabling real-time, interactive tool calls during document generation.

## Glossary

- **CLI_Interactive_Engine**: The new component that manages direct CLI process spawning and interactive stdin/stdout communication using the simple JSON protocol from the POC
- **Tool_Call_Protocol**: The JSON-based protocol for tool call requests (`{"toolCall":{"name":"<toolName>","arguments":{...}}}`) and responses (`{"toolResult":{"name":"<toolName>","success":true/false,"data":"...","error":"..."}}`)
- **Master_Prompt**: The complete prompt sent to the CLI at session start, containing role instructions, available tools, output format, and task description
- **Interactive_Loop**: The real-time loop that reads CLI stdout line-by-line, detects tool call requests, executes tools, and returns results via stdin
- **Pipeline_Strategy**: The strategy pattern interface used by BASubprocessOrchestrator to execute different document generation approaches
- **CLI_Interactive_Strategy**: A new PipelineStrategy implementation that uses CLI_Interactive_Engine for document generation
- **Tool_Executor**: The component responsible for executing tool calls, delegating to SubprocessProxy or direct tool implementations
- **Session_Context**: The accumulated context during an interactive session, including collected data, tool call history, and intermediate responses
- **End_Delimiter**: The `---END---` marker that signals completion of CLI response

## Requirements

### Requirement 1: CLI Interactive Engine Core

**User Story:** As a BA agent developer, I want a dedicated engine for CLI-based interactive communication, so that I can leverage the proven POC approach within the existing architecture.

#### Acceptance Criteria

1. THE CLI_Interactive_Engine SHALL spawn CLI processes using ProcessBuilder with configurable command and arguments
2. WHEN a CLI process is spawned, THE CLI_Interactive_Engine SHALL configure stdin/stdout streams for buffered reading and writing
3. THE CLI_Interactive_Engine SHALL support multiple CLI backends (gemini, copilot, kiro, ollama) via SubprocessConfig
4. WHEN the CLI process exits unexpectedly, THE CLI_Interactive_Engine SHALL detect the exit and report failure with exit code
5. THE CLI_Interactive_Engine SHALL provide a method to send text to CLI stdin with proper flushing
6. THE CLI_Interactive_Engine SHALL provide a method to read CLI stdout line-by-line in a non-blocking coroutine context
7. WHEN the session completes or times out, THE CLI_Interactive_Engine SHALL terminate the CLI process gracefully (SIGTERM, wait, then force-kill)

### Requirement 2: Tool Call Protocol Implementation

**User Story:** As a BA agent developer, I want a simple JSON protocol for tool calls, so that the CLI can request tool execution and receive results in a standardized format.

#### Acceptance Criteria

1. THE Tool_Call_Protocol SHALL parse tool call requests in format `{"toolCall":{"name":"<toolName>","arguments":{...}}}`
2. THE Tool_Call_Protocol SHALL format tool call responses in format `{"toolResult":{"name":"<toolName>","success":true/false,"data":"...","error":"..."}}`
3. WHEN a stdout line contains `"toolCall"` substring, THE Tool_Call_Protocol SHALL extract the JSON starting from `{"toolCall"`
4. WHEN parsing fails, THE Tool_Call_Protocol SHALL return null without throwing exceptions
5. THE Tool_Call_Protocol SHALL serialize tool results with proper JSON escaping for data containing special characters
6. FOR ALL valid tool call JSON strings, parsing then formatting the response SHALL produce valid JSON (round-trip property)

### Requirement 3: Interactive Loop Execution

**User Story:** As a BA agent, I want to execute an interactive loop that handles tool calls in real-time, so that I can gather data dynamically during document generation.

#### Acceptance Criteria

1. WHEN a Master_Prompt is sent, THE Interactive_Loop SHALL read stdout line-by-line until End_Delimiter or timeout
2. WHEN a line contains a tool call request, THE Interactive_Loop SHALL execute the tool and send the result back via stdin
3. WHEN a line does not contain a tool call, THE Interactive_Loop SHALL accumulate it as document content
4. WHEN the End_Delimiter `---END---` is received, THE Interactive_Loop SHALL stop reading and return accumulated content
5. WHILE the loop is running, THE Interactive_Loop SHALL track tool call count, success/failure counts, and execution times
6. IF the loop exceeds the configured timeout, THEN THE Interactive_Loop SHALL stop and return partial results with timeout flag
7. IF the tool call limit is exceeded, THEN THE Interactive_Loop SHALL send an error response instructing the CLI to produce final output
8. THE Interactive_Loop SHALL support cancellation via coroutine cancellation

### Requirement 4: Tool Executor Integration

**User Story:** As a BA agent developer, I want tool execution to integrate with existing SubprocessProxy, so that all registered tools (MCP, local, KB) are available during interactive sessions.

#### Acceptance Criteria

1. THE Tool_Executor SHALL delegate tool calls to SubprocessProxy.handleToolCallRequest
2. WHEN SubprocessProxy returns a ToolCallResponse, THE Tool_Executor SHALL convert it to Tool_Call_Protocol format
3. WHEN a tool call fails, THE Tool_Executor SHALL include the error message in the toolResult response
4. THE Tool_Executor SHALL log each tool call with name, duration, success status, and response size
5. THE Tool_Executor SHALL support the same tools as the current DataCollector: mcp_jira_get_issue, mcp_jira_search, mcp_local_knowledge_base_get_ticket_info, mcp_local_knowledge_base_search_relationships
6. WHEN a tool is not found, THE Tool_Executor SHALL return a toolResult with success=false and descriptive error

### Requirement 5: Master Prompt Builder

**User Story:** As a BA agent developer, I want a configurable Master Prompt builder, so that I can customize the AI's behavior for different document types and contexts.

#### Acceptance Criteria

1. THE Master_Prompt_Builder SHALL generate prompts containing role instructions, available tools, output format, and task description
2. THE Master_Prompt_Builder SHALL include the root ticket ID in the task section
3. THE Master_Prompt_Builder SHALL list available tools with their JSON call format
4. THE Master_Prompt_Builder SHALL specify the End_Delimiter (`---END---`) requirement
5. THE Master_Prompt_Builder SHALL support different document types (BRD, FSD) with appropriate template structures
6. WHERE custom instructions are provided, THE Master_Prompt_Builder SHALL append them to the prompt

### Requirement 6: CLI Interactive Pipeline Strategy

**User Story:** As a BA agent developer, I want a new PipelineStrategy that uses CLI interactive approach, so that it can be used alongside or instead of the existing MultiTurnPipelineStrategy.

#### Acceptance Criteria

1. THE CLI_Interactive_Strategy SHALL implement the PipelineStrategy interface
2. WHEN execute is called, THE CLI_Interactive_Strategy SHALL spawn CLI, send Master_Prompt, run Interactive_Loop, and return BATaskResult
3. THE CLI_Interactive_Strategy SHALL report progress at key milestones: CLI spawn (5%), prompt sent (10%), tool calls (15-80%), document received (90%), complete (100%)
4. WHEN the CLI produces a valid document, THE CLI_Interactive_Strategy SHALL return BATaskResult with status SUCCESS
5. IF the CLI fails or times out, THEN THE CLI_Interactive_Strategy SHALL return BATaskResult with status FAILED and error details
6. THE CLI_Interactive_Strategy SHALL populate BATaskResult with toolCallsExecuted, toolCallsFailed, and totalDurationMs

### Requirement 7: Configuration and Backend Selection

**User Story:** As a system administrator, I want to configure which CLI backend to use for interactive BA generation, so that I can choose the best AI provider for my needs.

#### Acceptance Criteria

1. THE System SHALL support selecting CLI backend via BATaskConfig.cliBackend field
2. THE System SHALL resolve CLI backend configuration using existing CliBackendResolver
3. WHEN cliBackend is "gemini", THE System SHALL spawn Gemini CLI with configured path and model
4. WHEN cliBackend is "copilot", THE System SHALL spawn Copilot CLI with configured path
5. WHEN cliBackend is "ollama", THE System SHALL spawn Ollama CLI with configured path and model
6. IF the CLI path is not configured, THEN THE System SHALL return failure with descriptive error message

### Requirement 8: Session Context and State Management

**User Story:** As a BA agent developer, I want proper session state management, so that tool call history and intermediate results are tracked throughout the session.

#### Acceptance Criteria

1. THE Session_Context SHALL track all tool calls with name, arguments, response, duration, and success status
2. THE Session_Context SHALL accumulate document content lines as they are received
3. THE Session_Context SHALL record session start time, end time, and total duration
4. THE Session_Context SHALL track consecutive failures for circuit breaker logic
5. WHEN the session completes, THE Session_Context SHALL provide a summary including total tool calls, failures, and document size
6. THE Session_Context SHALL be immutable after session completion

### Requirement 9: Error Handling and Recovery

**User Story:** As a BA agent developer, I want robust error handling, so that failures are gracefully handled and reported without crashing the system.

#### Acceptance Criteria

1. IF the CLI process fails to start, THEN THE System SHALL return failure with process error details
2. IF a tool call throws an exception, THEN THE System SHALL catch it and return a toolResult with success=false
3. IF the CLI produces malformed JSON, THEN THE System SHALL skip the line and continue processing
4. IF the CLI becomes unresponsive, THEN THE System SHALL timeout and terminate the process
5. WHEN any error occurs, THE System SHALL log the error with context (session ID, tool name, error message)
6. THE System SHALL never propagate uncaught exceptions from the interactive loop

### Requirement 10: Integration with Existing Architecture

**User Story:** As a BA agent developer, I want the new CLI interactive approach to integrate seamlessly with existing components, so that I can use it without major refactoring.

#### Acceptance Criteria

1. THE CLI_Interactive_Strategy SHALL be usable as a drop-in replacement for MultiTurnPipelineStrategy in BASubprocessOrchestrator
2. THE System SHALL reuse existing ProgressReporter for progress updates
3. THE System SHALL reuse existing SubprocessProxy for tool execution
4. THE System SHALL reuse existing CliBackendResolver for CLI configuration
5. THE System SHALL produce BATaskResult compatible with existing BADocumentAgent expectations
6. WHERE both strategies are available, THE System SHALL allow runtime selection via configuration

### Requirement 11: Logging and Observability

**User Story:** As a system operator, I want comprehensive logging, so that I can monitor and debug interactive BA sessions.

#### Acceptance Criteria

1. THE System SHALL log session start with ticket ID, CLI backend, and configuration
2. THE System SHALL log each tool call request with tool name and arguments
3. THE System SHALL log each tool call response with success status, duration, and response size (truncated)
4. THE System SHALL log session completion with total duration, tool call counts, and document size
5. WHEN errors occur, THE System SHALL log at ERROR level with full context
6. THE System SHALL use structured logging with consistent field names for log aggregation
