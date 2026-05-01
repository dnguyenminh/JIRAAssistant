package com.assistant.server.di

import com.assistant.ai.*
import com.assistant.graph.ForceDirectedGraphEngine
import com.assistant.graph.GraphEngine
import com.assistant.jira.*
import com.assistant.kb.ProviderConfigRepository
import com.assistant.scan.BatchScanEngine
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Base64

/**
 * Aggregator-level Koin bindings for cross-cutting services
 * used by multiple sub-modules. These do not belong to any
 * single sub-module because they wire together dependencies
 * from :shared, :server:core, and multiple feature modules.
 */
val aggregatorBindingsModule: Module = module {

    // ── Jira credentials & client ──
    single { JiraCredentialsService(get()) }
    factory<JiraClient> {
        val creds = get<JiraCredentialsService>().getJiraCredentials()
        if (creds != null) {
            val token = Base64.getEncoder()
                .encodeToString("${creds.email}:${creds.apiToken}".toByteArray())
            JiraRestClient(get<HttpClient>(), creds.domain, "Basic $token")
        } else NoOpJiraClient()
    }

    // ── Graph engine ──
    single<GraphEngine> { ForceDirectedGraphEngine() }

    // ── Deep Analysis pipeline ──
    includes(deepAnalysisBindings())

    // ── AI Orchestrator ──
    single<AIOrchestrator> {
        val http = get<HttpClient>()
        val pcr = get<ProviderConfigRepository>()
        AIOrchestratorImpl(
            kbRepository = get(), agents = emptyMap(),
            jiraContentExtractor = get(),
            deepPromptBuilder = get(),
            deepResponseParser = get(),
            agentProvider = { buildAgentMap(pcr, http) },
            providerConfigProvider = { buildProviderConfigs(pcr) },
            mapReduceAnalyzer = get()
        )
    }

    // ── Batch Scan Engine ──
    single<BatchScanEngine> {
        buildBatchScanEngine(
            aiOrchestrator = get(),
            kbRepository = get(),
            jiraCredentialsService = get(),
            http = get(),
            featureNetworkMapper = get(),
            scanStateRepository = get(),
            scanLogRepository = get(),
            attachmentPipeline = getOrNull(),
            settingsRepository = getOrNull(),
            jiraContentExtractor = getOrNull(),
        )
    }
}
