package com.assistant.server.document.curation

import com.assistant.server.document.curation.models.BudgetResult
import com.assistant.server.document.curation.models.CuratedContext

/**
 * Enforces prompt budget via progressive truncation.
 *
 * Requirements: 6.1
 */
interface BudgetEnforcer {
    /**
     * Enforce budget limit on curated context.
     *
     * @param context The curated context to constrain
     * @param maxChars Maximum allowed characters
     * @return Budget result with truncation info
     */
    fun enforce(context: CuratedContext, maxChars: Int): BudgetResult
}
