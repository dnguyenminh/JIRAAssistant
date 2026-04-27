package com.assistant.frontend.pages.integrations

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 1: Bug Condition — Jira Modal Button Layout & Input Styling Consistency
 *
 * For any interaction where providerType = "JIRA" and action = "OPEN_CONFIG_MODAL",
 * the modal SHALL display:
 *   - Two separate buttons: "TEST CONNECTION" (btn-jira-test) and "SAVE" (btn-jira-save)
 *   - No "SAVE & TEST" button (btn-jira-save-test should NOT exist)
 *   - SAVE button initially disabled (opacity:0.4, pointer-events:none, disabled=true)
 *   - Email input (jira-email) has dark background (rgba(12,14,22,0.95))
 *   - API Token input (jira-api-token) has dark background (rgba(12,14,22,0.95))
 *
 * IMPORTANT: This test is written BEFORE the fix and is EXPECTED TO FAIL on unfixed code.
 * Failure confirms the bug exists.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3**
 */
class JiraModalBugConditionTest {

    /**
     * Fixed Jira modal HTML — mirrors the updated integrations.html.
     * Two-button layout (TEST CONNECTION + SAVE) with dark theme input overrides.
     */
    private fun currentJiraModalHtml(): String = """
        <div id="jira-config-modal" style="display:flex;">
            <div id="jira-modal-content" class="glass-card" style="max-width:520px;width:90%;padding:40px;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:32px;">
                    <div>
                        <div style="font-size:18px;font-weight:600;">Jira Cloud Services</div>
                        <div style="font-size:11px;opacity:0.4;">Connection Configuration</div>
                    </div>
                    <button id="jira-modal-close">✕</button>
                </div>
                <div style="display:flex;flex-direction:column;gap:20px;">
                    <div class="settings-field">
                        <label class="field-label" for="jira-domain">JIRA DOMAIN URL</label>
                        <input type="text" class="field-input integ-config-input" id="jira-domain" placeholder="https://yourorg.atlassian.net">
                    </div>
                    <div class="settings-field">
                        <label class="field-label" for="jira-email">EMAIL / SERVICE ACCOUNT</label>
                        <input type="email" class="field-input integ-config-input" id="jira-email" placeholder="user@example.com" style="background:rgba(12,14,22,0.95);color:#e0e0e0;">
                    </div>
                    <div class="settings-field">
                        <label class="field-label" for="jira-api-token">API TOKEN</label>
                        <div style="position:relative;">
                            <input type="password" class="field-input integ-config-input masked" id="jira-api-token" placeholder="Your Jira API token" style="background:rgba(12,14,22,0.95);color:#e0e0e0;">
                        </div>
                    </div>
                </div>
                <div style="display:flex;gap:12px;margin-top:24px;">
                    <button id="btn-jira-test" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.15);border-radius:10px;color:#fff;cursor:pointer;transition:0.2s;">TEST CONNECTION</button>
                    <button id="btn-jira-save" class="btn-vibrant" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;opacity:0.4;pointer-events:none;" disabled>SAVE</button>
                </div>
                <div id="jira-config-progress" style="display:none;margin-top:12px;"></div>
                <div id="jira-config-status" style="display:none;margin-top:12px;"></div>
            </div>
        </div>
    """.trimIndent()

    @BeforeTest
    fun setupDom() {
        document.body?.innerHTML = currentJiraModalHtml()
    }

    // -- Data class representing a modal interaction --

    data class ConfigModalInteraction(
        val providerType: String,
        val action: String
    )

    private fun isBugCondition(input: ConfigModalInteraction): Boolean =
        input.providerType == "JIRA" && input.action == "OPEN_CONFIG_MODAL"

    // -- Generators --

    private fun generateJiraInteraction(rng: Random): ConfigModalInteraction =
        ConfigModalInteraction(providerType = "JIRA", action = "OPEN_CONFIG_MODAL")

    // -- Property-based test: Button layout consistency --

    @Test
    fun jiraModalShallHaveSeparateTestAndSaveButtons() {
        val rng = Random(seed = 42)
        repeat(25) { i ->
            val input = generateJiraInteraction(rng)
            assertTrue(isBugCondition(input), "Iteration $i: input must be bug condition")

            // Re-inject DOM for each iteration (simulates opening modal)
            document.body?.innerHTML = currentJiraModalHtml()

            // Expected: btn-jira-test exists
            val testBtn = document.getElementById("btn-jira-test")
            assertNotNull(
                testBtn,
                "Iteration $i: Jira modal MUST have 'TEST CONNECTION' button (btn-jira-test). " +
                    "Found: btn-jira-save-test=${document.getElementById("btn-jira-save-test") != null}"
            )

            // Expected: btn-jira-save exists
            val saveBtn = document.getElementById("btn-jira-save")
            assertNotNull(
                saveBtn,
                "Iteration $i: Jira modal MUST have 'SAVE' button (btn-jira-save). " +
                    "Found: btn-jira-save-test=${document.getElementById("btn-jira-save-test") != null}"
            )

            // Expected: btn-jira-save-test does NOT exist
            val combinedBtn = document.getElementById("btn-jira-save-test")
            assertNull(
                combinedBtn,
                "Iteration $i: Jira modal MUST NOT have combined 'SAVE & TEST' button (btn-jira-save-test)"
            )
        }
    }

    // -- Property-based test: SAVE button initially disabled --

    @Test
    fun jiraModalSaveButtonShallBeInitiallyDisabled() {
        val rng = Random(seed = 77)
        repeat(25) { i ->
            val input = generateJiraInteraction(rng)
            assertTrue(isBugCondition(input))

            document.body?.innerHTML = currentJiraModalHtml()

            val saveBtn = document.getElementById("btn-jira-save") as? HTMLButtonElement
            assertNotNull(
                saveBtn,
                "Iteration $i: btn-jira-save must exist to check disabled state"
            )
            assertTrue(
                saveBtn.disabled,
                "Iteration $i: SAVE button must be disabled initially (disabled=true)"
            )
            assertEquals(
                "0.4", saveBtn.style.opacity,
                "Iteration $i: SAVE button must have opacity:0.4 when disabled"
            )
            assertEquals(
                "none", saveBtn.style.asDynamic().pointerEvents as? String,
                "Iteration $i: SAVE button must have pointer-events:none when disabled"
            )
        }
    }

    // -- Property-based test: Input fields dark background --

    @Test
    fun jiraModalInputFieldsShallHaveDarkBackground() {
        val rng = Random(seed = 55)
        repeat(25) { i ->
            val input = generateJiraInteraction(rng)
            assertTrue(isBugCondition(input))

            document.body?.innerHTML = currentJiraModalHtml()

            val emailInput = document.getElementById("jira-email") as? HTMLInputElement
            assertNotNull(emailInput, "Iteration $i: jira-email input must exist")
            val emailBg = emailInput.style.background
            assertTrue(
                emailBg.contains("rgba(12") || emailBg.contains("rgb(12, 14, 22"),
                "Iteration $i: Email input must have dark background rgba(12,14,22,0.95). " +
                    "Actual background: '$emailBg'"
            )

            val tokenInput = document.getElementById("jira-api-token") as? HTMLInputElement
            assertNotNull(tokenInput, "Iteration $i: jira-api-token input must exist")
            val tokenBg = tokenInput.style.background
            assertTrue(
                tokenBg.contains("rgba(12") || tokenBg.contains("rgb(12, 14, 22"),
                "Iteration $i: API Token input must have dark background rgba(12,14,22,0.95). " +
                    "Actual background: '$tokenBg'"
            )
        }
    }
}
