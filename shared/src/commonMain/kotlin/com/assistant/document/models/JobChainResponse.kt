package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Response for "Generate All" — chain ID + list of jobs (Req 1.4, 8.4).
 */
@Serializable
data class JobChainResponse(
    val chainId: String,
    val jobs: List<GenerationJob>
)
