package com.assistant.ai

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Shared Koin module for AI Agents.
 */
val aiModule = module {
    // Provide common Ktor Client for all AI Agents
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000   // 2 min for LLM generation
                connectTimeoutMillis = 10_000    // 10s connect
                socketTimeoutMillis = 120_000    // 2 min socket
            }
        }
    }

    // Default to LOCAL agent for privacy (BA Skill guidance)
    single<AIAgent> { 
        OllamaAgent(get()) 
    }

    // Factory to switch between agents (Architect Skill guidance)
    factory<AIAgentFactory> { 
        AIAgentFactory(get()) 
    }
}

/**
 * Factory class to manage multiple AI providers.
 */
class AIAgentFactory(private val httpClient: HttpClient) {
    fun createOllama(model: String = "llama3") = OllamaAgent(httpClient, model)
    fun createGemini(apiKey: String, model: String = "gemini-1.5-pro") = GeminiAgent(httpClient, apiKey, model)
}
