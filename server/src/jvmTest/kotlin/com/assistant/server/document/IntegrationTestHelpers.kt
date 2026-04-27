package com.assistant.server.document

import com.assistant.kb.KBRecord
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.document.collection.FakeVectorStore
import com.assistant.server.document.models.TraversalConfig
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.sync.Semaphore

/**
 * Helper functions for [DeepCollectorIntegrationTest].
 *
 * Builds DeepCollector and DocumentAggregatorImpl instances
 * with in-memory fakes for isolated testing.
 */

/** In-memory [SettingsRepository] for testing feature toggles. */
class InMemorySettings(
    private val store: MutableMap<String, String> = mutableMapOf()
) : SettingsRepository {
    override suspend fun getAll() = store.toMap()
    override suspend fun get(key: String) = store[key]
    override suspend fun put(key: String, value: String) { store[key] = value }
    override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
}

/** Build a [DeepCollector] with in-memory fakes. */
fun buildDeepCollector(
    ticketLinks: Map<String, List<String>> = mapOf("TEST-1" to listOf("TEST-2")),
    kbRecords: Map<String, KBRecord> = emptyMap(),
    jiraSem: Semaphore = Semaphore(5),
    aiSem: Semaphore = Semaphore(5)
): DeepCollector = DeepCollector(
    jiraClientProvider = { GraphJiraClient(ticketLinks.withDefault { emptyList() }) },
    kbRepository = FakeKBRepository(kbRecords),
    vectorStore = FakeVectorStore(),
    scanLogRepository = NoOpScanLogRepository(),
    configProvider = { TraversalConfig(maxDepth = 3, maxTickets = 20).validated() },
    traversalCache = NoOpTraversalCache(),
    rateLimiter = NoOpRateLimiter(),
    collectionJobManager = NoOpCollectionJobManager(),
    jiraApiSemaphore = jiraSem,
    aiAnalysisSemaphore = aiSem
)

/** Build a [DocumentAggregatorImpl] with in-memory fakes. */
fun buildLegacyAggregator(
    kbRecords: Map<String, KBRecord> = emptyMap()
): DocumentAggregatorImpl = DocumentAggregatorImpl(
    kbRepository = FakeKBRepository(kbRecords),
    vectorStore = FakeVectorStore(),
    embeddingService = NoOpEmbedding()
)

/** No-op [EmbeddingService] for legacy aggregator tests. */
private class NoOpEmbedding : EmbeddingService {
    override suspend fun embed(text: String): FloatArray? = null
}
