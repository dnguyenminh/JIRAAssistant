package com.assistant.server.document.curation

import com.assistant.server.document.curation.generators.CurationArbitraries
import com.assistant.server.document.curation.models.ContentClassification
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Properties 1, 2, 3, 5, 11 for CurationPipeline.
 * Validates: Requirements 1.1-1.5, 3.2-3.6, 6.4, 8.5
 */
class CurationPipelinePropertyTest {

    private val pipeline = DefaultCurationPipeline(
        temporalClassifier = DefaultTemporalClassifier(),
        commentSummarizer = DefaultCommentSummarizer(),
        attachmentCurator = DefaultAttachmentCurator(),
        budgetEnforcer = DefaultBudgetEnforcer()
    )

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 1 - KB-first data selection`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbEnrichedContext(2..5)
        ) { context ->
            val result = pipeline.curate(context)
            assertNotNull(result.rootTicket)
            assertEquals(context.mainTicket.ticketId, result.rootTicket.ticketId)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 2 - pipeline always produces result`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbEnrichedContext(2..5)
        ) { context ->
            val result = pipeline.curate(context)
            assertNotNull(result)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 3 - root ticket always preserved`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbEnrichedContext(2..8)
        ) { context ->
            val result = pipeline.curate(context)
            assertEquals(context.mainTicket.ticketId, result.rootTicket.ticketId)
            assertNotNull(result.toBeSection)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 5 - section placement correctness`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbEnrichedContext(3..8)
        ) { context ->
            val result = pipeline.curate(context)
            result.asIsSection.existingFunctionality.forEach {
                assertEquals(ContentClassification.AS_IS, it.classification)
            }
            result.toBeSection.linkedRequirements.forEach {
                assertEquals(ContentClassification.TO_BE, it.classification)
            }
            result.outdatedMetadata.forEach {
                assertTrue(it.ticketId.isNotBlank())
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 11 - determinism`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbEnrichedContext(2..5)
        ) { context ->
            val result1 = pipeline.curate(context)
            val result2 = pipeline.curate(context)
            // Compare all fields except curationTimeMs (non-deterministic)
            val normalized1 = result1.copy(
                metrics = result1.metrics.copy(curationTimeMs = 0L)
            )
            val normalized2 = result2.copy(
                metrics = result2.metrics.copy(curationTimeMs = 0L)
            )
            assertEquals(normalized1, normalized2)
        }
    }
}
