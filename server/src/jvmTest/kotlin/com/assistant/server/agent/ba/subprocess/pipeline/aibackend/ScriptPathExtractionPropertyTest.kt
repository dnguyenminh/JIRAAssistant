package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 2: Script path regex extraction

/**
 * Property 2: Script path regex extraction
 *
 * For any script file content containing a JS path matching the configured
 * regex patterns (with known npm package and JS entry path embedded),
 * extractJsPathFromScript() SHALL extract a non-null path string that ends
 * with the expected JS entry filename.
 *
 * **Validates: Requirements 2.2**
 */
class ScriptPathExtractionPropertyTest {

    @TempDir
    lateinit var tempDir: File

    private val resolver = NodeCliPathResolver()

    @Test
    fun `Property 2 - Unix basedir wrapper extracts JS path`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbNpmPackage(),
                arbJsEntryPath()
            ) { npmPackage, jsEntryPath ->
                val jsFileName = jsEntryPath.substringAfterLast("/")
                val config = buildConfig(npmPackage, jsEntryPath)
                val fullJsPath = createJsEntryFile(npmPackage, jsEntryPath)
                val scriptFile = createUnixScript(fullJsPath, jsFileName)

                val result = resolver.extractJsPathFromScript(
                    scriptFile.absolutePath, config
                )

                assertNotNull(result, "Should extract path from Unix script")
                assertTrue(
                    result.endsWith(jsFileName),
                    "Extracted path '$result' should end with '$jsFileName'"
                )
            }
        }
    }

    @Test
    fun `Property 2 - Windows dp0 wrapper extracts JS path`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbNpmPackage(),
                arbJsEntryPath()
            ) { npmPackage, jsEntryPath ->
                val jsFileName = jsEntryPath.substringAfterLast("/")
                val config = buildConfig(npmPackage, jsEntryPath)
                val fullJsPath = createJsEntryFile(npmPackage, jsEntryPath)
                val scriptFile = createWindowsScript(fullJsPath, jsFileName)

                val result = resolver.extractJsPathFromScript(
                    scriptFile.absolutePath, config
                )

                assertNotNull(result, "Should extract path from Windows script")
                assertTrue(
                    result.endsWith(jsFileName),
                    "Extracted path '$result' should end with '$jsFileName'"
                )
            }
        }
    }

    @Test
    fun `Property 2 - absolute path wrapper extracts JS path`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbNpmPackage(),
                arbJsEntryPath()
            ) { npmPackage, jsEntryPath ->
                val jsFileName = jsEntryPath.substringAfterLast("/")
                val config = buildConfig(npmPackage, jsEntryPath)
                val fullJsPath = createJsEntryFile(npmPackage, jsEntryPath)
                val scriptFile = createAbsolutePathScript(fullJsPath)

                val result = resolver.extractJsPathFromScript(
                    scriptFile.absolutePath, config
                )

                assertNotNull(result, "Should extract absolute JS path")
                assertTrue(
                    result.endsWith(jsFileName),
                    "Extracted path '$result' should end with '$jsFileName'"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbNpmPackage(): Arb<String> = arbitrary {
        val scope = Arb.string(3..8, Codepoint.alphanumeric()).bind()
        val name = Arb.string(3..8, Codepoint.alphanumeric()).bind()
        "@${scope.lowercase()}/${name.lowercase()}"
    }

    private fun arbJsEntryPath(): Arb<String> = arbitrary {
        val useSubdir = Arb.boolean().bind()
        val filename = Arb.string(3..8, Codepoint.alphanumeric())
            .bind().lowercase()
        if (useSubdir) {
            val dir = Arb.string(3..6, Codepoint.alphanumeric())
                .bind().lowercase()
            "$dir/$filename.js"
        } else {
            "$filename.js"
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildConfig(
        npmPackage: String,
        jsEntryPath: String
    ): NodeCliConfig = NodeCliConfig(
        commandName = npmPackage.substringAfterLast("/"),
        npmPackage = npmPackage,
        jsEntryPath = jsEntryPath
    )

    private var fileCounter = 0

    private fun createJsEntryFile(
        npmPackage: String,
        jsEntryPath: String
    ): String {
        val jsFile = File(
            tempDir,
            "node_modules/$npmPackage/$jsEntryPath"
        )
        jsFile.parentFile.mkdirs()
        jsFile.writeText("// entry point\n")
        return jsFile.absolutePath
    }

    private fun createUnixScript(
        fullJsPath: String,
        jsFileName: String
    ): File {
        val script = File(tempDir, "script_unix_${fileCounter++}.sh")
        val relPath = fullJsPath.removePrefix(
            tempDir.absolutePath + File.separator
        )
        script.writeText(
            """
            |#!/bin/sh
            |basedir=$(dirname "$(echo "$0")")
            |node "${'$'}basedir/$relPath" "$@"
            """.trimMargin()
        )
        return script
    }

    private fun createWindowsScript(
        fullJsPath: String,
        jsFileName: String
    ): File {
        val script = File(tempDir, "script_win_${fileCounter++}.cmd")
        val relPath = fullJsPath.removePrefix(
            tempDir.absolutePath + File.separator
        )
        script.writeText(
            """
            |@ECHO off
            |SETLOCAL
            |SET "NODE_EXE=node"
            |"%NODE_EXE%" "%~dp0\$relPath" %*
            """.trimMargin()
        )
        return script
    }

    private fun createAbsolutePathScript(
        fullJsPath: String
    ): File {
        val script = File(tempDir, "script_abs_${fileCounter++}.sh")
        val normalized = fullJsPath.replace("\\", "/")
        script.writeText(
            """
            |#!/bin/sh
            |exec node "$normalized" "$@"
            """.trimMargin()
        )
        return script
    }
}
