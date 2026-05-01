package com.assistant.server.di

import com.assistant.server.document.DeepCollector
import com.assistant.server.document.DeepJiraContentExtractor
import com.assistant.server.document.DocumentAggregatorImpl
import com.assistant.server.document.FeatureFlagAggregator
import com.assistant.server.document.FeatureFlagContentExtractor
import com.assistant.server.document.TicketGraphHolder
import com.assistant.server.document.cache.InMemoryTraversalCache
import com.assistant.server.document.cache.TraversalCache
import com.assistant.server.document.curation.AttachmentCurator
import com.assistant.server.document.curation.BudgetEnforcer
import com.assistant.server.document.curation.CommentSummarizer
import com.assistant.server.document.curation.CurationPipeline
import com.assistant.server.document.curation.DefaultAttachmentCurator
import com.assistant.server.document.curation.DefaultBudgetEnforcer
import com.assistant.server.document.curation.DefaultCommentSummarizer
import com.assistant.server.document.curation.DefaultCurationPipeline
import com.assistant.server.document.curation.DefaultMcpToolRegistrar
import com.assistant.server.document.curation.DefaultTemporalClassifier
import com.assistant.server.document.curation.McpToolRegistrar
import com.assistant.server.document.curation.TemporalClassifier
import com.assistant.server.document.jobs.CollectionJobManager
import com.assistant.server.document.jobs.CollectionJobManagerImpl
import com.assistant.server.document.jobs.CollectionJobRepository
import com.assistant.server.document.models.TraversalConfig
import com.assistant.server.document.security.InMemoryRateLimiter
import com.assistant.server.document.security.RateLimiter
import com.assistant.server.db.pg.PgCollectionJobRepository
import com.assistant.server.jobs.DependencyChecker
import com.assistant.server.jobs.JobChainOrchestrator
import com.assistant.server.jobs.JobExecutor
import com.assistant.server.jobs.JobManager
import com.assistant.document.DocumentAggregator
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for the docgen sub-module.
 *
 * Absorbs all document, job, deep collection, and curation
 * bindings previously spread across ServerModule,
 * deepCollectionModule, and curationModule.
 *
 * Requirements: 8.1, 8.2, 10.6, 12.1, 12.2, 13.3
 */
val docgenModule = module {

    // ── Curation Pipeline (from curationModule) ──

    single<TemporalClassifier> { DefaultTemporalClassifier() }
    single<CommentSummarizer> { DefaultCommentSummarizer() }
    single<AttachmentCurator> { DefaultAttachmentCurator() }
    single<BudgetEnforcer> { DefaultBudgetEnforcer() }
    single<McpToolRegistrar> { DefaultMcpToolRegistrar() }
    single<CurationPipeline> {
        DefaultCurationPipeline(
            temporalClassifier = get(),
            commentSummarizer = get(),
            attachmentCurator = get(),
            budgetEnforcer = get()
        )
    }

    // ── Deep Collection (from deepCollectionModule) ──

    single(named("jiraApiSemaphore")) { Semaphore(5) }
    single(named("aiAnalysisSemaphore")) { Semaphore(3) }

    single<TraversalCache> { InMemoryTraversalCache() }
    single<RateLimiter> { InMemoryRateLimiter() }

    // CollectionJobRepository binding
    single<CollectionJobRepository> {
        PgCollectionJobRepository(get())
    }

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

    single {
        DeepCollector(
            jiraClientProvider = {
                val c = get<com.assistant.jira.JiraCredentialsService>()
                    .getJiraCredentials()
                if (c != null) {
                    val t = java.util.Base64.getEncoder()
                        .encodeToString(
                            "${c.email}:${c.apiToken}".toByteArray()
                        )
                    com.assistant.jira.JiraRestClient(
                        get<io.ktor.client.HttpClient>(),
                        c.domain, "Basic $t"
                    )
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

    single {
        DocumentAggregatorImpl(
            kbRepository = get(),
            vectorStore = get(),
            embeddingService = get()
        )
    }

    single<DocumentAggregator> {
        FeatureFlagAggregator(
            deepCollector = get(),
            legacyAggregator = get(),
            settingsRepository = get()
        )
    }

    single { TicketGraphHolder() }

    single {
        val credSvc = get<com.assistant.jira.JiraCredentialsService>()
        val http = get<io.ktor.client.HttpClient>()
        DeepJiraContentExtractor(
            jiraClientProvider = {
                val c = credSvc.getJiraCredentials()
                if (c != null) {
                    val t = java.util.Base64.getEncoder()
                        .encodeToString(
                            "${c.email}:${c.apiToken}".toByteArray()
                        )
                    com.assistant.jira.JiraRestClient(
                        http, c.domain, "Basic $t"
                    )
                } else com.assistant.jira.NoOpJiraClient()
            },
            sectionClassifier = get(),
            traversalConfigProvider = {
                DeepJiraContentExtractor.analysisConfig()
            },
            jiraApiSemaphore = get(named("jiraApiSemaphore")),
            ticketGraphHolder = get(),
            kbRepository = get()
        )
    }

    single {
        FeatureFlagContentExtractor(
            deepExtractor = get(),
            legacyExtractor = get(),
            settingsRepository = get()
        )
    }

    // ── Job Manager (from ServerModule) ──

    single<DependencyChecker> { DependencyChecker }
    single {
        JobChainOrchestrator(jobRepository = get())
    }
    single {
        JobExecutor(
            aggregator = get(),
            documentRepository = get(),
            jobRepository = get(),
            settingsRepository = get(),
            subprocessOrchestrator = getOrNull(),
            kbRepository = get(),
            aiOrchestrator = getOrNull()
        )
    }
    single {
        JobManager(
            jobRepository = get(),
            documentRepository = get(),
            jobExecutor = get(),
            chainOrchestrator = get(),
            dependencyChecker = get()
        )
    }
}
