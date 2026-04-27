package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Builds detailed AI analysis prompts from structured ticket content.
 *
 * Replaces AIOrchestratorImpl.buildAnalysisPrompt() with a prompt that
 * includes all data from StructuredTicketContent and requests analysis
 * across 6 aspects with strict JSON output format.
 *
 * Both Ticket Intelligence and Batch Scan use this prompt builder.
 *
 * Requirements: 18.1-18.6
 */
interface DeepAnalysisPromptBuilder {

    /**
     * Build a detailed analysis prompt from structured ticket content.
     *
     * @param content The structured ticket content extracted by JiraContentExtractor
     * @return The prompt text to send to the AI agent
     */
    fun buildPrompt(content: StructuredTicketContent): String
}
