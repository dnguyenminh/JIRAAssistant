package com.assistant.server.agent.error

import com.assistant.agent.models.ErrorClassification
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

data class ClassifiedError(
    val error: Throwable,
    val expected: ErrorClassification
)

private val recoverableErrors = listOf(
    { TimeoutException("tool timed out") },
    { SocketTimeoutException("connection timeout") },
    { SocketException("network error") },
    { UnknownHostException("unknown host") },
    { RuntimeException("rate limit exceeded") },
    { RuntimeException("too many requests") },
    { RuntimeException("connection refused") }
)

private val unrecoverableErrors = listOf(
    { RuntimeException("unauthorized access") },
    { RuntimeException("forbidden resource") },
    { RuntimeException("auth token expired") },
    { RuntimeException("invalid config value") },
    { RuntimeException("invalid agent config") }
)

/** Generate errors with their expected classification. */
fun arbClassifiedError(): Arb<ClassifiedError> = arbitrary {
    val allErrors = recoverableErrors.map {
        ClassifiedError(it(), ErrorClassification.RECOVERABLE)
    } + unrecoverableErrors.map {
        ClassifiedError(it(), ErrorClassification.UNRECOVERABLE)
    }
    val idx = Arb.int(allErrors.indices).bind()
    allErrors[idx]
}
