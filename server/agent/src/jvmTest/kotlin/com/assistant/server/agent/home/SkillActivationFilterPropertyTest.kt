package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Property 30: Skill activation filtering.
 *
 * For any agent home directory with N skill files and an `activeSkills`
 * config listing a subset S (where S ⊆ N), `getActiveSkills()` returns
 * exactly |S| skills matching the listed file names. When `activeSkills`
 * is empty, `getActiveSkills()` returns all N skills.
 *
 * **Validates: Requirements 15.3**
 */
@OptIn(ExperimentalKotest::class)
class SkillActivationFilterPropertyTest {

    private val cfg = PropTestConfig(iterations = 30)

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-30")
    fun `getActiveSkills returns subset when activeSkills is set`() {
        runBlocking {
            checkAll(cfg, Arb.int(2..6)) { totalSkills ->
                val base = Files.createTempDirectory(tempDir, "filter")
                val allNames = writeAllSkills(base, totalSkills)

                // Pick a random subset (1..totalSkills-1)
                val subsetSize = (1 until totalSkills).random()
                val activeNames = allNames.shuffled().take(subsetSize)

                writeConfig(base, activeNames)
                val loader = AgentHomeDirectoryLoader(base)

                val activeSkills = loader.getActiveSkills()
                activeSkills.size shouldBe subsetSize
                activeSkills.map { it.fileName } shouldContainExactlyInAnyOrder activeNames
            }
        }
    }

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-30")
    fun `getActiveSkills returns all when activeSkills is empty`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { totalSkills ->
                val base = Files.createTempDirectory(tempDir, "all")
                val allNames = writeAllSkills(base, totalSkills)

                writeConfig(base, emptyList())
                val loader = AgentHomeDirectoryLoader(base)

                val activeSkills = loader.getActiveSkills()
                activeSkills.size shouldBe totalSkills
                activeSkills.map { it.fileName } shouldContainExactlyInAnyOrder allNames
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun writeAllSkills(base: Path, count: Int): List<String> {
        val dir = base.resolve(".agent/skills")
        Files.createDirectories(dir)
        return (0 until count).map { i ->
            val name = "skill_$i.md"
            val content = "## Purpose\nPurpose $i\n\n## Procedure\nStep $i"
            Files.writeString(dir.resolve(name), content)
            name
        }
    }

    private fun writeConfig(base: Path, activeSkills: List<String>) {
        val config = AgentHomeConfig(agentType = "test", activeSkills = activeSkills)
        val json = JsonConfig.instance.encodeToString(AgentHomeConfig.serializer(), config)
        Files.writeString(base.resolve("config.json"), json)
    }
}
