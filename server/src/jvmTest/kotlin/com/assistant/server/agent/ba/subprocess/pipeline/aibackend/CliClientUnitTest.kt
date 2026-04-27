package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CLI client implementations:
 * GeminiCliClientImpl, CopilotCliClientImpl, KiroCliClientImpl.
 *
 * Tests config values, command args, and response parsing.
 */
class CliClientUnitTest {

    // ── Gemini CLI Tests ────────────────────────────────────

    @Test
    fun `Gemini - cliConfig values match spec`() {
        val client = TestableGeminiClient()
        val config = client.exposeCliConfig()
        assertEquals("gemini", config.commandName)
        assertEquals("@google/gemini-cli", config.npmPackage)
        assertEquals("bundle/gemini.js", config.jsEntryPath)
    }

    @Test
    fun `Gemini - type and displayName`() {
        val client = GeminiCliClientImpl()
        assertEquals(AiCliType.GEMINI, client.type)
        assertEquals("Gemini CLI", client.displayName)
    }

    @Test
    fun `Gemini - buildCommandArgs returns stateless args`() {
        val client = TestableGeminiClient()
        val args = client.exposeBuildCommandArgs("test prompt")
        assertEquals(listOf("-p", "", "--output-format", "json", "--sandbox"), args)
    }

    @Test
    fun `Gemini - buildCommandArgs includes model when set`() {
        val client = TestableGeminiClientWithModel("gemini-2.5-flash")
        val args = client.exposeBuildCommandArgs("test prompt")
        assertEquals(
            listOf("-p", "", "--output-format", "json", "--sandbox", "-m", "gemini-2.5-flash"),
            args
        )
    }

    @Test
    fun `Gemini - buildPersistentCommandArgs without resume`() {
        val client = TestableGeminiClient()
        val args = client.exposeBuildPersistentCommandArgs(false)
        assertEquals(
            listOf("-p", "", "--output-format", "json", "--sandbox"),
            args
        )
    }

    @Test
    fun `Gemini - buildPersistentCommandArgs with resume`() {
        val client = TestableGeminiClient()
        val args = client.exposeBuildPersistentCommandArgs(true)
        assertEquals(
            listOf(
                "-p", "", "--output-format", "json", "--sandbox",
                "--resume", "latest"
            ),
            args
        )
    }

    // ── Copilot CLI Tests ───────────────────────────────────

    @Test
    fun `Copilot - cliConfig values match spec`() {
        val client = TestableCopilotClient()
        val config = client.exposeCliConfig()
        assertEquals("copilot", config.commandName)
        assertEquals("@github/copilot", config.npmPackage)
        assertEquals("index.js", config.jsEntryPath)
    }

    @Test
    fun `Copilot - type and displayName`() {
        val client = CopilotCliClientImpl()
        assertEquals(AiCliType.COPILOT, client.type)
        assertEquals("GitHub Copilot CLI", client.displayName)
    }

    @Test
    fun `Copilot - buildCommandArgs returns stateless args`() {
        val client = TestableCopilotClient()
        val args = client.exposeBuildCommandArgs("test prompt")
        assertEquals(listOf("-p", "", "-s", "--allow-all-tools"), args)
    }

    @Test
    fun `Copilot - buildPersistentCommandArgs includes continue`() {
        val client = TestableCopilotClient()
        val args = client.exposeBuildPersistentCommandArgs(true)
        assertTrue(
            args.contains("--continue"),
            "Persistent args with resume should include --continue"
        )
    }

    @Test
    fun `Copilot - buildPersistentCommandArgs without continue`() {
        val client = TestableCopilotClient()
        val args = client.exposeBuildPersistentCommandArgs(false)
        assertFalse(
            args.contains("--continue"),
            "Persistent args without resume should not have --continue"
        )
    }

    @Test
    fun `Copilot - parseResponse treats output as plain text`() {
        val client = TestableCopilotClient()
        val response = client.exposeParseResponse("Hello, plain text")
        assertEquals("Hello, plain text", response.response)
        assertNull(response.sessionId)
        assertNull(response.rawJson)
    }

    @Test
    fun `Copilot - parseResponse trims whitespace`() {
        val client = TestableCopilotClient()
        val response = client.exposeParseResponse("  trimmed output  ")
        assertEquals("trimmed output", response.response)
    }

    // ── Kiro CLI Tests ──────────────────────────────────────

    @Test
    fun `Kiro - cliConfig values match spec`() {
        val client = TestableKiroClient()
        val config = client.exposeCliConfig()
        assertEquals("kiro-cli", config.commandName)
        assertEquals("@amazon/kiro-cli", config.npmPackage)
        assertEquals("dist/cli.js", config.jsEntryPath)
    }

    @Test
    fun `Kiro - type and displayName`() {
        val client = KiroCliClientImpl()
        assertEquals(AiCliType.KIRO, client.type)
        assertEquals("Kiro CLI", client.displayName)
    }

    @Test
    fun `Kiro - buildCommandArgs returns placeholder args`() {
        val client = TestableKiroClient()
        val args = client.exposeBuildCommandArgs("test prompt")
        assertEquals(
            listOf("--prompt", "", "--output-format", "json"),
            args
        )
    }

    @Test
    fun `Kiro - buildPersistentCommandArgs with resume`() {
        val client = TestableKiroClient()
        val args = client.exposeBuildPersistentCommandArgs(true)
        assertTrue(
            args.contains("--resume"),
            "Persistent args with resume should include --resume"
        )
    }
}

// ── Test wrappers exposing protected methods ────────────

private class TestableGeminiClient : GeminiCliClientImpl() {
    override val cliJsPath = "/tmp/gemini-test.js"
    fun exposeCliConfig(): NodeCliConfig = cliConfig
    fun exposeBuildCommandArgs(p: String) = buildCommandArgs(p)
    fun exposeBuildPersistentCommandArgs(r: Boolean) =
        buildPersistentCommandArgs(r)
}

private class TestableGeminiClientWithModel(
    model: String
) : GeminiCliClientImpl(model) {
    override val cliJsPath = "/tmp/gemini-test.js"
    fun exposeBuildCommandArgs(p: String) = buildCommandArgs(p)
}

private class TestableCopilotClient : CopilotCliClientImpl() {
    override val cliJsPath = "/tmp/copilot-test.js"
    fun exposeCliConfig(): NodeCliConfig = cliConfig
    fun exposeBuildCommandArgs(p: String) = buildCommandArgs(p)
    fun exposeBuildPersistentCommandArgs(r: Boolean) =
        buildPersistentCommandArgs(r)
    fun exposeParseResponse(output: String): AiCliResponse =
        parseResponse(output)
}

private class TestableKiroClient : KiroCliClientImpl() {
    override val cliJsPath = "/tmp/kiro-test.js"
    fun exposeCliConfig(): NodeCliConfig = cliConfig
    fun exposeBuildCommandArgs(p: String) = buildCommandArgs(p)
    fun exposeBuildPersistentCommandArgs(r: Boolean) =
        buildPersistentCommandArgs(r)
}
