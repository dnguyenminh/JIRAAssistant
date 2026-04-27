package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Represents a single document generation job (Req 2.1).
 * Stored in `generation_jobs` table.
 */
@Serializable
data class GenerationJob(
    val jobId: String,
    val ticketId: String,
    val documentType: String,
    val status: String,
    val progressPercent: Int = 0,
    val phase: String = "QUEUED",
    val chainId: String? = null,
    val createdBy: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val errorMessage: String? = null,
    val startedAt: String? = null
)
