package com.assistant.server.agent.error

import com.assistant.agent.models.ErrorClassification
import com.assistant.agent.models.ErrorStrategy
import com.assistant.agent.models.RetryConfig
import com.assistant.agent.models.ToolResult
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Property-based tests for ErrorHandler (Properties 18, 19).
 */
@OptIn(ExperimentalKotest::class)
class ErrorHandlerPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val handler = ErrorHandler()

    /**
     * Property 18: RETRY invocation count.
     *
     * For a tool with RETRY and maxRetries=N that always fails,
     * the tool is invoked exactly N+1 times (1 original + N retries).
     *
     * **Validates: Requirements 9.4**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-18")
    fun `RETRY invokes action exactly maxRetries times`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { maxRetries ->
                var invocations = 0
                val config = RetryConfig(
                    maxRetries = maxRetries, delayMs = 1
                )
                val failAction: suspend () -> ToolResult = {
                    invocations++
                    ToolResult(
                        toolName = "fail", success = false,
                        errorType = "TEST"
                    )
                }
                handler.handleError(
                    error = RuntimeException("test"),
                    toolStrategy = ErrorStrategy.RETRY,
                    phaseStrategy = ErrorStrategy.SKIP,
                    retryConfig = config,
                    retryAction = failAction,
                    fallbackAction = null
                )
                // handleError retries maxRetries times
                // (original attempt is outside handleError)
                invocations shouldBe maxRetries
            }
        }
    }

    /**
     * Property 19: Error classification correctness.
     *
     * Tool timeouts, network errors, rate limits → RECOVERABLE.
     * Auth failures, invalid config → UNRECOVERABLE.
     *
     * **Validates: Requirements 9.7**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-19")
    fun `errors are classified correctly`() {
        runBlocking {
            checkAll(cfg, arbClassifiedError()) { (error, expected) ->
                handler.classifyError(error) shouldBe expected
            }
        }
    }
}
