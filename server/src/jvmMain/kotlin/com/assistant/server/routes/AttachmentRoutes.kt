package com.assistant.server.routes

import com.assistant.rbac.Permission
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentProcessingStatus
import com.assistant.server.attachment.models.AttachmentStatusResponse
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Attachment status routes — query processed attachment chunks.
 * Requirements: 22.20
 */
fun Routing.attachmentRoutes() {
    val vectorStore by inject<VectorStore>()

    route("/api/projects/{key}/tickets/{ticketKey}/attachments") {
        withPermission(Permission.VIEW_ANALYSIS) {
            get {
                val ticketKey = call.parameters["ticketKey"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketKey is required"))
                val statuses = buildAttachmentStatuses(vectorStore, ticketKey)
                call.respond(HttpStatusCode.OK, statuses)
            }
        }
    }
}

private suspend fun buildAttachmentStatuses(vectorStore: VectorStore, ticketKey: String): List<AttachmentStatusResponse> = try {
    val chunks = vectorStore.findByTicketId(ticketKey)
    if (chunks.isEmpty()) emptyList()
    else chunks.groupBy { it.attachmentId }.map { (attId, attChunks) ->
        AttachmentStatusResponse(
            attachmentId = attId,
            filename = attChunks.first().filename,
            status = AttachmentProcessingStatus.CONVERTED,
            chunkCount = attChunks.size
        )
    }
} catch (e: Exception) {
    println("[AttachmentRoutes] Failed: ${e.message}")
    emptyList()
}
