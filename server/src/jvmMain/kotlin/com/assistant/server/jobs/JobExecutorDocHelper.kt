package com.assistant.server.jobs

import com.assistant.ai.AIAgent
import com.assistant.document.BrdResponseParser
import com.assistant.document.FsdResponseParser
import com.assistant.document.models.GeneratedDocument
import com.assistant.document.models.GenerationContext
import com.assistant.server.db.DocumentRepository
import com.assistant.server.document.models.EnrichedContext
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Document parsing, saving, and logging helpers for JobExecutor.
 *
 * Requirements: 2.3, 6.1
 */
internal class JobExecutorDocHelper(
    private val documentRepository: DocumentRepository
) {

    private val logger = LoggerFactory.getLogger(JobExecutorDocHelper::class.java)

    fun parseResponse(docType: String, response: String): String {
        logIfSuspiciousResponse(response)
        val sections = when (docType) {
            "BRD" -> BrdResponseParser.parse(response)
            "FSD" -> FsdResponseParser.parse(response)
            else -> error("Unsupported: $docType")
        }
        return when (docType) {
            "BRD" -> BrdResponseParser.serialize(sections)
            else -> FsdResponseParser.serialize(sections)
        }
    }

    suspend fun saveDocument(
        tracker: DocGenProgressTracker, ticketId: String, docType: String,
        markdown: String, ctx: GenerationContext?, agent: AIAgent
    ) {
        tracker.updateProgress(90, "SAVING")
        val sourceIds = collectSourceTicketIds(ctx, ticketId)
        val attachments = ctx?.attachmentChunks
            ?.map { it.filename }?.distinct() ?: emptyList()
        val doc = GeneratedDocument(
            documentType = docType, ticketId = ticketId,
            generatedAt = Instant.now().toString(),
            markdownContent = markdown,
            sourceTicketIds = sourceIds,
            attachmentSources = attachments,
            aiProviderUsed = agent.getAgentName(),
            approvalStatus = "DRAFT"
        )
        documentRepository.save(doc)
        tracker.updateProgress(95, "SAVING")
        tracker.updateProgress(100, "COMPLETE")
    }

    fun logPromptToFile(jobId: String, ticketId: String, docType: String, prompt: String) {
        try {
            val dir = resolvePromptDir()
            dir.mkdirs()
            val ts = Instant.now().toString().replace(":", "-").substringBefore(".")
            val safeTicket = ticketId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val safeJob = jobId.take(8).replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = java.io.File(dir, "${docType}_${safeTicket}_${ts}_${safeJob}.txt")
            file.writeText(prompt)
            logger.info("Prompt saved: ${file.absolutePath} (${prompt.length} chars)")
        } catch (e: Exception) {
            logger.warn("Failed to save prompt to file: ${e.message}")
        }
    }

    private fun resolvePromptDir(): java.io.File {
        val serverData = java.io.File("server/data/prompts")
        if (serverData.exists() || serverData.parentFile?.exists() == true) return serverData
        return java.io.File("data/prompts")
    }

    private fun logIfSuspiciousResponse(response: String) {
        if (response.isBlank()) {
            logger.warn("AI response is empty (length={})", response.length)
            return
        }
        val hasHeadings = Regex("^#{1,3}\\s+", RegexOption.MULTILINE).containsMatchIn(response)
        if (!hasHeadings) {
            logger.warn("AI response has no markdown headings (length={}): {}", response.length, response.take(200))
        }
    }

    private fun collectSourceTicketIds(ctx: GenerationContext?, ticketId: String): List<String> {
        if (ctx == null) return listOf(ticketId)
        if (ctx is EnrichedContext) {
            val allIds = ctx.ticketDepthMap.keys.toList()
            if (allIds.isNotEmpty()) return allIds.distinct()
        }
        val sourceIds = mutableListOf(ctx.mainTicket.ticketId)
        ctx.linkedTicketAnalyses.forEach { sourceIds.add(it.ticketId) }
        return sourceIds.distinct()
    }
}
