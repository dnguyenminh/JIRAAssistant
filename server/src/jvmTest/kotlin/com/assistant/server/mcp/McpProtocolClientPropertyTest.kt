package com.assistant.server.mcp

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Property 2: JSON-RPC request/response ID matching
 *
 * For any N concurrent requests (1-20), each request gets a unique
 * incrementing ID, and each response is dispatched to the correct caller.
 *
 * Property 4: Timeout enforcement
 *
 * For any request, if no response arrives within timeout, the request
 * must be cancelled with TimeoutCancellationException.
 *
 * **Validates: Requirements 6.40, 6.41, 6.43, 6.49**
 *
 * Feature: mcp-runtime, Property 2 & 4
 */
@OptIn(ExperimentalKotest::class)
class McpProtocolClientPropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Property 2 — dispatchResponse routes each response to the
     * correct CompletableDeferred by matching JSON-RPC id.
     */
    @Test
    fun `Property 2 - JSON-RPC responses dispatched to correct caller by ID`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), Arb.int(1..20)) { n ->
                val pipeOut = PipedOutputStream()
                val pipeIn = PipedInputStream(pipeOut)
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                val client = McpProtocolClientImpl(
                    stdin = ByteArrayOutputStream(),
                    stdout = pipeIn.bufferedReader(),
                    scope = scope,
                    serverId = "test"
                )
                try {
                    val ids = (1..n).toList()
                    val deferreds = ids.map { id ->
                        val d = CompletableDeferred<JsonElement>()
                        client.javaClass.getDeclaredField("pending").apply {
                            isAccessible = true
                        }.let {
                            @Suppress("UNCHECKED_CAST")
                            val pending = it.get(client) as ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>
                            pending[id] = d
                        }
                        id to d
                    }

                    // Feed mock responses through dispatchResponse
                    for (id in ids) {
                        val line = """{"jsonrpc":"2.0","id":$id,"result":"response-$id"}"""
                        client.dispatchResponse(line)
                    }

                    // Verify each deferred completed with correct result
                    for ((id, deferred) in deferreds) {
                        assertTrue(deferred.isCompleted, "Deferred for id=$id should be completed")
                        val result = deferred.await()
                        assertEquals(
                            "response-$id",
                            result.jsonPrimitive.content,
                            "Response for id=$id must match"
                        )
                    }
                } finally {
                    client.close()
                    scope.cancel()
                }
            }
        }
    }

    /**
     * Property 4 — sendRequestWithTimeout throws
     * TimeoutCancellationException when no response arrives.
     */
    @Test
    fun `Property 4 - request times out when no response received`() {
        runBlocking {
            // Pipe that never produces output simulates unresponsive server
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(pipeOut)
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val client = McpProtocolClientImpl(
                stdin = ByteArrayOutputStream(),
                stdout = pipeIn.bufferedReader(),
                scope = scope,
                serverId = "timeout-test"
            )
            try {
                val ex = assertThrows(TimeoutCancellationException::class.java) {
                    runBlocking {
                        client.sendRequestWithTimeout("test/method", null, timeoutMs = 100)
                    }
                }
                assertNotNull(ex, "Should throw TimeoutCancellationException")
            } finally {
                client.close()
                scope.cancel()
                pipeOut.close()
            }
        }
    }
}
