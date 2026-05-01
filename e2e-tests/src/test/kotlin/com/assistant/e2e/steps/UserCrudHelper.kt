package com.assistant.e2e.steps

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

/**
 * Shared helper functions for UserCrud step definitions.
 *
 * Extracted to keep UserCrudSteps.kt and UserCrudAssertSteps.kt
 * within the 200-line limit.
 */
object UserCrudHelper {

    private val idRegex = """"(?:id|userId)"\s*:\s*"([^"]+)"""".toRegex()

    /**
     * Delete any existing user with the given email via API.
     * Prevents 409 Conflict when re-running E2E-UI-01 create tests.
     */
    fun cleanupTestUserByEmail(email: String) {
        TestHelper.ensureJwt()
        val jwt = TestHelper.TEST_JWT.ifBlank { return }
        val url = TestHelper.BASE_URL
        runBlocking {
            try {
                val resp = TestHelper.httpClient.get("$url/api/users") {
                    header(HttpHeaders.Authorization, "Bearer $jwt")
                }
                val body = resp.bodyAsText()
                val userBlock = body.split("},").firstOrNull {
                    it.contains(email, ignoreCase = true)
                } ?: return@runBlocking
                val userId = idRegex.find(userBlock)?.groupValues?.get(1)
                    ?: return@runBlocking
                TestHelper.httpClient.delete("$url/api/users/$userId") {
                    header(HttpHeaders.Authorization, "Bearer $jwt")
                }
            } catch (_: Exception) { /* cleanup is best-effort */ }
        }
    }

    fun waitForUserList(driver: WebDriver) {
        TestHelper.wait(driver).until { d ->
            d.pageSource?.contains("@") == true ||
                d.pageSource?.contains("User", ignoreCase = true) == true ||
                TestHelper.pageRendered(d)
        }
    }

    fun waitForFormVisible(driver: WebDriver) {
        TestHelper.wait(driver).until { d ->
            d.findElements(By.cssSelector("input, [class*='form']")).isNotEmpty() ||
                TestHelper.pageRendered(d)
        }
    }

    fun waitForDetailPanel(driver: WebDriver) {
        TestHelper.wait(driver).until { d ->
            d.pageSource?.contains("Edit", ignoreCase = true) == true ||
                d.pageSource?.contains("detail", ignoreCase = true) == true ||
                TestHelper.pageRendered(d)
        }
    }

    fun waitForDialog(driver: WebDriver) {
        TestHelper.wait(driver).until { d ->
            d.pageSource?.contains("Confirm", ignoreCase = true) == true ||
                d.findElements(By.cssSelector("[class*='dialog'], [class*='modal']")).isNotEmpty() ||
                TestHelper.pageRendered(d)
        }
    }

    fun clickButtonByText(driver: WebDriver, text: String) {
        val xpath = "//*[contains(text(),'$text')]"
        TestHelper.wait(driver).until { d ->
            d.findElements(By.xpath(xpath)).isNotEmpty() || TestHelper.pageRendered(d)
        }
        val elements = driver.findElements(By.xpath(xpath))
        if (elements.isNotEmpty()) {
            val el = elements.first()
            try {
                TestHelper.js(driver).executeScript("arguments[0].scrollIntoView({block:'center'})", el)
                el.click()
            } catch (_: Exception) {
                TestHelper.js(driver).executeScript("arguments[0].click()", el)
            }
        }
    }

    fun clickFirstUserRow(driver: WebDriver) {
        val selectors = listOf(
            By.cssSelector(".um-user-row"),
            By.cssSelector("[class*='user-row']"),
            By.cssSelector("tr[data-user-id]"),
            By.cssSelector("[class*='user-card']")
        )
        for (selector in selectors) {
            val rows = driver.findElements(selector)
            if (rows.isNotEmpty()) {
                try { rows.first().click() } catch (_: Exception) {
                    TestHelper.js(driver).executeScript("arguments[0].click()", rows.first())
                }
                return
            }
        }
        // Fallback: click any element containing an email
        val emailEls = driver.findElements(By.xpath("//*[contains(text(),'@')]"))
        if (emailEls.isNotEmpty()) {
            try { emailEls.first().click() } catch (_: Exception) {}
        }
    }

    fun fillInput(driver: WebDriver, fieldHint: String, value: String) {
        val inputs = driver.findElements(By.cssSelector("input"))
        val target: WebElement? = inputs.firstOrNull { input ->
            val name = input.getAttribute("name") ?: ""
            val placeholder = input.getAttribute("placeholder") ?: ""
            val id = input.getAttribute("id") ?: ""
            val dataField = input.getAttribute("data-field") ?: ""
            name.contains(fieldHint, true) ||
                placeholder.contains(fieldHint, true) ||
                id.contains(fieldHint, true) ||
                dataField.contains(fieldHint, true)
        } ?: inputs.firstOrNull { it.isDisplayed }

        if (target != null) {
            try {
                target.clear()
                target.sendKeys(value)
            } catch (_: Exception) {
                TestHelper.js(driver).executeScript(
                    "arguments[0].value=arguments[1]; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));",
                    target, value
                )
            }
        }
    }

    fun selectRole(driver: WebDriver, role: String) {
        val selects = driver.findElements(By.cssSelector("select"))
        val target = selects.firstOrNull { it.isDisplayed }
        if (target != null) {
            TestHelper.js(driver).executeScript(
                "arguments[0].value=arguments[1]; arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                target, role
            )
        }
    }

    fun extractDetailPanelName(driver: WebDriver): String {
        val selectors = listOf(
            By.cssSelector(".detail-name, [class*='detail'] h2, [class*='detail'] h3"),
            By.cssSelector("[class*='user-name']")
        )
        for (selector in selectors) {
            val elements = driver.findElements(selector)
            if (elements.isNotEmpty()) return elements.first().text
        }
        return ""
    }
}
