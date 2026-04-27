package com.assistant.server.mcp

import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.handlers.errorResponse
import com.assistant.server.mcp.internal.handlers.missingField
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 11: Business error handling
 *
 * *For any* tool call gây ra lỗi nghiệp vụ (not found, conflict,
 * invalid state), response SHALL có `isError: true` với message mô tả
 * cụ thể lỗi. Không có exception nào propagate ra ngoài tool executor.
 *
 * **Validates: Requirements AC 6.110**
 *
 * Feature: mcp-servers, Property 11: Business error handling
 */
@OptIn(ExperimentalKotest::class)
class InternalBusinessErrorPropertyTest {

    private val arbMessage: Arb<String> =
        Arb.string(1..100, Codepoint.az())

    private val arbFieldName: Arb<String> =
        Arb.string(1..30, Codepoint.az())

    /**
     * Property 11a — errorResponse always has isError=true.
     * For any error message, errorResponse() produces a response
     * with isError=true and content containing the message.
     */
    @Test
    fun `Property 11a - errorResponse always sets isError true`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbMessage) { msg ->
                val response = errorResponse(msg)
                assertTrue(response.isError, "errorResponse must have isError=true")
                assertContent(response, msg)
            }
        }
    }

    /**
     * Property 11b — missingField always has isError=true with field name.
     * For any field name, missingField() produces isError=true response
     * whose message contains the field name.
     */
    @Test
    fun `Property 11b - missingField always sets isError true with field name`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbFieldName) { field ->
                val response = missingField(field)
                assertTrue(response.isError, "missingField must have isError=true")
                val text = response.content.firstOrNull()?.text ?: ""
                assertTrue(
                    text.contains(field),
                    "missingField message must contain '$field', got: $text"
                )
            }
        }
    }

    /**
     * Property 11c — Business error responses never have empty content.
     * For any error message, the response content list is non-empty
     * and the first text entry is non-blank.
     */
    @Test
    fun `Property 11c - business error responses have non-empty content`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbMessage) { msg ->
                val response = errorResponse(msg)
                assertTrue(
                    response.content.isNotEmpty(),
                    "Error response content must not be empty"
                )
                val text = response.content.first().text
                assertFalse(
                    text.isNullOrBlank(),
                    "Error response text must not be blank"
                )
            }
        }
    }

    /**
     * Property 11d — errorResponse content type is always "text".
     * Business errors use text content, never image or resource.
     */
    @Test
    fun `Property 11d - errorResponse content type is text`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbMessage) { msg ->
                val response = errorResponse(msg)
                response.content.forEach { content ->
                    assertEquals(
                        "text", content.type,
                        "Error content type must be 'text'"
                    )
                }
            }
        }
    }

    /** Assert response has exactly one text content matching msg. */
    private fun assertContent(response: McpToolCallResponse, msg: String) {
        assertEquals(1, response.content.size, "Expected exactly 1 content")
        assertEquals("text", response.content[0].type)
        assertEquals(msg, response.content[0].text)
    }
}
