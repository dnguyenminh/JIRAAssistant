# Module Analysis — shared

**Last Updated:** 2026-04-30T10:44:26.884Z
**Language:** javascript | **Framework:** —

## Package Structure

```
shared/
├── com.assistant.agent.ba.models/     # Domain model
├── com.assistant.agent.config/     # Configuration
├── com.assistant.agent.engine/     # Application logic
├── com.assistant.agent/     # Application logic
├── com.assistant.agent.home/     # Application logic
├── com.assistant.agent.memory/     # Application logic
├── com.assistant.agent.models/     # Domain model
├── com.assistant.agent.orchestrator/     # Application logic
├── com.assistant.agent.progress/     # Application logic
├── com.assistant.agent.registry/     # Application logic
├── com.assistant.agent.session/     # Application logic
├── com.assistant.agent.streaming/     # Application logic
├── com.assistant.agent.subprocess/     # Application logic
├── com.assistant.agent.tool/     # Application logic
├── com.assistant.ai/     # Application logic
├── com.assistant.ai.deepanalysis/     # Application logic
├── com.assistant.ai.deepanalysis.models/     # Domain model
├── com.assistant.ai.models/     # Domain model
├── com.assistant.auth/     # Security/Authentication
├── com.assistant.chat/     # Application logic
├── com.assistant.config/     # Configuration
├── com.assistant.document/     # Application logic
├── com.assistant.document.models/     # Domain model
├── com.assistant.domain/     # Domain model
├── com.assistant.graph/     # Application logic
├── com.assistant.jira/     # Application logic
├── com.assistant.kb/     # Application logic
├── com.assistant.mcp/     # Application logic
├── com.assistant.mcp.models/     # Domain model
├── com.assistant.rbac/     # Application logic
├── com.assistant.scan/     # Application logic
├── com.assistant.settings/     # Application logic
├── com.assistant.security/     # Security/Authentication
└── com.assistant.serialization/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| BATaskConfig | com.assistant.agent.config | Configuration | data |
| BATaskStatus | com.assistant.agent.ba.models | Application component | public |
| BATaskResult | com.assistant.agent.ba.models | Application component | data |
| ToolCallLogEntry | com.assistant.agent.ba.models | Application component | data |
| MemorySchemaBuilder | com.assistant.agent.ba.models | Application component | public |
| PhasesBuilder | com.assistant.agent.ba.models | Application component | public |
| ToolsBuilder | com.assistant.agent.ba.models | Application component | public |
| LimitsBuilder | com.assistant.agent.ba.models | Application component | public |
| ErrorStrategyBuilder | com.assistant.agent.ba.models | Application component | public |
| AgentConfig | com.assistant.agent.config | Configuration | data |
| InvalidAgentConfigException | com.assistant.agent.config | Error handling | public |
| AgentConfigBuilder | com.assistant.agent.config | Application component | public |
| PhaseDefinition | com.assistant.agent.ba.models | Application component | public |
| PhaseConfig | com.assistant.agent.config | Configuration | data |
| ThinkingLoopResult | com.assistant.agent.ba.models | Application component | data |
| ThinkingLoopEngine | com.assistant.agent.ba.models | Application component | public |
| GenericAgent | com.assistant.agent.ba.models | Application component | public |
| AgentHomeConfig | com.assistant.agent.config | Configuration | data |
| AgentHomeDirectory | com.assistant.agent.ba.models | Application component | public |
| as | com.assistant.agent.ba.models | Application component | public |
| AgentMcpConfig | com.assistant.agent.config | Configuration | data |
| RuleDefinition | com.assistant.agent.ba.models | Application component | data |
| SkillDefinition | com.assistant.agent.ba.models | Application component | data |
| WorkflowDefinition | com.assistant.agent.ba.models | Application component | data |
| SlotType | com.assistant.agent.ba.models | Application component | public |
| SlotSchema | com.assistant.agent.ba.models | Application component | data |
| MemoryEntry | com.assistant.agent.ba.models | Application component | data |
| SlotFullResult | com.assistant.agent.ba.models | Application component | data |
| StructuredMemory | com.assistant.agent.ba.models | Application component | public |
| StructuredMemorySurrogate | com.assistant.agent.ba.models | Application component | data |
| StructuredMemorySerializer | com.assistant.agent.ba.models | Application component | public |
| AgentInput | com.assistant.agent.ba.models | Application component | data |
| AgentMetrics | com.assistant.agent.ba.models | Application component | data |
| AgentOutput | com.assistant.agent.ba.models | Application component | data |
| AgentStatus | com.assistant.agent.ba.models | Application component | public |
| AgentState | com.assistant.agent.ba.models | Application component | data |
| AgentStateStatus | com.assistant.agent.ba.models | Application component | public |
| ErrorStrategy | com.assistant.agent.ba.models | Application component | public |
| RetryConfig | com.assistant.agent.config | Configuration | data |
| ErrorClassification | com.assistant.agent.ba.models | Application component | public |
| ToolResult | com.assistant.agent.ba.models | Application component | data |
| ToolCall | com.assistant.agent.ba.models | Application component | data |
| ToolCallRecord | com.assistant.agent.ba.models | Application component | data |
| ToolDescriptor | com.assistant.agent.ba.models | Application component | data |
| ToolDescriptorWithSource | com.assistant.agent.ba.models | Application component | data |
| OrchestratorBackend | com.assistant.agent.ba.models | Application component | public |
| ProgressReporter | com.assistant.agent.ba.models | Application component | public |
| AgentNotFoundException | com.assistant.agent.ba.models | Error handling | public |
| AgentRegistry | com.assistant.agent.ba.models | Application component | public |
| CommandHistoryEntry | com.assistant.agent.ba.models | Application component | data |
| SessionContext | com.assistant.agent.ba.models | Application component | data |
| SessionManager | com.assistant.agent.ba.models | Application component | public |
| for | com.assistant.agent.ba.models | Application component | public |
| StreamingCallback | com.assistant.agent.ba.models | Application component | public |
| StreamingConfig | com.assistant.agent.config | Configuration | data |
| SubprocessConfig | com.assistant.agent.config | Configuration | data |
| to | com.assistant.agent.ba.models | Application component | public |
| SubprocessManager | com.assistant.agent.ba.models | Application component | public |
| ToolCallRequest | com.assistant.agent.ba.models | Data transfer object | data |
| ToolCallResponse | com.assistant.agent.ba.models | Data transfer object | data |
| SubprocessMessage | com.assistant.agent.ba.models | Application component | data |
| SubprocessProxy | com.assistant.agent.ba.models | Application component | public |
| AgentTool | com.assistant.agent.ba.models | Application component | public |
| ToolRegistry | com.assistant.agent.ba.models | Application component | public |
| for | com.assistant.agent.ba.models | Application component | public |
| AIAgent | com.assistant.agent.ba.models | Application component | public |
| AIContext | com.assistant.agent.ba.models | Application component | data |
| JiraTicketSummary | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| AIResult | com.assistant.agent.ba.models | Application component | sealed |
| Success | com.assistant.agent.ba.models | Application component | data |
| Failure | com.assistant.agent.ba.models | Application component | data |
| to | com.assistant.agent.ba.models | Application component | public |
| AIAgentFactory | com.assistant.agent.ba.models | Object creation | public |
| AIOrchestrator | com.assistant.agent.ba.models | Application component | public |
| AnalysisResult | com.assistant.agent.ba.models | Application component | data |
| AnalysisSource | com.assistant.agent.ba.models | Application component | public |
| RequirementSummary | com.assistant.agent.ba.models | Application component | data |
| AffectedModule | com.assistant.agent.ba.models | Application component | data |
| EvolutionEntry | com.assistant.agent.ba.models | Application component | data |
| ComplexityAssessment | com.assistant.agent.ba.models | Application component | data |
| KBReference | com.assistant.agent.ba.models | Application component | data |
| ProviderStatus | com.assistant.agent.ba.models | Application component | data |
| ConnectionStatus | com.assistant.agent.ba.models | Application component | public |
| ProviderTestResult | com.assistant.agent.ba.models | Application component | data |
| ProviderConfig | com.assistant.agent.config | Configuration | data |
| ProviderType | com.assistant.agent.ba.models | Application component | public |
| AIOrchestratorImpl | com.assistant.agent.ba.models | Application component | public |
| FailoverEvent | com.assistant.agent.ba.models | Application component | data |
| MapReduceCheckResult | com.assistant.agent.ba.models | Application component | sealed |
| Analyzed | com.assistant.agent.ba.models | Application component | data |
| FallThrough | com.assistant.agent.ba.models | Application component | data |
| SprintVelocity | com.assistant.agent.ba.models | Application component | data |
| BottleneckAlert | com.assistant.agent.ba.models | Application component | data |
| AnalysisPhase | com.assistant.agent.ba.models | Application component | public |
| AnalysisStatus | com.assistant.agent.ba.models | Application component | data |
| ProjectAnalysisResponse | com.assistant.agent.ba.models | Data transfer object | data |
| BatchPromptBuilder | com.assistant.agent.ba.models | Application component | public |
| per | com.assistant.agent.ba.models | Application component | public |
| BatchResponseParser | com.assistant.agent.ba.models | Application component | public |
| CascadeState | com.assistant.agent.ba.models | Application component | internal |
| CascadingAnalysisEngine | com.assistant.agent.ba.models | Application component | public |
| CascadingAnalysisEngineImpl | com.assistant.agent.ba.models | Application component | public |
| DeepAnalysisPromptBuilder | com.assistant.agent.ba.models | Application component | public |
| DeepAnalysisPromptBuilderImpl | com.assistant.agent.ba.models | Application component | public |
| DeepAnalysisResponseParser | com.assistant.agent.ba.models | Application component | public |
| DeepAnalysisParseException | com.assistant.agent.ba.models | Error handling | public |
| DeepAnalysisResponseParserImpl | com.assistant.agent.ba.models | Application component | public |
| JiraContentExtractor | com.assistant.agent.ba.models | Application component | public |
| JiraContentExtractorImpl | com.assistant.agent.ba.models | Application component | public |
| JiraFieldMappers | com.assistant.agent.ba.models | Application component | public |
| AcceptanceCriterion | com.assistant.agent.ba.models | Application component | data |
| ExtractionConfidence | com.assistant.agent.ba.models | Application component | public |
| AnalysisMetadata | com.assistant.agent.ba.models | Application component | data |
| CascadeLogStatus | com.assistant.agent.ba.models | Application component | public |
| CascadeStatus | com.assistant.agent.ba.models | Application component | public |
| CascadeLogEntry | com.assistant.agent.ba.models | Application component | data |
| CascadeResult | com.assistant.agent.ba.models | Application component | data |
| ClassifiedContent | com.assistant.agent.ba.models | Application component | data |
| DependencyItem | com.assistant.agent.ba.models | Application component | data |
| DependencyInfo | com.assistant.agent.ba.models | Application component | data |
| DiagramData | com.assistant.agent.ba.models | Application component | data |
| DrawioMetadata | com.assistant.agent.ba.models | Application component | data |
| DrawioNode | com.assistant.agent.ba.models | Application component | data |
| DrawioConnection | com.assistant.agent.ba.models | Application component | data |
| MapReduceInfo | com.assistant.agent.ba.models | Application component | data |
| StructuredTicketContent | com.assistant.agent.ba.models | Application component | data |
| ApiSpecification | com.assistant.agent.ba.models | Application component | data |
| DatabaseChange | com.assistant.agent.ba.models | Application component | data |
| ExternalIntegration | com.assistant.agent.ba.models | Application component | data |
| TechnicalDetails | com.assistant.agent.ba.models | Application component | data |
| SubTaskInfo | com.assistant.agent.ba.models | Application component | data |
| IssueLinkInfo | com.assistant.agent.ba.models | Application component | data |
| AttachmentInfo | com.assistant.agent.ba.models | Application component | data |
| CommentInfo | com.assistant.agent.ba.models | Application component | data |
| ChangelogEntry | com.assistant.agent.ba.models | Application component | data |
| LinkedTicketContent | com.assistant.agent.ba.models | Application component | data |
| RelatedTicketCollector | com.assistant.agent.ba.models | Application component | public |
| AIResponseRoot | com.assistant.agent.ba.models | Application component | data |
| AIDiagram | com.assistant.agent.ba.models | Application component | data |
| AIDrawioMetadata | com.assistant.agent.ba.models | Application component | data |
| AIDrawioNode | com.assistant.agent.ba.models | Application component | data |
| AIDrawioConnection | com.assistant.agent.ba.models | Application component | data |
| AIRequirementSummary | com.assistant.agent.ba.models | Application component | data |
| AIAffectedModule | com.assistant.agent.ba.models | Application component | data |
| AIEvolutionEntry | com.assistant.agent.ba.models | Application component | data |
| AIComplexity | com.assistant.agent.ba.models | Application component | data |
| AIKBReference | com.assistant.agent.ba.models | Application component | data |
| AITechnicalDetails | com.assistant.agent.ba.models | Application component | data |
| AIApiSpec | com.assistant.agent.ba.models | Test class | data |
| AIDbChange | com.assistant.agent.ba.models | Application component | data |
| AIExternalIntegration | com.assistant.agent.ba.models | Application component | data |
| AIAcceptanceCriterion | com.assistant.agent.ba.models | Application component | data |
| AIDependencies | com.assistant.agent.ba.models | Application component | data |
| AIDependencyItem | com.assistant.agent.ba.models | Application component | data |
| AIAnalysisMetadata | com.assistant.agent.ba.models | Application component | data |
| ResponseParserHelpers | com.assistant.agent.ba.models | Application component | internal |
| ResponseToResultMapper | com.assistant.agent.ba.models | Data mapping | internal |
| ScrumPointsValidator | com.assistant.agent.ba.models | Input validation | internal |
| SectionClassifier | com.assistant.agent.ba.models | Application component | public |
| SectionClassifierImpl | com.assistant.agent.ba.models | Application component | public |
| SectionExtractors | com.assistant.agent.ba.models | Application component | internal |
| SectionPatterns | com.assistant.agent.ba.models | Application component | internal |
| GeminiAgent | com.assistant.agent.ba.models | Application component | public |
| GeminiRequest | com.assistant.agent.ba.models | Data transfer object | data |
| Content | com.assistant.agent.ba.models | Application component | data |
| Part | com.assistant.agent.ba.models | Application component | data |
| LegacyResponseCleaner | com.assistant.agent.ba.models | Application component | internal |
| LegacyPromptBuilder | com.assistant.agent.ba.models | Application component | internal |
| with | com.assistant.agent.ba.models | Application component | public |
| LegacyResponseMapper | com.assistant.agent.ba.models | Data mapping | internal |
| MapReduceAnalyzer | com.assistant.agent.ba.models | Application component | public |
| OllamaChatRequest | com.assistant.agent.ba.models | Data transfer object | data |
| OllamaChatMessage | com.assistant.agent.ba.models | Application component | data |
| OllamaChatToolCall | com.assistant.agent.ba.models | Application component | data |
| OllamaChatFunctionCall | com.assistant.agent.ba.models | Application component | data |
| OllamaChatResponse | com.assistant.agent.ba.models | Data transfer object | data |
| OllamaChatToolDef | com.assistant.agent.ba.models | Application component | data |
| OllamaChatFunctionDef | com.assistant.agent.ba.models | Application component | data |
| OllamaStreamLine | com.assistant.agent.ba.models | Application component | data |
| OllamaAgent | com.assistant.agent.ba.models | Application component | public |
| OllamaRequest | com.assistant.agent.ba.models | Data transfer object | data |
| OllamaResponse | com.assistant.agent.ba.models | Data transfer object | data |
| OllamaTagsResponse | com.assistant.agent.ba.models | Data transfer object | data |
| OllamaModel | com.assistant.agent.ba.models | Domain model | data |
| OllamaChatAgent | com.assistant.agent.ba.models | Application component | public |
| OllamaChatResult | com.assistant.agent.ba.models | Application component | sealed |
| TextResponse | com.assistant.agent.ba.models | Data transfer object | data |
| ToolCalls | com.assistant.agent.ba.models | Application component | data |
| Error | com.assistant.agent.ba.models | Error handling | data |
| StreamInterruptedException | com.assistant.agent.ba.models | Error handling | public |
| OllamaStreamReader | com.assistant.agent.ba.models | Application component | internal |
| ProviderTester | com.assistant.agent.ba.models | Application component | internal |
| UserRole | com.assistant.agent.ba.models | Application component | public |
| AuthenticatedUser | com.assistant.agent.ba.models | Application component | data |
| AuthResult | com.assistant.agent.ba.models | Application component | sealed |
| Success | com.assistant.agent.ba.models | Application component | data |
| Failure | com.assistant.agent.ba.models | Application component | data |
| AuthService | com.assistant.agent.ba.models | Business logic | public |
| ChatConversation | com.assistant.agent.ba.models | Application component | data |
| ChatConversationRepository | com.assistant.agent.ba.models | Data access | public |
| ChatRequest | com.assistant.agent.ba.models | Data transfer object | data |
| ChatResponse | com.assistant.agent.ba.models | Data transfer object | data |
| ChatAttachment | com.assistant.agent.ba.models | Application component | data |
| ChatContext | com.assistant.agent.ba.models | Application component | data |
| TicketChatContext | com.assistant.agent.ba.models | Application component | data |
| GraphChatContext | com.assistant.agent.ba.models | Application component | data |
| ChatAction | com.assistant.agent.ba.models | Application component | data |
| ChatReference | com.assistant.agent.ba.models | Application component | data |
| ChatActionRequest | com.assistant.agent.ba.models | Data transfer object | data |
| ChatActionResponse | com.assistant.agent.ba.models | Data transfer object | data |
| ChatHistoryResponse | com.assistant.agent.ba.models | Data transfer object | data |
| ChatMessage | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| ChatRepository | com.assistant.agent.ba.models | Data access | public |
| for | com.assistant.agent.ba.models | Application component | public |
| ChatService | com.assistant.agent.ba.models | Business logic | public |
| ToolPermissionsResponse | com.assistant.agent.ba.models | Data transfer object | data |
| ToolPermissionsUpdateRequest | com.assistant.agent.ba.models | Data transfer object | data |
| ToolPermissionsBulkRequest | com.assistant.agent.ba.models | Data transfer object | data |
| SkillEntry | com.assistant.agent.ba.models | Application component | data |
| WorkflowEntry | com.assistant.agent.ba.models | Application component | data |
| InstructionEntry | com.assistant.agent.ba.models | Application component | data |
| RuleEntry | com.assistant.agent.ba.models | Application component | data |
| UserAIConfig | com.assistant.agent.config | Configuration | data |
| UserAIConfigRepository | com.assistant.agent.config | Data access | public |
| UserToolPermissionRepository | com.assistant.agent.ba.models | Data access | public |
| JsonConfig | com.assistant.agent.config | Configuration | public |
| BrdPromptBuilder | com.assistant.agent.ba.models | Application component | public |
| CommentData | com.assistant.agent.ba.models | Application component | data |
| BrdResponseParser | com.assistant.agent.ba.models | Application component | public |
| DocumentAggregator | com.assistant.agent.ba.models | Application component | public |
| DocumentSectionValidator | com.assistant.agent.ba.models | Input validation | public |
| ValidationResult | com.assistant.agent.ba.models | Application component | data |
| DocumentValidation | com.assistant.agent.ba.models | Application component | data |
| FsdPromptBuilder | com.assistant.agent.ba.models | Application component | public |
| FsdResponseParser | com.assistant.agent.ba.models | Application component | public |
| ApprovalStatus | com.assistant.agent.ba.models | Application component | public |
| DocumentSection | com.assistant.agent.ba.models | Application component | data |
| DocumentStatus | com.assistant.agent.ba.models | Application component | data |
| DocumentType | com.assistant.agent.ba.models | Application component | public |
| GeneratedDocument | com.assistant.agent.ba.models | Application component | data |
| to | com.assistant.agent.ba.models | Application component | public |
| behavior | com.assistant.agent.ba.models | Application component | data |
| GenerationContext | com.assistant.agent.ba.models | Application component | open |
| copy | com.assistant.agent.ba.models | Application component | data |
| AttachmentChunkInfo | com.assistant.agent.ba.models | Application component | data |
| SprintMetadata | com.assistant.agent.ba.models | Application component | data |
| GenerationJob | com.assistant.agent.ba.models | Application component | data |
| JobChainResponse | com.assistant.agent.ba.models | Data transfer object | data |
| JobStatus | com.assistant.agent.ba.models | Application component | public |
| SlideGenerator | com.assistant.agent.ba.models | Application component | public |
| ScrumEstimation | com.assistant.agent.ba.models | Application component | data |
| SimilarTicket | com.assistant.agent.ba.models | Application component | data |
| NewRequirement | com.assistant.agent.ba.models | Application component | data |
| FeatureNetworkMapper | com.assistant.agent.ba.models | Data mapping | public |
| NetworkGraph | com.assistant.agent.ba.models | Application component | data |
| TicketNode | com.assistant.agent.ba.models | Application component | data |
| TicketEdge | com.assistant.agent.ba.models | Application component | data |
| ScrumEstimator | com.assistant.agent.ba.models | Application component | public |
| LayoutState | com.assistant.agent.ba.models | Application component | private |
| ForceDirectedGraphEngine | com.assistant.agent.ba.models | Application component | public |
| GraphDiffLogic | com.assistant.agent.ba.models | Application component | public |
| DiffResult | com.assistant.agent.ba.models | Application component | data |
| Position | com.assistant.agent.ba.models | Application component | data |
| Bounds | com.assistant.agent.ba.models | Application component | data |
| GraphLayout | com.assistant.agent.ba.models | Application component | data |
| Cluster | com.assistant.agent.ba.models | Application component | data |
| GraphEngine | com.assistant.agent.ba.models | Application component | public |
| GraphPollingStateMachine | com.assistant.agent.ba.models | Application component | public |
| LinkedTicketDTO | com.assistant.agent.ba.models | Data transfer object | data |
| SubTaskDTO | com.assistant.agent.ba.models | Data transfer object | data |
| JiraChangelog | com.assistant.agent.ba.models | Application component | data |
| JiraChangeHistory | com.assistant.agent.ba.models | Application component | data |
| JiraChangeItem | com.assistant.agent.ba.models | Application component | data |
| JiraProject | com.assistant.agent.ba.models | Application component | data |
| JiraIssue | com.assistant.agent.ba.models | Application component | data |
| JiraIssueType | com.assistant.agent.ba.models | Application component | data |
| JiraIssueFields | com.assistant.agent.ba.models | Application component | data |
| JiraParent | com.assistant.agent.ba.models | Application component | data |
| JiraSubtask | com.assistant.agent.ba.models | Application component | data |
| JiraSubtaskFields | com.assistant.agent.ba.models | Application component | data |
| JiraIssueLink | com.assistant.agent.ba.models | Application component | data |
| JiraIssueLinkType | com.assistant.agent.ba.models | Application component | data |
| JiraLinkedIssue | com.assistant.agent.ba.models | Application component | data |
| JiraLinkedIssueFields | com.assistant.agent.ba.models | Application component | data |
| JiraAttachment | com.assistant.agent.ba.models | Application component | data |
| JiraStatus | com.assistant.agent.ba.models | Application component | data |
| JiraResolution | com.assistant.agent.ba.models | Application component | data |
| JiraClient | com.assistant.agent.ba.models | External service client | public |
| JiraCredentials | com.assistant.agent.ba.models | Application component | data |
| JiraPriority | com.assistant.agent.ba.models | Application component | data |
| JiraUser | com.assistant.agent.ba.models | Application component | data |
| JiraComponent | com.assistant.agent.ba.models | Application component | data |
| JiraCommentWrapper | com.assistant.agent.ba.models | Application component | data |
| JiraCommentPageResponse | com.assistant.agent.ba.models | Data transfer object | data |
| JiraComment | com.assistant.agent.ba.models | Application component | data |
| JiraRestClient | com.assistant.agent.ba.models | External service client | public |
| NoOpJiraClient | com.assistant.agent.ba.models | External service client | public |
| KBDeepAnalysisData | com.assistant.agent.ba.models | Application component | data |
| KBRecord | com.assistant.agent.ba.models | Application component | data |
| EvolutionEntry | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| KBRepository | com.assistant.agent.ba.models | Data access | public |
| McpProcessManager | com.assistant.agent.ba.models | Application component | public |
| McpProtocolClient | com.assistant.agent.ba.models | External service client | public |
| McpServerConfig | com.assistant.agent.config | Configuration | data |
| McpConfigExport | com.assistant.agent.config | Application component | data |
| McpServerEntry | com.assistant.agent.ba.models | Application component | data |
| McpServerRepository | com.assistant.agent.ba.models | Data access | public |
| ToolCategory | com.assistant.agent.ba.models | Application component | public |
| InternalToolDefinition | com.assistant.agent.ba.models | Application component | data |
| JsonRpcRequest | com.assistant.agent.ba.models | Data transfer object | data |
| JsonRpcResponse | com.assistant.agent.ba.models | Data transfer object | data |
| JsonRpcError | com.assistant.agent.ba.models | Error handling | data |
| McpInitializeResult | com.assistant.agent.ba.models | Application component | data |
| McpServerInfoDto | com.assistant.agent.ba.models | Data transfer object | data |
| McpError | com.assistant.agent.ba.models | Error handling | public |
| McpHealthResponse | com.assistant.agent.ba.models | Data transfer object | data |
| McpServerHealth | com.assistant.agent.ba.models | Application component | data |
| McpProcessStatus | com.assistant.agent.ba.models | Application component | data |
| McpServerState | com.assistant.agent.ba.models | Application component | public |
| McpToolCallRequest | com.assistant.agent.ba.models | Data transfer object | data |
| McpToolCallResponse | com.assistant.agent.ba.models | Data transfer object | data |
| McpContent | com.assistant.agent.ba.models | Application component | data |
| McpToolInfo | com.assistant.agent.ba.models | Application component | data |
| McpAggregatedTool | com.assistant.agent.ba.models | Application component | data |
| AuditLogStore | com.assistant.agent.ba.models | Application component | public |
| InMemoryAuditLogStore | com.assistant.agent.ba.models | Application component | public |
| InMemoryUserStore | com.assistant.agent.ba.models | Application component | public |
| PermissionMatrix | com.assistant.agent.ba.models | Application component | public |
| for | com.assistant.agent.ba.models | Application component | public |
| RBACEngine | com.assistant.agent.ba.models | Application component | public |
| RBACEngineImpl | com.assistant.agent.ba.models | Application component | public |
| Permission | com.assistant.agent.ba.models | Application component | public |
| User | com.assistant.agent.ba.models | Application component | data |
| AuditLogEntry | com.assistant.agent.ba.models | Application component | data |
| RBACResult | com.assistant.agent.ba.models | Application component | sealed |
| Success | com.assistant.agent.ba.models | Application component | data |
| Failure | com.assistant.agent.ba.models | Application component | data |
| UserStore | com.assistant.agent.ba.models | Application component | public |
| ScanConflictException | com.assistant.agent.ba.models | Error handling | public |
| BatchScanEngine | com.assistant.agent.ba.models | Application component | public |
| IncrementalGraphBuilder | com.assistant.agent.ba.models | Application component | public |
| ScanLogStatus | com.assistant.agent.ba.models | Application component | public |
| ScanLogEntry | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| ScanLogRepository | com.assistant.agent.ba.models | Data access | public |
| ScanState | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| ScanStateRepository | com.assistant.agent.ba.models | Data access | public |
| ScanStatus | com.assistant.agent.ba.models | Application component | public |
| TicketAnalysisState | com.assistant.agent.ba.models | Application component | public |
| TicketAnalysisStatus | com.assistant.agent.ba.models | Application component | data |
| for | com.assistant.agent.ba.models | Application component | public |
| SettingsRepository | com.assistant.agent.ba.models | Data access | public |
| AppSettings | com.assistant.agent.ba.models | Application component | data |
| AppSettingsResponse | com.assistant.agent.ba.models | Data transfer object | data |
| BrdPromptBuilderTest | com.assistant.agent.ba.models | Test class | public |
| BrdResponseParserTest | com.assistant.agent.ba.models | Test class | public |
| DocumentSectionValidatorTest | com.assistant.agent.ba.models | Test class | public |
| ExportFilenameTest | com.assistant.agent.ba.models | Test class | public |
| FsdPromptBuilderTest | com.assistant.agent.ba.models | Test class | public |
| FsdResponseParserTest | com.assistant.agent.ba.models | Test class | public |
| SlideGeneratorTest | com.assistant.agent.ba.models | Test class | public |
| ScrumEstimatorTest | com.assistant.agent.ba.models | Test class | public |
| MockAIAgent | com.assistant.agent.ba.models | Application component | private |
| ChatConversationRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| ChatRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| UserAIConfigRepositoryImpl | com.assistant.agent.config | Application component | public |
| UserToolPermissionRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| JiraCredentialState | com.assistant.agent.ba.models | Application component | public |
| JiraCredentialsService | com.assistant.agent.ba.models | Business logic | public |
| KBRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| ProviderConfigRepository | com.assistant.agent.config | Data access | open |
| McpServerRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| FileBasedAuditLogStore | com.assistant.agent.ba.models | Application component | public |
| ScanLogRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| ScanStateRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| CryptoUtils | com.assistant.agent.ba.models | Utility functions | public |
| SettingsRepositoryImpl | com.assistant.agent.ba.models | Application component | public |
| BATaskConfigPropertyTest | com.assistant.agent.config | Test class | public |
| BATaskResultPropertyTest | com.assistant.agent.ba.models | Test class | public |
| AIOrchestratorDeepAnalysisTest | com.assistant.agent.ba.models | Test class | public |
| AIOrchestratorStrategyTest | com.assistant.agent.ba.models | Test class | public |
| FakeKBRepository | com.assistant.agent.ba.models | Data access | public |
| FakeAIAgent | com.assistant.agent.ba.models | Application component | public |
| FakeJiraContentExtractor | com.assistant.agent.ba.models | Application component | public |
| FakeDeepPromptBuilder | com.assistant.agent.ba.models | Application component | public |
| FakeDeepResponseParser | com.assistant.agent.ba.models | Application component | public |
| FakeCascadeOrchestrator | com.assistant.agent.ba.models | Application component | public |
| FakeCascadeKBRepo | com.assistant.agent.ba.models | Application component | public |
| FakeCascadeExtractor | com.assistant.agent.ba.models | Application component | public |
| CascadingAnalysisEngineTest | com.assistant.agent.ba.models | Test class | public |
| CascadingAnalysisLimitAndLogTest | com.assistant.agent.ba.models | Test class | public |
| DeepAnalysisPromptBuilderTest | com.assistant.agent.ba.models | Test class | public |
| DeepAnalysisResponseParserTest | com.assistant.agent.ba.models | Test class | public |
| returns | com.assistant.agent.ba.models | Application component | public |
| DiagramDataPropertyTest | com.assistant.agent.ba.models | Test class | public |
| JiraContentExtractorTest | com.assistant.agent.ba.models | Test class | public |
| JiraFieldMappersTest | com.assistant.agent.ba.models | Test class | public |
| FakeJiraClient | com.assistant.agent.ba.models | External service client | public |
| ParserMetadataPropertyTest | com.assistant.agent.ba.models | Test class | public |
| RelatedTicketCollectorTest | com.assistant.agent.ba.models | Test class | public |
| ResponseParserHelpersTest | com.assistant.agent.ba.models | Test class | public |
| ScrumPointsValidatorTest | com.assistant.agent.ba.models | Test class | public |
| SectionClassifierBasicTest | com.assistant.agent.ba.models | Test class | public |
| SectionClassifierExtractionTest | com.assistant.agent.ba.models | Test class | public |
| DuplicateExtractionBugConditionTest | com.assistant.agent.ba.models | Test class | public |
| DuplicateExtractionPreservationTest | com.assistant.agent.ba.models | Test class | public |
| SpyJiraContentExtractor | com.assistant.agent.ba.models | Application component | public |
| FakeMapReduceAnalyzer | com.assistant.agent.ba.models | Application component | public |
| NdjsonAccumulationPropertyTest | com.assistant.agent.ba.models | Test class | public |
| OllamaAgentStreamingTest | com.assistant.agent.ba.models | Test class | public |
| StreamErrorPropertyTest | com.assistant.agent.ba.models | Test class | public |
| StreamingProgressPropertyTest | com.assistant.agent.ba.models | Test class | public |
| AggregationCapacityPropertyTest | com.assistant.agent.ba.models | Test class | public |
| BrdPromptFallbackPropertyTest | com.assistant.agent.ba.models | Test class | public |
| DocumentStoragePropertyTest | com.assistant.agent.ba.models | Test class | public |
| InMemoryDocumentStore | com.assistant.agent.ba.models | Application component | private |
| HeadingParsingPreservationTest | com.assistant.agent.ba.models | Test class | public |
| HeadingVariationBugConditionTest | com.assistant.agent.ba.models | Test class | public |
| MarkdownRoundTripPropertyTest | com.assistant.agent.ba.models | Test class | public |
| GenerationContext | com.assistant.agent.ba.models | Application component | open |
| GenerationContextPropertyTest | com.assistant.agent.ba.models | Test class | public |
| that | com.assistant.agent.ba.models | Application component | public |
| must | com.assistant.agent.ba.models | Application component | public |
| ParserSectionPropertyTest | com.assistant.agent.ba.models | Test class | public |
| PromptCompletenessPropertyTest | com.assistant.agent.ba.models | Test class | public |
| SlideAndSerializationPropertyTest | com.assistant.agent.ba.models | Test class | public |
| FeatureNetworkMapperPropertyTest | com.assistant.agent.ba.models | Test class | public |
| FeatureNetworkMapperTest | com.assistant.agent.ba.models | Test class | public |
| ScrumEstimatorPropertyTest | com.assistant.agent.ba.models | Test class | public |
| FadeInRenderingTest | com.assistant.agent.ba.models | Test class | public |
| applied | com.assistant.agent.ba.models | Application component | public |
| Graph3DPropertyTest | com.assistant.agent.ba.models | Test class | public |
| GraphDiffPropertyTest | com.assistant.agent.ba.models | Test class | public |
| GraphEnginePropertyTest | com.assistant.agent.ba.models | Test class | public |
| GraphPollingBehaviorTest | com.assistant.agent.ba.models | Test class | public |
| JiraCredentialsServiceTest | com.assistant.agent.ba.models | Test class | public |
| JiraCredentialStateTest | com.assistant.agent.ba.models | Test class | public |
| KBDeepAnalysisDrawioPropertyTest | com.assistant.agent.ba.models | Test class | public |
| FileBasedAuditLogStoreTest | com.assistant.agent.ba.models | Test class | public |
| UserMgmtBugConditionExplorationTest | com.assistant.agent.ba.models | Test class | public |
| UserDtoSurrogate | com.assistant.agent.ba.models | Application component | data |
| UserInfoSurrogate | com.assistant.agent.ba.models | Application component | data |
| FrontendAuditEntrySurrogate | com.assistant.agent.ba.models | Application component | data |
| UserMgmtPreservationPropertyTest | com.assistant.agent.ba.models | Test class | public |
| BatchContentFormatterTest | com.assistant.agent.ba.models | Test class | public |
| BatchProcessorContentFetchTest | com.assistant.agent.ba.models | Test class | public |
| BatchProcessorPlaceholderTest | com.assistant.agent.ba.models | Test class | public |
| FakeContentExtractor | com.assistant.agent.ba.models | Application component | public |
| ThrowingContentExtractor | com.assistant.agent.ba.models | Application component | public |
| CapturingAIOrchestrator | com.assistant.agent.ba.models | Application component | public |
| StubJiraForBatch | com.assistant.agent.ba.models | Application component | public |
| TrackingKBRepoForBatch | com.assistant.agent.ba.models | Application component | public |
| NoOpKBRepo | com.assistant.agent.ba.models | Application component | public |
| FixedSettingsRepo | com.assistant.agent.ba.models | Application component | public |
| NoOpScanStateRepo | com.assistant.agent.ba.models | Application component | public |
| NoOpScanLogRepo | com.assistant.agent.ba.models | Application component | public |
| StubAIAgentForBatch | com.assistant.agent.ba.models | Application component | public |
| CompleteScanBugConditionPropertyTest | com.assistant.agent.ba.models | Test class | public |
| TrackingKBRepository | com.assistant.agent.ba.models | Data access | public |
| StubAIOrchestratorForScan | com.assistant.agent.ba.models | Application component | public |
| StubJiraClientForScan | com.assistant.agent.ba.models | Application component | public |
| StubAIAgentForScan | com.assistant.agent.ba.models | Application component | public |
| InMemoryScanStateRepo | com.assistant.agent.ba.models | Application component | public |
| InMemoryScanLogRepo | com.assistant.agent.ba.models | Application component | public |
| CompleteScanFinalGraphPropertyTest | com.assistant.agent.ba.models | Test class | public |
| TrackingKBRepoForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| StubAIForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| StubJiraForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| StubAIAgentForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| InMemoryScanStateForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| NoOpScanLogForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| NoOpSettingsForFinalGraph | com.assistant.agent.ba.models | Application component | private |
| CompleteScanPreservationPropertyTest | com.assistant.agent.ba.models | Test class | public |
| TrackingKBRepoForPreservation | com.assistant.agent.ba.models | Application component | public |
| StubAIOrchestratorForPreservation | com.assistant.agent.ba.models | Application component | public |
| SlowJiraClientForPreservation | com.assistant.agent.ba.models | Application component | public |
| StubJiraClientForPreservation | com.assistant.agent.ba.models | Application component | public |
| EmptyJiraClientForPreservation | com.assistant.agent.ba.models | Application component | public |
| StubAIAgentForPreservation | com.assistant.agent.ba.models | Application component | public |
| InMemoryScanStateRepoForPreservation | com.assistant.agent.ba.models | Application component | public |
| InMemoryScanLogRepoForPreservation | com.assistant.agent.ba.models | Application component | public |
| IncrementalBuildErrorResiliencePropertyTest | com.assistant.agent.ba.models | Test class | public |
| FailingSaveGraphKBRepo | com.assistant.agent.ba.models | Application component | public |
| StubAIForResilience | com.assistant.agent.ba.models | Application component | public |
| StubJiraForResilience | com.assistant.agent.ba.models | Application component | public |
| StubAIAgentForResilience | com.assistant.agent.ba.models | Application component | public |
| InMemoryScanStateForResilience | com.assistant.agent.ba.models | Application component | public |
| NoOpScanLogForResilience | com.assistant.agent.ba.models | Application component | public |
| NoOpSettingsForResilience | com.assistant.agent.ba.models | Application component | public |
| IncrementalGraphBuildPropertyTest | com.assistant.agent.ba.models | Test class | public |
| TrackingKBRepoForIncremental | com.assistant.agent.ba.models | Application component | public |
| StubAIOrchestratorForIncremental | com.assistant.agent.ba.models | Application component | public |
| StubJiraForIncremental | com.assistant.agent.ba.models | Application component | public |
| StubAIAgentForIncremental | com.assistant.agent.ba.models | Application component | public |
| NoOpScanStateRepoForIncremental | com.assistant.agent.ba.models | Application component | public |
| NoOpScanLogRepoForIncremental | com.assistant.agent.ba.models | Application component | public |
| IncrementalGraphDebouncePropertyTest | com.assistant.agent.ba.models | Test class | public |
| DebounceKBRepo | com.assistant.agent.ba.models | Application component | public |
| DebounceAIOrchestrator | com.assistant.agent.ba.models | Application component | public |
| DebounceJiraClient | com.assistant.agent.ba.models | External service client | public |
| DebounceAIAgent | com.assistant.agent.ba.models | Application component | public |
| DebounceNoOpScanStateRepo | com.assistant.agent.ba.models | Application component | public |
| DebounceNoOpScanLogRepo | com.assistant.agent.ba.models | Application component | public |
| ScanProgressBugConditionPropertyTest | com.assistant.agent.ba.models | Test class | public |
| ScanProgressPreservationPropertyTest | com.assistant.agent.ba.models | Test class | public |
| KBDeepAnalysisSerializationTest | com.assistant.agent.ba.models | Test class | public |
| serialization | com.assistant.agent.ba.models | Application component | public |
| RoundTripTest | com.assistant.agent.ba.models | Test class | public |

## Public API Surface

- `stringSlot(name: String, maxChars: Int): Unit`
- `listSlot(name: String, maxEntries: Int): Unit`
- `mapSlot(name: String, maxEntries: Int): Unit`
- `build(): List`
- `phase(name: String): Unit`
- `build(): List`
- `register(name: String): Unit`
- `build(): List`
- `forTool(toolName: String, strategy: ErrorStrategy): Unit`
- `agentConfig(block: AgentConfigBuilder.(): Unit`
- `memorySchema(block: MemorySchemaBuilder.(): Unit`
- `phases(block: PhasesBuilder.(): Unit`
- `tools(block: ToolsBuilder.(): Unit`
- `limits(block: LimitsBuilder.(): Unit`
- `errorStrategy(block: ErrorStrategyBuilder.(): Unit`
- `build(): AgentConfig`
- `getAgentId(): String`
- `getAgentType(): String`
- `getState(): AgentState`
- `getConfig(): AgentHomeConfig`
- `getSkills(): List`
- `getActiveSkills(): List`
- `getRules(): List`
- `getWorkflows(): List`
- `getMcpConfigs(): List`
- `buildSystemPrompt(): String`
- `reload(): Unit`
- `store(slotName: String, entry: MemoryEntry): SlotFullResult`
- `getSlot(slotName: String): List`
- `getCompleteness(): Map`
- `getTotalSize(): Int`
- `clear(): Unit`
- `getBackendName(): String`
- `getAgent(agentType: String, config: AgentConfig): GenericAgent`
- `listAgentTypes(): List`
- `getSessionContext(agentType: String): SessionContext`
- `addCommandToHistory(agentType: String, command: String, response: String): Unit`
- `getCommandHistory(agentType: String): List`
- `resetSession(agentType: String): Unit`
- `buildContextSummary(agentType: String): String`
- `onUpdate(chunk: String, progress: Int): Unit`
- `getRunningAgentTypes(): List`
- `getAvailableToolDescriptors(): List`
- `buildToolListMessage(): String`
- `buildToolsUpdatedMessage(): String`
- `register(tool: AgentTool): Unit`
- `registerAll(tools: List<AgentTool>): Unit`
- `listTools(): List`
- `listToolsWithSource(): List`
- `getRemainingCalls(): Int`
- `resetCallCount(): Unit`
- `getAgentName(): String`
- `createOllama(model: String = "llama3"): Unit`
- `createGemini(apiKey: String, model: String = "gemini-1.5-pro"): Unit`
- `setFailoverOrder(providerIds: List<String>): Unit`
- `generateVelocityTrend(totalTickets: Int, resolvedCount: Int): List`
- `calculateAIVelocity(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double): Double`
- `getFailoverLog(): List`
- `fromLegacy(phase: String): AnalysisPhase`
- `buildBatchPrompt(tickets: List<Pair<String, String>>: Any, isRetry: Boolean = false): String`
- `splitByContentLimit(tickets: List<Pair<String, String>>: Any): List`
- `enqueue(ticketId: String): Unit`
- `isNew(ticketId: String): Boolean`
- `dequeue(): String`
- `hasNext(): Boolean`
- `isLimitReached(): Boolean`
- `markCompleted(): Unit`
- `markFailed(): Unit`
- `markSkipped(): Unit`
- `log(status: CascadeLogStatus, ticketKey: String, message: String): Unit`
- `buildPrompt(content: StructuredTicketContent): String`
- `parse(ticketId: String, response: String): AnalysisResult`
- `mapSubTasks(subtasks: List<JiraSubtask>?): List`
- `mapIssueLinks(links: List<JiraIssueLink>?): List`
- `mapComments(wrapper: JiraCommentWrapper?): List`
- `mapChangelog(changelog: JiraChangelog?): List`
- `stripMarkdownFences(raw: String): String`
- `computeConfidence(sectionCount: Int): ExtractionConfidence`
- `map(ticketId: String, root: AIResponseRoot): AnalysisResult`
- `normalize(value: Double): Double`
- `isValid(value: Double): Boolean`
- `classify(description: String): ClassifiedContent`
- `extractAsIsState(description: String): String`
- `extractToBeState(description: String): String`
- `extractApiSpecifications(description: String): List`
- `extractDatabaseChanges(description: String): List`
- `extractAcceptanceCriteria(description: String): List`
- `stripMarkdownFences(response: String): String`
- `build(ticketId: String, ticketContent: String): String`
- `parseResponse(ticketId: String, response: String): AnalysisResult`
- `mapToResult(ticketId: String, root: JsonObject): AnalysisResult`
- `generateJwt(user: AuthenticatedUser): String`
- `validateJwt(token: String): AuthenticatedUser`
- `invalidateSession(userId: String): Unit`
- `buildSystemPrompt(context: ChatContext): String`
- `buildPrompt(context: GenerationContext): String`
- `parse(markdown: String): List`
- `serialize(sections: List<DocumentSection>): String`
- `hasValidCitations(section: DocumentSection): Boolean`
- `hasInsufficientDataWarning(section: DocumentSection): Boolean`
- `buildPrompt(context: GenerationContext): String`
- `parse(markdown: String): List`
- `serialize(sections: List<DocumentSection>): String`
- `generate(brdMarkdown: String): String`
- `resetDisplacements(): Unit`
- `find(x: Int): Int`
- `union(a: Int, b: Int): Unit`
- `edgeKey(sourceId: String, targetId: String): String`
- `computeLayout(graph: NetworkGraph, width: Double, height: Double): GraphLayout`
- `detectClusters(graph: NetworkGraph): List`
- `filterNodes(nodes: List<TicketNode>, query: String): List`
- `onScanStatus(status: ScanStatus): Boolean`
- `onPollError(): Unit`
- `isTerminal(status: ScanStatus): Boolean`
- `extractAdfText(node: kotlinx.serialization.json.JsonObject): String`
- `getRunningServers(): Map`
- `getStatus(configId: String): McpProcessStatus`
- `getActiveTools(): List`
- `getClient(configId: String): McpProtocolClient`
- `close(): Unit`
- `check(role: UserRole, permission: Permission): Boolean`
- `getPermissions(role: UserRole): Set`
- `hasPermission(role: UserRole, permission: Permission): Boolean`
- `getPermissions(role: UserRole): Set`
- `triggerBuild(projectKey: String): Unit`
- `cancel(): Unit`
- `fromSettings(settings: AppSettings): AppSettingsResponse`
- `promptContainsAllSectionHeadings(): Unit`
- `promptContainsAntiHallucinationInstructions(): Unit`
- `promptContainsSourceCitationFormat(): Unit`
- `promptIncludesBusinessSummary(): Unit`
- `promptIncludesDependencyContext(): Unit`
- `promptIncludesAcceptanceCriteria(): Unit`
- `promptIncludesLinkedTickets(): Unit`
- `promptIncludesAttachmentContent(): Unit`
- `promptRequestsThreeDrawioDiagrams(): Unit`
- `promptRequestsRawXmlFormat(): Unit`
- `promptIncludesSprintMetadata(): Unit`
- `parseEmptyStringReturnsAllDefaultSections(): Unit`
- `parseFullMarkdownReturnsAllSections(): Unit`
- `parsePartialMarkdownFillsMissingSections(): Unit`
- `extractsSourceCitations(): Unit`
- `serializeProducesValidMarkdown(): Unit`
- `roundTripPreservesAllHeadings(): Unit`
- `sectionOrderMatchesBrdTemplate(): Unit`
- `parseMalformedMarkdownDoesNotCrash(): Unit`
- `validateHeadingsAllMatchReturnsValid(): Unit`
- `validateHeadingsMissingSectionsDetected(): Unit`
- `validateHeadingsExtraSectionsDetected(): Unit`
- `validateHeadingsEmptySectionsListReportsMissing(): Unit`
- `validateHeadingsWorksWithFsdSections(): Unit`
- `hasValidCitationsReturnsTrueWhenPresent(): Unit`
- `hasValidCitationsReturnsFalseWhenAbsent(): Unit`
- `hasValidCitationsHandlesMultipleCitations(): Unit`
- `hasInsufficientDataWarningDetectsEmoji(): Unit`
- `hasInsufficientDataWarningReturnsFalseForNormal(): Unit`
- `validateDocumentFullBrdWithCitationsAndNoWarnings(): Unit`
- `validateDocumentPartialWithWarnings(): Unit`
- `validateDocumentFsdAllDefaultSections(): Unit`
- `brdFilenameMatchesPattern(): Unit`
- `fsdFilenameMatchesPattern(): Unit`
- `slidesFilenameMatchesPattern(): Unit`
- `filenameEndsWith_md(): Unit`
- `filenameContainsTicketIdAndType(): Unit`
- `promptContainsAll10SectionHeadings(): Unit`
- `promptContainsAntiHallucinationInstructions(): Unit`
- `promptContainsSourceCitationFormat(): Unit`
- `promptIncludesTechnicalArchitectRole(): Unit`
- `promptExpandsApiSpecifications(): Unit`
- `promptExpandsDatabaseChanges(): Unit`
- `promptExpandsExternalIntegrations(): Unit`
- `promptRequestsFourDrawioDiagrams(): Unit`
- `promptRequestsRawXmlFormat(): Unit`
- `promptIncludesBusinessSummary(): Unit`
- `promptIncludesLinkedTickets(): Unit`
- `parseEmptyStringReturnsAllDefaultSections(): Unit`
- `parseFullMarkdownReturnsAllSections(): Unit`
- `parsePartialMarkdownFillsMissingSections(): Unit`
- `extractsSourceCitations(): Unit`
- `serializeProducesValidMarkdown(): Unit`
- `roundTripPreservesAllHeadings(): Unit`
- `sectionOrderMatchesFsdTemplate(): Unit`
- `parseMalformedMarkdownDoesNotCrash(): Unit`
- `rejectsEmptyMarkdown(): Unit`
- `rejectsBlankMarkdown(): Unit`
- `generates7SlidesFromFullBrd(): Unit`
- `slidesSeparatedByTripleDash(): Unit`
- `eachSlideHasH2Heading(): Unit`
- `slideHeadingsMatchExpected(): Unit`
- `maxSevenBulletsPerSlide(): Unit`
- `handlesMissingSectionsGracefully(): Unit`
- `insufficientDataSectionsShowWarningMessage(): Unit`
- `extractsBulletPointsFromContent(): Unit`
- `fallsBackToSentenceExtractionWhenNoBullets(): Unit`
- `testEstimationLogic_RoundsToNearestScrumPoint(): Unit`
- `testScrumPointsScale(): Unit`
- `getCredentialState(): JiraCredentialState`
- `getJiraCredentials(): JiraCredentials`
- `retryWrite(block: (): Unit`
- `encryptAES256GCM(plaintext: String, key: String): String`
- `decryptAES256GCM(ciphertext: String, key: String): String`
- `buildFullJiraIssue(): JiraIssue`
- `arbFallbackTickets(): Arb`
- `arbCommentMap(): Arb`
- `arbAttachmentChunks(): Arb`
- `save(doc: GeneratedDocument): Unit`
- `findByTicketIdAndType(ticketId: String, type: String): GeneratedDocument`
- `countByTicketIdAndType(ticketId: String, type: String): Int`
- `diffProducesExactlyNewNodeIdsAndEdgeKeys(): Unit`
- `diffFromEmptyOldGraphReturnsAllNewElements(): Unit`
- `diffOfIdenticalGraphsProducesEmptyResult(): Unit`
- `setup(): Unit`
- `setup(): Unit`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| — | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: unknown
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
