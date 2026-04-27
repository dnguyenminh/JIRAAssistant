package com.assistant.server.chat

import com.assistant.chat.GraphChatContext
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import org.junit.jupiter.api.Assertions.*

// ── Generators ──────────────────────────────────────────────

/** Random Jira ticket IDs matching `[A-Z]{2,5}-[0-9]{1,5}`. */
internal fun arbJiraTicketId(): Arb<String> = Arb.bind(
    Arb.int(2..5),
    Arb.int(1..99999)
) { prefixLen, number ->
    val prefix = (1..prefixLen).map(::randomUpperChar).joinToString("")
    "$prefix-$number"
}

private fun randomUpperChar(seed: Int): Char =
    ('A'.code + (seed * 7 + 3) % 26).toChar()

/** Random filter string: 3–10 uppercase alpha chars. */
private fun arbFilterValue(): Arb<String> =
    Arb.string(minSize = 3, maxSize = 10, codepoints = Codepoint.az())
        .map { it.replaceFirstChar(Char::uppercase) }

/** Random non-blank search query. */
private fun arbSearchQuery(): Arb<String> =
    Arb.string(minSize = 1, maxSize = 30, codepoints = Codepoint.alphanumeric())

/** Random nullable cluster ID. */
private fun arbClusterId(): Arb<Int?> =
    Arb.choice(Arb.constant(null), Arb.int(1..1000).map { it })

/**
 * Random GraphChatContext with focusedNodeKey = null,
 * random filters (0–5), nullable cluster, random search.
 */
internal fun arbNoFocusGraphContext(): Arb<GraphChatContext> = Arb.bind(
    Arb.list(arbFilterValue(), range = 0..5),
    arbClusterId(),
    Arb.choice(Arb.constant(""), arbSearchQuery()),
    Arb.int(0..500),
    Arb.int(1..10)
) { filters, cluster, search, visible, depth ->
    GraphChatContext(
        focusedNodeKey = null,
        activeTypeFilters = filters,
        selectedClusterId = cluster,
        searchQuery = search,
        visibleNodeCount = visible,
        depthValue = depth
    )
}

// ── Assertions ──────────────────────────────────────────────

/** Assert focused key appears at least once, with EXACT and Do NOT split. */
internal fun assertFocusedKeyPresent(result: String, key: String) {
    assertTrue(result.contains(key), "Output must contain key '$key'")
    assertTrue(result.contains("EXACT"), "Output must contain 'EXACT'")
    assertTrue(
        result.contains("Do NOT split"),
        "Output must contain 'Do NOT split'"
    )
}

/** Assert no focused-node instruction text is present. */
internal fun assertNoFocusedInstruction(result: String) {
    assertFalse(result.contains("EXACT"), "Must NOT contain 'EXACT'")
    assertFalse(
        result.contains("Do NOT split"),
        "Must NOT contain 'Do NOT split'"
    )
    assertFalse(
        result.contains("focused on Jira ticket"),
        "Must NOT contain 'focused on Jira ticket'"
    )
}

/** Assert graph state values are preserved in output. */
internal fun assertGraphStatePreserved(
    result: String,
    gc: GraphChatContext
) {
    gc.activeTypeFilters.forEach { filter ->
        assertTrue(
            result.contains(filter),
            "Output must contain filter '$filter'"
        )
    }
    if (gc.searchQuery.isNotBlank()) {
        assertTrue(
            result.contains(gc.searchQuery),
            "Output must contain search query '${gc.searchQuery}'"
        )
    }
    gc.selectedClusterId?.let { id ->
        assertTrue(
            result.contains(id.toString()),
            "Output must contain cluster id '$id'"
        )
    }
}
