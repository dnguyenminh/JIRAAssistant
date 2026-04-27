package com.assistant.server.document.curation.models

import kotlinx.serialization.Serializable

/**
 * Result of budget enforcement on a CuratedContext.
 *
 * Requirements: 6.3, 6.5
 */
@Serializable
data class BudgetResult(
    val context: CuratedContext,
    val truncationApplied: Boolean,
    val truncationAnnotation: String? = null,
    val originalSize: Int,
    val finalSize: Int
)
