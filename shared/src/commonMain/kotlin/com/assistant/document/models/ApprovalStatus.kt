package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Review status of a generated document (Req 6.1, 7.1).
 */
@Serializable
enum class ApprovalStatus {
    DRAFT, APPROVED, REJECTED
}
