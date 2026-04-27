package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * Enum of supported document types for generation (Req 2, 3, 11).
 */
@Serializable
enum class DocumentType {
    BRD,
    FSD,
    REQUIREMENT_SLIDES
}
