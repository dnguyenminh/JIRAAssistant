package com.assistant.server.document

import com.assistant.document.BrdPromptBuilder
import com.assistant.document.FsdPromptBuilder
import com.assistant.server.document.collection.FakeVectorStore
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.TraversalConfig
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests for feature flag, backward compatibility,
 * concurrent coalesce pattern, and dual semaphores.
 *
 * Requirements: 8.1, 8.2, 10.6, 10.7, 12.1, 12.2
 */
class DeepCollectorIntegrationTest {

    // ── Feature flag toggle tests (Req 12.1, 12.2) ─────────────

    @Test
    fun `FeatureFlagAggregator uses DeepCollector when enabled`() = runBlocking {
        val settings = InMemorySettings(mutableMapOf("deep_collection_enabled" to "true"))
        val deep = buildDeepCollector()
        val legacy = buildLegacyAggregator()
        val aggregator = FeatureFlagAggregator(deep, legacy, settings)

        val result = aggregator.aggregate("TEST-1")
        assertTrue(result is EnrichedContext, "Should return EnrichedContext when deep enabled")
    }

    @Test
    fun `FeatureFlagAggregator uses DocumentAggregatorImpl when disabled`() = runBlocking {
        val settings = InMemorySettings(mutableMapOf("deep_collection_enabled" to "false"))
        val kbMap = mapOf("TEST-1" to buildKBRecord("TEST-1"))
        val deep = buildDeepCollector(kbRecords = kbMap)
        val legacy = buildLegacyAggregator(kbRecords = kbMap)
        val aggregator = FeatureFlagAggregator(deep, legacy, settings)

        val result = aggregator.aggregate("TEST-1")
        assertFalse(result is EnrichedContext, "Should return plain GenerationContext when disabled")
    }

    @Test
    fun `FeatureFlagAggregator defaults to enabled when setting not set`() = runBlocking {
        val settings = InMemorySettings()
        val deep = buildDeepCollector()
        val legacy = buildLegacyAggregator()
        val aggregator = FeatureFlagAggregator(deep, legacy, settings)

        val result = aggregator.aggregate("TEST-1")
        assertTrue(result is EnrichedContext, "Default should use DeepCollector")
    }

    @Test
    fun `runtime toggle switches aggregator without restart`() = runBlocking {
        val settings = InMemorySettings(mutableMapOf("deep_collection_enabled" to "true"))
        val kbMap = mapOf("TEST-1" to buildKBRecord("TEST-1"))
        val deep = buildDeepCollector(kbRecords = kbMap)
        val legacy = buildLegacyAggregator(kbRecords = kbMap)
        val aggregator = FeatureFlagAggregator(deep, legacy, settings)

        // First call: deep enabled
        val r1 = aggregator.aggregate("TEST-1")
        assertTrue(r1 is EnrichedContext)

        // Toggle off at runtime
        settings.put("deep_collection_enabled", "false")
        val r2 = aggregator.aggregate("TEST-1")
        assertFalse(r2 is EnrichedContext)

        // Toggle back on
        settings.put("deep_collection_enabled", "true")
        val r3 = aggregator.aggregate("TEST-1")
        assertTrue(r3 is EnrichedContext)
    }

    // ── EnrichedContext backward compat with prompt builders (Req 8.2) ──

    @Test
    fun `EnrichedContext works with BrdPromptBuilder`() = runBlocking {
        val deep = buildDeepCollector()
        val result = deep.aggregate("TEST-1") as EnrichedContext

        val prompt = BrdPromptBuilder.buildPrompt(result)
        assertTrue(prompt.isNotBlank(), "BRD prompt should not be blank")
        // Prompt should contain BRD section headings
        assertTrue(prompt.contains("Project Overview"), "Should contain BRD section heading")
    }

    @Test
    fun `EnrichedContext works with FsdPromptBuilder`() = runBlocking {
        val deep = buildDeepCollector()
        val result = deep.aggregate("TEST-1") as EnrichedContext

        val prompt = FsdPromptBuilder.buildPrompt(result)
        assertTrue(prompt.isNotBlank(), "FSD prompt should not be blank")
    }

    // ── Concurrent coalesce pattern (Req 10.7) ─────────────────

    @Test
    fun `concurrent requests for same ticket coalesce`() = runBlocking {
        val deep = buildDeepCollector()
        val results = (1..3).map { async { deep.aggregate("TEST-1") } }.awaitAll()

        // All should return the same result (coalesced)
        val first = results.first()
        results.forEach { assertEquals(first, it, "Coalesced results should be equal") }
    }

    // ── Dual semaphores (Req 10.6) ─────────────────────────────

    @Test
    fun `dual semaphores are independent`() = runBlocking {
        val jiraSem = Semaphore(2)
        val aiSem = Semaphore(2)
        val deep = buildDeepCollector(jiraSem = jiraSem, aiSem = aiSem)

        // Acquire all AI permits — should not block Jira operations
        aiSem.acquire()
        aiSem.acquire()

        // DeepCollector should still work (uses jiraSem, not aiSem)
        val result = withTimeout(5000) { deep.aggregate("TEST-1") }
        assertNotNull(result, "Should complete despite AI semaphore exhausted")

        aiSem.release()
        aiSem.release()
    }
}
