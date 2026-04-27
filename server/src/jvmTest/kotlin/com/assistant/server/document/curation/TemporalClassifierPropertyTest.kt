package com.assistant.server.document.curation

import com.assistant.server.document.curation.generators.CurationArbitraries
import com.assistant.server.document.curation.models.ContentClassification
import com.assistant.server.document.curation.models.TemporalRelation
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 4: Temporal Classification Correctness
 * Validates: Requirements 2.1, 2.3, 2.4, 2.5
 */
class TemporalClassifierPropertyTest {

    private val classifier = DefaultTemporalClassifier()

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `older resolved non-conflicting ticket classified AS_IS`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbStructuredTicketContent(),
            CurationArbitraries.arbStructuredTicketContent()
        ) { root, linked ->
            val rootTicket = root.copy(createdDate = "2024-07-01T00:00:00Z")
            val olderLinked = linked.copy(
                createdDate = "2024-01-15T00:00:00Z",
                status = "Done"
            )
            val result = classifier.classify(rootTicket, olderLinked, null)
            assertEquals(TemporalRelation.OLDER, result.temporalRelation)
            assertEquals(ContentClassification.AS_IS, result.contentClassification)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `newer ticket classified TO_BE`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbStructuredTicketContent(),
            CurationArbitraries.arbStructuredTicketContent()
        ) { root, linked ->
            val rootTicket = root.copy(createdDate = "2024-03-01T00:00:00Z")
            val newerLinked = linked.copy(createdDate = "2024-09-01T00:00:00Z")
            val result = classifier.classify(rootTicket, newerLinked, null)
            assertEquals(TemporalRelation.NEWER, result.temporalRelation)
            assertEquals(ContentClassification.TO_BE, result.contentClassification)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `concurrent ticket classified TO_BE`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbStructuredTicketContent()
        ) { ticket ->
            val sameDate = "2024-06-15T00:00:00Z"
            val root = ticket.copy(createdDate = sameDate)
            val linked = ticket.copy(createdDate = sameDate)
            val result = classifier.classify(root, linked, null)
            assertEquals(TemporalRelation.CONCURRENT, result.temporalRelation)
            assertEquals(ContentClassification.TO_BE, result.contentClassification)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `classification always produces non-blank ticketId`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbStructuredTicketContent(),
            CurationArbitraries.arbStructuredTicketContent()
        ) { root, linked ->
            val result = classifier.classify(root, linked, null)
            assertNotNull(result.contentClassification)
            assertTrue(result.ticketId.isNotBlank())
        }
    }
}
