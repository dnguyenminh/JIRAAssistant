# Requirements Document

## Introduction

This specification defines the removal of the legacy BA document generation pipeline from the Jira Assistant project. The system currently maintains two parallel execution paths for BA document generation: (1) the subprocess orchestration path (`BASubprocessOrchestrator` → long-lived AI subprocess → tool call loop → multi-turn review → document) and (2) the legacy pipeline path (4-phase thinking loop → `MasterPromptBuilder` → one-shot AI agent call). This removal eliminates the legacy pipeline entirely, making subprocess orchestration the single execution path. No fallback, no feature flags, no dual-path logic.

**Scope clarification based on codebase analysis:**
- The CLI agent classes (`GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`) and `CliAgentUtils.kt` are used by `IntegrationRoutes.kt` and `ServerModule.kt` for provider test connections. They MUST NOT be deleted — only their usage as one-shot document generation agents in `JobExecutorAIHelper.resolveAgentFromConfig()` is removed.
- `MasterPromptSections.kt` is reused by `TaskMessageBuilder` in the subprocess path and MUST be kept.
- The `OrchestratorBackend` interface lives in the shared module and is only consumed by the BA agent. It can be kept in shared for future extensibility or removed — this spec removes the server-side implementations only.
- The `CurationPipeline` and `PromptAssembler` are part of the legacy prompt flow in `JobExecutor` and are removed from that context.

## Glossary

- **Subprocess_Orchestrator**: The `BASubprocessOrchestrator` component that manages BA document generation via a long-lived AI subprocess with tool call loops and multi-turn review.
- **Legacy_Pipeline**: The 4-phase thinking loop (`CollectPhase` → `ExpandPhase` → `VisualizePhase` → `SynthesizePhase`) followed by `MasterPromptBuilder` prompt assembly and one-shot AI agent call via `GeminiCliAgent`/`CopilotCliAgent`/`KiroCliAgent`.
- **Feature_Flag**: Runtime settings `ba_subprocess_enabled` and `agent_pipeline_enabled` in `BAAgentSettings` that control which execution path is used.
- **JobExecutor**: The job execution engine that orchestrates document generation jobs, currently containing subprocess, agent pipeline, curation pipeline, and legacy prompt fallback paths.
- **BADocumentAgent**: The BA agent implementation on the Generic Agent Framework that currently supports both subprocess and legacy execution modes.
- **One_Shot_Agent**: An `AIAgent` implementation (`GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`) that spawns a CLI process per request, sends a prompt, and reads a single response — used by the legacy pipeline for document generation.
- **ThinkingLoop**: The `CustomKotlinOrchestrator` + `ThinkingLoopEngine` execution model that runs the 4-phase BA pipeline.
- **AgentJobExecutorBridge**: The bridge component that connects the Generic Agent Framework's `BADocumentAgent` to `JobExecutor` for the agent pipeline fallback path.

## Requirements

### Requirement 1: Delete Legacy Phase Files

**User Story:** As a developer, I want the legacy BA phase files removed from the codebase, so that there is no dead code from the old 4-phase thinking loop pipeline.

#### Acceptance Criteria

1. WHEN the Legacy_Pipeline phase files are removed, THE Codebase SHALL NOT contain `CollectPhase.kt`, `ExpandPhase.kt`, `VisualizePhase.kt`, or `SynthesizePhase.kt` in the `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/phases/` directory.
2. WHEN the Legacy_Pipeline phase files are removed, THE Codebase SHALL compile without errors referencing `CollectPhase`, `ExpandPhase`, `VisualizePhase`, or `SynthesizePhase`.

### Requirement 2: Delete Legacy Orchestrator Backends

**User Story:** As a developer, I want the legacy orchestrator backend implementations removed, so that the only orchestration path is subprocess-based.

#### Acceptance Criteria

1. WHEN the legacy orchestrator backends are removed, THE Codebase SHALL NOT contain `CustomKotlinOrchestrator.kt` or `LangChain4jOrchestrator.kt` in the `server/src/jvmMain/kotlin/com/assistant/server/agent/orchestrator/` directory.
2. WHEN the legacy orchestrator backends are removed, THE AgentModule SHALL NOT register `CustomKotlinOrchestrator` or `LangChain4jOrchestrator` as Koin beans.
3. WHEN the legacy orchestrator backends are removed, THE Codebase SHALL compile without errors referencing `CustomKotlinOrchestrator` or `LangChain4jOrchestrator`.

### Requirement 3: Delete Legacy Prompt Builder

**User Story:** As a developer, I want the `MasterPromptBuilder` class removed while keeping `MasterPromptSections`, so that the legacy prompt assembly logic is gone but the shared section builders remain available for `TaskMessageBuilder`.

#### Acceptance Criteria

1. WHEN the legacy prompt builder is removed, THE Codebase SHALL NOT contain `MasterPromptBuilder.kt` in the `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/prompt/` directory.
2. WHILE `MasterPromptBuilder.kt` is removed, THE `MasterPromptSections.kt` file SHALL remain in the codebase and continue to be used by `TaskMessageBuilder`.
3. WHEN the legacy prompt builder is removed, THE Codebase SHALL compile without errors referencing `MasterPromptBuilder`.

### Requirement 4: Remove Legacy Execution Path from BADocumentAgent

**User Story:** As a developer, I want `BADocumentAgent` to use only the subprocess orchestrator without any feature flag check or legacy fallback, so that the agent has a single, clear execution path.

#### Acceptance Criteria

1. THE BADocumentAgent SHALL execute document generation exclusively via the Subprocess_Orchestrator.
2. THE BADocumentAgent SHALL NOT contain any reference to `OrchestratorBackend`, `MasterPromptBuilder`, `CollectionStrategy` factory, or `BAAgentConfig.buildBAPhaseConfig()`.
3. THE BADocumentAgent SHALL NOT contain any feature flag check for `ba_subprocess_enabled`.
4. WHEN the Subprocess_Orchestrator is not available (null), THE BADocumentAgent SHALL return an `AgentOutput` with `AgentStatus.FAILED` and a descriptive error message.
5. WHEN the Subprocess_Orchestrator returns a `BATaskStatus.FAILED` result, THE BADocumentAgent SHALL return an `AgentOutput` with `AgentStatus.FAILED` and the error details — no silent fallback to any other pipeline.

### Requirement 5: Remove Legacy Fallback Chain from JobExecutor

**User Story:** As a developer, I want `JobExecutor` to use only the subprocess path for document generation, so that there is no multi-tier fallback chain with agent pipeline, curation pipeline, and legacy prompt paths.

#### Acceptance Criteria

1. THE JobExecutor SHALL attempt document generation exclusively via the Subprocess_Orchestrator.
2. WHEN the Subprocess_Orchestrator succeeds, THE JobExecutor SHALL save the generated document directly without invoking any AI agent call.
3. WHEN the Subprocess_Orchestrator fails or is unavailable, THE JobExecutor SHALL mark the job as failed with a descriptive error — no fallback to agent pipeline, curation pipeline, or legacy prompt.
4. THE JobExecutor SHALL NOT contain references to `resolveAgentFromConfig()`, `AgentJobExecutorBridge`, `CurationPipeline`, `CuratedPromptAssembler`, `PromptAssembler`, or `BAAgentSettings` feature flags.
5. THE JobExecutor SHALL NOT contain the `resolveLegacyPrompt()` or `resolvePrompt()` methods.
6. THE JobExecutor SHALL NOT depend on `JobExecutorCallHelper` for AI retry logic.

### Requirement 6: Remove Legacy Dependencies from BAAgentModule

**User Story:** As a developer, I want the BA agent Koin module cleaned up to remove legacy dependency registrations, so that only subprocess-related components are wired.

#### Acceptance Criteria

1. THE BAAgentModule SHALL NOT register `MasterPromptBuilder` as a Koin bean.
2. THE BAAgentModule SHALL NOT register a `CollectionStrategy` factory as a Koin bean.
3. THE BAAgentModule SHALL NOT register `AgentJobExecutorBridge` as a Koin bean.
4. WHEN registering `BADocumentAgent` in the `AgentRegistry`, THE BAAgentModule SHALL NOT inject `OrchestratorBackend`, `MasterPromptBuilder`, or `CollectionStrategy` factory.
5. THE BAAgentModule SHALL continue to register `BASubprocessOrchestrator` and its dependencies.

### Requirement 7: Remove Legacy Dependencies from AgentModule

**User Story:** As a developer, I want the agent framework Koin module cleaned up to remove orchestrator backend registrations that are no longer used.

#### Acceptance Criteria

1. THE AgentModule SHALL NOT register `CustomKotlinOrchestrator` as the `OrchestratorBackend` Koin bean.
2. THE AgentModule SHALL NOT register `LangChain4jOrchestrator` as a Koin bean.
3. THE AgentModule SHALL continue to register `SubprocessManager`, `SubprocessProxy`, `SessionManager`, and other subprocess-related components.

### Requirement 8: Remove Feature Flags from BAAgentSettings

**User Story:** As a developer, I want the legacy feature flag constants removed, so that there are no remnants of the dual-path control mechanism.

#### Acceptance Criteria

1. THE BAAgentSettings SHALL NOT contain the `SUBPROCESS_ENABLED` constant.
2. THE BAAgentSettings SHALL NOT contain the `AGENT_PIPELINE_ENABLED` constant.
3. THE BAAgentSettings SHALL NOT contain the `DEFAULT_ENABLED` constant.
4. IF BAAgentSettings has no remaining constants or functionality, THEN THE Codebase SHALL remove the `BAAgentSettings.kt` file entirely.

### Requirement 9: Remove Legacy Phase Configuration from BAAgentConfig

**User Story:** As a developer, I want the phase configuration methods removed from `BAAgentConfig`, so that only subprocess-related configuration remains.

#### Acceptance Criteria

1. THE BAAgentConfig SHALL NOT contain the `buildBAPhaseConfig()` method.
2. THE BAAgentConfig SHALL NOT contain the private phase builder methods (`buildCollectPhase`, `buildExpandPhase`, `buildVisualizePhase`, `buildSynthesizePhase`).
3. THE BAAgentConfig SHALL NOT import `CollectPhase`, `ExpandPhase`, `VisualizePhase`, `SynthesizePhase`, or `CollectionStrategy`.
4. THE BAAgentConfig SHALL continue to provide `buildSubprocessConfig()` and `buildBAAgentConfig()` methods.

### Requirement 10: Remove or Simplify JobExecutorAIHelper

**User Story:** As a developer, I want the one-shot AI agent resolution logic removed from `JobExecutorAIHelper`, so that only the subprocess agent stub remains.

#### Acceptance Criteria

1. THE JobExecutorAIHelper SHALL NOT contain the `resolveAgentFromConfig()` function.
2. THE JobExecutorAIHelper SHALL NOT contain the `supportedProviderTypes()` or `buildAgent()` helper functions.
3. THE JobExecutorAIHelper SHALL NOT import `GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`, or `ProviderConfigRepository`.
4. THE JobExecutorAIHelper SHALL retain the `SubprocessAgentStub` object for logging purposes in document save operations.

### Requirement 11: Remove JobExecutorCallHelper

**User Story:** As a developer, I want `JobExecutorCallHelper` removed entirely, so that the AI retry and streaming logic for one-shot calls is eliminated.

#### Acceptance Criteria

1. WHEN the legacy pipeline is removed, THE Codebase SHALL NOT contain `JobExecutorCallHelper.kt` in the `server/src/jvmMain/kotlin/com/assistant/server/jobs/` directory.
2. WHEN `JobExecutorCallHelper` is removed, THE JobExecutor SHALL NOT reference `JobExecutorCallHelper` or `callHelper`.
3. WHEN `JobExecutorCallHelper` is removed, THE Codebase SHALL compile without errors.

### Requirement 12: Update Tests for Subprocess-Only Path

**User Story:** As a developer, I want the test suite updated to validate the subprocess-only execution path, so that tests reflect the new single-path architecture.

#### Acceptance Criteria

1. THE `BADocumentAgentFeatureFlagTest` SHALL be rewritten to test subprocess-only behavior: successful subprocess execution, subprocess failure returning error (no fallback), and missing orchestrator returning error.
2. THE `BADocumentAgentFeatureFlagTest` SHALL NOT test feature flag routing or legacy fallback scenarios.
3. THE `JobExecutorFallbackChainTest` SHALL be rewritten to test subprocess-direct behavior: successful subprocess saving document, subprocess failure marking job as failed, and subprocess disabled (null orchestrator) marking job as failed.
4. THE `JobExecutorFallbackChainTest` SHALL NOT test multi-tier fallback chains or agent pipeline paths.
5. WHEN tests for deleted components exist (e.g., tests for `CollectPhase`, `ExpandPhase`, `VisualizePhase`, `SynthesizePhase`, `CustomKotlinOrchestrator`), THE Codebase SHALL remove those test files.
6. THE remaining test suite SHALL pass after all removals and modifications.

### Requirement 13: Preserve CLI Agent Classes for Provider Integration

**User Story:** As a developer, I want the CLI agent classes (`GeminiCliAgent`, `CopilotCliAgent`, `KiroCliAgent`) and `CliAgentUtils.kt` preserved, so that provider test connections in `IntegrationRoutes` and `ServerModule` continue to work.

#### Acceptance Criteria

1. THE `GeminiCliAgent.kt`, `CopilotCliAgent.kt`, `KiroCliAgent.kt`, and `CliAgentUtils.kt` files SHALL remain in the codebase.
2. THE `IntegrationRoutes.kt` provider test connection functionality SHALL continue to work after the legacy pipeline removal.
3. THE `ServerModule.kt` AI agent resolution for provider configurations SHALL continue to work after the legacy pipeline removal.
