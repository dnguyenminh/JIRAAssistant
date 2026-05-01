package com.assistant.server.attachment

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AttachmentDownloaderImpl using Ktor MockEngine.
 * Validates: Requirements 22.2
 */
class AttachmentDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `download sends correct URL to HttpClient`() = runBlocking {
        val expectedUrl = "https://jira.example.com/rest/api/3/attachment/content/42"
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("file-content", HttpStatusCode.OK)
        }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        downloader.download(expectedUrl, destPath(), "Basic token")
        assertEquals(expectedUrl, capturedUrl)
    }

    @Test
    fun `download sets Authorization header correctly`() = runBlocking {
        val authHeader = "Basic dXNlcjpwYXNz"
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("data", HttpStatusCode.OK)
        }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        downloader.download("https://jira.test/att/1", destPath(), authHeader)
        assertEquals(authHeader, capturedAuth)
    }

    @Test
    fun `download returns true on HTTP 200`() = runBlocking {
        val engine = MockEngine { respond("pdf-bytes", HttpStatusCode.OK) }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        val result = downloader.download("https://jira.test/att/1", destPath(), "Basic x")
        assertTrue(result)
    }

    @Test
    fun `download returns false on HTTP error status`() = runBlocking {
        val engine = MockEngine { respond("forbidden", HttpStatusCode.Forbidden) }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        val result = downloader.download("https://jira.test/att/1", destPath(), "Basic x")
        assertFalse(result)
    }

    @Test
    fun `download returns false when engine throws exception`() = runBlocking {
        val engine = MockEngine { throw RuntimeException("Network error") }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        val result = downloader.download("https://jira.test/att/1", destPath(), "Basic x")
        assertFalse(result)
    }

    @Test
    fun `download writes content to destination file`() = runBlocking {
        val content = "hello-attachment-content"
        val engine = MockEngine { respond(content, HttpStatusCode.OK) }
        val downloader = AttachmentDownloaderImpl(HttpClient(engine))
        val dest = destPath()
        downloader.download("https://jira.test/att/1", dest, "Basic x")
        assertEquals(content, File(dest).readText())
    }

    private fun destPath(): String =
        File(tempDir, "download_${System.nanoTime()}.bin").absolutePath
}
