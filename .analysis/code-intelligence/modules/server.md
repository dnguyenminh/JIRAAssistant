# Module Analysis — server

**Last Updated:** 2026-04-30T10:44:26.902Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server/
├── com.assistant.server.agent.ba/     # Application logic
├── com.assistant.server.agent.ba.integration/     # Application logic
├── com.assistant.server.agent.ba.memory/     # Application logic
├── com.assistant.server.agent.ba.models/     # Domain model
├── com.assistant.server.agent.ba.prompt/     # Application logic
├── com.assistant.server.agent.ba.subprocess/     # Application logic
├── com.assistant.server.agent.ba.subprocess.pipeline.aibackend/     # Application logic
├── com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models/     # Domain model
├── com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama/     # Application logic
├── com.assistant.server.agent.ba.subprocess.pipeline/     # Application logic
├── com.assistant.server.agent.ba.subprocess.pipeline.cli/     # Application logic
├── com.assistant.server.agent.ba.subprocess.pipeline.cli.models/     # Domain model
├── com.assistant.server.agent.ba.subprocess.pipeline.models/     # Domain model
├── com.assistant.server.agent.di/     # Application logic
├── com.assistant.server.agent.engine/     # Application logic
├── com.assistant.server.agent.error/     # Error handling
├── com.assistant.server.agent.home/     # Application logic
├── com.assistant.server.agent.progress/     # Application logic
├── com.assistant.server.agent.registry/     # Application logic
├── com.assistant.server.agent.session/     # Application logic
├── com.assistant.server.agent.state/     # Application logic
├── com.assistant.server.agent.streaming/     # Application logic
├── com.assistant.server.agent.subprocess/     # Application logic
├── com.assistant.server.agent.tool/     # Application logic
├── com.assistant.server.ai/     # Application logic
├── com.assistant.server.chat/     # Application logic
├── com.assistant.server.document.curation/     # Application logic
├── com.assistant.server.document.curation.models/     # Domain model
├── com.assistant.server.document.models/     # Domain model
├── com.assistant.server.jobs/     # Application logic
├── com.assistant.server.agent.ba.generators/     # Application logic
├── com.assistant.server.agent.ba.progress/     # Application logic
├── com.assistant.server.agent.ba.state/     # Application logic
├── com.assistant.server.agent.config/     # Configuration
├── com.assistant.server.agent.generators/     # Application logic
├── com.assistant.server.agent.memory/     # Application logic
├── com.assistant.server.agent.models/     # Domain model
├── com.assistant.server.analysis/     # Application logic
├── com.assistant.server.analysis.models/     # Domain model
├── com.assistant.server.attachment/     # Application logic
├── com.assistant.server.attachment.models/     # Domain model
├── com.assistant.server.db.pg/     # Application logic
├── com.assistant.server.di/     # Application logic
├── com.assistant.server.document.cache/     # Application logic
├── com.assistant.server.document.collection/     # Application logic
├── com.assistant.server.document/     # Application logic
├── com.assistant.server.document.extraction/     # Application logic
├── com.assistant.server.document.jobs/     # Application logic
├── com.assistant.server.document.security/     # Security/Authentication
├── com.assistant.server.document.traversal/     # Application logic
├── com.assistant.server.indexing/     # Application logic
├── com.assistant.server.routes/     # Application logic
├── com.assistant.server.chat.models/     # Domain model
├── com.assistant.server.auth/     # Security/Authentication
├── com.assistant.server.config/     # Configuration
├── com.assistant.server.db/     # Application logic
├── com.assistant.server.middleware/     # Application logic
├── com.assistant.server.kb/     # Application logic
├── com.assistant.server.rbac/     # Application logic
├── com.assistant.server.settings/     # Application logic
├── com.assistant.server.document.prompt/     # Application logic
├── com.assistant.server.document.curation.generators/     # Application logic
├── com.assistant.server.mcp/     # Application logic
├── com.assistant.server.mcp.internal/     # Application logic
├── com.assistant.server.mcp.internal.handlers/     # Application logic
├── com.assistant.server/     # Application logic
├── com.assistant.server.integration/     # Application logic
└── com.assistant.server.testing/     # Testing
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| BAAgentConfig | com.assistant.server.agent.config | Configuration | public |
| BaMcpToolWrapper | com.assistant.server.agent.ba | Application component | private |
| BADocumentAgent | com.assistant.server.agent.ba | Application component | public |
| BAProgressAdapter | com.assistant.server.agent.ba | Integration adapter | public |
| JiraContextMemorySchema | com.assistant.server.agent.ba | Application component | public |
| AgentPipelineMetrics | com.assistant.server.agent.ba | Application component | data |
| PhaseMetric | com.assistant.server.agent.ba | Application component | data |
| BAAgentPayload | com.assistant.server.agent.ba | Application component | public |
| CollectionStrategyConfig | com.assistant.server.agent.config | Configuration | data |
| MasterPromptResult | com.assistant.server.agent.ba | Application component | data |
| RelevanceScore | com.assistant.server.agent.ba | Application component | data |
| AttachmentCurationStep | com.assistant.server.agent.ba | Application component | internal |
| CommentCurationStep | com.assistant.server.agent.ba | Application component | internal |
| MasterPromptContextBuilder | com.assistant.server.agent.ba | Application component | internal |
| MasterPromptSections | com.assistant.server.agent.ba | Application component | internal |
| PromptTruncator | com.assistant.server.agent.ba | Application component | public |
| BASubprocessOrchestrator | com.assistant.server.agent.ba | Application component | open |
| CliBackendResolver | com.assistant.server.agent.ba | Application component | public |
| DocumentQualityChecker | com.assistant.server.agent.ba | Application component | public |
| QualityResult | com.assistant.server.agent.ba | Application component | data |
| McpToolUpdateNotifier | com.assistant.server.agent.ba | Application component | public |
| LoopState | com.assistant.server.agent.ba | Application component | internal |
| AgenticLoopRunner | com.assistant.server.agent.ba | Application component | public |
| AgenticPromptBuilder | com.assistant.server.agent.ba | Application component | public |
| DetectedTools | com.assistant.server.agent.ba | Application component | data |
| Quad | com.assistant.server.agent.ba | Application component | data |
| ExtraTriple | com.assistant.server.agent.ba | Application component | data |
| AiApiClient | com.assistant.server.agent.ba | External service client | public |
| for | com.assistant.server.agent.ba | Application component | public |
| AiBackend | com.assistant.server.agent.ba | Application component | public |
| AiBackendFactory | com.assistant.server.agent.ba | Object creation | public |
| AiCliClient | com.assistant.server.agent.ba | External service client | public |
| for | com.assistant.server.agent.ba | Application component | public |
| BaseNodeCliClient | com.assistant.server.agent.ba | External service client | abstract |
| StreamResult | com.assistant.server.agent.ba | Application component | data |
| StreamEvent | com.assistant.server.agent.ba | Application component | sealed |
| Init | com.assistant.server.agent.ba | Application component | data |
| Message | com.assistant.server.agent.ba | Application component | data |
| Result | com.assistant.server.agent.ba | Application component | data |
| Unknown | com.assistant.server.agent.ba | Application component | data |
| BrdAssembler | com.assistant.server.agent.ba | Application component | public |
| CopilotCliClientImpl | com.assistant.server.agent.ba | Application component | open |
| DataPreFetcher | com.assistant.server.agent.ba | Application component | public |
| DrawioSkillLoader | com.assistant.server.agent.ba | Application component | public |
| GeminiCliClientImpl | com.assistant.server.agent.ba | Application component | open |
| KiroCliClientImpl | com.assistant.server.agent.ba | Application component | open |
| LocalKBToolDescriptorProvider | com.assistant.server.agent.ba | Application component | public |
| AgenticLoopConfig | com.assistant.server.agent.config | Configuration | data |
| AgenticLoopResult | com.assistant.server.agent.ba | Application component | data |
| ToolBridgeResult | com.assistant.server.agent.ba | Application component | data |
| AiCliResponse | com.assistant.server.agent.ba | Data transfer object | data |
| AiCliType | com.assistant.server.agent.ba | Application component | public |
| ProcessMode | com.assistant.server.agent.ba | Application component | public |
| NodeCliConfig | com.assistant.server.agent.config | Configuration | data |
| ResolvedPaths | com.assistant.server.agent.ba | Application component | data |
| ToolRequest | com.assistant.server.agent.ba | Data transfer object | data |
| PhaseId | com.assistant.server.agent.ba | Application component | public |
| PhaseConfig | com.assistant.server.agent.config | Configuration | data |
| PhaseResult | com.assistant.server.agent.ba | Application component | data |
| PipelineConfig | com.assistant.server.agent.config | Configuration | data |
| NodeCliPathResolver | com.assistant.server.agent.ba | Application component | public |
| into | com.assistant.server.agent.ba | Application component | public |
| OllamaApiClient | com.assistant.server.agent.ba | External service client | public |
| OllamaApiClientHelpers | com.assistant.server.agent.ba | Application component | internal |
| OllamaChatRequest | com.assistant.server.agent.ba | Data transfer object | data |
| OllamaChatMessage | com.assistant.server.agent.ba | Application component | data |
| OllamaChatResponse | com.assistant.server.agent.ba | Data transfer object | data |
| OllamaOptions | com.assistant.server.agent.ba | Application component | data |
| OllamaTool | com.assistant.server.agent.ba | Application component | data |
| OllamaToolFunction | com.assistant.server.agent.ba | Application component | data |
| OllamaToolParameters | com.assistant.server.agent.ba | Application component | data |
| OllamaToolProperty | com.assistant.server.agent.ba | Application component | data |
| OllamaToolCall | com.assistant.server.agent.ba | Application component | data |
| OllamaToolCallFunction | com.assistant.server.agent.ba | Application component | data |
| PhasePromptBuilder | com.assistant.server.agent.ba | Application component | public |
| PhaseToolFilter | com.assistant.server.agent.ba | Request filtering | public |
| PipelineInteractionLogger | com.assistant.server.agent.ba | Application component | public |
| InteractionLog | com.assistant.server.agent.ba | Application component | public |
| PipelineOrchestrator | com.assistant.server.agent.ba | Application component | public |
| ToolExecutionBridge | com.assistant.server.agent.ba | Application component | public |
| AiBackendPipelineStrategy | com.assistant.server.agent.ba | Application component | public |
| CliInteractiveEngine | com.assistant.server.agent.ba | Application component | public |
| LoopState | com.assistant.server.agent.ba | Application component | private |
| never | com.assistant.server.agent.ba | Application component | public |
| CliToolExecutor | com.assistant.server.agent.ba | Application component | public |
| MasterPromptBuilder | com.assistant.server.agent.ba | Application component | public |
| ParsedToolCall | com.assistant.server.agent.ba | Application component | data |
| LoopConfig | com.assistant.server.agent.config | Configuration | data |
| LoopResult | com.assistant.server.agent.ba | Application component | data |
| SessionSummary | com.assistant.server.agent.ba | Application component | data |
| InteractiveSessionContext | com.assistant.server.agent.ba | Application component | public |
| ToolCallProtocol | com.assistant.server.agent.ba | Application component | public |
| ToolResultEnvelope | com.assistant.server.agent.ba | Application component | data |
| ToolResultPayload | com.assistant.server.agent.ba | Application component | data |
| CliInteractiveStrategy | com.assistant.server.agent.ba | Application component | public |
| DataCollector | com.assistant.server.agent.ba | Application component | public |
| DocumentAssembler | com.assistant.server.agent.ba | Application component | public |
| LegacyToolCallStrategy | com.assistant.server.agent.ba | Application component | public |
| CollectedContext | com.assistant.server.agent.ba | Application component | data |
| PipelineStepConfig | com.assistant.server.agent.config | Configuration | data |
| StepResponse | com.assistant.server.agent.ba | Data transfer object | data |
| StopReason | com.assistant.server.agent.ba | Application component | public |
| StopDecision | com.assistant.server.agent.ba | Application component | data |
| ToolCallOutcome | com.assistant.server.agent.ba | Application component | data |
| MultiTurnPipelineStrategy | com.assistant.server.agent.ba | Application component | public |
| PipelineStopCondition | com.assistant.server.agent.ba | Application component | public |
| for | com.assistant.server.agent.ba | Application component | public |
| PipelineStrategy | com.assistant.server.agent.ba | Application component | public |
| StepPromptBuilder | com.assistant.server.agent.ba | Application component | public |
| ReviewLoopHelper | com.assistant.server.agent.ba | Utility functions | public |
| TaskMessageBuilder | com.assistant.server.agent.ba | Application component | public |
| in | com.assistant.server.agent.ba | Application component | public |
| ToolCallLoopEngine | com.assistant.server.agent.ba | Application component | public |
| ToolCallLoopResult | com.assistant.server.agent.ba | Application component | data |
| LoopState | com.assistant.server.agent.ba | Application component | private |
| LoopContext | com.assistant.server.agent.ba | Application component | internal |
| ParallelToolExecutor | com.assistant.server.agent.ba | Application component | public |
| ThinkingLoopEngineImpl | com.assistant.server.agent.ba | Application component | public |
| in | com.assistant.server.agent.ba | Application component | public |
| ErrorHandler | com.assistant.server.agent.ba | HTTP request handling | public |
| AgentHomeDirectoryHelpers | com.assistant.server.agent.ba | Application component | public |
| AgentHomeDirectoryLoader | com.assistant.server.agent.ba | Application component | public |
| AgentHomeDirectoryWatcher | com.assistant.server.agent.ba | Application component | public |
| AgentMcpManager | com.assistant.server.agent.ba | Application component | public |
| McpServerState | com.assistant.server.agent.ba | Application component | data |
| McpToolWrapper | com.assistant.server.agent.ba | Application component | internal |
| McpProcessStarter | com.assistant.server.agent.ba | Application component | internal |
| McpToolDiscovery | com.assistant.server.agent.ba | Application component | internal |
| McpToolNameResolver | com.assistant.server.agent.ba | Application component | internal |
| McpCollisionDetector | com.assistant.server.agent.ba | Application component | public |
| ResolvedTool | com.assistant.server.agent.ba | Application component | data |
| RuleParser | com.assistant.server.agent.ba | Application component | public |
| SkillParser | com.assistant.server.agent.ba | Application component | public |
| DocGenProgressAdapter | com.assistant.server.agent.ba | Integration adapter | public |
| NoOpProgressReporter | com.assistant.server.agent.ba | Application component | public |
| AgentRegistryImpl | com.assistant.server.agent.ba | Application component | public |
| SessionManagerImpl | com.assistant.server.agent.ba | Application component | public |
| AgentStateManager | com.assistant.server.agent.ba | Application component | public |
| StreamingOutputAdapter | com.assistant.server.agent.ba | Integration adapter | public |
| ManagedSubprocess | com.assistant.server.agent.ba | Application component | public |
| parseable | com.assistant.server.agent.ba | Application component | public |
| MessageProtocol | com.assistant.server.agent.ba | Application component | public |
| to | com.assistant.server.agent.ba | Application component | public |
| SubprocessManagerImpl | com.assistant.server.agent.ba | Application component | public |
| under | com.assistant.server.agent.ba | Application component | public |
| ToolSource | com.assistant.server.agent.ba | Application component | internal |
| SubprocessProxyImpl | com.assistant.server.agent.ba | Application component | public |
| InputSchemaParser | com.assistant.server.agent.ba | Application component | public |
| ParamTypeConverter | com.assistant.server.agent.ba | Data mapping | public |
| ToolNameMapper | com.assistant.server.agent.ba | Data mapping | public |
| ToolNameMapping | com.assistant.server.agent.ba | Application component | data |
| ToolSourceInfo | com.assistant.server.agent.ba | Application component | data |
| ToolRegistryImpl | com.assistant.server.agent.ba | Application component | public |
| ProcessResult | com.assistant.server.agent.ba | Application component | data |
| CopilotCliAgent | com.assistant.server.agent.ba | Application component | public |
| ProcessResult | com.assistant.server.agent.ba | Application component | data |
| GeminiCliAgent | com.assistant.server.agent.ba | Application component | public |
| KiroCliAgent | com.assistant.server.agent.ba | Application component | public |
| ChatLocalKBContext | com.assistant.server.agent.ba | Application component | internal |
| LocalKBToolExecutor | com.assistant.server.agent.ba | Application component | public |
| AttachmentCurator | com.assistant.server.agent.ba | Application component | public |
| CommentSummarizer | com.assistant.server.agent.ba | Application component | public |
| TemporalRelation | com.assistant.server.agent.ba | Application component | public |
| ContentClassification | com.assistant.server.agent.ba | Application component | public |
| TicketClassification | com.assistant.server.agent.ba | Application component | data |
| CommentSummary | com.assistant.server.agent.ba | Application component | data |
| CuratedAttachment | com.assistant.server.agent.ba | Application component | data |
| FullComment | com.assistant.server.agent.ba | Application component | data |
| is | com.assistant.server.agent.ba | Application component | public |
| CommentCollectionResult | com.assistant.server.agent.ba | Application component | data |
| DocGenProgressTracker | com.assistant.server.agent.ba | Application component | public |
| BAAgentModuleRegistrationTest | com.assistant.server.agent.ba | Test class | public |
| BADocumentAgentIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryProviderConfigRepo | com.assistant.server.agent.config | Application component | public |
| FakeSubprocessManager | com.assistant.server.agent.ba | Application component | public |
| FakeSubprocessProxy | com.assistant.server.agent.ba | Application component | public |
| FakeSettingsRepo | com.assistant.server.agent.ba | Application component | public |
| IntegrationNoOpReporter | com.assistant.server.agent.ba | Application component | public |
| IntegrationNoOpToolRegistry | com.assistant.server.agent.ba | Application component | public |
| RealToolLayer | com.assistant.server.agent.ba | Application component | data |
| PropertiesHomeDirectory | com.assistant.server.agent.ba | Application component | private |
| BADocumentAgentMcpExplorationTest | com.assistant.server.agent.ba | Test class | public |
| BADocumentAgentPreservationTest | com.assistant.server.agent.ba | Test class | public |
| BADocumentAgentSubprocessOnlyPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CountingOrchestrator | com.assistant.server.agent.ba | Application component | private |
| NoOpReporterProp | com.assistant.server.agent.ba | Application component | private |
| NoOpToolRegistryProp | com.assistant.server.agent.ba | Application component | private |
| StubManager | com.assistant.server.agent.ba | Application component | private |
| StubProxy | com.assistant.server.agent.ba | Application component | private |
| StubSettings | com.assistant.server.agent.ba | Application component | private |
| BADocumentAgentSubprocessTest | com.assistant.server.agent.ba | Test class | public |
| StubOrchestrator | com.assistant.server.agent.ba | Application component | private |
| ThrowingOrchestrator | com.assistant.server.agent.ba | Application component | private |
| NoOpReporter | com.assistant.server.agent.ba | Application component | private |
| NoOpToolRegistry | com.assistant.server.agent.ba | Application component | private |
| DummySubprocessManager | com.assistant.server.agent.ba | Application component | private |
| DummySubprocessProxy | com.assistant.server.agent.ba | Application component | private |
| EmptySettingsRepo | com.assistant.server.agent.ba | Application component | private |
| GeminiCliInteractiveTest | com.assistant.server.agent.ba | Test class | public |
| IssueMetadata | com.assistant.server.agent.ba | Application component | data |
| JiraContextMemoryPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MinimalTest | com.assistant.server.agent.ba | Test class | public |
| ProgressMappingPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MasterPromptContextBuilderPropertyTest | com.assistant.server.agent.ba | Test class | public |
| BAAgentStatePropertyTest | com.assistant.server.agent.ba | Test class | public |
| BASubprocessOrchestratorTest | com.assistant.server.agent.ba | Test class | public |
| OrchestratorNoOpReporter | com.assistant.server.agent.ba | Application component | private |
| CliBackendResolverTest | com.assistant.server.agent.ba | Test class | public |
| AgenticLoopIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| TrackingReporter | com.assistant.server.agent.ba | Application component | private |
| AiBackendFactoryTest | com.assistant.server.agent.ba | Test class | public |
| BASubprocessOrchestratorTest | com.assistant.server.agent.ba | Test class | public |
| BrdDiagramAndSectionsExplorationTest | com.assistant.server.agent.ba | Test class | public |
| BrdDiagramAndSectionsPreservationTest | com.assistant.server.agent.ba | Test class | public |
| BrdPipelineLocalKBExplorationTest | com.assistant.server.agent.ba | Test class | public |
| BrdPipelinePreservationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CliClientUnitTest | com.assistant.server.agent.ba | Test class | public |
| TestableGeminiClient | com.assistant.server.agent.ba | External service client | private |
| TestableGeminiClientWithModel | com.assistant.server.agent.ba.models | Domain model | private |
| TestableCopilotClient | com.assistant.server.agent.ba | External service client | private |
| TestableKiroClient | com.assistant.server.agent.ba | External service client | private |
| ConversationHistoryPropertyTest | com.assistant.server.agent.ba | Test class | public |
| DeepBrdGenerationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| DeepBrdGenerationUnitTest | com.assistant.server.agent.ba | Test class | public |
| MaxToolCallsPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MultiPhasePipelineIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| TrackingReporter | com.assistant.server.agent.ba | Application component | private |
| MultiPhasePipelinePropertyTest | com.assistant.server.agent.ba | Test class | public |
| MultiPhasePipelineUnitTest | com.assistant.server.agent.ba | Test class | public |
| OllamaToolCallParsingPropertyTest | com.assistant.server.agent.ba | Test class | public |
| OllamaToolConverterPropertyTest | com.assistant.server.agent.ba | Test class | public |
| PromptBuildingPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ScriptPathExtractionPropertyTest | com.assistant.server.agent.ba | Test class | public |
| StatusDeterminationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolCallParsingPropertyTest | com.assistant.server.agent.ba | Test class | public |
| of | com.assistant.server.agent.ba | Application component | public |
| TestCliClient | com.assistant.server.agent.ba | External service client | private |
| ToolExecutionBridgePropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolResultFormattingPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CliInteractiveEnginePropertyTest | com.assistant.server.agent.ba | Test class | public |
| SimLine | com.assistant.server.agent.ba | Application component | sealed |
| ToolCall | com.assistant.server.agent.ba | Application component | data |
| Content | com.assistant.server.agent.ba | Application component | data |
| CliInteractiveIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| CliInteractiveStrategyTest | com.assistant.server.agent.ba | Test class | public |
| EmptySettingsRepository | com.assistant.server.agent.ba | Data access | private |
| CapturingProgressReporter | com.assistant.server.agent.ba | Application component | private |
| CliToolExecutorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SessionOp | com.assistant.server.agent.ba | Application component | sealed |
| RecordToolCall | com.assistant.server.agent.ba | Application component | data |
| AppendLine | com.assistant.server.agent.ba | Application component | data |
| RecordFailure | com.assistant.server.agent.ba | Application component | data |
| ResetFailures | com.assistant.server.agent.ba | Application component | data |
| InteractiveSessionContextPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MasterPromptBuilderPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolCallProtocolPropertyTest | com.assistant.server.agent.ba | Test class | public |
| DataCollectorTest | com.assistant.server.agent.ba | Test class | public |
| NoOpReporter | com.assistant.server.agent.ba | Application component | private |
| DocumentAssemblerTest | com.assistant.server.agent.ba | Test class | public |
| LegacyToolCallStrategyTest | com.assistant.server.agent.ba | Test class | public |
| LegacyNoOpReporter | com.assistant.server.agent.ba | Application component | private |
| MultiTurnPipelineIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| MultiTurnPipelineStrategyTest | com.assistant.server.agent.ba | Test class | public |
| PipelineStopConditionTest | com.assistant.server.agent.ba | Test class | public |
| StepPromptBuilderTest | com.assistant.server.agent.ba | Test class | public |
| SubprocessProxyPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TaskMessageBuilderPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TaskMessageBuilderTest | com.assistant.server.agent.ba | Test class | public |
| ToolCallLoopEnginePropertyTest | com.assistant.server.agent.ba | Test class | public |
| NoOpReporter | com.assistant.server.agent.ba | Application component | private |
| ToolCallLoopEngineTest | com.assistant.server.agent.ba | Test class | public |
| UnitTestNoOpReporter | com.assistant.server.agent.ba | Application component | private |
| AgentConfigPropertyTest | com.assistant.server.agent.config | Test class | public |
| CountingReporter | com.assistant.server.agent.ba | Application component | public |
| ParallelToolExecutorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MixedBatch | com.assistant.server.agent.ba | Application component | data |
| ThinkingLoopEnginePropertyTest | com.assistant.server.agent.ba | Test class | public |
| ErrorHandlerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ClassifiedError | com.assistant.server.agent.ba | Error handling | data |
| AgentHomeConfigPropertyTest | com.assistant.server.agent.config | Test class | public |
| AgentHomeDirectoryScanPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MarkdownValidationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpCollisionDetectorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpToolRegistrationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeHomeDirectory | com.assistant.server.agent.ba | Application component | private |
| RuleParsingPriorityPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SkillActivationFilterPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SkillParserTest | com.assistant.server.agent.ba | Test class | public |
| SkillParsingPromptPropertyTest | com.assistant.server.agent.ba | Test class | public |
| StructuredMemoryPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AgentInputPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AgentOutputPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AgentStatePropertyTest | com.assistant.server.agent.ba | Test class | public |
| AgentRegistryPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SessionPropertyTest | com.assistant.server.agent.ba | Test class | public |
| StreamingOrderPropertyTest | com.assistant.server.agent.ba | Test class | public |
| NoOpReporter | com.assistant.server.agent.ba | Application component | private |
| CommandMutexPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CorrelationIdPropertyTest | com.assistant.server.agent.ba | Test class | public |
| EmitStdoutInterruptibleTest | com.assistant.server.agent.ba | Test class | public |
| ErrorIsolationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeAliveProcess | com.assistant.server.agent.ba | Application component | public |
| MessageProtocolPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SubprocessProxyPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SubprocessSingletonPropertyTest | com.assistant.server.agent.ba | Test class | public |
| SubprocessTimeoutIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| ToolPriorityPropertyTest | com.assistant.server.agent.ba | Test class | public |
| WriteCommandToStdinTest | com.assistant.server.agent.ba | Test class | public |
| with | com.assistant.server.agent.ba | Application component | public |
| InputSchemaParserPropertyTest | com.assistant.server.agent.ba | Test class | public |
| from | com.assistant.server.agent.ba | Application component | public |
| schema | com.assistant.server.agent.ba | Application component | public |
| schema | com.assistant.server.agent.ba | Application component | public |
| ListToolsWithSourcePropertyTest | com.assistant.server.agent.ba | Test class | public |
| ParamTypeConverterPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolNameMapperPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolRegistryPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolBehavior | com.assistant.server.agent.ba | Application component | data |
| AIProviderFailoverPropertyTest | com.assistant.server.agent.ba | Test class | public |
| EmptyKBRepository | com.assistant.server.agent.ba | Data access | public |
| SuccessAgent | com.assistant.server.agent.ba | Application component | public |
| FailingAgent | com.assistant.server.agent.ba | Application component | public |
| KBFirstStrategyPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeKBRepository | com.assistant.server.agent.ba | Data access | public |
| SpyAIAgent | com.assistant.server.agent.ba | Application component | public |
| OrchestratorWiringPropertyTest | com.assistant.server.agent.ba | Test class | public |
| EmptyKB | com.assistant.server.agent.ba | Application component | public |
| StubAgent | com.assistant.server.agent.ba | Application component | public |
| BatchPromptBuilder | com.assistant.server.agent.ba | Application component | public |
| BatchStrategy | com.assistant.server.agent.ba | Application component | public |
| BatchSummaryParser | com.assistant.server.agent.ba | Application component | internal |
| MapPhaseExecutor | com.assistant.server.agent.ba | Application component | internal |
| MapReduceAnalyzerAdapter | com.assistant.server.agent.ba | Integration adapter | public |
| MapReduceOrchestrator | com.assistant.server.agent.ba | Application component | public |
| BatchInfo | com.assistant.server.agent.ba | Application component | data |
| instead | com.assistant.server.agent.ba | Application component | public |
| BatchSummary | com.assistant.server.agent.ba | Application component | data |
| MapReduceConfig | com.assistant.server.agent.config | Configuration | data |
| MapReduceResult | com.assistant.server.agent.ba | Application component | data |
| ProgressTracker | com.assistant.server.agent.ba | Application component | public |
| ReducePhaseExecutor | com.assistant.server.agent.ba | Application component | internal |
| ReduceResult | com.assistant.server.agent.ba | Application component | data |
| ReducePromptBuilder | com.assistant.server.agent.ba | Application component | public |
| AttachmentDownloader | com.assistant.server.agent.ba | Application component | public |
| AttachmentDownloaderImpl | com.assistant.server.agent.ba | Application component | public |
| AttachmentPipeline | com.assistant.server.agent.ba | Application component | public |
| CosineSimilarity | com.assistant.server.agent.ba | Application component | public |
| EmbeddingService | com.assistant.server.agent.ba | Business logic | public |
| EmbeddingServiceImpl | com.assistant.server.agent.ba | Business logic | public |
| EmbeddingConfig | com.assistant.server.agent.config | Configuration | data |
| LinkedAttachmentLogger | com.assistant.server.agent.ba | Application component | internal |
| LinkedAttachmentProcessor | com.assistant.server.agent.ba | Application component | public |
| MarkitdownAutoConfig | com.assistant.server.agent.config | Configuration | public |
| AttachmentChunk | com.assistant.server.agent.ba | Application component | data |
| AttachmentStatusResponse | com.assistant.server.agent.ba | Data transfer object | data |
| AttachmentProcessingStatus | com.assistant.server.agent.ba | Application component | public |
| ChunkType | com.assistant.server.agent.ba | Application component | public |
| OllamaEmbeddingRequest | com.assistant.server.agent.ba | Data transfer object | data |
| OllamaEmbeddingResponse | com.assistant.server.agent.ba | Data transfer object | data |
| TextChunk | com.assistant.server.agent.ba | Application component | data |
| TicketAttachmentGroup | com.assistant.server.agent.ba | Application component | data |
| TextChunker | com.assistant.server.agent.ba | Application component | public |
| VectorStore | com.assistant.server.agent.ba | Application component | public |
| VectorStoreImpl | com.assistant.server.agent.ba | Application component | public |
| PgVectorStoreImpl | com.assistant.server.agent.ba | Application component | public |
| PgVectorStoreSql | com.assistant.server.agent.ba | Application component | internal |
| InMemoryTraversalCache | com.assistant.server.agent.ba | Application component | public |
| CacheEntry | com.assistant.server.agent.ba | Application component | data |
| TraversalCache | com.assistant.server.agent.ba | Application component | public |
| TraversalCacheImpl | com.assistant.server.agent.ba | Application component | public |
| TraversalCacheRepository | com.assistant.server.agent.ba | Data access | public |
| CacheEntry | com.assistant.server.agent.ba | Application component | data |
| AttachmentContentCollector | com.assistant.server.agent.ba | Application component | public |
| CommentCollector | com.assistant.server.agent.ba | Application component | public |
| DeepCollectionSettings | com.assistant.server.agent.ba | Application component | public |
| DeepCollector | com.assistant.server.agent.ba | Application component | public |
| DeepJiraContentExtractor | com.assistant.server.agent.ba | Application component | public |
| DocumentAggregatorImpl | com.assistant.server.agent.ba | Application component | public |
| is | com.assistant.server.agent.ba | Application component | public |
| TicketIdExtractor | com.assistant.server.agent.ba | Application component | public |
| FeatureFlagAggregator | com.assistant.server.agent.ba | Application component | public |
| FeatureFlagContentExtractor | com.assistant.server.agent.ba | Application component | public |
| CollectionJobManager | com.assistant.server.agent.ba | Application component | public |
| CollectionJobManagerImpl | com.assistant.server.agent.ba | Application component | public |
| CollectionJobRepository | com.assistant.server.agent.ba | Data access | public |
| AttachmentCollectionResult | com.assistant.server.agent.ba | Application component | data |
| CollectionJobType | com.assistant.server.agent.ba | Application component | public |
| CollectionJobStatus | com.assistant.server.agent.ba | Application component | public |
| CollectionJobItemStatus | com.assistant.server.agent.ba | Application component | public |
| CollectionJobItem | com.assistant.server.agent.ba | Application component | data |
| CollectionJob | com.assistant.server.agent.ba | Application component | data |
| CollectionJobResponse | com.assistant.server.agent.ba | Data transfer object | data |
| CollectionJobItemResponse | com.assistant.server.agent.ba | Data transfer object | data |
| FullComment | com.assistant.server.agent.ba | Application component | data |
| is | com.assistant.server.agent.ba | Application component | public |
| CommentCollectionResult | com.assistant.server.agent.ba | Application component | data |
| EnrichedContext | com.assistant.server.agent.ba | Application component | public |
| used | com.assistant.server.agent.ba | Application component | data |
| EnrichedContextSurrogate | com.assistant.server.agent.ba | Application component | data |
| EnrichedContextSerializer | com.assistant.server.agent.ba | Application component | internal |
| RelationshipType | com.assistant.server.agent.ba | Application component | public |
| TicketNode | com.assistant.server.agent.ba | Application component | data |
| TicketEdge | com.assistant.server.agent.ba | Application component | data |
| TicketGraph | com.assistant.server.agent.ba | Application component | data |
| TraversalConfig | com.assistant.server.agent.config | Configuration | data |
| TraversalMetadata | com.assistant.server.agent.ba | Application component | data |
| InMemoryRateLimiter | com.assistant.server.agent.ba | Application component | public |
| RateLimiter | com.assistant.server.agent.ba | Application component | public |
| RateLimitExceededException | com.assistant.server.agent.ba | Error handling | public |
| RateLimiterImpl | com.assistant.server.agent.ba | Application component | public |
| RateLimitRepository | com.assistant.server.agent.ba | Data access | public |
| TicketGraphHolder | com.assistant.server.agent.ba | Application component | public |
| KBFirstTicketFetcher | com.assistant.server.agent.ba | Application component | public |
| DiscoveredTicket | com.assistant.server.agent.ba | Application component | data |
| RelatedTicketDiscovery | com.assistant.server.agent.ba | Application component | public |
| RelevanceScorer | com.assistant.server.agent.ba | Application component | public |
| FetchResult | com.assistant.server.agent.ba | Application component | sealed |
| Success | com.assistant.server.agent.ba | Application component | data |
| PermissionDenied | com.assistant.server.agent.ba | Application component | data |
| Failed | com.assistant.server.agent.ba | Application component | data |
| TicketFetcher | com.assistant.server.agent.ba | Application component | open |
| TraversalEngine | com.assistant.server.agent.ba | Application component | public |
| BfsQueueItem | com.assistant.server.agent.ba | Application component | data |
| TraversalState | com.assistant.server.agent.ba | Application component | public |
| BatchEmbedder | com.assistant.server.agent.ba | Application component | public |
| EmbedItem | com.assistant.server.agent.ba | Application component | data |
| IndexingPipeline | com.assistant.server.agent.ba | Application component | public |
| ConflictResponse | com.assistant.server.agent.ba | Data transfer object | data |
| AnalysisStatusTracker | com.assistant.server.agent.ba | Application component | public |
| CascadeStatusTracker | com.assistant.server.agent.ba | Application component | public |
| BatchPartitionPropertyTest | com.assistant.server.agent.ba | Test class | public |
| BatchPromptPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MapReduceIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| MockAIAgent | com.assistant.server.agent.ba | Application component | public |
| FailingAIAgent | com.assistant.server.agent.ba | Application component | public |
| MockResponseParser | com.assistant.server.agent.ba | Application component | public |
| BatchSummarySerializationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MapReduceConfigPropertyTest | com.assistant.server.agent.config | Test class | public |
| MapReduceInfoPropertyTest | com.assistant.server.agent.ba | Test class | public |
| PipelineInput | com.assistant.server.agent.ba | Application component | data |
| PipelineSelectionPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MapProgressInput | com.assistant.server.agent.ba | Application component | data |
| ProgressTrackerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ReducePromptPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AttachmentDownloaderTest | com.assistant.server.agent.ba | Test class | public |
| AttachmentEligibilityPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AttachmentPipelineMcpFallbackTest | com.assistant.server.agent.ba | Test class | public |
| AttachmentPipelineTest | com.assistant.server.agent.ba | Test class | public |
| BatchScanAttachmentTest | com.assistant.server.agent.ba | Test class | public |
| AttachmentCall | com.assistant.server.agent.ba | Application component | data |
| CosineSimilarityPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CosineSimilarityTest | com.assistant.server.agent.ba | Test class | public |
| DownloadCancellationTest | com.assistant.server.agent.ba | Test class | public |
| EmbeddingServiceImplTest | com.assistant.server.agent.ba | Test class | public |
| FullPipelineIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| JiraAttachmentSerializationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| KBFirstDeduplicationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| MarkitdownAutoConfigTest | com.assistant.server.agent.config | Test class | public |
| InMemoryMcpRepo | com.assistant.server.agent.ba | Application component | public |
| MarkitdownIdResolutionTest | com.assistant.server.agent.ba | Test class | public |
| IdTrackingProcessManager | com.assistant.server.agent.ba | Application component | public |
| MarkitdownRetryTest | com.assistant.server.agent.ba | Test class | public |
| CrashThenRecoverPM | com.assistant.server.agent.ba | Application component | public |
| ScanLogEntriesTest | com.assistant.server.agent.ba | Test class | public |
| StubAIOrchestrator | com.assistant.server.agent.ba | Application component | public |
| StubJiraClient | com.assistant.server.agent.ba | External service client | public |
| StubAIAgent | com.assistant.server.agent.ba | Application component | public |
| InMemoryScanStateRepo | com.assistant.server.agent.ba | Application component | public |
| FakeDownloader | com.assistant.server.agent.ba | Application component | public |
| FakeEmbeddingService | com.assistant.server.agent.ba | Business logic | public |
| FakeVectorStore | com.assistant.server.agent.ba | Application component | public |
| FakeScanLogRepository | com.assistant.server.agent.ba | Data access | public |
| FakeMcpProtocolClient | com.assistant.server.agent.ba | External service client | public |
| FakeMcpProcessManager | com.assistant.server.agent.ba | Application component | public |
| FakeAIAgentForAttachment | com.assistant.server.agent.ba | Application component | public |
| FakeKBRepoForAttachment | com.assistant.server.agent.ba | Application component | public |
| FakeGraphEngineForAttachment | com.assistant.server.agent.ba | Application component | public |
| TextChunkerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TextChunkerTest | com.assistant.server.agent.ba | Test class | public |
| PgVectorStorePropertyTest | com.assistant.server.agent.ba | Test class | public |
| BatchEmbedderPropertyTest | com.assistant.server.agent.ba | Test class | public |
| BatchTrackingEmbeddingService | com.assistant.server.agent.ba | Business logic | private |
| IndexingIdempotencyPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TrackingVectorStore | com.assistant.server.agent.ba | Application component | private |
| StubGraphEngine | com.assistant.server.agent.ba | Application component | private |
| IndexingPipelinePropertyTest | com.assistant.server.agent.ba | Test class | public |
| IndexingPipelineTest | com.assistant.server.agent.ba | Test class | public |
| FakeGraphEngineForReindex | com.assistant.server.agent.ba | Application component | private |
| AnalysisAttachmentBugTest | com.assistant.server.agent.ba | Test class | public |
| AnalysisPreservationTest | com.assistant.server.agent.ba | Test class | public |
| TicketDetailMappingTest | com.assistant.server.agent.ba | Test class | public |
| TicketDetailRoutesTest | com.assistant.server.agent.ba | Test class | public |
| FakeJiraClient | com.assistant.server.agent.ba | External service client | public |
| ThrowingJiraClient | com.assistant.server.agent.ba | External service client | public |
| ChatDeepAnalysisContext | com.assistant.server.agent.ba | Application component | public |
| ChatGraphStateContext | com.assistant.server.agent.ba | Application component | internal |
| ChatLocalKBContext | com.assistant.server.agent.ba | Application component | internal |
| ChatMcpToolsContext | com.assistant.server.agent.ba | Application component | internal |
| ChatPersonalization | com.assistant.server.agent.ba | Application component | internal |
| ChatPromptBuilder | com.assistant.server.agent.ba | Application component | internal |
| ChatResponseParser | com.assistant.server.agent.ba | Application component | internal |
| ChatServiceImpl | com.assistant.server.agent.ba | Business logic | public |
| ChatTicketStateContext | com.assistant.server.agent.ba | Application component | internal |
| ConfluenceMcpSyncHandler | com.assistant.server.agent.ba | HTTP request handling | public |
| ConfluencePage | com.assistant.server.agent.ba | Application component | data |
| JiraMcpSyncHandler | com.assistant.server.agent.ba | HTTP request handling | public |
| LocalKBToolExecutor | com.assistant.server.agent.ba | Application component | public |
| McpAgenticLoop | com.assistant.server.agent.ba | Application component | public |
| McpLoopSyncHelpers | com.assistant.server.agent.ba | Application component | internal |
| McpToolCallFallback | com.assistant.server.agent.ba | Application component | internal |
| McpToolCallParser | com.assistant.server.agent.ba | Application component | internal |
| starting | com.assistant.server.agent.ba | Application component | public |
| AIModelContext | com.assistant.server.agent.ba.models | Application component | data |
| SyncResult | com.assistant.server.agent.ba | Application component | data |
| SyncType | com.assistant.server.agent.ba | Application component | public |
| NativeToolCallHandler | com.assistant.server.agent.ba | HTTP request handling | public |
| OllamaToolConverter | com.assistant.server.agent.ba | Data mapping | public |
| ResponseFormatDetector | com.assistant.server.agent.ba | Application component | internal |
| DetectedReply | com.assistant.server.agent.ba | Application component | data |
| ToolCallFormatDetector | com.assistant.server.agent.ba | Application component | internal |
| Formats | com.assistant.server.agent.ba | Application component | public |
| UserToolPermissionService | com.assistant.server.agent.ba | Business logic | public |
| ModelInfo | com.assistant.server.agent.ba.models | Application component | data |
| ToolInfo | com.assistant.server.agent.ba | Application component | data |
| ConversationRenameRequest | com.assistant.server.agent.ba | Data transfer object | data |
| UploadResult | com.assistant.server.agent.ba | Application component | data |
| AgenticLoopPermissionPropertyTest | com.assistant.server.agent.ba | Test class | public |
| StubMcpProcessManager | com.assistant.server.agent.ba | Application component | private |
| AttachmentContextPropertyTest | com.assistant.server.agent.ba | Test class | public |
| AutoDetectPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InMemorySettingsRepo | com.assistant.server.agent.ba | Application component | private |
| BuildGraphStateContextPropertyTest | com.assistant.server.agent.ba | Test class | public |
| BuildGraphStateContextTest | com.assistant.server.agent.ba | Test class | public |
| BulkUpdatePropertyTest | com.assistant.server.agent.ba | Test class | public |
| ChatDeepAnalysisContextTest | com.assistant.server.agent.ba | Test class | public |
| ChatEmptyReplyPropertyTest | com.assistant.server.agent.ba | Test class | internal |
| ChatResponseParserEmptyReplyTest | com.assistant.server.agent.ba | Test class | internal |
| ChatResponseParserRegressionTest | com.assistant.server.agent.ba | Test class | public |
| ChatServiceAttachmentIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| ChatServiceImplTest | com.assistant.server.agent.ba | Test class | public |
| FakeAIAgent | com.assistant.server.agent.ba | Application component | private |
| FakeKBRepository | com.assistant.server.agent.ba | Data access | private |
| FakeGraphEngine | com.assistant.server.agent.ba | Application component | private |
| ChatServiceLocalKBPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeSettings | com.assistant.server.agent.ba | Application component | private |
| EmptyMcpManager | com.assistant.server.agent.ba | Application component | private |
| CapturingAIAgent | com.assistant.server.agent.ba | Application component | public |
| FakeEmbeddingService | com.assistant.server.agent.ba | Business logic | public |
| FakeVectorStore | com.assistant.server.agent.ba | Application component | public |
| StubKBRepository | com.assistant.server.agent.ba | Data access | public |
| StubGraphEngine | com.assistant.server.agent.ba | Application component | public |
| ChatTicketStateContextTest | com.assistant.server.agent.ba | Test class | public |
| ConfluenceMcpSyncHandlerTest | com.assistant.server.agent.ba | Test class | public |
| DefaultAllEnabledPropertyTest | com.assistant.server.agent.ba | Test class | public |
| JiraMcpSyncHandlerTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryKBRepository | com.assistant.server.agent.ba | Data access | private |
| KnowledgeContextPropertyTest | com.assistant.server.agent.ba | Test class | public |
| LocalKBToolExecutorTest | com.assistant.server.agent.ba | Test class | public |
| MapKBRepository | com.assistant.server.agent.ba | Data access | private |
| McpAgenticLoopEmptyReplyTest | com.assistant.server.agent.ba | Test class | internal |
| StubMcpManager | com.assistant.server.agent.ba | Application component | private |
| McpAgenticLoopInternalToolBugTest | com.assistant.server.agent.ba | Test class | public |
| NullClientMcpManager | com.assistant.server.agent.ba | Application component | private |
| StubBridge | com.assistant.server.agent.ba | Application component | private |
| McpAgenticLoopLocalKBTest | com.assistant.server.agent.ba | Test class | public |
| TrackingMcpManager | com.assistant.server.agent.ba | Application component | private |
| McpAgenticLoopPreservationTest | com.assistant.server.agent.ba | Test class | public |
| McpAgenticLoopTest | com.assistant.server.agent.ba | Test class | public |
| PermissionRoundTripPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryPermissionRepo | com.assistant.server.agent.ba | Application component | public |
| InMemoryMcpServerRepo | com.assistant.server.agent.ba | Application component | public |
| PerUserIsolationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TrackingClientManager | com.assistant.server.agent.ba | Application component | public |
| StubProtocolClient | com.assistant.server.agent.ba | External service client | public |
| PreservationStubEmbedding | com.assistant.server.agent.ba | Application component | public |
| PreservationStubVectorStore | com.assistant.server.agent.ba | Application component | public |
| PreservationStubKBRepo | com.assistant.server.agent.ba | Application component | public |
| ResponseFormatDetectorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| and | com.assistant.server.agent.ba | Application component | public |
| InMemorySettingsRepo | com.assistant.server.agent.ba | Application component | private |
| ResponseFormatDetectorTest | com.assistant.server.agent.ba | Test class | public |
| FakeSettingsRepo | com.assistant.server.agent.ba | Application component | private |
| SystemPromptFilterPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ToolListManager | com.assistant.server.agent.ba | Application component | private |
| ToolCallFormatDetectorTest | com.assistant.server.agent.ba | Test class | public |
| FakeSettingsRepo | com.assistant.server.agent.ba | Application component | private |
| ValidationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ValidationRejectsInvalidPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ChatConfigRoutesToolsTest | com.assistant.server.agent.config | Test class | public |
| FakeSettingsRepository | com.assistant.server.agent.ba | Data access | private |
| AuthServiceImpl | com.assistant.server.agent.ba | Business logic | public |
| UserCredentials | com.assistant.server.agent.ba | Application component | data |
| ServerConfig | com.assistant.server.agent.config | Configuration | data |
| DatabaseConfig | com.assistant.server.agent.config | Configuration | data |
| DataMigrationService | com.assistant.server.agent.ba | Business logic | public |
| DataSourceFactory | com.assistant.server.agent.ba | Object creation | public |
| for | com.assistant.server.agent.ba | Application component | public |
| DocumentRepository | com.assistant.server.agent.ba | Data access | public |
| GeneratedDocumentMeta | com.assistant.server.agent.ba | Application component | data |
| FlywayMigrator | com.assistant.server.agent.ba | Application component | public |
| for | com.assistant.server.agent.ba | Application component | public |
| JobRepository | com.assistant.server.agent.ba | Data access | public |
| PgChatConversationRepository | com.assistant.server.agent.ba | Data access | public |
| PgChatConversationSql | com.assistant.server.agent.ba | Application component | internal |
| PgChatRepository | com.assistant.server.agent.ba | Data access | public |
| PgChatSql | com.assistant.server.agent.ba | Application component | internal |
| PgDocumentRepository | com.assistant.server.agent.ba | Data access | public |
| PgDocumentSql | com.assistant.server.agent.ba | Application component | internal |
| PgJobRepository | com.assistant.server.agent.ba | Data access | public |
| PgJobSql | com.assistant.server.agent.ba | Application component | internal |
| PgKBRepository | com.assistant.server.agent.ba | Data access | public |
| PgKBSql | com.assistant.server.agent.ba | Application component | internal |
| PgMcpServerRepository | com.assistant.server.agent.ba | Data access | public |
| PgMcpServerSql | com.assistant.server.agent.ba | Application component | internal |
| PgProviderConfigRepository | com.assistant.server.agent.config | Data access | public |
| PgProviderConfigSql | com.assistant.server.agent.config | Application component | internal |
| PgScanLogRepository | com.assistant.server.agent.ba | Data access | public |
| PgScanLogSql | com.assistant.server.agent.ba | Application component | internal |
| PgScanStateRepository | com.assistant.server.agent.ba | Data access | public |
| PgScanStateSql | com.assistant.server.agent.ba | Application component | internal |
| PgSettingsRepository | com.assistant.server.agent.ba | Data access | public |
| PgSettingsSql | com.assistant.server.agent.ba | Application component | internal |
| PgUserAIConfigRepository | com.assistant.server.agent.config | Data access | public |
| PgUserAIConfigSql | com.assistant.server.agent.config | Application component | internal |
| PgUserToolPermissionRepository | com.assistant.server.agent.ba | Data access | public |
| PgUserToolPermissionSql | com.assistant.server.agent.ba | Application component | internal |
| LoginRequest | com.assistant.server.agent.ba | Data transfer object | data |
| LoginSuccessResponse | com.assistant.server.agent.ba | Data transfer object | data |
| UserResponse | com.assistant.server.agent.ba | Data transfer object | data |
| ErrorResponse | com.assistant.server.agent.ba | Data transfer object | data |
| MessageResponse | com.assistant.server.agent.ba | Data transfer object | data |
| HealthResponse | com.assistant.server.agent.ba | Data transfer object | data |
| ComponentHealth | com.assistant.server.agent.ba | Application component | data |
| ProjectsResponse | com.assistant.server.agent.ba | Data transfer object | data |
| SettingsStatusResponse | com.assistant.server.agent.ba | Data transfer object | data |
| FeatureToggleRequest | com.assistant.server.agent.ba | Data transfer object | data |
| FeatureToggleResponse | com.assistant.server.agent.ba | Data transfer object | data |
| kb_records | com.assistant.server.agent.ba | Application component | public |
| graph_data | com.assistant.server.agent.ba | Application component | public |
| users | com.assistant.server.agent.ba | Application component | public |
| audit_log | com.assistant.server.agent.ba | Application component | public |
| provider_configs | com.assistant.server.agent.config | Application component | public |
| app_settings | com.assistant.server.agent.ba | Application component | public |
| scan_states | com.assistant.server.agent.ba | Application component | public |
| scan_log | com.assistant.server.agent.ba | Application component | public |
| chat_messages | com.assistant.server.agent.ba | Application component | public |
| chat_conversations | com.assistant.server.agent.ba | Application component | public |
| user_ai_config | com.assistant.server.agent.config | Application component | public |
| mcp_servers | com.assistant.server.agent.ba | Application component | public |
| attachment_chunks | com.assistant.server.agent.ba | Application component | public |
| user_tool_permissions | com.assistant.server.agent.ba | Application component | public |
| generated_documents | com.assistant.server.agent.ba | Application component | public |
| generation_jobs | com.assistant.server.agent.ba | Application component | public |
| collection_jobs | com.assistant.server.agent.ba | Application component | public |
| traversal_cache | com.assistant.server.agent.ba | Application component | public |
| deep_collection_rate_limits | com.assistant.server.agent.ba | Application component | public |
| AuthServicePropertyTest | com.assistant.server.agent.ba | Test class | public |
| EmptyProviderConfigRepo | com.assistant.server.agent.config | Application component | private |
| ServerConfigTest | com.assistant.server.agent.config | Test class | public |
| FakeSettingsRepository | com.assistant.server.agent.ba | Data access | private |
| DatabaseConfigPropertyTest | com.assistant.server.agent.config | Test class | public |
| DataMigrationIdempotencyTest | com.assistant.server.agent.ba | Test class | public |
| DataMigrationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| DataMigrationTestHelper | com.assistant.server.agent.ba | Utility functions | internal |
| GraphDataPersistencePropertyTest | com.assistant.server.agent.ba | Test class | public |
| KBRecordPersistencePropertyTest | com.assistant.server.agent.ba | Test class | public |
| RBACAuditLogPropertyTest | com.assistant.server.agent.ba | Test class | public |
| RBACPermissionMatrixPropertyTest | com.assistant.server.agent.ba | Test class | public |
| ProjectsResponseTest | com.assistant.server.agent.ba | Test class | public |
| SettingsRepositoryImplTest | com.assistant.server.agent.ba | Test class | public |
| SettingsStatusTest | com.assistant.server.agent.ba | Test class | public |
| EstimationRequest | com.assistant.server.agent.ba | Data transfer object | data |
| HistoricalTicketDto | com.assistant.server.agent.ba | Data transfer object | data |
| NodeTypeInfoDto | com.assistant.server.agent.ba | Data transfer object | data |
| GraphResponse | com.assistant.server.agent.ba | Data transfer object | data |
| GraphNodeDto | com.assistant.server.agent.ba | Data transfer object | data |
| GraphEdgeDto | com.assistant.server.agent.ba | Data transfer object | data |
| GraphClusterDto | com.assistant.server.agent.ba | Data transfer object | data |
| ScanStatusResponse | com.assistant.server.agent.ba | Data transfer object | data |
| ScanLogEntryResponse | com.assistant.server.agent.ba | Data transfer object | data |
| ScanLogResponse | com.assistant.server.agent.ba | Data transfer object | data |
| AIStatusResponse | com.assistant.server.agent.ba | Data transfer object | data |
| ConflictException | com.assistant.server.agent.ba | Error handling | public |
| DashboardRoutingTest | com.assistant.server.agent.ba | Test class | public |
| PgCollectionJobRepository | com.assistant.server.agent.ba | Data access | public |
| PgCollectionJobSql | com.assistant.server.agent.ba | Application component | internal |
| InMemoryTraversalCache | com.assistant.server.agent.ba | Application component | public |
| CacheEntry | com.assistant.server.agent.ba | Application component | data |
| TraversalCache | com.assistant.server.agent.ba | Application component | public |
| TraversalCacheImpl | com.assistant.server.agent.ba | Application component | public |
| TraversalCacheRepository | com.assistant.server.agent.ba | Data access | public |
| CacheEntry | com.assistant.server.agent.ba | Application component | data |
| AttachmentContentCollector | com.assistant.server.agent.ba | Application component | public |
| CommentCollector | com.assistant.server.agent.ba | Application component | public |
| AttachmentCurator | com.assistant.server.agent.ba | Application component | public |
| BudgetEnforcer | com.assistant.server.agent.ba | Application component | public |
| CommentSummarizer | com.assistant.server.agent.ba | Application component | public |
| CuratedPromptAssembler | com.assistant.server.agent.ba | Application component | public |
| CurationConfig | com.assistant.server.agent.config | Configuration | public |
| CurationPipeline | com.assistant.server.agent.ba | Application component | public |
| CurationSettings | com.assistant.server.agent.ba | Application component | public |
| DefaultAttachmentCurator | com.assistant.server.agent.ba | Application component | public |
| DefaultBudgetEnforcer | com.assistant.server.agent.ba | Application component | public |
| DefaultCommentSummarizer | com.assistant.server.agent.ba | Application component | public |
| DefaultCurationPipeline | com.assistant.server.agent.ba | Application component | public |
| DefaultMcpToolRegistrar | com.assistant.server.agent.ba | Application component | public |
| DefaultTemporalClassifier | com.assistant.server.agent.ba | Application component | public |
| McpToolRegistrar | com.assistant.server.agent.ba | Application component | public |
| BudgetResult | com.assistant.server.agent.ba | Application component | data |
| TemporalRelation | com.assistant.server.agent.ba | Application component | public |
| ContentClassification | com.assistant.server.agent.ba | Application component | public |
| TicketClassification | com.assistant.server.agent.ba | Application component | data |
| CommentSummary | com.assistant.server.agent.ba | Application component | data |
| CuratedAttachment | com.assistant.server.agent.ba | Application component | data |
| CuratedContext | com.assistant.server.agent.ba | Application component | data |
| CurationMetrics | com.assistant.server.agent.ba | Application component | data |
| ToBeSection | com.assistant.server.agent.ba | Application component | data |
| AsIsSection | com.assistant.server.agent.ba | Application component | data |
| ClassifiedTicketData | com.assistant.server.agent.ba | Application component | data |
| OutdatedReference | com.assistant.server.agent.ba | Application component | data |
| TicketReference | com.assistant.server.agent.ba | Application component | data |
| TemporalClassifier | com.assistant.server.agent.ba | Application component | public |
| DeepCollectionSettings | com.assistant.server.agent.ba | Application component | public |
| DeepCollector | com.assistant.server.agent.ba | Application component | public |
| DeepJiraContentExtractor | com.assistant.server.agent.ba | Application component | public |
| DocumentAggregatorImpl | com.assistant.server.agent.ba | Application component | public |
| is | com.assistant.server.agent.ba | Application component | public |
| TicketIdExtractor | com.assistant.server.agent.ba | Application component | public |
| FeatureFlagAggregator | com.assistant.server.agent.ba | Application component | public |
| FeatureFlagContentExtractor | com.assistant.server.agent.ba | Application component | public |
| CollectionJobManager | com.assistant.server.agent.ba | Application component | public |
| CollectionJobManagerImpl | com.assistant.server.agent.ba | Application component | public |
| CollectionJobRepository | com.assistant.server.agent.ba | Data access | public |
| AttachmentCollectionResult | com.assistant.server.agent.ba | Application component | data |
| CollectionJobType | com.assistant.server.agent.ba | Application component | public |
| CollectionJobStatus | com.assistant.server.agent.ba | Application component | public |
| CollectionJobItemStatus | com.assistant.server.agent.ba | Application component | public |
| CollectionJobItem | com.assistant.server.agent.ba | Application component | data |
| CollectionJob | com.assistant.server.agent.ba | Application component | data |
| CollectionJobResponse | com.assistant.server.agent.ba | Data transfer object | data |
| CollectionJobItemResponse | com.assistant.server.agent.ba | Data transfer object | data |
| FullComment | com.assistant.server.agent.ba | Application component | data |
| is | com.assistant.server.agent.ba | Application component | public |
| CommentCollectionResult | com.assistant.server.agent.ba | Application component | data |
| EnrichedContext | com.assistant.server.agent.ba | Application component | public |
| used | com.assistant.server.agent.ba | Application component | data |
| EnrichedContextSurrogate | com.assistant.server.agent.ba | Application component | data |
| EnrichedContextSerializer | com.assistant.server.agent.ba | Application component | internal |
| RelationshipType | com.assistant.server.agent.ba | Application component | public |
| TicketNode | com.assistant.server.agent.ba | Application component | data |
| TicketEdge | com.assistant.server.agent.ba | Application component | data |
| TicketGraph | com.assistant.server.agent.ba | Application component | data |
| TraversalConfig | com.assistant.server.agent.config | Configuration | data |
| TraversalMetadata | com.assistant.server.agent.ba | Application component | data |
| PromptAssembler | com.assistant.server.agent.ba | Application component | public |
| PromptSkeleton | com.assistant.server.agent.ba | Application component | data |
| AssemblyResult | com.assistant.server.agent.ba | Application component | data |
| PromptSectionType | com.assistant.server.agent.ba | Application component | public |
| PromptPriorityConfig | com.assistant.server.agent.config | Configuration | public |
| PromptSectionBuilder | com.assistant.server.agent.ba | Application component | internal |
| InMemoryRateLimiter | com.assistant.server.agent.ba | Application component | public |
| RateLimiter | com.assistant.server.agent.ba | Application component | public |
| RateLimitExceededException | com.assistant.server.agent.ba | Error handling | public |
| RateLimiterImpl | com.assistant.server.agent.ba | Application component | public |
| RateLimitRepository | com.assistant.server.agent.ba | Data access | public |
| TicketGraphHolder | com.assistant.server.agent.ba | Application component | public |
| KBFirstTicketFetcher | com.assistant.server.agent.ba | Application component | public |
| DiscoveredTicket | com.assistant.server.agent.ba | Application component | data |
| RelatedTicketDiscovery | com.assistant.server.agent.ba | Application component | public |
| RelevanceScorer | com.assistant.server.agent.ba | Application component | public |
| FetchResult | com.assistant.server.agent.ba | Application component | sealed |
| Success | com.assistant.server.agent.ba | Application component | data |
| PermissionDenied | com.assistant.server.agent.ba | Application component | data |
| Failed | com.assistant.server.agent.ba | Application component | data |
| TicketFetcher | com.assistant.server.agent.ba | Application component | open |
| TraversalEngine | com.assistant.server.agent.ba | Application component | public |
| BfsQueueItem | com.assistant.server.agent.ba | Application component | data |
| TraversalState | com.assistant.server.agent.ba | Application component | public |
| DependencyChecker | com.assistant.server.agent.ba | Application component | public |
| CheckResult | com.assistant.server.agent.ba | Application component | data |
| DocGenProgressTracker | com.assistant.server.agent.ba | Application component | public |
| JobChainOrchestrator | com.assistant.server.agent.ba | Application component | public |
| JobExecutor | com.assistant.server.agent.ba | Application component | open |
| SubprocessFailedException | com.assistant.server.agent.ba | Error handling | public |
| SubprocessAgentStub | com.assistant.server.agent.ba | Application component | internal |
| JobExecutorDocHelper | com.assistant.server.agent.ba | Utility functions | internal |
| JobManager | com.assistant.server.agent.ba | Application component | public |
| GenerationLockException | com.assistant.server.agent.ba | Error handling | public |
| DependencyException | com.assistant.server.agent.ba | Error handling | public |
| JobNotFoundException | com.assistant.server.agent.ba | Error handling | public |
| InvalidTransitionException | com.assistant.server.agent.ba | Error handling | public |
| PhaseLabelMapper | com.assistant.server.agent.ba | Data mapping | public |
| RejectBody | com.assistant.server.agent.ba | Application component | data |
| UserPrincipal | com.assistant.server.agent.ba | Application component | data |
| JobResponseDto | com.assistant.server.agent.ba | Data transfer object | data |
| TraversalCachePropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeCacheJiraClient | com.assistant.server.agent.ba | External service client | public |
| AttachmentCollectorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CommentCollectorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeCommentJiraClient | com.assistant.server.agent.ba | External service client | public |
| FakeVectorStore | com.assistant.server.agent.ba | Application component | public |
| AttachmentCuratorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| BudgetEnforcerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CommentSummarizerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CommentSummarizerTest | com.assistant.server.agent.ba | Test class | public |
| CuratedPromptAssemblerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CurationIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| CurationPipelinePropertyTest | com.assistant.server.agent.ba | Test class | public |
| GeminiTimeoutTest | com.assistant.server.agent.ba | Test class | public |
| CurationArbitraries | com.assistant.server.agent.ba | Application component | public |
| McpToolRegistrarTest | com.assistant.server.agent.ba | Test class | public |
| TemporalClassifierPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TemporalClassifierTest | com.assistant.server.agent.ba | Test class | public |
| DeepCollectorIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| DeepCollectorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TicketGraphSpec | com.assistant.server.agent.ba | Test class | data |
| FakeKBRepository | com.assistant.server.agent.ba | Data access | public |
| NoOpTraversalCache | com.assistant.server.agent.ba | Application component | public |
| NoOpRateLimiter | com.assistant.server.agent.ba | Application component | public |
| NoOpCollectionJobManager | com.assistant.server.agent.ba | Application component | public |
| NoOpScanLogRepository | com.assistant.server.agent.ba | Data access | public |
| GraphJiraClient | com.assistant.server.agent.ba | External service client | public |
| DocumentAggregatorImplTest | com.assistant.server.agent.ba | Test class | public |
| DocumentApproveBugExplorationTest | com.assistant.server.agent.ba | Test class | public |
| DocumentPreservationGenerators | com.assistant.server.agent.ba | Application component | public |
| DocumentPreservationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TicketIdExtractorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InMemorySettings | com.assistant.server.agent.ba | Application component | public |
| NoOpEmbedding | com.assistant.server.agent.ba | Application component | private |
| CollectionJobManagerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| CollectionJobSpec | com.assistant.server.agent.ba | Test class | data |
| InMemoryCollectionJobRepository | com.assistant.server.agent.ba | Data access | public |
| NoOpDownloader | com.assistant.server.agent.ba | Application component | private |
| NoOpEmbeddingService | com.assistant.server.agent.ba | Business logic | private |
| NoOpMcpProcessManager | com.assistant.server.agent.ba | Application component | private |
| equivalent | com.assistant.server.agent.ba | Application component | public |
| EnrichedContextPropertyTest | com.assistant.server.agent.ba | Test class | public |
| that | com.assistant.server.agent.ba | Application component | public |
| produces | com.assistant.server.agent.ba | Application component | public |
| TraversalConfigPropertyTest | com.assistant.server.agent.config | Test class | public |
| PromptAssemblerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| RateLimiterPropertyTest | com.assistant.server.agent.ba | Test class | public |
| DummyJiraClient | com.assistant.server.agent.ba | External service client | internal |
| DummySectionClassifier | com.assistant.server.agent.ba | Application component | internal |
| FakeTicketFetcher | com.assistant.server.agent.ba | Application component | public |
| RelevanceScorerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TraversalEnginePropertyTest | com.assistant.server.agent.ba | Test class | public |
| TraversalEngineSecurityPropertyTest | com.assistant.server.agent.ba | Test class | public |
| TraversalEngineTerminationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| LinkedGraph | com.assistant.server.agent.ba | Application component | data |
| DocGenUxPropertyTest | com.assistant.server.agent.ba | Test class | public |
| FakeOllamaAgent | com.assistant.server.agent.ba | Application component | public |
| FakeNonStreamingAgent | com.assistant.server.agent.ba | Application component | public |
| FakeOrchestrator | com.assistant.server.agent.ba | Application component | public |
| TrackingAggregator | com.assistant.server.agent.ba | Application component | public |
| NoOpJobRepo | com.assistant.server.agent.ba | Application component | public |
| NoOpDocRepo | com.assistant.server.agent.ba | Application component | public |
| InMemoryDocumentRepository | com.assistant.server.agent.ba | Data access | public |
| InMemoryJobRepository | com.assistant.server.agent.ba | Data access | public |
| JobExecutorStreamingTest | com.assistant.server.agent.ba | Test class | public |
| JobExecutorSubprocessDirectPropertyTest | com.assistant.server.agent.ba | Test class | public |
| PropFakeOrchestrator | com.assistant.server.agent.ba | Application component | private |
| PropCapturingDocRepo | com.assistant.server.agent.ba | Application component | private |
| PropNoOpReporter | com.assistant.server.agent.ba | Application component | private |
| PropStubManager | com.assistant.server.agent.ba | Application component | private |
| PropStubProxy | com.assistant.server.agent.ba | Application component | private |
| PropStubSettings | com.assistant.server.agent.ba | Application component | private |
| JobExecutorSubprocessDirectTest | com.assistant.server.agent.ba | Test class | public |
| CapturingDocRepo | com.assistant.server.agent.ba | Application component | private |
| TrackingJobRepo | com.assistant.server.agent.ba | Application component | private |
| JobManagerUnitTest | com.assistant.server.agent.ba | Test class | public |
| JobManagerVersioningUnitTest | com.assistant.server.agent.ba | Test class | public |
| JobRecoveryVersioningPropertyTest | com.assistant.server.agent.ba | Test class | public |
| JobStateMachinePropertyTest | com.assistant.server.agent.ba | Test class | public |
| JobTestGenerators | com.assistant.server.agent.ba | Application component | public |
| NoOpJobExecutor | com.assistant.server.agent.ba | Application component | public |
| ProgressMappingRangePropertyTest | com.assistant.server.agent.ba | Test class | public |
| StreamingPipelineIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| TestableJobExecutor | com.assistant.server.agent.ba | Application component | public |
| ProgressWrite | com.assistant.server.agent.ba | Application component | data |
| TestJobManager | com.assistant.server.agent.ba | Application component | public |
| ThrottleLogicPropertyTest | com.assistant.server.agent.ba | Test class | public |
| KnowledgeGraphRoutingTest | com.assistant.server.agent.ba | Test class | public |
| ProcessResult | com.assistant.server.agent.ba | Application component | data |
| CopilotCliAgent | com.assistant.server.agent.ba | Application component | public |
| ProcessResult | com.assistant.server.agent.ba | Application component | data |
| GeminiCliAgent | com.assistant.server.agent.ba | Application component | public |
| KiroCliAgent | com.assistant.server.agent.ba | Application component | public |
| HttpMcpProtocolClient | com.assistant.server.agent.ba | External service client | public |
| AnalysisToolDefs | com.assistant.server.agent.ba | Application component | public |
| ArgumentValidator | com.assistant.server.agent.ba | Input validation | public |
| ChatToolDefs | com.assistant.server.agent.ba | Application component | public |
| DiagramToolDefs | com.assistant.server.agent.ba | Application component | public |
| AnalysisHandlers | com.assistant.server.agent.ba | Application component | public |
| ChatHandlers | com.assistant.server.agent.ba | Application component | public |
| DashboardHandlers | com.assistant.server.agent.ba | Application component | public |
| DiagramHandlers | com.assistant.server.agent.ba | Application component | public |
| DiagramResult | com.assistant.server.agent.ba | Application component | data |
| DiagramFileInfo | com.assistant.server.agent.ba | Application component | data |
| IntegrationHandlers | com.assistant.server.agent.ba | Application component | public |
| KnowledgeGraphHandlers | com.assistant.server.agent.ba | Application component | public |
| NavigationHandlers | com.assistant.server.agent.ba | Application component | public |
| PageInfo | com.assistant.server.agent.ba | Application component | data |
| ScanHandlers | com.assistant.server.agent.ba | Application component | public |
| SettingsHandlers | com.assistant.server.agent.ba | Application component | public |
| UserManagementHandlers | com.assistant.server.agent.ba | Application component | public |
| IntegrationToolDefs | com.assistant.server.agent.ba | Application component | public |
| InternalMcpBridge | com.assistant.server.agent.ba | Application component | open |
| InternalMcpToolExecutor | com.assistant.server.agent.ba | Application component | public |
| InternalToolRegistry | com.assistant.server.agent.ba | Application component | public |
| NavigationToolDefs | com.assistant.server.agent.ba | Application component | public |
| ScanToolDefs | com.assistant.server.agent.ba | Application component | public |
| with | com.assistant.server.agent.ba | Application component | public |
| property | com.assistant.server.agent.ba | Application component | public |
| SettingsToolDefs | com.assistant.server.agent.ba | Application component | public |
| UserContext | com.assistant.server.agent.ba | Application component | data |
| ManagedProcess | com.assistant.server.agent.ba | Application component | data |
| McpHealthChecker | com.assistant.server.agent.ba | Application component | public |
| McpLogEntry | com.assistant.server.agent.ba | Application component | data |
| McpLogBuffer | com.assistant.server.agent.ba | Application component | public |
| McpProcessManagerImpl | com.assistant.server.agent.ba | Application component | public |
| to | com.assistant.server.agent.ba | Application component | public |
| McpProtocolClientImpl | com.assistant.server.agent.ba | Application component | public |
| ProcessSpawner | com.assistant.server.agent.ba | Application component | public |
| ProviderConfigUpdateRequest | com.assistant.server.agent.config | Data transfer object | data |
| JiraConfigRequest | com.assistant.server.agent.config | Data transfer object | data |
| JiraConfigResponse | com.assistant.server.agent.config | Data transfer object | data |
| ProviderTestRequest | com.assistant.server.agent.ba | Data transfer object | data |
| ProviderStatusUpdateRequest | com.assistant.server.agent.ba | Data transfer object | data |
| OllamaModelsResponse | com.assistant.server.agent.ba.models | Data transfer object | data |
| OllamaModelInfo | com.assistant.server.agent.ba.models | Application component | data |
| ProviderTestResult | com.assistant.server.agent.ba | Application component | data |
| JiraStatusResponse | com.assistant.server.agent.ba | Data transfer object | data |
| McpTestResult | com.assistant.server.agent.ba | Application component | data |
| InternalArgumentValidationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InternalBusinessErrorPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InternalPageFilteringPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InternalRbacEnforcementPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InternalToolAggregationPropertyTest | com.assistant.server.agent.ba | Test class | public |
| with | com.assistant.server.agent.ba | Application component | public |
| InternalToolDefinitionsPropertyTest | com.assistant.server.agent.ba | Test class | public |
| and | com.assistant.server.agent.ba | Application component | public |
| McpAgenticLoopPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpAutoApprovePropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpCriticalServerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpHealthCheckerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryRepo | com.assistant.server.agent.ba | Application component | private |
| ConfigurableProcessManager | com.assistant.server.agent.config | Application component | private |
| SuccessClient | com.assistant.server.agent.ba | External service client | private |
| FailingClient | com.assistant.server.agent.ba | External service client | private |
| RoleCase | com.assistant.server.agent.ba | Application component | data |
| McpHealthCheckerTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryRepo | com.assistant.server.agent.ba | Application component | private |
| ConfigurableProcessManager | com.assistant.server.agent.config | Application component | private |
| SuccessClient | com.assistant.server.agent.ba | External service client | public |
| FailingClient | com.assistant.server.agent.ba | External service client | private |
| McpPlaywrightIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| McpProcessManagerPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpProtocolClientPropertyTest | com.assistant.server.agent.ba | Test class | public |
| McpStatusMergeTest | com.assistant.server.agent.ba | Test class | public |
| InMemoryMcpRepo | com.assistant.server.agent.ba | Application component | public |
| StubProcessManager | com.assistant.server.agent.ba | Application component | public |
| kb_records | com.assistant.server.agent.ba | Application component | public |
| graph_data | com.assistant.server.agent.ba | Application component | public |
| users | com.assistant.server.agent.ba | Application component | public |
| audit_log | com.assistant.server.agent.ba | Application component | public |
| provider_configs | com.assistant.server.agent.config | Application component | public |
| app_settings | com.assistant.server.agent.ba | Application component | public |
| scan_states | com.assistant.server.agent.ba | Application component | public |
| scan_log | com.assistant.server.agent.ba | Application component | public |
| chat_messages | com.assistant.server.agent.ba | Application component | public |
| chat_conversations | com.assistant.server.agent.ba | Application component | public |
| user_ai_config | com.assistant.server.agent.config | Application component | public |
| mcp_servers | com.assistant.server.agent.ba | Application component | public |
| attachment_chunks | com.assistant.server.agent.ba | Application component | public |
| user_tool_permissions | com.assistant.server.agent.ba | Application component | public |
| generated_documents | com.assistant.server.agent.ba | Application component | public |
| generation_jobs | com.assistant.server.agent.ba | Application component | public |
| collection_jobs | com.assistant.server.agent.ba | Application component | public |
| traversal_cache | com.assistant.server.agent.ba | Application component | public |
| deep_collection_rate_limits | com.assistant.server.agent.ba | Application component | public |
| configuration | com.assistant.server.agent.config | Application component | public |
| appender | com.assistant.server.agent.ba | Application component | public |
| encoder | com.assistant.server.agent.ba | Application component | public |
| pattern | com.assistant.server.agent.ba | Application component | public |
| file | com.assistant.server.agent.ba | Application component | public |
| rollingPolicy | com.assistant.server.agent.ba | Application component | public |
| fileNamePattern | com.assistant.server.agent.ba | Application component | public |
| maxHistory | com.assistant.server.agent.ba | Application component | public |
| totalSizeCap | com.assistant.server.agent.ba | Application component | public |
| filter | com.assistant.server.agent.ba | Application component | public |
| level | com.assistant.server.agent.ba | Application component | public |
| root | com.assistant.server.agent.ba | Application component | public |
| appender-ref | com.assistant.server.agent.ba | Application component | public |
| logger | com.assistant.server.agent.ba | Application component | public |
| DynamicConfigRefreshTest | com.assistant.server.agent.config | Test class | public |
| TrackingAgent | com.assistant.server.agent.ba | Application component | public |
| EmptyKB | com.assistant.server.agent.ba | Application component | public |
| StubGraph | com.assistant.server.agent.ba | Application component | public |
| HardcodedIdAuditTest | com.assistant.server.agent.ba | Test class | public |
| KoinModuleIntegrationTest | com.assistant.server.agent.ba | Test class | public |
| RouteRegistrationSmokeTest | com.assistant.server.agent.ba | Test class | public |
| Endpoint | com.assistant.server.agent.ba | Application component | data |
| NoOpDataSource | com.assistant.server.agent.ba | Application component | private |
| configuration | com.assistant.server.agent.config | Application component | public |
| appender | com.assistant.server.agent.ba | Application component | public |
| encoder | com.assistant.server.agent.ba | Application component | public |
| pattern | com.assistant.server.agent.ba | Application component | public |
| file | com.assistant.server.agent.ba | Application component | public |
| rollingPolicy | com.assistant.server.agent.ba | Application component | public |
| fileNamePattern | com.assistant.server.agent.ba | Application component | public |
| maxHistory | com.assistant.server.agent.ba | Application component | public |
| totalSizeCap | com.assistant.server.agent.ba | Application component | public |
| root | com.assistant.server.agent.ba | Application component | public |
| appender-ref | com.assistant.server.agent.ba | Application component | public |
| logger | com.assistant.server.agent.ba | Application component | public |
| serverName | com.assistant.server.agent.ba | Application component | public |
| command | com.assistant.server.agent.ba | Application component | public |
| args | com.assistant.server.agent.ba | Application component | public |
| env | com.assistant.server.agent.ba | Application component | public |
| test.gemini.cli.path | com.assistant.server.agent.ba | Application component | public |
| test.gemini.cli.model | com.assistant.server.agent.ba.models | Application component | public |
| test.copilot.cli.path | com.assistant.server.agent.ba | Application component | public |
| test.copilot.cli.model | com.assistant.server.agent.ba.models | Application component | public |
| test.kiro.cli.path | com.assistant.server.agent.ba | Application component | public |
| test.kiro.cli.model | com.assistant.server.agent.ba.models | Application component | public |
| test.ollama.cli.path | com.assistant.server.agent.ba | Application component | public |
| test.ollama.cli.model | com.assistant.server.agent.ba.models | Application component | public |
| test.brd.ticket | com.assistant.server.agent.ba | Application component | public |
| test.mcp.jira | com.assistant.server.agent.ba | Application component | public |
| TestConfigFactory | com.assistant.server.agent.config | Object creation | public |
| ChangeRoleRequest | com.assistant.server.agent.ba | Data transfer object | data |
| TogglePermissionRequest | com.assistant.server.agent.ba | Data transfer object | data |
| UserDto | com.assistant.server.agent.ba | Data transfer object | data |
| UserMgmtRoutingTest | com.assistant.server.agent.ba | Test class | public |

## Public API Surface

- `buildBAAgentConfig(): AgentConfig`
- `capReasoningLog(state: AgentState): AgentState`
- `mapPhaseToLabel(phaseName: String): String`
- `createMemory(): StructuredMemory`
- `apply(memory: StructuredMemory, curator: AttachmentCurator): Unit`
- `apply(memory: StructuredMemory, summarizer: CommentSummarizer): Unit`
- `buildOutdatedMetadata(memory: StructuredMemory): String`
- `hasClassifications(memory: StructuredMemory): Boolean`
- `buildRoleInstruction(docType: String): String`
- `buildContext(memory: StructuredMemory): String`
- `buildTemplateStructure(docType: String): String`
- `buildOutputFormat(): String`
- `buildDiagramInstructions(): String`
- `check(document: String, docType: String): QualityResult`
- `addToolCall(entry: ToolCallLogEntry, success: Boolean): Unit`
- `toResult(): Unit`
- `determineStatus(result: AgenticLoopResult): BATaskStatus`
- `buildInitialPrompt(ticketId: String, docType: String): String`
- `buildPersistentContinuation(latestToolResult: String): String`
- `startSession(): Unit`
- `endSession(): Unit`
- `isSessionActive(): Boolean`
- `isToolCall(response: String): Boolean`
- `parseToolCall(response: String): ToolRequest`
- `isInstalled(): Boolean`
- `getInstallInstructions(): String`
- `buildCommandArgs(prompt: String): List`
- `buildPersistentCommandArgs(isResume: Boolean): List`
- `assemble(brdMarkdown: String, diagramOutput: String?): String`
- `getSkillContent(): String`
- `getDescriptors(): List`
- `mapAliasToOriginal(aliasName: String): String`
- `runCommand(vararg command: String): String`
- `findExecutablePath(name: String): String`
- `findNodePath(): String`
- `findCliJsPath(config: NodeCliConfig): String`
- `resolve(config: NodeCliConfig): ResolvedPaths`
- `cancel(): Unit`
- `isToolCall(json: Json, response: String): Boolean`
- `parseToolCall(json: Json, response: String): ToolRequest`
- `checkAvailability(httpClient: HttpClient, baseUrl: String): Boolean`
- `buildAiCliResponse(json: Json, responseBody: String): AiCliResponse`
- `argumentsAsStrings(): Map`
- `buildPhase1Prompt(ticketId: String, tools: List<ToolDescriptor>): String`
- `buildPhaseContinuation(latestToolResult: String): String`
- `hasKbTools(tools: List<ToolDescriptor>): Boolean`
- `filterForPhase1(tools: List<ToolDescriptor>): List`
- `filterForPhase2(tools: List<ToolDescriptor>): List`
- `filterForPhase3(tools: List<ToolDescriptor>): List`
- `create(ticketId: String): InteractionLog`
- `logPhaseStart(phaseId: PhaseId, promptSize: Int): Unit`
- `logPrompt(label: String, content: String): Unit`
- `logResponse(content: String): Unit`
- `logToolCall(toolName: String, params: Map<String, String>: Any): Unit`
- `logToolResult(toolName: String, success: Boolean, size: Int): Unit`
- `logAttachmentDetails(files: Map<String, Int>: Any): Unit`
- `logPhaseEnd(phaseId: PhaseId, outputSize: Int, toolCalls: Int): Unit`
- `logAssembly(brdSize: Int, diagramSize: Int, finalSize: Int): Unit`
- `startProcess(config: SubprocessConfig): Process`
- `terminateProcess(process: Process): Unit`
- `appendLine(line: String): Unit`
- `recordToolCall(success: Boolean): Unit`
- `toLoopResult(timedOut: Boolean): Unit`
- `recordToolCall(entry: ToolCallLogEntry): Unit`
- `appendDocumentLine(line: String): Unit`
- `recordConsecutiveFailure(): Unit`
- `resetConsecutiveFailures(): Unit`
- `toSummary(): SessionSummary`
- `toBATaskResult(status: BATaskStatus): BATaskResult`
- `parseToolCall(line: String): ParsedToolCall`
- `buildAnalysisPrompt(context: CollectedContext, docType: String): String`
- `buildRequirementsPrompt(analysisResult: String, linkedData: String): String`
- `buildWritingPrompt(accumulatedResults: String, docType: String): String`
- `buildReviewPrompt(feedback: String, currentDocument: String): String`
- `buildStrategyHint(docType: String): String`
- `appendDocument(line: String): Unit`
- `isLimitReached(): Unit`
- `incrementExecuted(): Unit`
- `incrementFailed(): Unit`
- `toResult(timedOut: Boolean): Unit`
- `log(message: String): Unit`
- `classifyError(error: Throwable): ErrorClassification`
- `ensureDirectories(basePath: Path, logger: Logger): Unit`
- `parseWorkflow(fileName: String, content: String): WorkflowDefinition`
- `start(scope: CoroutineScope): Unit`
- `stop(): Unit`
- `getActiveServerNames(): List`
- `getRegisteredToolCount(): Int`
- `buildToolName(serverName: String, toolName: String): String`
- `sanitizeServerName(name: String): String`
- `stripPrefix(prefixed: String, serverName: String): String`
- `toJsonObject(params: Map<String, String>: Any): JsonObject`
- `toToolResult(toolName: String, response: McpToolCallResponse): ToolResult`
- `resolve(tools: List<McpAggregatedTool>): List`
- `parse(fileName: String, content: String): RuleDefinition`
- `parse(fileName: String, content: String): SkillDefinition`
- `resume(serializedState: String): AgentState`
- `registerCallback(callback: StreamingCallback): Unit`
- `unregisterCallback(callback: StreamingCallback): Unit`
- `callbackCount(): Int`
- `isAlive(): Boolean`
- `touch(): Unit`
- `formatCommand(command: String): String`
- `formatToolResponse(response: ToolCallResponse): String`
- `formatToolList(tools: List<ToolDescriptor>): String`
- `formatToolsUpdated(tools: List<ToolDescriptor>): String`
- `parseStdoutLine(line: String): SubprocessMessage`
- `isDelimiter(line: String): Boolean`
- `registerConfig(agentType: String, config: SubprocessConfig): Unit`
- `extractParameterNames(inputSchema: JsonElement): List`
- `resolve(agentName: String): ToolNameMapping`
- `hasMapping(agentName: String): Boolean`
- `getMcpName(agentName: String): String`
- `findByMcpTool(serverName: String, mcpToolName: String): ToolNameMapping`
- `fromConfig(config: Map<String, Map<String: Any, String>>: Any): ToolNameMapper`
- `isEnabled(settingsRepository: SettingsRepository?): Boolean`
- `buildToolsContext(enabled: Boolean): List`
- `buildPriorityHint(enabled: Boolean): String`
- `startHeartbeat(fromPercent: Int, maxPercent: Int, intervalMs: Long): Unit`
- `stopHeartbeat(): Unit`
- `tearDown(): Unit`
- `setup(): Unit`
- `addProvider(config: ProviderConfig): Unit`
- `clear(): Unit`
- `simpleDocStdoutProvider(): Unit`
- `toolCallStdoutProvider(): Unit`
- `exposeCliConfig(): NodeCliConfig`
- `exposeBuildCommandArgs(p: String): Unit`
- `exposeBuildPersistentCommandArgs(r: Boolean): Unit`
- `exposeBuildCommandArgs(p: String): Unit`
- `exposeCliConfig(): NodeCliConfig`
- `exposeBuildCommandArgs(p: String): Unit`
- `exposeBuildPersistentCommandArgs(r: Boolean): Unit`
- `exposeParseResponse(output: String): AiCliResponse`
- `exposeCliConfig(): NodeCliConfig`
- `exposeBuildCommandArgs(p: String): Unit`
- `exposeBuildPersistentCommandArgs(r: Boolean): Unit`
- `stubProxy(): SubprocessProxy`
- `stubReporter(): ProgressReporter`
- `stubResolver(): com`
- `arbToolCallBatch(): Arb`
- `arbMixedBatch(): Arb`
- `arbClassifiedError(): Arb`
- `capReasoningLog(log: List<String>): List`
- `arbAgentTypeName(): Arb`
- `arbAgentTypeSet(): Arb`
- `successTool(name: String, desc: String = "ok"): Unit`
- `throwingTool(name: String): Unit`
- `slowTool(name: String): Unit`
- `arbToolList(): Arb`
- `arbToolBehavior(): Arb`
- `reset(): Unit`
- `reset(): Unit`
- `seed(record: KBRecord): Unit`
- `reset(): Unit`
- `partition(graph: TicketGraph): List`
- `parse(response: String): BatchSummary`
- `validated(): MapReduceConfig`
- `onTraversalStart(ticketId: String, config: String): Unit`
- `onTraversalComplete(totalTickets: Int): Unit`
- `onMapStart(totalBatches: Int): Unit`
- `onBatchComplete(batchIndex: Int, totalBatches: Int, ticketCount: Int): Unit`
- `onBatchFailed(batchIndex: Int, totalBatches: Int, error: String): Unit`
- `onReduceStart(summaryCount: Int): Unit`
- `onReduceComplete(): Unit`
- `onParsingStart(): Unit`
- `onComplete(totalTimeMs: Long): Unit`
- `calculateMapProgress(completedBatches: Int, totalBatches: Int): Int`
- `isEligible(attachment: JiraAttachment): Boolean`
- `getExtension(filename: String): String`
- `compute(a: FloatArray, b: FloatArray): Float`
- `logStart(rootTicketId: String, ticketCount: Int, attachmentCount: Int): Unit`
- `logCompletion(rootTicketId: String, totalChunks: Int, processedTickets: Int, startTime: Long): Unit`
- `logTimeout(elapsed: Long, processed: Int, total: Int): Unit`
- `logTicketDebug(group: TicketAttachmentGroup): Unit`
- `logTicketError(ticketId: String, error: String): Unit`
- `logTopLevelError(rootTicketId: String, error: String?): Unit`
- `logSingleNodeSkip(rootTicketId: String): Unit`
- `logNoAttachments(rootTicketId: String): Unit`
- `chunk(text: String, maxTokens: Int = 1000): List`
- `get(rootTicketId: String): CacheEntry`
- `invalidate(rootTicketId: String): Unit`
- `analysisConfig(): TraversalConfig`
- `save(job: CollectionJob): Unit`
- `findById(jobId: String): CollectionJob`
- `findByParentTicketId(parentTicketId: String): List`
- `findActive(): List`
- `delete(jobId: String): Unit`
- `validated(): TraversalConfig`
- `countInLastHour(userId: String): Long`
- `record(userId: String): Unit`
- `cleanup(): Unit`
- `store(ticketId: String, graph: TicketGraph): Unit`
- `take(ticketId: String): TicketGraph`
- `compute(node: TicketNode): Double`
- `addNode(node: TicketNode): Unit`
- `addEdge(sourceId: String, targetId: String, type: RelationshipType, desc: String): Unit`
- `enqueue(item: BfsQueueItem): Unit`
- `updateDataSize(content: StructuredTicketContent): Unit`
- `incrementPermissionDenied(): Unit`
- `addSkipped(ticketId: String): Unit`
- `isVisited(ticketId: String): Boolean`
- `visitedIds(): Set`
- `hasWork(): Boolean`
- `isMaxTicketsReached(): Boolean`
- `isEarlyTermination(): Boolean`
- `dequeueCurrentLevel(): List`
- `nodeCount(): Int`
- `skippedCount(): Int`
- `skippedIds(): List`
- `permissionDeniedCount(): Int`
- `maxDepthReached(): Int`
- `dataSize(): Long`
- `totalDiscovered(): Int`
- `nodes(): Map`
- `edges(): List`
- `reindex(projectKey: String, graph: NetworkGraph, records: List<KBRecord>): Unit`
- `formatTicketText(ticket: TicketNode, description: String? = null): String`
- `formatClusterText(cluster: Cluster, nodeMap: Map<String, TicketNode>: Any): String`
- `formatAnalysisText(record: KBRecord): Unit`
- `formatEvolutionText(ticketId: String, entry: EvolutionEntry): Unit`
- `update(ticketId: String, phase: String, progressPercent: Int): Unit`
- `updatePhase(ticketId: String, phase: AnalysisPhase): Unit`
- `updatePhaseProgress(ticketId: String, phase: AnalysisPhase, percent: Int): Unit`
- `get(ticketId: String): AnalysisStatus`
- `remove(ticketId: String): Unit`
- `update(ticketId: String, result: CascadeResult): Unit`
- `get(ticketId: String): CascadeResult`
- `remove(ticketId: String): Unit`
- `isRunning(ticketId: String): Boolean`
- `testProvider(id: String = "mock-1"): Unit`
- `batchSummaryJson(batchIndex: Int, ticketIds: List<String>): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `teardown(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `stubNetworkMapper(): Unit`
- `setup(): Unit`
- `cleanTable(): Unit`
- `flushBatch(): Unit`
- `reset(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `runAnalysis(": Any): Unit`
- `runAnalysis(": Any): Unit`
- `hasDeepAnalysis(record: KBRecord): Boolean`
- `buildContext(record: KBRecord): String`
- `buildSuggestAnalyzeHint(ticketId: String): String`
- `build(gc: GraphChatContext?): String`
- `isEnabled(settingsRepository: SettingsRepository?): Boolean`
- `buildToolsContext(enabled: Boolean): List`
- `buildPriorityHint(enabled: Boolean): String`
- `buildBasePrompt(ctx: ChatContext): String`
- `parse(raw: String, usage: Int): ChatResponse`
- `build(tc: TicketChatContext?): String`
- `formatConfluenceText(page: ConfluencePage): String`
- `isConfluenceTool(toolName: String): Boolean`
- `isJiraTool(toolName: String): Boolean`
- `detectSyncType(toolName: String): SyncType`
- `parseJsonToolName(response: String): McpToolCallRequest`
- `parseTextPattern(response: String): McpToolCallRequest`
- `parseNaturalLanguage(response: String): McpToolCallRequest`
- `mapToolName(raw: String): String`
- `mapArgName(raw: String): String`
- `extractJsonObject(text: String, start: Int): String`
- `extractByPath(obj: JsonObject, path: String): String`
- `validate(permissions: Map<String, String>: Any): Result`
- `setUp(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setUp(): Unit`
- `setup(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `clear(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
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
- `get(rootTicketId: String): CacheEntry`
- `invalidate(rootTicketId: String): Unit`
- `enforce(context: CuratedContext, maxChars: Int): BudgetResult`
- `curate(context: EnrichedContext): CuratedContext`
- `buildToolBlock(referenceOnlyTickets: List<String>): String`
- `isToolUseSupported(agentType: String): Boolean`
- `analysisConfig(): TraversalConfig`
- `save(job: CollectionJob): Unit`
- `findById(jobId: String): CollectionJob`
- `findByParentTicketId(parentTicketId: String): List`
- `findActive(): List`
- `delete(jobId: String): Unit`
- `validated(): TraversalConfig`
- `priorityFor(docType: String): List`
- `formatComment(comment: FullComment): String`
- `formatChunk(chunk: AttachmentChunkInfo, ticketId: String): String`
- `buildRootRaw(context: EnrichedContext): String`
- `buildRootKb(context: EnrichedContext): String`
- `buildTicketsRaw(context: EnrichedContext, depth: Int): String`
- `buildDeeperTickets(context: EnrichedContext, minDepth: Int): String`
- `buildRootAttachments(context: EnrichedContext): String`
- `buildDepth1Attachments(context: EnrichedContext): String`
- `buildDeeperAttachments(context: EnrichedContext): String`
- `buildGraphMetadata(context: EnrichedContext): String`
- `countInLastHour(userId: String): Long`
- `record(userId: String): Unit`
- `cleanup(): Unit`
- `store(ticketId: String, graph: TicketGraph): Unit`
- `take(ticketId: String): TicketGraph`
- `compute(node: TicketNode): Double`
- `addNode(node: TicketNode): Unit`
- `addEdge(sourceId: String, targetId: String, type: RelationshipType, desc: String): Unit`
- `enqueue(item: BfsQueueItem): Unit`
- `updateDataSize(content: StructuredTicketContent): Unit`
- `incrementPermissionDenied(): Unit`
- `addSkipped(ticketId: String): Unit`
- `isVisited(ticketId: String): Boolean`
- `visitedIds(): Set`
- `hasWork(): Boolean`
- `isMaxTicketsReached(): Boolean`
- `isEarlyTermination(): Boolean`
- `dequeueCurrentLevel(): List`
- `nodeCount(): Int`
- `skippedCount(): Int`
- `skippedIds(): List`
- `permissionDeniedCount(): Int`
- `maxDepthReached(): Int`
- `dataSize(): Long`
- `totalDiscovered(): Int`
- `nodes(): Map`
- `edges(): List`
- `startHeartbeat(fromPercent: Int, maxPercent: Int, intervalMs: Long): Unit`
- `stopHeartbeat(): Unit`
- `parseResponse(docType: String, response: String): String`
- `logPromptToFile(jobId: String, ticketId: String, docType: String, prompt: String): Unit`
- `executeJob(jobId: String): Unit`
- `getLabel(phase: String): String`
- `setup(): Unit`
- `buildTestGraph(rootId: String, updatedAt: String): TicketGraph`
- `arbKBRecord(): Arb`
- `arbFullComment(isBot: Boolean = false): Arb`
- `arbCommentList(size: IntRange = 0..30): Arb`
- `arbAttachmentChunkInfo(): Arb`
- `arbTicketDates(): Arb`
- `arbTicketGraph(): Arb`
- `buildKBRecord(ticketId: String): Unit`
- `arbTicketId(): Arb`
- `arbDocType(): Arb`
- `arbAiProvider(): Arb`
- `arbApprovalStatus(): Arb`
- `arbNonDraftStatus(): Arb`
- `arbTimestamp(): Arb`
- `arbMarkdownContent(): Arb`
- `arbGeneratedDocument(): Arb`
- `arbDraftDocument(): Arb`
- `arbNonDraftDocument(): Arb`
- `setup(): Unit`
- `createInMemoryJobRepo(): InMemoryCollectionJobRepository`
- `arbCollectionJobSpec(): Arb`
- `clear(): Unit`
- `noOpAttachmentPipeline(): AttachmentPipeline`
- `setup(): Unit`
- `buildLargeTicketContent(charCount: Int): StructuredTicketContent`
- `arbLinkedGraph(): Arb`
- `fallbackStubManager(): Unit`
- `fallbackStubProxy(): Unit`
- `saveAndGetId(document: GeneratedDocument): Long`
- `allDocs(): Unit`
- `allJobs(): List`
- `arbJobStatus(): Arb`
- `arbDocumentType(): Arb`
- `arbApprovalStatus(): Arb`
- `arbTicketId(): Arb`
- `arbUserId(): Arb`
- `arbPhase(): Arb`
- `arbGenerationJob(): Arb`
- `arbDocumentMeta(): Arb`
- `validTransitions(): Map`
- `setUp(): Unit`
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
- `main(): Unit`
- `serverModule(config: ServerConfig): Module`
- `tearDown(): Unit`
- `minimal(): ServerConfig`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| — | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
