package com.assistant.server.document

import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.JiraContentExtractorImpl
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.settings.SettingsRepository
import org.slf4j.LoggerFactory

/**
 * Feature-flag-aware [JiraContentExtractor] that delegates to
 * [DeepJiraContentExtractor] or [JiraContentExtractorImpl] based
 * on the `deep_collection_enabled` runtime setting.
 *
 * Reads the setting from DB on every call — no restart required.
 */
class FeatureFlagContentExtractor(
    private val deepExtractor: DeepJiraContentExtractor,
    private val legacyExtractor: JiraContentExtractorImpl,
    private val settingsRepository: SettingsRepository
) : JiraContentExtractor {

    private val logger = LoggerFactory.getLogger(FeatureFlagContentExtractor::class.java)

    override suspend fun extract(ticketId: String): StructuredTicketContent {
        val enabled = DeepCollectionSettings.isEnabled(settingsRepository)
        if (!enabled) {
            logger.debug("Using JiraContentExtractorImpl for ticket {}", ticketId)
            return legacyExtractor.extract(ticketId)
        }
        return try {
            logger.debug("Using DeepJiraContentExtractor for ticket {}", ticketId)
            deepExtractor.extract(ticketId)
        } catch (e: Exception) {
            logger.warn(
                "Deep extraction failed for {}, falling back to legacy: {}",
                ticketId, e.message
            )
            legacyExtractor.extract(ticketId)
        }
    }
}
