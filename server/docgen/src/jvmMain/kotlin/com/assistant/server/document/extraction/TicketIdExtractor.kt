package com.assistant.server.document.extraction

/**
 * Pure function extractor for Jira ticket IDs from arbitrary text.
 *
 * Matches the standard Jira ticket format: one or more uppercase letters
 * (optionally followed by digits), a dash, then one or more digits.
 * Examples: "ICL2-100", "PROJ-42", "ABC-1".
 *
 * This object is stateless and performs no I/O or side effects.
 *
 * Requirements: 2.1, 2.4, 2.5, 2.6
 */
object TicketIdExtractor {

    /** Regex matching Jira ticket IDs: `[A-Z][A-Z0-9]+-\d+` */
    private val TICKET_ID_PATTERN = Regex("[A-Z][A-Z0-9]+-\\d+")

    /**
     * Extract ticket IDs from [text]. Pure function — no I/O.
     *
     * 1. Finds all matches of [TICKET_ID_PATTERN] in [text].
     * 2. Deduplicates the results (preserving first-occurrence order).
     * 3. Filters out any IDs present in [excludeIds].
     * 4. If [projectScope] is non-empty, keeps only IDs whose project key
     *    is contained in [projectScope].
     *
     * @param text        Input text to scan for ticket IDs.
     * @param excludeIds  IDs to exclude (e.g. self-reference, already visited).
     * @param projectScope Allowed project keys. Empty list means all projects.
     * @return Deduplicated, filtered list of ticket IDs in discovery order.
     */
    fun extract(
        text: String,
        excludeIds: Set<String> = emptySet(),
        projectScope: List<String> = emptyList()
    ): List<String> {
        if (text.isBlank()) return emptyList()

        return TICKET_ID_PATTERN.findAll(text)
            .map { it.value }
            .distinct()
            .filter { it !in excludeIds }
            .filter { id ->
                projectScope.isEmpty() || projectKey(id) in projectScope
            }
            .toList()
    }

    /**
     * Extract the project key from a ticket ID.
     *
     * The project key is the portion before the last dash.
     * For example: `"ICL2-100"` → `"ICL2"`, `"PROJ-42"` → `"PROJ"`.
     *
     * @param ticketId A valid Jira ticket ID.
     * @return The project key prefix.
     */
    internal fun projectKey(ticketId: String): String {
        return ticketId.substringBeforeLast('-')
    }
}
