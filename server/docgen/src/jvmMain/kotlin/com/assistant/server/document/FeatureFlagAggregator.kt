package com.assistant.server.document

import com.assistant.document.DocumentAggregator
import com.assistant.document.models.GenerationContext
import com.assistant.settings.SettingsRepository
import org.slf4j.LoggerFactory

/**
 * Delegates to [DeepCollector] or [DocumentAggregatorImpl] based on
 * the `deep_collection_enabled` runtime setting.
 *
 * Reads the setting from DB on every call — no restart required (Req 12.1, 12.2).
 *
 * Requirements: 8.1, 12.1, 12.2
 */
class FeatureFlagAggregator(
    private val deepCollector: DeepCollector,
    private val legacyAggregator: DocumentAggregatorImpl,
    private val settingsRepository: SettingsRepository
) : DocumentAggregator {

    private val logger = LoggerFactory.getLogger(FeatureFlagAggregator::class.java)

    override suspend fun aggregate(ticketId: String): GenerationContext {
        val enabled = DeepCollectionSettings.isEnabled(settingsRepository)
        return if (enabled) {
            logger.debug("Using DeepCollector for ticket {}", ticketId)
            deepCollector.aggregate(ticketId)
        } else {
            logger.debug("Using DocumentAggregatorImpl for ticket {}", ticketId)
            legacyAggregator.aggregate(ticketId)
        }
    }

    /** Expose deep collection status for prompt builder selection. */
    suspend fun isDeepCollectionEnabled(): Boolean =
        DeepCollectionSettings.isEnabled(settingsRepository)
}
