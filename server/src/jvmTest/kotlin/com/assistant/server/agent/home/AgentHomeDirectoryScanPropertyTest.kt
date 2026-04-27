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
 * Property 28: Agent home directory scan and load.
 *
 * For any valid agent home directory containing N skill files, M rule
 * files, and K workflow files, after initialization the AgentHomeDirectory
 * reports exactly N skills, M rules, and K workflows (excluding files
 * that fail validation).
 *
 * **Validates: Requirements 14.1, 14.2**
 */
@OptIn(ExperimentalKotest::class)
class AgentHomeDirectoryScanPropertyTest {

    private val cfg = PropTestConfig(iterations = 30)

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-28")
    fun `scan loads correct count of skills rules and workflows`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..5), Arb.int(0..5), Arb.int(0..3)) { nSkills, nRules, nWorkflows ->
                val base = Files.createTempDirectory(tempDir, "home")
                writeConfig(base)
                val skillFiles = writeSkillFiles(base, nSkills)
                val ruleFiles = writeRuleFiles(base, nRules)
                writeWorkflowFiles(base, nWorkflows)

                val loader = AgentHomeDirectoryLoader(base)

                loader.getSkills().size shouldBe skillFiles
                loader.getRules().size shouldBe ruleFiles
                loader.getWorkflows().size shouldBe nWorkflows
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun writeConfig(base: Path) {
        val configFile = base.resolve("config.json")
        val config = AgentHomeConfig(agentType = "test")
        Files.writeString(configFile, JsonConfig.instance.encodeToString(AgentHomeConfig.serializer(), config))
    }

    private fun writeSkillFiles(base: Path, count: Int): Int {
        val dir = base.resolve(".agent/skills")
        Files.createDirectories(dir)
        repeat(count) { i ->
            val content = "## Purpose\nSkill $i purpose\n\n## Procedure\nStep $i"
            Files.writeString(dir.resolve("skill_$i.md"), content)
        }
        return count
    }

    private fun writeRuleFiles(base: Path, count: Int): Int {
        val dir = base.resolve(".agent/rules")
        Files.createDirectories(dir)
        repeat(count) { i ->
            val content = "## Purpose\nRule $i purpose\n\n## Categories\n- cat_$i"
            Files.writeString(dir.resolve("rule_$i.md"), content)
        }
        return count
    }

    private fun writeWorkflowFiles(base: Path, count: Int) {
        val dir = base.resolve(".agent/workflows")
        Files.createDirectories(dir)
        repeat(count) { i ->
            val content = "# Workflow $i\nDescription of workflow $i\n\n## Steps\n- Step 1"
            Files.writeString(dir.resolve("workflow_$i.md"), content)
        }
    }
}
