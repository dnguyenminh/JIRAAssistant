package com.assistant.server.attachment

import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanLogStatus
import com.assistant.server.attachment.models.TicketAttachmentGroup
import com.assistant.server.document.models.TicketGraph
import org.slf4j.Logger
import java.time.Instant

/**
 * Logging helper for LinkedAttachmentProcessor.
 * Handles SLF4J logging and ScanLogRepository entries.
 * Requirements: 8.1-8.5, 4.5
 */
internal class LinkedAttachmentLogger(
    private val logger: Logger,
    private val scanLogRepository: ScanLogRepository
) {
    fun logCollectionSummary(
        rootTicketId: String, graph: TicketGraph,
        groups: List<TicketAttachmentGroup>, seenIds: Set<String>
    ) {
        val totalNew = groups.sumOf { it.attachments.size }
        val skipped = seenIds.size - totalNew
        logger.info(
            "Linked attachment collection for {}: {} attachments from {} tickets, {} new (not in KB), {} already processed",
            rootTicketId, seenIds.size, graph.nodes.size, totalNew, skipped
        )
    }

    fun logStart(rootTicketId: String, ticketCount: Int, attachmentCount: Int) {
        logger.info(
            "Starting linked attachment processing for {}: {} tickets, {} attachments",
            rootTicketId, ticketCount, attachmentCount
        )
    }

    fun logCompletion(rootTicketId: String, totalChunks: Int, processedTickets: Int, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        logger.info(
            "Linked attachment processing completed for {}: {} chunks from {} tickets, elapsed={}ms",
            rootTicketId, totalChunks, processedTickets, elapsed
        )
    }

    fun logTimeout(elapsed: Long, processed: Int, total: Int) {
        logger.warn(
            "Linked attachment processing timeout after {}ms, processed {}/{} tickets",
            elapsed, processed, total
        )
    }

    fun logTicketDebug(group: TicketAttachmentGroup) {
        logger.debug(
            "Processing attachments for linked ticket {} (depth={}): {} attachments",
            group.ticketId, group.depth, group.attachments.size
        )
    }

    fun logTicketError(ticketId: String, error: String) {
        logger.warn("Attachment processing failed for ticket {}: {}", ticketId, error)
    }

    fun logTopLevelError(rootTicketId: String, error: String?) {
        logger.error("Linked attachment processing failed for {}: {}", rootTicketId, error)
    }

    fun logSingleNodeSkip(rootTicketId: String) {
        logger.debug("Single-node graph for {}, skipping linked attachment processing", rootTicketId)
    }

    fun logNoAttachments(rootTicketId: String) {
        logger.info("No new attachments to process for {}", rootTicketId)
    }

    suspend fun logTicketProcessed(group: TicketAttachmentGroup, chunks: Int) {
        val msg = "Processed linked attachments for ${group.ticketId}: $chunks chunks from ${group.attachments.size} attachments"
        addLogEntry(group.projectKey, group.ticketId, ScanLogStatus.COMPLETED, msg)
    }

    suspend fun logTicketFailed(group: TicketAttachmentGroup, error: String) {
        val msg = "Linked attachment processing failed for ${group.ticketId}: $error"
        addLogEntry(group.projectKey, group.ticketId, ScanLogStatus.FAILED, msg)
    }

    private suspend fun addLogEntry(
        projectKey: String, ticketId: String,
        status: ScanLogStatus, msg: String
    ) {
        scanLogRepository.addEntry(
            ScanLogEntry(
                projectKey = projectKey, ticketId = ticketId,
                status = status, message = msg, timestamp = Instant.now().toString()
            )
        )
    }
}
