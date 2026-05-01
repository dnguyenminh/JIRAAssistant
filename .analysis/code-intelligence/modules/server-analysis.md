# Module Analysis — server-analysis

**Last Updated:** 2026-04-30T10:44:26.958Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-analysis/
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
├── com.assistant.server.document.models/     # Domain model
├── com.assistant.server.document.security/     # Security/Authentication
├── com.assistant.server.document.traversal/     # Application logic
├── com.assistant.server.indexing/     # Application logic
└── com.assistant.server.routes/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| BatchPromptBuilder | com.assistant.server.analysis | Application component | public |
| BatchStrategy | com.assistant.server.analysis | Application component | public |
| BatchSummaryParser | com.assistant.server.analysis | Application component | internal |
| MapPhaseExecutor | com.assistant.server.analysis | Application component | internal |
| MapReduceAnalyzerAdapter | com.assistant.server.analysis | Integration adapter | public |
| MapReduceOrchestrator | com.assistant.server.analysis | Application component | public |
| BatchInfo | com.assistant.server.analysis | Application component | data |
| instead | com.assistant.server.analysis | Application component | public |
| BatchSummary | com.assistant.server.analysis | Application component | data |
| MapReduceConfig | com.assistant.server.analysis | Configuration | data |
| MapReduceResult | com.assistant.server.analysis | Application component | data |
| ProgressTracker | com.assistant.server.analysis | Application component | public |
| ReducePhaseExecutor | com.assistant.server.analysis | Application component | internal |
| ReduceResult | com.assistant.server.analysis | Application component | data |
| ReducePromptBuilder | com.assistant.server.analysis | Application component | public |
| AttachmentDownloader | com.assistant.server.analysis | Application component | public |
| AttachmentDownloaderImpl | com.assistant.server.analysis | Application component | public |
| AttachmentPipeline | com.assistant.server.analysis | Application component | public |
| CosineSimilarity | com.assistant.server.analysis | Application component | public |
| EmbeddingService | com.assistant.server.analysis | Business logic | public |
| EmbeddingServiceImpl | com.assistant.server.analysis | Business logic | public |
| EmbeddingConfig | com.assistant.server.analysis | Configuration | data |
| LinkedAttachmentLogger | com.assistant.server.analysis | Application component | internal |
| LinkedAttachmentProcessor | com.assistant.server.analysis | Application component | public |
| MarkitdownAutoConfig | com.assistant.server.analysis | Configuration | public |
| AttachmentChunk | com.assistant.server.analysis | Application component | data |
| AttachmentStatusResponse | com.assistant.server.analysis | Data transfer object | data |
| AttachmentProcessingStatus | com.assistant.server.analysis | Application component | public |
| ChunkType | com.assistant.server.analysis | Application component | public |
| OllamaEmbeddingRequest | com.assistant.server.analysis | Data transfer object | data |
| OllamaEmbeddingResponse | com.assistant.server.analysis | Data transfer object | data |
| TextChunk | com.assistant.server.analysis | Application component | data |
| TicketAttachmentGroup | com.assistant.server.analysis | Application component | data |
| TextChunker | com.assistant.server.analysis | Application component | public |
| VectorStore | com.assistant.server.analysis | Application component | public |
| VectorStoreImpl | com.assistant.server.analysis | Application component | public |
| PgVectorStoreImpl | com.assistant.server.analysis | Application component | public |
| PgVectorStoreSql | com.assistant.server.analysis | Application component | internal |
| InMemoryTraversalCache | com.assistant.server.analysis | Application component | public |
| CacheEntry | com.assistant.server.analysis | Application component | data |
| TraversalCache | com.assistant.server.analysis | Application component | public |
| TraversalCacheImpl | com.assistant.server.analysis | Application component | public |
| TraversalCacheRepository | com.assistant.server.analysis | Data access | public |
| CacheEntry | com.assistant.server.analysis | Application component | data |
| AttachmentContentCollector | com.assistant.server.analysis | Application component | public |
| CommentCollector | com.assistant.server.analysis | Application component | public |
| DeepCollectionSettings | com.assistant.server.analysis | Application component | public |
| DeepCollector | com.assistant.server.analysis | Application component | public |
| DeepJiraContentExtractor | com.assistant.server.analysis | Application component | public |
| DocumentAggregatorImpl | com.assistant.server.analysis | Application component | public |
| is | com.assistant.server.analysis | Application component | public |
| TicketIdExtractor | com.assistant.server.analysis | Application component | public |
| FeatureFlagAggregator | com.assistant.server.analysis | Application component | public |
| FeatureFlagContentExtractor | com.assistant.server.analysis | Application component | public |
| CollectionJobManager | com.assistant.server.analysis | Application component | public |
| CollectionJobManagerImpl | com.assistant.server.analysis | Application component | public |
| CollectionJobRepository | com.assistant.server.analysis | Data access | public |
| AttachmentCollectionResult | com.assistant.server.analysis | Application component | data |
| CollectionJobType | com.assistant.server.analysis | Application component | public |
| CollectionJobStatus | com.assistant.server.analysis | Application component | public |
| CollectionJobItemStatus | com.assistant.server.analysis | Application component | public |
| CollectionJobItem | com.assistant.server.analysis | Application component | data |
| CollectionJob | com.assistant.server.analysis | Application component | data |
| CollectionJobResponse | com.assistant.server.analysis | Data transfer object | data |
| CollectionJobItemResponse | com.assistant.server.analysis | Data transfer object | data |
| FullComment | com.assistant.server.analysis | Application component | data |
| is | com.assistant.server.analysis | Application component | public |
| CommentCollectionResult | com.assistant.server.analysis | Application component | data |
| EnrichedContext | com.assistant.server.analysis | Application component | public |
| used | com.assistant.server.analysis | Application component | data |
| EnrichedContextSurrogate | com.assistant.server.analysis | Application component | data |
| EnrichedContextSerializer | com.assistant.server.analysis | Application component | internal |
| RelationshipType | com.assistant.server.analysis | Application component | public |
| TicketNode | com.assistant.server.analysis | Application component | data |
| TicketEdge | com.assistant.server.analysis | Application component | data |
| TicketGraph | com.assistant.server.analysis | Application component | data |
| TraversalConfig | com.assistant.server.analysis | Configuration | data |
| TraversalMetadata | com.assistant.server.analysis | Application component | data |
| InMemoryRateLimiter | com.assistant.server.analysis | Application component | public |
| RateLimiter | com.assistant.server.analysis | Application component | public |
| RateLimitExceededException | com.assistant.server.analysis | Error handling | public |
| RateLimiterImpl | com.assistant.server.analysis | Application component | public |
| RateLimitRepository | com.assistant.server.analysis | Data access | public |
| TicketGraphHolder | com.assistant.server.analysis | Application component | public |
| KBFirstTicketFetcher | com.assistant.server.analysis | Application component | public |
| DiscoveredTicket | com.assistant.server.analysis | Application component | data |
| RelatedTicketDiscovery | com.assistant.server.analysis | Application component | public |
| RelevanceScorer | com.assistant.server.analysis | Application component | public |
| FetchResult | com.assistant.server.analysis | Application component | sealed |
| Success | com.assistant.server.analysis | Application component | data |
| PermissionDenied | com.assistant.server.analysis | Application component | data |
| Failed | com.assistant.server.analysis | Application component | data |
| TicketFetcher | com.assistant.server.analysis | Application component | open |
| TraversalEngine | com.assistant.server.analysis | Application component | public |
| BfsQueueItem | com.assistant.server.analysis | Application component | data |
| TraversalState | com.assistant.server.analysis | Application component | public |
| BatchEmbedder | com.assistant.server.analysis | Application component | public |
| EmbedItem | com.assistant.server.analysis | Application component | data |
| IndexingPipeline | com.assistant.server.analysis | Application component | public |
| ConflictResponse | com.assistant.server.analysis | Data transfer object | data |
| AnalysisStatusTracker | com.assistant.server.analysis | Application component | public |
| CascadeStatusTracker | com.assistant.server.analysis | Application component | public |
| BatchPartitionPropertyTest | com.assistant.server.analysis | Test class | public |
| BatchPromptPropertyTest | com.assistant.server.analysis | Test class | public |
| MapReduceIntegrationTest | com.assistant.server.analysis | Test class | public |
| MockAIAgent | com.assistant.server.analysis | Application component | public |
| FailingAIAgent | com.assistant.server.analysis | Application component | public |
| MockResponseParser | com.assistant.server.analysis | Application component | public |
| BatchSummarySerializationPropertyTest | com.assistant.server.analysis | Test class | public |
| MapReduceConfigPropertyTest | com.assistant.server.analysis | Test class | public |
| MapReduceInfoPropertyTest | com.assistant.server.analysis | Test class | public |
| PipelineInput | com.assistant.server.analysis | Application component | data |
| PipelineSelectionPropertyTest | com.assistant.server.analysis | Test class | public |
| MapProgressInput | com.assistant.server.analysis | Application component | data |
| ProgressTrackerPropertyTest | com.assistant.server.analysis | Test class | public |
| ReducePromptPropertyTest | com.assistant.server.analysis | Test class | public |
| AttachmentDownloaderTest | com.assistant.server.analysis | Test class | public |
| AttachmentEligibilityPropertyTest | com.assistant.server.analysis | Test class | public |
| AttachmentPipelineMcpFallbackTest | com.assistant.server.analysis | Test class | public |
| AttachmentPipelineTest | com.assistant.server.analysis | Test class | public |
| BatchScanAttachmentTest | com.assistant.server.analysis | Test class | public |
| AttachmentCall | com.assistant.server.analysis | Application component | data |
| CosineSimilarityPropertyTest | com.assistant.server.analysis | Test class | public |
| CosineSimilarityTest | com.assistant.server.analysis | Test class | public |
| DownloadCancellationTest | com.assistant.server.analysis | Test class | public |
| EmbeddingServiceImplTest | com.assistant.server.analysis | Test class | public |
| FullPipelineIntegrationTest | com.assistant.server.analysis | Test class | public |
| JiraAttachmentSerializationPropertyTest | com.assistant.server.analysis | Test class | public |
| KBFirstDeduplicationPropertyTest | com.assistant.server.analysis | Test class | public |
| MarkitdownAutoConfigTest | com.assistant.server.analysis | Test class | public |
| InMemoryMcpRepo | com.assistant.server.analysis | Application component | public |
| MarkitdownIdResolutionTest | com.assistant.server.analysis | Test class | public |
| IdTrackingProcessManager | com.assistant.server.analysis | Application component | public |
| MarkitdownRetryTest | com.assistant.server.analysis | Test class | public |
| CrashThenRecoverPM | com.assistant.server.analysis | Application component | public |
| ScanLogEntriesTest | com.assistant.server.analysis | Test class | public |
| StubAIOrchestrator | com.assistant.server.analysis | Application component | public |
| StubJiraClient | com.assistant.server.analysis | External service client | public |
| StubAIAgent | com.assistant.server.analysis | Application component | public |
| InMemoryScanStateRepo | com.assistant.server.analysis | Application component | public |
| FakeDownloader | com.assistant.server.analysis | Application component | public |
| FakeEmbeddingService | com.assistant.server.analysis | Business logic | public |
| FakeVectorStore | com.assistant.server.analysis | Application component | public |
| FakeScanLogRepository | com.assistant.server.analysis | Data access | public |
| FakeMcpProtocolClient | com.assistant.server.analysis | External service client | public |
| FakeMcpProcessManager | com.assistant.server.analysis | Application component | public |
| FakeAIAgentForAttachment | com.assistant.server.analysis | Application component | public |
| FakeKBRepoForAttachment | com.assistant.server.analysis | Application component | public |
| FakeGraphEngineForAttachment | com.assistant.server.analysis | Application component | public |
| TextChunkerPropertyTest | com.assistant.server.analysis | Test class | public |
| TextChunkerTest | com.assistant.server.analysis | Test class | public |
| PgVectorStorePropertyTest | com.assistant.server.analysis | Test class | public |
| BatchEmbedderPropertyTest | com.assistant.server.analysis | Test class | public |
| BatchTrackingEmbeddingService | com.assistant.server.analysis | Business logic | private |
| IndexingIdempotencyPropertyTest | com.assistant.server.analysis | Test class | public |
| TrackingVectorStore | com.assistant.server.analysis | Application component | private |
| StubGraphEngine | com.assistant.server.analysis | Application component | private |
| IndexingPipelinePropertyTest | com.assistant.server.analysis | Test class | public |
| IndexingPipelineTest | com.assistant.server.analysis | Test class | public |
| FakeGraphEngineForReindex | com.assistant.server.analysis | Application component | private |
| AnalysisAttachmentBugTest | com.assistant.server.analysis | Test class | public |
| AnalysisPreservationTest | com.assistant.server.analysis | Test class | public |
| TicketDetailMappingTest | com.assistant.server.analysis | Test class | public |
| TicketDetailRoutesTest | com.assistant.server.analysis | Test class | public |
| FakeJiraClient | com.assistant.server.analysis | External service client | public |
| ThrowingJiraClient | com.assistant.server.analysis | External service client | public |

## Public API Surface

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

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | McpServerRepository |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
