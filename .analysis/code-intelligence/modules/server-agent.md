# Module Analysis — server-agent

**Last Updated:** 2026-04-30T10:44:26.970Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-agent/
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
└── com.assistant.server.agent.models/     # Domain model
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

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
