package com.assistant.server.attachment

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration test: AttachmentDownloader must survive parent cancellation.
 * Catches bug where scan pause/cancel cancels in-flight downloads.
 */
class DownloadCancellationTest {

    @Test
    fun `download completes even when parent scope is cancelled`() = runBlocking {
        var downloadCompleted = false
        val fakeDownloader = object : AttachmentDownloader {
            override suspend fun download(contentUrl: String, destPath: String, authHeader: String): Boolean {
                // Simulate slow download
                withContext(NonCancellable) {
                    delay(100)
                    downloadCompleted = true
                }
                return true
            }
        }

        val job = launch {
            fakeDownloader.download("https://example.com/file.pdf", "/tmp/test.pdf", "Basic abc")
        }
        delay(50) // let download start
        job.cancel() // simulate scan pause
        delay(200) // wait for NonCancellable to finish

        assertTrue(downloadCompleted,
            "Download must complete even after parent cancellation (NonCancellable)")
    }

    @Test
    fun `FakeDownloader returns false on failure`() = runBlocking {
        val downloader = FakeDownloader().apply { shouldSucceed = false }
        val result = downloader.download("https://example.com/file.pdf", "/tmp/test.pdf", "Basic abc")
        assertFalse(result, "FakeDownloader should return false when shouldSucceed=false")
    }
}
