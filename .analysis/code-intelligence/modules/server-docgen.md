# Module Analysis — server-docgen

**Last Updated:** 2026-04-30T10:44:26.963Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-docgen/
├── com.assistant.server.db.pg/     # Application logic
├── com.assistant.server.di/     # Application logic
├── com.assistant.server.document.cache/     # Application logic
├── com.assistant.server.document.collection/     # Application logic
├── com.assistant.server.document.curation/     # Application logic
├── com.assistant.server.document.curation.models/     # Domain model
├── com.assistant.server.document/     # Application logic
├── com.assistant.server.document.extraction/     # Application logic
├── com.assistant.server.document.jobs/     # Application logic
├── com.assistant.server.document.models/     # Domain model
├── com.assistant.server.document.prompt/     # Application logic
├── com.assistant.server.document.security/     # Security/Authentication
├── com.assistant.server.document.traversal/     # Application logic
├── com.assistant.server.jobs/     # Application logic
├── com.assistant.server.routes/     # Application logic
└── com.assistant.server.document.curation.generators/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| PgCollectionJobRepository | com.assistant.server.db.pg | Data access | public |
| PgCollectionJobSql | com.assistant.server.db.pg | Application component | internal |
| InMemoryTraversalCache | com.assistant.server.db.pg | Application component | public |
| CacheEntry | com.assistant.server.db.pg | Application component | data |
| TraversalCache | com.assistant.server.db.pg | Application component | public |
| TraversalCacheImpl | com.assistant.server.db.pg | Application component | public |
| TraversalCacheRepository | com.assistant.server.db.pg | Data access | public |
| CacheEntry | com.assistant.server.db.pg | Application component | data |
| AttachmentContentCollector | com.assistant.server.db.pg | Application component | public |
| CommentCollector | com.assistant.server.db.pg | Application component | public |
| AttachmentCurator | com.assistant.server.db.pg | Application component | public |
| BudgetEnforcer | com.assistant.server.db.pg | Application component | public |
| CommentSummarizer | com.assistant.server.db.pg | Application component | public |
| CuratedPromptAssembler | com.assistant.server.db.pg | Application component | public |
| CurationConfig | com.assistant.server.db.pg | Configuration | public |
| CurationPipeline | com.assistant.server.db.pg | Application component | public |
| CurationSettings | com.assistant.server.db.pg | Application component | public |
| DefaultAttachmentCurator | com.assistant.server.db.pg | Application component | public |
| DefaultBudgetEnforcer | com.assistant.server.db.pg | Application component | public |
| DefaultCommentSummarizer | com.assistant.server.db.pg | Application component | public |
| DefaultCurationPipeline | com.assistant.server.db.pg | Application component | public |
| DefaultMcpToolRegistrar | com.assistant.server.db.pg | Application component | public |
| DefaultTemporalClassifier | com.assistant.server.db.pg | Application component | public |
| McpToolRegistrar | com.assistant.server.db.pg | Application component | public |
| BudgetResult | com.assistant.server.db.pg | Application component | data |
| TemporalRelation | com.assistant.server.db.pg | Application component | public |
| ContentClassification | com.assistant.server.db.pg | Application component | public |
| TicketClassification | com.assistant.server.db.pg | Application component | data |
| CommentSummary | com.assistant.server.db.pg | Application component | data |
| CuratedAttachment | com.assistant.server.db.pg | Application component | data |
| CuratedContext | com.assistant.server.db.pg | Application component | data |
| CurationMetrics | com.assistant.server.db.pg | Application component | data |
| ToBeSection | com.assistant.server.db.pg | Application component | data |
| AsIsSection | com.assistant.server.db.pg | Application component | data |
| ClassifiedTicketData | com.assistant.server.db.pg | Application component | data |
| OutdatedReference | com.assistant.server.db.pg | Application component | data |
| TicketReference | com.assistant.server.db.pg | Application component | data |
| TemporalClassifier | com.assistant.server.db.pg | Application component | public |
| DeepCollectionSettings | com.assistant.server.db.pg | Application component | public |
| DeepCollector | com.assistant.server.db.pg | Application component | public |
| DeepJiraContentExtractor | com.assistant.server.db.pg | Application component | public |
| DocumentAggregatorImpl | com.assistant.server.db.pg | Application component | public |
| is | com.assistant.server.db.pg | Application component | public |
| TicketIdExtractor | com.assistant.server.db.pg | Application component | public |
| FeatureFlagAggregator | com.assistant.server.db.pg | Application component | public |
| FeatureFlagContentExtractor | com.assistant.server.db.pg | Application component | public |
| CollectionJobManager | com.assistant.server.db.pg | Application component | public |
| CollectionJobManagerImpl | com.assistant.server.db.pg | Application component | public |
| CollectionJobRepository | com.assistant.server.db.pg | Data access | public |
| AttachmentCollectionResult | com.assistant.server.db.pg | Application component | data |
| CollectionJobType | com.assistant.server.db.pg | Application component | public |
| CollectionJobStatus | com.assistant.server.db.pg | Application component | public |
| CollectionJobItemStatus | com.assistant.server.db.pg | Application component | public |
| CollectionJobItem | com.assistant.server.db.pg | Application component | data |
| CollectionJob | com.assistant.server.db.pg | Application component | data |
| CollectionJobResponse | com.assistant.server.db.pg | Data transfer object | data |
| CollectionJobItemResponse | com.assistant.server.db.pg | Data transfer object | data |
| FullComment | com.assistant.server.db.pg | Application component | data |
| is | com.assistant.server.db.pg | Application component | public |
| CommentCollectionResult | com.assistant.server.db.pg | Application component | data |
| EnrichedContext | com.assistant.server.db.pg | Application component | public |
| used | com.assistant.server.db.pg | Application component | data |
| EnrichedContextSurrogate | com.assistant.server.db.pg | Application component | data |
| EnrichedContextSerializer | com.assistant.server.db.pg | Application component | internal |
| RelationshipType | com.assistant.server.db.pg | Application component | public |
| TicketNode | com.assistant.server.db.pg | Application component | data |
| TicketEdge | com.assistant.server.db.pg | Application component | data |
| TicketGraph | com.assistant.server.db.pg | Application component | data |
| TraversalConfig | com.assistant.server.db.pg | Configuration | data |
| TraversalMetadata | com.assistant.server.db.pg | Application component | data |
| PromptAssembler | com.assistant.server.db.pg | Application component | public |
| PromptSkeleton | com.assistant.server.db.pg | Application component | data |
| AssemblyResult | com.assistant.server.db.pg | Application component | data |
| PromptSectionType | com.assistant.server.db.pg | Application component | public |
| PromptPriorityConfig | com.assistant.server.db.pg | Configuration | public |
| PromptSectionBuilder | com.assistant.server.db.pg | Application component | internal |
| InMemoryRateLimiter | com.assistant.server.db.pg | Application component | public |
| RateLimiter | com.assistant.server.db.pg | Application component | public |
| RateLimitExceededException | com.assistant.server.db.pg | Error handling | public |
| RateLimiterImpl | com.assistant.server.db.pg | Application component | public |
| RateLimitRepository | com.assistant.server.db.pg | Data access | public |
| TicketGraphHolder | com.assistant.server.db.pg | Application component | public |
| KBFirstTicketFetcher | com.assistant.server.db.pg | Application component | public |
| DiscoveredTicket | com.assistant.server.db.pg | Application component | data |
| RelatedTicketDiscovery | com.assistant.server.db.pg | Application component | public |
| RelevanceScorer | com.assistant.server.db.pg | Application component | public |
| FetchResult | com.assistant.server.db.pg | Application component | sealed |
| Success | com.assistant.server.db.pg | Application component | data |
| PermissionDenied | com.assistant.server.db.pg | Application component | data |
| Failed | com.assistant.server.db.pg | Application component | data |
| TicketFetcher | com.assistant.server.db.pg | Application component | open |
| TraversalEngine | com.assistant.server.db.pg | Application component | public |
| BfsQueueItem | com.assistant.server.db.pg | Application component | data |
| TraversalState | com.assistant.server.db.pg | Application component | public |
| DependencyChecker | com.assistant.server.db.pg | Application component | public |
| CheckResult | com.assistant.server.db.pg | Application component | data |
| DocGenProgressTracker | com.assistant.server.db.pg | Application component | public |
| JobChainOrchestrator | com.assistant.server.db.pg | Application component | public |
| JobExecutor | com.assistant.server.db.pg | Application component | open |
| SubprocessFailedException | com.assistant.server.db.pg | Error handling | public |
| SubprocessAgentStub | com.assistant.server.db.pg | Application component | internal |
| JobExecutorDocHelper | com.assistant.server.db.pg | Utility functions | internal |
| JobManager | com.assistant.server.db.pg | Application component | public |
| GenerationLockException | com.assistant.server.db.pg | Error handling | public |
| DependencyException | com.assistant.server.db.pg | Error handling | public |
| JobNotFoundException | com.assistant.server.db.pg | Error handling | public |
| InvalidTransitionException | com.assistant.server.db.pg | Error handling | public |
| PhaseLabelMapper | com.assistant.server.db.pg | Data mapping | public |
| RejectBody | com.assistant.server.db.pg | Application component | data |
| UserPrincipal | com.assistant.server.db.pg | Application component | data |
| JobResponseDto | com.assistant.server.db.pg | Data transfer object | data |
| TraversalCachePropertyTest | com.assistant.server.db.pg | Test class | public |
| FakeCacheJiraClient | com.assistant.server.db.pg | External service client | public |
| AttachmentCollectorPropertyTest | com.assistant.server.db.pg | Test class | public |
| CommentCollectorPropertyTest | com.assistant.server.db.pg | Test class | public |
| FakeCommentJiraClient | com.assistant.server.db.pg | External service client | public |
| FakeVectorStore | com.assistant.server.db.pg | Application component | public |
| AttachmentCuratorPropertyTest | com.assistant.server.db.pg | Test class | public |
| BudgetEnforcerPropertyTest | com.assistant.server.db.pg | Test class | public |
| CommentSummarizerPropertyTest | com.assistant.server.db.pg | Test class | public |
| CommentSummarizerTest | com.assistant.server.db.pg | Test class | public |
| CuratedPromptAssemblerPropertyTest | com.assistant.server.db.pg | Test class | public |
| CurationIntegrationTest | com.assistant.server.db.pg | Test class | public |
| CurationPipelinePropertyTest | com.assistant.server.db.pg | Test class | public |
| GeminiTimeoutTest | com.assistant.server.db.pg | Test class | public |
| CurationArbitraries | com.assistant.server.db.pg | Application component | public |
| McpToolRegistrarTest | com.assistant.server.db.pg | Test class | public |
| TemporalClassifierPropertyTest | com.assistant.server.db.pg | Test class | public |
| TemporalClassifierTest | com.assistant.server.db.pg | Test class | public |
| DeepCollectorIntegrationTest | com.assistant.server.db.pg | Test class | public |
| DeepCollectorPropertyTest | com.assistant.server.db.pg | Test class | public |
| TicketGraphSpec | com.assistant.server.db.pg | Test class | data |
| FakeKBRepository | com.assistant.server.db.pg | Data access | public |
| NoOpTraversalCache | com.assistant.server.db.pg | Application component | public |
| NoOpRateLimiter | com.assistant.server.db.pg | Application component | public |
| NoOpCollectionJobManager | com.assistant.server.db.pg | Application component | public |
| NoOpScanLogRepository | com.assistant.server.db.pg | Data access | public |
| GraphJiraClient | com.assistant.server.db.pg | External service client | public |
| DocumentAggregatorImplTest | com.assistant.server.db.pg | Test class | public |
| DocumentApproveBugExplorationTest | com.assistant.server.db.pg | Test class | public |
| DocumentPreservationGenerators | com.assistant.server.db.pg | Application component | public |
| DocumentPreservationPropertyTest | com.assistant.server.db.pg | Test class | public |
| TicketIdExtractorPropertyTest | com.assistant.server.db.pg | Test class | public |
| InMemorySettings | com.assistant.server.db.pg | Application component | public |
| NoOpEmbedding | com.assistant.server.db.pg | Application component | private |
| CollectionJobManagerPropertyTest | com.assistant.server.db.pg | Test class | public |
| CollectionJobSpec | com.assistant.server.db.pg | Test class | data |
| InMemoryCollectionJobRepository | com.assistant.server.db.pg | Data access | public |
| NoOpDownloader | com.assistant.server.db.pg | Application component | private |
| NoOpEmbeddingService | com.assistant.server.db.pg | Business logic | private |
| NoOpMcpProcessManager | com.assistant.server.db.pg | Application component | private |
| equivalent | com.assistant.server.db.pg | Application component | public |
| EnrichedContextPropertyTest | com.assistant.server.db.pg | Test class | public |
| that | com.assistant.server.db.pg | Application component | public |
| produces | com.assistant.server.db.pg | Application component | public |
| TraversalConfigPropertyTest | com.assistant.server.db.pg | Test class | public |
| PromptAssemblerPropertyTest | com.assistant.server.db.pg | Test class | public |
| RateLimiterPropertyTest | com.assistant.server.db.pg | Test class | public |
| DummyJiraClient | com.assistant.server.db.pg | External service client | internal |
| DummySectionClassifier | com.assistant.server.db.pg | Application component | internal |
| FakeTicketFetcher | com.assistant.server.db.pg | Application component | public |
| RelevanceScorerPropertyTest | com.assistant.server.db.pg | Test class | public |
| TraversalEnginePropertyTest | com.assistant.server.db.pg | Test class | public |
| TraversalEngineSecurityPropertyTest | com.assistant.server.db.pg | Test class | public |
| TraversalEngineTerminationPropertyTest | com.assistant.server.db.pg | Test class | public |
| LinkedGraph | com.assistant.server.db.pg | Application component | data |
| DocGenUxPropertyTest | com.assistant.server.db.pg | Test class | public |
| FakeOllamaAgent | com.assistant.server.db.pg | Application component | public |
| FakeNonStreamingAgent | com.assistant.server.db.pg | Application component | public |
| FakeOrchestrator | com.assistant.server.db.pg | Application component | public |
| TrackingAggregator | com.assistant.server.db.pg | Application component | public |
| NoOpJobRepo | com.assistant.server.db.pg | Application component | public |
| NoOpDocRepo | com.assistant.server.db.pg | Application component | public |
| InMemoryDocumentRepository | com.assistant.server.db.pg | Data access | public |
| InMemoryJobRepository | com.assistant.server.db.pg | Data access | public |
| JobExecutorStreamingTest | com.assistant.server.db.pg | Test class | public |
| JobExecutorSubprocessDirectPropertyTest | com.assistant.server.db.pg | Test class | public |
| PropFakeOrchestrator | com.assistant.server.db.pg | Application component | private |
| PropCapturingDocRepo | com.assistant.server.db.pg | Application component | private |
| PropNoOpReporter | com.assistant.server.db.pg | Application component | private |
| PropStubManager | com.assistant.server.db.pg | Application component | private |
| PropStubProxy | com.assistant.server.db.pg | Application component | private |
| PropStubSettings | com.assistant.server.db.pg | Application component | private |
| JobExecutorSubprocessDirectTest | com.assistant.server.db.pg | Test class | public |
| CapturingDocRepo | com.assistant.server.db.pg | Application component | private |
| TrackingJobRepo | com.assistant.server.db.pg | Application component | private |
| JobManagerUnitTest | com.assistant.server.db.pg | Test class | public |
| JobManagerVersioningUnitTest | com.assistant.server.db.pg | Test class | public |
| JobRecoveryVersioningPropertyTest | com.assistant.server.db.pg | Test class | public |
| JobStateMachinePropertyTest | com.assistant.server.db.pg | Test class | public |
| JobTestGenerators | com.assistant.server.db.pg | Application component | public |
| NoOpJobExecutor | com.assistant.server.db.pg | Application component | public |
| ProgressMappingRangePropertyTest | com.assistant.server.db.pg | Test class | public |
| StreamingPipelineIntegrationTest | com.assistant.server.db.pg | Test class | public |
| TestableJobExecutor | com.assistant.server.db.pg | Application component | public |
| ProgressWrite | com.assistant.server.db.pg | Application component | data |
| TestJobManager | com.assistant.server.db.pg | Application component | public |
| ThrottleLogicPropertyTest | com.assistant.server.db.pg | Test class | public |

## Public API Surface

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

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | com.assistant.server.document.security.RateLimiter, com.assistant.server.db.JobRepository, com.assistant.server.db.DocumentRepository, com.assistant.server.db.JobRepository |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
