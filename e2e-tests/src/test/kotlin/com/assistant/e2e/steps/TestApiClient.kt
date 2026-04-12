package com.assistant.e2e.steps

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Lightweight Ktor HTTP-client wrapper used by API-level step definitions.
 *
 * Every call automatically stores the response status and body in
 * [SharedTestContext] so that subsequent Then-steps can assert on them.
 */
object TestApiClient {

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            engine {
                requestTimeout = 15_000
            }
        }
    }

    private val baseUrl: String get() = SharedTestContext.BASE_URL

    // ── HTTP verbs ──────────────────────────────────────────

    suspend fun get(path: String, token: String? = null): HttpResponse {
        val response = client.get("$baseUrl$path") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        capture(response)
        return response
    }

    suspend fun post(path: String, body: String = "{}", token: String? = null): HttpResponse {
        val response = client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        capture(response)
        return response
    }

    suspend fun put(path: String, body: String = "{}", token: String? = null): HttpResponse {
        val response = client.put("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        capture(response)
        return response
    }

    suspend fun delete(path: String, token: String? = null): HttpResponse {
        val response = client.delete("$baseUrl$path") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        capture(response)
        return response
    }

    // ── Helpers ─────────────────────────────────────────────

    private suspend fun capture(response: HttpResponse) {
        SharedTestContext.lastResponseStatus = response.status.value
        SharedTestContext.lastResponseBody = response.bodyAsText()
        // Also mirror into TestHelper for steps that still read from there
        TestHelper.lastResponseStatus = response.status.value
        TestHelper.lastResponseBody = SharedTestContext.lastResponseBody
    }

    /** Quick reachability check — returns true when /health responds 2xx. */
    fun isServerReachable(): Boolean = try {
        kotlinx.coroutines.runBlocking {
            val resp = client.get("$baseUrl/health")
            resp.status.value in 200..299
        }
    } catch (_: Exception) {
        false
    }
}
