package com.assistant.server.attachment

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File

/**
 * Ktor HttpClient-based attachment downloader with streaming write.
 * Requirements: 22.2
 */
class AttachmentDownloaderImpl(
    private val httpClient: HttpClient
) : AttachmentDownloader {

    override suspend fun download(
        contentUrl: String, destPath: String, authHeader: String
    ): Boolean = try {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        // Use withContext to isolate from parent cancellation during download
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val response = httpClient.get(contentUrl) {
                header(HttpHeaders.Authorization, authHeader)
                timeout {
                    requestTimeoutMillis = 60_000 // 60s timeout for large files
                    connectTimeoutMillis = 10_000
                }
            }
            if (!response.status.isSuccess()) {
                println("[AttachmentDownloader] HTTP ${response.status} for $contentUrl")
                false
            } else {
                writeResponseToFile(response, destFile)
                true
            }
        }
    } catch (e: Exception) {
        println("[AttachmentDownloader] Download failed: ${e.message}")
        false
    }

    private suspend fun writeResponseToFile(response: HttpResponse, file: File) {
        val channel = response.bodyAsChannel()
        file.outputStream().use { out ->
            channel.toInputStream().use { input -> input.copyTo(out) }
        }
    }
}
