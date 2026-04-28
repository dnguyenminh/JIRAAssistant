package com.assistant.server.routes

import com.assistant.server.db.DocumentRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Handlers for document approval, versioning, and diff (Req 6.4–6.5, 7.2–7.8).
 */

internal suspend fun handleGetDocumentByType(
    call: ApplicationCall, ticketId: String, type: String, repo: DocumentRepository
) {
    val statusFilter = call.request.queryParameters["status"]?.uppercase()
    val doc = when (statusFilter) {
        "DRAFT" -> repo.findLatestDraftByTicketIdAndType(ticketId, type)
        else -> repo.findLatestByTicketIdAndType(ticketId, type)
            ?: repo.findByTicketIdAndType(ticketId, type)
    }
    if (doc != null) call.respond(HttpStatusCode.OK, doc)
    else call.respond(HttpStatusCode.NotFound, ErrorResponse("No $type document found for $ticketId"))
}

internal suspend fun handleGetVersions(
    call: ApplicationCall, ticketId: String, type: String, repo: DocumentRepository
) {
    val versions = repo.findAllVersions(ticketId, type)
    call.respond(HttpStatusCode.OK, versions)
}

internal suspend fun handleGetVersion(
    call: ApplicationCall, ticketId: String, type: String, n: Int, repo: DocumentRepository
) {
    val doc = repo.findByVersion(ticketId, type, n)
    if (doc != null) call.respond(HttpStatusCode.OK, doc)
    else call.respond(HttpStatusCode.NotFound, ErrorResponse("Version $n not found"))
}

internal suspend fun handleDiff(
    call: ApplicationCall, ticketId: String, type: String, repo: DocumentRepository
) {
    val v1 = call.request.queryParameters["v1"]?.toIntOrNull()
    val v2 = call.request.queryParameters["v2"]?.toIntOrNull()
    if (v1 == null || v2 == null) {
        return call.respond(HttpStatusCode.BadRequest, ErrorResponse("v1 and v2 required"))
    }
    val doc1 = repo.findByVersion(ticketId, type, v1)
    val doc2 = repo.findByVersion(ticketId, type, v2)
    if (doc1 == null || doc2 == null) {
        return call.respond(HttpStatusCode.NotFound, ErrorResponse("Version not found"))
    }
    val diff = generateUnifiedDiff(doc1.markdownContent, doc2.markdownContent, v1, v2)
    call.respond(HttpStatusCode.OK, mapOf("diff" to diff))
}

internal suspend fun handleApprove(call: ApplicationCall, docId: Long, repo: DocumentRepository) {
    val doc = repo.findById(docId)
    if (doc == null) return call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
    if (doc.approvalStatus != "DRAFT") {
        return call.respond(HttpStatusCode.Conflict, ErrorResponse("Chỉ document DRAFT mới có thể approve"))
    }
    repo.updateApprovalStatus(docId, "APPROVED", null, Instant.now().toString(), null)
    val updated = repo.findById(docId)
    call.respond(HttpStatusCode.OK, updated!!)
}

internal suspend fun handleReject(call: ApplicationCall, docId: Long, repo: DocumentRepository) {
    val doc = repo.findById(docId)
    if (doc == null) return call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
    if (doc.approvalStatus != "DRAFT") {
        return call.respond(HttpStatusCode.Conflict, ErrorResponse("Chỉ document DRAFT mới có thể reject"))
    }
    @Serializable data class RejectBody(val reason: String)
    val body = try { call.receive<RejectBody>() } catch (_: Exception) {
        return call.respond(HttpStatusCode.BadRequest, ErrorResponse("reason is required"))
    }
    if (body.reason.length < 10) {
        return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Lý do reject phải có ít nhất 10 ký tự"))
    }
    repo.updateApprovalStatus(docId, "REJECTED", null, Instant.now().toString(), body.reason)
    val updated = repo.findById(docId)
    call.respond(HttpStatusCode.OK, updated!!)
}

/** Simple unified diff between two text contents. */
private fun generateUnifiedDiff(text1: String, text2: String, v1: Int, v2: Int): String {
    val lines1 = text1.lines()
    val lines2 = text2.lines()
    val sb = StringBuilder()
    sb.appendLine("--- Version $v1")
    sb.appendLine("+++ Version $v2")
    val maxLines = maxOf(lines1.size, lines2.size)
    for (i in 0 until maxLines) {
        val l1 = lines1.getOrNull(i)
        val l2 = lines2.getOrNull(i)
        when {
            l1 == l2 -> sb.appendLine(" ${l1 ?: ""}")
            l1 != null && l2 != null -> { sb.appendLine("-$l1"); sb.appendLine("+$l2") }
            l1 != null -> sb.appendLine("-$l1")
            l2 != null -> sb.appendLine("+$l2")
        }
    }
    return sb.toString()
}
