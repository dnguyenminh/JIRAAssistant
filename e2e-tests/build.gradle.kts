import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

// Load .env file from project root if it exists
val dotEnv = mutableMapOf<String, String>()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val (key, value) = trimmed.split("=", limit = 2)
            dotEnv[key.trim()] = value.trim()
        }
    }
}
fun envOrDot(key: String): String? = System.getenv(key) ?: dotEnv[key]

plugins {
    id("java-library")
    kotlin("jvm")
    id("net.serenity-bdd.serenity-gradle-plugin") version "5.3.9"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.serenity.core)
    testImplementation(libs.serenity.cucumber)
    testImplementation(libs.serenity.screenplay)
    testImplementation(libs.serenity.screenplay.webdriver)
    testImplementation(libs.serenity.junit5)
    testImplementation(libs.junit)
    testImplementation("org.slf4j:slf4j-simple:2.0.13")

    // JNA for Selenium WebDriver on Windows (native process management)
    testImplementation("net.java.dev.jna:jna-platform:5.16.0")

    // JUnit 5 Cucumber Platform Engine — replaces deprecated CucumberWithSerenity (JUnit 4)
    testImplementation("io.cucumber:cucumber-junit-platform-engine:${libs.versions.cucumber.get()}")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.4")

    // Ktor HTTP client for API-only tests
    testImplementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    testImplementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor.get()}")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor.get()}")
}

// Force kotlin-stdlib to match the project Kotlin version.
// Serenity 5.x transitively pulls kotlin-stdlib 2.3.0 which is
// incompatible with the Kotlin 2.0.0 compiler used by this project.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}")
        force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    }
}

// ── Server lifecycle for E2E tests ──────────────────────────

val serverFatJar = tasks.getByPath(":server:fatJar")

fun findFreePort(): Int {
    val socket = ServerSocket(0)
    val port = socket.localPort
    socket.close()
    return port
}

val serverPort = (project.findProperty("test.server.port") as? String)?.toIntOrNull() ?: findFreePort()
var serverProcess: Process? = null

val startServer by tasks.registering {
    dependsOn(serverFatJar)
    dependsOn(":frontend:jsBrowserDevelopmentWebpack")
    doLast {
        val fatJar = project(":server").layout.buildDirectory
            .file("libs/jira-assistant-server-all.jar").get().asFile

        if (!fatJar.exists()) {
            throw GradleException("Server fat JAR not found at ${fatJar.absolutePath}")
        }

        logger.lifecycle("Starting server on port $serverPort ...")

        val dbDir = layout.buildDirectory.dir("e2e-db").get().asFile
        dbDir.mkdirs()

        // Assemble static dir from frontend resources + webpack JS bundle
        val staticDir = layout.buildDirectory.dir("e2e-static").get().asFile
        staticDir.mkdirs()

        // Copy frontend root files (index.html, CSS, sub-pages, JS helpers)
        val frontendRoot = project.rootProject.file("frontend")
        val frontendResources = project.rootProject.file("frontend/src/jsMain/resources")
        val frontendJs = project.rootProject.file("frontend/build/kotlin-webpack/js/developmentExecutable")

        // Copy the main index.html
        val indexHtml = File(frontendRoot, "index.html")
        if (indexHtml.exists()) {
            indexHtml.copyTo(File(staticDir, "index.html"), overwrite = true)
        }
        // Copy Kotlin/JS resource files (CSS, templates)
        if (frontendResources.exists()) {
            frontendResources.copyRecursively(staticDir, overwrite = true)
        }
        // Copy webpack JS bundle to static root (matching index.html <script src="/frontend.js">)
        if (frontendJs.exists()) {
            frontendJs.listFiles()?.forEach { f ->
                f.copyTo(File(staticDir, f.name), overwrite = true)
            }
        }

        val pb = ProcessBuilder(
            "java", "-jar", fatJar.absolutePath
        ).apply {
            environment()["PORT"] = serverPort.toString()
            environment()["JWT_SECRET"] = "e2e-test-jwt-secret-for-testing-only"
            environment()["ENCRYPTION_KEY"] = "e2e-test-encryption-key-32chars!"
            environment()["STATIC_DIR"] = staticDir.absolutePath
            // Pass .env values to server process
            envOrDot("JIRA_HOST")?.let { environment()["JIRA_HOST"] = it }
            envOrDot("JIRA_API_EMAIL")?.let { environment()["JIRA_API_EMAIL"] = it }
            envOrDot("JIRA_API_TOKEN")?.let { environment()["JIRA_API_TOKEN"] = it }
            envOrDot("GEMINI_API_KEY")?.let { environment()["GEMINI_API_KEY"] = it }
            envOrDot("OLLAMA_ENDPOINT")?.let { environment()["OLLAMA_ENDPOINT"] = it }
            // PostgreSQL connection
            envOrDot("DATABASE_URL")?.let { environment()["DATABASE_URL"] = it }
            envOrDot("DATABASE_USER")?.let { environment()["DATABASE_USER"] = it }
            envOrDot("DATABASE_PASSWORD")?.let { environment()["DATABASE_PASSWORD"] = it }
            envOrDot("DATABASE_POOL_SIZE")?.let { environment()["DATABASE_POOL_SIZE"] = it }
            redirectErrorStream(true)
        }

        serverProcess = pb.start()

        // Stream server output in background
        Thread {
            serverProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                logger.info("[server] $line")
            }
        }.apply { isDaemon = true }.start()

        // Poll /health until ready (max 30s)
        val deadline = System.currentTimeMillis() + 30_000L
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL("http://localhost:$serverPort/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                if (conn.responseCode in 200..299) { ready = true; break }
            } catch (_: Exception) { /* not ready */ }
            Thread.sleep(500)
        }

        if (!ready) {
            serverProcess?.destroyForcibly()
            throw GradleException("Server failed to start on port $serverPort within 30s")
        }
        logger.lifecycle("Server ready on port $serverPort")
    }
}

val stopServer by tasks.registering {
    doLast {
        serverProcess?.let { proc ->
            logger.lifecycle("Stopping server ...")
            proc.destroyForcibly()
            proc.waitFor(5, TimeUnit.SECONDS)
            logger.lifecycle("Server stopped.")
        }
        serverProcess = null
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true

    // Parallel execution: use multiple JVM forks (22 cores → 8 forks)
    // Each fork runs a subset of test classes concurrently
    // API tests: lightweight HTTP calls, safe to parallelize heavily
    // UI tests: each opens Chrome, ~300MB RAM each, limit forks

    // systemProperty("serenity.batch.strategy", "DIVIDE_BY_TEST_COUNT")
    maxParallelForks = Runtime.getRuntime().availableProcessors() // Sử dụng tối đa số nhân CPU

    systemProperty("cucumber.execution.parallel.enabled", "true") 
    systemProperty("cucumber.execution.parallel.config.strategy", "dynamic")

    systemProperty("test.server.port", serverPort.toString())
    systemProperty("test.server.baseUrl", "http://localhost:$serverPort")

    // Pass .env Jira credentials to test JVM as JIRA_TEST_* env vars
    val jiraHost = envOrDot("JIRA_HOST")
    val jiraEmail = envOrDot("JIRA_API_EMAIL")
    val jiraToken = envOrDot("JIRA_API_TOKEN")
    if (!jiraHost.isNullOrBlank()) environment("JIRA_TEST_URL", jiraHost)
    if (!jiraEmail.isNullOrBlank()) environment("JIRA_TEST_USER", jiraEmail)
    if (!jiraToken.isNullOrBlank()) environment("JIRA_TEST_TOKEN", jiraToken)
    dependsOn(startServer)
    finalizedBy(stopServer, "aggregate")
}

val apiTest by tasks.registering(Test::class) {
    description = "Run API-only E2E tests (no WebDriver)"
    group = "verification"
    useJUnitPlatform { includeTags("api") }
    testLogging.showStandardStreams = true
    systemProperty("test.server.port", serverPort.toString())
    systemProperty("test.server.baseUrl", "http://localhost:$serverPort")
    // Pass .env Jira credentials to apiTest JVM as JIRA_TEST_* env vars
    val jiraHostApi = envOrDot("JIRA_HOST")
    val jiraEmailApi = envOrDot("JIRA_API_EMAIL")
    val jiraTokenApi = envOrDot("JIRA_API_TOKEN")
    if (!jiraHostApi.isNullOrBlank()) environment("JIRA_TEST_URL", jiraHostApi)
    if (!jiraEmailApi.isNullOrBlank()) environment("JIRA_TEST_USER", jiraEmailApi)
    if (!jiraTokenApi.isNullOrBlank()) environment("JIRA_TEST_TOKEN", jiraTokenApi)
    dependsOn(startServer)
    finalizedBy(stopServer)
}

val uiTest by tasks.registering(Test::class) {
    description = "Run UI E2E tests (with WebDriver/Chrome)"
    group = "verification"
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    filter { includeTestsMatching("com.assistant.e2e.runners.Ui*") }
    // 10 runner classes (1 per feature) → 6 parallel Chrome instances
    maxParallelForks = 6
    // Ensure each fork gets its own temp dir for Chrome profile
    jvmArgs("-Djava.io.tmpdir=${layout.buildDirectory.dir("tmp-ui").get().asFile.absolutePath}")
    systemProperty("test.server.port", serverPort.toString())
    systemProperty("test.server.baseUrl", "http://localhost:$serverPort")
    // Pass .env Jira credentials to uiTest JVM as JIRA_TEST_* env vars
    val jiraHostUi = envOrDot("JIRA_HOST")
    val jiraEmailUi = envOrDot("JIRA_API_EMAIL")
    val jiraTokenUi = envOrDot("JIRA_API_TOKEN")
    if (!jiraHostUi.isNullOrBlank()) environment("JIRA_TEST_URL", jiraHostUi)
    if (!jiraEmailUi.isNullOrBlank()) environment("JIRA_TEST_USER", jiraEmailUi)
    if (!jiraTokenUi.isNullOrBlank()) environment("JIRA_TEST_TOKEN", jiraTokenUi)
    dependsOn(startServer)
    finalizedBy(stopServer)
}

serenity {
    reports = listOf("html")
    sourceDirectory = file("target/site/serenity").path
    // Requirements hierarchy: map subdirectories → capabilities, feature files → features
    requirementsBaseDir = file("src/test/resources/features").absolutePath
    requirementsDir = file("src/test/resources/features").absolutePath
}

// Ensure serenity properties are available during aggregate report generation
tasks.named("aggregate") {
    doFirst {
        System.setProperty("serenity.features.directory", file("src/test/resources/features").absolutePath)
        System.setProperty("serenity.requirement.types", "capability,feature")
        System.setProperty("serenity.test.root", "com.assistant.e2e")
    }
}
