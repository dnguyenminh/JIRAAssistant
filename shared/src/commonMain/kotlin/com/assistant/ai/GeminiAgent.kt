package com.assistant.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Cloud AI Agent implementation using Google's Gemini API.
 */
class GeminiAgent(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String = "gemini-1.5-pro"
) : AIAgent {

    @Serializable
    private data class GeminiRequest(val contents: List<Content>)
    @Serializable
    private data class Content(val parts: List<Part>)
    @Serializable
    private data class Part(val text: String)

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        return try {
            val fullPrompt = if (context != null) {
                "Project Context (Jira Tickets):\n${context.tickets.joinToString("\n") { "[${it.id}] ${it.summary}: ${it.description}" }}\n\nAnalysis Request: $prompt"
            } else {
                prompt
            }

            val request = GeminiRequest(listOf(Content(listOf(Part(fullPrompt)))))
            
            val httpResponse = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (httpResponse.status.isSuccess()) {
                val responseBody = httpResponse.bodyAsText()
                val jsonElement = Json.parseToJsonElement(responseBody)
                // Extraction of text from the nested Gemini response structure
                val text = jsonElement.jsonObject["candidates"]?.jsonArray?.get(0)
                    ?.jsonObject?.get("content")?.jsonObject?.get("parts")
                    ?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                
                if (text != null) {
                    AIResult.Success(text)
                } else {
                    AIResult.Failure("Gemini Parse Error: Response structure not as expected")
                }
            } else {
                AIResult.Failure("Gemini HTTP Error: ${httpResponse.status}")
            }
        } catch (e: Exception) {
            AIResult.Failure("Gemini API Error: ${e.message}")
        }
    }

    override fun getAgentName(): String = "Cloud Google - $model"
}
