package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.SubprocessMessage
import com.assistant.server.agent.generators.subprocessMessage
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 26 — Message protocol round-trip.
 *
 * For any valid command string, formatting it with
 * [MessageProtocol.formatCommand] then parsing the formatted
 * message back with [MessageProtocol.parseStdoutLine] SHALL
 * recover the original command string.
 *
 * **Validates: Requirements 13.2**
 */
@OptIn(ExperimentalKotest::class)
@Tag("generic-agent-framework")
class MessageProtocolPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val safeStr =
        Arb.string(1..50, Codepoint.alphanumeric())

    /**
     * Property 26: formatCommand → parseStdoutLine round-trip.
     *
     * The first line of a formatted command is a JSON envelope.
     * Parsing that line should recover a SubprocessMessage with
     * type="command" and the original content.
     *
     * **Validates: Requirements 13.2**
     */
    @Test
    @Tag("Property-26")
    fun `formatCommand then parseStdoutLine recovers command`() {
        runBlocking {
            checkAll(cfg, safeStr) { command ->
                val formatted = MessageProtocol.formatCommand(command)
                val jsonLine = formatted.lines().first()
                val parsed = MessageProtocol.parseStdoutLine(jsonLine)
                parsed shouldNotBe null
                parsed!!.type shouldBe "command"
                parsed.content shouldBe command
            }
        }
    }

    /**
     * Property 26 (extended): formatToolResponse round-trip.
     *
     * Formatting a ToolCallResponse then parsing the JSON line
     * should recover the original response fields.
     *
     * **Validates: Requirements 13.2**
     */
    @Test
    @Tag("Property-26")
    fun `formatToolResponse then parseStdoutLine recovers response`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.toolCallResponseArb()
            ) { response ->
                val formatted =
                    MessageProtocol.formatToolResponse(response)
                val jsonLine = formatted.lines().first()
                val parsed =
                    MessageProtocol.parseStdoutLine(jsonLine)
                parsed shouldNotBe null
                parsed!!.type shouldBe "toolResult"
                parsed.toolResult shouldBe response
            }
        }
    }

    /**
     * Property 26 (extended): delimiter detection.
     *
     * The second line of any formatted message is the delimiter.
     *
     * **Validates: Requirements 13.2**
     */
    @Test
    @Tag("Property-26")
    fun `formatted messages end with delimiter`() {
        runBlocking {
            checkAll(cfg, safeStr) { command ->
                val formatted = MessageProtocol.formatCommand(command)
                val lines = formatted.lines()
                    .filter { it.isNotEmpty() }
                lines.last() shouldBe MessageProtocol.DELIMITER
                MessageProtocol.isDelimiter(lines.last()) shouldBe true
            }
        }
    }

    // ── Generators ──────────────────────────────────────

    private fun Arb.Companion.toolCallResponseArb() = arbitrary {
        com.assistant.agent.subprocess.ToolCallResponse(
            id = safeStr.bind(),
            success = Arb.boolean().bind(),
            data = safeStr.bind(),
            error = safeStr.bind()
        )
    }
}
