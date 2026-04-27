package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Property 31: Markdown file validation — invalid files are skipped.
 *
 * For any skill file missing `## Purpose` or `## Procedure`, or any
 * rule file missing `## Purpose` or `## Categories`, the framework
 * skips the invalid file and loads only valid files — the count of
 * loaded files equals the count of valid files.
 *
 * **Validates: Requirements 15.4, 16.4**
 */
@OptIn(ExperimentalKotest::class)
class MarkdownValidationPropertyTest {

    private val cfg = PropTestConfig(iterations = 30)

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-31")
    fun `invalid skill files are skipped, valid ones loaded`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..4), Arb.int(1..4)) { validCount, invalidCount ->
                val base = Files.createTempDirectory(tempDir, "skillval")
                writeConfig(base)
                writeValidSkills(base, validCount)
                writeInvalidSkills(base, invalidCount, validCount)

                val loader = AgentHomeDirectoryLoader(base)
                loader.getSkills().size shouldBe validCount
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-31")
    fun `invalid rule files are skipped, valid ones loaded`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..4), Arb.int(1..4)) { validCount, invalidCount ->
                val base = Files.createTempDirectory(tempDir, "ruleval")
                writeConfig(base)
                writeValidRules(base, validCount)
                writeInvalidRules(base, invalidCount, validCount)

                val loader = AgentHomeDirectoryLoader(base)
                loader.getRules().size shouldBe validCount
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-31")
    fun `skill missing Purpose is skipped`() {
        val result = SkillParser.parse("bad.md", "## Procedure\nDo stuff")
        result shouldBe null
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-31")
    fun `rule missing Categories is skipped`() {
        val result = RuleParser.parse("bad.md", "## Purpose\nClassify things")
        result shouldBe null
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun writeConfig(base: Path) {
        val config = AgentHomeConfig(agentType = "test")
        val json = JsonConfig.instance.encodeToString(AgentHomeConfig.serializer(), config)
        Files.writeString(base.resolve("config.json"), json)
    }

    private fun writeValidSkills(base: Path, count: Int) {
        val dir = base.resolve(".agent/skills")
        Files.createDirectories(dir)
        repeat(count) { i ->
            val content = "## Purpose\nValid purpose $i\n\n## Procedure\nValid step $i"
            Files.writeString(dir.resolve("valid_skill_$i.md"), content)
        }
    }

    private fun writeInvalidSkills(base: Path, count: Int, offset: Int) {
        val dir = base.resolve(".agent/skills")
        Files.createDirectories(dir)
        repeat(count) { i ->
            // Missing ## Procedure section
            val content = "## Purpose\nInvalid skill ${offset + i}"
            Files.writeString(dir.resolve("invalid_skill_${offset + i}.md"), content)
        }
    }

    private fun writeValidRules(base: Path, count: Int) {
        val dir = base.resolve(".agent/rules")
        Files.createDirectories(dir)
        repeat(count) { i ->
            val content = "## Purpose\nValid rule $i\n\n## Categories\n- cat_$i"
            Files.writeString(dir.resolve("valid_rule_$i.md"), content)
        }
    }

    private fun writeInvalidRules(base: Path, count: Int, offset: Int) {
        val dir = base.resolve(".agent/rules")
        Files.createDirectories(dir)
        repeat(count) { i ->
            // Missing ## Categories section
            val content = "## Purpose\nInvalid rule ${offset + i}"
            Files.writeString(dir.resolve("invalid_rule_${offset + i}.md"), content)
        }
    }
}
