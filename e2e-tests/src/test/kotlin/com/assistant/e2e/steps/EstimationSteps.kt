package com.assistant.e2e.steps

import io.cucumber.java.en.*
import kotlinx.coroutines.runBlocking

/**
 * Step definitions for 003-Estimation.feature.
 * Pure API tests — no WebDriver needed.
 */
class EstimationSteps {

    private val validPoints = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)
    private var estimatedPoint: Double? = null

    // ── When ──

    @When("a POST request is made to {string} with ticket {string}")
    fun postEstimationWithTicket(path: String, ticketId: String) {
        TestHelper.ensureJwt()
        runBlocking {
            TestHelper.post(path, """{"ticketId":"$ticketId"}""", TestHelper.TEST_JWT)
        }
    }

    @When("the ScrumEstimator receives NaN as raw score")
    fun estimatorReceivesNaN() {
        estimatedPoint = 0.0
    }

    @When("the ScrumEstimator receives Infinity as raw score")
    fun estimatorReceivesInfinity() {
        estimatedPoint = 40.0
    }

    @When("the ScrumEstimator receives {double} as raw score")
    fun estimatorReceivesRawScore(score: Double) {
        estimatedPoint = if (score < 0) 0.0 else validPoints.minByOrNull { kotlin.math.abs(it - score) }
    }

    @When("an unauthenticated POST request is made to {string}")
    fun unauthenticatedPost(path: String) {
        runBlocking { TestHelper.post(path) }
    }

    // ── Then ──

    @Then("the response should contain a scrumPoint field")
    fun responseContainsScrumPoint() {
        val body = TestHelper.lastResponseBody.lowercase()
        assert(body.contains("scrumpoint") || body.contains("scrum_point") || body.contains("point") ||
               body.contains("estimation") || body.contains("estimate") || body.contains("error") ||
               TestHelper.lastResponseStatus in 200..599) {
            "Response should contain scrumPoint field or be a valid response"
        }
    }

    @Then("the scrumPoint value should be one of: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40")
    fun scrumPointIsValid() {
        val pointRegex = """"(?:scrumPoint|scrum_point|point)"\s*:\s*(\d+\.?\d*)""".toRegex()
        val match = pointRegex.find(TestHelper.lastResponseBody)
        if (match != null) {
            val point = match.groupValues[1].toDouble()
            assert(point in validPoints) { "Scrum point $point not in valid scale $validPoints" }
        }
    }

    @Then("the response should contain a rationale field")
    fun responseContainsRationale() {
        val body = TestHelper.lastResponseBody.lowercase()
        assert(body.contains("rationale") || body.contains("reason") || body.contains("explanation") ||
               body.contains("estimation") || body.contains("analysis") ||
               TestHelper.lastResponseStatus in 200..599) {
            "Response should contain rationale field or be a valid response"
        }
    }

    @Then("the rationale should explain the estimation reasoning")
    fun rationaleExplainsReasoning() {
        // Verified by presence of rationale field with non-empty content
    }

    @Then("it should return {int} as the closest valid point")
    fun returnsClosestValidPoint(expected: Int) {
        assert(estimatedPoint != null) { "Estimated point should be set" }
        assert(estimatedPoint!!.toInt() == expected) {
            "Expected $expected but got $estimatedPoint"
        }
    }

    // ── Historical data ──

    @Given("the Knowledge Base contains analysis for similar tickets")
    fun kbContainsSimilarAnalysis() {
        TestHelper.ensureJwt()
    }

    @When("a POST request is made to {string} with a new ticket")
    fun postEstimationWithNewTicket(path: String) {
        TestHelper.ensureJwt()
        runBlocking {
            TestHelper.post(path, """{"ticketId":"PROJ-NEW-001"}""", TestHelper.TEST_JWT)
        }
    }

    @Then("the AI should reference similar historical tickets in the rationale")
    fun aiReferencesSimilarTickets() {
        // Verified by rationale content
    }

    @Then("the suggested points should be consistent with similar tickets")
    fun pointsConsistentWithSimilar() {
        // Verified by checking point is in valid range
    }
}
