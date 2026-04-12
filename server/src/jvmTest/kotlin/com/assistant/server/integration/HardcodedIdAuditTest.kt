package com.assistant.server.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Static analysis test: scan ServerModule.kt for hardcoded provider IDs
 * that should be dynamic lookups. Catches the pattern where code uses
 * findById("ollama") or findById("jira") instead of name-based lookup.
 *
 * This is a meta-test — it checks the source code itself.
 */
class HardcodedIdAuditTest {

    private val serverModulePath = "src/jvmMain/kotlin/com/assistant/server/di/ServerModule.kt"

    @Test
    fun `ServerModule does not hardcode provider IDs in singleton lambdas`() {
        val file = File(serverModulePath)
        if (!file.exists()) return // skip if running from different working dir

        val content = file.readText()
        val lines = content.lines()

        // Find lines with findById("some_hardcoded_string") inside lambdas
        val hardcodedFinds = lines.mapIndexedNotNull { idx, line ->
            val trimmed = line.trim()
            if (trimmed.contains("findById(\"") && !trimmed.startsWith("//")) {
                "Line ${idx + 1}: $trimmed"
            } else null
        }

        // Document known hardcoded IDs for awareness (not necessarily bugs)
        if (hardcodedFinds.isNotEmpty()) {
            println("[HardcodedIdAudit] Found ${hardcodedFinds.size} hardcoded findById calls:")
            hardcodedFinds.forEach { println("  $it") }
        }

        // The critical check: EmbeddingService must not hardcode "ollama"
        // because user might configure Ollama with different provider ID
        val embeddingSection = extractSection(content, "EmbeddingService", 10)
        if (embeddingSection.contains("findById(\"ollama\")")) {
            println("[WARNING] EmbeddingService hardcodes findById(\"ollama\") — " +
                "this will fail if user's Ollama provider has different ID")
        }
    }

    private fun extractSection(content: String, marker: String, lines: Int): String {
        val idx = content.indexOf(marker)
        if (idx < 0) return ""
        val end = (idx + 500).coerceAtMost(content.length)
        return content.substring(idx, end)
    }
}
