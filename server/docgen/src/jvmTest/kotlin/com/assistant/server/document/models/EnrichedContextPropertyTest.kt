package com.assistant.server.document.models

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 8: EnrichedContext — Serialization Round-trip.
 *
 * For any valid EnrichedContext,
 * `Json.decodeFromString(Json.encodeToString(context))` SHALL produce
 * an object equivalent to the original (all fields equal).
 *
 * **Validates: Requirements 5.5**
 */
@OptIn(ExperimentalKotest::class)
class EnrichedContextPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Validates: Requirements 5.5**
     *
     * Serializing an EnrichedContext to JSON and deserializing it back
     * produces an object that equals the original.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 8: Serialization Round-trip")
    fun `serialization round-trip preserves all fields`() {
        runBlocking {
            checkAll(cfg, Arb.arbEnrichedContext()) { original ->
                val jsonStr = json.encodeToString(original)
                val restored = json.decodeFromString<EnrichedContext>(jsonStr)

                assertEquals(original.mainTicket, restored.mainTicket) {
                    "mainTicket mismatch"
                }
                assertEquals(original.linkedTicketAnalyses, restored.linkedTicketAnalyses) {
                    "linkedTicketAnalyses mismatch"
                }
                assertEquals(original.attachmentChunks, restored.attachmentChunks) {
                    "attachmentChunks mismatch"
                }
                assertEquals(original.sprintMetadata, restored.sprintMetadata) {
                    "sprintMetadata mismatch"
                }
                assertEquals(original.allTickets, restored.allTickets) {
                    "allTickets mismatch"
                }
                assertEquals(original.ticketRelationships, restored.ticketRelationships) {
                    "ticketRelationships mismatch"
                }
                assertEquals(original.rawComments, restored.rawComments) {
                    "rawComments mismatch"
                }
                assertEquals(original.allAttachmentChunks, restored.allAttachmentChunks) {
                    "allAttachmentChunks mismatch"
                }
                assertEquals(original.traversalMetadata, restored.traversalMetadata) {
                    "traversalMetadata mismatch"
                }
                assertEquals(original.ticketDepthMap, restored.ticketDepthMap) {
                    "ticketDepthMap mismatch"
                }
                assertEquals(original, restored) {
                    "Full EnrichedContext equality failed"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 5.5**
     *
     * Round-trip serialization is idempotent — serializing the
     * deserialized object produces the same JSON string.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 8: Serialization Round-trip")
    fun `double round-trip produces identical JSON`() {
        runBlocking {
            checkAll(cfg, Arb.arbEnrichedContext()) { original ->
                val json1 = json.encodeToString(original)
                val restored = json.decodeFromString<EnrichedContext>(json1)
                val json2 = json.encodeToString(restored)

                assertEquals(json1, json2) {
                    "JSON output differs after double round-trip"
                }
            }
        }
    }
}
