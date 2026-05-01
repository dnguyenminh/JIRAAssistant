package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import org.slf4j.LoggerFactory

/**
 * Kiro CLI Client Implementation (Placeholder).
 *
 * Uses @amazon/kiro-cli to interact with Kiro AI.
 * Note: Kiro CLI may not be publicly available yet.
 * This is a placeholder implementation.
 */
open class KiroCliClientImpl : BaseNodeCliClient() {

    private val log = LoggerFactory.getLogger(KiroCliClientImpl::class.java)

    override val type: AiCliType = AiCliType.KIRO

    override val displayName: String = "Kiro CLI"

    override val cliConfig: NodeCliConfig = NodeCliConfig(
        commandName = "kiro-cli",
        npmPackage = "@amazon/kiro-cli",
        jsEntryPath = "dist/cli.js"
    )

    override val cliJsPath: String by lazy {
        pathResolver.findCliJsPath(cliConfig)
            ?: throw RuntimeException(
                "Cannot find Kiro CLI. " +
                    "Kiro CLI may not be publicly available yet. " +
                    "If you have access: npm install -g @amazon/kiro-cli"
            )
    }

    /**
     * Stateless args: --prompt "" --output-format json
     */
    override fun buildCommandArgs(prompt: String): List<String> {
        return listOf("--prompt", "", "--output-format", "json")
    }

    /**
     * Persistent args: --prompt "" --output-format stream-json
     * Add --resume latest for subsequent messages.
     */
    override fun buildPersistentCommandArgs(isResume: Boolean): List<String> {
        val args = mutableListOf(
            "--prompt", "", "--output-format", "stream-json"
        )
        if (isResume) {
            args.addAll(listOf("--resume", "latest"))
        }
        return args
    }

    override fun getInstallInstructions(): String = """
        Kiro CLI Installation:
        Note: Kiro CLI may not be publicly available yet.
        If you have access:
        1. Ensure Node.js is installed (v18+)
        2. Install globally: npm install -g @amazon/kiro-cli
        3. Authenticate: kiro auth login
    """.trimIndent()
}
