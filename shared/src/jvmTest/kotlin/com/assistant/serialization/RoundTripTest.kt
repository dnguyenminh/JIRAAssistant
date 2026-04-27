package com.assistant.serialization

import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.ai.JiraTicketSummary
import com.assistant.ai.deepanalysis.models.*
import com.assistant.config.JsonConfig
import com.assistant.domain.NetworkGraph
import com.assistant.domain.ScrumEstimation
import com.assistant.domain.SimilarTicket
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.jira.JiraIssue
import com.assistant.jira.JiraIssueFields
import com.assistant.jira.JiraResolution
import com.assistant.jira.JiraStatus
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Property-based tests for domain object serialization.
 *
 * Feature: jira-assistant-app, Property 8: Domain Object Serialization Round-Trip
 * Feature: jira-assistant-app, Property 9: Missing Required Field Deserialization Error
 */
class RoundTripTest {

    private val json = JsonConfig.instance

    // ── Generators ──────────────────────────────────────────────────────

    private fun arbAlphanumeric(range: IntRange = 1..20): Arb<String> =
        Arb.string(minSize = range.first, maxSize = range.last, codepoints = Codepoint.alphanumeric())

    private fun arbSimilarTicket(): Arb<SimilarTicket> = arbitrary {
        SimilarTicket(
            ticketKey = "PROJ-${Arb.int(1..9999).bind()}",
            summary = arbAlphanumeric(5..50).bind(),
            actualPoints = Arb.element(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0).bind(),
            similarityScore = Arb.double(0.0..1.0).bind()
        )
    }

    private fun arbScrumEstimation(): Arb<ScrumEstimation> = arbitrary {
        ScrumEstimation(
            suggestedPoints = Arb.element(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0).bind(),
            confidenceScore = Arb.double(0.0..1.0).bind(),
            rationale = arbAlphanumeric(5..100).bind(),
            similarHistoricalTickets = Arb.list(arbSimilarTicket(), 0..3).bind()
        )
    }

    private fun arbTicketNode(): Arb<TicketNode> = arbitrary {
        TicketNode(
            id = Arb.int(1..99999).bind().toString(),
            key = "PROJ-${Arb.int(1..9999).bind()}",
            summary = arbAlphanumeric(5..50).bind(),
            status = Arb.element("To Do", "In Progress", "Done", "Review").bind(),
            featureName = Arb.element(null, "Auth", "Dashboard", "Graph").bind()
        )
    }

    private fun arbTicketEdge(): Arb<TicketEdge> = arbitrary {
        TicketEdge(
            fromId = Arb.int(1..99999).bind().toString(),
            toId = Arb.int(1..99999).bind().toString(),
            relationshipType = Arb.element("blocks", "relates to", "is blocked by", "duplicates").bind(),
            isSemantic = Arb.boolean().bind()
        )
    }

    private fun arbNetworkGraph(): Arb<NetworkGraph> = arbitrary {
        NetworkGraph(
            nodes = Arb.list(arbTicketNode(), 0..5).bind(),
            edges = Arb.list(arbTicketEdge(), 0..5).bind()
        )
    }

    private fun arbAIResultSuccess(): Arb<AIResult> = arbitrary {
        AIResult.Success(
            response = arbAlphanumeric(5..100).bind(),
            tokens = Arb.element(null, 100, 500, 1000).bind()
        )
    }

    private fun arbAIResultFailure(): Arb<AIResult> = arbitrary {
        AIResult.Failure(error = arbAlphanumeric(5..50).bind())
    }

    private fun arbAIResult(): Arb<AIResult> = Arb.choice(arbAIResultSuccess(), arbAIResultFailure())

    private fun arbJiraStatus(): Arb<JiraStatus> = arbitrary {
        JiraStatus(
            name = Arb.element("To Do", "In Progress", "Done", "Review").bind(),
            id = Arb.int(1..999).bind().toString()
        )
    }

    private fun arbJiraResolution(): Arb<JiraResolution> = arbitrary {
        JiraResolution(
            name = Arb.element("Fixed", "Won't Fix", "Duplicate").bind(),
            id = Arb.int(1..99).bind().toString()
        )
    }

    private fun arbJiraIssue(): Arb<JiraIssue> = arbitrary {
        JiraIssue(
            id = Arb.int(10000..99999).bind().toString(),
            key = "PROJ-${Arb.int(1..9999).bind()}",
            fields = JiraIssueFields(
                summary = arbAlphanumeric(5..50).bind(),
                description = Arb.element(
                    null,
                    kotlinx.serialization.json.JsonPrimitive("Some description"),
                    kotlinx.serialization.json.JsonPrimitive("Another desc")
                ).bind(),
                status = arbJiraStatus().bind(),
                resolution = Arb.element(
                    null,
                    JiraResolution("Fixed", "1"),
                    JiraResolution("Won't Fix", "2")
                ).bind(),
                created = Arb.element(null, "2024-01-15T10:30:00.000+0000").bind(),
                updated = Arb.element(null, "2024-06-20T14:00:00.000+0000").bind()
            )
        )
    }

    private fun arbEvolutionEntry(): Arb<EvolutionEntry> = arbitrary {
        EvolutionEntry(
            version = "v${Arb.int(1..10).bind()}.${Arb.int(0..9).bind()}",
            date = "2024-0${Arb.int(1..9).bind()}-${Arb.int(10..28).bind()}",
            description = arbAlphanumeric(5..50).bind(),
            changeType = Arb.element("ORIGIN", "UPDATE", "CURRENT").bind()
        )
    }

    private fun arbTechnicalDetails(): Arb<TechnicalDetails> = arbitrary {
        TechnicalDetails(
            apiSpecifications = Arb.list(arbitrary {
                ApiSpecification(
                    method = Arb.element("GET", "POST", "PUT", "DELETE").bind(),
                    path = "/api/${arbAlphanumeric(3..10).bind()}",
                    description = arbAlphanumeric(5..30).bind()
                )
            }, 0..2).bind(),
            databaseChanges = Arb.list(arbitrary {
                DatabaseChange(
                    tableName = arbAlphanumeric(3..15).bind(),
                    operationType = Arb.element("CREATE", "ALTER", "DROP").bind()
                )
            }, 0..2).bind(),
            externalIntegrations = emptyList()
        )
    }

    private fun arbAcceptanceCriterion(): Arb<AcceptanceCriterion> = arbitrary {
        AcceptanceCriterion(
            id = "AC-${Arb.int(1..99).bind()}",
            description = arbAlphanumeric(5..50).bind(),
            testabilityAssessment = Arb.element("HIGH", "MEDIUM", "LOW").bind()
        )
    }

    private fun arbDependencyInfo(): Arb<DependencyInfo> = arbitrary {
        DependencyInfo(
            blockingIssues = Arb.list(arbitrary {
                DependencyItem(key = "PROJ-${Arb.int(1..999).bind()}", summary = arbAlphanumeric(5..30).bind())
            }, 0..2).bind(),
            relatedIssues = emptyList(),
            externalDependencies = Arb.list(arbAlphanumeric(3..15), 0..2).bind()
        )
    }

    private fun arbAnalysisMetadata(): Arb<AnalysisMetadata> = arbitrary {
        AnalysisMetadata(
            extractionConfidence = Arb.element(ExtractionConfidence.HIGH, ExtractionConfidence.MEDIUM, ExtractionConfidence.LOW).bind(),
            analyzedAt = "2024-0${Arb.int(1..9).bind()}-${Arb.int(10..28).bind()}T10:00:00Z",
            aiProviderUsed = Arb.element("gemini", "ollama", "lmstudio").bind(),
            promptVersion = "v${Arb.int(1..5).bind()}"
        )
    }

    private fun arbKBRecord(): Arb<KBRecord> = arbitrary {
        KBRecord(
            ticketId = "PROJ-${Arb.int(1..9999).bind()}",
            requirementSummary = arbAlphanumeric(10..100).bind(),
            evolutionHistory = Arb.list(arbEvolutionEntry(), 0..3).bind(),
            scrumPoints = Arb.element(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0).bind(),
            confidenceScore = Arb.double(0.0..1.0).bind(),
            rationale = arbAlphanumeric(5..100).bind(),
            similarTicketRefs = Arb.list(arbAlphanumeric(3..10), 0..3).bind(),
            timestamp = "2024-01-15T10:30:00Z",
            technicalDetails = arbTechnicalDetails().bind(),
            acceptanceCriteria = Arb.list(arbAcceptanceCriterion(), 0..3).bind(),
            dependencies = arbDependencyInfo().bind(),
            analysisMetadata = arbAnalysisMetadata().bind(),
            businessSummary = arbAlphanumeric(0..50).bind(),
            asIsState = arbAlphanumeric(0..50).bind(),
            toBeState = arbAlphanumeric(0..50).bind(),
            extractedRequirements = Arb.list(arbAlphanumeric(5..30), 0..3).bind()
        )
    }

    // ── Property 8: Serialization Round-Trip ────────────────────────────

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 8 - ScrumEstimation serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbScrumEstimation()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<ScrumEstimation>(jsonStr)
            assertEquals(original, restored, "ScrumEstimation round-trip failed")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 8 - NetworkGraph serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbNetworkGraph()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<NetworkGraph>(jsonStr)
            assertEquals(original, restored, "NetworkGraph round-trip failed")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 8 - AIResult serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbAIResult()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<AIResult>(jsonStr)
            assertEquals(original, restored, "AIResult round-trip failed")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 8 - JiraIssue serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbJiraIssue()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<JiraIssue>(jsonStr)
            assertEquals(original, restored, "JiraIssue round-trip failed")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 8 - KBRecord serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbKBRecord()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<KBRecord>(jsonStr)
            assertEquals(original, restored, "KBRecord round-trip failed")
        }
    }


    // ── Property 9: Missing Required Field Deserialization Error ─────────

    /**
     * **Validates: Requirements 14.5**
     *
     * For any serializable type with required fields, removing a required field
     * from valid JSON should throw an exception containing the field name.
     *
     * Feature: jira-assistant-app, Property 9: Missing Required Field Deserialization Error
     */

    /**
     * Helper: given a valid JSON string and a required field name, remove that field
     * and attempt deserialization, asserting it throws with the field name in the message.
     */
    private inline fun <reified T> assertMissingFieldThrows(
        validJsonStr: String,
        requiredField: String
    ) {
        val jsonObj = json.parseToJsonElement(validJsonStr).jsonObject
        val modified = JsonObject(jsonObj.filterKeys { it != requiredField })
        val modifiedStr = modified.toString()

        try {
            json.decodeFromString<T>(modifiedStr)
            fail("Expected exception when deserializing ${T::class.simpleName} without required field '$requiredField', but deserialization succeeded with JSON: $modifiedStr")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains(requiredField) == true,
                "Exception message should contain field name '$requiredField' but was: ${e.message}"
            )
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - ScrumEstimation missing required field throws with field name`() = runTest {
        val requiredFields = listOf("suggestedPoints", "confidenceScore", "rationale")
        checkAll(PropTestConfig(iterations = 25), arbScrumEstimation(), Arb.element(requiredFields)) { estimation, field ->
            val validJson = json.encodeToString(estimation)
            assertMissingFieldThrows<ScrumEstimation>(validJson, field)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - NetworkGraph missing required field throws with field name`() = runTest {
        val requiredFields = listOf("nodes", "edges")
        checkAll(PropTestConfig(iterations = 25), arbNetworkGraph(), Arb.element(requiredFields)) { graph, field ->
            val validJson = json.encodeToString(graph)
            assertMissingFieldThrows<NetworkGraph>(validJson, field)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - JiraIssue missing required field throws with field name`() = runTest {
        val requiredFields = listOf("id", "key", "fields")
        checkAll(PropTestConfig(iterations = 25), arbJiraIssue(), Arb.element(requiredFields)) { issue, field ->
            val validJson = json.encodeToString(issue)
            assertMissingFieldThrows<JiraIssue>(validJson, field)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - KBRecord missing required field throws with field name`() = runTest {
        val requiredFields = listOf("ticketId", "requirementSummary", "evolutionHistory", "scrumPoints", "confidenceScore", "rationale", "similarTicketRefs", "timestamp")
        checkAll(PropTestConfig(iterations = 25), arbKBRecord(), Arb.element(requiredFields)) { record, field ->
            val validJson = json.encodeToString(record)
            assertMissingFieldThrows<KBRecord>(validJson, field)
        }
    }
}
