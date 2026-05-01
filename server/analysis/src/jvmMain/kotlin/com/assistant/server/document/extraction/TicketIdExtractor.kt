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

    /** Common false positive project keys (months, quarters, protocols, tech terms). */
    private val FALSE_POSITIVE_KEYS = setOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
        "Q1", "Q2", "Q3", "Q4", "H1", "H2",
        "URL", "HTTP", "HTTPS", "FTP", "SSH",
        "UTF", "AES", "RSA", "SHA", "MD5",
        "UC", "BD", "IB", "TMPL", "DATA", "USER",
        "VTIG", "CEA", "CHECKID", "NTBX"
    )

    /** Max reasonable Jira issue number. Above this = likely false positive. */
    private const val MAX_ISSUE_NUMBER = 99_999L

    /**
     * Extract ticket IDs from [text]. Pure function — no I/O.
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
            .filter { isValidTicketKey(it) }
            .filter { id ->
                projectScope.isEmpty() || projectKey(id) in projectScope
            }
            .toList()
    }

    /**
     * Validate that a regex match is a plausible Jira ticket key.
     * Rejects month abbreviations, quarter refs, protocol prefixes,
     * single-char project keys, and very large issue numbers.
     */
    internal fun isValidTicketKey(ticketId: String): Boolean {
        val parts = ticketId.split("-", limit = 2)
        if (parts.size != 2) return false
        val key = parts[0]
        val numberStr = parts[1]
        if (key.length < 2 || key.length > 8) return false
        if (key in FALSE_POSITIVE_KEYS) return false
        val number = numberStr.toLongOrNull() ?: return false
        if (number > MAX_ISSUE_NUMBER) return false
        return true
    }

    /** Extract the project key (portion before the last dash). */
    internal fun projectKey(ticketId: String): String {
        return ticketId.substringBeforeLast('-')
    }
}
