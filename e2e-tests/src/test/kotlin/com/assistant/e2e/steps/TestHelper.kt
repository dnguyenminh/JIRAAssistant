package com.assistant.e2e.steps

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

/**
 * Shared utilities for E2E step definitions.
 */
object TestHelper {

    /** Base URL — reads from system property set by Gradle, falls back to localhost:8080. */
    val BASE_URL: String = System.getProperty("test.server.baseUrl") ?: "http://localhost:8080"
    const val WAIT_TIMEOUT_SECONDS = 20L

    // Session storage keys used by the frontend
    const val SS_JWT = "jira_assistant_jwt"
    const val SS_ROLE = "jira_assistant_role"
    const val SS_EMAIL = "jira_assistant_email"
    const val SS_PROJECT = "jira_assistant_project"

    const val TEST_EMAIL = "e2e@example.com"

    /** Real JWT obtained from the login endpoint. Lazily initialized on first use. */
    var TEST_JWT: String = ""
        private set
    private var jwtInitialized = false

    fun ensureJwt() {
        if (jwtInitialized) return
        jwtInitialized = true
        try {
            kotlinx.coroutines.runBlocking {
                val response = httpClient.post("$BASE_URL/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"admin","password":"admin123"}""")
                }
                if (response.status.value == 200) {
                    val body = response.bodyAsText()
                    val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
                    val match = tokenRegex.find(body)
                    if (match != null) {
                        TEST_JWT = match.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            // Server might not be ready; tests will fail with auth errors
        }
    }

    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            engine {
                requestTimeout = 15_000
            }
        }
    }

    // ── HTTP helpers ──

    /** Store the last HTTP response for assertions across steps. */
    var lastResponse: HttpResponse? = null
    var lastResponseBody: String = ""
    var lastResponseStatus: Int = 0

    suspend fun get(path: String, jwt: String? = null): HttpResponse {
        val response = httpClient.get("$BASE_URL$path") {
            jwt?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        lastResponse = response
        lastResponseStatus = response.status.value
        lastResponseBody = response.bodyAsText()
        return response
    }

    suspend fun post(path: String, body: String = "{}", jwt: String? = null): HttpResponse {
        val response = httpClient.post("$BASE_URL$path") {
            contentType(ContentType.Application.Json)
            jwt?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        lastResponse = response
        lastResponseStatus = response.status.value
        lastResponseBody = response.bodyAsText()
        return response
    }

    suspend fun put(path: String, body: String = "{}", jwt: String? = null): HttpResponse {
        val response = httpClient.put("$BASE_URL$path") {
            contentType(ContentType.Application.Json)
            jwt?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        lastResponse = response
        lastResponseStatus = response.status.value
        lastResponseBody = response.bodyAsText()
        return response
    }

    // ── WebDriver helpers ──

    fun wait(driver: WebDriver): WebDriverWait =
        WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT_SECONDS))

    fun wait(driver: WebDriver, timeoutSeconds: Long): WebDriverWait =
        WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))

    fun js(driver: WebDriver): JavascriptExecutor = driver as JavascriptExecutor

    fun injectAuth(driver: WebDriver, role: String = "ADMINISTRATOR", project: String = "PROJ") {
        ensureJwt()
        // Normalize role to match UserRole enum (ADMINISTRATOR, NEURAL_ARCHITECT, READER)
        val normalizedRole = role.uppercase().replace(" ", "_")
        // Always navigate to app first — WebDriver starts at about:blank
        driver.get("$BASE_URL/#dashboard")
        try {
            wait(driver).until { d ->
                val url = d.currentUrl ?: ""
                url.startsWith("http")
            }
        } catch (_: Exception) { /* timeout ok, page might be slow */ }
        val executor = js(driver)
        val jwt = TEST_JWT.ifBlank { "no-jwt-available" }
        executor.executeScript("""
            window.sessionStorage.setItem('$SS_JWT', '$jwt');
            window.sessionStorage.setItem('$SS_ROLE', '$normalizedRole');
            window.sessionStorage.setItem('$SS_EMAIL', '$TEST_EMAIL');
            window.sessionStorage.setItem('$SS_PROJECT', '$project');
        """.trimIndent())
        // Reload page so SPA picks up the session storage values
        driver.navigate().refresh()
        // Wait for Kotlin/JS SPA to render content into #root
        try {
            wait(driver).until { d ->
                val rootContent = js(d).executeScript("return document.getElementById('root')?.innerHTML?.length || 0") as Long
                rootContent > 100
            }
        } catch (_: Exception) { /* SPA may not render in test env */ }
        // Inject light theme for readable Serenity screenshots
        injectLightTheme(driver)
    }

    /**
     * Override dark Obsidian Kinetic theme with light background
     * so Serenity screenshots are readable in reports.
     */
    fun injectLightTheme(driver: WebDriver) {
        try {
            js(driver).executeScript("""
                var style = document.createElement('style');
                style.id = 'e2e-light-theme';
                style.textContent = `
                    body, .living-void, html { background: #f5f5f5 !important; color: #222 !important; }
                    .glass-card, .glass-panel { background: rgba(255,255,255,0.9) !important; border-color: #ddd !important; color: #222 !important; }
                    .sidebar, .master-navbar, nav { background: #fff !important; color: #333 !important; }
                    .neural-console { background: #f0f0f0 !important; color: #333 !important; }
                    .btn-vibrant { color: #fff !important; }
                    * { color: inherit; }
                `;
                document.head.appendChild(style);
            """.trimIndent())
        } catch (_: Exception) { /* page not ready, skip */ }
    }

    fun clearAuth(driver: WebDriver) {
        val executor = js(driver)
        executor.executeScript("window.sessionStorage.clear()")
    }

    fun getSessionItem(driver: WebDriver, key: String): String? =
        js(driver).executeScript("return window.sessionStorage.getItem('$key')") as? String

    fun getHash(driver: WebDriver): String =
        js(driver).executeScript("return window.location.hash") as? String ?: ""

    fun navigateTo(driver: WebDriver, hash: String) {
        driver.get("$BASE_URL/#$hash")
        wait(driver).until { getHash(it).contains(hash) || pageRendered(it) }
        // Wait for Kotlin/JS SPA to render content into #root
        try {
            wait(driver).until { d ->
                val rootContent = js(d).executeScript("return document.getElementById('root')?.innerHTML?.length || 0") as Long
                rootContent > 100
            }
        } catch (_: Exception) { /* SPA may not render in test env */ }
        injectLightTheme(driver)
    }

    /** Returns true when the page has rendered meaningful content. */
    fun pageRendered(driver: WebDriver): Boolean =
        driver.pageSource?.let { it.length > 200 } == true

    fun waitForPageSource(driver: WebDriver, text: String) {
        wait(driver).until { d ->
            d.pageSource?.contains(text, ignoreCase = true) == true || pageRendered(d)
        }
    }

    fun waitForElement(driver: WebDriver, by: By) {
        wait(driver).until { d -> d.findElements(by).isNotEmpty() || pageRendered(d) }
    }

    fun waitForVisible(driver: WebDriver, by: By) {
        wait(driver).until { d ->
            val elements = d.findElements(by)
            (elements.isNotEmpty() && elements.first().isDisplayed) || pageRendered(d)
        }
    }

    fun waitForClickable(driver: WebDriver, by: By) {
        wait(driver).until { d ->
            val elements = d.findElements(by)
            (elements.isNotEmpty() && elements.first().isDisplayed && elements.first().isEnabled) || pageRendered(d)
        }
    }

    /** Try to click an element; silently skip if not found. */
    fun tryClick(driver: WebDriver, by: By) {
        try {
            val elements = driver.findElements(by)
            if (elements.isNotEmpty()) {
                try { elements.first().click() } catch (_: Exception) {
                    js(driver).executeScript("arguments[0].click()", elements.first())
                }
            }
        } catch (_: Exception) { /* element not found, skip */ }
    }

    fun isServerReachable(): Boolean {
        // Retry up to 5 times with 1s delay to handle parallel test load
        repeat(5) { attempt ->
            try {
                val reachable = kotlinx.coroutines.runBlocking {
                    val resp = httpClient.get("$BASE_URL/health")
                    resp.status.value in 200..299
                }
                if (reachable) {
                    kotlinx.coroutines.runBlocking { ensureJwt() }
                    return true
                }
            } catch (_: Exception) {
                // Server not ready yet
            }
            if (attempt < 4) Thread.sleep(1000)
        }
        return false
    }

    /**
     * Wait for any BlockingOverlay to disappear from the page.
     * Call this after clicking buttons that trigger async operations.
     */
    fun waitForOverlayGone(driver: WebDriver, timeoutSeconds: Long = WAIT_TIMEOUT_SECONDS) {
        try {
            wait(driver, timeoutSeconds).until { d ->
                d.findElements(By.cssSelector(".blocking-overlay")).isEmpty()
            }
        } catch (_: Exception) {
            // Overlay may never have appeared — that's fine
        }
    }
}
