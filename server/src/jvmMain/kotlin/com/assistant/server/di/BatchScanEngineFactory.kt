package com.assistant.server.di

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.jira.JiraCredentialsService
import com.assistant.jira.JiraRestClient
import com.assistant.jira.NoOpJiraClient
import com.assistant.kb.KBRepository
import com.assistant.scan.BatchScanEngine
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanStateRepository
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.settings.SettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Base64

/**
 * Factory for [BatchScanEngine] — wires together dependencies
 * from :shared, :server:core, and :server:analysis.
 */
internal fun buildBatchScanEngine(
    aiOrchestrator: AIOrchestrator,
    kbRepository: KBRepository,
    jiraCredentialsService: JiraCredentialsService,
    http: HttpClient,
    featureNetworkMapper: FeatureNetworkMapper,
    scanStateRepository: ScanStateRepository,
    scanLogRepository: ScanLogRepository,
    attachmentPipeline: AttachmentPipeline?,
    settingsRepository: SettingsRepository?,
    jiraContentExtractor: JiraContentExtractor?,
): BatchScanEngine {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    return BatchScanEngine(
        aiOrchestrator = aiOrchestrator,
        kbRepository = kbRepository,
        jiraClientProvider = {
            val c = jiraCredentialsService.getJiraCredentials()
            if (c != null) {
                val t = Base64.getEncoder()
                    .encodeToString("${c.email}:${c.apiToken}".toByteArray())
                JiraRestClient(http, c.domain, "Basic $t")
            } else NoOpJiraClient()
        },
        featureNetworkMapper = featureNetworkMapper,
        scanStateRepository = scanStateRepository,
        scanLogRepository = scanLogRepository,
        scope = scope,
        attachmentProcessor = attachmentPipeline?.let { pipeline ->
            { projectKey, ticketKey, attachments ->
                pipeline.processAttachments(projectKey, ticketKey, attachments)
            }
        },
        onScanComplete = null,
        settingsRepository = settingsRepository,
        jiraContentExtractor = jiraContentExtractor,
    )
}
