package com.assistant.server.document.curation

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.document.curation.models.ContentClassification
import com.assistant.server.document.curation.models.TemporalRelation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for TemporalClassifier edge cases.
 * Requirements: 2.2, 2.6
 */
class TemporalClassifierTest {

    private val classifier = DefaultTemporalClassifier()

    @Test
    fun `same-day tickets classified as CONCURRENT and TO_BE`() {
        val root = ticket(created = "2024-06-15T10:00:00Z")
        val linked = ticket(created = "2024-06-15T10:00:00Z")
        val result = classifier.classify(root, linked, null)
        assertEquals(TemporalRelation.CONCURRENT, result.temporalRelation)
        assertEquals(ContentClassification.TO_BE, result.contentClassification)
    }

    @Test
    fun `missing dates classified as CONCURRENT and TO_BE`() {
        val root = ticket(created = "")
        val linked = ticket(created = "2024-06-15T10:00:00Z")
        val result = classifier.classify(root, linked, null)
        assertEquals(TemporalRelation.CONCURRENT, result.temporalRelation)
        assertEquals(ContentClassification.TO_BE, result.contentClassification)
    }

    @Test
    fun `older ticket with Open status defaults to TO_BE`() {
        val root = ticket(created = "2024-09-01T00:00:00Z")
        val linked = ticket(created = "2024-01-01T00:00:00Z", status = "Open")
        val result = classifier.classify(root, linked, null)
        assertEquals(TemporalRelation.OLDER, result.temporalRelation)
        assertEquals(ContentClassification.TO_BE, result.contentClassification)
    }

    @Test
    fun `older ticket with Closed status classified AS_IS`() {
        val root = ticket(created = "2024-09-01T00:00:00Z")
        val linked = ticket(created = "2024-01-01T00:00:00Z", status = "Closed")
        val result = classifier.classify(root, linked, null)
        assertEquals(ContentClassification.AS_IS, result.contentClassification)
    }

    @Test
    fun `older ticket with Done status classified AS_IS`() {
        val root = ticket(created = "2024-09-01T00:00:00Z")
        val linked = ticket(created = "2024-01-01T00:00:00Z", status = "Done")
        val result = classifier.classify(root, linked, null)
        assertEquals(ContentClassification.AS_IS, result.contentClassification)
    }

    @Test
    fun `older ticket with Resolved status classified AS_IS`() {
        val root = ticket(created = "2024-09-01T00:00:00Z")
        val linked = ticket(created = "2024-01-01T00:00:00Z", status = "Resolved")
        val result = classifier.classify(root, linked, null)
        assertEquals(ContentClassification.AS_IS, result.contentClassification)
    }

    private fun ticket(
        created: String = "2024-06-01T00:00:00Z",
        status: String = "Open"
    ) = StructuredTicketContent(
        summary = "TEST-1 Test ticket",
        description = "Test description",
        status = status,
        createdDate = created,
        updatedDate = created
    )
}
