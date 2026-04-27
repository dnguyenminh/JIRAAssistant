package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ResolvedPaths
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Cross-platform utility to find Node.js and CLI JS entry point paths.
 * Supports Windows (Scoop, nvm-windows, APPDATA/npm) and Unix (nvm, npm-global, /usr/local/lib).
 */
class NodeCliPathResolver {

    private val logger = LoggerFactory.getLogger(NodeCliPathResolver::class.java)
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    /** Run a shell command and return trimmed stdout, or null on failure. */
    fun runCommand(vararg command: String): String? {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            logger.debug("Command failed: {} - {}", command.joinToString(" "), e.message)
            null
        }
    }

    /** Find executable path via OS command (where on Windows, which on Unix). */
    fun findExecutablePath(name: String): String? {
        val command = if (isWindows) arrayOf("cmd", "/c", "where", name)
        else arrayOf("which", name)
        return runCommand(*command)?.lines()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Find Node.js executable, falling back to "node" from PATH. */
    fun findNodePath(): String {
        val nodePath = findExecutablePath("node")
        if (nodePath != null) {
            logger.debug("Found Node.js via OS command: {}", nodePath)
            return nodePath
        }
        logger.debug("Node.js not found via OS command, using 'node' from PATH")
        return "node"
    }

    /** Find CLI JS entry point using script resolution, location inference, and fallback paths. */
    fun findCliJsPath(config: NodeCliConfig): String? {
        logger.debug("Finding {} CLI JS path...", config.commandName)
        val scriptPath = findExecutablePath(config.commandName)
        if (scriptPath != null) {
            logger.debug("Found {} script at: {}", config.commandName, scriptPath)
            extractJsPathFromScript(scriptPath, config)?.let { return it }
            inferJsPathFromScriptLocation(scriptPath, config)?.let { return it }
        }
        return findFallbackPath(config)
    }

    /** Resolve both Node path and CLI JS path. */
    fun resolve(config: NodeCliConfig): ResolvedPaths {
        return ResolvedPaths(nodePath = findNodePath(), jsPath = findCliJsPath(config))
    }

    /** Read script wrapper content and extract JS path via regex patterns. */
    internal fun extractJsPathFromScript(scriptPath: String, config: NodeCliConfig): String? {
        return try {
            val scriptFile = File(scriptPath)
            if (!scriptFile.exists()) return null
            val content = scriptFile.readText()
            val scriptDir = scriptFile.parentFile?.absolutePath ?: return null
            val patterns = config.jsPathPatterns.ifEmpty { buildDefaultPatterns(config) }
            matchJsPath(patterns, content, scriptDir)
        } catch (e: Exception) {
            logger.debug("Failed to extract JS path from script: {}", e.message)
            null
        }
    }

    private fun matchJsPath(patterns: List<Regex>, content: String, scriptDir: String): String? {
        for (pattern in patterns) {
            val match = pattern.find(content) ?: continue
            var path = match.groupValues.getOrNull(1) ?: match.value
            if (path.startsWith("node_modules")) {
                path = File(scriptDir, path).absolutePath
            }
            path = path.replace("\\", "/")
            if (File(path).exists()) return path
        }
        return null
    }

    private fun buildDefaultPatterns(config: NodeCliConfig): List<Regex> {
        val jsFileName = config.jsEntryPath.substringAfterLast("/")
        val escapedPkg = config.npmPackage.replace("/", "[/\\\\]+")
        return listOf(
            Regex("""["']?\${'$'}basedir[/\\]*(node_modules[/\\@a-zA-Z0-9_\-\.]+$jsFileName)["']?"""),
            Regex("""["']?%~dp0[/\\]*(node_modules[/\\@a-zA-Z0-9_\-\.]+$jsFileName)["']?"""),
            Regex("""["']?([A-Za-z]:[/\\][^"'\s]+$jsFileName)["']?"""),
            Regex("""["']?(/[^"'\s]+$jsFileName)["']?"""),
            Regex("""(node_modules[/\\]+$escapedPkg[/\\]+[^"'\s]+$jsFileName)""")
        )
    }

    private fun inferJsPathFromScriptLocation(scriptPath: String, config: NodeCliConfig): String? {
        return try {
            val scriptDir = File(scriptPath).parentFile ?: return null
            val relPaths = listOf(
                "node_modules/${config.npmPackage}/${config.jsEntryPath}",
                "../lib/node_modules/${config.npmPackage}/${config.jsEntryPath}",
                "../node_modules/${config.npmPackage}/${config.jsEntryPath}"
            )
            for (rel in relPaths) {
                val full = File(scriptDir, rel).canonicalPath
                if (File(full).exists()) {
                    logger.debug("Inferred JS path from script location: {}", full)
                    return full
                }
            }
            null
        } catch (e: Exception) {
            logger.debug("Failed to infer JS path: {}", e.message)
            null
        }
    }

    private fun findFallbackPath(config: NodeCliConfig): String? {
        val fullJsPath = "${config.npmPackage}/${config.jsEntryPath}"
        val paths = buildFallbackPaths(fullJsPath)
        for (path in paths) {
            if (File(path).exists()) {
                logger.debug("Found {} CLI at fallback path: {}", config.commandName, path)
                return path
            }
        }
        return null
    }

    private fun buildFallbackPaths(fullJsPath: String): List<String> {
        val paths = mutableListOf<String>()
        if (isWindows) {
            addWindowsFallbackPaths(paths, fullJsPath)
        } else {
            addUnixFallbackPaths(paths, fullJsPath)
        }
        return paths.map { it.replace("\\", "/") }
    }

    private fun addWindowsFallbackPaths(paths: MutableList<String>, fullJsPath: String) {
        System.getenv("APPDATA")?.let { appdata ->
            paths.add("$appdata/npm/node_modules/$fullJsPath")
        }
        System.getenv("USERPROFILE")?.let { userProfile ->
            paths.add("$userProfile/scoop/apps/nodejs/current/bin/node_modules/$fullJsPath")
            paths.add("$userProfile/scoop/apps/nodejs-lts/current/bin/node_modules/$fullJsPath")
            paths.add("$userProfile/AppData/Roaming/nvm/current/node_modules/$fullJsPath")
        }
        paths.add("C:/Program Files/nodejs/node_modules/$fullJsPath")
    }

    private fun addUnixFallbackPaths(paths: MutableList<String>, fullJsPath: String) {
        System.getenv("HOME")?.let { home ->
            paths.add("$home/.nvm/versions/node/current/lib/node_modules/$fullJsPath")
            paths.add("$home/.npm-global/lib/node_modules/$fullJsPath")
            paths.add("$home/node_modules/$fullJsPath")
        }
        paths.add("/usr/local/lib/node_modules/$fullJsPath")
        paths.add("/usr/lib/node_modules/$fullJsPath")
    }
}
