# Requirements Document — Agent-Based Document Generation Pipeline

## Introduction

The current document generation pipeline (BRD/FSD/Slides) follows a rigid sequential flow: `DeepCollector` traverses Jira tickets and collects ALL data into a flat `EnrichedContext` blob (200K+ chars), `PromptAssembler` transforms it into a prompt, `JobExecutor` calls an AI provider, and the response is parsed into a document. This architecture has fundamental limitations:

1. **No structured memory** — Data is collected into a flat `EnrichedContext`, not organized by purpose or relevance
2. **No reasoning loop** — The pipeline is sequential with no ability to decide what data to collect based on what's already known
3. **No tool abstraction** — Collection, KB lookup, and attachment processing are hardcoded steps, not composable tools
4. **Prompt too large** — 200K+ chars because everything is dumped upfront, causing Gemini CLI timeouts
5. **No incremental collection** — Collects ALL data even if only a subset is needed for a specific document type
6. **No agent state management** — No way to pause, resume, or inspect the agent's reasoning process

This feature rebuilds the document generation pipeline as an Agent-based architecture, introducing structured memory (`JiraContextMemory`), composable tools with `@Tool` annotations, a thinking loop that decides what data to collect, and focused Master Prompt generation per document type. The Agent integrates with the existing `JobExecutor` lifecycle and supports both LangChain4j and native Gemini Function Calling as orchestration backends.

## Glossary

- **DocGen_Agent**: The central orchestrator component that replaces the sequential `DeepCollector → PromptAssembler → AI call` pipeline with an intelligent agent that reasons about what data to collect, uses tools to gather information incrementally, and produces focused Master Prompts per document type
- **JiraContextMemory**: A structured workspace (memory) that organizes collected data into typed slots (summary, description, comments, attachments, linked tickets, business goals, KB records) instead of a flat blob, enabling the Agent to reason about what it knows and what it still needs
- **Agent_Tool**: A composable, reusable function annotated with `@Tool` that the DocGen_Agent can invoke during its thinking loop — examples include `fetchJiraDetails`, `getLinkedIssues`, `processAttachment`, `lookupKBRecord`
- **Thinking_Loop**: The iterative reasoning cycle where the DocGen_Agent evaluates its current memory state, decides which tools to call next, executes them, and repeats until it has sufficient context to produce a Master Prompt — follows the Collect → Expand → Visualize → Synthesize pattern
- **Master_Prompt**: A focused, document-type-specific prompt produced by the DocGen_Agent after its thinking loop completes — significantly smaller than the current 200K char dump because it contains only relevant, pre-analyzed information for the target document type (BRD, FSD, or Slides)
- **Agent_State**: A serializable snapshot of the DocGen_Agent's current progress including memory contents, tools called, reasoning steps taken, and current phase — enables pause/resume and observability
- **Tool_Registry**: The component that registers and manages available Agent_Tools, providing the DocGen_Agent with a catalog of capabilities it can invoke during its thinking loop
- **Collection_Strategy**: The Agent's plan for what data to collect based on the document type being generated — BRD focuses on business goals and requirements, FSD focuses on technical details and API specs, Slides focuses on executive summaries
- **Agent_Orchestrator**: The framework layer (LangChain4j or custom) that manages the Agent's tool calling loop, message history, and interaction with the underlying LLM
- **Parallel_Tool_Executor**: The coroutine-based component that executes multiple independent tool calls concurrently when the Agent requests them in the same reasoning step
- **Memory_Slot**: A typed section within JiraContextMemory (e.g., summary, comments, attachments) that holds a specific category of collected data with metadata about its source and completeness
- **Sufficiency_Check**: The Agent's evaluation of whether its current memory contains enough information to produce a quality Master Prompt for the requested document type, used to decide when to stop the thinking loop
- **EnrichedContext**: The existing flat data model produced by DeepCollector — retained for backward compatibility when the Agent pipeline is disabled
- **CurationPipeline**: The existing prompt curation layer (from prompt-curation-pipeline spec) — the Agent approach subsumes its functionality by collecting only relevant data from the start

## Requirements

### Requirement 1: Structured Memory (JiraContextMemory)

**User Story:** As a document generation system, I want to organize collected ticket data into a structured memory with typed slots, so that the Agent can reason about what it knows and what it still needs to collect.

#### Acceptance Criteria

1. THE JiraContextMemory SHALL organize data into the following Memory_Slots: summary (String), description (String), comments (List of comment entries), attachmentsData (List of extracted text from files/images), linkedTickets (Map of ticket ID to summary), businessGoals (String), kbRecords (Map of ticket ID to KB analysis record), technicalDetails (String), and acceptanceCriteria (List of criteria strings)
2. WHEN the DocGen_Agent stores data into a Memory_Slot, THE JiraContextMemory SHALL record the source ticket ID, the Agent_Tool that produced the data, and a timestamp for each entry
3. THE JiraContextMemory SHALL provide a `getCompleteness()` method that returns a map of Memory_Slot names to fill percentages (0.0 to 1.0), enabling the Sufficiency_Check to evaluate whether enough data has been collected
4. THE JiraContextMemory SHALL be serializable to JSON using kotlinx.serialization with `encodeDefaults = true`, enabling persistence of Agent_State for pause/resume
5. THE JiraContextMemory SHALL enforce a maximum size per Memory_Slot: 10000 characters for summary/description/businessGoals/technicalDetails, 50 entries for comments, 20 entries for linkedTickets, 30 entries for attachmentsData, and 20 entries for kbRecords
6. WHEN a Memory_Slot reaches its maximum size, THE JiraContextMemory SHALL reject additional entries for that slot and log a warning with the slot name and current size

### Requirement 2: Agent Tool Definitions

**User Story:** As a document generation system, I want data collection operations defined as composable, reusable tools with clear contracts, so that the Agent can invoke them dynamically during its thinking loop.

#### Acceptance Criteria

1. THE Tool_Registry SHALL provide the following Agent_Tools: `fetchJiraDetails` (retrieves ticket summary, description, status, priority, and metadata), `getLinkedIssues` (retrieves linked ticket keys and relationship types), `fetchComments` (retrieves comments for a ticket, max 20 most recent), `processAttachment` (extracts text content from a file attachment), `lookupKBRecord` (retrieves pre-analyzed KB record for a ticket), and `searchKB` (searches the knowledge base by query string)
2. WHEN the DocGen_Agent invokes an Agent_Tool, THE Tool_Registry SHALL validate the input parameters, execute the tool, and return a structured result containing the data and metadata (execution time, data size, source)
3. IF an Agent_Tool call fails (network error, timeout, invalid ticket ID), THEN THE Tool_Registry SHALL return a structured error result containing the error type and message, allowing the Agent to decide whether to retry or skip
4. THE Tool_Registry SHALL enforce a rate limit of 50 tool calls per document generation session to prevent infinite loops or excessive API usage
5. EACH Agent_Tool SHALL have a timeout of 30 seconds per invocation — IF the tool does not complete within 30 seconds, THEN THE Tool_Registry SHALL cancel the call and return a timeout error
6. THE Tool_Registry SHALL log each tool invocation with: tool name, input parameters (truncated to 200 chars), execution time (ms), result size (chars), and success/failure status

### Requirement 3: Thinking Loop (Collect → Expand → Visualize → Synthesize)

**User Story:** As a document generation system, I want the Agent to follow an iterative reasoning loop that decides what data to collect based on what it already knows, so that only relevant information is gathered for each document type.

#### Acceptance Criteria

1. THE DocGen_Agent SHALL execute a thinking loop with four phases: Collect (gather core ticket data), Expand (follow linked tickets and references), Visualize (process attachments and diagrams), and Synthesize (produce the Master Prompt)
2. WHEN entering the Collect phase, THE DocGen_Agent SHALL invoke `fetchJiraDetails` for the root ticket and `lookupKBRecord` to check for existing analysis, storing results in JiraContextMemory
3. WHEN entering the Expand phase, THE DocGen_Agent SHALL evaluate linked tickets discovered in the Collect phase and invoke `getLinkedIssues` followed by `fetchJiraDetails` only for tickets that are relevant to the target document type based on the Collection_Strategy
4. WHEN entering the Visualize phase, THE DocGen_Agent SHALL invoke `processAttachment` only for attachments whose filenames suggest relevance to the document type (requirement docs for BRD, technical specs for FSD, presentation materials for Slides)
5. WHEN entering the Synthesize phase, THE DocGen_Agent SHALL perform a Sufficiency_Check on JiraContextMemory — IF completeness is below the threshold for the document type (70% for BRD, 60% for FSD, 50% for Slides), THEN THE DocGen_Agent SHALL return to the Expand phase for one additional iteration
6. THE thinking loop SHALL complete within a maximum of 3 iterations (Collect → Expand → Visualize → Synthesize → optional re-Expand → Synthesize) to bound execution time
7. THE DocGen_Agent SHALL report its current phase and reasoning to the Progress_Tracker after each phase transition, enabling the Job_Manager to update progress for the frontend

### Requirement 4: Collection Strategy per Document Type

**User Story:** As a document generation system, I want the Agent to use different data collection strategies for BRD, FSD, and Slides, so that each document type gets the most relevant information without collecting unnecessary data.

#### Acceptance Criteria

1. WHEN generating a BRD, THE DocGen_Agent SHALL prioritize collecting: business goals, user stories, acceptance criteria, stakeholder comments, and requirement-related attachments — THE DocGen_Agent SHALL deprioritize technical implementation details and API specifications
2. WHEN generating an FSD, THE DocGen_Agent SHALL prioritize collecting: technical details, API specifications, database schemas, architecture decisions, and technical attachments — THE DocGen_Agent SHALL include business requirements from the BRD as context but not re-collect raw business data
3. WHEN generating Slides, THE DocGen_Agent SHALL prioritize collecting: executive summary, key business goals, high-level requirements, and visual attachments (mockups, diagrams) — THE DocGen_Agent SHALL limit linked ticket expansion to depth 1 (direct links only)
4. THE Collection_Strategy SHALL define a relevance scoring function that evaluates each linked ticket's relevance to the document type based on: issue type (Story, Bug, Task, Epic), labels, components, and relationship type (blocks, is-blocked-by, relates-to)
5. WHEN the relevance score for a linked ticket is below 0.3 (on a 0.0 to 1.0 scale), THE DocGen_Agent SHALL skip detailed collection for that ticket and store only a one-line reference in JiraContextMemory

### Requirement 5: Master Prompt Generation

**User Story:** As a document generation system, I want the Agent to produce focused, document-type-specific Master Prompts from its structured memory, so that the AI provider receives compact, relevant prompts instead of 200K char dumps.

#### Acceptance Criteria

1. THE DocGen_Agent SHALL produce a Master_Prompt no larger than 60000 characters for BRD, 70000 characters for FSD, and 30000 characters for Slides
2. THE Master_Prompt SHALL contain the following sections in order: Role instruction, Document-type-specific context (from JiraContextMemory), Template structure, Output format instructions, and Diagram instructions (for BRD and FSD only)
3. WHEN building the Master_Prompt, THE DocGen_Agent SHALL use KB records as the primary data source for each ticket — WHEN a KB record exists, THE DocGen_Agent SHALL NOT include raw ticket description or raw comment dumps for that ticket
4. THE Master_Prompt SHALL include source attribution for each piece of information (ticket ID and Memory_Slot name), enabling traceability from the generated document back to source tickets
5. WHEN the assembled Master_Prompt exceeds the size limit for the document type, THE DocGen_Agent SHALL apply progressive truncation: (1) reduce linked ticket details to summaries only, (2) truncate attachment previews, (3) reduce comment summaries — THE DocGen_Agent SHALL NEVER truncate the root ticket data, role instruction, or template structure
6. THE DocGen_Agent SHALL log the final Master_Prompt size, the number of source tickets included, and the number of Memory_Slots used for observability

### Requirement 6: Parallel Tool Execution via Coroutines

**User Story:** As a document generation system, I want the Agent to execute independent tool calls concurrently using Kotlin Coroutines, so that data collection completes faster when multiple tickets or attachments need to be fetched.

#### Acceptance Criteria

1. WHEN the DocGen_Agent identifies multiple independent tool calls in the same reasoning step (e.g., fetching details for 5 linked tickets), THE Parallel_Tool_Executor SHALL execute them concurrently using `coroutineScope` and `async`
2. THE Parallel_Tool_Executor SHALL limit concurrent tool executions to a maximum of 5 simultaneous calls to avoid overwhelming the Jira API
3. WHEN one parallel tool call fails, THE Parallel_Tool_Executor SHALL NOT cancel other in-flight calls — each call SHALL complete independently and failures SHALL be collected and reported to the DocGen_Agent
4. THE Parallel_Tool_Executor SHALL use a shared `Semaphore` (capacity 5) to throttle concurrent Jira API calls, reusing the existing `jiraApiSemaphore` from DeepCollector
5. THE Parallel_Tool_Executor SHALL log the total wall-clock time for each batch of parallel calls and the individual execution times, enabling performance monitoring

### Requirement 7: Agent State Management (Pause/Resume/Inspect)

**User Story:** As a system operator, I want to inspect the Agent's current state during document generation and support pause/resume, so that I can debug issues and recover from interruptions.

#### Acceptance Criteria

1. THE DocGen_Agent SHALL maintain an Agent_State object containing: current phase (Collect/Expand/Visualize/Synthesize), iteration count, JiraContextMemory snapshot, list of tool calls made (with results), reasoning log (list of decisions made), and elapsed time per phase
2. THE Agent_State SHALL be serializable to JSON using kotlinx.serialization, enabling persistence to the database for recovery after server restart
3. WHEN the Job_Manager pauses a Generation_Job that is in the Agent's thinking loop, THE DocGen_Agent SHALL serialize its current Agent_State and store it in the Generation_Job record, then stop execution gracefully
4. WHEN the Job_Manager resumes a paused Generation_Job, THE DocGen_Agent SHALL deserialize the stored Agent_State and continue the thinking loop from where it left off without re-executing completed tool calls
5. THE Backend_Server SHALL provide endpoint `GET /api/jobs/{jobId}/agent-state` that returns the current Agent_State (memory completeness, phase, tools called, reasoning log) for debugging and observability
6. THE Agent_State reasoning log SHALL contain a maximum of 50 entries — WHEN the log exceeds 50 entries, THE DocGen_Agent SHALL retain only the 50 most recent entries

### Requirement 8: Agent Orchestrator Integration (LangChain4j / Custom)

**User Story:** As a developer, I want the Agent to support pluggable orchestration backends (LangChain4j or custom Kotlin implementation), so that we can evaluate different frameworks and switch without rewriting the Agent logic.

#### Acceptance Criteria

1. THE DocGen_Agent SHALL depend on an Agent_Orchestrator interface that abstracts the tool-calling loop, message history management, and LLM interaction — concrete implementations SHALL be swappable via dependency injection
2. THE system SHALL provide a `CustomKotlinOrchestrator` implementation that manages the thinking loop using Kotlin Coroutines and direct Gemini/Ollama API calls, without requiring external AI framework dependencies
3. THE system SHALL provide a `LangChain4jOrchestrator` implementation that delegates tool calling and memory management to the LangChain4j library, using its `AiServices` and `@Tool` annotation support
4. THE Agent_Orchestrator interface SHALL define methods: `executeThinkingLoop(rootTicketId: String, docType: String, memory: JiraContextMemory): Master_Prompt`, `registerTools(tools: List<Agent_Tool>)`, and `getState(): Agent_State`
5. THE system SHALL select the Agent_Orchestrator implementation based on a configuration setting `agent_orchestrator_type` with values `custom` (default) or `langchain4j`
6. IF the configured Agent_Orchestrator fails to initialize (e.g., LangChain4j dependency missing), THEN THE system SHALL fall back to the `CustomKotlinOrchestrator` and log a warning

### Requirement 9: Integration with Existing JobExecutor and AI Providers

**User Story:** As a developer, I want the Agent pipeline to integrate seamlessly into the existing JobExecutor flow and work with both Gemini CLI and Ollama providers, so that the transition is gradual and backward-compatible.

#### Acceptance Criteria

1. THE JobExecutor SHALL support a feature flag `agent_pipeline_enabled` — WHEN enabled, THE JobExecutor SHALL use the DocGen_Agent pipeline instead of the existing `DeepCollector → PromptAssembler` flow
2. WHEN `agent_pipeline_enabled` is disabled, THE JobExecutor SHALL use the existing pipeline (DeepCollector + PromptAssembler or CurationPipeline) with no behavioral changes (backward compatibility)
3. WHEN `agent_pipeline_enabled` is enabled, THE JobExecutor SHALL replace the `aggregateData` and `buildDocPrompt` steps with a single `DocGen_Agent.generate(ticketId, docType)` call that returns a Master_Prompt
4. THE DocGen_Agent SHALL work with both GeminiCliAgent and OllamaAgent as the final AI provider for document generation — the Master_Prompt produced by the Agent SHALL be compatible with both providers
5. WHEN using GeminiCliAgent with function calling support, THE DocGen_Agent SHALL register KB lookup tools as Gemini functions, enabling the AI to request additional data during document generation (on-demand MCP lookup from prompt-curation-pipeline spec)
6. IF the DocGen_Agent pipeline encounters an unrecoverable error, THEN THE JobExecutor SHALL fall back to the existing pipeline (DeepCollector + PromptAssembler) and log a warning with the error details
7. THE DocGen_Agent SHALL report progress to the existing Progress_Tracker using the same phase labels (AGGREGATING_DATA, GENERATING_DOCUMENT, PARSING_RESPONSE, SAVING) to maintain compatibility with the frontend Inline_Progress_UI

### Requirement 10: Observability and Performance Monitoring

**User Story:** As a system operator, I want comprehensive logging and metrics for the Agent pipeline, so that I can monitor performance, debug issues, and compare effectiveness against the existing pipeline.

#### Acceptance Criteria

1. THE DocGen_Agent SHALL log the following metrics for every document generation: total thinking loop time (ms), number of iterations, number of tool calls made, number of parallel batches, total data collected (chars), final Master_Prompt size (chars), and memory completeness at synthesis time
2. THE DocGen_Agent SHALL log each phase transition with: phase name, duration (ms), memory completeness before and after, and number of tool calls in that phase
3. WHEN the thinking loop exceeds 60 seconds in total, THE DocGen_Agent SHALL log a performance warning with a breakdown of time spent per phase and per tool call
4. THE DocGen_Agent SHALL track and log the ratio of collected data to Master_Prompt size (compression ratio), enabling comparison with the existing pipeline's 200K input → document output ratio
5. THE Backend_Server SHALL provide endpoint `GET /api/metrics/agent-pipeline` that returns aggregated statistics: average thinking loop time, average tool calls per generation, average Master_Prompt size, and success/fallback/failure counts over the last 24 hours

### Requirement 11: Graceful Degradation and Error Handling

**User Story:** As a system operator, I want the Agent pipeline to degrade gracefully when individual tools fail or external services are unavailable, so that document generation completes with partial data rather than failing entirely.

#### Acceptance Criteria

1. WHEN an Agent_Tool call fails for a non-root ticket, THE DocGen_Agent SHALL skip that ticket, record the failure in the reasoning log, and continue the thinking loop with available data
2. WHEN the `fetchJiraDetails` tool fails for the root ticket, THE DocGen_Agent SHALL retry once after a 2-second delay — IF the retry also fails, THEN THE DocGen_Agent SHALL abort and the JobExecutor SHALL fall back to the existing pipeline
3. WHEN the KB service is unavailable, THE DocGen_Agent SHALL proceed without KB records and use raw ticket data as fallback, logging a warning that KB-first strategy is degraded
4. WHEN the total tool call count reaches the rate limit (50 calls), THE DocGen_Agent SHALL immediately proceed to the Synthesize phase with whatever data has been collected, logging a warning about the rate limit being reached
5. WHEN the thinking loop exceeds 90 seconds, THE DocGen_Agent SHALL force-proceed to the Synthesize phase regardless of memory completeness, producing a Master_Prompt from available data and logging a timeout warning
6. THE DocGen_Agent SHALL classify errors into recoverable (tool timeout, network glitch — retry or skip) and unrecoverable (root ticket not found, authentication failure — abort and fall back) categories
