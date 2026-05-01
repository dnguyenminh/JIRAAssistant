package com.assistant.server.document.models

import kotlinx.serialization.Serializable

/**
 * API response DTO for a [CollectionJob].
 *
 * Returned by GET /api/collection-jobs and GET /api/collection-jobs/active.
 *
 * Requirements: 13.5, 13.6
 */
@Serializable
data class CollectionJobResponse(
    val jobId: String,
    val parentTicketId: String,
    val jobType: String,
    val status: String,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val progressPercent: Int,
    val items: List<CollectionJobItemResponse>,
    val createdAt: String,
    val updatedAt: String
)

/**
 * API response DTO for a single item inside a [CollectionJob].
 *
 * Requirements: 13.5
 */
@Serializable
data class CollectionJobItemResponse(
    val itemId: String,
    val status: String,
    val skipReason: String?,
    val errorMessage: String?
)
