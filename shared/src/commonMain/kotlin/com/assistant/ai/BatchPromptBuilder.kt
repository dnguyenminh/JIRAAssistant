package com.assistant.ai

/**
 * Builds batch prompts containing multiple tickets for single AI call.
 * Handles content length limiting and ticket separator formatting.
 * Req: AC 37, AC 39
 */
object BatchPromptBuilder {

    const val MAX_PROMPT_CHARS = 12000
    private const val SEPARATOR_PREFIX = "--- TICKET "
    private const val SEPARATOR_SUFFIX = " ---"

    /** Build a single prompt containing multiple tickets with separators. */
    fun buildBatchPrompt(tickets: List<Pair<String, String>>, isRetry: Boolean = false): String {
        val retryHint = if (isRetry) "\nIMPORTANT: Return ONLY valid JSON array. No markdown, no extra text." else ""
        return buildString {
            appendLine("Analyze the following Jira tickets. Return a JSON array with one object per ticket.")
            appendLine()
            tickets.forEachIndexed { idx, (ticketId, content) ->
                appendLine("$SEPARATOR_PREFIX${idx + 1}$SEPARATOR_SUFFIX")
                appendLine("Ticket ID: $ticketId")
                if (content.isNotBlank()) appendLine(content)
                appendLine()
            }
            appendLine("Return ONLY a valid JSON array:")
            appendLine("[")
            tickets.forEachIndexed { idx, (ticketId, _) ->
                val comma = if (idx < tickets.size - 1) "," else ""
                appendLine("""  { "ticketId": "$ticketId", "requirementSummary": {"unified":"...","affectedModules":[{"name":"...","colorCategory":"PRIMARY|ACCENT|SECONDARY"}]}, "evolution": [{"version":"...","date":"...","description":"...","changeType":"ORIGIN|UPDATE|CURRENT"}], "complexity": {"scrumPoints":5.0,"description":"...","kbReferences":[{"ticketId":"...","similarityPercent":85.0}]} }$comma""")
            }
            appendLine("]")
            append(retryHint)
        }
    }

    /** Split tickets into sub-batches where each batch's content fits within MAX_PROMPT_CHARS. */
    fun splitByContentLimit(tickets: List<Pair<String, String>>): List<List<Pair<String, String>>> {
        if (tickets.isEmpty()) return emptyList()
        val result = mutableListOf<List<Pair<String, String>>>()
        var currentBatch = mutableListOf<Pair<String, String>>()
        var currentLen = 0
        for (ticket in tickets) {
            val ticketLen = estimateTicketLength(ticket)
            if (currentBatch.isNotEmpty() && currentLen + ticketLen > MAX_PROMPT_CHARS) {
                result.add(currentBatch.toList())
                currentBatch = mutableListOf()
                currentLen = 0
            }
            currentBatch.add(ticket)
            currentLen += ticketLen
        }
        if (currentBatch.isNotEmpty()) result.add(currentBatch.toList())
        return result
    }

    /** Estimate the character length a ticket will occupy in the prompt. */
    private fun estimateTicketLength(ticket: Pair<String, String>): Int {
        // separator + "Ticket ID: " + id + content + newlines overhead
        return SEPARATOR_PREFIX.length + 4 + SEPARATOR_SUFFIX.length +
            12 + ticket.first.length + ticket.second.length + 10
    }
}
