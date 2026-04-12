package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 1: JiraAttachment serialization round-trip
 * For any JiraAttachment (including nullable content field),
 * serialize → deserialize must produce an identical object.
 * Validates: Requirements 22.1
 */
@OptIn(ExperimentalKotest::class)
class JiraAttachmentSerializationPropertyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val arbAttachment: Arb<JiraAttachment> = Arb.bind(
        Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric()),
        Arb.string(minSize = 3, maxSize = 20, codepoints = Codepoint.alphanumeric()),
        Arb.long(0L..100_000_000L),
        Arb.string(minSize = 0, maxSize = 200).orNull(0.3)
    ) { id, filename, size, content ->
        val ext = listOf("pdf", "docx", "txt", "png", "xlsx").random()
        JiraAttachment(
            id = id, filename = "$filename.$ext",
            mimeType = "application/octet-stream", size = size,
            content = content
        )
    }

    @Test
    fun `serialization round-trip preserves all fields including content`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 200), arbAttachment) { original ->
                val encoded = json.encodeToString(original)
                val decoded = json.decodeFromString<JiraAttachment>(encoded)
                assertEquals(original.id, decoded.id, "id mismatch")
                assertEquals(original.filename, decoded.filename, "filename mismatch")
                assertEquals(original.mimeType, decoded.mimeType, "mimeType mismatch")
                assertEquals(original.size, decoded.size, "size mismatch")
                assertEquals(original.content, decoded.content, "content field must survive round-trip")
            }
        }
    }

    @Test
    fun `null content field preserved after round-trip`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25),
                Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric())
            ) { id ->
                val att = JiraAttachment(id = id, filename = "test.pdf", size = 100, content = null)
                val encoded = json.encodeToString(att)
                val decoded = json.decodeFromString<JiraAttachment>(encoded)
                assertNull(decoded.content, "null content must remain null")
                assertEquals(att.id, decoded.id)
            }
        }
    }
}
