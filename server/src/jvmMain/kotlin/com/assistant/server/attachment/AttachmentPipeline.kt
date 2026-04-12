package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpServerState
import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanLogStatus
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * Orchestrates attachment processing: download → markitdown → chunk → embed → store.
 * Requirements: 22.3, 22.4, 22.6, 22.8, 22.13, 22.14, 22.15
 */
class AttachmentPipeline(
    private val downloader: AttachmentDownloader,
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val mcpProcessManager: McpProcessManager,
    private val scanLogRepository: ScanLogRepository,
    private val jiraAuthProvider: () -> String?,
    private val markitdownIdResolver: (() -> String?)? = null
) {
    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "pdf", "docx", "xlsx", "pptx", "txt", "md",
            "csv", "html", "png", "jpg", "jpeg", "gif"
        )
        const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50MB
        const val MARKITDOWN_NAME = "markitdown"
    }

    /** Resolve the actual MCP server ID for markitdown (may differ from "markitdown"). */
    private fun resolveMarkitdownId(): String {
        return markitdownIdResolver?.invoke() ?: MARKITDOWN_NAME
    }

    /** Check if attachment is eligible for processing (size limit only). */
    fun isEligible(attachment: JiraAttachment): Boolean {
        return attachment.size <= MAX_FILE_SIZE
    }

    /** Extract file extension (lowercase, no dot). */
    fun getExtension(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot >= 0) filename.substring(dot + 1).lowercase() else ""
    }

    /** Process all attachments for a ticket. Returns total chunks saved. */
    suspend fun processAttachments(
        projectKey: String, ticketKey: String,
        attachments: List<JiraAttachment>
    ): Int {
        var totalChunks = 0
        for (att in attachments) {
            totalChunks += processSingleAttachment(projectKey, ticketKey, att)
        }
        return totalChunks
    }

    /** Process a single attachment: skip/download/convert/chunk/embed/store. */
    private suspend fun processSingleAttachment(
        projectKey: String, ticketKey: String, att: JiraAttachment
    ): Int {
        // KB-First: skip if already processed
        if (vectorStore.existsByAttachmentId(att.id)) {
            logSkipped(projectKey, ticketKey, att.filename, "already in KB")
            return 0
        }
        // Eligibility check
        if (!isEligible(att)) {
            logSkipped(projectKey, ticketKey, att.filename, skipReason(att))
            return 0
        }
        return downloadAndProcess(projectKey, ticketKey, att)
    }

    private suspend fun downloadAndProcess(
        projectKey: String, ticketKey: String, att: JiraAttachment
    ): Int = try {
        logAnalyzing(projectKey, ticketKey, att)
        val destPath = "data/attachments/${ticketKey}/${att.filename}"
        val authHeader = jiraAuthProvider() ?: error("No Jira auth")
        val contentUrl = att.content ?: error("No content URL")
        val ok = downloader.download(contentUrl, destPath, authHeader)
        if (!ok) { logFailed(projectKey, ticketKey, att.filename, "download failed"); return 0 }
        val chunks = convertAndEmbed(projectKey, ticketKey, att, destPath)
        cleanupTempFile(destPath)
        chunks
    } catch (e: Exception) {
        logFailed(projectKey, ticketKey, att.filename, e.message ?: "unknown error")
        0
    }

    private suspend fun convertAndEmbed(
        projectKey: String, ticketKey: String, att: JiraAttachment, filePath: String
    ): Int {
        val markdown = convertViaMarkitdown(filePath) ?: return 0.also {
            logFailed(projectKey, ticketKey, att.filename, "markitdown conversion failed")
        }
        val textChunks = TextChunker.chunk(markdown)
        val saved = embedAndStore(ticketKey, att, textChunks)
        logCompleted(projectKey, ticketKey, att.filename, saved)
        return saved
    }

    private suspend fun embedAndStore(
        ticketKey: String, att: JiraAttachment,
        textChunks: List<com.assistant.server.attachment.models.TextChunk>
    ): Int {
        var saved = 0
        val now = Instant.now().toString()
        for (tc in textChunks) {
            val emb = embeddingService.embed(tc.text) ?: continue
            val chunk = AttachmentChunk(
                ticketId = ticketKey, attachmentId = att.id,
                filename = att.filename, chunkIndex = tc.index,
                chunkText = tc.text, embedding = emb.toList(), createdAt = now
            )
            if (vectorStore.saveChunk(chunk)) saved++
        }
        return saved
    }

    /** Call markitdown MCP to convert file to markdown. */
    internal suspend fun convertViaMarkitdown(filePath: String): String? {
        if (!ensureMarkitdownRunning()) return null
        val result = callMarkitdownTool(filePath)
        if (result != null) return result
        // First call failed — restart and retry once
        println("[AttachmentPipeline] markitdown call failed, restarting and retrying...")
        return retryAfterRestart(filePath)
    }

    private suspend fun ensureMarkitdownRunning(): Boolean {
        val id = resolveMarkitdownId()
        val status = mcpProcessManager.getStatus(id)
        if (status != null && status.state == McpServerState.RUNNING) return true
        return tryStartMarkitdown()
    }

    private suspend fun tryStartMarkitdown(): Boolean = try {
        val id = resolveMarkitdownId()
        val result = mcpProcessManager.startServer(id)
        val running = result.state == McpServerState.RUNNING
        if (!running) println("[AttachmentPipeline] markitdown MCP not running, skipping")
        running
    } catch (e: Exception) {
        println("[AttachmentPipeline] Failed to start markitdown: ${e.message}")
        false
    }

    private suspend fun retryAfterRestart(filePath: String): String? = try {
        val id = resolveMarkitdownId()
        mcpProcessManager.restartServer(id)
        kotlinx.coroutines.delay(1000)
        if (ensureMarkitdownRunning()) callMarkitdownTool(filePath) else null
    } catch (e: Exception) {
        println("[AttachmentPipeline] markitdown retry failed: ${e.message}")
        null
    }

    private suspend fun callMarkitdownTool(filePath: String): String? = try {
        val id = resolveMarkitdownId()
        val client = mcpProcessManager.getClient(id) ?: return null
        val absPath = java.io.File(filePath).absolutePath
        val args = JsonObject(mapOf("uri" to JsonPrimitive(absPath)))
        val result = client.callTool("convert_to_markdown", args)
        result.content.firstOrNull { it.type == "text" }?.text
    } catch (e: Exception) {
        println("[AttachmentPipeline] markitdown call failed: ${e.message}")
        null
    }

    internal fun skipReason(att: JiraAttachment): String {
        if (att.size > MAX_FILE_SIZE) return "file too large (${att.size / 1024 / 1024}MB > 50MB)"
        return "unknown"
    }

    private fun cleanupTempFile(path: String) = try {
        java.io.File(path).delete()
    } catch (e: Exception) {
        println("[AttachmentPipeline] Temp file cleanup failed: ${e.message}")
    }

    // --- Logging helpers (ScanLogRepository + println) ---

    private suspend fun logAnalyzing(projectKey: String, ticketKey: String, att: JiraAttachment) {
        val sizeKB = att.size / 1024
        val msg = "Processing attachment ${att.filename} (${sizeKB}KB) for $ticketKey"
        println("[AttachmentPipeline] $msg")
        addLogEntry(projectKey, ticketKey, ScanLogStatus.ANALYZING, msg)
    }

    private suspend fun logCompleted(projectKey: String, ticketKey: String, filename: String, chunks: Int) {
        val msg = "Attachment converted: $filename → $chunks chunks"
        println("[AttachmentPipeline] $msg")
        addLogEntry(projectKey, ticketKey, ScanLogStatus.COMPLETED, msg)
    }

    private suspend fun logSkipped(projectKey: String, ticketKey: String, filename: String, reason: String) {
        val msg = "Attachment skipped: $filename — $reason"
        println("[AttachmentPipeline] $msg")
        addLogEntry(projectKey, ticketKey, ScanLogStatus.COMPLETED, msg)
    }

    private suspend fun logFailed(projectKey: String, ticketKey: String, filename: String, error: String) {
        val msg = "Attachment failed: $filename — $error"
        println("[AttachmentPipeline] $msg")
        addLogEntry(projectKey, ticketKey, ScanLogStatus.FAILED, msg)
    }

    private suspend fun addLogEntry(projectKey: String, ticketKey: String, status: ScanLogStatus, msg: String) {
        scanLogRepository.addEntry(ScanLogEntry(
            projectKey = projectKey, ticketId = ticketKey,
            status = status, message = msg, timestamp = Instant.now().toString()
        ))
    }
}
