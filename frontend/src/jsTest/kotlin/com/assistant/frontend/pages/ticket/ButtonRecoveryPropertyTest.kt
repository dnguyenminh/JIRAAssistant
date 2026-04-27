package com.assistant.frontend.pages.ticket

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 7: Button recovery on terminal job status.
 *
 * For any job with status in {COMPLETED, FAILED, CANCELLED} × docType in
 * {BRD, FSD, REQUIREMENT_SLIDES}, the Generate button SHALL be enabled.
 * Tests the pure logic function that determines button state based on job status.
 *
 * **Validates: Requirements 8.1, 8.2**
 *
 * Feature: docgen-ux-improvement, Property 7: Button recovery on terminal status
 */
class ButtonRecoveryPropertyTest {

    private val terminalStatuses = listOf("COMPLETED", "FAILED", "CANCELLED")
    private val docTypes = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
    private val nonTerminalStatuses = listOf("QUEUED", "RUNNING")

    @Test
    fun shouldEnableButtonForAllTerminalStatusAndDocTypeCombinations() {
        val rng = Random(seed = 42)
        repeat(200) {
            val status = terminalStatuses[rng.nextInt(terminalStatuses.size)]
            val docType = docTypes[rng.nextInt(docTypes.size)]
            assertTrue(
                DocGenButtonHelper.shouldEnableButton(status),
                "Button should be enabled for status=$status, docType=$docType"
            )
            val btnId = DocGenButtonHelper.buttonIdForDocType(docType)
            assertTrue(
                btnId != null,
                "buttonIdForDocType should return non-null for docType=$docType"
            )
        }
    }

    @Test
    fun shouldNotEnableButtonForNonTerminalStatuses() {
        val rng = Random(seed = 77)
        repeat(200) {
            val status = nonTerminalStatuses[rng.nextInt(nonTerminalStatuses.size)]
            assertTrue(
                !DocGenButtonHelper.shouldEnableButton(status),
                "Button should NOT be enabled for non-terminal status=$status"
            )
        }
    }

    @Test
    fun allTerminalStatusesExhaustive() {
        for (status in terminalStatuses) {
            for (docType in docTypes) {
                assertTrue(
                    DocGenButtonHelper.shouldEnableButton(status),
                    "shouldEnableButton($status) must be true for $docType"
                )
            }
        }
    }

    @Test
    fun buttonIdMappingCoversAllDocTypes() {
        for (docType in docTypes) {
            val btnId = DocGenButtonHelper.buttonIdForDocType(docType)
            assertTrue(btnId != null, "buttonIdForDocType($docType) should not be null")
            assertTrue(btnId.orEmpty().startsWith("btn-generate-"), "btnId should start with btn-generate-")
        }
    }

    @Test
    fun progressAreaIdCoversAllDocTypes() {
        for (docType in docTypes) {
            val areaId = DocGenButtonHelper.progressAreaId(docType)
            assertTrue(areaId.endsWith("-progress-area"), "areaId should end with -progress-area")
        }
    }
}
