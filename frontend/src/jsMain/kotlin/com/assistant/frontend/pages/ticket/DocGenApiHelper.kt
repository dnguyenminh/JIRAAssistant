package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GeneratedDocumentFull
import com.assistant.frontend.models.GenerationJobDto
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * API call helpers and toast management for document generation.
 * Extracted from DocumentGenerationFlow to keep files under 200 lines.
 */
internal object DocGenApiHelper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val ERROR_TOAST_ID = "docgen-error-toast"

    suspend fun postGenerate(ticketId: String, docType: String): String {
        val endpoint = generateEndpoint(ticketId, docType)
        val resp = ApiClient.post(endpoint)
        if (ApiClient.handleUnauthorized(resp)) throw IllegalStateException("Unauthorized")
        if (resp.status.value >= 400) throw IllegalStateException(resp.bodyAsText())
        val result: DocumentGenerationFlow.GenerateResponse = json.decodeFromString(resp.bodyAsText())
        return result.jobId
    }

    suspend fun postGenerateAll(ticketId: String): DocumentGenerationFlow.ChainResponse {
        val resp = ApiClient.post("/api/analysis/$ticketId/generate-all")
        if (ApiClient.handleUnauthorized(resp)) throw IllegalStateException("Unauthorized")
        if (resp.status.value >= 400) throw IllegalStateException(resp.bodyAsText())
        return json.decodeFromString(resp.bodyAsText())
    }

    suspend fun fetchJobStatus(jobId: String): GenerationJobDto {
        val resp = ApiClient.get("/api/jobs/$jobId")
        return json.decodeFromString(resp.bodyAsText())
    }

    suspend fun fetchJobStatusSafe(jobId: String): GenerationJobDto? {
        return try {
            val resp = ApiClient.get("/api/jobs/$jobId")
            if (resp.status.value == 404) return null
            json.decodeFromString(resp.bodyAsText())
        } catch (_: Exception) { null }
    }

    suspend fun fetchFullDocument(
        ticketId: String, docType: String, status: String? = null
    ): GeneratedDocumentFull? {
        val typePath = docType.lowercase()
        val query = if (status != null) "?status=$status" else ""
        val resp = ApiClient.get("/api/analysis/$ticketId/documents/$typePath$query")
        if (resp.status.value >= 400) return null
        return json.decodeFromString(resp.bodyAsText())
    }

    fun generateEndpoint(ticketId: String, docType: String): String = when (docType) {
        "BRD" -> "/api/analysis/$ticketId/generate-brd"
        "FSD" -> "/api/analysis/$ticketId/generate-fsd"
        "REQUIREMENT_SLIDES" -> "/api/analysis/$ticketId/generate-slides"
        else -> "/api/analysis/$ticketId/generate-brd"
    }

    fun showErrorToast(sectionId: String, message: String) {
        dismissErrorToast()
        val section = document.getElementById(sectionId) ?: return
        val toast = document.createElement("div") as HTMLElement
        toast.id = ERROR_TOAST_ID
        toast.className = "docgen-error-toast"
        toast.textContent = "⚠️ $message"
        toast.addEventListener("click", { dismissErrorToast() })
        section.appendChild(toast)
        window.setTimeout({ dismissErrorToast() }, 8000)
    }

    fun dismissErrorToast() {
        document.getElementById(ERROR_TOAST_ID)?.remove()
    }
}
