package com.assistant.server.document.extraction

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Regex that every extracted ticket ID must match. */
private val TICKET_ID_REGEX = Regex("[A-Z][A-Z0-9]+-\\d+")

/**
 * Generates a random valid Jira ticket ID matching `[A-Z][A-Z0-9]+-\d+`
 * that also passes [TicketIdExtractor.isValidTicketKey].
 *
 * Project key: 2-5 chars — first char [A-Z], remaining [A-Z0-9]+
 * Number: 1-5 digits (1..99999), no leading zeros.
 * Retries if the generated key collides with a known false positive.
 */
fun Arb.Companion.arbTicketId(): Arb<String> = arbitrary {
    var id: String
    do {
        val firstChar = Arb.of(('A'..'Z').toList()).bind()
        val tailLen = Arb.int(1..4).bind()
        val tailChars = List(tailLen) {
            Arb.of(('A'..'Z') + ('0'..'9')).bind()
        }
        val projectKey = firstChar + tailChars.joinToString("")
        val number = Arb.int(1..99999).bind()
        id = "$projectKey-$number"
    } while (!TicketIdExtractor.isValidTicketKey(id))
    id
}

/** Generates a random uppercase project key of 1-5 chars [A-Z]. */
private fun arbProjectKey(): Arb<String> = arbitrary {
    val len = Arb.int(1..5).bind()
    List(len) { Arb.of(('A'..'Z').toList()).bind() }.joinToString("")
}

/**
 * Generates random text that embeds a known list of ticket IDs
 * among filler words. Returns a pair of (text, embeddedIds).
 */
fun Arb.Companion.arbTextWithTicketIds(): Arb<Pair<String, List<String>>> =
    arbitrary {
        val ids = Arb.list(arbTicketId(), 1..10).bind()
        val unique = ids.distinct()
        val fillers = listOf("see", "related to", "check", "ref", "from", "in")
        val parts = unique.map { id ->
            val filler = Arb.of(fillers).bind()
            "$filler $id"
        }
        val text = parts.joinToString(" ")
        Pair(text, unique)
    }

/**
 * Property 3: TicketIdExtractor — Comprehensive Filter.
 *
 * For any text input, for any excludeIds set, and for any projectScope
 * list, the result of extract(text, excludeIds, projectScope) SHALL
 * satisfy simultaneously:
 * - Each ID matches regex `[A-Z][A-Z0-9]+-\d+`
 * - No ID belongs to excludeIds
 * - If projectScope is non-empty, every ID has project key in projectScope
 * - No duplicate IDs
 *
 * Property 4: TicketIdExtractor — Round-trip.
 *
 * For any list of valid ticket IDs, when formatted into text then parsed
 * back with extract(), the result SHALL contain exactly the same set of
 * ticket IDs (same set, order doesn't matter).
 *
 * **Validates: Requirements 2.1, 2.4, 2.5, 2.6, 2.7**
 */
@OptIn(ExperimentalKotest::class)
class TicketIdExtractorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    // ── Property 3: Comprehensive Filter ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 3: Comprehensive Filter")
    fun `every extracted ID matches the ticket ID regex`() {
        runBlocking {
            checkAll(cfg, Arb.arbTextWithTicketIds()) { (text, _) ->
                val result = TicketIdExtractor.extract(text)
                result.forEach { id ->
                    assertTrue(TICKET_ID_REGEX.matches(id)) {
                        "ID '$id' does not match $TICKET_ID_REGEX"
                    }
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 3: Comprehensive Filter")
    fun `no extracted ID belongs to excludeIds`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.arbTextWithTicketIds(),
                Arb.set(Arb.arbTicketId(), 0..5)
            ) { (text, _), excludeIds ->
                val result = TicketIdExtractor.extract(text, excludeIds)
                result.forEach { id ->
                    assertFalse(id in excludeIds) {
                        "ID '$id' should have been excluded"
                    }
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 3: Comprehensive Filter")
    fun `project scope filters IDs to allowed projects`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.arbTextWithTicketIds(),
                Arb.list(arbProjectKey(), 1..3)
            ) { (text, _), projectScope ->
                val result = TicketIdExtractor.extract(
                    text, emptySet(), projectScope
                )
                result.forEach { id ->
                    val key = TicketIdExtractor.projectKey(id)
                    assertTrue(key in projectScope) {
                        "ID '$id' has key '$key' not in scope $projectScope"
                    }
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 3: Comprehensive Filter")
    fun `extracted IDs contain no duplicates`() {
        runBlocking {
            checkAll(cfg, Arb.arbTextWithTicketIds()) { (text, _) ->
                val result = TicketIdExtractor.extract(text)
                assertEquals(result.size, result.toSet().size) {
                    "Duplicate IDs found: $result"
                }
            }
        }
    }

    // ── Property 4: Round-trip ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 4: Round-trip")
    fun `formatting IDs into text then parsing back yields same set`() {
        runBlocking {
            checkAll(cfg, Arb.list(Arb.arbTicketId(), 1..15)) { ids ->
                val uniqueIds = ids.distinct().toSet()
                val text = uniqueIds.joinToString(" ")
                val parsed = TicketIdExtractor.extract(text).toSet()
                assertEquals(uniqueIds, parsed) {
                    "Round-trip failed: input=$uniqueIds, parsed=$parsed"
                }
            }
        }
    }
}
