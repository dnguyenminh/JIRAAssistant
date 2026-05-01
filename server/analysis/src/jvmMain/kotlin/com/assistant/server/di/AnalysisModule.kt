package com.assistant.server.di

import com.assistant.ai.MapReduceAnalyzer
import com.assistant.server.analysis.BatchPromptBuilder
import com.assistant.server.analysis.BatchStrategy
import com.assistant.server.analysis.MapReduceAnalyzerAdapter
import com.assistant.server.analysis.MapReduceOrchestrator
import com.assistant.server.analysis.ProgressTracker
import com.assistant.server.analysis.ReducePromptBuilder
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.attachment.AttachmentDownloader
import com.assistant.server.attachment.AttachmentDownloaderImpl
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.EmbeddingServiceImpl
import com.assistant.server.attachment.LinkedAttachmentProcessor
import com.assistant.server.attachment.VectorStore
import com.assistant.server.db.pg.PgVectorStoreImpl
import com.assistant.server.indexing.IndexingPipeline
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for analysis, attachment, embedding, indexing,
 * and map-reduce pipeline bindings.
 */
val analysisModule = module {
    // VectorStore — PgVectorStoreImpl (pgvector-backed)
    single<VectorStore> { PgVectorStoreImpl(get()) }

    // Embedding service
    single<EmbeddingService> {
        val repo = get<com.assistant.kb.ProviderConfigRepository>()
        EmbeddingServiceImpl(
            get<io.ktor.client.HttpClient>(),
            configProvider = {
                val emb = repo.findByType(com.assistant.ai.ProviderType.EMBEDDING)
                if (emb != null) {
                    EmbeddingServiceImpl.EmbeddingConfig(emb.model ?: "nomic-embed-text", emb.endpoint)
                } else {
                    val ollama = repo.findByType(com.assistant.ai.ProviderType.OLLAMA)
                    EmbeddingServiceImpl.EmbeddingConfig("nomic-embed-text", ollama?.endpoint ?: "http://localhost:11434")
                }
            }
        )
    }

    // Attachment downloader
    single<AttachmentDownloader> { AttachmentDownloaderImpl(get<io.ktor.client.HttpClient>()) }

    // Attachment pipeline
    single<AttachmentPipeline> { buildAttachmentPipeline(get(), get(), get(), get(), get(), get()) }

    // Linked attachment processor
    single { LinkedAttachmentProcessor(attachmentPipeline = get(), vectorStore = get(), scanLogRepository = get()) }

    // Indexing pipeline
    single { IndexingPipeline(embeddingService = get(), vectorStore = get(), graphEngine = get()) }

    // ── Map-Reduce Analysis Pipeline (Req 10.1, 10.5) ──
    single { MapReduceConfig().validated() }
    single { BatchStrategy(get()) }
    single { BatchPromptBuilder() }
    single { ReducePromptBuilder() }
    single { ProgressTracker() }
    single {
        MapReduceOrchestrator(
            batchStrategy = get(), batchPromptBuilder = get(), reducePromptBuilder = get(),
            responseParser = get(), configProvider = { get<MapReduceConfig>() },
            aiAnalysisSemaphore = get(named("aiAnalysisSemaphore")), progressTracker = get()
        )
    }
    single<MapReduceAnalyzer> {
        val credSvc = get<com.assistant.jira.JiraCredentialsService>()
        val http = get<io.ktor.client.HttpClient>()
        MapReduceAnalyzerAdapter(
            orchestrator = get(), configProvider = { get<MapReduceConfig>() },
            jiraClientProvider = {
                val c = credSvc.getJiraCredentials()
                if (c != null) {
                    val t = java.util.Base64.getEncoder()
                        .encodeToString("${c.email}:${c.apiToken}".toByteArray())
                    com.assistant.jira.JiraRestClient(http, c.domain, "Basic $t")
                } else com.assistant.jira.NoOpJiraClient()
            },
            sectionClassifier = get(),
            jiraApiSemaphore = get(named("jiraApiSemaphore"))
        )
    }
}

private fun buildAttachmentPipeline(
    credSvc: com.assistant.jira.JiraCredentialsService,
    downloader: AttachmentDownloader,
    embeddingService: EmbeddingService,
    vectorStore: VectorStore,
    mcpProcessManager: com.assistant.mcp.McpProcessManager,
    scanLogRepository: com.assistant.scan.ScanLogRepository,
): AttachmentPipeline = AttachmentPipeline(
    downloader = downloader, embeddingService = embeddingService, vectorStore = vectorStore,
    mcpProcessManager = mcpProcessManager, scanLogRepository = scanLogRepository,
    jiraAuthProvider = {
        val c = credSvc.getJiraCredentials() ?: return@AttachmentPipeline null
        val t = java.util.Base64.getEncoder().encodeToString("${c.email}:${c.apiToken}".toByteArray())
        "Basic $t"
    },
    markitdownIdResolver = {
        val mcpRepo = org.koin.java.KoinJavaComponent.getKoin().get<com.assistant.mcp.McpServerRepository>()
        kotlinx.coroutines.runBlocking {
            mcpRepo.getAll().firstOrNull { it.name.equals("markitdown", ignoreCase = true) }?.id
        }
    }
)
