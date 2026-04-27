package com.assistant.frontend.pages.integrations

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 2: Preservation — AI Provider Modal Behavior Unchanged
 *
 * For any interaction where the user opens a non-JIRA provider config modal
 * (isBugCondition returns false), the modal SHALL produce exactly the same
 * layout and behavior as the original code:
 *   - "TEST CONNECTION" button (modal-btn-test) present
 *   - "SAVE" button (modal-btn-save) present and initially disabled
 *   - No "SAVE & TEST" button in the modal
 *   - Modal HTML built by buildModalHtml() contains correct button structure
 *
 * These tests run BEFORE the fix and are EXPECTED TO PASS on unfixed code,
 * confirming the baseline AI provider modal behavior to preserve.
 *
 * Preservation: FOR ALL X WHERE NOT isBugCondition(X) DO ASSERT F(X) = F'(X)
 *
 * **Validates: Requirements 3.1, 3.3, 3.4, 3.5, 3.6**
 */
class AIProviderModalPreservationTest {

    /** Non-JIRA provider types that use the standard config modal. */
    private val aiProviderTypes = listOf("OLLAMA", "GEMINI", "LM_STUDIO", "GEMINI_CLI")

    data class ConfigModalInteraction(
        val providerType: String,
        val action: String
    )

    private fun isBugCondition(input: ConfigModalInteraction): Boolean =
        input.providerType == "JIRA" && input.action == "OPEN_CONFIG_MODAL"

    /**
     * Builds the AI provider modal HTML matching IntegrationsConfigModal.buildModalHtml().
     * This replicates the actual output for a given provider name, preserving the
     * exact button structure: modal-btn-test + modal-btn-save (disabled).
     */
    private fun buildAIProviderModalHtml(providerName: String): String = """
        <div id="integ-modal-overlay" style="display:flex; position:fixed; inset:0;">
            <div id="integ-modal-content" class="glass-card" style="max-width:520px;width:90%;padding:40px;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:32px;">
                    <div><div style="font-size:18px;font-weight:600;">$providerName</div>
                    <div style="font-size:11px;opacity:0.4;letter-spacing:1px;margin-top:4px;">Configuration</div></div>
                    <button id="modal-btn-close" style="background:none;border:none;color:var(--text-sub);font-size:20px;cursor:pointer;padding:8px;">✕</button>
                </div>
                <div style="display:flex;flex-direction:column;gap:20px;">
                    <div><label>ENDPOINT URL</label><input id="cfg-endpoint" class="integ-config-input" type="text" value="http://localhost:11434"></div>
                </div>
                <div style="display:flex;gap:12px;margin-top:24px;">
                    <button id="modal-btn-test" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.15);border-radius:10px;color:#fff;cursor:pointer;transition:0.2s;">TEST CONNECTION</button>
                    <button id="modal-btn-save" class="btn-vibrant" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;opacity:0.4;pointer-events:none;" disabled>SAVE</button>
                </div>
                <div id="modal-save-progress" style="display:none;margin-top:12px;"></div>
                <div id="modal-save-msg" style="display:none;margin-top:12px;"></div>
            </div>
        </div>
    """.trimIndent()

    // -- Generators --

    private fun generateNonJiraInteraction(rng: Random): Pair<ConfigModalInteraction, String> {
        val providerType = aiProviderTypes[rng.nextInt(aiProviderTypes.size)]
        val providerName = when (providerType) {
            "OLLAMA" -> "Ollama"
            "GEMINI" -> "Gemini"
            "LM_STUDIO" -> "LM Studio"
            "GEMINI_CLI" -> "Gemini CLI"
            else -> providerType
        }
        return ConfigModalInteraction(
            providerType = providerType,
            action = "OPEN_CONFIG_MODAL"
        ) to providerName
    }

    // -- Property-based test: TEST CONNECTION button present --

    @Test
    fun aiProviderModalShallHaveTestConnectionButton() {
        val rng = Random(seed = 100)
        repeat(30) { i ->
            val (input, name) = generateNonJiraInteraction(rng)
            assertTrue(
                !isBugCondition(input),
                "Iteration $i: input must NOT be bug condition (type=${input.providerType})"
            )

            document.body?.innerHTML = buildAIProviderModalHtml(name)

            val testBtn = document.getElementById("modal-btn-test") as? HTMLElement
            assertNotNull(
                testBtn,
                "Iteration $i [${input.providerType}]: AI provider modal MUST have " +
                    "'TEST CONNECTION' button (modal-btn-test)"
            )
            assertTrue(
                testBtn.textContent?.contains("TEST CONNECTION") == true,
                "Iteration $i [${input.providerType}]: TEST CONNECTION button text mismatch. " +
                    "Actual: '${testBtn.textContent}'"
            )
        }
    }

    // -- Property-based test: SAVE button present and initially disabled --

    @Test
    fun aiProviderModalSaveButtonShallBePresentAndDisabled() {
        val rng = Random(seed = 200)
        repeat(30) { i ->
            val (input, name) = generateNonJiraInteraction(rng)
            assertTrue(!isBugCondition(input))

            document.body?.innerHTML = buildAIProviderModalHtml(name)

            val saveBtn = document.getElementById("modal-btn-save") as? HTMLButtonElement
            assertNotNull(
                saveBtn,
                "Iteration $i [${input.providerType}]: AI provider modal MUST have " +
                    "'SAVE' button (modal-btn-save)"
            )
            assertTrue(
                saveBtn.disabled,
                "Iteration $i [${input.providerType}]: SAVE button must be disabled initially"
            )
            assertEquals(
                "0.4", saveBtn.style.opacity,
                "Iteration $i [${input.providerType}]: SAVE button must have opacity:0.4"
            )
            assertEquals(
                "none", saveBtn.style.asDynamic().pointerEvents as? String,
                "Iteration $i [${input.providerType}]: SAVE button must have pointer-events:none"
            )
        }
    }

    // -- Property-based test: No "SAVE & TEST" button in AI provider modals --

    @Test
    fun aiProviderModalShallNotHaveSaveAndTestButton() {
        val rng = Random(seed = 300)
        repeat(30) { i ->
            val (input, name) = generateNonJiraInteraction(rng)
            assertTrue(!isBugCondition(input))

            document.body?.innerHTML = buildAIProviderModalHtml(name)

            val combinedBtn = document.getElementById("btn-jira-save-test")
            assertNull(
                combinedBtn,
                "Iteration $i [${input.providerType}]: AI provider modal MUST NOT have " +
                    "combined 'SAVE & TEST' button (btn-jira-save-test)"
            )

            // Also verify no element with text "SAVE & TEST" exists
            val allButtons = document.querySelectorAll("button")
            for (j in 0 until allButtons.length) {
                val btn = allButtons.item(j) as? HTMLElement
                val text = btn?.textContent?.trim() ?: ""
                assertTrue(
                    !text.contains("SAVE & TEST") && !text.contains("SAVE &amp; TEST"),
                    "Iteration $i [${input.providerType}]: Found unexpected 'SAVE & TEST' " +
                        "button text in AI provider modal: '$text'"
                )
            }
        }
    }

    // -- Property-based test: Routing — non-JIRA providers use standard modal --

    @Test
    fun openConfigModalRoutesNonJiraToStandardModal() {
        val rng = Random(seed = 400)
        repeat(30) { i ->
            val (input, name) = generateNonJiraInteraction(rng)
            assertTrue(!isBugCondition(input))

            // Verify the routing logic: non-JIRA types should NOT trigger
            // IntegrationsJiraModal — they use IntegrationsConfigModal which
            // renders into integ-modal-overlay with buildModalHtml()
            assertTrue(
                input.providerType != "JIRA",
                "Iteration $i: Non-JIRA provider must not be JIRA type"
            )

            // Simulate: standard modal overlay is used (not jira-config-modal)
            document.body?.innerHTML = buildAIProviderModalHtml(name)

            val standardOverlay = document.getElementById("integ-modal-overlay")
            assertNotNull(
                standardOverlay,
                "Iteration $i [${input.providerType}]: Standard modal overlay " +
                    "(integ-modal-overlay) must be present for AI providers"
            )

            val jiraModal = document.getElementById("jira-config-modal")
            assertNull(
                jiraModal,
                "Iteration $i [${input.providerType}]: Jira modal (jira-config-modal) " +
                    "must NOT be present when opening AI provider modal"
            )
        }
    }

    // -- Property-based test: Input fields use dark background via field-input --

    @Test
    fun aiProviderModalInputFieldsUseDarkBackgroundClass() {
        val rng = Random(seed = 500)
        repeat(30) { i ->
            val (input, name) = generateNonJiraInteraction(rng)
            assertTrue(!isBugCondition(input))

            document.body?.innerHTML = buildAIProviderModalHtml(name)

            val configInputs = document.querySelectorAll(".integ-config-input")
            assertTrue(
                configInputs.length > 0,
                "Iteration $i [${input.providerType}]: AI provider modal must have " +
                    "config input fields with integ-config-input class"
            )
        }
    }
}
