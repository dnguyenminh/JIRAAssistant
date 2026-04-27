package com.assistant.server.di

import com.assistant.ai.MapReduceAnalyzer
import com.assistant.server.analysis.BatchPromptBuilder
import com.assistant.server.analysis.BatchStrategy
import com.assistant.server.analysis.MapReduceAnalyzerAdapter
import com.assistant.server.analysis.MapReduceOrchestrator
import com.assistant.server.analysis.ProgressTracker
import com.assistant.server.analysis.ReducePromptBuilder
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.attachment.LinkedAttachmentProcessor
import com.assistant.server.document.DeepCollector
import com.assistant.server.document.DeepJiraContentExtractor
import com.assistant.server.document.DocumentAggregatorImpl
import com.assistant.server.document.FeatureFlagAggregator
import com.assistant.server.document.FeatureFlagContentExtractor
import com.assistant.server.document.TicketGraphHolder
import com.assistant.server.document.cache.InMemoryTraversalCache
import com.assistant.server.document.cache.TraversalCache
import com.assistant.server.document.jobs.CollectionJobManager
import com.assistant.server.document.jobs.CollectionJobManagerImpl
import com.assistant.server.document.models.TraversalConfig
import com.assistant.server.document.security.InMemoryRateLimiter
import com.assistant.server.document.security.RateLimiter
import com.assistant.document.DocumentAggregator
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for Deep Ticket Data Collection components.
 *
 * Registers DeepCollector, FeatureFlagAggregator, DeepJiraContentExtractor,
 * FeatureFlagContentExtractor, and all supporting services.
 *
 * The [FeatureFlagAggregator] replaces the direct DocumentAggregator
 * binding — it delegates to DeepCollector or DocumentAggregatorImpl
 * based on the `deep_collection_enabled` runtime setting (Req 12.1).
 *
 * Requirements: 8.1, 8.2, 10.6, 12.1, 12.2
 */
val deepCollectionModule = module {

    // Dual semaphores — Jira API and AI don't block each other (Req 10.6)
    single(named("jiraApiSemaphore")) { Semaphore(5) }
    single(named("aiAnalysisSemaphore")) { Semaphore(3) }

    // In-memory implementations for cache and rate limiter
    // (Pg implementations can replace these when available)
    single<TraversalCache> { InMemoryTraversalCache() }
    single<RateLimiter> { InMemoryRateLimiter() }

    // Collection Job Manager
    single<CollectionJobManager> {
        CollectionJobManagerImpl(
            collectionJobRepository = get(),
            kbRepository = get(),
            scanLogRepository = get(),
            attachmentPipeline = get(),
            aiAnalysisSemaphore = get(named("aiAnalysisSemaphore")),
            jiraApiSemaphore = get(named("jiraApiSemaphore"))
        )
    }

    // DeepCollector
    single {
        DeepCollector(
            jiraClientProvider = {
                val c = get<com.assistant.jira.JiraCredentialsService>().getJiraCredentials()
                if (c != null) {
                    val t = java.util.Base64.getEncoder()
                        .encodeToString("${c.email}:${c.apiToken}".toByteArray())
                    com.assistant.jira.JiraRestClient(get<io.ktor.client.HttpClient>(), c.domain, "Basic $t")
                } else com.assistant.jira.NoOpJiraClient()
            },
            kbRepository = get(),
            vectorStore = get(),
            scanLogRepository = get(),
            configProvider = { TraversalConfig().validated() },
            traversalCache = get(),
            rateLimiter = get(),
            collectionJobManager = get(),
            jiraApiSemaphore = get(named("jiraApiSemaphore")),
            aiAnalysisSemaphore = get(named("aiAnalysisSemaphore"))
        )
    }

    // Legacy aggregator
    single {
        DocumentAggregatorImpl(
            kbRepository = get(),
            vectorStore = get(),
            embeddingService = get()
        )
    }

    // Feature flag aggregator — replaces direct DocumentAggregator binding
    single<DocumentAggregator> {
        FeatureFlagAggregator(
            deepCollector = get(),
            legacyAggregator = get(),
            settingsRepository = get()
        )
    }

    // TicketGraphHolder — passes graph from DeepJiraContentExtractor to AnalysisRoutes (Req 3.2)
    single { com.assistant.server.document.TicketGraphHolder() }

    // LinkedAttachmentProcessor — processes attachments from linked tickets (Req 1.1-1.5, 4.1-4.5)
    single {
        LinkedAttachmentProcessor(
            attachmentPipeline = get(),
            vectorStore = get(),
            scanLogRepository = get()
        )
    }

    // DeepJiraContentExtractor — BFS-based content extraction for analysis
    // Uses same JiraCredentialsService pattern as JiraContentExtractorImpl
    single {
        val credSvc = get<com.assistant.jira.JiraCredentialsService>()
        val http = get<io.ktor.client.HttpClient>()
        DeepJiraContentExtractor(
            jiraClientProvider = {
                val c = credSvc.getJiraCredentials()
                if (c != null) {
                    val t = java.util.Base64.getEncoder()
                        .encodeToString("${c.email}:${c.apiToken}".toByteArray())
                    com.assistant.jira.JiraRestClient(http, c.domain, "Basic $t")
                } else com.assistant.jira.NoOpJiraClient()
            },
            sectionClassifier = get(),
            traversalConfigProvider = { DeepJiraContentExtractor.analysisConfig() },
            jiraApiSemaphore = get(named("jiraApiSemaphore")),
            ticketGraphHolder = get()
        )
    }

    // FeatureFlagContentExtractor — switches between deep and legacy extraction
    single {
        FeatureFlagContentExtractor(
            deepExtractor = get(),
            legacyExtractor = get(),
            settingsRepository = get()
        )
    }

    // --- Map-Reduce Analysis Pipeline (Req 10.1, 10.5) ---

    // MapReduceConfig — validated defaults
    single { MapReduceConfig().validated() }

    // BatchStrategy — depth-level grouping
    single { BatchStrategy(get()) }

    // BatchPromptBuilder — map phase prompts
    single { BatchPromptBuilder() }

    // ReducePromptBuilder — reduce phase prompts
    single { ReducePromptBuilder() }

    // ProgressTracker — pipeline progress callbacks
    single { ProgressTracker() }

    // MapReduceOrchestrator — core pipeline
    single {
        MapReduceOrchestrator(
            batchStrategy = get(),
            batchPromptBuilder = get(),
            reducePromptBuilder = get(),
            responseParser = get(),
            configProvider = { get<MapReduceConfig>() },
            aiAnalysisSemaphore = get(named("aiAnalysisSemaphore")),
            progressTracker = get()
        )
    }

    // MapReduceAnalyzer adapter — bridges shared ↔ server
    single<MapReduceAnalyzer> {
        val credSvc = get<com.assistant.jira.JiraCredentialsService>()
        val http = get<io.ktor.client.HttpClient>()
        MapReduceAnalyzerAdapter(
            orchestrator = get(),
            configProvider = { get<MapReduceConfig>() },
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
