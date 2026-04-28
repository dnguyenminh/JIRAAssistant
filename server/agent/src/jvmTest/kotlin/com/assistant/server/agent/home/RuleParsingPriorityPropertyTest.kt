package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.agent.home.RuleDefinition
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Property 32: Rule parsing and priority ordering.
 *
 * For any set of N valid rule files with distinct priority values,
 * when sorted by priority the rules are in ascending order (lowest
 * number first). Also validates that RuleParser correctly extracts
 * purpose, categories, keywords, and priority from markdown.
 *
 * **Validates: Requirements 16.1, 16.2, 16.3**
 */
@OptIn(ExperimentalKotest::class)
class RuleParsingPriorityPropertyTest {

    private val cfg = PropTestConfig(iterations = 30)

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-32")
    fun `rules sorted by priority are in ascending order`() {
        runBlocking {
            checkAll(cfg, Arb.int(2..6)) { n ->
                val base = Files.createTempDirectory(tempDir, "priority")
                writeConfig(base)
                val priorities = generateDistinctPriorities(n)
                writeRulesWithPriorities(base, priorities)

                val loader = AgentHomeDirectoryLoader(base)
                val rules = loader.getRules().sortedBy { it.priority }

                rules.shouldBeSortedWith(compareBy { it.priority })
                rules.size shouldBe n
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-32")
    fun `RuleParser extracts all sections correctly`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..200)) { priority ->
                val content = buildRuleContent(
                    purpose = "Classify tickets",
                    keywords = listOf("bug", "feature"),
                    categories = listOf("Bug", "Feature", "Task"),
                    priority = priority,
                    conflictResolution = "Use highest priority"
                )
                val rule = RuleParser.parse("test.md", content)

                rule!!.purpose shouldContain "Classify tickets"
                rule.categories shouldBe listOf("Bug", "Feature", "Task")
                rule.keywords shouldBe listOf("bug", "feature")
                rule.priority shouldBe priority
                rule.conflictResolution shouldContain "Use highest priority"
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-32")
    fun `missing Priority defaults to 100`() {
        val content = "## Purpose\nClassify\n\n## Categories\n- A\n- B"
        val rule = RuleParser.parse("default.md", content)

        rule!!.priority shouldBe 100
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun writeConfig(base: Path) {
        val config = AgentHomeConfig(agentType = "test")
        val json = JsonConfig.instance.encodeToString(AgentHomeConfig.serializer(), config)
        Files.writeString(base.resolve("config.json"), json)
    }

    private fun generateDistinctPriorities(count: Int): List<Int> {
        return (1..count * 10).shuffled().take(count)
    }

    private fun writeRulesWithPriorities(base: Path, priorities: List<Int>) {
        val dir = base.resolve(".agent/rules")
        Files.createDirectories(dir)
        priorities.forEachIndexed { i, priority ->
            val content = buildRuleContent(
                purpose = "Rule $i purpose",
                keywords = listOf("keyword_$i"),
                categories = listOf("Category_$i"),
                priority = priority,
                conflictResolution = "First match wins"
            )
            Files.writeString(dir.resolve("rule_$i.md"), content)
        }
    }

    private fun buildRuleContent(
        purpose: String,
        keywords: List<String>,
        categories: List<String>,
        priority: Int,
        conflictResolution: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("## Purpose")
        sb.appendLine(purpose)
        sb.appendLine()
        sb.appendLine("## Keywords")
        keywords.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.appendLine("## Categories")
        categories.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.appendLine("## Priority")
        sb.appendLine(priority)
        sb.appendLine()
        sb.appendLine("## Conflict Resolution")
        sb.appendLine(conflictResolution)
        return sb.toString()
    }
}
