package com.assistant.server.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Chat file upload endpoint.
 * POST /api/chat/upload — multipart, max 10MB.
 * Requirements: 19.33
 */
fun Routing.chatUploadRoutes() {
    route("/api/chat") {
        authenticate("auth-jwt") {
            post("/upload") { handleFileUpload() }
        }
    }
}

private suspend fun RoutingContext.handleFileUpload() {
    val (userId, _) = extractUserClaims() ?: return
    val multipart = call.receiveMultipart()
    var savedFile: UploadResult? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (savedFile == null) {
                    savedFile = saveUploadedFile(part, userId)
                }
            }
            else -> {}
        }
        part.dispose()
    }

    if (savedFile != null) {
        call.respond(HttpStatusCode.OK, savedFile)
    } else {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file provided"))
    }
}

private fun saveUploadedFile(part: PartData.FileItem, userId: String): UploadResult {
    val fileName = part.originalFileName ?: "upload"
    val timestamp = System.currentTimeMillis()
    val safeFileName = "${timestamp}-${fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"

    val uploadDir = File("data/chat-uploads/$userId")
    uploadDir.mkdirs()

    val destFile = File(uploadDir, safeFileName)
    @Suppress("DEPRECATION")
    val bytes = part.streamProvider().readBytes()

    // Enforce 10MB limit
    if (bytes.size > 10 * 1024 * 1024) {
        throw IllegalArgumentException("File exceeds 10MB limit")
    }

    destFile.writeBytes(bytes)
    val fileType = part.contentType?.toString() ?: "application/octet-stream"

    return UploadResult(
        fileId = safeFileName,
        fileName = fileName,
        fileType = fileType,
        fileUrl = "/api/chat/uploads/$userId/$safeFileName"
    )
}

@Serializable
data class UploadResult(
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileUrl: String
)
