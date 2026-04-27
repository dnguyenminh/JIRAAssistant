package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Status of a document generation job (Req 2.1).
 */
@Serializable
enum class JobStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}
