package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.string.shouldContain
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
 * Property 29: Skill parsing and prompt composition.
 *
 * For any set of N valid skill files (each containing `## Purpose` and
 * `## Procedure` sections), the combined system prompt produced by
 * `buildSystemPrompt()` contains the purpose and procedure content
 * from all N skill files.
 *
 * **Validates: Requirements 15.1, 15.2**
 */
@OptIn(ExperimentalKotest::class)
class SkillParsingPromptPropertyTest {

    private val cfg = PropTestConfig(iterations = 30)

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-29")
    fun `buildSystemPrompt contains all skill purposes and procedures`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { n ->
                val base = Files.createTempDirectory(tempDir, "prompt")
                writeConfig(base)
                val skills = writeSkills(base, n)

                val loader = AgentHomeDirectoryLoader(base)
                val prompt = loader.buildSystemPrompt()

                for ((purpose, procedure) in skills) {
                    prompt shouldContain purpose
                    prompt shouldContain procedure
                }
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-29")
    fun `parsed skill has correct purpose and procedure`() {
        val content = buildSkillContent("Analyze data", "1. Collect\n2. Process")
        val skill = SkillParser.parse("test.md", content)

        skill!!.purpose shouldContain "Analyze data"
        skill.procedure shouldContain "1. Collect"
        skill.procedure shouldContain "2. Process"
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun writeConfig(base: Path) {
        val cfg = AgentHomeConfig(agentType = "test")
        val json = JsonConfig.instance.encodeToString(AgentHomeConfig.serializer(), cfg)
        Files.writeString(base.resolve("config.json"), json)
    }

    private fun writeSkills(base: Path, count: Int): List<Pair<String, String>> {
        val dir = base.resolve(".agent/skills")
        Files.createDirectories(dir)
        return (0 until count).map { i ->
            val purpose = "SkillPurpose_$i"
            val procedure = "SkillProcedure_$i"
            val content = buildSkillContent(purpose, procedure)
            Files.writeString(dir.resolve("skill_$i.md"), content)
            purpose to procedure
        }
    }

    private fun buildSkillContent(purpose: String, procedure: String): String {
        return "## Purpose\n$purpose\n\n## Procedure\n$procedure"
    }
}
