package com.assistant.document.models

import com.assistant.document.generationContext
import com.assistant.document.kbRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

/**
 * Property 24: GenerationContext Open Class Compatibility.
 *
 * Verifies that the open class GenerationContext preserves data class
 * behavior for copy(), equals(), hashCode(), and toString().
 *
 * **Validates: Requirements 5.2**
 */
@OptIn(ExperimentalKotest::class)
class GenerationContextPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Validates: Requirements 5.2**
     *
     * copy() creates a NEW object (not same reference) where
     * equals() returns true compared to the original.
     */
    @Test
    fun `copy creates equal but distinct object`() = runTest {
        checkAll(cfg, Arb.generationContext()) { original ->
            val copied = original.copy()
            assertNotSame(original, copied, "copy() must create a new instance")
            assertEquals(original, copied, "copy() must produce an equal object")
        }
    }

    /**
     * **Validates: Requirements 5.2**
     *
     * hashCode() is consistent with equals(): if a == b then
     * a.hashCode() == b.hashCode().
     */
    @Test
    fun `hashCode consistent with equals`() = runTest {
        checkAll(cfg, Arb.generationContext()) { original ->
            val copied = original.copy()
            assertEquals(original, copied)
            assertEquals(
                original.hashCode(), copied.hashCode(),
                "Equal objects must have the same hashCode"
            )
        }
    }

    /**
     * **Validates: Requirements 5.2**
     *
     * copy() with a modified field produces an object that differs
     * only in the modified field — equals() returns false, and the
     * unmodified fields remain the same.
     */
    @Test
    fun `copy with modified mainTicket differs from original`() = runTest {
        checkAll(cfg, Arb.generationContext(), Arb.kbRecord()) { original, newTicket ->
            val modified = original.copy(mainTicket = newTicket)
            assertEquals(newTicket, modified.mainTicket)
            assertEquals(original.linkedTicketAnalyses, modified.linkedTicketAnalyses)
            assertEquals(original.attachmentChunks, modified.attachmentChunks)
            assertEquals(original.sprintMetadata, modified.sprintMetadata)
            if (newTicket != original.mainTicket) {
                assertNotEquals(original, modified, "Different mainTicket → not equal")
            }
        }
    }

    /**
     * **Validates: Requirements 5.2**
     *
     * Reflexive property: every GenerationContext equals itself.
     */
    @Test
    fun `equals is reflexive`() = runTest {
        checkAll(cfg, Arb.generationContext()) { ctx ->
            assertEquals(ctx, ctx, "An object must equal itself")
        }
    }

    /**
     * **Validates: Requirements 5.2**
     *
     * Symmetric property: if a == b then b == a.
     */
    @Test
    fun `equals is symmetric`() = runTest {
        checkAll(cfg, Arb.generationContext()) { original ->
            val copied = original.copy()
            assertEquals(original, copied)
            assertEquals(copied, original, "equals must be symmetric")
        }
    }
}
